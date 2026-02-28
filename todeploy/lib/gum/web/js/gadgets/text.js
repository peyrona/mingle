
"use strict";

class GumText extends GumGadget
{
    constructor( props )
    {
        super( "text", props );

        if( p_base.isEmpty( props ) )
        {
            this.exen           = null;
            this.device         = null;
            this.accessor       = null;     // Key (for pair) or 1-based index (for list)
            this._devValueType_ = null;     // "list", "pair", or "scalar" — auto-detected, not persisted
            this.label          = "This is a fixed text";
            this.align     = "left";
            this.rows      = 1  ;
            this.keep      = 0  ;       // Keep only last N lines (0 == all)
            this.size      = 100;       // Font size (relative as %)
            this.color     = null;
            this.bold      = false;
            this.italic    = false;
            this.underline = false;

            // Must be here -->
            this.width  = 160;               // Rewritten from parent
            this.height = this.rows * 22;    // Rewritten from parent
        }
    }

    //-----------------------------------------------------------------------------------------//

    /**
     * Shows a dialog to modify received gadget.
     */
    edit( bStart = true )
    {
        super.edit( bStart );

        if( bStart )
        {
            let self = this;

            dlgText.setup( this,
                        () => self.show(),
                        () => self.edit( false ) )
                   .show();
        }

        return this;
    }

    show( isOngoing = false )
    {
        if( isOngoing )
            return this;

        let $el;

        if( this.rows <= 1 )
        {
            $el = GumGadget.cloneTemplate( "tpl-text-label" );
        }
        else
        {
            $el = GumGadget.cloneTemplate( "tpl-text-textarea" );
            $el.attr( 'rows', this.rows );
        }

        $el.attr( 'id', this.id );

        let $txt = this.getContentArea()
                       .empty()
                       .append( $el )
                       .children()
                       .first();     // The label or the textarea

        $txt.removeAttr( 'style' )
            .css('font-weight', 'normal')      // 'font-weight: normal' because Bulma CSS make <label> bold

        // Change text effects ------------------------------------

        if( this.rows <= 1 )
        {
            $txt.removeClass( ['has-text-left', 'has-text-centered', 'has-text-right'] );

            switch( this.align )
            {
                case 'left'  : $txt.addClass('has-text-left'    );  break;
                case 'center': $txt.addClass('has-text-centered');  break;
                case 'right' : $txt.addClass('has-text-right'   );  break;
            }

            $txt.css( 'font-weight', (this.bold ? 'bold' : 'normal') );

            if( this.italic )
                $txt.css( 'font-style', 'italic' );

            if( this.underline )
                $txt.css( 'text-decoration', 'underline' );
        }

        let txt = this._hasMacro_( this.label ) ? this.label.replace( "{*device*}", "???" )    // When the macro is part of the label, shown text has to start being empty
                                                : this.label;

        if( this.rows === 1 ) $txt.html( txt );
        else                  $txt.val(  txt );

        $txt.css('fontSize', this.size +'%')
            .css('color', this.color || null)       // null removes the inline style (inherits CSS)
            .css('background-color', 'rgba(0, 0, 0, 0)');

        this._updateListener_();

        return super.show( isOngoing );
    }

    //--------------------------------------------------------------------------------//
    // PRIVATE SCOPE

    _updateListener_()
    {
        this._resetListeners();

        if( ! this._hasMacro_( this.label ) )
            return;

        let self = this;
        let $txt = $('#'+this.id);

        let fn = function( action, payload )
                {
                    if( ! self._devValueType_ )
                        self._devValueType_ = self._detectValueType_( payload.value );

                    let xResolved = self._resolveAccessor_( payload.value, self.accessor, payload.name );

                    if( xResolved === null || xResolved === undefined )
                        return;

                    let sTxt = self.label.replace( /\{\*device\*\}/gi, xResolved.toString() );

                    if( self.rows === 1 )    // It is a <label></label>
                    {
                        $txt.html( sTxt );
                    }
                    else                     // It is a <textarea></textarea>  (therefore can not use .html(...))
                    {
                        if( self.keep <= 1 )
                        {
                            $txt.val( sTxt );
                        }
                        else
                        {
                            let last = self._getLastLines_( $txt.val(), self.keep );

                            sTxt = sTxt +'\n';

                            if( ! last.endsWith( sTxt ) )     // Avoid appending if the last line is already the same
                                $txt.val( last + sTxt );
                        }
                    }

                    self._executeUserCode_( action, payload );
                };

        this._addListener( this.exen, this.device, fn );
    }

    /**
     * Returns the last nth lines in passed str.
     *
     * @param {type} str
     * @param {type} nth
     * @returns {Number}
     */
    _getLastLines_( str, nth )
    {
        const lines = str.split('\n');
        const lastN = lines.slice( -nth );

        return lastN.join('\n');
    }

    _hasMacro_( str )
    {
        return str &&
               str.length >= 10 &&      // '{*device*}' == 10
               str.toLowerCase().includes('{*device*}');
    }
}