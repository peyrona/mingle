
package com.peyrona.mingle.candi.unec.parser;

import com.peyrona.mingle.candi.LangBuilder;
import com.peyrona.mingle.candi.unec.transpiler.UnecTools;
import com.peyrona.mingle.lang.interfaces.ICandi;
import com.peyrona.mingle.lang.interfaces.IXprEval;
import com.peyrona.mingle.lang.japi.Pair;
import com.peyrona.mingle.lang.japi.UtilColls;
import com.peyrona.mingle.lang.japi.UtilComm;
import com.peyrona.mingle.lang.japi.UtilIO;
import com.peyrona.mingle.lang.japi.UtilStr;
import com.peyrona.mingle.lang.lexer.CodeError;
import com.peyrona.mingle.lang.lexer.Language;
import com.peyrona.mingle.lang.lexer.Lexeme;
import com.peyrona.mingle.lang.lexer.Lexer;
import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Base class for Transpilation Structures: each structure is an entity obtained
 * from its corresponding source-code command.
 *
 * @author Francisco José Morero Peyrona
 *
 * Official web site at: <a href="https://github.com/peyrona/mingle">https://github.com/peyrona/mingle</a>
 */
public abstract class ParseBase
{
    protected       Lexeme                   name       = null;
    private   final List<ICandi.IError>      lstErrors  = new ArrayList<>();      // If after transpiling this is still an empty array, then ::transpiled will be a valid command
    private   final Map<Lexeme,List<Lexeme>> mapClauses = new LinkedHashMap<>();  // This Map has as many map.key as clauses has the command, and the map.value,
                                                                                  // is a list with the contents of the clause parsed into items of the Lexemes.
                                                                                  // Has to be a LinkedMashMap because the order of adding the entries has to be preserved.
    //------------------------------------------------------------------------//

    /**
     * Serializes into JSON format this Transpiled Command Unit, returning the JSON representation
     * of the command.
     * <p>
     * INCLUDE and USE commands return null.
     *
     * @return The JSON representation of the command managed.
     */
    public abstract String serialize();

    //------------------------------------------------------------------------//

    protected ParseBase()
    {
        this( null, (String[]) null );
    }

    protected ParseBase( List<Lexeme> lexemes, String... clauses )
    {
        if( ! UtilColls.isEmpty( lexemes ) )
        {
            mapClauses.putAll( UnecTools.splitByClause( lexemes, clauses ) );

            List<Lexeme> lstNoClause = mapClauses.remove( null );

            if( UtilColls.isNotEmpty( lstNoClause ) )
            {
                lstErrors.add( new CodeError( "Unrecognized syntax", lstNoClause.get( 0 ) ) );
            }
        }
    }

    //------------------------------------------------------------------------//
    // PUBLIC INTERFACE

    public final Lexeme getStart()
    {
        return mapClauses.keySet().iterator().next();   // The 1st key in map
    }

    public final Lexeme getName()
    {
        return name;
    }

    /**
     * Returns the errors found during analyzing the code.
     *
     * @return
     */
    public final List<ICandi.IError> getErrors()
    {
        return lstErrors;
    }

    /**
     * Returns associated Lexeme with passed clause.<br>
     * The search for tokens is done among all tokens passed to this class constructor and
     * using the array of string (representing the clauses of the command) passed to same
     * constructor.
     * <p>
     * Returned lexeme will be null if the clause has no arguments and will be null if the
     * clause does not exist.
     *
     * @param sClauseName
     * @return The Lexeme associated with passed clause.
     */
    public final Lexeme getClause( String sClauseName )
    {
        for( Lexeme lex : mapClauses.keySet() )
        {
            if( lex.isText( sClauseName ) )
                return lex;
        }

        return null;
    }

    /**
     * Returns associated fragments with passed clause.<br>
     * The search for tokens is done among all tokens passed to this class constructor and
     * using the array of string (representing the clauses of the command) passed to same
     * constructor.
     * <p>
     * Returned list will be empty if the clause has no arguments and will be null if the
     * clause does not exist.
     *
     * @param sClauseName
     * @return The tokens associated with passed clause or an empty list.
     */
    public final List<Lexeme> getClauseContents( String sClauseName )
    {
        for( Map.Entry<Lexeme, List<Lexeme>> entry : mapClauses.entrySet() )
        {
            if( entry.getKey().isText( sClauseName ) )
                return entry.getValue();
        }

        return null;
    }

