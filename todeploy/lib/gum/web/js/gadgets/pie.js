
"use strict";

class GumPie extends GumGadget
{
    constructor( props )
    {
        super( "pie", props );

        if( p_base.isEmpty( props ) )
        {
            this.width  = gum.isUsingFreeLayout() ? 320 : 0;     // Rewritten from parent
            this.height = gum.isUsingFreeLayout() ? 320 : 0;     // Rewritten from parent

            this.slices    = [];        // [ {exen:oExen, name:"devName", label:"Label", color:"#00ff00"}, ... ]
            this.wrapper   = null;      // An instance of PieWrap
            this.show_lgnd = true;      // Show legend
            this.lgnd_pos  = "right";   // Legend position: top, right, bottom, left
            this.lbl_clr   = "#000000"; // For labels color
            this.doughnut  = false;     // If true, render as doughnut instead of pie
        }

        this.wrapper = new PieWrap( this );
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

            dlgPie.setup( this,
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
        this.wrapper.destroy();

        return super.destroy();
    }

    show( isOngoing = false )
    {
        if( isOngoing )
            return this;

        this.wrapper.show();

        this._updateListeners_();

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

    _updateListeners_()
    {
        this._resetListeners();

        if( p_base.isNotEmpty( this.slices ) )
        {
            let self = this;

            for( let item of this.slices )     // [ {exen:oExen, name:"devName", label:"Label", color:"#00ff00"}, ... ]
            {
                let fn = function( action, payload )
                    {
                        if( payload && payload.name !== undefined &&
                            self._isValidValue( "Number", payload.value, payload.name ) )
                        {
                            self._hasErrors( false );
                            self.wrapper.updateSlice( payload.name, payload.value );
                        }
                    };

                this._addListener( item.exen, item.name, fn );
            }
        }

        return this;
    }
}

//------------------------------------------------------------------------------------------------------------------------------------//
//------------------------------------------------------------------------------------------------------------------------------------//

//-------------------------------------------------------------------------------------//
// Wrapper class for Pie/Doughnut charts using Chart.js
//-------------------------------------------------------------------------------------//

class PieWrap
{
    static _aInstances_ = [];     // [ { id:<div_id>, chart:<instance> }, ... ]

    //----------------------------------------------------------------------------//

    constructor( parent )
    {
        if( ! (parent instanceof GumPie) )
            throw new Error( "Parent must be a GumPie" );

        if( ! parent.id )
            parent.id = p_base.uuid();

        PieWrap._aInstances_.push( { id: parent.id, chart: this } );

        this.parent = parent;
        this.divId  = p_base.uuid();
        this.canvas = null;
        this.chart  = null;
    }

    //----------------------------------------------------------------------------//
    // PUBLIC METHODS

    /**
     * Updates the value of a slice in the pie chart.
     *
     * @param {String} sDevName Device name identifying the slice.
     * @param {Number} nValue   New value for the slice.
     * @returns {PieWrap} This instance.
     */
    updateSlice( sDevName, nValue )
    {
        if( ! this.chart || ! this.chart.data || ! this.chart.data.datasets ||
            this.chart.data.datasets.length === 0 )
        {
            console.error( "Pie chart not initialized" );
            return this;
        }

        let nIndex = this._findSliceIndex_( sDevName );

        if( nIndex === -1 )
        {
            console.error( sDevName + ": slice does not exist" );
            return this;
        }

        this.chart.data.datasets[0].data[nIndex] = parseFloat( nValue );
        this.chart.update();

        return this;
    }

    destroy()
    {
        let id = this.parent.id;

        PieWrap._aInstances_ = PieWrap._aInstances_.filter( item => item.id !== id );

        if( this.chart )
        {
            this.chart.destroy();
            this.chart = null;
        }

        if( this.canvas )
            this.canvas = null;

        return this;
    }

    show( isOngoing = false )
    {
        if( isOngoing )
            return this;

        let $container = $(this.parent.getContentArea());

        $container.empty()
                  .append( this._getChartDiv_( $container.width(), $container.height() ) );

        let $canvas = $('#'+this.divId).find('canvas').first();

        if( $canvas.length === 0 )
        {
            console.error( "Canvas element not found in pie chart div" );
            return this;
        }

        this.canvas = $canvas[0];

        let aLabels = [];
        let aData   = [];
        let aColors = [];

        // Check if slices are configured; if not, show demo data
        if( p_base.isEmpty( this.parent.slices ) )
        {
            // Demo data with random values and colors
            aLabels = [ 'Category A', 'Category B', 'Category C', 'Category D', 'Category E' ];
            aData   = [ Math.random() * 100 + 10,
                        Math.random() * 100 + 10,
                        Math.random() * 100 + 10,
                        Math.random() * 100 + 10,
                        Math.random() * 100 + 10 ];
            aColors = [ this._randomColor_(),
                        this._randomColor_(),
                        this._randomColor_(),
                        this._randomColor_(),
                        this._randomColor_() ];
        }
        else
        {
            for( const slice of this.parent.slices )
            {
                aLabels.push( slice.label || slice.name );
                aData.push( 0 );    // Initial value, will be updated by listeners
                aColors.push( slice.color || this._randomColor_() );
            }
        }

        if( this.chart )
            this.chart.destroy();

        let context = this.canvas.getContext( '2d' );

        if( ! context )
        {
            console.error( "Failed to get canvas context" );
            return this;
        }

        this.chart = new Chart( context,
                                {
                                    type   : this.parent.doughnut ? "doughnut" : "pie",
                                    data   : {
                                                labels  : aLabels,
                                                datasets: [{
                                                    data           : aData,
                                                    backgroundColor: aColors,
                                                    borderColor    : aColors.map( c => this._darkenColor_( c, 20 ) ),
                                                    borderWidth    : 1
                                                }]
                                             },
                                    options: this._getChartOptions_()
                                } );

        return this;
    }

    //------------------------------------------------------------------------//
    // PRIVATE METHODS

    _findSliceIndex_( sDevName )
    {
        for( let n = 0; n < this.parent.slices.length; n++ )
        {
            if( this.parent.slices[n].name === sDevName )
                return n;
        }

        return -1;
    }

    _getChartDiv_( nWidth, nHeight )
    {
        return '<div id="'+ this.divId +'" style="width:100%; height:100%;">'+
                   '<canvas'+
                       ' width ="'+ parseInt( nWidth  ) +'"'+
                       ' height="'+ parseInt( nHeight ) +'">'+
                   '</canvas>'+
               '</div>';
    }

    _getChartOptions_()
    {
        let lbl_clr  = p_base.isEmpty( this.parent.lbl_clr ) ? 'black' : this.parent.lbl_clr;
        let lgnd_pos = p_base.isEmpty( this.parent.lgnd_pos ) ? 'right' : this.parent.lgnd_pos;

        let options =
            {
                responsive         : true,
                maintainAspectRatio: false,
                animation          : { duration: 500 },
                plugins            : {
                                        legend:
                                        {
                                            display : this.parent.show_lgnd,
                                            position: lgnd_pos,
                                            labels  : { color: lbl_clr }
                                        },
                                        tooltip:
                                        {
                                            enabled        : true,
                                            backgroundColor: 'rgba(0, 0, 0, 0.8)',
                                            titleColor     : '#fff',
                                            bodyColor      : '#fff',
                                            borderColor    : lbl_clr,
                                            borderWidth    : 1,
                                            displayColors  : true
                                        }
                                     }
            };

        return options;
    }

    /**
     * Generates a random hex color.
     *
     * @returns {String} Random hex color (e.g., "#a1b2c3")
     */
    _randomColor_()
    {
        return '#' + Math.floor( Math.random() * 16777215 ).toString( 16 ).padStart( 6, '0' );
    }

    /**
     * Darkens a hex color by a percentage.
     *
     * @param {String} hex    Hex color (e.g., "#ff0000")
     * @param {Number} percent Percentage to darken (0-100)
     * @returns {String} Darkened hex color
     */
    _darkenColor_( hex, percent )
    {
        // Remove # if present
        hex = hex.replace( /^#/, '' );

        // Parse r, g, b
        let r = parseInt( hex.substring( 0, 2 ), 16 );
        let g = parseInt( hex.substring( 2, 4 ), 16 );
        let b = parseInt( hex.substring( 4, 6 ), 16 );

        // Darken
        r = Math.max( 0, Math.floor( r * (1 - percent / 100) ) );
        g = Math.max( 0, Math.floor( g * (1 - percent / 100) ) );
        b = Math.max( 0, Math.floor( b * (1 - percent / 100) ) );

        // Convert back to hex
        return '#' + ((1 << 24) + (r << 16) + (g << 8) + b).toString( 16 ).slice( 1 );
    }
}
