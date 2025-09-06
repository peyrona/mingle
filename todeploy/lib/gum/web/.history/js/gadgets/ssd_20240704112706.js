
"use strict";

class GumSSD extends GumGadget
{
    constructor( props )
    {
        super( "ssd", props );

        if( p_base.isEmpty( props ) )
        {
            this.exen     = null ;
            this.device   = null ;
            this.color    = "lcd";
            this.integers = 4    ;
            this.decimals = 2    ;
            this.wrapper  = null ;    // instance of class SevenSegmentDisplay
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

        dlgSSD.setup( this,
                      (ssd) => self._update_( ssd ),
                      ()    => self._isEditing = false )
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
        let sId4SVG;

        if( ! this.wrapper )    // Is this the 1st time
        {
            sId4SVG = p_base.uuid();

            let sHTML = '<svg id="'+ sId4SVG +'">'+
                        '</svg>';

            this.$container
                .empty()
                .append( sHTML );
        }
        else
        {
            sId4SVG = this.wrapper._SVGID;
        }

        this.wrapper = new SevenSegmentDisplay( sId4SVG );
        this.refresh();
    }

    refresh( isOngoing = false )
    {
        let svg = this.$container.find('svg')[0];
            svg.setAttribute( "width" , this.$container.width()  );
            svg.setAttribute( "height", this.$container.height() );

        // This forces to recalculate the height and the segments -->
        this.wrapper.NumberOfDigits        = Math.min( 12, this.integers + this.decimals );    // Max total cells is 12
        this.wrapper.NumberOfDecimalPlaces = Math.min(  6, this.decimals );

        return this;
    }

    /**
     * Returns a copy of this gatget properties (without uneeded properties).
     */
    distill()
    {
        return super.distill( "wrapper" );
    }

    //-----------------------------------------------------------------------------------------//
    // PRIVATE SCOPE

    _update_( props )
    {
        props.wrapper.NumberOfDigits        = Math.min( 12, props.integers + props.decimals );    // Max total cells is 12
        props.wrapper.NumberOfDecimalPlaces = Math.min(  6, props.decimals );
        props.wrapper.ColorScheme           = (props.color === 'lcd') ? props.wrapper._ColorSchemes.LCD
                                                                      : (props.color === 'red') ? props.wrapper._ColorSchemes.Red
                                                                                                : (props.color === 'orange') ? props.wrapper._ColorSchemes.Orange
                                                                                                                             : props.wrapper._ColorSchemes.Green;
        super._update( props );

        this.refresh();
        this._updateListener_();

        return this;
    }

    _updateListener_()
    {
        let self = this;

        let fn = function( when, devName, devVal )
                 {
                    try
                    {
                        if( p_base.isNumber( devVal ) )
                            self.wrapper.Value = devVal;
                    }
                    catch( error )     // Generates several errors: e.g. when the number has more digits tha the display (or it is not even a number)
                    {
                        console.log( 'Display for "'+ self +'" error: '+ error );
                    }
                 };

        gum_ws.delListener( this.id );
        gum_ws.addListener( this.id, { exen: this.exen, name: this.device }, fn );



        this._delAllListeners();

        if( this.device )
        {
            let self = this;

            let fn = function( when, devName, devVal )
                 {
                    try
                    {
                        if( p_base.isNumber( devVal ) )
                            self.wrapper.Value = devVal;
                    }
                    catch( error )     // Generates several errors: e.g. when the number has more digits tha the display (or it is not even a number)
                    {
                        console.log( 'Display for "'+ self +'" error: '+ error );
                    }
                 };

            this._addListener( this.exen, this.device, fn );
        }
    }
}