
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

            this.exen     = null;
            this.device   = null;
            this.icon_on  = 'default_on.png';
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

        let img = '<img id="'+ this.id +'"src="images/'+ this.icon_off +'">';

        this.getContainer()
            .empty()
            .append( img );

        $('#'+this.id).attr('width' , Math.max( this.getContainer().width() , 16 ))
                      .attr('height', Math.max( this.getContainer().height(), 16 ))
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
                        if( self._isProperValue( "Boolean", payload.value, payload.name ) )
                        {
                            self._hasErrors( false );
                            $id.attr('src', (payload.value ? sOn : sOff) );
                        }
                    };

            this._addListener( this.exen, this.device, fn );
        }
    }
}