
package com.peyrona.mingle.lang.xpreval;

import com.peyrona.mingle.lang.MingleException;
import com.peyrona.mingle.lang.interfaces.ICandi;
import com.peyrona.mingle.lang.japi.UtilColls;
import com.peyrona.mingle.lang.japi.UtilType;
import com.peyrona.mingle.lang.lexer.CodeError;
import com.peyrona.mingle.lang.lexer.Language;
import com.peyrona.mingle.lang.xpreval.functions.StdXprFns;
import com.peyrona.mingle.lang.xpreval.operators.StdXprOps;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * This class instances are the nodes used to construct an AST tree that represents an
 * expression. The expression is evaluated by traversing the tree.
 * <p>
 * There's no explicit specialization (subclasses) for leaf nodes. Leaves are denoted by
 * nodes where both the left and right node is null.
 *
 * @author Francisco José Morero Peyrona
 *
 * Official web site at: <a href="https://github.com/peyrona/mingle">https://github.com/peyrona/mingle</a>
 */
final class ASTNode
{
    private volatile XprToken      token     = null;   // Literal, operator, variable or func_name
    private volatile ASTNode       left      = null;   // Left child (volatile for thread-safe reads)
    private volatile ASTNode       right     = null;   // Right child (volatile for thread-safe reads)
    private volatile ASTNode       parent    = null;   // ReadOnly property (volatile for thread-safe reads)
    private volatile List<ASTNode> lstFnArgs = null;   // Function arguments (AKA parameters)
    private volatile Future        future    = null;   // Used to make this class less verbose

    private static final String sOP_LOGIC_AND = "&&";
    private static final String sOP_LOGIC_OR  = "||";

    //------------------------------------------------------------------------//
    // PUBLIC INTERFACE

    // CARE !
    // Intentionally this class does not have hashcode() neither equals()

    public String toText()
    {
        return (token == null ? "null" : token.text()) + getFnArgs();
    }

    @Override
    public String toString()
    {
        StringBuilder sb = new StringBuilder( 256 );

        String tokenText  = (token == null) ? "null" : token.text();
        String parentText = (parent == null || parent.token == null) ? "" : parent.token.text();
        String leftText   = (left   == null || left.token   == null) ? "" : left.token.text();
        String rightText  = (right  == null || right.token  == null) ? "" : right.token.text();

        sb.append( "{<" ).append( tokenText ).append( getFnArgs() ).append( "> | " )
          .append(" parent<" ).append( parentText ).append( "> | " )
          .append( "left<"   ).append( leftText   ).append( "> | " )
          .append( "right<"  ).append( rightText  ).append( '>' )
          .append( (isAfter() ? " (After)"
                              : (isWithin() ? " (Within)"
                                            : " (!F)")) )
          .append( '}' );

        return sb.toString();
    }

    //------------------------------------------------------------------------//
    // PACKAGE SCOPE

    /**
     * Returns the left node or null if there is not left node.
     * @return the left node or null if there is not left node.
     */
    ASTNode left()
    {
        return left;
    }

    /**
     * Assigns received node's parent to this instance and assigns received node to ::left.
     *
     * @param node
     * @return Itself.
     */
    ASTNode left( ASTNode node )
    {
        assert node != null && left == null;

        synchronized( this )
        {
            node.parent = this;
            this.left   = node;
        }

        return this;
    }

    /**
     * Returns the right node or null if there is not right node.
     * @return the right node or null if there is not right node.
     */
    ASTNode right()
    {
        return right;
    }

    /**
     * Assigns received node's parent to this instance and assigns received node to ::right.
     *
     * @param node
     * @return Itself.
     */
    ASTNode right( ASTNode node )
    {
        assert node != null && right == null;

        synchronized( this )
        {
            node.parent = this;
            this.right  = node;
        }

        return this;
    }

    ASTNode parent()       // ReadOnly property
    {
        return parent;
    }

    XprToken token()
    {
        return token;
    }

    ASTNode token( XprToken token )
    {
        synchronized( this )
        {
            this.token = token;

            if( token.isType( XprToken.RESERVED_WORD ) )
                future = new Future( token );
        }

        return this;
    }

    long delay()
    {
        return (future == null || right == null) ? -1 : UtilType.toLong( right.token.value() );    // Better to return -1 than provoking an exc
    }

    List<ASTNode> getFnArgs()
    {
        return lstFnArgs;
    }

    ASTNode addFnArg( ASTNode node )
    {
        synchronized( this )
        {
            if( lstFnArgs == null )
                lstFnArgs = new ArrayList<>();

            lstFnArgs.add( node );
        }

        return this;
    }

// NOT USED
//    boolean isRoot()
//    {
//        return parent == null;
//    }

