/*
 * DBeaver - Universal Database Manager
 * Copyright (C) 2010-2019 Serge Rider (serge@jkiss.org)
 * Copyright (C) 2011-2012 Eugene Fradkin (eugene.fradkin@gmail.com)
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
package org.jkiss.dbeaver.ext.mysql.edit;

import org.jkiss.code.Nullable;
import org.jkiss.dbeaver.DBException;
import org.jkiss.dbeaver.ext.mysql.MySQLConstants;
import org.jkiss.dbeaver.ext.mysql.model.MySQLTableBase;
import org.jkiss.dbeaver.ext.mysql.model.MySQLTableColumn;
import org.jkiss.dbeaver.model.DBPDataKind;
import org.jkiss.dbeaver.model.DBPEvaluationContext;
import org.jkiss.dbeaver.model.DBUtils;
import org.jkiss.dbeaver.model.edit.DBECommandContext;
import org.jkiss.dbeaver.model.edit.DBEObjectRenamer;
import org.jkiss.dbeaver.model.edit.DBEObjectReorderer;
import org.jkiss.dbeaver.model.edit.DBEPersistAction;
import org.jkiss.dbeaver.model.impl.DBSObjectCache;
import org.jkiss.dbeaver.model.impl.edit.DBECommandAbstract;
import org.jkiss.dbeaver.model.impl.edit.SQLDatabasePersistAction;
import org.jkiss.dbeaver.model.impl.sql.edit.struct.SQLTableColumnManager;
import org.jkiss.dbeaver.model.runtime.DBRProgressMonitor;
import org.jkiss.dbeaver.model.sql.SQLUtils;
import org.jkiss.dbeaver.model.struct.DBSDataType;
import org.jkiss.dbeaver.model.struct.DBSObject;
import org.jkiss.dbeaver.ui.UITask;
import org.jkiss.dbeaver.ui.editors.object.struct.AttributeEditPage;
import org.jkiss.utils.CommonUtils;

import java.sql.Types;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * MySQL table column manager
 */
