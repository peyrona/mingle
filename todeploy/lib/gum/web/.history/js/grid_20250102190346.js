//----------------------------------------------------------------------------//
//     Grid of cells layout: a grid of cells: each one contains 1 gadget      //
//----------------------------------------------------------------------------//

"use strict";

if( typeof grid === "undefined" )
{
    $.ajax( { url     : '/gum/lib/gridstack_v7.2.3.all.js',
              dataType: 'script',
              async   : false } );

    $('body').append( $('<div class="grid-title"></div>') )    // Optional dashboard title
             .append( $('<div class="grid-stack"></div>') );   // Needed for GridStack to identify its <DIV>

var grid =
{
    _KEY_GADGET_  : 'key4gadget',   // Key used to store a gadget in the 'data' of a widget (a <div>) (Warning: jQuery converts this to camelCase)
    _isInited_    : false,          // Just a flag to avoid multiple initializations
    _isChanged_   : false,          // Just a flag
    _title_       : "",             // Dashboard big title at top of the page
    _lastFocused_ : null,
    _stack_       : GridStack.init( {
                                        column: 12,
                                        minRow: 1,
                                        cellHeight: 80,
                                        float: true,
                                        resizable: true,
                                        resizable: { handles: 'n,nw,ne,e,se,s,sw,w' },
                                        alwaysShowResizeHandle: 'mobile', // true if we're on mobile devices
                                        disableOneColumnMode: true,
                                        animate: true
                                    } ),

    //---------------------------------------------------------------------------------------------//
    // Public methods

    isChanged : function()
    {
        if( ! gum.isInDesignMode() )
            return false;

        if( this._isChanged_ )
            return true;

        for( const gadget of this._getGadgets_() )    // This 'widget' has minium information (it is not the live instance)
            if( gadget.isChanged() )
                return true;

        return false;
    },

    /**
     * This method is invoked only when saving dashboard, therefore we are in design mode.
     *
     * @returns {Object} Serialized data of the grid.
     */
    getContents : function()
    {
        this._init_();

        let aCards = [];
        let aSave  = this._stack_.save();

        for( const widget of aSave )    // This 'widget' has minium information (it is not the live instance)
        {
            let $card  = $(this._getCardById_( widget.id ));
            let gadget = $card.data( this._KEY_GADGET_ );
            let titleN = $card.find('#card-title-north').text();
            let titleS = $card.find('#card-title-south').text();
            let title  = titleN + titleS;

            widget.content = "";        // This info is unneeded: I save the gadget data, not its HTML representation

            aCards.push( { widget: widget,
                           gadget: (gadget ? gadget.distill() : null),
                           title : { text: title, location: (titleS ? 'south' : 'north') } } );
        }

        return { title: this._title_, cards: aCards };
    },

    setContents : function( oSaved )
    {
        this._init_();

        this._stack_.removeAll();

        if( p_base.isEmpty( oSaved ) )
            return;

        for( const card of oSaved.cards )
            this.addCard( card );

        this._isChanged_ = false;    // Beacause ::this.addCard(...) sets it to true
    },

    getTitle : function()
    {
        this._init_();
        return this._title_;
    },

    setTitle : function( sTitle )
    {
        this._init_();
        this._title_ = p_base.isEmpty( sTitle ) ? null : sTitle;
        $('.grid-title').html( this._title_ );
        this._isChanged_ = true;

        return this;
    },

    addCard : function( card = null )
    {
        this._init_();

        let oData = card ? card.widget : {
                                            x: 0,
                                            y: 0,
                                            w: 4,
                                            h: 2,
                                            id: p_base.uuid(),
                                            minW: 2,
                                            maxW: 12,
                                            minH: 1,
                                            maxH: 12
                                         };

        const widget = this._stack_.addWidget( oData );

        this._showTitle_( card );
        this._setFocus_( widget );     // Must be before 'addGadget' because it uses '_lastFocused_'

        if( card )
            this.addGadget( GumGadget.instantiate( card.gadget ) );

        this._isChanged_ = true;

        return widget;
    },

    editCard : function()
    {
        this._init_();

        if( ! gum.isInDesignMode() )
            return this;

        if( ! this._lastFocused_ )
            return this;

        const gadget = this._lastFocused_ &&
                       this._lastFocused_.data( this._KEY_GADGET_ );

        if( gadget )      // 'if' because the widget could be empty or could be no card focused
            gadget.edit( true, null, this._moveTitleFromGadgetToCard_( gadget ) );

        return this;
    },

    delCard : function()
    {
        this._init_();

        if( ! gum.isInDesignMode() )
            return this;

        if( ! this._lastFocused_ )
            return this;

        this._stack_.removeWidget( this._lastFocused_[0] );
        this._isChanged_   = true;
        this._lastFocused_ = null;
        this._updateButtonsState_();

        return this;
    },

    addGadget : function( gadget )
    {
        this._init_();

        if( ! this._lastFocused_ )
            return this;

        this._lastFocused_.data( this._KEY_GADGET_, gadget )    // Store the gadget in the widget (has to be 1st because 'find(...) crerates a new object)
                          .find('.grid-stack-item-content')
                          .empty()
                          .append( $('<div id="card-title-north" class="card-title" style="display:none;"></div>') )
                          .append( gadget.getContainer() )
                          .append( $('<div id="card-title-south" class="card-title" style="display:none;"></div>') );

        gadget.getContainer()          // The DIV were the gadget is shown (contained)
              .css('width' ,'100%')    // (this has to be done after append(...))
              .css('height','100%');

        gadget.show();

        this._isChanged_ = true;

        return this;
    },

    onBrowserResized : function()
    {
        let aWidgets  = this._stack_.save();

        for( const gadget of this._getGadgets_() )    // This 'widget' has minium information (it is not the live instance)
            if( gadget.isResizable() )
                gadget.show();
    },

    //------------------------------------------------------------------------//
    // Private methods

    _getCardById_( id )
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
        if( ! gum.isInDesignMode() )
            return;

        if( this._lastFocused_ )
            this._lastFocused_.find('.grid-stack-item-content').removeClass('grid-widget-focus');

        this._lastFocused_ = $(card);
        this._lastFocused_.find('.grid-stack-item-content').addClass('grid-widget-focus');

        this._updateButtonsState_();
    },

    _moveTitleFromGadgetToCard_ : function( gadget, card = this._lastFocused_ )
    {
        let title = gadget['card_title'];
        let locat = gadget['card_title_location'];

        if( title )
            delete gadget['card_title'];

        if( locat )
            delete gadget['card_title_location'];

        card['title'] = { text: title, location: locat };

        this._showTitle_( card );
    },

    _showTitle_ : function( card = this._lastFocused_ )
    {
        if( ! card )
            return;

        $(card).find('card_title'         ).hide();
        $(card).find('card_title_location').hide();

        if( card.title.text )
        {
            let where = '#card-title-'+ card.title.location;

            $(card).find( where )
                   .html( title )
                   .show();
        }
    },

    _updateButtonsState_ : function()
    {
        const hasFocusedWidget = this._lastFocused_ !== null;

     // $('#grid-btn-add'      ).prop('disabled', ! hasFocusedWidget);  --> is always enabled
        $('#grid-btn-del'      ).prop('disabled', ! hasFocusedWidget);
        $('#gum-toolbar select').prop('disabled', ! hasFocusedWidget);
    },

    /**
     * Inital setup
     */
    _init_ : function()
    {
        if( this._isInited_ )
            return;

        this._isInited_ = true;

        if( ! gum.isInDesignMode() )
            return;

        $(document).on('mousedown',
                       '.grid-stack-item',
                       function() { grid._setFocus_( this ); });     // Cant use lambda here

        this._stack_.on('resizestop', (evt, div) =>
                        {
                            const gadget = $(div.gridstackNode.el).data( this._KEY_GADGET_ );

                            if( gadget )      // 'if' because the widget could be empty
                                gadget.show();
                        } );

        $('#gum-toolbar').append( $('<br>'+
                                    '<div>'+
                                        '<i class="gum-mini-btn fa fa-plus"   id="grid-btn-add"  title="Add a new card"           onclick="grid.addCard()" ></i>'+
                                        '<i class="gum-mini-btn fa fa-pencil" id="grid-btn-edit" title="Edit highlighted card"    onclick="grid.editCard()"></i>'+
                                        '<i class="gum-mini-btn fa fa-trash"  id="grid-btn-del"  title="Delete highlighted card"  onclick="grid.delCard()" ></i>'+
                                    '</div>') );

        this._updateButtonsState_();
    }
};
}