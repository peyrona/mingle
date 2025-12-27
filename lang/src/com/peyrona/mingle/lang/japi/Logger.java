
package com.peyrona.mingle.lang.japi;

import com.peyrona.mingle.lang.interfaces.ILogger;
import com.peyrona.mingle.lang.interfaces.ILogger.Level;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.List;
import java.util.logging.ConsoleHandler;
import java.util.logging.FileHandler;
import java.util.logging.Formatter;
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
 * @author Francisco José Morero Peyrona
 *
 * Official web site at: <a href="https://github.com/peyrona/mingle">https://github.com/peyrona/mingle</a>
 */
public final class Logger implements ILogger
{
    private static final String sLOG_EXT      = ".log.txt";       // Extensión para el nombre de los ficheros de Log
    private static final String sLOG_LOCK_EXT = ".log.txt.lck";   // Extensión para el nombre de los ficheros de lock de los Log
    private static final String sLOG_FORMAT   = "[%1$tF %1$tT] %5$s %n";

    private java.util.logging.Logger oLogger;
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

            if( fLogDir.exists() || UtilIO.mkdirs( fLogDir ) )    // It is not needed to be created here: it is created by UtilSys. But done as double check.
            {
                if( ! fLogDir.isDirectory() )
                {
                    System.err.println( fLogDir +" exists but is is not a folder." );
                }
                else if( ! fLogDir.canWrite() )
                {
                    String name = "unknown";

                    try{ name = Files.getOwner( fLogDir.toPath() ).getName(); }
                    catch( IOException ioe ) { }

                    System.err.println( "Current user does not have permission to write in "+ fLogDir +
                                        "\n.The owner of this folder is "+ name );
                }
                else
                {
                    fLog = new File( fLogDir, sFileName );
                }
            }
            else
            {
                System.err.println( "'log' folder does not exists and can not be created." );
            }
        }

        setLevel( (Level) null );     // null == takes the default one

        bAlwaysConsole = bAlwaysConsole || (fLog == null);    // When fLog is null, outputs unconditionally to the console (user can always redirect outputs to a file)

        oLogger = initLogger( fLog, bAlwaysConsole );         // At this point, oLogger is never null

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
    public ILogger deleteOlderThan( int days )
    {
        if( days > 0 )
        {
            long now = System.currentTimeMillis();
            long max = UtilUnit.DAY * days;        // Tiene q ser un long (el int puede salir negativo)

            for( File f : getAllLogFiles( true ) )
            {
                long last = f.lastModified();

                if( (last > 0) &&                  // lastModified() returns 0 when unknown
                    (now - last) > max )
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
        System.out.println( msg );

        if( isLoggable( ILogger.Level.INFO ) )
            oLogger.log( myLevel2javaLevel( ILogger.Level.INFO ), msg );    // Has to use ::oLogger to unconditionally log (oLogger has Level.ALL)

        return true;
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
        System.setProperty( "java.util.logging.SimpleFormatter.format", sLOG_FORMAT );

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
                            fh.setFormatter( new MyXMLFormatter() );

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

    //------------------------------------------------------------------------//
    // INNER CLASS
    //------------------------------------------------------------------------//
    private final class MyXMLFormatter extends Formatter
    {
        @Override
        public String format( LogRecord record )
        {
            StringBuilder sb = new StringBuilder();
                          sb.append( "<record>\n" );
                          sb.append( "  <date>" ).append( record.getInstant() ).append( "</date>\n" );
                          sb.append( "  <level>" ).append( record.getLevel() ).append( "</level>\n" );
                          sb.append( "  <class>" ).append( record.getSourceClassName() ).append( "</class>\n" );
                          sb.append( "  <method>" ).append( record.getSourceMethodName() ).append( "</method>\n" );
                          sb.append( "  <thread>" ).append( record.getThreadID() ).append( "</thread>\n" );
                          sb.append( "  <message>" ).append( escapeXml( record.getMessage() ) ).append( "</message>\n" );
                          sb.append( "</record>\n" );
            return sb.toString();
        }

        private String escapeXml( String input )
        {
            if( input == null )
                return "";

            return input.replace( "&", "&amp;" )
                        .replace( "<", "&lt;" )
                        .replace( ">", "&gt;" )
                        .replace( "\"", "&quot;" )
                        .replace( "'", "&apos;" );
        }
    }
}