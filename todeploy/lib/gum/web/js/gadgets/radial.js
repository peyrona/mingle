
"use strict";

// ============================================================================
// Radial — SVG arc gauge renderer
// Adapted from radial.html (jQuery plugin wrapper removed; used directly).
// ============================================================================

/**
 * @class Radial
 * @classdesc
 * A highly configurable, production-ready circular arc gauge widget using HTML5 SVG.
 * Features smooth cubic-bezier animations, dynamic live updates, and XSS-safe DOM
 * manipulation. Instantiate directly (no jQuery plugin needed).
 *
 * @example
 * const r = new Radial(document.getElementById('my-div'), { min: 0, max: 100 });
 * r.update(75);
 */
class Radial
{
    /**
     * Initializes a new Radial gauge instance.
     *
     * @param {HTMLElement} element - The root DOM element to render the gauge into.
     * @param {Object}      [options={}] - Configuration overrides (merged with Radial.DEFAULTS).
     */
    constructor( element, options )
    {
        this.$el      = $( element );
        this.settings = $.extend( {}, Radial.DEFAULTS, options );

        // State tracking
        this.currentValue    = this.settings.min;
        this.targetValue     = Math.max( this.settings.min, Math.min( this.settings.max, this.settings.value ) );
        this.animationFrameId = null;

        // Unique identifier prevents SVG gradient/filter ID collisions when multiple gauges coexist
        this.uid = 'gauge_grad_' + Math.random().toString( 36 ).substring( 2, 9 );

        this.init();
    }

    //----------------------------------------------------------------------------//
    // PRIVATE METHODS

    /**
     * Computes mathematical constants and triggers initial rendering.
     * @private
     */
    init()
    {
        // Prevent division-by-zero and undefined behavior
        if( this.settings.max <= this.settings.min )
        {
            console.warn( 'Radial: "max" must be strictly greater than "min". Reverting to defaults.' );
            this.settings.max = this.settings.min + 100;
        }

        const { size, thickness } = this.settings;

        // SVG geometry constants
        this.center       = size / 2;
        this.radius       = (size - thickness) / 2;
        this.circumference = 2 * Math.PI * this.radius;

        // The gauge uses a 270-degree visible arc (75% of a full circle)
        this.arcLength = this.circumference * 0.75;

        this.render();

        // Allow the browser to process the initial DOM state before triggering the fill animation
        requestAnimationFrame( () => this.update( this.targetValue ) );
    }

    /**
     * Renders SVG and text overlay into the DOM using XSS-safe manipulation.
     * @private
     */
    render()
    {
        const { size, thickness, trackColor, colorStart, colorEnd, textColor, labelColor, label, colorMidpoint } = this.settings;

        // SVG string — math and color values only; no user input injected here
        // Note: the circle has transform="rotate(135)", which rotates the objectBoundingBox gradient
        // along with it. In the rotated frame, offset=0% ends up near the arc end and offset=100%
        // near the arc start, so the stops are assigned in reverse order to keep colorStart at the
        // arc start (left/7:30 o'clock) and colorEnd at the arc end (right/4:30 o'clock).
        //
        // colorMidpoint (10–90) controls where colorStart begins transitioning into colorEnd.
        // In visual arc space, the first colorMidpoint% of the arc stays colorStart; the remainder
        // blends towards colorEnd. Because the gradient is reversed, the SVG stop offset is
        // (100 - colorMidpoint)%.
        const splitPct = ( 100 - colorMidpoint ) + '%';

        const svgString = `
            <svg class="gauge-arc-svg" viewBox="0 0 ${size} ${size}" width="${size}" height="${size}">
                <defs>
                    <linearGradient id="${this.uid}" x1="0%" y1="0%" x2="100%" y2="0%">
                        <stop offset="0%"          stop-color="${colorEnd}"   />
                        <stop offset="${splitPct}" stop-color="${colorStart}" />
                        <stop offset="100%"        stop-color="${colorStart}" />
                    </linearGradient>
                    <filter id="shadow_${this.uid}" x="-20%" y="-20%" width="140%" height="140%">
                        <feDropShadow dx="0" dy="4" stdDeviation="6" flood-color="${colorEnd}" flood-opacity="0.4"/>
                    </filter>
                </defs>
                <circle class="gauge-arc-track" cx="${this.center}" cy="${this.center}" r="${this.radius}"
                    stroke="${trackColor}" stroke-width="${thickness}"
                    stroke-dasharray="${this.arcLength} ${this.circumference}"
                    stroke-linecap="round" transform="rotate(135 ${this.center} ${this.center})" />
                <circle class="gauge-arc-progress" cx="${this.center}" cy="${this.center}" r="${this.radius}"
                    stroke="url(#${this.uid})" stroke-width="${thickness}"
                    stroke-dasharray="0 ${this.circumference}"
                    transform="rotate(135 ${this.center} ${this.center})"
                    filter="url(#shadow_${this.uid})" />
            </svg>
        `;

        // Set up container
        this.$el.empty()
                .addClass( 'gauge-arc-container' )
                .css({ width: `${size}px`, height: `${size}px` });

        this.$el.append( svgString );

        // XSS protection: build text nodes via jQuery DOM methods, never innerHTML
        const $content = $( '<div>' ).addClass( 'gauge-arc-content' );

        this.$valueText = $( '<div>' )
            .addClass( 'gauge-arc-value' )
            .css({ color: textColor, fontSize: `${size * 0.22}px` })
            .text( this.settings.format( this.settings.min ) );

        $content.append( this.$valueText );

        this.$labelText = null;

        if( label )
        {
            this.$labelText = $( '<div>' )
                .addClass( 'gauge-arc-label' )
                .css({ color: labelColor, fontSize: `${size * 0.08}px` })
                .text( label );   // $.text() sanitizes the input

            $content.append( this.$labelText );
        }

        this.$el.append( $content );

        // Cache progress arc element for fast stroke updates
        this.$progress = this.$el.find( '.gauge-arc-progress' );
    }

