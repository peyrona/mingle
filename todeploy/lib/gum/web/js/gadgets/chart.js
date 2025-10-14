
"use strict";

class GumChart extends GumGadget
{
    constructor( props )
    {
        super( "chart", props );

        if( p_base.isEmpty( props ) )   // canvas is embeded inside container div
        {
            this.width  = gum.isUsingFreeLayout() ? 480 : 0;     // Rewritten from parent
            this.height = gum.isUsingFreeLayout() ? 320 : 0;     // Rewritten from parent

            this.devices   = [];        // [ {exen:oExen, name:"devName", color:"#00ff00"}, ... ]
            this.wrapper   = null;      // An instance of ChartWrap (the ChartWrap instance contains a reference to the GumChart instance)
            this.x_title   = null;
            this.y_title   = null;
            this.lbl_clr   = null;      // For X and Y titles and X and Y ticks (values)
            this.time_zone = null;
            this.db_jars   = null;      // From here to the end there is the JDBC Driver information to retrieve data from a DB
            this.db_jdbc   = null;
            this.db_url    = null;
            this.db_user   = null;
            this.db_pwd    = null;
            this.db_tables = [];        // Must be an empty array.  [ {table:"name", timestamp:"col_name", values:"col_name"}, ... ]
        }

        this.wrapper = new ChartWrap( this );
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

            dlgChart.setup( this,                   // Passing a reference, this will be updated by dlgChart(...) module
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
            return;

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

        if( p_base.isNotEmpty( this.devices ) )
        {
            let self = this;

            for( let item of this.devices )     // [ {exen:oExen, name:"devName", color:"#00ff00"}, ... ]
            {
                let fn = function( action, payload )   // Better to have a function per device
                    {
                        if( self._isProperValue( "Number", payload.value, payload.name ) )
                        {
                            self._hasErrors( false );
                            self.wrapper.plot( payload.name, payload.when, payload.value );
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
//------------------------------------------------------------------------------------------------------------------------------------//


//-------------------------------------------------------------------------------------//
// Wrapper class for graphs whose X-axis (abscissa) is the time: dynamic
// and static.
//
// config:
// canvas : Canvas of the DOM to use (an id or a canvas object).
// measure: Y axis title (optional, default null)
// density: Dots per 1000 pixels of width of the graphic. Default: 60
// devices: Array of devices names: [“CelsiusInside”, “CelsiusOutside”, ...]
//-------------------------------------------------------------------------------------//

class ChartWrap
{
    static _aInstances_ = [];     // [ { id:<div_id>, chart:<instance> }, ... ]

    //----------------------------------------------------------------------------//

    constructor( parent )
    {
        if( ! (parent instanceof GumChart) )
            throw "Parent must be a GumChart";

        if( ! parent.id )
            parent.id = p_base.uuid();

        ChartWrap._aInstances_.push( { id: parent.id, chart: this } );

        this.parent    = parent;
        this.divId     = p_base.uuid();
        this.canvas    = null;
        this.chart     = null;
        this.aSeries   = [];      // All points received for all series --> [ { label: <sDeviceName>, data: [ {when:<nWhen>, x:<sWhen>, y:<nValue> }, ...] }, ... ]
        this.isPaused  = false;
        this.timeframe = 0;       // Milliseconds to be shown (for right-to-left scrolling). 0 == show all points
    }

    //----------------------------------------------------------------------------//
    // PUBLIC METHODS

    setPaused( b )
    {
        this.isPaused = b;
    }

     /**
      * Adds a dot to the graph and updates it: makes it visible.
      *
      * @param {String} sDS_Name Name of the dataset to add the point to.
      * @param {String} nWhen Value of the abcissa (in millisecs)
      * @param {Number} nY Ordinate value
      * @returns {ChartWrap} This instance.
      */
    plot( sDS_Name, nWhen, nY )
    {
    // console.log( sDS_Name +' : '+ new Date( nWhen ).toLocaleTimeString() +' , '+ nY );

        if( ! this.chart || ! this.chart.data )
        {
            console.error( "Chart not initialized" );
            return this;
        }

        let aoTargetSerie = this._findSerieData_( this.chart.data.datasets, sDS_Name );
        let aSeriesDots   = this._findSerieData_( this.aSeries, sDS_Name );

        if( ! aoTargetSerie )
            throw sDS_Name + ": serie does not exist"

        if( ! aSeriesDots )
            throw sDS_Name + ": serie does not exist in series data"

        let nValue = parseFloat( nY );   // parseFloat because it could be a string (no harm if nY is already a number)
        let oDot = { when: nWhen,
                     x   : new Date( nWhen ).toLocaleTimeString(),
                     y   : nValue };

        // ::aSeriesDots has all dots for all serires for all times
        // (used to copy some parts from ::aSeries to a certain serie)
            aSeriesDots.push( oDot );

        if( (aoTargetSerie.length > 0) && (nWhen < aoTargetSerie[0].when ) )   // When oDot.when is before the 1st dot shown in this serie, it is ignored
            return this;

        aoTargetSerie.push( this._setLabel_( aoTargetSerie, oDot ) );

        if( (this.timeframe > 0) &&
            (aoTargetSerie.length > 1) &&
            ((aoTargetSerie[aoTargetSerie.length - 1].when - aoTargetSerie[0].when ) > this.timeframe) )
        {
            aoTargetSerie.shift();
        }

        if( ! this.isPaused && this.chart )
            this.chart.update();

        return this;
    }

    bulk( aSeries )
    {
        if( ! this.chart || ! this.chart.config || ! this.chart.config.data )
        {
            console.error("Chart not initialized for bulk operation");
            return this;
        }

        if( ! aSeries || ! Array.isArray(aSeries) )
        {
            console.error("Invalid series data for bulk operation");
            return this;
        }

        for( let s = 0; s < aSeries.length; s++ )
        {
            if( !aSeries[s] || !aSeries[s].data || !Array.isArray(aSeries[s].data) )
            {
                console.error("Invalid series data at index", s);
                continue;
            }

            let aDots = new Array( aSeries[s].data.length );

            for( let p = 0; p < aSeries[s].data.length; p++ )
                aDots[p] = { when: aSeries[s].data[p][0], x: new Date( aSeries[s].data[p][0] ).toLocaleTimeString(), y: aSeries[s].data[p][1] };

            this.chart.config.data.datasets.push( { label: aSeries[s].label, data: aDots } );
        }

        this.chart.update();
    }

    clear()   // Implemented in this way to keep (to not loose) the references to the arrays and the rest of the configuration
    {
        if( ! this.chart || ! this.chart.config || ! this.chart.config.data )
        {
            console.error("Chart not initialized for clear operation");
            return this;
        }

        this.chart.config.data.labels.length = 0;

        for( const dataset of this.chart.config.data.datasets )
            dataset.data.length = 0;

        this.chart.update();
    }

    destroy()
    {
        let id = this.parent.id;

        ChartWrap._aInstances_ = ChartWrap._aInstances_.filter( item => item.id !== id );    // deletes array item

        if( this.chart )
        {
            this.chart.destroy();
            this.chart = null;
        }

        if( this.canvas )
        {
            this.canvas.onwheel = null;
            this.canvas = null;
        }

        return this;
    }

    show( isOngoing = false )
    {
        if( isOngoing )
            return this;

        let $container = $(this.parent.getContainer());

        $container.empty()
                  .append( this._getChartDiv_( $container.width(), $container.height() - 75 ) );

        this.canvas = $('#'+this.divId).find('canvas')
                                       .first()[0];     // Now that div (which contains a canvas is appended, a reference to it can be assigned)

        let self = this;

        this.canvas.onwheel = (evt) => self._zoom_( evt );

        let aDataset = [];

        for( const device of this.parent.devices )
        {
            this.aSeries.push( { label: device.name, data: [] } );

            aDataset.push( { label          : device.name,
                             borderColor    : device.color,
                             backgroundColor: device.color,
                             fill           : false,
                             lineTension    : 0.4,
                             data           : [] } );
        }

        if( this.chart )
            this.chart.destroy();

        let context = this.canvas.getContext('2d');

        if( ! context )
        {
            console.error("Failed to get canvas context");
            return this;
        }

        this.chart = new Chart( context,
                                { type   : "line",
                                  data   : { labels: [], datasets: aDataset },    // 'labels' are the labels that appear at the X (the times as strings)
                                  options: this._getChartOptions_() } );

        let isDbPanelVisible = p_base.isNotEmpty( this.parent.db_jars ) &&
                               p_base.isNotEmpty( this.parent.db_jdbc ) &&
                               p_base.isNotEmpty( this.parent.db_url  );

        let $div = $('#'+this.divId +' [name="chart_data_load_panel"]');

        if( $div.length > 0 )
            $div[0].style.visibility = (isDbPanelVisible ? "visible" : "hidden");     // Shows or hides these controls: time-ini, time-end and button Load/Live

        return this;
    }

    //------------------------------------------------------------------------//
    // SI JS LO PERMITIESE, DE AQUI EN ADELANTE SERIAN PRIVATE

    _setLabel_( aoSerieDots, oDot )
    {
        function findClosest( serie, target )
        {
            return serie.reduce( (prev, curr) =>
                                  Math.abs( curr.when - target ) < Math.abs( prev.when - target ) ? curr : prev );
        }

        if( ! aoSerieDots || aoSerieDots.length === 0 )
        {
            if( this.chart && this.chart.data && this.chart.data.labels )
                this.chart.data.labels.push( oDot.x );

            return oDot;
        }

        let oLastShown = aoSerieDots[aoSerieDots.length - 1];

        if( ! oLastShown )
        {
            if( this.chart && this.chart.data && this.chart.data.labels )
                this.chart.data.labels.push( oDot.x );

            return oDot;
        }

        // Current one is after last one at least 100 millis

        if( Math.abs( oDot.when - oLastShown.when ) > 100 )
        {
            if( this.chart && this.chart.data && this.chart.data.labels )
                this.chart.data.labels.push( oDot.x );

            return oDot;
        }

        // Let's find the coloset exiting label for recevived oDot

        let nNewWhen = findClosest( aoSerieDots, oDot.when );

        if( nNewWhen && nNewWhen.x )
            oDot.x = nNewWhen.x;

        return oDot;
    }

    _zoom_( event )
    {
        event.preventDefault();

        if( !this.chart || !this.chart.options || !this.canvas ) {
            console.error("Chart not initialized for zoom operation");
            return;
        }

        let nMinWhen = this._getMinShownWhen_();
        let nMaxWhen = this._getMaxShownWhen_();
        let nNowWhen = nMaxWhen - nMinWhen;

        if( nMinWhen === -1 || nMaxWhen === -1 )    // means there are no points being shown
            return;

        const nScale = 0.50;

        let bTitleY   = this.chart.options.scales.y.title.display;          // Para descontar el ancho de la escala a la izq t el título si lo tuviera
        let offset    = $(this.canvas).offset();
        if( !offset ) {
            console.error("Failed to get canvas offset for zoom");
            return;
        }
        let offsetX   = event.pageX - offset.left - (bTitleY ? 80 : 60);
        let xPercent  = offsetX / this.canvas.width;                        // Porcentaje de desplazamiento del puntero de ratón en la X con respecto al origen de coordenadas
            xPercent  = p_base.setBetween( 0, xPercent, 1 );

        let nCursor   = nMinWhen + ((nMaxWhen - nMinWhen) * xPercent);
        let nLateWhen = (event.deltaY < 0) ? (nNowWhen / (1+ nScale)) : (nNowWhen * (1+ nScale));

        let nBegin    = parseInt( nCursor - (nLateWhen * xPercent) );
        let nEnd      = parseInt( nBegin + nLateWhen );

        this._filter_( nBegin, nEnd );
        this.chart.update();

        // console.log( "min  ="+ new Date( nMinWhen ).toLocaleTimeString() , "  max="+ new Date( nMaxWhen ).toLocaleTimeString() );
        // console.log( "begin="+ new Date( nBegin   ).toLocaleTimeString() , "  end="+ new Date( nEnd     ).toLocaleTimeString() );
    }

    _getMinShownWhen_()
    {
        if( this.chart.config.data.datasets.length === 0 )
            return -1;

        let min = Date.now();   // millis

        for( const dataset of this.chart.config.data.datasets )
            if( dataset.data.length > 0 )
                min = Math.min( min, dataset.data[0].when );

        return min;
    }

    _getMaxShownWhen_()
    {
        let max = -1;

        for( const dataset of this.chart.config.data.datasets )
            if( dataset.data.length > 0 && dataset.data[dataset.data.length - 1] )
                max = Math.max( max, dataset.data[dataset.data.length - 1].when );

        return max;
    }

    /**
     * Returns the dataset corresponding with passed label or throws an error if none.
     *
     * @param {Array} aoData Where to search (array of objects).
     * @param {String} sLabel La etiqueta del data set q sequiere obtener.
     * @returns {Array} El array de datos que corresponde a la etiqueta recibida.
     */
    _findSerieData_( aoData, sLabel )
    {
        for( const oData of aoData )
        {
            if( oData.label === sLabel )
                return oData.data;
        }

        throw "Serie not found: this should not happen";
    }

    // Recorro todos los DataSets del histórico y con esos puntos, actualizo
    // los DataSets que están visibles (los del Chart(...))
    _filter_( nWhenFrom, nWhenTo )
    {
        for( let nDS = 0; nDS < this.aSeries.length; nDS++ )
        {
            let aDot2Show = [];   // Array to be filled with proper points from ::aHistory

            for( const dot of this.aSeries[nDS].data )
            {
                if( dot.when >= nWhenFrom &&
                    dot.when <= nWhenTo )
                {
                    aDot2Show.push( dot );
                }
            }

            if( nDS < this.chart.config.data.datasets.length )
            {
                let dataSet = this.chart.config.data.datasets[nDS];

                dataSet.data.length = 0;
                dataSet.data.push( ...aDot2Show );
            }
        }
    }

    // All, canvas and time-frame controls at bottom right
    _getChartDiv_( nWidth, nHeight )
    {
        return '<div id="'+ this.divId +'">'+
                        '<div>'+                       // Chart.js can not share the div where canvas is with any other element: chart goes crazy
                            '<canvas'+
                                ' width ="'+ parseInt( nWidth  ) +'"'+
                                ' height="'+ parseInt( nHeight ) +'">'+
                            '</canvas>'+
                        '</div>'+
                        '<div class="columns">'+       // div for load data and for select time frame
                            '<div class="column is-half is-narrow" name="chart_data_load_panel" style="visibility:hidden">'+
                                '<label class="label is-small">Show historic</label>'+
                                '<div class="field is-horizontal">'+
                                    '<div class="field-body pl-5">'+
                                        '<label class="label is-small pr-3">From</label>'+
                                        '<div class="field">'+
                                            '<p class="control">'+
                                                '<input class="input is-small" type="datetime-local" name="from_date">'+
                                            '</p>'+
                                        '</div>'+
                                    '</div>'+
                                    '<div class="field-body pl-5">'+
                                        '<label class="label is-small pr-3">To</label>'+
                                        '<div class="field">'+
                                            '<p class="control">'+
                                                '<input class="input is-small" type="datetime-local" name="to_date">'+
                                            '</p>'+
                                        '</div>'+
                                    '</div>'+
                                    '<p class="pl-5 pr-5">'+
                                        '<button class="button is-small is-info" name="btnLoadOrLive" onclick="ChartWrap._loadFromDB_(\''+ this.parent.id +'\')">Load</button>'+
                                    '</p>'+
                                '</div>'+
                            '</div>'+
                            '<div class="column is-half is-narrow pr-5">'+
                                '<div class="field">'+
                                    '<div class="field-label is-small is-pulled-right">'+
                                        '<label class="label">Show only last... (0 for all)</label>'+
                                    '</div>'+
                                    '<br>'+
                                    '<div class="field-body is-pulled-right">'+
                                        '<div class="field has-addons has-addons-centered">'+
                                            '<p class="control">'+
                                                '<input class="input is-small" type="number" name="txtTimeFrame" value="0" min="0" style="width:9em;" onchange="ChartWrap._setTimeFrame_(\''+ this.parent.id +'\')">'+
                                            '</p>'+
                                            '<p class="control">'+
                                                '<span class="select is-small">'+
                                                    '<select name="lstTimeFrameUnit" onchange="ChartWrap._setTimeFrame_(\''+ this.parent.id +'\')">'+
                                                        '<option>mins</option>'+
                                                        '<option>hours</option>'+
                                                        '<option>days</option>'+
                                                    '</select>'+
                                                '</span>'+
                                            '</p>'+
                                        '</div>'+
                                    '</div>'+
                                '</div>'+
                            '</div>'+
                        '</div>'+
                    '</div>';
    }

    _getChartOptions_()
    {
        let x_title = p_base.isEmpty( this.parent.x_title ) ? null    : this.parent.x_title.trim();
        let y_title = p_base.isEmpty( this.parent.y_title ) ? null    : this.parent.y_title.trim();
        let lbl_clr = p_base.isEmpty( this.parent.lbl_clr ) ? 'black' : this.parent.lbl_clr;

        let options =
            {
                responsive: true,
                maintainAspectRatio: false,
                animation : { duration: 0 },
                scales    : {
                                x:  {
                                        display: true,
                                        grid   : {
                                                    display: false
                                                 },
                                        title  : {
                                                    text   : x_title,
                                                    display: (x_title !== null),
                                                    color  : lbl_clr
                                                 },
                                        ticks:   {
                                                    color: lbl_clr
                                                 },
                                        /*type   : 'time',
                                        time   : {
                                                   unit: 'second',
                                                   displayFormats: { second: 'HH:mm:ss' }
                                                 }*/
                                    },
                                y:  {
                                        display: true,
                                        grid   : {
                                                    display: true
                                                 },
                                        title  : {
                                                    text   : y_title,
                                                    display: (y_title !== null),
                                                    color  : lbl_clr
                                                 },
                                        ticks:   {
                                                    color: lbl_clr
                                                 }
                                    }
                            },
                plugins   : {
                                legend:
                                {
                                  display: true,
                                  labels: { color: lbl_clr }   // Set the label color to blue
                                },
                                tooltip:
                                {
                                    enabled: true,
                                    backgroundColor: 'rgba(0, 0, 0, 0.8)',
                                    titleColor: '#fff',
                                    bodyColor: '#fff',
                                    borderColor: lbl_clr,
                                    borderWidth: 1,
                                    displayColors: true,
                                    callbacks:
                                    {
                                        title: function(context)
                                        {
                                            return context[0].dataset.label;
                                        },
                                        label: function(context)
                                        {
                                            let dataPoint = context.raw;
                                            let timestamp = new Date(dataPoint.when).toLocaleString();
                                            let value = dataPoint.y;
                                            return ['Time: ' + timestamp, 'Value: ' + value, '', 'Mouse wheel can be used to zoom in and out'];
                                        }
                                    }
                                }
                            }
            };

        return options;
    }

    //---------------------------------------------------------------------------------//
    // PRIVATE AND STATIC

    /**
     * Removes all dots that  are out of scope: selected by the user using a number and a
     * time unit (mins, hours, days); using the controls at the graph right-bottom corner.
     *
     * @param {String} divId4Chart
     */
    static _setTimeFrame_( divId4Chart )
    {
        let instance = ChartWrap._aInstances_.find( item => item.id === divId4Chart );

        if( ! instance )
            return;

        let wrapper = instance.chart;
        let sValue  = $('#'+divId4Chart+' [name="txtTimeFrame"]').val();
        let sUnit   = $('#'+divId4Chart+' [name="lstTimeFrameUnit"]').val();
        let nValue  = parseInt( sValue );

        if( isNaN(nValue) || nValue < 0 ) {
            console.error("Invalid timeframe value");
            return;
        }

        if( nValue === 0 )
            return;

        switch( sUnit )
        {
            case "mins" : wrapper.timeframe = nValue *           60 * 1000; break;
            case "hours": wrapper.timeframe = nValue *      60 * 60 * 1000; break;
            case "days" : wrapper.timeframe = nValue * 24 * 60 * 60 * 1000; break;
        }

        let now      = new Date().getTime();
        let n1stWhen = (wrapper.timeframe === 0) ? 0 : (now - wrapper.timeframe);    // 1st point to be shown

        wrapper._filter_( n1stWhen, now + 9999 );   // 9999 is any moment in the future
        wrapper.chart.update();
    }

    // FIXME: comprobar que esta func funciona.
    /**
     * Invokes a web service to retrieve data from a DB
     *
     * @param {String} divId4Chart
     */
    static _loadFromDB_( divId4Chart )
    {
        let $btnLoadOrLive = $('#'+divId4Chart+' [name="btnLoadOrLive"]');                      // Can have one of these 2 labels: "Load" or "Live"
        let instance       = ChartWrap._aInstances_.find( item => item.id === divId4Chart );

        if( ! instance )
            return;

        let wrapper = instance.chart;
        let gadget  = wrapper.parent;

        if( $btnLoadOrLive.text() === "Live" )     // Is showing stored data?
        {
            $btnLoadOrLive.text('Load');           // Go to 'Live' mode and show 'Live' in btn label
            wrapper.clear();
            wrapper.setPaused( false );
            return;
        }

        let sFrom = $('#'+divId4Chart+' [name="from_date"]').val();
        let sTo   = $('#'+divId4Chart+' [name="to_date"]'  ).val();

        if( p_base.isEmpty( sFrom ) || p_base.isEmpty( sTo ) )
        {
            p_app.alert( "'From' and 'To': none can be empty" );
            return;
        }

        let dFrom = new Date( sFrom );
        let dTo   = new Date( sTo   );

        if( dFrom.getTime() > dTo.getTime() )
        {
            p_app.alert( "'To' can not be after 'From'" );
            return;
        }

        if( p_base.isEmpty( gadget.db_jars ) || p_base.isEmpty( gadget.db_jdbc ) || p_base.isEmpty(  gadget.db_url ) || p_base.isEmpty( gadget.db_tables ) )
        {
            p_app.alert( "All DB information is mandatory except user name and user password" );
            return;
        }

        let uid   = p_app.showLoading( wrapper.canvas );
        let oData = { from: dFrom.getTime(), to: dTo.getTime(),
                        jars: gadget.db_jars, jdbc: gadget.db_jdbc, url: gadget.db_url,
                        user: gadget.db_user, pwd : gadget.db_pwd,  tables: JSON.stringify( gadget.db_tables ) };

       $.ajax( {
                   url     : '/ws/db',
                   type    : "GET",
                   data    : oData,
                   error   : (xhr ) => p_app.showAjaxError( xhr ),
                   complete: () => p_app.hideLoading( uid ),
                   success : (data) => {
                                           $btnLoadOrLive.text('Live');
                                           wrapper.setPaused( true );
                                           wrapper.clear();
                                           wrapper.bulk( data );
                                       }
               } );
    }
}