    boolean isAfter()
    {
        return (future != null) && future.isAfter();
    }

    boolean isWithin()
    {
        return (future != null) && future.isWithin();
    }

    boolean isLeaf()
    {
        return (left == null) && (right == null);
    }

    /**
     * Converts this node into a leaf
     * <pre>
     *    left = right = null;
     * </pre>
     *
     * @return Itself.
     */
    ASTNode beLeaf()
    {
        left  = null;
        right = null;

        return this;
    }

    /**
     * Invoked when this node (future AFTER or WITHIN) timeout has elapsed.
     *
     * @param ops
     * @param fns
     * @param vars
     * @param hasAllVars
     * @return
     */
    /**
     * Invoked when this node (future AFTER or WITHIN) timeout has elapsed.
     *
     * @param vars Variable map for evaluation.
     * @param hasAllVars Whether all variables have values.
     * @return The result of the future evaluation (true or false).
     * @throws MingleException If this is not a future node or left child is missing.
     */
    boolean expired( Map<String,Object> vars, boolean hasAllVars )
    {
        if( future == null )
            throw new MingleException( MingleException.INVALID_STATE );

        if( left == null )
            throw new MingleException( "Future node is missing its expression (left child)" );

        if( future.result() == null )    // If not already solved
            future.expired( left.eval( vars, hasAllVars ) );

        return future.result();
    }

    /**
     * Evaluates all its child nodes and finally evaluates itself.
     * <p>
     * This method returns null if there were one or more nodes with unsatisfied future
     * conditions (AFTER and WITHIN), otherwise it returns the result of the evaluation.
     *
     * @param ops  An instance of this class.
     * @param fns  An instance of this class.
     * @param vars Granted to contain no null values.
     * @return The result of the evaluation or null if it can not resolved yet or there
     *         are unsatisfied futures.
     */
    Object eval( Map<String,Object> vars, boolean hasAllVars ) throws MingleException
    {
        // 'hasAllVars' is needed because this evaluator is a lazy one: under certain
        // circumstances it can operate even when not all variables varlues are known.

        if( future != null && future.result() != null )    // This branch (starting at this node) was a future and the future was already resolved.
            return future.result();                        // The future was resolved to true or false (futures always resolve to a boolean value).

        switch( token.type() )
        {
            case XprToken.BOOLEAN:
            case XprToken.NUMBER:
            case XprToken.STRING:
                return token.value();

            case XprToken.VARIABLE:
                return vars.get( token.text() );

            case XprToken.OPERATOR:
                if( token.isText( Language.SEND_OP ) )
                {
                    if( hasAllVars )
                    {
                        Object   leftValue = left.eval( vars, hasAllVars );
                        Object[] aoArgs    = right.toArrayAndPrepare( leftValue, vars, hasAllVars );

                        return StdXprFns.invoke( right.token.text(), aoArgs );
                    }
                }
                else if( token.isText( sOP_LOGIC_AND ) )      // Lazy operator AND  (AKA Short-Circuit Evaluation)
                {
                    Object lVal = left.eval( vars, hasAllVars );

                    if( lVal != null && ! UtilType.isTruthy( lVal ) )              // Short-Circuit Evaluation at left side
                    {
                        return Boolean.FALSE;
                    }
                    else
                    {
                        Object rVal = right.eval( vars, hasAllVars );    // Lets see if the right is known

                        if( rVal != null && ! UtilType.isTruthy( rVal ) )          // Short-Circuit Evaluation at right side
                        {
                            return Boolean.FALSE;
                        }
                        else
                        {
                            if( (lVal != null) && (rVal != null) )
                            {
                                return StdXprOps.eval( token.text(), lVal, rVal );
                            }
                        }
                    }
                }
                else if( token.isText( sOP_LOGIC_OR ) )    // Lazy operator OR  (AKA Short-Circuit Evaluation)
                {
                    Object lVal = left.eval( vars, hasAllVars );

                    if( lVal != null && UtilType.isTruthy( lVal ) )              // Short-Circuit Evaluation at left side
                    {
                        return Boolean.TRUE;
                    }
                    else
                    {
                        Object rVal = right.eval( vars, hasAllVars );  // Lets see if the right is known

                        if( rVal != null && UtilType.isTruthy( rVal ) )          // Short-Circuit Evaluation at right side
                        {
                            return Boolean.TRUE;
                        }
                        else
                        {
                            if( (lVal != null) && (rVal != null) )
                            {
                                return StdXprOps.eval( token.text(), lVal, rVal );
                            }
                        }
                    }
                }
                else   // Any other operator (if one or more vars are not initialized, ops.eval(...) returns 'null')
                {
                    Object value = StdXprOps.eval( token.text(),
                                                   left.eval(  vars, hasAllVars ),
                                                   right.eval( vars, hasAllVars ) );

                    return value;
                }

                break;

            case XprToken.OPERATOR_UNARY:
                // As MINUS and UNARY_MINUS operators share same representation ('-'), the best approach is to
                // check everytime if the unary is a MINUS and in this case, to change it to an different character.
                // If one or more vars are not initialized, ops.eval(...) returns 'null'
                // NEXT: quizás esto se podría hacer en algún método de EvalByAST.java

                String sOp = (token.isText( '-' ) ? StdXprOps.sUNARY_MINUS : token.text());

                return StdXprOps.eval( sOp, left.eval( vars, hasAllVars ) );

            case XprToken.FUNCTION:
                if( hasAllVars )
                    return StdXprFns.invoke( token.text(), toArray( vars, hasAllVars ) );

                break;

            case XprToken.RESERVED_WORD:
                return future.apply( left.eval( vars, hasAllVars ) );    // Returns either false (if WITHIN failed) or null (if still working)

            default:
                throw new MingleException( MingleException.SHOULD_NOT_HAPPEN );
        }

        return null;
    }

