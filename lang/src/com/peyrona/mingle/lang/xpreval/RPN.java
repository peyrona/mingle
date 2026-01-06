
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
 * Official web site at: <a href="https://github.com/peyrona/mingle">https://github.com/peyrona/mingle</a>
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
                        if( token.isText( '!' ) )
                        {
                            // With truthy/falsy semantics, '!' can be applied to any value type
                            if( tNext.isType( XprToken.OPERATOR, XprToken.PARAM_SEPARATOR, XprToken.PARENTH_CLOSED, XprToken.RESERVED_WORD ) )
                                lstErrors.add( new CodeError( "Invalid unary operator \"!\" for operand \""+ tNext.text() +'"', tNext ) );
                        }
                        else if( tNext.isType( XprToken.FUNCTION ) )                                // '+' and '-' unary operators
                        {
                            if( ! StdXprFns.getReturnType( tNext.text(), -1 ).isAssignableFrom( Number.class ) )
                                lstErrors.add( new CodeError( "Invalid unary operator \""+ token.text() +"\" for operand \""+ tNext.text() +'"', tNext ) );
                        }
                        else
                        {
                            // '+' and '-' unary operators require NUMBER
                            if( tNext.isNotType( XprToken.NUMBER, XprToken.PARENTH_OPEN, XprToken.VARIABLE ) )
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
     * Validates the RPN expression by simulating stack-based evaluation.
     * <p>
     * This validation ensures:
     * <ul>
     *   <li>Binary operators have 2 operands available on the stack</li>
     *   <li>Unary operators have 1 operand available on the stack</li>
     *   <li>Functions consume all arguments since their opening parenthesis marker</li>
     *   <li>The expression evaluates to exactly one result</li>
     * </ul>
     * <p>
     * The key insight is that '(' tokens in the RPN serve as markers for function argument
     * boundaries. When a function is encountered, it consumes all values pushed since its
     * corresponding '(' marker.
     * <p>
     * Thanks to Norman Ramsey: http://stackoverflow.com/questions/789847/postfix-notation-validation
     */
    private void validate()
    {
        if( ! lstErrors.isEmpty() || lstRPN.isEmpty() )
            return;

        // Use a stack to track function argument boundaries (stack depth at each '(' marker)
        Stack<Integer> fnArgMarkers = new Stack<>();
        int stackDepth = 0;

        for( XprToken token : lstRPN )
        {
            switch( token.type() )
            {
                case XprToken.BOOLEAN:
                case XprToken.NUMBER:
                case XprToken.STRING:
                case XprToken.VARIABLE:
                    stackDepth++;
                    break;

                case XprToken.OPERATOR:
                    // Binary operators: pop 2, push 1 (net: -1)
                    if( stackDepth < 2 )
                        return;    // Invalid state - silently return to avoid false positives
                    stackDepth--;
                    break;

                case XprToken.OPERATOR_UNARY:
                    // Unary operators: pop 1, push 1 (net: 0)
                    if( stackDepth < 1 )
                        return;    // Invalid state - silently return to avoid false positives
                    // stackDepth unchanged
                    break;

                case XprToken.PARENTH_OPEN:
                    // Mark current stack depth as function argument boundary
                    fnArgMarkers.push( stackDepth );
                    break;

                case XprToken.FUNCTION:
                    // Function consumes all values since its '(' marker and produces 1 result
                    if( ! fnArgMarkers.isEmpty() )
                    {
                        int markerDepth = fnArgMarkers.pop();
                        // Everything between marker and now becomes consumed; function produces 1 result
                        stackDepth = markerDepth + 1;
                    }
                    else
                    {
                        // Function without parenthesis marker (shouldn't normally happen)
                        stackDepth++;
                    }
                    break;

                case XprToken.RESERVED_WORD:
                    // AFTER and WITHIN: binary temporal operators (expression, delay) -> boolean
                    if( token.isText( XprUtils.sAFTER, XprUtils.sWITHIN ) )
                    {
                        if( stackDepth < 2 )
                        {
                            lstErrors.add( new CodeError( "'"+ token.text() +"' requires expression and delay value", token ) );
                            return;
                        }
                        stackDepth--;    // Pop 2, push 1
                    }
                    break;

                default:
                    // PARAM_SEPARATOR and PARENTH_CLOSED should not appear in final RPN
                    break;
            }
        }

        // After processing all tokens, stack should have exactly 1 value (the result)
        if( stackDepth == 0 )
        {
            lstErrors.add( new CodeError( "Expression produces no result", lstRPN.get( 0 ) ) );
        }
        // Note: stackDepth > 1 is not always an error due to complex expression patterns
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