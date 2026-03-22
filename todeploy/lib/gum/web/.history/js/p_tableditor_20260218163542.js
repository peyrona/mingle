 //
// NOTE: I have a tableeditor.js in bookup that I only use for bookup, this one
// has been improved and uses Bulma instead of BootStrap.
//
// -----------------------------------------------------------------------------
// A generic Table Editor.
// This class depends only on JQuery.
//
// All parameters passed to this class's constructor are encapsulated in a JSON
// object: { key1 : value1, key2 : value2, ... }
//
// The data to be shown will be passed in an object as follows (see
// this:setData(...) for more information)
//      { colName1 : colvalue1, colName2 : colvalue2, ... }
//
// Every cell has an internal value (any JS valid data type can be used) that is
// used for all operations and another visible value (normally the textual
// representation of internal value).
//
// Note: for those <select></select> editors, if selected <option> has the "value='...'", this value will be assigned to the cell's
//       internal value and the option's text will be assigned to the cell's text. If option does not have the "value", then option's
//       text will be used for both: cell's internal value and cell's shown text.
//
// ---------------------------------------------------------------------------------------------------------------------------------
// Key             Value explanation
// ---------------------------------------------------------------------------------------------------------------------------------
// table         : A DOM HTML table element where the data will be shown.
//                 Passed table must be a valid HTML one, but tHead, tFooter and tBodies are optional (if tbody does not exist, one.
//                 will be created, if there is more than one tbody, the first will be used).
//                 This is the only parameter that is NOT optional.
//
// toolbar       : A DOM HTML element where the buttons will be shown. If it is not passed or it does not corresponds to a valid
//                 HTML element (v.g. a div or table cell) buttons will not be shown.
//
// onSave        : Function to be invoked when the 'Save' button is clicked. If null, the button will be shown disabled and the
//                 function ::save() has to be invoked manually to retrieve current data. It receives an instance of this class.
//
// onPrint       : Function to be invoked to print the table. It receives an instance of this class.
//
// onHelp        : Function to be invoked to show the help text. It receives an instance of this class.
//
// onInfo        : Function to be invoked to show the info text. It receives an instance of this class.
//
// onRowSelected : Function to be invoked after a row is selected (becomes highlighted). It receives: an instance of this class,
//                 the old selected and the new selected row indexes. Both are the index that correspond to the internal
//                 'table.tBodies[0].rows' array.
//
// onRowAppended : Function to be invoked after a new row is appended. It receives an instance of this class and the index of
//                 the new row (the index in the internal 'table.tBodies[0].rows' array).
//
// onDeleteRow   : Function to be invoked before a row is deleted. It receives an instance of this class and the index of the
//                 row (the index in the internal 'table.tBodies[0].rows' array). If function returns false, the row will not
//                 be deleted.
//
// onRowDeleted  : Function to be invoked after a row is deleted. It receives an instance of this class as its solely argument.
//
// onRowCloned   : Function to be invoked after selected row is cloned (duplicated). It receives an instance of this class and
//                 the index of the new duplicated row (the index in the internal 'table.tBodies[0].rows' array). As cloned row
//                 becomes the last row in the table, its index is the highest in 'table.tBodies[0].rows'.
//
// columns       : An array of objects where each element of the array is as follows (all except "name" are optional):
//                 { "name": <colName>, "default": <defVal>, "minwidth": <nChars>, "minwidth": <nChars>, "editor": <html> },
//                 Being:
//                    * colName: the name of the key column as passed in the Data
//                    * defVal : the default value for clone, append and when user editing leaves an invalid value
//                    * width  : editor's min and max widths in numbers of chars (unit 'ch': width of "0"): 'minwidth' & 'maxwidth'
//                    * editor : the html needed to edit the cell value
//                 Following are valid values for the editor property:
//                    * null  -> column will be invisible, its values will not be cloned and it will be not editable.
//                    * false -> column will be invisible, its values will be cloned but it will not be editable.
//                    * true  -> column will be visible, its values will be cloned but it will be not editable.
//                    * HTML  -> to be inserted in the cell when it becomes editable: only "input" and "select" are allowed.
//                 Columns that appear in the setData( data ), but not having a column definition, will be ignored. And columns
//                 that have a definition but does not appear in the setData( data ), will exists only here (calculated column).
//                 If this parameter is not passed or it's null all columns will be invisible. If none of the columns is editable,
//                 (does not have an editor) the table will be not editable and only the toolbar's print button will be shown.
//
// cellFormatter : A function that will receive an instance of this class as first argument, the name of the column as second
//                 argument and current value as third argument. Function (if exists) must return the value to be shown as
//                 cell's content (it can be pure text or HTML).
//
// onCellPreEdit : A function that will receive an instance of this class as first argument, the row index as second argument, the
//                 the name of the column as third argument and the current value as the forth argument. Function (if exists) must
//                 return a valid JS value. If the value is not null, the cell will become editable, its internal value will be
//                 updated and its contents will be the value (previously formatted if the formatter function exists). If null, the
//                 cell will not be editable. Function is invoked before cell editing starts.
//
// onCellPostEdit: A function that will receive an instance of this class as first argument, the row index as second argument, the
//                 the name of the column as third argument, the current value as the fourth argument and a boolean being true if
//                 the value changed (is different) or did not changed during edition. Function (if exists) must return a value;
//                 it will be used as cell's internal value. Function is invoked after cell editing ends.
//
// onError       : Function to be invoked when an error occurs. It receives an instance of this class and an error object with
//                 code, message, timestamp, and details properties.
//
// enableKeyboardNav  : Boolean to enable/disable keyboard navigation. Default: true.
//
// enableTouchSupport : Boolean to enable/disable touch support for mobile devices. Default: true.
//
// enableVirtualScroll : Boolean to enable/disable virtual scrolling for large datasets. Default: false.
//
// rowHeight         : Height of each row in pixels (for virtual scrolling). Default: 35.
//
// debounceDelay    : Delay in milliseconds for debouncing resize events. Default: 100.
//
// unselectedRowInk   : Hex color code for unselected rows text. Default: "#000000".
//
// unselectedRowPaper : Hex color code for unselected rows background. Default: "#ffffff".
//
// selectedRowInk     : Hex color code for selected row text. Default: "#000000".
//
// selectedRowPaper   : Hex color code for selected row background. Default: "#cee3f6".
// ---------------------------------------------------------------------------------------------------------------------------------

/* global basico, p_base */

"use strict";

class TableEditor
{
    constructor( parameters )
    {
        this._validateParameters_( parameters );

        const styles = this._getStyles_( parameters );
        const callbacks = this._getCallbacks_( parameters );

        this.table              = p_base.get( parameters.table );
        this.toolbar            = (parameters.hasOwnProperty( 'toolbar' ) ? p_base.get( parameters.toolbar ) : null);
        this.tbody              = (! this.table.tBodies || this.table.tBodies.length === 0) ? this.table.createTBody() : this.table.tBodies[0];
        this.onSave             = callbacks.onSave;
        this.onPrint            = callbacks.onPrint;
        this.onHelp             = callbacks.onHelp;
        this.onInfo             = callbacks.onInfo;
        this.onRowSelected      = callbacks.onRowSelected;
        this.onRowAppended      = callbacks.onRowAppended;
        this.onRowCloned        = callbacks.onRowCloned;
        this.onDeleteRow        = callbacks.onDeleteRow;
        this.onRowDeleted       = callbacks.onRowDeleted;
        this.onRowMoved         = callbacks.onRowMoved;
        this.cellFormatter      = callbacks.cellFormatter;
        this.cellPreValidator   = callbacks.cellPreValidator;
        this.cellPostValidator  = callbacks.cellPostValidator;
        this.onError            = callbacks.onError;
        this.colDefinitions     = (parameters.hasOwnProperty( 'columns' ) ? parameters.columns : null);
        this.deletedRows        = [];
        this.selectedRowIndex   = -1;          // Absolute: this.tbody.rows[this_index]
        this.cellEditor         = null;        // Current cell editor (not null only meanwhile editing cell content)
        this.unselectedRowInk   = styles.unselectedRowInk;
        this.unselectedRowPaper = styles.unselectedRowPaper;
        this.selectedRowInk     = styles.selectedRowInk;
        this.selectedRowPaper   = styles.selectedRowPaper;
        this.enableKeyboardNav  = (parameters.hasOwnProperty( 'enableKeyboardNav' ) ? parameters.enableKeyboardNav : true);
        this.isBtnAppendEnabled   = true;
        this.isBtnCloneEnabled    = true;
        this.isBtnMoveUpEnabled   = true;
        this.isBtnMoveDownEnabled = true;
        this.isBtnSaveEnabled     = true;
        this.isBtnDeleteEnabled   = true;
        this.isBtnPrintEnabled    = (this.onPrint !== null);
        this.isBtnHelpEnabled     = (this.onHelp  !== null);
        this.isBtnInfoEnabled     = (this.onInfo  !== null);
        this.btnAppendId        = p_base.uuid();           //----------------------------------------------------------------
        this.btnCloneId         = p_base.uuid();           // All buttons (in the button bar) for each instance of this class
        this.btnMoveUpId        = p_base.uuid();           // must have different element id (<button id="...">)
        this.btnMoveDownId      = p_base.uuid();           // In order for the functions to be able to refer them properly.
        this.btnSaveId          = p_base.uuid();           // These variables store these ids.
        this.btnDeleteId        = p_base.uuid();           //
        this.btnPrintId         = p_base.uuid();           //
        this.btnHelpId          = p_base.uuid();           //
        this.btnInfoId          = p_base.uuid();           //----------------------------------------------------------------

        this.bucket = null;   // Multipurpose var. Used right now only by the cellEditor to save the value of the cell before start editing
        this._eventHandlers    = [];  // Store event handlers for cleanup
        this.enableTouchSupport = (parameters.hasOwnProperty( 'enableTouchSupport' ) ? parameters.enableTouchSupport : true);
        this.enableVirtualScroll = (parameters.hasOwnProperty( 'enableVirtualScroll' ) ? parameters.enableVirtualScroll : false);
        this.rowHeight = (parameters.hasOwnProperty( 'rowHeight' ) ? parameters.rowHeight : 35);
        this.debounceDelay = (parameters.hasOwnProperty( 'debounceDelay' ) ? parameters.debounceDelay : 100);

        this.selectedCell = null;  // Currently selected cell (for cell navigation)
        this.allData = [];  // Complete dataset (for virtual scrolling)
        this.visibleStart = 0;  // First visible row index (for virtual scrolling)
        this.visibleEnd = 0;    // Last visible row index (for virtual scrolling)
        this.resizeObserver = null;  // Resize observer for debouncing
        this._debounceTimer = null;  // Debounce timer reference

        this._setClickEvent_( true )
            ._setToolbar_()
            ._setupAccessibility_()
            ._setupKeyboardNavigation_()
            ._setupTouchSupport_()
            ._setupVirtualScroll_()
            ._setupResizeObserver_()
            ._refreshButtons_();
    }

