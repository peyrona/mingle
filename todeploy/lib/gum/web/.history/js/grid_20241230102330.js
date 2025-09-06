//----------------------------------------------------------------------------//
//     Grid of cells layout: a grid of cells: each one contains 1 gadget      //
//----------------------------------------------------------------------------//

/* global p_base, p_app, gadget, gum, dlgStyle */

"use strict";

if( typeof grid === "undefined" )
{
var grid =
{
    _isInited_    : false,   // Just a flag to avoid multiple initializations

    $SelectedCard : null,    // A Card is a grid of 2 classes: 'column' and 'card'; inside each Card there is one or more Cells
    $SelectedCell : null,    // Each of Cells inside a Card: have class 'cell'
    _aoGadgets_   : [],



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

    isGridEmpty : function()
    {
        return ($('#div-grid').find('.card').length === 0);
    },

    isCardEmpty : function( $Card = this.$SelectedCard ) // A Card should never be empty
    {
        return (this._countCells_( $Card ) === 0);
    },

    isCellEmpty : function()   // Cell (row) inside the card
    {
        return (! this.getGadget());
    },

    getTitle : function()
    {
        let title = $('#div_Grid_Global_Title_').html();

        return (p_base.isEmpty( title ) ? null : title);
    },

    setTitle : function( title )
    {
        if( p_base.isEmpty( title ) )
        {
            $('#div_Grid_Global_Title_').remove();
        }
        else
        {
            let $div = $('#div_Grid_Global_Title_');

            if( $div.length === 0 )
            {
                $div = $('<div id="div_Grid_Global_Title_" style="text-align:center;margin-bottom:14px"></div>');
                $('#div-grid').prepend( $div );
            }

            $div.html( title );
        }
    },

    /**
     * Returns focused card as JQuery Object.
     *
     * @returns {jQuery|$} Focused card as JQuery Object.
     */
    getCard : function()
    {
        return this.$SelectedCard;
    },

    /**
     * Returns focused cell as JQuery Object.
     *
     * @returns {jQuery|$} Focused cell as JQuery Object.
     */
    getCell : function()    // Note: getRow() does not exists because it is not needed
    {
        return this.$SelectedCell;
    },

    /**
     * Returns the gadget in passed cell or null if none. Default cell is ::getCell().
     *
     * @param $Cell Cell to use (defaults to ::getCell()).
     * @returns {g} The gadget in passed cell or null if none. Default cell is ::getCell().
     */
    getGadget : function( $Cell = this.getCell() )
    {
        let gadget = null;

        $Cell.find('div')
             .each( function()
                    {
                        for( const g of grid._aoGadgets_ )
                        {
                            if( this === g.getContainer()[0] )
                            {
                                gadget = g;
                                break
                            }
                        }
                    } );

        return gadget;
    },

    getCardOf : function( what )
    {
        let $what = $(what);

        if( $what.hasClass('card') )
            return $what;

        if( $what.hasClass('cell') )
            return $what.parent();

        if( $what.parent().hasClass('cell') )     // It is a gadget container: gadget.getContainer()
            return $what.parent().parent();

        return null;
    },

    getCellOf : function( what )
    {
        let $what = $(what);

        if( $what.hasClass('cell') )
            return $what;

        if( $what.parent().hasClass('cell') )     // It is a gadget container: gadget.getContainer()
            return $what.parent();

        return null;
    },

    /**
     * Inserts a new card.
     *
     * @param {type} sWhere "up" | "left" | "down" | "right"
     */
    insertCard : function( sWhere = 'down' )
    {
        if( ! gum.isInDesignMode() )
            throw "To be used only in desig mode";

        if( this._countCards_() === 12 )
        {
            p_app.alert( "Can not add more columns (max is 12)");
            return;
        }

        let id = p_base.uuid();
        let template = '<div class="column card" id="'+ id +'" style="min-width:48px"></div>';

        this._showToolBar_( false );

        if( this.isGridEmpty() )
        {
            $('#div-grid').append( $('<div class="columns">'+ template +'</div>') );

            this.$SelectedCard = $('#'+id);
            this.$SelectedCell = this.insertCell('down');
            this._highlightCell_( true );
        }
        else
        {
            switch( sWhere )
            {
                case "up":
                    this.$SelectedCard   // column
                        .parent()        // columns
                        .before( $('<div class="columns">'+ template +'</div>') );
                    this.insertCell( null, $('#'+id) );
                    break;

                case "down":
                    this.$SelectedCard   // column
                        .parent()        // columns
                        .after(  $('<div class="columns">'+ template +'</div>') );
                    this.insertCell( null, $('#'+id) );
                    break;

                case "left":
                    this.$SelectedCard.before( $(template) );
                    this.insertCell( null, $('#'+id) );
                    break;

                case "right":
                    this.$SelectedCard.after( $(template) );
                    this.insertCell( null, $('#'+id) );
                    break;
            }
        }

        this._showToolBar_( true );

        if( this.isCardEmpty( $('#'+id) ) )
            throw "This should not happen";
    },

    insertCell : function( sWhere = 'down', $targetCard = this.getCard() )
    {
        if( ! gum.isInDesignMode() )
            throw "To be used only in desig mode";

        let id    = p_base.uuid();
        let $Cell = $('<div class="cell" id="'+ id +'" >'+
                        '<br>'+    // Needed
                      '</div>');

        this._showToolBar_( false );

        if( this.isCardEmpty( $targetCard ) )
        {
            $targetCard.append( $Cell );

            if( ! this.$SelectedCell )
                this.$SelectedCell = $('#'+id);
        }
        else
        {
            if( sWhere === "up" )  this.$SelectedCell.before( $Cell );
            else                   this.$SelectedCell.after(  $Cell );
        }

        this._highlightCell_( false, $Cell );
        this._refresh_( this.getCell().parent() );
        this._showToolBar_( true );

        return $('#'+id);
    },

    editCell : function()
    {
        if( this.getGadget() )
            this.getGadget().edit();
        else
            p_app.alert('Can not edit an empty cell: add a Gadget');
    },

    deleteCell : function()
    {
        let sRefresh = 'cell';

        this._showToolBar_( false );

        this.$SelectedCell.remove();
        this.$SelectedCell = null;

        if( this.isCardEmpty() )
        {
            sRefresh = (this._countCards_() === 1) ?  null   // This card is going to be deleted and the row too
                                                   : 'row';

            if( sRefresh === null )  this.$SelectedCard.parent().remove();    // Removes the row
            else                     this.$SelectedCard.remove();

            this.$SelectedCard = null;
        }

        if( this.isGridEmpty() )
        {
            this.insertCard();
        }
        else
        {
            if( ! this.$SelectedCard )
                this.$SelectedCard = $('#div-grid').find('div.card').first();

            this.$SelectedCell = this.$SelectedCard.children('div.cell').first();

            if( sRefresh === 'cell' )  this._refresh_( this.getCell() );
            else                       this._refresh_( this.getCard().parent() );

            this._highlightCell_( true );
        }

        this._showToolBar_( true );
    },

    forAllCells( fnTask )
    {
        for( const card of document.getElementById('div-grid')
                                   .getElementsByClassName('card') )
        {
            for( const cell of card.getElementsByClassName('cell') )
            {
                fnTask( card, cell );
            }
        }
    },

    forAllCards : function( fnTask )
    {
        for( const card of document.getElementById('div-grid')
                                   .getElementsByClassName('card') )
        {
            fnTask( card );
        }
    },

    forCardsInRow : function( fnTask, $Card = this.getCard() )
    {
        $Card.parent()
             .find('div.card')
             .each( function() { fnTask( this ); } );
    },

    forCellsInCard : function( fnTask, $Card = this.getCard() )
    {
        $Card.find('div.cell')
             .each( function() { fnTask( this ); } );
    },

    help : function()
    {
        let sHTML = '<table class="table is-striped">'+
                        '<thead>'+
                            '<tr style="background-color:#F9E79F; font-weight:bold;"><td>Action</td><td>Result</td></tr>'+
                        '</thead>'+
                        '<tbody>'+
                            '<tr>'+
                                '<td>Sin hacer</td><td>Sin hacer</td>'+
                            '</tr>'+
                            '<tr>'+
                                '<td></td><td></td>'+
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

    //---------------------------------------------------------------------------------------------//
    // ACCESORY FUNCTIONS

    _addGadget_ : function( gadget, cell = this.getCell() )
    {
        this._aoGadgets_.push( gadget );

        this._showToolBar_( false );

        $(cell).css('clear','both')   // Needed to autoresize Card when Cells are resized
               .empty()
               .append( gadget.getContainer() );

        gadget.show();

        this._showToolBar_( true );
    },

    _onCellClicked_ : function( evt )
    {
        this._highlightCell_( false );

        this.$SelectedCard = $(evt.target).closest('div[class=card]');
        this.$SelectedCell = $(evt.target).closest('div[class=cell]');

        // If closest(...) did not work, we do it using a slower approach

        if( p_base.isEmpty(    this.getCard() ) &&
            p_base.isNotEmpty( this.getCell() ) )
        {
            this.$SelectedCard = this.getCell().parent();
        }

        // If closest(...) did not work, we do it using a slower approach

        if( p_base.isNotEmpty( this.getCard() ) &&
            p_base.isEmpty(    this.getCell() ) )
        {
            let aCells = this.getCard().find('.cell');

            for( let n = 0; n < aCells.length; n++ )
            {
                if( $(aCells[n]).is(':hover') )
                {
                    this.$SelectedCell = $(aCells[n]);
                    break;
                }
            }
        }

        if( this.$SelectedCard.length === 0 )  throw "No card selected";
        if( this.$SelectedCell.length === 0 )  throw "No cell selected";

        this._highlightCell_( true );
    },

    // Only used during design mode
    _highlightCell_ : function( bOn, cell = this.getCell() )
    {
        let $Cell = $(cell);
            cell  = $Cell[0];

        $Cell.css( 'border','1px dashed '+ (bOn ? '#B40404' : '#888888') );

        if( p_base.isEmpty( cell.title ) )
            cell.title = "Click to make this cell the focused one and double-click on gadget to edit its properties";

        if( (! cell.innerText) || (cell.innerText.length === 0) )
            cell.innerHTML = '<br>';

        if( ! cell.onclick )
        {
            cell.onclick    = (evt) => grid._onCellClicked_( evt );
            cell.ondblclick = (evt) => grid.editCell( evt );
        }

        return this;
    },

    _countCards_( card = this.getCard() )
    {
        if( card )
        {
            return $(card)          // column
                    .parent()       // columns
                    .find('div.column').length;
        }

        return 0;
    },

    _countCells_( card = this.getCard() )
    {
        if( card )
            return $(card).find('div.cell').length;

        return 0;
    },

    _resizeCells_ : function( card = this.getCard() )
    {
        let $Card   = $(card);
        let nHeight = 100 / this._countCells_( $Card );

        this.forCellsInCard( $Card, (cell) => $(cell).height( nHeight +'%') );
    },

    // Refreshes a Cell or a Card or all Cards in a row
    _refresh_ : function( what )
    {
        let sWhat = null;
        let $what = $(what);

             if( $what.hasClass('columns') || $what.find('div.columns').length > 0 ) sWhat = 'row';
        else if( $what.hasClass('card'   ) || $what.find('div.card'   ).length > 0 ) sWhat = 'card';
        else if( $what.hasClass('cell'   ) || $what.find('div.cell'   ).length > 0 ) sWhat = 'cell';

        if( ! sWhat )
            throw "Error "+ JSON.stringify( what );

        switch( sWhat )
        {
            case 'cell':
                let gadget = this.getGadget( $what );

                if( gadget )
                    gadget.refresh();

                break;

            case 'card':
                let nPercent = 100 / this._countCells_( $what );
                this.forCellsInCard( (cell) =>
                                            {
                                                cell.style.height = nPercent +'%';
                                                grid._refresh_( cell );
                                            },
                                     $what );
                break;

            case 'row':
                this.forCardsInRow( (card) => grid._refresh_( card ),
                                    $what );
                break;
        }
    },

    _showToolBar_ : function( bShow )
    {
        if( ! gum.isInDesignMode() )
            return;

        if( ! this.hasOwnProperty( '_toolbar_counter_') )
            this['_toolbar_counter_'] = 0;

        let $toolbar;

        if( bShow )
        {
            if( --this['_toolbar_counter_'] === 0 )
            {
                $toolbar = this['_gum_toolbar_'];

                $('body').append( $toolbar.div );
                $toolbar.div.offset( { top: $toolbar.top, left: $toolbar.left } );
            }
        }
        else     // Hide
        {
            if( this['_toolbar_counter_']++ === 0 )
            {
                $toolbar = $('#gum-toolbar');

                if( p_base.isNotEmpty( $toolbar ) )    // Is empty when invoked by second time (after being already detached)
                {
                    this['_gum_toolbar_'] = { div: $toolbar, top: $toolbar.offset().top, left: $toolbar.offset().left };

                    $toolbar.detach();
                }
            }
        }
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