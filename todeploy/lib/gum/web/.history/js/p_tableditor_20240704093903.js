//
// OJO: tengo un tableeditor.js en bookup q sólo lo uso para bookup, este ha
// sido mejorado y utiliza Bulma en lugar de BootStrap.
//
// -----------------------------------------------------------------------------
// A generic Table Editor.
// This class depends only on JQuery.
//
// All parameters pased to this class's constructor are encapsulated in a JSON
// object: { key1 : value1, key2 : value2, ... }
//
// The data to be shown will be passed in an object as follows (see
// this:setData(...) for more information)
//      { colName1 : colvalue1, colName2 : colvalue2, ... }
//
// Every cell has an internal value (any JS valid data type can be used) that is
// used for all operations and another visible value (normally the textual
// representation of the internal value).
//
// Note: for those <select></select> editors, if selected <option> has the "value='...'", this value will be asigned to the cell's
//       internal value and the option's text will be asigned to the cell's text. If option does not have the "value", then option's
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
//                    * defVal : the defauls value for clone, append and when user editing leaves an invalid value
//                    * width  : editor's min and max widths in numbers of chars (unit 'ch': width of "0"): 'minwidth' & 'maxwidth'
//                    * editor : the html needed to edit the cell value
//                 Following are valid values for the editor property:
//                    * null  -> column will be invisible, its values will not be cloned and it will be not editable.
//                    * false -> column will be invisible, its values will be cloned but it will not be editable.
//                    * true  -> column will be visible, its values will be cloned but it will not be editable.
//                    * HTML  -> to be inserted in the cell when it becomes editable: only "input" and "select" are allowed.
//                 Columns that appear in the setData( data ), but not having a column definition, will be ignored. And columns
//                 that have a definition but does not appear in the setData( data ), will exists only here (calculated column).
//                 If this parameter is not passed or it's null all columns will be invisible. If none of the columns is editable,
//                 (does not have an editor) the table will be not editable and only the toolbar's print button will be shown.
//
// cellFormatter : A function that will receive an instance of this class as first argument, the name of the column as second
//                 argument and current value as the third argument. Function (if exists) must return the value to be shown as
//                 cell's content (it can be pure text or HTML).
//
// onCellPreEdit : A function that will receive an instance of this class as first argument, the row index as second argument, the
//                 the name of the column as third argument and the current value as the forth argument. Function (if exists) must
//                 return a valid JS value. If the value is not null, the cell will become editable, its internal value will be
//                 updated and its contents will be the value (previously formmated if the formated funciton exists). If null, the
//                 cell will not be editable. Function is invoked before cell editing starts.
//
// onCellPostEdit: A function that will receive an instance of this class as first argument, the row index as second argument, the
//                 the name of the column as third argument, the current value as the fourth argument and a boolean being true if
//                 the value changed (is different) or did not changed during edition. Function (if exists) must return a value;
//                 it will be used as cell's internal value. Function is invoked after cell editing ends.
// ---------------------------------------------------------------------------------------------------------------------------------

/* global basico, p_base */

"use strict";

