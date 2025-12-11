//----------------------------------------------------------------------------//
// Utility classes to manage gadget properties in a generic way.
//----------------------------------------------------------------------------//

//----------------------------------------------------------------------------//
// BASE CLASS
//----------------------------------------------------------------------------//

class GadgetEditorHelper
{
    static selectTabInPropsEditDlg( li )
    {
        let $li = $(li);

        $li.closest('ul')
           .children('li')
           .removeClass('is-active');

        $li.addClass('is-active');

        let $dlg = $li.closest('div').parent();
        let sTag = $li.children('a').first().text().toLowerCase();

        if( sTag === 'style' )
        {
            $dlg.children('div').eq(2).hide();
            $dlg.children('div').eq(1).show();    // eq(1) is 2nd child (becasue it is zero based)
        }
        else
        {
            $dlg.children('div').eq(1).hide();    // eq(1) is 2nd child (becasue it is zero based)
            $dlg.children('div').eq(2).show();
        }
    }

    //----------------------------------------------------------------------------//

    constructor( sDivName, gadget, fnOnChanged, fnOnEditEnd, sSelect4ExEnName = "exen", sSelect4DeviceName = "device" )
    {
        if( fnOnChanged === null )
            fnOnChanged = () => {};

        if( fnOnEditEnd === null )
            fnOnEditEnd = () => {};

        if( ! p_base.isFunction( fnOnChanged ) )
            throw '"fnOnChanged" is not a function';

        if( ! p_base.isFunction( fnOnEditEnd ) )
            throw '"fnOnEditEnd" is not a function';

        this.divName     = sDivName;
        this.gadget      = gadget;
        this.fnOnChanged = fnOnChanged;
        this.fnOnEditEnd = fnOnEditEnd;
        this.sExEn       = sSelect4ExEnName;
        this.sDevice     = sSelect4DeviceName;
        this.$dialog     = $('<div>'+
                                '<div class="tabs is-boxed" name="div-tabs">'+
                                    '<ul>'+
                                        '<li class="is-active" onclick="GadgetEditorHelper.selectTabInPropsEditDlg(this)"><a>Gadget</a></li>'+
                                        '<li                   onclick="GadgetEditorHelper.selectTabInPropsEditDlg(this)"><a>Style</a></li>'+
                                    '</ul>'+
                                '</div>'+
                            '</div>');
    }

    showDialog()
    {
        let self = this;

        this.$dialog.append( $('#properties-style') )
                    .append( $('#'+this.divName).show() )
                    .dialog( {
                                 title    : 'Gadget Properties',
                                 modal    : true,
                                 autoOpen : true,
                                 resizable: true,
                                 width    : p_app.getBestWidth(  "90%", 1150, 950 ),
                                 height   : p_app.getBestHeight( "70%",  920, 750 ),
                                 open     : function()
                                            {
                                                self.$dialog.find('div[name="div-tabs"]').find('li:eq(0)').addClass(   'is-active');
                                                self.$dialog.find('div[name="div-tabs"]').find('li:eq(1)').removeClass('is-active');
                                                self.$dialog.find('div[name="div-tabs"]').find('li:eq(2)').removeClass('is-active');
                                                setTimeout( function()     // Because internal JQuery way of functioning, a timeout is the only (dirty) choice
                                                            {
                                                                self._fillForm_();
                                                                dlgStyle.setup( self.gadget );    // Set the values for the 'Style' tab in the gadget dialog
                                                            }, 500 );
                                            },
                                 beforeClose: function()
                                            {
                                                self.fnOnEditEnd();  // Ensure edit end callback is always called before closing
                                                return true;         // Allow the dialog to close
                                            },
                                 close    : function()
                                            {                             // eq(0) is the div for tabs
                                                self.$dialog.children('div').eq(1).hide();
                                                self.$dialog.children('div').eq(2).hide();
                                                self.fnOnEditEnd();
                                            }
                             } );
        return this;
    }

    //-------------------------------------------------------------------------//

    _fillForm_()
    {
        let $form = $('#'+this.divName).find('form').first();

        p_base.setFormFields( $form, this.gadget );
    }
}

