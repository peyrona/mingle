
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

            this.exen           = null;
            this.device         = null;
            this.accessor       = null;     // Key (for pair) or 1-based index (for list)
            this._devValueType_ = null;     // "list", "pair", or "scalar" — auto-detected, not persisted
            this.wrapper        = null;     // Instance of class GaugeWrap

            this.min          = 0;          // Min value
            this.max          = 100;        // Max value
            this.angle_span   = 4.2;        // Total arc sweep in radians
            this.zone_thick   = 12;         // Arc zone thickness
            this.zones        = [];         // Colored zones: { start: <num>, end: <num>, color: <str> }
            this.ticks        = true;       // Show ticks or not
            this.ticks_pos    = '';         // Tick label values (comma-separated string)
            this.ticks_color  = null;       // Color for tick marks and tick labels (null = default)
            this.needle_color = null;       // Color of the pointer needle (null = default)
            this.bg_color     = null;       // Gauge plate background color (null = default)
            this.label        = '';         // Label text on gauge face
            this.label_color  = null;       // Label text color (null = default)
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

        let $canvas = GumGadget.cloneTemplate( "tpl-gauge" );
        $canvas.attr( 'id', this.id );

        this.getContentArea()
            .empty()
            .append( $canvas );

        this.getContentArea()
            .attr('width' , Math.max( this.getContentArea().width() , 48 ))
            .attr('height', Math.max( this.getContentArea().height(), 48 ));

        $('#'+this.id).width(  this.getContentArea().width()  )
                      .height( this.getContentArea().height() );

        if( this.wrapper )
            this.wrapper.del();

        this.wrapper = new GaugeWrap( this );

        this._updateListener_();

        return super.show( isOngoing );
    }

    /**
     * Returns a copy of this gadget properties (without unneeded properties).
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
                            self._hasErrors( false );
                            self.wrapper.set( xValue );
                        }

                        self._executeUserCode_( action, payload );
                    };

            this._addListener( this.exen, this.device, fn );
        }
    }
}

//------------------------------------------------------------------------------------------------------------------------------------//
//------------------------------------------------------------------------------------------------------------------------------------//

/**
 * Adapter between GumGauge configuration and GaugeVintage renderer.
 * Translates persisted GumGauge properties into the GaugeVintage options format.
 */
class GaugeWrap
{
    constructor( config )
    {
        let tickValues = this._parseTickValues_( config.ticks_pos );

        let opts =
        {
            angleSpan      : config.angle_span || 4.2,
            zoneThickness  : config.zone_thick || 12,
            zones          : config.zones      || [],
            showTicks      : config.ticks !== false,
            tickValues     : tickValues,
            tickColor      : config.ticks_color  || '#333333',
            needleColor    : config.needle_color || '#d32f2f',
            backgroundColor: config.bg_color     || '#f4ecd8',
            label          : config.label        || '',
            labelColor     : config.label_color  || '#555555'
        };

        this._vintage = new GaugeVintage( config.id, opts );
        this._vintage.config.minValue = config.min;
        this._vintage.config.maxValue = config.max;
        this._vintage.setValue( config.min );
    }

    //----------------------------------------------------------------------------//
    // PUBLIC METHODS

    /**
     * Sets the current gauge value (animated).
     * @param {number} nValue - The new value to display.
     */
    set( nValue )
    {
        this._vintage.setValue( nValue );
    }

    /**
     * Stops the animation loop and cleans up.
     */
    del()
    {
        this._vintage.stop();
        delete this._vintage;
    }

    //----------------------------------------------------------------------------//
    // PRIVATE METHODS

    /**
     * Parses a comma-separated string of tick values into an array of numbers.
     * @param {string} sPos - Comma-separated tick position string.
     * @returns {number[]} Array of numeric tick values.
     */
    _parseTickValues_( sPos )
    {
        if( p_base.isEmpty( sPos ) )
            return [];

        return sPos.split( ',' )
                   .map( v => v.indexOf('.') > -1 ? parseFloat( v.trim() ) : parseInt( v.trim() ) )
                   .filter( v => ! isNaN( v ) );
    }
}

//------------------------------------------------------------------------------------------------------------------------------------//
//------------------------------------------------------------------------------------------------------------------------------------//

/**
 * Represents a vintage-style analog sphere/arc gauge.
 * Handles rendering logic via HTML5 Canvas and provides animation for value transitions.
 */
