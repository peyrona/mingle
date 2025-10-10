
package com.peyrona.mingle.candi.unec.parser;

import com.peyrona.mingle.candi.unec.transpiler.UnecTools;
import com.peyrona.mingle.lang.japi.UtilColls;
import com.peyrona.mingle.lang.japi.UtilStr;
import com.peyrona.mingle.lang.lexer.Language;
import com.peyrona.mingle.lang.lexer.Lexeme;
import com.peyrona.mingle.lang.lexer.Lexer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * This class represents a transpiled USE command.
 * <p>
 * Note: toLowerCase() and toUpperCase() use default Locale.
 * Nevertheless, it can be specified at CLI when invoking the JVM. e.g.:<br>
 *     <code>java -Duser.country=CA -Duser.language=fr</code>
 * <p>
 * More here: https://www.oracle.com/technical-resources/articles/javase/locale.html#using
 *
 * @author Francisco José Morero Peyrona
 *
 * Official web site at: <a href="https://github.com/peyrona/mingle">https://github.com/peyrona/mingle</a>
 */
public final class ParseUse extends ParseBase
{
    private static final Map<String,String> mapAll = new HashMap<>();
    private        final List<UseAs>        lstRep = new ArrayList<>();
    private static final String sKEY = "USE";

    //------------------------------------------------------------------------//

    /**
     * Returns true if passed source Une code is a 'USE' command.<br>
     * The whole syntax is not checked, therefore the command could have errors.
     *
     * @param source Une source code.
     * @return
     */
    public static boolean is( String source )
    {
        return UtilStr.startsWith( source, sKEY );
    }

    public static void clean()
    {
        mapAll.clear();
    }

    //------------------------------------------------------------------------//
    // CONSTRUCTOR

    public ParseUse( List<Lexeme> lexemes )
    {
        super( lexemes, "use" );

        if( lexemes.size() < 4 )
        {
            addError( "Invalid syntax, expected: "+ UnecTools.getMapCmdSyntax().get( sKEY.toLowerCase() ), getClause( sKEY ) );
            return;
        }

        List<List<Lexeme>> splitted = UnecTools.splitByDelimiter( lexemes.subList( 1, lexemes.size() ) );     // Not includes 1st token: 'USE' token

        for( List<Lexeme> lstRow : splitted )
        {
            UseAs row = split( lstRow );

            if( row == null )
            {
                addError( "Invalid syntax, expected: "+ UnecTools.getMapCmdSyntax().get( sKEY.toLowerCase() ), getClause( sKEY ) );
                continue;
            }

            if( row.before.isEmpty() )
            {
                addError( "Left literal is empty", lstRow.get(0) );
                continue;
            }

            if( mapAll.containsKey( row.hash ) )
            {
                addError( "Duplicated: already defined as '"+ mapAll.get( row.hash ) +'\'', lstRow.get(0) );
                continue;
            }

            if( Language.isCmdWord( row.hash ) &&
                (row.hash.equals( "INCLUDE" ) || row.hash.equals( "USE" )) )    // These two can not be replaced because they are used to find out what has to be replaced
            {
                addError( '\''+ row.hash +"' can not be replaced", lstRow.get(0) );
                continue;
            }

            mapAll.put( row.hash, Lexer.toCode( row.after ) );
            lstRep.add( row );
        }
    }

    //------------------------------------------------------------------------//
    // PUBLIC SCOPE

    /**
     * Executes all replaces contained in this command to passed Fragments updating the token type if needed.
     * <p>
     * e.g.: when source code were passed to the Lexer, it detected the lexeme
     * "SET" as a type NAME, after changed to "=", its new type is OPERATOR.
     *
     * @param list Where to execute the replacements.
     * @return Itself.
     */
    public ParseUse doUses( List<Lexeme> list )
    {
        assert getErrors().isEmpty();

        for( UseAs row : lstRep )
        {
            if( ParseScript.is( list.get(0).text() ) )
            {
                ParseScript ps = new ParseScript( list );

                if( ps.isInline && "UNE".equalsIgnoreCase( ps.language ) )
                {
                    for( int n = 0; n < list.size(); n++ )
                    {
                        if( list.get(n).isText( "FROM" ) )
                        {
                            List<Lexeme> lst = new Lexer( list.get( n+1 ).text() ).getLexemes();
                            row.replaceIn( lst );
                            list.get( n+1 ).updateUsign( Lexer.toCode( lst ) );
                            break;
                        }
                    }
                }
            }
            else
            {
                row.replaceIn( list );
            }
        }

        return this;
    }

