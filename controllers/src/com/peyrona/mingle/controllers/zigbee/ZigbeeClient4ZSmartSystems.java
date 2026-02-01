
package com.peyrona.mingle.controllers.zigbee;

import com.zsmartsystems.zigbee.CommandResult;
import com.zsmartsystems.zigbee.IeeeAddress;
import com.zsmartsystems.zigbee.ZigBeeChannel;
import com.zsmartsystems.zigbee.ZigBeeEndpoint;
import com.zsmartsystems.zigbee.ZigBeeNetworkManager;
import com.zsmartsystems.zigbee.ZigBeeNetworkNodeListener;
import com.zsmartsystems.zigbee.ZigBeeNetworkState;
import com.zsmartsystems.zigbee.ZigBeeNetworkStateListener;
import com.zsmartsystems.zigbee.ZigBeeNode;
import com.zsmartsystems.zigbee.ZigBeeStatus;
import com.zsmartsystems.zigbee.app.discovery.ZigBeeDiscoveryExtension;
import com.zsmartsystems.zigbee.security.ZigBeeKey;
import com.zsmartsystems.zigbee.serialization.DefaultDeserializer;
import com.zsmartsystems.zigbee.serialization.DefaultSerializer;
import com.zsmartsystems.zigbee.transport.TransportConfig;
import com.zsmartsystems.zigbee.transport.ZigBeeTransportTransmit;
import com.zsmartsystems.zigbee.zcl.ZclAttribute;
import com.zsmartsystems.zigbee.zcl.ZclCluster;
import com.zsmartsystems.zigbee.zcl.clusters.ZclLevelControlCluster;
import com.zsmartsystems.zigbee.zcl.clusters.ZclOnOffCluster;
import com.zsmartsystems.zigbee.zdo.field.NodeDescriptor;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Implementation of {@link IZigbeeClient} using the ZSmartSystems Zigbee library.
 * <p>
 * This class provides Zigbee network management and device communication using
 * the com.zsmartsystems.zigbee library, which supports multiple coordinator dongles
 * including Silicon Labs Ember, Texas Instruments CC2531, Dresden Elektronik ConBee,
 * Digi XBee, and Telegesis ETRX modules.
 *
 * <h3>Thread Safety:</h3>
 * This class is thread-safe. All state modifications are protected by a ReentrantLock,
 * and device cache operations use ConcurrentHashMap.
 *
 * <h3>Supported Dongles:</h3>
 * <ul>
 *   <li><b>ember</b>: Uses ZigBeeTransportTransmit from dongle.ember package</li>
 *   <li><b>cc2531</b>: Uses ZigBeeTransportTransmit from dongle.cc2531 package</li>
 *   <li><b>conbee</b>: Uses ZigBeeTransportTransmit from dongle.conbee package</li>
 *   <li><b>xbee</b>: Uses ZigBeeTransportTransmit from dongle.xbee package</li>
 *   <li><b>telegesis</b>: Uses ZigBeeTransportTransmit from dongle.telegesis package</li>
 * </ul>
 *
 * @author Francisco Jose Morero Peyrona
 * @see <a href="https://github.com/zsmartsystems/com.zsmartsystems.zigbee">ZSmartSystems Zigbee library</a>
 * @see <a href="https://github.com/peyrona/mingle">Mingle project</a>
 */
public final class ZigbeeClient4ZSmartSystems implements IZigbeeClient
{
    // Configuration
    private final IZigbeeClient.Config config;
    private final IZigbeeClient.Listener listener;

    // Network manager and transport
    private volatile ZigBeeNetworkManager networkManager;
    private volatile ZigBeeTransportTransmit dongle;

    // Device cache: IEEE address string -> ZigBeeNode
    private final Map<String, ZigBeeNode> deviceCache = new ConcurrentHashMap<>();

    // State management
    private volatile boolean running = false;
    private final ReentrantLock lock = new ReentrantLock();

    // Polling scheduler (for polling mode)
    private volatile ScheduledExecutorService scheduler;
    private volatile ScheduledFuture<?> pollingTask;

    //------------------------------------------------------------------------//
    // CONSTRUCTOR
    //------------------------------------------------------------------------//

