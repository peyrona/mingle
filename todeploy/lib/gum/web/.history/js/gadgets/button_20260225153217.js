
"use strict";

class GumButton extends GumGadget
{
    /**
     * Creates a new GumButton gadget.
     *
     * Supports two data formats:
     *   - **New format**: `props.buttons` is an array of button definitions.
     *   - **Legacy format**: flat `props.label`, `props.color`, `props.devices` — auto-migrated
     *     to a single-entry `buttons[]` array.
     *
     * The `mode` property (`"single"` | `"toolbar"` | `"radio"`) controls rendering:
     *   - `"single"`: one standalone button.
     *   - `"toolbar"`: N independent buttons (no mutual exclusion).
     *   - `"radio"`: N mutually exclusive buttons (clicking one deselects others).
     *
     * @param {Object} [props] Saved gadget properties (may be empty for a brand-new gadget).
     */
    constructor( props )
    {
        super( "button", props );
debugger;
        // --- Brand-new gadget (no saved props) ---
        if( p_base.isEmpty( props ) )
        {
            this.buttons     = [];
            this.size        = "is-normal";
            this.orientation = "horizontal";
            this.mode        = "single";
        }
        // --- Legacy format migration ---
        else if( props.label !== undefined && props.buttons === undefined )
        {
            this.buttons = [
                {
                    text   : this.label   || "Do it",
                    icon   : "",
                    color  : this.color   || "is-info",
                    enabled: true,
                    devices: this.devices || []
                }
            ];

            delete this.label;
            delete this.color;
            delete this.devices;
        }

        // Backward-compatibility: orientation may be absent in older saved gadgets
        if( this.orientation === undefined )
            this.orientation = "horizontal";

        // Mode migration (absent in older saved gadgets)
        if( this.mode === undefined )
            this.mode = (this.buttons.length <= 1) ? "single" : "toolbar";

        // Runtime-only state (excluded from persistence via distill)
        this._selectedIndex_ = -1;
        this._toggleState_   = {};    // { idx: boolean } — current toggle state per button index
        this._keydownFn_     = null;  // Bound document keydown handler reference, for cleanup
    }

    //-----------------------------------------------------------------------------------------//

    /**
     * Excludes runtime-only fields from serialization.
     *
     * @returns {Object} A clean copy of this gadget's persistable properties.
     */
    distill()
    {
        return super.distill( ["_selectedIndex_", "_toggleState_", "_keydownFn_"] );
    }

    isResizable()
    {
        return false;
    }

    /**
     * Shows a dialog to modify this gadget's properties.
     *
     * @param {boolean} [bStart=true] true to open the editor, false to close it.
     * @returns {GumButton} this
     */
    edit( bStart = true )
    {
        if( ! bStart )
        {
            dlgButton._saveCurrentBehavior_();
            dlgButton._saveCurrentActions_();
        }

        super.edit( bStart );

        if( bStart )
        {
            let self = this;

            dlgButton.setup( this,
                            () => self.show(),
                            () => self.edit( false ) )
                     .show();
        }

        return this;
    }

    /**
     * Renders the gadget into its content area.
     *
     * - `"single"` mode: one standalone Bulma `<button>`.
     * - `"toolbar"` mode: N independent buttons; clicking one does not affect others.
     * - `"radio"` mode: N mutually exclusive buttons; clicking one deselects all others.
     *
     * @param {boolean} [isOngoing=false] true during drag/resize (skip re-render).
     * @returns {GumButton} this
     */
    show( isOngoing = false )
    {
        if( isOngoing )
            return this;

        this._selectedIndex_ = -1;

        const $content = this.getContentArea().empty();

        if( this.mode === "single" )
            this._showSingle_( $content );
        else if( this.mode === "toolbar" )
            this._showToolbar_( $content );
        else
            this._showRadio_( $content );

        this._updateStateListeners_();
        this._registerShortcuts_();

        return super.show( isOngoing );
    }

