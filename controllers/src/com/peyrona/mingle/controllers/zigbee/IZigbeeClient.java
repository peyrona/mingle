
package com.peyrona.mingle.controllers.zigbee;

import java.io.IOException;
import java.util.List;
import java.util.Map;

/**
 * Interface for Zigbee client implementations.
 * <p>
 * This interface defines the contract for Zigbee network communication.
 * Implementations should handle coordinator initialization, network management,
 * device discovery, and data read/write operations.
 *
 * <h3>Zigbee Architecture:</h3>
 * <ul>
 *   <li><b>Coordinator</b>: The central node that creates and manages the network (this client)</li>
 *   <li><b>Router</b>: Devices that can relay messages and extend the network range</li>
 *   <li><b>End Device</b>: Simple devices like sensors and switches</li>
 * </ul>
 *
 * <h3>Device Identification:</h3>
 * Devices are identified by their IEEE address (64-bit unique identifier), which is stable
 * across network rejoins. Network addresses (16-bit) can change when devices rejoin.
 *
 * <h3>Clusters:</h3>
 * Zigbee uses clusters to define functionality:
 * <ul>
 *   <li><b>On/Off (0x0006)</b>: Binary switch control</li>
 *   <li><b>Level Control (0x0008)</b>: Dimming/brightness control</li>
 *   <li><b>Temperature (0x0402)</b>: Temperature measurement</li>
 *   <li><b>Humidity (0x0405)</b>: Relative humidity measurement</li>
 *   <li><b>Occupancy (0x0406)</b>: Motion/presence detection</li>
 *   <li><b>IAS Zone (0x0500)</b>: Intruder alarm system</li>
 * </ul>
 *
 * <h3>Lifecycle:</h3>
 * <ol>
 *   <li>Create instance with configuration and listener</li>
 *   <li>Call {@link #open()} to initialize the dongle and start the network</li>
 *   <li>Use {@link #permitJoin(int)} to allow new devices to join</li>
 *   <li>Use {@link #read(String, int, int)} and {@link #write(String, int, int, Object)} for data operations</li>
 *   <li>Call {@link #close()} to shut down the network</li>
 * </ol>
 *
 * <h3>Thread Safety:</h3>
 * Implementations must be thread-safe. Network events may arrive on different threads,
 * and read/write operations may be called from any thread.
 *
 * @author Francisco Jose Morero Peyrona
 * @see <a href="https://github.com/zsmartsystems/com.zsmartsystems.zigbee">ZSmartSystems Zigbee library</a>
 * @see <a href="https://github.com/peyrona/mingle">Mingle project</a>
 */
public interface IZigbeeClient
{
    //------------------------------------------------------------------------//
    // INNER INTERFACES
    //------------------------------------------------------------------------//

    /**
     * Listener interface for receiving Zigbee network events.
     * <p>
     * Implementations receive notifications when:
     * <ul>
     *   <li>Data is received from a device (via {@link #onMessage(String, int, int, Object)})</li>
     *   <li>An error occurs (via {@link #onError(Exception)})</li>
     *   <li>A device joins the network (via {@link #onDeviceJoined(String)})</li>
     *   <li>A device leaves the network (via {@link #onDeviceLeft(String)})</li>
     *   <li>The network starts (via {@link #onNetworkStarted()})</li>
     *   <li>The network stops (via {@link #onNetworkStopped()})</li>
     * </ul>
     */
    interface Listener
    {
        /**
         * Called when data is received from a Zigbee device.
         * <p>
         * This method is called for attribute reports, read responses, and other
         * data events from devices.
         *
         * @param ieeeAddress The device's IEEE address (64-bit unique identifier)
         * @param endpoint    The endpoint number (1-240)
         * @param clusterId   The cluster ID (e.g., 0x0006 for On/Off)
         * @param value       The received value (type depends on cluster)
         */
        void onMessage( String ieeeAddress, int endpoint, int clusterId, Object value );

        /**
         * Called when an error occurs during Zigbee communication.
         * <p>
         * Common error types include:
         * <ul>
         *   <li>Dongle not found or access denied</li>
         *   <li>Network initialization failure</li>
         *   <li>Communication timeout</li>
         *   <li>Device not responding</li>
         * </ul>
         *
         * @param exc The exception that occurred
         */
        void onError( Exception exc );

