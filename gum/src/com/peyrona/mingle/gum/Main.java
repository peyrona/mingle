
package com.peyrona.mingle.gum;

import com.eclipsesource.json.Json;
import com.eclipsesource.json.JsonObject;
import com.peyrona.mingle.lang.interfaces.IConfig;
import com.peyrona.mingle.lang.japi.Config;
import com.peyrona.mingle.lang.japi.UtilCLI;
import com.peyrona.mingle.lang.japi.UtilColls;
import com.peyrona.mingle.lang.japi.UtilIO;
import com.peyrona.mingle.lang.japi.UtilJson;
import com.peyrona.mingle.lang.japi.UtilStr;
import com.peyrona.mingle.lang.japi.UtilSys;
import com.peyrona.mingle.lang.japi.UtilUnit;
import java.io.File;
import java.io.IOException;
import java.net.InetAddress;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.UnknownHostException;
import java.util.List;

/**
 *
 *
 * @author Francisco José Morero Peyrona
 *
 * Official web site at: <a href="https://mingle.peyrona.com">https://mingle.peyrona.com</a>
 */
public class Main
{
    public static void main( String[] as ) throws IOException, URISyntaxException
    {
        UtilCLI cli = new UtilCLI( as );

        if( cli.hasOption( "help" ) || cli.hasOption( "h" ) )
        {
            showHelp();
            return;
        }

        IConfig config = new Config().load( cli.getValue( "config", null ) )    // If defined, use this config file (instead of the default one)
                                     .setCliArgs( as );

        UtilSys.setConfig( config );
        UtilSys.setLogger( "gum", config );
        UtilSys.getLogger().say( getLogo() );

        try
        {
            String     host  = config.get( "monitoring", "host", "" );
            int        port  = config.get( "monitoring", "port", 8080 );
            int        maxSe = config.get( "monitoring", "max_sessions", 64 );
            String     allow = config.get( "monitoring", "allow", "intranet" );
            JsonObject joSSL = config.get( "monitoring", "ssl", Json.object() );

            checkPreRequisites( joSSL );

            ServiceUtil.setSessionTimeout( (UtilSys.isDevEnv ? (3 * UtilUnit.HOUR / 1000)
                                                             : config.get( "monitoring", "timeout", 1800 )) );    // Inseconds

            if( UtilStr.isEmpty( host ) || UtilSys.isDocker() )    // For info when running inside a Docker:
            {
                try
                {
                    host = InetAddress.getLocalHost().getHostAddress();    // https://stackoverflow.com/questions/41647948/how-to-run-undertow-java-app-with-docker
                }
                catch( UnknownHostException uhe )
                {
                    host = null;
                }
            }

            UtilJson   juSSL   = (joSSL == null) ? null : new UtilJson( joSSL );
            HttpServer server  = new HttpServer( host,
                                                 port,
                                                 maxSe,
                                                 allow,
                                                 (juSSL == null) ?   -1 : juSSL.getInt(    "port",   -1 ),
                                                 (juSSL == null) ? null : juSSL.getString( "path", null ),
                                                 (juSSL == null) ? null : juSSL.getString( "pwd" , null ) )
                                     .start();

            // By using a hook we maximize the possibilities the finalization code will be
            // invoked: even if INTERRUPT signal (Ctrl-C) is used, the JVM will invoke this hook.
            // Even when System.exit(...) is used, the JVM will invoke this hook.

            Runtime.getRuntime()
                   .addShutdownHook( new Thread( () -> server.stop() ) );
        }
        catch( Exception ioe )
        {
            System.out.println( UtilStr.toString( ioe ) );
            System.out.println( "Can not continue" );
            System.exit( 1 );
        }
    }

    //------------------------------------------------------------------------//

    private static void showHelp()
    {
        System.out.println( getLogo() +
                            "Version: "+ UtilSys.getVersion( Main.class ) +'\n'+
                            '\n'+
                            "Syntax:\n"+
                            "\tgum [-config=<URI>] [-host=<host>] [-port=<port>] [-user=<user_name>] [-password=<pwd>] [-help] [-h]\n"+
                            "\t\tconfig  A JSON file with the configuration to use. By default, '{*home*}config.json'. It is optional.\n"+
                            "\t\thost    Host name. By default, 'localhost'. It is optional.\n"+
                            "\t\tport    Port number. By default, '8080'. It is optional.\n"+
                            '\n'+
                            "When both, config file and command line arguments are provided, these values have precedence over those at the config file.\n"+
                            '\n'+
                            "For detailed information, refer to the handbook or visit: https://mingle.peyrona.com/docs" );
    }