    @Override
    public String serialize()
    {
        return null;
    }

    //------------------------------------------------------------------------//
    // PROTECTED SCOPE

    //------------------------------------------------------------------------//
    // PRIVATE SCOPE

    /**
     * Invoked for every row of the USE command
     *
     * @param list It is known that list.size() >= 3
     * @return A Pair instance where the key is the left side of AS and the value the right side of AS.
     */
    private UseAs split( List<Lexeme> list )
    {
        List<Lexeme> before = new ArrayList<>();    // Everything before AS
        List<Lexeme> after  = new ArrayList<>();    // Everything after  AS
        List<Lexeme> what   = before;

        for( Lexeme lex : list )
        {
            boolean assign = true;

            if( lex.isCommandWord() && lex.isText( "AS" ) )
            {
                assign = (what != before);     // Only does not assigns the 1st time tha "AS" is found (dmore AS could appear after the 1st AS: USE COMO AS AS)
                what = after;
            }

            if( assign )
                what.add( lex );
        }

        if( before.isEmpty() || after.isEmpty() )
            return null;

        return new UseAs( before, after );
    }

    //------------------------------------------------------------------------//
    // INNER CLASS
    //------------------------------------------------------------------------//
    private static final class UseAs
    {
        final List<Lexeme> before;    // Left  side of AS
        final List<Lexeme> after;     // Right side of AS
        final String       hash;      // An unique key

        UseAs( List<Lexeme> before, List<Lexeme> after )
        {
            boolean isQuoted = (before.size() > 1)     &&
                               before.get(0).isQuote() &&
                               UtilColls.getAt( before, -1 ).isQuote();

            this.before = Collections.unmodifiableList( (isQuoted ? before.subList( 1, before.size()-1 ) : before) );
            this.after  = Collections.unmodifiableList( after );
            this.hash   = Lexer.toCode( before ).toUpperCase();    // Better (it will be faster most part of the times) toUpper than toLower
        }

        void replaceIn( List<Lexeme> list )
        {
            int ndx;

            while( (ndx = findIn( list )) > -1 )
            {
                if( list.get( ndx ).isString() )
                {
                    Lexeme lex = list.get( ndx );

                    String search = Language.buildMacro( Lexer.toCode( before ) );
                           search = "(?i)"+  Pattern.quote( search );             // (?i) -> ignore-case. The replace is inside a string: "Say {*_CONST_*}"

                    String replac = Lexer.toCode( after );

                    lex.updateUsign( lex.text().replaceAll( search, replac ) );   // Not needed to do toUne(...) beacuse lexeme is an String
                }
                else
                {
                    for( int n = 0; n < before.size(); n++ )
                        list.remove( ndx );

                    list.addAll( ndx, clone( after ) );   // It is needed to create a new clone for every replace
                }
            }
        }

        private int findIn( List<Lexeme> list )     // 'list' is normally a Command or an Expression
        {
            if( list.size() < before.size() )
                return -1;

            Lexeme _1st = before.get( 0 );
            int n;

            for( n = 0; n < list.size(); n++ )
            {
                Lexeme lex = list.get( n );

                if( lex.equivalent( _1st ) )
                    break;

                if( lex.isString() && UtilStr.contains( lex.text(), Language.buildMacro( _1st.text() ) ) )
                    break;
            }

            if( n == list.size() )
                return -1;

            if( before.size() == 1 )
                return n;

            // When 'before' has more than one token: it is needed to check that the whole sequence of 'before' is inside 'list'

            int m = n++;
            int o;

            for( o = 1; n < list.size() && o < before.size(); n++, o++ )
            {
                if( ! list.get( n ).equivalent( before.get( o ) ) )
                    return -1;
            }

            if( o == before.size() && n < list.size() )
                return m;

            return -1;
        }

        private List<Lexeme> clone( List<Lexeme> list )
        {
            List<Lexeme> l = new ArrayList<>( list. size() );

            for( Lexeme lex : list )
                l.add( lex.clonar() );

            return l;
        }
    }
}