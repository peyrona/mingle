//-------------------------------------------------------------------------------------------//
// GUM WEBSOCKETS (BroadcastChannel Implementation)
//-------------------------------------------------------------------------------------------//
//
// Manages WebSocket connections for Dashboards (Leader) and Previews (Follower).
//
// ARCHITECTURE CHANGE:
// To prevent the Server from disconnecting the Dashboard when a Preview opens with the same ID,
// we implement a Leader-Follower pattern using the BroadcastChannel API.
//
// 1. Dashboard (Design Mode) -> LEADER
//    - Maintains the actual WebSocket connection to the Server.
//    - Broadcasts received messages to all Previews.
//    - Listens for requests from Previews and forwards them to the Server.
//
// 2. Preview (Preview Mode) -> FOLLOWER
//    - Does NOT connect to the Server (preventing ID collision).
//    - Connects to the Dashboard via BroadcastChannel.
//    - Sends requests to the Dashboard to be forwarded.
//
//-------------------------------------------------------------------------------------------//

"use strict";

if( typeof gum_ws === "undefined" )
{
var gum_ws =
{
    // State
    contexts: new Map(),
    currentContext: null,

    // Legacy support
    socket: null,
    aListener: [],
    fnOnList: null,

        //------------------------------------------------------------------------//
        // PUBLIC API
        //------------------------------------------------------------------------//

    /**
     * Establishes the connection.
     * If Design Mode: Opens real WebSocket + BroadcastChannel (Leader).
     * If Preview Mode: Opens BroadcastChannel only (Follower).
     */
    connect: function( fnOnConnected )
    {
    // _connectPlain_: function( fnOnConnected )
    // {
    //     if( this.socket !== null )
    //         return;

    //     // Use the tab ID as part of the WebSocket path, not just as a query parameter
    //     const wsUrl = 'ws://'+ location.host +'/gum/bridge/'+ p_base.uuid();

    //     this.socket = new WebSocketWrap( wsUrl);
    //     this.socket.addOnMessage( (msg) => this._onMessageReceived(msg) );
    //     this.socket.addOnOpen( fnOnConnected );

    //     // Debug Logger
    //     if( p_base.isLocalHost() )
    //     {
    //         this.socket.addLogger( (msg, sender) =>
    //                                 {
    //                                     if( sender && "connected closed".includes(sender) ) console.log(`[WS-Leader] ${sender}:`, msg);
    //                                 } );
    //     }

    //     this.socket.connect();

    //     // Setup cleanup
    //     window.addEventListener('beforeunload', () =>
    //         {
    //             if( this.socket )
    //             {
    //                 this.socket.close();
    //                 this.socket = null;
    //             }
    //         });
    // },

        const isDesign = window.gum && window.gum.isInDesignMode();
        const tabId    = this._resolveTabId( isDesign );              // Resolve the Shared ID (The Dashboard's ID)
        const context  = this._createContext( tabId, isDesign );
        const wsUrl    = `ws://${location.host}/gum/bridge/${tabId}`;

        if( isDesign )
        {
            // -----------------------------------------------------------
            // LEADER ROLE (Dashboard)
            // -----------------------------------------------------------
            // 1. Create Real WebSocket
            context.socket = new WebSocketWrap(p_base.addCacheBuster(wsUrl));

            // 2. Create Broadcast Channel to talk to Previews
            context.channel = new BroadcastChannel(`gum_ws_channel_${tabId}`);

            // 3. Relay: Server -> Previews
            context.socket.addOnMessage((msg) =>
                {
                    // Process locally
                    this._onMessageReceived(msg);
                    // Broadcast to previews
                    context.channel.postMessage({ type: 'SERVER_TO_CLIENT', data: msg.data });
                });

            // 4. Relay: Previews -> Server
            context.channel.onmessage = (event) =>
                {
                    if( event.data && event.data.type === 'CLIENT_TO_SERVER' )
                    {
                        // Forward request from Preview to Server
                        context.socket.send(event.data.payload);
                    }
                };

            context.socket.addOnOpen(fnOnConnected);

            // Error Handling
            context.socket.addOnError((error) =>
                {
                    p_app.showEventError(error, "WebSocket Error");

                    if( error.target && error.target.readyState === WebSocket.CLOSED )
                    {
                        if( error.wasClean )
                            try { new AuthenticationService().throw401AjaxError(); } catch (e) {}
                    }
                });

            // Debug Logger
            if( p_base.isLocalHost() )
            {
                context.socket.addLogger((msg, sender) =>
                    {
                        if( sender && "connected closed".includes(sender) ) console.log(`[WS-Leader] ${sender}:`, msg);
                    });
            }

            context.socket.connect();
        }
        else
        {
            // -----------------------------------------------------------
            // FOLLOWER ROLE (Preview)
            // -----------------------------------------------------------
            // We use a "Virtual Socket" that talks to the Dashboard via BroadcastChannel
            console.log(`[WS-Follower] Connecting to Leader via channel: gum_ws_channel_${tabId}`);

            context.socket = new VirtualSocket(tabId);

            // Handle messages coming from Dashboard (originally from Server)
            context.socket.addOnMessage( (msg) => this._onMessageReceived(msg) );

            // Simulate connection open
            setTimeout( () => { if( fnOnConnected ) fnOnConnected(); }, 100) ;
        }

        // Sync legacy pointers
        this._updateLegacyReferences(context);

        // Setup cleanup
        this._setupUnloadCleanup(context, isDesign);
    },

    close: function()
    {
        if( this.socket )
        {
            // If this is a VirtualSocket, this just closes the channel
            // If this is a Real Socket, it closes the connection
            if( this.socket.close ) this.socket.close();
            this.socket = null;
        }

        this._cleanupSessionStorage(gum.isInDesignMode());
    },

        //------------------------------------------------------------------------//
        // STANDARD METHODS (Unchanged Logic, just modernized)
        //------------------------------------------------------------------------//

    setOnList: function(fn) { this.fnOnList = fn; },

    requestList: function(oExEnAddr)
    {
        this._sendMessage(oExEnAddr, { List: null });
    },

    requestValue: function(xExEnAddr, sDevice)
    {
        this._sendMessage(xExEnAddr, { Read: { name: sDevice } });
    },

    requestChange: function(oExEnAddr, sActuator, xValue, sValueClass = null)
    {
        if( p_base.isUndefined(sValueClass) || sValueClass === null )
            sValueClass = this._inferValueType(xValue);

        let finalValue;
        try
        {
            switch( sValueClass.toLowerCase() ) {
                case "string": finalValue = xValue; break;
                case "boolean": finalValue = String(xValue).trim().toLowerCase() === 'true'; break;
                case "number": case "numeric": finalValue = parseFloat(xValue); break;
                case "date": finalValue = { class: 'date', data: xValue.getTime() }; break;
                case "time": finalValue = { class: 'time', data: p_base.millisSinceMidnight(xValue) / 1000 }; break;
                case "array": case "list": finalValue = { class: 'list', data: xValue }; break;
                case "object": case "pair": finalValue = { class: 'pair', data: xValue }; break;
                default: throw new Error(`${sValueClass}: unknown value type`);
            }
        }
        catch( e )
        {
            console.error("Error formatting requestChange:", e);
            return;
        }

        this._sendMessage(oExEnAddr, { Change: { name: sActuator, value: finalValue } });
    },

    executeRuleOrScript: function(oExEnAddr, sName)
    {
        this._sendMessage(oExEnAddr, { Execute: sName });
    },

    addListener: function(oExEn, xDevNames, fnCallback, sAction = '*')
    {
        const ctx = this._getCurrentContext();

        if( ! ctx )
            throw new Error("No active context.");

        let parsedExEn = oExEn;

        if( p_base.isString(oExEn) )
            try { parsedExEn = JSON.parse(oExEn.replace(/\\"/g, '"')); } catch (e) { return null; }

        const sUUID = p_base.uuid();
        const devices = p_base.isArray(xDevNames) ? xDevNames : [xDevNames];

        devices.forEach( dev => {
                ctx.listeners.push({
                    id: sUUID, fn: fnCallback, exen: parsedExEn,
                    name: dev, action: sAction.toLowerCase()
                });
            });

        this.aListener = ctx.listeners;
        return sUUID;
    },

    delListener: function(sUUID)
    {
        if( ! this.aListener ) return;
        const index = this.aListener.findIndex(l => l.id === sUUID);
        if( index !== -1 ) this.aListener.splice(index, 1);
    },

        //------------------------------------------------------------------------//
        // INTERNAL HELPERS
        //------------------------------------------------------------------------//

    _resolveTabId: function(isDesign)
    {
        const storageKey = isDesign ? 'gum_tab_id_design' : 'gum_tab_id_preview';
        let tabId = sessionStorage.getItem(storageKey);

        if( ! tabId || tabId.length < 5 )
        {
            if( ! isDesign )
            {
                // Preview MUST inherit ID from parent to share connection
                const urlParams = new URLSearchParams(window.location.search);
                tabId = urlParams.get('parentTabId');
            }

            // Fallback (only for Design mode usually)
            if( ! tabId )
                tabId = p_base.uuid();

            sessionStorage.setItem(storageKey, tabId);
        }

        return tabId;
    },

    _createContext: function(dashboardId, isDesign)
    {
        const context = {
                dashboardId: dashboardId,
                socket: null,
                channel: null, // For BroadcastChannel
                listeners: [],
                isDesign: isDesign
            };

        this.contexts.set(dashboardId, context);
        this.currentContext = context;

        return context;
    },

    _getCurrentContext: function() { return this.currentContext; },

    _updateLegacyReferences: function(ctx)
    {
        this.socket = ctx.socket;
        this.aListener = ctx.listeners;
    },

    _sendMessage: function(xExEnAddr, oMsg)
    {
        const ctx = this._getCurrentContext();

        if( ! ctx || ! ctx.socket )
            return;

        let parsedExEn = xExEnAddr;

        if( p_base.isString(xExEnAddr) )
        {
            try { parsedExEn = JSON.parse(xExEnAddr.replace(/\\"/g, '"')); }
            catch (e) { return; }
        }

        // If we are Leader: Sends to WS
        // If we are Follower: VirtualSocket posts to Channel -> Leader -> WS
        const payload = JSON.stringify({ exen: parsedExEn, msg: oMsg });
        ctx.socket.send(payload);
    },

    _onMessageReceived: function(msg)
    {
        if( ! msg.data )
            return;

        try
        {
            const jo = JSON.parse(msg.data);
            const keys = Object.keys(jo);

            if( keys.length === 0 )
                return;

            let sAction = keys[0];
            const payload = jo[sAction];

            sAction = sAction.toLowerCase();

            switch( sAction ) {
                case 'readed':
                case 'changed':
                    this._triggerListeners(jo.exen, sAction, payload);
                    break;
                case 'listed':
                    if( this.fnOnList ) this.fnOnList(jo.exen, payload);
                    break;
                case 'error':
                    p_app.alert(payload, `ExEn Error: ${JSON.stringify(jo.exen)}`);
                    break;
                case 'added':
                    gum.deviceAddedAtExEn(payload.name);
                    break;
                case 'removed':
                    gum.deviceRemovedAtExEn(payload.name);
                    break;
            }
        }
        catch( e )
        {
            console.error("Msg Parse Error:", e);
        }
    },

    _triggerListeners: function(oExEn, sAction, payload)
    {
        const ctx = this._getCurrentContext();

        if( ! ctx )
            return;

        for( const l of ctx.listeners )
        {
            if( this._areSameAction(l.action, sAction) &&
                this._areSameName(l.name, payload.name) &&
                this._areSameExEn(l.exen, oExEn) )
            {
                l.fn(sAction, payload);
            }
        }
    },

    _setupUnloadCleanup: function(context, isDesign)
    {
        window.addEventListener('beforeunload', () =>
            {
                // If Leader: Close WS and Channel
                // If Follower: Close Channel
                if( context.socket && context.socket.close ) context.socket.close();
                if( context.channel ) context.channel.close();

                this._cleanupSessionStorage(isDesign);
                this.contexts.delete(context.dashboardId);
            });
    },

    _cleanupSessionStorage: function(isDesign)
    {
        sessionStorage.removeItem(isDesign ? 'gum_tab_id_design' : 'gum_tab_id_preview');
    },

    _inferValueType: function(xValue)
    {
        if( p_base.isString(xValue) )
        {
            const lower = xValue.trim().toLowerCase();
            if( lower === 'true' || lower === 'false' ) return 'boolean';
            return Number.isNaN(parseFloat(xValue)) ? "string" : "number";
        }

        if( p_base.isArray(xValue)  ) return "list";
        if( p_base.isObject(xValue) ) return "pair";
        if( xValue instanceof Date  ) return "date";

        return typeof xValue;
    },

    //------------------------------------------------------------------------//
    // MATCHERS
    //------------------------------------------------------------------------//
    _areSameAction: function(l, e)
    {
        return (l === '*' || l === e || (l.endsWith('*') && e.startsWith(l.slice(0, -1))));
    },

    _areSameName: function(l, e)
    {
        return (l === '*' || l === e || (l.endsWith('*') && e.startsWith(l.slice(0, -1))));
    },

    _areSameExEn: function(e1, e2)
    {
        if( e1 === e2 )
            return true;

        try { return p_base.jsonAreEquals(e1, e2); } catch (e) { return false; }
    }
 };

//----------------------------------------------------------------------------//
// HELPER CLASSES
//----------------------------------------------------------------------------//

/**
 * VirtualSocket
 * Acts as a WebSocket proxy for Preview tabs.
 * Communicates with the Dashboard (Leader) via BroadcastChannel.
 */
class VirtualSocket
{
    constructor(channelId)
    {
        this.channel = new BroadcastChannel(`gum_ws_channel_${channelId}`);
        this.onMessageHandlers = [];

        // Listen for broadcasts from Leader
        this.channel.onmessage = (event) =>
            {
                if( event.data && event.data.type === 'SERVER_TO_CLIENT' )
                {
                    // Wrap in object to mimic WS MessageEvent
                    const msgEvent = { data: event.data.data };
                    this.onMessageHandlers.forEach(fn => fn(msgEvent));
                }
            };
    }

    // Mimic WebSocketWrap API
    addOnMessage(fn) { this.onMessageHandlers.push(fn); }
    addOnOpen(fn) { /* Auto-opened in virtual mode */ }
    addOnError(fn) { /* Errors handled by Leader */ }
    addLogger(fn) { /* Logs handled by Leader */ }

    connect()    // Virtual connection is instant
    {
        console.log("[VirtualSocket] Linked to BroadcastChannel");
    }

    send(data)   // Send to Leader to forward to Server
    {
        this.channel.postMessage({ type: 'CLIENT_TO_SERVER', payload: data });
    }

    close()
    {
        this.channel.close();
    }
}
}