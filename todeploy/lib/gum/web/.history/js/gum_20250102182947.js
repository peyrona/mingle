//----------------------------------------------------------------------------//
//                         GUM functions                                      //
//----------------------------------------------------------------------------//

"use strict";

// $.getScript( "https://cdn.jsdelivr.net/npm/chart.js@4.4.1/dist/chart.umd.min.js",
//              function() { $.getScript( "https://cdn.jsdelivr.net/npm/date-fns@4.1.0/cdn.min.js",
//                                        function() { $.getScript( "https://cdn.jsdelivr.net/npm/chartjs-adapter-date-fns@3.0.0/dist/chartjs-adapter-date-fns.min.js",
//                                                                  function() { console.log('chart.js and dependencies are fully loaded'); } )
//                                                   } )
//                         } );

//$.getScript( "https://cdn.jsdelivr.net/npm/chart.js", // "https://unpkg.com/vis-timeline@latest/standalone/umd/vis-timeline-graph2d.min.js",
//             function() { console.log('Loaded: vis,js'); } );

//----------------------------------------------------------------------------------------------------------//
// Load libs from local HD (do not change the order for chart.js and dependencies)

let asLib = ["/gum/lib/chart_v4.4.1.min.js",
             "/gum/lib/date-fns_v4.1.0.min.js",
             "/gum/lib/chartjs-adapter-date-fns_v3.0.0.min.js",
             "/gum/lib/gauge.min.js",
             "/gum/lib/ssd.js"];

for( let n = 0; n < asLib .length; n++ )
{
    $.ajax( {
                url     : asLib[n],
                dataType: 'script'
            } );
}

console.log( "All local libs for Gadgets were loaded" );