    /**
     * Searches for a lexeme inside a clause.
     *
     * @param sClauseName Clause name.
     * @param sWhatToFind Lexeme text to search for (case-insensitive).
     * @return The lexeme or null if not found.
     */
    public final Lexeme findInClause( String sClauseName, String sWhatToFind )
    {
        List<Lexeme> list = getClauseContents( sClauseName );

        if( list != null )
        {
            for( Lexeme lex : list )
            {
                if( lex.isText( sWhatToFind ) )
                    return lex;
            }
        }

        return null;
    }

    /**
     * A clause is empty when it has no lexemes or all are delimiters (';' or '\n')
     *
     * @param sClauseName The clause name.
     * @return true if clause is delimiter
     */
    public boolean isClauseEmpty( String sClauseName )
    {
        List<Lexeme> list = getClauseContents( sClauseName );

        for( Lexeme lex : list )
            if( ! lex.isDelimiter() )
                return false;

        return true;
    }

    public String toCode()
    {
        StringBuilder sb = new StringBuilder( 1024 );

        for( Map.Entry<Lexeme,List<Lexeme>> entry : mapClauses.entrySet() )
            sb.append( entry.getKey().text() )
              .append( ' ' )
              .append( Lexer.toCode( entry.getValue() ) )
              .append( UtilStr.sEoL );

        return UtilStr.removeLast( sb, 1 ).toString();
    }

    @Override
    public int hashCode()
    {
        int hash = 7;
            hash = 67 * hash + Objects.hashCode( this.name );
            hash = 67 * hash + Objects.hashCode( this.mapClauses );
        return hash;
    }

    @Override
    public boolean equals(Object obj)
    {
        if( this == obj )
            return true;

        if( obj == null )
            return false;

        if( getClass() != obj.getClass() )
            return false;

        final ParseBase other = (ParseBase) obj;

        if( ! Objects.equals( this.name, other.name ) )
            return false;

        return Objects.equals( this.mapClauses, other.mapClauses );
    }

    @Override
    public String toString()
    {
        return UtilStr.toString( this );
    }

    //------------------------------------------------------------------------//
    // PROTECTED INTERFACE

    protected final ICandi.IError addError( String message, Lexeme token )
    {
        lstErrors.add( new CodeError( message, token ) );

        return UtilColls.getAt( lstErrors, -1 );
    }

    protected final ParseBase addErrors( List<ICandi.IError> errors )
    {
        lstErrors.addAll( errors );

        return this;
    }

    /**
     * When the error is reported by NAXE, the line 0 is the first line provided to NAXE,
     * this method shifts the lines and column.
     *
     * @param fromLine
     * @param fromCol
     * @param lstIn
     * @return Itself.
     */
    protected final ParseBase addErrors( int fromLine, int fromCol, List<ICandi.IError> lstIn )
    {
        List<ICandi.IError> lstOut = new ArrayList<>();

        for( ICandi.IError error : lstIn )
        {
            lstOut.add( new CodeError( error.message(),
                                       error.line()   + fromLine -1,
                                       error.column() + fromCol  -1 ) );
        }

        addErrors( lstOut );

        return this;
    }

    /**
     * Searches for an ID inside a clause adding proper errors, if any.
     * <p>
     * It checks that:
     * <ul>
     *     <li>It is only one word</li>
     *     <li>The word is a valid identifier (not starts with digit & length <= nMAX_ID_LEN)</li>
     *     <li>The word is not a reserved keyword</li>
     * </ul>
     *
     * @param clause Where to search.
     * @return The ID or null if not found or not valid ID.
     */
    protected final Lexeme findID( String clause )
    {
        List<Lexeme> tokens = getClauseContents( clause );

        if( isClauseEmpty( clause, tokens ) )
        {
            addError( '"'+ clause.toUpperCase() +"\" clause is empty, but it needs a name", getClause( clause ) );
            return null;
        }

        if( isNotOneToken( clause, tokens ) )
            return null;

        return (validateName( tokens.get( 0 ) ) ? tokens.get( 0 ) : null);
    }

