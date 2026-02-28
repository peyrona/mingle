
package com.peyrona.mingle.controllers.serial;

import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Set;

/**
 * Interface for serial port client implementations.
 * <p>
 * This interface defines the contract for asynchronous serial port communication.
 * Implementations should handle connection management, data reading/writing,
 * and background reading internally.
 *
 * <h3>Reading Modes:</h3>
 * Serial communication can operate in two modes:
 * <ul>
 *   <li><b>AUTO (default)</b>: A background reader thread continuously reads
 *       from the serial port and notifies the listener when data is available.
 *       This provides real-time event-driven communication. This mode is used
 *       when no interval is configured (interval = 0).</li>
 *   <li><b>INTERVAL</b>: Data is read at a fixed interval (in milliseconds).
 *       An internal timer triggers reads every N milliseconds. Use this mode
 *       when you want periodic polling without continuous background reading.
 *       Minimum interval is 500ms to prevent overloading devices.</li>
 * </ul>
 *
 * <h3>Lifecycle:</h3>
 * <ol>
 *   <li>Create instance with configuration parameters and listener</li>
 *   <li>Call {@link #open()} to connect and start reading (background or interval-based)</li>
 *   <li>Use {@link #write(String)} for sending data</li>
 *   <li>Call {@link #close()} to stop reading and disconnect</li>
 * </ol>
 *
 * <h3>Thread Safety:</h3>
 * Implementations must be thread-safe. {@link #open()} starts either a background
 * reading thread (AUTO mode) or an interval timer (INTERVAL mode), and
 * {@link #write(String)} may be called from any thread.
 *
 * @author Francisco José Morero Peyrona
 * @see <a href="https://github.com/peyrona/mingle">Mingle project</a>
 */
public interface ISerialClient
{
    //------------------------------------------------------------------------//
    // INNER INTERFACES
    //------------------------------------------------------------------------//

    /**
     * Listener interface for receiving serial port events.
     * <p>
     * Implementations receive notifications when:
     * <ul>
     *   <li>Data is received from the serial port (via {@link #onMessage(String)})</li>
     *   <li>An error occurs during communication (via {@link #onError(Exception)})</li>
     *   <li>The connection is established (via {@link #onConnected()})</li>
     *   <li>The connection is closed (via {@link #onDisconnected()})</li>
     * </ul>
     */
    interface Listener
    {
        /**
         * Called when data is received from the serial port.
         * <p>
         * Data is delivered as complete lines, terminated by the configured
         * line terminator. The terminator itself is not included in the message.
         *
         * @param message The data received (without terminator)
         */
        void onMessage( String message );

        /**
         * Called when an error occurs during serial communication.
         * <p>
         * Common error types include:
         * <ul>
         *   <li>Port not found or access denied</li>
         *   <li>Connection lost</li>
         *   <li>Read/write failures</li>
         * </ul>
         *
         * @param exc The exception that occurred
         */
        void onError( Exception exc );

        /**
         * Called when the serial port connection is established.
         */
        void onConnected();

        /**
         * Called when the serial port connection is closed.
         */
        void onDisconnected();
    }

    //------------------------------------------------------------------------//
    // CONFIGURATION CLASS
    //------------------------------------------------------------------------//

    /**
     * Configuration class for serial port parameters.
     * <p>
     * Encapsulates all configuration options for a serial port connection,
     * including default values, validation, and string-to-constant conversion.
     * <p>
     * Use the {@link Builder} for convenient construction with string-based
     * parameters (e.g., from configuration files).
     *
     * <h3>Reading Modes:</h3>
     * <ul>
     *   <li><b>AUTO (interval = 0)</b>: Continuous background reading. Data arrives
     *       in real-time via the listener. This is the default mode.</li>
     *   <li><b>INTERVAL (interval &gt; 0)</b>: Periodic reading at the specified
     *       interval in milliseconds. Minimum interval is 500ms.</li>
     * </ul>
     *
     * <h3>Usage Example:</h3>
     * <pre>
     * // AUTO mode (default) - continuous reading
     * ISerialClient.Config config = new ISerialClient.Config.Builder( "/dev/ttyUSB0" )
     *     .baudrate( 115200 )
     *     .parity( "even" )
     *     .build();
     *
     * // INTERVAL mode - read every 1000ms
     * ISerialClient.Config config = new ISerialClient.Config.Builder( "/dev/ttyUSB0" )
     *     .baudrate( 115200 )
     *     .interval( 1000 )
     *     .build();
     * </pre>
     */
    public static class Config
    {
        //--------------------------------------------------------------------//
        // PARITY CONSTANTS
        //--------------------------------------------------------------------//

