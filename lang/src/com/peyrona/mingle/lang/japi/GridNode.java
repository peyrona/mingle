
package com.peyrona.mingle.lang.japi;

import com.eclipsesource.json.JsonArray;
import com.eclipsesource.json.JsonValue;
import java.util.ArrayList;
import java.util.List;

/**
 * Represents a node in the distributed execution grid (ExEn cluster).
 * <p>
 * Each GridNode contains the configuration for connecting to a specific ExEn instance,
 * including target connections, client implementation, and required libraries (URIs).
 *
 * @author francisco
 */
public class GridNode
{
    /**
     * Array of target ExEn instances to connect to.
     * Each target is stored as a JSON string (not a JSON object).
     * Empty array if no targets are configured.
     */
    public final String[] targets;

    /**
     * The fully qualified class name of the network client implementation to use
     * (e.g., "com.peyrona.mingle.network.socket.SocketClient").
     * Null if not configured.
     */
    public final String   client;

    /**
     * Array of library URIs required by the network client.
     * Each URI specifies a JAR file location (e.g., "file:///path/to/network.jar").
     * Empty array if no libraries are required.
     */
    public final String[] URIs;

    //------------------------------------------------------------------------//

    /**
     * Creates a GridNode from JSON values parsed from configuration.
     *
     * @param targets JSON array of target ExEn connections (each target as JSON object string).
     *                If null or not an array, targets will be empty array.
     * @param client  JSON string value of fully qualified network client class name.
     *                If null, client will be null.
     * @param URIs    JSON array of library URIs required by the client.
     *                If null or not an array, URIs will be empty array.
     */
    GridNode( JsonValue targets, JsonValue client, JsonValue URIs )
    {
        if( (targets == null) || (! targets.isArray()) )
        {
            this.targets = new String[0];
        }
        else
        {
            List<String> list = new ArrayList<>();
            JsonArray    ja   = targets.asArray();

            for( int n = 0; n < ja.size(); n++ )
            {
                if( ja.get( n ).isObject() )  list.add( ja.get( n ).toString() );    // .toString() not .asString()
                else                          list.add( null );                      // This will be checked later by GridManager
            }

            this.targets = list.toArray( String[]::new );
        }

        this.client = ((client == null) ? null : client.asString());

        if( (URIs == null) || (! URIs.isArray()) )
        {
            this.URIs = new String[0];
        }
        else
        {
            List<String> list = new ArrayList<>();

            URIs.asArray().forEach( (JsonValue jv) -> list.add( jv.asString() ) );

            this.URIs = list.toArray( String[]::new );
        }
    }
}