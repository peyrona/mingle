
package com.peyrona.mingle.cil.rules;

import com.peyrona.mingle.cil.Command;
import com.peyrona.mingle.lang.interfaces.ICandi;
import com.peyrona.mingle.lang.interfaces.ILogger;
import com.peyrona.mingle.lang.interfaces.IXprEval;
import com.peyrona.mingle.lang.interfaces.commands.IRule;
import com.peyrona.mingle.lang.interfaces.exen.IRuntime;
import com.peyrona.mingle.lang.japi.Dispatcher;
import com.peyrona.mingle.lang.japi.UtilColls;
import com.peyrona.mingle.lang.japi.UtilStr;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * RULE commands provide the logic to the language.
 * <p>
 * Rules can be dynamically added and removed: this is something that has to be done carefully:
 * adding dynamically a rule does not passes all the "controls" that are passed by the
 * transpiler; e.g.: time units can not be used: time has to be expressed in milliseconds.
 * <p>
 * On the other hand, adding devices that belongs to an existing group will not affect to
 * Rules (these changes will be ignored by rules) that were defined as using this group. And
 * removing a device that is used by a Rule will eventually end with an exception. In other
 * words: groups are not dynamically (on-the-fly) updated.
 * <br>
 * Developers that add Rules at runtime do it at their own risk (all checks are done
 * at transpiler time): a big power comes with a big responsibility.
 *
 * @author Francisco José Morero Peyrona
 *
 * Official web site at: <a href="https://github.com/peyrona/mingle">https://github.com/peyrona/mingle</a>
 */
