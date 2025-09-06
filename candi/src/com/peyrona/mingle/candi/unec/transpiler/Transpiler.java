
package com.peyrona.mingle.candi.unec.transpiler;

import com.peyrona.mingle.candi.IntermediateCodeWriter;
import com.peyrona.mingle.candi.unec.parser.ParseInclude;
import com.peyrona.mingle.candi.unec.parser.ParseUse;
import com.peyrona.mingle.lang.interfaces.IConfig;
import com.peyrona.mingle.lang.japi.UtilColls;
import com.peyrona.mingle.lang.japi.UtilIO;
import java.io.IOException;
import java.io.PrintWriter;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * ¡ BEFORE CHANGING ANYTHING, READ ME !
 * <p>
 * Previous to execute any USE command, it is needed to execute all INCLUDE commands,
 * because USE commands can be spread along all files and USE commands can change
 * Lexemes type; e.g. "EQUALS" is interpreted by the Lexer as a NAME, but after USE
 * changes it to "==" its type has to be updated as OPERATOR. This is done by the USE
 * class in method doUses(...)
 * <p>
 * NOTE: this class keeps internally a list of TransUnits. Some checks (like IDs uniqueness)
 * are performed against the whole set of TransUnits. Therefore when it is needed to check
 * that names are unique throughout more than one source code file, the <u>same Transpiler
 * instance</u> has to be used.
 *
 * @author Francisco José Morero Peyrona
 *
 * Official web site at: <a href="https://mingle.peyrona.com">https://mingle.peyrona.com</a>
 */
public final class Transpiler
{
    // !!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!
    //   BEFORE CHANGING ANYTHING, READ THIS CLASS DOC
    // ¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡

    private final IConfig         config;
    private       Charset         charset;
    private final List<TransUnit> tus = new ArrayList<>();

    //------------------------------------------------------------------------//
    // CONSTRUCTOR

    public Transpiler( IConfig config )
    {
        this.config = config;

        System.setProperty( "intermediate",
                            config.get( null, "intermediate", false ).toString() );   // Needed beacuse used at: IntermediateCodeWriter.java
    }

    //------------------------------------------------------------------------//

    public Transpiler execute( URI uri, Charset charset ) throws URISyntaxException, IOException
    {
        this.charset = (charset == null) ? Charset.defaultCharset() : charset;

        tus.addAll( doChecks(
                              doCommands(
                                          doIncludes( uri, new ArrayList<>() ) ) ) );    // Passing a List because doIncludes(...) is recursive

        if( IntermediateCodeWriter.isRequired() )
        {
            try( IntermediateCodeWriter writer = IntermediateCodeWriter.get() )
            {
                writer.startSection( "Lexemes after USEs\n" );

                for( TransUnit tu : tus )
                {
                    writer.writeln( tu.sourceUnit.uri )
                          .writeln( "Source: "+ tu.sourceUnit.uri +"\n" )
                          .writeln( tu.toCode() )
                          .writeSepara();
                }

                writer.endSection();
            }
        }

        return this;
    }

    public Transpiler output( PrintWriter pwCode, PrintWriter pwInfo )
    {
        if( pwCode == null )
            pwCode = new PrintWriter( System.out, true );

        if( pwInfo == null )
            pwInfo = new PrintWriter( System.out, true );

        if( config.get( null, "intermediate", false ) )
            pwInfo.println( "Intermediate code output file:\n"+ IntermediateCodeWriter.get().getTarget() +'\n' );

        new Writer().write( pwCode, pwInfo, tus );

        return this;
    }

    public List<TransUnit> getResult()
    {
        return Collections.unmodifiableList( tus );
    }

    //------------------------------------------------------------------------//
    // PRIVATE INTERFACE

    /**
     * Recursively load all needed files by searching INCLUDE commands in passed SourceUnit.
     *
     * @param tus
     * @param su
     * @return
     */
    private List<TransUnit> doIncludes( URI uri, List<TransUnit> tus ) throws URISyntaxException, IOException
    {
        SourceUnit su = new SourceUnit( uri, charset );

        tus.add( new TransUnit( su, config.newXprEval() ) );

        if( su.error == null )     // Error loading URI (URI is normally passed at CLI)
        {
            for( ParseInclude iu : UtilColls.getAt( tus, -1 ).getCommands( ParseInclude.class ) )
            {
                for( URI include : UtilIO.expandPath( iu.getURIs() ) )
                {
                    if( ! isUriAlreadyLoaded( tus, include.toString() ) )
                    {
                        doIncludes( include, tus );
                    }
                }
            }
        }

        return tus;
    }

    private List<TransUnit> doCommands( List<TransUnit> tus )
    {
        List<ParseUse> lstUse = new ArrayList<>();

        // Search for USE ... AS ... command in all Units

        for( TransUnit tu : tus )
        {
            for( ParseUse ru : tu.getCommands( ParseUse.class ) )
            {
                if( ru.getErrors().isEmpty() )
                    lstUse.add( ru );
                else
                    tu.addErrors( ru.getErrors() );
            }
        }

        // Apply second phase to every TranspilerUnit

        for( TransUnit tu : tus )
            tu.doCommands( lstUse );

        return tus;
    }

    /**
     * Performs last validations before serialization.
     *
     * @param tus
     * @return
     */
    private List<TransUnit> doChecks( List<TransUnit> tus )
    {
        new Checker( config ).check( tus );     // Do not make all methods 'static' in Checker class

        return tus;
    }

    private boolean isUriAlreadyLoaded( List<TransUnit> tus, String sURI )
    {
        for( TransUnit tu : tus )
        {
            if( sURI.equals( tu.sourceUnit.uri ) )
                return true;
        }

        return false;
    }
}