class GaugeVintage
{
    /**
     * Initializes a new instance of the GaugeVintage class.
     * @param {string} canvasId - The ID of the HTML canvas element.
     * @param {Object} [options={}] - Configuration options for the gauge.
     * @param {number} [options.angleSpan=4.2] - The total radial sweep of the gauge in radians.
     * @param {number} [options.zoneThickness=12] - Thickness of the colored value zones.
     * @param {Array<number>} [options.tickValues=[0, 20, 40, 60, 80, 100]] - Array of values to display as ticks.
     * @param {string} [options.tickColor='#2b2b2b'] - Hex color for ticks and tick text.
     * @param {string} [options.needleColor='#d32f2f'] - Hex color for the needle.
     * @param {string} [options.backgroundColor='#f4ecd8'] - Hex color for the gauge plate.
     * @param {Array<Object>} [options.zones=[]] - Array of zone objects {start, end, color}.
     * @param {string} [options.label=""] - Label text displayed on the gauge face.
     * @param {string} [options.labelColor='#555555'] - Hex color for the label text.
     */
    constructor( canvasId, options = {} )
    {
        this.canvas = document.getElementById( canvasId );
        this.ctx    = this.canvas.getContext('2d');

        this._stopped = false;

        this.config = {
            minValue      : 0,
            maxValue      : 100,
            value         : 0,
            displayValue  : 0,
            angleSpan     : options.angleSpan      || 4.2,
            zoneThickness : options.zoneThickness  || 12,
            showTicks     : options.showTicks !== false,
            tickValues    : options.tickValues     || [0, 20, 40, 60, 80, 100],
            tickColor     : options.tickColor      || '#2b2b2b',
            needleColor   : options.needleColor    || '#d32f2f',
            centerColor   : options.centerColor    || '#2b2b2b',
            backgroundColor: options.backgroundColor || '#f4ecd8',
            labelColor    : options.labelColor     || '#555555',
            zones         : options.zones          || [],
            label         : options.label          || '',
            animationSpeed: 0.1,
            jitterAmount  : 0.2
        };

        this.resize();
        window.addEventListener( 'resize', () => this.resize() );
        this.animate();
    }

    /**
     * Adjusts the canvas internal coordinate system to match its display size.
     */
    resize()
    {
        if( ! this.canvas || ! this.canvas.parentElement )
            return;

        const rect = this.canvas.parentElement.getBoundingClientRect();
        this.canvas.width  = rect.width;
        this.canvas.height = rect.height;
    }

    /**
     * Sets the target value for the gauge. The gauge will animate toward this value.
     * @param {number|string} val - The new value to set.
     */
    setValue( val )
    {
        const num = parseFloat( val );
        this.config.value = Math.min( Math.max( isNaN(num) ? 0 : num, this.config.minValue ), this.config.maxValue );
    }

    /**
     * Updates a specific configuration property.
     * @param {string} key   - The configuration key to update.
     * @param {any}    value - The new value for the configuration key.
     */
    updateConfig( key, value )
    {
        this.config[key] = value;
    }

    /**
     * Stops the animation loop. Call this before discarding the instance to avoid
     * leaking rAF callbacks.
     */
    stop()
    {
        this._stopped = true;
    }

