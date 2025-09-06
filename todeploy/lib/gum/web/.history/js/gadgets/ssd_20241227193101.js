
"use strict";

class GumSSD extends GumGadget
{
    constructor( props )
    {
        super( "ssd", props );

        if( p_base.isEmpty( props ) )
        {
            this.width  = gum.isUsingFreeLayout() ? 120 : "100%";       // Rewritten from parent
            this.height = gum.isUsingFreeLayout() ?  32 : "100%";       // Rewritten from parent

            this.exen     = null ;
            this.device   = null ;
            this.color    = "lcd";
            this.integers = 4    ;
            this.decimals = 2    ;
            this.wrapper  = null ;    // instance of class SevenSegmentDisplay
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

            dlgSSD.setup( this,
                        () => self.show(),
                        () => self.edit( false ) )
                .show();
        }

        return this;
    }

    show()
    {
        let sId4SVG;

        if( ! this.wrapper )    // Is this the 1st time
        {
            sId4SVG = p_base.uuid();

            let sHTML = '<svg id="'+ sId4SVG +'">'+
                        '</svg>';

            this.getContainer()
                .empty()
                .append( sHTML );
        }
        else
        {
            sId4SVG = this.wrapper._SVGID;
        }

        this.wrapper = new SevenSegmentDisplay( sId4SVG );

        let svg = this.getContainer().find('svg')[0];
            svg.setAttribute( "width" , this.getContainer().width()  );
            svg.setAttribute( "height", this.getContainer().height() );

        // This forces to recalculate the height and the segments -->
        this.wrapper.NumberOfDigits        = Math.min( 12, this.integers + this.decimals );    // Max total cells is 12
        this.wrapper.NumberOfDecimalPlaces = Math.min(  6, this.decimals );
        this.wrapper.ColorScheme           = (this.color === 'lcd') ? this.wrapper._ColorSchemes.LCD
                                                                    : (this.color === 'red') ? this.wrapper._ColorSchemes.Red
                                                                                             : (this.color === 'orange') ? this.wrapper._ColorSchemes.Orange
                                                                                                                         : this.wrapper._ColorSchemes.Green;

        this._updateListener_();

        return this;
    }

    /**
     * Returns a copy of this gatget properties (without uneeded properties).
     */
    distill()
    {
        return super.distill( ["wrapper"] );
    }

    //-----------------------------------------------------------------------------------------//
    // PRIVATE SCOPE

    _updateListener_()
    {
        this._resetListeners();

        if( this.device !== null )
        {
            let self = this;

            let fn = function( action, when, devName, devVal )
                    {
                        if( self._getReadedCounter( action ) > 1 )
                            return;

                        try
                        {
                            self.wrapper.Value = devVal.toFixed( self.decimals );
                        }
                        catch( error )     // Several errors can be generated: e.g. when the number has more digits that the display (or it is not even a number)
                        {
                            p_app.alert( 'Display for "'+ name +'" error: '+ error );
                        }
                    };

            this._addListener( this.exen, this.device, fn );
        }
    }
}