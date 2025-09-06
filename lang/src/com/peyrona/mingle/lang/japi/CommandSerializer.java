
package com.peyrona.mingle.lang.japi;

import com.eclipsesource.json.Json;
import com.eclipsesource.json.JsonArray;
import com.eclipsesource.json.JsonObject;
import com.eclipsesource.json.JsonValue;
import com.peyrona.mingle.lang.MingleException;
import com.peyrona.mingle.lang.interfaces.commands.ICmdKeys;
import java.util.Map;

/**
 * Serializes ICommand instances. This implementation is the standard MSP serializer:
 * it writes passed information into a JSON format. Other serializers could act
 * in a different way: adding more information or writing the information into
 * XML format for instance.<br>
 * <br>
 * This class produces the standard output (the standard JSON) that CILs read to create
 * Commands back (deserialize) and forth (serialize).
 * <br>
 * Other implementations of the Une language and the Une Platform can have their own
 * CommandSerializer, but this CommandSerializer is the default one for the Mingle
 * Standard Platform.
 <br>
 * One of the consequences of the fact that transpiler is agnostic about the CIL,
 * is that transpilers can not directly create instances of ICommands. This is done
 * via CILBuilder. Therefore the only way to create an ICommand is to ask the
 * builder to create it.
 *
 * @author Francisco José Morero Peyrona
 *
 * Official web site at: <a href="https://mingle.peyrona.com">https://mingle.peyrona.com</a>
 */
public final class CommandSerializer
{
    /**
     * Returns the JSON representation of a Device command defined by passed arguments.
     * <p>
     * This method makes no checks: garbage-in-garbage-out.
     *
     * @param name
     * @param driverName
     * @param driverInit Driver's Configuration Map. key is a name and value is either a primitive value or a serialized extended value.
     * @param deviceInit Device's Configuration Map. key is a name and value is either a primitive value or a serialized extended value.
     * @return
     */
    public static String Device( String name, String driverName,
                                 Map<String,Object> driverInit, Map<String,Object> deviceInit )
    {
        return Json.object()
                   .add( ICmdKeys.CMD_CMD    , ICmdKeys.CMD_DEVICE )
                   .add( ICmdKeys.CMD_NAME   , name )
                   .add( ICmdKeys.DRIVER_NAME, driverName )
                   .add( ICmdKeys.DRIVER_INIT, map2Json( driverInit ) )
                   .add( ICmdKeys.DEVICE_INIT, map2Json( deviceInit ) )
                   .toString();
    }

    /**
     * Returns the JSON representation of a Driver command defined by passed arguments.
     * <p>
     * This method makes no checks: garbage-in-garbage-out.
     *
     * @param name
     * @param scriptName
     * @return
     */
    public static String Driver( String name, String scriptName )
    {
        return Json.object()
                   .add( ICmdKeys.CMD_CMD      , ICmdKeys.CMD_DRIVER )
                   .add( ICmdKeys.CMD_NAME     , name )
                   .add( ICmdKeys.DRIVER_SCRIPT, scriptName )
                   .toString();
    }

    /**
     * Returns the JSON representation of a RuleAction command clause defined by passed arguments.
     * <p>
     * This method makes no checks: garbage-in-garbage-out.
     *
     * @param delay
     * @param targetName
     * @param valueToSet
     * @return The JSON representation of a RuleAction command clause defined by passed arguments.
     */
    public static String RuleAction( long delay, String targetName, Object valueToSet )
    {
        return Json.object()
                   .add( ICmdKeys.RULE_THEN_AFTER , delay )
                   .add( ICmdKeys.RULE_THEN_TARGET, targetName )
                   .add( ICmdKeys.RULE_THEN_VALUE , value2Json( valueToSet ) )
                   .toString();
    }

    /**
     * Returns the JSON representation of a Rule command defined by passed arguments.
     * <p>
     * This method makes no checks: garbage-in-garbage-out.
     *
     * @param name
     * @param sWhen
     * @param sIf
     * @param asActions The result of invoking ::RuleAction(...) for every action in the THEN clause.
     * @return
     */
    public static String Rule( String name, String sWhen, String sIf, String[] asActions )
    {
        JsonArray jaThen = Json.array();

        for( String s : asActions )
            jaThen.add( Json.parse( s ) );     // It is needed to convert back to JSON object

        return Json.object()
                   .add( ICmdKeys.CMD_CMD  , ICmdKeys.CMD_RULE )
                   .add( ICmdKeys.CMD_NAME , name   )
                   .add( ICmdKeys.RULE_WHEN, sWhen  )
                   .add( ICmdKeys.RULE_THEN, jaThen )
                   .add( ICmdKeys.RULE_IF  , sIf    )
                   .toString();
    }

    /**
     * Returns the JSON representation of a Script command defined by passed arguments.
     * <p>
     * This method makes no checks: garbage-in-garbage-out.
     *
     * @param name
     * @param language
     * @param onStart
     * @param onStop
     * @param inline True when Une source code FROM clause contents (SCRIPT command) is in between brackets ({...})
     * @param from
     * @param callName
     * @return
     */
    public static String Script( String name, String language, boolean onStart, boolean onStop, boolean inline, String[] from, String callName )
    {
        return Json.object()
                   .add( ICmdKeys.CMD_CMD        , ICmdKeys.CMD_SCRIPT )
                   .add( ICmdKeys.CMD_NAME       , name )
                   .add( ICmdKeys.SCRIPT_LANGUAGE, language )
                   .add( ICmdKeys.SCRIPT_ONSTART , onStart )
                   .add( ICmdKeys.SCRIPT_ONSTOP  , onStop )
                   .add( ICmdKeys.SCRIPT_INLINE  , inline )
                   .add( ICmdKeys.SCRIPT_FROM    , UtilJson.toJSON( from ) )
                   .add( ICmdKeys.SCRIPT_CALL    , callName )
                   .toString();
    }

    //------------------------------------------------------------------------//
    // PRIVATE SCOPE

    private CommandSerializer()
    {
        // Avoids creating instances of this class
    }

    private static JsonValue map2Json( Map<String,Object> map )
    {
        if( UtilColls.isEmpty( map ) )
            return Json.NULL;

        JsonObject jo = Json.object();

        map.forEach( (String key, Object value) -> jo.add( key, value2Json( value ) ) );

        return jo;
    }

    public static JsonValue value2Json( Object value )
    {
        if( value == null )
            return Json.NULL;

        if( value instanceof Map )          // Faster 1st
            return map2Json( (Map) value );

        JsonValue jv = UtilType.toJson( value );

        if( jv != null )
            return jv;

        throw new MingleException( value.getClass().getName() +" Invalid data type" );
    }
}