    private static String getLogo()
    {
        return "  ____    _    _    __  __  " +'\n'+
               " / ___|  | |  | |  |  \\/  |" +'\n'+
               "| |  _   | |  | |  | |\\/| |" +'\n'+
               "| |_| |  | |__| |  | |  | |"  +'\n'+
               " \\____|   \\____/   |_|  |_|"+'\n';
    }

    private static void checkPreRequisites( JsonObject joSSL ) throws Exception
    {
        if( ! Util.getAppDir().exists() )                                        // Where HTML/CSS/JS files are
                throw new IOException( Util.getAppDir() + ": does not exist" );

        if( UtilColls.isEmpty( Util.getAppDir().list() ) )
            throw new IOException( Util.getAppDir() +": is empty" );

        if( ! joSSL.isEmpty() )
        {
            if( joSSL.get( "port" ).isNull() || ! joSSL.get( "port" ).isNumber() )
                joSSL.set( "port", 8443 );

            if( joSSL.get( "port" ).asInt() < 1 || joSSL.get( "port" ).asInt() > 65535 )
                joSSL.set( "port", 8443 );

            if( joSSL.get( "path" ).isNull() )
            {
                throw new IOException( "Can not attend SSL without a 'keystore.jks' file" );
            }
            else
            {
                String    sPath   = joSSL.get( "path" ).asString();
                List<URI> lstURIs = UtilIO.expandPath( sPath );

                if( lstURIs.size() > 1 )
                    throw new IOException( "Path must be only one local file" );

                if( new File( lstURIs.get(0) ).exists() )
                    joSSL.set( "path", new File( lstURIs.get(0) ).getAbsolutePath() );
                else
                    throw new IOException( "'keystore.jks' file does not exists: "+ joSSL.get( "path" ).asString() );
            }

            if( joSSL.get( "pwd" ).isNull() )
                throw new IOException( "Can not attend SSL without a 'keystore.jks' password" );
        }

        if( UtilColls.isEmpty( Util.getServedFilesDir().list() ) )
        {
            UtilIO.newFileWriter()
                  .setFile( new File( Util.getServedFilesDir(), "READ_ME.txt" ) )
                  .append( "The files shown here are served by Gum from context 'user_files'.\nFor full files URL, refer to Gum startup info.\n\n"+
                           "Dashboards are stored under '"+ Util.getBoardsDir().getName() +"' folder: every Dashboard is stored in a folder having the same name as the Dashboard itself.");

            UtilIO.newFileWriter()
                  .setFile( new File( Util.getServedFilesDir(), "users.json" ) )
                  .append( "#---------------------------------------------------------------------------------------------"+
                           "# This is a very simplistic file and the use of it made by the HTTP server is evem simpler."+
                           "#"+
                           "# While user-name is case insensitive, user-password is case sensitive."+
                           "#"+
                           "# Following macros can be used (only in user-password, not in user-name):"+
                           "#   + {*d*} -> Will be replaced by current day of the month"+
                           "#   + {*h*} -> Will be replaced by current hour of the day"+
                           "#"+
                           "# Example: \"pwd\": \"{*d*}adm{*t*}in\""+
                           "#"+
                           "# Before saving, be sure the contents of this file are JSON compliant."+
                           "#"+
                           "# // TODO: improve how users are used by the HHTP Server"+
                           "#          Much more work has to be done in this area"+
                           "#"+
                           "#---------------------------------------------------------------------------------------------"+
                           "\n"+
                           "["+
                           "    { \"name\": \"root\", \"pwd\": \"admin\", \"role\": \"admin\" },"+
                           "    { \"name\": \"1234\", \"pwd\": \"12345\", \"role\": \"guest\", \"allow\": \"192.168.6.9\" }"+
                           "]" );
        }
    }
 }