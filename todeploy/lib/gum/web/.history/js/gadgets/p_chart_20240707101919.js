//-------------------------------------------------------------------------------------//
// Clase envoltoria para gráficos cuyo eje X (abcisa) es el tiempo: dinámicos
// y estáticos.
//
// config:
//      canvas : Canvas del DOM a utilizar (un id o un objeto canvas)
//      measure: Título del eje Y (opcional, defecto null)
//      density: Puntos por cada 1000 pixels de ancho del gráfico. Defecto: 60
//      devices: Array de nombres de devices: ["CelsiusInside", "CelsiusOutside", ...]
//-------------------------------------------------------------------------------------//

/* global p_base, p_app */

"use strict";

class ChartWrap
{
    static _aInstances_ = [];     // [ { id:<div_id>, chart:<instance> }, ... ]

    //----------------------------------------------------------------------------//

    constructor( parent )
    {
        ChartWrap._aInstances_.push( { id: parent.id, chart: this } );

        this.parent    = parent;
        this.canvas    = null;
        this.chart     = null;
        this.aReceived = [];      // All points received --> [ { label: <sDeviceName>, data: [ {when:<nWhen>, x:<sWhen> ,y:<nValue> }, ...] }, ... ]
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
     * Añade un punto al gráfico y lo actualiza: lo hace vivible.
     *
     * @param {String} sDevice Nombre del dataset en al que añadir el punto
     * @param {String} nWhen Valor de la abcisa (in millisecs)
     * @param {Number} nY Valor de la ordenada
     * @returns {GraphTimeLine.prototype} this
     */
    plot( sDevice, nWhen, nY )
    {
        let oPoint = { when: nWhen,
                       x   : new Date( nWhen ).toLocaleTimeString(),
                       y   : nY = parseFloat( nY ) };   // parseFloat because it could be a string (no harm if nY is already a number)

        let aDataAll = this._findSerieData_( this.aReceived, sDevice, true );
            aDataAll.push( oPoint );

        let aDataShown = this._findSerieData_( this.chart.config.data.datasets, sDevice );
            aDataShown.push( oPoint );

        //////////////////////////////////////////////////////
        // Quitar esto cuando corrijan el bug en chart.js
        aDataShown.push( oPoint );

        if( ! this.isPaused )
            this.chart.update();
        //////////////////////////////////////////////////////

        if( (this.timeframe > 0) && ((aDataShown.last().when - aDataShown[0].when ) > this.timeframe) )
        {
            aDataShown.shift();
        }

        //////////////////////////////////////////////////////
        aDataShown.pop();
        //////////////////////////////////////////////////////

        if( ! this.isPaused )
            this.chart.update();

        return this;
    }

    bulk( aSeries )
    {
        for( let s = 0; s < aSeries.length; s++ )
        {
            let aPoints = new Array( aSeries[s].data.length );

            for( let p = 0; p < aSeries[s].data.length; p++ )
                aPoints[p] = { when: aSeries[s].data[p][0], x: new Date( aSeries[s].data[p][0] ).toLocaleTimeString(), y: aSeries[s].data[p][1] };

            this.chart.config.data.datasets.push( { label: aSeries[s].label, data: aPoints } );
        }

        this.chart.update();
    }

    clear()   // Implemented in this way to keep (to not loose) the references to the arrays and the rest of the configuration
    {
        this.chart.config.data.labels.length = 0;

        for( let n = 0; n < this.chart.config.data.datasets.length; n++ )
            this.chart.config.data.datasets[n].data.length = 0;

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

    showIn( container )
    {
        let $container = $(container);

        let sHTML = '<div id="'+ this.parent.id +'">'+
                        '<div>'+                       // Chart.js can not share the div where canvas is with any other element: chart goes crazy
                            '<canvas'+
                                ' width ="'+ parseInt( $container.width() ) +'"'+
                                ' height="'+ parseInt( $container.height() - 75 ) +'">'+
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

        $container.empty().append( sHTML );

        // TODO: no se ve el tooltip -->
        //        let $tooltip = $container.tooltip( { content: 'Mouse wheel can be used to zoom in and out' } );
        //            $tooltip.tooltip('open');
        //            setInterval( () => $tooltip.tooltip('close'), 5000 );

        let self = this;

        this.canvas = $('#'+this.parent.id).find('canvas').first()[0];
        this.canvas.onwheel = (evt) => self._zoom_( evt );

        let x_title = p_base.isEmpty( this.parent.x_title     ) ? null    : this.parent.x_title.trim();
        let y_title = p_base.isEmpty( this.parent.y_title     ) ? null    : this.parent.y_title.trim();
        let lbl_clr = p_base.isEmpty( this.parent.label_color ) ? 'black' : this.parent.label_color;

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
                                                 }
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
                            }
            };

