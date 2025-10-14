//---------------------------------------------------------------------------//
// GUM WEBSOCKETS
//
// There is only one connection from client to server (with the HTPP Server).
// Then, HTPP Server will contact with as many ExEns as requested.
//---------------------------------------------------------------------------//

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

        // Create a unique tab identifier that persists for the session
        let tabId = sessionStorage.getItem('gum_tab_id');

        if( ! tabId || tabId.length < 5 )
        {
            tabId = Date.now() + '_' + Math.random().toString(36).substring(2,9);
            sessionStorage.setItem('gum_tab_id', tabId);
        }

        // Use the tab ID as part of the WebSocket path, not just query parameter
        const wsUrl = "ws://" + location.host + "/gum/bridge/" + tabId;
        this.socket = new WebSocketWrap( p_base.addCacheBuster( wsUrl ) );
        this.socket.addOnMessage( (msg) => gum_ws._onMessageReceived_( msg ) );
        this.socket.addOnOpen( fnOnConnected );
        this.socket.addOnError( (error) =>
                                {
                                    p_app.showEventError( error, "WebSocket Error" );

                                    if( error.target.readyState === WebSocket.CLOSED )
                                    {
                                        gum_ws.socket = null;   // As the socket is closed, I must set it to null

                                        if( error.wasClean )
                                            new AuthenticationService().throw401AjaxError();    // The authenticator will: throw the error, capture it and handle it
                                        else
                                            sessionStorage.removeItem('gum_tab_id');            // Clean up tab ID on abnormal closure
                                    }
                                }
                              );

        if( p_base.isLocalHost() )
        {
            this.socket.addLogger( (msg,sender) => {
                                                        if( sender && "connected closed".includes( sender ) )
                                                            console.log( msg );
                                                   } );
        }

        this.socket.connect();

        // Add page unload cleanup
        window.addEventListener('beforeunload', () =>   {
                                                            if( this.socket !== null )
                                                                this.socket.close();

                                                            sessionStorage.removeItem('gum_tab_id');
                                                        });
    },

    isConnected : function()
    {
        return this.socket !== null;
    },

    close : function()
    {
        if( this.socket !== null )
        {
            this.socket.close();
            this.socket = null;
        }

        // Clean up the persistent tab ID
        sessionStorage.removeItem('gum_tab_id');
    },

    /**
     * Received function is executed everytime a 'List' commands is sent here from an ExEn.
     */
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
     * @param {Object|String} xExEnAddr ExEn address. E.g.: {"host":<str>,"port":<number>,"ssl":boolean}
     * @param {String} sDevice Device name.
     */
    requestValue : function( xExEnAddr, sDevice )
    {
        this._sendMessage_( xExEnAddr, { Read: { name: sDevice } } );
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
     * @param {Any}    xValue      New value.
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
                    sValueClass = 'boolean';
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
                sValueClass = typeof xValue;
            }
        }

        switch( sValueClass.toLowerCase() )
        {
            case "string" : /* nothing to do */                                                             break;
            case "boolean": xValue = xValue.toString().trim().toLowerCase() === 'true';                     break;
            case "number" :
            case "numeric": xValue = parseFloat( xValue );                                                  break;
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
     * @param {Object} oExEn The target ExEn.
     * @param {String|Array} xDevNames One or more (an array) device name(s) the listener is interested in ('*' can be used).
     * @param {Function} fnCallback Function to be invoked passing the arrived changes (new values).
     * @param {String|Array} sAction Which action or actions the listener is interested in ("readed", "changed", ...). Defaults to '*'.
     * @returns {String} An UUID to unequely identify the new listener.
     */
    addListener : function( oExEn, xDevNames, fnCallback, sAction = '*' )
    {
        if( p_base.isEmpty( oExEn ) || p_base.isEmpty( xDevNames ) || p_base.isEmpty( fnCallback ) )
            throw "ExEn, device name(s) and callback function are required to add a listener";

        if( p_base.isString( oExEn ) )
            oExEn = JSON.parse( oExEn.replace( /\\"/g, '"' ) );    // This 'replace(...)' removes extra '\'

        let sUUID   = p_base.uuid();
        let devices = p_base.isArray( xDevNames) ? xDevNames : [xDevNames];

        for( const dev of devices )
        {
            this.aListener.push( { id    : sUUID,
                                   fn    : fnCallback,
                                   exen  : oExEn,
                                   name  : dev,
                                   action: sAction.toLowerCase() } );
        }

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

    _sendMessage_ : function( xExEnAddr, oMsg )
    {
        if( p_base.isString( xExEnAddr ) )
            xExEnAddr = JSON.parse( xExEnAddr.replace( /\\"/g, '"' ) );    // This 'replace(...)' removes extra '\'

        this.socket.send( JSON.stringify( { exen: xExEnAddr, msg: oMsg } ) );
    },

    _onMessageReceived_ : function( msg )      // msg is like this: { "exen": { host: 192.168.1.7, port: 55886, ssl: false }, "Changed": {"when":1659613508344, "name":"clock", "value":1521284 } }"
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
                this._triggerListeners_( jo.exen, sAction, payload );
                break;

            case 'listed':
                this.fnOnList( jo.exen, payload );   // 'payload' is the list of devices
                break;

            case 'change':                           // Nothing to do: I do not see this is going to be used
                break;

            case 'error':                            // 'payload' is the error description
                p_app.alert( payload, "Error detected at ExEn: "+ jo.exen );
                break;

            case 'added':
                gum.deviceAddedAtExEn( payload.name );
                break;

            case 'removed':
                gum.deviceRemovedAtExEn( payload.name );
                break;

            // default: --> v.g. 'read' (to read the value of a device) can arrive here
            // console.log( "Action: " + sAction );
            //    break;
        }
    },

    _triggerListeners_ : function( oExEn, sAction, payload )
    {
        for( const l of this.aListener )    // A 'listener' in this.aListeners is:  { id: uuid, fn: callback, exen: {host:<str>, port:<number>, ssl:boolean}, name: devName }
        {
            if( this._areSameAction_( l.action, sAction )      &&    // From faster to slower
                this._areSameName_(   l.name  , payload.name ) &&
                this._areSameExEn_(   l.exen  , oExEn ) )
            {
                l.fn( sAction, payload );
            }
        }
    },

    _areSameAction_ : function( actListener, actEvent )
    {
        if( actListener === '*' || actListener === actEvent )
            return true;

        if( actListener.endsWith( '*' ) )                                                                        // see ::_areSameName_(...)
            return actEvent.startsWith( actListener.substring( 0, actListener.length - 1 ) );

        return false;
    },

    _areSameName_ : function( nameListener, nameEvent )
    {
        if( nameListener === '*' )
            return true;

        if( nameListener === nameEvent )
            return true;

        if( p_base.isString( nameListener ) && p_base.isString( nameEvent ) && nameListener.endsWith( '*' ) )   // v.g. 'light_*'
            return nameEvent.startsWith( nameListener.substring( 0, nameListener.length - 1 ) );                // -1 to remove '*' (-1, not -2)

        return false;
    },

    _areSameExEn_ : function( exen1, exen2 )
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