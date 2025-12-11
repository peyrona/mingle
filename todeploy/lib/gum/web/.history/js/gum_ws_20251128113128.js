//-------------------------------------------------------------------------------------------//
// GUM WEBSOCKETS
//-------------------------------------------------------------------------------------------//
//
// There is only one connection from client to server (with the HTTP Server).
// Then, HTTP Server will communicate with as many ExEns as requested.
//
// IMPLEMENTATION NOTES
// --------------------
// To support multiple design tabs each with its own WebSocket connection,
// and multiple preview tabs sharing the design tab's connection, the following
// changes were made:
//
// 1. WebSocket Connection Logic (gum_ws.js)
//    Design Mode:
//    - Generates unique UUID using p_base.uuid() for each design tab
//    - Stores in sessionStorage as gum_tab_id_design
//    - Each design tab gets its own WebSocket connection
//    Preview Mode:
//    - Inherits parent design tab's ID from parentTabId URL parameter
//    - Falls back to generating its own ID if parameter not found
//    - All previews from same design share the same WebSocket connection
//
// 2. Preview Window Opening (gum.js)
//    - Modified _onPreview_ method to pass parent design tab ID via URL parameter
//    - URL format: ?design=false&parentTabId=<design-tab-uuid>
//
// 3. Cleanup Logic
//    - Updated cleanup code to remove appropriate storage key based on mode
//    - Design tabs clean up gum_tab_id_design
//    - Preview tabs clean up gum_tab_id_preview (fallback case)
//
// How It Works
//    1. Design Tab A opens → generates UUID abc-123 → WebSocket: /gum/bridge/abc-123
//    2. Design Tab B opens → generates UUID def-456 → WebSocket: /gum/bridge/def-456
//    3. Preview from Tab A opens → inherits abc-123 → WebSocket: /gum/bridge/abc-123
//    4. Another Preview from Tab A opens → inherits abc-123 → WebSocket: /gum/bridge/abc-123
//    5. Preview from Tab B opens → inherits def-456 → WebSocket: /gum/bridge/def-456
//
// Message Distribution Summary
// ----------------------------
// Server sends 1 message
//     ↓
// WebSocket: 1 connection (shared tab ID)
//     ↓
// Design Tab   : receives 1 time
// Preview Tab 1: receives 1 time
// Preview Tab 2: receives 1 time
//
// Because the design tab WebSocket connection is shared among its previews, messages are
// received only once but executed by ::_onMessageReceived_(...) in all associated tabs.
//-------------------------------------------------------------------------------------------//

"use strict";

