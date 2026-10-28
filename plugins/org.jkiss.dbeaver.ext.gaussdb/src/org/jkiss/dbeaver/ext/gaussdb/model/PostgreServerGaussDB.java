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
import org.jkiss.code.Nullable;
import org.jkiss.dbeaver.DBException;
import org.jkiss.dbeaver.Log;
import org.jkiss.dbeaver.ext.postgresql.model.PostgreDataSource;
import org.jkiss.dbeaver.ext.postgresql.model.PostgreDatabase;
import org.jkiss.dbeaver.ext.postgresql.model.PostgreTableBase;
import org.jkiss.dbeaver.ext.postgresql.model.impls.PostgreServerExtensionBase;
import org.jkiss.dbeaver.model.DBUtils;
import org.jkiss.dbeaver.model.exec.jdbc.JDBCPreparedStatement;
import org.jkiss.dbeaver.model.exec.jdbc.JDBCResultSet;
import org.jkiss.dbeaver.model.exec.jdbc.JDBCSession;
import org.jkiss.dbeaver.model.impl.jdbc.JDBCUtils;
import org.jkiss.dbeaver.model.runtime.DBRProgressMonitor;
import org.jkiss.utils.CommonUtils;

import java.sql.SQLException;

/**
 * GaussDB server extension.
 *
 * <p>Adapts the PostgreSQL server extension for GaussDB-specific behavior:
 * <ul>
 *   <li>Disables native client (pg_dump/pg_restore) and PG extensions support.</li>
 *   <li>Enables stored procedures and materialized views unconditionally.</li>
 *   <li>In distributed deployments, {@link #readTableDDL} prefers GaussDB's native
 *       {@code pg_get_tabledef()} (which returns the complete DDL including the
 *       {@code DISTRIBUTE BY} clause) and falls back to {@code null} so the default
 *       DDL builder is used. Distribution metadata is never returned as a "complete"
 *       DDL — that would corrupt the CREATE TABLE statement emitted by
 *       {@code PostgreTableManager.addStructObjectCreateActions}.</li>
 * </ul>
 */
public class PostgreServerGaussDB extends PostgreServerExtensionBase {

    private static final Log log = Log.getLog(PostgreServerGaussDB.class);

    private boolean supportJobs;

    protected PostgreServerGaussDB(PostgreDataSource dataSource) {
        super(dataSource);
        this.supportJobs = false;
    }

    @NotNull
    @Override
    public String getServerTypeName() {
        return "GaussDB";
    }

    @Override
    public boolean supportsNativeClient() {
        // GaussDB does not support pg_dump/pg_restore
        return false;
    }

    public boolean isSupportJobs() {
        return supportJobs;
    }

    public void setSupportJobs(boolean supportJobs) {
        this.supportJobs = supportJobs;
    }

    @Override
    public boolean supportsExtensions() {
        // GaussDB does not use PG extensions
        return false;
    }

    @Override
    public boolean supportsStoredProcedures() {
        return true;
    }

    @Override
    public boolean supportsMaterializedViews() {
        return true;
    }

    @Override
    public boolean supportsForeignServers() {
        // Foreign servers (postgres_fdw / foreign tables) are not supported in distributed mode
        if (isDistributed()) {
            return false;
        }
        return super.supportsForeignServers();
    }

    @Override
    public boolean supportsEventTriggers() {
        // GaussDB does not support event triggers
        return false;
    }

    @Override
    public boolean supportsRowLevelSecurity() {
        // GaussDB supports row-level security policies
        return true;
    }

    @Override
    public boolean supportsRoleReplication() {
        // GaussDB does not expose the PG replication role attribute
        return false;
    }

    @Override
    public boolean supportsRoleBypassRLS() {
        // GaussDB does not expose the PG BYPASSRLS role attribute
        return false;
    }

    @Override
    public boolean supportsCommentsOnRole() {
        // GaussDB does not support COMMENT ON ROLE
        return false;
    }

    @Override
    public boolean supportsDefaultPrivileges() {
        // GaussDB does not support ALTER DEFAULT PRIVILEGES
        return false;
    }

    @Override
    public boolean supportsPartitions() {
        // GaussDB supports declarative partitioning (and range/list/hash distribution in distributed mode)
        return true;
    }

