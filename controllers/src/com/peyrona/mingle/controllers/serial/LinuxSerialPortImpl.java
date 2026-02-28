
package com.peyrona.mingle.controllers.serial;

import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InterruptedIOException;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Linux-specific serial port implementation using pure Java.
 * <p>
 * This implementation uses standard Linux utilities ({@code stty}) for port
 * configuration and direct file I/O ({@code /dev/ttyXXX}) for data transfer.
 * Zero native dependencies — completely eliminates SIGSEGV crashes on ARM/aarch64
 * platforms (Raspberry Pi, Jetson, etc.).
 *
 * <h3>Platform Support:</h3>
 * <ul>
 *   <li><b>Linux</b>: All architectures supported (ARM, ARM64, x86, x86_64)</li>
 *   <li><b>Raspberry Pi</b>: ARMv6, ARMv7, ARMv8 (aarch64)</li>
 *   <li><b>Jetson Nano/TX2/Xavier</b>: ARM64 (aarch64)</li>
 *   <li><b>Standard Linux servers</b>: x86_64 (Ubuntu, Debian, CentOS)</li>
 * </ul>
 *
 * <h3>Dependencies:</h3>
 * <ul>
 *   <li>{@code stty} utility (part of GNU coreutils, available on all Linux)</li>
 *   <li>Read/write access to serial device files ({@code /dev/ttyXXX})</li>
 *   <li>Java 11+ (StandardCharsets, modern I/O APIs)</li>
 * </ul>
 *
 * <h3>How It Works:</h3>
 * <ol>
 *   <li><b>Configuration</b>: Uses {@code stty} to set baud rate, data bits, stop bits,
 *       parity, flow control, and read timeout (VMIN/VTIME)</li>
 *   <li><b>Connection</b>: Opens device file using {@code FileInputStream} for reading
 *       and {@code FileOutputStream} for writing</li>
 *   <li><b>Reading</b>: In AUTO mode, a background thread continuously
 *       reads bytes; in INTERVAL mode, reads occur at fixed intervals</li>
 *   <li><b>Writing</b>: Writes directly to output stream with proper encoding</li>
 * </ol>
 *
 * <h3>Advantages Over JNI-Based Solutions:</h3>
 * <ul>
 *   <li><b>Zero native dependencies</b>: No .so/.dll/.dylib files to manage</li>
 *   <li><b>Architecture independent</b>: Same JAR works on all Linux variants</li>
 *   <li><b>No ABI compatibility issues</b>: Works regardless of glibc/kernel version</li>
 *   <li><b>Better debugging</b>: Pure Java stack traces, not native crashes</li>
 *   <li><b>Security</b>: Operates within Java sandbox, no native code</li>
 *   <li><b>Container-friendly</b>: Works in Docker/K8s without special mounts</li>
 * </ul>
 *
 * <h3>Limitations:</h3>
 * <ul>
 *   <li><b>Linux only</b>: Cannot use on Windows or macOS</li>
 *   <li><b>Requires stty</b>: Must be in PATH (standard on virtually all Linux)</li>
 *   <li><b>No hardware signals</b>: Cannot access DTR/RTS/DSR/CTS directly</li>
 *   <li><b>No events</b>: Cannot use event-driven I/O, only polling/threaded reads</li>
 *   <li><b>Performance</b>: Slightly slower than optimized native code for very high throughput</li>
 * </ul>
 *
 * <h3>Supported Features:</h3>
 * <ul>
 *   <li><b>Baud rates</b>: 300, 1200, 2400, 4800, 9600, 19200, 38400, 57600, 115200, 230400, 460800, 921600</li>
 *   <li><b>Data bits</b>: 5, 6, 7, or 8</li>
 *   <li><b>Stop bits</b>: 1, 1.5, or 2</li>
 *   <li><b>Parity</b>: None, odd, even, mark, or space</li>
 *   <li><b>Flow control</b>: None, RTS/CTS, or XON/XOFF</li>
 *   <li><b>Read modes</b>: AUTO (continuous) or INTERVAL (periodic)</li>
 *   <li><b>Reconnection</b>: Automatic with exponential backoff</li>
 * </ul>
 *
 * <h3>Reading Modes:</h3>
 * <ul>
 *   <li><b>AUTO (default, interval=0)</b>: A background reader thread continuously
 *       reads from the serial port and notifies the listener when data arrives.
 *       This provides real-time event-driven communication with minimal latency.</li>
 *   <li><b>INTERVAL (interval&gt;0)</b>: Data is read at fixed intervals (minimum 500ms).
 *       The caller is responsible for scheduling reads by calling {@code readOnce()}
 *       at the configured interval. This mode is useful when you want periodic
 *       polling without continuous background reading.</li>
 * </ul>
 *
 * <h3>Thread Safety:</h3>
 * This class is thread-safe. All public methods use internal locking to ensure
 * safe concurrent access from multiple threads. The background reader thread
 * in AUTO mode uses the same lock for synchronization.
 *
 * <h3>Usage Example (AUTO mode):</h3>
 * <pre>
 * ISerialClient.Config config = new ISerialClient.Config.Builder( "/dev/ttyUSB0" )
 *     .baudrate( 115200 )
 *     .build();
 *
 * ISerialClient client = new LinuxSerialPortImpl( config, new ISerialClient.Listener() {
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
 * ISerialClient client = new LinuxSerialPortImpl( config, listener );
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
 * <h3>Permission Requirements:</h3>
 * On Linux, the user running the JVM must have read/write access to the serial
 * port device file. This is typically granted by adding the user to the
 * {@code dialout} or {@code uucp} group:
 * <pre>
 * sudo usermod -a -G dialout $USER
 * # Logout and login again for changes to take effect
 * </pre>
 *
 * <h3>Port Discovery:</h3>
 * Supported serial port prefixes on Linux:
 * <ul>
 *   <li>{@code ttyUSB} - USB-to-Serial adapters (FTDI, CP210x, CH340, etc.)</li>
 *   <li>{@code ttyACM} - USB CDC devices (Arduino, some ESP32, STM32)</li>
 *   <li>{@code ttyAMA} - Raspberry Pi built-in UART</li>
 *   <li>{@code ttyS} - Legacy PC serial ports</li>
 *   <li>{@code ttyO} - OMAP SoC (BeagleBone, etc.)</li>
 *   <li>{@code ttySAC} - Samsung SoC</li>
 *   <li>{@code ttyMFD} - Intel Medfield</li>
 *   <li>{@code ttyHS} - HiKey boards</li>
 *   <li>{@code rfcomm} - Bluetooth serial</li>
 *   <li>{@code serial} - Generic serial ports</li>
 * </ul>
 *
 * <h3>Debugging Tips:</h3>
 * <ul>
 *   <li>Use {@code stty -F /dev/ttyUSB0 -a} to view current settings</li>
 *   <li>Use {@code dmesg | grep tty} to check for kernel-level serial errors</li>
 *   <li>Use {@code sudo cat /dev/ttyUSB0} to manually verify device output</li>
 *   <li>Check device permissions: {@code ls -l /dev/ttyUSB0}</li>
 *   <li>Verify baud rate: {@code stty -F /dev/ttyUSB0}</li>
 * </ul>
 *
 * @author Francisco José Morero Peyrona
 * @see ISerialClient
 * @see ISerialClient.Config
 * @see ISerialClient.Listener
 * @since 1.0
 */
public final class LinuxSerialPortImpl implements ISerialClient
{
    // Aliases for ISerialClient.Config constants (used in configurePortLinux)
    private static final int PARITY_NONE  = ISerialClient.Config.PARITY_NONE;
    private static final int PARITY_ODD   = ISerialClient.Config.PARITY_ODD;
    private static final int PARITY_EVEN  = ISerialClient.Config.PARITY_EVEN;
    private static final int PARITY_MARK  = ISerialClient.Config.PARITY_MARK;
    private static final int PARITY_SPACE = ISerialClient.Config.PARITY_SPACE;

    private static final int STOPBITS_1   = ISerialClient.Config.STOPBITS_1;
    private static final int STOPBITS_2   = ISerialClient.Config.STOPBITS_2;
    private static final int STOPBITS_1_5 = ISerialClient.Config.STOPBITS_1_5;

    private static final int FLOWCONTROL_NONE    = ISerialClient.Config.FLOWCONTROL_NONE;
    private static final int FLOWCONTROL_RTSCTS  = ISerialClient.Config.FLOWCONTROL_RTSCTS;
    private static final int FLOWCONTROL_XONXOFF = ISerialClient.Config.FLOWCONTROL_XONXOFF;

    // Read timeout for blocking I/O (allows responsive shutdown)
    private static final int RECEIVE_TIMEOUT_MS = 100;

    // Known serial port device prefixes on Linux
    private static final String[] LINUX_PORT_PREFIXES = new String[]{ "ttyUSB", "ttyACM", "ttyAMA", "ttyS", "ttyO", "ttySAC", "ttyMFD", "ttyHS", "rfcomm", "serial" };

    // Configuration
    private final ISerialClient.Config   config;
    private final ISerialClient.Listener listener;

    // Runtime state (protected by lock)
    private final    ReentrantLock    lock         = new ReentrantLock();
    private          DataInputStream  inputStream  = null;
    private          DataOutputStream outputStream = null;
    private          Thread           readerThread = null;
    private volatile boolean          isRunning    = false;

    // Linux mode: file-based port handle
    private FileInputStream  linuxFIS  = null;
    private FileOutputStream linuxFOS  = null;
    private String           linuxPort = null;    // Non-null when connected

    // Buffer for POLL mode (persisted between calls) - uses bytes for proper encoding
    private final DirectByteArrayOutputStream pollBuffer = new DirectByteArrayOutputStream();

    // Terminator as bytes for efficient comparison
    private byte[] terminatorBytes = null;

    // Reconnection state
    private volatile int  currentRetryCount = 0;
    private volatile long currentRetryDelay = 0;
    private volatile long lastReconnectTime = 0;

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
    public LinuxSerialPortImpl( ISerialClient.Config config, ISerialClient.Listener listener )
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
        return linuxPort    != null &&
               inputStream  != null &&
               outputStream != null;
    }

    @Override
    public void write( String data ) throws IOException
    {
        if( data == null )
            return;

        write( data.getBytes( config.getEncoding() ) );
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

        DataInputStream in;

        lock.lock();
        try
        {
            ensureConnected();
            in = inputStream;

            if( in == null )
                throw new IOException( "Serial port is not connected" );
        }
        finally
        {
            lock.unlock();
        }

        // Read loop runs WITHOUT holding the lock — write()/close() remain responsive.
        // pollBuffer is only used from readOnce() (INTERVAL mode), never from readerLoop()
        // (AUTO mode), so it needs no lock protection.
        int     linesRead     = 0;
        Charset encoding      = config.getEncoding();
        int     maxBufferSize = ISerialClient.Config.MAX_BUFFER_SIZE;
        long    startTime     = System.currentTimeMillis();
        long    timeout       = config.getTimeout();

        try
        {
            while( true )
            {
                if( System.currentTimeMillis() - startTime > timeout )
                    break;

                int byteRead = in.read();

                if( byteRead == -1 )
                    continue;    // VTIME expired, no data yet — keep waiting until outer timeout

                pollBuffer.write( byteRead );

                // Check buffer size limit - discard oldest data if exceeded
                if( pollBuffer.size() > maxBufferSize )
                {
                    byte[] data = pollBuffer.toByteArray();
                    int discardSize = data.length - ( maxBufferSize / 2 );
                    pollBuffer.reset();
                    pollBuffer.write( data, discardSize, data.length - discardSize );
                }

                // Extract all complete lines from buffer
                linesRead += drainLines( pollBuffer, encoding );
            }
        }
        catch( InterruptedIOException ex )
        {
            // Timeout — return normally with current count
        }
        catch( IOException ex )
        {
            handleConnectionError( ex );
            throw ex;
        }

        return linesRead;
    }

    //------------------------------------------------------------------------//
    // PRIVATE SCOPE

    /**
     * Initializes serial port resources (connection, streams).
     * <p>
     * Uses {@code stty} to configure the port and opens it as a regular
     * device file.
     *
     * @throws IOException If the port cannot be opened or configured
     */
    private void initializeResources() throws IOException
    {
        // Reset poll buffer and initialize terminator bytes
        pollBuffer.reset();
        terminatorBytes = config.getTerminator().getBytes( config.getEncoding() );

        initializeLinux();
    }

    /**
     * Initializes serial port using pure Java on Linux.
     * <p>
     * Configures the port via {@code stty} command and opens it as a regular
     * device file.
     *
     * @throws IOException If the port cannot be opened or configured
     */
    private void initializeLinux() throws IOException
    {
        String portPath = config.getPort();
        File   portFile = new File( portPath );

        if( ! portFile.exists() )
            throw new IOException( "Port device not found: " + portPath );

        if( ! portFile.canRead() || ! portFile.canWrite() )
            throw new IOException( "Insufficient permissions on port: " + portPath +". Ensure the user is in the 'dialout' group." );

        try
        {
            // Configure port via stty BEFORE opening streams
            configurePortLinux( portPath );

            // Open file I/O streams on the device file
            linuxFIS     = new FileInputStream( portFile );
            linuxFOS     = new FileOutputStream( portFile );
            inputStream  = new DataInputStream( linuxFIS );
            outputStream = new DataOutputStream( linuxFOS );
            linuxPort    = portPath;
        }
        catch( Exception ex )
        {
            // Clean up partial initialization
            closeLinuxResources();

            if( ex instanceof IOException )
                throw (IOException) ex;

            throw new IOException( "Failed to configure serial port: " + ex.getMessage(), ex );
        }
    }

    /**
     * Ensures that the serial port is connected, attempting reconnection if needed.
     * <p>
     * Uses exponential backoff between reconnection attempts.
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
            throw new IOException( "Reconnection attempt too soon, waiting " + (currentRetryDelay - (now - lastReconnectTime)) + "ms" );

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
     * Handles connection errors by closing resources and notifying listener.
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
     * <p>
     * Used during error handling to prepare for reconnection.
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

        closeLinuxResources();
    }

    /**
     * Closes Linux file-based serial port resources.
     */
    private void closeLinuxResources()
    {
        if( linuxFIS != null )
        {
            try { linuxFIS.close(); } catch( IOException ignored ) { }
            linuxFIS = null;
        }

        if( linuxFOS != null )
        {
            try { linuxFOS.close(); } catch( IOException ignored ) { }
            linuxFOS = null;
        }

        linuxPort = null;
    }

    /**
     * Configures a serial port on Linux using {@code stty} command.
     * <p>
     * Sets raw mode, baud rate, data bits, stop bits, parity, flow control,
     * and a read timeout of {@value #RECEIVE_TIMEOUT_MS}ms (via VMIN=0, VTIME).
     *
     * @param portPath Path to the serial device (e.g., "/dev/ttyUSB0")
     * @throws IOException If stty command fails
     */
    private void configurePortLinux( String portPath ) throws IOException
    {
        List<String> cmd = new ArrayList<>();
        cmd.add( "stty" );
        cmd.add( "-F" );
        cmd.add( portPath );

        // Raw mode: disable all input/output processing
        cmd.add( "raw" );
        cmd.add( "-echo" );
        cmd.add( "-echoe" );
        cmd.add( "-echok" );

        // Baud rate
        cmd.add( String.valueOf( config.getBaudrate() ) );

        // Data bits: cs5, cs6, cs7, cs8
        cmd.add( "cs" + config.getDatabits() );

        // Stop bits: -cstopb = 1, cstopb = 2
        switch( config.getStopbits() )
        {
            case STOPBITS_2:
            case STOPBITS_1_5:   // 1.5 not supported by stty; use 2 as closest approximation
                cmd.add( "cstopb" );
                break;
            default:
                cmd.add( "-cstopb" );
                break;
        }

        // Parity
        switch( config.getParity() )
        {
            case PARITY_ODD:
                cmd.add( "parenb" );
                cmd.add( "parodd" );
                break;
            case PARITY_EVEN:
                cmd.add( "parenb" );
                cmd.add( "-parodd" );
                break;
            case PARITY_MARK:
            case PARITY_SPACE:
                // Mark/space not directly supported by stty; disable parity
                cmd.add( "-parenb" );
                break;
            default:  // PARITY_NONE
                cmd.add( "-parenb" );
                break;
        }

        // Flow control
        switch( config.getFlowcontrol() )
        {
            case FLOWCONTROL_RTSCTS:
                cmd.add( "crtscts" );
                cmd.add( "-ixon" );
                cmd.add( "-ixoff" );
                break;
            case FLOWCONTROL_XONXOFF:
                cmd.add( "-crtscts" );
                cmd.add( "ixon" );
                cmd.add( "ixoff" );
                break;
            default:  // FLOWCONTROL_NONE
                cmd.add( "-crtscts" );
                cmd.add( "-ixon" );
                cmd.add( "-ixoff" );
                break;
        }

        // Read timeout: VMIN=0 VTIME=n (tenths of a second)
        // VMIN=0 + VTIME>0: read returns when data available or after VTIME timeout
        cmd.add( "min" );
        cmd.add( "0" );
        cmd.add( "time" );
        cmd.add( String.valueOf( Math.max( 1, RECEIVE_TIMEOUT_MS / 100 ) ) );

        // Execute stty
        ProcessBuilder pb = new ProcessBuilder( cmd );
        pb.redirectErrorStream( true );

        Process process = pb.start();

        try
        {
            int exitCode = process.waitFor();

            if( exitCode != 0 )
            {
                byte[] errBytes = process.getInputStream().readAllBytes();
                String errMsg   = new String( errBytes ).trim();

                throw new IOException( "stty configuration failed (exit " + exitCode + "): " + errMsg );
            }
        }
        catch( InterruptedException ex )
        {
            Thread.currentThread().interrupt();
            throw new IOException( "stty configuration interrupted", ex );
        }
    }

    //------------------------------------------------------------------------//
    // READER THREAD
    //------------------------------------------------------------------------//

    /**
     * Background thread that continuously reads from the serial port
     * and notifies the listener when complete lines are received.
     * <p>
     * Supports automatic reconnection with exponential backoff.
     */
    private void readerLoop()
    {
        Charset encoding = config.getEncoding();
        int maxBufferSize = ISerialClient.Config.MAX_BUFFER_SIZE;
        DirectByteArrayOutputStream buffer = new DirectByteArrayOutputStream();

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

                // Blocking read with timeout (configured via stty VTIME)
                int byteRead = in.read();

                if( byteRead == -1 )
                    continue;    // VTIME expired, no data — normal idle on Linux serial

                buffer.write( byteRead );

                // Check buffer size limit - discard oldest data if exceeded
                if( buffer.size() > maxBufferSize )
                {
                    byte[] data = buffer.toByteArray();
                    int discardSize = data.length - ( maxBufferSize / 2 );  // Keep half of buffer
                    buffer.reset();
                    buffer.write( data, discardSize, data.length - discardSize );
                }

                // Extract all complete lines from buffer
                drainLines( buffer, encoding );
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

    }

    /**
     * Returns input stream in a thread-safe manner.
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
     * Closes all resources without notifying listener of disconnect.
     * <p>
     * Stops the reader thread and closes all streams.
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

        closeLinuxResources();
    }

    //------------------------------------------------------------------------//
    // STATIC UTILITY METHODS
    //------------------------------------------------------------------------//

    /**
     * Enumerates available serial ports on Linux by scanning {@code /dev/}.
     * <p>
     * Looks for device files matching known serial port prefixes:
     * ttyUSB (USB-Serial), ttyACM (Arduino/CDC), ttyAMA (Raspberry Pi UART),
     * ttyS (built-in), ttyO (OMAP), ttySAC (Samsung), etc.
     *
     * @return Set of available port paths (e.g., "/dev/ttyUSB0")
     */
    public static Set<String> getAvailablePorts()
    {
        Set<String> ports = new LinkedHashSet<>();
        File devDir = new File( "/dev" );
        File[] files = devDir.listFiles();

        if( files == null )
            return ports;

        for( File file : files )
        {
            String name = file.getName();

            for( String prefix : LINUX_PORT_PREFIXES )
            {
                if( name.startsWith( prefix ) && file.canRead() && file.canWrite() )
                {
                    ports.add( file.getAbsolutePath() );
                    break;
                }
            }
        }

        return ports;
    }

    /**
     * Finds the first occurrence of the terminator sequence in the buffer.
     * <p>
     * Scans the buffer from the beginning, looking for the configured terminator
     * bytes. Returns the index of the first byte of the terminator, or -1 if not
     * found.
     *
     * @param buffer The buffer to scan
     * @return Index of the first terminator byte, or -1 if not found
     */
    private int findTerminator( DirectByteArrayOutputStream buffer )
    {
        int    bufLen = buffer.size();
        int    sufLen = terminatorBytes.length;
        byte[] buf    = buffer.buf();

        if( bufLen < sufLen )
            return -1;

        int limit = bufLen - sufLen;

        for( int i = 0; i <= limit; i++ )
        {
            if( buf[i] == terminatorBytes[0] )
            {
                boolean match = true;

                for( int j = 1; j < sufLen; j++ )
                {
                    if( buf[i + j] != terminatorBytes[j] )
                    {
                        match = false;
                        break;
                    }
                }

                if( match )
                    return i;
            }
        }

        return -1;
    }

    /**
     * Extracts all complete lines (terminated messages) from the buffer and delivers
     * them to the listener.
     * <p>
     * For each terminator found in the buffer: the data before it is delivered as a
     * message, and the data after it remains in the buffer for subsequent reads.
     *
     * @param buffer   The buffer to drain
     * @param encoding Character encoding for converting bytes to String
     * @return Number of complete lines extracted and delivered
     */
    private int drainLines( DirectByteArrayOutputStream buffer, Charset encoding )
    {
        int linesRead = 0;
        int termPos;

        while( (termPos = findTerminator( buffer )) >= 0 )
        {
            if( termPos > 0 )
            {
                byte[] buf  = buffer.buf();
                String line = new String( buf, 0, termPos, encoding );
                listener.onMessage( line );
                linesRead++;
            }

            // Keep data after the terminator in the buffer
            int    afterTerm = termPos + terminatorBytes.length;
            int    remaining = buffer.size() - afterTerm;
            byte[] tail      = (remaining > 0) ? new byte[remaining] : null;

            if( tail != null )
                System.arraycopy( buffer.buf(), afterTerm, tail, 0, remaining );

            buffer.reset();

            if( tail != null )
                buffer.write( tail, 0, tail.length );
        }

        return linesRead;
    }

    //------------------------------------------------------------------------//
    // INNER CLASSES
    //------------------------------------------------------------------------//

    /**
     * {@link ByteArrayOutputStream} subclass that exposes its internal buffer
     * for zero-copy reads.
     * <p>
     * Avoids the O(n) copy that {@code toByteArray()} performs on every call,
     * enabling efficient suffix checks without allocating new arrays.
     */
    private static final class DirectByteArrayOutputStream extends ByteArrayOutputStream
    {
        /** Returns the internal buffer. Valid bytes are from index 0 to {@code size()-1}. */
        byte[] buf()  { return buf; }
    }
}