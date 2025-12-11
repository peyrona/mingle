//---------------------------------------------------------------------------//
// GUM WEBSOCKETS
//
// There is only one connection from client to server (with the HTPP Server).
// Then, HTPP Server will contact with as many ExEns as requested.
//---------------------------------------------------------------------------//

/* global p_base, gum, p_app, gadget */

"use strict";

if( typeof gum_ws === "undefined" )
{
var gum_ws =
{
    socket    : null,
    aListener : [],     // Like: { id: uuid, fn: callback, exen: {host:<str>, port:<number>, ssl:boolean}, name: devName }
    fnOnList  : null,   // Function to be invoked when a 'Listed' response arrives from an ExEn.

    connect : function( fnOnConnected )
    {
        if( this.socket !== null )
            return;

        this.socket = new WebSocketWrap( "ws://"+ location.host +"/gum/bridge" );
        this.socket.addOnMessage( (msg) => gum_ws._onMessageReceived_( msg ) );
        this.socket.addOnError(   (str) => p_app.alert( (p_base.isEmpty( str ) ? "Unable to connect" : str), "WebSocket Error" ) );
        this.socket.addOnOpen(    fnOnConnected );
        this.socket.connect();
    },

    isConnected : function()
    {
        return this.socket !== null;
    },

    close : function()
    {
        if( this.socket !== null )
            this.socket.close();
    },

    setOnList( fn )
    {
        this.fnOnList = fn;
    },

    /**
     * Asynchronously requests for a list with all devices names (and other info) managed by a remote ExEn.
     *
     * @param {Object} oExEnAddr ExEn address. E.g.: {"host":<str>,"port":<number>,"ssl":boolean}
     */
    requestList : function( oExEnAddr )
    {
        this._sendMessage_( oExEnAddr, { List: null } );
    },

    /**
     * Asynchronously asks for a device's value.
     *
     * @param {Object} oExEnAddr ExEn address. E.g.: {"host":<str>,"port":<number>,"ssl":boolean}
     * @param {String} sDevice Device name.
     */
    requestValue : function( oExEnAddr, sDevice )
    {
        this._sendMessage_( oExEnAddr, { Read: { name: sDevice } } );
    },

    /**
     * Asynchronously requests for a device (an Actuator) to change its state.
     *
     * When sending a basic Une type (boolean, number, string), the 4th paramter can be ommited.
     * But when sending Une advanced types (date, time, list, pair) it should be be present. If
     * it is not, Arrays are sent as Une 'list' and Objects as Une 'pair'.
     *
     * If ommited, if value is an string and its content is "true" or "false", value will be converted
     * into boolean. Same is the string contains a valid number: valus is converted into Number.
     *
     * @param {Object} oExEnAddr   ExEn address. E.g.: {"host":<str>,"port":<number>,"ssl":boolean}
     * @param {String} sActuator   Name of the device.
     * @param {Any}    xValue       New value.
     * @param {String} sValueClass Optional type of value (although recomended when the 'value' is a JS Object).
     */
    requestChange : function( oExEnAddr, sActuator, xValue, sValueClass = null )
    {
        if( p_base.isUndefined( sValueClass ) )
        {
            // If 'value' is Boolean or Number, there is nothing to do.

            if( p_base.isString( xValue ) )
            {
                let lower = xValue.toString().trim().toLowerCase();

                if( lower === 'true' || lower === 'false' )
                {
                    sValueClass = "boolean";
                }
                else
                {
                    let number = parseFloat( xValue );

                    sValueClass = Number.isNaN( number ) ? "string" : "number";
                }
            }
            else if( p_base.isArray( xValue ) )
            {
                sValueClass = "list";
            }
            else if( p_base.isObject( xValue ) )
            {
                sValueClass = "pair";
            }
            else if( xValue instanceof Date )
            {
                sValueClass = "date";
            }
            else
            {
                xValue = "";    // can not be null
            }
        }

        switch( sValueClass.toLowerCase() )
        {
            case "string" : /* nothing to do */                                                             break;
            case "boolean": xValue = xValue.toString().trim().toLowerCase() === 'true';                     break;
            case "mumber" :
            case "mumeric": xValue = parseFloat( xValue );                                                  break;
            case "date"   : xValue = { class: 'date', data: xValue.getTime() };                             break;
            case "time"   : xValue = { class: 'time', data: p_base.millisSinceMidnight( xValue ) / 1000 };  break;    // x / 1000 because my time() class uses seconds
            case "array"  :
            case "list"   : xValue = { class: 'list', data: xValue };                                       break;
            case "object" :
            case "pair"   : xValue = { class: 'pair', data: xValue };                                       break;
            default       : throw xValue +": unknown value type";
        }

        this._sendMessage_( oExEnAddr, { Change: { name: sActuator, value: xValue } } );
    },

    executeRuleOrScript : function( oExEnAddr, sName )
    {
        this._sendMessage_( oExEnAddr, { Execute: sName } );
    },

    /**
     * Registers a listener that will be informed when a gadget value changes at ExEn.
     * <br>
     * Device names can be a single string or an array or strings.
     * <br>
     * Note: when deviceName ends with '*' matches all devices which name start with previous to asterisk chars.
     *       v.g. "control_*" matches "control_A1", "control_A2", ...
     *
     * @param {String|Array} devNames One or more (an array) device name(s) the listener is interested in.
     * @param {Function} fnCallback Function to be invoked passing the arrived changes (new values).
     * @param {String} An optional UUID (by default the result of invoking p_base.uuid()).
     * @returns {String} An UUID to unequely identify the new listener.
     */
    addListener : function( oExEn, devNames, fnCallback, sUUID = p_base.uuid() )
    {
        if( p_base.isString( oExEn ) )
            oExEn = JSON.parse( oExEn.replace( /\\"/g, '"' ) );    // This 'replace(...)' removes extra '\'

        let devices = p_base.isArray( devNames) ? devNames : [devNames];

        for( const dev of devices )
            this.aListener.push( { id: sUUID, fn: fnCallback, exen: oExEn, name: dev } );

        return sUUID;
    },

    /**
     * Removes an existing listener from the listeners list.
     *
     * @param {String} sUUID The UUID returned by ::addListener(...) that uniquely identifies the listener to be removed.
     */
    delListener : function( sUUID )
    {
        for( let n = 0; n < this.aListener.length; n++ )
        {
            if( this.aListener[n].id === sUUID )
            {
                this.aListener.splice( n, 1 );
                return;
            }
        }
    },

    //------------------------------------------------------------------------//
    // FOLLOWING ARE ACCESORY INTERNAL FUNCTIONS
    //------------------------------------------------------------------------//

    _sendMessage_ : function( oExEnAddr, oMsg )
    {
        if( p_base.isString( oExEnAddr ) )
            oExEnAddr = JSON.parse( oExEnAddr.replace( /\\"/g, '"' ) );    // This 'replace(...)' removes extra '\'

        this.socket.send( JSON.stringify( { exen: oExEnAddr, msg: oMsg } ) );
    },

    _onMessageReceived_ : function( msg )      // msg is like this: { "exex": "localhost:55886", "Changed": {"when":1659613508344, "name":"clock", "value":1521284 } }"
    {
        if( (! msg.data) || (msg.data.trim().length === 0) )
            return;

        let jo      = JSON.parse( msg.data );
        let sAction = Object.keys( jo )[0];    // "List", "Changed", ...
        let payload = jo[sAction];             // 'payload' is {"when":1659613508344, "name":"clock", "value":1521284 }

        sAction = sAction.toLowerCase();       // Can not do it until 'payload' is extracted

        switch( sAction )
        {
            case 'readed':
            case 'changed':
                for( const l of this.aListener )    // A 'listener' in this.aListeners is:  { id: uuid, fn: callback, exen: {host:<str>, port:<number>, ssl:boolean}, name: devName }
                {
                    if( this._areSameName_( l.name, payload.name ) && this._areSameExEn_( l.exen, jo.exen ) )    // Comparing names is first because it is faster
                        l.fn( sAction, payload.when, payload.name, payload.value );
                }

                break;

            case 'listed':                                       // 'payload' is the list of devices
                this.fnOnList( jo.exen, payload );
                break;

            case 'change':                                       // Nothing to do here
                break;

            case 'error':                                        // 'payload' is the error description
                p_app.alert( payload, "Error detected at remote ExEn" );
                break;

            case 'added':
                gum.deviceAddedAtExEn( payload.name );
                break;

            case 'removed':                                      // 'payload' is the name of the removed device
                gum.deviceRemovedAtExEn( payload.name );
                break;

            default:
                console.log( "Unknown action: " + sAction );
                break;
        }
    },

    _areSameName_( nameListener, nameEvent )
    {
        if( nameListener === nameEvent )
            return true;

        if( p_base.isString( nameListener ) && p_base.isString( nameEvent ) && nameListener.endsWith( '*' ) )
            return nameEvent.startsWith( nameListener.substring( 0, nameListener.length - 1 ) );    // Removes '*'

        return false;
    },

    _areSameExEn_( exen1, exen2 )
    {
        if( exen1 === exen2 )
            return true;

        if( p_base.isString( exen1 ) )
            exen1 = JSON.parse( exen1 );

        if( p_base.isString( exen2 ) )
            exen2 = JSON.parse( exen2 );

        return p_base.jsonAreEquals( exen1, exen2 );
    }
};
}