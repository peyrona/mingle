
package com.peyrona.mingle.candi.unec.parser;

import com.peyrona.mingle.candi.unec.transpiler.UnecTools;
import com.peyrona.mingle.lang.interfaces.IXprEval;
import com.peyrona.mingle.lang.japi.CommandSerializer;
import com.peyrona.mingle.lang.japi.Pair;
import com.peyrona.mingle.lang.japi.UtilStr;
import com.peyrona.mingle.lang.japi.UtilType;
import com.peyrona.mingle.lang.lexer.Language;
import com.peyrona.mingle.lang.lexer.Lexeme;
import com.peyrona.mingle.lang.lexer.Lexer;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * This class represents a transpiled DEVICE command.
 *
 * @author Francisco José Morero Peyrona
 *
 * Official web site at: <a href="https://mingle.peyrona.com">https://mingle.peyrona.com</a>
 */
public final class ParseDevice extends ParseBase
{
    public  final String             drvName;
    private final Map<String,Lexeme> drvInit;
    private final Map<String,Lexeme> dvcInit;
    private final IXprEval           xprEval;

    //------------------------------------------------------------------------//
    // STATIC INTERFACE

    public static boolean is( String source )
    {
        return UtilStr.startsWith( source, "device" );
    }

    //------------------------------------------------------------------------//

    public ParseDevice( List<Lexeme> lstToken, IXprEval xprEval )
    {
        super( lstToken, lstToken.get(0).text(), "DRIVER", "CONFIG", "INIT" );

        this.xprEval = xprEval;

        name = findID( "DEVICE" );

        drvName = getDriverName( getClauseContents( "DRIVER" ) );
        drvInit = getDriverInit( getClauseContents( "CONFIG" ) );
        dvcInit = getDeviceInit( getClauseContents( "INIT"   ) );
     }

    //------------------------------------------------------------------------//
    // PUBLIC INTERFACE

    public Map<String,Lexeme> getDriverInit()
    {
        return Collections.unmodifiableMap( drvInit );
    }

    public Map<String,Lexeme> getDeviceInit()
    {
        return Collections.unmodifiableMap( dvcInit );
    }

    @Override
    public String serialize()
    {
        Map<String,Object> mapDriverInit = new HashMap<>();
        Map<String,Object> mapDeviceInit = new HashMap<>();

        getDriverInit().forEach((k,v) -> mapDriverInit.put(k, delQuotes( UtilType.toUne( v ) ) ) );

        getDeviceInit().forEach((k,v) -> mapDeviceInit.put(k, delQuotes( UtilType.toUne( v ) ) ) );

        return CommandSerializer.Device( name.text().toLowerCase(),
                                         drvName.toLowerCase(),
                                         mapDriverInit,
                                         mapDeviceInit );
    }

    //------------------------------------------------------------------------//
    // PRIVATE INTERFACE

    private Object delQuotes( Object oBasicData )
    {
        if( oBasicData instanceof String )
        {
            String s = oBasicData.toString();

            if( s.charAt( 0 ) == Language.QUOTE && UtilStr.isLastChar( s, Language.QUOTE ) )
                return s.substring( 1, s.length() -1 );
        }

        return oBasicData;
    }

    private String getDriverName( List<Lexeme> tokens )
    {
        if( (tokens == null)                  ||    // It is OK because 'CONFIG' clause is optional
            isClauseEmpty( "DRIVER", tokens ) ||    // But if exists, it can not be empty (the method (defined in super-class) adds the error)
            isNotOneToken( "DRIVER", tokens ) )
        {
            return null;
        }

        Lexeme id = findID( "DRIVER" );

        return (id == null) ? null : id.text();
    }

    private Map<String,Lexeme> getDriverInit( List<Lexeme> tokens )
    {
        return getOptions( true, tokens );
    }

    /**
     * Returns a Map where the key is the Device's property name (ie "color") and the value
     * is the value for the property.
     *
     * @param tokens Where to search.
     * @return A map with the above information.
     */
    private Map<String,Lexeme> getDeviceInit( List<Lexeme> tokens )
    {
        return getOptions( false, tokens );
    }

    private Map<String,Lexeme> getOptions( boolean is4Driver, List<Lexeme> tokens )
    {
        String             sClause = is4Driver ? "CONFIG" : "INIT";
        Map<String,Lexeme> map2Ret = new HashMap<>();

        if( (tokens == null) ||                   // It is OK because 'INIT' and 'CONFIG' clauses are optional
            isClauseEmpty( sClause, tokens ) )    // But if they exist, it can not be empty (the method (defined in super-class) adds the error)
        {
            return map2Ret;
        }

        List<List<Lexeme>> lstAllOpts = UnecTools.splitByDelimiter( tokens );

        for( List<Lexeme> lstOneOpt : lstAllOpts )
        {
            Pair<String,Lexeme> pair = parseLine( sClause, lstOneOpt );

                if( pair != null )
                {
                    if( map2Ret.containsKey( pair.getKey() ) )
                        addError( "Duplicated name in \""+ sClause +"\" clause: \""+ lstOneOpt.get(0).text() +'"', lstOneOpt.get(0) );
                    else
                        map2Ret.put( pair.getKey(), pair.getValue() );
                }
        }

        return map2Ret;
    }

    /**
     * Parses a line like this: name = literal | expression
     * <p>
     * Used by CONFIG and INIT.
     * <p>
     * Note: meanwhile the key must be lowered, the value must preserve its case (it could
     *       be important for the driver and or the controller).
     *
     * @param sClause
     * @param line
     * @return
     */
    private Pair<String,Lexeme> parseLine( String sClause, List<Lexeme> line )
    {
        if( line.size() < 3 )                  // Can not check size != 3 because the right part can be an expression (which has more than 1 lexeme)
        {
            addError( "Unrecognized syntax, in "+ sClause +", expected: \"<name> = <literal | expression> [; ...]\"", line.get(0) );
            return null;
        }

        if( ! validateName( line.get(0) ) )    // validateName(...) adds founded errors
        {
            return null;
        }

        if( ! Language.isAssignOp( line.get(1).text() ) )
        {
            addError( '"'+ String.valueOf( Language.ASSIGN_OP ) +"\" operator expected but found: \""+ line.get(1).text() +'"', line.get(1) );
        }

        String key = line.get(0).text().toLowerCase();   // 1st token is an ID

        // 2nd token is "="

        // 3rd token can be the last one or the first of a list that should be a transpiler-time resolvable expression (e.g.: (-15 * (5 + 2) < 0).
        // The Expressions Evaluator can evaluate both: a single Lexeme literal or a list of lexemes representing an expression.

        List<Lexeme> lstLex  = line.subList( 2, line.size() );
        String       sExpr   = Lexer.toCode( lstLex );

        xprEval.build( sExpr );

        if( ! xprEval.getErrors().isEmpty() )
            addErrors( UnecTools.updateErrorLine( lstLex.get(0).line(), xprEval.getErrors() ) );

        if( getErrors().isEmpty() )
        {
            if( ! xprEval.getVars().isEmpty() )
                addError( '"'+ Lexer.toCode( lstLex ) +"\" expression with variables not allowed here.", line.get(2) );

            try
            {
                Object oResult = xprEval.eval();

                if( oResult != null )
                    return new Pair( key, line.get(2).updateUsign( oResult ) );
            }
            catch( Exception exc )
            {
                addError( "Invalid expression:\n"+ Lexer.toCode( lstLex ) +"\nCause: "+ UtilStr.toStringBrief( exc ), line.get(2) );
            }
        }

        return null;    // It is an invalid expression
    }
}