        /** No parity bit. */
        public static final int PARITY_NONE  = 0;
        /** Odd parity: parity bit set so total 1-bits is odd. */
        public static final int PARITY_ODD   = 1;
        /** Even parity: parity bit set so total 1-bits is even. */
        public static final int PARITY_EVEN  = 2;
        /** Mark parity: parity bit always 1. */
        public static final int PARITY_MARK  = 3;
        /** Space parity: parity bit always 0. */
        public static final int PARITY_SPACE = 4;

        //--------------------------------------------------------------------//
        // STOP BITS CONSTANTS
        //--------------------------------------------------------------------//

        /** One stop bit. */
        public static final int STOPBITS_1   = 1;
        /** Two stop bits. */
        public static final int STOPBITS_2   = 2;
        /** One and a half stop bits (rarely used, mainly with 5 data bits). */
        public static final int STOPBITS_1_5 = 3;

        //--------------------------------------------------------------------//
        // FLOW CONTROL CONSTANTS
        //--------------------------------------------------------------------//

        /** No flow control. */
        public static final int FLOWCONTROL_NONE    = 0;
        /** Hardware flow control using RTS/CTS signals. */
        public static final int FLOWCONTROL_RTSCTS  = 1;
        /** Software flow control using XON/XOFF characters. */
        public static final int FLOWCONTROL_XONXOFF = 2;

        //--------------------------------------------------------------------//
        // DEFAULT VALUES
        //--------------------------------------------------------------------//

        /** Default baud rate: 9600 bps. */
        public static final int    DEFAULT_BAUDRATE    = 9600;
        /** Default data bits: 8. */
        public static final int    DEFAULT_DATABITS    = 8;
        /** Default stop bits: 1. */
        public static final int    DEFAULT_STOPBITS    = STOPBITS_1;
        /** Default parity: none. */
        public static final int    DEFAULT_PARITY      = PARITY_NONE;
        /** Default flow control: none. */
        public static final int    DEFAULT_FLOWCONTROL = FLOWCONTROL_NONE;
        /** Default line terminator: newline. */
        public static final String DEFAULT_TERMINATOR  = "\n";
        /** Default read timeout: 5000 milliseconds. */
        public static final int    DEFAULT_TIMEOUT     = 5000;
        /** Minimum allowed timeout: 100 milliseconds. */
        public static final int    MIN_TIMEOUT         = 100;
        /** Default character encoding: UTF-8. */
        public static final Charset DEFAULT_ENCODING    = StandardCharsets.UTF_8;
        /** Maximum buffer size in bytes (8KB) to prevent unbounded growth. */
        public static final int    MAX_BUFFER_SIZE     = 8192;

        //--------------------------------------------------------------------//
        // INTERVAL CONSTANTS
        //--------------------------------------------------------------------//

        /** Default interval: 0 (AUTO mode - continuous background reading). */
        public static final int    DEFAULT_INTERVAL    = 0;
        /** Minimum allowed interval: 500 milliseconds (to prevent device overload). */
        public static final int    MIN_INTERVAL        = 500;

        //--------------------------------------------------------------------//
        // RECONNECTION DEFAULTS
        //--------------------------------------------------------------------//

        /** Default reconnection enabled: true. */
        public static final boolean DEFAULT_RECONNECT       = true;
        /** Default maximum retries: 7 attempts. */
        public static final int     DEFAULT_MAX_RETRIES     = 7;
        /** Hard-coded initial reconnection delay: 1000 milliseconds. */
        public static final int     RECONNECT_DELAY         = 1000;
        /** Hard-coded maximum reconnection delay: 64000 milliseconds (1000 * 2^6 for 7 attempts). */
        public static final int     RECONNECT_MAX_DELAY     = 64000;

