
package com.peyrona.mingle.lang.xpreval;

import com.peyrona.mingle.lang.interfaces.ICandi;
import com.peyrona.mingle.lang.japi.UtilColls;
import com.peyrona.mingle.lang.lexer.CodeError;
import com.peyrona.mingle.lang.xpreval.functions.StdXprFns;
import com.peyrona.mingle.lang.xpreval.operators.Operator;
import com.peyrona.mingle.lang.xpreval.operators.StdXprOps;
import java.util.ArrayList;
import java.util.List;
import java.util.Stack;

/**
 * A class to contain several methods related with Reverse Polish Notation.<br>
 * <br>
 * The expression: time(2020, 4*2, ceil(15.5)):hour() > 0<br>
 * <br>
 * Is converted into following RPN: "(" "2020" "4" "2" "*" "(" "15.5" "ceil" "time" "(" "hour" ":" "0" ">"
 *
 * @author Francisco José Morero Peyrona
 *
 * Official web site at: <a href="https://mingle.peyrona.com">https://mingle.peyrona.com</a>
 */
final class RPN
{
    private final List<XprToken>      lstRPN    = new ArrayList<>();
    private final List<ICandi.IError> lstErrors = new ArrayList<>();  // Used to add items only, never removed

    //------------------------------------------------------------------------//

    RPN( List<XprToken> lstInfix )
    {
        shuntingyard( lstInfix );
        validate();
    }

    //------------------------------------------------------------------------//

    List<XprToken> getPostFix()
    {
        return lstRPN;
    }

    List<ICandi.IError> getErrors()
    {
        return lstErrors;
    }

    //------------------------------------------------------------------------//
    // PRIVATE SCOPE

