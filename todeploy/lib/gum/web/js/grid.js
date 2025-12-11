//----------------------------------------------------------------------------//
//     Grid layout: a grid of cells: each one contains one gadget             //
//----------------------------------------------------------------------------//

"use strict";

if( typeof grid === "undefined" )
{
    $.ajax( { url     : '/gum/lib/gridstack_v7.2.3.all.js',
              dataType: 'script',
              async   : false } );

var grid =
{
    _KEY_GADGET_ : 'key4gadget',   // Key used to store a gadget in the 'data' of a widget (a <div>) (Warning: jQuery converts this to camelCase)
    _isInited_   : false,          // Just a flag to avoid multiple initializations
    _isChanged_  : false,          // Just a flag
    _title_      : "",             // Dashboard big title at top of the page
    _$focused_   : null,           // Selected card (the one with blue border)
    _aCardsSelected_  : [],           // Selected cards (the one with blue border)
    _aCardsSelectedPos_: [],           // Position of selected cards when starting a drag operation
    _stack_      : null,

    //---------------------------------------------------------------------------------------------//
    // Public methods

    /**
     * This method is invoked only when saving dashboard, therefore we are in design mode.
     * It is invoked after all gadgets were properly saved.
     */
    saved : function()
    {
        this._isChanged_ = false;
        
        // Reset all gadget changed flags
        for( const gadget of this._getGadgets_() )
            gadget._isChanged_ = false;
    },

    isChanged : function()
    {
        if( ! gum.isInDesignMode() )
            return false;

        if( this._isChanged_ )
            return true;

        for( const gadget of this._getGadgets_() )    // This 'widget' has minium information (it is not the live instance)
        {
            if( gadget.isChanged() )
            {
                this._isChanged_ = true;              // At least one gadget is changed
                return true;
            }
        }

        return false;
    },

    /**
     * This method is invoked only when saving dashboard, therefore we are in design mode.
     *
     * @returns {Object} Serialized data of the grid.
     */
    getContents : function()
    {
        let aCards = [];
        let aSave  = this._stack_.save();

        for( const widget of aSave )    // This 'widget' has minium information (it is not the live instance)
        {
            let $card  = $(this._getCardById_( widget.id ));
            let gadget = $card.data( this._KEY_GADGET_ );

            widget.content = "";        // This info is unneeded: I save the gadget data, not its HTML representation

            aCards.push( { widget: widget,
                           gadget: (gadget ? gadget.distill() : null) } );
        }

        return p_base.isEmpty( aCards ) ? null
                                        : { title: this._title_, cards: aCards };
    },

    setContents : function( oSaved )
    {
        this._stack_.removeAll();

        this._gadgetFocus_ = null;

        if( p_base.isEmpty( oSaved ) || p_base.isEmpty( oSaved.cards ))
        {
            p_app.alert( "Warning: no saved data found." );
        }
        else
        {
            this.setTitle( oSaved.title );

            for( const card of oSaved.cards )
                this.addCard( card );
        }

        this._isChanged_ = false;
        this.onBrowserResized();
    },

    getTitle : function()
    {
        return this._title_;
    },

    setTitle : function( sTitle )
    {
        this._title_ = p_base.isEmpty( sTitle ) ? null : sTitle;

        $('.grid-title').hide();

        if( this._title_ )
            $('.grid-title').html( this._title_ ).show();

        this._isChanged_ = true;

        return this;
    },

    addCard: function(card = null)     // null == add new card
    {
        let oData = card ? card.widget :{
                                            x: 0,
                                            y: 0,
                                            w: 4,
                                            h: 2,
                                            id: p_base.uuid(),
                                            minW: 1,
                                            maxW: 12,
                                            minH: 1,
                                            maxH: 12
                                        };

        // Calculate bottom position
        if( ! card )
        {
            // Get all existing cards
            const cards = this._stack_.getGridItems();
            let maxY = 0;

            cards.forEach(card =>
                {
                    const y = parseInt(card.getAttribute('gs-y'));
                    const h = parseInt(card.getAttribute('gs-h'));
                    maxY = Math.max(maxY, y + h);
                } );

            // Set new card's y position to be after the last card
            oData.y = maxY;
        }

        const widget = this._stack_.addWidget(oData);
        this._setFocus_(widget);

        if( card && card.gadget )
            this.addGadget(GumGadget.instantiate(card.gadget));

        this._isChanged_ = true;
        this._updateButtonsState_();

        return widget;
    },

    /**
     * Edit the gadget of the focused card.
     *
     * @returns This instance.
     */
    editCard : function()
    {
        if( ! gum.isInDesignMode() )
            return this;

        if( ! this._$focused_ )
            return this;

        const gadget = this._$focused_.data( this._KEY_GADGET_ );

        if( gadget )      // 'if' because the widget could be empty or could be no card focused
            gadget.edit( true );

        return this;
    },

    cloneCard : function()
    {
        if( ! gum.isInDesignMode() || ! this._$focused_ )
            return this;

        const gadget = this._$focused_.data( grid._KEY_GADGET_ );

        if( ! gadget )
        {
            p_app.alert( "No gadget found in the selected card." );
            return this;
        }

        this._setFocus_( this.addCard() );
        this.addGadget( gadget.clone() );
        this._isChanged_ = true;

        return this;
    },

    delCard : function()
    {
        if( ! gum.isInDesignMode() || this._aCardsSelected_.length === 0 )
            return this;

        p_app.confirm( "Do you want to delete selected cards?", () =>
        {
            for( const $card of grid._aCardsSelected_ )
                grid._stack_.removeWidget( $card[0] );

            grid._aCardsSelected_ = [];
            grid._$focused_       = null;
            grid._updateButtonsState_();
            grid._isChanged_      = true;
        });

        return this;
    },

    addGadget : function( gadget )
    {
        if( ! this._$focused_ )
            return this;

        this._$focused_.data( grid._KEY_GADGET_, gadget )    // Store the gadget in the widget (has to be 1st because 'find(...) crerates a new object)
                       .find('.grid-stack-item-content')
                       .empty()
                       .append( gadget.getContainer() );

        gadget.getContainer()          // The DIV were the gadget is shown (contained)
              .css('width' ,'100%')    // (this has to be done after append(...))
              .css('height','100%');

        gadget.show();

        this._isChanged_ = true;

        return this;
    },

    onBrowserResized : function()
    {
        let aWidgets  = grid._stack_.save();

        for( const gadget of this._getGadgets_() )    // This 'widget' has minium information (it is not the live instance)
            if( gadget.isResizable() )
                gadget.show();
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
                                '<td>Click</td><td>Selects a card (makes it the default one)</td>'+
                            '</tr>'+
                            '<tr>'+
                                '<td>Double click</td><td>Open properties editor dialog for selected card</td>'+
                            '</tr>'+
                            '<tr>'+
                                '<td>Drag</td><td>Rearange cards layout</td>'+
                            '</tr>'+
                            '<tr>'+
                                '<td>Resize</td><td>Changes width and height (left, bottom, right borders and botttom-right corner)</td>'+
                            '</tr>'+
                            '<tr>'+
                                '<td>+</td><td>Adds a new card</td>'+
                            '</tr>'+
                            '<tr>'+
                                '<td>Insert</td><td>Clones selected card</td>'+
                            '</tr>'+
                            '<tr>'+
                                '<td>Del</td><td>Deletes selected card (asking for confirmation)</td>'+
                            '</tr>'+
                            '<tr>'+
                                '<td>Esc</td><td>Closes a dialog</td>'+
                            '</tr>'+
                        '</tbody>'+
                    '</table>'+
                    '<p class="mt-1 is-pulled-right" style="font-size:80%">Note: you can press [Esc] to close this dialog</p>';

        $('<div>'+ sHTML +'</div>')
            .dialog( { title     : "Help :: GRID LAYOUT",
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
        if( grid._isInited_ )
            return this;

        grid._isInited_ = true;

        $('body').append( $('<div class="grid-title"></div>') )    // Dashboard title (optional)
                 .append( $('<div class="grid-stack"></div>') );   // Needed for GridStack to identify its <DIV>

        grid._stack_ = GridStack.init(  {
                                            column: 12,
                                            minRow: 1,
                                            cellHeight: 80,
                                            float: true,
                                            resizable: { handles: 'n,nw,ne,e,se,s,sw,w' },
                                            alwaysShowResizeHandle: 'mobile',  // true if we're on mobile devices
                                            disableOneColumnMode: true,
                                            animate: true
                                        } );

        grid._stack_.on('dragstart', (evt, el) => grid._onGadgetDragStart_(evt, el));
        grid._stack_.on('dragstop', (evt, el) => grid._onGadgetDragStop_(evt, el));

        grid._stack_.on('resizestop', (evt, div) =>
                        {
                            grid._isChanged_ = true;

                            const gadget = $(div.gridstackNode.el).data( grid._KEY_GADGET_ );

                            if( gadget )      // 'if' because the widget could be empty
                                gadget.show();
                        } );

        if( gum.isInDesignMode() )
        {
            $('body').on( 'mousedown',
                          '.grid-stack-item',
                          function(e) { grid._setFocus_( this, e ); } );   // Cant use lambda here

            $('body').on( 'dblclick',
                          '.grid-stack-item',
                          function() { if( grid._aCardsSelected_.length === 1 ) grid.editCard(); } );     // Cant use lambda here

            $(document).on( 'keydown', function(evt) { grid._onKeyPressed_(evt); } );

            $('#gum-toolbar').append( $('<br>'+
                                        '<div style="display:flex; justify-content:flex-end; align-items:center;">'+
                                            '<i class="gum-mini-btn ti ti-plus"   id="grid-btn-add"   title="Add a new card"             onclick="grid.addCard()"  ></i>'+
                                            '<i class="gum-mini-btn ti ti-copy"   id="grid-btn-clone" title="Clone highlighted card"     onclick="grid.cloneCard()"></i>'+
                                            '<i class="gum-mini-btn ti ti-trash"  id="grid-btn-del"   title="Delete highlighted card"    onclick="grid.delCard()"  ></i>'+
                                            '<i class="gum-mini-btn ti ti-code"                       title="HTML and JavaScript editor" onclick="gum._coder_()"   ></i>'+
                                        '</div>') );

            grid._updateButtonsState_();
        }

        return this;
    },

    //------------------------------------------------------------------------//
    // Private methods

    _getCardById_ : function( id )
    {
        return this._stack_.getGridItems().find( item =>
                                                 item.getAttribute('gs-id') === id );
    },

    _getGadgets_ : function()
    {
        let aWidgets = this._stack_.save();
        let aGadgets = [];

        for( const widget of aWidgets )    // This 'widget' has minium information (it is not the live instance)
        {
            let $card  = $(this._getCardById_( widget.id ));
            let gadget = $card.data( this._KEY_GADGET_ );

            if( gadget )       // Care: the widget could be empty
                aGadgets.push( gadget );
        }

        return aGadgets;
    },

    _setFocus_ : function( card, e )
    {
        const bShift = e && e.shiftKey;

        if( ! bShift )
        {
            for( const $card of this._aCardsSelected_ )
                $card.find('.grid-stack-item-content').removeClass('grid-widget-focus');

            this._aCardsSelected_ = [];
        }

        this._$focused_ = $(card);

        if( gum.isInDesignMode() )
        {
            const nIdx = this._aCardsSelected_.findIndex( $c => $c[0] === this._$focused_[0] );

            if( nIdx >= 0 )
            {
                if( bShift )
                {
                    this._aCardsSelected_[ nIdx ].find('.grid-stack-item-content').removeClass('grid-widget-focus');
                    this._aCardsSelected_.splice( nIdx, 1 );
                }
            }
            else
            {
                this._aCardsSelected_.push( this._$focused_ );
            }

            for( const $card of this._aCardsSelected_ )
                $card.find('.grid-stack-item-content').addClass('grid-widget-focus');

            this._updateButtonsState_();
        }
    },

     _onKeyPressed_ : function( evt )    // Is invoked only when in design mode
    {
        if( p_app.isAnyJQueryDlgOpen() )
            return;

        // '+' key does not require a focused card
        if (evt.keyCode === 107 || (evt.keyCode === 187 && evt.shiftKey)) // + key on numpad or main keyboard
        {
            evt.preventDefault();
            grid.addCard();
            return; // Done
        }

        if( ! this._$focused_ ) // Check for a focused card for other shortcuts
            return;

        // The isEditing check should be on the gadget inside the focused card.
        const gadget = this._$focused_.data( grid._KEY_GADGET_ );
        if( gadget && gadget.isEditing() )
            return;

        if( evt.keyCode === 45 && this._aCardsSelected_.length === 1 ) // Insert
        {
            evt.preventDefault();
            grid.cloneCard();
        }
        else if( evt.keyCode === 46 ) // Delete
        {
            evt.preventDefault();
            grid.delCard();
        }
    },

    _updateButtonsState_ : function()
    {
        const nSelected = this._aCardsSelected_.length;
        const bCanClone = nSelected === 1;
        const bCanDel   = nSelected > 0;

     // $('#grid-btn-add'      ).prop('disabled', ! hasFocusedWidget);  --> is always enabled
        $('#grid-btn-clone'    ).prop('disabled', ! bCanClone);
        $('#grid-btn-del'      ).prop('disabled', ! bCanDel);
        $('#gum-toolbar select').prop('disabled', ! bCanClone);
    },

    _onGadgetDragStart_ : function( evt, el )
    {
        this._aCardsSelectedPos_ = [];

        if( this._aCardsSelected_.length > 1 )
            for( const $card of this._aCardsSelected_ )
                this._aCardsSelectedPos_.push( { card: $card,
                                                 x   : parseInt( $card.attr('gs-x') ),
                                                 y   : parseInt( $card.attr('gs-y') ) } );
    },

    _onGadgetDragStop_ : function( evt, el )
    {
        grid._isChanged_ = true;

        if( this._aCardsSelectedPos_.length > 1 )
        {
            const $dragged = $(el);
            const nNewX    = parseInt( $dragged.attr('gs-x') );
            const nNewY    = parseInt( $dragged.attr('gs-y') );
            let   oPos     = null;

            for( const o of this._aCardsSelectedPos_ )
                if( o.card[0] == el )
                {
                    oPos = o;
                    break;
                }

            const nDeltaX = nNewX - oPos.x;
            const nDeltaY = nNewY - oPos.y;

            for( const o of this._aCardsSelectedPos_ )
                if( o.card[0] != el )
                    grid._stack_.update( o.card[0], { x: o.x + nDeltaX, y: o.y + nDeltaY } );
        }

        this._aCardsSelectedPos_ = [];
    }
};
}