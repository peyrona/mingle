
package com.peyrona.mingle.lang.messages;

import com.eclipsesource.json.Json;
import com.eclipsesource.json.JsonObject;
import com.peyrona.mingle.lang.japi.UtilType;

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
    public final long   when = System.currentTimeMillis();

    /**
     * Device or command name associated with this message.
     */
    public final String name;

    /**
     * Value carried by this message, or {@code null} when not applicable
     * (e.g. {@link MsgReadDevice}).
     */
    public final Object value;

    /**
     * {@code true} when the message is generated in this ExEn,
     * {@code false} when it comes from another (via network).
     */
    public final boolean isOwn;

    //------------------------------------------------------------------------//

    /**
     * Returns the JSON object that represents this Message instance.
     *
     * @return The JSON object that represents this Message instance.
     */
    public JsonObject toJSON()
    {
        JsonObject jo = Json.object()
                            .add( sWHEN, when )
                            .add( sNAME, name );

        if( value != null )
            jo.add( sVALUE, UtilType.toJson( value ) );

        return jo;
    }

    @Override
    public String toString()    // Only needed here (not needed in subclasses)
    {
        return toJSON().toString();
    }

    //------------------------------------------------------------------------//
    // PROTECTED SCOPE

    /**
     * Creates a new message with the given device/command name, value and origin.
     *
     * @param name  Device or command name.
     * @param value Value carried by this message, or {@code null} when not applicable.
     * @param isOwn {@code true} when the message is generated in this ExEn,
     *              {@code false} when it comes from another (via network).
     */
    protected Message( String name, Object value, boolean isOwn )
    {
        this.name  = name;
        this.value = value;
        this.isOwn = isOwn;
    }
}