    //----------------------------------------------------------------------------//
    // PUBLIC METHODS

    /**
     * Updates the gauge to a new value with smooth arc and text animations.
     *
     * @param {number} newValue - The target value to animate towards.
     */
    update( newValue )
    {
        // Clamp to configured range
        newValue = Math.max( this.settings.min, Math.min( this.settings.max, newValue ) );

        const { min, max } = this.settings;
        const percentage  = (newValue - min) / (max - min);
        const drawLength  = this.arcLength * percentage;

        // Animate SVG stroke via CSS transition defined in gum.css (.gauge-arc-progress)
        this.$progress.css( 'stroke-dasharray', `${drawLength} ${this.circumference}` );

        // Animate numeric text counter
        this.animateText( this.currentValue, newValue );
        this.currentValue = newValue;
    }

    /**
     * Smoothly animates the displayed numeric text using requestAnimationFrame.
     * Cancels any in-progress animation to prevent race conditions.
     *
     * @param {number} start - Starting value.
     * @param {number} end   - Target value.
     * @private
     */
    animateText( start, end )
    {
        // Cancel prior animation loop to prevent overlapping contexts
        if( this.animationFrameId )
            cancelAnimationFrame( this.animationFrameId );

        const duration  = 1000;  // Must match CSS transition duration in .gauge-arc-progress
        const startTime = performance.now();

        const step = ( currentTime ) =>
        {
            const elapsed   = currentTime - startTime;
            const progress  = Math.min( elapsed / duration, 1 );

            // Ease-out-cubic timing function
            const eased     = 1 - Math.pow( 1 - progress, 3 );
            const currentNum = start + (end - start) * eased;

            this.$valueText.text( this.settings.format( Math.round( currentNum ) ) );

            if( progress < 1 )
            {
                this.animationFrameId = requestAnimationFrame( step );
            }
            else
            {
                // Lock final value precisely
                this.$valueText.text( this.settings.format( Math.round( end ) ) );
                this.animationFrameId = null;
            }
        };

        this.animationFrameId = requestAnimationFrame( step );
    }

    /**
     * Updates value and label font sizes to fit the actual rendered container.
     * Enforces minimum sizes so text remains legible at small card dimensions.
     * Call this after the gauge is mounted and whenever the container is resized.
     *
     * @param {number} containerPx - The shortest side of the container in CSS pixels.
     */
    resizeFonts( containerPx )
    {
        const MIN_VALUE_PX = 11;
        const MIN_LABEL_PX = 9;

        this.$valueText.css( 'font-size', Math.max( MIN_VALUE_PX, containerPx * 0.22 ) + 'px' );

        if( this.$labelText )
            this.$labelText.css( 'font-size', Math.max( MIN_LABEL_PX, containerPx * 0.08 ) + 'px' );
    }
}

/**
 * Default configuration for Radial.
 * @static
 */
