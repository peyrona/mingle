
package com.peyrona.mingle.cil.devices;

import com.eclipsesource.json.JsonObject;
import com.eclipsesource.json.JsonValue;
import com.peyrona.mingle.lang.japi.CommandSerializer;
import com.peyrona.mingle.lang.interfaces.commands.ICmdKeys;
import com.peyrona.mingle.lang.interfaces.commands.IDevice;
import com.peyrona.mingle.lang.japi.UtilType;
import java.util.HashMap;
import java.util.Map;

/**
 *
 * @author Francisco José Morero Peyrona
 *
 * Official web site at: <a href="https://mingle.peyrona.com">https://mingle.peyrona.com</a>
 */
public final class DeviceBuilder
{
    public static IDevice build( JsonObject jo )
    {
        final Map<String,Object> mapDrvInit = new HashMap<>();    // Driver CONFIG
        final Map<String,Object> mapDevInit = new HashMap<>();    // Device INIT

        populate( jo.get( ICmdKeys.DRIVER_INIT ), mapDrvInit );   // Driver Init (config)

        populate( jo.get( ICmdKeys.DEVICE_INIT ), mapDevInit );   // Device INIT

        String deviceName = jo.getString( ICmdKeys.CMD_NAME   , null );
        String driverName = jo.getString( ICmdKeys.DRIVER_NAME, null );

        return new Device( deviceName, mapDevInit, driverName, mapDrvInit );
    }

    public static String unbuild( IDevice device )
    {
        return CommandSerializer.Device( device.name(),
                                         device.getDriverName(),
                                         device.getDriverInit(),
                                         device.getDeviceInit() );
    }

    //------------------------------------------------------------------------//
    // PRIVATE SCOPE

    private DeviceBuilder()
    {
        // Avoids creating instances of this class
    }

    private static void populate( JsonValue jv, Map<String,Object> map )
    {
        if( ! jv.isNull() )           // Driver Init (config)
        {
            jv.asObject()
              .forEach( member -> map.put( member.getName(), UtilType.toUne( member.getValue() ) ) );
        }
    }
}