        let aDataset = [];

        for( let n = 0; n < this.parent.devices.length; n++ )
        {
            this.aReceived.push( { label: this.parent.devices[n].name, data: [] } );

            aDataset.push( { label          : this.parent.devices[n].name,
                             borderColor    : this.parent.devices[n].color,
                             backgroundColor: this.parent.devices[n].color,
                             fill           : false,
                             lineTension    : 0.4,
                             data           : [] } );
        }

        if( this.chart )
            this.chart.destroy();

        this.chart = new Chart( this.canvas.getContext( "2d" ),
                                { type   : "line",
                                  data   : { datasets: aDataset },
                                  options: options } );

        let isDbPanelVisible = p_base.isNotEmpty( this.parent.db_jars ) &&
                               p_base.isNotEmpty( this.parent.db_jdbc ) &&
                               p_base.isNotEmpty( this.parent.db_url  );

        let $div = $('#'+this.parent.id+' [name="chart_data_load_panel"]');

        $div[0].style.visibility = (isDbPanelVisible ? "visible" : "hidden");     // Shows or hide these controls: time-ini, time-end and button Load/Live
    }

    //------------------------------------------------------------------------//
    // SI JS LO PERMITIESE, DE AQUI EN ADELANTE SERIAN PRIVATE

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

        for( let n = 0; n < this.chart.config.data.datasets.length; n++ )
            if( this.chart.config.data.datasets[n].data.length > 0 )
                min = Math.min( min, this.chart.config.data.datasets[n].data[0].when );

        return min;
    }

    _getMaxShownWhen_()
    {
        let max = -1;

        for( let n = 0; n < this.chart.config.data.datasets.length; n++ )
            if( this.chart.config.data.datasets[n].data.length > 0 )
                max = Math.max( max, this.chart.config.data.datasets[n].data.last().when );

        return max;
    }

    /**
     * Devuelve el dataset que corresponde a la etiqueta es la pasada o null si no
     * corresponde a ninguno.
     *
     * @param {Array} aData Where to search.
     * @param {String} sLabel La etiqueta del data set q sequiere obtener.
     * @returns {undefined} El array de datos que corresponde a la etiqueta es la pasada.
     */
    _findSerieData_( aData, sLabel )
    {
        for( let n = 0; n < aData.length; n++ )
        {
            if( aData[n].label === sLabel )
                return aData[n].data;
        }

        return null;
    }

    // Recorro todos los DataSets del histórico y con esos puntos, actualizo
    // los DataSets que están visibles (los del Chart(...))
    _filter_( nWhenFrom, nWhenTo )
    {
        for( let nDS = 0; nDS < this.aReceived.length; nDS++ )
        {
            let aPoint2Show = [];   // Array to be filled with proper points from ::aHistory

            for( let n = 0; n < this.aReceived[nDS].data.length; n++ )
            {
                if( this.aReceived[nDS].data[n].when >= nWhenFrom &&
                    this.aReceived[nDS].data[n].when <= nWhenTo )
                {
                    aPoint2Show.push( this.aReceived[nDS].data[n] );
                }
            }

            let dataSet = this.chart.config.data.datasets[nDS];
                dataSet.data.length = 0;
                dataSet.data.push( ...aPoint2Show );
        }
    }

    //---------------------------------------------------------------------------------//
    // PRIVATE STATIC

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
            p_app.alert( "'To' can not be later than 'From'" );
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
                    url     : p_base.doURL( '/ws/db' ),
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