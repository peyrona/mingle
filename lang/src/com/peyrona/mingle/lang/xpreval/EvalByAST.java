
package com.peyrona.mingle.lang.xpreval;

import com.peyrona.mingle.lang.MingleException;
import com.peyrona.mingle.lang.interfaces.ICandi;
import com.peyrona.mingle.lang.interfaces.ILogger;
import com.peyrona.mingle.lang.japi.UtilStr;
import com.peyrona.mingle.lang.japi.UtilSys;
import com.peyrona.mingle.lang.japi.UtilType;
import com.peyrona.mingle.lang.lexer.CodeError;
import com.peyrona.mingle.lang.lexer.Language;
import com.peyrona.mingle.lang.xpreval.functions.StdXprFns;
import com.peyrona.mingle.lang.xpreval.functions.date;
import com.peyrona.mingle.lang.xpreval.functions.list;
import com.peyrona.mingle.lang.xpreval.functions.pair;
import com.peyrona.mingle.lang.xpreval.functions.time;
import com.peyrona.mingle.lang.xpreval.operators.StdXprOps;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.Stack;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

/**
 * Transforms an infix notated list of tokens into its Expression Tree representation.<br>
 * This class also provides an evaluation process.
 * <p>
 * ASTs are preferred over RPN because AST allows more optimizations.
 * <p>
 * The expression: date(2020,8,30):day() >= 10<br>
 * Is converted into following AST:
 *          >=
 *          /\
 *         :  10
 *        /\
 *    date  day
 *   (args)
 * <p>
 *
 * NOTES:<br>
 * An expression can can have no variables; e.g.: "TRUE || FALSE" or "3 + 4".<br>
 * An expression can be evaluated even if not all its variables have a value:<br>
 * temperature > 20 || light == true<br>
 * Only one of the 2 vars is needed to resolve the expr.<br>
 * <br>
 * Devices trigger a change in their status only when their status effectively changed (obeying delta).<br>
 * There is no need to inform AFTERs about every change: they only check the value when when they end.<br>
 * When a variable is part of an AFTER, there nothing else to do (except what is done by super::set(...)),
 * later, when AFTER delay expires, expr will be attempted to be resolved (if it was not previously cancelled).
 *
 * @author Francisco José Morero Peyrona
 *
 * Official web site at: <a href="https://github.com/peyrona/mingle">https://github.com/peyrona/mingle</a>
 */
final class EvalByAST
{
    public static final short VISIT_PRE_ORDER  = -1;
    public static final short VISIT_IN_ORDER   =  0;
    public static final short VISIT_POST_ORDER =  1;

    private final    Consumer<Object>    onSolved;                               // It is invoked when the expr is solved passing the result (either TRUE or FALSE)
    private final    List<ICandi.IError> lstErrors = new ArrayList<>();          // Used to add items only, never removed
    private final    Map<String,Object>  mapVars   = Collections.synchronizedMap( new HashMap<>() );  // key == deviceName, value == deviceValue (not nneded to be sync)
    private final    MyExecutor          executor;                               // Used for both AFTER and WITHIN.
    private final    ASTNode             root;                                   // The root node of the AST
    private final    boolean             isBoolean;
    private final    boolean             hasWithin;                              // hasAfter is not really needed
    private volatile boolean             hasAllVars;
    private final    int                 nHashCode;

    //------------------------------------------------------------------------//
    // CONSTRUCTOR (package scope)

    /**
     * Constructs de AST to evaluate the received expression.
     *
     * @param lstInfix List of instances of XprToken: returned by XprPreProc::getAsInfix()
     * @param onSolv Can be null.
     */
    EvalByAST( List<XprToken> lstInfix, Consumer<Object> onSolv )    // This method is invoked only if lstInfix is not empty
    {
        onSolved = onSolv;
        root     = infix2AST( lstInfix );

        MyExecutor exec    = null;
        boolean    bWithin = false;

        if( validate( root ) )
        {
            int nFutures = 0;

            for( XprToken xt : lstInfix )
            {
                if( xt.isType( XprToken.VARIABLE ) && ! StdXprFns.isLibraryNamespace( xt.text() ) )
                    mapVars.put( xt.text(), null );
            }

            for( XprToken token : lstInfix )
            {
                if( token.isType( XprToken.RESERVED_WORD ) )
                {
                         if( token.isText( XprUtils.sAFTER  ) )  nFutures++;
                    else if( token.isText( XprUtils.sWITHIN ) )  { nFutures++; bWithin = true; }
                    else throw new MingleException( MingleException.SHOULD_NOT_HAPPEN );
                }
            }

            if( nFutures > 0 )
            {
                if( onSolved == null )
                    lstErrors.add( new CodeError( XprUtils.toString( lstInfix ) +" has AFTER and/or WITHIN but 'onSolved' was not provided.", lstInfix.get(0).line(), -1 ) );

                if( mapVars.isEmpty() )   // When expr has errors, getVars().length == 0
                    lstErrors.add( new CodeError( "The expression has no variables: therefore any AFTER or WITHIN will be useless", lstInfix.get(0).line(), -1 ) );

                if( lstErrors.isEmpty() )
                {
                    try
                    {
                        solveConstantNodes( root );              // Makes some optimizations
                        exec = new MyExecutor( nFutures + 3 );   // +3 to provide some extra margin (most probably it is not needed)
                    }
                    catch( RuntimeException me )
                    {
                        lstErrors.add( new CodeError( me.getMessage(), lstInfix.get(0).line(), -1 ) );
                    }
                }
            }
        }

        String sToString = (root == null) ? "" : toString( root );

        executor           = exec;
        isBoolean          = _isBool_();
        hasAllVars         = mapVars.isEmpty();    // Some expressions does not have variables
        hasWithin          = bWithin;
        nHashCode          = 37 * 3 + sToString.hashCode();

//System.out.println( "Original: " + XprUtils.toString( lstInfix ) );
//System.out.println( "AST:\n" + toString() );
    }

