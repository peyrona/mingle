
package com.peyrona.mingle.cil.scripts;

import com.eclipsesource.json.JsonObject;
import com.peyrona.mingle.lang.interfaces.commands.ICmdKeys;
import com.peyrona.mingle.lang.interfaces.commands.IScript;
import com.peyrona.mingle.lang.japi.CommandSerializer;
import com.peyrona.mingle.lang.japi.UtilJson;
import com.peyrona.mingle.lang.japi.UtilType;

/**
 *
 * @author Francisco José Morero Peyrona
 *
 * Official web site at: <a href="https://mingle.peyrona.com">https://mingle.peyrona.com</a>
 */
public final class ScriptBuilder
{
    public static IScript build( JsonObject jo )
    {
        UtilJson json  = new UtilJson( jo );
        String[] aFrom = UtilType.convertArray( UtilJson.toArray( json.getArray( ICmdKeys.SCRIPT_FROM ) ), String.class );

        return new Script( json.getString(  ICmdKeys.CMD_NAME        ),
                           json.getString(  ICmdKeys.SCRIPT_LANGUAGE ),
                           json.getBoolean( ICmdKeys.SCRIPT_ONSTART  ),
                           json.getBoolean( ICmdKeys.SCRIPT_ONSTOP   ),
                           json.getBoolean( ICmdKeys.SCRIPT_INLINE   ),
                           aFrom,
                           json.getString(  ICmdKeys.SCRIPT_CALL ) );
    }

    public static String unbuild( IScript script )
    {
        return CommandSerializer.Script( script.name(),
                                         script.getLanguage(),
                                         script.isOnStart(),
                                         script.isOnStop(),
                                         script.isInline(),
                                         script.getFrom(),
                                         script.getCall() );
    }

    //------------------------------------------------------------------------//
    // PRIVATE SCOPE

    private ScriptBuilder()
    {
        // Avoids creating instances of this class
    }
}