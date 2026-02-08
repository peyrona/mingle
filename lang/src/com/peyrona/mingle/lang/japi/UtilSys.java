
package com.peyrona.mingle.lang.japi;

import com.peyrona.mingle.lang.MingleException;
import com.peyrona.mingle.lang.interfaces.IConfig;
import com.peyrona.mingle.lang.interfaces.ILogger;
import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileStore;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.Collections;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/**
 * Utility class providing system-level operations and services for the Mingle framework.
 *
 * This class offers functionality for:
 * - Directory management and file system operations
 * - Logger and configuration management
 * - Thread pool execution and task scheduling
 * - System information and OS detection
 * - Class path manipulation and JAR loading
 * - Time utilities and version information
 * - Simple key-value storage
 *
 * All methods are static and the class cannot be instantiated.
 *
 * @author Francisco José Morero Peyrona
 *
 * Official web site at: <a href="https://github.com/peyrona/mingle">https://github.com/peyrona/mingle</a>
 */
public final class UtilSys
{
    public  static final boolean            isDevEnv     = getDevEnvDir().exists();   // true when the app is running in my Development Environment (NetBeans).
    public  static final File               fHomeDir     = getHomeDir();
    public  static final boolean            isFsWritable = isFileSystemWritable();    // Has to be after getHomeDir()
    private static       ILogger            logger       = null;
    private static       IConfig            config       = null;
    private static final long               nAtStart     = System.currentTimeMillis();    // To calculate millis since the application started (see ::elapsed())
    private static final Set<URI>           lstLoaded    = Collections.synchronizedSet( new HashSet<>() );  // Sync is enought because JARs are only added
    private static final Map<Object,Object> mapStorage   = new ConcurrentHashMap<>();                       // Used by ::put(...), ::get(...) and ::del(...)

    //------------------------------------------------------------------------//

    static
    {
        if( ! isAtLeastJava11() )
        {
            System.err.println( "Java version at: "+ UtilSys.getJavaHome() +'\n'+
                                "is "+ System.getProperty( "java.version" ) +". But minimum needed is Java 11.\n"+
                                "Can not continue.");
            System.exit( 1 );
        }
    }

    //------------------------------------------------------------------------//

    /**
     * Returns the include directory where external files and libraries are stored.
     *
     * @return File object representing the include directory
     */
    public static File getIncDir()
    {
        return new File( fHomeDir, "include" );
    }

    /**
     * Returns the library directory where JAR files and dependencies are stored.
     *
     * @return File object representing the library directory
     */
    public static File getLibDir()
    {
        return new File( fHomeDir, "lib" );
    }

    /**
     * Returns the log directory where log files are stored.
     *
     * @return File object representing the log directory
     */
    public static File getLogDir()
    {
        return new File( fHomeDir, "log" );
    }

    /**
     * Returns the temporary directory for transient files.
     *
     * @return File object representing the temporary directory
     */
    public static File getTmpDir()
    {
        return new File( fHomeDir, "tmp" );
    }

    /**
     * Returns the etc directory for configuration files.
     *
     * @return File object representing the etc directory
     */
    public static File getEtcDir()
    {
        return new File( fHomeDir, "etc" );
    }

    /**
     * Returns the current logger instance.
     *
     * @return the logger instance, or null if not initialized
     */
    public static ILogger getLogger()
    {
        return (logger == null ? new DefaultLogger() : logger);
    }

    /**
     * Initializes and sets the logger with the specified name and configuration.
     *
     * @param name the logger name
     * @param config the configuration to use, or null to use default config
     * @return the initialized logger instance
     */
    public static ILogger setLogger( String name, IConfig config )
    {
        config = (config == null) ? getConfig() : config;

        String  sLogLevel = "WARNING";
        boolean bDisk     = isFsWritable;
        int     expire    = 90;
        boolean b2Console = UtilSys.isDevEnv;

        if( config != null )
        {
            sLogLevel = config.get( "common", "log_level" , "WARNING" );
            bDisk     = isFsWritable && config.get( "exen", "write_disk", true );
            expire    = config.get( "common", "log_expire", -1 );
        }

        synchronized( fHomeDir )
        {
            logger = new Logger().init( name.trim(), bDisk, b2Console )
                             .setLevel( sLogLevel )
                             .deleteOlderThan( (UtilSys.isDevEnv ? 21 : expire) );
        }

        return logger;
    }