    //------------------------------------------------------------------------//
    // PUBLIC SCOPE

    boolean isBoolean()
    {
        return isBoolean;
    }

    Map<String,Object> getVars()
    {
        return Collections.unmodifiableMap( mapVars );
    }

    List<ICandi.IError> getErrors()
    {
        return lstErrors;    // Can not be unmodifiable
    }

    boolean isFutureing()
    {
        return (executor != null) &&
               (executor.getActiveCount() > 0);
    }

    synchronized boolean set( String name, Object value )
    {
        if( ! mapVars.containsKey( name ) )
            return false;

        assert value != null;    // Do not move

        mapVars.put( name, value );

        if( ! hasAllVars )                              // Once all vars had been initializaed, there is need to check
            hasAllVars = mapVars.values().stream().noneMatch( Objects::isNull );

        if( hasWithin )     // AFTERs are not needed to be evaluated everytime a variable changes its value, but if the expression has
            eval();         // WITHINs they have to be evaluated in case the new value of the variable could resolve the expression.

        return true;
    }

    void shutdown()
    {
        if( executor != null )
            executor.shutdownExecutor();
    }

    synchronized Object eval()
    {
     // This is not needed because it is cheked by NAXE.java -->
     // if( root == null )
     //     throw new MingleException( "Can not eval() an expression with errors" );

        if( (! isBoolean) && (! hasAllVars) )
            return null;        // result is null when some needed vars are not yet initialized, but only if the expression is not of type boolean,
                                // because if the expression is of type boolean, it is needed to attempt to evaluate left and right parts, because
                                // lazy eval: "true || x > 5" can be evaluated, this one: "x > 5 || true" has to be also possible to be evaluated.

        Object result = null;

        if( executor == null )  // Because the expression does not have futures, it can be evaluated now -------------------------------------
        {
            result = _eval_();

            if( (result != null) && (onSolved != null) )            // onSolved is allowed to be null when the expression has no futures
                onSolved.accept( result );

            // It is not needed to do 'root.reset()' because this expr has no futures
        }
        else                    // The expression has futures --------------------------------------------------------------------------------
        {                       // A future always resolves to boolean
            if( ! isFutureing() )
            {
                root.reset();

                // Create timers for nodes that has 'futures' and start the timers

                visitor( root, VISIT_PRE_ORDER, (node) ->     // Order here is not important: -1, 0, 1, will produce same result
                        {
                            if( node.isAfter() || node.isWithin() )
                                EvalByAST.this.executor.execute( new RunFuture( node ) );
                        } );
            }
            else                     // If the eval is already initialized...
            {
                result = _eval_();

                if( result != null )
                {
                    if( executor != null )
                        executor.cancelAllTasks();

                    onSolved.accept( result );
                }
            }
        }

        return result;
    }

    void reset()
    {
        shutdown();

        root.reset();

        // ¡ Do not clear the mapVars !
    }

    @Override
    public int hashCode()
    {
        return nHashCode;
    }

    @Override
    public boolean equals(Object obj)
    {
        if( this == obj )
            return true;

        if( obj == null )
            return false;

        if( getClass() != obj.getClass() )
            return false;

        final EvalByAST other = (EvalByAST) obj;

        return toString().equals( other.toString() );
    }

    @Override
    public String toString()
    {
        return toString( root );
    }

    //------------------------------------------------------------------------//
    // EXTENDED API: NOT INCLUDED IN IEvalCommon INTERFACE