        //--------------------------------------------------------------------//
        // VALID VALUES
        //--------------------------------------------------------------------//

        /** Valid baud rates. */
        public static final int[] VALID_BAUDRATES = {
            300, 1200, 2400, 4800, 9600, 19200,
            38400, 57600, 115200, 230400, 460800, 921600
        };

        /** Valid data bits values. */
        public static final int[] VALID_DATABITS = { 5, 6, 7, 8 };

        /** Valid stop bits values (as doubles for user input). */
        public static final double[] VALID_STOPBITS = { 1.0, 1.5, 2.0 };

        /** Valid parity string values. */
        public static final String VALID_PARITIES = "none,odd,even,mark,space";

        /** Valid flow control string values. */
        public static final String VALID_FLOWCONTROLS = "none,rtscts,xonxoff";

        //--------------------------------------------------------------------//
        // INSTANCE FIELDS
        //--------------------------------------------------------------------//

        private final String    port;
        private final int       baudrate;
        private final int       databits;
        private final int       stopbits;
        private final int       parity;
        private final int       flowcontrol;
        private final String    terminator;
        private final int       timeout;
        private final int       interval;
        private final Charset   encoding;

        // Reconnection configuration
        private final boolean   reconnect;
        private final int       maxRetries;

        //--------------------------------------------------------------------//
        // CONSTRUCTORS
        //--------------------------------------------------------------------//

        /**
         * Creates a new configuration with default values (9600 8N1, AUTO mode).
         *
         * @param port Serial port name (e.g., "/dev/ttyUSB0", "COM3")
         * @throws IllegalArgumentException If port is null or empty
         */
        public Config( String port )
        {
            this( port, DEFAULT_BAUDRATE, DEFAULT_DATABITS, DEFAULT_STOPBITS,
                  DEFAULT_PARITY, DEFAULT_FLOWCONTROL, DEFAULT_TERMINATOR, DEFAULT_TIMEOUT, DEFAULT_INTERVAL, DEFAULT_ENCODING,
                  DEFAULT_RECONNECT, DEFAULT_MAX_RETRIES );
        }

        /**
         * Creates a new configuration with all parameters specified.
         *
         * @param port        Serial port name
         * @param baudrate    Baud rate (e.g., 9600, 115200)
         * @param databits    Data bits (5, 6, 7, or 8)
         * @param stopbits    Stop bits constant (STOPBITS_1, STOPBITS_2, or STOPBITS_1_5)
         * @param parity      Parity constant (PARITY_NONE, PARITY_ODD, PARITY_EVEN, PARITY_MARK, PARITY_SPACE)
         * @param flowcontrol Flow control constant (FLOWCONTROL_NONE, FLOWCONTROL_RTSCTS, FLOWCONTROL_XONXOFF)
         * @param terminator  Line terminator for reading (must not be null or empty)
         * @param timeout     Read timeout in milliseconds (minimum 100)
         * @param interval    Read interval in milliseconds (0 = AUTO mode, &gt;= 500 = INTERVAL mode)
         * @param encoding    Character encoding for text conversion
         * @param reconnect   Enable/disable automatic reconnection
         * @param maxRetries  Maximum reconnection attempts (-1 = unlimited)
         * @throws IllegalArgumentException If port is null or empty, or terminator is null or empty
         */
        public Config( String port, int baudrate, int databits, int stopbits,
                       int parity, int flowcontrol, String terminator, int timeout,
                       int interval, Charset encoding,
                       boolean reconnect, int maxRetries )
        {
            if( port == null || port.trim().isEmpty() )
                throw new IllegalArgumentException( "Port cannot be null or empty" );

            if( terminator == null || terminator.isEmpty() )
                throw new IllegalArgumentException( "Terminator cannot be null or empty" );

            this.port        = port;
            this.baudrate    = baudrate;
            this.databits    = databits;
            this.stopbits    = stopbits;
            this.parity      = parity;
            this.flowcontrol = flowcontrol;
            this.terminator  = terminator;
            this.timeout     = Math.max( MIN_TIMEOUT, timeout );
            this.interval    = (interval > 0) ? Math.max( MIN_INTERVAL, interval ) : DEFAULT_INTERVAL;
            this.encoding    = (encoding != null) ? encoding : DEFAULT_ENCODING;
            this.reconnect   = reconnect;
            this.maxRetries  = maxRetries;
        }

