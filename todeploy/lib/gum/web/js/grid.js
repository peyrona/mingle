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

    addCard : function( card = null )     // null == add new card
    {
        let oData = card ? card.widget : {
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

        const widget = this._stack_.addWidget( oData );    // This adds at top, because it is very complex to add at bottom

        this._setFocus_( widget );     // Must be before 'addGadget' because ::addGadget() and ::setTitle() uses '_$focused_'

        if( card && card.gadget )      // Card could be empty
            this.addGadget( GumGadget.instantiate( card.gadget ) );

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
        if( ! gum.isInDesignMode() || ! this._$focused_ )
            return this;

        p_app.confirm( "Do you want to delete selected card?", () =>
        {
            const cards = grid._stack_.getGridItems();
            const idx   = cards.indexOf( grid._$focused_[0] );

            grid._stack_.removeWidget( grid._$focused_[0] );

            const remaining = grid._stack_.getGridItems();
            if( remaining.length > 0 )
            {
                grid._setFocus_( remaining[ Math.min( idx, remaining.length - 1 ) ] );
            }
            else
            {
                grid._$focused_ = null;
                grid._updateButtonsState_();
            }

            grid._isChanged_ = true;
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
                          function() { grid._setFocus_( this ); } );   // Cant use lambda here

            $('body').on( 'dblclick',
                          '.grid-stack-item',
                          function() { grid.editCard(); } );     // Cant use lambda here

            $(document).on( 'keydown', function(evt) { grid._onKeyPressed_(evt); } );

            $('#gum-toolbar').append( $('<br>'+
                                        '<div style="display:flex; justify-content:flex-end; align-items:center;">'+
                                            '<i class="gum-mini-btn fa fa-plus"   id="grid-btn-add"   title="Add a new card"          onclick="grid.addCard()"  ></i>'+
                                            '<i class="gum-mini-btn fa fa-copy"   id="grid-btn-clone" title="Clone highlighted card"  onclick="grid.cloneCard()"></i>'+
                                            '<i class="gum-mini-btn fa fa-trash"  id="grid-btn-del"   title="Delete highlighted card" onclick="grid.delCard()"  ></i>'+
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

    _setFocus_ : function( card )
    {
        if( gum.isInDesignMode() && this._$focused_ )
            this._$focused_.find('.grid-stack-item-content').removeClass('grid-widget-focus');    // Removes blue (highligth) border

        this._$focused_ = $(card);

        if( gum.isInDesignMode() )
        {
            this._$focused_.find('.grid-stack-item-content').addClass('grid-widget-focus');
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

        if( evt.keyCode === 45 ) // Insert
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
        const hasFocusedWidget = this._$focused_ !== null;

     // $('#grid-btn-add'      ).prop('disabled', ! hasFocusedWidget);  --> is always enabled
        $('#grid-btn-clone'    ).prop('disabled', ! hasFocusedWidget);
        $('#grid-btn-del'      ).prop('disabled', ! hasFocusedWidget);
        $('#gum-toolbar select').prop('disabled', ! hasFocusedWidget);
    }
};
}