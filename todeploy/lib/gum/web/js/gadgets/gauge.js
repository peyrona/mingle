
"use strict";

class GumGauge extends GumGadget
{
    constructor( props )
    {
        super( "gauge", props );

        if( p_base.isEmpty( props ) )
        {
            this.width  = gum.isUsingFreeLayout() ? 180 : "100%";      // Rewritten from parent
            this.height = gum.isUsingFreeLayout() ?  90 : "100%";      // Rewritten from parent

            this.exen      = null;
            this.device    = null;
            this.wrapper   = null;     // Instance of class GaugeWrap
            this.min       = 0;        // Min value
            this.max       = 100;      // Max value
            this.angle     = 0;
            this.radius    = 95;
            this.thick     = 38;       // Arc thickness
            this.zones     = [];       // Colored zones: { start : <num>, end: <num>, color: <str> }
            this.ticks     = true;     // Show ticks or not (Divisions: 6, Length: 50, Color: #333333, Width: 10) (Subdivisions: 3, Length: 30, Color: #666666, Width: 6)
            this.ticks_pos = [];       // Show ticks  values at their position
            this.decimals  = 0;        // Decimal digits for current device value
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

            dlgGauge.setup( this,
                            () => self.show(),
                            () => self.edit( false ) )
                    .show();
        }

        return this;
    }

    destroy()
    {
        this.wrapper.del();

        return super.destroy();
    }

    show( isOngoing = false )
    {
        if( isOngoing )
            return this;

        this.getContainer()
            .empty()
            .append('<canvas id="'+ this.id +'"></canvas>');

        this.getContainer()
            .attr('width' , Math.max( this.getContainer().width() , 48 ))
            .attr('height', Math.max( this.getContainer().height(), 48 ));

        $('#'+this.id).width(  this.getContainer().width()  )
                      .height( this.getContainer().height() );

        if( this.wrapper )
            this.wrapper.del();

        this.wrapper = new GaugeWrap( this );   // Has to be recreated from zero: the lib I use
                                                // does not allow to manipulate it after been created

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
                        if( self._isProperValue( "Number", payload.value, payload.name ) )
                        {
                            self._hasErrors( false );
                            self.wrapper.set( payload.value );
                        }
                    };

            this._addListener( this.exen, this.device, fn );
        }
    }
}

//------------------------------------------------------------------------------------------------------------------------------------//
//------------------------------------------------------------------------------------------------------------------------------------//
//------------------------------------------------------------------------------------------------------------------------------------//

//----------------------------------------------------------------------------//
// Wrapper for another Gauge
// In this case the warpper is for: https://bernii.github.io/gauge.js/
// "angle"  , 0        // Of arch: from -33 to +33  (0 == 180º)
// "radius" , 95       // Of arch: from 50 to 100
// "width"  , 38       // Of arch: from  10 to  60  (Pointer (aguja) length: 60, Color: #000000, Stroke: 45 )
// "ticks"  , true     // Show ticks or not (Divisions: 6, Length: 50, Color: #333333, Width: 10) (Subdivisions: 3, Length: 30, Color: #666666, Width: 6)
//----------------------------------------------------------------------------//

"use strict";

class GaugeWrap
{
    constructor( config )
    {
        let opts =
        {
            angle      : config.angle  / 100,
            radiusScale: config.radius / 100,
            lineWidth  : config.thick  / 100,

            limitMax: false,
            limitMin: false,

            generateGradient: false,
            highDpiSupport  : true,

            pointer:
            {
                length: 0.6,
                strokeWidth: 0.045,
                color: '#000000'
            },

            staticLabels:
            {
                labels: this._getTicksValues_( config ),
                color : "#000000",
                font  : "14px sans-serif"
            },

            staticZones: this._getZones_( config ),

            renderTicks: this._getTicks_( config )
        };

        let canvas = p_base.get( config.id );    // Better not to use JQuey

        this.gauge = new Gauge( canvas );
        this.gauge.setOptions( opts );
        this.gauge.animationSpeed = 12;          // set animation speed delay (default is 32)
        this.gauge.maxValue       = config.max;  // Note: setMaxValue(...) does not exist
        this.gauge.setMinValue( config.min );    // Note: prefer setter over gauge.minValue = 0
        this.gauge.set( config.min );            // Must be provided, and must be done in this wired way
    }

    //----------------------------------------------------------------------------//
    // PUBLIC METHODS

    set( nValue )
    {
        this.gauge.set( nValue );
    }

    del()
    {
        delete this.gauge;
    }

    //----------------------------------------------------------------------------//
    // PRIVATE' METHODS

    _getTicksValues_( config )
    {
        let sPos = config.ticks_pos;

        if( p_base.isEmpty( sPos ) )
            return [];                  // gauge.js needs an array

        let aPos = sPos.split( ',' );

        if( p_base.isEmpty( aPos ) )
            return [];                  // gauge.js needs an array

        for( let n = 0; n < aPos.length; n++ )
            aPos[n] = (aPos[n].indexOf('.') > -1 ? parseFloat( aPos[n] )
                                                 : parseInt(   aPos[n] ));

        return aPos;
    }

    _getDecimals_( config )
    {
        if( p_base.isEmpty( config.ticks_pos ) )
            return 0;

        return ((config.ticks_pos.indexOf('.') > 1) ? 3 : 0);
    }

    _getZones_( config )
    {
        if( p_base.isEmpty( config.zones ) )
            return null;

        let aRet = [];

        for( let n = 0; n < config.zones.length; n++ )
        {
            aRet.push( { strokeStyle: config.zones[n].color,
                         min: config.zones[n].start,
                         max: config.zones[n].end } );
        }

        return aRet;
    }

    _getTicks_( config )        // No need to worry about arc thickness: the gauge readjust the length of ticks automatically
    {
        if( ! config.ticks )
            return null;

        return  {
                    divisions: 6,
                    divWidth : 1,
                    divLength: 0.4,
                    divColor : '#333333',
                    subDivisions: 3,
                    subLength   : 0.2,
                    subWidth    : 0.6,
                    subColor    : '#666666'
                };
    }
}