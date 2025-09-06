
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
    edit( bStart = true )
    {
        super.edit( bStart );

        if( bStart )
        {
            let self = this;

            dlgButton.setup( this,    
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

        this.getContainer()
            .empty()
            .append( '<button id="'+ this.id +'" class="button is-responsive">'+
                         this.label +
                     '</button>' );

        let $btn = $('#'+this.id);

        $btn.removeClass( ['is-small','is-normal','is-medium','is-large',
                           'is-primary','is-info','is-success','is-warning','is-danger','is-light','is-dark','is-black'] )
            .addClass( [this.size, this.color] )
            .html( this.label );

        if( gum.isInDesignMode() && gum.isUsingFreeLayout() )
        {
            this.getContainer()
                .parent()     // Wnd Div
                .width(  $btn.outerWidth()  +  4)
                .height( $btn.outerHeight() + 22);
        }

        // Set or remove onClick

        if( p_base.isEmpty( this.devices ) )
        {
            $btn.off('click');
        }
        else
        {
            let self = this;

            $btn.on('click', function( evt )
                            {
                                for( const dev of self.devices )
                                    gum_ws.requestChange( dev.exen, dev.name, dev.value, dev.type );
                            } );
        }    
        
        return super.show( isOngoing );
    }

    //-----------------------------------------------------------------------------------------//
    // PRIVATE SCOPE
}