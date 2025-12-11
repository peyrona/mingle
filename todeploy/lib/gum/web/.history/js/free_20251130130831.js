//----------------------------------------------------------------------------//
//     Free layout: gadgets have aboslute positions and relative sizes        //
//----------------------------------------------------------------------------//

"use strict";

if( typeof free === "undefined" )
{
var free =
{
    _isInited_         : false,   // Just a flag to avoid multiple initializations
    _isChanged_        : false,   // Just a flag
    _aGadgets_         : [],
    _aGadgetsSelected_ : [],      // All selected gadgets
    _gadgetFocus_      : null,    // Current focused gadget (null when not under design mode)
    _bodySize_         : null,    // Body size when dashboard was saved last time


    //------------------------------------------------------------------------//
    // PUBLIC FUNCTIONS

    /**
     * This method is invoked only when saving dashboard, therefore we are in design mode.
     * It is invoked after all gadgets were properly saved.
     */
    saved : function()
    {
        this._isChanged_ = false;    // Reset the changed flag because all gadgets were saved
    },

    isChanged : function()
    {
        if( ! gum.isInDesignMode() )
            return false;

        if( this._isChanged_ )
            return true;

        for( const gadget of this._aGadgets_ )
        {
            if( gadget.isChanged() )
            {
                this._isChanged_ = true;              // At least one gadget is changed
                return true;
            }
        }

        return false;
    },

    getContents : function()    // This is invoked only when in design mode (all coordinates will be related with document dimensions)
    {
        let aoGadgets = [];

        for( const gadget of this._aGadgets_ )
            aoGadgets.push( gadget.distill() );

        return p_base.isEmpty( aoGadgets ) ? null
                                           : { bodysize : p_app.getBodySize(),
                                               gadgets  : aoGadgets };
    },

    setContents : function( oData )
    {
        this._bodySize_         = oData.bodysize;
        this._aGadgets_         = [];
        this._aGadgetsSelected_ = [];
        this._gadgetFocus_      = null;

        if( ! oData.gadgets )
        {
            p_app.alert( "Warning: no saved data found." );
        }
        else
        {
            for( const saved of oData.gadgets )    // Serialized gadgets
            {
                let gadget = GumGadget.instantiate( saved );

                if( gum.isInDesignMode() )  this.addGadget( gadget );
                else                        this._add_(     gadget );
            }
        }

        this._isChanged_ = false;
        this._onBrowserResized_();
    },

    addGadget : function( gadget )     // Used when in design mode
    {
        this._aGadgets_.push( gadget );

        // Do not change code secuence
        let $outter = $('<div class="gum-moveable"></div>');

        $('body').append( $outter );

        $outter.width(  gadget.width )
               .height( gadget.height )
               .css('z-index', gadget.z)
               .append( gadget.getContainer() )
               .offset( { left: gadget.x, top: gadget.y } )
               .on( 'mousedown', (event) => free._setFocused_( event ) )
               .on( 'dblclick' , (event) => free._edit_( event ) )
               .draggable( { start: function(event,ui) { free._onGadgetDragStart_( event, ui ); },
                              drag : function(event,ui) { free._onGadgetDrag_( event, ui, true  ); },
                              stop : function(event,ui) { free._onGadgetDrag_( event, ui, false ); } } );

        gadget.getContainer()           // The DIV were the gadget is shown (contained)
              .css('width' ,'100%')     // 100% only when inside the pseudo-window (design mode)
              .css('height','100%');    // (this has to be done after $outter offset and size)

        if( gadget.isResizable() )
        {
            $outter.resizable( { resize   : function(event) { free._onGadgetResize_( event, true  ); },
                                 stop     : function(event) { free._onGadgetResize_( event, false ); },
                                 minWidth : 16,
                                 minHeight: 16 } );
        }

        gadget.show();
        free._setFocused_( gadget );
        this._isChanged_ = true;

        return this;
    },

    showHelp : function()
    {
        let sHTML = '<table class="table is-striped">'+
                        '<thead>'+
                            '<tr style="background-color:#F9E79F; font-weight:bold;">'+
                                '<td>Action</td><td>Result</td>'+
                            '</tr>'+
                        '</thead>'+
                        '<tbody>'+
                            '<tr>'+
                                '<td>Click</td><td>Selects it (makes it the default one)</td>'+
                            '</tr>'+
                            '<tr>'+
                                '<td>Shift + Click</td><td>To add/remove a gadget to/from selection</td>'+
                            '</tr>'+
                            '<tr>'+
                                '<td>Double click</td><td>Open properties editor dialog for selected</td>'+
                            '</tr>'+
                            '<tr>'+
                                '<td>Drag</td><td>Changes position</td>'+
                            '</tr>'+
                            '<tr>'+
                                '<td>Resize</td><td>Changes width and height (right and bottom margings and handler at botttom-right corner)</td>'+
                            '</tr>'+
                            '<tr>'+
                                '<td>Shift + arrows</td><td>Moves selected gadget pixel by pixel.<br>'+
                                'Add [Alt] or [Super] (depending on your OS) to make steps of 10 pixels.</td>'+
                            '</tr>'+
                            '<tr>'+
                                '<td>Ctrl + arrows</td><td>Grows and shrink selected gadget pixel by pixel.<br>'+
                                'Add [Alt] or [Super] (depending on your OS) to make steps of 10 pixels.</td>'+
                            '</tr>'+
                            '<tr>'+
                                '<td>Page Up</td><td>Increases Z-order (min: 0, max: '+ p_base.getMaxZIndex() +')<br>'+
                                'Gadgets start with Z == 100 to easily allow shift them in Z order.<br>'+
                                'Add [Shift] to make steps of 10 levels.</td>'+
                            '</tr>'+
                            '<tr>'+
                                '<td>Page Down</td><td>Decreases Z-order (min: 0, max: '+ p_base.getMaxZIndex() +')<br>'+
                                'Gadgets start with Z == 100 to easily allow shift them in Z order.<br>'+
                                'Add [Shift] to make steps of -10 levels.</td>'+
                            '</tr>'+
                            '<tr>'+
                                '<td>Insert</td><td>Clones selected gadget</td>'+
                            '</tr>'+
                            '<tr>'+
                                '<td>Del</td><td>Deletes selected gadget(s) (asking for confirmation)</td>'+
                            '</tr>'+
                            '<tr>'+
                                '<td>Esc</td><td>Closes a dialog</td>'+
                            '</tr>'+
                        '</tbody>'+
                    '</table>'+
                    '<p class="mt-1 is-pulled-right" style="font-size:80%">Note: you can press [Esc] to close this dialog</p>';

        $('<div>'+ sHTML +'</div>')
            .dialog( { title     : "Help :: FREE LAYOUT",
                       modal     : false,
                       autoOpen  : true,
                       resizable : true,
                       width     : p_app.getBestWidth('70%', 780, 680) } );
    },

    /**
     * Inital setup
     */
    init : function()
    {
        if( this._isInited_ )
            return this;

        this._isInited_ = true;

        window.addEventListener( "resize", () => free._onBrowserResized_() );

        if( gum.isInDesignMode() )
        {
            $('#gum-toolbar').append(
                '<br>' +
                '<div style="display:flex; justify-content:space-between; align-items:center;">' +
                    '<span id="free_focused_wnd_info" style="font-size:70%;">x = , y = , w = , h = , z =</span>' +
                    '<span>' +
                        '<i class="gum-mini-btn ti ti-copy" title="Clone highlighted gadget" onclick="free._clone_()"></i>' +
                        '<i class="gum-mini-btn ti ti-trash" title="Delete highlighted gadget(s)" onclick="free._del_()"></i>' +
                        '<i class="gum-mini-btn ti ti-code" title="HTML and JavaScript editor" onclick="gum._coder_()"></i>' +
                    '</span>' +
                '</div>' +
                '<div id="gum-align-toolbar" style="display:flex; justify-content:space-between; align-items:center; margin-top: 4px;">' +
                    '<span>' +
                        '<i class="gum-mini-btn ti ti-layout-align-left" title="Align left" onclick="free._alignLeft_()"></i>' +
                        '<i class="gum-mini-btn ti ti-layout-align-center" title="Align center" onclick="free._alignCenter_()"></i>' +
                        '<i class="gum-mini-btn ti ti-layout-align-right" title="Align right" onclick="free._alignRight_()"></i>' +
                    '</span>' +
                    '<span>' +
                        '<i class="gum-mini-btn ti ti-layout-align-top" title="Align top" onclick="free._alignTop_()"></i>' +
                        '<i class="gum-mini-btn ti ti-layout-align-middle" title="Align middle" onclick="free._alignMiddle_()"></i>' +
                        '<i class="gum-mini-btn ti ti-layout-align-bottom" title="Align bottom" onclick="free._alignBottom_()"></i>' +
                    '</span>' +
                    '<span>' +
                        '<i class="gum-mini-btn ti ti-arrows-horizontal" title="Same width" onclick="free._sameWidth_()"></i>' +
                        '<i class="gum-mini-btn ti ti-arrows-vertical" title="Same height" onclick="free._sameHeight_()"></i>' +
                        '<i class="gum-mini-btn ti ti-maximize" title="Same size" onclick="free._sameSize_()"></i>' +
                    '</span>' +
                '</div>'
                );

            $(document).on( 'keydown', function(evt) { free._onKeyPressed_(evt); } );  // Arrow keys are only triggered by onkeydown
        }

        return this;
    },

    //------------------------------------------------------------------------//
    // PRIVATE FUNCTIONS

    _add_ : function( gadget )    // Used when not in design mode
    {
        $('body').append( gadget.getContainer() );

        gadget.getContainer()
              .width(  gadget.width  )
              .height( gadget.height )
              .css('z-index', gadget.z)
              .offset( { left: gadget.x, top: gadget.y } );   // Has to be after width, height and added to the body

        gadget.show();

        if( gum.isInDesignMode() )
        {
            this._showGadgetInfo_();
        }

        return this;
    },

    _del_ : function()    // Usend only when in design mode
    {
        if( this._aGadgetsSelected_.length === 0 )
        {
            return this;
        }

        const msg = (this._aGadgetsSelected_.length > 1) ? "Do you want to delete selected gadgets?"
                                                         : "Do you want to delete selected gadget?";
        p_app.confirm( msg, function() {
            let aRemainingGadgets = [];

            for( const gadget of free._aGadgets_ )
            {
                if( free._aGadgetsSelected_.includes( gadget ) )
                {
                    gadget.getContainer().parent().remove();
                    gadget.destroy();
                }
                else
                {
                    aRemainingGadgets.push( gadget );
                }
            }

            free._aGadgets_ = aRemainingGadgets;
            free._aGadgetsSelected_ = [];
            free._gadgetFocus_ = null;

            if( free._aGadgets_.length > 0 )
            {
                free._setFocused_( free._aGadgets_[0] );
            }
            else
            {
                free._showGadgetInfo_(); // this will clear the info panel
            }
        });

        this._isChanged_ = true;

        return this;
    },

    _edit_ : function()    // Usend only when in design mode
    {
        if( this._aGadgetsSelected_.length > 1 )
        {
            p_app.alert( "Please, select only one gadget to edit." );
            return this;
        }

        if( this._gadgetFocus_ )
        {
            this._gadgetFocus_.edit();
        }

        return this;
    },

    _clone_ : function()    // Usend only when in design mode
    {
        if( this._aGadgetsSelected_.length > 1 )
        {
            p_app.alert( "Please, select only one gadget to clone." );
            return this;
        }

        if( ! this._gadgetFocus_ )
        {
            p_app.alert( "Please, select a gadget to clone." );
            return this;
        }

        let saved = this._gadgetFocus_.distill();

        // A new gadget should not have the same ID
        delete saved.id;

        // Just to avoid gadgets are cloned one over another
        saved.x = p_base.setBetween( 0, (saved.x || 0) + 20, p_app.getBodySize().width  - (saved.width  || 100) );
        saved.y = p_base.setBetween( 0, (saved.y || 0) + 20, p_app.getBodySize().height - (saved.height || 100) );

        let newGadget = GumGadget.instantiate( saved );

        this.addGadget( newGadget );

        return this;
    },

    _setFocused_ : function( eventOrGadget )    // Usend only when in design mode
    {
        const isShift = eventOrGadget && eventOrGadget.shiftKey;
        const $gadWnd = (eventOrGadget instanceof GumGadget) ? eventOrGadget.getContainer().parent() : this._getGadgetWnd_( eventOrGadget );
        const gadget  = this._getGadget_( $gadWnd );

        if( ! isShift )
        {
            this._aGadgetsSelected_ = [];
            this._aGadgetsSelected_.push( gadget );
        }
        else
        {
            const index = this._aGadgetsSelected_.indexOf( gadget );

            if( index > -1 )
            {
                this._aGadgetsSelected_.splice( index, 1 ); // Already selected, so unselect
            }
            else
            {
                this._aGadgetsSelected_.push( gadget );     // Not selected, so select
            }
        }

        this._gadgetFocus_ = (this._aGadgetsSelected_.length > 0) ? this._aGadgetsSelected_[ this._aGadgetsSelected_.length - 1 ] : null;

        //-- Update borders
        for( const g of this._aGadgets_ )
        {
            const $w = g.getContainer().parent();

            if( this._aGadgetsSelected_.includes( g ) )
            {
                $w.css('border', (g === this._gadgetFocus_) ? '2px dashed #BE81F7' : '1px solid #BE81F7');
            }
            else
            {
                $w.css('border', '0px');
            }
        }

        this._showGadgetInfo_();
        this._updateAlignToolbar_();

        return this;
    },

    _onGadgetDragStart_ : function( event, ui )
    {
        for( const g of this._aGadgetsSelected_ )
        {
            const $w = g.getContainer().parent();
            $w.data('originalPosition', $w.offset());
        }
    },

    _onGadgetDrag_ : function( event, ui, isOngoing )
    {
        let $gadWnd = this._getGadgetWnd_( event );
        let gadget  = this._getGadget_( $gadWnd );

        if( isOngoing )
        {
            let originalDraggedPos = $(ui.helper).data('originalPosition');
            let dx = ui.offset.left - originalDraggedPos.left;
            let dy = ui.offset.top - originalDraggedPos.top;

            for( const g of this._aGadgetsSelected_ )
            {
                if( g === gadget )
                {
                    continue;
                }

                const $w = g.getContainer().parent();
                const originalPos = $w.data('originalPosition');
                if (originalPos) {
                    $w.offset({ top: originalPos.top + dy, left: originalPos.left + dx });
                }
            }
        }
        else // stop
        {
             for( const g of this._aGadgetsSelected_ )
            {
                const $w = g.getContainer().parent();
                g.x = parseInt( $w.offset().left );
                g.y = parseInt( $w.offset().top );
                $w.removeData('originalPosition');
            }
            this._isChanged_ = true;
        }

        this._showGadgetInfo_( gadget );
    },

    _onGadgetResize_ : function( event, isOngoing )
    {
        this._updateGadgetSize_( free._getGadgetWnd_( event ), isOngoing );
    },

    _onKeyPressed_ : function( evt )    // Is invoked only when in design mode
    {
        if( ! gum.isInDesignMode() )
            return;

        if( p_app.isAnyJQueryDlgOpen() )
            return;

        if( ! this._gadgetFocus_ )
            return;                     // v.g. after a gadget is deleted, no gadget has focus

        if( this._gadgetFocus_.isEditing() )
            return;                     // Do not process keys when editing a gadget

        if( this._gadgetFocus_.getContainer() === null )
            return;                     // When a gadget is being deleted, its parent is null

        let $gadWnd = this._gadgetFocus_.getContainer().parent();

        if( ! $gadWnd )
            return;

        // Make only evt.preventDefault(); when the key is used by this function

        if( evt.keyCode === 45 )    // 45 == insert
        {
            evt.preventDefault();
            free._clone_();
        }
        else if( evt.keyCode === 46 )    // 46 == delete
        {
            evt.preventDefault();
            free._del_();
        }
        else if( (evt.keyCode === 33 ) || (evt.keyCode === 34) )       // PgUp = 33, PgDn = 34
        {
            evt.preventDefault();

            let incr = ((evt.shiftKey || evt.metaKey) ? 10 : 1);

            for( const gadget of this._aGadgetsSelected_ )
            {
                 if( ! p_base.isNumber( gadget.z ) )
                    gadget.z = 100;    // Default Z index when not defined

                let z  = gadget.z;
                    z += (evt.keyCode === 33) ? incr : (incr * -1);
                    z  = p_base.setBetween( 0, z, p_base.getMaxZIndex() );

                gadget.z = z;
                gadget.getContainer().parent().css('z-index', z.toString());
            }
            this._showGadgetInfo_( this._gadgetFocus_ );
            this._isChanged_ = true;
        }
        else if( (evt.keyCode >= 37 ) && (evt.keyCode <= 40) )         // Arrows key codes: left = 37, up = 38, right = 39, down = 40
        {
            let incr = ((evt.altKey || evt.metaKey) ? 10 : 1);

            if( evt.shiftKey )
            {
                evt.preventDefault();
                for( const gadget of this._aGadgetsSelected_ )
                {
                    const $w = gadget.getContainer().parent();
                    switch( evt.keyCode )
                    {
                        case 37: $w.offset( { left: $w.offset().left - incr } ); break;
                        case 38: $w.offset( { top : $w.offset().top  - incr } ); break;
                        case 39: $w.offset( { left: $w.offset().left + incr } ); break;
                        case 40: $w.offset( { top : $w.offset().top  + incr } ); break;
                    }
                    this._updateGadgetSize_( $w, false );
                }
            }
            else if( evt.ctrlKey && this._gadgetFocus_.isResizable() )
            {
                 evt.preventDefault();
                for( const gadget of this._aGadgetsSelected_ )
                {
                    const $w = gadget.getContainer().parent();
                    if( !gadget.isResizable() )
                    {
                        continue;
                    }

                    switch( evt.keyCode )
                    {
                        case 37: $w.width(  $w.width()  - incr ); break;
                        case 38: $w.height( $w.height() - incr ); break;
                        case 39: $w.width(  $w.width()  + incr ); break;
                        case 40: $w.height( $w.height() + incr ); break;
                    }
                    this._updateGadgetSize_( $w, false );
                }
            }
            this._isChanged_ = true;
        }
    },

    _showGadgetInfo_ : function( wndOrGad = null )    // Is invoked only when in design mode
    {
        let gadget = (wndOrGad && (wndOrGad instanceof GumGadget)) ? wndOrGad : this._gadgetFocus_;

        if( ! gadget )    // v.g. after last gadget is deleted, no gadget has focus
        {
            $('#free_focused_wnd_info').html( "&nbsp;" );
        }
        else
        {
            let width  = gadget.width  ? gadget.width  : gadget.getContainer().parent().width();    // Some gadtes like buttons,
            let height = gadget.height ? gadget.height : gadget.getContainer().parent().height();   // do not have width and height defined

            $('#free_focused_wnd_info').text(   "x=" + parseInt( gadget.x ) +
                                              ", y=" + parseInt( gadget.y ) +
                                              ", w=" + parseInt( width    ) +
                                              ", h=" + parseInt( height   ) +
                                              ", z=" + parseInt( gadget.z ) );
        }

        return this;
    },

    /**
     * Returns the gadget DIV where the gadget is shown: the whole thing when in design mode, the oine with the title bar with the [X] button.
     * @param
     * @returns
     */
    _getGadgetWnd_ : function( evt )
    {
        if( ! gum.isInDesignMode() )
            throw "Error: this function is only used when in design mode";

        if( evt === null || evt.currentTarget === null )
            throw "Error: no event or currentTarget found";

        let $div = $(evt.currentTarget).closest('.gum-moveable');   // The focused whole window DIV (the one containing the TitleBar and the Gadget) where another DIV is inserted to contain the gadget.

        if( (! $div) || ($div.length === 0) )
        {
            $div = $(evt.target).closest('.gum-moveable');

            if( (! $div) || ($div.length === 0) )
                throw "Error: no class 'gum-moveable' found";
        }

        return $div;
    },

    _getGadget_ : function( $gadWnd )
    {
        if( ! gum.isInDesignMode() )
            throw "Error: this function is only used when in design mode";

        if( (! $gadWnd) || ($gadWnd.length === 0) )
            throw "Error: no gadget window found";

        if( p_base.isEmpty( free._aGadgets_ ) )
            return null;

        for( const gadget of this._aGadgets_ )
        {
            if( gadget.getContainer().parent()[0] === $gadWnd[0] )
                return gadget;
        }

        throw "Error: no gadget found for the given window";
    },

    _updateGadgetSize_: function( $gadWnd, isOngoing = false )
    {
        let gadget = free._getGadget_( $gadWnd );
        let z      = $gadWnd.css('z-index');

        gadget.x      = parseInt( $gadWnd.offset().left );
        gadget.y      = parseInt( $gadWnd.offset().top );
        gadget.z      = (p_base.isNumber( z ) ? z : 100);
        gadget.width  = parseInt( $gadWnd.width() );
        gadget.height = parseInt( $gadWnd.height() );

        this._showGadgetInfo_( gadget );
        if( this._gadgetFocus_ )
        {
            this._gadgetFocus_.show( isOngoing );
        }

        if( ! isOngoing )
        {
            free._isChanged_ = true;
        }

        return this;
    },

    _updateAlignToolbar_ : function()
    {
        const disabled = this._aGadgetsSelected_.length < 2;
        $('#gum-align-toolbar i').css('opacity', disabled ? 0.2 : 1.0);
        $('#gum-align-toolbar i').css('cursor', disabled ? 'not-allowed' : 'pointer');
        $('#gum-align-toolbar i').prop('disabled', disabled);
    },

    //------------------------------------------------------------------------//
    // --- ALIGNMENT AND SIZING ---

    _alignLeft_ : function()
    {
        if (this._aGadgetsSelected_.length < 2) return;
        const targetX = this._gadgetFocus_.x;
        for (const g of this._aGadgetsSelected_)
        {
            if (g === this._gadgetFocus_) continue;
            g.x = targetX;
            g.getContainer().parent().offset({ left: g.x, top: g.y });
        }
        this._isChanged_ = true;
    },

    _alignCenter_ : function()
    {
        if (this._aGadgetsSelected_.length < 2) return;
        const targetX = this._gadgetFocus_.x + this._gadgetFocus_.width / 2;
        for (const g of this._aGadgetsSelected_)
        {
            if (g === this._gadgetFocus_) continue;
            g.x = targetX - g.width / 2;
            g.getContainer().parent().offset({ left: g.x, top: g.y });
        }
        this._isChanged_ = true;
    },

    _alignRight_ : function()
    {
        if (this._aGadgetsSelected_.length < 2) return;
        const targetX = this._gadgetFocus_.x + this._gadgetFocus_.width;
        for (const g of this._aGadgetsSelected_)
        {
            if (g === this._gadgetFocus_) continue;
            g.x = targetX - g.width;
            g.getContainer().parent().offset({ left: g.x, top: g.y });
        }
        this._isChanged_ = true;
    },

    _alignTop_ : function()
    {
        if (this._aGadgetsSelected_.length < 2) return;
        const targetY = this._gadgetFocus_.y;
        for (const g of this._aGadgetsSelected_)
        {
            if (g === this._gadgetFocus_) continue;
            g.y = targetY;
            g.getContainer().parent().offset({ left: g.x, top: g.y });
        }
        this._isChanged_ = true;
    },

    _alignMiddle_ : function()
    {
        if (this._aGadgetsSelected_.length < 2) return;
        const targetY = this._gadgetFocus_.y + this._gadgetFocus_.height / 2;
        for (const g of this._aGadgetsSelected_)
        {
            if (g === this._gadgetFocus_) continue;
            g.y = targetY - g.height / 2;
            g.getContainer().parent().offset({ left: g.x, top: g.y });
        }
        this._isChanged_ = true;
    },

    _alignBottom_ : function()
    {
        if (this._aGadgetsSelected_.length < 2) return;
        const targetY = this._gadgetFocus_.y + this._gadgetFocus_.height;
        for (const g of this._aGadgetsSelected_)
        {
            if (g === this._gadgetFocus_) continue;
            g.y = targetY - g.height;
            g.getContainer().parent().offset({ left: g.x, top: g.y });
        }
        this._isChanged_ = true;
    },

    _sameWidth_ : function()
    {
        if (this._aGadgetsSelected_.length < 2) return;
        const targetWidth = this._gadgetFocus_.width;
        for (const g of this._aGadgetsSelected_)
        {
            if (g === this._gadgetFocus_ || !g.isResizable()) continue;
            g.width = targetWidth;
            g.getContainer().parent().width(g.width);
            g.show();
        }
        this._isChanged_ = true;
    },

    _sameHeight_ : function()
    {
        if (this._aGadgetsSelected_.length < 2) return;
        const targetHeight = this._gadgetFocus_.height;
        for (const g of this._aGadgetsSelected_)
        {
            if (g === this._gadgetFocus_ || !g.isResizable()) continue;
            g.height = targetHeight;
            g.getContainer().parent().height(g.height);
            g.show();
        }
        this._isChanged_ = true;
    },

    _sameSize_ : function()
    {
        this._sameWidth_();
        this._sameHeight_();
    }
};
}