public class MySQLTableColumnManager extends SQLTableColumnManager<MySQLTableColumn, MySQLTableBase>
    implements DBEObjectRenamer<MySQLTableColumn>, DBEObjectReorderer<MySQLTableColumn>
{

    private final ColumnModifier<MySQLTableColumn> MySQLDataTypeModifier = (monitor, column, sql, command) ->
        sql.append(' ').append(column.getFullTypeName());

    private final ColumnModifier<MySQLTableColumn> CharsetModifier = (monitor, column, sql, command) -> {
        if (column.getDataKind() == DBPDataKind.STRING && column.getCharset() != null) {
            sql.append(" CHARACTER SET ").append(column.getCharset().getName()); //$NON-NLS-1$
        }
    };

    private final ColumnModifier<MySQLTableColumn> CollationModifier = (monitor, column, sql, command) -> {
        if (column.getDataKind() == DBPDataKind.STRING && column.getCollation() != null) {
            sql.append(" COLLATE ").append(column.getCollation().getName()); //$NON-NLS-1$
        }
    };

    private final ColumnModifier<MySQLTableColumn> ExtraInfoModifier = (monitor, column, sql, command) -> {
        if (!CommonUtils.isEmpty(column.getExtraInfo())) {
            if (MySQLConstants.EXTRA_INFO_VIRTUAL_GENERATED.equalsIgnoreCase(column.getExtraInfo())) {
                if (!CommonUtils.isEmpty(column.getGenExpression())) {
                    sql.append(" GENERATED ALWAYS AS (").append(column.getGenExpression()).append(") VIRTUAL"); //$NON-NLS-1$ //$NON-NLS-2$
                } else {
                    log.debug("No virtual column generate expression found for " + column.getName());
                }
            } else {
                sql.append(" ").append(column.getExtraInfo()); //$NON-NLS-1$
            }
        }
    };

    @Nullable
    @Override
    public DBSObjectCache<? extends DBSObject, MySQLTableColumn> getObjectsCache(MySQLTableColumn object)
    {
        return object.getParentObject().getContainer().getTableCache().getChildrenCache(object.getParentObject());
    }

    protected ColumnModifier[] getSupportedModifiers(MySQLTableColumn column, Map<String, Object> options)
    {
        return new ColumnModifier[] {MySQLDataTypeModifier, CharsetModifier, CollationModifier, DefaultModifier, ExtraInfoModifier, NullNotNullModifier};
    }

    @Override
    public StringBuilder getNestedDeclaration(DBRProgressMonitor monitor, MySQLTableBase owner, DBECommandAbstract<MySQLTableColumn> command, Map<String, Object> options)
    {
        StringBuilder decl = super.getNestedDeclaration(monitor, owner, command, options);
        final MySQLTableColumn column = command.getObject();
        if (column.isAutoGenerated() &&
            (CommonUtils.isEmpty(column.getExtraInfo()) || !column.getExtraInfo().toLowerCase(Locale.ENGLISH).contains(MySQLConstants.EXTRA_AUTO_INCREMENT)))
        {
            decl.append(" AUTO_INCREMENT"); //$NON-NLS-1$
        }
        if (!CommonUtils.isEmpty(column.getComment())) {
            decl.append(" COMMENT ").append(SQLUtils.quoteString(column, column.getComment())); //$NON-NLS-1$
        }
        return decl;
    }

    @Override
    protected MySQLTableColumn createDatabaseObject(final DBRProgressMonitor monitor, final DBECommandContext context, final MySQLTableBase parent, Object copyFrom)
    {
        return new UITask<MySQLTableColumn>() {
            @Override
            protected MySQLTableColumn runTask() {
                DBSDataType columnType = findBestDataType(parent.getDataSource(), "varchar"); //$NON-NLS-1$

                final MySQLTableColumn column = new MySQLTableColumn(parent);
                column.setName(getNewColumnName(monitor, context, parent));
                final String typeName = columnType == null ? "integer" : columnType.getName().toLowerCase();
                column.setTypeName(typeName); //$NON-NLS-1$
                column.setMaxLength(columnType != null && columnType.getDataKind() == DBPDataKind.STRING ? 100 : 0);
                column.setValueType(columnType == null ? Types.INTEGER : columnType.getTypeID());
                column.setOrdinalPosition(parent.getCachedAttributes().size() + 1);
                column.setFullTypeName(DBUtils.getFullTypeName(column));
                AttributeEditPage page = new AttributeEditPage(null, column);
                if (!page.edit()) {
                    return null;
                }
                return column;
            }
        }.execute();
    }

    @Override
    protected void addObjectModifyActions(DBRProgressMonitor monitor, List<DBEPersistAction> actionList, ObjectChangeCommand command, Map<String, Object> options)
    {
        final MySQLTableColumn column = command.getObject();

        actionList.add(
            new SQLDatabasePersistAction(
                "Modify column",
                "ALTER TABLE " + column.getTable().getFullyQualifiedName(DBPEvaluationContext.DDL) + " MODIFY COLUMN " + getNestedDeclaration(monitor, column.getTable(), command, options))); //$NON-NLS-1$ //$NON-NLS-2$
    }

    @Override
    public void renameObject(DBECommandContext commandContext, MySQLTableColumn object, String newName) throws DBException {
        processObjectRename(commandContext, object, newName);
    }

    @Override
    protected void addObjectRenameActions(DBRProgressMonitor monitor, List<DBEPersistAction> actions, ObjectRenameCommand command, Map<String, Object> options)
    {
        final MySQLTableColumn column = command.getObject();

        actions.add(
            new SQLDatabasePersistAction(
                "Rename column",
                "ALTER TABLE " + column.getTable().getFullyQualifiedName(DBPEvaluationContext.DDL) + " CHANGE " +
                    DBUtils.getQuotedIdentifier(column.getDataSource(), command.getOldName()) + " " +
                    getNestedDeclaration(monitor, column.getTable(), command, options)));
    }

    @Override
    protected void addObjectReorderActions(DBRProgressMonitor monitor, List<DBEPersistAction> actions, ObjectReorderCommand command, Map<String, Object> options) {
        final MySQLTableColumn column = command.getObject();
        String order = "FIRST";
        if (column.getOrdinalPosition() > 0) {
            for (MySQLTableColumn col : command.getObject().getTable().getCachedAttributes()) {
                if (col.getOrdinalPosition() == column.getOrdinalPosition() - 1) {
                    order = "AFTER " + DBUtils.getQuotedIdentifier(col);
                    break;
                }
            }
        }
        actions.add(
            new SQLDatabasePersistAction(
                "Reorder column",
                "ALTER TABLE " + column.getTable().getFullyQualifiedName(DBPEvaluationContext.DDL) + " CHANGE " +
                    DBUtils.getQuotedIdentifier(command.getObject()) + " " +
                    getNestedDeclaration(monitor, column.getTable(), command, options) + " " + order));
    }

    ///////////////////////////////////////////////
    // Reorder

    @Override
    public int getMinimumOrdinalPosition(MySQLTableColumn object) {
        return 1;
    }

    @Override
    public int getMaximumOrdinalPosition(MySQLTableColumn object) {
        return object.getTable().getCachedAttributes().size();
    }

    @Override
    public void setObjectOrdinalPosition(DBECommandContext commandContext, MySQLTableColumn object, List<MySQLTableColumn> siblingObjects, int newPosition) throws DBException {
        processObjectReorder(commandContext, object, siblingObjects, newPosition);
    }
}
