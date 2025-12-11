//----------------------------------------------------------------------------//
// Class WebSocketWrap
//
// A simple wrapper to easily handle WebSockets.
// This class does not use any external library, not even JQuery.
// Note: WebBrowsers automatically close all WebSockets when page is closed.
//----------------------------------------------------------------------------//

"use strict";

/**
 * Class constructor.
 *
 * @param {type} uri Server sido to connect to.
 * @returns {Object} An instance of this class.
 */
function WebSocketWrap( uri )
{
    if( ! WebSocketWrap.isSupported() )
    {
        console.log( "Error: WebSocket is not supported in this browser" );
        return;
    }

    if( (typeof( uri ) === 'undefined') )
    {
        throw "Error: URI is undefined";
    }

    this.URI       = uri;
    this.websocket = null;           // La instancia de WebSocket que esta clase encapsula
    this.onOpen    = new Array();    // Funciones invocadas cuando se conecta con el servidor
    this.onClose   = new Array();    // Funciones invocadas cuando se desconecta con el servidor
    this.onMessage = new Array();    // Funciones invocadas cuando se recibe un mensaje desde el servidor
    this.onError   = new Array();    // Funciones invocadas cuando se produce un error en el cliente
    this.logger    = new Array();    // Funciones invocadas para hacer logging en el cliente
}

//----------------------------------------------------------------------------//

// Connect function -----------------------

WebSocketWrap.prototype.connect = function()
{
    // Guarantees that this instance will have only one connection open
    if( this.isOpen() )
    {
        return;
    }

    // Here can not be null, but can be undefined
    if( typeof this.URI === "undefined" )
    {
        return;
    }

    let self = this;

    // Creates a new websocket instance
    this.websocket = new WebSocket( this.URI );

    // Registering: onopen, onmessage, onclose and onerror as "listeners"
    this.websocket.onopen = function( event )
    {
        if( self.canLog() )
            self.log( "WebSocket connected to: "+ self.URI );

        for( let n = 0; n < self.onOpen.length; n++ )
        {
            self.onOpen[n]( event.data );
        }
    };

    this.websocket.onmessage = function( event )
    {
        if( self.canLog() )
            self.log( "WebSocket: message arrived "+ event.data );

        for( let n = 0; n < self.onMessage.length; n++ )
        {
            self.onMessage[n]( event );
        }
    };

    this.websocket.onclose = function( event )
    {
        if( self.canLog() )
        {
            if( event.wasClean )
                this.log( "WebSocket: disconnected (Code = "+ event.code +" | Reason = "+ event.reason +")" );
            else     // e.g. server process killed or network down; event.code is usually 1006 in this case
                this.log( "WebSocket: disconnected: Connection died (not clean)" );
        }

        for( let n = 0; n < self.onClose.length; n++ )
        {
            self.onClose[n]( event );
        }
    };

    this.websocket.onerror = function( event )
    {
        if( self.canLog() )
            self.log( "Error "+ event.data );

        for( let n = 0; n < self.onError.length; n++ )
        {
            self.onError[n]( event.data );
        }
    };
};

// Instrumenting functions -----------------------

WebSocketWrap.prototype.isOpen = function()
{
    return (this.websocket !== null) &&
           (this.websocket.readyState !== this.websocket.CLOSED);
};

WebSocketWrap.prototype.isReady = function()
{
    return (this.websocket !== null) &&
           (this.websocket.readyState === 1);    // 1 == OPEN
};

WebSocketWrap.prototype.canLog = function( text )
{
    return this.logger.length > 0;
};

WebSocketWrap.prototype.log = function( text )
{
    for( var n = 0; n < this.logger.length; n++ )
    {
        this.logger[n]( text );
    }
};

WebSocketWrap.prototype.send = function( text )
{
    if( this.isReady() )
    {
        this.websocket.send( text );

        if( this.canLog() )
            this.log( "WS sent: "+ text );
    }
    else
    {
        if( this.canLog() )
            this.log( "Can not send: '"+ text +' because WebSocket is not ready.' );
    }
};

WebSocketWrap.prototype.close = function()
{
    if( this.websocket !== null )
    {
        this.websocket.close();
        this.websocket = null;
    }
};

// Callback functions -------------------------

WebSocketWrap.prototype.addOnOpen = function( onOpen )
{
    if( typeof onOpen === 'function' )
    {
        this.onOpen.push( onOpen );
    }
};

WebSocketWrap.prototype.addOnClose = function( onClose )
{
    if( typeof onClose === 'function' )
    {
        this.onClose.push( onClose );
    }
};

WebSocketWrap.prototype.addOnMessage = function( onMessage )
{
    if( typeof onMessage === 'function' )
    {
        this.onMessage.push( onMessage );
    }
};

WebSocketWrap.prototype.addOnError = function( onError )
{
    if( typeof onError === 'function' )
    {
        this.onError.push( onError );
    }
};

WebSocketWrap.prototype.addLogger = function( logger )
{
    if( typeof logger === 'function' )
    {
        this.logger.push( logger );
    }
};

// This is a "static" function (does not include 'prototype' keyword)
WebSocketWrap.isSupported = function()
{
    if( (! window.WebSocket) && window.MozWebSocket )
    {
        window.WebSocket = window.MozWebSocket;
    }

    return window.WebSocket;
};
