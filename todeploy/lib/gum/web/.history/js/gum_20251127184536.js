//----------------------------------------------------------------------------//
//                         GUM functions                                      //
//----------------------------------------------------------------------------//

"use strict";

if( typeof gum === "undefined" )
{
var gum =
{
    _oBackGr_     : null,    // Background image and background gradient { background: { image: {...}, color: {...} }
    _aoTarget_    : [],      // { exen: <oDef>, devs: [ <sDevName>, ... ] }     // Note: sorted by module dlgCfg
    _layout_      : null,    // A reference to a Layout manager: Grid or Free
    _name_        : null,    // Dashboard name
    _password_    : "",      // Password to allow the dashboard to be edited
    _resizeTimer_ : null,    // Timer to avoid multiple resize events
    _isSaving_    : false,   // Flag to prevent concurrent saves

    run : function( oConfig )    // This function is called by _template_.html::getConfig() to pass the config
    {
        this._loadAllScripts_()
            .then( function()
            {
                console.log('All scripts have been loaded.');
                return gum._loadChuncks_();
            } )
            .done( function()
            {
                gum._init_( oConfig );    // This is called after both scripts and chunks are loaded.
            } )
            .fail( function( jqxhr, settings, exception )
            {
                console.error('Error during initialization:', exception);
                p_app.alert( exception +'\nCan not continue. Press [Esc] to close.', "Error during initialization", window.close );
            } );
    },

    isInDesignMode : function()
    {
        return ((new URLSearchParams( document.location.search )
                    .get('design') || '')
                    .toString()
                    .toLowerCase() === 'true');
    },

    isUsingFreeLayout : function()
    {
        return this._layout_ === free;
    },

    isUsingGridLayout : function()
    {
        return this._layout_ === grid;
    },

    getDashboardName : function()
    {
        return this._name_;
    },

    getDashboardPassword : function()
    {
        return this._password_;
    },

    setDashboardPassword : function( pwd )
    {
        if( p_base.isEmpty( pwd ) )
            pwd = "";

        this._password_ = pwd;
    },

    getBackground : function()
    {
        return this._oBackGr_;
    },

    setBackground : function( oData )
    {
        this._oBackGr_ = oData;

        (new Background( oData )).applyTo( document.body );
    },

    getAllExEn : function()
    {
        let ao = [];

        this._aoTarget_.forEach( (oTarget) => ao.push( oTarget.exen ) );

        return ao;
    },

    getTarget4 : function( xExEn )
    {
        if( p_base.isString( xExEn ) )
            xExEn = JSON.parse( xExEn );

        for( let oTarget of this._aoTarget_ )
        {
            if( p_base.jsonAreEquals( oTarget.exen, xExEn ) )
                return oTarget;
        }

        return null;
    },

    /**
     * Returns an array with all devices associated with passed ExEn: { name: <devName>, type: <devType> }
     *
     * @param {Object|String} xExEn
     * @returns {Array} An array with all devices associated with passed ExEn.
     */
    getDeviceNames4 : function( xExEn )
    {
        if( p_base.isString( xExEn ) )
            xExEn = JSON.parse( xExEn.replace( /\\"/g, '"' ) );   // '.replace(...)' removes extra '\'

        let oTarget = null;

        for( let target of this._aoTarget_ )
        {
            if( p_base.jsonAreEquals( target.exen, xExEn ) )
            {
                oTarget = target;
                break;
            }
        }

        if( oTarget === null )
            throw xExEn +": target not found";

        let asNames = [];

        oTarget.devs.forEach( (oDev) => asNames.push( oDev.name ) );

        asNames = [ ...new Set( asNames ) ];    // Removes duplicated names (it is normal that there are duplicated names)
        asNames.sort();

        return asNames;
    },

    /**
     * Returns true if passed device name is managed by passed sExEn address. If no sExEn is
     * passed, then sDevice is searched through out all declared ExEns and instead of returning
     * a boolean, the address of the ExEn is returned.
     *
     * @param {type} sDevice
     * @param {type} sExEn
     * @returns {Boolean | String}
     */
    hasDevice( sDevice, sExEn = null )
    {
        if( p_base.isUndefined( sExEn ) )
        {
            for( let oTarget of this._aoTarget_ )
            {
                if( this.getDeviceNames4( oTarget.exen ).includes( sDevice ) )
                    return oTarget.exen;
            }

            return false;
        }

        return this.getDeviceNames4( sExEn ).includes( sDevice );
    },

    fillWithExEns : function( oSelect4ExEns )
    {
        let $lst = $(oSelect4ExEns).empty();   // '.empty()' clears the options, but does not set selectedIndex to -1
            $lst[0].selectedIndex = -1;        // Ensures no option is selected

        for( let oExEn of this.getAllExEn() )
        {
            if( ! p_base.isObject( oExEn ) )
                alert( "Error, no es un JSON" );

            $lst.append( '<option>'+ JSON.stringify( oExEn ) +'</option>' );
        }
    },

    fillWithDevices : function( oSelec4ExEns, oSelect4Devices )
    {
        let $lst4ExEns = $(oSelec4ExEns);
        let $lst4Devs  = $(oSelect4Devices).empty();
        let sExEnDef   = p_base.getFieldValue( $lst4ExEns );

        if( p_base.isEmpty( sExEnDef ) )            // No option is selected
            sExEnDef = $lst4ExEns[0].options[0];

        this.getDeviceNames4( sExEnDef )
            .forEach( (sDevName) => $lst4Devs.append( $("<option></option>").text( sDevName ) ) );
    },

    deviceAddedAtExEn : function()
    {
// TODO: hacerlo -> añadir el nuevo device y al gum._aoExEn_
    },

    deviceRemovedAtExEn : function()
    {
// TODO: hacerlo -> eliminar los gadgets q usen este device y del gum._aoExEn_
    },

    //---------------------------------------------------------------------------//
    // GUM INTERNALS (should be private)
    //---------------------------------------------------------------------------//

    // Buttons that appear at top-right corner
    _setButtonBar_()
    {
        if( ! this.isInDesignMode() )
            return;

        let $select = $('<select>' +
                            '<option value=""      >Select gadget to add...</option>' +
                            '<option value="chart" >Chart (time line)      </option>' +
                            '<option value="check" >Check box              </option>' +
                            '<option value="gauge" >Gauge (numerical)      </option>' +
                            '<option value="button">Push button            </option>' +
                            '<option value="ssd"   >Seven Segments Display </option>' +
                            '<option value="text"  >Text and/or Image      </option>' +
                        '</select>');

        $select.change( function()
                        {
                            if( this.value.length > 0 )
                                gum._layout_.addGadget( GumGadget.instantiate( this.value ) );

                            $(this).blur()[0].selectedIndex = 0;     // Blur needed because certain keys generate select:onchange event
                        } );

        const sHTMLBtns = '<i class="gum-mini-btn fa fa-eye"             title="Preview (in new tab)"></i>'+
                          '<i class="gum-mini-btn fa fa-sliders"         title="Configuration"></i>'+
                          '<i class="gum-mini-btn fa fa-save"            title="Save"></i>'+
                          '<i class="gum-mini-btn fa fa-question-circle" title="Show help"></i>';

        const $toolbar = $('#gum-toolbar').empty()
                                          .css('color'           ,'#222222')
                                          .css('background-color','#E0E0F8')
                                          .css('border'          ,'solid #444444 2px')
                                          .css('padding-top'     ,'6px')
                                          .addClass('is-pulled-right')
                                          .append( $('<div class="select is-small"></div>').append( $select ) )
                                          .append( sHTMLBtns )
                                          .draggable()
                                          .show();

        $toolbar.find('.fa-eye'            ).on( 'click', gum._onPreview_ );
        $toolbar.find('.fa-sliders'        ).on( 'click', gum._showCfgDlg_ );
        $toolbar.find('.fa-save'           ).on( 'click', gum._save_ );
        $toolbar.find('.fa-question-circle').on( 'click', () => gum._layout_.showHelp() );
    },

    _onPreview_ : function()
    {
        if( p_base.isEmpty( gum._aoTarget_ ) )
        {
            p_app.alert( "No ExEns defined.\nPlease, open the configuration dialog and add at least one ExEn." );
            return;
        }

        const uuid = p_base.uuid();

        const openPreview = () =>
                            {
                                p_app.hideLoading( uuid );

                                const loc = gt.normalizeHtmlName( gum._name_ ) +'?design=false';
                                const use = gt.read( "_GUM_REUSE_WND_", false );
                                const wnd = window.open( p_base.doURL( loc, true ), (use ? '_self' : '_blank') );

                                if( wnd ) wnd.focus();
                                else      p_app.alert( "Can't open the window: it may have been blocked.\nAre popups allowed for this site?" );
                            };

        if( gum._layout_.isChanged() )  gum._save_( openPreview );
        else                            openPreview();
    },

    _showCfgDlg_ : function()
    {
        $('#div-dlg_options')
            .dialog( {  title      : 'Configuration',
                        modal      : true,
                        autoOpen   : true,
                        resizable  : true,
                        width      : p_app.getBestWidth(  "90%", 520 ),
                        height     : p_app.getBestHeight( "90%", 820 ),
                        open       : () => dlgCfg.beforeOpen(),
                        beforeClose: function()
                                     {
                                        if( dlgCfg.getExEns().length === 0 )
                                        {
                                            p_app.alert( "At least one ExEn is needed" );
                                            return false;
                                        }
                                     },
                        close      : function()   // Can not be a lambda when using $(this)
                                     {
                                        gum._updateExEns_( dlgCfg.getExEns() );
                                        gum._save_();
                                        $(this).dialog('destroy');    // Closes this dialog window (must be last)
                                     }
                     } );
    },

    _updateExEns_ : function( axExEn, fnOnDone )
    {
        let nDone = 0;

        gum._aoTarget_ = [];

        gum_ws.setOnList( (oExEn, aoDevs) =>     // Even if this call has to be done only once, it is done here just for clarity
                            {
                                let oTarget = gum.getTarget4( oExEn );     // To get its reference

                                for( let oDev of aoDevs )
                                    if( oDev.cmd === 'device' )
                                        oTarget.devs.push( oDev );

                                if( fnOnDone && (++nDone === axExEn.length) )
                                    fnOnDone();
                            } );

        for( let xExEn of axExEn )
        {                                                                          // '.replace(...)' removes extra '\'
            this._aoTarget_.push( { exen: (p_base.isString( xExEn ) ? JSON.parse( xExEn.replace( /\\"/g, '"' ) ) : xExEn),
                                    devs: [] } );

            gum_ws.requestList( this._aoTarget_.at( -1 ).exen );   // at( -1 ) -> Get last item
        }
    },

    _save_ : function( fnOnOK = () => {} )     // Inside this function 'this' can not be used because this funciton can be inmvoked from an event handler 'onlick( _save_)'
    {
        if( gum._isSaving_ )
            return;

        gum._isSaving_ = true;

        const sData = gum._getData2Save_();

        if( p_base.isEmpty( sData ) )      // Even if there is no data to save, we have to call fnOnOK()   (i.e. for preview)
        {
            gum._isSaving_ = false;
            fnOnOK();
            return;
        }

        $('#gum-toolbar').find('.fa-save')
                         .css('color', 'red');

        $.ajax( {
                    url        : '/gum/ws/board',    // Do not change this URL
                    type       : "POST",
                    data       : sData,              // My POST at server side expects JSON
                    contentType: "application/json; charset=UTF-8",
                    error      : (xhr) => { p_app.showAjaxError( xhr, "Error saving dashboard" ); },
                    success    : () => { gum._layout_.saved(); fnOnOK(); }    // Only on success can be executed this func
                } ).always((xhr, status) =>
                                            {
                                                gum._isSaving_ = false;
                                                console.log('AJAX save() status:', status);
                                                $('#gum-toolbar').find('.fa-save').css('color', '');
                                            } );
    },

    _getData2Save_ : function()
    {
        if( ! gum.isInDesignMode() )
            return null;

        let contents = gum._layout_.getContents();

        let oLayout = { type    : gum.isUsingGridLayout() ? "grid" : "free",
                        contents: contents };

        let oRest = { background: gum._oBackGr_,
                      password  : gum.getDashboardPassword(),
                      exens     : gum.getAllExEn(),
                      layout    : oLayout };

        let oData = {
                      name: gt.normalizeHtmlName( gum._name_ ),    // FileName is needed by Server to give name to the HTML file.
                      rest: oRest                                  // Everything else in oConfig
                    };

        return JSON.stringify( oData );   // POST at server side expects JSON
    },

    _init_ : function( oConfig )
    {
        // Init dashboard name
        let name = window.location.pathname.split('/').pop();
            name = name.trim().toLowerCase().endsWith('.html') ? name.substring( 0, name.length - 5 ) : name;      // -5 to remove ".html"

        if( name === "_template_" )
            name = "new dashboard";

        document.title = "Gum ::: "+ name;

        gum._name_ = gt.normalizeFileName( name );

        gum._setButtonBar_();
// FIXME: --->
        // $(window).on('resize', function( event )    // Used on both: design and not design modes (better not to touch this function)
        //                         {
        //                             if( gum._resizeTimer_ )
        //                                 clearTimeout( gum._resizeTimer_ );

        //                             if( event && event.target === window )    // 'window' is the browser window
        //                                 gum._resizeTimer_ = setTimeout( function() { gum._layout_.onBrowserResized(); }, 350 );   // Must be 'function', not lambda
        //                         } );

        gum_ws.connect( () =>    // On successfully connected via WebSockets with the server
            {
                gum.setBackground( oConfig.background );
                gum.setDashboardPassword( oConfig.password );
                gum._layout_ = (oConfig.layout.type === 'grid') ? grid.init() : free.init();    // A reference to the layout chossen by user (can not be changed after choosen)

                if( p_base.isEmpty( oConfig.exens ) )
                {
                    gum._showCfgDlg_();
                }
                else
                {
                    gum._updateExEns_( oConfig.exens,
                                       () => gum._layout_.setContents( oConfig.layout.contents ) );
                }
            } );
    },

    // Load all scripts from local HD
    _loadAllScripts_ : function()
    {
        const aScripts = [  // 3rd party libs (do not change the order for chart.js and dependencies)
                            "/gum/lib/chart_v4.4.1.min.js",
                            "/gum/lib/date-fns_v4.1.0.min.js",
                            "/gum/lib/chartjs-adapter-date-fns_v3.0.0.min.js",
                            "/gum/lib/gauge.min.js",
                            "/gum/lib/ssd.js" ];

        // Start a "resolved" promise to begin the chain
        let promiseChain = $.when();

        // Dynamically build the chain by iterating through the scripts
        aScripts.forEach( function( scriptUrl )
                        {
                            promiseChain = promiseChain.then( function() { return $.getScript(scriptUrl); } );
                        } );

        return promiseChain;
    },

    _loadChuncks_ : function()
    {
        const deferred      = $.Deferred();
        let   nLoadCounter  = 0;
        const nChunksToLoad = 8;

        function onChunkLoaded()
        {
            if( ++nLoadCounter === nChunksToLoad )
            {
                console.log('All chunks have been loaded.');
                deferred.resolve();
            }
        }

        p_app.append( "../../chunks/dialog_config.html"    , "div-dlg_options"   , onChunkLoaded );
        p_app.append( "../../chunks/properties_style.html" , "div-dlg_properties", onChunkLoaded );
        p_app.append( "../../chunks/properties_chart.html" , "div-dlg_properties", onChunkLoaded );
        p_app.append( "../../chunks/properties_ssd.html"   , "div-dlg_properties", onChunkLoaded );
        p_app.append( "../../chunks/properties_gauge.html" , "div-dlg_properties", onChunkLoaded );
        p_app.append( "../../chunks/properties_button.html", "div-dlg_properties", onChunkLoaded );
        p_app.append( "../../chunks/properties_check.html" , "div-dlg_properties", onChunkLoaded );
        p_app.append( "../../chunks/properties_text.html"  , "div-dlg_properties", onChunkLoaded );

        return deferred.promise();
    }
};
}