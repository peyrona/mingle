
package com.peyrona.mingle.lang.japi;

import com.eclipsesource.json.Json;
import com.eclipsesource.json.JsonArray;
import com.eclipsesource.json.JsonObject;
import com.eclipsesource.json.JsonObject.Member;
import com.eclipsesource.json.JsonValue;
import com.peyrona.mingle.lang.MingleException;
import com.peyrona.mingle.lang.interfaces.ICmdEncDecLib;
import com.peyrona.mingle.lang.interfaces.commands.ICommand;
import com.peyrona.mingle.lang.messages.Message;
import com.peyrona.mingle.lang.messages.MsgAbstractOne;
import com.peyrona.mingle.lang.messages.MsgAbstractTwo;
import com.peyrona.mingle.lang.messages.MsgChangeActuator;
import com.peyrona.mingle.lang.messages.MsgDeviceChanged;
import com.peyrona.mingle.lang.messages.MsgDeviceReaded;
import com.peyrona.mingle.lang.messages.MsgReadDevice;
import com.peyrona.mingle.lang.messages.MsgTrigger;
import java.util.ArrayList;
import java.util.List;

/**
 * ExEn can receive the requests (commands) defined in this class.<br>
 * ExEn reacts to every command by executing the appropriate action(s).
 * <p>
 * Commands are JSON objects with the form: { "command_name": {pay_load} }.
 *
 * @author Francisco José Morero Peyrona
 *
 * Official web site at: <a href="https://mingle.peyrona.com">https://mingle.peyrona.com</a>
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
                                /** Used by ExEn to report an internal error occurred in ExEn (this is not a real request)*/                                 Error,
                                /** Used by ExEn to report an internal error occurred in ExEn when adding commands (this is not a real request)*/            ErrorAdding,
                                /** Used by ExEn to report an internal error occurred in ExEn when deleting commands (this is not a real request) */         ErrorDeleting
                               };

    //------------------------------------------------------------------------//

    public final Request       request;
    public final JsonValue     payload;   // Can be null
    public       ICmdEncDecLib pclBuilder = null;

    //------------------------------------------------------------------------//
    // STATIC INTERFACE

    public static ExEnComm fromJSON( String sJSON )
    {
        Member member = Json.parse( sJSON )
                            .asObject()
                            .iterator()
                            .next();

        return new ExEnComm( Request.valueOf( member.getName() ), (member.getValue().isNull() ? Json.NULL : member.getValue()) );
    }

    public static ExEnComm asError( String sErrorMsg )
    {
        return new ExEnComm( Request.Error, Json.value( sErrorMsg ) );
    }

    //------------------------------------------------------------------------//

    /**
     *
     * @param verb  One of ::Request
     * @param sJSON Can be null
     */
    public ExEnComm( Request verb, String sJSON )
    {
        this.request = verb;
        this.payload = (sJSON == null ? null : Json.parse( sJSON ));
    }

    public ExEnComm( Request verb, ICommand cmd )
    {
        this.request = verb;
        this.payload = Json.parse( getCIL().unbuild( cmd ) );
    }

    public ExEnComm( Request verb, ICommand[] aCmds )
    {
        JsonArray ja = Json.array();

        this.request = verb;
        this.payload = ja;

        for( ICommand cmd : aCmds )
            ja.add( Json.parse( getCIL().unbuild( cmd ) ) );
    }

    public ExEnComm( Message msg )
    {
             if( msg instanceof MsgChangeActuator ) request = ExEnComm.Request.Change;
        else if( msg instanceof MsgDeviceChanged  ) request = ExEnComm.Request.Changed;
        else if( msg instanceof MsgTrigger        ) request = ExEnComm.Request.Execute;
        else if( msg instanceof MsgReadDevice     ) request = ExEnComm.Request.Read;     // This two are
        else if( msg instanceof MsgDeviceReaded   ) request = ExEnComm.Request.Readed;   // less frequent
        else throw new MingleException( MingleException.SHOULD_NOT_HAPPEN );

        this.payload = msg.toJSON();
    }

    public ExEnComm( Exception exc  )
    {
        this( Request.Error, Json.value( UtilStr.toString( exc ) ) );
    }

    //------------------------------------------------------------------------//

    public String getErrorMsg()
    {
        assert request == Request.Error;

        return payload.asString();
    }

    public long getWhen()
    {
        if( request == Request.List || request == Request.Listed )
            return 0;

        long nWhen = new UtilJson( payload ).getLong( Message.sWHEN, -1l );

        return ((nWhen == -1) ? System.currentTimeMillis() : nWhen);
    }

    public String getDeviceName()
    {
        if( request == Request.List || request == Request.Listed )
            return "List have no device";

        return new UtilJson( payload ).getString( Message.sNAME, null );
    }

    public Pair<String,Object> getChange()
    {
        if( payload == null )
            return new Pair( "No device", "No payload" );

        JsonObject joPayload = payload.asObject();

        String sDevice = joPayload.getString( MsgAbstractOne.sNAME, null );

        if( sDevice == null )
            return newMsgPropNotFound( MsgAbstractOne.sNAME );

        Object oValue = UtilType.toUne( joPayload.get( MsgAbstractTwo.sVALUE ) );

        if( oValue == null )
            return newMsgPropNotFound( MsgAbstractTwo.sVALUE );

        return new Pair( sDevice, oValue );
    }

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

    public JsonValue toJSON()
    {
        return Json.object()
                   .add( request.name(), ((payload == null) ? Json.NULL : payload) );
    }

    @Override
    public String toString()
    {
        return toJSON().toString();
    }

    //------------------------------------------------------------------------//

    private ExEnComm( Request verb, JsonValue payload )
    {
        this.request = verb;
        this.payload = payload;
    }

    private ICmdEncDecLib getCIL()
    {
        if( pclBuilder == null )
        {
            synchronized( this )
            {
                if( pclBuilder == null )
                    this.pclBuilder = UtilSys.getConfig().newCILBuilder();
            }
        }

        return pclBuilder;
    }

    private Pair newMsgPropNotFound( String property )    // To save RAM
    {
        return new Pair( "_error_", "Property \""+ property +"\" not found in: "+ payload.asObject() +", or its value is null" );
    }
}
