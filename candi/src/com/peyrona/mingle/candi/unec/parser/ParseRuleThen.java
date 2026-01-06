
package com.peyrona.mingle.candi.unec.parser;

import com.peyrona.mingle.candi.unec.transpiler.UnecTools;
import com.peyrona.mingle.lang.MingleException;
import com.peyrona.mingle.lang.interfaces.IXprEval;
import com.peyrona.mingle.lang.japi.UtilColls;
import com.peyrona.mingle.lang.japi.UtilStr;
import com.peyrona.mingle.lang.japi.UtilType;
import com.peyrona.mingle.lang.lexer.Language;
import com.peyrona.mingle.lang.lexer.Lexeme;
import com.peyrona.mingle.lang.lexer.Lexer;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 *
 * @author Francisco José Morero Peyrona
 *
 * Official web site at: <a href="https://github.com/peyrona/mingle">https://github.com/peyrona/mingle</a>
 */
public class ParseRuleThen extends ParseBase
{
    private final List<Lexeme> lexemes;
    private final List<Action> lstActions = new ArrayList<>();

    //------------------------------------------------------------------------//
    // CONSTRUCTOR

    public ParseRuleThen( List<Lexeme> lstLexemes, IXprEval xprEval )
    {
        super();

        this.lexemes = lstLexemes;

        if( UtilColls.isNotEmpty( lstLexemes ) )
        {
            List<List<Lexeme>> lstActionAll = UnecTools.splitByDelimiter( lstLexemes );

            for( List<Lexeme> lstAction : lstActionAll )
            {
                lstAction.removeIf( lex -> lex.isDelimiter() );

                Action action = getAction( lstAction, xprEval );

                if( action != null )
                    lstActions.add( action );
            }
        }

        if( lstActions.isEmpty() )
            addError( "\"THEN\" clause is empty: the rule is useless", lstLexemes.get( 0 ) );
    }

    //------------------------------------------------------------------------//
    // PUBLIC SCOPE

    @Override
    public String serialize()
    {
        throw new MingleException( "Do not use me!" );
    }

    @Override
    public String toCode()     // It is needed to be overwritten
    {
        StringBuilder sb = new StringBuilder( 1024 );

        for( Action act : getActions() )
            sb.append( act.targetName )
              .append( " = " )
              .append( (act.getValueToSet() == null ? "" : act.getValueToSet()) )
              .append( (act.getDelay() > 0 ? "AFTER "+ act.getDelay() : "") )
              .append( UtilStr.sEoL );

        return UtilStr.removeLast( sb, 1 ).toString();
    }

    public Action[] getActions()
    {
        return lstActions.toArray( Action[]::new );
    }

    public Lexeme getLexemeAt( int ndx )
    {
        return lexemes.get( ndx );
    }

    //------------------------------------------------------------------------//
    // PACKAGE SCOPE

    boolean applyUse( String sUseName, String sReplacement )
    {
        if( ! getErrors().isEmpty() )
            return true;

        boolean bUsed = false;

        for( int n = 0; n < lstActions.size(); n++ )
        {
            Action act = lstActions.get(n);
            String xpr = replaceAll( act.getTargetName(), sUseName, sReplacement );

            if( xpr != null )
            {
                bUsed = true;

                lstActions.set( n, new Action( act.getType(), act.getDelay(), xpr, act.getValueToSet() ) );
            }
            else if( act.isOf( Action.Type.AssignDevice, Action.Type.Expression, Action.Type.AssignExpression ) )   // Es raro q se necesite Type.AssignDevice, pero es que en este momento el transpiler cree que que el xxx (USE xxx) es el nombre de un DEVICE
            {
                xpr = replaceAll( act.getValueToSet(), sUseName, sReplacement );

                if( xpr != null )
                {
                    bUsed = true;

                    Action.Type type = (act.isOf( Action.Type.AssignDevice ) ? Action.Type.AssignExpression : act.getType());   // Hay que cambiarlo por lo que explico un poco más arriba

                    lstActions.set( n, new Action( type, act.getDelay(), act.getTargetName(), xpr ) );
                }
            }
        }

        return bUsed;
    }

    //------------------------------------------------------------------------//
    // PRIVATE SCOPE

