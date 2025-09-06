//----------------------------------------------------------------------------//
//     Grid layout: a grid of cells: each one contains one gadget             //
//----------------------------------------------------------------------------//

"use strict";

if( typeof grid === "undefined" )
{
    $.ajax( { url     : '/gum/lib/gridstack_v7.2.3.all.js',
              dataType: 'script',
              async   : false } );

    $('body').append( $('<div class="grid-title"></div>') )    // Dashboard title (optional)
             .append( $('<div class="grid-stack"></div>') );   // Needed for GridStack to identify its <DIV>

//---------------------------------------------------------------------------------------------//

var grid =
{
    _KEY_GADGET_ : 'key4gadget',   // Key used to store a gadget in the 'data' of a widget (a <div>) (Warning: jQuery converts this to camelCase)
    _isInited_   : false,          // Just a flag to avoid multiple initializations
    _isChanged_  : false,          // Just a flag
    _title_      : "",             // Dashboard big title at top of the page
    _$focused_   : null,
    _stack_      : GridStack.init(  {
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

            widget.content = "";        // This info is unneeded: I save the gadget data, not its HTML representation

            aCards.push( { widget: widget,
                           gadget: (gadget ? gadget.distill() : null) } );
        }

        return { title: this._title_, cards: aCards };
    },

    setContents : function( oSaved )
    {
        this._init_();

        this._stack_.removeAll();

        if( p_base.isEmpty( oSaved ) )
            return;

        this.setTitle( oSaved.title );

        for( const card of oSaved.cards )
            this.addCard( card );

        this._isChanged_ = false;    // Beacause ::this.addCard(...) and ::setTitle(...) set it to true
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

        $('.grid-title').hide();

        if( this._title_ )
            $('.grid-title').html( this._title_ ).show();

        this._isChanged_ = true;

        return this;
    },

    addCard : function( card = null )     // null == add new card
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

        const widget = this._stack_.addWidget( oData );    // This adds at top, but it is very complex to add at bottom

        this._setFocus_( widget );     // Must be before 'addGadget' because ::addGadget() and ::setTitle() uses '_$focused_'

        if( card && card.gadget )      // Cad could be empty
            this.addGadget( GumGadget.instantiate( card.gadget ) );

        this._isChanged_ = true;

        return widget;
    },

    /**
     * Edit the gadget of the focused card.
     *
     * @returns This instance.
     */
    editCard : function()
    {
        this._init_();

        if( ! gum.isInDesignMode() )
            return this;

        if( ! this._$focused_ )
            return this;

        const gadget = this._$focused_.data( this._KEY_GADGET_ );

        if( gadget )      // 'if' because the widget could be empty or could be no card focused
        {
            gadget.fnOnEditEnd = this._showTitle_;
            gadget.edit( true );
        }

        return this;
    },

    delCard : function()
    {
        this._init_();

        if( ! gum.isInDesignMode() )
            return this;

        if( ! this._$focused_ )
            return this;

        this._stack_.removeWidget( this._$focused_[0] );
        this._isChanged_   = true;
        this._$focused_ = null;
        this._updateButtonsState_();

        return this;
    },

    addGadget : function( gadget )
    {
        this._init_();

        if( ! this._$focused_ )
            return this;

        this._$focused_.data( this._KEY_GADGET_, gadget )    // Store the gadget in the widget (has to be 1st because 'find(...) crerates a new object)
                       .find('.grid-stack-item-content')
                       .empty()
                       .append( $('<div id="card-title-north" class="card-title"></div>') )
                       .append( gadget.getContainer() )
                       .append( $('<div id="card-title-south" class="card-title"></div>') );

        this._showTitle_( gadget );    // Shown before gadget is shown

        gadget.getContainer()          // The DIV were the gadget is shown (contained)
              .css('width' ,'100%')    // (this has to be done after append(...))
              .css('height','80%');

        gadget.show();

        this._isChanged_ = true;

        return this;
    },

    onBrowserResized : function()
    {
        this._init_();

        let aWidgets  = this._stack_.save();

        for( const gadget of this._getGadgets_() )    // This 'widget' has minium information (it is not the live instance)
            if( gadget.isResizable() )
                gadget.show();
    },

    showHelp : function()
    {
        this._init_();

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
                            '<tr style="background-color:#F9E79F; font-weight:bold;">'+
                                '<td colspan="2">Other Keys</td>'+
                            '</tr>'+
                            '<tr>'+
                                '<td>Esc</td><td>Closes a dialog</td>'+
                            '</tr>'+
                        '</tbody>'+
                    '</table>'+
                    '<p class="mt-1 is-pulled-right" style="font-size:80%">Note: you can press [Esc] to close this dialog</p>';

        $('<div>'+ sHTML +'</div>')
            .dialog( { title     : "Help :: FREE LAYOUT :: Actions over focused gadget",
                       modal     : false,
                       autoOpen  : true,
                       resizable : true,
                       width     : p_app.getBestWidth('70%', 780, 680) } );
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

    _showTitle_ : function( gadget )
    {
        if( ! gadget )
            return;

        let $card = grid._$focused_;     // Can not use 'this' because _showTitle_ can be invoked from a gadget as callback

        $card.find('#card-title-north').hide();
        $card.find('#card-title-south').hide();

        if( gadget.card_title )
        {
            let title = gadget.card_title;
            let where = '#card-title-'+ gadget.card_title_location;

            $card.find( where )
                 .html( title )
                 .show();
        }
    },

    _updateButtonsState_ : function()
    {
        const hasFocusedWidget = this._$focused_ !== null;

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

        $('body').on( 'mousedown',
                        '.grid-stack-item',
                        function() { grid._setFocus_( this ); } );   // Cant use lambda here

        $('body').on( 'dblclick',
                        '.grid-stack-item',
                        function() { grid.editCard( this ); } );     // Cant use lambda here

        this._stack_.on('resizestop', (evt, div) =>
                        {
                            const gadget = $(div.gridstackNode.el).data( this._KEY_GADGET_ );

                            if( gadget )      // 'if' because the widget could be empty
                                gadget.show();
                        } );

        $('#gum-toolbar').append( $('<br>'+
                                    '<div>'+
                                        '<i class="gum-mini-btn fa fa-plus"   id="grid-btn-add"  title="Add a new card"                            onclick="grid.addCard()" ></i>'+
                                        '<i class="gum-mini-btn fa fa-pencil" id="grid-btn-edit" title="Edit highlighted card (Also double-click)" onclick="grid.editCard()"></i>'+
                                        '<i class="gum-mini-btn fa fa-trash"  id="grid-btn-del"  title="Delete highlighted card"                   onclick="grid.delCard()" ></i>'+
                                    '</div>') );

        this._updateButtonsState_();
    }
};
}