
package com.peyrona.mingle.lang.japi;

import com.peyrona.mingle.lang.interfaces.ILogger;
import com.peyrona.mingle.lang.interfaces.ILogger.Level;
import java.awt.GraphicsEnvironment;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.List;
import java.util.logging.ConsoleHandler;
import java.util.logging.FileHandler;
import java.util.logging.LogRecord;

/**
 * This class allows to easily work with logging tasks in applications.
 * <p>
 * Where are log files stored?<br>
 * If the System property "logs.dir" is defined, then it will be used,
 * otherwise it will be: <code>File( Config.getHomeDir(), "logs" )</code>.
 * <p>
 * If passed file name is empty, then exceptions are not stored into a file, they
 * are only shown in System:out.
 *
 * Created by peyrona on 21/07/2013.
 */
public final class Logger implements ILogger
{
    private static final String  sLOG_EXT      = ".log.txt";       // Extensión para el nombre de los ficheros de Log
    private static final String  sLOG_LOCK_EXT = ".log.txt.lck";   // Extensión para el nombre de los ficheros de lock de los Log

    private java.util.logging.Logger oLogger;    // This uses fFOLDER
    private Level  level;
    private String sName;

    //------------------------------------------------------------------------//

    /**
     * Creates a logger which outputs to file named as sFileName when 'bUseDisk' is
     * true and also to the console if third parameter is true.<br>
     *
     * @param sFileName File name to save errors.
     * @param bUseDisk
     * @param bAlwaysConsole Output also to the console.
     * @return This instance.
     */
    @Override
    public ILogger init( String sFileName, boolean bUseDisk, boolean bAlwaysConsole )
    {
        File fLog = null;

        if( bUseDisk && UtilStr.isNotEmpty( sFileName ) )
        {
            File fLogDir = new File( UtilSys.fHomeDir, "log" );

            if( fLogDir.exists() )    // It is not needed to be created here: it is created by UtilSys
            {
                if( ! fLogDir.isDirectory() )
                {
                    fLog = null;
                    System.err.println( fLogDir +" exists but is is not a folder." );
                }
                else if( ! fLogDir.canWrite() )
                {
                    fLog = null;

                    String name = "unknown";

                    try{ Files.getOwner( fLogDir.toPath() ).getName(); }
                    catch( IOException ioe ) { }

                    System.err.println( "Current use does not have permission to write in "+ fLogDir +
                                        "\n.The owner of this folder is "+ name );
                }
                else
                {
                    fLog = new File( fLogDir, sFileName );
                }
            }
            else
            {
                fLog = null;
                System.err.println( "Unable to create logs folder." );
            }
        }

        boolean bConsole = bAlwaysConsole ||
                          (fLog == null)  ||                   // When fLog is null, outputs unconditionally to the console (user can always redirect outputs to a file)
                          ! GraphicsEnvironment.isHeadless();

        setLevel( (Level) null );     // null == takes the default one

        oLogger = initLogger( fLog, bConsole );    // At this point, oLogger is never null

        return this;
    }

    //------------------------------------------------------------------------//
    // GENERAL PURPOSE

    @Override
    public String getName()
    {
        return ((sName == null) ? "" : sName + sLOG_EXT);
    }

    @Override
    public String toString()
    {
        return UtilStr.toString( this );
    }

    //------------------------------------------------------------------------//
    // WORKING WITH LOG FILES

    /**
     * Returns all log files (and optionally also lock log files) available in log folder.
     *
     * @param bIncludeLockFiles
     * @return All log files (and optionally also lock log files) available in log folder.
     */
    public List<File> getAllLogFiles( boolean bIncludeLockFiles )
    {
        return UtilIO.listFiles( new File( UtilSys.fHomeDir, "log" ),
                                 (File f) ->
                                 {
                                     if( ! f.isFile() )
                                         return false;

                                     String name = f.getName();

                                     return name.endsWith( sLOG_EXT ) || (bIncludeLockFiles && name.endsWith( sLOG_LOCK_EXT ));
                                 } );
    }

    @Override
    public ILogger deleteOlderThan( int hours )
    {
        if( hours > 0 )
        {
            long now = System.currentTimeMillis();
            long max = UtilUnit.HOUR * hours;       // Tiene q ser un long (el int puede salir negativo)

            for( File f : getAllLogFiles( true ) )
            {
                if( (now - f.lastModified()) > max )
                {
                    try
                    {
                        UtilIO.delete( f );
                    }
                    catch( IOException ioe )
                    {
                        log( Level.WARNING, ioe );
                    }
                }
            }
        }

        return this;
    }

    //----------------------------------------------------------------------------//
    // LOGGING

    @Override
    public boolean say( String msg )
    {
        if( ! GraphicsEnvironment.isHeadless() )
            System.out.println( msg );

        return _log_( Level.INFO, msg );
    }

