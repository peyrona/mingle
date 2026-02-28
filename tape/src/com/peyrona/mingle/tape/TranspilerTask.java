
package com.peyrona.mingle.tape;

import com.peyrona.mingle.candi.unec.transpiler.TransUnit;
import com.peyrona.mingle.candi.unec.transpiler.Transpiler;
import com.peyrona.mingle.lang.interfaces.IConfig;
import com.peyrona.mingle.lang.japi.Chronometer;
import com.peyrona.mingle.lang.japi.UtilComm;
import com.peyrona.mingle.lang.japi.UtilSys;
import com.peyrona.mingle.lang.xpreval.functions.time;
import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.Charset;
import java.util.List;

/**
 * A utility class for executing the Une language transpilation task.
 * <p>
 * This class provides static methods to transpile Une source code files into
 * JSON model format (.model files). The transpilation process converts human-readable
 * Une declarative code into a structured JSON representation that can be executed
 * by the Mingle runtime engine (Stick).
 * <p>
 * The class supports transpilation from various sources including local files
 * (file://) and remote URLs (http://, https://). Output files are written to
 * local storage with a .model extension.
 *
 * @see com.peyrona.mingle.candi.unec.transpiler.Transpiler
 * @author Francisco José Morero Peyrona
 *
 * Official web site at: <a href="https://github.com/peyrona/mingle">https://github.com/peyrona/mingle</a>

 */
public final class TranspilerTask
{
    /**
     * Executes the transpilation task with console output directed to System.out.
     * <p>
     * This is a convenience method that internally delegates to the four-parameter
     * execute() method, using System.out for console output with auto-flushing enabled.
     *
     * @param config  the configuration object containing transpiler settings
     * @param charset the character encoding to use when reading the source file
     * @param uri     the URI of the Une source file to transpile (file://, http://, https://)
     * @return {@code true} if transpilation completed without errors, {@code false} otherwise
     * @throws URISyntaxException if the provided URI has an invalid syntax
     * @throws IOException        if an I/O error occurs while reading the source or writing output
     */
    public static boolean execute( IConfig config, Charset charset, URI uri ) throws URISyntaxException, IOException
    {
        return execute( config, charset, new PrintWriter( System.out, true ), uri ); // Enable auto-flushing
    }

    /**
     * Executes the transpilation task with the specified console output destination.
     * <p>
     * This method performs the complete transpilation workflow:
     * <ul>
     *   <li>Creates a Transpiler instance with the provided configuration</li>
     *   <li>Reads and parses the Une source file from the specified URI</li>
     *   <li>Writes the transpiled JSON model to a .model file</li>
     *   <li>Outputs progress and results to the provided PrintWriter</li>
     *   <li>Tracks and reports statistics (files processed, commands, errors)</li>
     *   <li>Measures and reports execution time</li>
     * </ul>
     * <p>
     * Output file location depends on the source protocol:
     * <ul>
     *   <li>file://  - Output in the same directory as the source</li>
     *   <li>http://  - Output in the system temporary directory</li>
     *   <li>https:// - Output in the system temporary directory</li>
     * </ul>
     * <p>
     * If errors occur during transpilation, the output file is deleted if it exists.
     *
     * @param config  the configuration object containing transpiler settings
     * @param charset the character encoding to use when reading the source file
     * @param console the PrintWriter for console output (progress, errors, statistics)
     * @param uri     the URI of the Une source file to transpile (file://, http://, https://)
     * @return {@code true} if transpilation completed without errors, {@code false} otherwise
     * @throws URISyntaxException if the provided URI has an invalid syntax
     * @throws IOException        if an I/O error occurs while reading the source or writing output
     */
    public static boolean execute( IConfig config, Charset charset, PrintWriter console, URI uri ) throws URISyntaxException, IOException
    {
        int         nFiles     = 0;
        int         nCommands  = 0;
        int         nErrors    = 0;
        Transpiler  transpiler = new Transpiler( config );
        Chronometer chrono     = new Chronometer();
        File        fOut       = getOutFile( uri );
        PrintWriter pwOut      = null;
        ProgressIndicator progress = new ProgressIndicator();

        try
        {
            pwOut = new PrintWriter( fOut );
        }
        catch( IOException ioe )
        {
            // Nothing to do: pwOut is already null
        }

        try
        {
            System.out.println();

            progress.start();

            List<TransUnit> tus = transpiler.execute( uri, charset )
                                            .execute( () -> progress.stop()  )
                                            .output( pwOut, console )    // No autoflush for code output (a file)
                                            .getResult();

            for( TransUnit tu : tus )
            {
                nFiles++;
                nCommands += tu.getCommands().size();
                nErrors   += tu.errorCount();
            }
        }
        catch( Exception exc )
        {
            progress.stop();
            throw exc;
        }

        console.print(   ">>>>>>>>>>>>>>>>>\nTranspiler output:" );
        if( nErrors > 0 )  console.println( " none" );
        else               console.println( "\n    "+ fOut.getAbsolutePath() );
        console.println( "<<<<<<<<<<<<<<<<<\n" );

        float n = chrono.getElapsed() / 1000f;

        console.println( "================================" );
        console.println( "Totals:  Files  Commands  Errors" );
        console.print(   "         " );
        console.print(   String.format( "%5d" , nFiles    ) );
        console.print(   String.format( "%10d", nCommands ) );
        console.println( String.format( "%8d" , nErrors   ) );
        console.println( "================================" );
        console.println();
        console.println( "["+ new time() +"] Tasks accomplished in "+ String.format( "%.2f", n ) +" seconds" );

        if( (nErrors > 0) && fOut.exists() && (! fOut.delete()) )
            console.println( "Error deleting \""+ fOut + '"' );

        return nErrors == 0;
    }

    //------------------------------------------------------------------------//

    /**
     * Determines the output file path for the transpiled model.
     * <p>
     * This method calculates where the .model output file should be written based on the source URI protocol and file name. The output file name is derived from the source file name by replacing the .une extension with .model.
     * <p>
     * Output location rules:
     * <ul>
     * <li><b>file://</b> - Output is placed in the same directory as the source file</li>
     * <li><b>http://</b> - Output is placed in the system temporary directory</li>
     * <li><b>https://</b> - Output is placed in the system temporary directory</li>
     * </ul>
     * <p>
     * This method assumes the URI is valid and represents a file location, not inline source code (which is not supported by the main entry point).
     *
     * @param uri the URI of the source file (e.g., "file://test.une", "https://www.myweb.com/test.une")
     * @return a File object representing the output .model file location
     */
    private static File getOutFile( URI uri )
    {
        String sURI  = uri.toString();
        String sName = sURI.substring( sURI.lastIndexOf( '/' ) + 1 );        // For the output file we need only the name, not the whole URI

        if( sName.toLowerCase().endsWith( ".une" ) )
            sName = sName.substring( 0, sName.length() - 4 );

        sName += ".model";

        switch( UtilComm.getFileProtocol( uri.toString() ) )
        {
            case http:
            case https:
                return new File( UtilSys.getTmpDir(), sName );

            case file:
            default:
                File fParent = new File( uri.normalize().getPath() ).getParentFile();    // ".model" will be in same folder as ".une"
                return new File( fParent, sName );    // fFolder can be null
        }
    }
}