    /**
     * Returns the configuration instance, creating it if necessary.
     *
     * @return the configuration instance
     * @throws MingleException if there's an error loading the configuration
     */
    public static IConfig getConfig()
    {
        if( config == null )
        {
            try
            {
                setConfig( new Config().load( null ) );
            }
            catch( IOException ex )
            {
                throw new MingleException( "Error creating Config from file" );
            }
        }

        return config;
    }

    /**
     * Sets the configuration instance and updates the logger if it exists.
     *
     * @param conf the configuration instance to set
     */
    public static void setConfig( IConfig conf )
    {
        config = conf;
    }

    //------------------------------------------------------------------------//
    // PUBLIC STORAGE

    /**
     * Stores a key-value pair in the internal storage map.
     *
     * @param key the key to store
     * @param value the value to associate with the key
     * @return always returns true
     */
    public static boolean put( Object key, Object value )
    {
        mapStorage.put( key, value );
        return true;
    }

    /**
     * Retrieves a value from the internal storage map.
     *
     * @param key the key to retrieve
     * @return the associated value, or empty string if not found
     */
    public static Object get( Object key )
    {
        return get( key, "" );
    }

    /**
     * Retrieves a value from the internal storage map with a default value.
     *
     * @param key the key to retrieve
     * @param def the default value to return if key is not found
     * @return the associated value, or the default if not found
     */
    public static Object get( Object key, Object def )
    {
        Object value = mapStorage.get( key );

        return (value == null) ? def : value;
    }

    /**
     * Removes a key-value pair from the internal storage map.
     *
     * @param key the key to remove
     * @return true if the key existed and was removed, false otherwise
     */
    public static boolean del( Object key )
    {
        Object ret = mapStorage.remove( key );

        return (ret != null);
    }

    //------------------------------------------------------------------------//
    // THERAD POOL

    /**
     * Creates an Executor instance for task scheduling.
     *
     * @param fromPool if true, uses the shared thread pool; if false, creates a new single-thread executor
     * @return a new Executor instance
     */
    public static Executor executor( boolean fromPool )
    {
        return new Executor( fromPool );
    }

    //------------------------------------------------------------------------//

    /**
     * Returns the time in milliseconds elapsed since ExEn started.<br>
     * Note that while the unit of time of the return value is a millisecond,
     * the granularity of the value depends on the underlying operating system
     * and may be larger. For example, many operating systems measure time in
     * units of tens of milliseconds.
     * <p>
     * Note: the max amount of time (in millis) with an int is 24,8 days:
     * therefore a long is needed (that provides: 106,751,991,167.3 days).
     *
     * @return The current time in milliseconds since ExEn started.
     */
    public static long elapsed()
    {
        return (System.currentTimeMillis() - nAtStart);
    }

    /**
     * Returns LocalDate based on Unix Time.
     *
     * @param unixTime
     * @return Returns LocalDate based on Unix Time.
     */
    public static LocalDate toLocalDate( long unixTime )
    {
        return Instant.ofEpochMilli( unixTime ).atZone( ZoneId.systemDefault() ).toLocalDate();     // Java internally caches ZoneId.systemDefault()
    }

    /**
     * Returns LocalTime based on Unix Time.
     *
     * @param unixTime
     * @return Returns LocalTime based on Unix Time.
     */
    public static LocalTime toLocalTime( long unixTime )
    {
        return Instant.ofEpochMilli( unixTime ).atZone( ZoneId.systemDefault() ).toLocalTime();     // Java internally caches ZoneId.systemDefault()
    }

    /**
     * Returns the version of a class based on its JAR file modification date.
     *
     * @param clazz the class to get version information for
     * @return the modification date as a string, or "unknown" if it cannot be determined
     */
    public static final String getVersion( Class clazz )
    {
        try
        {
            File fApp = new File( clazz.getProtectionDomain().getCodeSource().getLocation().toURI() );

            return UtilSys.toLocalDate( fApp.lastModified() ).toString();
        }
        catch( URISyntaxException ex )
        {
            return "unknown";
        }
    }

