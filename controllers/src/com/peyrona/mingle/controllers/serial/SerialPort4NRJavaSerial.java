
package com.peyrona.mingle.controllers.serial;

import gnu.io.NRSerialPort;
import gnu.io.SerialPort;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InterruptedIOException;
import java.nio.charset.Charset;
import java.util.Set;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Serial port client implementation using the nrjavaserial library.
 * <p>
 * This class provides full support for serial port communication with configurable:
 * <ul>
 *   <li>Baud rate - standard rates from 300 to 921600</li>
 *   <li>Data bits - 5, 6, 7, or 8</li>
 *   <li>Stop bits - 1, 1.5, or 2</li>
 *   <li>Parity - none, odd, even, mark, or space</li>
 *   <li>Flow control - none, RTS/CTS, or XON/XOFF</li>
 *   <li>Read mode - AUTO (continuous) or INTERVAL (periodic)</li>
 * </ul>
 * <p>
 * This class is a pure Java wrapper for the nrjavaserial library and has no
 * dependencies on Mingle-specific classes. It can be used independently in any
 * Java application requiring serial port communication.
 *
 * <h3>Reading Modes:</h3>
 * <ul>
 *   <li><b>AUTO (default, interval=0)</b>: A background reader thread continuously
 *       reads from the serial port and notifies the listener when data arrives.
 *       This provides real-time event-driven communication with minimal latency.</li>
 *   <li><b>INTERVAL (interval&gt;0)</b>: Data is read at fixed intervals (minimum 500ms).
 *       The caller is responsible for scheduling the reads by calling {@code readOnce()}
 *       at the configured interval. This mode is useful when you want periodic
 *       polling without continuous background reading.</li>
 * </ul>
 *
 * <h3>Thread Safety:</h3>
 * This class is thread-safe. All public methods use internal locking to ensure
 * safe concurrent access from multiple threads.
 *
 * <h3>Usage Example (AUTO mode):</h3>
 * <pre>
 * ISerialClient.Config config = new ISerialClient.Config.Builder( "/dev/ttyUSB0" )
 *     .baudrate( 115200 )
 *     .build();
 *
 * ISerialClient client = new SerialPort4NRJavaSerial( config, new ISerialClient.Listener() {
 *     public void onMessage( String msg ) { System.out.println( "Received: " + msg ); }
 *     public void onError( Exception e )  { e.printStackTrace(); }
 *     public void onConnected()           { System.out.println( "Connected" ); }
 *     public void onDisconnected()        { System.out.println( "Disconnected" ); }
 * });
 *
 * client.open();
 * // Data arrives automatically via onMessage()
 * client.write( "Hello\n" );
 * client.close();
 * </pre>
 *
 * <h3>Usage Example (INTERVAL mode):</h3>
 * <pre>
 * ISerialClient.Config config = new ISerialClient.Config.Builder( "/dev/ttyUSB0" )
 *     .baudrate( 115200 )
 *     .interval( 1000 )  // Read every 1000ms
 *     .build();
 *
 * ISerialClient client = new SerialPort4NRJavaSerial( config, listener );
 * client.open();
 *
 * // In INTERVAL mode, the caller (or SerialClient) is responsible for
 * // scheduling readOnce() calls at the configured interval
 * while( running ) {
 *     int lines = client.readOnce();  // Read available data
 *     if( lines > 0 ) {
 *         System.out.println( "Read " + lines + " lines" );
 *     }
 *     Thread.sleep( config.getInterval() );
 * }
 *
 * client.close();
 * </pre>
 *
 * @author Francisco José Morero Peyrona
 * @see <a href="https://github.com/NeuronRobotics/nrjavaserial">nrjavaserial library</a>
 * @see <a href="https://github.com/peyrona/mingle">Mingle project</a>
 */
public final class SerialPort4NRJavaSerial implements ISerialClient
{
    // Parity constants (matching ISerialClient.Config values)
    private static final int PARITY_NONE  = 0;
    private static final int PARITY_ODD   = 1;
    private static final int PARITY_EVEN  = 2;
    private static final int PARITY_MARK  = 3;
    private static final int PARITY_SPACE = 4;