//----------------------------------------------------------------------------//
// CLASS
// Utility class used by the dialogs to manage gadget properties when the
// gadget has one single exen associated: SSD, Gauge, Text
//----------------------------------------------------------------------------//
class GadgetEditorHelperSingleExEn extends GadgetEditorHelper
{
    /**
     * Constructor.
     *
     * @param {String}   sDivName          The DIV that has a FORM that has all inputs to be shown in a dialog to manage gadget properties
     * @param {Object}   gadget
     * @param {Function} fnOnChanged
     * @param {Function} fnOnEditEnd
     * @param {String}   sSelect4ExEnName   The name of the key in gadget oProps and the name of the SELECT that holds the ExEns
     * @param {String}   sSelect4DeviceName The name of the key in gadget oProps and the name of the SELECT that holds the Devices for selected ExEn
     */
    constructor( sDivName, gadget, fnOnChanged, fnOnEditEnd, sSelect4ExEnName = "exen", sSelect4DeviceName = "device" )
    {
        super( sDivName, gadget, fnOnChanged, fnOnEditEnd, sSelect4ExEnName, sSelect4DeviceName );
    }

    /**
     * Invoked when a value changes in a Gadget's dialog.
     *
     * @param {type} element
     * @returns {this}
     */
    changed( element )
    {
        element = p_base.get( element );     // If it comes as JQuery Object, it has to be transformed into a pure HTML DOM element

        this.gadget[element.name] = p_base.getFieldValue( element );

        if( element.name === this.sExEn )
        {
            let $olst4ExEns = $('#'+ this.divName +' [name="'+ this.sExEn   +'"]');
            let $olst4Devs  = $('#'+ this.divName +' [name="'+ this.sDevice +'"]');

            gum.fillWithDevices( $olst4ExEns, $olst4Devs );
        }

        this.fnOnChanged();

        return this;
    }

    //------------------------------------------------------------------------//

    _fillForm_()
    {
        let $olst4ExEns = $('#'+ this.divName +' [name="'+ this.sExEn   +'"]');
        let $olst4Devs  = $('#'+ this.divName +' [name="'+ this.sDevice +'"]');

        gum.fillWithExEns(   $olst4ExEns );
        gum.fillWithDevices( $olst4ExEns, $olst4Devs );

        super._fillForm_();     // Do not move this

        if( this.gadget[this.sExEn] )
        {
            p_base.setFieldValue( $olst4ExEns, this.gadget[this.sExEn  ].replace( /\\/g, '' ) );    // Removes escape char '\' );

            if( this.gadget[this.sDevice] )
                p_base.setFieldValue( $olst4Devs , this.gadget[this.sDevice] );
            else
                p_base.setFieldValue( $olst4Devs , null );
        }
        else
        {
            p_base.setFieldValue( $olst4ExEns, null );
        }
    }
}

//----------------------------------------------------------------------------//
// CLASS
// Utility class used by the dialogs to manage gadget properties when the
// gadget has more than one exen associated: Chart, Button
//----------------------------------------------------------------------------//
class GadgetEditorHelperMultiExEn extends GadgetEditorHelper
{
    constructor( sDivName, gadget, fnOnChange, fnOnEditEnd, sColname4Exen = "exen", sColname4Device = "name" )
    {
        super( sDivName, gadget, fnOnChange, fnOnEditEnd, sColname4Exen, sColname4Device );
    }

    setColEditor( table, sColName )
    {
        let $list = $('<select></select>');

        if( sColName === this.sExEn )
        {
            gum.fillWithExEns( $list );
        }
        else
        {
            let sExEn = table.getValue( table.getSelectedRowIndex(), this.sExEn );

            gum.getDeviceNames4( sExEn )
                .forEach( (sDevName) => $list.append( $('<option>'+ sDevName +'</option>') ) );
        }

        // oColum is just a String: we can safely replace it (it is not yet a SELECT DOM object).
        let oColumn = this._getColumn_( table, sColName );
        oColumn.editor = $list[0].outerHTML;

        return this;
    }

    changed( input )
    {
        let newValue = p_base.getFieldValue( input );

        this.gadget[input.name] = newValue;     // Updates internal gadget property

        this.fnOnChanged();

        return this;
    }

    //------------------------------------------------------------------------//

    _getColumn_( table, sColName )
    {
        let aoColumns = table.getColumns();     // This array is not a copy, but a reference to to the internal table array

        for( let n = 0; n < aoColumns.length; n++ )
        {
            if( aoColumns[n].name === sColName )
                return aoColumns[n];
        }

        throw "This should not happen";
    }

    // Esta clase no necesita añadir nada más.
    // _fillForm_()
    // {
    //     super._fillForm_();
    // }
}