class TableEditor
{
    constructor( parameters )
    {
        if( ! parameters.hasOwnProperty( 'table' ) )
            throw "Mandatory paramenter 'table' not found";

        if( p_base.get( parameters.table ) === parameters.table )
            throw "Table '"+ parameters.table +"' not found";

        if( parameters.hasOwnProperty( 'toolbar' ) &&  p_base.get( parameters.toolbar ) === parameters.toolbar )
            throw "ToolBar '"+ parameters.toolbar +"' not found";

        let fnSave   = (parameters.hasOwnProperty('onSave'        ) ? parameters.onSave         : null);
        let fnPrint  = (parameters.hasOwnProperty('onPrint'       ) ? parameters.onPrint        : null);
        let fnHelp   = (parameters.hasOwnProperty('onHelp'        ) ? parameters.onHelp         : null);
        let fnSelec  = (parameters.hasOwnProperty('onRowSelected' ) ? parameters.onRowSelected  : null);
        let fnAppend = (parameters.hasOwnProperty('onRowAppended' ) ? parameters.onRowAppended  : null);
        let fnClone  = (parameters.hasOwnProperty('onRowCloned'   ) ? parameters.onRowCloned    : null);
        let fnDelRow = (parameters.hasOwnProperty('onDeleteRow'   ) ? parameters.onDeleteRow    : null);
        let fnRowDel = (parameters.hasOwnProperty('onRowDeleted'  ) ? parameters.onRowDeleted   : null);
        let fnCelFmt = (parameters.hasOwnProperty('cellFormatter' ) ? parameters.cellFormatter  : null);
        let fnCelPre = (parameters.hasOwnProperty('onCellPreEdit' ) ? parameters.onCellPreEdit  : null);
        let fnCelPos = (parameters.hasOwnProperty('onCellPostEdit') ? parameters.onCellPostEdit : null);

        this.table              = p_base.get( parameters.table );
        this.toolbar            = (parameters.hasOwnProperty( 'toolbar' ) ? p_base.get( parameters.toolbar ) : null);
        this.tbody              = (! this.table.tBodies || this.table.tBodies.length === 0) ? this.table.createTBody() : this.table.tBodies[0];
        this.onSave             = (p_base.isFunction( fnSave   ) ? fnSave   : null);
        this.onPrint            = (p_base.isFunction( fnPrint  ) ? fnPrint  : null);
        this.onHelp             = (p_base.isFunction( fnHelp   ) ? fnHelp   : null);
        this.onRowSelected      = (p_base.isFunction( fnSelec  ) ? fnSelec  : null);
        this.onRowAppended      = (p_base.isFunction( fnAppend ) ? fnAppend : null);
        this.onRowCloned        = (p_base.isFunction( fnClone  ) ? fnClone  : null);
        this.onDeleteRow        = (p_base.isFunction( fnDelRow ) ? fnDelRow : null);
        this.onRowDeleted       = (p_base.isFunction( fnRowDel ) ? fnRowDel : null);
        this.cellFormatter      = (p_base.isFunction( fnCelFmt ) ? fnCelFmt : null);
        this.cellPreValidator   = (p_base.isFunction( fnCelPre ) ? fnCelPre : null);
        this.cellPostValidator  = (p_base.isFunction( fnCelPos ) ? fnCelPos : null);
        this.colDefinitions     = (parameters.hasOwnProperty( 'columns' ) ? parameters.columns : null);
        this.deletedRows        = [];
        this.selectedRowIndex   = -1;          // Absolute: this.tbody.rows[this_index]
        this.cellEditor         = null;        // Current cell editor (not null only meanwhile editing cell content)
        this.unselectedRowInk   = "#000000";
        this.unselectedRowPaper = "#ffffff";
        this.selectedRowInk     = "#000000";
        this.selectedRowPaper   = "#cee3f6";
        this.isBtnAppendEnabled = true;
        this.isBtnCloneEnabled  = true;
        this.isBtnSaveEnabled   = true;
        this.isBtnDeleteEnabled = true;
        this.isBtnPrintEnabled  = (this.onPrint !== null);
        this.isBtnHelpEnabled   = (this.onHelp  !== null);
        this.btnAppendId        = p_base.uuid();           //----------------------------------------------------------------
        this.btnCloneId         = p_base.uuid();           // All buttons (in the button bar) for each instance of this class
        this.btnSaveId          = p_base.uuid();           // must have different element id (<button id="...">)
        this.btnDeleteId        = p_base.uuid();           // In order for the functions to be able to refer them properly.
        this.btnPrintId         = p_base.uuid();           // These variables store these ids.
        this.btnHelpId          = p_base.uuid();           //----------------------------------------------------------------

        this.bucket = null;   // Multipurpose var. Used right now only by the cellEditor to save the value of the cell before start editing

        this._setClickEvent_( true )
            ._setToolbar_()
            ._refreshButtons_();
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
     * If passed data is not a JSON array or it is empty, table will be empty.
     *
     * @param {Array} aData A JSON array containing all table rows and columns.
     * @returns this.
     */
    setData( aData )
    {
        this.clear();

        if( (! p_base.isArray( aData )) ||
            (aData.length === 0)        ||
            (this.colDefinitions === null) )
        {
            return this;
        }

        for( let row = 0; row < aData.length; row++ )
        {
            this._addCells_( this.tbody.insertRow( -1 ), aData[row] );      // -1 == insert at end of tbody (append)
        }

        this.selectRowAtIndex( 0 );
        this._refreshButtons_();

        return this;
    }

    /**
     * Returns non empty rows that were deleted (either by the user or
     * progamatically) or an empty array if no row was deleted.
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

        if( (nRow < 0) || (nRow >= this.getBodyRowCount() ) )
        {
            throw "Index out of bounds: "+ nRow;
        }

        for( let nCol = 0; nCol < this.tbody.rows[nRow].cells.length; nCol++ )
        {
            let cell = this.tbody.rows[nRow].cells[nCol];

            data[ this._dataColName_( cell ) ] = this._dataValue_( cell );
        }

        return data;
    }

    /**
     * Appends a new empty row at the end of the table and evaluates
     * this:onRowAppended function if not null.
     *
     * @returns this.
     */
    appendRow()
    {
        if( ! this.isEditable() )
            return this;

        // Creating a def value for every col -------------------------
        let oData = {};

        for( let n = 0; n < this.colDefinitions.length; n++ )
        {
            let sColName = this.colDefinitions[n].name;

            oData[sColName] = this._getValue4KeyAtColumn_( sColName, "default", "" );
        }
        //-------------------------------------------------------------

        this._addCells_( this.tbody.insertRow( -1 ), oData );

        if( this.getBodyRowCount() === 1 )   // 1st line just added
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
        if( (this.getSelectedRowIndex() < 0)  ||  // Clone button is disabled when no row is selected, but this can be called programatically
            (! this.isEditable()) )
        {
            return this;
        }

        let oData = this.getRowData( this.getSelectedRowIndex() );
        let oRow  = this.tbody.insertRow( -1 );

        // Now we have to set a def value for those cols which editor is null (false editor means clonable but no editable)
        for( let colName in oData )
        {
            if( this._getValue4KeyAtColumn_( colName, "editor", null ) === null )
            {
                oData[colName] = this._getValue4KeyAtColumn_( colName, "default", "" );
            }
        }

        this._addCells_( oRow, oData );

        if( this.onRowCloned !== null )
            this.onRowCloned( this, this.tbody.rows.length - 1 );

        this._refreshButtons_();

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
            (nRow < 0) )    // Delete button is disabled when no row is selected, but this can be called programatically
        {
            return this;
        }

        if( this.onDeleteRow !== null )
        {
            let result = this.onDeleteRow( this, nRow );

            if( p_base.isBoolean( result ) && (! result) )
                return this;    // Returns only if the invoked function returned a boolean which value is false
        }

        if( ! this.isEmptyRow( nRow ) )
            this.deletedRows.push( this.getRowData( nRow ) );

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
        while( this.tbody.rows.length > 0 )
            this.deleteRow( 0 );

        this.selectedRowIndex = -1;
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
        let n1stRow = this.getHeadRowCount();

        for( let nRow = n1stRow; nRow < this.tbody.rows.length; nRow++ )
        {
            if( this.isEmptyRow( this.tbody.rows[nRow] ) )
                this.table.deleteRow( nRow-- );    // row-- because current row was deleted
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
     * this funciton does nothing. Otherwise, navigator (web browser) print function
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
                '<td><i class="fa fa-plus"></i></td>'+
                '<td>Appends a new row at bottom of table</td>'+
            '</tr>'+
            '<tr>'+
                '<td><i class="fa fa-copy"></i></td>'+
                '<td>Clones highlighted row</td>'+
            '</tr>'+
            (this.onSave === null ? '' :
            '<tr>'+
                '<td><i class="fa fa-save"></i></td>'+
                '<td>Saves current data</td>'+
            '</tr>') +
            '<tr>'+
                '<td><i class="fa fa-trash"></i></td>'+
                '<td>Deletes highlighted row</td>'+
            '</tr>'+
            (this.onPrint === null ? '' :
            '<tr>'+
                '<td><i class="fa fa-print"></i></td>'+
                '<td>Prints current data</td>'+
            '</tr>')+
            '<tr>'+
                '<td><i class="fa fa-question-circle"></i></td>'+
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
        for( let n = 0; n < colDefs.length; n++ )
        {
            if( (! colDefs[n].hasOwnProperty( "name" ))  ||
                (! p_base.isString( colDefs[n].name )) )
            {
                throw "Invalid column definitions";
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

                    if( (sEditor !== null) && (sEditor.startsWith( "<input" ) || sEditor.startsWith( "<select" )) )
                    {
                        return true;     // Is editable because at least one column has an editor
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
        if( p_base.isNumeric( row ) )
            row = this.getRow( Number.parseInt(row ) );

        if( ! row )
            throw "Invalid row "+ row;

        for( let nCol = 0; nCol < row.cells.length; nCol++ )
        {
            let value = this.getValue( row.cells[nCol] );

            if( (value !== null)                                                 &&
                (value !== this._getValue4KeyAtColumn_( "default", nCol, null )) &&
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
        row = Number.parseInt( row );

        if( (row < 0) || (row >= this.tbody.rows.length) )
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
        else
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
     * Sets a new value for desigated row and col or cell.
     * La fila tiene que ser absoluta: incluyendo las líneas de cabecera.
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

        if( arguments.length > 2 )             // It is assumed that args are: nRow and nCol | sColName and value
        {
            oCell = this.getCell( arguments[0], arguments[1] );
            value = arguments[2];
        }
        else                                   // It is assumed that args are: oCell and value
        {
            oCell = arguments[0];
            value = arguments[1];
        }

        if( oCell !== null )
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
        newIndex = Number.parseInt( newIndex );

        if( ! p_base.isNumeric( newIndex ) )
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

        let oldIndex = this.selectedRowIndex;    // No puedo usar esto --> this.getSelectedRowIndex(); pq desde allí se llama a aquí

        if( oldIndex !== -1 )
        {
            $(this.tbody.rows[oldIndex]).css( 'color'           , this.unselectedRowInk   );
            $(this.tbody.rows[oldIndex]).css( 'background-color', this.unselectedRowPaper );
        }

        $(this.tbody.rows[newIndex]).css( 'color'           , this.selectedRowInk   );
        $(this.tbody.rows[newIndex]).css( 'background-color', this.selectedRowPaper );

        this.selectedRowIndex = newIndex;

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
        return (p_base.isUndefined( this.tbody.rows ) ? 0 : this.tbody.length);
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
     * Sets ink and paper colors (in HEX format) for the unselecetd rows.
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
        this.isBtnPrintEnabled  = bEnabled;
        this.isBtnHelpEnabled   = bEnabled;
        this._refreshButtons_();

        // Buttons added by user
        let aoButton = p_base.get( this.toolbar ).getElementsByTagName( "button" );

        for( let n = 0; n < aoButton.length; n++ )
        {
            aoButton[n].disabled = ! bEnabled;
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

        // If there is not an editor for this column, edit has to be aborted.
        if( editor === null )
        {
            return;
        }

        $(cell).html( editor );                             // Sustituyo el contenido de la celda por su editor

        if( $(cell).find('input').length > 0 )              // Ahora puedo mirar si es un 'input'
        {
            this.cellEditor = $(cell).find('input')[0];     // Cambio una string con texto HTML por el elemento HTML
        }
        else if( $(cell).find('select').length > 0 )        // Si no es un 'input', miro si es un 'select'
        {
            this.cellEditor = $(cell).find('select')[0];    // Cambio una string con texto HTML por el elemento HTML
        }
        else                                                // Si no es ni imput ni select, no puedo trabajar con el editor
        {
            return this;                                    // No puedo trabajar con él: la celda se queda como estaba
        }

        this.bucket = val2edit;

        let nMinWidth = this._getValue4KeyAtColumn_( sColName, "minwidth", null );
        let nMaxWidth = this._getValue4KeyAtColumn_( sColName, "maxwidth", null );

        if( nMinWidth !== null ) { $(this.cellEditor).css( "min-width", nMinWidth +"ch" ); }
        if( nMaxWidth !== null ) { $(this.cellEditor).css( "max-width", nMaxWidth +"ch" ); }

        this._setClickEvent_( false );
        this._setEditorValue_( this.cellEditor, val2edit );

        $(this.cellEditor).focus().trigger( 'focusin' );

        let self = this;

        if( p_base.isOfType( this.cellEditor, 'color' ) )                 // THIS IS ONLY FOR FIREFOX !!!!!!
        {
            this.cellEditor.addEventListener('change',
                                             (evt) =>
                                                {
                                                    self._endEdition_( cell, evt );
                                                },
                                                false);     // Event func does not work when done via JQuery: $(cellEditor).on('change'...
        }
        else
        {
            $(this.cellEditor).on( 'blur', (evt) => self._endEdition_( cell, evt ) );

            if( $(this.cellEditor).is('select') || p_base.isOfType( this.cellEditor, 'date' ) )
            {
                $(this.cellEditor).on( 'change', function(evt){ $(evt.target).blur(); } );
            }
        }

        $(this.cellEditor).on( 'keyup',
            function( event )
            {
                event.preventDefault();
                event.stopPropagation();

                if( (event.keyCode === 13) || (event.keyCode === 27) )
                {
                    if (event.keyCode === 27)
                    {
                        self._setEditorValue_( self.cellEditor, sCellVal );    // Restore previous value
                    }

                    $(event.target).blur();
                }
            } );
    }

    _endEdition_( cell, evt )
    {
        let sColName = this._dataColName_( cell );
        let $editor  = $(evt.target);
        let newVal   = $editor.val();   // P.ej.: <option value="{value}">{text}</option>
                                        // Si es un select y la option tiene el "value=", entonces val() devuelve {value},
                                        // pero si la option no tiene el "value=", entonces devuelve {text}
        let bChanged = (this.bucket != newVal);  // It is OK !=
        this.bucket  = undefined;                // Reset the value

        if( $editor.is( "select" ) )
        {
            let text = $editor.find( "option:selected" ).text();

            if( newVal !== text )           // newVal contiene el {value}
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
        $(cell).css( 'background-color', '' );
        $(event.target).off( 'blur.table_editor_myspace' );      // Removes the event handler
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

            this.selectRowAtIndex( this.selectedRowIndex, true );
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
        if( this.toolBar !== null )
        {
            let editable = this.isEditable();
            let noRows   = this.getBodyRowCount() === 0;

            $(p_base.get( this.btnAppendId )).prop( 'disabled', (! this.isBtnAppendEnabled) || (! editable));
            $(p_base.get( this.btnCloneId  )).prop( 'disabled', (! this.isBtnCloneEnabled ) || (! editable) || noRows);
            $(p_base.get( this.btnSaveId   )).prop( 'disabled', (! this.isBtnSaveEnabled  ) || (! editable) || (this.onSave === null) );
            $(p_base.get( this.btnDeleteId )).prop( 'disabled', (! this.isBtnDeleteEnabled) || (! editable) || noRows );
            $(p_base.get( this.btnHelpId   )).prop( 'disabled', (! this.isBtnHelpEnabled  ) || (this.onHelp  === null) );
            $(p_base.get( this.btnPrintId  )).prop( 'disabled', (! this.isBtnPrintEnabled ) || (this.onPrint === null) || noRows );
        }

        return this;
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
            $(editor).val( cellValue );
        }
        else
        {
            $(editor).find( 'value' )
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
        for( let n = 0; n < this.colDefinitions.length; n++ )
        {
            let cell  = oRow.insertCell( -1 );            // -1 == insert at end of row (append)
            let name  = this.colDefinitions[n].name;
            let value = (p_base.isDefined( oData[name] ) ? oData[name] : this._getValue4KeyAtColumn_( name, "default", "" ));
            let text  = (this.cellFormatter === null ? value : this.cellFormatter( this, name, value ));

            cell.innerHTML = text;

            if( ! this.colDefinitions[n].editor )          // null or false value for a column means: not-visible
            {
                $(cell).css( "display", "none" );
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
        $(this.toolbar).empty();    // Deletes all child elements

        let toolbar = '';

        if( this.isEditable() )         // CARE: can not use use <button> because when a button has focus or is the default button and user press [Enter] its action is executed
        {
            // NEXT: definir la class mini-btn de algun modo en este fichero
            toolbar += '<i class="mini-btn fa fa-plus"  id="'+ this.btnAppendId +'" title="Appends a new row"></i>'+
                       '<i class="mini-btn fa fa-copy"  id="'+ this.btnCloneId  +'" title="Clones highlighted row"></i>'+
                       '<i class="mini-btn fa fa-save"  id="'+ this.btnSaveId   +'" title="Save current data"></i>'+
                       '<i class="mini-btn fa fa-trash" id="'+ this.btnDeleteId +'" title="Deletes highlighted row"></i>';
        }

        toolbar += '<i class="mini-btn fa fa-print"           id="'+ this.btnPrintId +'" title="Prints current data"></i>'+
                   '<i class="mini-btn fa fa-question-circle" id="'+ this.btnHelpId  +'" title="Shows help"></i>';

        $(this.toolbar).append( $('<div>'+ toolbar +'</div>') );

        // It is easier to hide than to check in all the code if it exists prior to use it
        if( this.onSave  === null )  $('#'+this.btnSaveId ).hide();
        if( this.onPrint === null )  $('#'+this.btnPrintId).hide();
        if( this.onHelp  === null )  $('#'+this.btnHelpId ).hide();

        let self = this;

        if( this.isEditable() )
        {
            $(p_base.get( this.btnAppendId )).on( 'click', function() { self.appendRow(); } );
            $(p_base.get( this.btnCloneId  )).on( 'click', function() { self.cloneRow();  } );
            $(p_base.get( this.btnSaveId   )).on( 'click', function() { self.save();      } );
            $(p_base.get( this.btnDeleteId )).on( 'click', function() { self.deleteRow(); } );
        }

        $(p_base.get( this.btnPrintId )).on( 'click', function() { self.print(); } );
        $(p_base.get( this.btnHelpId  )).on( 'click', function() { self.help();  } );

        return this;
    }

    _dataColName_( oCell, name )
    {
        if( p_base.isDefined( name ) )
        {
            $(oCell).data( "te_colname", name );
        }

        return $(oCell).data( "te_colname" );                 // Care: it is mandatory to use lower-case and to omit "data-" prefix
    }

    /**
     * Si se pasa sólo un arg, devuelve el valor actual, si se pasan 2 args,
     * actualiza el valor actual pero devuelve el valor anterior a ser cambiado.
     *
     * @param {type} oCell
     * @param {type} value
     * @returns {Object}
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
     * [de]activate click on all cells (current and future ones).
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
                                                        self.selectRowAtIndex( event.target.parentElement.rowIndex, true );
                                                        self.editCell( event.target );
                                                    } );
        }
        else
        {
            $(this.table).off( 'click', 'tbody tr' );
        }

        return this;
    }
}