    // Stop bits constants (matching ISerialClient.Config values)
    private static final int STOPBITS_1   = 1;
    private static final int STOPBITS_2   = 2;
    private static final int STOPBITS_1_5 = 3;

    // Flow control constants (matching ISerialClient.Config values)
    private static final int FLOWCONTROL_NONE    = 0;
    private static final int FLOWCONTROL_RTSCTS  = 1;
    private static final int FLOWCONTROL_XONXOFF = 2;

    // Read timeout for blocking I/O (allows responsive shutdown)
    private static final int RECEIVE_TIMEOUT_MS = 100;

    // Configuration
    private final ISerialClient.Config   config;
    private final ISerialClient.Listener listener;

    // Runtime state (protected by lock)
    private final    ReentrantLock    lock         = new ReentrantLock();
    private          NRSerialPort     serial       = null;
    private          DataInputStream  inputStream  = null;
    private          DataOutputStream outputStream = null;
    private          Thread           readerThread = null;
    private volatile boolean          isRunning    = false;

    // Buffer for POLL mode (persisted between calls) - uses bytes for proper encoding
    private final FastByteArrayOutputStream pollBuffer = new FastByteArrayOutputStream();

    // Terminator as bytes for efficient comparison
    private byte[] terminatorBytes = null;

    // Reconnection state
    private volatile int  currentRetryCount = 0;
    private volatile long currentRetryDelay = 0;
    private volatile long lastReconnectTime = 0;

    /**
     * Specialized ByteArrayOutputStream that exposes internal buffer
     * to avoid array copying during suffix checks.
     */
    private static class FastByteArrayOutputStream extends ByteArrayOutputStream
    {
        public FastByteArrayOutputStream()
        {
            super();
        }

        public FastByteArrayOutputStream( int size )
        {
            super( size );
        }

        /** Returns the internal buffer (not a copy). */
        public byte[] getBuffer()
        {
            return buf;
        }

        /** Returns the valid count of bytes in the buffer. */
        public int getCount()
        {
            return count;
        }
        
        /** Resets the buffer but keeps the array allocated (standard behavior). */
        @Override
        public void reset()
        {
            super.reset();
        }
    }

    //------------------------------------------------------------------------//
    // CONSTRUCTOR
    //------------------------------------------------------------------------//

    /**
     * Creates a new serial port client with the specified configuration.
     *
     * @param config   Serial port configuration (port name, baud rate, etc.)
     * @param listener Callback listener for messages and errors (must not be null)
     * @throws IllegalArgumentException If config or listener is null
     */
    public SerialPort4NRJavaSerial( ISerialClient.Config config, ISerialClient.Listener listener )
    {
        if( config == null )
            throw new IllegalArgumentException( "Config cannot be null" );

        if( listener == null )
            throw new IllegalArgumentException( "Listener cannot be null" );

        this.config   = config;
        this.listener = listener;
    }

    //------------------------------------------------------------------------//
    // ISerialClient IMPLEMENTATION
    //------------------------------------------------------------------------//

    @Override
    public void open() throws IOException
    {
        lock.lock();

        try
        {
            if( isConnected() )
                return;

            // Initialize retry state
            currentRetryCount = 0;
            currentRetryDelay = ISerialClient.Config.RECONNECT_DELAY;
            lastReconnectTime = 0;

            initializeResources();

            // Start reader thread only in AUTO mode (interval = 0)
            if( config.isAutoMode() )
            {
                isRunning = true;
                readerThread = new Thread( this::readerLoop, "SerialReader-" + config.getPort() );
                readerThread.setDaemon( true );
                readerThread.start();
            }
            else
            {
                // INTERVAL mode - no background thread, caller manages timing
                isRunning = false;
            }

            // Notify listener
            listener.onConnected();
        }
        catch( IOException ex )
        {
            closeInternal();
            throw ex;
        }
        catch( Exception ex )
        {
            closeInternal();
            throw new IOException( "Failed to open serial port: " + ex.getMessage(), ex );
        }
        finally
        {
            lock.unlock();
        }
    }

