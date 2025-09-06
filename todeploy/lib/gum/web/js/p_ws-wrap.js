//----------------------------------------------------------------------------//
// Class WebSocketWrap
//
// A simple wrapper to easily handle WebSockets.
// This class does not use any external library, not even JQuery.
//
// Note: WebBrowsers automatically close all WebSockets when page is closed.
//----------------------------------------------------------------------------//

"use strict";

/**
 * @class WebSocketWrap
 * @classdesc A simple wrapper to easily handle WebSockets.
 */
class WebSocketWrap
{
    /**
     * Class constructor.
     *
     * @param {string} uri Server side to connect to.
     * @returns {WebSocketWrap} An instance of this class.
     */
    constructor( uri )
    {
        if( ! WebSocketWrap.isSupported() )
            throw "Error: WebSocket is not supported in this browser";

        if( (typeof( uri ) === 'undefined') )
            throw "Error: URI is undefined";

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

    /**
     * Establishes the connection to the WebSocket server.
     */
    connect()
    {
        // Guarantees that this instance will have only one connection open
        if( this.isOpen() )
            return;

        // Here can not be null, but can be undefined
        if( typeof this.URI === "undefined" )
            return;

        // Creates a new websocket instance
        this.websocket = new WebSocket( this.URI );

        // Registering: onopen, onmessage, onclose and onerror as "listeners"
        this.websocket.onopen = ( event ) =>
            {
                this.log( "WebSocket connected to: "+ this.URI, "connected" );

                for( let n = 0; n < this.onOpen.length; n++ )
                    this.onOpen[n]( event.data );
            };

        this.websocket.onclose = ( event ) => 
            {
                for( let n = 0; n < this.onClose.length; n++ )
                    this.onClose[n]( event );
                
                if( event.wasClean )
                {
                    this.log( "WebSocket: disconnected (Code = "+ event.code +" | Reason = "+ event.reason +")", "closed" );
                }
                else
                {
                    if( event.code === 1006 )     // A close code of 1006 is an abnormal closure, typical on page refresh.
                    {                             // Treat it as expected and avoid showing a scary error message.
                        this.log( "WebSocket: connection closed abnormally (likely page refresh).", "closed" );
                    }
                    else                          // For other unclean closures, trigger the generic error handler.
                    {
                        for( let n = 0; n < this.onError.length; n++ )
                            this.onError[n]( event );
                    }
                }
            };

        this.websocket.onmessage = ( event ) =>
            {
                this.log( "WebSocket: message arrived "+ event.data, "received" );

                for( let n = 0; n < this.onMessage.length; n++ )
                    this.onMessage[n]( event );
            };

        this.websocket.onerror = ( event ) =>
            {
                this.log( "Error "+ event, "error" );

                for( let n = 0; n < this.onError.length; n++ )
                    this.onError[n]( event );
            };
    }

    // Instrumenting functions -----------------------

    /**
     * Checks if the WebSocket connection is open.
     * @returns {boolean} True if the connection is open, false otherwise.
     */
    isOpen()
    {
        return (this.websocket !== null) &&
               (this.websocket.readyState !== this.websocket.CLOSED);
    }

    /**
     * Checks if the WebSocket connection is ready to send messages.
     * @returns {boolean} True if the connection is ready, false otherwise.
     */
    isReady()
    {
        return (this.websocket !== null) &&
               (this.websocket.readyState === 1);    // 1 == OPEN
    }

    /**
     * Checks if a logger function is available.
     * @returns {boolean} True if a logger is available, false otherwise.
     */
    canLog()
    {
        return this.logger.length > 0;
    }

    /**
     * Logs a message using the registered logger functions.
     * @param {string} text The message to log.
     * @param {string} sender The source of the message.
     */
    log( text, sender )
    {
        if( this.canLog() )
        {
            for( var n = 0; n < this.logger.length; n++ )
                this.logger[n]( text, sender );
        }
    }

    /**
     * Sends a message to the WebSocket server.
     * @param {string} text The message to send.
     */
    send( text )
    {
        if( this.isReady() )
        {
            this.websocket.send( text );
            this.log( "WS sent: "+ text, "sent" );
        }
        else
        {
            this.websocket.onerror( 'Can not send: "'+ text +'" because WebSocket is not ready.' );
        }
    }

    /**
     * Closes the WebSocket connection.
     */
    close()
    {
        if( this.websocket !== null )
        {
            this.websocket.close();
            this.websocket = null;
        }
    }

    // Callback functions -------------------------

    /**
     * Adds a callback function to be executed when the connection is opened.
     * @param {function} onOpen The callback function.
     */
    addOnOpen( onOpen )
    {
        if( typeof onOpen === 'function' )
            this.onOpen.push( onOpen );
    }

    /**
     * Adds a callback function to be executed when the connection is closed.
     * @param {function} onClose The callback function.
     */
    addOnClose( onClose )
    {
        if( typeof onClose === 'function' )
            this.onClose.push( onClose );
    }

    /**
     * Adds a callback function to be executed when a message is received.
     * @param {function} onMessage The callback function.
     */
    addOnMessage( onMessage )
    {
        if( typeof onMessage === 'function' )
            this.onMessage.push( onMessage );
    }

    /**
     * Adds a callback function to be executed when an error occurs.
     * @param {function} onError The callback function.
     */
    addOnError( onError )
    {
        if( typeof onError === 'function' )
            this.onError.push( onError );
    }

    /**
     * Adds a logger function.
     * @param {function} logger The logger function.
     */
    addLogger( logger )
    {
        if( typeof logger === 'function' )
            this.logger.push( logger );
    }

    /**
     * Checks if WebSockets are supported by the browser.
     * @returns {boolean} True if supported, false otherwise.
     */
    static isSupported()
    {
        return typeof window.WebSocket === 'function';
    }
}