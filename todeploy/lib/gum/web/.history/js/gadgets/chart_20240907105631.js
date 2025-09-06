
"use strict";

class GumChart extends GumGadget
{
    constructor( props )
    {
        super( "chart", props );

        if( p_base.isEmpty( props ) )   // canvas is embeded inside container div
        {
            this.devices   = [];      // [ {exen:oExen, name:"devName", color:"#00ff00"}, ... ]
            this.wrapper   = null;    // An instance of ChartWrap
            this.x_title   = null;
            this.y_title   = null;
            this.lbl_clr   = null;    // For X and Y titles and X and Y ticks (values)
            this.time_zone = null;
            this.db_jars   = null;    // From here to the end there is the JDBC Driver information to retrieve data from a DB
            this.db_jdbc   = null;
            this.db_url    = null;
            this.db_user   = null;
            this.db_pwd    = null;
            this.db_tables = [];      // Must be an empty array.  [ {table:"name", timestamp:"col_name", values:"col_name"}, ... ]
        }

        this.getContainer().attr('width' , 128 )     // Inital default size
                           .attr('height',  80 );

        this.wrapper = new ChartWrap( this );
    }

    //-----------------------------------------------------------------------------------------//

    /**
     * Shows a dialog to modify received gadget.
     */
    edit()
    {
        super.edit();

        let self = this;

        dlgChart.setup( this,                   // Passing a reference, this will be updated by dlgChart(...) module
                        () => self.show(),
                        () => self._isEditing = false )
                .onTableChanged( () => self.show() )
                .show();

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

    show()
    {
        this.wrapper
            .showIn( this.getContainer() );

        this._updateListeners_();

        return this;
    }

    refresh( isOngoing = false )
    {
        if( ! isOngoing )
            this.show();

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

    _updateListeners_()
    {
        this._delAllListeners();

        if( p_base.isNotEmpty( this.devices ) )
        {
            let self = this;

            for( let item of this.devices )     // [ {exen:oExen, name:"devName", color:"#00ff00"}, ... ]
                this._addListener( item.exen, item.name, (when, name, value) => self.wrapper.plot( name, when, value ) );
        }

        return this;
    }
}