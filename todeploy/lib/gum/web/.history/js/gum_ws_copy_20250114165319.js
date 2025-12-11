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
    socket : null,

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
        // if( p_base.isUndefined( sValueClass ) )
        // {
        //     // If 'value' is Boolean or Number, there is nothing to do.

        //     if( p_base.isString( xValue ) )
        //     {
        //         let lower = xValue.toString().trim().toLowerCase();

        //         if( lower === 'true' || lower === 'false' )
        //         {
        //             sValueClass = "boolean";
        //         }
        //         else
        //         {
        //             let number = parseFloat( xValue );

        //             sValueClass = Number.isNaN( number ) ? "string" : "number";
        //         }
        //     }
        //     else if( p_base.isArray( xValue ) )
        //     {
        //         sValueClass = "list";
        //     }
        //     else if( p_base.isObject( xValue ) )
        //     {
        //         sValueClass = "pair";
        //     }
        //     else if( xValue instanceof Date )
        //     {
        //         sValueClass = "date";
        //     }
        //     else
        //     {
        //         xValue = "";    // can not be null
        //     }
        // }

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

    // executeRuleOrScript : function( oExEnAddr, sName )
    // {
    //     this._sendMessage_( oExEnAddr, { Execute: sName } );
    // },

    //------------------------------------------------------------------------//
    // FOLLOWING ARE ACCESORY INTERNAL FUNCTIONS
    //------------------------------------------------------------------------//

    _sendMessage_ : function( oExEnAddr, oMsg )
    {
        if( p_base.isString( oExEnAddr ) )
            oExEnAddr = JSON.parse( oExEnAddr.replace( /\\"/g, '"' ) );    // This 'replace(...)' removes extra '\'

        this.socket.send( JSON.stringify( { exen: oExEnAddr, msg: oMsg } ) );
    }
};
}