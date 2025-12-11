
package com.peyrona.mingle.lang.messages;

import com.eclipsesource.json.Json;
import com.eclipsesource.json.JsonObject;
import com.peyrona.mingle.lang.MingleException;
import com.peyrona.mingle.lang.japi.UtilJson;
import com.peyrona.mingle.lang.japi.UtilReflect;
import com.peyrona.mingle.lang.japi.UtilType;
import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.net.URISyntaxException;

/**
 * This is the base class for all messages that are known by the ExEn.
 *
 * @author Francisco José Morero Peyrona
 *
 * Official web site at: <a href="https://github.com/peyrona/mingle">https://github.com/peyrona/mingle</a>
 */
public abstract class Message
{
    public static final String sCLASS = "class";
    public static final String sWHEN  = "when";
    public static final String sNAME  = "name";
    public static final String sVALUE = "value";

    /**
     * Unix time stamp (in milliseconds).
     */
    public final long when  = System.currentTimeMillis();

    //------------------------------------------------------------------------//

    // NOTE: methods ::serialize() and ::deserialize(...) exist to be used by third parties.
    //       As MSP is built using Eclipse Minimal JSON library, I do not need to use these
    //       methods, but others (which use different libs) would need them.

    public String serialize()             // Only needed here (not needed in subclasses)
    {
        return toJSON().toString();
    }

    public static Object deserialize( String sJSON )
    {
        try
        {
            UtilJson json   = new UtilJson( sJSON );
            String   sClass = Message.class.getName();
                     sClass = sClass.substring( 0, sClass.lastIndexOf( '.' ) + 1 ) + json.getString( sCLASS, null );     // Never is null
            Class<?> clazz  = Class.forName( sClass );
            String   name   = json.getString( sNAME , null );    // Never is null
            String   value  = json.getString( sVALUE, null );    // Can be null
            Object   oValue = value == null ? null : UtilType.toUne( Json.parse( value ) );

            return (oValue == null) ? UtilReflect.newInstance( clazz, name )
                                    : UtilReflect.newInstance( clazz, name, oValue );
        }
        catch( ClassNotFoundException | InstantiationException   | NoSuchMethodException     | IllegalAccessException   |
               URISyntaxException     | IllegalArgumentException | InvocationTargetException | IOException exc )
        {
            throw new MingleException( exc );    // this should to happen
        }
    }

    public JsonObject toJSON()
    {
        try
        {
            JsonObject jo = Json.object()
                                .add( sCLASS, getClass().getSimpleName() )    // getSimpleName() to save payload
                                .add( sWHEN , when )
                                .add( sNAME , (String) UtilReflect.getField( getClass(), "name" ).get( this ) );

            Field field = UtilReflect.getField( getClass(), "value" );

            if( field != null )
                jo.add( sVALUE, UtilType.toJson( field.get( this ) ) );

            return jo;
        }
        catch( IllegalArgumentException | IllegalAccessException exc )
        {
            throw new MingleException( exc );    // this should to happen
        }
    }

    @Override
    public String toString()              // Only needed here (not needed in subclasses)
    {
        return toJSON().toString();
    }

    //------------------------------------------------------------------------//

    protected Message()
    {
    }
}