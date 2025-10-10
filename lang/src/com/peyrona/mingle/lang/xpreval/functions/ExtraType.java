
package com.peyrona.mingle.lang.xpreval.functions;

import com.eclipsesource.json.Json;
import com.eclipsesource.json.JsonArray;
import com.eclipsesource.json.JsonObject;
import com.eclipsesource.json.JsonValue;
import com.peyrona.mingle.lang.MingleException;
import com.peyrona.mingle.lang.japi.UtilJson;

/**
 * Extra types:
 * <ul>
 *    <li>Must implement UneSerializable</li>
 *    <li>Can have only one constructor and it must have following signature: clazz( Object...args )</li>
 </ul>
 *
 * @param <T>
 * @author Francisco José Morero Peyrona
 *
 * Official web site at: <a href="https://github.com/peyrona/mingle">https://github.com/peyrona/mingle</a>
 */
public abstract class ExtraType<T> implements Comparable<Object>
{
    public abstract Object serialize();
    public abstract T      deserialize( Object str );

    //------------------------------------------------------------------------//

    UtilJson parse( Object o )
    {
        if( o instanceof JsonValue )
            return new UtilJson( (JsonValue) o );

        try
        {
           return new UtilJson( o.toString() );
        }
        catch( MingleException ioe )
        {
            return null;
        }
    }

    JsonObject build( String data )
    {
        return Json.object()
                   .add( "class", getClass().getSimpleName() )    // Better to use getSimpleName() in case I move later date, time, list or pair to a different package
                   .add( "data" , data );
    }

    JsonObject build( JsonArray ja )
    {
        return Json.object()
                   .add( "class", getClass().getSimpleName() )
                   .add( "data" , ja );
    }

    JsonObject build( JsonObject jo )
    {
        return Json.object()
                   .add( "class", getClass().getSimpleName() )
                   .add( "data" , jo );
    }

    public String type()
    {
        return getClass().getSimpleName();
    }
}