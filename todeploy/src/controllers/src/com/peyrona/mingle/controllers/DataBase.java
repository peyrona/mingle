
package com.peyrona.mingle.controllers;

import com.peyrona.mingle.lang.MingleException;
import com.peyrona.mingle.lang.interfaces.IController;
import com.peyrona.mingle.lang.interfaces.ILogger;
import com.peyrona.mingle.lang.japi.UtilColls;
import com.peyrona.mingle.lang.japi.UtilIO;
import com.peyrona.mingle.lang.japi.UtilStr;
import com.peyrona.mingle.lang.japi.UtilSys;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * This Controller initializes a SQL DataBase Server using JDBC and sends SQL commands to it.
 * <p>
 * See note at ControllerBase.
 *
 * @author Francisco José Morero Peyrona
 *
 * Official web site at: <a href="https://github.com/peyrona/mingle">https://github.com/peyrona/mingle</a>
 */
public final class   DataBase
             extends ControllerBase
{
    private Map<String,Object>  mapConfig;
    private java.sql.Connection conn;

    //------------------------------------------------------------------------//

    @Override
    public void set( String deviceName, Map<String,Object> mapConfig, IController.Listener listener )
    {
        // DB Controller can work even if Use_Disk is off because the DB could reside in another machine

        setName( deviceName );
        setListener( listener );     // Must be at begining: in case an error happens, Listener is needed

        this.mapConfig = new HashMap<>( mapConfig );

        String sJARs = (String) mapConfig.get( "jars" );
        String sJDBC = (String) mapConfig.get( "jdbc" );

        try
        {
            String    sURL   = (String) mapConfig.get( "url" );
            List<URI> lstURL = UtilIO.expandPath( sURL );

            if( lstURL.size() != 1 )
            {
                sendIsInvalid( "Invalid URL: "+ sURL );
            }
            else
            {
                UtilSys.addToClassPath( UtilColls.toArrayOfStr( UtilColls.toArray( sJARs ) ) );
                Class.forName( sJDBC );

                connect( false );
                setValid( true );
            }
        }
        catch( IOException | ClassNotFoundException | URISyntaxException | SQLException ex )
        {
            sendIsInvalid( ex );
        }
    }

    @Override
    public void read()
    {
        // Nothing to do.
        // DO NOT DO THIS --> sendIsNotReadable();
    }

    @Override
    public void write( final Object sql )    // sql is one or more SQL commands
    {
        if( isFaked || isInvalid() || conn == null )
            return;

        if( ! (sql instanceof String) )
            sendWriteError( sql, new MingleException( "Is not a string" ) );

        UtilSys.execute( getClass().getName(),
                         () ->
                        {
                            Exception exc = null;

                            for( String s : sql.toString().split( ";" ) )
                            {
                                try
                                {
                                    execute( s );
                                }
                                catch( Exception se )
                                {
                                    try
                                    {
                                        connect( true );    // It could be that DB is a local file and the user deleted it
                                        execute( s );       // Lets try again
                                    }
                                    catch( Exception e )
                                    {
                                        exc = e;
                                        break;
                                    }
                                }
                            }

                            if( exc == null )  sendReaded( sql );
                            else               sendWriteError( sql, exc );
                        } );
    }

    @Override
    public void stop()
    {
        if( conn != null )
        {
            try
            {
                conn.close();
            }
            catch( SQLException ioe )
            {
                sendGenericError( ILogger.Level.WARNING, "Error closing DataBase: "+ ioe.getMessage() );
            }

            conn = null;
        }

        super.stop();
    }

    //------------------------------------------------------------------------//

    private void execute( String sql ) throws SQLException
    {
        try( java.sql.Statement stmt = conn.createStatement() )    // DB Statement is closeable
        {
            stmt.execute( sql );
        }
    }

    private void connect( boolean bCheck ) throws URISyntaxException, SQLException, IOException
    {
        if( bCheck && (! isValid()) )
            return;                     // Nothing to do because there were an error on ::add(...)

        if( conn != null )
        {
            try
            {
                conn.close();
            }
            catch( SQLException ioe )
            {
                // Nothing to do
            }
        }

        String sURL  = (String) mapConfig.get( "url"     );
        String sUser = (String) mapConfig.get( "user"    );
        String sPwd  = (String) mapConfig.get( "pwd"     );
        String sInit = (String) mapConfig.get( "initial" );   // Initial SQL command(s) to be sent after successfully be connected

        synchronized( this )
        {
            sURL = UtilIO.replaceFileMacros( sURL );

         // This is not needed because it is DB server responsability to create the folders if they do not exists (H2 does it) -->
         // if( UtilComm.getFileProtocol( sURL ) == UtilComm.Protocol.file )    // To create parent folders if they do not exist
         // {
         //     java.io.File fDB = new java.io.File( new URL( sURL ).toURI() );
         //
         //     UtilIO.mkdirs( fDB.getParentFile() );
         // }

            if( (sUser == null) || (sPwd == null) )  conn = java.sql.DriverManager.getConnection( sURL );
            else                                     conn = java.sql.DriverManager.getConnection( sURL, sUser, sPwd );

            conn.setAutoCommit( true );
        }

        if( sInit != null )
        {
            UtilSys.execute( getClass().getName(),
                             () ->
                            {
                                for( String s : sInit.split( ";" ) )
                                {
                                    try( java.sql.Statement stmt = conn.createStatement() )
                                    {
                                        stmt.execute( s );
                                    }
                                    catch( SQLException ex )
                                    {
                                        sendGenericError( ILogger.Level.SEVERE, "Error executing: "+ s +'\n'+ UtilStr.toStringBrief( ex ) );
                                    }
                                }
                            } );
        }
    }
}