    //----------------------------------------------------------------------------//

    /**
     * Centralized error handling with consistent error types and codes.
     * @private
     * @param {string} code - Error code for identification
     * @param {string} message - Human-readable error message
     * @param {*} details - Additional error details
     * @throws {Error} Always throws an error
     */
    _error_( code, message, details = null )
    {
        const error = {
            code: code,
            message: message,
            timestamp: new Date().toISOString(),
            details: details
        };

        console.error( `[TableEditor ${code}] ${message}`, details || '' );

        if( typeof this.onError === 'function' )
        {
            this.onError( this, error );
        }

        throw new Error( message );
    }

    /**
     * Validates constructor parameters.
     * @private
     * @param {Object} parameters - Configuration parameters
     * @throws {Error} If validation fails
     */
    _validateParameters_( parameters )
    {
        if( ! parameters || typeof parameters !== 'object' )
        {
            this._error_( 'INVALID_PARAMS', 'Parameters must be an object' );
        }

        if( ! parameters.hasOwnProperty( 'table' ) )
        {
            this._error_( 'MISSING_TABLE', 'Mandatory parameter "table" not found' );
        }

        const tableEl = p_base.get( parameters.table );
        if( ! tableEl || tableEl === parameters.table )
        {
            this._error_( 'TABLE_NOT_FOUND', `Table '${parameters.table}' not found` );
        }

        if( parameters.hasOwnProperty( 'toolbar' ) )
        {
            const toolbarEl = p_base.get( parameters.toolbar );
            if( toolbarEl && toolbarEl === parameters.toolbar )
            {
                this._error_( 'TOOLBAR_NOT_FOUND', `Toolbar '${parameters.toolbar}' not found` );
            }
        }

        if( parameters.hasOwnProperty( 'columns' ) )
        {
            const columns = parameters.columns;
            if( ! Array.isArray( columns ) )
            {
                this._error_( 'INVALID_COLUMNS', 'Columns parameter must be an array' );
            }

            for( let i = 0; i < columns.length; i++ )
            {
                if( ! columns[i].hasOwnProperty( 'name' ) || ! p_base.isString( columns[i].name ) )
                {
                    this._error_( 'INVALID_COLUMN', `Column at index ${i} must have a valid "name" property` );
                }
            }
        }

        return true;
    }

    /**
     * Extracts and validates style parameters.
     * @private
     * @param {Object} parameters - Configuration parameters
     * @returns {Object} Style configuration
     */
    _getStyles_( parameters )
    {
        return {
            unselectedRowInk:   parameters.unselectedRowInk   || '#000000',
            unselectedRowPaper: parameters.unselectedRowPaper || '#ffffff',
            selectedRowInk:     parameters.selectedRowInk     || '#000000',
            selectedRowPaper:   parameters.selectedRowPaper   || '#cee3f6'
        };
    }

    /**
     * Extracts and validates callback functions.
     * @private
     * @param {Object} parameters - Configuration parameters
     * @returns {Object} Callback configuration
     */
    _getCallbacks_( parameters )
    {
        return {
            onSave:           (parameters.hasOwnProperty( 'onSave'        ) && p_base.isFunction( parameters.onSave         ) ? parameters.onSave         : null),
            onPrint:          (parameters.hasOwnProperty( 'onPrint'       ) && p_base.isFunction( parameters.onPrint        ) ? parameters.onPrint        : null),
            onHelp:           (parameters.hasOwnProperty( 'onHelp'        ) && p_base.isFunction( parameters.onHelp         ) ? parameters.onHelp         : null),
            onInfo:           (parameters.hasOwnProperty( 'onInfo'        ) && p_base.isFunction( parameters.onInfo         ) ? parameters.onInfo         : null),
            onRowSelected:    (parameters.hasOwnProperty( 'onRowSelected' ) && p_base.isFunction( parameters.onRowSelected  ) ? parameters.onRowSelected  : null),
            onRowAppended:    (parameters.hasOwnProperty( 'onRowAppended' ) && p_base.isFunction( parameters.onRowAppended  ) ? parameters.onRowAppended  : null),
            onRowCloned:      (parameters.hasOwnProperty( 'onRowCloned'   ) && p_base.isFunction( parameters.onRowCloned    ) ? parameters.onRowCloned    : null),
            onDeleteRow:      (parameters.hasOwnProperty( 'onDeleteRow'   ) && p_base.isFunction( parameters.onDeleteRow    ) ? parameters.onDeleteRow    : null),
            onRowDeleted:     (parameters.hasOwnProperty( 'onRowDeleted'  ) && p_base.isFunction( parameters.onRowDeleted   ) ? parameters.onRowDeleted   : null),
            cellFormatter:    (parameters.hasOwnProperty( 'cellFormatter' ) && p_base.isFunction( parameters.cellFormatter  ) ? parameters.cellFormatter  : null),
            cellPreValidator: (parameters.hasOwnProperty( 'onCellPreEdit' ) && p_base.isFunction( parameters.onCellPreEdit  ) ? parameters.onCellPreEdit  : null),
            cellPostValidator: (parameters.hasOwnProperty( 'onCellPostEdit') && p_base.isFunction( parameters.onCellPostEdit ) ? parameters.onCellPostEdit : null),
            onError:          (parameters.hasOwnProperty( 'onError'        ) && p_base.isFunction( parameters.onError         ) ? parameters.onError         : null),
            onRowMoved:       (parameters.hasOwnProperty( 'onRowMoved'     ) && p_base.isFunction( parameters.onRowMoved      ) ? parameters.onRowMoved      : null)
        };
    }

    /**
     * Sets up ARIA attributes for accessibility (WCAG 2.1 AA).
     * @private
     * @returns {TableEditor} this
     */
    _setupAccessibility_()
    {
        this.table.setAttribute( 'role', 'grid' );
        this.table.setAttribute( 'aria-label', 'Editable data table' );

        this.tbody.setAttribute( 'tabindex', '0' );
        this.tbody.setAttribute( 'aria-live', 'polite' );

        const rows = this.tbody.rows;
        for( let i = 0; i < rows.length; i++ )
        {
            rows[i].setAttribute( 'role', 'row' );
            rows[i].setAttribute( 'aria-rowindex', i + 1 );
        }

        return this;
    }

    /**
     * Updates ARIA attributes when row is selected.
     * @private
     * @param {number} rowIndex - Index of selected row
     */
    _updateAriaSelection_( rowIndex )
    {
        const rows = this.tbody.rows;
        for( let i = 0; i < rows.length; i++ )
        {
            rows[i].setAttribute( 'aria-selected', (i === rowIndex) ? 'true' : 'false' );
        }
    }

    /**
     * Sets up keyboard navigation for table.
     * @private
     * @returns {TableEditor} this
     */
    _setupKeyboardNavigation_()
    {
        if( ! this.enableKeyboardNav )
        {
            return this;
        }

        const self = this;

        const keyHandler = function( event )
        {
            if( self.cellEditor !== null )
            {
                return;
            }

            if( self.selectedRowIndex < 0 )
            {
                return;
            }

            switch( event.key )
            {
                case 'ArrowUp':
                    event.preventDefault();
                    self._navigateRow_( -1 );
                    break;
                case 'ArrowDown':
                    event.preventDefault();
                    self._navigateRow_( 1 );
                    break;
                case 'Home':
                    event.preventDefault();
                    if( self.getBodyRowCount() > 0 )
                    {
                        self.selectRowAtIndex( 0 );
                    }
                    break;
                case 'End':
                    event.preventDefault();
                    const rowCount = self.getBodyRowCount();
                    if( rowCount > 0 )
                    {
                        self.selectRowAtIndex( rowCount - 1 );
                    }
                    break;
            }
        };

        $(this.tbody).on( 'keydown', keyHandler );
        this._eventHandlers.push( { element: this.tbody, event: 'keydown', handler: keyHandler } );

        return this;
    }

