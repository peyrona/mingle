package com.peyrona.mingle.network;

import com.eclipsesource.json.Json;
import com.eclipsesource.json.JsonArray;
import com.eclipsesource.json.JsonValue;
import com.peyrona.mingle.lang.MingleException;
import com.peyrona.mingle.lang.interfaces.network.INetClient;
import com.peyrona.mingle.lang.interfaces.network.INetServer;
import com.peyrona.mingle.lang.japi.UtilJson;
import com.peyrona.mingle.lang.japi.UtilReflect;
import com.peyrona.mingle.lang.japi.UtilType;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * To build all types of network classes, existing in visible JARs (class-path).
 * <p>
 * sConfAsJSON is something like this:<br>
 * { class: "com.peyrona.mingle.network.netty", key1: value1, key2: value2, keyN, valueN }
 *
 * @author Francisco José Morero Peyrona
 *
 */
public final class NetworkBuilder
{
    /**
     * Starts all servers defined in the given JSON string.
     *
     * @param sJSON the JSON string containing server configurations
     * @return a set of started servers
     */
    public static Set<INetServer> startAllServers( String sJSON )
    {
        Set<INetServer> set = new HashSet<>();

        for( Map.Entry<INetServer,String> entry : buildAllServers( sJSON ).entrySet() )
        {
            set.add( entry.getKey() );

            entry.getKey().start( entry.getValue() );
        }

        return set;
    }

    /**
     * Builds a single server from the given JSON string.
     *
     * @param sJSON the JSON string containing the server configuration
     * @return the built server
     */
    public static INetServer buildServer( String sJSON )
    {
        return build( sJSON, INetServer.class ).keySet().iterator().next();
    }

    /**
     * Builds all servers defined in the given JSON string.
     *
     * @param sJSON the JSON string containing server configurations
     * @return a map of servers and their initialization parameters
     */
    public static Map<INetServer, String> buildAllServers( String sJSON )
    {
        return build( sJSON, INetServer.class );
    }

    /**
     * Connects all clients defined in the given JSON string.
     *
     * @param sJSON the JSON string containing client configurations
     * @return a set of connected clients
     */
    public static Set<INetClient> connectAllClients( String sJSON )
    {
        Set<INetClient> set = new HashSet<>();

        for( Map.Entry<INetClient,String> entry : buildAllClients( sJSON ).entrySet() )
        {
            set.add( entry.getKey() );

            entry.getKey().connect( entry.getValue() );
        }

        return set;
    }

    /**
     * Builds a single client from the given JSON string.
     *
     * @param sJSON the JSON string containing the client configuration
     * @return the built client
     */
    public static INetClient buildClient( String sJSON )
    {
        Map<INetClient,String> map = build( sJSON, INetClient.class );

        if( map.isEmpty() )
            return null;

        return map.keySet().iterator().next();
    }

    /**
     * Builds all clients defined in the given JSON string.
     *
     * @param sJSON the JSON string containing client configurations
     * @return a map of clients and their initialization parameters
     */
    public static Map<INetClient, String> buildAllClients( String sJSON )
    {
        return build( sJSON, INetClient.class );
    }

    //------------------------------------------------------------------------//

    /**
     * Builds network entities (servers or clients) from the given JSON string.
     *
     * @param <T> the type of network entity (INetServer or INetClient)
     * @param sJSON the JSON string containing network configurations
     * @param networkType the class of the network entity
     * @return a map of network entities and their initialization parameters
     */
    private static <T> Map<T, String> build( String sJSON, Class<T> networkType )
    {
        Map<T, String> map = new HashMap<>();

        JsonValue jv = Json.parse( sJSON );
        JsonArray ja = jv.isArray() ? jv.asArray() : new JsonArray().add( jv.asObject() );

        for( JsonValue j : ja )
        {
            try
            {
                UtilJson ju = new UtilJson( j.asObject() );
                Object[] ao = UtilJson.toArray( ju.getArray( "uris" ) );

                T network = UtilReflect.newInstance( networkType,
                                                     ju.getString( "builder" ),
                                                     UtilType.convertArray( ao, String.class ) );

                map.put( network, ju.getAsString( "init", "" ) );
            }
            catch( Exception exc )
            {
                throw new MingleException( exc );
            }
        }

        return map;
    }
}