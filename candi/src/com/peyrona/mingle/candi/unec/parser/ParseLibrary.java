/*
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.peyrona.mingle.candi.unec.parser;

import com.peyrona.mingle.candi.unec.transpiler.UnecTools;
import com.peyrona.mingle.lang.interfaces.IXprEval;
import com.peyrona.mingle.lang.japi.CommandSerializer;
import com.peyrona.mingle.lang.japi.Pair;
import com.peyrona.mingle.lang.japi.UtilStr;
import com.peyrona.mingle.lang.japi.UtilType;
import com.peyrona.mingle.lang.lexer.Language;
import com.peyrona.mingle.lang.lexer.Lexeme;
import com.peyrona.mingle.lang.xpreval.functions.StdXprFns;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Represents a compiled LIBRARY command.
 * <p>
 * A LIBRARY command exposes an external function collection (Java JAR, Python module,
 * JavaScript file or any other Mingle supported language) so that its public methods
 * are callable directly in WHEN/THEN/CONFIG expressions using the colon operator:
 * {@code LibraryName:functionName(args)}.
 * <p>
 * The library name is mandatory: the runtime uses it to locate the matching class in the
 * JAR (case-insensitive simple-name match) — no CLASS clause is required.
 *
 * @author Francisco José Morero Peyrona
 *
 * Official web site at: <a href="https://github.com/peyrona/mingle">https://github.com/peyrona/mingle</a>
 */
public final class ParseLibrary extends ParseBase
{
    public  final        String             language;
    public  final        String[]           from;
    public  final        Map<String,Lexeme> config;
    private final        IXprEval           xprEval;
    private static final String             sKEY = "LIBRARY";

    //------------------------------------------------------------------------//
    // STATIC INTERFACE

    /**
     * Returns true if the given source line starts with the LIBRARY keyword.
     *
     * @param source The Une source line to test.
     * @return true if this parser applies.
     */
    public static boolean is( String source )
    {
        return UtilStr.startsWith( source, sKEY );
    }

    //------------------------------------------------------------------------//

    /**
     * Parses a LIBRARY command from its lexeme list.
     *
     * @param lstToken The lexemes representing the LIBRARY command block.
     * @param xprEval  Expression evaluator used to resolve CONFIG values at transpile-time.
     */
    public ParseLibrary( List<Lexeme> lstToken, IXprEval xprEval )
    {
        super( lstToken, "library", "language", "from", "config" );

        this.xprEval = xprEval;

        name = findName();

        if( name != null )
            StdXprFns.registerLibraryName( name.text() );   // Phase 1: must happen during doCommands()
                                                             // so subsequent ParseRule tokenization sees
                                                             // this name as a library namespace.

        language = getLang(   getClauseContents( "language" ), sKEY );
        from     = getFrom(   getClauseContents( "from"     ) );
        config   = getConfig( getClauseContents( "config"   ) );

        if( name == null )
            addError( "LIBRARY command requires a name.", lstToken.get(0) );
    }

    //------------------------------------------------------------------------//
    // PUBLIC INTERFACE

    /**
     * Returns an unmodifiable view of the CONFIG map.
     *
     * @return Unmodifiable map of config key → Lexeme value.
     */
    public Map<String,Lexeme> getConfigMap()
    {
        return Collections.unmodifiableMap( config );
    }

    @Override
    public String serialize()
    {
        Map<String,Object> resolvedConfig = new HashMap<>();

        config.forEach( (k, v) -> resolvedConfig.put( k, UtilType.toUne( v ) ) );

        return CommandSerializer.Library( name.text().toLowerCase(),
                                          language.toLowerCase(),
                                          from,
                                          resolvedConfig );
    }

    //------------------------------------------------------------------------//
    // PROTECTED INTERFACE

    //------------------------------------------------------------------------//
    // PRIVATE INTERFACE

    private Lexeme findName()
    {
        if( isClauseEmpty( sKEY ) )
            return null;

        return findID( sKEY );
    }

    /**
     * 'FROM' clause is mandatory for Java, can not be empty, and contains one or more URIs.
     */
    private String[] getFrom( List<Lexeme> lexemes )
    {
        if( lexemes == null || isClauseEmpty( "FROM", lexemes ) )
            return new String[0];

        List<List<Lexeme>> splitted = UnecTools.splitByDelimiter( lexemes );
        String[]           asURIs   = new String[ splitted.size() ];

        for( int n = 0; n < splitted.size(); n++ )
            asURIs[n] = splitted.get( n ).get( 0 ).text();

        return asURIs;
    }

    /**
     * 'CONFIG' is optional. When present, each entry has the form: {@code name = value}.
     */
    private Map<String,Lexeme> getConfig( List<Lexeme> tokens )
    {
        Map<String,Lexeme> map2Ret = new HashMap<>();

        if( tokens == null || isClauseEmpty( "CONFIG", tokens ) )
            return map2Ret;

        List<List<Lexeme>> lstAllOpts = UnecTools.splitByDelimiter( tokens );

        for( List<Lexeme> lstOneOpt : lstAllOpts )
        {
            Pair<String,Lexeme> pair = parseLine( lstOneOpt );

            if( pair != null )
                putNoDuplicate( map2Ret, pair, "CONFIG", lstOneOpt.get(0) );
        }

        return map2Ret;
    }

    /**
     * Parses a single config line of the form: {@code name = literal | expression}.
     * <p>
     * The key may be a dotted identifier (e.g. {@code returns.floor}), which the lexer
     * splits into multiple tokens. This method therefore scans forward to find the {@code =}
     * operator rather than assuming it is always at index 1.
     *
     * @param line The lexemes for one config entry.
     * @return A key-value pair, or null if the line is invalid.
     */
    private Pair<String,Lexeme> parseLine( List<Lexeme> line )
    {
        if( line.size() < 3 )
        {
            addError( "Unrecognized syntax in CONFIG, expected: \"<name> = <literal | expression> [; ...]\"", line.get(0) );
            return null;
        }

        // Find the '=' operator — it may not be at index 1 when the key is dotted (e.g. "returns.floor")
        int eqIdx = -1;

        for( int n = 0; n < line.size(); n++ )
        {
            if( Language.isAssignOp( line.get(n).text() ) )
            {
                eqIdx = n;
                break;
            }
        }

        if( eqIdx < 1 )
        {
            addError( '"'+ String.valueOf( Language.ASSIGN_OP ) +"\" operator expected but not found", line.get(0) );
            return null;
        }

        // Build the key from all tokens before '=', concatenated as-is (preserves dots)
        StringBuilder sbKey = new StringBuilder();

        for( int n = 0; n < eqIdx; n++ )
            sbKey.append( line.get(n).text() );

        String key = sbKey.toString().toLowerCase();

        // Validate every segment of the (possibly dotted) key individually
        for( String segment : key.split( "\\.", -1 ) )
        {
            String sErr = Language.isValidName( segment );

            if( sErr != null )
            {
                addError( "Name "+ sErr, line.get(0) );
                return null;
            }
        }

        List<Lexeme> lstLex = line.subList( eqIdx + 1, line.size() );

        // Single-identifier values are type declarations (e.g. "double", "int", "void").
        // They are not expressions: store them as plain strings without feeding to xprEval.
        if( lstLex.size() == 1 && lstLex.get(0).isName() )
            return new Pair( key, lstLex.get(0).updateAsStr( lstLex.get(0).text() ) );

        return evalConfigExpr( xprEval, key, lstLex );
    }
}