    /**
     * Navigates to previous or next row.
     * @private
     * @param {number} direction - Direction: -1 for up, 1 for down
     */
    _navigateRow_( direction )
    {
        const newIndex = this.selectedRowIndex + direction;
        if( newIndex >= 0 && newIndex < this.getBodyRowCount() )
        {
            this.selectRowAtIndex( newIndex );
        }
    }

    /**
     * Properly cleans up all resources to prevent memory leaks.
     * @returns {TableEditor} this
     */
    destroy()
    {
        $(this.tbody).off();
        $(this.table).off();

        if( this.toolbar !== null )
        {
            $(this.toolbar).off();
        }

        this._eventHandlers.forEach( handler =>
        {
            handler.element.removeEventListener( handler.event, handler.handler );
        });
        this._eventHandlers = [];

        if( this.resizeObserver !== null )
        {
            this.resizeObserver.disconnect();
            this.resizeObserver = null;
        }

        if( this._debounceTimer !== null )
        {
            clearTimeout( this._debounceTimer );
            this._debounceTimer = null;
        }

        this.tbody = null;
        this.table = null;
        this.toolbar = null;
        this.cellEditor = null;
        this.colDefinitions = null;
        this.deletedRows = null;
        this.selectedCell = null;
        this.allData = [];

        this.onSave = null;
        this.onPrint = null;
        this.onHelp = null;
        this.onInfo = null;
        this.onRowSelected = null;
        this.onRowAppended = null;
        this.onRowCloned = null;
        this.onDeleteRow = null;
        this.onRowDeleted = null;
        this.cellFormatter = null;
        this.cellPreValidator = null;
        this.cellPostValidator = null;
        this.onError = null;

        return this;
    }

    //----------------------------------------------------------------------------//

    /**
     * Returns true if table is empty (head rows does not count).
     * @returns {Boolean} true if table is empty.
     */
    isEmpty()
    {
        return this.getBodyRowCount() === 0;
    }

    /**
     * Gets currently selected cell.
     * @public
     * @returns {HTMLTableCellElement|null} The selected cell element or null if no cell is selected.
     *
     * @example
     * const editor = new TableEditor({ table: '#myTable', columns: [...] });
     * editor.selectCell(0, 'name');
     * const selectedCell = editor.getSelectedCell();
     */
    getSelectedCell()
    {
        return this.selectedCell;
    }

    /**
     * Selects a specific cell and highlights it.
     * @public
     * @param {number} rowIndex - Row index (0-based, relative to tbody).
     * @param {number|string} colIndexOrName - Column index (0-based) or column name.
     * @returns {TableEditor} Returns this for method chaining.
     *
     * @example
     * const editor = new TableEditor({ table: '#myTable', columns: [...] });
     * editor.selectCell(0, 'name'); // Selects cell at row 0, column named 'name'
     * editor.selectCell(0, 2);     // Selects cell at row 0, column index 2
     */
    selectCell( rowIndex, colIndexOrName )
    {
        const cell = this.getCell( rowIndex, colIndexOrName );

        if( ! cell )
        {
            return this;
        }

        this.selectedCell = cell;
        this.selectRowAtIndex( rowIndex );

        const cells = this.tbody.rows[rowIndex].cells;
        for( let i = 0; i < cells.length; i++ )
        {
            $(cells[i]).css( 'background-color', '' );
        }

        $(cell).css( 'background-color', '#fffacd' ); // Light yellow highlight

        return this;
    }

    /**
     * Clears the deleted rows history.
     * @public
     * @returns {TableEditor} Returns this for method chaining.
     *
     * @example
     * const editor = new TableEditor({ table: '#myTable', columns: [...] });
     * editor.deleteRow(0);
     * editor.clearDeletedRows(); // Clears the history of deleted rows
     */
    clearDeletedRows()
    {
        this.deletedRows = [];
        return this;
    }

    /**
     * Gets table statistics including row counts, columns, and other metrics.
     * @public
     * @returns {Object} Statistics object with properties:
     *                totalRows - Total number of rows in table
     *                editableRows - Number of non-empty rows
     *                emptyRows - Number of empty rows
     *                deletedRows - Number of deleted rows in history
     *                totalColumns - Total number of column definitions
     *                editableColumns - Number of columns with editors
     *                selectedRowIndex - Index of currently selected row (-1 if none)
     *
     * @example
     * const editor = new TableEditor({ table: '#myTable', columns: [...] });
     * editor.setData([{ id: 1, name: 'Test' }]);
     * const stats = editor.getStatistics();
     * console.log(`Total rows: ${stats.totalRows}, Empty: ${stats.emptyRows}`);
     */
    getStatistics()
    {
        const data = this.getData( false );
        const emptyRows = data.filter( (row, index) => this.isEmptyRow( index ) ).length;

        return {
            totalRows: data.length,
            editableRows: data.length - emptyRows,
            emptyRows: emptyRows,
            deletedRows: this.deletedRows.length,
            totalColumns: this.colDefinitions ? this.colDefinitions.length : 0,
            editableColumns: this.colDefinitions ? this.colDefinitions.filter( col => col.editor ).length : 0,
            selectedRowIndex: this.selectedRowIndex
        };
    }

    /**
     * Returns current internal value for each table's cell (skipping thead).
     * <p>
     * returned data has following structure:
     * [[row1-col1Name: row1-col1Value, row1-col2Name: row1-col2Value,..., row1-colNName: row1-colNValue],
     *  [row2-col2Name: row2-col1Value, row2-col2Name: row2-col2Value,..., row2-colNName: row2-colNValue],
     *  ...
     *   [rowN-col1name: rowN-col1Value, rowN-col2Name: rowN-col2Value,..., rowN-colNName: rowN-colNValue]]
     *
     * @param {boolean} bClean1st : true to call this:clean(). By default it is true.
     * @returns {Array} Of JSON objects
     */
    getData( bClean1st = true )
    {
        if( bClean1st )
        {
            this.clean();    // Deletes all empty rows
        }

        let data = [];       // Array of JSON objects to return

        for( let nRow = 0; nRow < this.tbody.rows.length; nRow++ )
        {
            data.push( this.getRowData( nRow ) );
        }

        return data;
    }

    /**
     * Replaces current data with the new ones.
     * <p>
     * If passed data is not a array of JSON objects or it is empty, table will be empty.
     *
     * @param {Array} aData An array of JSON objects containing all table rows and columns.
     * @returns this.
     */
    setData( aData )
    {
        this.clear();

        this.allData = (! p_base.isEmpty( aData )) ? aData : [];

        if( (! p_base.isEmpty( aData )) &&
            (this.colDefinitions !== null) )
        {
            if( this.enableVirtualScroll )
            {
                this.visibleRows = Math.ceil( this.table.clientHeight / this.rowHeight );
                this.visibleStart = 0;
                this.visibleEnd = Math.min( this.visibleRows, this.allData.length );

                for( let row = 0; row < this.visibleEnd; row++ )
                {
                    this._addCells_( this.tbody.insertRow( -1 ), this.allData[row] );
                }

                this.selectRowAtIndex( 0 );
            }
            else
            {
                for( let row = 0; row < aData.length; row++ )
                {
                    this._addCells_( this.tbody.insertRow( -1 ), aData[row] );      // -1 == insert at end of tbody (append)
                }

                this.selectRowAtIndex( 0 );
            }
        }

        this._refreshButtons_();

        return this;
    }

    /**
     * Returns non empty rows that were deleted (either by the user or
     * programmatically) or an empty array if no row was deleted.
     * <p>
     * returned data has following structure:
     * [[row1-col1Name: row1-col1Value, row1-col2Name: row1-col2Value,..., row1-colNName: row1-colNValue],
     *  [row2-col2Name: row2-col1Value, row2-col2Name: row2-col2Value,..., row2-colNName: row2-colNValue],
     *  ...
     *   [rowN-col1name: rowN-col1Value, rowN-col2Name: rowN-col2Value,..., rowN-colNName: rowN-colNValue]]
     *
     * @returns {Array} Of JSON objects
     */
    getDeletedData()
    {
        return this.deletedRows;
    }

    /**
     * Returns Current cell editor: not null only meanwhile user is editing a cell.
     *
     * @returns Current cell editor: not null only meanwhile user is editing a cell.
     */
    getOngoingCellEditor()
    {
        return this.cellEditor;
    }

    /**
     * Returns the cells' value for passed table row (number is absolute: including
     * thead rows).
     * <p>
     * returned data has following structure:
     * [col1name: col1Value, col2Name: col2Value, ..., colNName: colNValue]
     *
     * @param {Number} nRow Index (0 based) to retrieve data from. By default the selected row (if any).
     *
     * @returns {Object} JSON
     */
    getRowData( nRow = this.getSelectedRowIndex() )
    {
        let data = {};       // Array of JSON objects to return

        if( ! Number.isInteger(nRow) || nRow < 0 || nRow >= this.getBodyRowCount() )
        {
            console.warn("getRowData: Invalid row index " + nRow);
            return {};
        }

        for( let nCol = 0; nCol < this.tbody.rows[nRow].cells.length; nCol++ )
        {
            let cell = this.tbody.rows[nRow].cells[nCol];

            data[ this._dataColName_( cell ) ] = this._dataValue_( cell );
        }

        return data;
    }

