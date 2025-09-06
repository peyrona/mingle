
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

    _updateListeners_()
    {
        this._resetListeners();

        if( p_base.isNotEmpty( this.devices ) )
        {
            let self = this;

            for( let item of this.devices )     // [ {exen:oExen, name:"devName", color:"#00ff00"}, ... ]
            {
                let fn = function( action, when, name, value )    // Better to have a function per device
                        {
                            if( self._getReadedCounter( action ) > 1 )
                                return;

                            if( p_base.isNumber( value ) )  self.wrapper.plot( name, when, value )
                            else                            p_app.info( 'Device "'+ name +'" is not Numeric; its value is: '+ value );
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

        let aoTargetSerie = this._findSerieData_( this.chart.data.datasets, sDS_Name );

        if( ! aoTargetSerie )
            throw sDS_Name + ": serie does not exist"

        let oDot = { when: nWhen,
                     x   : new Date( nWhen ).toLocaleTimeString(),
                     y   : nY = parseFloat( nY ) };   // parseFloat because it could be a string (no harm if nY is already a number)

        // ::aSeriesDots has all dots for all serires for all times
        // (used to copy some parts from ::aSeries to a certain serie)

        let aSeriesDots = this._findSerieData_( this.aSeries, sDS_Name );
            aSeriesDots.push( oDot );

        if( (aoTargetSerie.length > 0) && (nWhen < aoTargetSerie[0].when ) )   // When oDot.when is before the 1st dot shown in this serie, it is ignored
            return;

        aoTargetSerie.push( this._setLabel_( aoTargetSerie, oDot ) );

        if( (this.timeframe > 0) &&
            ((aoTargetSerie.last().when - aoTargetSerie[0].when ) > this.timeframe) )
        {
            aoTargetSerie.shift();
        }

        if( ! this.isPaused )
            this.chart.update();

        return this;
    }

    bulk( aSeries )
    {
        for( let s = 0; s < aSeries.length; s++ )
        {
            let aDots = new Array( aSeries[s].data.length );

            for( let p = 0; p < aSeries[s].data.length; p++ )
                aDots[p] = { when: aSeries[s].data[p][0], x: new Date( aSeries[s].data[p][0] ).toLocaleTimeString(), y: aSeries[s].data[p][1] };

            this.chart.config.data.datasets.push( { label: aSeries[s].label, data: aDots } );
        }

        this.chart.update();
    }

    clear()   // Implemented in this way to keep (to not loose) the references to the arrays and the rest of the configuration
    {
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
            this.chart.destroy();

        return this;
    }

    show()
    {
        let $container = $(this.parent.getContainer());

        $container.empty()
                  .append( this._getChartDiv_( $container.width(), $container.height() - 75 ) );

        this.canvas = $('#'+this.divId).find('canvas')
                                       .first()[0];     // Now that div (which contains a canvas is appended, a reference to it can be assigned)

        // TODO: no se ve el tooltip y además da un error -->
        // let $tooltip = $container.tooltip( { content: 'Mouse wheel can be used to zoom in and out' } );
        //     $tooltip.tooltip('open');
        //     setInterval( () => $tooltip.tooltip('close'), 5000 );

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

        if( this.chart !== null )
            this.chart.destroy();

        this.chart = new Chart( this.canvas.getContext('2d'),
                                { type   : "line",
                                  data   : { labels: [], datasets: aDataset },    // 'labels' are the labels that appear at the X (the times as strings)
                                  options: this._getChartOptions_() } );

        let isDbPanelVisible = p_base.isNotEmpty( this.parent.db_jars ) &&
                               p_base.isNotEmpty( this.parent.db_jdbc ) &&
                               p_base.isNotEmpty( this.parent.db_url  );

        let $div = $('#'+this.divId +' [name="chart_data_load_panel"]');

        $div[0].style.visibility = (isDbPanelVisible ? "visible" : "hidden");     // Shows or hides these controls: time-ini, time-end and button Load/Live
    }

    //------------------------------------------------------------------------//
    // SI JS LO PERMITIESE, DE AQUI EN ADELANTE SERIAN PRIVATE

    _setLabel_( aoSerieDots, oDot )
    {
        function findClosest( serie, target )
        {
            return serie.reduce( (prev, curr) =>
                                  Math.abs( curr.when - target ) < Math.abs( prev - target.when ) ? curr : prev );
        }

        let oLastShown = aoSerieDots.last();

        if( aoSerieDots.length === 0 )
        {
            this.chart.data.labels.push( oDot.x );
            return oDot;
        }

        // Current one is after last one at least 100 millis

        if( Math.abs( oDot.when - oLastShown.when ) > 100 )
        {
            this.chart.data.labels.push( oDot.x );
            return oDot;
        }

        // Let's find the coloset exiting label for receviced oDot

        let nNewWhen = findClosest( aoSerieDots, oDot.when );

        oDot.x = nNewWhen.x;

        return oDot;
    }

    _zoom_( event )
    {
        event.preventDefault();

        let nMinWhen = this._getMinShownWhen_();
        let nMaxWhen = this._getMaxShownWhen_();
        let nNowWhen = nMaxWhen - nMinWhen;

        if( nMinWhen === -1 || nMaxWhen === -1 )    // means there are no points being shown
            return;

        const nScale = 0.50;

        let bTitleY   = this.chart.options.scales.y.title.display;          // Para descontar el ancho de la escala a la izq t el título si lo tuviera
        let offset    = $(this.canvas).offset();
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
            if( dataset.data.length > 0 )
                max = Math.max( max, dataset.data.last().when );

        return max;
    }

    /**
     * Devuelve el dataset que corresponde a la etiqueta es la pasada o null si no
     * corresponde a ninguno.
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

            let dataSet = this.chart.config.data.datasets[nDS];
                dataSet.data.length = 0;
                dataSet.data.push( ...aDot2Show );
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
        let wrapper = ChartWrap._aInstances_.find( item => item.id === divId4Chart ).chart;
        let sValue  = $('#'+divId4Chart+' [name="txtTimeFrame"]').val();
        let sUnit   = $('#'+divId4Chart+' [name="lstTimeFrameUnit"]').val();
        let nValue  = parseInt( sValue );

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

    /**
     * Invokes a web service to retrieve data from a DB
     *
     * @param {String} divId4Chart
     */
    static _loadFromDB_( divId4Chart )
    {
        let $btnLoadOrLive = $('#'+divId4Chart+' [name="btnLoadOrLive"]');                      // Can have one of these 2 labels: "Load" or "Live"
        let props          = ChartWrap._aInstances_.find( item => item.id === divId4Chart ).chart.props;

        if( $btnLoadOrLive.text() === "Live" )     // Is showing stored data?
        {
            $btnLoadOrLive.text('Load');           // Go to 'Live' mode and show 'Live' in btn label
            props.chart.clear();
            props.chart.setPaused( false );
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

        if( p_base.isEmpty( props.db_jars ) || p_base.isEmpty( props.db_jdbc ) || p_base.isEmpty(  props.db_url ) || p_base.isEmpty( props.db_tables ) )
        {
            p_app.alert( "All DB information is mandatory except user name and user password" );
            return;
        }

        let uid   = p_app.showLoading( props.chart.canvas );
        let oData = { from: dFrom.getTime(), to: dTo.getTime(),
                      jars: props.db_jars, jdbc: props.db_jdbc, url: props.db_url,
                      user: props.db_user, pwd : props.db_pwd,  tables: JSON.stringify( props.db_tables ) };

        $.ajax( {
                    url     : '/ws/db',
                    type    : "GET",
                    data    : oData,
                    error   : (xhr ) => p_app.showAjaxError( xhr ),
                    complete: () => p_app.hideLoading( uid ),
                    success : (data) => {
                                            $btnLoadOrLive.text('Live');
                                            props.chart.setPaused( true );
                                            props.chart.clear();
                                            props.chart.bulk( data );
                                        }
                } );
    }
}