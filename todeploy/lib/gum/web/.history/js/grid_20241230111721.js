//----------------------------------------------------------------------------//
//     Grid of cells layout: a grid of cells: each one contains 1 gadget      //
//----------------------------------------------------------------------------//

"use strict";

if( typeof grid === "undefined" )
{
    $(document).on('mousedown', '.grid-stack-item', function() { grid._setFocus_( this ); });     // Cant use lambda here

    console.log( 'grid.js' );

var grid =
{
    _isInited_    : false,   // Just a flag to avoid multiple initializations
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
        let aGadgets = [];    // This array will store all gadget's properties

        for( const gadget of this._aoGadgets_ )
        {
            aGadgets.push( { id_cell: gadget.getContainer().parent()[0].id,
                             g_props: gadget.distill() } );
        }

        let $clone = $('#div-grid').clone(); // Makes a copy (to transform the copy)

        $clone.find('div.cell')                         // Removes all gadgets: needed to have a clean HTML  (divs have the class 'cell' only when in design mode)
              .each( function()
                     {
                        this.style.border = 'none';     // Better 'none' than null
                        $(this).removeAttr('title');    // It is not needed any more and I do not want it to be saved (::getContents())
                        $(this).empty();
                     } );

        let sHTML = $clone.html().trim();

        return { html: sHTML, gadgets: aGadgets };
    },

    setContents : function( oConfig )           // oGrid -> { html: <string>, props: [ {<gadget>}, ... ] }
    {
        this._showToolBar_( false );

        $('#div-grid').html( oConfig.html );

        if( this.isGridEmpty() )
        {
            this.insertCard();
        }

        this.$SelectedCard = $('#div-grid').find('.card').first();
        this.$SelectedCell = this.$SelectedCard.find('.cell').first();

        for( const props of oConfig.gadgets )
        {
            this._addGadget_( GumGadget.instantiate( props.g_props ),
                              $('#'+props.id_cell) );
        }

        if( gum.isInDesignMode() )
        {
            this.forAllCells(
                function( card, cell )
                {
                    if( p_base.isEmpty( cell.textContent ) )
                        grid._highlightCell_( false, cell );
                } );

            this.forAllCards( (card) => grid._refresh_( card ) );
            this._highlightCell_( true, this.$SelectedCell );
            this._showToolBar_( true );
        }
    },

    add : function()
    {
        this.widgetCounter++;
        const widget = this.wrapper.addWidget( {
                                                    x: 0,
                                                    y: 0,
                                                    w: 4,
                                                    h: 2,
                                                    content: `Widget ${this.widgetCounter}`,
                                                    id: `widget-${this.widgetCounter}`,
                                                    minW: 2,
                                                    maxW: 12,
                                                    minH: 1,
                                                    maxH: 6
                                               } );

        this._setFocus_( widget );
    },

    del : function()
    {
        if( this.lastFocused )
        {
            this.wrapper.removeWidget( this.lastFocused[0] );
            this.lastFocused = null;
            this._updateButtonsState_();
        }
    },

    set : function()
    {
        if( this.lastFocused )
        {
            this.lastFocused.find('.grid-stack-item-content')
                            .html( new Date().toLocaleTimeString() );
        }
    },

    //------------------------------------------------------------------------//
    // Private methods

    _setFocus_ : function( widget )
    {
        if( ! this.focusEnabled )
            return;

        if( this.lastFocused )
            this.lastFocused.find('.grid-stack-item-content').removeClass('widget-focus');

        this.lastFocused = $( widget );
        this.lastFocused.find('.grid-stack-item-content').addClass('widget-focus');

        this._updateButtonsState_();
    },

    _updateButtonsState_ : function()
    {
        const hasFocusedWidget = this.lastFocused !== null && this.focusEnabled;
        $('#deleteWidget').prop('disabled', !hasFocusedWidget);
        $('#setContent').prop('disabled', !hasFocusedWidget);
        $('#setImage').prop('disabled', !hasFocusedWidget);
    },

    /**
     * Inital setup
     */
    _init_ : function()
    {
        let sBtn1 = '<i class="gum-mini-btn fa fa-caret-square-o-up"    title="Insert a card above"  onclick="grid.insertCard(\'up\'   )"></i>'+
                    '<i class="gum-mini-btn fa fa-caret-square-o-down"  title="Insert a card below"  onclick="grid.insertCard(\'down\' )"></i>'+
                    '<i class="gum-mini-btn fa fa-caret-square-o-left"  title="Insert a card before" onclick="grid.insertCard(\'left\' )"></i>'+
                    '<i class="gum-mini-btn fa fa-caret-square-o-right" title="Insert a card after"  onclick="grid.insertCard(\'right\')"></i>';

        let sBtn2 = '<i class="gum-mini-btn fa fa-chevron-up"   title="Insert a row above" onclick="grid.insertCell(\'up\'  )"></i>'+
                    '<i class="gum-mini-btn fa fa-chevron-down" title="Insert a row below" onclick="grid.insertCell(\'down\')"></i>'+
                    '<i class="gum-mini-btn fa fa-trash         title="Delete highlighted" onclick="grid.deleteCell();"></i>'+
                    '<i class="gum-mini-btn fa fa-pencil        title="Edit highlighted"   onclick="grid.editCell();"></i>';

        let $select = GumGadget.createSelect( (oGadget) => grid._addGadget_( oGadget ) );    // This SELECT has all available gadgets

        $('#gum-toolbar').append( $('<div></div>').append( $(sBtn1) ) )
                         .append( $('<div></div>').append( $(sBtn2) ) )
                         .append( $('<div class="select is-small"></div>')
                         .append( $select ) );
    }
};
}