    /**
     * Destroys the gadget, removing keyboard listeners and WebSocket subscriptions.
     *
     * @returns {GumButton} this
     */
    destroy()
    {
        if( this._keydownFn_ )
        {
            document.removeEventListener( 'keydown', this._keydownFn_ );
            this._keydownFn_ = null;
        }

        return super.destroy();
    }

    //-----------------------------------------------------------------------------------------//
    // PRIVATE SCOPE

    /**
     * Renders a single standalone Bulma button.
     *
     * @param {jQuery} $content The gadget's content area (already emptied).
     */
    _showSingle_( $content )
    {
        const btnDef = this.buttons[0] || { text: "", icon: "", color: "is-info", enabled: true, devices: [] };

        const $btn = GumGadget.cloneTemplate( "tpl-button" );
        $btn.attr( 'id', this.id )
            .attr( 'data-btn-idx', 0 );

        this._applyButtonContent_( $btn, btnDef );

        $btn.removeClass( ['is-small','is-normal','is-medium','is-large',
                           'is-primary','is-info','is-success','is-warning','is-danger','is-light','is-dark','is-black'] )
            .addClass( [this.size, btnDef.color] );

        if( ! btnDef.enabled )
            $btn.attr( 'disabled', 'disabled' );

        $content.append( $btn );

        // Re-query after DOM insertion
        const $mounted = $( '#' + this.id );

        if( gum.isInDesignMode() && gum.isUsingFreeLayout() )
        {
            this.getContainer()
                .parent()
                .width(  $mounted.outerWidth()  +  4 )
                .height( $mounted.outerHeight() + 22 );
        }

        if( btnDef.enabled )
            $mounted.on( 'click', this._buildClickHandler_( 0, btnDef, $mounted ) );
    }

    /**
     * Renders a Bulma button group — horizontal (`buttons`, no addons) or vertical stack —
     * where each button acts independently (no mutual exclusion).
     *
     * @param {jQuery} $content The gadget's content area (already emptied).
     */
    _showToolbar_( $content )
    {
        const $bar = (this.orientation === "vertical")
            ? GumGadget.cloneTemplate( "tpl-button-bar-vertical" )
            : $( '<div class="buttons"></div>' );

        const self = this;

        for( let i = 0; i < this.buttons.length; i++ )
        {
            const btnDef = this.buttons[i];
            const $btn   = $( '<button class="button is-responsive"></button>' );

            $btn.addClass( this.size )
                .attr( 'data-btn-idx', i );

            this._applyButtonContent_( $btn, btnDef );

            if( ! btnDef.enabled )
                $btn.attr( 'disabled', 'disabled' );

            if( btnDef.enabled )
            {
                (function( idx, def )
                {
                    const baseClickFn = self._buildClickHandler_( idx, def, $btn );
                    $btn.on( 'click', baseClickFn );
                })( i, btnDef );
            }

            $bar.append( $btn );
        }

        $content.append( $bar );
    }

    /**
     * Renders a Bulma button group — horizontal (`buttons has-addons`) or vertical stack —
     * with radio-like mutual exclusion: clicking one button visually deselects all others.
     *
     * @param {jQuery} $content The gadget's content area (already emptied).
     */
    _showRadio_( $content )
    {
        const ALL_COLORS = ['is-primary','is-info','is-success','is-warning','is-danger','is-light','is-dark','is-black'];

        const sTemplate = (this.orientation === "vertical") ? "tpl-button-bar-vertical" : "tpl-button-bar";
        const $bar      = GumGadget.cloneTemplate( sTemplate );
        const self      = this;

        for( let i = 0; i < this.buttons.length; i++ )
        {
            const btnDef = this.buttons[i];
            const $btn   = $( '<button class="button is-responsive"></button>' );

            $btn.addClass( this.size )
                .attr( 'data-btn-idx', i );

            this._applyButtonContent_( $btn, btnDef );

            if( ! btnDef.enabled )
                $btn.attr( 'disabled', 'disabled' );

            if( btnDef.enabled )
            {
                (function( idx, def )
                {
                    const baseClickFn = self._buildClickHandler_( idx, def, $btn );

                    $btn.on( 'click', function()
                    {
                        // Radio-style visual selection — skipped when state reflection drives it
                        if( p_base.isEmpty( def.state_device ) )
                        {
                            $bar.children( 'button' ).each( function()
                            {
                                $( this ).removeClass( ALL_COLORS );
                            });

                            $( this ).addClass( def.color );
                            self._selectedIndex_ = idx;
                        }

                        baseClickFn();
                    });
                })( i, btnDef );
            }

            $bar.append( $btn );
        }

        $content.append( $bar );
    }

