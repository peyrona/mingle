
package com.peyrona.mingle.stick;

import com.peyrona.mingle.lang.interfaces.IConfig;
import com.peyrona.mingle.lang.interfaces.ILogger;
import com.peyrona.mingle.lang.japi.Config;
import com.peyrona.mingle.lang.japi.Pair;
import com.peyrona.mingle.lang.japi.UtilCLI;
import com.peyrona.mingle.lang.japi.UtilColls;
import com.peyrona.mingle.lang.japi.UtilIO;
import com.peyrona.mingle.lang.japi.UtilStr;
import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.ListIterator;

/**
 * Class to run an stand-alone instance of Stick.
 *
 * @author Francisco José Morero Peyrona
 *
 * Official web site at: <a href="https://mingle.peyrona.com">https://mingle.peyrona.com</a>
 */
public final class Main
{
    public static void main( String[] as ) throws IOException
    {
        UtilCLI cli = new UtilCLI( as );

        if( cli.hasOption( "help" ) || cli.hasOption( "h" ))
        {
            System.out.println( UtilIO.getAsText( Main.class.getResourceAsStream( "help.txt" ),     // Done using a .txt file to save RAM
                                                  Charset.forName( "UTF-8" ) ) );
        }
        else
        {
            Stick stick = null;

            try
            {
                String              sCfgURI = cli.getValue( "config", null );
                IConfig             config  = new Config().load( sCfgURI ).setCliArgs( as );
                Pair<String,String> pair    = loadFirstValidModel( getModels( cli, config ) );
                String              sName   = pair == null ? null : pair.getKey();
                String              sJSON   = pair == null ? null : pair.getValue();

                stick = new Stick( sJSON, config ).start( sName );
            }
            catch( Exception exc )
            {
                if( stick != null )  stick.log( ILogger.Level.SEVERE, exc );
                else                 System.err.println( exc.getMessage() + "\nStick can not continue.");
            }
        }
    }

    //-----------------------------------------------------------------------//

    // URI extension ".model" is not mandatory
    // 'model' can be null because Stick can run without a model: it can be built later

    private static String[] getModels( UtilCLI cli, IConfig config ) throws IOException, URISyntaxException
    {
        String[]     asURI    = cli.getNoOptions();
        List<String> lstModel = new ArrayList<>();                 // Config file can be empty, or can have one file or an array of files

        // First look among CLI args

        if( UtilColls.isNotEmpty( asURI ) )    // CLI args have precedence over config file
        {
            if( asURI.length > 1 )
            {
                System.err.println( "One and only one Model file needed.\nReceived: '"+ UtilColls.toString( asURI ) +'\'' );
                System.exit( 1 );
            }

            lstModel.add( asURI[0].trim() );
        }

        // Now look in config file (it can be one file or an array of files)

        try
        {
            String sModel = config.get( "exen", "model", "" );     // First we try considering it is one single item

            if( UtilStr.isNotEmpty( sModel ) )
                lstModel.add( sModel );
        }
        catch( Exception ex )                                      // If this is not the case, lets consider it is an array
        {
            String[] as = config.get( "exen", "model", new String[0] );

            if( UtilColls.isNotEmpty( as ) )
                lstModel.addAll( Arrays.asList( as ) );
        }

        for( ListIterator<String> itera = lstModel.listIterator(); itera.hasNext(); )
        {
            String sModel = itera.next();

            if( UtilStr.isMeaningless( sModel ) )
            {
                itera.remove();
            }
            else
            {
                if( UtilStr.endsWith( sModel, ".une" ) )
                    sModel = UtilStr.removeLast( sModel, 4 );

                if( ! UtilStr.endsWith( sModel, ".model" ) )
                    sModel = sModel + ".model";

                itera.set( sModel );
            }
        }

        return lstModel.toArray( String[]::new );
    }

    private static Pair<String,String> loadFirstValidModel( String[] asModel ) throws IOException
    {
        // Can not log because logger is not initialized

        if( UtilColls.isEmpty( asModel ) )
            return null;                   // 'model' can be null because Stick can run without a model: it can be built later

        for( String sName : asModel )
        {
            try
            {
                return new Pair( sName, UtilIO.getAsText( sName ) );
            }
            catch( IOException ioe )
            {
                System.err.println( "Can not load: "+ sName );
            }
        }

        throw new IOException( "None of the proposed models can be loaded." );
    }
}