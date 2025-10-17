
package com.peyrona.mingle.lang.xpreval;

import com.peyrona.mingle.lang.MingleException;
import com.peyrona.mingle.lang.interfaces.ICandi;
import com.peyrona.mingle.lang.japi.UtilColls;
import com.peyrona.mingle.lang.japi.UtilStr;
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
    private XprToken      token     = null;   // Literal, operator, variable or func_name
    private ASTNode       left      = null;   // Left child        (no sync needed)
    private ASTNode       right     = null;   // Right child       (no sync needed)
    private ASTNode       parent    = null;   // ReadOnly property (no sync needed)
    private List<ASTNode> lstFnArgs = null;   // Function arguments (AKA parameters)
    private Future        future    = null;   // Used to make this class less verbose

    private static final String sOP_LOGIC_AND = "&&";
    private static final String sOP_LOGIC_OR  = "||";

    //------------------------------------------------------------------------//
    // PUBLIC INTERFACE

    // CARE !
    // Intentionally this class does not have hashcode() neither equals()

    public String toText()
    {
        return token.text() + getFnArgs();
    }

    @Override
    public String toString()
    {
        StringBuilder sb = new StringBuilder( 256 );

        sb.append( "{<" ).append( token.text() ).append( getFnArgs() ).append( "> | " )
          .append(" parent<" ).append( (parent == null ? "" : parent.token.text()) ).append( "> | " )
          .append( "left<"   ).append( (left   == null ? "" : left.token.text()  ) ).append( "> | " )
          .append( "right<"  ).append( (right  == null ? "" : right.token.text() ) ).append( '>' )
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

        node.parent = this;
        this.left   = node;

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

        node.parent = this;
        this.right  = node;

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

    synchronized ASTNode token( XprToken token )
    {
        this.token = token;

        if( token.isType( XprToken.RESERVED_WORD ) )
            future = new Future( token );

        return this;
    }

    long delay()
    {
        return (future == null) ? -1 : UtilType.toLong( right.token.value() );    // Better to return -1 to provoke an exc
    }

    ASTNode addFnArg( ASTNode node )
    {
        if( lstFnArgs == null )
            lstFnArgs = new ArrayList<>();

        lstFnArgs.add( node );

        return this;
    }

// NOT USED
//    boolean isRoot()
//    {
//        return parent == null;
//    }

    boolean isAfter()
    {
        return future != null && future.isAfter();
    }

    boolean isWithin()
    {
        return future != null && future.isWithin();
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
    boolean expired( StdXprOps ops, StdXprFns fns, Map<String,Object> vars, boolean hasAllVars )
    {
        if( future.result() == null )    // If not already already solved
            future.expired( left.eval( ops, fns, vars, hasAllVars ) );

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
    Object eval( StdXprOps ops, StdXprFns fns, Map<String,Object> vars, boolean hasAllVars ) throws MingleException
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
                        Object   leftValue = left.eval( ops, fns, vars, hasAllVars );
                        Object[] aoArgs    = right.toArrayAndPrepare( leftValue, ops, fns, vars, hasAllVars );

                        return fns.invoke( right.token.text(), aoArgs );
                    }
                }
                else if( token.isText( sOP_LOGIC_AND ) )      // Lazy operator AND  (AKA Short-Circuit Evaluation)
                {
                    Object lVal = left.eval( ops, fns, vars, hasAllVars );

                    if( lVal == Boolean.FALSE )                                    // Short-Circuit Evaluation at left side
                    {
                        return Boolean.FALSE;
                    }
                    else
                    {
                        Object rVal = right.eval( ops, fns, vars, hasAllVars );    // Lets see if the right is known

                        if( rVal == Boolean.FALSE )                                // Short-Circuit Evaluation at right side
                        {
                            return Boolean.FALSE;
                        }
                        else
                        {
                            if( (lVal != null) && (rVal != null) )
                            {
                                if( ! (lVal instanceof Boolean) ) throw invalidArg( "Boolean", lVal );
                                if( ! (rVal instanceof Boolean) ) throw invalidArg( "Boolean", rVal );

                                return ops.eval( token.text(), (Boolean) lVal, (Boolean) rVal );
                            }
                        }
                    }
                }
                else if( token.isText( sOP_LOGIC_OR ) )    // Lazy operator OR  (AKA Short-Circuit Evaluation)
                {
                    Object lVal = left.eval( ops, fns, vars, hasAllVars );

                    if( lVal == Boolean.TRUE )                       // Short-Circuit Evaluation at left side
                    {
                        return Boolean.TRUE;
                    }
                    else
                    {
                        Object rVal = right.eval( ops, fns, vars, hasAllVars );  // Lets see if the right is known

                        if( rVal == Boolean.TRUE )                   // Short-Circuit Evaluation at right side
                        {
                            return Boolean.TRUE;
                        }
                        else
                        {
                            if( (lVal != null) && (rVal != null) )
                            {
                                if( ! (lVal instanceof Boolean) ) throw invalidArg( "Boolean", lVal );
                                if( ! (rVal instanceof Boolean) ) throw invalidArg( "Boolean", rVal );

                                return ops.eval( token.text(), (Boolean) lVal, (Boolean) rVal );
                            }
                        }
                    }
                }
                else   // Any other operator (if one or more vars are not initialized, ops.eval(...) returns 'null')
                {
                    Object value = ops.eval( token.text(),
                                             left.eval(  ops, fns, vars, hasAllVars ),
                                             right.eval( ops, fns, vars, hasAllVars ) );

                    return value;
                }

                break;

            case XprToken.OPERATOR_UNARY:
                // As MINUS and UNARY_MINUS operators share same representation ('-'), the best approach is to
                // check everytime if the unary is a MINUS and in this case, to change it to an different character.
                // If one or more vars are not initialized, ops.eval(...) returns 'null'
                // NEXT: quizás esto se podría hacer en algún método de EvalByAST.java

                String sOp = (token.isText( '-' ) ? StdXprOps.sUNARY_MINUS : token.text());

                return ops.eval( sOp, left.eval( ops, fns, vars, hasAllVars ) );

            case XprToken.FUNCTION:
                if( hasAllVars )
                    return fns.invoke( token.text(), toArray( ops, fns, vars, hasAllVars ) );

                break;

            case XprToken.RESERVED_WORD:
                return future.apply( left.eval( ops, fns, vars, hasAllVars ) );    // Returns either false (if WITHIN failed) or null (if still working)

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
     *
     * The expression:
     *     clock:int() == 1
     * produces AST:
     * ├── ==
     *     ├── :
     *     │   ├── clock
     *     │   └── int
     *     └── 1
     *
     * The expression:
     *     get("today"):day() == 1
     * produces AST:
     * ├── ==
     *     ├── :
     *     │   ├── get("today")
     *     │   └── day()
     *     └── 1
     *
     * @param lstErrors To add found errors (if any).
     * @return true if this node is valid.
     */
    boolean validate( List<ICandi.IError> lstErrors )
    {
        // NEXT: other checks?
        //------------------------------------------------------------------------//

        if( token.type() != XprToken.FUNCTION )    // XprTokenizer identfied it as a function or a class or a method.
            return true;                           // But XprTokenizer does not check if the num of params is correct (it is too complex there)

        // If node is a function or method or class, lets check if it receives the especified parameters.

        int nErrs = lstErrors.size();
        int nArgs = -1; // Cambiar por -> (UtilColls.isEmpty( lstFnArgs ) ? 0 : lstFnArgs.size());

// TODO: esto no funciona en todos los casos
//        if( parent != null &&
//            Language.isSendOp( parent.token.text() ) &&
//            parent.left.token.isType( XprToken.VARIABLE, XprToken.OPERATOR, XprToken.OPERATOR_UNARY, XprToken.BOOLEAN, XprToken.NUMBER, XprToken.STRING ) &&
//            StdXprFns.getFunction( parent.right.token.text(), -1 ) != null )
//        {
//            nArgs++;
//        }
//------------------------------------------
        if( StdXprFns.getFunction(    token.text(), nArgs ) == null &&    // More frequent
            StdXprFns.getMethod(      token.text(), nArgs ) == null &&
            StdXprFns.isExtendedType( token.text()        ) == false )    // '== false' For clarity
        {
            lstErrors.add( new CodeError( "Function does not exist or invalid number of arguments '"+ token.text() +'\'', token ) );
        }

        return nErrs == lstErrors.size();    // This method did not add errors
    }

    //------------------------------------------------------------------------//
    // PRIVATE SCOPE

    // Functions does not admit AFTER neither WITHIN inside their parameters

    private Object[] args = new Object[1];   // To save CPU

    private Object[] toArray( StdXprOps ops, StdXprFns fns, Map<String,Object> vars, boolean hasAllVars )   // vars is never null, checked at ::eval(...)
    {
        return toArrayAndPrepare( null, ops, fns, vars, hasAllVars );
    }

    private Object[] toArrayAndPrepare( Object prependValue, StdXprOps ops, StdXprFns fns, Map<String,Object> vars, boolean hasAllVars )
    {
        if( UtilColls.isEmpty( lstFnArgs ) )
            return (prependValue == null) ? null : new Object[] { prependValue };

        int size      = lstFnArgs.size();    // No need to sync on lstFnArgs beacuse once created it is inmutable
        int totalSize = size + (prependValue != null ? 1 : 0);

        if( args.length != totalSize )
            args = new Object[totalSize];
     // else --> when the size is the same as the last call, we reuse 'args'

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
            args[startIndex + (size-n)] = lstFnArgs.get( n ).eval( ops, fns, vars, hasAllVars );    // Items in lstFnArgs are in reverse order

        return args;
    }

    private MingleException invalidArg( String sExpected, Object found )
    {
        String sClass = (found == null) ? "null" : found.getClass().getSimpleName();

        return new MingleException( "Invalid argument. Expected '"+ sExpected +"', found '"+ sClass +"'  (value="+ found +')' );
    }

    private String getFnArgs()    // Aux func for ::toString()
    {
        if( UtilColls.isEmpty( lstFnArgs ) )
            return "";

        StringBuilder sb = new StringBuilder( 64 ).append( '(' );

        for( ASTNode nod : lstFnArgs )
            EvalByAST.visitor( nod, EvalByAST.VISIT_IN_ORDER, node -> sb.append( node.toText() ).append( ',' ) );

        return UtilStr.removeLast( sb, 1 ).append( ')' ).toString();
    }

    //------------------------------------------------------------------------//
    // INNER CLASS
    //------------------------------------------------------------------------//
    /**
     * Used to manage nodes that are of type AFTER ow WITHIN
     */
    private final class Future
    {
        private final boolean  isAfter;
        private       Object   oWithInitVal = null;   // WITHIN initial Value (null when this node is not of type 'future' and 'new AtomicReference(null)' when not initialized yet
        private       Boolean  bResult      = null;   // Updated when Timer ends for AFTER and when a var changed or timer ended for WITHIN.
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
                if( oWithInitVal == null )  bResult = false;       // When the variable's value never arrived, WITHIN has to evaluate to false
                else                        bResult = Objects.equals( value, oWithInitVal );   // The value never changed
            }
        }

        void reset()
        {
            bResult      = null;
            oWithInitVal = null;
        }
    }
}