    /**
     * Registers WebSocket listeners that update button visual state in response to
     * device value changes (state reflection and conditional enable/disable).
     *
     * Resets and re-registers on every `show()` call to stay in sync with
     * the current button definitions.
     */
    _updateStateListeners_()
    {
        this._resetListeners();

        const $area = this.getContentArea();
debugger;
        for( let i = 0; i < this.buttons.length; i++ )
        {
            const btnDef = this.buttons[i];
            const idx    = i;

            // State reflection: highlight button when device value matches state_value
            if( p_base.isNotEmpty( btnDef.state_device ) && p_base.isNotEmpty( btnDef.state_exen ) )
            {
                const fnState = function( action, payload )
                {
                    const $btn = $area.find( '[data-btn-idx="'+ idx +'"]' );
                    if( $btn.length === 0 ) return;

                    const isActive = (String( payload.value ) === String( btnDef.state_value ));
                    const color    = btnDef.color || 'is-info';

                    $btn.toggleClass( color, isActive );
                };

                this._addListener( btnDef.state_exen, btnDef.state_device, fnState );
            }

            // Conditional enable/disable: enable button only when device value matches cond_value
            if( p_base.isNotEmpty( btnDef.cond_device ) && p_base.isNotEmpty( btnDef.cond_exen ) )
            {
                const fnCond = function( action, payload )
                {
                    const $btn    = $area.find( '[data-btn-idx="'+ idx +'"]' );
                    if( $btn.length === 0 ) return;

                    const enabled = (String( payload.value ) === String( btnDef.cond_value ));
                    $btn.prop( 'disabled', ! enabled );
                };

                this._addListener( btnDef.cond_exen, btnDef.cond_device, fnCond );
            }
        }
    }

    /**
     * Attaches (or replaces) a single document-level `keydown` listener that
     * triggers the matching button when any defined keyboard shortcut is pressed.
     *
     * Shortcuts are not fired when a text-input element has focus.
     */
    _registerShortcuts_()
    {
        if( this._keydownFn_ )
        {
            document.removeEventListener( 'keydown', this._keydownFn_ );
            this._keydownFn_ = null;
        }

        const hasShortcut = this.buttons.some( b => p_base.isNotEmpty( b.shortcut ) );
        if( ! hasShortcut )
            return;

        const self  = this;
        const $area = this.getContentArea();

        this._keydownFn_ = function( evt )
        {
            // Do not fire shortcuts when an input element has focus
            const tag = document.activeElement ? document.activeElement.tagName : '';
            if( tag === 'INPUT' || tag === 'TEXTAREA' || tag === 'SELECT' )
                return;

            for( let i = 0; i < self.buttons.length; i++ )
            {
                const btnDef = self.buttons[i];
                if( ! btnDef.enabled || p_base.isEmpty( btnDef.shortcut ) )
                    continue;

                const parsed = self._parseShortcut_( btnDef.shortcut );
                if( ! parsed ) continue;

                if( evt.ctrlKey  === parsed.ctrl  &&
                    evt.altKey   === parsed.alt   &&
                    evt.shiftKey === parsed.shift &&
                    evt.key.toLowerCase() === parsed.key.toLowerCase() )
                {
                    evt.preventDefault();

                    const $btn = $area.find( '[data-btn-idx="'+ i +'"]' );
                    if( $btn.length > 0 && ! $btn.prop('disabled') )
                        $btn.trigger('click');

                    break;
                }
            }
        };

        document.addEventListener( 'keydown', this._keydownFn_ );
    }