    /**
     * Returns the Java home directory, handling both JDK and JRE installations.
     *
     * If the java.home points to a JRE, attempts to find the parent JDK directory.
     *
     * @return the Java home directory, or null if it cannot be determined
     */
    public static File getJavaHome()
    {
        File f = new File( System.getProperty( "java.home" ) );

        if( (! f.exists()) || (! f.isDirectory()) )
            return null;

        if( "jre".equals( f.getName().toLowerCase() ) )
        {
            File p = f.getParentFile();

            if( p.exists() && p.isDirectory() )
            {
                String s = p.getName().toLowerCase();

                if( s.contains( "java" ) || s.contains( "jdk") )
                    return p;

                String[] as = f.list();

                if( UtilColls.contains( as, "lib" ) )
                    return p;
            }
        }

        return f;
    }

    /**
     * Returns True if the version of the JVM is above 11 or above.
     * <p>
     * java.version is a system property that exists in every JVM.
     * There are two possible formats for it:
     *     Java 8 or lower: 1.6.0_23, 1.7.0, 1.7.0_80, 1.8.0_211
     *     Java 9 or higher: 9.0.1, 11.0.4, 12, 12.0.1
     *
     * @return True if the version of the JVM is 11 or above.
     */
    public static boolean isAtLeastJava11()
    {
        String version = System.getProperty( "java.version" );
               version = version.charAt(0) + (Character.isDigit( version.charAt(1) ) ? version.substring(1,2) : "");

        return Integer.parseInt( version ) >= 11;
    }

    /**
     * Returns the operating system name.
     *
     * @return the OS name from system properties
     */
    public static String getOS()
    {
        return System.getProperty( "os.name" );
    }

    /**
     * Checks if the current operating system is Windows.
     *
     * @return true if running on Windows, false otherwise
     */
    public static boolean isWindows()
    {
        return getOS().toLowerCase().contains( "win" );
    }

    /**
     * Checks if the current operating system is macOS.
     *
     * @return true if running on macOS, false otherwise
     */
    public static boolean isMac()
    {
        return getOS().toLowerCase().contains( "mac" );
    }

    /**
     * Checks if the current operating system is a Unix-like system.
     *
     * @return true if running on a Unix-like system (Linux, AIX, etc.), false otherwise
     */
    public static boolean isUnix()
    {
        String OS = getOS().toLowerCase();

        return OS.contains( "nix" ) ||
               OS.contains( "nux" ) ||
               OS.contains( "aix" );
    }

    /**
     * Checks if the current operating system is Solaris.
     *
     * @return true if running on Solaris, false otherwise
     */
    public static boolean isSolaris()
    {
        String OS = getOS().toLowerCase();

        return OS.contains( "sunos" ) ||
               OS.contains( "solaris" );
    }

    /**
     * Checks if the current operating system is Linux.
     *
     * @return true if running on Linux, false otherwise
     */
    public static boolean isLinux()
    {
        return getOS().toLowerCase().contains( "linux" );
    }

    /**
     * Checks if the current operating system is HP-UX.
     *
     * @return true if running on HP-UX, false otherwise
     */
    public static boolean isHpUnix()
    {
        String OS = getOS().toLowerCase();

		return OS.contains( "hp-ux" ) ||
               OS.contains( "hpux"  );
    }

    /**
     * Checks if the current system architecture is ARM.
     *
     * @return true if running on ARM architecture, false otherwise
     */
    public static boolean isARM()
    {
        String os = System.getProperty( "os.arch" ).toLowerCase();

        return os.contains( "aarch64" ) ||    // More common
               os.contains( "arm" );
    }

