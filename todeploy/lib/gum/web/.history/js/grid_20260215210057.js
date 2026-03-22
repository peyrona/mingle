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

    /** Default options applied to every new sub-grid. */
    _SUB_GRID_OPTS_ : { column: 12,
                         cellHeight: 60,
                         float: true,
                         animate: true,
                         disableOneColumnMode: true,
                         minRow: 1 },

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
        if( this._isChanged_ )
            return true;

        if( ! gum.isInDesignMode() )
            return false;

        for( const gadget of this._getGadgets_() )
        {
            if( gadget.isChanged() )
            {
                this._isChanged_ = true;
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

        for( const widget of aSave )
        {
            let $card  = $(this._getCardById_( widget.id ));
            let gadget = $card.data( this._KEY_GADGET_ );

            widget.content = "";

            if( widget.subGrid )
            {
                // Sub-grid card: collect gadgets from children
                const childGadgets = {};
                const subGridEl    = $card.find('> .grid-stack-item-content > .grid-stack')[0];

                if( subGridEl && subGridEl.gridstack )
                {
                    const subItems = subGridEl.gridstack.getGridItems();

                    for( const childEl of subItems )
                    {
                        const childId     = childEl.getAttribute('gs-id');
                        const childGadget = $(childEl).data( this._KEY_GADGET_ );

                        childGadgets[ childId ] = childGadget ? childGadget.distill() : null;
                    }
                }

                // Clean content from sub-grid children too
                if( widget.subGrid.children )
                    for( const child of widget.subGrid.children )
                        child.content = "";

                aCards.push( { widget: widget, gadget: null, childGadgets: childGadgets } );
            }
            else
            {
                aCards.push( { widget: widget,
                               gadget: (gadget ? gadget.distill() : null) } );
            }
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
            {
                if( card.widget.subGrid )
                {
                    this._loadSubGridCard_( card );
                }
                else
                {
                    this.addCard( card );
                }
            }
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
        const targetGrid = card ? this._stack_ : this._getActiveGrid_();

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
            const cards = targetGrid.getGridItems();
            let maxY = 0;

            cards.forEach(item =>
                {
                    const y = parseInt(item.getAttribute('gs-y'));
                    const h = parseInt(item.getAttribute('gs-h'));
                    maxY = Math.max(maxY, y + h);
                } );

            oData.y = maxY;
        }

        const widget = targetGrid.addWidget(oData);
        this._setFocus_(widget);

        if( card && card.gadget )
            this.addGadget(GumGadget.instantiate(card.gadget));

        this._isChanged_ = true;
        this._updateButtonsState_();

        return widget;
    },

    /**
     * Creates a sub-grid inside the focused card.
     * If the card has a gadget, it is moved into the first child of the sub-grid.
     * If the card is empty, two empty children are created.
     *
     * @returns This instance.
     */
    addGrid : function()
    {
        if( ! gum.isInDesignMode() || ! this._$focused_ )
            return this;

        const el   = this._$focused_[0];
        const node = el.gridstackNode;

        if( ! node )
            return this;

        // Guard: already has a sub-grid
        if( node.subGrid && node.subGrid.el )
        {
            p_app.alert( "This card already contains an inner grid." );
            return this;
        }

        const existingGadget = this._$focused_.data( this._KEY_GADGET_ );

        // Determine the parent GridStack instance that owns this card
        const parentGrid = this._getOwnerGrid_( el );

        // Create the sub-grid (saveContent=false so existing HTML is discarded)
        const subGrid = parentGrid.makeSubGrid( el, this._SUB_GRID_OPTS_, null, false );

        if( existingGadget )
        {
            // Case (b): card had a gadget — move it into the first child
            this._$focused_.removeData( this._KEY_GADGET_ );

            const child1 = subGrid.addWidget( { x: 0, y: 0, w: 6, h: 2, id: p_base.uuid() } );
            const child2 = subGrid.addWidget( { x: 6, y: 0, w: 6, h: 2, id: p_base.uuid() } );

            const $child1 = $(child1);
            $child1.data( grid._KEY_GADGET_, existingGadget );
            $child1.find('.grid-stack-item-content')
                   .empty()
                   .append( existingGadget.getContainer() );

            existingGadget.getContainer().css('width', '100%').css('height', '100%');
            existingGadget.show();

            this._setFocus_( child1 );
        }
        else
        {
            // Case (a): empty card — create two empty children
            const child1 = subGrid.addWidget( { x: 0, y: 0, w: 6, h: 2, id: p_base.uuid() } );
            subGrid.addWidget( { x: 6, y: 0, w: 6, h: 2, id: p_base.uuid() } );

            this._setFocus_( child1 );
        }

        this._isChanged_ = true;
        this._updateButtonsState_();

        return this;
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

        const node = this._$focused_[0].gridstackNode;

        // Card with sub-grid: clone the entire nested structure
        if( node && node.subGrid && node.subGrid.el )
        {
            this._cloneSubGridCard_();
            return this;
        }

        const gadget = this._$focused_.data( grid._KEY_GADGET_ );

        if( ! gadget )
        {
            // Allow cloning empty cards too
            this._setFocus_( this.addCard() );
            this._isChanged_ = true;
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

        // Check if any selected card contains a sub-grid
        let bHasSubGrid = false;

        for( const $card of this._aCardsSelected_ )
        {
            const node = $card[0].gridstackNode;

            if( node && node.subGrid && node.subGrid.el )
            {
                bHasSubGrid = true;
                break;
            }
        }

        const sMsg = bHasSubGrid ? "Delete selected card(s) including inner grid(s) and all their contents?"
                                 : "Do you want to delete selected card(s)?";

        p_app.confirm( sMsg, () =>
                        {
                            for( const $card of grid._aCardsSelected_ )
                            {
                                const ownerGrid = grid._getOwnerGrid_( $card[0] );
                                ownerGrid.removeWidget( $card[0] );
                            }

                            // Check if any sub-grid became empty and should be removed
                            grid._cleanEmptySubGrids_();

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
                       .first()                              // Only the direct content, not nested sub-grid content
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
        for( const gadget of this._getGadgets_() )
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
                                '<td>Shift-Click</td><td>Add/Remove clicked card to the selected ones</td>'+
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
                                '<td>Esc</td><td>Closes dialogs</td>'+
                            '</tr>'+
                        '</tbody>'+
                    '</table>'+
                    '<p class="mt-1 is-pulled-right" style="font-size:80%">You can press [Esc] to close this dialog</p>';

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
                                            column: 18,
                                            cellHeight: 20,
                                            sizeToContent: true,
                                            // column: 12,
                                            // minRow: 1,
                                            // cellHeight: 80,
                                            float: true,
                                            resizable: { handles: 'n,nw,ne,e,se,s,sw,w' },
                                            alwaysShowResizeHandle: 'mobile',  // true if we're on mobile devices
                                            disableOneColumnMode: true,
                                            animate: true
                                        } );

        grid._stack_.on('dragstart', (evt, el) => grid._onGadgetDragStart_(evt, el));
        grid._stack_.on('dragstop' , (evt, el) => grid._onGadgetDragStop_( evt, el));

        grid._stack_.on('resizestop', (evt, div) =>
                        {
                            grid._isChanged_ = true;

                            const gadget = $(div.gridstackNode.el).data( grid._KEY_GADGET_ );

                            if( gadget )      // 'if' because the widget could be empty
                                gadget.show();

                            if( ! gum.isInDesignMode() )
                                gum._save_();
                        } );

        if( gum.isInDesignMode() )
        {
            $('body').on( 'mousedown',
                          '.grid-stack-item',
                          function(e)
                          {
                              e.stopPropagation();              // Prevent parent cards from stealing focus
                              grid._setFocus_( this, e );
                          } );

            $('body').on( 'dblclick',
                          '.grid-stack-item',
                          function(e)
                          {
                              e.stopPropagation();
                              if( grid._aCardsSelected_.length === 1 ) grid.editCard();
                          } );

            $(document).on( 'keydown', function(evt) { grid._onKeyPressed_(evt); } );

            $('#gum-toolbar').append( $('<br>'+
                                        '<div style="display:flex; justify-content:flex-end; align-items:center;">'+
                                            '<i class="gum-mini-btn ti ti-plus"         id="grid-btn-add"     title="Add a new card"                     onclick="grid.addCard()"  ></i>'+
                                            '<i class="gum-mini-btn ti ti-library-plus" id="grid-btn-addgrid" title="Add an inner grid to selected card" onclick="grid.addGrid()"  ></i>'+
                                            '<i class="gum-mini-btn ti ti-copy"         id="grid-btn-clone"   title="Clone highlighted card"             onclick="grid.cloneCard()"></i>'+
                                            '<i class="gum-mini-btn ti ti-trash"        id="grid-btn-del"     title="Delete highlighted card"            onclick="grid.delCard()"  ></i>'+
                                        '</div>') );

            grid._updateButtonsState_();
        }

        return this;
    },

    //------------------------------------------------------------------------//
    // Private methods

    /**
     * Returns the GridStack instance that directly owns the given card element.
     *
     * @param {HTMLElement} el A `.grid-stack-item` element.
     * @returns {GridStack} The owning GridStack instance.
     */
    _getOwnerGrid_ : function( el )
    {
        const parentGridEl = $(el).closest('.grid-stack')[0];

        return (parentGridEl && parentGridEl.gridstack) ? parentGridEl.gridstack
                                                        : this._stack_;
    },

    /**
     * Returns the GridStack instance where new cards should be added,
     * based on the currently focused card.
     *
     * - If focused card has a sub-grid, returns that sub-grid.
     * - If focused card lives inside a sub-grid, returns that sub-grid.
     * - Otherwise returns the main grid.
     *
     * @returns {GridStack}
     */
    _getActiveGrid_ : function()
    {
        if( ! this._$focused_ )
            return this._stack_;

        const node = this._$focused_[0].gridstackNode;

        // Focused card has a sub-grid: add into it
        if( node && node.subGrid && node.subGrid.el )
            return node.subGrid;

        // Focused card is inside a sub-grid: add to same sub-grid
        const ownerGrid = this._getOwnerGrid_( this._$focused_[0] );

        if( ownerGrid !== this._stack_ )
            return ownerGrid;

        return this._stack_;
    },

    _getCardById_ : function( id )
    {
        // Search main grid first
        let found = this._stack_.getGridItems().find( item =>
                                                       item.getAttribute('gs-id') === id );
        if( found )
            return found;

        // Search inside sub-grids
        const subGridEls = document.querySelectorAll('.grid-stack .grid-stack');

        for( const sgEl of subGridEls )
        {
            if( ! sgEl.gridstack )
                continue;

            found = sgEl.gridstack.getGridItems().find( item =>
                                                         item.getAttribute('gs-id') === id );
            if( found )
                return found;
        }

        return undefined;
    },

    /**
     * Collects all gadgets from the main grid and all sub-grids recursively.
     *
     * @returns {Array} Array of gadget instances.
     */
    _getGadgets_ : function()
    {
        let aGadgets = [];

        this._collectGadgets_( this._stack_, aGadgets );

        return aGadgets;
    },

    /**
     * Recursively collects gadgets from a GridStack instance and its sub-grids.
     *
     * @param {GridStack} gridInstance The grid to collect from.
     * @param {Array}     aGadgets    Accumulator array.
     */
    _collectGadgets_ : function( gridInstance, aGadgets )
    {
        const items = gridInstance.getGridItems();

        for( const el of items )
        {
            const $el    = $(el);
            const gadget = $el.data( this._KEY_GADGET_ );

            if( gadget )
                aGadgets.push( gadget );

            // Recurse into sub-grid if present
            const node = el.gridstackNode;

            if( node && node.subGrid && node.subGrid.el )
                this._collectGadgets_( node.subGrid, aGadgets );
        }
    },

    /**
     * Loads a card that contains a sub-grid from saved data.
     *
     * @param {Object} card The saved card data with widget.subGrid and childGadgets.
     */
    _loadSubGridCard_ : function( card )
    {
        const widgetData   = Object.assign( {}, card.widget );
        const subGridData  = widgetData.subGrid;
        const childGadgets = card.childGadgets || {};

        // Remove subGrid from widget data so addWidget doesn't auto-create it
        delete widgetData.subGrid;
        widgetData.content = "";

        const parentEl = this._stack_.addWidget( widgetData );

        // Create the sub-grid
        const subGrid = this._stack_.makeSubGrid( parentEl, this._SUB_GRID_OPTS_, null, false );

        // Add children
        if( subGridData && subGridData.children )
        {
            for( const childWidget of subGridData.children )
            {
                childWidget.content = "";

                const childEl = subGrid.addWidget( childWidget );
                const childId = childWidget.id;

                if( childId && childGadgets[ childId ] )
                {
                    const gadget  = GumGadget.instantiate( childGadgets[ childId ] );
                    const $child  = $(childEl);

                    $child.data( this._KEY_GADGET_, gadget );
                    $child.find('.grid-stack-item-content')
                          .empty()
                          .append( gadget.getContainer() );

                    gadget.getContainer().css('width', '100%').css('height', '100%');
                    gadget.show();
                }
            }
        }
    },

    /**
     * Clones the focused card that contains a sub-grid,
     * duplicating its nested structure and all child gadgets.
     */
    _cloneSubGridCard_ : function()
    {
        const sourceEl   = this._$focused_[0];
        const sourceNode = sourceEl.gridstackNode;

        if( ! sourceNode || ! sourceNode.subGrid || ! sourceNode.subGrid.el )
            return;

        // Create a new parent card in the main grid
        const newParent = this._stack_.addWidget( {
            x: 0, y: 0,
            w: parseInt( sourceEl.getAttribute('gs-w') ) || 4,
            h: parseInt( sourceEl.getAttribute('gs-h') ) || 2,
            id: p_base.uuid()
        } );

        // Create sub-grid in new parent
        const newSubGrid = this._stack_.makeSubGrid( newParent, this._SUB_GRID_OPTS_, null, false );

        // Clone each child from the source sub-grid
        const sourceItems = sourceNode.subGrid.getGridItems();

        for( const childEl of sourceItems )
        {
            const $child  = $(childEl);
            const childW  = parseInt( childEl.getAttribute('gs-w') ) || 6;
            const childH  = parseInt( childEl.getAttribute('gs-h') ) || 2;
            const childX  = parseInt( childEl.getAttribute('gs-x') ) || 0;
            const childY  = parseInt( childEl.getAttribute('gs-y') ) || 0;
            const gadget  = $child.data( this._KEY_GADGET_ );

            const newChild = newSubGrid.addWidget( {
                x: childX, y: childY, w: childW, h: childH, id: p_base.uuid()
            } );

            if( gadget )
            {
                const cloned  = gadget.clone();
                const $newCh  = $(newChild);

                $newCh.data( this._KEY_GADGET_, cloned );
                $newCh.find('.grid-stack-item-content')
                      .empty()
                      .append( cloned.getContainer() );

                cloned.getContainer().css('width', '100%').css('height', '100%');
                cloned.show();
            }
        }

        this._setFocus_( newParent );
        this._isChanged_ = true;
    },

    /**
     * Checks all sub-grids and removes any that have zero children,
     * converting the parent back to a plain empty card.
     */
    _cleanEmptySubGrids_ : function()
    {
        const mainItems = this._stack_.getGridItems();

        for( const el of mainItems )
        {
            const node = el.gridstackNode;

            if( ! node || ! node.subGrid || ! node.subGrid.el )
                continue;

            if( node.subGrid.getGridItems().length === 0 )
            {
                // Remove the sub-grid DOM and reset the node
                const subGridEl = node.subGrid.el;

                node.subGrid.destroy( false );
                $(subGridEl).remove();
                delete node.subGrid;

                // Clear the content area
                $(el).find('.grid-stack-item-content').empty();
            }
        }
    },

    _setFocus_ : function( card, e )
    {
        const bShift = e && e.shiftKey;

        if( ! bShift )
        {
            for( const $card of this._aCardsSelected_ )
                $card.find('> .grid-stack-item-content').removeClass('grid-widget-focus');

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
                    this._aCardsSelected_[ nIdx ].find('> .grid-stack-item-content').removeClass('grid-widget-focus');
                    this._aCardsSelected_.splice( nIdx, 1 );
                }
            }
            else
            {
                this._aCardsSelected_.push( this._$focused_ );
            }

            for( const $card of this._aCardsSelected_ )
                $card.find('> .grid-stack-item-content').addClass('grid-widget-focus');

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
        const nSelected    = this._aCardsSelected_.length;
        const bCanClone    = nSelected === 1;
        const bCanDel      = nSelected > 0;

        // "Add Grid" enabled when exactly 1 card selected and it has no sub-grid yet
        let bCanAddGrid = false;

        if( nSelected === 1 )
        {
            const node = this._aCardsSelected_[0][0].gridstackNode;
            bCanAddGrid = ! (node && node.subGrid && node.subGrid.el);
        }

     // $('#grid-btn-add'      ).prop('disabled', ! hasFocusedWidget);  --> is always enabled
        $('#grid-btn-addgrid'  ).prop('disabled', ! bCanAddGrid);
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

        if( ! gum.isInDesignMode() )
            gum._save_();
    }
};
}