    @Override
    public Level getLevel()
    {
        return level;
    }

    @Override
    public ILogger setLevel( String sLevel )
    {
        if( UtilStr.isNotEmpty( sLevel ) )
        {
            try
            {
                setLevel( ILogger.Level.fromName( sLevel ) );
            }
            catch( IllegalArgumentException iae )
            {
                log( ILogger.Level.SEVERE, iae, "Invalid log level in config: "+ sLevel +"; using default one: "+ getLevel() );
            }
        }

        return this;
    }

    @Override
    public synchronized ILogger setLevel( Level level )
    {
        this.level = (level == null) ? Level.WARNING : level;

        return this;
    }

    @Override
    public boolean isLoggable( Level level )
    {
        return (level.weight >= this.level.weight);
    }

    @Override
    public boolean log( Level level, String msg )
    {
        return log( level, null, msg );
    }

    @Override
    public boolean log( Level level, Throwable th )
    {
        return log( level, th, null );
    }

    /**
     * This is a special one because use an IBus if passed.
     *
     * @param level
     * @param th
     * @param msg
     * @return
     */
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

            return _log_( level, msg );
        }

        return false;
    }

    //----------------------------------------------------------------------------//
    // AUX FUNCTIONS

    /**
     * Unconditionally logs the message.
     *
     * @param level
     * @param sMessage
     * @return Always returns true.
     */
    private boolean _log_( Level level, String sMessage )
    {
        sMessage = "["+level+"] "+ sMessage;

        oLogger.log( myLevel2javaLevel( level ), sMessage );

        return true;
    }

    /**
     * Inicializa el sistema de Logging en base a un fichero de Properties y si
     * este no existiese, se utilizan ciertos valores por defecto.
     * <p>
     * Véase:
     *         http://www.hildeberto.com/2009/04/using-java-logging-configuration-file.html
     *         http://java.ociweb.com/mark/programming/JavaLogging.html
     *
     * @return El logger inicializado.
     */
    private java.util.logging.Logger initLogger( File fLog, boolean bConsole )
    {
        System.setProperty( "java.util.logging.SimpleFormatter.format", "[%1$tF %1$tT] %5$s %n" );

        File fLogDir = new File( UtilSys.fHomeDir, "log" );

        sName = (fLog == null) ? fLogDir.getParentFile().getName()
                               : fLog.getName();

        java.util.logging.Logger logger = java.util.logging.Logger.getLogger( sName );
                                 logger.setUseParentHandlers( false );              // Prevent parent handlers from processing the log records
                                 logger.setLevel( java.util.logging.Level.ALL );    // ALL because I decide which ones will be logged and wich will be not

        if( bConsole || fLog == null )
            logger.addHandler( new MyConsoleHandler() );

        if( fLog != null )
        {
            try
            {
                FileHandler fh = new FileHandler( fLog.getAbsolutePath() +"-%u.%g"+ sLOG_EXT, 5 * UtilUnit.MEGA_BYTE, 9, true );

                logger.addHandler( fh );
            }
            catch( IOException ex )
            {
                System.err.println( "Error while configuring "+ logger.getClass() );
                ex.printStackTrace( System.err );
            }
        }

        return logger;
    }

    private java.util.logging.Level myLevel2javaLevel( ILogger.Level level )
    {
        switch( level )
        {
            case ALL    : return java.util.logging.Level.ALL;
            case INFO   : return java.util.logging.Level.INFO;
            case MESSAGE: return java.util.logging.Level.FINEST;
            case OFF    : return java.util.logging.Level.OFF;
            case RULE   : return java.util.logging.Level.FINE;
            case SEVERE : return java.util.logging.Level.SEVERE;
            case WARNING:
            default     : return java.util.logging.Level.WARNING;
        }
    }

    //------------------------------------------------------------------------//
    // INNER CLASS
    //------------------------------------------------------------------------//
    private final class MyConsoleHandler extends ConsoleHandler
    {
        MyConsoleHandler()
        {
            setLevel( java.util.logging.Level.ALL );
        }

        @Override
        public void publish( LogRecord record )
        {
            String msg = record.getMessage();

                 if( record.getLevel().equals( java.util.logging.Level.SEVERE  ) )  UtilANSI.outRed(    msg );
            else if( record.getLevel().equals( java.util.logging.Level.WARNING ) )  UtilANSI.outYellow( msg );
            else if( record.getLevel().equals( java.util.logging.Level.FINE    ) )  UtilANSI.outCyan(   msg );   // RULE     (changed in ::myLevel2javaLevel)
            else if( record.getLevel().equals( java.util.logging.Level.FINEST  ) )  UtilANSI.outPurple( msg );   // MESSAGE  (changed in ::myLevel2javaLevel)
        }
    }
}