        //--------------------------------------------------------------------//
        // GETTERS
        //--------------------------------------------------------------------//

        public String    getPort()            { return port; }
        public int       getBaudrate()        { return baudrate; }
        public int       getDatabits()        { return databits; }
        public int       getStopbits()        { return stopbits; }
        public int       getParity()          { return parity; }
        public int       getFlowcontrol()     { return flowcontrol; }
        public String    getTerminator()      { return terminator; }
        public int       getTimeout()         { return timeout; }
        public int       getInterval()        { return interval; }
        public Charset   getEncoding()        { return encoding; }
        public boolean   isReconnectEnabled() { return reconnect; }
        public int       getMaxRetries()      { return maxRetries; }

        /**
         * Returns whether this configuration uses AUTO mode (continuous reading).
         * <p>
         * AUTO mode is enabled when interval is 0 (or not specified).
         *
         * @return true if AUTO mode, false if INTERVAL mode
         */
        public boolean   isAutoMode()         { return interval <= 0; }

        /**
         * Returns whether this configuration uses INTERVAL mode (periodic reading).
         * <p>
         * INTERVAL mode is enabled when interval is greater than 0.
         *
         * @return true if INTERVAL mode, false if AUTO mode
         */
        public boolean   isIntervalMode()     { return interval > 0; }

        //--------------------------------------------------------------------//
        // VALIDATION METHODS
        //--------------------------------------------------------------------//

        /**
         * Checks if the given baud rate is valid.
         *
         * @param baudrate The baud rate to validate
         * @return true if valid, false otherwise
         */
        public static boolean isValidBaudrate( int baudrate )
        {
            for( int valid : VALID_BAUDRATES )
            {
                if( valid == baudrate )
                    return true;
            }
            return false;
        }

        /**
         * Checks if the given data bits value is valid.
         *
         * @param databits The data bits to validate (5, 6, 7, or 8)
         * @return true if valid, false otherwise
         */
        public static boolean isValidDatabits( int databits )
        {
            for( int valid : VALID_DATABITS )
            {
                if( valid == databits )
                    return true;
            }
            return false;
        }

        /**
         * Checks if the given stop bits value is valid.
         *
         * @param stopbits The stop bits to validate (1, 1.5, or 2)
         * @return true if valid, false otherwise
         */
        public static boolean isValidStopbits( double stopbits )
        {
            // Use epsilon comparison for floating-point values
            final double EPSILON = 0.001;
            for( double valid : VALID_STOPBITS )
            {
                if( Math.abs( valid - stopbits ) < EPSILON )
                    return true;
            }
            return false;
        }

        /**
         * Checks if the given parity string is valid.
         *
         * @param parity The parity string to validate (case-insensitive)
         * @return true if valid, false otherwise
         */
        public static boolean isValidParity( String parity )
        {
            if( parity == null ) return false;
            String[] valid = VALID_PARITIES.split( "," );
            String p = parity.toLowerCase();
            for( String v : valid )
            {
                if( v.equals( p ) ) return true;
            }
            return false;
        }

        /**
         * Checks if the given flow control string is valid.
         *
         * @param flowcontrol The flow control string to validate (case-insensitive)
         * @return true if valid, false otherwise
         */
        public static boolean isValidFlowcontrol( String flowcontrol )
        {
            if( flowcontrol == null ) return false;
            String[] valid = VALID_FLOWCONTROLS.split( "," );
            String fc = flowcontrol.toLowerCase();
            for( String v : valid )
            {
                if( v.equals( fc ) ) return true;
            }
            return false;
        }

        //--------------------------------------------------------------------//
        // CONVERSION METHODS
        //--------------------------------------------------------------------//