    /**
     * Initializes serial port resources (connection, streams).
     * Must be called while holding the lock.
     *
     * @throws IOException If the port cannot be opened or configured
     */
    private void initializeResources() throws IOException
    {
        // Create and connect serial port
        serial = new NRSerialPort( config.getPort(), config.getBaudrate() );

        if( ! serial.connect() )
        {
            serial = null;
            throw new IOException( "Failed to connect to port: " + config.getPort() );
        }

        // Reset poll buffer and initialize terminator bytes
        pollBuffer.reset();
        terminatorBytes = config.getTerminator().getBytes( config.getEncoding() );

        try
        {
            // Configure port parameters
            configurePort();

            // Get I/O streams
            inputStream  = new DataInputStream( serial.getInputStream() );
            outputStream = new DataOutputStream( serial.getOutputStream() );
        }
        catch( Exception ex )
        {
            // Clean up partial initialization
            if( serial != null )
            {
                serial.disconnect();
                serial = null;
            }
            inputStream = null;
            outputStream = null;
            throw new IOException( "Failed to configure serial port: " + ex.getMessage(), ex );
        }
    }

    /**
     * Ensures the serial port is connected, attempting reconnection if needed.
     * Uses exponential backoff between reconnection attempts.
     * Must be called while holding the lock.
     *
     * @throws IOException If reconnection fails or max retries exceeded
     */
    private void ensureConnected() throws IOException
    {
        if( isConnected() )
        {
            // Reset retry state on successful connection
            currentRetryCount = 0;
            currentRetryDelay = ISerialClient.Config.RECONNECT_DELAY;
            return;
        }

        if( ! config.isReconnectEnabled() )
            throw new IOException( "Serial port disconnected and reconnection disabled" );

        // Check max retries
        int maxRetries = config.getMaxRetries();
        if( maxRetries >= 0 && currentRetryCount >= maxRetries )
            throw new IOException( "Max reconnection attempts (" + maxRetries + ") exceeded" );

        // Check if enough time has passed since last attempt (exponential backoff)
        long now = System.currentTimeMillis();
        if( now - lastReconnectTime < currentRetryDelay )
            throw new IOException( "Reconnection attempt too soon, waiting " +
                                   (currentRetryDelay - (now - lastReconnectTime)) + "ms" );

        // Attempt reconnection
        lastReconnectTime = now;
        currentRetryCount++;

        try
        {
            initializeResources();

            // Success - reset retry state
            currentRetryCount = 0;
            currentRetryDelay = ISerialClient.Config.RECONNECT_DELAY;
            listener.onConnected();
        }
        catch( IOException ex )
        {
            // Increase delay for next attempt (exponential backoff)
            currentRetryDelay = Math.min(
                currentRetryDelay * 2,
                ISerialClient.Config.RECONNECT_MAX_DELAY
            );

            throw new IOException( "Reconnection attempt " + currentRetryCount +
                                   " failed: " + ex.getMessage() +
                                   ". Next attempt in " + currentRetryDelay + "ms", ex );
        }
    }

    /**
     * Handles connection errors by closing resources and preparing for reconnection.
     * Must be called while holding the lock.
     *
     * @param exc The exception that caused the connection error
     */
    private void handleConnectionError( Exception exc )
    {
        closeResourcesWithoutNotify();
        listener.onError( exc );
    }

    /**
     * Closes serial port resources without notifying listener of disconnect.
     * Used during error handling to prepare for reconnection.
     * Must be called while holding the lock.
     */
    private void closeResourcesWithoutNotify()
    {
        // Close input stream
        if( inputStream != null )
        {
            try { inputStream.close(); } catch( IOException ignored ) { }
            inputStream = null;
        }

        // Close output stream
        if( outputStream != null )
        {
            try { outputStream.close(); } catch( IOException ignored ) { }
            outputStream = null;
        }

        // Disconnect serial port
        if( serial != null )
        {
            serial.disconnect();
            serial = null;
        }
    }

    @Override
    public void close()
    {
        lock.lock();
        try
        {
            if( !isConnected() )
                return;

            closeInternal();
            listener.onDisconnected();
        }
        finally
        {
            lock.unlock();
        }
    }

    @Override
    public boolean isConnected()
    {
        return serial != null && serial.isConnected();
    }