public final class      Rule
             extends    Command
             implements IRule
{
    // Developers that add Rules at runtime do it at their own risk because all
    // checks are done at transpiled time: a big power comes with a big responsability.

    private final List<IAction> then;     // Original actions (before expanding device's groups if any). Used to serialize Actions.
    private       IXprEval      when;     // Compiled sWhen: null when sWhen has errors
    private       IXprEval      _if_;     // Compiled sIf: null when sIf has errors or it was not provided
    private       String        sWhen;    // To be used temporarely
    private       String        sIf;      // To be used temporarely

    private final Dispatcher<Object[]> onDeviceChangedTask;    // Using Object[] for speed (instead of using Pair<>())


    //------------------------------------------------------------------------//
    // PACKAGE SCOPE CONSTRUCTOR

    Rule( String sWhen, String sIf, Action[] actions, String ruleName )     // A RULE can be created from an SCRIPT and can be done on-the-fly
    {
        super( ruleName );

        this.sWhen = sWhen;
        this.sIf   = sIf;
        this.then  = UtilColls.toList( (Object[]) actions );

        Consumer<Object[]> consumer = (change) ->
            {
                IXprEval xpr = ((_if_ != null && _if_.isFutureing()) ? _if_ : when);

                xpr.eval( (String) change[0],    // Device name
                                   change[1] );  // Device value
            };

        onDeviceChangedTask = new Dispatcher<>( consumer,
                                                (exc) -> getRuntime().log( ILogger.Level.SEVERE, exc ),
                                                name() );
    }

    //------------------------------------------------------------------------//

    @Override
    public void start( IRuntime rt )     // After creating a RULE on-the-fly, ::start(...) has to be invoked
    {
        if( isStarted() )
            return;

        super.start( rt );

        // Reminder: WHEN must not include 'futures' and IF must include 'futures'

        IXprEval xprIf = null;

        if( sIf != null )
        {
            Consumer<Object> onSolved = (result) -> {
                                                        if( canTrigger( result, "IF" ) )
                                                            _trigger_();

                                                        if( result != null )
                                                            logVarChanged();
                                                    };

            xprIf = getRuntime().newXprEval().build( sIf, onSolved, rt.newGroupWiseFn() );
        }

        Consumer<Object> onSolved = (result) -> {
                                                    if( canTrigger( result, "WHEN" ) )
                                                    {
                                                        if( _if_ == null )  _trigger_();
                                                        else                _if_.eval();
                                                    }

                                                    if( result != null )
                                                        logVarChanged();
                                                };

        synchronized( this )
        {
            when = getRuntime().newXprEval().build( sWhen, onSolved, rt.newGroupWiseFn() );
            when = checkXprEval( when , "WHEN" );
            _if_ = checkXprEval( xprIf, "IF"   );

            sWhen = sIf = null;    // Saves RAM (not needed any more)
        }

        boolean isValid = (when != null) && (xprIf == null || _if_ != null);

        if( isValid )
        {
            for( IAction action : then )    // Traverse the originals
                ((Action) action).setRuntime( rt );

            onDeviceChangedTask.start();
        }
    }

    @Override
    public void stop()
    {
        if( ! isStarted() )
            return;

        onDeviceChangedTask.stop();

        if( when != null )
            when.cancel();     // No harm if already cancelled

        if( _if_ != null )
            _if_.cancel();     // No harm if already cancelled

        synchronized( this )
        {
            when = null;
            _if_ = null;
            then.clear();
        }

        super.stop();
    }

    @Override
    public IRule eval( String devName, Object devValue )
    {
        if( when != null )                                                    // when == null -> rule has errors (remember: rules can be added on the fly)
            onDeviceChangedTask.add( new Object[] { devName, devValue } );    // Need to create a new reference to put it into the queue

        return this;
    }

    @Override
    public IRule trigger( boolean bForce )     // To be used by another RULE or by an SCRIPT
    {
        if( when == null )
        {
            getRuntime().log( ILogger.Level.SEVERE, "RULE "+ name() +" can not be triggered because it has errors" );
            return this;
        }

        if( bForce )
        {
            _trigger_();
        }
        else
        {
            if( _if_ != null )
            {
                getRuntime().log( ILogger.Level.SEVERE, "RULE "+ name() +" can not be triggered because it has an IF clause" );   // Can not trigger a Rule that has an IF clause
                return this;
            }

            Object result = when.eval();

            if( (result != null) && (result instanceof Boolean) && ((Boolean) result) )
                _trigger_();
        }

        return this;
    }

    @Override
    public String getWhen()
    {
        return (when == null) ? sWhen : when.toString();
    }

    @Override
    public String getIf()
    {
        return (_if_ == null) ? sIf : _if_.toString();
    }

    @Override
    public IAction[] getActions()
    {
        return then.toArray( IAction[]::new );
    }

    @Override
    public IAction addAction( long delay, String targetName, Object valueToSet )
    {
        Action action = new Action( delay, targetName, valueToSet );

        synchronized( then )
        {
            if( ! then.contains( action ) )
            {
                then.add( action );
                action.setRuntime( getRuntime() );
                return action;
            }
        }

        return null;
    }

    @Override
    public synchronized boolean removeAction( IAction action )
    {
        return then.remove( action );
    }

    //------------------------------------------------------------------------//
    // PRIVATE SCOPE

    // It does not need to be inside a thread because the Rule itself is evaluated inside a thread
    private void _trigger_()
    {
        boolean bLog = getRuntime().isLoggable( ILogger.Level.RULE );

        if( bLog )
        {
            logVarChanged();
            getRuntime().log( ILogger.Level.RULE, "[RULE '"+ name() +"'+satisfied]" );
        }

        for( IAction action : then )     // There is no needed to sync the 'then' List because it is inmutable: once created, there is no way to add or remove items
        {
            try
            {
                if( bLog )
                    getRuntime().log( ILogger.Level.RULE, "\t"+ action.getTarget() +" = "+ action.getValueToSet() + ((action.getDelay() > 0) ? (" delay="+ action.getDelay()) : "") );

                action.trigger();        // If an action fails (throws an exception),
            }
            catch( Exception exc )       // the exception is logged and next action will be triggered: this maximizes the number of actions that will be accomplished.
            {
                String msg =   "Error : "+ UtilStr.toStringBrief( exc ) +
                             "\nAction: { target="+ action.getTarget() +", value="+ action.getValueToSet() +'}'+
                             "\nRule  : "+ name();

                getRuntime().log( ILogger.Level.SEVERE, msg );
            }
        }
    }

    private void logVarChanged()
    {
        if( getRuntime().isLoggable( ILogger.Level.RULE ) )
        {
            IXprEval eval = ((_if_ != null && _if_.isFutureing()) ? _if_ : when);    // IF always contains a var, but WHEN could contain no var
            String   expr = eval.toString();

            for( Map.Entry<String,Object> entry : eval.getVars().entrySet() )
            {
                expr = expr.replaceAll( entry.getKey(), entry.getKey() +'{'+ entry.getValue() +'}' );
            }

            getRuntime().log( ILogger.Level.RULE, name() +" ["+ ((eval == when) ? "WHEN" : "IF") +" -> "+ expr +']' );
        }
    }

    private boolean canTrigger( Object result, String sClause )
    {
        if( result == null )    // It is null when the var is not part of the expr or the xpr does not have all vars values yet
            return false;

        if( result instanceof Boolean )
            return (Boolean) result;

        getRuntime().log( ILogger.Level.SEVERE, "Clause "+ sClause +" of RULE "+ name() +" is returning '"+ result +"' instead of 'true' or 'false'" );

        return false;
    }

    private IXprEval checkXprEval( IXprEval eval, String sClause )
    {
        if( eval == null )
            return null;

        if( eval.getErrors().isEmpty() )
            return eval;

        String sMsg = "Expression\n:"+ eval.toString() +"\nat clause "+ sClause +" in RULE " + name() +" is useless due to following errors:\n";

        for( ICandi.IError error : eval.getErrors() )
            sMsg += error.toString() +'\n';

        getRuntime().log( ILogger.Level.SEVERE, sMsg );

        return null;
    }
}