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

import org.jkiss.dbeaver.ext.gaussdb.GaussDBConstants;
import org.jkiss.utils.CommonUtils;

/**
 * GaussDB deployment mode: centralized (single-node) or distributed (sharded cluster).
 *
 * <p>Detection is based on the existence of the {@code pgxc_node} system catalog,
 * which only exists in distributed (GaussDB Distributed / openGauss distributed) deployments.</p>
 */
public enum GaussDBDeploymentMode {

    /** Single-node centralized deployment. */
    CENTRALIZED(GaussDBConstants.GAUSSDB_DEPLOY_CENTRALIZED),

    /** Distributed cluster deployment with CN/DN nodes. */
    DISTRIBUTED(GaussDBConstants.GAUSSDB_DEPLOY_DISTRIBUTED),

    /** Deployment mode could not be determined. */
    UNKNOWN("unknown");

    private final String value;

    GaussDBDeploymentMode(String value) {
        this.value = value;
    }

    public String getValue() {
        return value;
    }

    /**
     * Resolve a {@link GaussDBDeploymentMode} from its string identifier.
     *
     * @param value the mode string (e.g. "centralized" or "distributed")
     * @return the matching mode, or {@link #UNKNOWN} if the value is unrecognized
     */
    public static GaussDBDeploymentMode fromValue(String value) {
        if (CommonUtils.isEmpty(value)) {
            return UNKNOWN;
        }
        for (GaussDBDeploymentMode mode : values()) {
            if (mode.value.equalsIgnoreCase(value)) {
                return mode;
            }
        }
        return UNKNOWN;
    }

    public boolean isDistributed() {
        return this == DISTRIBUTED;
    }

    public boolean isCentralized() {
        return this == CENTRALIZED;
    }
}