//---------------------------------------------------------------------------------------------------------//

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

    onDomReady : function()
    {
        window.addEventListener( 'beforeunload', function(evt) { gum.save(); } );

        let nLoadCounter = 0;

        function onChunkLoaded()
        {
            if( ++nLoadCounter === 8 )   // Do not allow to init until all chunks are loaded
                gum._init_();
        }

        p_app.append( "../../chunks/dialog_config.html"    , "div-dlg_options"   , onChunkLoaded );
        p_app.append( "../../chunks/properties_style.html" , "div-dlg_properties", onChunkLoaded );
        p_app.append( "../../chunks/properties_chart.html" , "div-dlg_properties", onChunkLoaded );
        p_app.append( "../../chunks/properties_ssd.html"   , "div-dlg_properties", onChunkLoaded );
        p_app.append( "../../chunks/properties_gauge.html" , "div-dlg_properties", onChunkLoaded );
        p_app.append( "../../chunks/properties_button.html", "div-dlg_properties", onChunkLoaded );
        p_app.append( "../../chunks/properties_check.html" , "div-dlg_properties", onChunkLoaded );
        p_app.append( "../../chunks/properties_text.html"  , "div-dlg_properties", onChunkLoaded );
    },

    isInDesignMode : function()
    {
        return (new URLSearchParams( document.location.search )
                    .get('design')
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
                    return oExEn;
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

    save : function( fnOnOK = () => {} )
    {
        if( ! this.isInDesignMode() )    // ::save() is invoked at 'beforeunload' event (which happens even when not under design mode)
            return;

        if( ! this._layout_.isChanged() )
            return fnOnOK();

        let sFileName = gt.normalizeHtmlName( this._name_ );

        let oLayout = { type    : this.isUsingGridLayout() ? "grid" : "free",
                        contents: this._layout_.getContents() };

        let oData = {
                      name: sFileName,                           // FileName is needed by Server to give name to the HTML file.
                      rest: { background: this._oBackGr_,        // Everything else: this will be returned by _template_.html::getConfig()
                              password  : this.getDashboardPassword(),
                              exens     : this.getAllExEn(),
                              layout    : oLayout }
                    };

        $.ajax( {
                    url        : '/gum/ws/board',            // Do not change this URL
                    type       : "POST",
                    data       : JSON.stringify( oData ),    // My POST at server side expects JSON
                    contentType: "application/json; charset=UTF-8",
                    error      : (xhr) =>  p_app.showAjaxError( xhr, "Error saving dashboard" ),
                    success    : fnOnOK    // Only on success can be executed this func
                } ).always((xhr, status) => console.log('AJAX save() status:', status));
    },

    //---------------------------------------------------------------------------//
    // LOCAL STORAGE MANAGEMENT

    /**
     * Stores information (key,value) in localStorage.
     *
     * @param {type} key
     * @param {type} value
     * @param {type} nValidFor Seconds from now to consider the information obsolete. 0 => info will be last forever, x < 0 for the session. 0 by default.
     * @returns {this}
     */
    write : function( key, value, nValidFor = 0 )
    {
        if( nValidFor < 0 )
        {
            sessionStorage.setItem( key, JSON.stringify( value ) );
        }
        else
        {
            let until = (nValidFor === 0) ? 0 : (new Date().getTime() + nValidFor * 1000);
            let data  = JSON.stringify( { value: value, until: until } );

            localStorage.setItem( key, data );
        }

        return this;
    },

    /**
     * Retrieves from localStorage a value previously stored (if value is expired, null is returned).
     *
     * @param {String} key
     * @returns The value associated with received key.
     */
    read : function( key )
    {
        let value = sessionStorage.getItem( key );

        if( value )
            return JSON.parse( value );

        value = localStorage.getItem( key );

        if( value )
        {
            let data = JSON.parse( value );

            if( (data.until > 0) && (data.until < new Date().getTime()) )
            {
                localStorage.removeItem( key );
                return null;
            }

            return data.value;
        }

        return null;
    },

    /**
     * Deletes from localStorage previously stored information (key,value).
     *
     * @param {String} key To delete.
     */
    del : function( key )
    {
        localStorage.removeItem( key );
        sessionStorage.removeItem( key );
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

        let sHTMLBtns = '<i class="gum-mini-btn fa fa-eye"             title="Preview (in new tab)" onclick="gum._onPreview_()"      ></i>'+
                        '<i class="gum-mini-btn fa fa-sliders"         title="Open configuration"   onclick="gum._showCfgDlg_()"     ></i>'+
                        '<i class="gum-mini-btn fa fa-question-circle" title="Show help"            onclick="gum._layout_.showHelp()"></i>';

        $('#gum-toolbar').empty()
                         .css('color'           ,'#222222')
                         .css('background-color','#E0E0F8')
                         .css('border'          ,'solid #444444 2px')
                         .css('padding-top'     ,'6px')
                         .addClass('is-pulled-right')
                         .append( $('<div class="select is-small"></div>').append( $select ) )
                         .append( sHTMLBtns )
                         .draggable()
                         .show();
    },

    _onPreview_ : function()
    {
        gum.save( function()
                    {
                        const loc = gt.normalizeHtmlName( gum._name_ ) +'?design=false';
                        const wnd = window.open( p_base.doURL( loc ), '_blank' );

                        if( wnd ) wnd.focus();
                        else      p_app.alert( "Can't open the window: it may have been blocked.\nAre popups allowed for this site?" );
                    }
                );
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
                                        gum.save();
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

    _init_ : function()
    {
        // Init dashboard name
        let name = window.location.pathname.split('/').pop();
            name = name.trim().toLowerCase().endsWith('.html') ? name.substring( 0, name.length - 5 ) : name;      // -5 to remove ".html"

        if( name === "_template_" )
            name = "new dashboard";

        document.title = "Gum ::: "+ name;

        this._name_ = gt.normalizeFileName( name );

        this._setButtonBar_();

        $(window).on('resize', function( event )    // Used on both: design and not design modes (better not to touch this function)
                                {
                                    if( free._resizeTimer_ )
                                        clearTimeout( free._resizeTimer_ );

                                    if( event && event.target === window )    // 'window' is the browser window
                                        free._resizeTimer_ = setTimeout( function() { gum._layout_.onBrowserResized(); }, 350 );   // Must be 'function', not lambda
                                } );

        gum_ws.connect( () =>    // On successfully connected via WebSockets with the server
            {
                let oConfig  = getConfig();     // This method is in _template_.html

                if( p_base.isEmpty( oConfig ) )
                    throw "Config is empty: this should not happen";

                gum.setBackground( oConfig.background );
                gum.setDashboardPassword( oConfig.password );
                gum._layout_ = (oConfig.layout.type === 'grid') ? grid : free;    // A reference to the layout chossen by user (can not be changed after choosen)

                if( p_base.isEmpty( oConfig.exens ) )
                {
                    gum._showCfgDlg_();
                }
                else
                {
                    if( gum.isInDesignMode() )
                    {
                        gum._updateExEns_( oConfig.exens, () =>
                                           gum._layout_.setContents( oConfig.layout.contents ) );
                    }
                    else
                    {
                        // TODO:
                        // En principio sólo con esto debería bastar -->
                        //     gum._layout_.setContents( oConfig.layout.contents );
                        // Pero en ::_updateExEns_(...) se hace algo que es necesario
                        // para que se comience a recibir datos desde los ExEn remotos-

                        gum._updateExEns_( oConfig.exens, () =>
                                           gum._layout_.setContents( oConfig.layout.contents ) );
                    }
                }
            } );
    }
};
}