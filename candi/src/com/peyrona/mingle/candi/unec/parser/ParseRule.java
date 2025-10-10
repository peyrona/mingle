
package com.peyrona.mingle.candi.unec.parser;

import com.peyrona.mingle.candi.IntermediateCodeWriter;
import com.peyrona.mingle.candi.unec.parser.ParseRuleThen.Action;
import com.peyrona.mingle.lang.interfaces.IXprEval;
import com.peyrona.mingle.lang.japi.CommandSerializer;
import com.peyrona.mingle.lang.japi.UtilColls;
import com.peyrona.mingle.lang.japi.UtilStr;
import com.peyrona.mingle.lang.lexer.Language;
import com.peyrona.mingle.lang.lexer.Lexeme;
import com.peyrona.mingle.lang.lexer.Lexer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * This class represents a transpiled RULE command.
 *
 * @author Francisco José Morero Peyrona
 *
 * Official web site at: <a href="https://github.com/peyrona/mingle">https://github.com/peyrona/mingle</a>
 */
public final class ParseRule extends ParseBase
{
    private final static AtomicInteger count = new AtomicInteger( 1 );

    private        final List<Lexeme>  when;     // WHEN and IF clauses can not be validated by this class bacuse they can include futures
    private        final List<Lexeme>  _if_;     // (AFTER and WITHIN) and groups (ALL and ANY): they are validadted by Checker.java.
    private        final ParseRuleThen then;
    private        final IXprEval      xprEval;
    private static final String sKEY = "RULE";

    //------------------------------------------------------------------------//
    // STATIC INTERFACE

    public static boolean is( String source )
    {
        return UtilStr.startsWith( source, sKEY ) ||
               UtilStr.startsWith( source, "WHEN" );
    }

    //------------------------------------------------------------------------//
    // CONSTRUCTOR

    public ParseRule( List<Lexeme> lstLexemes, IXprEval xprEval )
    {
        super( lstLexemes, "RULE", "WHEN", "THEN", "IF", "USE" );

        this.xprEval = xprEval;

        if( getClauseContents( sKEY ) != null )     // The clause 'RULE' can be ommited
            name = findID( sKEY );                  // By default name == null in superclass

        when = getWhen( getClauseContents( "WHEN" ) );
        _if_ = getIf(   getClauseContents( "IF"   ) );

        List<Lexeme> lstThen = getThen( getClauseContents( "THEN" ) );

        if( lstThen == null )
        {
            then = null;
        }
        else
        {
            then = new ParseRuleThen( lstThen, xprEval );
            addErrors( then.getErrors() );
        }

        String sBefore = toCode();   // Has to be asigned before ::applyUse(...) is invoked

        applyUse( getUse( getClauseContents( "USE" ) ) );

        if( IntermediateCodeWriter.isRequired() )
        {
            String sAfter = ((name == null) ? "" : "RULE "+ name.text() +'\n') +
                            "WHEN "+ Lexer.toCode( when ) +'\n'                +
                            "THEN "+ (then == null ? "" : then.toCode())       +    // 'then' is null when WHEN clause is empty
                            (UtilColls.isEmpty( _if_ ) ? "" : "\nIF "+ Lexer.toCode( _if_ ));

            try( IntermediateCodeWriter writer = IntermediateCodeWriter.get() )
            {
                writer.startSection( "RULE" )
                      .writeln( "Original = \n"+ sBefore )
                      .writeln()
                      .writeln( "Transformed = \n"+ sAfter )
                      .endSection();
            }
        }
    }

    //------------------------------------------------------------------------//
    // PUBLIC INTERFACE

    public List<Lexeme> getWhen()
    {
        return when;
    }

    public List<Lexeme> getIf()
    {
        return _if_;
    }

    public ParseRuleThen getThen()
    {
        return then;
    }

