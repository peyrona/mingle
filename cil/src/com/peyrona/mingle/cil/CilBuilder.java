
package com.peyrona.mingle.cil;

import com.eclipsesource.json.Json;
import com.eclipsesource.json.JsonObject;
import com.peyrona.mingle.cil.devices.Device;
import com.peyrona.mingle.cil.devices.DeviceBuilder;
import com.peyrona.mingle.cil.drivers.Driver;
import com.peyrona.mingle.cil.drivers.DriverBuilder;
import com.peyrona.mingle.cil.rules.Rule;
import com.peyrona.mingle.cil.rules.RuleBuilder;
import com.peyrona.mingle.cil.scripts.Script;
import com.peyrona.mingle.cil.scripts.ScriptBuilder;
import com.peyrona.mingle.lang.interfaces.ICmdEncDecLib;
import com.peyrona.mingle.lang.interfaces.commands.ICmdKeys;
import com.peyrona.mingle.lang.interfaces.commands.ICommand;
import com.peyrona.mingle.lang.interfaces.commands.IDevice;
import com.peyrona.mingle.lang.interfaces.commands.IDriver;
import com.peyrona.mingle.lang.interfaces.commands.IRule;
import com.peyrona.mingle.lang.interfaces.commands.IScript;

/**
 * Serializes and deserializes instances of ICommand.
 * <p>
 * As Mingle transpiler writes to JSON, this implementation uses same JSON format to store the IComands.<br>
 * This is more natural and makes easier writing the unbuild(...) because it just invokes the Serializer
 * class (which is used by the transpiler to serialize the ICommand standard representations).
 *
 * @author Francisco José Morero Peyrona
 *
 * Official web site at: <a href="https://mingle.peyrona.com">https://mingle.peyrona.com</a>
 */
public class CilBuilder implements ICmdEncDecLib
{
    @Override
    public ICommand build( String sJSON )
    {
        JsonObject jo  = Json.parse( sJSON ).asObject();
        String     cmd = jo.get( ICmdKeys.CMD_CMD ).asString();      // script | driver | device | rule

        switch( cmd )
        {
            case ICmdKeys.CMD_DEVICE : return DeviceBuilder.build( jo );
            case ICmdKeys.CMD_SCRIPT : return ScriptBuilder.build( jo );
            case ICmdKeys.CMD_DRIVER : return DriverBuilder.build( jo );
            case ICmdKeys.CMD_RULE   : return RuleBuilder.build(   jo );
        }

        return null;    // Could be INCLUDE or USE
    }

    @Override
    public String unbuild( ICommand cmd )
    {
             if( cmd instanceof IScript ) return ScriptBuilder.unbuild( (Script) cmd );
        else if( cmd instanceof IDriver ) return DriverBuilder.unbuild( (Driver) cmd );
        else if( cmd instanceof IDevice ) return DeviceBuilder.unbuild( (Device) cmd );
        else if( cmd instanceof IRule   ) return RuleBuilder.unbuild(   (Rule)   cmd );

        return null;    // Could be INCLUDE or USE
    }

    @Override
    public Object checkProperty( String sPropertyName, Object oValue )
    {
        switch( sPropertyName.toLowerCase() )
        {
            case "groups"  : return (oValue instanceof String) ? Boolean.TRUE : String.class;
            case "delta"   : return (oValue instanceof Number) ? Boolean.TRUE : Number.class;
            case "downtime": return (oValue instanceof Number) ? Boolean.TRUE : Number.class;
            case "value"   : return Boolean.TRUE;     // Any type is accepted
            default        : return Boolean.FALSE;
        }
    }

    @Override
    public String about()
    {
        return "MSP Command Incarnation Library v.1.2 (the default one for the MSP)";
    }

    @Override
    public String toString()
    {
        return about();
    }
}