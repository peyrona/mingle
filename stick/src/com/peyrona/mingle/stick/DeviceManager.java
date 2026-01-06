
package com.peyrona.mingle.stick;

import com.peyrona.mingle.lang.interfaces.commands.IDevice;
import com.peyrona.mingle.lang.interfaces.exen.IRuntime;
import com.peyrona.mingle.lang.japi.UtilColls;
import com.peyrona.mingle.lang.japi.UtilStr;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Manages all devices in the runtime environment.
 * <p>
 * This class provides a "Logical Twin": a computerized representation
 * of the real world that is always synchronized with it.
 * Supports device groups and group-based queries.
 *
 * @author Francisco José Morero Peyrona
 *
 * Official web site at: <a href="https://github.com/peyrona/mingle">https://github.com/peyrona/mingle</a>
 */
final class   DeviceManager
      extends BaseManager<IDevice>
{
    /**
     * Creates a new DeviceManager instance.
     *
     * @param runtime The runtime environment for this manager.
     */
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

    /**
     * Checks if any device belongs to the specified group.
     *
     * @param name The group name to check.
     * @return true if at least one device has this group name; false otherwise.
     */
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

    /**
     * Returns all devices that belong to ANY of the specified groups.
     *
     * @param group One or more group names to filter devices.
     * @return A set of devices belonging to at least one of the specified groups, or null if no groups provided.
     */
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

    /**
     * Returns all devices that belong to ANY of the specified groups.
     *
     * @param group One or more group names to filter devices.
     * @return A set of devices belonging to at least one of the specified groups, or null if no groups provided.
     */
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

    /**
     * Returns all devices that belong to ALL of the specified groups.
     *
     * @param group One or more group names to filter devices.
     * @return A set of devices belonging to all of the specified groups (empty set if no device matches all groups).
     */
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
        private final String                  name;
        private final AtomicReference<Object> value = new AtomicReference<>( null );

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
            return value.get();
        }

        @Override
        public boolean value( Object newValue )
        {
            value.set( newValue );
            return true;
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