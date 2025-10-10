
package com.peyrona.mingle.cil.drivers;

import com.eclipsesource.json.JsonObject;
import com.peyrona.mingle.lang.japi.CommandSerializer;
import com.peyrona.mingle.lang.interfaces.commands.ICmdKeys;
import com.peyrona.mingle.lang.interfaces.commands.IDriver;

/**
 *
 * @author Francisco José Morero Peyrona
 *
 * Official web site at: <a href="https://github.com/peyrona/mingle">https://github.com/peyrona/mingle</a>
 */
public final class DriverBuilder
{
    public static IDriver build( JsonObject jo )
    {
        return new Driver( jo.get( ICmdKeys.CMD_NAME      ).asString(),
                           jo.get( ICmdKeys.DRIVER_SCRIPT ).asString() );
    }

    public static String unbuild( IDriver driver )
    {
        return CommandSerializer.Driver( driver.name(),
                                         driver.getScriptName() );
    }
}