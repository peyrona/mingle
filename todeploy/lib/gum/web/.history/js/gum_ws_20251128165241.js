//-------------------------------------------------------------------------------------------//
// GUM WEBSOCKETS (Production Refactor)
//-------------------------------------------------------------------------------------------//
//
// Manages WebSocket connections for Dashboards (Design Mode) and Previews.
//
// ARCHITECTURE NOTE:
// Browser tabs operate in isolated memory spaces. Sharing a raw WebSocket object
// between tabs is not possible without a SharedWorker.
// Therefore, this implementation establishes a unique connection for every tab.
//
//-------------------------------------------------------------------------------------------//

"use strict";

if (typeof gum_ws === "undefined") {
    var gum_ws = {
        // State management
        contexts: new Map(), // dashboardId -> Context Object
        currentContext: null, // Active context for this specific tab

        // Legacy properties for backward compatibility
        socket: null,
        aListener: [],
        fnOnList: null,

        //------------------------------------------------------------------------//
        // PUBLIC API
        //------------------------------------------------------------------------//

        /**
         * Establishes the WebSocket connection for the current tab.
         * Handles ID generation/retrieval and event binding.
         * * @param {Function} fnOnConnected - Callback invoked when WebSocket opens.
         */
        connect: function(fnOnConnected) {
            const isDesign = gum.isInDesignMode();
            const tabId = this._resolveTabId(isDesign);

            // Create context for this specific tab instance
            // Note: In a multi-tab environment, this is usually the only context in this window.
            const context = this._createContext(tabId, isDesign);

            // Establish Connection
            // We always create a new connection because we cannot access sockets from other tabs.
            const wsUrl = `ws://${location.host}/gum/bridge/${tabId}`;

            // Assuming WebSocketWrap is a global dependency per original file
            context.socket = new WebSocketWrap(p_base.addCacheBuster(wsUrl));

            // Update legacy public pointers
            this._updateLegacyReferences(context);

            // Bind Events
            context.socket.addOnOpen(fnOnConnected);

            context.socket.addOnMessage((msg) => {
                // Verify we are acting on the correct context (critical for SPAs, less so for multi-tab)
                if (this._getCurrentContext()?.dashboardId === tabId) {
                    this._onMessageReceived(msg);
                }
            });

            context.socket.addOnError((error) => {
                p_app.showEventError(error, "WebSocket Error");

                if (error.target && error.target.readyState === WebSocket.CLOSED) {
                    context.socket = null;

                    // Handle specific auth errors
                    if (error.wasClean) {
                        try {
                            new AuthenticationService().throw401AjaxError();
                        } catch (e) {
                            console.error("Authentication service error:", e);
                        }
                    }
                    // NOTE: We do NOT clear session storage on error here.
                    // This allows for potential reconnection logic or page refreshes to persist the session.
                }
            });

            // Debug Logging (Design Mode only)
            if (isDesign && p_base.isLocalHost()) {
                context.socket.addLogger((msg, sender) => {
                    if (sender && "connected closed".includes(sender)) {
                        console.log(`[WS-Debug] ${sender}:`, msg);
                    }
                });
            }

            // Connect
            context.socket.connect();

            // Setup Cleanup on Tab Close
            this._setupUnloadCleanup(context, isDesign);
        },

        /**
         * Closes the connection and cleans up resources.
         */
        close: function() {
            if (this.socket !== null) {
                this.socket.close();
                this.socket = null;
            }
            // We explicitly clean storage on manual close or tab unload
            this._cleanupSessionStorage(gum.isInDesignMode());
        },

        /**
         * Sets the callback for 'Listed' responses.
         * @param {Function} fn
         */
        setOnList(fn) {
            this.fnOnList = fn;
        },

        /**
         * Requests a list of devices from the ExEn.
         * @param {Object} oExEnAddr
         */
        requestList: function(oExEnAddr) {
            this._sendMessage(oExEnAddr, { List: null });
        },

        /**
         * Requests a specific device value.
         * @param {Object|String} xExEnAddr
         * @param {String} sDevice
         */
        requestValue: function(xExEnAddr, sDevice) {
            this._sendMessage(xExEnAddr, { Read: { name: sDevice } });
        },

        /**
         * Requests a state change for an actuator.
         * Automatically detects type if sValueClass is omitted.
         *
         * @param {Object} oExEnAddr
         * @param {String} sActuator
         * @param {Any} xValue
         * @param {String} [sValueClass]
         */
        requestChange: function(oExEnAddr, sActuator, xValue, sValueClass = null) {
            // Auto-detect type if missing
            if (p_base.isUndefined(sValueClass) || sValueClass === null) {
                sValueClass = this._inferValueType(xValue);
            }

            let finalValue;

            try {
                switch (sValueClass.toLowerCase()) {
                    case "string":
                        finalValue = xValue;
                        break;
                    case "boolean":
                        finalValue = String(xValue).trim().toLowerCase() === 'true';
                        break;
                    case "number":
                    case "numeric":
                        finalValue = parseFloat(xValue);
                        break;
                    case "date":
                        finalValue = { class: 'date', data: xValue.getTime() };
                        break;
                    case "time":
                        // x / 1000 assuming input is ms and target wants seconds
                        finalValue = { class: 'time', data: p_base.millisSinceMidnight(xValue) / 1000 };
                        break;
                    case "array":
                    case "list":
                        finalValue = { class: 'list', data: xValue };
                        break;
                    case "object":
                    case "pair":
                        finalValue = { class: 'pair', data: xValue };
                        break;
                    default:
                        throw new Error(`${sValueClass}: unknown value type for ${xValue}`);
                }
            } catch (e) {
                console.error("Error formatting requestChange value:", e);
                return;
            }

            this._sendMessage(oExEnAddr, { Change: { name: sActuator, value: finalValue } });
        },

        /**
         * Executes a rule or script on the remote ExEn.
         */
        executeRuleOrScript: function(oExEnAddr, sName) {
            this._sendMessage(oExEnAddr, { Execute: sName });
        },

        /**
         * Registers a listener for device changes.
         * @returns {String} UUID of the listener
         */
        addListener: function(oExEn, xDevNames, fnCallback, sAction = '*') {
            const currentContext = this._getCurrentContext();

            if (!currentContext) throw new Error("No active context found. Call connect() first.");
            if (p_base.isEmpty(oExEn) || p_base.isEmpty(xDevNames) || p_base.isEmpty(fnCallback)) {
                throw new Error("ExEn, device names, and callback are required.");
            }

            let parsedExEn = oExEn;
            if (p_base.isString(oExEn)) {
                try {
                    parsedExEn = JSON.parse(oExEn.replace(/\\"/g, '"'));
                } catch (e) {
                    console.error("Invalid JSON in addListener:", oExEn);
                    return null;
                }
            }

            const sUUID = p_base.uuid();
            const devices = p_base.isArray(xDevNames) ? xDevNames : [xDevNames];

            devices.forEach(dev => {
                currentContext.listeners.push({
                    id: sUUID,
                    fn: fnCallback,
                    exen: parsedExEn,
                    name: dev,
                    action: sAction.toLowerCase()
                });
            });

            // Sync legacy array
            this.aListener = currentContext.listeners;

            return sUUID;
        },

        /**
         * Removes a listener by UUID.
         */
        delListener: function(sUUID) {
            if (!this.aListener || !Array.isArray(this.aListener)) return;

            const index = this.aListener.findIndex(l => l.id === sUUID);
            if (index !== -1) {
                this.aListener.splice(index, 1);
            }
        },

        //------------------------------------------------------------------------//
        // INTERNAL HELPERS
        //------------------------------------------------------------------------//

        /**
         * Resolves the Tab ID based on mode and URL parameters.
         * Ensures persistence via sessionStorage.
         */
        _resolveTabId: function(isDesign) {
            let tabId;
            const storageKey = isDesign ? 'gum_tab_id_design' : 'gum_tab_id_preview';

            // 1. Try to get existing ID from session storage
            tabId = sessionStorage.getItem(storageKey);

            // 2. If no ID in storage...
            if (!tabId || tabId.length < 5) {
                if (!isDesign) {
                    // If Preview, try to inherit from URL
                    const urlParams = new URLSearchParams(window.location.search);
                    tabId = urlParams.get('parentTabId');
                }

                // 3. Fallback: Generate new ID if still missing
                if (!tabId) {
                    tabId = p_base.uuid();
                }

                sessionStorage.setItem(storageKey, tabId);
            }

            return tabId;
        },

        _createContext: function(dashboardId, isDesign) {
            const context = {
                dashboardId: dashboardId,
                socket: null,
                listeners: [],
                isDesign: isDesign,
                _cleanupHandler: null
            };

            this.contexts.set(dashboardId, context);
            this.currentContext = context;
            return context;
        },

        _getCurrentContext: function() {
            return this.currentContext;
        },

        _updateLegacyReferences: function(context) {
            this.socket = context.socket;
            this.aListener = context.listeners;
        },

        _sendMessage: function(xExEnAddr, oMsg) {
            const ctx = this._getCurrentContext();
            if (!ctx || !ctx.socket) return;

            let parsedExEn = xExEnAddr;
            if (p_base.isString(xExEnAddr)) {
                try {
                    parsedExEn = JSON.parse(xExEnAddr.replace(/\\"/g, '"'));
                } catch (e) {
                    console.error("Invalid JSON in _sendMessage:", xExEnAddr);
                    return;
                }
            }

            ctx.socket.send(JSON.stringify({ exen: parsedExEn, msg: oMsg }));
        },

        _onMessageReceived: function(msg) {
            if (!msg.data || msg.data.trim().length === 0) return;

            try {
                const jo = JSON.parse(msg.data);
                const keys = Object.keys(jo);
                if (keys.length === 0) return;

                let sAction = keys[0]; // "List", "Changed", etc.
                const payload = jo[sAction];
                sAction = sAction.toLowerCase();

                switch (sAction) {
                    case 'readed':
                    case 'changed':
                        this._triggerListeners(jo.exen, sAction, payload);
                        break;
                    case 'listed':
                        if (this.fnOnList) this.fnOnList(jo.exen, payload);
                        break;
                    case 'error':
                        p_app.alert(payload, `Error detected at ExEn: ${JSON.stringify(jo.exen)}`);
                        break;
                    case 'added':
                        gum.deviceAddedAtExEn(payload.name);
                        break;
                    case 'removed':
                        gum.deviceRemovedAtExEn(payload.name);
                        break;
                }
            } catch (e) {
                console.error("Error parsing WebSocket message:", e, msg.data);
            }
        },

        _triggerListeners: function(oExEn, sAction, payload) {
            const ctx = this._getCurrentContext();
            if (!ctx) return;

            // Optimizing loop slightly for readability
            for (const l of ctx.listeners) {
                if (this._areSameAction(l.action, sAction) &&
                    this._areSameName(l.name, payload.name) &&
                    this._areSameExEn(l.exen, oExEn)) {
                    l.fn(sAction, payload);
                }
            }
        },

        _setupUnloadCleanup: function(context, isDesign) {
            const cleanupHandler = () => {
                if (context.socket) context.socket.close();
                this._cleanupSessionStorage(isDesign);
                this.contexts.delete(context.dashboardId);
            };

            window.addEventListener('beforeunload', cleanupHandler);
            context._cleanupHandler = cleanupHandler;
        },

        _cleanupSessionStorage: function(isDesign) {
            if (isDesign) sessionStorage.removeItem('gum_tab_id_design');
            else sessionStorage.removeItem('gum_tab_id_preview');
        },

        _inferValueType: function(xValue) {
            if (p_base.isString(xValue)) {
                const lower = xValue.trim().toLowerCase();
                if (lower === 'true' || lower === 'false') return 'boolean';
                return Number.isNaN(parseFloat(xValue)) ? "string" : "number";
            }
            if (p_base.isArray(xValue)) return "list";
            if (p_base.isObject(xValue)) return "pair";
            if (xValue instanceof Date) return "date";
            return typeof xValue;
        },

        //------------------------------------------------------------------------//
        // MATCHING LOGIC
        //------------------------------------------------------------------------//

        _areSameAction: function(actListener, actEvent) {
            if (actListener === '*' || actListener === actEvent) return true;
            if (actListener.endsWith('*')) {
                return actEvent.startsWith(actListener.slice(0, -1));
            }
            return false;
        },

        _areSameName: function(nameListener, nameEvent) {
            if (nameListener === '*' || nameListener === nameEvent) return true;
            if (p_base.isString(nameListener) && p_base.isString(nameEvent) && nameListener.endsWith('*')) {
                return nameEvent.startsWith(nameListener.slice(0, -1));
            }
            return false;
        },

        _areSameExEn: function(exen1, exen2) {
            if (exen1 === exen2) return true;

            // Helper to safe parse just in case
            const parseIfNeeded = (e) => (p_base.isString(e) ? JSON.parse(e) : e);

            try {
                return p_base.jsonAreEquals(parseIfNeeded(exen1), parseIfNeeded(exen2));
            } catch (e) {
                return false;
            }
        }
    };
}