        /**
         * Called when a new device joins the Zigbee network.
         *
         * @param ieeeAddress The device's IEEE address
         */
        void onDeviceJoined( String ieeeAddress );

        /**
         * Called when a device leaves the Zigbee network.
         *
         * @param ieeeAddress The device's IEEE address
         */
        void onDeviceLeft( String ieeeAddress );

        /**
         * Called when the Zigbee network has started successfully.
         */
        void onNetworkStarted();

        /**
         * Called when the Zigbee network has stopped.
         */
        void onNetworkStopped();
    }

    //------------------------------------------------------------------------//
    // CONFIGURATION CLASS
    //------------------------------------------------------------------------//

    /**
     * Configuration class for Zigbee coordinator parameters.
     * <p>
     * Encapsulates all configuration options for a Zigbee network coordinator,
     * including default values, validation, and string-to-constant conversion.
     * <p>
     * Use the {@link Builder} for convenient construction with string-based
     * parameters (e.g., from configuration files).
     *
     * <h3>Dongle Types:</h3>
     * <ul>
     *   <li><b>ember</b>: Silicon Labs Ember EM35x-based coordinators (most common)</li>
     *   <li><b>cc2531</b>: Texas Instruments CC2531 USB dongle</li>
     *   <li><b>conbee</b>: Dresden Elektronik ConBee/ConBee II/RaspBee</li>
     *   <li><b>xbee</b>: Digi XBee modules</li>
     *   <li><b>telegesis</b>: Telegesis ETRX series</li>
     * </ul>
     *
     * <h3>Usage Example:</h3>
     * <pre>
     * // Basic configuration with Ember dongle
     * IZigbeeClient.Config config = new IZigbeeClient.Config.Builder( "/dev/ttyUSB0", "ember" )
     *     .channel( 15 )
     *     .panId( "auto" )
     *     .build();
     *
     * // Full configuration
     * IZigbeeClient.Config config = new IZigbeeClient.Config.Builder( "/dev/ttyUSB0", "conbee" )
     *     .channel( 20 )
     *     .panId( "1234" )
     *     .networkKey( "0123456789ABCDEF0123456789ABCDEF" )
     *     .permitJoin( true )
     *     .interval( 5000 )
     *     .build();
     * </pre>
     */
    class Config
    {
        //--------------------------------------------------------------------//
        // DONGLE TYPE CONSTANTS
        //--------------------------------------------------------------------//

        /** Silicon Labs Ember EM35x-based coordinators. */
        public static final String DONGLE_EMBER     = "ember";
        /** Texas Instruments CC2531 USB dongle. */
        public static final String DONGLE_CC2531    = "cc2531";
        /** Dresden Elektronik ConBee/ConBee II/RaspBee. */
        public static final String DONGLE_CONBEE    = "conbee";
        /** Digi XBee modules. */
        public static final String DONGLE_XBEE      = "xbee";
        /** Telegesis ETRX series. */
        public static final String DONGLE_TELEGESIS = "telegesis";

        /** Valid dongle types. */
        public static final String VALID_DONGLES = "ember,cc2531,conbee,xbee,telegesis";

        //--------------------------------------------------------------------//
        // ZIGBEE CHANNEL CONSTANTS
        //--------------------------------------------------------------------//

        /** Minimum Zigbee channel (2.4 GHz band). */
        public static final int CHANNEL_MIN = 11;
        /** Maximum Zigbee channel (2.4 GHz band). */
        public static final int CHANNEL_MAX = 26;
        /** Default Zigbee channel. */
        public static final int DEFAULT_CHANNEL = 11;

        //--------------------------------------------------------------------//
        // CLUSTER ID CONSTANTS (Common ZCL Clusters)
        //--------------------------------------------------------------------//

