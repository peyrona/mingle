package com.peyrona.mingle.candi;

import com.peyrona.mingle.lang.japi.UtilStr;
import com.peyrona.mingle.lang.japi.UtilSys;
import java.io.Closeable;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;

/**
 *
 * @author Francisco José Morero Peyrona
 *
 * Official web site at: <a href="https://github.com/peyrona/mingle">https://github.com/peyrona/mingle</a>
 */
public final class IntermediateCodeWriter implements Closeable
{
    private PrintWriter pw = null;

    //------------------------------------------------------------------------//
    // SINGLETON METHODS

    private IntermediateCodeWriter()
    {
        if( isRequired() && getTarget().exists() )
            getTarget().delete();   // The file used for last time that the transpiled was used has to be replaced by the new one
    }

    public static IntermediateCodeWriter get()
    {
        return isRequired() ? IntermediateCodeWriterHolder.INSTANCE
                            : null;
    }

    private static class IntermediateCodeWriterHolder
    {
        private static final IntermediateCodeWriter INSTANCE = new IntermediateCodeWriter();
    }

    //------------------------------------------------------------------------//
    // STATIC

    public static boolean isRequired()
    {
        return System.getProperty( "intermediate", "false" ).equalsIgnoreCase( "true" );
    }

    //------------------------------------------------------------------------//

    public IntermediateCodeWriter startSection( String sSectionName )
    {
        println( UtilStr.fill( '-', 132 ) );
        println( "INTERMEDIATE CODE: "+ sSectionName );
        println();
        println();

        return this;
    }

    public IntermediateCodeWriter endSection()
    {
        println();
        println( UtilStr.fill( '-', 132 ) );

        return this;
    }

    public IntermediateCodeWriter write( String str )
    {
        print( str );

        return this;
    }

    public IntermediateCodeWriter writeln()
    {
        println();

        return this;
    }

    public IntermediateCodeWriter writeln( String line )
    {
        println( line );

        return this;
    }

    public IntermediateCodeWriter writeSepara()
    {
        println( UtilStr.fill( '-', 66 ) );

        return this;
    }

    public File getTarget()
    {
        return (isRequired() ? new File( UtilSys.getTmpDir(), "transpiler_intermediate.txt" )
                             : null);
    }

    @Override
    public void close()
    {
        if( pw != null )
        {
            pw.flush();
            pw.close();
            pw = null;
        }
    }

    //------------------------------------------------------------------------//
    // PROTECTED SCOPE

    //------------------------------------------------------------------------//
    // PRIVATE SCOPE

    private void println()
    {
        print( UtilStr.sEoL );
    }

    private void println( String s )
    {
        print( s + UtilStr.sEoL );
    }

    private void print( String s )
    {
        if( pw == null )   // This can be true more than once during same transpiler execution
        {
            PrintWriter p = null;

            try
            {
                p = new PrintWriter( new FileWriter( getTarget(), true ) );    // true == appending mode (it is needed)
            }
            catch( IOException ex )
            {
                ex.printStackTrace( System.err );
            }

            pw = p;
        }

        if( pw != null )
            pw.print( s );
    }
}