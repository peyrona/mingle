//----------------------------------------------------------------------------//
//     Grid of cells layout: a grid of cells: each one contains 1 gadget      //
//----------------------------------------------------------------------------//

"use strict";

if( typeof grid === "undefined" )
{
    $.ajax( { url     : "/gum/lib/gridstack_v7.2.3.all.js",
              dataType: 'script',
              async   : false } );

    $('#div-grid').append( $('<div id="grid-title"></div>') )
                  .append( $('<div id="grid" class="grid-stack"></div>') );   // Needed for GridStack to identify its <DIV>

    $(document).on('mousedown', '.grid-stack-item', function() { grid._setFocus_( this ); });     // Cant use lambda here

var grid =
{
    _isInited_    : false,   // Just a flag to avoid multiple initializations
    _title_       : "",      // Dashboard big title at top of the page
    _aoGadgets_   : [],
    lastFocused : null,
    wrapper : GridStack.init( {
                                column: 12,
                                minRow: 1,
                                cellHeight: 80,
                                disableOneColumnMode: true,
                                float: true,
                                resizable: true,
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

    setTitle : function( sTitle = null )
    {
        this._init_();
        this._title_ = sTitle === null ? "" : sTitle;
        $('#grid-title').html( this._title_ );
        return this;
    },

    add : function()
    {
        this._init_();

        const widget = this.wrapper.addWidget( {
                                                    x: 0,
                                                    y: 0,
                                                    w: 4,
                                                    h: 2,
                                                    id: p_base.uuid(),
                                                    minW: 2,
                                                    maxW: 12,
                                                    minH: 1,
                                                    maxH: 12
                                               } );

        this._setFocus_( widget );
        return this;
    },

    del : function()
    {
        this._init_();

        if( this.lastFocused )
        {
            this.wrapper.removeWidget( this.lastFocused[0] );
            this.lastFocused = null;
            this._updateButtonsState_();
        }

        return this;
    },

    set : function()
    {
        this._init_();

        if( this.lastFocused )
        {
            this.lastFocused.find('.grid-stack-item-content')
                            .html( new Date().toLocaleTimeString() );
        }

        return this;
    },

    //------------------------------------------------------------------------//
    // Private methods

    _setFocus_ : function( widget )
    {
        if( ! gum.isInDesignMode() )
            return;

        if( this.lastFocused )
            this.lastFocused.find('.grid-stack-item-content').removeClass('grid.widget-focus');

        this.lastFocused = $(widget);
        this.lastFocused.find('.grid-stack-item-content').addClass('grid.widget-focus');

        this._updateButtonsState_();
    },

    _updateButtonsState_ : function()
    {
        const hasFocusedWidget = this.lastFocused !== null && gum.isInDesignMode();

        $('#grid-btn-add'      ).prop('disabled', ! hasFocusedWidget);
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

        $('#gum-toolbar').append( $('<br>'+
                                    '<div>'+
                                        '<i class="gum-mini-btn fa fa-plus"  id="grid-btn-add" title="Add a new card"           onclick="grid.add()"></i>'+
                                        '<i class="gum-mini-btn fa fa-trash" id="grid-btn-del" title="Delete highlighted card"  onclick="grid.del()"></i>'+
                                    '</div>') );

        this._updateButtonsState_();
    }
};
}