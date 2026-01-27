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

        // Heartbeat properties for connection health monitoring
        this.pingIntervalId  = null;
        this.pongTimeoutId   = null;
        this.lastPongTime    = 0;
        this.PING_INTERVAL_MS = 20000;   // Send ping every 20 seconds
        this.PONG_TIMEOUT_MS  = 10000;   // Expect pong within 10 seconds
        this.heartbeatEnabled = true;    // Can be disabled if needed
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
                this.startHeartbeat();

                for( let n = 0; n < this.onOpen.length; n++ )
                    this.onOpen[n]( event.data );
            };

        this.websocket.onclose = ( event ) =>
            {
                this.stopHeartbeat();

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
                // Check for PONG response (heartbeat)
                if( this.handlePong( event.data ) )
                    return;

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
        this.stopHeartbeat();

        if( this.websocket !== null )
        {
            this.websocket.close();
            this.websocket = null;
        }
    }

    // Heartbeat functions -------------------------

    /**
     * Starts the heartbeat mechanism to monitor connection health.
     * Sends PING messages at regular intervals and expects PONG responses.
     */
    startHeartbeat()
    {
        if( ! this.heartbeatEnabled )
            return;

        this.stopHeartbeat();
        this.lastPongTime = Date.now();

        this.pingIntervalId = setInterval( () => this.sendPing(), this.PING_INTERVAL_MS );
        this.log( "Heartbeat started (ping every " + (this.PING_INTERVAL_MS / 1000) + "s)", "heartbeat" );
    }

    /**
     * Stops the heartbeat mechanism.
     */
    stopHeartbeat()
    {
        if( this.pingIntervalId !== null )
        {
            clearInterval( this.pingIntervalId );
            this.pingIntervalId = null;
        }

        if( this.pongTimeoutId !== null )
        {
            clearTimeout( this.pongTimeoutId );
            this.pongTimeoutId = null;
        }
    }

    /**
     * Sends a PING message to the server.
     */
    sendPing()
    {
        if( ! this.isReady() )
            return;

        const pingMsg = JSON.stringify( { type: "PING", ts: Date.now() } );
        this.websocket.send( pingMsg );
        this.log( "PING sent", "heartbeat" );

        // Set timeout for PONG response
        this.pongTimeoutId = setTimeout( () => this.onPongTimeout(), this.PONG_TIMEOUT_MS );
    }

    /**
     * Handles a PONG message from the server.
     * @param {string} data The received message data.
     * @returns {boolean} True if the message was a PONG, false otherwise.
     */
    handlePong( data )
    {
        try
        {
            const msg = JSON.parse( data );

            if( msg.type === "PONG" )
            {
                this.lastPongTime = Date.now();

                if( this.pongTimeoutId !== null )
                {
                    clearTimeout( this.pongTimeoutId );
                    this.pongTimeoutId = null;
                }

                this.log( "PONG received (latency: " + (this.lastPongTime - msg.ts) + "ms)", "heartbeat" );
                return true;
            }
        }
        catch( e )
        {
            // Not JSON or not a PONG message, continue normal processing
        }

        return false;
    }

    /**
     * Called when a PONG response is not received within the timeout period.
     * Triggers connection close to allow reconnection.
     */
    onPongTimeout()
    {
        this.log( "PONG timeout - connection appears dead", "heartbeat" );
        this.pongTimeoutId = null;

        // Force close to trigger reconnection
        if( this.websocket !== null )
        {
            this.websocket.close( 4000, "Heartbeat timeout" );
        }
    }

    /**
     * Enables or disables heartbeat monitoring.
     * @param {boolean} enabled True to enable, false to disable.
     */
    setHeartbeatEnabled( enabled )
    {
        this.heartbeatEnabled = enabled;

        if( ! enabled )
            this.stopHeartbeat();
        else if( this.isReady() )
            this.startHeartbeat();
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