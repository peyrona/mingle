
"use strict";

class GumButton extends GumGadget
{
    constructor( props )
    {
        super( "button", props );

        if( p_base.isEmpty( props ) )
        {
            this.devices = [];         // [ {exen:"exenAddress", name:"devName", value: oValue, type: "number" | "boolean" | "string"}, ... ]
            this.label   = "Do it"    ;
            this.color   = "is-info"  ;
            this.size    = "is-normal";
        }
    }

    //-----------------------------------------------------------------------------------------//

    isResizable()
    {
        return false;
    }

    /**
     * Shows a dialog to modify received gadget.
     */
    edit()
    {
        super.edit();

        let self = this;

        dlgButton.setup( this,
                         (btn) => self._update_( btn ),
                         ()    => self._isEditing = false )
                 .onTableChanged( (gadget) => self._update_( gadget ) )
                 .show();
    }

    /**
     * Destroy associated listeners and any other needed thing.
     */
    destroy()
    {
        gum_ws.delListener( this.id );

        super.destroy();

        return this;
    }

    show()
    {
        this.$container
            .empty()
            .append( '<button id="'+ this.id +'" class="button is-responsive">'+
                         this.label +
                     '</button>' );

        this.refresh();
    }

    refresh()
    {
        let $btn = $('#'+this.id);

        $btn.removeClass( ['is-small','is-normal','is-medium','is-large',
                           'is-primary','is-info','is-success','is-warning','is-danger','is-light','is-dark','is-black'] )
            .addClass( [this.size, this.color] )
            .html( this.label );

        if( gum.isInDesignMode() && gum.isUsingFreeLayout() )
        {
            this.$container
                .parent()     // Wnd Div
                .width(  $btn.outerWidth()  +  4)
                .height( $btn.outerHeight() + 22);
        }
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

    _update_( props )
    {
        super._update( props );

        if( p_base.isEmpty( this.devices ) )
        {
            $('#'+this.id).off('click');
        }
        else
        {
            let self = this;

            $('#'+this.id).on('click', function( evt )
                                {
                                   for( const dev of self.devices.length )
                                       gum_ws.requestChange( dev.exen, dev.name, dev.value, dev.type );
                                } );
        }

        this.refresh();

        return this;
    }
}