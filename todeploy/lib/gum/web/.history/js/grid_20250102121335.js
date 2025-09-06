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
        this._init_();

        return true;
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

            widget.content = "";    // Unneeded: I save the gadget data, not its HTML representation

            aCards.push( { widget: widget,
                           gadget: (gadget ? gadget.distill() : null),
                           title : { text: title, location: (titleS ? 'south' : 'north') } } );
        }

        return { title: this._title_, cards: aCards };
    },

    setContents : function( oData )
    {
        this._init_();

        this._stack_.removeAll();

        if( p_base.isEmpty( oData ) )
            return;

        this.setTitle( oData.title );

        for( const card of oData.cards )
        {
            let $card = $(this.addCard( card.widget ));

            if( card.gadget )
                this.addGadget( GumGadget.instantiate( card.gadget ) );

            if( card.title.text )
            {
                let where = '#card-title-'+ card.title.location;
                $card.find( where ).html( card.title.text );
            }
        }
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
        return this;
    },

    addCard : function( oData )
    {
        this._init_();

        if( ! oData )
            oData = {
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

        const card = this._stack_.addWidget( oData );

        this._setFocus_( card );

        return card;
    },

    editCard : function()
    {
        this._init_();

        if( ! gum.isInDesignMode() )
            return this;

        if( ! this._lastFocused_ )
            return this;

        const gadget = this._lastFocused_.data( this._KEY_GADGET_ );

        if( gadget )      // 'if' because the widget could be empty
            gadget.edit( true );

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
        this._lastFocused_ = null;
        this._updateButtonsState_();

        return this;
    },

    addGadget : function( gadget )
    {
        this._init_();

        if( ! gum.isInDesignMode() )
            return this;

        if( ! this._lastFocused_ )
            return this;

        this._lastFocused_.data( this._KEY_GADGET_, gadget )    // Store the gadget in the widget (has to be 1st because 'find(...) crerates a new object)
                          .find('.grid-stack-item-content')
                          .empty()
                          .append( $('<div id="card-title-north" class="card-title"></div>') )
                          .append( gadget.getContainer() )
                          .append( $('<div id="card-title-south" class="card-title"></div>') );

        gadget.getContainer()          // The DIV were the gadget is shown (contained)
              .css('width' ,'100%')    // (this has to be done after append(...))
              .css('height','100%');

        gadget.show();
        return this;
    },

    //------------------------------------------------------------------------//
    // Private methods

    _getCardById_( id )
    {
        return this._stack_.getGridItems().find( item =>
                                                 item.getAttribute('gs-id') === id );
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