        /**
         * Converts a stop bits double value to the corresponding constant.
         *
         * @param stopbits The stop bits value (1, 1.5, or 2)
         * @return STOPBITS_1, STOPBITS_1_5, or STOPBITS_2
         */
        public static int stopbitsToConstant( double stopbits )
        {
            // Use epsilon comparison for floating-point values
            final double EPSILON = 0.001;
            if( Math.abs( stopbits - 1.5 ) < EPSILON ) return STOPBITS_1_5;
            if( Math.abs( stopbits - 2.0 ) < EPSILON ) return STOPBITS_2;
            return STOPBITS_1;
        }

        /**
         * Converts a parity string to the corresponding constant.
         *
         * @param parity The parity string ("none", "odd", "even", "mark", "space")
         * @return PARITY_NONE, PARITY_ODD, PARITY_EVEN, PARITY_MARK, or PARITY_SPACE
         */
        public static int parityToConstant( String parity )
        {
            if( parity == null )
                return PARITY_NONE;

            switch( parity.toLowerCase() )
            {
                case "odd":   return PARITY_ODD;
                case "even":  return PARITY_EVEN;
                case "mark":  return PARITY_MARK;
                case "space": return PARITY_SPACE;
                default:      return PARITY_NONE;
            }
        }

        /**
         * Converts a flow control string to the corresponding constant.
         *
         * @param flowcontrol The flow control string ("none", "rtscts", "xonxoff")
         * @return FLOWCONTROL_NONE, FLOWCONTROL_RTSCTS, or FLOWCONTROL_XONXOFF
         */
        public static int flowcontrolToConstant( String flowcontrol )
        {
            if( flowcontrol == null )
                return FLOWCONTROL_NONE;

            switch( flowcontrol.toLowerCase() )
            {
                case "rtscts":  return FLOWCONTROL_RTSCTS;
                case "xonxoff": return FLOWCONTROL_XONXOFF;
                default:        return FLOWCONTROL_NONE;
            }
        }

        /**
         * Expands escape sequences in a terminator string.
         * <p>
         * Converts "\\n" to "\n", "\\r" to "\r", and "\\t" to "\t".
         *
         * @param terminator The terminator string with possible escape sequences
         * @return The terminator with escape sequences expanded
         */
        public static String expandTerminator( String terminator )
        {
            if( terminator == null )
                return DEFAULT_TERMINATOR;

            return terminator.replace( "\\n", "\n" )
                             .replace( "\\r", "\r" )
                             .replace( "\\t", "\t" );
        }

        //--------------------------------------------------------------------//
        // TO STRING
        //--------------------------------------------------------------------//

        @Override
        public String toString()
        {
            return port + " @ " + baudrate + " " + databits +
                   parityToChar( parity ) + stopbitsToString( stopbits );
        }

        /**
         * Converts a parity constant to its single-character representation.
         */
        private static char parityToChar( int p )
        {
            switch( p )
            {
                case PARITY_ODD:   return 'O';
                case PARITY_EVEN:  return 'E';
                case PARITY_MARK:  return 'M';
                case PARITY_SPACE: return 'S';
                default:           return 'N';
            }
        }

        /**
         * Converts a stop bits constant to its string representation.
         */
        private static String stopbitsToString( int s )
        {
            switch( s )
            {
                case STOPBITS_2:   return "2";
                case STOPBITS_1_5: return "1.5";
                default:           return "1";
            }
        }

        //--------------------------------------------------------------------//
        // BUILDER CLASS
        //--------------------------------------------------------------------//