if( typeof gum_ws === "undefined" )
{
var gum_ws =
{
    // Context-aware state management for multiple dashboard instances
    contexts  : new Map(),     // dashboardId -> {socket, listeners, isDesign, parentContextId}
    currentContext : null,      // Active context for this tab

    // Legacy properties for backward compatibility (deprecated)
    socket    : null,
    aListener : [],     // Like: { id: uuid, fn: callback, exen: {host:<str>, port:<number>, ssl:boolean}, name: devName }
    fnOnList  : null,   // Function to be invoked when a 'Listed' response arrives from an ExEn.

    //------------------------------------------------------------------------//

    connect : function( fnOnConnected )
    {
        // Create a unique tab identifier that persists for the session
        // Design tabs generate their own UUID, preview tabs inherit parent's ID
        let tabId;
        let isDesign = gum.isInDesignMode();

        if( isDesign )
        {
            // Design mode: generate unique UUID for this design tab
            tabId = sessionStorage.getItem('gum_tab_id_design');

            if( ! tabId || tabId.length < 5 )
            {
                tabId = p_base.uuid();  // Generate proper 36-char UUID
                sessionStorage.setItem('gum_tab_id_design', tabId);
            }
        }
        else
        {
            // Preview mode: inherit parent design tab's ID from URL parameter
            const urlParams = new URLSearchParams( window.location.search );
            tabId = urlParams.get('parentTabId');

            if( ! tabId )
            {
                // Fallback: generate own ID if parentTabId not found
                tabId = sessionStorage.getItem('gum_tab_id_preview');

                if( ! tabId || tabId.length < 5 )
                {
                    tabId = p_base.uuid();
                    sessionStorage.setItem('gum_tab_id_preview', tabId);
                }
            }
        }

        // Create context for this dashboard
        const dashboardId = tabId; // Use tabId as dashboard ID for context management
        const context = this._createContext_( dashboardId, isDesign, isDesign ? null : tabId );

        // WebSocket connection management with sharing logic
        if( isDesign )
        {
            // Design mode: create new WebSocket connection
            const wsUrl = "ws://" + location.host + "/gum/bridge/" + tabId;
            context.socket = new WebSocketWrap( p_base.addCacheBuster( wsUrl ) );
        }
        else
        {
            // Preview mode: share parent's WebSocket connection
            const parentContext = this._getContext_( context.parentContextId );
            if( parentContext && parentContext.socket )
            {
                // Parent exists and has valid socket - share it
                context.socket = parentContext.socket;
            }
            else
            {
                // Parent doesn't exist OR parent socket is null - create own connection
                const wsUrl = "ws://" + location.host + "/gum/bridge/" + tabId;
                context.socket = new WebSocketWrap( p_base.addCacheBuster( wsUrl ) );
            }
        }

        // Set up message handlers with context filtering
        if( context.socket )
        {
            context.socket.addOnMessage( (msg) => { // Only process messages for current context to prevent cross-contamination
                                                    if( this._getCurrentContext_() && this._getCurrentContext_().dashboardId === dashboardId )
                                                    {
                                                        this._onMessageReceived_( msg );
                                                    }
                                                  } );

            context.socket.addOnOpen( fnOnConnected );

            context.socket.addOnError( (error) =>
                                    {
                                        p_app.showEventError( error, "WebSocket Error" );

                                        if( error.target && error.target.readyState === WebSocket.CLOSED )
                                        {
                                            context.socket = null;   // Clear socket reference in context

                                            if( error.wasClean )
                                            {
                                                try
                                                {
                                                    new AuthenticationService().throw401AjaxError();    // The authenticator will: throw the error, capture it and handle it
                                                }
                                                catch( e )
                                                {
                                                    console.error( "Authentication service error:", e );
                                                }
                                            }
                                            else
                                            {
                                                // Clean up appropriate tab ID based on mode
                                                if( isDesign )
                                                    sessionStorage.removeItem('gum_tab_id_design');
                                                else
                                                    sessionStorage.removeItem('gum_tab_id_preview');
                                            }
                                        }
                                    } );
        }

        if( p_base.isLocalHost() && context.socket && isDesign )
        {
            context.socket.addLogger( (msg,sender) =>
                                                    {
                                                        if( sender && "connected closed".includes( sender ) )
                                                            console.log( msg );
                                                    } );
        }

        // Only connect if this context owns the WebSocket connection
        // OR if we're sharing a parent's socket that hasn't been connected yet
        const ownsSocket  = isDesign || ! parentContext;
        const shareUnconn = ! isDesign && parentContext && ! parentContext.socket;   // Sharing Unconnected Socket

        if( ownsSocket || shareUnconn )
        {
            if( context.socket )
                context.socket.connect();
        }

        // Update legacy properties for backward compatibility
        this.socket    = context.socket;
        this.aListener = context.listeners;

        // Add page unload cleanup
        const cleanupHandler = () =>{
                                        if( context.socket !== null )
                                            context.socket.close();

                                        // Clean up appropriate tab ID based on mode
                                        if( isDesign )
                                            sessionStorage.removeItem('gum_tab_id_design');
                                        else
                                            sessionStorage.removeItem('gum_tab_id_preview');

                                        // Remove context on page unload
                                        this.removeContext( dashboardId );
                                    };

        window.addEventListener('beforeunload', cleanupHandler );

        // Store cleanup handler for potential removal
        context._cleanupHandler = cleanupHandler;
    },

    close : function()
    {
        if( this.socket !== null )
        {
            this.socket.close();
            this.socket = null;
        }

        // Clean up the persistent tab IDs
        sessionStorage.removeItem('gum_tab_id_design');
        sessionStorage.removeItem('gum_tab_id_preview');
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
     * @param {Object|String} xExEnAddr ExEn address. E.g.: {"host":<str>,"port":<number>,"ssl":boolean} or JSON string
     * @param {String} sDevice Device name.
     */
    requestValue : function( xExEnAddr, sDevice )
    {
        this._sendMessage_( xExEnAddr, { Read: { name: sDevice } } );
    },

    /**
     * Asynchronously requests for a device (an Actuator) to change its state.
     *
* When sending a basic Une type (boolean, number, string), the 4th parameter can be omitted.
 * But when sending Une advanced types (date, time, list, pair) it should be be present. If
 * it is not, Arrays are sent as Une 'list' and Objects as Une 'pair'.
 *
 * If omitted, if value is an string and its content is "true" or "false", value will be converted
 * into boolean. Same is the string contains a valid number: value is converted into Number.
 *
 * @param {Object} oExEnAddr   ExEn address. E.g.: {"host":<str>,"port":<number>,"ssl":boolean}
 * @param {String} sActuator   Name of the device.
 * @param {Any}    xValue      New value.
 * @param {String} sValueClass Optional type of value (although recommended when the 'value' is a JS Object).
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
 * @returns {String} An UUID to uniquely identify the new listener.
     */
    addListener : function( oExEn, xDevNames, fnCallback, sAction = '*' )
    {
        const currentContext = this._getCurrentContext_();

        if( ! currentContext )
            throw "No active context found. Call connect() first.";

        if( p_base.isEmpty( oExEn ) || p_base.isEmpty( xDevNames ) || p_base.isEmpty( fnCallback ) )
            throw "ExEn, device name(s) and callback function are required to add a listener";

        if( p_base.isString( oExEn ) )
            oExEn = JSON.parse( oExEn.replace( /\\"/g, '"' ) );    // This 'replace(...)' removes extra '\'

        let sUUID   = p_base.uuid();
        let devices = p_base.isArray( xDevNames) ? xDevNames : [xDevNames];

        for( const dev of devices )
        {
            currentContext.listeners.push( { id    : sUUID,
                                             fn    : fnCallback,
                                             exen  : oExEn,
                                             name  : dev,
                                             action: sAction.toLowerCase() } );
        }

        // Update legacy property for backward compatibility
        this.aListener = currentContext.listeners;

        return sUUID;
    },

    /**
     * Removes an existing listener from the listeners list.
     *
     * @param {String} sUUID The UUID returned by ::addListener(...) that uniquely identifies the listener to be removed.
     */
    delListener : function( sUUID )
    {
        if( ! this.aListener || ! Array.isArray( this.aListener ) )
            return;

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
    // FOLLOWING ARE INTERNAL (PRIVATE) FUNCTIONS
    //------------------------------------------------------------------------//

    //------------------------------------------------------------------------//
    // CONTEXT MANAGEMENT FUNCTIONS

    /**
     * Creates a new dashboard context with isolated state.
     *
     * @param {String} dashboardId Unique identifier for this dashboard
     * @param {boolean} isDesign True for design mode, false for preview mode
     * @param {String} parentContextId Parent dashboard ID for preview tabs
     */
    _createContext_ : function( dashboardId, isDesign, parentContextId = null )
    {
        const context = { dashboardId    : dashboardId,
                          socket         : null,
                          listeners      : [],
                          isDesign       : isDesign,
                          parentContextId: parentContextId };

        this.contexts.set( dashboardId, context );
        this.currentContext = context;

        // Update legacy properties for backward compatibility
        this.socket = context.socket;
        this.aListener = context.listeners;

        return context;
    },

    /**
     * Gets context by dashboard ID.
     *
     * @param {String} dashboardId Dashboard identifier
     * @returns {Object|null} Context object or null if not found
     */
    _getContext_ : function( dashboardId )
    {
        return this.contexts.get( dashboardId ) || null;
    },

    /**
     * Sets the current active context for this tab.
     *
     * @param {String} dashboardId Dashboard identifier to set as current
     */
    _setCurrentContext_ : function( dashboardId )
    {
        const context = this.contexts.get( dashboardId );

        if( context )
        {
            this.currentContext = context;

            // Update legacy properties for backward compatibility
            this.socket = context.socket;
            this.aListener = context.listeners;
        }
    },

    /**
     * Gets the current active context.
     *
     * @returns {Object|null} Current context or null if none set
     */
    _getCurrentContext_ : function()
    {
        return this.currentContext;
    },

    /**
     * Removes context and cleans up its resources.
     *
     * @param {String} dashboardId Dashboard identifier to remove
     */
    removeContext : function( dashboardId )
    {
        const context = this.contexts.get( dashboardId );

        if( context )
        {
            // Close socket if this context owns it
            if( context.socket && context.isDesign )
            {
                context.socket.close();
            }

            // Remove cleanup event listener if it exists
            if( context._cleanupHandler )
            {
                window.removeEventListener('beforeunload', context._cleanupHandler );
            }

            this.contexts.delete( dashboardId );

            // Clear current context if it was the one being removed
            if( this.currentContext && this.currentContext.dashboardId === dashboardId )
            {
                this.currentContext = null;
                this.socket = null;
                this.aListener = [];
            }
        }
    },

    //------------------------------------------------------------------------//
    // MESSAGE HANDLING FUNCTIONS

    _sendMessage_ : function( xExEnAddr, oMsg )
    {
        const currentContext = this._getCurrentContext_();
        if( ! currentContext || ! currentContext.socket )
            return;

        if( p_base.isString( xExEnAddr ) )
            xExEnAddr = JSON.parse( xExEnAddr.replace( /\\"/g, '"' ) );    // This 'replace(...)' removes extra '\'

        currentContext.socket.send( JSON.stringify( { exen: xExEnAddr, msg: oMsg } ) );
    },

    _onMessageReceived_ : function( msg )      // msg is like this: { "exen": { host: 192.168.1.7, port: 55880, ssl: false }, "Changed": {"when":1659613508344, "name":"clock", "value":1521284 } }"
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

    //------------------------------------------------------------------------//
    // AUXILIARY FUNCTIONS

    _triggerListeners_ : function( oExEn, sAction, payload )
    {
        const currentContext = this._getCurrentContext_();

        if( ! currentContext )
            return;

        for( const l of currentContext.listeners )    // A 'listener' in this.aListeners is:  { id: uuid, fn: callback, exen: {host:<str>, port:<number>, ssl:boolean}, name: devName }
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