    @Override
    public String serialize()
    {
        List<String> lstThen = new ArrayList<>();

        for( Action action : then.getActions() )
        {
            lstThen.add(CommandSerializer.RuleAction( action.getDelay(),
                                                       action.getTargetName(),
                                                       trim( action.getValueToSet() ) ) );
        }

        return CommandSerializer.Rule( (getName() == null) ? null : getName().text().toLowerCase(),
                                       Lexer.toCode( when ),
                                       ((_if_ == null) ? null : Lexer.toCode( _if_ )),
                                       lstThen.toArray( String[]::new ) );
    }

    //------------------------------------------------------------------------//
    // PRIVATE INTERFACE

    /**
     * Returns the source code that corresponds with the expression Lexerized in passed list of Fragments.
     *
     * @param lexemes
     * @return
     */
    private List<Lexeme> getWhen( List<Lexeme> lexemes )
    {
        if( isClauseMissed( "WHEN", lexemes ) ||         // 'WHEN' is mandatory
            isClauseEmpty(  "WHEN", lexemes ) )          // and can not be empty
        {
            return null;
        }

        lexemes.removeIf( lex -> lex.isDelimiter() );    // Removes '\n' and ';'

        return lexemes;
    }

    /**
     * Returns the source code that corresponds with the expression Lexerized in passed list of Fragments.
     *
     * @param lexemes
     * @return
     */
    private List<Lexeme> getIf( List<Lexeme> lexemes )
    {
        if( UtilColls.isEmpty( lexemes ) )               // 'IF' is not mandatory, but when declared, it can not be empty
            return null;                                 // 'IF' should be an expression

        if( isClauseEmpty( "IF", lexemes ) )
            return null;

        lexemes.removeIf( lex -> lex.isDelimiter() );    // Removes '\n' and ';'

        return lexemes;
    }

    private List<Lexeme> getThen( List<Lexeme> lexemes )
    {
        if( isClauseMissed( "THEN", lexemes ) ||
            isClauseEmpty(  "THEN", lexemes ) )          // 'THEN' is mandatory and can not be empty
        {
            return null;
        }

        return lexemes;
    }

    /**
     * Processes USE ... AS ... clause.
     *
     * @param lstLex
     * @return A Map which Key is the Lexeme of the 1st argument of the (USE ... AS ...) and the
     *         Value of the map is a List with all Lexemes that  compose the 2nd argument.
     */
    private Map<Lexeme,List<Lexeme>> getUse( List<Lexeme> lstLex )
    {
        if( UtilColls.isEmpty( lstLex ) )                   // 'USE' is not mandatory, but when declared, it can not be empty
            return null;

        if( isClauseEmpty( "USE", lstLex ) )
            return null;

        List<List<Lexeme>> lstLines = new ArrayList<>();    // One item per line: <name> AS <xpr>
        List<Lexeme>       lstLine  = new ArrayList<>();    // Every item is an USE definition: <name> AS <xpr>

        for( Lexeme lex : lstLex )
        {
            if( lex.isDelimiter() )
            {
                lstLines.add( lstLine );
                lstLine = new ArrayList<>();
            }
            else
            {
                lstLine.add( lex );
            }
        }

        if( ! lstLine.isEmpty() )
            lstLines.add( lstLine );    // Adds last USE line

        Map<Lexeme,List<Lexeme>> map = new HashMap<>();

        for( List<Lexeme> line : lstLines )
        {
            if( line.size() >= 3 )
            {
                if( line.get(0).isName() )
                {
                    if( line.get(1).isCommandWord() && line.get(1).isText( "AS" ) )
                    {
                        map.put( line.get(0), line.subList( 2, line.size() ) );
                    }
                    else
                    {
                        addError( "Invalid syntax: 'AS' expected, but found '"+ line.get(1) +'\'', line.get(1) );
                    }
                }
                else
                {
                    addError( "Invalid syntax: name expected, but found '"+ line.get(0) +'\'', line.get(0) );
                }
            }
            else
            {
                addError( "'USE' clause invalid syntax. Expected: 'USE <name> AS <expression>'", getClause( "USE" ) );
            }
        }

        return map;
    }

