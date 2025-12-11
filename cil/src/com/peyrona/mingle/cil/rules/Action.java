
package com.peyrona.mingle.cil.rules;

import com.peyrona.mingle.lang.MingleException;
import com.peyrona.mingle.lang.interfaces.IXprEval;
import com.peyrona.mingle.lang.interfaces.commands.ICommand;
import com.peyrona.mingle.lang.interfaces.commands.IDevice;
import com.peyrona.mingle.lang.interfaces.commands.IRule;
import com.peyrona.mingle.lang.interfaces.commands.IScript;
import com.peyrona.mingle.lang.interfaces.exen.IRuntime;
import com.peyrona.mingle.lang.japi.UtilStr;
import com.peyrona.mingle.lang.messages.MsgChangeActuator;
import com.peyrona.mingle.lang.messages.MsgExecute;
import java.util.Map;
import java.util.Objects;

/**
 * Instances of this class represent the Rule's actions (the THEN clause).
 * One Rule can have more than one action and each one can be either the name of an existing SCRIPT
 * to be executed or the invocation of an Actuator's method passing optionally a value to it.
 * <p>
 * Actions has also an optional delay (clause AFTER): when present, the action will be executed after
 * this time.
 * <p>
 * The WHEN clause has an expression, which has to be evaluated and if it evaluates to true, then
 * all Actions in THEN clause will be executed in same order as they appear in the source code.
 * <p>
 * The shortest expression is when the name of a device: MyLight. If the light
 * is ON, this expression evaluates to true.
 *
 * @author Francisco José Morero Peyrona
 *
 * Official web site at: <a href="https://github.com/peyrona/mingle">https://github.com/peyrona/mingle</a>
 */
final class Action implements IRule.IAction
{
    // The package scope members are accessed by ScriptBuilder
    private final long     delay;
    private final String   target;    // The name of: either a Device (an actuator) or a Script or a Rule
    private final Object   value;     // Value to set: can be null
    private       IXprEval xprEval;
    private       IRuntime runtime;

    //------------------------------------------------------------------------//
    // PACKAGE SCOPE CONSTRUCTOR

    /**
     * This constructor is used by RuleBuilder.java. These instances are used until Rule receives
     * the IRuntime at Rule::start(...), then, the other constructor is used to create all Actions
     * again. But this time they are fully created and tested.
     *
     * @param delay
     * @param target An Script, Rule or Device's name or an expression.
     * @param valueToSet
     * @param isValueAnExpr
     */
    Action( long delay, String target, Object valueToSet )
    {
        this.delay   = (delay < 0) ? 0 : delay;
        this.target  = target;
        this.value   = valueToSet;
        this.xprEval = null;
        this.runtime = null;
    }

    //------------------------------------------------------------------------//
    // OVERRIDEN

    @Override
    public String getTarget()
    {
        return target;
    }

    @Override
    public Object getValueToSet()
    {
        return value;
    }

    @Override
    public long getDelay()
    {
        return delay;
    }

    @Override
    public void trigger()
    {
        assert runtime != null;

        if( isTargetScriptOrRule() )              // It is of type: THEN MyScript | MyRule
        {
            runtime.bus().post(new MsgExecute( target, false ), delay );
        }
        else                                      // It is of type: THEN device = new_value
        {
            Object val = getValue();              // This method evaluate ::xprEval if needed

            if( val != null )                     // Device's value is null until the device receives its first value
            {
                if( ! isTargetAnExpression() )    // As said, if it was an expr, it was evaluated by ::getValue()
                {
                    if( runtime.isNameOfGroup( target ) )
                    {
                        for( IDevice dev : runtime.getMembersOf( target ) )
                            runtime.bus().post( new MsgChangeActuator( dev.name(), val ), delay );
                    }
                    else
                    {
                        runtime.bus().post( new MsgChangeActuator( target, val ), delay );
                    }
                }
             // else --> If it is just an expression (e.g.: THEN put("myvar", "myvalue")), there is no message to send.
             //          And if the xpr modifies a var it must be done via CellSet driver and this driver sends the message.
            }
        }
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
            hash = 97 * hash + Objects.hashCode( this.target );
            hash = 97 * hash + (int) (this.delay ^ (this.delay >>> 32));
            hash = 97 * hash + Objects.hashCode( this.value );
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

        if( ! Objects.equals( this.target, other.target ) )
            return false;

        return Objects.equals( this.value, other.value );
    }

    //------------------------------------------------------------------------//
    // PROTECTED SCOPE

    void setRuntime( IRuntime runtime )
    {
        this.runtime = runtime;

        if( value == null )                                     // When 'value' is null, 'target' can be an Expression or a Rule's or a Script's name
        {
            ICommand cmd  = runtime.get( target );

            if( (! (cmd instanceof IRule)) &&                   // When 'value' is null, and 'target' is not a Rule, neither a Script, it must be an Expression
                (! (cmd instanceof IScript)) )
            {
                xprEval = newXprEval( target, runtime );
            }

            return;                                             // 'value' is a Rule or Script name (nothing else to do)
        }

        if( ! (value instanceof String) )                       // 'value' (things after '=') is not and expression. Has to be a constant or another Device
            return;

        String sValue = (String) value;

        if( runtime.get( sValue ) instanceof IDevice )          // 'value' is another Device; e.g.: THEN MyDevice1 = MyDevice2
            return;

        // 'value' has to be a constant; e.g.: THEN MyDevice = true  (or 48, or list(1,2,3))
        // Note: expressions without vars are resolved at compilation time, giving as result a constant

        this.xprEval = newXprEval( sValue, runtime );
    }

    //------------------------------------------------------------------------//
    // PRIVATE SCOPE

    private IXprEval newXprEval( String sXpr, IRuntime runtime )
    {
        IXprEval xe = runtime.newXprEval().build( sXpr, (o) -> {}, runtime.newGroupWiseFn() );

        if( ! xe.getErrors().isEmpty() )
            throw new MingleException( "Invalid expression: "+ sXpr );

        return xe;
    }

    private boolean isTargetScriptOrRule()
    {
        return (value == null) && (xprEval == null);
    }

    private boolean isTargetAnExpression()    // Only and expression v.g.: THEN put("myvar", "myval") --> true
    {                                         // This is an expr plus an assingment: THEN myDevice = put("myvar", "myval")  --> false
        return (value == null) && (xprEval != null);
    }

    private Object getValue()
    {
        IDevice device = (value == null) ? null : getDevice( value.toString() );     // when THEN put("myvar", "myval") --> value == null

        if( device != null )
        {
            if( device.value() == null )
                return "Action can not be evaluted now: the device '"+ device.name() +" havs not a value yet";

            return device.value();     // 'value' is another Device; e.g.: THEN MyDevice1 = MyDevice2
        }

        if( xprEval == null )          // Then it has to be a constant (a basic Une data type)
            return value;

        // The value is an expression and it has to be evaluated
        Map<String,Object> mapVars = xprEval.getVars();

        if( mapVars.isEmpty() )
            return xprEval.eval();

        // Expression has variables: they are not automatically updated, so, lets find out their current values.

        for( String varName : mapVars.keySet() )
        {
            Object val = getDevice( varName ).value();

            if( val != null )                // A device has a null value when no value arrived yet
                xprEval.set( varName, val );
        }

        return xprEval.eval();   // When one or more devices have not a value yet, the xpr was not evaluated and null is returned.
    }

    private IDevice getDevice( String name )
    {
        ICommand cmd = runtime.get( name );

        if( cmd instanceof IDevice )
            return (IDevice) cmd;

        return null;
    }
}