    /**
     * Creates a new Zigbee client with the specified configuration and listener.
     *
     * @param config   The configuration parameters
     * @param listener The listener for network events
     * @throws IllegalArgumentException If config or listener is null
     */
    public ZigbeeClient4ZSmartSystems( IZigbeeClient.Config config, IZigbeeClient.Listener listener )
    {
        if( config == null )
            throw new IllegalArgumentException( "Config cannot be null" );

        if( listener == null )
            throw new IllegalArgumentException( "Listener cannot be null" );

        this.config   = config;
        this.listener = listener;
    }

    //------------------------------------------------------------------------//
    // LIFECYCLE METHODS
    //------------------------------------------------------------------------//

    @Override
    public void open() throws IOException
    {
        lock.lock();

        try
        {
            if( running )
                return;

            // Create transport based on dongle type
            dongle = createTransport( config.getPort(), config.getDongle() );

            if( dongle == null )
                throw new IOException( "Failed to create transport for dongle: " + config.getDongle() );

            // Create network manager
            networkManager = new ZigBeeNetworkManager( dongle );

            // Set serializers
            networkManager.setSerializer( DefaultSerializer.class, DefaultDeserializer.class );

            // Add network state listener
            networkManager.addNetworkStateListener( new NetworkStateListener() );

            // Add node listener for device join/leave events
            networkManager.addNetworkNodeListener( new NetworkNodeListener() );

            // Configure network key
            if( ! config.isNetworkKeyAuto() )
            {
                byte[] keyBytes = IZigbeeClient.Config.parseNetworkKey( config.getNetworkKey() );
                int[]  keyInts  = new int[keyBytes.length];

                for( int n = 0; n < keyBytes.length; n++ )
                    keyInts[n] = keyBytes[n] & 0xFF;

                ZigBeeKey networkKey = new ZigBeeKey( keyInts );
                networkManager.setZigBeeNetworkKey( networkKey );
            }

            // Configure transport
            TransportConfig transportConfig = new TransportConfig();

            // Set PAN ID if not auto
            if( ! config.isPanIdAuto() )
            {
                int panId = IZigbeeClient.Config.parsePanId( config.getPanId() );
                networkManager.setZigBeePanId( panId );
            }

            // Set channel
            ZigBeeChannel zigbeeChannel = ZigBeeChannel.create( config.getChannel() );
            networkManager.setZigBeeChannel( zigbeeChannel );

            // Apply transport configuration
            dongle.updateTransportConfig( transportConfig );

            // Initialize network
            ZigBeeStatus initStatus = networkManager.initialize();

            if( initStatus != ZigBeeStatus.SUCCESS )
                throw new IOException( "Failed to initialize Zigbee network: " + initStatus );

            // Add discovery extension
            networkManager.addExtension( new ZigBeeDiscoveryExtension() );

            // Start the network
            ZigBeeStatus startStatus = networkManager.startup( false );

            if( startStatus != ZigBeeStatus.SUCCESS )
                throw new IOException( "Failed to start Zigbee network: " + startStatus );

            // Set permit join if configured
            if( config.isPermitJoin() )
            {
                networkManager.permitJoin( 255 ); // Permanent until disabled
            }

            running = true;

            // Start polling if configured
            if( config.isPollingMode() )
            {
                startPolling();
            }

            // Cache existing devices
            cacheExistingDevices();
        }
        catch( Exception ex )
        {
            close();

            if( ex instanceof IOException )
                throw (IOException) ex;

            throw new IOException( "Failed to open Zigbee network: " + ex.getMessage(), ex );
        }
        finally
        {
            lock.unlock();
        }
    }

    @Override
    public void close()
    {
        lock.lock();

        try
        {
            // Stop polling
            stopPolling();

            // Shutdown network manager
            if( networkManager != null )
            {
                try
                {
                    networkManager.shutdown();
                }
                catch( Exception ex )
                {
                    // Ignore shutdown errors
                }

                networkManager = null;
            }

            // Close transport
            if( dongle != null )
            {
                dongle = null;
            }

            // Clear device cache
            deviceCache.clear();

            running = false;
        }
        finally
        {
            lock.unlock();
        }
    }