        /** On/Off cluster for binary switch control. */
        public static final int CLUSTER_ONOFF          = 0x0006;
        /** Level Control cluster for dimming. */
        public static final int CLUSTER_LEVEL_CONTROL  = 0x0008;
        /** Color Control cluster for color lights. */
        public static final int CLUSTER_COLOR_CONTROL  = 0x0300;
        /** Temperature Measurement cluster. */
        public static final int CLUSTER_TEMPERATURE    = 0x0402;
        /** Pressure Measurement cluster. */
        public static final int CLUSTER_PRESSURE       = 0x0403;
        /** Relative Humidity Measurement cluster. */
        public static final int CLUSTER_HUMIDITY       = 0x0405;
        /** Occupancy Sensing cluster (motion). */
        public static final int CLUSTER_OCCUPANCY      = 0x0406;
        /** Illuminance Measurement cluster. */
        public static final int CLUSTER_ILLUMINANCE    = 0x0400;
        /** IAS Zone cluster (alarm system). */
        public static final int CLUSTER_IAS_ZONE       = 0x0500;
        /** Power Configuration cluster (battery level). */
        public static final int CLUSTER_POWER_CONFIG   = 0x0001;
        /** Electrical Measurement cluster. */
        public static final int CLUSTER_ELECTRICAL     = 0x0B04;
        /** Metering cluster (smart meters). */
        public static final int CLUSTER_METERING       = 0x0702;

        //--------------------------------------------------------------------//
        // DEFAULT VALUES
        //--------------------------------------------------------------------//

        /** Default PAN ID: auto-generate. */
        public static final String DEFAULT_PAN_ID      = "auto";
        /** Default network key: auto-generate. */
        public static final String DEFAULT_NETWORK_KEY = "auto";
        /** Default permit join: false. */
        public static final boolean DEFAULT_PERMIT_JOIN = false;
        /** Default polling interval: 0 (event-driven). */
        public static final int DEFAULT_INTERVAL       = 0;
        /** Minimum polling interval: 500ms. */
        public static final int MIN_INTERVAL           = 500;
        /** Default timeout for operations: 10000ms. */
        public static final int DEFAULT_TIMEOUT        = 10000;
        /** Minimum timeout: 1000ms. */
        public static final int MIN_TIMEOUT            = 1000;

        //--------------------------------------------------------------------//
        // INSTANCE FIELDS
        //--------------------------------------------------------------------//

        private final String  port;
        private final String  dongle;
        private final int     channel;
        private final String  panId;
        private final String  networkKey;
        private final boolean permitJoin;
        private final int     interval;
        private final int     timeout;

        //--------------------------------------------------------------------//
        // CONSTRUCTORS
        //--------------------------------------------------------------------//

        /**
         * Creates a new configuration with default values.
         *
         * @param port   Serial port for the Zigbee dongle (e.g., "/dev/ttyUSB0", "COM3")
         * @param dongle Dongle type: "ember", "cc2531", "conbee", "xbee", "telegesis"
         * @throws IllegalArgumentException If port or dongle is null or empty, or dongle is invalid
         */
        public Config( String port, String dongle )
        {
            this( port, dongle, DEFAULT_CHANNEL, DEFAULT_PAN_ID, DEFAULT_NETWORK_KEY,
                  DEFAULT_PERMIT_JOIN, DEFAULT_INTERVAL, DEFAULT_TIMEOUT );
        }

        /**
         * Creates a new configuration with all parameters specified.
         *
         * @param port       Serial port for the Zigbee dongle
         * @param dongle     Dongle type: "ember", "cc2531", "conbee", "xbee", "telegesis"
         * @param channel    Zigbee channel (11-26)
         * @param panId      PAN ID as hex string (0x0000-0xFFFF) or "auto"
         * @param networkKey 128-bit network key as 32 hex chars or "auto"
         * @param permitJoin Whether to allow new devices to join
         * @param interval   Polling interval in ms (0 = event-driven, >= 500 = polling)
         * @param timeout    Timeout for operations in ms
         * @throws IllegalArgumentException If port or dongle is null or empty
         */
        public Config( String port, String dongle, int channel, String panId, String networkKey,
                       boolean permitJoin, int interval, int timeout )
        {
            if( port == null || port.trim().isEmpty() )
                throw new IllegalArgumentException( "Port cannot be null or empty" );

            if( dongle == null || dongle.trim().isEmpty() )
                throw new IllegalArgumentException( "Dongle type cannot be null or empty" );

            if( ! isValidDongle( dongle ) )
                throw new IllegalArgumentException( "Invalid dongle: " + dongle + ". Valid values: " + VALID_DONGLES );

            this.port       = port;
            this.dongle     = dongle.toLowerCase();
            this.channel    = Math.max( CHANNEL_MIN, Math.min( CHANNEL_MAX, channel ) );
            this.panId      = (panId == null || panId.trim().isEmpty()) ? DEFAULT_PAN_ID : panId;
            this.networkKey = (networkKey == null || networkKey.trim().isEmpty()) ? DEFAULT_NETWORK_KEY : networkKey;
            this.permitJoin = permitJoin;
            this.interval   = (interval > 0) ? Math.max( MIN_INTERVAL, interval ) : DEFAULT_INTERVAL;
            this.timeout    = Math.max( MIN_TIMEOUT, timeout );
        }