    /**
     * Visits all child nodes of received node in one of following orders:
     * <ul>
     *  <li>order == -1: preorder</li>
     *  <li>order ==  0: in-order </li>
     *  <li>order == -1: postorder</li>
     * </ul>
     *
     * @param node
     * @param order
     * @param consumer This receives all child nodes in specified order.
     */
    static void visitor( ASTNode node, int order, Consumer<ASTNode> consumer )
    {
        if( node == null )
            return;

        switch( order )
        {
            case VISIT_PRE_ORDER:
                consumer.accept( node );
                visitor( node.left() , VISIT_PRE_ORDER, consumer );
                visitor( node.right(), VISIT_PRE_ORDER, consumer );
                break;

            case VISIT_IN_ORDER:
                visitor( node.left() , VISIT_IN_ORDER, consumer );
                consumer.accept( node );
                visitor( node.right(), VISIT_IN_ORDER, consumer );
                break;

            case VISIT_POST_ORDER:
                visitor( node.left() , VISIT_POST_ORDER, consumer );
                visitor( node.right(), VISIT_POST_ORDER, consumer );
                consumer.accept( node );
                break;

            default:
                throw new IllegalStateException();
        }
    }

    //------------------------------------------------------------------------//
    // PRIVATE SCOPE

    private boolean _isBool_()
    {
        if( root == null )
            return false;

        XprToken token    = (root.isAfter() || root.isWithin()) ? root.left().token() : root.token();
        boolean  isMethod = false;

        if( token.isType( XprToken.BOOLEAN ) )
            return true;

        if( token.isType( XprToken.OPERATOR ) )
        {
            if( token.isText( Language.SEND_OP ) )
            {
                isMethod = true;
                ASTNode node = root.right();     // This is a function and will be analized below (in this method)

                if( node != null )
                    token = (node.isAfter() || node.isWithin()) ? node.left().token() : node.token();
            }
            else
            {
                return Language.isBooleanOp( token.text() ) ||
                       Language.isRelationalOp( token.text() );
            }
        }

        if( token.isType( XprToken.OPERATOR_UNARY ) && token.isText( '!' ) )
            return true;

        if( token.isType( XprToken.FUNCTION ) )
        {
            Class c = StdXprFns.getReturnType( token.text(), -1, isMethod );

            return (c == Boolean.class);
        }

        return root.isLeaf() &&                                                            // If the there is only one node in the tree and it is a variable it is because the
               (token.isType( XprToken.VARIABLE ) || token.isType( XprToken.FUNCTION ));   // original expression was like this: 'var == true' what is optimized into: 'var'
    }

    // This toString shows a tree (because to print the original expression, we have NAXE::sOriginal).
    private String toString( ASTNode node )
    {
        node = (node == null) ? root : node;

        if( node == null )
            return "";

        StringBuilder sb = new StringBuilder( 1024 * 4 );

        printTree( sb, node, "", true );

        return sb.toString();
    }

    private boolean validate( ASTNode node )
    {
        return validate( node, 0 );
    }

    private boolean validate( ASTNode node, long currentMaxDelay )
    {
        return validate( node, currentMaxDelay, false, false );
    }

    /**
     * Recursively validates the AST tree.
     *
     * @param node            Current node being validated.
     * @param currentMaxDelay Maximum delay from ancestor AFTER/WITHIN nodes (for nesting check).
     * @param insideAfter     True if an ancestor node is an AFTER.
     * @param insideWithin    True if an ancestor node is a WITHIN.
     * @return True if the subtree is valid.
     */
    private boolean validate( ASTNode node, long currentMaxDelay, boolean insideAfter, boolean insideWithin )
    {
        if( node == null )
            return true;

        boolean bValid = node.validate( lstErrors );
        long    delay  = node.delay();

        if( delay > 0 )
        {
            if( currentMaxDelay > 0 && delay > currentMaxDelay )
            {
                lstErrors.add( new CodeError( "Nested delay (" + delay + ") is bigger than parent (" + currentMaxDelay + ") delay", node.token() ) );
            }

            currentMaxDelay = delay;
        }

        // #10: Detect WITHIN nested inside AFTER (semantically confusing)
        if( node.isAfter() && insideWithin )
            lstErrors.add( new CodeError( "AFTER nested inside WITHIN: the AFTER delay may exceed the enclosing WITHIN window", node.token() ) );

        if( node.isWithin() && insideAfter )
            lstErrors.add( new CodeError( "WITHIN nested inside AFTER: the enclosing AFTER may expire before the WITHIN window completes", node.token() ) );

        // #6: AFTER/WITHIN sub-expression without variables is pointless
        if( node.isAfter() || node.isWithin() )
        {
            if( ! hasVariables( node.left() ) )
                lstErrors.add( new CodeError( node.token().text().toUpperCase() +" sub-expression has no variables: the delay is pointless", node.token() ) );
        }

        // #2: Type compatibility for numeric-only operators with constant operands
        // #3: Division by zero on constants (mod, floor, ceiling)
        bValid &= validateOperatorTypes( node );

        // #4: Method receiver type checking (':' operator)
        bValid &= validateMethodReceiver( node );

        boolean nowInsideAfter  = insideAfter  || node.isAfter();
        boolean nowInsideWithin = insideWithin || node.isWithin();

        // Use bitwise AND to avoid short-circuiting so all nodes are visited/validated
        bValid &= validate( node.left(),  currentMaxDelay, nowInsideAfter, nowInsideWithin );
        bValid &= validate( node.right(), currentMaxDelay, nowInsideAfter, nowInsideWithin );

        return bValid;
    }

