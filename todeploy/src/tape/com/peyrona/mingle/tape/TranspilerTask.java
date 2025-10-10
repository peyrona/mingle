/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package com.peyrona.mingle.tape;

import com.peyrona.mingle.candi.unec.transpiler.TransUnit;
import com.peyrona.mingle.candi.unec.transpiler.Transpiler;
import com.peyrona.mingle.lang.interfaces.IConfig;
import com.peyrona.mingle.lang.japi.Chronometer;
import com.peyrona.mingle.lang.japi.UtilComm;
import static com.peyrona.mingle.lang.japi.UtilComm.Protocol.file;
import static com.peyrona.mingle.lang.japi.UtilComm.Protocol.http;
import static com.peyrona.mingle.lang.japi.UtilComm.Protocol.https;
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
 *
 * @author francisco
 */
public final class TranspilerTask
{
    public static boolean execute( IConfig config, Charset charset, URI uri ) throws URISyntaxException, IOException
    {
        return execute( config, charset, new PrintWriter( System.out, true ), uri ); // Enable auto-flushing
    }

    public static boolean execute( IConfig config, Charset charset, PrintWriter console, URI uri ) throws URISyntaxException, IOException
    {
        int         nFiles     = 0;
        int         nCommands  = 0;
        int         nErrors    = 0;
        Transpiler  transpiler = new Transpiler( config );
        Chronometer chrono     = new Chronometer();

        File fOut = getOutFile( uri );

        List<TransUnit> tus = transpiler.execute( uri, charset )
                                        .output( new PrintWriter( fOut ), console )    // No autoflush for code output (a file)
                                        .getResult();

        for( TransUnit tu : tus )
        {
            nFiles++;
            nCommands += tu.getCommands().size();
            nErrors   += tu.errorCount();
        }

        console.print( ">>>>>>>>>>>>>>>>>\nTranspiler output:" );
        if( nErrors > 0 )  console.println( " none" );
        else               console.println( "\n    "+ fOut.getAbsolutePath() );
        console.println( "<<<<<<<<<<<<<<<\n" );

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
     * Returns the File to use (extension changed to '.model') to write in local-storage (HD).<br>
     * sURI is known to be valid before this method is invoked.
     * It could be something like this "file://test.une", or something like this: "https://www.myweb.com/test.une".
     * But never source code because 'main( String[] args )' does not accept it.
     *
     * @param sURI
     * @return
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