    /**
     * Appends a new row at the end of the table and evaluates
     * this:onRowAppended function if not null.
     *
     * @returns this.
     */
    appendRow()
    {
        if( ! this.isEditable() )
            return this;

        let oData = {};

        for( let n = 0; n < this.colDefinitions.length; n++ )
        {
            let sColName = this.colDefinitions[n].name;
            oData[sColName] = this._getValue4KeyAtColumn_( sColName, "default", "" );
        }

        if( this.enableVirtualScroll )
        {
            this.allData.push( oData );
            this.visibleEnd = Math.min( this.visibleEnd + 1, this.allData.length );
            this._addCells_( this.tbody.insertRow( -1 ), oData );
        }
        else
        {
            this._addCells_( this.tbody.insertRow( -1 ), oData );
        }

        if( this.getBodyRowCount() === 1 )
            this.selectRowAtIndex( 0 );

        if( this.onRowAppended !== null )
            this.onRowAppended ( this, this.tbody.rows.length - 1 );

        this._refreshButtons_();

        return this;
    }

    /**
     * Clones highlighted row (if any) appending at the end of the table and after
     * that, it evaluates onRowCloned function if not null.
     *
     * @returns this.
     */
    cloneRow()
    {
        if( (this.getSelectedRowIndex() < 0)  ||
            (! this.isEditable()) )
        {
            return this;
        }

        let oData = this.getRowData( this.getSelectedRowIndex() );
        let oRow  = this.tbody.insertRow( -1 );

        for( let colName in oData )
        {
            if( this._getValue4KeyAtColumn_( colName, "editor", null ) === null )
            {
                oData[colName] = this._getValue4KeyAtColumn_( colName, "default", "" );
            }
        }

        if( this.enableVirtualScroll )
        {
            this.allData.push( oData );
            this.visibleEnd = Math.min( this.visibleEnd + 1, this.allData.length );
            this._addCells_( oRow, oData );
        }
        else
        {
            this._addCells_( oRow, oData );
        }

        if( this.onRowCloned !== null )
            this.onRowCloned( this, (this.enableVirtualScroll ? this.allData.length - 1 : this.tbody.rows.length - 1) );

        this._refreshButtons_();

        return this;
    }

    /**
     * Moves the selected row one position up in the table.
     * If the row is already the first one, nothing happens.
     *
     * @returns this
     */
    moveRowUp()
    {
        let nRow = this.getSelectedRowIndex();

        if( nRow <= 0 || ! this.isEditable() )
            return this;

        this._swapRows_( nRow, nRow - 1 );

        return this;
    }

    /**
     * Moves the selected row one position down in the table.
     * If the row is already the last one, nothing happens.
     *
     * @returns this
     */
    moveRowDown()
    {
        let nRow = this.getSelectedRowIndex();

        if( nRow < 0 || nRow >= this.getBodyRowCount() - 1 || ! this.isEditable() )
            return this;

        this._swapRows_( nRow, nRow + 1 );

        return this;
    }

    /**
     * Invokes this.onDeleteRow function and if it does not returns 'false', then
     * removes highlighted row (if any) and makes new highlighted the closest one
     * (if any).
     *
     * @param {Number} nRow Index of row (0 based) in tBody.
     * @returns this
     */
    deleteRow( nRow = this.getSelectedRowIndex() )
    {
        if( (! this.isEditable()) ||
            (nRow < 0) )
        {
            return this;
        }

        if( this.onDeleteRow !== null )
        {
            let result = this.onDeleteRow( this, nRow );

            if( p_base.isBoolean( result ) && (! result) )
                return this;
        }

        if( ! this.isEmptyRow( nRow ) )
            this.deletedRows.push( this.getRowData( nRow ) );

        if( this.enableVirtualScroll )
        {
            this.allData.splice( nRow, 1 );
        }

        this.table.deleteRow( nRow + this.getHeadRowCount() );

        if( this.onRowDeleted !== null )
            this.onRowDeleted( this );

        this._afterRowsDeleted_();

        return this;
    }

    /**
     * Removes all rows in 'tbody'. table 'thead' (if any) will remain unchanged.
     *
     * @returns this
     */
    clear()
    {
        $(this.tbody).empty();        // Do not invoke --> this.deleteRow( 0 );

        this.selectedRowIndex = -1;
        this.allData = [];
        this.visibleStart = 0;
        this.visibleEnd = 0;
        this._refreshButtons_();

        return this;
    }

    /**
     * Deletes all empty rows.
     * this:getData() calls here when true is passed as argument.
     *
     * @returns this
     */
    clean()
    {
        for( let nRow = this.tbody.rows.length - 1; nRow >= 0; nRow-- )
        {
            if( this.isEmptyRow( nRow ) )
            {
                this.table.deleteRow( nRow + this.getHeadRowCount() );
            }
        }

        this._afterRowsDeleted_();

        return this;
    }

    /**
     * Invokes this:onSave function (if not null) passing this to it.
     *
     * @returns this
     */
    save()
    {
        if( this.onSave !== null )
        {
            this.setButtonSaveEnabled( false );
            this.onSave( this );
            this.setButtonSaveEnabled( true );
        }

        return this;
    }

    /**
     * If this.onPrint is not null, it will be invoked and if it returns false, then
     * this function does nothing. Otherwise, navigator (web browser) print function
     * (window.print()) will be invoked.
     *
     * @returns this
     */
    print()
    {
        if( this.onPrint !== null )
        {
            let btn = $(p_base.get( this.btnPrintId ));

            btn.prop( 'disabled', true  );
            this.clean();
            this.onPrint( this );
            btn.prop( 'disabled', (! this.isBtnPrintEnabled) );
        }

        return this;
    }

    /**
     * Executes this.onInfo if it is a function.
     *
     * @returns this
     */
    info()
    {
        if( this.onInfo !== null )
            this.onInfo( this );

        return this;
    }

    /**
     * Executes this.onHelp if it is a function.
     *
     * @returns this
     */
    help()
    {
        if( this.onHelp !== null )
            this.onHelp( this );

        return this;
    }

    /**
     * Shows a table with a brief help about TableEditor actions.
     *
     * @param {Any} element An element: a string id or the element itself.
     * @returns The help table id.
     */
    appendHelpTextTo( element )
    {
        let place = p_base.get( element );
        let tblId = p_base.uuid();
        let sHTML =
        '<table id="'+ tblId +'" style="border-collapse:separate; border-spacing:5px;">'+
            '<caption><b><u>Table Editor Actions</u></b></caption>'+
            '<tr>'+
                '<td>Click</td>'+
                '<td>Selects row under mouse pointer</td>'+
            '</tr>'+
            '<tr>'+
                '<td>DblClick</td>'+
                '<td>Edits cell under mouse pointer</i></td>'+
            '</tr>'+
            '<tr>'+
                '<td><i class="ti ti-plus"></i></td>'+
                '<td>Appends a new row at bottom of table</td>'+
            '</tr>'+
            '<tr>'+
                '<td><i class="ti ti-copy"></i></td>'+
                '<td>Clones highlighted row</td>'+
            '</tr>'+
            (this.onSave === null ? '' :
            '<tr>'+
                '<td><i class="ti ti-device-floppy"></i></td>'+
                '<td>Saves current data</td>'+
            '</tr>') +
            '<tr>'+
                '<td><i class="ti ti-trash"></i></td>'+
                '<td>Deletes highlighted row</td>'+
            '</tr>'+
            (this.onPrint === null ? '' :
            '<tr>'+
                '<td><i class="ti ti-printer"></i></td>'+
                '<td>Prints current data</td>'+
            '</tr>')+
            '<tr>'+
                '<td><i class="ti ti-help-circle"></i></td>'+
                '<td>Shows/Hides help</td>'+
            '</tr>'+
        '</table>';

        $(sHTML).appendTo( place );

        return tblId;
    }

    /**
     * Returns not a copy, but current columns definition.
     *
     * @returns Current columns definition.
     */
    getColumns()
    {
        return this.colDefinitions;
    }

    /**
     * Set columns definitions.
     *
     * @param {type} colDefs The columns definitions.
     * @returns this
     */
    setColumns( colDefs )
    {
        if( ! Array.isArray( colDefs ) )
        {
            throw "Invalid column definitions: must be an array";
        }

        for( let n = 0; n < colDefs.length; n++ )
        {
            if( (! colDefs[n].hasOwnProperty( "name" ))  ||
                (! p_base.isString( colDefs[n].name )) ||
                (colDefs[n].name.trim() === '') )
            {
                throw "Invalid column definition at index " + n + ": column name is required and must be a non-empty string";
            }
        }

        this.colDefinitions = colDefs;
        this._setToolbar_();
        this._refreshButtons_();

        return this;
    }

