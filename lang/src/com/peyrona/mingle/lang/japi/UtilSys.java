
package com.peyrona.mingle.lang.japi;

import com.peyrona.mingle.lang.MingleException;
import com.peyrona.mingle.lang.interfaces.IConfig;
import com.peyrona.mingle.lang.interfaces.ILogger;
import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
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
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 *
 * @author Francisco José Morero Peyrona
 *
 * Official web site at: <a href="https://github.com/peyrona/mingle">https://github.com/peyrona/mingle</a>
 */
public final class UtilSys
{
    public  static final boolean                  isDevEnv   = getDevEnvDir().exists();   // true when the app is running in my Development Environment (NetBeans).
    public  static final File                     fHomeDir   = getHomeDir();
    private static       ILogger                  logger     = null;
    private static       IConfig                  config     = null;
    private static final long                     nAtStart   = System.currentTimeMillis();    // To calculate millis since the application started (see ::elapsed())
    private static final Set<URI>                 lstLoaded  = Collections.synchronizedSet( new HashSet<>() );  // Sync is enought because messsages are only added
    private static final Map<Object,Object>       mapStorage = new ConcurrentHashMap<>();                       // Used by ::put(...), ::get(...) and ::del(...)
    private static final ScheduledExecutorService pool       = (ScheduledExecutorService) Executors.newScheduledThreadPool( 8 );   // Note: a lot needed beacuse there is one per each AFTER and WITHIN (plus Controllers)

    //------------------------------------------------------------------------//

    static     // These are needed to be created if they do not exist
    {          // IOException is never thrown, even if HD is read-only
        UtilIO.mkdirs( new File( fHomeDir, "log" ) );
        UtilIO.mkdirs( new File( fHomeDir, "tmp" ) );
        UtilIO.mkdirs( new File( fHomeDir, "etc" ) );    // This one has no macro associated because it is used internally only
    }

    //------------------------------------------------------------------------//

    public static File getIncDir()
    {
        return new File( fHomeDir, "include" );
    }

    public static File getLibDir()
    {
        return new File( fHomeDir, "lib" );
    }

    public static File getLogDir()
    {
        return new File( fHomeDir, "log" );
    }

    public static File getTmpDir()
    {
        return new File( fHomeDir, "tmp" );
    }

    public static File getEtcDir()
    {
        return new File( fHomeDir, "etc" );
    }

    public static ILogger getLogger()
    {
        return logger;
    }

    public static ILogger setLogger( String name, IConfig config )
    {
        config = (config == null) ? getConfig() : config;

        String  sLogLevel = config.get( "common", "log_level", "WARNING" );
        boolean bDisk     = config.get( "exen"  , "use_disk" , true      );
        boolean b2Console = UtilSys.isDevEnv;                                    // FIXME: en la RPi no se están creando los ficheros de log

        logger = new Logger().init( name.trim(), bDisk, b2Console )
                             .setLevel( sLogLevel )
                             .deleteOlderThan( (UtilSys.isDevEnv ? 21 : config.get( "common", "log_expire", -1 )) );

        return logger;
    }

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

    public static void setConfig( IConfig conf )
    {
        config = conf;

        if( logger != null )    // Has to be here (can not be at ::setLogger(...) because config is normlly null when setting the logger)
        {
            logger.setLevel( config.get( "common", "log_level", "WARNING" ) )
                  .deleteOlderThan( (UtilSys.isDevEnv ? 21 : config.get( "common", "log_expire", -1 )) );
        }
    }

    //------------------------------------------------------------------------//
    // PUBLIC STORAGE

    public static boolean put( Object key, Object value )
    {
        mapStorage.put( key, value );
        return true;
    }

    public static Object get( Object key )
    {
        return get( key, "" );
    }

    public static Object get( Object key, Object def )
    {
        Object value = mapStorage.get( key );

        return (value == null) ? def : value;
    }

    public static boolean del( Object key )
    {
        Object ret = mapStorage.remove( key );

        return (ret != null);
    }

    //------------------------------------------------------------------------//
    // THERAD POOL

    /**
     * Executes passed Runnable using an internal ThreadPoolExecutor.
     * @param sThreadName
     * @param r What to execute.
     */
    public static void execute( String sThreadName, Runnable r )
    {
        execute( sThreadName, -1, r );
    }