    /**
     * Parses a shortcut string like `"Ctrl+S"` or `"Alt+Shift+1"` into its components.
     *
     * @param {string} s The shortcut string.
     * @returns {{key:string, ctrl:boolean, alt:boolean, shift:boolean}|null}
     */
    _parseShortcut_( s )
    {
        if( p_base.isEmpty( s ) )
            return null;

        const parts = s.split('+').map( p => p.trim() ).filter( p => p.length > 0 );
        if( parts.length === 0 )
            return null;

        let ctrl  = false;
        let alt   = false;
        let shift = false;
        let key   = null;

        for( const part of parts )
        {
            const lp = part.toLowerCase();
            if( lp === 'ctrl' || lp === 'control' ) { ctrl  = true; continue; }
            if( lp === 'alt'                       ) { alt   = true; continue; }
            if( lp === 'shift'                     ) { shift = true; continue; }
            key = part;
        }

        return (key !== null) ? { key, ctrl, alt, shift } : null;
    }

    /**
     * Builds and returns a click handler function for a button.
     *
     * Encapsulates: confirmation prompt, toggle logic, action firing,
     * success-feedback flash, and cooldown disable.
     *
     * @param {number} idx    Index of the button in `this.buttons[]`.
     * @param {Object} btnDef Button definition object.
     * @param {jQuery} $btn   The jQuery button element.
     * @returns {Function} The click handler.
     */
    _buildClickHandler_( idx, btnDef, $btn )
    {
        const self = this;

        const doFire = function()
        {
            // Toggle logic: determine "on" (stored value) vs "off" (value_off) on each click
            const bToggle      = (btnDef.toggle === true);
            const bCurrentlyOn = self._toggleState_[idx] || false;

            // Fire all actions
            if( p_base.isNotEmpty( btnDef.devices ) )
            {
                for( const dev of btnDef.devices )
                {
                    if( dev.action === "execute_rule" || dev.action === "execute_script" )
                    {
                        gum_ws_boards.executeRuleOrScript( dev.exen, dev.name );
                    }
                    else if( dev.action === "open_url" )
                    {
                        window.open( dev.value, '_blank' );
                    }
                    else
                    {
                        let val = dev.value;

                        if( bToggle )
                            val = bCurrentlyOn ? (p_base.isNotEmpty( btnDef.value_off ) ? btnDef.value_off : "") : dev.value;

                        gum_ws_boards.requestChange( dev.exen, dev.name, val, dev.type );
                    }
                }
            }

            // Advance toggle state
            if( bToggle )
                self._toggleState_[idx] = ! bCurrentlyOn;

            // Success feedback: brief brightness flash
            $btn.addClass( 'gum-btn-flash' );
            setTimeout( function() { $btn.removeClass( 'gum-btn-flash' ); }, 400 );

            // Cooldown: temporarily disable the button
            const nCooldown = parseInt( btnDef.cooldown ) || 0;
            if( nCooldown > 0 )
            {
                $btn.prop( 'disabled', true );
                setTimeout( function() { $btn.prop( 'disabled', false ); }, nCooldown * 1000 );
            }
        };

        return function()
        {
            if( btnDef.confirm === true )
            {
                const msg = p_base.isNotEmpty( btnDef.confirm_msg ) ? btnDef.confirm_msg : "Are you sure?";
                p_app.confirm( msg, doFire );
            }
            else
            {
                doFire();
            }
        };
    }

    /**
     * Populates a `<button>` element with icon and/or text content from a button definition.
     *
     * @param {jQuery} $btn   The button element to populate.
     * @param {Object} btnDef A button definition `{ text, icon, color, enabled, devices }`.
     */
    _applyButtonContent_( $btn, btnDef )
    {
        $btn.empty();

        if( p_base.isNotEmpty( btnDef.icon ) )
        {
            $btn.append( '<span class="icon"><i class="ti ti-' + btnDef.icon + '"></i></span>' );
        }

        if( p_base.isNotEmpty( btnDef.text ) )
        {
            $btn.append( '<span>' + btnDef.text + '</span>' );
        }
    }
}