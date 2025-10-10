
package com.peyrona.mingle.candi.unec.parser;

import com.peyrona.mingle.candi.LangBuilder;
import com.peyrona.mingle.candi.unec.transpiler.UnecTools;
import com.peyrona.mingle.lang.interfaces.ICandi;
import com.peyrona.mingle.lang.japi.CommandSerializer;
import com.peyrona.mingle.lang.japi.UtilColls;
import com.peyrona.mingle.lang.japi.UtilStr;
import com.peyrona.mingle.lang.lexer.Lexeme;
import java.util.Arrays;
import java.util.List;

/**
 *
 * @author Francisco José Morero Peyrona
 *
 * Official web site at: <a href="https://github.com/peyrona/mingle">https://github.com/peyrona/mingle</a>
 */
public final class ParseScript extends ParseBase
{
    public  final        String   language;
    public               String   callName;   // Returns the SCRIPT entry point (in OOP languages, a method's name). Note: This is an exception to the Une "ignore-case-principle": method name case must be preserved.
    public  final        String[] from;
    public  final        boolean  isOnStart;
    public  final        boolean  isOnStop;
    public  final        boolean  isInline;
    private final static String   sKEY = "SCRIPT";


    //------------------------------------------------------------------------//
    // STATIC INTERFACE

    public static boolean is( String source )
    {
        return UtilStr.startsWith( source, sKEY );
    }

    //------------------------------------------------------------------------//

    public ParseScript( List<Lexeme> lstToken )
    {
        super( lstToken, "script", "from", "language", "call", "onstart", "onstop" );

        List<Lexeme> lstTokenFrom = getClauseContents( "FROM" );

        name      = findName();
        isInline  = UtilColls.isNotEmpty( lstTokenFrom ) && lstTokenFrom.get(0).isInline();   // Before 'callName' because 'callName' uses it
        language  = getLang( getClauseContents( "language" ) );                               // Before 'from' because 'from' uses it
        callName  = getCall( getClauseContents( "call"     ) );                               // This must preserve the string-case
        from      = getFrom( getClauseContents( "from"     ) );
        isOnStart = getClauseContents( "onstart" ) != null;
        isOnStop  = getClauseContents( "onstop"  ) != null;

        if( (name == null) && (! (isOnStart || isOnStop)) )
            addError( "SCRIPT has no name: this is allowed only when it is either ONSTART or ONSTOP:", lstToken.get(0) );
    }

    //------------------------------------------------------------------------//
    // PUBLIC INTERFACE

    @Override
    public String serialize()
    {
        return CommandSerializer.Script( (name == null ? null : name.text().toLowerCase()),
                                         language.toLowerCase(),
                                         isOnStart,
                                         isOnStop,
                                         isInline,
                                         from,
                                         callName );    // Must preserve case
    }

    //------------------------------------------------------------------------//
    // PROTECTED INTERFACE

    //------------------------------------------------------------------------//
    // PRIVATE INTERFACE

    private Lexeme findName()
    {
        if( isClauseEmpty( sKEY ) )
            return null;                     // The clause 'SCRIPT' is mandatory (has to be present) but it can has no name only when SCCRIPT is ONSTART or ONSTOP

        return findID( sKEY );
    }

    /**
     * 'FROM' clause is mandatory, can not be empty and must be only one token.
     *
     * @param lexemes
     */
    private String[] getFrom( List<Lexeme> lexemes )
    {
        if( isClauseMissed( "FROM", lexemes ) ||
            isClauseEmpty(  "FROM", lexemes ) )
        {
            return new String[0];
        }

        Lexeme token = UtilColls.find( lexemes, (lex) -> ! lex.isDelimiter() );   // Finds 1st not blank token

        if( token.isInline() )                          // When the code is isInline,
            return new String[] { compile( token ) };   // it can be compiled now)

        // If it is not Inline, then has to be one (or more) URIs (code will be loaded and compiled at execution time)
        // These Strings could contain macros like '{*home*}', but they can not be expanded (invoking UriReader.expandURI(...))
        // because the transpiled code will not be portable. It is CIL's Builder responsability to do it.

        List<List<Lexeme>> splitted = UnecTools.splitByDelimiter( lexemes );
        String[]           asURIs   = new String[ splitted.size() ];

        for( int n = 0; n < splitted.size(); n++ )
            asURIs[n] = splitted.get( n ).get( 0 ).text();    // get(0) because each line of Une source code is one lexeme: FROM {*home*}file1.jar; {*home*}file2.jar; ...

        return asURIs;
    }

    /**
     * 'CALL' is optional
     *
     * @param aoToken
     */
    private String getCall( List<Lexeme> tokens )
    {
        boolean bNotExists = UtilColls.isEmpty( tokens ) || UtilColls.areAll( tokens, (item) -> ((Lexeme) item).isDelimiter() );

        if( bNotExists )   // Clause does not exists: it could be OK because under certain circumstances this clause is optional
            return null;

        Lexeme token = UtilColls.find( tokens, (lex) -> ! lex.isDelimiter() );

        return token.text();    // This is an exception to the Une "ignore-case-principle": method name case must be preserved.
    }

    /**
     * 'LANGUAGE' clause is mandatory, can not be empty and must be only one token.
     *
     * @param aoToken
     */
    private String getLang( List<Lexeme> tokens )
    {
        if( isClauseMissed( "LANGUAGE", tokens ) ||
            isClauseEmpty(  "LANGUAGE", tokens ) ||
            isNotOneToken(  "LANGUAGE", tokens ) )
        {
            return null;
        }

        Lexeme id = findID( "LANGUAGE" );

        if( id != null )
        {
            String sLang = id.text().toLowerCase();

            ICandi.ILanguage lang = new LangBuilder().build( sLang );

            if( lang == null )
                addError( "There is no language artifact registered for: "+ id, getClause( "LANGUAGE" ) );

            return sLang;
        }

        return null;
    }

    /**
     * Compiles passed source code and returns the result (encoded using Base64 if needed).
     *
     * @param source
     * @return The compiled code.
     */
    private String compile( final Lexeme lexSource )
    {
        ICandi.ILanguage lang = new LangBuilder().build( language );

        if( lang == null )
            return null;

        String code = null;

        ICandi.IPrepared compiled;
        compiled = lang.prepare( lexSource.text(), callName );

        if( compiled.getErrors().length == 0 )
        {
            code = compiled.getCode();

            if( UtilStr.areNotEquals( callName, compiled.getCallName() ) )     // v.g.: Java compiler creates a callName under certain circumstances
            {
                synchronized( lexSource )
                {
                    callName = compiled.getCallName();
                }
            }
        }
        else
        {   // TODO; esto no va bien
            addErrors( lexSource.line(), 0, Arrays.asList( compiled.getErrors() ) );
        }

        return code;
    }
}