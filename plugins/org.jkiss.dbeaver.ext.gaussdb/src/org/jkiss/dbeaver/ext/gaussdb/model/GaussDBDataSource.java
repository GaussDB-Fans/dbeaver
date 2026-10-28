/*
 * DBeaver - Universal Database Manager
 * Copyright (C) 2010-2026 DBeaver Corp and others
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.jkiss.dbeaver.ext.gaussdb.model;

import org.jkiss.code.NotNull;
import org.jkiss.dbeaver.DBException;
import org.jkiss.dbeaver.Log;
import org.jkiss.dbeaver.ext.gaussdb.GaussDBConstants;
import org.jkiss.dbeaver.ext.postgresql.PostgreConstants;
import org.jkiss.dbeaver.ext.postgresql.PostgreUtils;
import org.jkiss.dbeaver.ext.postgresql.model.PostgreCharset;
import org.jkiss.dbeaver.ext.postgresql.model.PostgreDataSource;
import org.jkiss.dbeaver.ext.postgresql.model.PostgreDatabase;
import org.jkiss.dbeaver.ext.postgresql.model.PostgreRole;
import org.jkiss.dbeaver.ext.postgresql.model.PostgreServerExtension;
import org.jkiss.dbeaver.ext.postgresql.model.PostgreTablespace;
import org.jkiss.dbeaver.model.DBPDataSourceContainer;
import org.jkiss.dbeaver.model.DBUtils;
import org.jkiss.dbeaver.model.connection.DBPConnectionConfiguration;
import org.jkiss.dbeaver.model.exec.jdbc.JDBCSession;
import org.jkiss.dbeaver.model.impl.jdbc.JDBCUtils;
import org.jkiss.dbeaver.model.meta.Property;
import org.jkiss.dbeaver.model.runtime.DBRProgressMonitor;
import org.jkiss.utils.CommonUtils;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class GaussDBDataSource extends PostgreDataSource {

    private static final Log log = Log.getLog(GaussDBDataSource.class);

    /**
     * Pattern to extract version numbers from GaussDB version strings such as
     * "GaussDB Kernel 503.1.0.SPC1200" or "openGauss 5.0.0".
     */
    private static final Pattern GAUSSDB_VERSION_PATTERN = Pattern.compile("(\\d+)\\.(\\d+)");

    private PostgreServerExtension serverExtension;

    /**
     * Detected deployment mode: centralized or distributed.
     * Determined at connection initialization by checking the {@code pgxc_node} catalog existence.
     */
    private GaussDBDeploymentMode deploymentMode = GaussDBDeploymentMode.UNKNOWN;

    public GaussDBDataSource(DBRProgressMonitor monitor, DBPDataSourceContainer container) throws DBException {
        super(monitor, container, new GaussDBDialect());
    }

    @Override
    public void initialize(@NotNull DBRProgressMonitor monitor) throws DBException {
        super.initialize(monitor);
        detectDeploymentMode(monitor);
    }

    /**
     * Detects whether the connected GaussDB instance is centralized or distributed.
     *
     * <p>Detection is performed in three escalating steps, each of which alone is sufficient:
     * <ol>
     *   <li><b>{@code pgxc_node} catalog + datanode count</b> — Distributed deployments store
     *       at least one row with {@code node_type = 'D'} (datanode). Centralized single-node
     *       deployments either lack the catalog entirely or have an empty {@code pgxc_node}.</li>
     *   <li><b>{@code pgxc_version()} function</b> — Available only in distributed GaussDB.
     *       A non-null return indicates a distributed cluster, regardless of {@code pgxc_node}
     *       row visibility (covers restricted-permission accounts on the coordinator).</li>
     *   <li><b>Fallback</b> — If both checks are inconclusive (catalog missing and
     *       {@code pgxc_version()} missing/unavailable), the instance is treated as centralized.</li>
     * </ol>
     *
     * <p>Permissions on {@code pgxc_node} are inconsistent across GaussDB versions and tenants;
     * relying solely on the datanode-row count would misclassify a distributed cluster whose
     * current role lacks read access. The {@code pgxc_version()} probe corrects for that.</p>
     */
    private void detectDeploymentMode(@NotNull DBRProgressMonitor monitor) {
        try (JDBCSession session = DBUtils.openMetaSession(monitor, this, "Detect GaussDB deployment mode")) {
            session.enableLogging(false);
            try {
                boolean pgxcNodeExists = PostgreUtils.isMetaObjectExists(session, GaussDBConstants.PGXC_NODE_CATALOG, "*");
                if (pgxcNodeExists) {
                    // Catalog exists. In centralized mode it is empty (only definitions can be queried).
                    // In distributed mode it contains at least one datanode row (node_type = 'D').
                    try {
                        long datanodeCount = JDBCUtils.queryLong(session,
                            "SELECT count(*) FROM pg_catalog." + GaussDBConstants.PGXC_NODE_CATALOG
                            + " WHERE node_type = '" + GaussDBConstants.PGXC_NODE_TYPE_DATANODE + "'");
                        if (datanodeCount > 0) {
                            deploymentMode = GaussDBDeploymentMode.DISTRIBUTED;
                            return;
                        }
                        // Zero datanodes so far — could still be a distributed cluster whose
                        // pgxc_node is restricted. Fall through to the pgxc_version probe.
                    } catch (SQLException e) {
                        log.debug("Error counting pgxc_node datanodes: " + e.getMessage()
                            + " — falling back to pgxc_version()");
                    }
                }
                // Either pgxc_node is missing, returned zero datanodes, or is restricted.
                // pgxc_version() is only defined on distributed GaussDB — a non-null result
                // confirms a distributed cluster despite any catalog access limitations.
                if (isPgxcVersionAvailable(session)) {
                    deploymentMode = GaussDBDeploymentMode.DISTRIBUTED;
                    return;
                }
                // All probes failed or returned nothing — single-node centralized deployment.
                deploymentMode = GaussDBDeploymentMode.CENTRALIZED;
            } catch (Exception e) {
                log.debug("Error detecting GaussDB deployment mode: " + e.getMessage());
                deploymentMode = GaussDBDeploymentMode.UNKNOWN;
            }
        } catch (DBException e) {
            log.debug("Error opening session for deployment mode detection: " + e.getMessage());
            deploymentMode = GaussDBDeploymentMode.UNKNOWN;
        }
    }

    /**
     * Probes the {@code pg_catalog.pgxc_version()} function to confirm a distributed deployment.
     *
     * <p>The function exists only on coordinator (CN) nodes of distributed GaussDB clusters.
     * A successful non-null return (or even a successful call returning NULL) confirms that
     * the binary is a distributed build. A function-not-found or other SQL error means the
     * function is not available — typically a centralized single-node deployment.</p>
     *
     * @return {@code true} if the function call succeeded (distributed cluster), {@code false} otherwise
     */
    private boolean isPgxcVersionAvailable(@NotNull JDBCSession session) {
        try {
            // SELECT pgxc_version() returns a text string on distributed GaussDB.
            // We do not need the result value — only whether the function is callable.
            JDBCUtils.queryString(session, "SELECT pg_catalog.pgxc_version()");
            return true;
        } catch (SQLException e) {
            log.debug("pgxc_version() unavailable: " + e.getMessage());
            return false;
        }
    }

    @NotNull
    @Override
    public GaussDBDatabase createDatabaseImpl(@NotNull DBRProgressMonitor monitor, ResultSet dbResult) throws DBException {
        return new GaussDBDatabase(monitor, this, dbResult);
    }

    @NotNull
    @Override
    public GaussDBDatabase createDatabaseImpl(@NotNull DBRProgressMonitor monitor, String name) throws DBException {
        return new GaussDBDatabase(monitor, this, name);
    }

    @NotNull
    @Override
    public GaussDBDatabase createDatabaseImpl(DBRProgressMonitor monitor, String name, PostgreRole owner, String templateName,
        PostgreTablespace tablespace, PostgreCharset encoding) throws DBException {
        return new GaussDBDatabase(monitor, this, name, owner, templateName, tablespace, encoding);
    }

    // True if we need multiple databases
    @Override
    protected boolean isReadDatabaseList(DBPConnectionConfiguration configuration) {
        // It is configurable by default
        return CommonUtils.getBoolean(configuration.getProviderProperty(PostgreConstants.PROP_SHOW_NON_DEFAULT_DB), true);
    }

    /**
     * Returns the detected GaussDB deployment mode.
     *
     * @return {@link GaussDBDeploymentMode#CENTRALIZED} for single-node,
     *         {@link GaussDBDeploymentMode#DISTRIBUTED} for cluster deployments,
     *         {@link GaussDBDeploymentMode#UNKNOWN} if detection failed.
     */
    @Property(viewable = true, order = 8)
    public GaussDBDeploymentMode getDeploymentMode() {
        return deploymentMode;
    }

    /**
     * Convenience flag: true when connected to a distributed GaussDB cluster.
     */
    public boolean isDistributed() {
        return deploymentMode.isDistributed();
    }

    @Override
    public boolean isServerVersionAtLeast(int major, int minor) {
        // GaussDB version strings differ from PostgreSQL (e.g. "GaussDB Kernel 503.1.0" or "openGauss 5.0.0").
        // Parse the real version from serverVersion instead of relying on the PG version parser.
        String version = getServerVersion();
        if (CommonUtils.isEmpty(version)) {
            return super.isServerVersionAtLeast(major, minor);
        }
        Matcher matcher = GAUSSDB_VERSION_PATTERN.matcher(version);
        if (matcher.find()) {
            try {
                int actualMajor = Integer.parseInt(matcher.group(1));
                int actualMinor = Integer.parseInt(matcher.group(2));
                if (actualMajor != major) {
                    return actualMajor > major;
                }
                return actualMinor >= minor;
            } catch (NumberFormatException e) {
                log.debug("Error parsing GaussDB version: " + version);
            }
        }
        // Fall back to PG version comparison
        return super.isServerVersionAtLeast(major, minor);
    }

    @Override
    public PostgreServerExtension getServerType() {
        if (serverExtension == null) {
            serverExtension = new PostgreServerGaussDB(this);
        }
        return serverExtension;
    }
}