    /**
     * Checks if the subtree rooted at the given node contains any VARIABLE token.
     * Traverses left, right, and function arguments recursively.
     */
    private static boolean hasVariables( ASTNode node )
    {
        if( node == null )
            return false;

        if( node.token() != null && node.token().isType( XprToken.VARIABLE ) )
            return true;

        if( node.getFnArgs() != null )
        {
            for( ASTNode arg : node.getFnArgs() )
            {
                if( hasVariables( arg ) )
                    return true;
            }
        }

        return hasVariables( node.left() ) || hasVariables( node.right() );
    }

    // Operators that strictly require numeric operands (no string concat, no truthy/falsy)
    private static final String[] NUMERIC_ONLY_OPS = { "*", "/", "%", "^", "&", "|", "><", ">>", "<<" };

    /**
     * Validates type compatibility between constant operands and operators.
     * <p>
     * Only checks numeric-only operators (*, /, %, ^, bitwise) against constant literal
     * operands that are clearly incompatible: BOOLEAN literals or STRING literals that
     * are not valid numbers.
     * <p>
     * This avoids false positives because:
     * <ul>
     *   <li>Variables have unknown types at parse time — skipped</li>
     *   <li>Functions have return types that are hard to infer in all cases — skipped</li>
     *   <li>String literals like "10" that are valid numbers are allowed (JS-like coercion)</li>
     *   <li>Flexible operators (+, -, ==, &&, etc.) are not checked</li>
     * </ul>
     *
     * @param node The AST node to check.
     * @return True if valid (or not applicable), false if a type error was found.
     */
    private boolean validateOperatorTypes( ASTNode node )
    {
        if( node == null || node.token() == null )
            return true;

        XprToken token = node.token();

        // Check binary numeric-only operators
        if( token.isType( XprToken.OPERATOR ) && isNumericOnlyOp( token.text() ) )
        {
            if( node.left()  != null ) { if( ! checkNumericOperand( node.left().token(),  token ) ) return false; }
            if( node.right() != null ) { if( ! checkNumericOperand( node.right().token(), token ) ) return false; }
        }

        // Check unary numeric operators: '~' (bitwise NOT) and unary minus
        if( token.isType( XprToken.OPERATOR_UNARY ) && (token.isText( '~' ) || token.isText( StdXprOps.sUNARY_MINUS )) )
        {
            if( node.left() != null )
            {
                if( ! checkNumericOperand( node.left().token(), token ) )
                    return false;
            }
        }

        // #3: Functions that throw at runtime when a constant argument is zero
        if( token.isType( XprToken.FUNCTION ) )
        {
            List<ASTNode> fnArgs = node.getFnArgs();

            if( fnArgs != null && fnArgs.size() == 2 )
            {
                // fnArgs are stored in reverse order (see ASTNode.toArrayAndPrepare):
                // first in list = last argument (the second parameter in the function call)
                ASTNode secondArg = fnArgs.get( 0 );

                if( token.isText( "mod" ) && isConstantZero( secondArg ) )
                {
                    lstErrors.add( new CodeError( "mod() divisor is 0 (produces NaN)", secondArg.token() ) );
                    return false;
                }

                if( token.isText( "floor", "ceiling" ) && isConstantZero( secondArg ) )
                {
                    lstErrors.add( new CodeError( token.text() + "() significance cannot be 0", secondArg.token() ) );
                    return false;
                }
            }
        }

        return true;
    }

    /**
     * Checks if a node is a constant numeric zero (literal 0, 0.0, etc.).
     */
    private static boolean isConstantZero( ASTNode node )
    {
        if( node == null || node.token() == null )
            return false;

        XprToken tok = node.token();

        if( tok.isType( XprToken.NUMBER ) )
            return UtilType.toFloat( tok.text() ) == 0f;

        if( tok.isType( XprToken.STRING ) && Language.isNumber( tok.text() ) )
            return UtilType.toFloat( tok.text() ) == 0f;

        return false;
    }

    private static boolean isNumericOnlyOp( String op )
    {
        for( String s : NUMERIC_ONLY_OPS )
            if( s.equals( op ) )
                return true;

        return false;
    }

    /**
     * Checks if a constant operand is compatible with a numeric-only operator.
     *
     * @param operand  The operand token to check.
     * @param operator The operator token (for error message).
     * @return True if compatible or not a constant (cannot determine), false if definitely incompatible.
     */
    private boolean checkNumericOperand( XprToken operand, XprToken operator )
    {
        if( operand == null )
            return true;

        if( operand.isType( XprToken.BOOLEAN ) )
        {
            lstErrors.add( new CodeError( "Operator \"" + operator.text() + "\" requires numeric operands, but found boolean: " + operand.text(), operand ) );
            return false;
        }

        if( operand.isType( XprToken.STRING ) && ! Language.isNumber( operand.text() ) )
        {
            lstErrors.add( new CodeError( "Operator \"" + operator.text() + "\" requires numeric operands, but found string: \"" + operand.text() + "\"", operand ) );
            return false;
        }

        return true;    // NUMBER, VARIABLE, FUNCTION, or numeric string — all fine
    }

