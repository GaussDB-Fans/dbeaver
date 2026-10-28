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

import org.jkiss.dbeaver.ext.postgresql.model.PostgreDialect;

/**
 * GaussDB SQL dialect.
 *
 * <p>Extends the PostgreSQL dialect with GaussDB-specific syntax differences.
 * Only the methods whose behavior actually differs from {@link PostgreDialect}'s
 * defaults are overridden here — redundant overrides have been removed to keep
 * behavior consistent with the parent class.</p>
 *
 * <ul>
 *   <li>{@link #isDelimiterAfterBlock()} returns {@code true} — GaussDB requires
 *       the statement delimiter after the block terminator in procedure/function DDL,
 *       whereas the base PostgreSQL dialect defaults to {@code false}.</li>
 *   <li>{@link #supportsColumnAutoIncrement()} returns {@code true} — GaussDB
 *       supports both {@code SERIAL}/{@code BIGSERIAL} and SQL-standard
 *       {@code GENERATED ... AS IDENTITY} columns; the parent returns {@code false}.</li>
 * </ul>
 *
 * <p>Other PostgreSQL-compatible behaviors (nested block comments, DROP ... CASCADE,
 * backslash escaping controlled by {@code standard_conforming_strings}) are inherited
 * unchanged from {@link PostgreDialect}. In particular, {@link #isEscapeBackslash()}
 * already delegates to {@code serverExtension.supportsBackslashStringEscape()}, so
 * the dialect does not need to re-implement it.</p>
 */
public class GaussDBDialect extends PostgreDialect {

    @Override
    public boolean isDelimiterAfterBlock() {
        // GaussDB requires a statement delimiter after the block terminator ('/')
        // in CREATE PROCEDURE / CREATE FUNCTION DDL, matching Oracle-style PL/SQL blocks.
        return true;
    }

    @Override
    public boolean supportsColumnAutoIncrement() {
        // GaussDB supports both SERIAL/BIGSERIAL and SQL-standard GENERATED AS IDENTITY.
        return true;
    }
}
