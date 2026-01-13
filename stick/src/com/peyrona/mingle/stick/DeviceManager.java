
package com.peyrona.mingle.stick;

import com.peyrona.mingle.lang.interfaces.commands.IDevice;
import com.peyrona.mingle.lang.interfaces.exen.IRuntime;
import com.peyrona.mingle.lang.japi.OneToMany;
import com.peyrona.mingle.lang.japi.UtilColls;
import com.peyrona.mingle.lang.japi.UtilStr;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
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
    // Inverted Index: Group Name -> List of Devices
    private final OneToMany<String, IDevice> groupsMap = new OneToMany<>();

    //------------------------------------------------------------------------//

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

    @Override
    void add( IDevice device )
    {
        // 1. Check if it already exists to avoid desync (BaseManager logs duplication but doesn't throw)
        if( named( device.name() ) != null )
        {
            super.add( device ); // Call super to trigger the standard "already exists" logging
            return;
        }

        // 2. Add to main storage
        super.add( device );

        // 3. Update Group Index
        for( String group : device.groups() )
        {
            if( UtilStr.isNotEmpty( group ) )
                groupsMap.put( group, device );
        }
    }

    @Override
    boolean remove( IDevice device )
    {
        boolean removed = super.remove( device );

        if( removed )
        {
            for( String group : device.groups() )
            {
                if( UtilStr.isNotEmpty( group ) )
                    groupsMap.remove( group, device );
            }
        }

        return removed;
    }

    /**
     * Creates (and adds) some kind of stub to a Device existing in another ExEn to
     * be partially managed in this ExEn.<br>
     * Needed when ExEn is a Grid Node: pseudo devices have to be created.
     *
     * @param name Of the device to be created.
     */
    void createRemoteDevice( String name )
    {
        this.add( new RemoteDevice( name ) );
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

        return groupsMap.containsKey( name );
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

        for( String sGroup : group )
        {
            if( UtilStr.isEmpty( sGroup ) )
                continue;

            toReturn.addAll( groupsMap.get( sGroup.trim() ) );
        }

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
        return getMembersOf( group );
    }

    /**
     * Returns all devices that belong to ALL of the specified groups.
     *
     * @param group One or more group names to filter devices.
     * @return A set of devices belonging to all of the specified groups (empty set if no device matches all groups).
     */
    Set<IDevice> getInAllGroups( String... group )
    {
        if( UtilColls.isEmpty( group ) )
            return new HashSet<>();

        // 1. Sanitize inputs. If any requested group is invalid (null/empty),
        // the intersection is logically empty because no device has an empty group name.

        String[] sanitized = new String[group.length];

        for( int i = 0; i < group.length; i++ )
        {
            if( UtilStr.isEmpty( group[i] ) )
                return new HashSet<>();

            sanitized[i] = group[i].trim();
        }

        // 2. Start with the devices from the first group

        List<IDevice> candidates = groupsMap.get( sanitized[0] );

        if( candidates.isEmpty() )
            return new HashSet<>();

        Set<IDevice> toReturn = new HashSet<>();

        // 3. Check if these candidates belong to ALL other groups

        for( IDevice device : candidates )
        {
            // Retrieve device groups only once per candidate (hoisted out of inner loop)

            String[] devGroups = device.groups();

            if( devGroups == null )
                continue;

            boolean inAll = true;

            // Start from 1 since we already grabbed candidates from sanitized[0]

            for( int i = 1; i < sanitized.length; i++ )
            {
                String  targetGroup = sanitized[i];
                boolean matches     = false;

                for( String g : devGroups )
                {
                    if( targetGroup.equals( g ) )
                    {
                        matches = true;
                        break;
                    }
                }

                if( ! matches )
                {
                    inAll = false;
                    break;
                }
            }

            if( inAll )
                toReturn.add( device );
        }

        return toReturn;
    }

    //------------------------------------------------------------------------//
    // INNER CLASS
    // Used to store Devices that are in other ExEns when this ExEn is a Grid Node
    //------------------------------------------------------------------------//
    private static final class RemoteDevice implements IDevice
    {
        private static final String[]         EMPTY_GROUPS = new String[0];
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
            return EMPTY_GROUPS;
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