        //--------------------------------------------------------------------//
        // GETTERS
        //--------------------------------------------------------------------//

        public String  getPort()       { return port; }
        public String  getDongle()     { return dongle; }
        public int     getChannel()    { return channel; }
        public String  getPanId()      { return panId; }
        public String  getNetworkKey() { return networkKey; }
        public boolean isPermitJoin()  { return permitJoin; }
        public int     getInterval()   { return interval; }
        public int     getTimeout()    { return timeout; }

        /**
         * Returns whether this configuration uses event-driven mode.
         * <p>
         * Event-driven mode is enabled when interval is 0 (or not specified).
         * In this mode, devices report changes via attribute reporting.
         *
         * @return true if event-driven, false if polling mode
         */
        public boolean isEventDriven() { return interval <= 0; }

        /**
         * Returns whether this configuration uses polling mode.
         * <p>
         * Polling mode is enabled when interval is greater than 0.
         * In this mode, devices are polled at the specified interval.
         *
         * @return true if polling mode, false if event-driven
         */
        public boolean isPollingMode() { return interval > 0; }

        /**
         * Returns whether the PAN ID is set to auto-generate.
         *
         * @return true if PAN ID should be auto-generated
         */
        public boolean isPanIdAuto() { return DEFAULT_PAN_ID.equalsIgnoreCase( panId ); }

        /**
         * Returns whether the network key is set to auto-generate.
         *
         * @return true if network key should be auto-generated
         */
        public boolean isNetworkKeyAuto() { return DEFAULT_NETWORK_KEY.equalsIgnoreCase( networkKey ); }

        //--------------------------------------------------------------------//
        // VALIDATION METHODS
        //--------------------------------------------------------------------//

        /**
         * Checks if the given dongle type is valid.
         *
         * @param dongle The dongle type to validate (case-insensitive)
         * @return true if valid, false otherwise
         */
        public static boolean isValidDongle( String dongle )
        {
            if( dongle == null )
                return false;

            String d = dongle.toLowerCase();

            return DONGLE_EMBER.equals( d ) || DONGLE_CC2531.equals( d ) ||
                   DONGLE_CONBEE.equals( d ) || DONGLE_XBEE.equals( d ) ||
                   DONGLE_TELEGESIS.equals( d );
        }

        /**
         * Checks if the given channel is valid.
         *
         * @param channel The channel to validate (11-26)
         * @return true if valid, false otherwise
         */
        public static boolean isValidChannel( int channel )
        {
            return channel >= CHANNEL_MIN && channel <= CHANNEL_MAX;
        }

        /**
         * Checks if the given PAN ID string is valid.
         * <p>
         * Valid formats:
         * <ul>
         *   <li>"auto" (case-insensitive)</li>
         *   <li>1-4 hex digits (e.g., "1234", "ABCD", "0001")</li>
         * </ul>
         *
         * @param panId The PAN ID to validate
         * @return true if valid, false otherwise
         */
        public static boolean isValidPanId( String panId )
        {
            if( panId == null )
                return false;

            if( DEFAULT_PAN_ID.equalsIgnoreCase( panId ) )
                return true;

            // Must be 1-4 hex characters
            return panId.matches( "^[0-9A-Fa-f]{1,4}$" );
        }

