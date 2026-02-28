
package com.peyrona.mingle.controllers;

import com.peyrona.mingle.lang.interfaces.IController;
import com.peyrona.mingle.lang.interfaces.ILogger;
import com.peyrona.mingle.lang.interfaces.commands.ICommand;
import com.peyrona.mingle.lang.interfaces.commands.IDevice;
import com.peyrona.mingle.lang.interfaces.commands.IDriver;
import com.peyrona.mingle.lang.interfaces.exen.IEventBus;
import com.peyrona.mingle.lang.interfaces.exen.IRuntime;
import com.peyrona.mingle.lang.japi.UtilJson;
import com.peyrona.mingle.lang.japi.UtilStr;
import com.peyrona.mingle.lang.messages.MsgChangeActuator;
import com.peyrona.mingle.lang.messages.MsgDeviceChanged;
import com.peyrona.mingle.lang.messages.MsgExecute;
import com.peyrona.mingle.lang.xpreval.functions.list;
import com.peyrona.mingle.lang.xpreval.functions.pair;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * AgentNL (Agent Natural Language) Controller.
 * <p>
 * This controller enables natural language interaction with a running Une model.
 * User messages are enriched with a live snapshot of the runtime state (devices,
 * rules, scripts) and forwarded to a configured MCP DEVICE for LLM processing.
 * When the MCP device fires a {@code MsgDeviceChanged} event with the LLM reply,
 * this controller parses the returned JSON action array and executes each validated
 * action directly on the EventBus — without requiring any external scripts.
 * <p>
 * Configuration parameters:
 * <ul>
 *   <li>mcp: Name of the MCP DEVICE to use for LLM calls (required)</li>
 *   <li>system: Static system prompt prefix injected before the NL guide (optional)</li>
 *   <li>context: Name of a DEVICE whose current value is appended to the system prompt (optional)</li>
 *   <li>log: Name of a DEVICE where the raw LLM text response is posted on each invocation (optional)</li>
 *   <li>exclude: Comma-separated names of devices to hide from the LLM context (optional)</li>
 * </ul>
 * <p>
 * Write accepts:
 * <ul>
 *   <li>String: Simple user message</li>
 *   <li>pair: With a mandatory "message" key containing the user message text</li>
 * </ul>
 * <p>
 * LLM action schema (returned by the LLM as a JSON array):
 * <ul>
 *   <li>{"action":"write"  , "device":"&lt;name&gt;", "value":&lt;value&gt;}</li>
 *   <li>{"action":"execute", "name":"&lt;rule_or_script_name&gt;"}</li>
 *   <li>{"action":"unknown", "reason":"&lt;explanation&gt;"}</li>
 * </ul>
 *
 * @author Francisco José Morero Peyrona
 *
 * Official web site at: <a href="https://github.com/peyrona/mingle">https://github.com/peyrona/mingle</a>
 */