    @Override
    public boolean isRunning()
    {
        return running;
    }

    //------------------------------------------------------------------------//
    // NETWORK MANAGEMENT
    //------------------------------------------------------------------------//

    @Override
    public void permitJoin( int durationSeconds ) throws IOException
    {
        if( ! running || networkManager == null )
            throw new IOException( "Zigbee network is not running" );

        ZigBeeStatus status = networkManager.permitJoin( durationSeconds );

        if( status != ZigBeeStatus.SUCCESS )
            throw new IOException( "Failed to set permit join: " + status );
    }

    @Override
    public List<Map<String, Object>> getDevices()
    {
        List<Map<String, Object>> devices = new ArrayList<>();

        if( networkManager == null )
            return devices;

        Collection<ZigBeeNode> nodes = networkManager.getNodes();

        for( ZigBeeNode node : nodes )
        {
            Map<String, Object> deviceInfo = new HashMap<>();
            deviceInfo.put( "ieee",    node.getIeeeAddress().toString() );
            deviceInfo.put( "network", node.getNetworkAddress() );
            deviceInfo.put( "type",    getNodeType( node ) );

            List<Integer> endpoints = new ArrayList<>();

            for( ZigBeeEndpoint endpoint : node.getEndpoints() )
            {
                endpoints.add( endpoint.getEndpointId() );
            }

            deviceInfo.put( "endpoints", endpoints );
            devices.add( deviceInfo );
        }

        return devices;
    }

    @Override
    public Map<String, Object> getDeviceInfo( String ieeeAddress )
    {
        if( networkManager == null || ieeeAddress == null )
            return null;

        try
        {
            IeeeAddress address = new IeeeAddress( ieeeAddress );
            ZigBeeNode node = networkManager.getNode( address );

            if( node == null )
                return null;

            Map<String, Object> info = new HashMap<>();
            info.put( "ieee",     node.getIeeeAddress().toString() );
            info.put( "network",  node.getNetworkAddress() );
            info.put( "type",     getNodeType( node ) );
            info.put( "online",   node.getNodeState() == ZigBeeNode.ZigBeeNodeState.ONLINE );
            info.put( "lastSeen", node.getLastUpdateTime().getTime() );

            // Build endpoint details
            List<Map<String, Object>> endpoints = new ArrayList<>();

            for( ZigBeeEndpoint endpoint : node.getEndpoints() )
            {
                Map<String, Object> epInfo = new HashMap<>();
                epInfo.put( "id",         endpoint.getEndpointId() );
                epInfo.put( "profileId",  endpoint.getProfileId() );
                epInfo.put( "deviceId",   endpoint.getDeviceId() );

                List<Integer> inputClusters = new ArrayList<>();

                for( ZclCluster cluster : endpoint.getInputClusterIds().stream()
                                                   .map( endpoint::getInputCluster )
                                                   .toArray( ZclCluster[]::new ) )
                {
                    if( cluster != null )
                        inputClusters.add( cluster.getClusterId() );
                }

                epInfo.put( "inputClusters", inputClusters );

                List<Integer> outputClusters = new ArrayList<>();

                for( ZclCluster cluster : endpoint.getOutputClusterIds().stream()
                                                    .map( endpoint::getOutputCluster )
                                                    .toArray( ZclCluster[]::new ) )
                {
                    if( cluster != null )
                        outputClusters.add( cluster.getClusterId() );
                }

                epInfo.put( "outputClusters", outputClusters );
                endpoints.add( epInfo );
            }

            info.put( "endpoints", endpoints );

            return info;
        }
        catch( Exception ex )
        {
            return null;
        }
    }

    //------------------------------------------------------------------------//
    // DATA OPERATIONS
    //------------------------------------------------------------------------//