        /**
         * Checks if the given network key string is valid.
         * <p>
         * Valid formats:
         * <ul>
         *   <li>"auto" (case-insensitive)</li>
         *   <li>32 hex digits (128-bit key)</li>
         * </ul>
         *
         * @param networkKey The network key to validate
         * @return true if valid, false otherwise
         */
        public static boolean isValidNetworkKey( String networkKey )
        {
            if( networkKey == null )
                return false;

            if( DEFAULT_NETWORK_KEY.equalsIgnoreCase( networkKey ) )
                return true;

            // Must be exactly 32 hex characters (128 bits)
            return networkKey.matches( "^[0-9A-Fa-f]{32}$" );
        }

        /**
         * Parses a PAN ID string to an integer value.
         * <p>
         * Returns -1 for "auto" to indicate auto-generation.
         *
         * @param panId The PAN ID string
         * @return The PAN ID as integer (0x0000-0xFFFF) or -1 for auto
         * @throws NumberFormatException If the string is not valid hex
         */
        public static int parsePanId( String panId )
        {
            if( panId == null || DEFAULT_PAN_ID.equalsIgnoreCase( panId ) )
                return -1;

            return Integer.parseInt( panId, 16 );
        }

        /**
         * Parses a network key string to a byte array.
         * <p>
         * Returns null for "auto" to indicate auto-generation.
         *
         * @param networkKey The network key string (32 hex chars)
         * @return The network key as byte array (16 bytes) or null for auto
         */
        public static byte[] parseNetworkKey( String networkKey )
        {
            if( networkKey == null || DEFAULT_NETWORK_KEY.equalsIgnoreCase( networkKey ) )
                return null;

            byte[] key = new byte[16];

            for( int i = 0; i < 16; i++ )
            {
                key[i] = (byte) Integer.parseInt( networkKey.substring( i * 2, i * 2 + 2 ), 16 );
            }

            return key;
        }

        //--------------------------------------------------------------------//
        // TO STRING
        //--------------------------------------------------------------------//

        @Override
        public String toString()
        {
            return "Zigbee[" + dongle + "@" + port + " ch:" + channel + " pan:" + panId + "]";
        }

        //--------------------------------------------------------------------//
        // BUILDER CLASS
        //--------------------------------------------------------------------//

        /**
         * Builder for creating Config instances with string-based parameters.
         * <p>
         * This builder accepts string values for configuration options,
         * making it convenient for use with configuration files or user input.
         *
         * <h3>Usage Example:</h3>
         * <pre>
         * // Event-driven mode (default)
         * IZigbeeClient.Config config = new IZigbeeClient.Config.Builder( "/dev/ttyUSB0", "ember" )
         *     .channel( 15 )
         *     .panId( "1234" )
         *     .permitJoin( true )
         *     .build();
         *
         * // Polling mode (poll every 5 seconds)
         * IZigbeeClient.Config config = new IZigbeeClient.Config.Builder( "/dev/ttyUSB0", "conbee" )
         *     .channel( 20 )
         *     .interval( 5000 )
         *     .build();
         * </pre>
         */
        public static class Builder
        {
            private final String port;
            private final String dongle;
            private int     channel    = DEFAULT_CHANNEL;
            private String  panId      = DEFAULT_PAN_ID;
            private String  networkKey = DEFAULT_NETWORK_KEY;
            private boolean permitJoin = DEFAULT_PERMIT_JOIN;
            private int     interval   = DEFAULT_INTERVAL;
            private int     timeout    = DEFAULT_TIMEOUT;

            /**
             * Creates a new builder for the specified port and dongle.
             *
             * @param port   Serial port for the Zigbee dongle (required)
             * @param dongle Dongle type (required)
             */
            public Builder( String port, String dongle )
            {
                this.port   = port;
                this.dongle = dongle;
            }

            /**
             * Sets the Zigbee channel.
             *
             * @param channel Zigbee channel 11-26 (default: 11)
             * @return This builder
             */
            public Builder channel( int channel )
            {
                this.channel = channel;
                return this;
            }