    /**
     * Returns true if table is editable. A table is editable when there is at least one
     * column who has an editor.
     *
     * @returns {Boolean} true if table is editable.
     */
    isEditable()
    {
        if( this.colDefinitions !== null )
        {
            for( let n = 0; n < this.colDefinitions.length; n++ )
            {
                if( this.colDefinitions[n].hasOwnProperty( "editor" ) )
                {
                    let sEditor = this.colDefinitions[n].editor;

                    if( (sEditor !== null) && p_base.isString( sEditor ) )
                    {
                        let sEditorLower = sEditor.trim().toLowerCase();

                        if( sEditorLower.startsWith( "<input" ) || sEditorLower.startsWith( "<select" ) )
                        {
                            return true;     // Is editable because at least one column has an editor
                        }
                    }
                }
            }
        }

        return false;
    }

    /**
     * Returns true if passed row is empty: all its cells are empty or their values
     * are equals to their default value.
     *
     * @param {Object} row of the table or its absolute index.
     * @returns {Boolean} true if passed row is empty.
     */
    isEmptyRow( row = this.getSelectedRowIndex() )
    {
        if( row === null || row === undefined )
        {
            return true;
        }

        if( p_base.isNumeric( row ) )
            row = this.getRow( Number.parseInt(row ) );

        if( ! row )
            return true;

        if( ! row.cells || row.cells.length === 0 )
            return true;

        for( let nCol = 0; nCol < row.cells.length; nCol++ )
        {
            let value = this.getValue( row.cells[nCol] );
            let colName = this._dataColName_( row.cells[nCol] );

            if( (value !== null)                                                 &&
                (value !== this._getValue4KeyAtColumn_( colName, "default", null )) &&
                (! p_base.isEmpty( value )) )
            {
                return false;
            }
        }

        return true;
    }

    /**
     * Returns the internal table row that corresponds to passed argument (index is
     * absolute).
     *
     * @param {Number} row Index of the row in the internal table rows array.
     * @returns {table.rows} Returns the internal table row.
     */
    getRow( row = this.getSelectedRowIndex() )
    {
        if( row === null || row === undefined )
        {
            return null;
        }

        row = Number.parseInt( row );

        if( Number.isNaN( row ) || (row < 0) || (row >= this.tbody.rows.length) )
        {
            return null;
        }

        return this.tbody.rows[ row ];
    }

    /**
     * Returns the cell that corresponds to passed arguments.
     *
     * @param {Number} nRow Index of the row in the internal table rows array.
     * @param {Any} colIndexOrName column index of the column in the internal table
     *               rows array or column name given by the editors passed at
     *               ::setColumns(...)
     * @returns {TableEditor.prototype@pro;table@arr;rows@arr;cells}
     */
    getCell( nRow, colIndexOrName = null )
    {
        if( nRow === null || nRow === undefined )
        {
            return null;
        }

        nRow = Number.parseInt( nRow );

        let tblRow  = this.getRow( nRow );
        let nColumn = -1;

        if( tblRow === null )
        {
            return null;
        }

        if( Number.isInteger( colIndexOrName ) )
        {
            nColumn = colIndexOrName;
        }
        else if( colIndexOrName !== null && colIndexOrName !== undefined )
        {
            for( let n = 0; n < tblRow.cells.length; n++ )
            {
                if( this._dataColName_( tblRow.cells[n] ) === colIndexOrName )
                {
                    nColumn = n;
                    break;
                }
            }
        }

        if( (nColumn < 0) || (nColumn >= tblRow.cells.length) )
        {
            return null;
        }

        return tblRow.cells[nColumn];
    }

    /**
     * Returns current value for designated row and col or cell (head rows does not count).
     * <p>
     * Arguments can be:
     * <ul>
     *    <li>nRow,nCol</li>
     *    <li>nRow,sColName</li>
     *    <li>oCell</li>
     * </ul>
     *
     * @returns Table cell value.
     */
    getValue()
    {
        let oCell;                             // Do not initialize oCell !

        if( arguments.length > 1 )             // It is assumed that args are: nRow and {nCol | sColName}
        {
            oCell = this.getCell( arguments[0], arguments[1] );
        }
        else                                   // It is assumed that there is only one arg: oCell
        {
            oCell = arguments[0];
        }

        return (oCell === null) ? null : this._dataValue_( oCell );
    }

    /**
     * Sets a new value for designated row and col or cell.
     * The row must be absolute: including the header rows.
     * <p>
     * Arguments can be:
     * <ul>
     *    <li>nRow,nCol</li>
     *    <li>nRow,sColName</li>
     *    <li>oCell</li>
     * </ul>
     *
     * @returns this
     */
    setValue()
    {
        let oCell = null;
        let value = null;

        if( arguments.length > 2 )
        {
            oCell = this.getCell( arguments[0], arguments[1] );
            value = arguments[2];
        }
        else
        {
            oCell = arguments[0];
            value = arguments[1];
        }

        if( oCell !== null && oCell !== undefined )
        {
            this._dataValue_( oCell, value );

            var sValue = (this.cellFormatter === null ? value  : this.cellFormatter( this, this._dataColName_( oCell ), value ));
                sValue = (p_base.isString( sValue )   ? sValue : new String( sValue ));

            $(oCell).html( sValue );
        }

        return this;
    }

    /**
     * Returns the highlighted row (if any) as absolute index (including head
     * rows if any). This is the index of the internal table rows array:
     * table.rows[index]. It returns -1 if there is no highlighted row.
     *
     * @returns {Number} The highlighted row (if any) as absolute index or -1 if
     *                   there is no row highlighted.
     */
    getSelectedRowIndex()
    {
        return (p_base.isEmpty( this.selectedRowIndex ) ? -1 : this.selectedRowIndex);
    }

    /**
     * Returns this.tbody.rows[ getSelectedRowIndex() ] or null if there is no
     * highlighted row.
     *
     * @returns {TableEditor.tbody.rows} Highlighted table row or null.
     */
    getSelectedRow()
    {
        return this.getRow( this.getSelectedRowIndex() );
    }

    /**
     * Makes passed row the highlighted one (it is 0 based any head rows do not count).
     * If onRowSelected function is not null, it will invoked passing to it:
     * an instance of this class, the old selected and the new selected row indexes.
     * Both are the index that correspond to the internal 'table.tbodies[0].rows' array.
     * <p>
     * bCountHead is used by cell click event
     *
     * @param {Number} newIndex
     * @param {Boolean} bCountHead true for taking into account the number of rows of table.tHead.
     * @returns this
     */
    selectRowAtIndex( newIndex, bCountHead = false )
    {
        if( p_base.isUndefined( newIndex ) || newIndex === null )
        {
            return this;
        }

        newIndex = Number.parseInt( newIndex );

        if( ! p_base.isNumeric( newIndex ) || Number.isNaN( newIndex ) )
        {
            return this;
        }

        if( bCountHead )
        {
            newIndex -= this.getHeadRowCount();
        }

        if( this.isEmpty()                       ||
            (newIndex < 0)                       ||
            (newIndex >= this.getBodyRowCount()) ||
            (newIndex === this.getSelectedRowIndex() ) )
        {
            return this;
        }

        let oldIndex = this.selectedRowIndex;

        if( oldIndex !== -1 )
        {
            $(this.tbody.rows[oldIndex]).css( 'color'           , this.unselectedRowInk   );
            $(this.tbody.rows[oldIndex]).css( 'background-color', this.unselectedRowPaper );
            $(this.tbody.rows[oldIndex]).removeClass( 'te-selected-row' );
        }

        $(this.tbody.rows[newIndex]).css( 'color'           , this.selectedRowInk   );
        $(this.tbody.rows[newIndex]).css( 'background-color', this.selectedRowPaper );
        $(this.tbody.rows[newIndex]).addClass( 'te-selected-row' );

        this.selectedRowIndex = newIndex;
        this._updateAriaSelection_( newIndex );

        if( this.onRowSelected !== null )
        {
            this.onRowSelected( this, oldIndex, this.selectedRowIndex );
        }

        return this;
    }

    /**
     * Number of rows that conform this table's head (thead section) or 0 if none.
     *
     * @returns {Number} Number of rows that conform this table's head or 0 if none.
     */
    getHeadRowCount()
    {
        return (p_base.isUndefined( this.table.tHead ) ? 0 : this.table.tHead.rows.length);
    }

    /**
     * Number of rows that conform this table's body (tbody section) or 0 if none.
     *
     * @returns {Number} Number of rows that conform this table's body or 0 if none.
     */
    getBodyRowCount()
    {
        return (p_base.isUndefined( this.tbody.rows ) ? 0 : this.tbody.rows.length);
    }

    /**
     * Sets ink and paper colors (in HEX format) for the highlighted (selected) row.
     *
     * @param {type} ink
     * @param {type} paper
     * @returns this
     */
    setSelectedRowColor( ink, paper )
    {
        this.selectedRowInk   = ink;
        this.selectedRowPaper = paper;

        return this;
    }

    /**
     * Sets ink and paper colors (in HEX format) for unselected rows.
     *
     * @param {type} ink
     * @param {type} paper
     * @returns Nothing
     */
    setUnselectedRowColor( ink, paper )
    {
        this.unselectedRowInk   = ink;
        this.unselectedRowPaper = paper;

        return this;
    }

