//----------------------------------------------------------------------------//
//     Free layout: gadgets have aboslute positions and relative sizes        //
//----------------------------------------------------------------------------//

"use strict";

if( typeof free === "undefined" )
{
var free =
{
    _isInited_    : false,   // Just a flag to avoid multiple initializations
    _isChanged_   : false,   // Just a flag
    _aGadgets_    : [],
    _gadgetFocus_ : null,    // Current focused gadget (null when not under design mode)
    _bodySize_    : null,    // Body size when dashboard was saved last time


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
        this._bodySize_    = oData.bodysize;
        this._aGadgets_    = [];
        this._gadgetFocus_ = null;

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
               .draggable( { drag : function(event) { free._onGadgetDrag_( event, true  ); },
                              stop : function(event) { free._onGadgetDrag_( event, false ); } } );

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
                                'Gadgets start with Z == 100 to easily allow shift them in Z order.</td>'+
                            '</tr>'+
                            '<tr>'+
                                '<td>Page Down</td><td>Decreases Z-order (min: 0, max: '+ p_base.getMaxZIndex() +')</td>'+
                            '</tr>'+
                            '<tr>'+
                                '<td>Insert</td><td>Clones selected gadget</td>'+
                            '</tr>'+
                            '<tr>'+
                                '<td>Del</td><td>Deletes selected gadget (asking for confirmation)</td>'+
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

        if( gum.isInDesignMode() )
        {
            $('#gum-toolbar').append( $('<br>' +
                                        '<div style="display:flex; justify-content:space-between; align-items:center;">' +
                                            '<span id="free_focused_wnd_info" style="font-size:70%;">x = , y = , w = , h = , z =</span>' +
                                            '<span>' +
                                                '<i class="gum-mini-btn fa fa-copy" title="Clone highlighted gadget" onclick="free._clone_()"></i>' +
                                                '<i class="gum-mini-btn fa fa-trash" title="Delete highlighted gadget" onclick="free._del_()"></i>' +
                                            '</span>' +
                                        '</div>') );

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
        this._showGadgetInfo_();

        return this;
    },

    _del_ : function( gadget = this._gadgetFocus_ )    // Usend only when in design mode
    {
        if( !gadget ) return this;

        p_app.confirm( "Do you want to delete selected gadget?",
                       () => {
                                let index = free._aGadgets_.indexOf( gadget );

                                gadget.getContainer().parent().remove();
                                gadget.destroy();
                                free._aGadgets_.splice( index, 1 );

                                if( free._aGadgets_.length > 0 )
                                {
                                    let newIndex = (index >= free._aGadgets_.length) ? free._aGadgets_.length - 1 : index;
                                    free._setFocused_( free._aGadgets_[newIndex] );
                                }
                                else
                                {
                                    free._gadgetFocus_ = null;
                                    free._showGadgetInfo_(); // this will clear the info panel
                                }
                             } );

        this._isChanged_ = true;

        return this;
    },

    _edit_ : function()    // Usend only when in design mode
    {
        this._gadgetFocus_.edit();

        return this;
    },

    _clone_ : function()    // Usend only when in design mode
    {
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
        let $gadWnd = (eventOrGadget instanceof GumGadget) ? eventOrGadget.getContainer().parent()   // It is a gadget
                                                           : this._getGadgetWnd_( eventOrGadget );   // It is an event

        for( const gadget of this._aGadgets_ )
        {
            let $wnd = gadget.getContainer().parent();

            $wnd.css('border', '0px');    // Unfocused: no border

            if( $wnd[0] === $gadWnd[0] )   // [0] is needed: comparing (===) JQuery objects does not work
                this._gadgetFocus_ = gadget;
        }

        $gadWnd.css('border', '1px dashed #BE81F7');    // Focused: dashed border

        this._showGadgetInfo_( $gadWnd );

        return this;
    },

    _onGadgetDrag_ : function( event, isOngoing )
    {
        let $gadWnd = this._getGadgetWnd_( event );
        let gadget  = this._getGadget_( $gadWnd );

        gadget.x    = parseInt( $gadWnd.offset().left );
        gadget.y    = parseInt( $gadWnd.offset().top );

        if( isOngoing )
        {
            this._showGadgetInfo_( gadget );
        }
        else
        {
            this._isChanged_ = true;
        }
    },

    _onGadgetResize_ : function( event, isOngoing )
    {
        this._updateGadgetSize_( free._getGadgetWnd_( event ), isOngoing );
    },

    _onKeyPressed_ : function( evt )    // Is invoked only when in design mode
    {
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

            let z  = $gadWnd.css('z-index');
                z += (evt.keyCode === 33) ? 1 : -1;
                z  = p_base.setBetween( 0, z, p_base.getMaxZIndex() );

            $gadWnd.css('z-index', z.toString());

            this._showGadgetInfo_( $gadWnd );
            this._isChanged_ = true;
        }
        else if( (evt.keyCode >= 37 ) && (evt.keyCode <= 40) )         // Arrows key codes: left = 37, up = 38, right = 39, down = 40
        {
            let incr = ((evt.altKey || evt.metaKey) ? 10 : 1);

            if( evt.shiftKey )
            {
                switch( evt.keyCode )
                {
                    case 37: evt.preventDefault(); $gadWnd.offset( { left: $gadWnd.offset().left - incr } ); break;
                    case 38: evt.preventDefault(); $gadWnd.offset( { top : $gadWnd.offset().top  - incr } ); break;
                    case 39: evt.preventDefault(); $gadWnd.offset( { left: $gadWnd.offset().left + incr } ); break;
                    case 40: evt.preventDefault(); $gadWnd.offset( { top : $gadWnd.offset().top  + incr } ); break;
                }
            }
            else if( evt.ctrlKey && free._getGadget_( $gadWnd ).isResizable() )
            {
                switch( evt.keyCode )
                {
                    case 37: evt.preventDefault(); $gadWnd.width(  $gadWnd.width()  - incr ); break;
                    case 38: evt.preventDefault(); $gadWnd.height( $gadWnd.height() - incr ); break;
                    case 39: evt.preventDefault(); $gadWnd.width(  $gadWnd.width()  + incr ); break;
                    case 40: evt.preventDefault(); $gadWnd.height( $gadWnd.height() + incr ); break;
                }
            }

            this._updateGadgetSize_( $gadWnd, false );
            this._isChanged_ = true;
        }
    },

    _showGadgetInfo_ : function( wndOrGad = null )    // Is invoked only when in design mode
    {
        let gadget = (wndOrGad && (wndOrGad instanceof GumGadget)) ? wndOrGad : free._getGadget_( wndOrGad );

        if( ! gadget )    // v.g. after last gadget is deleted, no gadget has focus
        {
            $('#free_focused_wnd_info').text( "  x = " +
                                              ", y = " +
                                              ", w = " +
                                              ", h = " +
                                              ", z = " );
        }
        else
        {
            $('#free_focused_wnd_info').text( "  x = "+ gadget.x +
                                              ", y = "+ gadget.y +
                                              ", w = "+ ((gadget.width  == null) ? "auto" : gadget.width ) +
                                              ", h = "+ ((gadget.height == null) ? "auto" : gadget.height) +
                                              ", z = "+ gadget.z );
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

    _onBrowserResized_ : function()
    {
        if( ! this._bodySize_ ) /////////////////////////////////////////
            return;

        const newSize = p_app.getBodySize();
        const scale   = Math.min( newSize.width / this._bodySize_.width,
                                  newSize.height / this._bodySize_.height );

        this._aGadgets_.forEach(gadget =>
        {
            const scaledX      = gadget.originalX * scale;
            const scaledY      = gadget.originalY * scale;
            const scaledWidth  = gadget.originalWidth * scale;
            const scaledHeight = gadget.originalHeight * scale;

            // Update DOM if in design mode
            if (gum.isInDesignMode()) {
                const $container = gadget.getContainer().parent();
                $container.offset({ left: scaledX, top: scaledY })
                        .width(scaledWidth)
                        .height(scaledHeight);
            }

            // Update gadget properties
            gadget.x = scaledX;
            gadget.y = scaledY;
            gadget.width = scaledWidth;
            gadget.height = scaledHeight;
        } );
    },

    _resizeGadget_ : function( gadget, scale )
    {
        const newX      = gadget.x      * scale;
        const newY      = gadget.y      * scale;
        const newWidth  = gadget.width  * scale;
        const newHeight = gadget.height * scale;

        if( gum.isInDesignMode() )
        {
            gadget.getContainer().parent()
                  .offset( { left: newX, top: newY } )
                  .width( newWidth )
                  .height( newHeight );
        }

        gadget.x = newX;
        gadget.y = newY;
        gadget.width = newWidth;
        gadget.height = newHeight;
    }
};
}