Radial.DEFAULTS =
{
    size         : 200,
    thickness    : 16,
    min          : 0,
    max          : 100,
    value        : 0,
    label        : '',
    trackColor   : '#334155',
    colorStart   : '#38bdf8',
    colorEnd     : '#818cf8',
    colorMidpoint: 50,             // Where (% of arc) colorStart starts blending into colorEnd
    textColor    : '#f8fafc',
    labelColor   : '#94a3b8',
    format       : ( value ) => value    // Passthrough — overridden by GumRadial to append unit suffix
};


// ============================================================================
// GumRadial — GUM gadget wrapper for the Radial arc gauge
// ============================================================================

/**
 * @class GumRadial
 * @extends GumGadget
 * @classdesc
 * GUM gadget that wraps the Radial SVG arc gauge renderer. Handles template
 * cloning, WebSocket device subscription, properties dialog, and JSON
 * serialisation following the same patterns as all other GUM gadgets.
 */
class GumRadial extends GumGadget
{
    /**
     * @param {Object|null} props - Serialised properties (null when creating a new gadget).
     */
    constructor( props )
    {
        super( "radial", props );

        if( p_base.isEmpty( props ) )
        {
            this.width  = gum.isUsingFreeLayout() ? 180 : "100%";   // Rewritten from parent
            this.height = gum.isUsingFreeLayout() ? 180 : "100%";   // Rewritten from parent

            this.exen           = null;
            this.device         = null;
            this.accessor       = null;     // Key (for pair) or 1-based index (for list)
            this._devValueType_ = null;     // "list", "pair", or "scalar" — auto-detected, not persisted

            this.min         = 0;
            this.max         = 100;
            this.label       = '';
            this.unit_suffix = '';

            this.thickness    = 16;
            this.colorStart   = '#38bdf8';
            this.colorEnd     = '#818cf8';
            this.colorMidpoint= 50;
            this.trackColor   = '#334155';
            this.textColor    = '#f8fafc';
            this.labelColor   = '#94a3b8';
        }

        // Runtime state — never persisted
        this._radial_ = null;
    }

    //----------------------------------------------------------------------------//

    /**
     * Opens the properties dialog for this gadget.
     *
     * @param {boolean} [bStart=true] - True to open, false to close.
     * @returns {GumRadial}
     */
    edit( bStart = true )
    {
        super.edit( bStart );

        if( bStart )
        {
            let self = this;

            dlgRadial.setup( this,
                             () => self.show(),
                             () => self.edit( false ) )
                     .show();
        }

        return this;
    }

    /**
     * Renders the arc gauge into the gadget content area.
     *
     * @param {boolean} [isOngoing=false] - True during resize/drag — skips re-render.
     * @returns {GumRadial}
     */
    show( isOngoing = false )
    {
        if( isOngoing )
            return this;

        let $container = GumGadget.cloneTemplate( "tpl-radial" );
        $container.attr( 'id', this.id );

        this.getContentArea()
            .empty()
            .append( $container );

        const unitSuffix = this.unit_suffix || '';

        this._radial_ = new Radial( document.getElementById( this.id ),
        {
            size         : 200,
            min          : this.min,
            max          : this.max,
            label        : this.label,
            thickness    : this.thickness,
            colorStart   : this.colorStart,
            colorEnd     : this.colorEnd,
            colorMidpoint: this.colorMidpoint,
            trackColor   : this.trackColor,
            textColor    : this.textColor,
            labelColor   : this.labelColor,
            format       : ( v ) => unitSuffix ? ( v + unitSuffix ) : String( v )
        } );

        // Scale SVG and container to fill available space; viewBox="0 0 200 200" handles proportional scaling
        this.getContentArea().find( 'svg' ).css({ width: '100%', height: '100%' });
        this.getContentArea().find( '.gauge-arc-container' ).css({ width: '100%', height: '100%' });

        // Resize fonts to match the actual rendered container size.
        // Math.min(w, h) uses the shortest side so text fits in non-square cards too.
        const $area    = this.getContentArea();
        const areaPx   = Math.min( $area.width(), $area.height() ) || 200;
        this._radial_.resizeFonts( areaPx );

        this._updateListener_();

        return super.show( isOngoing );
    }

    /**
     * Returns a serialisable copy of this gadget's properties,
     * excluding runtime-only fields.
     *
     * @returns {Object} Plain object safe for JSON serialisation.
     */
    distill()
    {
        return super.distill( ['_radial_'] );
    }

    //----------------------------------------------------------------------------//
    // PRIVATE SCOPE

    /**
     * Resets the WebSocket device listener and resubscribes when exen+device are set.
     * @private
     */
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
                             self._radial_.update( xValue );
                         }

                         self._executeUserCode_( action, payload );
                     };

            this._addListener( this.exen, this.device, fn );
        }
    }
}