    @Override
    public Object read( String ieeeAddress, int endpoint, int clusterId ) throws IOException
    {
        if( ! running || networkManager == null )
            throw new IOException( "Zigbee network is not running" );

        ZigBeeEndpoint ep = getEndpoint( ieeeAddress, endpoint );

        if( ep == null )
            throw new IOException( "Endpoint not found: " + ieeeAddress + "/" + endpoint );

        ZclCluster cluster = ep.getInputCluster( clusterId );

        if( cluster == null )
            cluster = ep.getOutputCluster( clusterId );

        if( cluster == null )
            throw new IOException( "Cluster not found: 0x" + Integer.toHexString( clusterId ) );

        // Read the primary attribute based on cluster type
        int attributeId = getPrimaryAttributeId( clusterId );
        ZclAttribute attribute = cluster.getAttribute( attributeId );

        if( attribute == null )
            throw new IOException( "Attribute not found: " + attributeId );

        Object value = attribute.readValue( config.getTimeout() );

        return value;
    }

    @Override
    public void write( String ieeeAddress, int endpoint, int clusterId, Object value ) throws IOException
    {
        if( ! running || networkManager == null )
            throw new IOException( "Zigbee network is not running" );

        ZigBeeEndpoint ep = getEndpoint( ieeeAddress, endpoint );

        if( ep == null )
            throw new IOException( "Endpoint not found: " + ieeeAddress + "/" + endpoint );

        try
        {
            switch( clusterId )
            {
                case IZigbeeClient.Config.CLUSTER_ONOFF:
                    writeOnOff( ep, value );
                    break;

                case IZigbeeClient.Config.CLUSTER_LEVEL_CONTROL:
                    writeLevelControl( ep, value );
                    break;

                default:
                    writeGenericAttribute( ep, clusterId, value );
                    break;
            }
        }
        catch( InterruptedException | ExecutionException ex )
        {
            throw new IOException( "Failed to write to device: " + ex.getMessage(), ex );
        }
    }

    @Override
    public void sendCommand( String ieeeAddress, int endpoint, int clusterId,
                             int commandId, byte[] payload ) throws IOException
    {
        if( ! running || networkManager == null )
            throw new IOException( "Zigbee network is not running" );

        ZigBeeEndpoint ep = getEndpoint( ieeeAddress, endpoint );

        if( ep == null )
            throw new IOException( "Endpoint not found: " + ieeeAddress + "/" + endpoint );

        ZclCluster cluster = ep.getInputCluster( clusterId );

        if( cluster == null )
            throw new IOException( "Cluster not found: 0x" + Integer.toHexString( clusterId ) );

        // This is a simplified implementation - full implementation would construct proper ZCL command
        throw new UnsupportedOperationException( "Raw command sending not yet implemented" );
    }

    //------------------------------------------------------------------------//
    // CONVENIENCE METHODS
    //------------------------------------------------------------------------//

    @Override
    public void setOnOff( String ieeeAddress, int endpoint, boolean on ) throws IOException
    {
        write( ieeeAddress, endpoint, IZigbeeClient.Config.CLUSTER_ONOFF, on );
    }

    @Override
    public void setLevel( String ieeeAddress, int endpoint, int level ) throws IOException
    {
        write( ieeeAddress, endpoint, IZigbeeClient.Config.CLUSTER_LEVEL_CONTROL, level );
    }

    @Override
    public void toggle( String ieeeAddress, int endpoint ) throws IOException
    {
        if( ! running || networkManager == null )
            throw new IOException( "Zigbee network is not running" );

        ZigBeeEndpoint ep = getEndpoint( ieeeAddress, endpoint );

        if( ep == null )
            throw new IOException( "Endpoint not found: " + ieeeAddress + "/" + endpoint );

        ZclOnOffCluster cluster = (ZclOnOffCluster) ep.getInputCluster( ZclOnOffCluster.CLUSTER_ID );

        if( cluster == null )
            throw new IOException( "On/Off cluster not found on endpoint " + endpoint );

        try
        {
            CommandResult result = cluster.toggleCommand().get( config.getTimeout(), TimeUnit.MILLISECONDS );

            if( ! result.isSuccess() )
                throw new IOException( "Toggle command failed: " + result.getStatusCode() );
        }
        catch( InterruptedException | ExecutionException | TimeoutException ex )
        {
            throw new IOException( "Toggle command failed: " + ex.getMessage(), ex );
        }
    }