    @Override
    public boolean supportsGeneratedColumns() {
        // GaussDB supports GENERATED ALWAYS AS (...) STORED generated columns
        return true;
    }

    @Override
    public boolean supportsDisablingAllTriggers() {
        // GaussDB supports ALTER TABLE ... DISABLE TRIGGER ALL (needed by data transfer)
        return true;
    }

    @Override
    public boolean supportsKeyAndIndexRename() {
        // GaussDB supports ALTER TABLE ... RENAME CONSTRAINT (separate from index rename)
        return true;
    }

    @Override
    public boolean supportsAlterStorageStrategy() {
        // GaussDB supports ALTER COLUMN ... SET STORAGE {PLAIN|EXTERNAL|EXTENDED|MAIN}
        return true;
    }

    @Override
    public boolean supportsAlterUserChangePassword() {
        // GaussDB supports ALTER USER ... PASSWORD '...' (used by the change-password UI)
        return true;
    }

    @Override
    public boolean supportsBackslashStringEscape() {
        // GaussDB enforces standard_conforming_strings = on by default — backslash is literal
        return false;
    }

    /**
     * Reads the native table DDL from GaussDB.
     *
     * <p>The caller ({@code PostgreTableManager.addStructObjectCreateActions}) treats a non-null
     * return value as the <b>complete</b> {@code CREATE TABLE} statement. We therefore only return
     * a non-null value when {@code pg_get_tabledef()} is available and yields a full definition.
     * A bare {@code DISTRIBUTE BY ...} clause is never returned here — emitting it as the table's
     * DDL would replace the {@code CREATE TABLE} statement with a fragment that the server cannot
     * execute on its own. When {@code pg_get_tabledef()} is unavailable, {@code null} is returned
     * so the framework's default DDL builder is used.</p>
     */
    @Nullable
    @Override
    public String readTableDDL(@NotNull DBRProgressMonitor monitor, @NotNull PostgreTableBase table) throws DBException {
        if (!isDistributed()) {
            // In centralized mode, fall back to the default DDL builder.
            return null;
        }
        // In distributed mode, prefer the native pg_get_tabledef() which returns the full DDL
        // (including the DISTRIBUTE BY clause). If unavailable, fall back to the default builder.
        return readNativeTableDef(monitor, table);
    }

    /**
     * Reads the full table definition via GaussDB's {@code pg_get_tabledef()} function.
     *
     * <p>The function returns a complete {@code CREATE TABLE ... DISTRIBUTE BY ...} statement for
     * distributed tables. If the function is not available on this server (older GaussDB/openGauss
     * builds) or returns an empty result, {@code null} is returned so the caller falls back to the
     * default DDL builder.</p>
     *
     * @return the complete native DDL string, or {@code null} if it cannot be obtained
     */
    @Nullable
    private String readNativeTableDef(@NotNull DBRProgressMonitor monitor, @NotNull PostgreTableBase table)
        throws DBException {
        try (JDBCSession session = DBUtils.openMetaSession(monitor, table.getDataSource(), "Read GaussDB table DDL")) {
            // pg_get_tabledef() returns the full CREATE TABLE statement with DISTRIBUTE BY clause
            // attached. It is the only safe source for a complete distributed-table DDL.
            String sql = "SELECT pg_catalog.pg_get_tabledef(?) AS tabledef";
            try (JDBCPreparedStatement dbStat = session.prepareStatement(sql)) {
                dbStat.setLong(1, table.getObjectId());
                try (JDBCResultSet rs = dbStat.executeQuery()) {
                    if (rs.nextRow()) {
                        String tableDef = JDBCUtils.safeGetString(rs, "tabledef");
                        if (!CommonUtils.isEmpty(tableDef)) {
                            return tableDef;
                        }
                    }
                }
            } catch (SQLException e) {
                // pg_get_tabledef may not exist on this server version — fall back silently
                log.debug("pg_get_tabledef unavailable or failed for table " + table.getName() + ": " + e.getMessage());
            }
        }
        return null;
    }

    @NotNull
    @Override
    public PostgreDatabase.SchemaCache createSchemaCache(@NotNull PostgreDatabase database) {
        return new GaussDBSchemaCache();
    }
}