    /**
     * Validates that a method called via the ':' operator exists on the receiver type.
     * <p>
     * When the receiver type can be statically determined (extended type constructor or basic literal),
     * the method is checked against the receiver's class:
     * <ul>
     *   <li>Extended types (date, time, list, pair): method must exist on that specific class</li>
     *   <li>Basic type literals (NUMBER, STRING, BOOLEAN): method must exist on StdXprFns (with receiver as first arg)</li>
     * </ul>
     * When the receiver type is unknown (VARIABLE, complex expression), validation is skipped.
     *
     * @param node The AST node to check.
     * @return True if valid (or cannot determine), false if the method definitely does not exist on the receiver type.
     */
    private boolean validateMethodReceiver( ASTNode node )
    {
        if( node == null || node.token() == null )
            return true;

        XprToken token = node.token();

        // Only check ':' operator nodes
        if( ! token.isType( XprToken.OPERATOR ) || ! token.isText( Language.SEND_OP ) )
            return true;

        ASTNode leftNode  = node.left();
        ASTNode rightNode = node.right();

        if( leftNode == null || rightNode == null || rightNode.token() == null )
            return true;

        if( ! rightNode.token().isType( XprToken.FUNCTION ) )
            return true;

        String methodName = rightNode.token().text();
        int    nArgs      = (rightNode.getFnArgs() == null) ? 0 : rightNode.getFnArgs().size();

        // Try to infer the receiver type from the left node
        Class<?> receiverType = inferReceiverType( leftNode );

        if( receiverType == null )
            return true;    // Cannot determine — skip

        if( StdXprFns.isExtendedType( receiverType ) )
        {
            // Method must exist on the specific extended type class
            if( ! hasMethodOnClass( receiverType, methodName, nArgs ) )
            {
                lstErrors.add( new CodeError( "Method \"" + methodName + "\" does not exist on type \"" + receiverType.getSimpleName() + "\"", rightNode.token() ) );
                return false;
            }
        }
        else
        {
            // Basic type (Number, String, Boolean): method is called on StdXprFns with the receiver as first arg
            if( StdXprFns.getFunction( methodName, nArgs + 1 ) == null )
            {
                lstErrors.add( new CodeError( "Function \"" + methodName + "\" cannot be called as a method with " + nArgs + " argument" + (nArgs != 1 ? "s" : ""), rightNode.token() ) );
                return false;
            }
        }

        return true;
    }

    /**
     * Infers the type produced by a node, if it can be determined statically.
     *
     * @return The Class of the value this node produces, or null if unknown.
     */
    private static Class<?> inferReceiverType( ASTNode node )
    {
        if( node == null || node.token() == null )
            return null;

        XprToken token = node.token();

        // Literals
        if( token.isType( XprToken.NUMBER  ) ) return Float.class;
        if( token.isType( XprToken.STRING  ) ) return String.class;
        if( token.isType( XprToken.BOOLEAN ) ) return Boolean.class;

        // Extended type constructors
        if( token.isType( XprToken.FUNCTION ) )
        {
            String fn = token.text().toLowerCase();

            if( "date".equals( fn ) ) return date.class;
            if( "time".equals( fn ) ) return time.class;
            if( "list".equals( fn ) ) return list.class;
            if( "pair".equals( fn ) ) return pair.class;

            // For other functions, try to infer from return type.
            // Object.class means the return type is unknown at compile time (e.g. get()),
            // so we return null to skip method validation rather than wrongly reject it.
            int nArgs = (node.getFnArgs() == null) ? 0 : node.getFnArgs().size();
            Class<?> rt = StdXprFns.getReturnType( token.text(), nArgs );
            return (rt == Object.class) ? null : rt;
        }

        // Chained ':' — infer from the right-side method's return type
        if( token.isType( XprToken.OPERATOR ) && token.isText( Language.SEND_OP ) && node.right() != null )
        {
            XprToken methodToken = node.right().token();

            if( methodToken != null && methodToken.isType( XprToken.FUNCTION ) )
            {
                Class<?> leftType = inferReceiverType( node.left() );

                if( leftType != null )
                {
                    int nArgs   = (node.right().getFnArgs() == null) ? 0 : node.right().getFnArgs().size();
                    boolean isOnExtended = StdXprFns.isExtendedType( leftType );

                    if( isOnExtended )
                    {
                        Class<?> rt = getMethodReturnType( leftType, methodToken.text(), nArgs );
                        return (rt == Object.class) ? null : rt;
                    }
                    else
                    {
                        Class<?> rt = StdXprFns.getReturnType( methodToken.text(), nArgs + 1, false );
                        return (rt == Object.class) ? null : rt;
                    }
                }
            }
        }

        return null;    // VARIABLE or complex expression — cannot determine
    }

