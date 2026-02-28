
"use strict";

class GumCheck extends GumGadget
{
    constructor( props )
    {
        super( "check", props );

        if( p_base.isEmpty( props ) )
        {
            this.width  = gum.isUsingFreeLayout() ? 64 : "100%";      // Rewritten from parent
            this.height = gum.isUsingFreeLayout() ? 28 : "100%";      // Rewritten from parent

            this.exen           = null;
            this.device         = null;
            this.accessor       = null;     // Key (for pair) or 1-based index (for list)
            this._devValueType_ = null;     // "list", "pair", or "scalar" — auto-detected, not persisted
            this.icon_on        = 'default_on.png';
            this.icon_off = 'default_off.png';
            this.actuable = false;     // Can toogle (on click) its associated device state?
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

            dlgCheck.setup( this,
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

        let $img = GumGadget.cloneTemplate( "tpl-check" );
        $img.attr( 'id', this.id )
            .attr( 'src', 'images/' + this.icon_off );

        this.getContentArea()
            .empty()
            .append( $img );

        $('#'+this.id).attr('width' , Math.max( this.getContentArea().width() , 16 ))
                      .attr('height', Math.max( this.getContentArea().height(), 16 ))
                      .attr('src'   , 'images/'+ this.icon_off )
                      .css ('cursor', (this.actuable ? 'pointer' : 'default'));

        this._updateListener_();

        return super.show( isOngoing );
    }

    //-----------------------------------------------------------------------------------------//
    // PRIVATE SCOPE

    _updateListener_()
    {
        this._resetListeners();

        if( this.exen && this.device )
        {
            let $id  = $('#'+this.id);
            let sOn  = "images/"+ this.icon_on;
            let sOff = "images/"+ this.icon_off;
            let self = this;

            let fn = function( action, payload )
                    {
                        if( ! self._devValueType_ )
                            self._devValueType_ = self._detectValueType_( payload.value );

                        let xValue = self._resolveAccessor_( payload.value, self.accessor, payload.name );

                        if( xValue !== null && self._isValidValue( "Boolean", xValue, payload.name ) )
                        {
                            self._hasErrors( false );
                            $id.attr('src', (xValue ? sOn : sOff) );
                        }

                        self._executeUserCode_( action, payload );
                    };

            this._addListener( this.exen, this.device, fn );
        }
    }
}