    private Action getAction( List<Lexeme> lexemes, IXprEval xprEval )     // Syntax:  {<script> | <rule> | [{<device> | <group>} =] <expression>}
    {
        if( lexemes.isEmpty() )
        {
            addError( "\"THEN\" clause: it is empty.", lexemes.get( 0 ) );
            return null;
        }

        List<Lexeme> lstLexBak = new ArrayList<>( lexemes );  // To be used only in case of error
        Lexeme       lexTarget = lexemes.get(0);
        String       sTarget   = lexTarget.text();
        int          nAfter    = getAfter( lexemes );         // The lexemes 'AFTER <n>' if existed, were removed by getAfter(...)

        if( lexemes.isEmpty() )
        {
            addError( "\"THEN\" clause: has only \"AFTER\" modifier.", lstLexBak.get( 0 ) );
            return null;
        }

        // If there is only one lexeme, it must be a name: either an script's name or a rule's name,
        // can not be an expression because expressions need at least 2 lexemes: "! true"

        if( lexemes.size() == 1 )
        {
            if( ! lexemes.get(0).isName() )
            {
                addError( "\"THEN\" clause needs either an Script name, a Rule name, a valid expression or a Device name (or group of Devices): none of them were found.\n"+
                          "Syntax: {<script> | <rule> | [{<device> | <group>} =] <expression>} [AFTER <time-unit>]", lexemes.get(0) );
                return null;
            }

            if( ! validateName( lexTarget ) )
            {
                addError( "Invalid name \""+ lexTarget + "\" in THEN clause", lexTarget );
                return null;
            }

            return new Action( Action.Type.RuleOrScript, nAfter, sTarget, null );
        }

        // Syntax is: THEN <name> = <const> | <expr>   (<const> hast to be a basic data type)
        //        or: THEN <expression>

        int index = UtilColls.findIndex( lexemes, lex -> Language.isAssignOp( lex.text() ) );

        if( index == -1 )   // Has to be an expression. V.G.: 'put( "my_key", list(1,2,3) )' or something similar
        {
            String   sExpr = Lexer.toCode( lexemes );
            IXprEval xpr   = xprEval.build( sExpr );

            if( xpr.getErrors().isEmpty() )
            {
                if( isBasicDataType( xpr ) )
                {
                    addError( "Expression expected but found basic data", lexTarget );
                    return null;
                }

                return new Action( Action.Type.Expression, nAfter, xpr.toString(), null );
            }
            else
            {
                addErrors( UnecTools.updateErrorLine( lexemes.get(0).line(), xpr.getErrors() ) );
                return null;
            }
        }

        // Has to be an assigment like this: THEN <name> = <const> | <expr>

        if( ! lexemes.get(0).isName() )    // 1st token must be a device's name or group of devices
        {
            addError( "Device name (or group of Devices) expected, found \""+ sTarget +'"', lexemes.get(0) );
            return null;
        }

        if( ! validateName( lexTarget ) )
            addError( "Invalid name \""+ lexTarget + "\" in THEN clause", lexTarget );

        if( lexemes.size() < 3 )           // If it is not 1, must be 3 (THEN MyDevice = OPEN) or more than 3 (THEN MyDevice = MyOtherDevice * 5)
        {
            addError( "Syntax error: invalid \"THEN\" clause.", lexTarget );
            return null;
        }

        // Now we know that the first lexeme  is a valid name then, '=' (Language.ASSIGN_OP) must be the second lexeme

        if( ! Language.isAssignOp( lexemes.get(1).text() ) )
        {
            addError( "Invalid syntax for \"THEN\" clause: when \""+ sTarget +"\" is a RULE or SCRIPT name, it must be the only content."+
                      "When it is a DEVICE, the \""+ Language.ASSIGN_OP +"\" operator followed by a valid expression must exist.", lexemes.get(1) );
            return null;
        }

        // The constant or expression is at right of '='
        // At this point we know that lexemes.size() > 2 (x = ...)

        if( lexemes.size() == 3 )     // THEN <name> = <const> | <device_name>
        {
            Lexeme val2set = lexemes.get(2);

            if( val2set.isBasicDataType() )    // Must be before next if
                return new Action( Action.Type.AssignBasicData, nAfter, sTarget, UtilType.toUne( val2set ) );

            if( validateName( val2set ) )      // Must be after previous if
                return new Action( Action.Type.AssignDevice, nAfter, sTarget, val2set.text() );

            return null;
        }

        // All lexemes after '=' (index == 2) must conform a valid Une expression

        String   sExpr = Lexer.toCode( lexemes.subList( 2, lexemes.size() ) );
        IXprEval xpr   = xprEval.build( sExpr );

        addErrors( UnecTools.updateErrorLine( lexemes.get(0).line(), xpr.getErrors() ) );

        // Expressions like this: "left( "Francisco", 4 )", can be resolved now by evaluating the expression because it has no variables.
        // But the problem is that expressions like this: ""Result = "+ floor( rand( 0,99 ) )" can not be resolved now because the function
        // "rand" generates a new value each time.
        // As it is very difficult in this portion of code to know which expressions are resolvable and which are not, better not to do it.
        // Expressions Evaluators should do their best to optimize expressions.

        if( getErrors().isEmpty() )
            return new Action( Action.Type.AssignExpression, nAfter, sTarget, sExpr );

        return null;
    }