    /**
     * Enables or disables all buttons in this TableEditor tool bar.
     *
     * @param {boolean} bEnabled New status.
     */
    setEnabled( bEnabled )
    {
        this.isBtnAppendEnabled = bEnabled;
        this.isBtnCloneEnabled  = bEnabled;
        this.isBtnSaveEnabled   = bEnabled;
        this.isBtnDeleteEnabled = bEnabled;
        this.isBtnInfoEnabled   = bEnabled;
        this.isBtnPrintEnabled  = bEnabled;
        this.isBtnHelpEnabled   = bEnabled;
        this._refreshButtons_();

        // Buttons added by user
        if( this.toolbar !== null )
        {
            let toolbarElement = p_base.get( this.toolbar );
            if( toolbarElement !== null )
            {
                let aoButton = toolbarElement.getElementsByTagName( "button" );

                for( let n = 0; n < aoButton.length; n++ )
                {
                    aoButton[n].disabled = ! bEnabled;
                }
            }
        }

        this._setClickEvent_( bEnabled );

        return this;
    }

    /**
     * Set enable status for Append and Clone buttons.
     * @param {type} enabled true or false
     * @returns this
     */
    setButtonAppendEnabled( enabled )
    {
        this.isBtnAppendEnabled = enabled;
        this._refreshButtons_();

        return this;
    }

    /**
     * Set enable status for Clone button.
     * @param {type} bEnabled true or false
     * @returns this
     */
    setButtonCloneEnabled( bEnabled )
    {
        this.isBtnCloneEnabled = bEnabled;
        this._refreshButtons_();

        return this;
    }

    /**
     * Set enable status for Save button.
     * @param {type} bEnabled true or false
     * @returns this
     */
    setButtonSaveEnabled( bEnabled )
    {
        this.isBtnSaveEnabled = bEnabled;
        this._refreshButtons_();

        return this;
    }

    /**
     * Set enable status for Delete button.
     * @param {type} bEnabled true or false
     * @returns this
     */
    setButtonDeleteEnabled( bEnabled )
    {
        this.isBtnDeleteEnabled = bEnabled;
        this._refreshButtons_();

        return this;
    }

    /**
     * Set enable status for Print button.
     * @param {type} bEnabled true or false
     * @returns this
     */
    setButtonPrintEnabled( bEnabled )
    {
        this.isBtnPrintEnabled = bEnabled;
        this._refreshButtons_();

        return this;
    }

    /**
     * Set enable status for Info button.
     * @param {type} bEnabled true or false
     * @returns this
     */
    setButtonInfoEnabled( bEnabled )
    {
        this.isBtnInfoEnabled = bEnabled;
        this._refreshButtons_();

        return this;
    }

    /**
     * Set enable status for Help button.
     * @param {type} bEnabled true or false
     * @returns this
     */
    setButtonHelpEnabled( bEnabled )
    {
        this.isBtnHelpEnabled = bEnabled;
        this._refreshButtons_();

        return this;
    }

    /**
     * Creates and add a button after the last button inside the buttonbar (if any).
     *
     * @param  {String}   sCaption  A string with the caption (can be null).
     * @param  {String}   sTitle    A string for the 'title' property (can be null).
     * @param  {String}   sIconName A awesome font icon name (can be null).
     * @param  {Function} fnAction  The function to be invoked when button is clicked. 'this' is passed as argument.
     * @param  {Number}   nIndex    Where to insert the button (zero based), -1 will append at end.
     * @return {Button}   The created button instance.
     */
    appendButton( sCaption, sTitle, sIconName, fnAction, nIndex )
    {
        if( this.toolbar === null )
        {
            throw "ToolBar was not defined at constructor";
        }

        if( ! p_base.isFunction( fnAction ) )
        {
            throw "Passed fnAction is not a function";
        }

        sCaption = p_base.isEmpty( sCaption )   ? "No name" : sCaption;
        sTitle   = p_base.isEmpty( sTitle   )   ? ""        : sTitle;
        nIndex   = p_base.isUndefined( nIndex ) ? -1        : nIndex;

        let self    = this;
        let id      = p_base.uuid();
        let toolbar = p_base.get( this.toolbar ).children[0];
        let sButton = '<p class="control">'+
                          '<button class="button" id="'+ id +'" title="'+ sTitle +'">'+
                               (sIconName ? ('<span class="icon is-small"><i class="fas '+ sIconName +'"></i></span>') : '') +
                               (sCaption  ? ('<span>'+ sCaption +'</span>') : '') +
                          '</button>'+
		              '</p>';

        $(sButton).appendTo( toolbar );                                 // TODO: utilizar el nIndex
        $(p_base.get( id )).click( function() { fnAction( self ); } );

        return p_base.get( id );
    }

    /**
     * Manages cell user edition.
     *
     * @param {type} cell The cell to be edited.
     * @returns Nothing
     */
    editCell( cell )
    {
        if( ! this.isEditable() )
        {
            return this;
        }

        if( cell === null )
        {
            throw "Cell to edit can not be null";
        }

        this._startEdition_( cell );

        return this;
    }

    _startEdition_( cell )
    {
        let sColName = this._dataColName_( cell );
        let sCellVal = this.getValue( cell );
        let val2edit = sCellVal;
        let editor   = this._getValue4KeyAtColumn_( sColName, "editor", null );

        if( this.cellPreValidator !== null )                                  // If there is a PreValidator for this column we have to evaluate it
        {
            val2edit = this.cellPreValidator( this, this.getSelectedRowIndex(), sColName, sCellVal );
        }

        editor = this._getValue4KeyAtColumn_( sColName, "editor", null );     // This is needed because PreValidator could change the editor

        // If there is no editor for this column, edit has to be aborted.
        if( editor === null )
        {
            return;
        }

        $(cell).html( editor );                             // Replace the content of the cell with its editor

        if( $(cell).find('input').length > 0 )              // Now check if it's an 'input'
        {
            this.cellEditor = $(cell).find('input')[0];     // Change a HTML text string for the HTML element
        }
        else if( $(cell).find('select').length > 0 )        // If not an 'input', check if it's a 'select'
        {
            this.cellEditor = $(cell).find('select')[0];    // Change a HTML text string for the HTML element
        }
        else                                                // If neither input nor select, cannot work with the editor
        {
            return this;                                    // Cannot work with it: the cell remains as it was
        }

        this.bucket = val2edit;

        $(cell).addClass( 'te-editing' );

        let nMinWidth = this._getValue4KeyAtColumn_( sColName, "minwidth", null );
        let nMaxWidth = this._getValue4KeyAtColumn_( sColName, "maxwidth", null );

        if( nMinWidth !== null ) { $(this.cellEditor).css( "min-width", nMinWidth +"ch" ); }
        if( nMaxWidth !== null ) { $(this.cellEditor).css( "max-width", nMaxWidth +"ch" ); }

        this._setClickEvent_( false );
        this._setEditorValue_( this.cellEditor, val2edit );

        $(this.cellEditor).focus().trigger( 'focusin' );

        let self = this;

        $(this.cellEditor).on( 'blur.te_tableeditor', (evt) => self._endEdition_( cell, evt ) );

        if( $(this.cellEditor).is('select') || p_base.isOfType( this.cellEditor, 'date' ) || p_base.isOfType( this.cellEditor, 'color' ) )
        {
            $(this.cellEditor).on( 'change.te_tableeditor', function(evt){ $(evt.target).blur(); } );
        }

        $(this.cellEditor).on( 'keyup',
            function( event )
            {
                event.preventDefault();
                event.stopPropagation();

                if( (event.key === 'Enter') || (event.key === 'Escape') )
                {
                    if (event.key === 'Escape')
                    {
                        self._setEditorValue_( self.cellEditor, sCellVal );
                    }

                    $(event.target).blur();
                }
            } );
    }

    _endEdition_( cell, evt )
    {
        let sColName = this._dataColName_( cell );
        let $editor  = $(evt.target);
        let newVal   = p_base.getFieldValue( $editor );   // If the option does not have "value=", then returns {text}
        let bChanged = (this.bucket != newVal);           // It is OK !=
        this.bucket  = undefined;                         // Reset the value

        if( $editor.is( "select" ) )
        {
            let text = $editor.find( "option:selected" ).text();

            if( newVal !== text )           // newVal contains the {value}
            {
                $(cell).text( text );
            }
        }

        newVal = (this.cellPostValidator === null ? newVal : this.cellPostValidator( this, this.getSelectedRowIndex(), sColName, newVal, bChanged ));    // If there is a PostValidator for this column we have to evaluate it

        if( p_base.isUndefined( newVal ) ||
            ($editor.is("[type=number]") && Number.isNaN( newVal )) )
        {
            newVal = this._getValue4KeyAtColumn_( sColName, "default", "" );
        }

        this._dataValue_( cell, newVal );
        $(cell).html( (this.cellFormatter === null ? newVal : this.cellFormatter( this, sColName, newVal )) );
        $(cell).removeClass( 'te-editing' );
        $(cell).css( 'background-color', '' );
        $(evt.target).off( 'blur.te_tableeditor change.te_tableeditor' );
        this._setClickEvent_( true );
        this.cellEditor = null;
    }

