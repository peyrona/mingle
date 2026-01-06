
package com.peyrona.mingle.candi.unec.transpiler;

import com.peyrona.mingle.candi.unec.parser.ParseBase;
import com.peyrona.mingle.lang.interfaces.ICandi;
import com.peyrona.mingle.lang.interfaces.IXprEval;
import com.peyrona.mingle.lang.japi.Pair;
import com.peyrona.mingle.lang.japi.UtilColls;
import com.peyrona.mingle.lang.japi.UtilIO;
import com.peyrona.mingle.lang.lexer.CodeError;
import com.peyrona.mingle.lang.lexer.Lexeme;
import java.net.URI;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * This class contains a collection of miscellaneous methods.
 */
public final class UnecTools
{
    @SuppressWarnings("empty-statement")
    public static List<List<Lexeme>> splitByCommand( List<Lexeme> list )
    {
        List<List<Lexeme>> all = new ArrayList<>();

        if( ! list.isEmpty() )
        {
            all.add( new ArrayList<>() );

            for( int n = 0; n < list.size(); n++ )
            {
                if( list.get( n ).isEoL() && (n > 0) && (list.get( n - 1 ).isEoL() ) )    // This char is charEoL and previous char was EoL too
                {
                    while( (n + 1 < list.size()) && list.get( n + 1 ).isDelimiter()  )
                        n++;

                    all.add( new ArrayList<>() );
                }
                else
                {
                    UtilColls.getAt( all, -1 ).add( list.get( n ) );
                }
            }

            while( UtilColls.getAt( all, -1 ).isEmpty() )
                UtilColls.removeTail( all );
        }

        return all;
    }

    /**
     * Traverses 'list' searching for passed clause(s) (no case). This method fills 'mapClauses'
     * creating as many keys as passed clauses, and which values are Lists of Lexeme instances,
     * those that corresponds to the clause.<br>
     * <br>
     * A value of null for a clause means this clause does not appear.
     * A value of empty List for a clause means this clause appears but with no contents.
     *
     * @param list To traverse.
     * @param asClauses To split by.
     * @return What explained.
     */
    public static Map<Lexeme,List<Lexeme>> splitByClause( List<Lexeme> list, String... asClauses )      // This method is here just for congruency (as it is used only bt ParseBase.java it could be there, but I prefer it here)
    {
        Map<Lexeme,List<Lexeme>> mapClauses = new LinkedHashMap<>();   // Using a LinkedHashMap, because the order is important

        Lexeme currentClause = null;

        for( Lexeme lex : list )
        {
            if( UtilColls.contains( asClauses, lex.text() ) )
            {
                currentClause = lex;

                if( ! mapClauses.containsKey( lex ) )            // Checked to allow idiots writting for example 2 WHEN clauses in same RULE
                    mapClauses.put( lex, new ArrayList<>() );

                continue;  // To avoid adding the clause itself
            }

            if( currentClause == null )
            {
                if( ! mapClauses.containsKey( null ) )
                    mapClauses.put( null, new ArrayList<>() );

                mapClauses.get( null ).add( lex );
            }
            else
            {
                mapClauses.get( currentClause ).add( lex );
            }
        }

        return mapClauses;
    }

    /**
     * Returns a List of Lists obtained by splitting passed List using isDelimiter() as criteria.
     * <p>
     * Items that satisfy the criteria are not included in returned lists.
     *
     * @param list Lexemes to split.
     * @return A List of Lists obtained by splitting passed List using isDelimiter() as criteria.
     */
    public static List<List<Lexeme>> splitByDelimiter( List<Lexeme> list )
    {
        List<List<Lexeme>> all = new ArrayList<>();

        if( list.isEmpty() )
            return all;

        all.add( new ArrayList<>() );

        for( Lexeme item : list )
        {
            boolean isSpliter = item.isDelimiter();

            if( isSpliter )  all.add( new ArrayList<>() );
            else             UtilColls.getAt( all, -1 ).add( item );
        }

        all.removeIf( lst -> lst.isEmpty() );    // Needed for instance when using both ';' and '\n' (an empty list is created)

        return all;
    }

    public static List<ICandi.IError> updateErrorLine( int line, List<ICandi.IError> list )   // List is unmodifiable
    {
        List<ICandi.IError> l = new ArrayList<>( list.size() );

        for( int n = 0; n < list.size(); n++ )
        {
            ICandi.IError err = list.get( n );

            l.add( n, new CodeError( err.message(), err.line() + line - 1, err.column() ) );
        }

        return l;
    }

    public static Map<String,String> getMapCmdSyntax()
    {
        Map<String,String> map = new HashMap<>(6);    // Created upon invocation to save RAM

        map.put("device",
                "DEVICE <name>\n" +
                "\t[INIT <property> = {<type> | <expression>} [; ...]]\n" +
                "\tDRIVER <driver>\n" +
                "\t\t[CONFIG name = {<type> | <expression>} [; ...]]");

        map.put("driver",
                "DRIVER <name>\n" +
                "\tSCRIPT <script_name>\n" +
                "\t[CONFIG <name> AS {ANY | <data-type>} [REQUIRED]\n" +
                "\t[; ...]]");

        map.put("include",
                "INCLUDE \"<URI> [*|**]\" [USE <name> [, ...] AS <literal> [, ...]]");

        map.put("rule",
                "[RULE <name>]\n" +
                "WHEN <device-name> | [ANY | ALL] | <group>} <RelationalOp> <expression>\n" +
                "\tTHEN {<script> | <rule> | [{<device> | <group>} =] <expression>} [AFTER <time-unit>] [; ...]\n" +
                "\tIF {<device-name> | [ANY | ALL] <group>} [<RelationalOp> <expression>] [{AFTER | WITHIN} <time-unit>]\n"+
                "\tUSE <name> AS <expression> [;...]");

        map.put("script",
                "SCRIPT [<name>]\n" +
                "\t[ONSTART] [ONSTOP]\n"+
                "\tLANGUAGE { une | java | js | python}\n" +
                "\tFROM { \"<URI>[*|**]\" [; ...] | {<code>} }\n" +
                "\t[CALL \"<entry_point>\"]");

        map.put("use",
                "USE <literal> AS <literal> [; ...]");

        return map;
    }

    public static Pair<Collection<ParseBase>,Collection<ICandi.IError>> transpile( URI uri, Charset cs, IXprEval xprEval )
    {
        if( UtilIO.hasExtension( uri, "une" ) )
        {
            TransUnit tu = new TransUnit( new SourceUnit( uri, cs ), xprEval );
                      tu.doCommands( null );

            return new Pair( tu.getCommands(), tu.getErrors() );
        }

        return null;
    }

    //------------------------------------------------------------------------//

    private UnecTools()
    {
        // Avoids creating instances of this class
    }
}