package org.jkiss.dbeaver.ext.gaussdb.ui.config;

import org.jkiss.dbeaver.Log;
import org.jkiss.dbeaver.ext.gaussdb.model.GaussDBProcedure;
import org.jkiss.dbeaver.ext.gaussdb.ui.views.CreateFunctionOrProcedurePage;
import org.jkiss.dbeaver.ext.postgresql.model.PostgreProcedureKind;
import org.jkiss.dbeaver.model.edit.DBECommandContext;
import org.jkiss.dbeaver.model.edit.DBEObjectConfigurator;
import org.jkiss.dbeaver.model.runtime.DBRProgressMonitor;
import org.jkiss.dbeaver.ui.UITask;

import java.util.Map;

/**
 * GaussDB procedure configurator
 */
public class GaussDBProcedureConfigurator implements DBEObjectConfigurator<GaussDBProcedure> {

    protected static final Log log        = Log.getLog(GaussDBProcedureConfigurator.class);

    public static boolean      isFunction = false;

    @Override
    public GaussDBProcedure configureObject(DBRProgressMonitor monitor,
                                            DBECommandContext commandContext,
                                            Object container,
                                            GaussDBProcedure newProcedure,
                                            Map<String, Object> options) {
        return new UITask<GaussDBProcedure>() {
            @Override
            protected GaussDBProcedure runTask() {
                CreateFunctionOrProcedurePage editPage = new CreateFunctionOrProcedurePage(monitor, newProcedure, isFunction);
                if (!editPage.edit()) {
                    return null;
                }
                newProcedure.setKind(PostgreProcedureKind.p);
                newProcedure.setName(editPage.getProcedureName());
                String procedure = "CREATE [OR REPLACE] PROCEDURE " + newProcedure.getFullQualifiedSignature()
                            + " ([ parameter [IN|OUT|INOUT] datatype[,parameter [IN|OUT|INOUT] datatype] ])\r\n" + "\r\n" + "AS\r\n"
                            + "\r\n" + "DECLARE\r\n" + "\r\n" + " /*declaration_section*/\r\n" + "\r\n" + "BEGIN\r\n" + "\r\n"
                            + " /*executable_section*/\r\n" + "\r\n" + "END;";
                newProcedure.setObjectDefinitionText(procedure);
                return newProcedure;
            }
        }.execute();
    }
}