    /**
     * Returns the value of "AFTER" clause or -1 if the clause does not exists.
     * It also removes the clause and its value from passed List.
     * <p>
     * Following both are valid:
     * <code>
     *  THEN AFTER 120 light:ON
     *  THEN light:ON AFTER 120
     * </code>
     * @param lexemes Where to search.
     * @return The value of "AFTER" clause or -1 if the clause does not exists.
     */
    private int getAfter( List<Lexeme> lexemes )
    {
        int index = 0;

        for( ; index < lexemes.size(); index++ )
        {
            if( lexemes.get( index ).isCommandWord() &&
                "AFTER".equalsIgnoreCase( lexemes.get( index ).text() ) )
            {
                break;
            }
        }

        if( index == lexemes.size() )                 // AFTER not found: it is ok because the clause is optional
        {
            return -1;
        }

        // If AFTER exists, it needs a numerical parameter

        if( index == (lexemes.size() - 1) )           // It is the last lexeme, therefore the parameter is missed
        {
            addError( "\"AFTER\" clause needs a numeric parameter.", lexemes.get( index ) );
            UtilColls.removeTail( lexemes );
            return -1;
        }

        Lexeme param = lexemes.get( index + 1 );      // There is a parameter after "AFTER"

        if( ! lexemes.get( index + 1 ).isNumber() )   // but it is not an number
        {
            addError( "\"AFTER\" clause parameter must be a number, but found: "+ param.text(), lexemes.get( index ) );
            // Can't remove it because it could be a token needed for the assignment.
            // e.g. this is valid: THEN AFTER 120 light = ON
            // but this is not   : THEN AFTER light = ON
            return -1;
        }

        lexemes.remove( index );    // Removes the "AFTER"
        lexemes.remove( index );    // Removes what is after "AFTER"

        return UtilType.toInteger( param.text() );
    }

    private boolean isBasicDataType( IXprEval xpr )
    {
        List<Lexeme> list = new Lexer( xpr.toString() ).getLexemes();

        return list.size() == 1 &&
               list.get(0).isBasicDataType();
    }

    private String replaceAll( Object sWhere2Search, Object sWhat2Search, Object sWhat2Replace )
    {
        if( sWhere2Search == null || sWhat2Search == null || sWhat2Search.toString().isBlank() )     // sWhat2Replace is never null
            return null;

        List<Lexeme> lstWhere   = new Lexer( sWhere2Search.toString() ).getLexemes();
        List<Lexeme> lstReplace = new Lexer( sWhat2Replace.toString() ).getLexemes();

        if( lstWhere.isEmpty() )
            return null;

        if( lstReplace.isEmpty() )
            throw new MingleException( "Internal error" );

        boolean bFound = false;

        for( int n = 0; n < lstWhere.size(); n++ )
        {
            Lexeme lex = lstWhere.get( n );

            if( ! lex.isString() &&     // It does not count if what is being searched is inside a string
                UtilStr.contains( lex.text(), sWhat2Search.toString() ) )
            {
                bFound = true;

                lstWhere.remove( n );
                lstWhere.addAll( n, lstReplace );
            }
        }

        return (bFound ? Lexer.toCode( lstWhere ) : null);
    }

    //------------------------------------------------------------------------//
    // INNER CLASSES
    //------------------------------------------------------------------------//
    public static final class Action
    {
        public enum Type{ RuleOrScript,       // THEN myScript
                          Expression,         // THEN put("var","value")
                          AssignDevice,       // THEN light2 = light1
                          AssignBasicData,    // THEN light = true
                          AssignExpression }  // THEN light = time():hour() >= 22

        private final Type   type;
        private final int    delay;
        private       String targetName;
        private       Object valueToSet;

        private Action( Type type, int delay, String targetName, Object valueToSet )
        {
            this.type       = type;
            this.delay      = delay;
            this.targetName = ((targetName == null) ? null : targetName.toLowerCase());
            this.valueToSet = ((valueToSet != null && (valueToSet == Type.RuleOrScript || valueToSet == Type.AssignDevice)) ? valueToSet.toString().toLowerCase() : valueToSet);
        }

        public Type getType()
        {
            return type;
        }

        public boolean isOf( Type... tt )
        {
            for( Type t : tt )
                if( type == t )
                    return true;

            return false;
        }

        public boolean isNotOf( Type t )
        {
            return type != t;
        }

        public int getDelay()
        {
            return delay;
        }

        public String getTargetName()
        {
            return targetName;
        }

        public void setTargetName( String str )
        {
            this.targetName = str;
        }

        public Object getValueToSet()
        {
            return valueToSet;
        }

        public void setValueToSet( Object val )
        {
            this.valueToSet = val;
        }

        @Override
        public String toString()
        {
            return UtilStr.toString( this );
        }

        @Override
        public int hashCode()
        {
            int hash = 7;
                hash = 59 * hash + this.delay;
                hash = 59 * hash + Objects.hashCode( this.targetName );
                hash = 59 * hash + Objects.hashCode( this.valueToSet );
            return hash;
        }

        @Override
        public boolean equals( Object obj )
        {
            if( this == obj )
                return true;

            if( obj == null )
                return false;

            if( getClass() != obj.getClass() )
                return false;

            final Action other = (Action) obj;

            if( this.delay != other.delay )
                return false;

            if( ! Objects.equals( this.targetName, other.targetName ) )
                return false;

            return Objects.equals( this.valueToSet, other.valueToSet );
        }
    }
}
