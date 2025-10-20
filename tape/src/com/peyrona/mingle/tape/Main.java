
package com.peyrona.mingle.tape;

import com.peyrona.mingle.lang.interfaces.IConfig;
import com.peyrona.mingle.lang.japi.Config;
import com.peyrona.mingle.lang.japi.UtilCLI;
import com.peyrona.mingle.lang.japi.UtilIO;
import com.peyrona.mingle.lang.japi.UtilStr;
import com.peyrona.mingle.lang.japi.UtilSys;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.Charset;
import java.util.List;

/**
 *
 * @author Francisco José Morero Peyrona
 *
 * Official web site at: <a href="https://github.com/peyrona/mingle">https://github.com/peyrona/mingle</a>
 */
public class Main
{
    /**
     *
     * Command line options have precedence over properties in configuration file.<br>
     * main() accepts only URIs, no source code.
     *
     * @param as the command line arguments
     * @throws java.net.URISyntaxException
     * @throws java.io.IOException
     */
    public static void main( String[] as ) throws URISyntaxException, IOException
    {
        UtilCLI cli = new UtilCLI( as );

        if( (as.length == 0) || cli.hasOption( "help" ) || cli.hasOption( "h" ) )
        {
            showHelp();
            return;
        }

        String    sCfgURI = cli.getValue( "config", null );
        IConfig   config  = new Config().load( sCfgURI ).setCliArgs( as );
        String[]  asURIs  = cli.getNoOptions();
        List<URI> lstURIs = UtilIO.expandPath( asURIs );
        Charset   charset = null;

        if( ! config.get( "exen", "use_disk", true ) )
            throw new IOException( "'use_disk' is set to false: can not transpile" );

        if( UtilStr.isNotEmpty( cli.getValue( "charset" ) ) )
        {
            try
            {
                charset = Charset.forName( cli.getValue( "charset" ) );
            }
            catch( Exception exc )
            {
                charset = Charset.defaultCharset();
                System.err.println( "Error: invalid charset '"+ cli.getValue( "charset" ) +"'. Using: '"+ charset.displayName() );
                exc.printStackTrace( System.err );
            }
        }

        System.out.println( getlogo() );
        System.out.println( "A transpiler for the Une language\n" );

        if( lstURIs.isEmpty() )
        {
            System.out.println( "Nothing to transpile: task finished." );
        }
        else
        {
            try
            {
                for( URI uri : lstURIs )
                    TranspilerTask.execute( config, charset, uri );
            }
            catch( Exception exc )
            {
                System.out.println( "MSP internal error:\n"+ UtilStr.toString( exc ) );
                System.exit( 1 );
            }
        }

        System.exit( 0 );
    }

    //------------------------------------------------------------------------//

    private static void showHelp()
    {
        System.out.println( getlogo() );
        System.out.println( "Version: "+ UtilSys.getVersion( Main.class ) +"\n\n"+
                            "Syntax:\n"+
                            "\ttape [options] <fileName1>[.une] ... <fileNameN>[.une]"+
                            "\n\n"+
                            "Options (all are optional)\n"+
                            "\t-grid=true|false         Transpiles to be executed in a grid environment.By default false.\n"+
                            "\t-charset=<a_charset>     Charset name to read local source files. By default the platform one.\n"+
                            "\t-intermediate=true|false Prints into files the intermediate code in 'tmp' folder. By default false.\n"+
                            "\t-config=URI              URI with the configuration file to use. By default, '{*home*}config.json'.\n"+
                            "\t-help | -h               Shows this help.\n"+
                            "\n\n"+
                            "Une files can be in the following forms:\n"+
                            "\t* File name:"+
                            "\t\t+ With or without path and with or without extension.\n"+
                            "\t\t+ Wildcards and macros (Glob Syntax) can be used.\n"+
                            "\t* URI:\n"+
                            "\t\t+ file://<file>\n"+
                            "\t\t+ http://<uri>\n"+
                            "\t\t+ https://<uri>\n"+
                            "\n\n"+
                            "For detailed information, refer to the handbook at: https://github.com/peyrona/mingle/docs" );
    }

    public static String getlogo()
    {
        return " _______    ____    _____    ______ "  +'\n'+     // Do not touch
               "|__   __|  /    \\  |  __ \\  |  ____|"+'\n'+
               "   | |    | |  | | | |__) | | |__   "  +'\n'+
               "   | |    | |==| | |  ___/  |  __|  "  +'\n'+
               "   | |    | |  | | | |      | |____ "  +'\n'+
               "   |_|    |_|  |_| |_|      |______|"  +'\n';
    }
}