        /**
         * Builder for creating Config instances with string-based parameters.
         * <p>
         * This builder accepts string values for parity and flow control,
         * and double values for stop bits, making it convenient for use
         * with configuration files or user input.
         *
         * <h3>Usage Example:</h3>
         * <pre>
         * // AUTO mode (default)
         * ISerialClient.Config config = new ISerialClient.Config.Builder( "/dev/ttyUSB0" )
         *     .baudrate( 115200 )
         *     .databits( 8 )
         *     .stopbits( 1.0 )
         *     .parity( "even" )
         *     .flowcontrol( "none" )
         *     .terminator( "\\r\\n" )
         *     .timeout( 3000 )
         *     .build();
         *
         * // INTERVAL mode (read every 1 second)
         * ISerialClient.Config config = new ISerialClient.Config.Builder( "/dev/ttyUSB0" )
         *     .baudrate( 9600 )
         *     .interval( 1000 )
         *     .build();
         * </pre>
         */
        public static class Builder
        {
            private final String port;
            private int       baudrate    = DEFAULT_BAUDRATE;
            private int       databits    = DEFAULT_DATABITS;
            private double    stopbits    = 1.0;
            private String    parity      = "none";
            private String    flowcontrol = "none";
            private String    terminator  = DEFAULT_TERMINATOR;
            private int       timeout     = DEFAULT_TIMEOUT;
            private int       interval    = DEFAULT_INTERVAL;
            private Charset   encoding    = DEFAULT_ENCODING;
            private boolean   reconnect   = DEFAULT_RECONNECT;
            private int       maxRetries  = DEFAULT_MAX_RETRIES;

            /**
             * Creates a new builder for the specified port.
             *
             * @param port Serial port name (required)
             */
            public Builder( String port )
            {
                this.port = port;
            }

            /**
             * Sets the baud rate.
             *
             * @param baudrate Baud rate (default: 9600)
             * @return This builder
             */
            public Builder baudrate( int baudrate )
            {
                this.baudrate = baudrate;
                return this;
            }

            /**
             * Sets the number of data bits.
             *
             * @param databits Data bits: 5, 6, 7, or 8 (default: 8)
             * @return This builder
             */
            public Builder databits( int databits )
            {
                this.databits = databits;
                return this;
            }

            /**
             * Sets the number of stop bits.
             *
             * @param stopbits Stop bits: 1, 1.5, or 2 (default: 1)
             * @return This builder
             */
            public Builder stopbits( double stopbits )
            {
                this.stopbits = stopbits;
                return this;
            }

            /**
             * Sets the parity mode.
             *
             * @param parity Parity: "none", "odd", "even", "mark", or "space" (default: "none")
             * @return This builder
             */
            public Builder parity( String parity )
            {
                this.parity = parity;
                return this;
            }

            /**
             * Sets the flow control mode.
             *
             * @param flowcontrol Flow control: "none", "rtscts", or "xonxoff" (default: "none")
             * @return This builder
             */
            public Builder flowcontrol( String flowcontrol )
            {
                this.flowcontrol = flowcontrol;
                return this;
            }

            /**
             * Sets the line terminator for reading.
             * <p>
             * Escape sequences (\\n, \\r, \\t) will be expanded automatically.
             *
             * @param terminator Line terminator (default: "\n")
             * @return This builder
             */
            public Builder terminator( String terminator )
            {
                this.terminator = terminator;
                return this;
            }

            /**
             * Sets the read timeout.
             *
             * @param timeout Timeout in milliseconds (default: 5000, minimum: 100)
             * @return This builder
             */
            public Builder timeout( int timeout )
            {
                this.timeout = timeout;
                return this;
            }

            /**
             * Sets the read interval for INTERVAL mode.
             * <p>
             * The interval determines how data is read from the serial port:
             * <ul>
             *   <li>0 (default): AUTO mode - background thread continuously reads data</li>
             *   <li>&gt; 0: INTERVAL mode - data is read every N milliseconds (minimum 500ms)</li>
             * </ul>
             *
             * @param interval Read interval in milliseconds (0 = AUTO, &gt;= 500 = INTERVAL)
             * @return This builder
             */
            public Builder interval( int interval )
            {
                this.interval = interval;
                return this;
            }

            /**
             * Sets the character encoding.
             *
             * @param encoding Character encoding for text conversion (default: UTF-8)
             * @return This builder
             */
            public Builder encoding( Charset encoding )
            {
                this.encoding = (encoding != null) ? encoding : DEFAULT_ENCODING;
                return this;
            }

            /**
             * Sets the character encoding by name.
             *
             * @param encodingName Character encoding name (e.g., "UTF-8", "ISO-8859-1")
             * @return This builder
             * @throws java.nio.charset.UnsupportedCharsetException If the named charset is not supported
             */
            public Builder encoding( String encodingName )
            {
                if( encodingName != null && ! encodingName.isEmpty() )
                    this.encoding = Charset.forName( encodingName );

                return this;
            }

