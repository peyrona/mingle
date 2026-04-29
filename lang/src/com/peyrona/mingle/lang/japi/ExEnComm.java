
package com.peyrona.mingle.lang.japi;

import com.eclipsesource.json.Json;
import com.eclipsesource.json.JsonArray;
import com.eclipsesource.json.JsonObject;
import com.eclipsesource.json.JsonObject.Member;
import com.eclipsesource.json.JsonValue;
import com.peyrona.mingle.lang.interfaces.ICmdEncDecLib;
import com.peyrona.mingle.lang.interfaces.commands.ICommand;
import com.peyrona.mingle.lang.messages.Message;
import com.peyrona.mingle.lang.messages.MsgChangeActuator;
import com.peyrona.mingle.lang.messages.MsgDeviceChanged;
import com.peyrona.mingle.lang.messages.MsgDeviceReaded;
import com.peyrona.mingle.lang.messages.MsgExecute;
import com.peyrona.mingle.lang.messages.MsgReadDevice;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * ExEn can receive the requests (commands) defined in this class.<br>
 * ExEn reacts to every command by executing the appropriate action(s).
 * <p>
 * Commands are JSON objects with the form: { "command_name": {pay_load} }.
 *
 * @author Francisco José Morero Peyrona
 *
 * Official web site at: <a href="https://github.com/peyrona/mingle">https://github.com/peyrona/mingle</a>
 */
public class ExEnComm
{
    /** Requests that ExEn handles: they arrive from outside via network. */
    public static enum Request {                                                                                                                           // Responses to requests
                                /** As response to this request an ExEn returns a JSON array with all hosted ICommands */                                    List,
                                /** After successfully processed a List request, an ExEn broadcasts this command */                                          Listed,
                                /** As response to this request an ExEn appends received ICommand(s) to existing ones */                                     Add,
                                /** As response to this request an ExEn deletes one or more existing ICommands */                                            Remove,
                                /** As response to this request an ExEn places the appropriate message into the bus, so rules can react.<br>
                                  * An ExEn is informing a device changed its state
                                  * (the message is received by a different ExEn from the one that hosts the device).
                                  * This message's Payload is a JSON as follows: { "device": "device_name", "value": new_value } */        Changed,
                                /** As response to this request an ExEn places the appropriate message into the bus to change Actuator's state.<br>
                                  * A request to change an Actuator's state.
                                  * This message's Payload is a JSON as follows: { "device": "actuator_name", "value": new_value } */ Change,   // Note: changes in this ExEn's devices are managed by listening to Bus msgs
                                /** ExEn has to tell the drivers to read their device's value */                                                             Read,
                                /** A driver is informing that a new value was read: it can or can not change a device's state */                            Readed,
                                                                                                                                                              Execute,
                                /** As response to this request an ExEn gently finishes itself execution */                                                  Exit,
                                                                                                                                                           // Post request actions
                                /** After successfully processed an Add request, an ExEn broadcasts this command */                                          Added,
                                /** After successfully processed a Remove request, an ExEn broadcasts this command */                                        Removed,
                                                                                                                                                           // Other messages
                                /** Used by ExEn to report an internal error occurred in ExEn (this is not a real request)*/                                 Error
                               };

    //------------------------------------------------------------------------//

    public           final Request            request;
    public           final JsonValue          payload;                        // Can be null
    private static   final Map<Class,Request> mapRequest = new HashMap<>();   // Just to reduce CPU
    private volatile       ICmdEncDecLib      pclBuilder = null;

    //------------------------------------------------------------------------//
    // STATIC INTERFACE

    static
    {
        mapRequest.put( MsgDeviceChanged.class , Request.Changed );
        mapRequest.put( MsgChangeActuator.class, Request.Change  );
        mapRequest.put( MsgExecute.class       , Request.Execute );
        mapRequest.put( MsgReadDevice.class    , Request.Read    );
        mapRequest.put( MsgDeviceReaded.class  , Request.Readed  );
    }

    /**
     * Parses a JSON string and creates an {@code ExEnComm} instance.
     *
     * @param sJSON JSON string in format { "request_name": {pay_load} }.
     * @return An {@code ExEnComm} instance parsed from the JSON string.
     */
    public static ExEnComm fromJSON( String sJSON )
    {
        Member member = Json.parse( sJSON )
                            .asObject()
                            .iterator()
                            .next();

        return new ExEnComm( Request.valueOf( member.getName() ),
                             (member.getValue().isNull() ? Json.NULL : member.getValue()) );
    }

    /**
     * Creates an error {@code ExEnComm} with the specified error message.
     *
     * @param sErrorMsg The error message to include in the payload.
     * @return An {@code ExEnComm} instance with {@code Request.Error}.
     */
    public static ExEnComm asError( String sErrorMsg )
    {
        return new ExEnComm( Request.Error, Json.value( sErrorMsg ) );
    }

    //------------------------------------------------------------------------//

    /**
     * Class constructor.
     *
     * @param verb  One of ::Request
     * @param sJSON Can be null
     */
    public ExEnComm( Request verb, String sJSON )
    {
        request = verb;
        payload = (sJSON == null ? null : Json.parse( sJSON ));
    }