    /**
     * Returns the return type of a method on a specific extended type class.
     * Unlike StdXprFns.getReturnType(isMethod=true), this searches only the given class,
     * avoiding ambiguity when multiple extended types have methods with the same name (e.g., del).
     */
    private static Class<?> getMethodReturnType( Class<?> clazz, String methodName, int nArgs )
    {
        for( Method method : clazz.getMethods() )
        {
            if( method.getDeclaringClass().equals( Object.class ) || method.isBridge() )
                continue;

            if( method.getName().equalsIgnoreCase( methodName ) &&
                (method.isVarArgs() || method.getParameterCount() == nArgs) )
            {
                Class<?> c = method.getReturnType();

                     if( c == boolean.class ) return Boolean.class;
                else if( c == int.class     ) return Integer.class;
                else if( c == float.class   ) return Float.class;

                return c;
            }
        }

        return null;
    }

    /**
     * Checks if a public method with the given name and parameter count exists on a specific class.
     */
    private static boolean hasMethodOnClass( Class<?> clazz, String methodName, int nArgs )
    {
        for( Method method : clazz.getMethods() )
        {
            if( method.getDeclaringClass().equals( Object.class ) )
                continue;

            if( method.getName().equalsIgnoreCase( methodName ) )
            {
                if( method.isVarArgs() || method.getParameterCount() == nArgs )
                    return true;
            }
        }

        return false;
    }

    /**
     * Transform an Infix into a PostFix and a Postfix into an AST.<br>
     * <br>
     * NOTE: Handling operator precedence and associativity can be more challenging when
     * parsing infix expressions directly into an AST. You need to implement more complex
     * logic to determine the correct tree structure. In this case it is better to prior
     * pass to PostFix (RPN).
     *
     * @param lstInfix Expression tokens in Infix mode.
     * @return Tree's root node.
     */
    private ASTNode infix2AST( List<XprToken> lstInfix )
    {
        // RPN handles structural validations, and AST.java handles semantic validation.
        // The two-phase approach matches Mingle's "clarity over speed" principle and performance cost is negligible.
        RPN rpn = new RPN( lstInfix );

        lstErrors.addAll( rpn.getErrors() );

        if( ! lstErrors.isEmpty() )
            return null;

        List<XprToken> lstRPN = rpn.getPostFix();
        Stack<ASTNode> stack  = new Stack<>();

        for( int n = 0; n < lstRPN.size(); n++ )
        {
            XprToken token = lstRPN.get( n );

            switch( token.type() )
            {
                case XprToken.BOOLEAN:
                case XprToken.NUMBER:
                case XprToken.STRING:
                case XprToken.VARIABLE:
                    stack.add( new ASTNode().token( token ) );

                    if( token.isType( XprToken.VARIABLE ) && ! StdXprFns.isLibraryNamespace( token.text() ) )
                        mapVars.put( token.text(), null );
                    break;

                case XprToken.PARENTH_OPEN:                        // Next tokens (until function name arrives) are the function parameters
                    stack.add( new ASTNode().token( token ) );     // Places the '(' as a mark in the stack to know later how many params the func has
                    break;

                case XprToken.FUNCTION:                            // XprTokenier checks that this function or method really exists
                    ASTNode nodeFn = new ASTNode().token( token );

                    while( (! stack.isEmpty()) && stack.peek().token().isNotType( XprToken.PARENTH_OPEN ) )   // There are funcs with 0 args
                        nodeFn.addFnArg( stack.pop() );

                    if( stack.isEmpty() )
                    {
                        lstErrors.add( new CodeError( "Missing '(' for function: " + token.text(), token ) );
                        return null;
                    }

                    stack.pop();           // pops the mark node '('
                    stack.add( nodeFn );
                    break;

                case XprToken.OPERATOR_UNARY:
                    if( stack.isEmpty() )
                    {
                        lstErrors.add( new CodeError( "Missing operand for unary operator: " + token.text(), token ) );
                        return null;
                    }

                    stack.add( new ASTNode().token( token ).left( stack.pop() ) );
                    break;

                case XprToken.RESERVED_WORD:            // Only AFTER and WITHIN can be part of an expression
                    if( stack.size() < 2 )
                    {
                        lstErrors.add( new CodeError( token.text() + " requires an expression and a delay value", token ) );
                        return null;
                    }

                    ASTNode nodeDelay = stack.pop();    // Previous token in RPN is the delay amount
                    ASTNode nodeExpr  = stack.pop();    // Previous-previous token must be a boolean expression

                    // Valid expressions for AFTER/WITHIN:
                    // - Boolean/relational operators (e.g., "a == 5", "a && b")
                    // - Bare variables (treated as truthy/falsy, e.g., "flag AFTER 1000")
                    // - Boolean literals (true/false)
                    // - Functions (e.g., "isOpen() AFTER 1000")
                    // - Unary operators (e.g., "!flag AFTER 1000")
                    if( (! Language.isBooleanOp(    nodeExpr.token().text() )) &&
                        (! Language.isRelationalOp( nodeExpr.token().text() )) &&
                        (! nodeExpr.token().isType( XprToken.VARIABLE )) &&
                        (! nodeExpr.token().isType( XprToken.BOOLEAN )) &&
                        (! nodeExpr.token().isType( XprToken.FUNCTION )) &&
                        (! nodeExpr.token().isType( XprToken.OPERATOR_UNARY )) )
                    {
                        lstErrors.add( new CodeError( "AFTER and WITHIN work with booleans, not with: "+ nodeExpr.token().text(), nodeExpr.token() ) );
                    }
                    if( nodeDelay.token().isNotType( XprToken.NUMBER ) )
                    {
                        lstErrors.add( new CodeError( "Number not found after modifier \""+ token.text() +'"', nodeDelay.token() ) );
                    }
                    else if( nodeDelay.token().text().indexOf( '.' ) > -1 )
                    {
                        lstErrors.add( new CodeError( "Integer expected, but found decimal: "+ nodeDelay.token().text(), nodeExpr.token() ) );
                    }
                    else if( UtilType.toInteger( nodeDelay.token().text() ) <= 0 )
                    {
                        lstErrors.add( new CodeError( "AFTER and WITHIN delay must be x > 0", nodeDelay.token() ) );
                    }

                    stack.add( new ASTNode().token( token ).left( nodeExpr ).right( nodeDelay ) );
                    break;

                default:
                    if( stack.size() < 2 )
                    {
                        lstErrors.add( new CodeError( "Missing operand(s) for operator: " + token.text(), token ) );
                        return null;
                    }

                    ASTNode right = stack.pop();
                    ASTNode left  = stack.pop();
                    stack.add( new ASTNode().token( token ).left( left ).right( right ) );
            }
        }

        if( stack.size() != 1 )
            lstErrors.add( new CodeError( "Invalid expression", lstRPN.get( 0 ) ) );

        if( ! lstErrors.isEmpty() )
            return null;

        return stack.pop();     // The only expression node that remains in the stack is root node
    }