    /**
     * Shunting Yard algorithm implementation to convert an expression from infix notation to
     * Reverse Polish Notation (AKA postfix notation).
     *
     * @param lstInfix A list of tokens representing an expression using infix notation.
     * @param mapVars
     * @param mapErrors
     * @return A list of tokens representing same expression but using Reverse Polish Notation.
     */
    private void shuntingyard( List<XprToken> lstInfix )
    {
        StdXprOps       operators = new StdXprOps();
        Stack<XprToken> stack     = new Stack<>();
        XprToken        lastFunc  = null;

        for( int n = 0; n < lstInfix.size(); n++ )
        {
            XprToken token = lstInfix.get( n );
            XprToken tPrev = UtilColls.getAt( lstInfix, n - 1 );
            XprToken tNext = UtilColls.getAt( lstInfix, n + 1 );

            switch( token.type() )
            {
                case XprToken.BOOLEAN:
                case XprToken.NUMBER :
                case XprToken.STRING :
                case XprToken.VARIABLE:
                    if( (tNext != null) && tNext.isNotType( XprToken.OPERATOR, XprToken.RESERVED_WORD, XprToken.PARENTH_CLOSED, XprToken.PARAM_SEPARATOR ) )
                        lstErrors.add( new CodeError( "Missing operator", tNext, true ) );

                    lstRPN.add( token );    // Constants and variable-names are added directly to the RPN List

                    break;

                case XprToken.FUNCTION:
                    if( isNotLast( token, tNext ) && tNext.isNotType( XprToken.PARENTH_OPEN ) )
                        lstErrors.add( new CodeError( "Expected \"(\"", token ) );

                    stack.push( token );
                    lastFunc = token;

                    break;

                case XprToken.PARAM_SEPARATOR:     // ','
                    if( (tNext != null) && tNext.isType( XprToken.OPERATOR, XprToken.PARAM_SEPARATOR, XprToken.PARENTH_CLOSED, XprToken.RESERVED_WORD ) )
                    {
                        lstErrors.add( new CodeError( "Invalid token"+ tNext.text(), tNext ) );
                    }
                    else if( isNotFirst( token, tPrev ) && isNotLast( token, tNext ) )
                    {
                        while( (! stack.isEmpty()) && stack.peek().isNotType( XprToken.PARENTH_OPEN ) )
                            lstRPN.add( stack.pop() );

                        if( stack.isEmpty() )
                        {
                            if( lastFunc == null ) lstErrors.add( new CodeError( "Unexpected comma", token ) );
                            else                   lstErrors.add( new CodeError( "Parse error for function \""+ lastFunc.text() +'"', token ) );
                        }
                    }

                    break;

                case XprToken.OPERATOR:
                    if( isNotFirst( token, tPrev ) && isNotLast( token, tNext ) &&
                        tNext.isType( XprToken.OPERATOR, XprToken.PARAM_SEPARATOR, XprToken.PARENTH_CLOSED, XprToken.RESERVED_WORD ) )
                    {
                         lstErrors.add( new CodeError( "Missing operand for operator "+ token.text(), tNext ) );
                    }

                    Operator op = operators.get( token.text() );

                    if( op == null )
                    {
                        lstErrors.add( new CodeError( "Unknown operator \"" + token.text() +'"', token ) );
                    }
                    else
                    {
                        while( (! stack.isEmpty())
                                && stack.peek().isType( XprToken.OPERATOR, XprToken.OPERATOR_UNARY )
                                && (op.isLeftAssoc && (op.precedence <= operators.get( stack.peek().text() ).precedence))
                                && (! stack.peek().isType( XprToken.PARENTH_OPEN )) )
                        {
                            lstRPN.add( stack.pop() );
                        }

                        stack.push( token );
                    }

                    break;

                case XprToken.OPERATOR_UNARY:
                    if( tNext != null )
                    {
                        if( tNext.isType( XprToken.FUNCTION ) )                                     // We need to know the function return type
                        {
                            Class<?> clazz = token.isText( '!' ) ? Boolean.class : Number.class;    // If not "!", the other unaries are: "+" and "-"

                            if( ! StdXprFns.getReturnType( tNext.text(), -1 ).isAssignableFrom( clazz ) )
                                lstErrors.add( new CodeError( "Invalid unary operator \""+ token.text() +"\" for operand \""+ tNext.text() +'"', tNext ) );
                        }
                        else
                        {
                            short nNextType = token.isText( '!' ) ? XprToken.BOOLEAN : XprToken.NUMBER;    // If not "!", the other unaries are: "+" and "-"

                            if( tNext.isNotType( nNextType, XprToken.PARENTH_OPEN, XprToken.VARIABLE ) )
                                lstErrors.add( new CodeError( "Invalid unary operator \""+ token.text() +"\" for operand \""+ tNext.text() +'"', tNext ) );
                        }
                    }
                    else
                    {
                        lstErrors.add( new CodeError( "Operator \""+ token.text() +"\" can not be last token", token ) );
                    }

                    stack.push( token );

                    break;

                case XprToken.PARENTH_OPEN:
                    if( isNotLast( token, tNext ) && tNext.isType( XprToken.OPERATOR, XprToken.PARAM_SEPARATOR ) )
                        lstErrors.add( new CodeError( "Invalid token after \"(\"", tNext ) );

                    if( (tPrev != null) && tPrev.isType( XprToken.FUNCTION ) )
                        lstRPN.add( token );

                    stack.push( token );

                    break;

                case XprToken.PARENTH_CLOSED:                                                                                                           // Only AFTER and WITHIN can exist because ALL and ANY were expanded
                    if( isNotFirst( token, tPrev) && (tNext != null) && tNext.isNotType( XprToken.OPERATOR, XprToken.PARAM_SEPARATOR, XprToken.PARENTH_CLOSED, XprToken.RESERVED_WORD ) )
                        lstErrors.add( new CodeError( "Invalid token after \")\"", tNext ) );

                    while( ! stack.isEmpty() && stack.peek().isNotType( XprToken.PARENTH_OPEN ) )
                        lstRPN.add( stack.pop() );

                    if( stack.isEmpty() )
                        lstErrors.add( new CodeError( "Mismatched parentheses", token ) );
                    else
                        stack.pop();

                    if( ! stack.isEmpty() && stack.peek().isType( XprToken.FUNCTION ) )    // As function comes out from the 'stack'm we now it was checked by 'case XprToken.FUNCTION'
                    {
                        lstRPN.add( stack.pop() );                                     // Extracts the function name form the stack
                        lastFunc = null;
                    }

                    break;

                case XprToken.RESERVED_WORD:
                    if( token.isText( XprUtils.sAFTER, XprUtils.sWITHIN ) )                // Only AFTER and WITHIN can exist in the expression because ALL and ANY were substituted (expanded)
                    {
                        if( isNotFirst( token, tPrev ) && isNotLast( token, tNext ) && tNext.isNotType( XprToken.FUNCTION, XprToken.NUMBER, XprToken.PARENTH_OPEN, XprToken.VARIABLE ) )
                        {
                            lstErrors.add( new CodeError( "Invalid token after "+ token.text(), tNext ) );
                        }
                        else if( tNext.isType( XprToken.FUNCTION ) && ! StdXprFns.getReturnType( tNext.text(), -1 ).isAssignableFrom( Number.class ) )
                        {
                            lstErrors.add( new CodeError( "Function does not returns a number"+ tNext.text(), tNext ) );
                        }

                        while( (! stack.isEmpty())
                               && stack.peek().isType( XprToken.OPERATOR, XprToken.OPERATOR_UNARY )
                               && (! stack.peek().isType( XprToken.PARENTH_OPEN )) )
                        {
                            lstRPN.add( stack.pop() );
                        }

                        stack.push( token );
                    }
                    else
                    {
                        lstErrors.add( new CodeError( "Invalid token '"+ token.text() +'\'', token ) );
                    }

                    break;

                default:
                    lstErrors.add( new CodeError( "Unknown token '"+ token.text() +'\'', token ) );
            }
        }

        while( ! stack.isEmpty() )
        {
            XprToken token = stack.pop();

            if( token.isType( XprToken.PARENTH_OPEN, XprToken.PARENTH_CLOSED ) )
                lstErrors.add( new CodeError( "Mismatched parentheses", token ) );
            else
                lstRPN.add( token );
        }

        if( ! lstErrors.isEmpty() )
            lstRPN.clear();

//lstRPN.forEach( t -> System.out.print( t.text() +' ' ) ); System.out.println();
        //output.forEach( token -> System.out.toExpression( token.text+'{'+token.type()+"} " ) ); System.out.println();
    }

