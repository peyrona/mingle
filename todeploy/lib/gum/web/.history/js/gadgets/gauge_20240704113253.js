
"use strict";

class GumGauge extends GumGadget
{
    constructor( props )
    {
        super( "gauge", props );

        if( p_base.isEmpty( props ) )
        {
            this.exen      = null;
            this.device    = null;
            this.wrapper   = null;     // instance of class GaugeWrap
            this.min       = 0;        // Min value
            this.max       = 100;      // Max value
            this.angle     = 0;
            this.radius    = 95;
            this.thick     = 38;       // Arc thickness
            this.zones     = [];       // Colored zones: { start : <num>, end: <num>, color: <str> }
            this.ticks     = true;     // Show ticks or not (Divisions: 6, Length: 50, Color: #333333, Width: 10) (Subdivisions: 3, Length: 30, Color: #666666, Width: 6)
            this.ticks_pos = [];       // Show ticks  values at their position
            this.decimals  = 0;        // Decimal digits for current device value
            this.canvas    = p_base.uuid();
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

        dlgGauge.setup( this,
                        (gauge) => self._update_( gauge ),
                        ()      => self._isEditing = false )
                .onTableChanged( (gadget) => self._update_(gadget) )
                .show();
    }

    destroy()
    {
        this.wrapper.del();

        return super.destroy();
    }

    show()
    {
        let sHTML = '<canvas id="'+ this.canvas +'"'+
                       ' width="' + this.$container.width()  +'"'+
                       ' height="'+ this.$container.height() +'"'+
                    '</canvas>';

        this.$container
            .empty()
            .append( sHTML );

        this.refresh();

        return this;
    }

    refresh( isOngoing = false )
    {
        $('#'+this.canvas).attr('width' , this.$container.width())
                          .attr('height', this.$container.height());

        if( this.wrapper )
            this.wrapper.del();

        this.wrapper = new GaugeWrap( this );   // Has to be recreated from zero: the lib I use does not allow to manipulate it after been created

        return this;
    }

    distill()
    {
        return super.distill( "wrapper" );
    }

    //-----------------------------------------------------------------------------------------//
    // PRIVATE SCOPE

    _update_( props )
    {
        super._update( props );

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
                        try
                        {
                            self.wrapper.set( devVal );
                        }
                        catch( error )
                        {
                            console.log( 'Gauge for "'+ self +'" error: '+ error );
                        }
                    };

            this._addListener( this.exen, this.device, fn );
        }
    }
}