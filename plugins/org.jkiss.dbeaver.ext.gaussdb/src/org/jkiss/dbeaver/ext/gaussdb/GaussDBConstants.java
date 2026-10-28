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
package org.jkiss.dbeaver.ext.gaussdb;

public class GaussDBConstants {
    public static final String GAUSSDB_M_COMPATIBLE_MODE = "M";

    // Deployment mode identifiers
    public static final String GAUSSDB_DEPLOY_CENTRALIZED = "centralized";
    public static final String GAUSSDB_DEPLOY_DISTRIBUTED = "distributed";

    // Distributed system catalog tables
    public static final String PGXC_NODE_CATALOG = "pgxc_node";
    public static final String PGXC_CLASS_CATALOG = "pgxc_class";

    // pgxc_node node_type values
    public static final String PGXC_NODE_TYPE_COORDINATOR = "C";
    public static final String PGXC_NODE_TYPE_DATANODE = "D";
    public static final String PGXC_NODE_TYPE_STANDBY = "S";

    // Default connection port for GaussDB
    public static final int DEFAULT_PORT_GAUSSDB = 8000;

    // pgxc_class pclocatortype values (the distribution/locator type)
    // Reference: PGXC_CLASS system catalog documentation
    public static final String PGXC_LOCATOR_TYPE_HASH = "H";          // Hash distribution
    public static final String PGXC_LOCATOR_TYPE_RANGE = "G";         // Range distribution
    public static final String PGXC_LOCATOR_TYPE_LIST = "L";          // List distribution
    public static final String PGXC_LOCATOR_TYPE_MODULO = "M";        // Modulo distribution
    public static final String PGXC_LOCATOR_TYPE_ROUNDROBIN = "N";    // Round Robin distribution
    public static final String PGXC_LOCATOR_TYPE_REPLICATION = "R";   // Replication (full copy on each DN)
    public static final String PGXC_LOCATOR_TYPE_MURMURHASH = "U";    // MurmurHash distribution
}
