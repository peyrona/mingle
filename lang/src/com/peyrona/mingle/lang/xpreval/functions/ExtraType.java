
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

    public abstract Object serialize();
    public abstract T      deserialize( Object str );

    //------------------------------------------------------------------------//
    // PUBLIC

    /**
     * Returns the serialized JSON representation of this instance: date, time, list, pair.<br>
     * The returned value can be passed to the constructor <code>date(...), time(...), list(...), pair(...)</code> to unpack it.
     *
     * @return The JSON string from serialize().
     */
    public String pack()
    {
        return serialize().toString();
    }

    /**
     * Return the name of the class of this instance in lower case: date, time, list, pair.
     *
     * @return The name of the class of this instance in lower case.
     */
    public String type()
    {
        return getClass().getSimpleName().toLowerCase();
    }

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