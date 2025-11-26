
package com.peyrona.mingle.cil.devices;

import com.peyrona.mingle.cil.Command;
import com.peyrona.mingle.lang.MingleException;
import com.peyrona.mingle.lang.interfaces.ILogger;
import com.peyrona.mingle.lang.interfaces.commands.IDevice;
import com.peyrona.mingle.lang.interfaces.exen.IRuntime;
import com.peyrona.mingle.lang.japi.UtilColls;
import com.peyrona.mingle.lang.messages.MsgChangeActuator;
import com.peyrona.mingle.lang.messages.MsgReadDevice;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * A Device can be either a sensor or an actuator.<br>
 * <br>
 * After DEVICE is added to its controller and it has a bus to send messages to, we can init
 * the DEVICE get.<br>
 * <br>
 * Devices can be dynamically added and removed: this is something that has to be done carefully:
 * adding dynamically a device does not passes all the "controls" that are passed by the
 * transpiler; e.g.: time units can not be used: time has to be expressed in milliseconds.
 * <p>
 * On the other hand, adding devices that belongs to an existing group will not affect to
 * Rules (these changes will be ignored by rules) that were defined as using this group. And
 * removing a device that is used by a Rule will eventually end with an exception. In other
 * words: groups are not dynamically (on-the-fly) updated.
 * <br>
 * Developers that add Devices at runtime do it at their own risk (all checks are done
 * at transpiler time): a big power comes with a big responsibility.
 *
 * @author Francisco José Morero Peyrona
 *
 * Official web site at: <a href="https://github.com/peyrona/mingle">https://github.com/peyrona/mingle</a>
 */
public class Device
       extends Command
       implements IDevice
{
    private final Map<String,Object> deviceInit;
    private final String[]           groups;
    private final DeviceValue          value;
    private final Map<String,Object> driverInit;
    private final String             driverName;
    private final long               downtime;

    //------------------------------------------------------------------------//
    // PACKAGE SCOPE CONSTRUCTOR

    /**
     * Constructor.<br>
     * <br>
     * Developers that add Devices at runtime do it at their own risk (all checks are done
     * at transpiler time): a big power comes with a big responsibility.
     *
     * @param deviceName Device's unique name.
     * @param driverName
     * @param driverInit Driver's configuration for this device.
     * @param deviceInit Device's configuration.
     */
    Device( String deviceName, Map<String,Object> deviceInit,
            String driverName, Map<String,Object> driverInit )
    {
        super( deviceName );     // A DEVICE can be created from an SCRIPT and can be done on-the-fly

        // Note: properties (delta, groups, value and downtime) are checked by com.peyrona.mingle.cil.CilBuilder

        // delta has to be managed at this level, so different implementations
        // can behave different approaches: even ignoring delta.

        Float delta = 0f;

        if( (deviceInit != null) && deviceInit.containsKey( "delta" ) )   // Do not move this if to DeviceValue class
        {
            delta = ((Number) deviceInit.get( "delta" )).floatValue();
            delta = Math.abs( delta );
        }

        this.value      = new DeviceValue( delta );
        this.driverName = driverName;
        this.driverInit = UtilColls.isEmpty( driverInit ) ? null : Collections.unmodifiableMap( driverInit );    // Acts as a defensive copy
        this.deviceInit = UtilColls.isEmpty( deviceInit ) ? null : Collections.unmodifiableMap( deviceInit );    // Acts as a defensive copy

        // 'downtime' is the maximum amount of time that a device can be without updating its value prior to consider it could be malfunctioning

        if( (this.deviceInit != null) && this.deviceInit.containsKey( "downtime" ) )
        {
            downtime = Math.abs( ((Number) deviceInit.get( "downtime" )).longValue() );
        }
        else
        {
            downtime = 0;
        }

        // If 'groups' is defined, we use it (e.g.: s == "lights, clima, movement").

        if( (this.deviceInit != null) && (this.deviceInit.get( "groups" ) != null) )
        {
            String      sAll      = this.deviceInit.get( "groups" ).toString().trim();
            Set<String> lstGroups = new HashSet<>();

            for( String s : sAll.split( "," ) )
            {
                s = s.trim().toLowerCase();     // because Une is case in-sensitive

                if( (! s.isEmpty()) && (! lstGroups.contains( s )) )
                    lstGroups.add( s );
            }

            groups = lstGroups.toArray( String[]::new );
        }
        else
        {
            groups = null;
        }
    }

    //----------------------------------------------------------------------------//
    // IDevice interface methods

    @Override
    public Object value()
    {
        return value.get();     // It is null until the device receives its first value
    }

    @Override
    public boolean value( Object newValue )
    {
        try
        {
            // DOC: If user wants that MsgDeviceChanged messages are sent only when the new
            // value is different from the previous value, he has to set a proper DELTA > 0

            return value.set( newValue );
        }
        catch( MingleException me )
        {
            getRuntime().log( ILogger.Level.WARNING, me );
        }
        catch( NullPointerException npe )        // Thrown by Objects.requireNonNull(...)
        {
            getRuntime().log( ILogger.Level.WARNING, "Can not assign null to device '"+ name() +'\'' );
        }

        return false;
    }

    @Override
    public Float delta()
    {
        return value.delta();
    }

    @Override
    public boolean isDowntimed()
    {
        return (downtime > 0) && value.isElapsed( downtime );
    }

    @Override
    public Map<String,Object> getDeviceInit()
    {
        return deviceInit;     // No problem if map is null
    }

    @Override
    public String getDriverName()
    {
        return driverName;
    }

    @Override
    public Map<String,Object> getDriverInit()
    {
        return driverInit;     // No problmen if map is null
    }

    @Override
    public void start( IRuntime rt )
    {
        if( isStarted() )
            return;

        super.start( rt );

        if( (deviceInit != null) && deviceInit.containsKey( "value" ) )                              // This only works when using a driver of type Actuator
            getRuntime().bus().post( new MsgChangeActuator( name(), deviceInit.get( "value" ) ) );   // Sets the initial state for this Actuator in the physical world (*)
        else
            getRuntime().bus().post( new MsgReadDevice( name() ) );                                  // Reads current state for this Actuator in the physical world

        // (*) This message is captured by the Actuator's Driver, which sends it to the Driver's Controller,
        // which changes the physical actuator and informs back (via its listener) to the Driver which
        // transforms the information received from the Controller into a MsgDeviceChanged, which will be
        // intercepted by this class and will change the internal get in this super-class.
    }

    //----------------------------------------------------------------------------//
    // Groups related methods

    @Override
    public final String[] groups()
    {
        return (groups == null) ? new String[0] : Arrays.copyOf( groups, groups.length );
    }

    @Override
    public boolean isMemberOfGroup( String name )
    {
        if( UtilColls.isEmpty( groups ) )
            return false;

        name = name.trim().toLowerCase();

        // I do not do Arrays.binarySearch(...) because for small arrays it is slower

        for( String s : groups )
            if( s.equals( name ) )
                return true;

        return false;
    }

    //----------------------------------------------------------------------------//
    // PRIVATE INTERFACE
}