/*
 * Copyright (C) 2013      Denis Forveille titou10.titou10@gmail.com
 * Copyright (C) 2010-2013 Serge Rieder serge@jkiss.org
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2.1 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA
 */
package org.jkiss.dbeaver.ext.db2.edit;

import org.eclipse.ui.IWorkbenchWindow;
import org.jkiss.dbeaver.ext.IDatabasePersistAction;
import org.jkiss.dbeaver.ext.db2.model.DB2Schema;
import org.jkiss.dbeaver.ext.db2.model.DB2Trigger;
import org.jkiss.dbeaver.model.edit.DBECommandContext;
import org.jkiss.dbeaver.model.impl.DBSObjectCache;
import org.jkiss.dbeaver.model.impl.edit.AbstractDatabasePersistAction;
import org.jkiss.dbeaver.model.impl.jdbc.edit.struct.JDBCObjectEditor;
import org.jkiss.dbeaver.model.struct.DBSObject;

/**
 * DB2 Trigger Manager
 * 
 * @author Denis Forveille
 */
public class DB2TriggerManager extends JDBCObjectEditor<DB2Trigger, DB2Schema> {

    private static final String SQL_DROP_TRIGGER = "DROP TRIGGER %s";

    @Override
    public long getMakerOptions()
    {
        return FEATURE_CREATE_UNSUPPORTED;
    }

    @Override
    protected IDatabasePersistAction[] makeObjectDeleteActions(ObjectDeleteCommand command)
    {
        String triggerName = command.getObject().getFullQualifiedName();
        IDatabasePersistAction action = new AbstractDatabasePersistAction("Drop trigger", String.format(SQL_DROP_TRIGGER,
            triggerName));
        return new IDatabasePersistAction[] { action };
    }

    @Override
    public DBSObjectCache<? extends DBSObject, DB2Trigger> getObjectsCache(DB2Trigger db2Trigger)
    {
        return db2Trigger.getSchema().getTriggerCache();
    }

    @Override
    protected DB2Trigger createDatabaseObject(IWorkbenchWindow workbenchWindow, DBECommandContext context, DB2Schema db2Schema,
        Object copyFrom)
    {
        return null;
    }

    @Override
    protected IDatabasePersistAction[] makeObjectCreateActions(ObjectCreateCommand command)
    {
        return null;
    }

}
