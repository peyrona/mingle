//----------------------------------------------------------------------------//
//     Grid of cells layout: a grid of cells: each one contains 1 gadget      //
//----------------------------------------------------------------------------//

"use strict";

if( typeof grid === "undefined" )
{
    $.ajax( { url     : '/gum/lib/gridstack_v7.2.3.all.js',
              dataType: 'script',
              async   : false } );

    $('#div-grid').append( $('<div id="card-title-north"></div>') )
                  .append( $('<div id="card" class="grid-stack"></div>') )    // Needed for GridStack to identify its <DIV>
                  .append( $('<div id="card-title-south"></div>') );

    $(document).on('mousedown', '.grid-stack-item', function() { grid._setFocus_( this ); });     // Cant use lambda here

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
     * This method is invoked only when saving dashboard, therefore we are in design mode,
     * therefore divs with gadgets have 'card-col' class.<br>
     * <br>
     * Do not remove empty rows: they can be used to create empty spaces (at the ened,
     * to keep them or not is an user responsability).
     *
     * @returns {Object}
     */
    getContents : function()
    {
        this._init_();

        let aGadgets = [];    // This array will store all gadget's properties

        // for( const gadget of this._aoGadgets_ )
        // {
        //     aGadgets.push( { id_cell: gadget.getContainer().parent()[0].id,
        //                      g_props: gadget.distill() } );
        // }

        // let $clone = $('#div-grid').clone(); // Makes a copy (to transform the copy)

        // $clone.find('div.cell')                         // Removes all gadgets: needed to have a clean HTML  (divs have the class 'cell' only when in design mode)
        //       .each( function()
        //              {
        //                 this.style.border = 'none';     // Better 'none' than null
        //                 $(this).removeAttr('title');    // It is not needed any more and I do not want it to be saved (::getContents())
        //                 $(this).empty();
        //              } );

        // let sHTML = $clone.html().trim();
        let sHTML = "";
        return { html: sHTML, gadgets: aGadgets };
    },

    setContents : function( oConfig )           // oGrid -> { html: <string>, props: [ {<gadget>}, ... ] }
    {
        this._init_();

        // this._showToolBar_( false );

        // $('#div-grid').html( oConfig.html );

        // if( this.isGridEmpty() )
        // {
        //     this.insertCard();
        // }

        // this.$SelectedCard = $('#div-grid').find('.card').first();
        // this.$SelectedCell = this.$SelectedCard.find('.cell').first();

        // for( const props of oConfig.gadgets )
        // {
        //     this._addGadget_( GumGadget.instantiate( props.g_props ),
        //                       $('#'+props.id_cell) );
        // }

        // if( gum.isInDesignMode() )
        // {
        //     this.forAllCells(
        //         function( card, cell )
        //         {
        //             if( p_base.isEmpty( cell.textContent ) )
        //                 grid._highlightCell_( false, cell );
        //         } );

        //     this.forAllCards( (card) => grid._refresh_( card ) );
        //     this._highlightCell_( true, this.$SelectedCell );
        //     this._showToolBar_( true );
        // }
    },

    getTitle : function()
    {
        this._init_();
        return this._title_;
    },

    setTitle : function( sTitle )
    {
        this._init_();
        this._title_ = p_base.isEmpty( sTitle ) ? "" : sTitle;
        $('#grid-title').html( this._title_ );
        return this;
    },

    addCard : function()
    {
        this._init_();

        if( ! gum.isInDesignMode() )
            return this;

        const widget = this._stack_.addWidget( {
                                                    x: 0,
                                                    y: 0,
                                                    w: 4,
                                                    h: 2,
                                                    id: p_base.uuid(),   // Temporal Id
                                                    minW: 2,
                                                    maxW: 12,
                                                    minH: 1,
                                                    maxH: 12
                                               } );

        this._setFocus_( widget );
        return this;
    },

    editCard : function()
    {
        this._init_();

        if( ! gum.isInDesignMode() )
            return this;

        if( ! this._lastFocused_ )
            return this;

        const gadget = grid._getGadget_( this._lastFocused_ );

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

        this._lastFocused_.find('.grid-stack-item-content')
                          .empty()
                          .attr('id', gadget.id )              // By having both same ID things are easier
                          .append( gadget.getContainer() )
                          .data( this._KEY_GADGET, gadget );   // Store the gadget in the widget JS Object

        gadget.show();

        return this;
    },

    //------------------------------------------------------------------------//
    // Private methods

    _getGadget_ : function( widget )
    {
        widget = $(widget);
        widget = p_base.isUndefined( widget[0]['gridstackNode'] ) ? widget : $(widget[0]['gridstackNode']);

        let id2Find = widget.attr('id');

        for( const w of this._stack_.engine.nodes )
        {
            if( id2Find === w.id )
                return $(w).find('.grid-stack-item')
                           .data( this._KEY_GADGET_);
        }

        return null;
    },

    _setFocus_ : function( widget )
    {
        if( ! gum.isInDesignMode() )
            return;

        if( this._lastFocused_ )
            this._lastFocused_.find('.grid-stack-item-content').removeClass('grid-widget-focus');

        this._lastFocused_ = $(widget);
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

        this._stack_.on('resizestop', (evt, div) =>
                        {
                            const gadget = grid._getGadget_( div.gridstackNode );

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