    @Override
    public void write( String data ) throws IOException
    {
        if( data == null )
            return;

        write( data.getBytes() );
    }

    @Override
    public void write( byte[] data ) throws IOException
    {
        if( data == null || data.length == 0 )
            return;

        lock.lock();
        try
        {
            ensureConnected();

            if( outputStream == null )
                throw new IOException( "Serial port is not connected" );

            outputStream.write( data );
            outputStream.flush();
        }
        catch( IOException ex )
        {
            handleConnectionError( ex );
            throw ex;
        }
        finally
        {
            lock.unlock();
        }
    }

    /**
     * Performs a blocking read operation until data arrives or timeout occurs.
     * <p>
     * This method continuously reads bytes from the serial port until either:
     * <ul>
     *   <li>A complete line (terminated by the configured terminator) is received</li>
     *   <li>The read timeout (from config) is reached with no data</li>
     *   <li>An IOException occurs</li>
     * </ul>
     * Only complete lines are returned; partial data (data before a terminator)
     * is buffered and will be returned when terminator arrives.
     *
     * @return Number of complete lines read and delivered to the listener
     * @throws IllegalStateException If called in AUTO mode (use background listener instead)
     * @throws IOException         If the port is not connected or read fails
     */
    @Override
    public int readOnce() throws IOException
    {
        if( config.isAutoMode() )
            throw new IllegalStateException( "readOnce() cannot be called in AUTO mode (interval=0)" );

        lock.lock();

        try
        {
            ensureConnected();

            if( inputStream == null )
                throw new IOException( "Serial port is not connected" );

            int linesRead = 0;
            Charset encoding = config.getEncoding();
            int maxBufferSize = ISerialClient.Config.MAX_BUFFER_SIZE;
            long startTime = System.currentTimeMillis();
            long timeout = config.getTimeout();

            while( true )
            {
                // Check for timeout to avoid infinite loop if data streams continuously
                if( System.currentTimeMillis() - startTime > timeout )
                    break;

                int byteRead = inputStream.read();

                if( byteRead == -1 )
                    break;

                pollBuffer.write( byteRead );

                // Check buffer size limit - discard oldest data if exceeded
                if( pollBuffer.size() > maxBufferSize )
                {
                    byte[] data = pollBuffer.toByteArray();
                    int discardSize = data.length - ( maxBufferSize / 2 );  // Keep half the buffer
                    pollBuffer.reset();
                    pollBuffer.write( data, discardSize, data.length - discardSize );
                }

                // Check for terminator
                if( checkBufferEndsWith( pollBuffer, terminatorBytes ) )
                {
                    byte[] data = pollBuffer.toByteArray();
                    int lineLength = data.length - terminatorBytes.length;

                    if( lineLength > 0 )
                    {
                        String line = new String( data, 0, lineLength, encoding );
                        listener.onMessage( line );
                        linesRead++;
                    }

                    pollBuffer.reset();
                }
            }

            return linesRead;
        }
        catch( InterruptedIOException ex )
        {
            // Timeout occurred - return normally with current count
            return 0;
        }
        catch( IOException ex )
        {
            handleConnectionError( ex );
            throw ex;
        }
        finally
        {
            lock.unlock();
        }
    }

    //------------------------------------------------------------------------//
    // PORT CONFIGURATION
    //------------------------------------------------------------------------//