    /**
     * Apply definitions in USE clause to ::when, ::then and ::_if_
     */
    private void applyUse( Map<Lexeme,List<Lexeme>> map )   // Map --> USE <name> AS <expression>
    {
        if( UtilColls.isEmpty( map ) )
            return;

        if( name == null )
            name = Lexeme.build( "_RULE_"+ count.getAndIncrement() +'_' );

        List<String> lstUnUsed = new ArrayList<>();

        for( Map.Entry<Lexeme,List<Lexeme>> entry : map.entrySet() )
        {
            String       sUse  = entry.getKey().text().toLowerCase();
            List<Lexeme> lstAs = entry.getValue();
            boolean      bUsed = false;

            if( Language.isCmdWord( sUse ) )
            {
                addError( '\''+ sUse +"' is an Une reserved word", entry.getKey() );
                continue;
            }

            if( (lstAs.size() == 1) && (! lstAs.get(0).isString()) && Language.isCmdWord( lstAs.get(0).text() ) )
            {
                addError( '\''+ lstAs.get(0).text() +"' is an Une reserved word", lstAs.get(0) );
                continue;
            }

            if( lstAs.size() > 1 )     // So, it has to be an expression
            {
                xprEval.build( Lexer.toCode( lstAs ) );

                if( ! xprEval.getErrors().isEmpty() )
                {
                    addErrors( lstAs.get(0).line(), lstAs.get(0).column(), xprEval.getErrors() );
                    continue;
                }
            }

            String sFullName = name.text() +'-'+ sUse;   // RULE name + USE name

            bUsed = doUseAs( when, sUse ) || bUsed;    // To avoid lazy eval
            bUsed = doUseAs( _if_, sUse ) || bUsed;    // To avoid lazy eval
            bUsed = (then != null && then.applyUse( sUse, " get(\""+ sFullName +"\") ")) || bUsed;   // 'then' is null when WHEN clause is empty

            if( ! bUsed )
            {
                lstUnUsed.add( sUse );
            }
            else
            {
                List<Lexeme> lst = new ArrayList<>();    // What to insert at begining of WHEN clause "put(<USE>,<AS>)&&"

                lst.add( 0, Lexeme.build( "put" ) );
                lst.add( 1, Lexeme.build( '('   ) );
                lst.add( 2, Lexeme.build( ""    ).updateAsStr( sFullName ) );
                lst.add( 3, Lexeme.build( ','   ) );
                lst.add( 4, Lexeme.build( ')'   ) );
                lst.add( 5, Lexeme.build( "&&"  ) );
                lst.addAll( 4, lstAs );

                when.addAll( 0, lst );
            }
        }

        if( ! lstUnUsed.isEmpty() )
        {
            String part = lstUnUsed.size() > 1 ? "are" : "is";

            addError( "Following "+ part +" not used: "+ UtilColls.toString( lstUnUsed ), getClause( "USE" ) );
        }
    }

    private Object trim( Object obj )
    {
        if( obj instanceof String )
            return UtilStr.removeDoubleSpaces( (String) obj );

        return obj;
    }

    private boolean doUseAs( List<Lexeme> clause, String sUse )
    {
        if( UtilColls.isEmpty( clause ) )
            return false;

        boolean bUsed = false;

        for( int n = 0; n < clause.size(); n++ )
        {
            Lexeme lex = clause.get( n );

            if( lex.isText( sUse ) )
            {
                lex.updateAsStr( name.text() +'-'+ lex.text() );   // RULE name + USE name

                clause.add( n  , Lexeme.build( '(' ) );
                clause.add( n  , Lexeme.build( "get" ) );
                clause.add( n+3, Lexeme.build( ')' ) );

                n += 3;
                bUsed = true;
            }
        }

        return bUsed;
    }
}