    /**
     * Main rendering method. Clears the canvas and redraws the plate, zones, ticks, and needle.
     */
    draw()
    {
        const { ctx, canvas, config } = this;
        if( !canvas.width || !canvas.height ) return;

        const cx         = canvas.width  / 2;
        const cy         = canvas.height / 2;
        const baseRadius = (Math.min(cx, cy)) * 0.85;

        // THRESHOLDS for UI logic
        const fullCircleThreshold = 2 * Math.PI * 0.54;

        // Determine actual arc for ticks/needle
        let activeAngleSpan = config.angleSpan;
        let isFullSphere    = config.angleSpan >= fullCircleThreshold;

        // Special case: if sphere is visually "full", limit the active scale to 60% of the circle
        if( config.angleSpan >= 2 * Math.PI - 0.05 )
        {
            activeAngleSpan = 2 * Math.PI * 0.60;
        }

        // CENTERING: Arc is centered at the top (-PI/2)
        const startAngle = (-Math.PI / 2) - (activeAngleSpan / 2);

        ctx.clearRect( 0, 0, canvas.width, canvas.height );

        // 1. Draw Plate (Background)
        ctx.save();
        ctx.beginPath();
        if( isFullSphere )
        {
            ctx.arc( cx, cy, baseRadius, 0, 2 * Math.PI );
        }
        else
        {
            const margin = 0.15;
            ctx.moveTo( cx, cy );
            ctx.arc( cx, cy, baseRadius, startAngle - margin, startAngle + activeAngleSpan + margin );
            ctx.closePath();
        }
        ctx.fillStyle = config.backgroundColor;
        ctx.fill();
        ctx.restore();

        // 2. Color Zones
        config.zones.forEach( zone =>
        {
            const sPerc  = (zone.start - config.minValue) / (config.maxValue - config.minValue);
            const ePerc  = (zone.end   - config.minValue) / (config.maxValue - config.minValue);
            const sAngle = startAngle + (sPerc * activeAngleSpan);
            const eAngle = startAngle + (ePerc * activeAngleSpan);

            ctx.beginPath();
            ctx.arc( cx, cy, baseRadius - (config.zoneThickness/2 + 4), sAngle, eAngle );
            ctx.strokeStyle  = zone.color;
            ctx.lineWidth    = config.zoneThickness;
            ctx.globalAlpha  = 0.4;
            ctx.stroke();
            ctx.globalAlpha  = 1.0;
        });

        // 3. Tick Marks and Values
        if( config.showTicks )
        {
            ctx.save();
            ctx.textAlign    = 'center';
            ctx.textBaseline = 'middle';
            ctx.fillStyle    = config.tickColor;
            ctx.strokeStyle  = config.tickColor;

            config.tickValues.forEach( val =>
            {
                const perc  = (val - config.minValue) / (config.maxValue - config.minValue);
                const angle = startAngle + (perc * activeAngleSpan);

                const x1 = cx + Math.cos(angle) * (baseRadius - 8);
                const y1 = cy + Math.sin(angle) * (baseRadius - 8);
                const x2 = cx + Math.cos(angle) * baseRadius;
                const y2 = cy + Math.sin(angle) * baseRadius;

                ctx.beginPath();
                ctx.moveTo( x1, y1 );
                ctx.lineTo( x2, y2 );
                ctx.lineWidth = 2.5;
                ctx.stroke();

                const tx = cx + Math.cos(angle) * (baseRadius - 32);
                const ty = cy + Math.sin(angle) * (baseRadius - 32);
                ctx.font = 'bold 16px "Courier New"';
                ctx.fillText( val, tx, ty );
            });
            ctx.restore();
        }

        // 4. (Label rendered after glare — see step 7)

        // 5. Needle Rendering
        const jitter      = (Math.random() - 0.5) * config.jitterAmount;
        const needlePerc  = (config.displayValue - config.minValue) / (config.maxValue - config.minValue);
        const needleAngle = startAngle + (needlePerc * activeAngleSpan) + (jitter * 0.015);

        ctx.save();
        ctx.translate( cx, cy );
        ctx.rotate( needleAngle );
        ctx.shadowColor   = 'rgba(0,0,0,0.3)';
        ctx.shadowBlur    = 4;
        ctx.shadowOffsetX = 2;

        // Triangle needle pointing towards arc
        ctx.beginPath();
        ctx.moveTo( 0, -5 );
        ctx.lineTo( baseRadius - 15, 0 );
        ctx.lineTo( 0,  5 );
        ctx.closePath();
        ctx.fillStyle = config.needleColor;
        ctx.fill();

        // Central pivot point
        ctx.beginPath();
        ctx.arc( 0, 0, 14, 0, 2 * Math.PI );
        ctx.fillStyle = config.centerColor;
        ctx.fill();
        ctx.restore();

        // 6. Glare/Glass Effect
        ctx.save();
        const grad = ctx.createLinearGradient( cx - baseRadius, cy - baseRadius, cx + baseRadius, cy + baseRadius );
        grad.addColorStop( 0,   'rgba(255,255,255,0.15)' );
        grad.addColorStop( 0.5, 'rgba(255,255,255,0)'    );
        grad.addColorStop( 1,   'rgba(255,255,255,0.05)' );

        ctx.beginPath();
        if( isFullSphere )
        {
            ctx.arc( cx, cy, baseRadius, 0, 2 * Math.PI );
        }
        else
        {
            const margin = 0.15;
            ctx.moveTo( cx, cy );
            ctx.arc( cx, cy, baseRadius, startAngle - margin, startAngle + activeAngleSpan + margin );
            ctx.closePath();
        }
        ctx.fillStyle = grad;
        ctx.fill();
        ctx.restore();

        // 7. Label Text — drawn last so it sits on top of the knob and glare
        if( config.label )
        {
            ctx.save();
            ctx.font         = 'bold 13px "Courier New"';
            ctx.fillStyle    = config.labelColor;
            ctx.textAlign    = 'center';
            ctx.textBaseline = 'middle';
            ctx.fillText( config.label, cx, (cy + 14 + canvas.height) / 2 );   // midpoint of the space below the knob
            ctx.restore();
        }
    }

    /**
     * Starts the animation loop for smoothing value changes and adding jitter.
     * @private
     */
    animate()
    {
        const step = () =>
        {
            if( this._stopped ) return;

            const diff = this.config.value - this.config.displayValue;
            this.config.displayValue += diff * this.config.animationSpeed;
            this.draw();
            requestAnimationFrame( step );
        };
        step();
    }
}