    /**
     * Class constructor.
     *
     * @param verb
     * @param cmd
     */
    public ExEnComm( Request verb, ICommand cmd )
    {
        request = verb;
        payload = Json.parse( getCIL().unbuild( cmd ) );
    }

    /**
     * Class constructor.
     *
     * @param verb
     * @param aCmds
     */
    public ExEnComm( Request verb, ICommand[] aCmds )
    {
        JsonArray ja = Json.array();

        request = verb;
        payload = ja;

        for( ICommand cmd : aCmds )
            ja.add( Json.parse( getCIL().unbuild( cmd ) ) );
    }

    /**
     * Class constructor.
     *
     * @param msg The message to wrap as an ExEn communication.
     */
    public ExEnComm( Message msg )
    {
        request = mapRequest.get( msg.getClass() );
        payload = msg.toJSON();
    }

    /**
     * Class constructor.
     *
     * @param exc
     */
    public ExEnComm( Exception exc  )
    {
        this( Request.Error, Json.value( UtilStr.toString( exc ) ) );
    }

    //------------------------------------------------------------------------//

    /**
     * Returns the error message from an error request.
     *
     * @return The error message string.
     * @throws AssertionError if this is not an {@code Request.Error}.
     */
    public String getErrorMsg()
    {
        assert request == Request.Error;

        return payload.asString();
    }

    /**
     * Returns the timestamp from the payload.
     * <p>
     * Returns {@code 0} for List/Listed requests.
     * Falls back to {@code System.currentTimeMillis()} if the payload
     * does not contain a timestamp.
     *
     * @return The timestamp in milliseconds.
     */
    public long getWhen()
    {
        if( request == Request.List || request == Request.Listed )
            return 0;

        if( payload == null || payload.isNull() )
            return System.currentTimeMillis();

        long nWhen = new UtilJson( payload ).getLong( Message.sWHEN, -1L );

        return (nWhen == -1) ? System.currentTimeMillis() : nWhen;
    }

    /**
     * Returns the device name from the payload, or {@code null} if the
     * payload does not contain one (e.g. List, Listed, Error requests).
     *
     * @return The device name, or {@code null}.
     */
    public String getDeviceName()
    {
        if( payload == null || payload.isNull() ||
            request == Request.List || request == Request.Listed )
            return null;

        return new UtilJson( payload ).getString( Message.sNAME, null );
    }

    /**
     * Returns the device value from the payload, or {@code null} if the
     * payload does not contain one.
     * <p>
     * The returned value is converted to its Une type via
     * {@link UtilJson#toUneType(JsonValue)}.
     *
     * @return The device value as a Une-compatible object, or {@code null}.
     */
    public Object getValue()
    {
        if( payload == null || payload.isNull() )
            return null;

        JsonValue jv = payload.asObject().get( Message.sVALUE );

        return (jv == null || jv.isNull()) ? null : UtilJson.toUneType( jv );
    }

    /**
     * Extracts device name and value from the payload for change-related requests.
     *
     * @return A {@code Pair<String, Object>} with device name and value,
     *         or {@code null} if the payload is missing or malformed.
     */
    public Pair<String,Object> getChange()
    {
        if( payload == null || payload.isNull() )
            return null;

        JsonObject joPayload = payload.asObject();

        String sDevice = joPayload.getString( Message.sNAME, null );

        if( sDevice == null )
            return null;

        Object oValue = UtilJson.toUneType( joPayload.get( Message.sVALUE ) );

        if( oValue == null )
            return null;

        return new Pair<>( sDevice, oValue );
    }

    /**
     * Extracts commands from the payload.
     *
     * @return A list of {@code ICommand} objects parsed from the payload.
     */
    public List<ICommand> getCommands()
    {
        List<ICommand> lstCmd  = new ArrayList<>();

        if( payload.isArray() )
        {
            JsonArray ja = (JsonArray) payload;

            for( int n = 0; n < ja.size(); n++ )
                lstCmd.add( getCIL().build( ja.get( n ).toString() ) );
        }
        else
        {
            lstCmd.add( getCIL().build( payload.toString() ) );
        }

        return lstCmd;
    }

    /**
     * Returns the JSON string representation of this communication.
     *
     * @return JSON string in format { "request_name": {pay_load} }.
     */
    @Override
    public String toString()
    {
        return Json.object()
                   .add( request.name(), ((payload == null) ? Json.NULL : payload) )
                   .toString();
    }

    //------------------------------------------------------------------------//
    // PRIVATE SCOPE

    private ExEnComm( Request verb, JsonValue payload )
    {
        this.request = verb;
        this.payload = payload;
    }

    private ICmdEncDecLib getCIL()
    {
        ICmdEncDecLib builder = this.pclBuilder;

        if( builder == null )
        {
            synchronized( this )
            {
                builder = this.pclBuilder;   // Re-check after acquiring the lock

                if( builder == null )
                    this.pclBuilder = builder = UtilSys.getConfig().newCILBuilder();
            }
        }

        return builder;
    }
}