            /**
             * Sets the PAN ID.
             *
             * @param panId PAN ID as hex string or "auto" (default: "auto")
             * @return This builder
             */
            public Builder panId( String panId )
            {
                this.panId = panId;
                return this;
            }

            /**
             * Sets the network key.
             *
             * @param networkKey 128-bit key as 32 hex chars or "auto" (default: "auto")
             * @return This builder
             */
            public Builder networkKey( String networkKey )
            {
                this.networkKey = networkKey;
                return this;
            }

            /**
             * Sets whether to allow new devices to join.
             *
             * @param permitJoin true to allow joining (default: false)
             * @return This builder
             */
            public Builder permitJoin( boolean permitJoin )
            {
                this.permitJoin = permitJoin;
                return this;
            }

            /**
             * Sets the polling interval.
             * <p>
             * The interval determines how data is read:
             * <ul>
             *   <li>0 (default): Event-driven - devices report changes automatically</li>
             *   <li>&gt; 0: Polling mode - devices are polled every N milliseconds (min 500ms)</li>
             * </ul>
             *
             * @param interval Polling interval in ms (0 = event-driven, &gt;= 500 = polling)
             * @return This builder
             */
            public Builder interval( int interval )
            {
                this.interval = interval;
                return this;
            }

            /**
             * Sets the timeout for operations.
             *
             * @param timeout Timeout in milliseconds (default: 10000, min: 1000)
             * @return This builder
             */
            public Builder timeout( int timeout )
            {
                this.timeout = timeout;
                return this;
            }

            /**
             * Builds the Config instance.
             *
             * @return A new Config with the specified parameters
             * @throws IllegalArgumentException If port or dongle is null or empty
             */
            public Config build()
            {
                return new Config( port, dongle, channel, panId, networkKey,
                                   permitJoin, interval, timeout );
            }
        }
    }

    //------------------------------------------------------------------------//
    // LIFECYCLE METHODS
    //------------------------------------------------------------------------//

    /**
     * Opens the Zigbee coordinator and starts the network.
     * <p>
     * This method:
     * <ol>
     *   <li>Initializes the serial port for the dongle</li>
     *   <li>Configures the Zigbee network (channel, PAN ID, network key)</li>
     *   <li>Starts the network manager</li>
     *   <li>Discovers existing devices</li>
     * </ol>
     * <p>
     * After calling this method, the client will receive device events
     * through the listener.
     *
     * @throws IOException If the dongle cannot be opened or network fails to start
     */
    void open() throws IOException;

    /**
     * Closes the Zigbee coordinator and stops the network.
     * <p>
     * After calling this method, no more listener notifications will be sent.
     * The method is safe to call multiple times.
     */
    void close();

    /**
     * Returns whether the Zigbee network is currently running.
     *
     * @return true if the network is running, false otherwise
     */
    boolean isRunning();

    //------------------------------------------------------------------------//
    // NETWORK MANAGEMENT
    //------------------------------------------------------------------------//

    /**
     * Enables or disables the permit join mode.
     * <p>
     * When permit join is enabled, new devices can join the network.
     * This is typically enabled temporarily (e.g., 60-255 seconds) when
     * pairing new devices.
     *
     * @param durationSeconds Duration in seconds (0 = disable, 255 = until disabled)
     * @throws IOException If the command fails
     */
    void permitJoin( int durationSeconds ) throws IOException;

    /**
     * Returns a list of all devices currently in the network.
     * <p>
     * Each device is represented as a Map containing:
     * <ul>
     *   <li>"ieee" - IEEE address (String)</li>
     *   <li>"network" - Network address (Integer)</li>
     *   <li>"type" - Device type: "coordinator", "router", "end_device" (String)</li>
     *   <li>"endpoints" - List of endpoint numbers (List&lt;Integer&gt;)</li>
     *   <li>"manufacturer" - Manufacturer name if available (String)</li>
     *   <li>"model" - Model identifier if available (String)</li>
     * </ul>
     *
     * @return List of device information maps
     */
    List<Map<String,Object>> getDevices();