    /**
     * Performs several checks.<br>
     * Thanks to Norman Ramsey: http://stackoverflow.com/questions/789847/postfix-notation-validation
     *
     * @return The error messages and their offset in passed expression string.
     */
    private void validate()
    {
        if( ! lstErrors.isEmpty() )
            return;

        // When code arrives here, the expression is syntactically valid: parenthesis are balanced, the func exists (XprePreproc checks it), etc.
        // We can only check that a methos exist and receives the appropriate number of arguments, but we can not check that this method belongs
        // to the right class because this is known only at run time.
        // We do not check the type of argument because they always receive Object (it is checked inside the funcs).

        // Following expression,    --> get("ram" ):add( ram:format("##") )
        // is transformed into this --> ( ram get ( ram ( ## format : add :

// TODO: terminarlo el validate()
//        Stack<XprToken> stack = new Stack<>();
//
//        for( int n = 0; n < lstRPN.size(); n++ )
//        {
//            XprToken token = lstRPN.get( n );
//
//            switch( token.type() )
//            {
//                case XprToken.OPERATOR:
//                case XprToken.OPERATOR_UNARY:
//                    stack.push( token );
//                    break;
//
//                case XprToken.PARENTH_OPEN:
//                    while( )
//                    break;
//
//                case XprToken.FUNCTION:
//                    XprToken tok = stack.pop();
//                    boolean  isType;
//
//                         if( tok.isType( XprToken.PARENTH_OPEN ) )                          isType = StdXprFns.isFunction( sFn, n );
//                    else if( tok.isType( XprToken.OPERATOR ) && tok.text().equals( ":" ) )  isType = StdXprFns.isFunction( sFn, n );
//
//                    if( ! isType )
//
//
//                    break;
//
//                default:
//                    throw new AssertionError();
//            }
//
//
//
//
//            if( token.isType( XprToken.PARENTH_OPEN ) )
//            {
//                int nParams = 0;
//
//                while( lstRPN.get( ++n ).isNotType( XprToken.FUNCTION ) )
//                    nParams++;
//
//                XprToken next = UtilColls.getAt( lstRPN, n+1 );
//
//                if( next != null && next.isType( Language.SEND_OP ) )
//                    nParams++;
//
//                XprToken tokFn = lstRPN.get( n );     // The while above loops (increasing n) until a function isType found
//
////                short nRes = StdXprFns.isFuncOrMethod( tokFn.text(), nParams );
////
////                assert nRes != 0;    // Can not be 0 because it was previously checked it exists (if it isType 0, I did something wrong)
////
////                if( nRes == -1 )
////                    lstErrors.add( new CodeError( "Invalid number of parameters for \""+ tokFn.text() +'\"', tokFn ) );
//            }
//        }
    }

    private static boolean isClosedParenth( Object o )
    {
        if( o.getClass() != String.class )
            return false;

        String s = o.toString();

        return (s.length() == 1) &&
               (s.charAt(0) == ')');
    }

    private boolean isNotFirst( XprToken tCurrent, XprToken tPrevious )
    {
        if( tPrevious == null )
        {
            lstErrors.add( new CodeError( "Invalid expression start: "+ tCurrent.text(), tCurrent ) );
            return false;
        }

        return true;
    }

    private boolean isNotLast( XprToken tCurrent, XprToken tNext )
    {
        if( tNext == null )
        {
            lstErrors.add( new CodeError( "Incomplete expression, invalid end: "+ tCurrent.text(), tCurrent ) );
            return false;
        }

        return true;
    }
}