    /**
     * Checks if the current system is a Raspberry Pi.
     * This method uses multiple detection strategies:
     * 1. Checks OS architecture (arm/aarch64)
     * 2. Looks for Raspberry Pi specific hardware info
     * 3. Examines OS release information
     *
     * @return true if running on a Raspberry Pi, false otherwise
     */
    public static boolean isRaspberryPi()
    {
        // Check CPU architecture

        if( ! isARM() )
            return false;

        // Check OS name

        if( ! isLinux() )
        {
            return false;
        }

        // Check hardware model file
        try
        {
            Path modelPath = Paths.get( "/proc/cpuinfo" );

            if( Files.exists( modelPath ) )
            {
                String cpuInfo = Files.readString( modelPath, StandardCharsets.UTF_8 );

                if( cpuInfo.contains( "Raspberry Pi" ) ||
                    cpuInfo.contains( "BCM2708" )      ||
                    cpuInfo.contains( "BCM2709" )      ||
                    cpuInfo.contains( "BCM2711" )      ||
                    cpuInfo.contains( "BCM2835" )      ||
                    cpuInfo.contains( "BCM2836" )      ||
                    cpuInfo.contains( "BCM2837" ) )
                {
                    return true;
                }
            }

            // Check OS release info

            Path osReleasePath = Paths.get( "/etc/os-release" );

            if( Files.exists( osReleasePath ) )
            {
                String osRelease = Files.readString( osReleasePath, StandardCharsets.UTF_8 );

                if( osRelease.toLowerCase().contains( "raspbian" ) || osRelease.toLowerCase().contains( "raspberry pi os" ) )
                {
                    return true;
                }
            }
        }
        catch( IOException e )
        {
            return false;    // If we can't read the files, err on the side of caution
        }

        return false;
    }

    /**
     * Checks if the application is running inside a Docker container.
     *
     * Uses multiple detection methods including environment variables,
     * Docker-specific files, and cgroup information.
     *
     * @return true if running in Docker, false otherwise
     */
    public static boolean isDocker()
    {
        if( System.getenv( "MINGLE_CONTAINER" ) != null )    // I use this var when crerating my own dockers
            return true;

        if( new File("/.dockerenv").exists() )
            return true;

        try
        {
            String content = new String( Files.readAllBytes( Paths.get( "/proc/1/cgroup" ) ) );
            return content.contains( "/docker/" );
        }
        catch( IOException e )
        {
            return false;
        }
    }

    /**
     * Adds to JVM passed URI, if URI is not a local file, the file will be retrieved from remote,
     * stored in {*home.tmp*} folder and deleted when the JVM ends.
     *
     * @param sJAR
     * @throws MalformedURLException
     * @throws IOException
     * @throws java.net.URISyntaxException
     */
    public static void addToClassPath( String... sJAR ) throws MalformedURLException, IOException, URISyntaxException
    {
        for( URI uri : UtilIO.expandPath( sJAR ) )
        {
            if( ! lstLoaded.contains( uri ) )
            {
                lstLoaded.add( uri );     // Try only once: even if there were errors, there is no need to try again: same errors will happen

                UtilComm.Protocol protocol = UtilComm.getFileProtocol( uri.toString() );

                File fJAR;

                if( (protocol == UtilComm.Protocol.http) || (protocol == UtilComm.Protocol.https) )
                {
                    fJAR = UtilIO.newFileWriter()
                                 .setTemporal( "jar" )
                                 .replace( UtilIO.getAsBinary( uri ) );
                }
                else
                {
                    fJAR = new File( uri.getPath() );
                }

                JarLoader.addToClassPath( fJAR );
            }
        }
    }

    //------------------------------------------------------------------------//
    // PRIVATE SCOPE

    private UtilSys()
    {
        // Avoids creating instances of this class.
    }

    /**
     * Returns the development environment directory.
     *
     * Used only during development to locate the todeploy directory.
     *
     * @return File object representing the development directory
     */
    private static File getDevEnvDir()   // This method is invoked only twice
    {
        return new File( System.getProperty( "user.home" ), "proyectos/mingle/todeploy" );
    }

    /**
     * Returns the home directory based on the execution environment.
     *
     * In development mode, returns the development directory.
     * In production mode, returns the current working directory.
     *
     * @return File object representing the appropriate home directory
     */
    private static File getHomeDir()     // This method is invoked only once
    {
        return (isDevEnv ? getDevEnvDir()
                         : new File( System.getProperty( "user.dir" ) ));
    }

    private static boolean isFileSystemWritable()
    {
        try
        {
            // Check the actual working directory (more relevant than home dir)
            Path targetDir = Paths.get( "" ).toAbsolutePath();

            // Alternative: Check specific directory if known
            // Path targetDir = Paths.get("/path/to/app/data");
            FileStore store = Files.getFileStore( targetDir );

            return ! store.isReadOnly();
        }
        catch( IOException ioe )
        {
            String msg = "WARNING: Unable to verify filesystem write access. Assuming read-only.\n"+
                         UtilStr.toStringBrief( ioe );

            System.err.println( msg );

            return false;   // Conservative: assume read-only on error
        }
    }