    ASTNode reset()
    {
        if( left != null )      // It is null when node is a leaf
            left.reset();

        if( right != null )     // It is null for Unary operators
            right.reset();

        if( future != null )
            future.reset();

        return this;
    }

    /**
     * Checks if this node is valid.
     * <p>
     * The expression:
     *     clock:int() == 1
     * produces AST:
     * <pre>
     * ├── ==
     *     ├── :
     *     │   ├── clock
     *     │   └── int
     *     └── 1
     * </pre>
     * The expression:
     *     get("today"):day() == 1
     * produces AST:
     * <pre>
     * ├── ==
     *     ├── :
     *     │   ├── get("today")
     *     │   └── day()
     *     └── 1
     * </pre>
     * When validating method calls via send operator (:), the left operand of the
     * send operator becomes the first argument to the method. For example:
     * <ul>
     *   <li>{@code myDate:day()} - day() receives 1 arg (myDate)</li>
     *   <li>{@code myList:get(0)} - get() receives 2 args (myList, 0)</li>
     * </ul>
     * <p>
     * Note: This validation is intentionally lenient to avoid false positives.
     * Many functions use varargs, and the exact argument count may be difficult
     * to determine statically. Runtime validation will catch actual errors.
     *
     * @param lstErrors To add found errors (if any).
     * @return true if this node is valid.
     */
    boolean validate( List<ICandi.IError> lstErrors )
    {
        if( token.type() != XprToken.FUNCTION )
            return true;    // Only validate FUNCTION nodes; XprTokenizer already validated other token types

        String fnName  = token.text();
        int    nArgs   = (lstFnArgs == null) ? 0 : lstFnArgs.size();

        // When called as a method via ':' (e.g., "22.3:round()"), the receiver from the left
        // side of ':' is prepended as the first argument at runtime, so effective arity is +1.
        boolean isMethodCall = (parent != null) &&
                               (parent.token != null) &&
                               parent.token.isText( Language.SEND_OP ) &&
                               (parent.right == this);

        int nArgsForFunction = isMethodCall ? nArgs + 1 : nArgs;

        // Extended types (date, time, list, pair) are constructors with varargs - always valid
        if( StdXprFns.isExtendedType( fnName ) )
            return true;

        // Check if the function exists with the exact argument count
        if( StdXprFns.getFunction( fnName, nArgsForFunction ) != null )
            return true;

        // Check if it exists as a method (on date, time, list, pair) with the exact argument count.
        // Methods on extended types do NOT receive the receiver as a parameter (it is the 'this' object),
        // so use the original nArgs count.
        if( StdXprFns.getMethod( fnName, nArgs ) != null )
            return true;

        // The function/method name exists but not with this argument count: arity mismatch
        if( StdXprFns.getFunction( fnName, -1 ) != null || StdXprFns.getMethod( fnName, -1 ) != null )
        {
            int displayArgs = isMethodCall ? nArgsForFunction : nArgs;
            lstErrors.add( new CodeError( "Function \"" + fnName + "\" does not accept " + displayArgs + " argument" + (displayArgs != 1 ? "s" : ""), token ) );
            return false;
        }

        // Function/method not found at all.
        // We intentionally don't add errors here because the function might be:
        // 1. A method that will be resolved at runtime based on the receiver type
        // 2. A dynamically registered function
        // Runtime validation will catch actual undefined functions

        return true;
    }