    //------------------------------------------------------------------------//
    // PRIVATE HELPER METHODS
    //------------------------------------------------------------------------//

    /**
     * Creates the transport for the specified dongle type.
     */
    private ZigBeeTransportTransmit createTransport( String port, String dongleType ) throws IOException
    {
        try
        {
            switch( dongleType.toLowerCase() )
            {
                case IZigbeeClient.Config.DONGLE_EMBER:
                    return createEmberTransport( port );

                case IZigbeeClient.Config.DONGLE_CC2531:
                    return createCC2531Transport( port );

                case IZigbeeClient.Config.DONGLE_CONBEE:
                    return createConBeeTransport( port );

                case IZigbeeClient.Config.DONGLE_XBEE:
                    return createXBeeTransport( port );

                case IZigbeeClient.Config.DONGLE_TELEGESIS:
                    return createTelegesisTransport( port );

                default:
                    throw new IOException( "Unknown dongle type: " + dongleType );
            }
        }
        catch( Exception ex )
        {
            throw new IOException( "Failed to create transport: " + ex.getMessage(), ex );
        }
    }

    /**
     * Creates an Ember dongle transport.
     */
    private ZigBeeTransportTransmit createEmberTransport( String port ) throws Exception
    {
        Class<?> transportClass = Class.forName( "com.zsmartsystems.zigbee.dongle.ember.ZigBeeDongleEzsp" );
        Class<?> portClass      = Class.forName( "com.zsmartsystems.zigbee.serial.ZigBeeSerialPort" );

        Object serialPort = portClass.getConstructor( String.class, int.class )
                                     .newInstance( port, 115200 );

        return (ZigBeeTransportTransmit) transportClass.getConstructor( portClass.getInterfaces()[0] )
                                                       .newInstance( serialPort );
    }

    /**
     * Creates a CC2531 dongle transport.
     */
    private ZigBeeTransportTransmit createCC2531Transport( String port ) throws Exception
    {
        Class<?> transportClass = Class.forName( "com.zsmartsystems.zigbee.dongle.cc2531.ZigBeeDongleTiCc2531" );
        Class<?> portClass      = Class.forName( "com.zsmartsystems.zigbee.serial.ZigBeeSerialPort" );

        Object serialPort = portClass.getConstructor( String.class, int.class )
                                     .newInstance( port, 115200 );

        return (ZigBeeTransportTransmit) transportClass.getConstructor( portClass.getInterfaces()[0] )
                                                       .newInstance( serialPort );
    }

    /**
     * Creates a ConBee dongle transport.
     */
    private ZigBeeTransportTransmit createConBeeTransport( String port ) throws Exception
    {
        Class<?> transportClass = Class.forName( "com.zsmartsystems.zigbee.dongle.conbee.ZigBeeDongleConBee" );
        Class<?> portClass      = Class.forName( "com.zsmartsystems.zigbee.serial.ZigBeeSerialPort" );

        Object serialPort = portClass.getConstructor( String.class, int.class )
                                     .newInstance( port, 115200 );

        return (ZigBeeTransportTransmit) transportClass.getConstructor( portClass.getInterfaces()[0] )
                                                       .newInstance( serialPort );
    }

    /**
     * Creates an XBee dongle transport.
     */
    private ZigBeeTransportTransmit createXBeeTransport( String port ) throws Exception
    {
        Class<?> transportClass = Class.forName( "com.zsmartsystems.zigbee.dongle.xbee.ZigBeeDongleXBee" );
        Class<?> portClass      = Class.forName( "com.zsmartsystems.zigbee.serial.ZigBeeSerialPort" );

        Object serialPort = portClass.getConstructor( String.class, int.class )
                                     .newInstance( port, 115200 );

        return (ZigBeeTransportTransmit) transportClass.getConstructor( portClass.getInterfaces()[0] )
                                                       .newInstance( serialPort );
    }