    /**
     *
     * Note: meanwhile IDs are unique, names can be repeated in different places.
     * @param token
     * @return
     */
    protected final boolean validateName( Lexeme token )
    {
        int nErrors = lstErrors.size();

        if( token.isCommandWord() )
        {
            addError( '"'+ token.text() +"\" is a reserved word: use a different name", token );
        }
        else
        {
            String sErr = Language.isValidName( token.text() );

            if( sErr != null )
                addError( "Name "+ sErr, token );
        }

        return (lstErrors.size() == nErrors);    // No error was added
    }

    protected final Map<Lexeme,List<Lexeme>> getClauses()
    {
        return Collections.unmodifiableMap( mapClauses );
    }

    /**
     * Checks if two consecutive clause keywords exist in the parsed command.
     * This is used to detect compound clauses like "PRE ONSTART" or "PRE ONSTOP".
     * <p>
     * Since both keywords are clause keywords, when we have "PRE ONSTART", the mapClauses
     * will have both as separate entries. This method checks if "first" is immediately
     * followed by "second" in the clause order.
     *
     * @param first The first keyword (e.g., "PRE")
     * @param second The second keyword (e.g., "ONSTART" or "ONSTOP")
     * @return true if the two keywords appear consecutively as clauses
     */
    protected final boolean hasClauseSequence( String first, String second )
    {
        // Both clauses must exist
        if( getClause( first ) == null || getClause( second ) == null )
            return false;

        // Check if 'first' is immediately followed by 'second' in mapClauses order
        // (mapClauses is a LinkedHashMap, so order is preserved)
        boolean foundFirst = false;

        for( Lexeme clause : mapClauses.keySet() )
        {
            if( foundFirst )
                return clause.isText( second );    // True only if next clause is 'second'

            if( clause.isText( first ) )
                foundFirst = true;
        }

        return false;
    }

    /**
     * Check if clause is empty, if this is the case, an error message is added to the internal list.
     *
     * @param clause
     * @param tokens
     * @return true if clause is null.
     */
    protected final boolean isClauseMissed( String clause, List<Lexeme> tokens )
    {
        if( tokens == null )
        {
            Lexeme lex = name;

            if( lex == null )
            {
                for( Lexeme cla : mapClauses.keySet() )
                {
                    if( (lex == null) || (lex.line() > cla.line()) )
                        lex = cla;
                }
            }

            addError( "Missed the mandatory \""+ clause +"\" clause", lex );
            return true;
        }

        return isClauseEmpty( clause, tokens );
    }

    /**
     * Returns true if clause is empty, but does not check if the clause itself exists or not.
     *
     * @param clause
     * @param tokens
     * @return true if aoToken is null or its size is 0.
     */
    protected final boolean isClauseEmpty( String clause, List<Lexeme> tokens )
    {
        if( UtilColls.isEmpty( tokens ) || UtilColls.areAll( tokens, (item) -> ((Lexeme) item).isDelimiter() ) )
        {
            addError( "Clause \""+ clause +"\" is empty.", getClause( clause ) );
            return true;
        }

        return false;
    }

    protected final boolean isOneToken( String clause, List<Lexeme> tokens )
    {
        return (! isNotOneToken( clause, tokens ));
    }

    /**
     * Returns true if aoToken is null or its size is not 1.
     *
     * @param clause
     * @param tokens List to check.
     * @return
     */
    protected final boolean isNotOneToken( String clause, List<Lexeme> tokens )
    {
        if( tokens == null )
        {
            addError( "Parameter expected but not found after \""+ clause +"\".", getClause( clause ) );
            return true;
        }

        int nTokens = UtilColls.count( tokens, (lex) -> ! ((Lexeme) lex).isDelimiter() );

        if( nTokens == 1 )
            return false;

        List<String> list = new ArrayList<>();

        tokens.forEach( token ->
                        {
                            if( ! token.isDelimiter() )
                                list.add( token.text() );
                        } );

        addError( "Only one paramenter was expected after \""+ clause.toUpperCase() +
                  "\", but found: \""+ Arrays.toString( list.toArray() ) +'"', tokens.get( 1 ) );

        return true;
    }

    /**
     * Validates and returns the language name from the mandatory LANGUAGE clause.
     * <p>
     * The clause must be present, non-empty, and contain exactly one token that
     * names a registered language artifact.
     *
     * @param tokens The contents of the LANGUAGE clause.
     * @return The lower-cased language name, or null if the clause is invalid.
     */
    protected final String getLang( List<Lexeme> tokens, String invokerCmd  )
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