    //------------------------------------------------------------------------//
    // PRIVATE SCOPE

    // Functions does not admit AFTER neither WITHIN inside their parameters

    private Object[] toArray( Map<String,Object> vars, boolean hasAllVars )   // vars is never null, checked at ::eval(...)
    {
        return toArrayAndPrepare( null, vars, hasAllVars );
    }

    /**
     * Builds an array of evaluated function arguments, optionally prepending a value.
     * <p>
     * This method always creates a new array to avoid data corruption from callers
     * modifying the returned array.
     *
     * @param prependValue Optional value to prepend (can be null).
     * @param vars Variable map for evaluation.
     * @param hasAllVars Whether all variables have values.
     * @return A new array containing the evaluated arguments, or null if no arguments.
     */
    private Object[] toArrayAndPrepare( Object prependValue, Map<String,Object> vars, boolean hasAllVars )
    {
        if( UtilColls.isEmpty( lstFnArgs ) )
            return (prependValue == null) ? null : new Object[] { prependValue };

        int size      = lstFnArgs.size();    // No need to sync on lstFnArgs beacuse once created it is inmutable
        int totalSize = size + (prependValue != null ? 1 : 0);

        // Always create a new array to prevent data corruption from callers
        // modifying the returned array (which would affect subsequent calls)
        Object[] args = new Object[totalSize];

        int len = size;
        size--;

        // Place prepend value at the beginning if provided
        int startIndex = 0;

        if( prependValue != null )
        {
            args[0] = prependValue;
            startIndex = 1;
        }

        // Evaluate function arguments in reverse order
        for( int n = 0; n < len; n++ )
            args[startIndex + (size-n)] = lstFnArgs.get( n ).eval( vars, hasAllVars );    // Items in lstFnArgs are in reverse order

        return args;
    }

    //------------------------------------------------------------------------//
    // INNER CLASS
    //------------------------------------------------------------------------//
    /**
     * Used to manage nodes that are of type AFTER ow WITHIN
     */
    private final class Future
    {
        private final    boolean  isAfter;
        private volatile Object   oWithInitVal = null;   // WITHIN initial Value (null when this node is not of type 'future' and 'new AtomicReference(null)' when not initialized yet
        private volatile Boolean  bResult      = null;   // Updated when Timer ends for AFTER and when a var changed or timer ended for WITHIN.
                                                         // After ended, it has the furure result (futures are always booleans). null means that it is not resolved yet.

        //------------------------------------------------------------------------//

        Future( XprToken token )
        {
            isAfter = token.isText( XprUtils.sAFTER );
        }

        //------------------------------------------------------------------------//

        boolean isAfter()
        {
            return isAfter;
        }

        boolean isWithin()
        {
            return ! isAfter;
        }

        Boolean result()
        {
            return bResult;
        }

        Boolean apply( Object value )
        {
            if( bResult != null )
                return bResult;

            if( value == null )
                return null;

            // If this ASTNode is AFTER, there is nothing to do beacuse when the delay is elapsed,
            // the ASTNode::expired(...) will be invoked by EvalByAST and the AFTER will be evaluated.

            if( isWithin() )
            {
                if( oWithInitVal == null )    // Not initialized yet
                {
                    if( Boolean.FALSE.equals( value ) )    // Condition is already false: WITHIN can never be satisfied
                    {
                        bResult = false;
                        return Boolean.FALSE;
                    }

                    oWithInitVal = value;
                }
                else
                {
                    if( ! Objects.equals( value, oWithInitVal ) )   // Has changed
                    {
                        bResult = false;                            // 0 == false
                        return Boolean.FALSE;                       // Can resolve now
                    }
                }
            }

            return null;
        }

        void expired( Object value )
        {
            if( isAfter() )
            {
                if( value == null )  bResult = false;              // When variable's value never arrived, the AFTER result hast to be 'false'
                else                 bResult = (Boolean) value;
            }
            else
            {
                if( oWithInitVal == null )       bResult = false;                             // When the variable's value never arrived, WITHIN has to evaluate to false
                else if( value instanceof Boolean ) bResult = (Boolean) value;               // Boolean WITHIN: condition must be true now (if apply() was not cancelled, it has been true throughout)
                else                             bResult = Objects.equals( value, oWithInitVal );   // Non-boolean WITHIN: the value never changed
            }
        }

        void reset()
        {
            bResult      = null;
            oWithInitVal = null;
        }
    }
}