    /**
     * Creates a Telegesis dongle transport.
     */
    private ZigBeeTransportTransmit createTelegesisTransport( String port ) throws Exception
    {
        Class<?> transportClass = Class.forName( "com.zsmartsystems.zigbee.dongle.telegesis.ZigBeeDongleTelegesis" );
        Class<?> portClass      = Class.forName( "com.zsmartsystems.zigbee.serial.ZigBeeSerialPort" );

        Object serialPort = portClass.getConstructor( String.class, int.class )
                                     .newInstance( port, 19200 );

        return (ZigBeeTransportTransmit) transportClass.getConstructor( portClass.getInterfaces()[0] )
                                                       .newInstance( serialPort );
    }

    /**
     * Gets an endpoint from a device.
     */
    private ZigBeeEndpoint getEndpoint( String ieeeAddress, int endpointId )
    {
        if( networkManager == null || ieeeAddress == null )
            return null;

        try
        {
            IeeeAddress address = new IeeeAddress( ieeeAddress );
            ZigBeeNode node = networkManager.getNode( address );

            if( node == null )
                return null;

            return node.getEndpoint( endpointId );
        }
        catch( Exception ex )
        {
            return null;
        }
    }

    /**
     * Gets the node type string.
     */
    private String getNodeType( ZigBeeNode node )
    {
        NodeDescriptor descriptor = node.getNodeDescriptor();

        if( descriptor == null )
            return "unknown";

        switch( descriptor.getLogicalType() )
        {
            case COORDINATOR:
                return "coordinator";
            case ROUTER:
                return "router";
            case END_DEVICE:
                return "end_device";
            default:
                return "unknown";
        }
    }

    /**
     * Checks if a node is a coordinator.
     */
    private boolean isCoordinator( ZigBeeNode node )
    {
        NodeDescriptor descriptor = node.getNodeDescriptor();
        return descriptor != null &&
               descriptor.getLogicalType() == NodeDescriptor.LogicalType.COORDINATOR;
    }

    /**
     * Gets the primary attribute ID for a cluster.
     */
    private int getPrimaryAttributeId( int clusterId )
    {
        switch( clusterId )
        {
            case IZigbeeClient.Config.CLUSTER_ONOFF:
                return 0x0000; // OnOff attribute

            case IZigbeeClient.Config.CLUSTER_LEVEL_CONTROL:
                return 0x0000; // CurrentLevel attribute

            case IZigbeeClient.Config.CLUSTER_TEMPERATURE:
                return 0x0000; // MeasuredValue attribute

            case IZigbeeClient.Config.CLUSTER_HUMIDITY:
                return 0x0000; // MeasuredValue attribute

            case IZigbeeClient.Config.CLUSTER_PRESSURE:
                return 0x0000; // MeasuredValue attribute

            case IZigbeeClient.Config.CLUSTER_OCCUPANCY:
                return 0x0000; // Occupancy attribute

            case IZigbeeClient.Config.CLUSTER_ILLUMINANCE:
                return 0x0000; // MeasuredValue attribute

            case IZigbeeClient.Config.CLUSTER_IAS_ZONE:
                return 0x0002; // ZoneStatus attribute

            case IZigbeeClient.Config.CLUSTER_POWER_CONFIG:
                return 0x0021; // BatteryPercentageRemaining attribute

            default:
                return 0x0000; // Default to first attribute
        }
    }

    /**
     * Writes to On/Off cluster.
     */
    private void writeOnOff( ZigBeeEndpoint endpoint, Object value )
            throws InterruptedException, ExecutionException, IOException
    {
        ZclOnOffCluster cluster = (ZclOnOffCluster) endpoint.getInputCluster( ZclOnOffCluster.CLUSTER_ID );

        if( cluster == null )
            throw new IOException( "On/Off cluster not found" );

        boolean on = toBoolean( value );

        CommandResult result;

        try
        {
            if( on )
                result = cluster.onCommand().get( config.getTimeout(), TimeUnit.MILLISECONDS );
            else
                result = cluster.offCommand().get( config.getTimeout(), TimeUnit.MILLISECONDS );

            if( ! result.isSuccess() )
                throw new IOException( "On/Off command failed: " + result.getStatusCode() );
        }
        catch( TimeoutException ex )
        {
            throw new IOException( "On/Off command timed out", ex );
        }
    }