    //------------------------------------------------------------------------//
    // INNER CLASS
    //------------------------------------------------------------------------//

    /**
     * A flexible execution framework supporting synchronous, asynchronous, delayed, and recurring execution.
     * <p>
     * This class provides a builder-based API for executing Runnables and shell commands with various scheduling options:
     * <ul>
     *   <li>One-time synchronous execution via {@link #executeAndWait(String...)}</li>
     *   <li>One-time asynchronous execution via {@link #execute(Runnable)} or {@link #execute(String...)}</li>
     *   <li>Delayed execution via {@link #delay(long)}</li>
     *   <li>Recurring execution via {@link #rate(long)}</li>
     *   <li>Custom error handling via {@link #error(Runnable)}</li>
     *   <li>Output capture for async operations via {@link #output(OutputCallback)}</li>
     * </ul>
     * <p>
     * <b>Usage Examples:</b>
     * <pre>{@code
     * // Simple Runnable execution
     * UtilSys.executor()
     *        .name("TaskName")
     *        .execute(() -> doWork());
     *
     * // Delayed execution
     * UtilSys.executor()
     *        .name("DelayedTask")
     *        .delay(5000)
     *        .execute(() -> doWork());
     *
     * // Periodic at fixed rate
     * UtilSys.executor()
     *        .name("PeriodicTask")
     *        .delay(1000)
     *        .rate(10000)
     *        .fixedRate(true)
     *        .error(() -> handleError())
     *        .execute(() -> pollSensor());
     *
     * // Shell command (async)
     * UtilSys.executor()
     *        .name("ShellTask")
     *        .output(line -> log(line))
     *        .execute("ls", "-la");
     *
     * // Shell command (sync)
     * ProcessResult result = UtilSys.executor()
     *        .executeAndWait("git", "status");
     * }</pre>
     */
    public static final class Executor
    {
        private static final ScheduledExecutorService pool = Executors.newScheduledThreadPool( 32 );   // Note: a lot needed: Dispatchers, AFTERs, WITHINs, Controllers...

        private final boolean usePool;
        private String   name      = null;
        private long     delay     = 0;
        private long     rate      = 0;
        private boolean  fixedRate = false;
        private Consumer<Exception> error = null;

        //------------------------------------------------------------------------//

        private Executor( boolean fromPool )
        {
            this.usePool = fromPool;
        }

        //------------------------------------------------------------------------//
        // BUILDER METHODS

        /**
         * Sets a name for this execution.
         * <p>
         * The name is used for thread naming and logging purposes.
         * If not set, a UUID-based name will be generated.
         *
         * @param name the name for this execution
         * @return this builder
         */
        public Executor name( String name )
        {
            this.name = name;
            return this;
        }

        /**
         * Sets the initial delay before execution starts.
         * <p>
         * Only applicable for {@code execute()} methods.
         * The executor waits for this delay before the first execution.
         *
         * @param delay delay in milliseconds (0 for immediate execution)
         * @return this builder
         */
        public Executor delay( long delay )
        {
            this.delay = delay;
            return this;
        }

        /**
         * Sets the recurrence rate for periodic execution.
         * <p>
         * If rate is 0 (default), the command executes once.
         * If rate &gt; 0, the command executes repeatedly.
         * Use {@link #fixedRate(boolean)} to control the scheduling semantics.
         *
         * @param rate rate in milliseconds (0 for one-time execution)
         * @return this builder
         */
        public Executor rate( long rate )
        {
            this.rate = rate;
            return this;
        }

        /**
         * Sets a custom error handler for this execution.
         * <p>
         * The error handler is called if an exception occurs during execution.
         * Errors are also logged via the Mingle logger regardless of this setting.
         *
         * @param error the error handler Runnable
         * @return this builder
         */
        public Executor error( Consumer<Exception> error )
        {
            this.error = error;
            return this;
        }

        /**
         * Sets whether recurring execution uses fixed rate or fixed delay.
         * <p>
         * If {@code true}, uses {@code scheduleAtFixedRate()} - tasks run at
         * regular intervals regardless of execution time.
         * If {@code false} (default), uses {@code scheduleWithFixedDelay()} -
         * tasks run with a delay between completions.
         *
         * @param isFixedRate true for fixed rate, false for fixed delay
         * @return this builder
         */
        public Executor fixedRate( boolean isFixedRate )
        {
            this.fixedRate = isFixedRate;
            return this;
        }