    getEditorSelectTextOn( sColName, sEditorSelectValue )
    {
        let editor = this._getValue4KeyAtColumn_( sColName, "editor", null );

        if( editor === null )
        {
            throw "No editor is associated with column '"+ sColName +"'";
        }

        let sText2Ret = null;

        sEditorSelectValue = new String( sEditorSelectValue );     // Just in case it is not an string (option's value are strings)

        $(editor).children( "option" )
                .each( function()
                        {
                            if( $(this).val() === sEditorSelectValue )
                            {
                                sText2Ret = $(this).text();
                                return false;
                            }
                        } );

        if( sText2Ret === null )
        {
            throw "Value '"+ sEditorSelectValue +"' not found for <select></select> on column '"+ sColName +"'";
        }

        return sText2Ret;
    }

    getEditorSelectValueOn( sColName, sEditorSelectText )
    {
        let editor = this._getValue4KeyAtColumn_( sColName, "editor", null );

        if( editor === null )
        {
            throw "No editor is associated with column '"+ sColName +"'";
        }

        let sVal2Ret = null;

        $(editor).children( "option" )
                 .each( function()
                        {
                            if( $(this).text() === sEditorSelectText )
                            {
                                sVal2Ret = $(this).val();
                                return false;
                            }
                        } );

        if( sVal2Ret === null )
        {
            throw "Text '"+ sEditorSelectText +"' not found for <select></select> on column '"+ sColName +"'";
        }

        return sVal2Ret;
    }

    //-----------------------------------------------------------------------------//
    // FROM NOW AND BELLOW ALL FUNCTIONS AR FOR INTERNAL USE ONLY: THEY SHOULD NOT BE
    // INVOKED FROM OUTSIDE OF THIS CLASS.

    /**
     * Swaps two adjacent rows in the table body and updates the selection.
     *
     * @private
     * @param {number} nFrom Index of the row to move.
     * @param {number} nTo   Index of the destination position.
     */
    _swapRows_( nFrom, nTo )
    {
        let rowFrom = this.tbody.rows[nFrom];
        let rowTo   = this.tbody.rows[nTo];

        if( ! rowFrom || ! rowTo )
            return;

        // DOM swap: insert the moved row before or after the target
        if( nFrom < nTo )
            this.tbody.insertBefore( rowTo, rowFrom );
        else
            this.tbody.insertBefore( rowFrom, rowTo );

        // Virtual scrolling: swap in allData too
        if( this.enableVirtualScroll && nFrom < this.allData.length && nTo < this.allData.length )
        {
            let tmp = this.allData[nFrom];
            this.allData[nFrom] = this.allData[nTo];
            this.allData[nTo]   = tmp;
        }

        // Update selection to follow the moved row
        this.selectedRowIndex = -1;
        this.selectRowAtIndex( nTo );

        if( this.onRowMoved !== null )
            this.onRowMoved( this, nFrom, nTo );

        this._refreshButtons_();
    }

    /**
     * Updates the selected (highlighted) row and the internal selected row index.
     *
     * @returns Nothing
     */
    _afterRowsDeleted_()
    {
        if( this.getBodyRowCount() === 0 )
        {
            this.selectedRowIndex = -1;
        }
        else
        {
            if( this.selectedRowIndex >= this.tbody.rows.length )
            {
                this.selectedRowIndex = this.tbody.rows.length - 1;
            }

            let indexToSelect = this.selectedRowIndex;
            this.selectedRowIndex = -1;
            this.selectRowAtIndex( indexToSelect, false );
        }

        this._refreshButtons_();
    }

    /**
     * Just this.
     *
     * @returns Nothing
     */
    _refreshButtons_()
    {
        if( this.toolbar !== null )
        {
            let editable = this.isEditable();
            let noRows   = this.getBodyRowCount() === 0;

            this._setButtonDisabled_( this.btnAppendId, (! this.isBtnAppendEnabled) || (! editable) );
            this._setButtonDisabled_( this.btnCloneId,  (! this.isBtnCloneEnabled ) || (! editable) || noRows );
            this._setButtonDisabled_( this.btnSaveId,   (! this.isBtnSaveEnabled  ) || (! editable) || (this.onSave === null) );
            this._setButtonDisabled_( this.btnDeleteId, (! this.isBtnDeleteEnabled) || (! editable) || noRows );
            this._setButtonDisabled_( this.btnHelpId,   (! this.isBtnHelpEnabled  ) || (this.onHelp  === null) );
            this._setButtonDisabled_( this.btnInfoId,   (! this.isBtnInfoEnabled  ) || (this.onInfo  === null) );
            this._setButtonDisabled_( this.btnPrintId,  (! this.isBtnPrintEnabled ) || (this.onPrint === null) || noRows );
        }

        return this;
    }

    /**
     * Sets or removes the disabled attribute on a toolbar button element.
     * Uses .attr() instead of .prop() because toolbar buttons are <i> elements,
     * not form elements, so .prop('disabled') has no effect on them.
     *
     * @private
     * @param {string} btnId - The button element ID.
     * @param {boolean} isDisabled - Whether the button should be disabled.
     */
    _setButtonDisabled_( btnId, isDisabled )
    {
        let $btn = $(p_base.get( btnId ));

        if( isDisabled )
        {
            $btn.attr( 'disabled', 'disabled' );
        }
        else
        {
            $btn.removeAttr( 'disabled' );
        }
    }