    /**
     * Writes to Level Control cluster.
     */
    private void writeLevelControl( ZigBeeEndpoint endpoint, Object value )
            throws InterruptedException, ExecutionException, IOException
    {
        ZclLevelControlCluster cluster = (ZclLevelControlCluster) endpoint.getInputCluster( ZclLevelControlCluster.CLUSTER_ID );

        if( cluster == null )
            throw new IOException( "Level Control cluster not found" );

        int level = toInteger( value );
        level = Math.max( 0, Math.min( 254, level ) );

        try
        {
            CommandResult result = cluster.moveToLevelWithOnOffCommand( level, 10 )
                                          .get( config.getTimeout(), TimeUnit.MILLISECONDS );

            if( ! result.isSuccess() )
                throw new IOException( "Level control command failed: " + result.getStatusCode() );
        }
        catch( TimeoutException ex )
        {
            throw new IOException( "Level control command timed out", ex );
        }
    }

    /**
     * Writes to a generic cluster attribute.
     */
    private void writeGenericAttribute( ZigBeeEndpoint endpoint, int clusterId, Object value )
            throws InterruptedException, ExecutionException, IOException
    {
        ZclCluster cluster = endpoint.getInputCluster( clusterId );

        if( cluster == null )
            throw new IOException( "Cluster not found: 0x" + Integer.toHexString( clusterId ) );

        int attributeId = getPrimaryAttributeId( clusterId );
        ZclAttribute attribute = cluster.getAttribute( attributeId );

        if( attribute == null )
            throw new IOException( "Attribute not found: " + attributeId );

        try
        {
            CommandResult result = cluster.write( attribute, value )
                                          .get( config.getTimeout(), TimeUnit.MILLISECONDS );

            if( ! result.isSuccess() )
                throw new IOException( "Write command failed: " + result.getStatusCode() );
        }
        catch( TimeoutException ex )
        {
            throw new IOException( "Write command timed out", ex );
        }
    }

    /**
     * Converts a value to boolean.
     */
    private boolean toBoolean( Object value )
    {
        if( value instanceof Boolean )
            return (Boolean) value;

        if( value instanceof Number )
            return ((Number) value).intValue() != 0;

        if( value instanceof String )
        {
            String s = ((String) value).toLowerCase();
            return "true".equals( s ) || "on".equals( s ) || "1".equals( s );
        }

        return false;
    }

    /**
     * Converts a value to integer.
     */
    private int toInteger( Object value )
    {
        if( value instanceof Number )
            return ((Number) value).intValue();

        if( value instanceof Boolean )
            return ((Boolean) value) ? 254 : 0;

        if( value instanceof String )
        {
            try
            {
                return Integer.parseInt( (String) value );
            }
            catch( NumberFormatException ex )
            {
                return 0;
            }
        }

        return 0;
    }

    /**
     * Caches existing devices in the network.
     */
    private void cacheExistingDevices()
    {
        if( networkManager == null )
            return;

        for( ZigBeeNode node : networkManager.getNodes() )
        {
            deviceCache.put( node.getIeeeAddress().toString(), node );
        }
    }

    /**
     * Starts the polling scheduler.
     */
    private void startPolling()
    {
        if( scheduler != null )
            return;

        scheduler = Executors.newSingleThreadScheduledExecutor( r ->
        {
            Thread t = new Thread( r, "Zigbee-Poller-" + config.getPort() );
            t.setDaemon( true );
            return t;
        } );

        pollingTask = scheduler.scheduleAtFixedRate(
            this::pollDevices,
            config.getInterval(),
            config.getInterval(),
            TimeUnit.MILLISECONDS
        );
    }

    /**
     * Stops the polling scheduler.
     */
    private void stopPolling()
    {
        if( pollingTask != null )
        {
            pollingTask.cancel( true );
            pollingTask = null;
        }

        if( scheduler != null )
        {
            scheduler.shutdownNow();
            scheduler = null;
        }
    }