public final class AgentNL
       extends ControllerBase
{
    // Configuration keys
    private static final String KEY_MCP     = "mcp";     // The value of this key is never null because it is declared as REQUIRED in DRIVER declaration
    private static final String KEY_LOG     = "log";
    private static final String KEY_EXCLUDE = "exclude";

    private IEventBus.Listener busListener = null;

    //------------------------------------------------------------------------//

    @Override
    public void set( String deviceName, Map<String, Object> deviceInit, IController.Listener listener )
    {
        setDeviceName( deviceName );
        setListener( listener );         // Must be at beginning: in case an error happens, Listener is needed
        setDeviceConfig( deviceInit );   // Store raw config first, validated values will be stored at the end

        if( get( KEY_EXCLUDE ) != null )
        {
            Set<String> setUserExcluded = new HashSet<>();

            for( String name : ((String) get( KEY_EXCLUDE )).split( "," ) )
            {
                String trimmed = name.trim();

                if( ! trimmed.isEmpty() )
                    setUserExcluded.add( trimmed );
            }

            set( KEY_EXCLUDE, setUserExcluded );
        }

        setValid( true );
    }

    @Override
    @SuppressWarnings( "unchecked" )
    public boolean start( IRuntime rt )
    {
        if( isInvalid() || (! super.start( rt )) )
            return false;

        checkDevice( (String) get( KEY_MCP ) );    // Declared as REQUIRED

        if( get( KEY_LOG ) != null )
            checkDevice( (String) get( KEY_LOG ) );

        if( isValid() )
        {
            busListener = (msg) ->
                            {
                                MsgDeviceChanged m = (MsgDeviceChanged) msg;

                                if( m.name.equals( get( KEY_MCP ) ) )
                                    handleMcpResponse( m.value );
                            };

            getRuntime().bus().add( busListener, MsgDeviceChanged.class );
        }

        return isValid();
    }

    @Override
    public void stop()
    {
        if( busListener != null )
        {
            getRuntime().bus().remove( busListener );
            busListener = null;
        }

        super.stop();
    }

    @Override
    public void read()
    {
        sendReaded( "" );   // AgentNL is write-only: user messages flow in; actions flow out via the bus
    }

    /**
     * Sends a natural language message to the LLM via the configured MCP DEVICE.
     * <p>
     * The current runtime state (devices, rules, scripts) is automatically appended
     * to the user message as context before forwarding to the MCP device.
     *
     * @param request The user's natural language message (String or pair with "message" key).
     */
    @Override
    public void write( Object request )
    {
        if( isInvalid() )
            return;

        String msg = buildRuntimeScenario()
                     +"\n\n"+
                     "**User request**:\n"+ request.toString().trim();

        if( isFaked() )
        {
            if( get( KEY_LOG ) != null )
                getRuntime().bus().post( new MsgChangeActuator( (String) get( KEY_LOG ), msg ) );
        }
        else
        {
            getRuntime().bus().post( new MsgChangeActuator( (String) get( KEY_MCP ), msg ) );
        }
    }

    //------------------------------------------------------------------------//
    // PRIVATE SCOPE

    /**
     * Builds a live snapshot of the runtime state for inclusion in the user message.
     * Infrastructure devices (this device, the MCP device, the log device if configured,
     * and any devices listed in the {@code exclude} config key) are excluded from the
     * listing so they are not exposed to the LLM.
     */
    private String buildRuntimeScenario()
    {
        Set<String> excluded = new HashSet<>();        // Devices to not be sent to the LLM: they part of the Agent.
                    excluded.add( getDeviceName() );
                    excluded.add( (String) get( KEY_MCP ) );

        if( get( KEY_LOG ) != null )
            excluded.add( get( KEY_LOG ).toString() );

        if( get( KEY_EXCLUDE ) != null )
            excluded.addAll( (Set) get( KEY_EXCLUDE ) );

        StringBuilder ctx = new StringBuilder()
                         .append( "**Available widgets**:\n" )
                         .append( "- Devices (name | type | current_value ):\n" );

        for( ICommand cmd : getRuntime().all( "device" ) )
        {
            if( ! excluded.contains( cmd.name() ) )
            {
                IDevice dev = (IDevice) cmd;

                ctx.append( "+ "  ).append( dev.name() )
                   .append( " | " ).append( dev.value() == null ? "unknown" : dev.value().getClass().getSimpleName() )
                   .append( " | " ).append( dev.value() )
                   .append( '\n' );
            }
        }

        ctx.append( "\n- Rules:\n" );

        for( ICommand cmd : getRuntime().all( "rule" ) )
        {
            if( ! excluded.contains( cmd.name() ) )
                ctx.append( "+ " ).append( cmd.name() ).append( '\n' );
        }

        ctx.append( "\n- Scripts:\n" );

        // Collect scripts that are bound to a DRIVER — they are internal
        // infrastructure and cannot be triggered by the LLM.
        Set<String> driver = new HashSet<>();

        for( ICommand cmd : getRuntime().all( "driver" ) )
            driver.add( ((IDriver) cmd).getScriptName() );

        for( ICommand cmd : getRuntime().all( "script" ) )
        {
            if( ! excluded.contains( cmd.name() ) &&
                ! driver.contains( cmd.name() ) )
            {
                ctx.append( "+ " ).append( cmd.name() ).append( '\n' );
            }
        }

        return ctx.toString();
    }

    /**
     * Processes the LLM response received from the MCP DEVICE via {@code MsgDeviceChanged}.
     * <p>
     * Optionally posts the raw LLM text to the {@code log} device, then parses the JSON
     * action array and executes each validated action on the EventBus.
     *
     * @param value The new value of the MCP device (expected to be a pair with "response" key).
     */
    private void handleMcpResponse( Object value )
    {
        if( ! (value instanceof pair) )
        {
            getRuntime().log( ILogger.Level.WARNING,
                              "AgentNL: expected a pair from MCP device, received: "
                              + (value == null ? "null" : value.getClass().getSimpleName()) );
            return;
        }

        Object responseObj = ((pair) value).get( "response" );

        if( responseObj == null )
        {
            getRuntime().log( ILogger.Level.WARNING, "AgentNL: MCP returned no 'response' key" );
            return;
        }

        String content = responseObj.toString();

        // Post raw LLM response text to the log device if one is configured
        String logDevice = (String) get( KEY_LOG );

        if( ! UtilStr.isEmpty( logDevice ) )
            getRuntime().bus().post( new MsgChangeActuator( logDevice, content ) );

        // Strip markdown code fences that some models add despite the instructions
        String json = content.trim();

        if( json.startsWith( "```" ) )
            json = json.replaceAll( "(?s)```[a-zA-Z]*\\n?", "" ).replace( "```", "" ).trim();

        try
        {
            Object parsed = UtilJson.toUneType( json );

            list actions;

            if( parsed instanceof list )
            {
                actions = (list) parsed;
            }
            else if( parsed instanceof pair )
            {
                // Accept a single JSON object as a one-element list
                actions = new list();
                actions.add( parsed );
            }
            else
            {
                getRuntime().log( ILogger.Level.WARNING,
                                  "AgentNL: LLM response was not a JSON array or object — raw: " + json );
                return;
            }

            for( Object av : actions )
            {
                if( ! (av instanceof pair) )
                    continue;

                pair   action  = (pair) av;
                Object typeObj = action.get( "action" );

                if( typeObj == null )
                    continue;

                String type = typeObj.toString();

                if( "write".equals( type ) )
                {
                    Object deviceNameObj = action.get( "device" );

                    if( deviceNameObj == null )
                        continue;

                    String   deviceName = deviceNameObj.toString();
                    ICommand cmd        = getRuntime().get( deviceName );

                    if( ! (cmd instanceof IDevice) )
                    {
                        getRuntime().log( ILogger.Level.WARNING,
                                          "AgentNL: LLM referenced unknown device '" + deviceName + '\'' );
                        continue;
                    }

                    Object newValue = action.get( "value" );

                    if( newValue != null )
                        getRuntime().bus().post( new MsgChangeActuator( deviceName, newValue ) );
                }
                else if( "execute".equals( type ) )
                {
                    Object nameObj = action.get( "name" );

                    if( nameObj == null )
                        continue;

                    String name = nameObj.toString();

                    if( getRuntime().get( name ) == null )
                    {
                        getRuntime().log( ILogger.Level.WARNING,
                                          "AgentNL: LLM referenced unknown rule/script '" + name + '\'' );
                        continue;
                    }

                    getRuntime().bus().post( new MsgExecute( name, true ) );
                }
                else
                {
                    Object reason = action.get( "reason" );

                    getRuntime().log( ILogger.Level.INFO,
                                      "AgentNL: LLM action 'unknown' — "
                                      + (reason != null ? reason.toString() : "no reason given") );
                }
            }
        }
        catch( Exception exc )
        {
            getRuntime().log( ILogger.Level.WARNING,
                              "AgentNL: error processing LLM response: " + exc.getMessage()
                              + " | Raw JSON: " + json );
        }
    }

    private void checkDevice( String name )
    {
        ICommand dev = getRuntime().get( name );

        if( dev == null )
        {
            sendIsInvalid( "Device '"+ name +"' not found." );
        }
        else if( ! (dev instanceof IDevice) )
        {
            sendIsInvalid( "Device '"+ name +"' is not a DEVICE, but a "+ dev.getClass().getSimpleName() );
        }
    }
}