            /**
             * Sets whether automatic reconnection is enabled.
             *
             * @param reconnect true to enable automatic reconnection (default: true)
             * @return This builder
             */
            public Builder reconnect( boolean reconnect )
            {
                this.reconnect = reconnect;
                return this;
            }

            /**
             * Sets the maximum number of reconnection attempts.
             * <p>
             * Reconnection uses exponential backoff starting at 1 second,
             * doubling with each attempt up to the maximum.
             *
             * @param maxRetries Maximum attempts, or -1 for unlimited (default: 7)
             * @return This builder
             */
            public Builder maxRetries( int maxRetries )
            {
                this.maxRetries = maxRetries;
                return this;
            }

            /**
             * Builds the Config instance.
             *
             * @return A new Config with the specified parameters
             * @throws IllegalArgumentException If port is null or empty, or terminator is null or empty
             */
            public Config build()
            {
                String expandedTerminator = expandTerminator( terminator );
                if( expandedTerminator == null || expandedTerminator.isEmpty() )
                    expandedTerminator = DEFAULT_TERMINATOR;

                return new Config(
                    port,
                    baudrate,
                    databits,
                    stopbitsToConstant( stopbits ),
                    parityToConstant( parity ),
                    flowcontrolToConstant( flowcontrol ),
                    expandedTerminator,
                    timeout,
                    interval,
                    encoding,
                    reconnect,
                    maxRetries
                );
            }
        }
    }

    //------------------------------------------------------------------------//
    // LIFECYCLE METHODS
    //------------------------------------------------------------------------//

    /**
     * Opens the serial port and starts the background reading thread.
     * <p>
     * After calling this method, the client will continuously read from the
     * serial port and notify the listener when data is received.
     *
     * @throws IOException If the port cannot be opened
     */
    void open() throws IOException;

    /**
     * Closes the serial port and stops the background reading thread.
     * <p>
     * After calling this method, no more listener notifications will be sent.
     * The method is safe to call multiple times.
     */
    void close();

    /**
     * Returns whether the serial port is currently open and connected.
     *
     * @return true if connected, false otherwise
     */
    boolean isConnected();

    //------------------------------------------------------------------------//
    // DATA OPERATIONS
    //------------------------------------------------------------------------//

    /**
     * Writes data to the serial port.
     * <p>
     * This method is thread-safe and can be called from any thread.
     * The data is written asynchronously.
     *
     * @param data The string data to write
     * @throws IOException If the write operation fails
     */
    void write( String data ) throws IOException;

    /**
     * Writes raw bytes to the serial port.
     * <p>
     * This method is thread-safe and can be called from any thread.
     *
     * @param data The byte array to write
     * @throws IOException If the write operation fails
     */
    void write( byte[] data ) throws IOException;

    /**
     * Reads all currently available data from the serial port (INTERVAL mode only).
     * <p>
     * This method reads all data currently available in the serial port's input
     * buffer, splits it by the configured terminator, and notifies the listener
     * for each complete line. Only complete lines (ending with the terminator)
     * are returned; partial data is discarded.
     * <p>
     * This method blocks for up to the configured timeout waiting for data.
     * If no data arrives before the timeout, it returns 0 without error.
     * <p>
     * <b>Important:</b> This method is used internally by the interval timer
     * in INTERVAL mode. In AUTO mode, data is read by a background thread and
     * calling this method will throw an IllegalStateException.
     *
     * @return Number of complete lines read and delivered to the listener
     * @throws IOException         If the port is not connected or read fails
     * @throws IllegalStateException If called in AUTO mode
     */
    int readOnce() throws IOException;

    //------------------------------------------------------------------------//
    // STATIC UTILITY METHODS
    //------------------------------------------------------------------------//

    /**
     * Returns a set of available serial port names on this system.
     * <p>
     * This static method can be used to discover available ports before
     * configuring the client.
     *
     * @return Set of available port names (e.g., "/dev/ttyUSB0", "COM3")
     */
    static Set<String> getAvailablePorts()
    {
        return LinuxSerialPortImpl.getAvailablePorts();
    }
}