    /**
     * Polls all devices for their current state.
     */
    private void pollDevices()
    {
        if( ! running || networkManager == null )
            return;

        for( ZigBeeNode node : networkManager.getNodes() )
        {
            if( isCoordinator( node ) )
                continue;

            for( ZigBeeEndpoint endpoint : node.getEndpoints() )
            {
                pollEndpoint( node.getIeeeAddress().toString(), endpoint );
            }
        }
    }

    /**
     * Polls a single endpoint for its state.
     */
    private void pollEndpoint( String ieeeAddress, ZigBeeEndpoint endpoint )
    {
        // Poll On/Off cluster if available
        ZclCluster onOff = endpoint.getInputCluster( IZigbeeClient.Config.CLUSTER_ONOFF );

        if( onOff != null )
        {
            try
            {
                ZclAttribute attr = onOff.getAttribute( 0 );

                if( attr != null )
                {
                    Object value = attr.readValue( config.getTimeout() / 2 );

                    if( value != null )
                    {
                        listener.onMessage( ieeeAddress, endpoint.getEndpointId(),
                                            IZigbeeClient.Config.CLUSTER_ONOFF, value );
                    }
                }
            }
            catch( Exception ex )
            {
                // Ignore polling errors for individual attributes
            }
        }

        // Poll temperature cluster if available
        ZclCluster temp = endpoint.getInputCluster( IZigbeeClient.Config.CLUSTER_TEMPERATURE );

        if( temp != null )
        {
            try
            {
                ZclAttribute attr = temp.getAttribute( 0 );

                if( attr != null )
                {
                    Object value = attr.readValue( config.getTimeout() / 2 );

                    if( value != null )
                    {
                        // Convert from centidegrees to degrees
                        if( value instanceof Number )
                            value = ((Number) value).doubleValue() / 100.0;

                        listener.onMessage( ieeeAddress, endpoint.getEndpointId(),
                                            IZigbeeClient.Config.CLUSTER_TEMPERATURE, value );
                    }
                }
            }
            catch( Exception ex )
            {
                // Ignore polling errors for individual attributes
            }
        }

        // Poll humidity cluster if available
        ZclCluster humidity = endpoint.getInputCluster( IZigbeeClient.Config.CLUSTER_HUMIDITY );

        if( humidity != null )
        {
            try
            {
                ZclAttribute attr = humidity.getAttribute( 0 );

                if( attr != null )
                {
                    Object value = attr.readValue( config.getTimeout() / 2 );

                    if( value != null )
                    {
                        // Convert from centipercent to percent
                        if( value instanceof Number )
                            value = ((Number) value).doubleValue() / 100.0;

                        listener.onMessage( ieeeAddress, endpoint.getEndpointId(),
                                            IZigbeeClient.Config.CLUSTER_HUMIDITY, value );
                    }
                }
            }
            catch( Exception ex )
            {
                // Ignore polling errors for individual attributes
            }
        }
    }

    //------------------------------------------------------------------------//
    // INNER CLASSES - Listeners
    //------------------------------------------------------------------------//

    /**
     * Listener for network state changes.
     */
    private class NetworkStateListener implements ZigBeeNetworkStateListener
    {
        @Override
        public void networkStateUpdated( ZigBeeNetworkState state )
        {
            switch( state )
            {
                case ONLINE:
                    listener.onNetworkStarted();
                    break;

                case OFFLINE:
                    listener.onNetworkStopped();
                    break;

                default:
                    break;
            }
        }
    }

    /**
     * Listener for node join/leave events.
     */
    private class NetworkNodeListener implements ZigBeeNetworkNodeListener
    {
        @Override
        public void nodeAdded( ZigBeeNode node )
        {
            String ieeeAddress = node.getIeeeAddress().toString();
            deviceCache.put( ieeeAddress, node );
            listener.onDeviceJoined( ieeeAddress );
        }

        @Override
        public void nodeUpdated( ZigBeeNode node )
        {
            deviceCache.put( node.getIeeeAddress().toString(), node );
        }

        @Override
        public void nodeRemoved( ZigBeeNode node )
        {
            String ieeeAddress = node.getIeeeAddress().toString();
            deviceCache.remove( ieeeAddress );
            listener.onDeviceLeft( ieeeAddress );
        }
    }
}