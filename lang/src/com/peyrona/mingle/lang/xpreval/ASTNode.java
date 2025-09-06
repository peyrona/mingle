
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
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * This class instances are the nodes used to construct an AST tree that represents an
 * expression. The expression is evaluated by traversing the tree.
 * <p>
 * There's no explicit specialization (subclasses) for leaf nodes. Leaves are denoted by
 * nodes where both the left and right node is null.
 *
 * @author Francisco José Morero Peyrona
 *
 * Official web site at: <a href="https://mingle.peyrona.com">https://mingle.peyrona.com</a>
 */
final class ASTNode
{
    private volatile ASTNode       left         = null;   // Left child
    private volatile ASTNode       right        = null;   // Right child
    private volatile ASTNode       parent       = null;   // ReadOnly property
    private          XprToken      token        = null;   // Parent node
    private          List<ASTNode> lstFnArgs    = null;   // Function arguments (AKA parameters)
    private          AtomicBoolean bAfterEnded  = null;   // Flag indicating that AFTER operation is already ended
    private          AtomicBoolean bWithinEnded = null;   // Flag indicating that WITHIN operation is already ended
    private          AtomicInteger bWitIniVal   = null;   // WITHIN initial Value (null when this node is not of type 'future' (0 false, 1 true, -1 not inited yet)
    private          AtomicInteger bFutureEnded = null;   // Updated when Timer ends for AFTER and when a var changed or timer ended for WITHIN.
                                                          // After ended, it has the furure result (futures are always booleans: 1 for true and 0 for false).
                                                          // If null, this Node is not a node of type 'future'. If -1 it is a 'future' but it is not resolved yet.

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
    synchronized ASTNode left( ASTNode node )
    {
        assert node != null;

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
    synchronized ASTNode right( ASTNode node )
    {
        assert node != null;

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
        {
            bAfterEnded  = token.isText( XprUtils.sAFTER  ) ? new AtomicBoolean( false ) : null;
            bWithinEnded = token.isText( XprUtils.sWITHIN ) ? new AtomicBoolean( false ) : null;
            bWitIniVal   = token.isText( XprUtils.sWITHIN ) ? new AtomicInteger( -1    ) : null;
            bFutureEnded = new AtomicInteger( -1 );
        }
        else
        {
            bAfterEnded  = null;
            bWithinEnded = null;
            bWitIniVal   = null;
            bFutureEnded = null;
        }

        return this;
    }

    synchronized ASTNode addFnArg( ASTNode node )
    {
        if( lstFnArgs == null )
            lstFnArgs = new ArrayList<>();

        lstFnArgs.add( node );

        return this;
    }

    int delay()
    {
        return token.isNotType( XprToken.RESERVED_WORD ) ? 0    // RESERVED_WORD -> can only be "after" or "within" (in lower case)
                                                         : UtilType.toInteger( right.token.value() );
    }

// NOT USED
//    boolean isRoot()
//    {
//        return parent == null;
//    }

    boolean isAfter()
    {
        return bAfterEnded != null;
    }

    boolean isWithin()
    {
        return bWithinEnded != null;
    }

    void end()
    {
        if( isAfter()  )  bAfterEnded.set(  true );
        else              bWithinEnded.set( true );
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

        assert vars != null;

        if( bFutureEnded != null && bFutureEnded.get() != -1 )    // This branch (starting at this node) was a future and the future was already resolved.
            return bFutureEnded.get() == 1;                       // The future was resolved to true or false (futures always resolve to a boolean value).

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
                        Object[] aoArgs = prepend( left.eval( ops, fns, vars, hasAllVars ),
                                                   right.params2Array( ops, fns, vars, hasAllVars ) );

//                        Object   target = left.eval( ops, fns, vars, hasAllVars );
//                        Object[] aoArgs = right.params2Array( ops, fns, vars, hasAllVars );

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
                    return ops.eval( token.text(),
                                     left.eval(  ops, fns, vars, hasAllVars ),
                                     right.eval( ops, fns, vars, hasAllVars ) );
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
                    return fns.invoke( token.text(), params2Array( ops, fns, vars, hasAllVars ) );

                break;

            case XprToken.RESERVED_WORD:
                if( ! hasAllVars )
                    break;

                if( isAfter() && bAfterEnded.get() )
                {
                    Object result = left.eval( ops, fns, vars, hasAllVars );

                    boolean bResult = ((result == null) ? false : (Boolean) result);

                    bFutureEnded.set( (bResult ? 1 : 0) );

                    return result;
                }
                else if( isWithin() )    // Needed to be checked (take a look to previous if)
                {
                    Object result = left.eval( ops, fns, vars, hasAllVars );

                    if( bWithinEnded.get() )     // WITHIN is finished
                    {
                        boolean bResult = (bWitIniVal.get() == -1) ? false     // If bWitIniVal == -1 --> we never knew the initial value
                                                                   : Objects.equals( result, (bWitIniVal.get() == 1) );
                        bFutureEnded.set( (bResult ? 1 : 0) );

                        return result;
                    }
                    else
                    {
                        if( bWitIniVal.get() == -1 )    // Not initialized yet
                        {
                            bWitIniVal.set( ((Boolean) result ? 1 : 0) );
                        }
                        else
                        {
                            if( ! Objects.equals( result, bWitIniVal ) )    // Has changed
                            {
                                bFutureEnded.set( 0 );   // 0 == false
                                return false;
                            }

                            return null;    // null beause it is still working
                        }
                    }
                }
                else
                {
                    throw new MingleException( MingleException.SHOULD_NOT_HAPPEN );
                }

                break;

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

        if( token.isType( XprToken.RESERVED_WORD ) )
        {
            if( bAfterEnded  != null )  bAfterEnded.set(  false );    // Is null when node is of type WITHIN
            if( bWithinEnded != null )  bWithinEnded.set( false );    // Is null when node is of type AFTER
            if( bWitIniVal   != null )  bWitIniVal.set( -1 );         // Is null when node is of type AFTER
            bFutureEnded.set( -1 );                                   // Will always exist when AFTER or WITHIN
        }
        else
        {
            synchronized( this )
            {
                bAfterEnded  = null;
                bWithinEnded = null;
                bWitIniVal   = null;
                bFutureEnded = null;
            }
        }

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

    private Object[] params2Array( StdXprOps ops, StdXprFns fns, Map<String,Object> vars, boolean hasAllVars )   // vars is never null, checked at ::eval(...)
    {
        if( lstFnArgs == null )
            return null;

        int size = lstFnArgs.size();    // No need to sync on lstFnArgs beacuse once created it is inmutable

        if( size == 0 )
            return null;

        if( args.length != size )
            args = new Object[size];
     // else --> when the size is the same as the last call, we reuse 'args'

        int len = size;
        size--;

        for( int n = 0; n < len; n++ )
            args[size-n] = lstFnArgs.get( n ).eval( ops, fns, vars, hasAllVars );    // Items in lstFnArgs are in reverse order

        return args;
    }

    // Inserts 'item' as first element of 'array'
    private Object[] prepend( Object item, Object[] array )
    {
        if( array == null || array.length == 0 )
            return new Object[] { item };

        Object[] toRet = new Object[ array.length + 1 ];
                 toRet[0] = item;

        System.arraycopy( array, 0, toRet, 1, array.length );

        return toRet;
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
            //sb.append( nod.token.original() ).append( ',' );

        return UtilStr.removeLast( sb, 1 ).append( ')' ).toString();
    }
}