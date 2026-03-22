
package com.peyrona.mingle.lang.xpreval.functions;

import com.eclipsesource.json.Json;
import com.eclipsesource.json.JsonObject;
import com.eclipsesource.json.JsonValue;
import com.peyrona.mingle.lang.MingleException;
import com.peyrona.mingle.lang.japi.UtilJson;

/**
 * Extra types.<br>
 * At least but not only: date, time, list, pair.<br>
 * In general all returned by IXPrEval::getExtendedTypes().<br>
 * They must:
 * <ul>
 *    <li>Implement: serialize() and deserialize(), pack()</li>
 *    <li>Have a constructor with this signature: clazz( Object...args )</li>
 * </ul>
 *
 * @param <T> date, time, list, pair, ...
 * @author Francisco José Morero Peyrona
 *
 * Official web site at: <a href="https://github.com/peyrona/mingle">https://github.com/peyrona/mingle</a>
 */
public abstract class ExtraType<T> implements Comparable<Object>
{
    //------------------------------------------------------------------------//
    // ABSTRACT

    public abstract JsonObject serialize();
    public abstract T          deserialize( JsonObject str );

    //------------------------------------------------------------------------//
    // PROTECTED

    protected UtilJson parse( Object o )
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

    protected JsonObject build( String data )
    {
        return Json.object()
                   .add( "class", getClass().getCanonicalName() )
                   .add( "data" , data );
    }
}