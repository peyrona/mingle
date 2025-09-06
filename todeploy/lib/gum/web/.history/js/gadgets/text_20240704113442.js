
"use strict";

class GumText extends GumGadget
{
    constructor( props )
    {
        super( "text", props );

        if( p_base.isEmpty( props ) )
        {
            this.exen   = null;
            this.device = null;
            this.label  = null;
            this.align  = "left";
            this.rows   = 1  ;       // Multi-line text (textarea)
            this.keep   = 0  ;       // Keep only last N lines (0 == all)
            this.size   = 100;       // Font size (relative as %)
            this.color  = "#000000";
        }

        this._updateListener_();
    }

    //-----------------------------------------------------------------------------------------//

    /**
     * Shows a dialog to modify received gadget.
     */
    edit()
    {
        super.edit();

        let self = this;

        dlgText.setup( this,
                       (text) => self._update_( text ),
                       ()     => self._isEditing = false )
               .show();
    }

    refresh( isOngoing = false )
    {
        // Nothing to do --- this.show();
    }

    /**
     * Returns a copy of this gatget properties (without uneeded properties).
     */
    distill()
    {
        return super.distill();
    }

    show()
    {
        let sHTML;

        if( this.rows === 1 )
        {
            sHTML = '<label id="'+ this.id +'" class="label style="font-size:'+ this.size +'%; color:'+ this.color +'">'+
                        this.label +
                    '</label>';
        }
        else
        {
            sHTML = '<textarea id="'+ this.id +'" class="textarea is-expanded" rows="'+ this.rows +'" style="font-size:'+ this.size +'%; color:'+ this.color +'; background-color: rgba(0,0,0,0);">'+
                         this.label +
                    '<textarea>';
        }

        this.$container
            .empty()
            .append( sHTML );

        // Change text alignment ------------------------------------

        $('#'+this.id).removeClass( ['has-text-left', 'has-text-centered', 'has-text-right'] );

        if( this.rows === 1 )
        {
            switch( this.align )
            {
                case 'left'  : $('#'+this.id).addClass('has-text-left'    );  break;
                case 'center': $('#'+this.id).addClass('has-text-centered');  break;
                case 'right' : $('#'+this.id).addClass('has-text-right'   );  break;
            }
        }

        if( this.rows === 1 ) $('#'+this.id).html( this.label );
        else                  $('#'+this.id).val(  this.label );

        $('#'+this.id).css('fontSize', this.size +'%').css('color', this.color);
    }

    //--------------------------------------------------------------------------------//
    // PRIVATE SCOPE

    _update_( props )
    {
        super._update( props );

        this.show();
        this._updateListener_();

        return this;
    }

    _updateListener_()
    {
        this._delAllListeners();

        if( p_base.isNotEmpty( this.label ) && this.label.toLowerCase().includes('{*device*}')  )
        {
            let self  = this;
            let nRows = this.rows;     // Store values into a variable to detach them from the props reference (breaks the reference to props)
            let nKeep = this.keep;

            let fn = function( when, devName, devValue )
                     {
                        let $txt = $('#'+self.id);

                        devValue = self.label.replace( /\{\*device\*\}/gi, devValue.toString() );

                        if( nRows === 1 )      // It is a <label></label>
                        {
                            $txt.html( devValue );
                        }
                        else                   // It is a <textarea></textarea>  (therefore can not use .html(...))
                        {
                            if( nKeep === 1 )
                            {
                               $txt.val( devValue );
                            }
                            else
                            {
                                if( p_base.isNotEmpty( $txt ) )    // It could be that a msg comes from server even after the gadget is deleted (because my system is async)
                                {
                                    let sVal = $txt.val();

                                    let ndx = self._getSubstringIndex_( sVal, nKeep - 1 );   // Returns the index of the nth position of '\n' in str.

                                    if( ndx > -1 )
                                        sVal = sVal.substring( ndx );

                                    if( sVal.charAt( sVal.length - 1 ) !== '\n' )
                                        sVal = $txt + '\n';

                                    $txt.val( sVal + devValue );
                                }
                            }
                        }
                     };

            this._addListener( this.exen, this.device, fn );
        }
    }

    /**
     * Returns the index of the nth position of '\n' in str counting backwards from the end of str.
     * Tiene que ir desde el final hacia el principio pq necesito quedarme con las últimas nth líneas.
     *
     * @param {type} str
     * @param {type} nth
     * @returns {Number}
     */
    _getSubstringIndex_( str, nth )
    {
        if( p_base.isEmpty( str ) || nth < 2 )
            return -1;

        let ndx = str.length;
        let cnt = 0;                    // Counter

        while( ndx > 0 )
        {
            if( str.charAt( ndx-- ) === '\n' )
            {
                if( ++cnt === nth )
                    return ndx + 2;     // +2 pq así funciona
            }
        }

        return -1;
    }
}