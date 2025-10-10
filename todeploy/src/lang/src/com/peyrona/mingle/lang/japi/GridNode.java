/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package com.peyrona.mingle.lang.japi;

import com.eclipsesource.json.JsonArray;
import com.eclipsesource.json.JsonValue;
import java.util.ArrayList;
import java.util.List;

/**
 *
 * @author francisco
 */
public class GridNode
{
    public final String[] targets;    // It is needed later as JSON string, not as JSON object
    public final String   client;
    public final String[] URIs;

    //------------------------------------------------------------------------//

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