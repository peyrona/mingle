
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

            this.exen           = null ;
            this.device         = null ;
            this.accessor       = null ;    // Key (for pair) or 1-based index (for list)
            this._devValueType_ = null ;    // "list", "pair", or "scalar" — auto-detected, not persisted
            this.color          = "lcd";
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

    show( isOngoing = false )
    {
        if( isOngoing )
            return this;

        if( this._hasErrors() )
            return this;

        let sId4SVG;

        if( ! this.wrapper )    // Is this the 1st time?
        {
            sId4SVG = p_base.uuid();

            let $svg = GumGadget.cloneTemplate( "tpl-ssd" );
            $svg.attr( 'id', sId4SVG );

            this.getContentArea()
                .empty()
                .append( $svg );
        }
        else
        {
            sId4SVG = this.wrapper._SVGID;
        }

        this.wrapper = new SevenSegmentDisplay( sId4SVG );

        let svg = this.getContentArea().find('svg')[0];
            svg.setAttribute( "width" , this.getContentArea().width()  );
            svg.setAttribute( "height", this.getContentArea().height() );

        // This forces to recalculate the height and the segments -->
        this.wrapper.NumberOfDigits        = Math.min( 12, this.integers + this.decimals );    // Max total cells is 12
        this.wrapper.NumberOfDecimalPlaces = Math.min(  6, this.decimals );
        this.wrapper.ColorScheme           = (this.color === 'lcd') ? this.wrapper._ColorSchemes.LCD
                                                                    : (this.color === 'red') ? this.wrapper._ColorSchemes.Red
                                                                                             : (this.color === 'orange') ? this.wrapper._ColorSchemes.Orange
                                                                                                                         : this.wrapper._ColorSchemes.Green;

        this._updateListener_();

        return super.show( isOngoing );
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

        if( this.exen && this.device )
        {
            let self = this;

            let fn = function( action, payload )
                    {
                        if( ! self._devValueType_ )
                            self._devValueType_ = self._detectValueType_( payload.value );

                        let xValue = self._resolveAccessor_( payload.value, self.accessor, payload.name );

                        if( xValue !== null && self._isValidValue( "Number", xValue, payload.name ) )
                        {
                            let sValue = xValue.toFixed( self.decimals );

                            if( parseInt( sValue ).toString().length > self.integers )
                            {
                                self._showError( "Device value has more digits that gadget integer places" );
                            }
                            else
                            {
                                self._hasErrors( false );
                                self.wrapper.Value = sValue;
                            }
                        }

                        self._executeUserCode_( action, payload );
                    };

            this._addListener( this.exen, this.device, fn );
        }
    }
}