    /**
     * Optimizes by solving constants:
     * <ol>
     *    <li>"12*3" to "36"</li>
     *    <li>"true && false" to "false"</li>
     * </ol>
     *
     * @param lstTokens
     */
    private void solveConstantNodes( ASTNode node )     // This method makes BasicsToProfile.java 10 times faster for only contants expressions
    {
        if( ! lstErrors.isEmpty() )
            return;

        visitor( node,
                 VISIT_PRE_ORDER,     // Order is important
                 (ASTNode child) ->
                    {
                        Object value = null;

                        // Check for binary operator with constant operands
                        if( child.token().isType( XprToken.OPERATOR ) &&
                            child.left()  != null && child.left().token()  != null &&
                            child.right() != null && child.right().token() != null &&
                            child.left().token().isType(  XprToken.BOOLEAN, XprToken.NUMBER, XprToken.STRING ) &&
                            child.right().token().isType( XprToken.BOOLEAN, XprToken.NUMBER, XprToken.STRING ) )
                        {
                            value = child.eval( mapVars, hasAllVars );
                        }
                        // Check for unary operator with constant operand
                        else if( child.token().isType( XprToken.OPERATOR_UNARY ) &&
                                 child.left() != null && child.left().token() != null &&
                                 child.left().token().isType( XprToken.BOOLEAN, XprToken.NUMBER, XprToken.STRING ) )
                        {
                            value = child.eval( mapVars, true );   // mapVars is useless because node is made up of constants, but it is requested by the method
                        }

                        if( value != null )
                        {
                            short type = (value instanceof Boolean) ? XprToken.BOOLEAN :
                                         (value instanceof String ) ? XprToken.STRING  :
                                                                      XprToken.NUMBER;

                            child.token( new XprToken( child.token(), value.toString(), type ) );
                            child.beLeaf();

                            solveConstantNodes( child.parent() );     // Recursive calling until the branch is reduced
                        }
                    } );
    }

    private void printTree( StringBuilder sb, ASTNode node, String prefix, boolean isLeft )
    {
        if( node != null )
        {
            sb.append( prefix ).append( (isLeft ? "├── " : "└── ") ).append( node.toText() ).append( UtilStr.sEoL );

            printTree( sb, node.left() , prefix + (isLeft ? "│   " : "    "), true  );
            printTree( sb, node.right(), prefix + (isLeft ? "│   " : "    "), false );
        }
    }

    //------------------------------------------------------------------------//
    // INNER CLASS
    //------------------------------------------------------------------------//

    /**
 * Custom ThreadPoolExecutor that tracks running threads and provides
 * cancellation capabilities for all active tasks.
 */
    private final class MyExecutor extends ThreadPoolExecutor
    {
        private final          Set<Thread>   runningThreads = ConcurrentHashMap.newKeySet();
        private final          AtomicBoolean isShuttingDown = new AtomicBoolean( false );