    /**
     * Returns detailed information about a specific device.
     * <p>
     * The returned map contains:
     * <ul>
     *   <li>"ieee" - IEEE address (String)</li>
     *   <li>"network" - Network address (Integer)</li>
     *   <li>"type" - Device type (String)</li>
     *   <li>"endpoints" - List of endpoints with their clusters (List&lt;Map&gt;)</li>
     *   <li>"manufacturer" - Manufacturer name (String)</li>
     *   <li>"model" - Model identifier (String)</li>
     *   <li>"online" - Whether device is responding (Boolean)</li>
     *   <li>"lastSeen" - Timestamp of last communication (Long)</li>
     * </ul>
     *
     * @param ieeeAddress The device's IEEE address
     * @return Device information map, or null if device not found
     */
    Map<String,Object> getDeviceInfo( String ieeeAddress );

    //------------------------------------------------------------------------//
    // DATA OPERATIONS
    //------------------------------------------------------------------------//

    /**
     * Reads a value from a device's cluster attribute.
     * <p>
     * This method sends a ZCL Read Attributes command to the device
     * and waits for the response.
     *
     * @param ieeeAddress The device's IEEE address
     * @param endpoint    The endpoint number (1-240)
     * @param clusterId   The cluster ID (e.g., 0x0006 for On/Off)
     * @return The attribute value, type depends on the cluster
     * @throws IOException If the read operation fails or times out
     */
    Object read( String ieeeAddress, int endpoint, int clusterId ) throws IOException;

    /**
     * Writes a value to a device's cluster attribute or sends a command.
     * <p>
     * This method sends a ZCL command to the device. The exact command
     * depends on the cluster:
     * <ul>
     *   <li>On/Off cluster: Boolean value triggers On/Off commands</li>
     *   <li>Level Control: Integer 0-254 sets the level</li>
     *   <li>Color Control: Map with "hue", "saturation", "colorTemp"</li>
     * </ul>
     *
     * @param ieeeAddress The device's IEEE address
     * @param endpoint    The endpoint number (1-240)
     * @param clusterId   The cluster ID
     * @param value       The value to write (type depends on cluster)
     * @throws IOException If the write operation fails
     */
    void write( String ieeeAddress, int endpoint, int clusterId, Object value ) throws IOException;

    /**
     * Sends a raw command to a device.
     * <p>
     * This method allows sending arbitrary ZCL commands when the
     * standard read/write operations are not sufficient.
     *
     * @param ieeeAddress The device's IEEE address
     * @param endpoint    The endpoint number
     * @param clusterId   The cluster ID
     * @param commandId   The ZCL command ID
     * @param payload     The command payload (may be null)
     * @throws IOException If the command fails
     */
    void sendCommand( String ieeeAddress, int endpoint, int clusterId,
                      int commandId, byte[] payload ) throws IOException;

    //------------------------------------------------------------------------//
    // CONVENIENCE METHODS
    //------------------------------------------------------------------------//

    /**
     * Sets the On/Off state of a device.
     * <p>
     * This is a convenience method equivalent to:
     * <pre>
     * write( ieeeAddress, endpoint, CLUSTER_ONOFF, on );
     * </pre>
     *
     * @param ieeeAddress The device's IEEE address
     * @param endpoint    The endpoint number
     * @param on          true for On, false for Off
     * @throws IOException If the command fails
     */
    void setOnOff( String ieeeAddress, int endpoint, boolean on ) throws IOException;

    /**
     * Sets the brightness level of a dimmable device.
     * <p>
     * This is a convenience method equivalent to:
     * <pre>
     * write( ieeeAddress, endpoint, CLUSTER_LEVEL_CONTROL, level );
     * </pre>
     *
     * @param ieeeAddress The device's IEEE address
     * @param endpoint    The endpoint number
     * @param level       Brightness level 0-254 (0 = off, 254 = full brightness)
     * @throws IOException If the command fails
     */
    void setLevel( String ieeeAddress, int endpoint, int level ) throws IOException;

    /**
     * Toggles the On/Off state of a device.
     * <p>
     * Sends the Toggle command to flip the current state.
     *
     * @param ieeeAddress The device's IEEE address
     * @param endpoint    The endpoint number
     * @throws IOException If the command fails
     */
    void toggle( String ieeeAddress, int endpoint ) throws IOException;
}
