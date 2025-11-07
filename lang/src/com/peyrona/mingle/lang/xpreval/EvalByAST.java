
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
import com.peyrona.mingle.lang.xpreval.operators.StdXprOps;
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

    private final Consumer<Object>    onSolved;                       // It is invoked when the expr is solved passing the result (either TRUE or FALSE)
    private final StdXprOps           operators = new StdXprOps();
    private final StdXprFns           functions = new StdXprFns();
    private final List<ICandi.IError> lstErrors = new ArrayList<>();  // Used to add items only, never removed
    private final Map<String,Object>  mapVars   = new HashMap<>();    // key == deviceName, value == deviceValue (not nneded to be sync)
    private       boolean             hasAllVars;
    private final boolean             hasWithin;                      // hasAFTER is not really needed

    private final MyExecutor executor;    // Used for both AFTER and WITHIN.
    private final ASTNode    root;        // The fucking root node
    private final boolean    isBoolean;
    private final boolean    hasChangedDeviceFn;

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
                if( xt.isType( XprToken.VARIABLE ) )
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

        boolean bHasChgDevFn = false;

        if( lstErrors.isEmpty() )
        {
            for( XprToken token : lstInfix )
            {
                if( token.isType( XprToken.FUNCTION ) && token.isText( "getTriggeredBy" ) )
                {
                    bHasChgDevFn = true;
                    break;
                }
            }
        }

        executor           = exec;
        isBoolean          = _isBool_();
        hasAllVars         = mapVars.isEmpty();    // Some expressions does not have variables
        hasWithin          = bWithin;
        hasChangedDeviceFn = bHasChgDevFn;

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

    boolean set( String name, Object value )
    {
        if( hasChangedDeviceFn )                        // If true, then the expression has a call to getChangedDevice() as part of it.
            functions.setTriggeredBy( name, value );    // In this case, even if the expression has no vars, we have to call this func because
                                                        // Action::trigger(...) calls here to set the device name & value that triggered the rule.
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

    Object eval()
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
            result = root.eval( operators, functions, mapVars, hasAllVars );

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
                result = root.eval( operators, functions, mapVars, hasAllVars );

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
        int hash = 3;
            hash = 37 * hash + toString().hashCode();

        return hash;
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
        return (root == null) ? "" : toString( root );
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

        XprToken token = (root.isAfter() || root.isWithin()) ? root.left().token() : root.token();

        if( token.isType( XprToken.BOOLEAN ) )
            return true;

        if( token.isType( XprToken.OPERATOR ) )
        {
            if( token.isText( Language.SEND_OP ) )
            {
                ASTNode node = root.right();     // This is a function and will be analized below (in this method)
                token = (node.isAfter() || node.isWithin()) ? node.left().token() : node.token();
            }
            else
            {
                return Language.isBooleanOp( token.text() ) ||
                       Language.isRelationalOp( token.text() );
            }
        }

        if( token.isType( XprToken.OPERATOR_UNARY ) )
            return true;

        if( token.isType( XprToken.FUNCTION ) )
        {
            Class c = StdXprFns.getReturnType( token.text(), -1 );

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
        if( node == null )
            return false;

        final AtomicBoolean bRet       = new AtomicBoolean( true );
        final Stack<Long>   delayStack = new Stack<>();

        // Ckeck that a nested delay is not bigger than its parent delay -----------------------------
        visitor( node,
                VISIT_POST_ORDER,     // Visit childs first
                (ASTNode child) ->
                {
                    bRet.set( child.validate( lstErrors ) && bRet.get() );

                    if( child.delay() > 0 )
                    {
                        long currentMax = delayStack.isEmpty() ? 0 : delayStack.peek();

                        if( currentMax > 0 && child.delay() > currentMax )
                        {
                            lstErrors.add( new CodeError( "Nested delay (" + child.delay() + ") is bigger than parent (" + currentMax + ") delay", child.token() ) );
                        }

                        delayStack.push( child.delay() );
                    }
                    else if( !delayStack.isEmpty() && delayStack.peek() > 0 )
                    {
                        // Pop when we've finished a subtree that had a delay
                        delayStack.pop();
                    }
                } );

        return bRet.get();
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

                    if( token.isType( XprToken.VARIABLE ) )
                        mapVars.put( token.text(), null );
                    break;

                case XprToken.PARENTH_OPEN:                        // Next tokens (until function name arrives) are the function parameters
                    stack.add( new ASTNode().token( token ) );     // Places the '(' as a mark in the stack to know later how many params the func has
                    break;

                case XprToken.FUNCTION:                            // XprTokenier checks that this function or method really exists
                    ASTNode nodeFn = new ASTNode().token( token );

                    while( (! stack.isEmpty()) && stack.peek().token().isNotType( XprToken.PARENTH_OPEN ) )   // There are funcs with 0 args
                        nodeFn.addFnArg( stack.pop() );

                    stack.pop();           // pops the mark node '('
                    stack.add( nodeFn );
                    break;

                case XprToken.OPERATOR_UNARY:
                    stack.add( new ASTNode().token( token ).left( stack.pop() ) );
                    break;

                case XprToken.RESERVED_WORD:            // Only AFTER and WITHIN can be part of an expression
                    ASTNode nodeDelay = stack.pop();    // Previous token in RPN is the delay amount
                    ASTNode nodeExpr  = stack.pop();    // Previous-previous token must be the Boolean or Relational operator

                    if( (! Language.isBooleanOp(    nodeExpr.token().text() )) &&
                        (! Language.isRelationalOp( nodeExpr.token().text() )) )
                    {
                        lstErrors.add( new CodeError( "AFTER and WITHIN work with booleans, not with: "+ nodeExpr.token().text(), nodeExpr.token() ) );
                    }
// TODO:
// WHEN clock >= 0
// THEN console = "Tested simple AFTER and WITHIN"
// IF (cell_1 == 3 AFTER 5s) && (cell_2 == 0 WITHIN 2s)  -->  así sí funciona, pero si le quito los paréntesis al IF, no funciona. Da este error -->
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

// NEXT:
    // Morgan's Laws: used to simplify boolean expressions.
    // !(A && B) --> !A || !B
    // !(A || B) --> !A && !B

// NEXT: resolver los nodos con operador unario '-' cuando se aplica a un número (constante: -40 * 12). (Ojo, no es fácil)
// NEXT: resolver los nodos "lo_que_sea == false" (cuidado pq pueden ser cosas como: "IIF( time():hour() > 12, get( var_1 ), get( var_2 ) ) == false")

        visitor( node,
                 VISIT_PRE_ORDER,     // Order is important
                 (ASTNode child) ->
                    {
                        Object value = null;

                        if( child.token().isType( XprToken.OPERATOR )                                          &&
                            child.left( ).token().isType( XprToken.BOOLEAN, XprToken.NUMBER, XprToken.STRING ) &&
                            child.right().token().isType( XprToken.BOOLEAN, XprToken.NUMBER, XprToken.STRING ) )
                        {
                            value = child.eval( operators, functions, mapVars, hasAllVars );
                        }
                        else if( child.token().isType( XprToken.OPERATOR_UNARY ) &&
                                 child.left().token().isType( XprToken.BOOLEAN, XprToken.NUMBER, XprToken.STRING ) )
                        {
                            value = child.eval( operators, functions, mapVars, true );   // mapVars is useless because node is made up of constants, but it is requested by the method
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
        private final          Set<Thread> runningThreads = ConcurrentHashMap.newKeySet();
        private       volatile boolean     isShuttingDown = false;

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

            if( ! isShuttingDown )
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
                    else                   logger.log( ILogger.Level.INFO, msg );
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
            if( isShuttingDown )
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
            if( isShuttingDown )
                return;    // Already shutting down

            isShuttingDown = true;

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
                        else                  logger.log( ILogger.Level.INFO, msg );
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

                node.expired( operators, functions, mapVars, hasAllVars );
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
                else                   logger.log( ILogger.Level.INFO, e );
            }
        }
    }
}