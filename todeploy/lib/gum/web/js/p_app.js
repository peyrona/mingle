// ---------------------------------------------------------------------------//
//          A bunch of useful and generalistic application functions
// ---------------------------------------------------------------------------//
//  This module depends on: p_base.js and JQuery.
// ---------------------------------------------------------------------------//

"use strict";

/**
 * Generic error management.
 * <p>
 * Note: this can not be in "p_base.js" because this uses ::alert(...)
 *
 * @param sMessage
 * @param sURL
 * @param nLine
 * @param nCol
 * @param oError
 * @returns false.
 */
window.onerror = function( sMessage, sURL, nLine, nCol, oError )
        {
            return p_app.onGenericError( sMessage, sURL, nLine, nCol, oError );
        };

if( typeof p_app === "undefined" )
{
var p_app =
{
    onGenericError : function( sMessage, sURL, nLine, nCol, oError )
    {
        sURL = (p_base.isEmpty( sURL ) ? window.location.pathname : sURL);

        let sErr = ((oError === null) ? "" : "<br>"+ JSON.stringify( oError ));
        let sMsg = "JS internal error at "+ sURL +" [lin:"+ nLine +",col:"+ nCol +"]<br>"+ sMessage + sErr;

        if( p_base.isMobile() )
            console.log( tile +': '+ sMsg.replace( /<br\s*\/?>/gi, '\n' ) );
        else
            this.alert( sMsg, "Error" );

        return false;
    },

    /**
     * Shows an AJAX error inside an ::alert().
     *
     * @param {Object} xhr The AJAX error (as sent by JQuery during AJAX calls).
     * @param {String} title Optional dialog title. By default "AJAX error".
     * @param {Function} fnOnClosed Optional function to be invoked after message dialog is closed. By default null.
     */
    showAjaxError : function( xhr, title = "AJAX error", fnOnClosed = null )
    {
        if( xhr.status === 401 )  // 401 --> Unauthorized: session expired
        {
            let response = JSON.parse( xhr.responseText );

            if( response.redirectTo )
                window.location.href = response.redirectTo;
        }
        else
        {
            let sErr408 = (xhr.status !== 408) ? ""   // 408 --> request timed out
                                               : "Server not avaibale.<br>Please check your Internet connection and try again.<br>";

            let msg = sErr408 +
                      "<br>Server response: "+ ((! xhr) ? "Unknown" : (xhr.responseText || "No response")) +
                      "<br>Status: "+ xhr.status + " - "+ xhr.statusText;

            if( p_base.isMobile() )
                console.log( tile +': '+ msg.replace( /<br\s*\/?>/gi, '\n' ) );
            else
                this.alert( msg, String( title ), fnOnClosed );
        }
    },

    /**
     * Shows an Event Error error inside an ::alert().
     *
     * @param {Object} error The Event error.
     * @param {String} title Optional dialog title. By default "Event error".
     * @param {Function} fnOnClosed Optional function to be invoked after message dialog is closed. By default null.
     */
    showEventError : function( error, title = "Event error", fnOnClosed = null )
    {
        if( p_base.isString( error ) )
        {
            if( p_base.isMobile() )
                console.log( tile +': '+ error.replace( /<br\s*\/?>/gi, '\n' ) );
            else
                this.alert( error, String( title ), fnOnClosed );

            return;
        }

        const props = [];

        // Get all enumerable properties
        for( const key in error )
        {
            try
            {
                let value = error[key];

                // Handle different value types
                if( (value !== null) && (typeof value === 'object') )
                {
                    if( value.constructor && value.constructor.name )
                        value = `[${value.constructor.name}]`;
                    else
                        value = JSON.stringify( value );
                }
                // else if (typeof value === 'function')
                // {
                //     value = '[Function]';
                // }

                if( value.length > 80 )
                    value = value.substring( 0, 80 ) + "...";

                props.push(`${key}: ${value}`);
            }
            catch( err )
            {
                props.push(`${key}: [Error accessing property]`);
            }
        }

        let msg = props.join('\n');

        if( p_base.isMobile() )
            console.log( tile +': '+ msg.replace( /<br\s*\/?>/gi, '\n' ) );
        else
            this.alert( msg, String( title ), fnOnClosed );
    },

    /**
     * Retrives an HTML file from the server and embeds it into passed target element (normally a DIV).
     *
     * @param {String} sServerFile Server file containing HTML.
     * @param {String or HTMLElement} targetElement Either a String ID or a DOM element or a JQuery object.
     * @param {Function} onLoaded onLoaded Callback to be invoked after successfully loaded the HTML (optional);
     *                            receives as parameters: the target and the HTML gridContents.
     */
    embed : function( sServerFile, targetElement, onLoaded )
    {
        this._downloadFileAndAttach_( false, sServerFile, targetElement, onLoaded );
    },

    /**
     * Retrives an HTML file from the server and appends it to passed target element
     * (normally a DIV).
     *
     * @param {String} sServerFile Server file containing HTML.
     * @param {String or HTMLElement} target Either a String ID or a DOM element or a JQuery object.
     * @param {Function} onLoaded onLoaded Callback to be invoked after successfully loaded the HTML (optional);
     *                            receives as parameters: the target and the HTML gridContents.
     */
    append : function( sServerFile, target, onLoaded )
    {
        this._downloadFileAndAttach_( true, sServerFile, target, onLoaded );
    },

    nLastUserActionTime : Date.now(),     // Used by ::setSessionTimeout (Note: it is needed to be inited to now)

    /**
     * Invokes received function after certain minutes with no user actions.
     * <p>
     * This function triggers all 'beforeunload' event handlers existing in the application.
     *
     * @param {Function} fn Function to be invoked when session expires.
     * @param {Number} nMinutes Time (in minutes) to declare a session expiration (by default 45).
     */
    setSessionTimeout : function( fn, nMinutes = 45 )
    {
        nMinutes = (nMinutes < 1) ? 1 : nMinutes;

        let self   = this;
        let millis = parseInt( nMinutes ) * 60 * 1000;

        $(document).on( 'keyup click touch scroll', () => self.nLastUserActionTime = Date.now() );

        let id = setInterval( () =>
                                {
                                    if( (Date.now() - self.nLastUserActionTime) > millis )
                                    {
                                        clearInterval( id );                   // To stops the interval
                                        $(window).trigger('beforeunload');     // Triggers all handlers associated with this event
                                        fn();
                                    }
                                },
                                10*1000 );  // Checks every 10 seconds
    },

    //------------------------------------------------------------------------//
    // AJAX RELATED FUNCTIONS
    //------------------------------------------------------------------------//

    /**
     * Performs an AJAX <u>async</u> GET request to passed service URL (using p_base:doURL(...))
     * and reports errors via ::showAjaxError(...)
     *
     * @param {String} sURL2Append Append to be added via p_base:doURL(...).
     * @param {Function} fnOnOk To be executed on success. The function will receive the data received from the server.
     * @param {Function} fnOnComplete To be executed on complete. By default it is null.
     */
    ajaxGet : function( sURL2Append, fnOnOk, fnOnComplete = null )
    {
        let self = this;

        $.ajax( {   url     : p_base.doURL( sURL2Append ),
                    type    : "GET",
                    error   : (xhr ) => self.showAjaxError( xhr ),
                    success : (data) => fnOnOk( data ),
                    complete: fnOnComplete
                } );
    },

    //------------------------------------------------------------------------//
    // UI RELATED FUNCTIONS
    //------------------------------------------------------------------------//
    isAnyJQueryDlgOpen : function()
    {
        return $(".ui-dialog:visible").length > 0;
    },

    /**
     * Muestra un mensaje de alerta con texto HTML en una dialog de JQuery modal,
     * y si se pasa fnOnClose, ejecuta esa función tras cerrar la dialog.
     *
     * @param {String} msg
     * @param {String} title
     * @param {Function} fnOnClosed
     */
    alert : function( msg, title, fnOnClosed )
    {
        msg = (p_base.isString( msg ) ? msg.replace( /\n/g, "<br/>" )        // Changes all spaces by its HTML mark
                                      : (msg + ""));                         // Converts number (or whatever) into string

        if( ! p_base.isString( title ) )
            title = 'Notification';

        let fn = function()
                 {
                     $(this).dialog('destroy');

                     if( p_base.isFunction( fnOnClosed ) )
                         fnOnClosed();
                 };

        $('<div></div>')
            .html( msg )
            .dialog( { title    : title,
                       modal    : true,
                       zIndex   : 10000,
                       autoOpen : true,
                       width    : 'auto',
                       height   : 'auto',
                       resizable: false,
                       close    : fn } );
    },

    /**
     * Shows a confirmation dialog, executing passed fnOnYes onky if user clicks the [Yes] button.
     *
     * @param {String}   message To be shown. Can not be null.
     * @param {Function} fnOnYes To be executed when user clicks [Yes]. Can be null.
     * @param {Function} fnOnNo  To be executed when user clicks [No]. Can be null.
     * @param {String}   bgColor Background color. By default null.
     */
    confirm : function( message, fnOnYes = null, fnOnNo = null, bgColor = null )
    {
        let sDiv = '<div'+ (bgColor ? (' style="background-color:'+bgColor+'"') : '')+ '></div>';

        if( fnOnYes === null )
            fnOnYes = () => {};

        if( fnOnNo === null )
            fnOnNo = () => {};

        $(sDiv)
            .html( message )
            .dialog( {  title    : 'Confirm',
                        modal    : true,
                        zIndex   : 10000,
                        autoOpen : true,
                        width    : 'auto',
                        height   : 'auto',
                        resizable: false,
                        close    : function() { $(this).dialog('destroy'); },   // Can not be changed to a lambda to use $(this) (this func is invoked by dialog('close'))
                        buttons  : {
                                      Yes: function() { $(this).dialog('destroy'); fnOnYes(); },    // Can not be changed to a lambda to use $(this)
                                      No : function() { $(this).dialog('destroy'); fnOnNo();  }     // Can not be changed to a lambda to use $(this)
                                   }
                     } );
    },

    prompt : function( title, initialValue = '', callback )
    {
        // 1. Create the HTML content for the dialog dynamically
        const $dialogContent = $(`<div>
                                    <input type="text" class="input" />
                                  </div>`);

        const $input = $dialogContent.find('input');
              $input.val(initialValue).select();

        // 2. Define the function to be called on confirmation
        const onConfirm = () =>
        {
            const value = $input.val().trim();

            if( (value.length > 0) && p_base.isFunction( callback ) )   // If value is empty, callback is not invoked
                callback( value );

            $dialogContent.dialog('close');
        };

        // 3. Handle the 'Enter' key press on the input field
        $input.on('keydown', (event) =>
        {
            if (event.key === 'Enter')
            {
                event.preventDefault();
                onConfirm();
            }
        });

        // 4. Initialize the jQuery UI Dialog
        $dialogContent.dialog(
            {
                modal    : true,
                title    : title,
                resizable: false,
                buttons  : { "OK": onConfirm },
                close    : function() { $(this).remove(); },         // Important: Clean up the dialog from the DOM when it's closed
                open     : function() { $input.trigger('focus'); }   // Automatically focus the input field when the dialog opens
            });
    },

    /**
     * Muestra un indicador de "carga en proceso".
     *
     * @param {type} element Si es null, se aplica a toda la pantalla.
     * @returns {String} Un UUID que identifica al element que hace la animación.
     */
    showLoading : function( element )
    {
        let isBody  = p_base.isEmpty( element );
        let target  = isBody ? document.body : p_base.get( element );
        let $target = $( target );

        if( ! $target.is(":visible") )
            return -1;

        let originalOpacity = $target.css('opacity') || '1';
        let uid    = p_base.uuid();
        let style  = "position:" + (isBody ? "fixed" : "absolute") + "; " +
                     "z-index:99999; " +
                     "cursor:wait; " +
                     "background:rgba(255,255,255,0.01); ";

        if( isBody )
        {
            style += "top:0; left:0; width:100vw; height:100vh;";
        }
        else
        {
            let offset = $target.offset();
            style += "top:"    + parseInt( offset.top )    + "px; " +
                     "left:"   + parseInt( offset.left )   + "px; " +
                     "width:"  + parseInt( $target.width() )  + "px; " +
                     "height:" + parseInt( $target.height() ) + "px;";
        }

        let html   = "<div id='" + uid + "' data-target-selector='" + (isBody ? 'body' : element) + "' data-original-opacity='" + originalOpacity + "' style='" + style + "'>" +
                     "   <svg xmlns='http://www.w3.org/2000/svg'" +
                     "        width='64' height='64'" +
                     "        viewBox='0 0 24 24'" +
                     "        fill='none' stroke='currentColor' stroke-width='2'" +
                     "        stroke-linecap='round' stroke-linejoin='round'" +
                     "        style='position:absolute; top:50%; left:50%; transform:translate(-50%, -50%);'>" +
                     "       <g>" +
                     "       <animateTransform attributeName='transform'" +
                     "                           type='rotate'" +
                     "                           from='0 12 12'" +
                     "                           to='360 12 12'" +
                     "                           dur='1s'" +
                     "                           repeatCount='indefinite'/>" +
                     "       <path d='M12 2a10 10 0 1 0 10 10'/>" +
                     "       </g>" +
                     "   </svg>" +
                     "</div>";

        $(document.body).append( html );

        $target.css('opacity', '0.5');

        // Block mouse and touch events on the overlay
        $("#" + uid).on( "mousedown mouseup click dblclick contextmenu touchstart touchmove touchend", function( e )
        {
            e.preventDefault();
            e.stopPropagation();
        } );

        // Block keyboard
        $(window).on( "keydown.loading." + uid + " keypress.loading." + uid, function( e )
        {
            if( isBody || target.contains( e.target ) )
            {
                e.preventDefault();
                e.stopPropagation();
            }
        });

        // Blur currently focused element if it is inside the blocked area
        if( document.activeElement && (isBody || target.contains( document.activeElement )) )
            document.activeElement.blur();

        return uid;
    },

    /**
     * Oculta el previamente mostrado indicador de "carga en proceso".
     *
     * @param {String} uuid El UUID devuelto por ::showLoading(...)
     * @returns {void} Nothing
     */
    hideLoading : function( uuid )
    {
        let $element = $("#"+ uuid);

        if( $element.length > 0 )
        {
            let targetSelector = $element.data('target-selector');
            let originalOpacity = $element.data('original-opacity') || '1';
            let $targetElement = $(targetSelector);

            if( $targetElement.length > 0 )
                $targetElement.css('opacity', originalOpacity);

            $element.remove();
            $(window).off( ".loading." + uuid );
        }
    },

    /**
     * Shows help inside a dialog.
     * <p>
     * Note: A function key (e.g. F1) can not be associated with a function becasue WebBrowsers use them.
     *
     * @param {Strin} url
     */
    help : function( url )
    {
        url = p_base.isEmpty( url ) ? "index" : url.trim().toLowerCase();
        url = url.endsWith( ".html" ) ? url : url +".html";

        let self = this;
        let uuid = p_base.uuid();
        let sDiv = '<div id="'+ uuid +'" style="background-color:#f0ffff"></div>';

        $(sDiv)
            .dialog( {  title   : "Help",
                        autoOpen: true,
                        width   : self.getBestWidth(  "70%", 1400 ),
                        height  : self.getBestHeight( "70%", 1100 ),
                        modal   : true,
                        close   : function() { $(this).dialog('destroy'); }
                     } ).prev(".ui-dialog-titlebar")
                        .css("background","#afdcec");

        // showLoading( uuid ); --> Does not work in dialogs

        $('#'+uuid).load( url,
                          function()
                            {
                                  let sHtml = $('#'+uuid).html();
                                  let regex = sHtml.match( /<title>(.*?)<\/title>/ );

                                  if( regex && regex.length > 1 )
                                      $('#'+uuid).dialog( 'option', 'title', regex[1] );
                                  else
                                      $('#'+uuid).dialog( 'option', 'title', "Help" );
                            } );
    },

    showClockIn( element )
    {
        element = p_base.get( element );

        if( element === null )
            throw element + ": invalid DOM element";

        $(element).text( p_base.time2String( new Date(), 'hh:mm' ) );

        setInterval( () => $(element).text( p_base.time2String( new Date(), 'hh:mm' ) ), 10 * 1000 );
    },

    //--------------------------------------------------------------------------//
    // Screen stuff
    //--------------------------------------------------------------------------//

    /**
     * Window Size : Size of the browser's viewport.<br>
     * As { width: xx, height: yy }<br>
     * <br>
     * The window size refers to the dimensions of the browser's viewport,
     * which is the area where web content is displayed.
     *
     * @returns Size of the browser's viewport
     */
    getWndSize : function()
    {
        return { width  : $(window).width().toFixed(3),
                 height : $(window).height().toFixed(3) };
    },

    /**
     * Document Size : Total size of the HTML document.<br>
     * As { width: xx, height: yy }<br>
     * <br>
     * The document size represents the total dimensions of the HTML document,
     * which can extend beyond what is visible in the viewport due to scrolling.
     *
     * @returns Total size of the HTML document.
     */
    getDocSize : function()
    {
        return { width  : $(document).width().toFixed(3),
                 height : $(document).height().toFixed(3) };
    },

    /**
     * Body Size : Size of the body element in HTML.<br>
     * As { width: xx, height: yy }<br>
     * <br>
     * The body size refers specifically to the dimensions of the <body>
     * element within an HTML document. This can differ from both window
     * and document sizes based on margins, padding, or other styles applied.
     *
     * @returns Size of the body element in HTML.
     */
    getBodySize : function()
    {
        return { width  : $('body').width().toFixed(3),
                 height : $('body').height().toFixed(3) };
    },

    /**
     * Returns requested width based on Window size.
     *
     * @param {Number|String} width
     * @param {Number} nMaxWidth
     * @param {Number} nMinWidth
     * @returns {Number}
     */
    getBestWidth : function( width = null, nMaxWidth = null, nMinWidth = null )
    {
        if( width === null )
        {
            return "auto";
        }

        if( p_base.isString( width ) )
        {
            width = width.trim();

            if( width[width.length-1] === "%" )   // v.g. "85%"
            {
                width = p_base.asInt( width.substring( 0, width.length -1 ), 80 );
                width = Number.parseInt( $(window).width() * (width / 100) );
            }
            else                                  // v.g. "85"
            {
                width = Number.parseInt( width );
            }
        }

        if( nMaxWidth !== null )
            width = Math.min( width, nMaxWidth );

        if( nMinWidth !== null )
            width = Math.max( width, nMinWidth );

        width = Math.min( width, $(window).width() - 48 );    // It is awful not to have a space between the dialog and the screen border

        return width;
    },

    /**
     * Returns requested height based on Window size.
     *
     * @param {Number|String} height
     * @param {Number} nMaxHeight
     * @param {Number} nMinHeight
     * @returns {Number}
     */
    getBestHeight : function( height = null, nMaxHeight = null, nMinHeight = null )
    {
        if( height === null )
        {
            return "auto";
        }

        if( p_base.isString( height ) )
        {
            height = height.trim();

            if( height[height.length-1] === "%" )   // v.g. "85%"
            {
                height = p_base.asInt( height.substring( 0, height.length -1 ), 80 );
                height = Number.parseInt( $(window).height() * (height / 100) );
            }
            else                                    // v.g. "85"
            {
                height = p_base.asInt( height, 80 );
            }
        }

        if( nMaxHeight !== null )
            height = Math.min( height, nMaxHeight );

        if( nMinHeight !== null )
            height = Math.max( height, nMinHeight );

        height = Math.min( height, $(window).height() - 48 );    // It is awful not to have a space between the dialog and the screen border

        return height;
    },

    //------------------------------------------------------------------------//
    // PRIVATE (ACCESSORY) FUNCTIONS
    //------------------------------------------------------------------------//

    _downloadFileAndAttach_ : function( bAppend, sServerFile, target, onLoaded = null )
    {
        target = p_base.get( target );

        if( target === null )
            throw "Invalid DOM element: "+ target;

        let self = this;

        $.ajax( {
                  async  : false,     // Must be sync.
                  type   : "GET",
                  url    : p_base.doURL( sServerFile ),
                  error  : (xhr)  => self.showAjaxError( xhr ),
                  success: (html) => {
                                         if( bAppend )  $(target).append( html );
                                         else           $(target).html( html );

                                         if( onLoaded !== null )
                                            onLoaded( target, html );
                                     }
                } );
    }
};
}