    /**
     * Configures the serial port parameters (data bits, stop bits, parity, flow control).
     *
     * @throws Exception If configuration fails
     */
    private void configurePort() throws Exception
    {
        SerialPort port = serial.getSerialPortInstance();

        // Convert stop bits to library constant
        int stopBits;
        switch( config.getStopbits() )
        {
            case STOPBITS_1:   stopBits = SerialPort.STOPBITS_1;   break;
            case STOPBITS_2:   stopBits = SerialPort.STOPBITS_2;   break;
            case STOPBITS_1_5: stopBits = SerialPort.STOPBITS_1_5; break;
            default:           stopBits = SerialPort.STOPBITS_1;   break;
        }

        // Convert parity to library constant
        int parity;
        switch( config.getParity() )
        {
            case PARITY_NONE:  parity = SerialPort.PARITY_NONE;  break;
            case PARITY_ODD:   parity = SerialPort.PARITY_ODD;   break;
            case PARITY_EVEN:  parity = SerialPort.PARITY_EVEN;  break;
            case PARITY_MARK:  parity = SerialPort.PARITY_MARK;  break;
            case PARITY_SPACE: parity = SerialPort.PARITY_SPACE; break;
            default:           parity = SerialPort.PARITY_NONE;  break;
        }

        // Set serial port parameters
        port.setSerialPortParams( config.getBaudrate(), config.getDatabits(), stopBits, parity );

        // Convert and set flow control
        int flowControl;
        switch( config.getFlowcontrol() )
        {
            case FLOWCONTROL_NONE:
                flowControl = SerialPort.FLOWCONTROL_NONE;
                break;
            case FLOWCONTROL_RTSCTS:
                flowControl = SerialPort.FLOWCONTROL_RTSCTS_IN | SerialPort.FLOWCONTROL_RTSCTS_OUT;
                break;
            case FLOWCONTROL_XONXOFF:
                flowControl = SerialPort.FLOWCONTROL_XONXOFF_IN | SerialPort.FLOWCONTROL_XONXOFF_OUT;
                break;
            default:
                flowControl = SerialPort.FLOWCONTROL_NONE;
                break;
        }

        port.setFlowControlMode( flowControl );

        // Enable receive timeout for blocking I/O (allows responsive shutdown)
        port.enableReceiveTimeout( RECEIVE_TIMEOUT_MS );
    }

    //------------------------------------------------------------------------//
    // READER THREAD
    //------------------------------------------------------------------------//

    /**
     * Background thread that continuously reads from the serial port
     * and notifies the listener when complete lines are received.
     * Supports automatic reconnection with exponential backoff.
     */
    private void readerLoop()
    {
        Charset encoding = config.getEncoding();
        int maxBufferSize = ISerialClient.Config.MAX_BUFFER_SIZE;
        FastByteArrayOutputStream buffer = new FastByteArrayOutputStream();

        while( isRunning && ! Thread.currentThread().isInterrupted() )
        {
            try
            {
                // Check connection and attempt reconnect if needed
                lock.lock();
                try
                {
                    if( ! isConnected() && config.isReconnectEnabled() )
                    {
                        try
                        {
                            ensureConnected();
                        }
                        catch( IOException ex )
                        {
                            // Reconnection failed, wait and retry
                            lock.unlock();
                            try
                            {
                                Thread.sleep( Math.min( currentRetryDelay, 1000 ) );
                            }
                            catch( InterruptedException ie )
                            {
                                Thread.currentThread().interrupt();
                                break;
                            }
                            continue;
                        }
                    }
                }
                finally
                {
                    if( lock.isHeldByCurrentThread() )
                        lock.unlock();
                }

                DataInputStream in = getInputStream();

                if( in == null )
                {
                    // No input stream - possibly disconnected
                    if( config.isReconnectEnabled() && isRunning )
                    {
                        try
                        {
                            Thread.sleep( 100 );  // Brief pause before checking again
                        }
                        catch( InterruptedException ie )
                        {
                            Thread.currentThread().interrupt();
                            break;
                        }
                        continue;
                    }
                    break;
                }

                // Blocking read with timeout (configured via enableReceiveTimeout)
                int byteRead = in.read();

                if( byteRead == -1 )
                {
                    // End of stream - possibly disconnected
                    if( config.isReconnectEnabled() && isRunning )
                    {
                        lock.lock();
                        try
                        {
                            closeResourcesWithoutNotify();
                            listener.onError( new IOException( "End of stream - connection lost" ) );
                        }
                        finally
                        {
                            lock.unlock();
                        }
                        continue;  // Loop will attempt reconnection
                    }
                    break;
                }

                buffer.write( byteRead );

                // Check buffer size limit - discard oldest data if exceeded
                if( buffer.size() > maxBufferSize )
                {
                    byte[] data = buffer.toByteArray();
                    int discardSize = data.length - ( maxBufferSize / 2 );  // Keep half the buffer
                    buffer.reset();
                    buffer.write( data, discardSize, data.length - discardSize );
                }

                // Check for terminator
                if( checkBufferEndsWith( buffer, terminatorBytes ) )
                {
                    byte[] data = buffer.toByteArray();
                    int lineLength = data.length - terminatorBytes.length;

                    if( lineLength > 0 )
                    {
                        String line = new String( data, 0, lineLength, encoding );
                        listener.onMessage( line );
                    }

                    buffer.reset();
                }
            }
            catch( InterruptedIOException ex )
            {
                // Timeout - check if still running and continue
                if( isRunning && ! Thread.currentThread().isInterrupted() )
                    continue;

                break;
            }
            catch( IOException ex )
            {
                // I/O error - attempt reconnection if enabled
                if( isRunning && ! Thread.currentThread().isInterrupted() )
                {
                    lock.lock();
                    try
                    {
                        closeResourcesWithoutNotify();
                        listener.onError( ex );
                    }
                    finally
                    {
                        lock.unlock();
                    }

                    if( config.isReconnectEnabled() )
                        continue;  // Loop will attempt reconnection
                }

                break;
            }
        }

        // Flush any remaining data in buffer
        if( buffer.size() > 0 )
        {
            String remaining = new String( buffer.toByteArray(), encoding );
            listener.onMessage( remaining );
        }
    }