        MyExecutor( int poolSize )
        {
            super( poolSize,
                   poolSize,
                   0L,
                   TimeUnit.MILLISECONDS,
                   new LinkedBlockingQueue<>(),
                   new ThreadFactory()            // Used to give names to thes set of threads
                        {
                            private final AtomicInteger counter = new AtomicInteger( 1 );

                            @Override
                            public Thread newThread( Runnable r )
                            {
                                Thread t = new Thread( r, "XprEval-MyExecutor-" + counter.getAndIncrement() );
                                t.setDaemon( false );
                                return t;
                            }
                        } );
        }

        @Override
        protected void beforeExecute( Thread t, Runnable r )
        {
            super.beforeExecute( t, r );

            if( ! isShuttingDown.get() )
                runningThreads.add( t );
        }

        @Override
        protected void afterExecute( Runnable r, Throwable t )
        {
            try
            {
                super.afterExecute( r, t );

                if( t != null )    // Log the exception or handle it appropriately
                {
                    String  msg    = "Task execution failed: " + t.getMessage();
                    ILogger logger = UtilSys.getLogger();

                    if(  logger == null )  System.err.println( UtilStr.toString( t ) );
                    else                   logger.log( ILogger.Level.ERROR, msg );
                }
            }
            finally
            {
                runningThreads.remove( Thread.currentThread() );
            }
        }

        @Override
        protected void terminated()
        {
            super.terminated();
            runningThreads.clear();
        }

        void cancelAllTasks()
        {
            if( isShuttingDown.get() )
                return;    // Already shutting down

            // Create a snapshot of currently running threads
            Set<Thread> threadsToInterrupt = new HashSet<>( runningThreads );

            // Clear the queue first to prevent new tasks from starting
            getQueue().clear();

            // Interrupt all running threads
            for( Thread thread : threadsToInterrupt )
            {
                if( thread != null && thread.isAlive() )
                    thread.interrupt();
            }
        }

        /**
         * Cancels all running tasks and initiates shutdown. This method is thread-safe and can be called multiple times.
         */
        void shutdownExecutor()
        {
            if( ! isShuttingDown.compareAndSet( false, true ) )
                return;    // Already shutting down

            // Create a snapshot of currently running threads
            Set<Thread> threadsToInterrupt = new HashSet<>( runningThreads );

            // Clear the queue first to prevent new tasks from starting
            getQueue().clear();

            // Interrupt all running threads
            for( Thread thread : threadsToInterrupt )
            {
                if( thread != null && thread.isAlive() )
                    thread.interrupt();
            }

            shutdown();    // Initiate orderly shutdown

            // Wait for termination with timeout

            try
            {
                if( ! awaitTermination( 2, TimeUnit.SECONDS ) )
                {
                    shutdownNow();    // Force shutdown if tasks don't complete

                    if( ! awaitTermination( 3, TimeUnit.SECONDS ) )
                    {
                        String  msg    = "Executor did not terminate gracefully";
                        ILogger logger = UtilSys.getLogger();

                        if( logger == null )  System.err.println( msg );
                        else                  logger.log( ILogger.Level.WARNING, msg );
                    }
                }
            }
            catch( InterruptedException ie )
            {
                shutdownNow();
                Thread.currentThread().interrupt();
            }
        }
    }

    private Object _eval_()
    {
        if( root == null )
            throw new MingleException( "Cannot evaluate expression: AST root is null because expression has errors" );

        Object result = root.eval( mapVars, hasAllVars );

        // Convert to boolean if expression is boolean type and result isn't already Boolean
        if( isBoolean && (result != null) && ! (result instanceof Boolean) )
            result = UtilType.isTruthy( result );

        return result;
    }

    //------------------------------------------------------------------------//
    // INNER CLASS
    //------------------------------------------------------------------------//

    /**
     * Runnable wrapper that handles AST node execution with proper interruption handling and error management.
     */
    private final class RunFuture implements Runnable
    {
        private final ASTNode node;

        RunFuture( final ASTNode node )
        {
            this.node = node;
        }

        @Override
        public void run()
        {
            try
            {
                Thread.sleep( node.delay() );

                // Synchronized on EvalByAST.this to be mutually exclusive with set() and eval().
                // This prevents the race between Future.expired() (called by node.expired()) and
                // Future.apply() (called during eval()), and ensures consistent reads of hasAllVars
                // and mapVars. The lock is reentrant, so the subsequent eval() call is safe.
                synchronized( EvalByAST.this )
                {
                    node.expired( mapVars, hasAllVars );
                }

                EvalByAST.this.eval();
            }
            catch( InterruptedException ie )
            {
                Thread.currentThread().interrupt();    // Thread was interrupted, so, restore interrupt status and exit gracefully
            }
            catch( Exception e )
            {
                ILogger logger = UtilSys.getLogger();

                if(  logger == null )  System.err.println( UtilStr.toString( e ) );
                else                   logger.log( ILogger.Level.ERROR, e );
            }
        }
    }
}