    /**
     * Executes passed Runnable after specified delay (in millis) using an internal ThreadPoolExecutor.
     *
     * @param sThreadName
     * @param delay Delay in millis to execute the task.
     * @param r What to execute.
     * @return
     */
    public static ScheduledFuture execute( String sThreadName, long delay, Runnable r )
    {
        // Can not afford to have an execption inside r because the Scheduler stops.
        // Although we wrap r with a generic try catch, the r should have its own try catch.

        Runnable run = () ->
                        {
                            String original = Thread.currentThread().getName();    // TODO: esto no funciona
                            Thread.currentThread().setName( sThreadName );

                            try
                            {
                                r.run();
                            }
                            catch( Exception exc )
                            {
                                if( UtilSys.getLogger() == null )
                                    exc.printStackTrace( System.err );
                                else
                                    UtilSys.getLogger().log( ILogger.Level.SEVERE, exc );
                            }
                            finally
                            {
                                Thread.currentThread().setName( original );
                            }
                        };

        if( delay <= 0 )
        {
            pool.execute( run );
            return null;
        }

        return pool.schedule( run, delay, TimeUnit.MILLISECONDS );
    }

    /**
     * Periodically executes passed Runnable after specified initial delay every rate millis using an internal ThreadPoolExecutor.
     *
     * @param sThreadName
     * @param delay Initial delay in milliseconds to execute the task.
     * @param rate Rate in milliseconds to execute the task.
     * @param r What to execute.
     * @return
     */
    public static ScheduledFuture executeAtRate( String sThreadName, long delay, long rate, Runnable r )
    {
        // Can not afford to have an execption inside r because the Scheduler stops.
        // Although we wrap r with a generic try catch, the r should have its own try catch.

        Runnable run = () ->
                        {
                            String original = Thread.currentThread().getName();
                            Thread.currentThread().setName( sThreadName );

                            try
                            {
                                r.run();
                            }
                            catch( Exception exc )
                            {
                                if( UtilSys.getLogger() == null )
                                    exc.printStackTrace( System.err );
                                else
                                    UtilSys.getLogger().log( ILogger.Level.SEVERE, exc );
                            }
                            finally
                            {
                                Thread.currentThread().setName( original );
                            }
                        };

        return pool.scheduleAtFixedRate( run, (delay < 0 ? 0 : delay), rate, TimeUnit.MILLISECONDS );
    }

    /**
     * Periodically executes passed Runnable after specified initial delay: when task ends its execution,
     * an amount of delay millis is waited until the next execution of the task starts.
     *
     * @param sThreadName
     * @param delay Interval between an iteration ends and the next iteration starts.
     * @param rate
     * @param r What to execute.
     * @return
     */
    public static ScheduledFuture executeAtFixed( String sThreadName, long delay, long rate, Runnable r )
    {
        // Can not afford to have an execption inside r because the Scheduler stops.
        // Although we wrap r with a generic try catch, the r should have its own try catch.

        Runnable run = () ->
                        {
                            String original = Thread.currentThread().getName();
                            Thread.currentThread().setName( sThreadName );

                            try
                            {
                                r.run();
                            }
                            catch( Exception exc )
                            {
                                if( UtilSys.getLogger() == null )
                                    exc.printStackTrace( System.err );
                                else
                                    UtilSys.getLogger().log( ILogger.Level.SEVERE, exc );
                            }
                            finally
                            {
                                Thread.currentThread().setName( original );
                            }
                        };

        return pool.scheduleWithFixedDelay( run, (delay < 0 ? 0 : delay), rate, TimeUnit.MILLISECONDS );
    }

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

    public static String getOS()
    {
        return System.getProperty( "os.name" );
    }

    public static boolean isWindows()
    {
        return getOS().toLowerCase().contains( "win" );
    }

    public static boolean isMac()
    {
        return getOS().toLowerCase().contains( "mac" );
    }

    public static boolean isUnix()
    {
        String OS = getOS().toLowerCase();

        return OS.contains( "nix" ) ||
               OS.contains( "nux" ) ||
               OS.contains( "aix" );
    }

    public static boolean isSolaris()
    {
        String OS = getOS().toLowerCase();

        return OS.contains( "sunos" ) ||
               OS.contains( "solaris" );
    }

    public static boolean isLinux()
    {
        return getOS().toLowerCase().contains( "linux" );
    }

    public static boolean isHpUnix()
    {
        String OS = getOS().toLowerCase();

		return OS.contains( "hp-ux" ) ||
               OS.contains( "hpux"  );
    }

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
                String cpuInfo = Files.readString( modelPath );

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
                String osRelease = Files.readString( osReleasePath );

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

    private static File getDevEnvDir()   // This method is invoked only twice
    {
        return new File( System.getProperty( "user.home" ), "proyectos/mingle/todeploy" );
    }

    private static File getHomeDir()     // This method is invoked only once
    {
        return (isDevEnv ? getDevEnvDir()
                         : new File( System.getProperty( "user.dir" ) ));
    }
}