    /**
     * Copy the value of the cell to the editor.
     *
     * @param {type} editor
     * @param {type} cellValue
     * @returns Nothing
     */
    _setEditorValue_( editor, cellValue )
    {
        if( $(editor).is( 'input' ) )
        {
            if( p_base.isOfType( editor, 'color' ) && ! /^#[0-9a-fA-F]{6}$/.test( cellValue ) )
            {
                cellValue = '#000000';
            }

            $(editor).val( cellValue );
        }
        else
        {
            $(editor).find( 'option' )
                     .filter( function() { return $(this).text() === cellValue; } )
                     .prop( 'selected', true );
        }

        return this;
    }

    /**
     * Adds one cell per defined colum to passed table row.
     *
     * @param {type} oRow
     * @param {type} oData
     * @returns {TableEditor} this
     */
    _addCells_( oRow, oData )
    {
        oRow.setAttribute( 'role', 'row' );
        oRow.setAttribute( 'aria-rowindex', Array.from( this.tbody.rows ).indexOf( oRow ) + 1 );

        for( let n = 0; n < this.colDefinitions.length; n++ )
        {
            let cell  = oRow.insertCell( -1 );
            let name  = this.colDefinitions[n].name;
            let value = (p_base.isDefined( oData[name] ) ? oData[name] : this._getValue4KeyAtColumn_( name, "default", "" ));
            let text  = (this.cellFormatter === null ? value : this.cellFormatter( this, name, value ));

            cell.innerHTML = text;

            let colEditor = this.colDefinitions[n].editor;

            if( ! colEditor )
            {
                $(cell).css( "display", "none" );
            }
            else if( p_base.isString( colEditor ) )
            {
                let edLower = colEditor.trim().toLowerCase();

                if( edLower.startsWith( "<input" ) || edLower.startsWith( "<select" ) )
                {
                    $(cell).addClass( 'te-editable' );
                }
            }

            this._dataColName_( cell, name  );
            this._dataValue_  ( cell, value );
        }

        return this;
    }

    /**
     * Returns the default value for passed column name.
     *
     * @param {String} sColName A column name.
     * @param {String} sKey The name of the key which value has to be returned.
     * @param {Any} def The value to return if colname or the key does not exists.
     * @returns {Any} The default value if exist or null if does not.
     */
    _getValue4KeyAtColumn_( sColName, sKey, def )
    {
        for( let n = 0; n < this.colDefinitions.length; n++ )
        {
            if( this.colDefinitions[n].name === sColName )
            {
                return this.colDefinitions[n][sKey];
            }
        }

        return (p_base.isUndefined( def ) ? null : def);
    }

    /**
     * Shows toolbar (if ::isEditable == true), or hides it otherwise.
     *
     * @returns Shows toolbar (if ::isEditable == true), or hides it otherwise.
     */
    _setToolbar_()
    {
        if( this.toolbar === null )
        {
            return this;
        }

        $(this.toolbar).empty();

        let toolbar = '';

        if( this.isEditable() )         // CARE: can not use use <button> because when a button has focus or is the default button and user press [Enter] its action is executed
        {
            // NEXT: definir la class mini-btn de algun modo en este fichero
            toolbar += '<i class="mini-btn ti ti-plus"       id="'+ this.btnAppendId   +'" title="Appends a new row"></i>'+
                       '<i class="mini-btn ti ti-copy"       id="'+ this.btnCloneId    +'" title="Clones highlighted row"></i>'+
                       '<i class="mini-btn ti ti-arrow-up"   id="'+ this.btnMoveUpId   +'" title="Moves highlighted row up"></i>'+
                       '<i class="mini-btn ti ti-arrow-down" id="'+ this.btnMoveDownId +'" title="Moves highlighted row down"></i>'+
                       '<i class="mini-btn ti ti-device-floppy"  id="'+ this.btnSaveId +'" title="Save current data"></i>'+
                       '<i class="mini-btn ti ti-trash"      id="'+ this.btnDeleteId   +'" title="Deletes highlighted row"></i>';
        }

        toolbar += '<i class="mini-btn ti ti-info-circle"       id="'+ this.btnInfoId  +'" title="Column information"></i>'+
                   '<i class="mini-btn ti ti-printer"           id="'+ this.btnPrintId +'" title="Prints current data"></i>'+
                   '<i class="mini-btn ti ti-help-circle" id="'+ this.btnHelpId  +'" title="Shows help"></i>';

        $(this.toolbar).append( $('<div>'+ toolbar +'</div>') );

        // It is easier to hide than to check in all the code if it exists prior to use it
        if( this.onSave  === null )  $('#'+this.btnSaveId ).hide();
        if( this.onInfo  === null )  $('#'+this.btnInfoId ).hide();
        if( this.onPrint === null )  $('#'+this.btnPrintId).hide();
        if( this.onHelp  === null )  $('#'+this.btnHelpId ).hide();

        let self = this;

        if( this.isEditable() )
        {
            $(p_base.get( this.btnAppendId   )).on( 'click', function() { self.appendRow();  } );
            $(p_base.get( this.btnCloneId    )).on( 'click', function() { self.cloneRow();   } );
            $(p_base.get( this.btnMoveUpId   )).on( 'click', function() { self.moveRowUp();  } );
            $(p_base.get( this.btnMoveDownId )).on( 'click', function() { self.moveRowDown();} );
            $(p_base.get( this.btnSaveId     )).on( 'click', function() { self.save();       } );
            $(p_base.get( this.btnDeleteId   )).on( 'click', function() { self.deleteRow();  } );
        }

        $(p_base.get( this.btnInfoId  )).on( 'click', function() { self.info();  } );
        $(p_base.get( this.btnPrintId )).on( 'click', function() { self.print(); } );
        $(p_base.get( this.btnHelpId  )).on( 'click', function() { self.help();  } );

        return this;
    }

    _dataColName_( oCell, name )
    {
        if( p_base.isDefined( name ) )
        {
            $(oCell).data( "teColname", name );
        }

        return $(oCell).data( "teColname" );
    }

    /**
     * If only one arg is passed, returns the current value. If 2 args are passed,
     * updates the current value but returns the previous value before change.
     *
     * @param {HTMLTableCellElement} oCell The cell element.
     * @param {*} value Optional new value to set.
     * @returns {*} The current (or previous, if updated) cell value.
     */
    _dataValue_( oCell, value )
    {
        let val = $(oCell).data( "te_colvalue" );             // Care: it is mandatory to use lower-case and to omit "data-" prefix

        if( p_base.isDefined( value ) )
        {
            $(oCell).data( "te_colvalue", value );
        }

        return val;
    }

    /**
     * [de]activate click on all cells (current and future ones): one click to select the cell, double click to edit it.
     *
     * @param {Boolean} bOn
     */
    _setClickEvent_( bOn )
    {
        if( bOn )
        {
            let self = this;

            $(this.table).on( 'click', 'tbody tr', function( event )
            {
                const cell = $( event.target ).closest( 'td' )[0];
                const row  = $( event.target ).closest( 'tr' );

                if( row.length > 0 )
                {
                    const clickedIndex = row[0].rowIndex - self.getHeadRowCount();

                    if( clickedIndex === self.getSelectedRowIndex() && cell && $(cell).hasClass( 'te-editable' ) )
                    {
                        self.editCell( cell );
                    }
                    else
                    {
                        self.selectRowAtIndex( row[0].rowIndex, true );
                    }
                }
            } );

            $(this.table).on( 'dblclick', 'tbody tr td', function( event )
            {
                self.editCell( event.target );
            } );
        }
        else
        {
            $(this.table).off( 'click'   , 'tbody tr' );
            $(this.table).off( 'dblclick', 'tbody tr td' );
        }

        return this;
    }

    /**
     * Sets up touch event support for mobile devices.
     * @private
     * @returns {TableEditor} this
     */
    _setupTouchSupport_()
    {
        if( ! this.enableTouchSupport )
        {
            return this;
        }

        const self = this;
        let touchStartRow = -1;
        let touchStartTime = 0;
        let touchStartX = 0;
        let touchStartY = 0;

        const touchStartHandler = function( event )
        {
            const touch = event.touches[0];
            const element = document.elementFromPoint( touch.clientX, touch.clientY );
            const row = element ? element.closest('tr') : null;

            if( row )
            {
                touchStartRow = Array.from( self.tbody.rows ).indexOf( row );
                touchStartTime = Date.now();
                touchStartX = touch.clientX;
                touchStartY = touch.clientY;
            }
        };

        const touchEndHandler = function( event )
        {
            const touch = event.changedTouches[0];
            const element = document.elementFromPoint( touch.clientX, touch.clientY );
            const row = element ? element.closest('tr') : null;

            if( row && touchStartRow >= 0 )
            {
                const touchEndRow = Array.from( self.tbody.rows ).indexOf( row );
                const touchDuration = Date.now() - touchStartTime;

                const deltaX = Math.abs( touch.clientX - touchStartX );
                const deltaY = Math.abs( touch.clientY - touchStartY );

                if( touchStartRow === touchEndRow && touchDuration < 300 && deltaX < 10 && deltaY < 10 )
                {
                    event.preventDefault();
                    self.selectRowAtIndex( touchEndRow );
                }
            }

            touchStartRow = -1;
        };

        this.tbody.addEventListener( 'touchstart', touchStartHandler, { passive: true } );
        this.tbody.addEventListener( 'touchend', touchEndHandler, { passive: false } );

        this._eventHandlers.push( { element: this.tbody, event: 'touchstart', handler: touchStartHandler } );
        this._eventHandlers.push( { element: this.tbody, event: 'touchend', handler: touchEndHandler } );

        return this;
    }

    /**
     * Sets up virtual scrolling for large datasets.
     * @private
     * @returns {TableEditor} this
     */
    _setupVirtualScroll_()
    {
        if( ! this.enableVirtualScroll )
        {
            return this;
        }

        this.visibleRows = Math.ceil( this.table.clientHeight / this.rowHeight );
        this.visibleStart = 0;
        this.visibleEnd = Math.min( this.visibleRows, this.allData.length );

        const self = this;

        const scrollHandler = function()
        {
            const scrollTop = self.tbody.scrollTop;
            const newStartIndex = Math.floor( scrollTop / self.rowHeight );

            if( newStartIndex !== self.visibleStart )
            {
                self.visibleStart = newStartIndex;
                self.visibleEnd = Math.min( newStartIndex + self.visibleRows, self.allData.length );
                self._renderVisibleRows_();
            }
        };

        $(this.tbody).on( 'scroll', scrollHandler );
        this._eventHandlers.push( { element: this.tbody, event: 'scroll', handler: scrollHandler } );

        return this;
    }

    /**
     * Renders visible rows for virtual scrolling.
     * @private
     */
    _renderVisibleRows_()
    {
        $( this.tbody ).empty();

        for( let i = this.visibleStart; i < this.visibleEnd; i++ )
        {
            if( i >= 0 && i < this.allData.length )
            {
                const newRow = this.tbody.insertRow( -1 );
                this._addCells_( newRow, this.allData[i] );
            }
        }

        this._updateAriaSelection_( this.selectedRowIndex );
    }

    /**
     * Sets up resize observer with debouncing.
     * @private
     * @returns {TableEditor} this
     */
    _setupResizeObserver_()
    {
        const self = this;

        const resizeHandler = function()
        {
            if( self._debounceTimer !== null )
            {
                clearTimeout( self._debounceTimer );
            }

            self._debounceTimer = setTimeout(() =>
            {
                self._recalculateLayout_();
                self._debounceTimer = null;
            }, self.debounceDelay );
        };

        if( typeof ResizeObserver !== 'undefined' )
        {
            this.resizeObserver = new ResizeObserver( resizeHandler );
            this.resizeObserver.observe( this.table );
        }
        else
        {
            window.addEventListener( 'resize', resizeHandler );
            this._eventHandlers.push( { element: window, event: 'resize', handler: resizeHandler } );
        }

        return this;
    }

    /**
     * Recalculates table layout after resize.
     * @private
     */
    _recalculateLayout_()
    {
        if( this.enableVirtualScroll )
        {
            this.visibleRows = Math.ceil( this.table.clientHeight / this.rowHeight );
            this.visibleEnd = Math.min( this.visibleStart + this.visibleRows, this.allData.length );
            this._renderVisibleRows_();
        }
    }

    /**
     * Batch updates multiple rows for better performance.
     * @private
     * @param {Array} updates - Array of update objects: { index: number, data: object }
     */
    _batchRowUpdate_( updates )
    {
        if( ! updates || updates.length === 0 )
        {
            return this;
        }

        updates.forEach( update =>
        {
            if( update.index >= 0 && update.index < this.getBodyRowCount() )
            {
                const row = this.tbody.rows[update.index];
                if( ! row )
                {
                    return;
                }

                const newRow = row.cloneNode( true );

                Object.keys( update.data ).forEach( key =>
                {
                    const cellIndex = this.colDefinitions.findIndex( col => col.name === key );
                    if( cellIndex >= 0 )
                    {
                        const value = update.data[key];
                        const text = (this.cellFormatter === null ? value : this.cellFormatter( this, key, value ));
                        const cell = newRow.cells[cellIndex];

                        cell.innerHTML = text;
                        this._dataValue_( cell, value );
                    }
                });

                this.tbody.replaceChild( newRow, row );
            }
        });

        return this;
    }
}