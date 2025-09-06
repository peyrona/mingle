
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

    /**
     * Destroy associated listeners and any other needed thing.
     */
    destroy()
    {
        return super.destroy();
    }

    show( isOngoing = false )
    {
        if( isOngoing )
            return;

        let img = '<img id="'+ this.id +'"src="images/'+ this.icon_off +'">';

        this.getContainer()
            .empty()
            .append( img );

        $('#'+this.id).attr('width' , Math.max( this.getContainer().width() , 16 ))
                      .attr('height', Math.max( this.getContainer().height(), 16 ))
                      .attr('src'   , 'images/'+ this.icon_off )
                      .css ('cursor', (this.actuable ? 'pointer' : 'default'));

        this._updateListener_();

        return this;
    }

    //-----------------------------------------------------------------------------------------//
    // PRIVATE SCOPE

    _updateListener_()
    {
        this._resetListeners();

        if( this.device !== null )
        {
            let self = this;

            let fn = function( action, when, name, value )
                    {
                        // if( self._getReadedCounter( action ) > 1 )    --> Not used by check
                        //     return;

                        let sOn  = "images/"+ self.icon_on;
                        let sOff = "images/"+ self.icon_off;

                        if( p_base.isBoolean( value ) )  $('#'+self.id).attr('src', (value ? sOn : sOff) );
                        else                              p_app.alert( 'Device "'+ name +'" is not Boolean; its value is: '+ value );
                    };

            this._addListener( this.exen, this.device, fn );
        }
    }
}