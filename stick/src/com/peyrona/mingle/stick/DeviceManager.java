
package com.peyrona.mingle.stick;

import com.peyrona.mingle.lang.interfaces.commands.IDevice;
import com.peyrona.mingle.lang.interfaces.exen.IRuntime;
import com.peyrona.mingle.lang.japi.UtilColls;
import com.peyrona.mingle.lang.japi.UtilStr;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * This class is what is usually called the "Logical Twin": a computerized representation
 * of the real world. Both worlds are always synchronized.
 * <p>
 * Implementation note: CIL lib (the one that contains the commands implementations) holds
 * all devices and these have to handle following messages: MsgDeviceChanged and MsgChangeActuator.
 * I tried to move to here this responsibilities (trying to keep the development of CILs as simple as
 * possible), but I've found that it fits better and easier to put MsgDeviceChanged in Sensor.java
 * and MsgChangeActuator in Actuator.java.
 *
 * @author Francisco José Morero Peyrona
 *
 * Official web site at: <a href="https://mingle.peyrona.com">https://mingle.peyrona.com</a>
 */
final class   DeviceManager
      extends BaseManager<IDevice>
{
    DeviceManager( IRuntime runtime )
    {
        super( runtime );
    }

    //----------------------------------------------------------------------------//

    /**
     * Creates (and adds) some kind of stub to a Device existing in another ExEn to
     * be partially managed in this ExEn.<br>
     * Needed when ExEn is a Grid Node: pseudo devices have to be created.
     *
     * @param name Of the device to be created.
     */
    void createRemoteDevice( String name )
    {
        super.add( new RemoteDevice( name ) );
    }

    boolean isGroup( String name )
    {
        if( UtilStr.isEmpty( name ) )
            return false;

        // Simplicity over efficiency

        AtomicBoolean found = new AtomicBoolean( false );

        forEach( device ->
                {
                    if( ! found.get() )
                    {
                        for( String sGroup : device.groups() )
                        {
                            if( name.equals( sGroup ) )
                                found.set( true );
                        }
                    }
                } );

        return found.get();
    }

    Set<IDevice> getMembersOf( String... group )
    {
        if( UtilColls.isEmpty( group ) )
            return null;

        Set<IDevice> toReturn = new HashSet<>();

        for( int n = 0; n < group.length; n++ )
        {
            group[n] = (group[n] == null) ? "" : group[n].trim();
        }

        forEach( device ->
                {
                    for( String sGroup : device.groups() )
                    {
                        for( String sParamGroup : group )
                        {
                            if( sGroup.equals( sParamGroup ) )      // Meanwhile sDevGroup is guaranteed to be not null, sParamGroup could be null
                                toReturn.add( device );
                        }
                    }
                } );

        return toReturn;
    }

    Set<IDevice> getInAnyGroup( String... group )
    {
        if( UtilColls.isEmpty( group ) )
            return null;

        Set<IDevice> toReturn = new HashSet<>();

        for( int n = 0; n < group.length; n++ )
        {
            group[n] = (group[n] == null) ? "" : group[n].trim();
        }

        forEach( device ->
                {
                    for( String sGroup : device.groups() )
                    {
                        for( String sParamGroup : group )
                        {
                            if( sGroup.equals( sParamGroup ) )        // Meanwhile sDevGroup is guaranteed to be not null, sParamGroup could be null
                            {
                                toReturn.add( device );
                            }
                        }
                    }
                } );

        return toReturn;
    }

    Set<IDevice> getInAllGroups( String... group )
    {
        Set<IDevice> toReturn = new HashSet<>();

        if( UtilColls.isNotEmpty( group ) )
        {
            for( int n = 0; n < group.length; n++ )
            {
                group[n] = (group[n] == null) ? null : group[n].trim();
            }

            forEach( device ->
                    {
                        for( String sGroup : device.groups() )
                        {
                            boolean bInAll = true;

                            for( String sParamGroup : group )
                            {
                                if( ! sGroup.equals( sParamGroup ) )      // Meanwhile sDevGroup is guaranteed to be not null, sParamGroup could be null
                                {
                                    bInAll = false;
                                    break;
                                }
                            }

                            if( bInAll )
                            {
                                toReturn.add( device );
                            }
                        }
                    } );
        }

        return toReturn;
    }

    //------------------------------------------------------------------------//
    // INNER CLASS
    // Used to store Devices that are in other ExEns when this ExEn is a Grid Node
    //------------------------------------------------------------------------//
    private static final class RemoteDevice implements IDevice
    {
        private final String name;
        private       Object value = null;

        RemoteDevice( String sName )
        {
            name = sName.trim().toLowerCase();
        }

        @Override
        public Map<String, Object> getDeviceInit()
        {
            return null;
        }

        @Override
        public String getDriverName()
        {
            return null;
        }

        @Override
        public Map<String, Object> getDriverInit()
        {
            return null;
        }

        @Override
        public Object value()
        {
            return value;
        }

        @Override
        public IDevice value( Object newValue )
        {
            value = newValue;
            return this;
        }

        @Override
        public Float delta()
        {
            return null;
        }

        @Override
        public boolean isDowntimed()
        {
            return false;
        }

        @Override
        public String[] groups()
        {
            return null;
        }

        @Override
        public boolean isMemberOfGroup(String group)
        {
            return false;
        }

        @Override
        public String name()
        {
            return name;
        }

        @Override
        public void start(IRuntime runtime)
        {
        }

        @Override
        public void stop()
        {
        }
    }
}