    /**
     * Returns the input stream in a thread-safe manner.
     *
     * @return The input stream, or null if not connected
     */
    private DataInputStream getInputStream()
    {
        lock.lock();

        try
        {
            return inputStream;
        }
        finally
        {
            lock.unlock();
        }
    }

    //------------------------------------------------------------------------//
    // INTERNAL HELPERS
    //------------------------------------------------------------------------//

    /**
     * Closes all resources without notifying the listener.
     * Must be called while holding the lock.
     */
    private void closeInternal()
    {
        isRunning = false;

        // Capture thread reference before releasing lock
        Thread threadToJoin = readerThread;
        readerThread = null;

        // Stop reader thread - must release lock to avoid deadlock
        if( threadToJoin != null )
        {
            threadToJoin.interrupt();
            lock.unlock();
            try
            {
                // Wait briefly for thread to finish
                threadToJoin.join( 1000 );
            }
            catch( InterruptedException ex )
            {
                Thread.currentThread().interrupt();
            }
            finally
            {
                lock.lock();
            }
        }

        // Close input stream
        if( inputStream != null )
        {
            try { inputStream.close(); } catch( IOException ignored ) { }
            inputStream = null;
        }

        // Close output stream
        if( outputStream != null )
        {
            try { outputStream.close(); } catch( IOException ignored ) { }
            outputStream = null;
        }

        // Disconnect serial port
        if( serial != null )
        {
            serial.disconnect();
            serial = null;
        }
    }

    //------------------------------------------------------------------------//
    // STATIC UTILITY METHODS
    //------------------------------------------------------------------------//

    /**
     * Efficiently checks if the buffer ends with the specified suffix bytes.
     * <p>
     * This avoids creating a new String object for the entire buffer.
     *
     * @param buffer The buffer to check
     * @param suffix The suffix bytes to look for
     * @return true if buffer ends with suffix, false otherwise
     */
    private boolean checkBufferEndsWith( FastByteArrayOutputStream buffer, byte[] suffix )
    {
        if( suffix == null || suffix.length == 0 )
            return false;

        int bufLen = buffer.getCount();
        int sufLen = suffix.length;

        if( bufLen < sufLen )
            return false;

        byte[] bufferBytes = buffer.getBuffer();

        for( int i = 0; i < sufLen; i++ )
        {
            if( bufferBytes[ bufLen - sufLen + i ] != suffix[ i ] )
                return false;
        }

        return true;
    }

    /**
     * Returns a set of available serial port names on this system.
     * <p>
     * This method queries the operating system for all available serial ports,
     * including USB-to-Serial adapters, built-in serial ports, and virtual
     * serial ports.
     *
     * @return Set of available port names (e.g., "/dev/ttyUSB0", "COM3")
     */
    public static Set<String> getAvailablePorts()
    {
        return NRSerialPort.getAvailableSerialPorts();
    }
}