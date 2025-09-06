
"use strict";

class GumCheck extends GumGadget
{
    constructor( props )
    {
        super( "check", props );

        if( p_base.isEmpty( props ) )
        {
            this.exen     = null;
            this.device   = null;
            this.icon_on  = 'default_on.png';
            this.icon_off = 'default_off.png';
            this.actuable = false;      // Can toogle (on click) its associated device state?
        }

        if( gum.isInDesignMode() && gum.isUsingFreeLayout() )
        setTimeout( () => { // Dirty but simple
                            this.getContainer()
                                .parent()
                                .width(  64 )     // Inital default size
                                .height( 64 );
                          }, 350 );
    }

    //-----------------------------------------------------------------------------------------//

    /**
     * Shows a dialog to modify received gadget.
     */
    edit()
   {
        super.edit();

        let self = this;

        dlgCheck.setup( this,
                        () => self._changed_(),
                        () => self._isEditing = false )
                .show();
    }

    /**
     * Destroy associated listeners and any other needed thing.
     */
    destroy()
    {
        return super.destroy();
    }

    show()
    {
        let img = '<img id="'+ this.id +'"src="images/'+ this.icon_off +'">';

        this.getContainer()
            .empty()
            .append( img );

        this.refresh();
        this._updateListener_();
    }

    refresh( isOngoing = false )
    {
        this.getContainer().width(  64 );
        this.getContainer().height( 60 )



        $('#'+this.id).attr('width' , Math.max( this.getContainer().width() , 16 ))
                      .attr('height', Math.max( this.getContainer().height(), 16 ))
                      .attr('src'   , 'images/'+ this.icon_off )
                      .css ('cursor', (this.actuable ? 'pointer' : 'default'));

        return this;
    }

    /**
     * Returns a copy of this gatget properties (without uneeded properties).
     */
    distill()
    {
        return super.distill();
    }

    //-----------------------------------------------------------------------------------------//
    // PRIVATE SCOPE

    _changed_( props )
    {
        this.refresh();
        this._updateListener_();

        return this;
    }

    _updateListener_()
    {
        this._delAllListeners();

        if( this.device )
        {
            let self = this;

            let fn = function( when, devName, devVal )
                    {
                        let sOn  = "images/"+ self.icon_on;
                        let sOff = "images/"+ self.icon_off;

                        if( p_base.isBoolean( devVal ) )
                        {
                            $('#'+self.id).attr('src', (devVal ? sOn : sOff) );
                        }
                        else
                        {
                            console.log( 'Device "'+ devName +'" is not Boolean; its value is: '+ devVal );
                        }
                    };


            this._addListener( this.exen, this.device, fn );
        }
    }
}