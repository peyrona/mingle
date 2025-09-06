
package com.peyrona.mingle.cil.rules;

import com.eclipsesource.json.JsonArray;
import com.eclipsesource.json.JsonObject;
import com.peyrona.mingle.lang.japi.CommandSerializer;
import com.peyrona.mingle.lang.interfaces.commands.ICmdKeys;
import com.peyrona.mingle.lang.interfaces.commands.IRule;
import com.peyrona.mingle.lang.interfaces.commands.IRule.IAction;
import com.peyrona.mingle.lang.japi.UtilStr;
import com.peyrona.mingle.lang.japi.UtilType;
import java.util.ArrayList;
import java.util.List;

/**
 * This class builds (deserializes) and Unbuild (serializes) a RULE command, with
 * an expression as its WHEN clause and a set of IActions for its THEN clause.
 *
 * @author Francisco José Morero Peyrona
 *
 * Official web site at: <a href="https://mingle.peyrona.com">https://mingle.peyrona.com</a>
 */
public final class RuleBuilder
{
    public static IRule build( JsonObject jo )
    {
        JsonArray jaAct = jo.get( ICmdKeys.RULE_THEN ).asArray();
        IAction[] aoAct = new Action[ jaAct.size() ];

        for( int n = 0; n < aoAct.length; n++ )
        {
            JsonObject j = jaAct.get( n ).asObject();

            aoAct[n] = new Action( j.get( ICmdKeys.RULE_THEN_AFTER  ).asLong(),
                                   j.get( ICmdKeys.RULE_THEN_TARGET ).asString(),
                                   UtilType.toUne( j.get(ICmdKeys.RULE_THEN_VALUE ) ) );
        }

        String sName = (jo.get( ICmdKeys.CMD_NAME ).isNull() ? null : jo.get( ICmdKeys.CMD_NAME ).asString());
        String sIf   = (jo.get( ICmdKeys.RULE_IF  ).isNull() ? null : jo.get( ICmdKeys.RULE_IF  ).asString());

        return new Rule( jo.get( ICmdKeys.RULE_WHEN ).asString(),
                         sIf,
                         (Action[]) aoAct,
                         sName );
    }

    public static String unbuild( IRule rule )
    {
        List<String> lstThen = new ArrayList<>();

        for( IAction action : rule.getActions() )
        {
            lstThen.add(CommandSerializer.RuleAction( action.getDelay(),
                                                       action.getTarget(),
                                                       action.getValueToSet() ) );
        }

        return CommandSerializer.Rule( rule.name(),
                                       rule.getWhen(),
                                       (UtilStr.isEmpty( rule.getIf() ) ? null : rule.getIf()),
                                       lstThen.toArray( new String[0] ) );
    }

    //------------------------------------------------------------------------//
    // PRIVATE SCOPE

    private RuleBuilder()
    {
        // Avoids creating instances of this class
    }
}