        //------------------------------------------------------------------------//
        // EXECUTION METHODS

        /**
         * Executes a Runnable task.
         * <p>
         * If {@link #rate(long)} was set to a value &gt; 0, the task will be executed periodically.
         * Otherwise, it will be executed once (after optional {@link #delay(long)}).
         *
         * @param r the Runnable to execute
         * @return a ScheduledFuture representing the pending execution
         */
        public ScheduledFuture<?> execute( Runnable r )
        {
            if( UtilStr.isMeaningless( name ) )
            {
                Class<?> callerClass = UtilReflect.getCallerClass( 4 );
                String   methodName  = UtilReflect.getCallerMethodName( 4 );

                if( callerClass != null && methodName != null )
                    name = UtilSys.class.getSimpleName() + "[pool]" + callerClass.getSimpleName() +':'+ methodName;
                else
                    name = UUID.randomUUID().toString();
            }

            final Consumer<Exception> onError = this.error;

            Runnable wrapped = () ->
            {
                try
                {
                    Thread.currentThread().setName( name );
                    r.run();
                }
                catch( Exception exc )
                {
                    if( onError == null )
                    {
                        UtilSys.getLogger().log( ILogger.Level.SEVERE, exc );
                    }
                    else
                    {
                        try
                        {
                            onError.accept( exc );
                        }
                        catch( Exception e )
                        {
                            UtilSys.getLogger().log( ILogger.Level.WARNING, e, "Error in error handler" );
                        }
                    }
                }
            };

            ScheduledExecutorService executor = usePool ? pool
                                                        : Executors.newSingleThreadScheduledExecutor();

            if( rate > 0 )
            {
                if( fixedRate )  return executor.scheduleAtFixedRate(    wrapped, (delay < 0 ? 0 : delay), rate, TimeUnit.MILLISECONDS );
                else             return executor.scheduleWithFixedDelay( wrapped, (delay < 0 ? 0 : delay), rate, TimeUnit.MILLISECONDS );
            }
            else
            {
                return executor.schedule( wrapped, delay, TimeUnit.MILLISECONDS );
            }
        }
    }

    //------------------------------------------------------------------------//
    // INNER CLASS
    // A simplistic default logger that send msgs to console.
    //------------------------------------------------------------------------//

    private static final class DefaultLogger implements ILogger
    {
        private volatile Level level = Level.WARNING;

        @Override
        public ILogger init( String sFileName, boolean bUseDisk, boolean bUseConsole )
        {
            return this;
        }

        @Override
        public String getName()
        {
            return getClass().getName();
        }

        @Override
        public Level getLevel()
        {
            return level;
        }

        @Override
        public ILogger setLevel( Level l )
        {
            level = l;
            return this;
        }

        @Override
        public ILogger setLevel( String s )
        {
            try
            {
                setLevel( ILogger.Level.fromName( s ) );
            }
            catch( Exception iae )
            {
                setLevel( Level.WARNING );
            }

            return this;
        }

        @Override
        public boolean isLoggable( Level level )
        {
            return (level.weight >= this.level.weight);
        }

        @Override
        public ILogger deleteOlderThan( int days )
        {
            return this;
        }

        @Override
        public boolean say( String msg )
        {
            System.out.println( msg );
            return true;
        }

        @Override
        public boolean log( Level l, String msg )
        {
            return log( level, null, msg );
        }

        @Override
        public boolean log( Level l, Throwable th )
        {
            return log( level, th, null );
        }

        @Override
        public boolean log( Level level, Throwable th, String msg )
        {
            if( (msg == null) && (th == null) )
            {
                level = Level.WARNING;
                msg   = "Invalid call: Message and Throwable are null";
            }

            if( level == null )
                level = Level.WARNING;

            if( isLoggable( level ) )
            {
                if( th != null )
                    msg = ((msg == null) ? "" : msg +"\n") + UtilStr.toString( th );     // toString( th ) returns "null" when th is null

                return say( "["+ level +"] "+ msg );
            }

            return false;
        }
    }
}