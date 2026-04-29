
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

            this.devices   = [];        // [ {exen:oExen, name:"devName", accessor:null, label:null, color:"#00ff00"}, ... ]
            this.wrapper   = null;      // An instance of ChartWrap (the ChartWrap instance contains a reference to the GumChart instance)
            this.x_title   = null;
            this.y_title   = null;
            this.lbl_clr   = null;      // For X and Y titles and X and Y ticks (values)
            this.time_zone = null;
            this.ts_round  = 'millis';  // Timestamp rounding: 'millis', 'secs', 'mins', 'hours'
            this.db_jars   = null;      // From here to the end there is the JDBC Driver information to retrieve data from a DB
            this.db_jdbc   = null;
            this.db_url    = null;
            this.db_user   = null;
            this.db_pwd    = null;
            this.db_tables = [];        // Must be an empty array.  [ {table:"name", timestamp:"col_name", values:"col_name"}, ... ]
            this._chartUid = p_base.uuid();   // Stable ID for localStorage keys (persisted via distill)
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
            return this;

        this.wrapper.show();

        this._updateListeners_();

        super.show( isOngoing );

        // Apply lbl_clr to non-Chart.js elements (gadget title, template labels)
        this.wrapper._applyLabelColor_();

        return this;
    }

    /**
     * Returns a copy of this gatget properties (without uneeded properties).
     */
    distill()
    {
        let props = super.distill( ["wrapper"] );

        if( props.devices )
        {
            for( let dev of props.devices )
                delete dev._valueType_;
        }

        return props;
    }

    //-----------------------------------------------------------------------------------------//
    // PRIVATE SCOPE

    _roundTimestamp_( ts )
    {
        switch( this.ts_round )
        {
            case 'secs' : return Math.round( ts / 1000    ) * 1000;
            case 'mins' : return Math.round( ts / 60000   ) * 60000;
            case 'hours': return Math.round( ts / 3600000 ) * 3600000;
            default     : return ts;    // 'millis' or null/undefined — no rounding
        }
    }

    _updateListeners_()
    {
        this._resetListeners();

        if( p_base.isNotEmpty( this.devices ) )
        {
            let self = this;

            for( let item of this.devices )     // [ {exen:oExen, name:"devName", color:"#00ff00", accessor:null, label:null}, ... ]
            {
                let sLabel = item.label || (item.accessor ? item.name +'['+ item.accessor +']' : item.name);

                let fn = function( action, payload )   // Better to have a function per device
                    {
                        if( ! item._valueType_ )
                            item._valueType_ = self._detectValueType_( payload.value );

                        let xValue = self._resolveAccessor_( payload.value, item.accessor, payload.name );

                        if( xValue !== null &&
                            payload.name !== undefined && payload.when !== undefined &&
                            self._isValidValue( "Number", xValue, payload.name ) )
                        {
                            self._hasErrors( false );
                            self.wrapper.plot( sLabel, self._roundTimestamp_( payload.when ), xValue );
                        }

                        self._executeUserCode_( action, payload );
                    };

                this._addListener( item.exen, item.name, fn );
            }
        }

        return this;
    }
}

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
            throw new Error( "Parent must be a GumChart" );

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
            throw new Error( sDS_Name + ": serie does not exist" );

        if( ! aSeriesDots )
            throw new Error( sDS_Name + ": serie does not exist in series data" );

        let nValue = parseFloat( nY );   // parseFloat because it could be a string (no harm if nY is already a number)
        let oDot = { when: nWhen,
                     x   : nWhen,    // Use timestamp for time scale positioning
                     y   : nValue };

        // ::aSeriesDots has all dots for all serires for all times
        // (used to copy some parts from ::aSeries to a certain serie)
            aSeriesDots.push( oDot );

        // When oDot.when is before the 1st dot shown in this serie, it is ignored
        if( aoTargetSerie.length > 0 && aoTargetSerie[0] && aoTargetSerie[0].when !== undefined && nWhen < aoTargetSerie[0].when )
            return this;

        aoTargetSerie.push( oDot );

        let firstDot = aoTargetSerie[0];
        let lastDot  = aoTargetSerie[aoTargetSerie.length - 1];

        if( this.timeframe > 0 &&
            aoTargetSerie.length > 1 &&
            firstDot && lastDot &&
            firstDot.when !== undefined && lastDot.when !== undefined &&
            (lastDot.when - firstDot.when) > this.timeframe )
        {
            aoTargetSerie.shift();
        }

        if( ! this.isPaused && this.chart )
            this.chart.update();

        return this;
    }

    bulk( aSeries )
    {
        if( ! this.chart ||
            ! this.chart.config ||
            ! this.chart.config.data ||
            ! this.chart.config.data.datasets )
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
            let serie = aSeries[s];

            if( ! serie || ! serie.data || ! Array.isArray(serie.data) )
            {
                console.error("Invalid series data at index", s);
                continue;
            }

            let aDots = [];

            for( let p = 0; p < serie.data.length; p++ )
            {
                let point = serie.data[p];

                if( ! point || ! Array.isArray(point) || point.length < 2 )
                {
                    console.error("Invalid data point at series", s, "index", p);
                    continue;
                }

                let timestamp = point[0];
                let value     = point[1];

                if( timestamp === undefined || value === undefined )
                    continue;

                aDots.push( {
                                when: timestamp,
                                x   : timestamp,    // Use timestamp for time scale positioning
                                y   : value
                            } );
            }

            this.chart.config.data.datasets.push( { label: serie.label || ("Series " + s), data: aDots } );
        }

        this.chart.update();

        return this;
    }

    clear()   // Implemented in this way to keep (to not lose) the references to the arrays and the rest of the configuration
    {
        if( ! this.chart ||
            ! this.chart.config ||
            ! this.chart.config.data )
        {
            console.error("Chart not initialized for clear operation");
            return this;
        }

        // Clear chart labels
        if( this.chart.config.data.labels )
            this.chart.config.data.labels.length = 0;

        // Clear chart datasets
        if( this.chart.config.data.datasets )
        {
            for( const dataset of this.chart.config.data.datasets )
            {
                if( dataset && dataset.data )
                    dataset.data.length = 0;
            }
        }

        // Clear historical series data to keep zoom/filter consistent
        for( const serie of this.aSeries )
        {
            if( serie && serie.data )
                serie.data.length = 0;
        }

        this.chart.update();

        return this;
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

        let $container = $(this.parent.getContentArea());

        $container.empty()
                  .append( this._getChartDiv_( $container.width(), $container.height() - 75 ) );

        let $canvas = $('#'+this.divId).find('canvas').first();

        if( $canvas.length === 0 )
        {
            console.error("Canvas element not found in chart div");
            return this;
        }

        this.canvas = $canvas[0];     // Now that div (which contains a canvas) is appended, a reference to it can be assigned

        this._restoreTimeFrame_();

        let self = this;

        this.canvas.onwheel = (evt) => self._zoom_( evt );

        let aDataset = [];

        this.aSeries.length = 0;    // Clear previous series data to prevent memory leak on repeated show() calls

        for( const device of this.parent.devices )
        {
            let sLabel = device.label || (device.accessor ? device.name +'['+ device.accessor +']' : device.name);

            this.aSeries.push( { label: sLabel, data: [] } );

            aDataset.push( { label          : sLabel,
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

    _restoreTimeFrame_()
    {
        if( ! this.parent._chartUid )
            return;

        let sRaw;

        try { sRaw = localStorage.getItem( 'gum-chart-tf-' + this.parent._chartUid ); }
        catch( ignored ) { return; }

        if( ! sRaw )
            return;

        let oSaved;

        try { oSaved = JSON.parse( sRaw ); }
        catch( ignored ) { return; }

        if( ! oSaved || typeof oSaved.amount !== 'number' || ! oSaved.unit )
            return;

        let $txtValue = $('#'+this.divId+' [name="txtTimeFrame"]');
        let $lstUnit  = $('#'+this.divId+' [name="lstTimeFrameUnit"]');

        if( $txtValue.length === 0 || $lstUnit.length === 0 )
            return;

        $txtValue.val( oSaved.amount );
        $lstUnit.val( oSaved.unit );

        if( oSaved.amount === 0 )
        {
            this.timeframe = 0;
        }
        else
        {
            switch( oSaved.unit )
            {
                case "mins" : this.timeframe = oSaved.amount *           60 * 1000; break;
                case "hours": this.timeframe = oSaved.amount *      60 * 60 * 1000; break;
                case "days" : this.timeframe = oSaved.amount * 24 * 60 * 60 * 1000; break;
                default     : return;
            }
        }
    }

    /**
     * Applies lbl_clr to all non-Chart.js text elements: gadget title, template labels, etc.
     */
    _applyLabelColor_()
    {
        let lbl_clr = p_base.isEmpty( this.parent.lbl_clr ) ? 'black' : this.parent.lbl_clr;

        // Template labels: "Show only last...", "Show historic", etc.
        // Note: the gadget card title is intentionally excluded — it supports HTML,
        // so the user can style it freely via the "Gadget title" field.
        let $tpl = $('#'+this.divId);
        $tpl.find('label').css( 'color', lbl_clr );
    }

    //------------------------------------------------------------------------//
    // SI JS LO PERMITIESE, DE AQUI EN ADELANTE SERIAN PRIVATE

    _zoom_( event )
    {
        event.preventDefault();

        if( ! this.chart ||
            ! this.chart.options ||
            ! this.canvas )
        {
            console.error("Chart not initialized for zoom operation");
            return;
        }

        let nMinWhen = this._getMinShownWhen_();
        let nMaxWhen = this._getMaxShownWhen_();
        let nNowWhen = nMaxWhen - nMinWhen;

        if( nMinWhen === -1 || nMaxWhen === -1 )    // means there are no points being shown
            return;

        const nScale = 0.50;

        // Check if Y-axis title is displayed to adjust offset calculation
        let bTitleY = false;

        if( this.chart.options &&
            this.chart.options.scales &&
            this.chart.options.scales.y &&
            this.chart.options.scales.y.title )
        {
            bTitleY = this.chart.options.scales.y.title.display === true;
        }

        let offset = $(this.canvas).offset();
        if( ! offset )
        {
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
        if( ! this.chart || ! this.chart.config || ! this.chart.config.data ||
            ! this.chart.config.data.datasets || this.chart.config.data.datasets.length === 0 )
        {
            return -1;
        }

        let min      = Number.MAX_SAFE_INTEGER;
        let hasValid = false;

        for( const dataset of this.chart.config.data.datasets )
        {
            if( dataset && dataset.data && dataset.data.length > 0 &&
                dataset.data[0] && dataset.data[0].when !== undefined )
            {
                min = Math.min( min, dataset.data[0].when );
                hasValid = true;
            }
        }

        return hasValid ? min : -1;
    }

    _getMaxShownWhen_()
    {
        if( ! this.chart ||
            ! this.chart.config ||
            ! this.chart.config.data ||
            ! this.chart.config.data.datasets )
        {
            return -1;
        }

        let max = -1;

        for( const dataset of this.chart.config.data.datasets )
        {
            if( dataset && dataset.data && dataset.data.length > 0 )
            {
                let lastDot = dataset.data[dataset.data.length - 1];

                if( lastDot && lastDot.when !== undefined )
                    max = Math.max( max, lastDot.when );
            }
        }

        return max;
    }

    /**
     * Returns the dataset corresponding with passed label or null if not found.
     *
     * @param {Array} aoData Where to search (array of objects).
     * @param {String} sLabel The label of the dataset to retrieve.
     * @returns {Array|null} The data array corresponding to the label, or null if not found.
     */
    _findSerieData_( aoData, sLabel )
    {
        if( ! aoData || ! Array.isArray( aoData ) )
            return null;

        for( const oData of aoData )
        {
            if( oData && oData.label === sLabel )
                return oData.data;
        }

        return null;    // Return null instead of throwing - caller must handle this case
    }

    // Iterate all DataSets from history and update the visible DataSets (those in Chart(...))
    _filter_( nWhenFrom, nWhenTo )
    {
        if( ! this.chart ||
            ! this.chart.config ||
            ! this.chart.config.data ||
            ! this.chart.config.data.datasets )
        {
            return;
        }

        for( let nDS = 0; nDS < this.aSeries.length; nDS++ )
        {
            let serie = this.aSeries[nDS];

            if( ! serie || ! serie.data )
                continue;

            let aDot2Show = [];   // Array to be filled with proper points from history

            for( const dot of serie.data )
            {
                if( dot &&
                    dot.when &&
                    dot.when >= nWhenFrom &&
                    dot.when <= nWhenTo )
                {
                    aDot2Show.push( dot );
                }
            }

            if( nDS < this.chart.config.data.datasets.length )
            {
                let dataSet = this.chart.config.data.datasets[nDS];

                if( dataSet && dataSet.data )
                {
                    dataSet.data.length = 0;
                    dataSet.data.push( ...aDot2Show );
                }
            }
        }
        // With time scale, no need to manage labels - Chart.js uses timestamp values directly
    }

    // All, canvas and time-frame controls at bottom right
    _getChartDiv_( nWidth, nHeight )
    {
        let $tpl     = GumGadget.cloneTemplate( "tpl-chart" );
        let parentId = this.parent.id;

        $tpl.attr( 'id', this.divId );

        $tpl.find( 'canvas' )
            .attr( 'width',  parseInt( nWidth  ) )
            .attr( 'height', parseInt( nHeight ) );

        // Bind event handlers (replacing inline onclick attributes)
        $tpl.find( '[name="btnLoadOrLive"]'   ).on( 'click',  function() { ChartWrap._loadFromDB_(   parentId ); } );
        $tpl.find( '[name="txtTimeFrame"]'    ).on( 'input',  function() { ChartWrap._setTimeFrame_( parentId ); } );
        $tpl.find( '[name="lstTimeFrameUnit"]').on( 'change', function() { ChartWrap._setTimeFrame_( parentId ); } );

        return $tpl;
    }

    _getChartOptions_()
    {
        let x_title  = p_base.isEmpty( this.parent.x_title ) ? null    : this.parent.x_title.trim();
        let y_title  = p_base.isEmpty( this.parent.y_title ) ? null    : this.parent.y_title.trim();
        let lbl_clr  = p_base.isEmpty( this.parent.lbl_clr ) ? 'black' : this.parent.lbl_clr;
        let ts_round = this.parent.ts_round || 'millis';
        let timeUnit = (ts_round === 'secs'  ? 'second' :
                        ts_round === 'mins'  ? 'minute' :
                        ts_round === 'hours' ? 'hour'   : null);   // null = Chart.js auto-detects

        let options =
            {
                responsive: true,
                maintainAspectRatio: false,
                animation : { duration: 0 },
                scales    : {
                                x:  {
                                        type   : 'time',
                                        display: true,
                                        border : {
                                                    color: lbl_clr
                                                 },
                                        grid   : {
                                                    display: false
                                                 },
                                        title  : {
                                                    text   : x_title,
                                                    display: (x_title !== null),
                                                    color  : lbl_clr
                                                 },
                                        ticks:   {
                                                    color    : lbl_clr,
                                                    maxTicksLimit: 10
                                                 },
                                        time   : {
                                                    unit          : timeUnit,   // null = auto; forced when rounding is applied
                                                    displayFormats: {
                                                        millisecond: 'HH:mm:ss.SSS',
                                                        second     : 'HH:mm:ss',
                                                        minute     : 'HH:mm',
                                                        hour       : 'HH:mm'
                                                    },
                                                    tooltipFormat: 'PPpp'    // Full date and time for tooltip
                                                 }
                                    },
                                y:  {
                                        display: true,
                                        border : {
                                                    color: lbl_clr
                                                 },
                                        grid   : {
                                                    display: true,
                                                    color  : lbl_clr + '20'    // Same color with low opacity
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

        if( ! instance || ! instance.chart )
            return;

        let wrapper    = instance.chart;
        let sDivId     = wrapper.divId;
        let $txtValue  = $('#'+sDivId+' [name="txtTimeFrame"]');
        let $lstUnit   = $('#'+sDivId+' [name="lstTimeFrameUnit"]');

        if( $txtValue.length === 0 || $lstUnit.length === 0 )
        {
            console.error("Timeframe controls not found");
            return;
        }

        let sValue = $txtValue.val();
        let sUnit  = $lstUnit.val();
        let nValue = parseInt( sValue );

        if( isNaN( nValue ) || nValue < 0 )
        {
            console.error("Invalid timeframe value");
            return;
        }

        try
        {
            localStorage.setItem( 'gum-chart-tf-' + wrapper.parent._chartUid,
                                  JSON.stringify( { amount: nValue, unit: sUnit } ) );
        }
        catch( ignored )
        { }

        // When nValue is 0, show all points (no timeframe restriction)
        if( nValue === 0 )
        {
            wrapper.timeframe = 0;
            wrapper._filter_( 0, Number.MAX_SAFE_INTEGER );

            if( wrapper.chart )
                wrapper.chart.update();

            return;
        }

        switch( sUnit )
        {
            case "mins" : wrapper.timeframe = nValue *           60 * 1000; break;
            case "hours": wrapper.timeframe = nValue *      60 * 60 * 1000; break;
            case "days" : wrapper.timeframe = nValue * 24 * 60 * 60 * 1000; break;
            default     : console.error("Unknown time unit: " + sUnit); return;
        }

        let now      = new Date().getTime();
        let n1stWhen = now - wrapper.timeframe;    // 1st point to be shown

        wrapper._filter_( n1stWhen, now + 60000 );   // 60000ms = 1 minute buffer for incoming points

        if( wrapper.chart )
            wrapper.chart.update();
    }

    /**
     * Invokes a web service to retrieve data from a DB
     *
     * @param {String} divId4Chart
     */
    static _loadFromDB_( divId4Chart )
    {
        let instance = ChartWrap._aInstances_.find( item => item.id === divId4Chart );

        if( ! instance || ! instance.chart )
            return;

        let wrapper = instance.chart;
        let gadget  = wrapper.parent;
        let sDivId  = wrapper.divId;

        if( ! gadget )
        {
            console.error("Chart wrapper has no parent gadget");
            return;
        }

        let $btnLoadOrLive = $('#'+sDivId+' [name="btnLoadOrLive"]');

        if( $btnLoadOrLive.length === 0 )
        {
            console.error("Load/Live button not found");
            return;
        }

        if( $btnLoadOrLive.text() === "Live" )     // Is showing stored data?
        {
            $btnLoadOrLive.text('Load');           // Go to 'Live' mode and show 'Load' in btn label
            wrapper.clear();
            wrapper.setPaused( false );
            return;
        }

        let $fromDate = $('#'+sDivId+' [name="from_date"]');
        let $toDate   = $('#'+sDivId+' [name="to_date"]');

        if( $fromDate.length === 0 || $toDate.length === 0 )
        {
            console.error("Date inputs not found");
            return;
        }

        let sFrom = $fromDate.val();
        let sTo   = $toDate.val();

        if( p_base.isEmpty( sFrom ) || p_base.isEmpty( sTo ) )
        {
            p_app.alert( "'From' and 'To': none can be empty" );
            return;
        }

        let dFrom = new Date( sFrom );
        let dTo   = new Date( sTo   );

        if( isNaN( dFrom.getTime() ) || isNaN( dTo.getTime() ) )
        {
            p_app.alert( "Invalid date format" );
            return;
        }

        if( dFrom.getTime() > dTo.getTime() )
        {
            p_app.alert( "'To' can not be before 'From'" );
            return;
        }

        if( p_base.isEmpty( gadget.db_jars ) || p_base.isEmpty( gadget.db_jdbc ) ||
            p_base.isEmpty( gadget.db_url  ) || p_base.isEmpty( gadget.db_tables ) )
        {
            p_app.alert( "All DB information is mandatory except user name and user password" );
            return;
        }

        let uid   = p_app.showLoading( wrapper.canvas );
        let oData = {
            from  : dFrom.getTime(),
            to    : dTo.getTime(),
            jars  : gadget.db_jars,
            jdbc  : gadget.db_jdbc,
            url   : gadget.db_url,
            user  : gadget.db_user || '',
            pwd   : gadget.db_pwd  || '',
            tables: JSON.stringify( gadget.db_tables )
        };

        $.ajax( {
            url     : '/ws/db',
            type    : "GET",
            data    : oData,
            error   : (xhr) => p_app.showAjaxError( xhr ),
            complete: ()    => p_app.hideLoading( uid ),
            success : (data) =>
            {
                $btnLoadOrLive.text('Live');
                wrapper.setPaused( true );
                wrapper.clear();
                wrapper.bulk( data );
            }
        } );
    }
}