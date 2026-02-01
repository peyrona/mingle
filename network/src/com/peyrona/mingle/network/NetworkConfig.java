package com.peyrona.mingle.network;

import com.eclipsesource.json.Json;
import com.eclipsesource.json.JsonArray;
import com.eclipsesource.json.JsonObject;
import com.eclipsesource.json.JsonValue;
import com.peyrona.mingle.lang.MingleException;
import com.peyrona.mingle.lang.japi.UtilStr;
import com.peyrona.mingle.lang.japi.UtilSys;
import java.util.ArrayList;
import java.util.List;

/**
 * Configuration utilities for the Network module.
 *
 * @author Francisco José Morero Peyrona
 *
 * Official web site at: <a href="https://github.com/peyrona/mingle">https://github.com/peyrona/mingle</a>
 */
public class NetworkConfig
{
    /**
     * Returns the network value for the received value.<br>
     * <br>
     * 'jvNetwork' can be in the form of:
     * <ul>
     *    <li>"Plain Socket Server" --> A string that is the name of an entry in module "network"</li>
     *    <li>{ "name": "Plain Socket Server", "port": 4444 } --> An object with an entry in "network" plus some values overwritten</li>
     *    <li> Or an array of any combination of the previous two.
     * </ul>
     *
     * It is assumed that <code>UtilSys.getConfig()</code> returns a non null instance.
     *
     * @param jvNetwork The value of a key in 'config.json'.
     * @return The value for the key "network" from "network" already merged if needed.
     *         Can be: Json.NULL, a JsonObject or a JsonArray of JsonObjects.
     */
    public static JsonValue getAsNetwork( JsonValue jvNetwork )
    {
        if( (jvNetwork != null) && (! jvNetwork.isNull()) )
        {
            if( ! jvNetwork.isArray() )
                return getNetworkItem( jvNetwork );

            JsonArray js2Ret = Json.array();

            jvNetwork.asArray()
                     .forEach( (JsonValue jv) ->
                                {
                                    JsonValue jvItem = getNetworkItem( jv );

                                    if( jvItem != null )
                                        js2Ret.add( jvItem );
                                } );

            return js2Ret.isEmpty() ? Json.NULL : js2Ret;    // Clarity over efficiency
        }

        return jvNetwork;
    }

    /**
     * Returns a list of nodes in the section "grid" or an empty list if the section does
     * not exists or if the section is empty.<br>
     * <br>
     * It is assumed that <code>UtilSys.getConfig()</code> returns a non null instance.
     *
     * @return A list of nodes in the section "grid" or an empty list if the section does
     *         not exists or if the section is empty.
     */
    public static List<JsonValue> getGridNodes()
    {
        JsonValue       jaNodes = UtilSys.getConfig().get( "grid", "nodes", Json.NULL );
        List<JsonValue> lst2Ret = new ArrayList<>();

        if( jaNodes.isArray() )
        {
            jaNodes.asArray()
                   .forEach( (JsonValue jv) ->
                             {
                                if( ! jv.isObject() )
                                    throw new MingleException( "Parse exception '"+ jv +"' is not a JSON object" );

                                lst2Ret.add( jv.asObject() );
                             } );
        }

        return lst2Ret;
    }

    /**
     * Checks if a network configuration name refers to a server.
     *
     * @param name The network name to check.
     * @return true if found in "servers", false otherwise.
     */
    public static boolean isServerNetwork( String name )
    {
        if( UtilStr.isEmpty( name ) )
            return false;

        JsonValue jvServers = UtilSys.getConfig().get( "network", "servers", Json.NULL );

        return findByName( jvServers, name ) != null;
    }

    /**
     * Checks if a network configuration name refers to a client.
     *
     * @param name The network name to check.
     * @return true if found in "clients", false otherwise.
     */
    public static boolean isClientNetwork( String name )
    {
        if( UtilStr.isEmpty( name ) )
            return false;

        JsonValue jvClients = UtilSys.getConfig().get( "network", "clients", Json.NULL );

        return findByName( jvClients, name ) != null;
    }

    /**
     * Retrieves the configuration for a specific network component (Server or Client) by its name.
     * <p>
     * It scans the "servers" and "clients" arrays within the "network" module configuration.<br>
     * <br>
     * It is assumed that <code>UtilSys.getConfig()</code> returns a non null instance.
     *
     * @param name The name of the component to find.
     * @return The JsonValue object corresponding to the found component, or null if not found.
     * @throws MingleException If the name is found in both "servers" and "clients".
     */
    public static JsonValue getNetworkByName( String name )
    {
        if( UtilStr.isEmpty( name ) )
            return Json.NULL;

        // Get 'servers' and 'clients' from config
        JsonValue jvServers = UtilSys.getConfig().get( "network", "servers", Json.NULL );
        JsonValue jvClients = UtilSys.getConfig().get( "network", "clients", Json.NULL );

        // Search in 'servers'
        JsonValue jvServer = findByName( jvServers, name );

        // Search in 'clients'
        JsonValue jvClient = findByName( jvClients, name );

        if( jvServer != null && jvClient != null )
            throw new MingleException( "Name '" + name + "' found in both servers and clients configuration." );

        if( jvServer != null )
            return jvServer;

        return (jvClient != null) ? jvClient : Json.NULL;
    }

    //------------------------------------------------------------------------//
    // PRIVATE SCOPE

    private static JsonValue getNetworkItem( JsonValue jvNetwork )
    {
        if( (jvNetwork != null) && (! jvNetwork.isNull()) )
        {
            if( jvNetwork.isString() )
                return getNetworkByName( jvNetwork.asString() );

            if( jvNetwork.isObject() )
            {
                JsonObject jo    = jvNetwork.asObject();
                String     sName = jo.getString( "name", null );

                if( sName != null )
                    return merge(jo, getNetworkByName( sName ) );
            }
        }

        return jvNetwork;
    }

    private static JsonValue findByName( JsonValue array, String name )
    {
        if( array != null && array.isArray() )
        {
            for( JsonValue item : array.asArray() )
            {
                if( item.isObject() )
                {
                    JsonObject obj = item.asObject();

                    if( name.equals( obj.getString( "name", null ) ) )
                        return obj;
                }
            }
        }

        return null;
    }

    private static JsonValue merge( JsonValue js1, JsonValue js2 )
    {
        if( js1 == null ) return js2;
        if( js2 == null ) return js1;

        if( ! js1.isObject() || ! js2.isObject() )
            return js1;

        JsonObject object1 = js1.asObject();
        JsonObject object2 = js2.asObject();
        JsonObject merged  = new JsonObject();

        // First, copy all members from object2 (base config)
        for( JsonObject.Member member : object2 )
            merged.set( member.getName(), member.getValue() );

        // Then, merge/override with members from object1 (user overrides)
        for( JsonObject.Member member : object1 )
        {
            String    key  = member.getName();
            JsonValue val1 = member.getValue();
            JsonValue val2 = merged.get( key );

            // Deep merge if both are objects
            if( val1 != null && val1.isObject() && val2 != null && val2.isObject() )
                merged.set( key, merge( val1, val2 ) );
            else
                merged.set( key, val1 );
        }

        return merged;
    }
}