            // To be precise and referring to LIBRARY, 'Java' is not appropritae for LANGUAGE clause
            // because any language that compiles to JavaByteCode can be used.
            // This is not teh case for SCRIPT commnad when source code is embeded, where 'Java' is
            // the proper name.
            // To make things simple and because this is just a sematic question, internally only
            // 'java' is used.
            if( "LIBRARY".equals( invokerCmd ) && "jvm".equals( sLang ) )
                sLang = "java";

            ICandi.ILanguage lang = new LangBuilder().build( sLang );

            if( lang == null )
                addError( "There is no language artifact registered for: "+ id, getClause( "LANGUAGE" ) );

            return sLang;
        }

        return null;
    }

    /**
     * Evaluates a transpile-time config expression and returns a key-value pair.
     * <p>
     * Builds the expression from {@code lstLex}, checks for parse and evaluation
     * errors, and rejects expressions that contain variables (not allowed in
     * CONFIG/INIT clauses).
     *
     * @param xprEval The expression evaluator.
     * @param key     The lower-cased config key.
     * @param lstLex  The lexemes representing the value expression.
     * @return A {@link Pair} with the key and its evaluated {@link Lexeme}, or
     *         null if the expression is invalid.
     */
    protected final Pair<String,Lexeme> evalConfigExpr( IXprEval xprEval, String key, List<Lexeme> lstLex )
    {
        String sExpr = Lexer.toCode( lstLex );

        xprEval.build( sExpr );

        if( ! xprEval.getErrors().isEmpty() )
            addErrors( UnecTools.updateErrorLine( lstLex.get(0).line(), xprEval.getErrors() ) );

        if( getErrors().isEmpty() )
        {
            if( ! xprEval.getVars().isEmpty() )
                addError( '"'+ Lexer.toCode( lstLex ) +"\" expression with variables not allowed here.", lstLex.get(0) );

            try
            {
                Object oResult = xprEval.eval();

                if( oResult != null )
                    return new Pair( key, lstLex.get(0).updateUsign( oResult ) );
            }
            catch( Exception exc )
            {
                addError( "Invalid expression:\n"+ Lexer.toCode( lstLex ) +"\nCause: "+ UtilStr.toStringBrief( exc ), lstLex.get(0) );
            }
        }

        return null;
    }

    /**
     * Adds a key-value pair to a map, emitting a duplicate-key error if the key
     * already exists.
     *
     * @param map         The target map.
     * @param pair        The key-value pair to insert.
     * @param clauseName  The clause name used in the error message (e.g. "CONFIG").
     * @param errorAnchor The {@link Lexeme} to attach the error to.
     */
    protected final void putNoDuplicate( Map<String,Lexeme> map, Pair<String,Lexeme> pair,
                                         String clauseName, Lexeme errorAnchor )
    {
        if( map.containsKey( pair.getKey() ) )
            addError( "Duplicated name in \""+ clauseName +"\" clause: \""+ errorAnchor.text() +'"', errorAnchor );
        else
            map.put( pair.getKey(), pair.getValue() );
    }

    protected String[] expandURIs( List<Lexeme> tokens )
    {
        tokens.removeIf( lex -> lex.isDelimiter() );

        List<String> lstURIs = new ArrayList<>();

        for( Lexeme lex :  tokens )     // 1 to jump over "INCLUDE" itself
        {
            UtilComm.Protocol prot = UtilComm.getFileProtocol( lex.text() );

            if( (prot == null) || (prot == UtilComm.Protocol.file) )
            {
                try
                {
                    for( URI uri : UtilIO.expandPath( lex.text() ) )
                    {
                        if( new File( uri ).exists() )
                            lstURIs.add( uri.toString() );
                        else
                            addError( "Invalid URI, file does not exist: \""+ lex.text() +'"', lex );
                    }
                }
                catch( IOException | URISyntaxException ex )
                {
                    addError( "Invalid URI ("+ ex.getMessage() +"): \""+ lex.text() +'"', lex );
                }
            }
            else
            {
                lstURIs.add( lex.text() );
            }
        }

        return lstURIs.toArray( String[]::new );
    }
}
