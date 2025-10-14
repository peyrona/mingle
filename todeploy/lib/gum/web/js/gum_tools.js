// ---------------------------------------------------------------------------//
//          A bunch of generic functions needed by GUM
// ---------------------------------------------------------------------------//

"use strict";

if( typeof gt === "undefined" )
{
var gt =
{
    //---------------------------------------------------------------------------//
    // PIXELS CONVERSIONS
    //---------------------------------------------------------------------------//

    // Width  of the document can be wider  or norrower than what is being seen ($(window).width())
    // Height of the document can be larger or shorter  than what is being seen ($(window).height())

    pixels2vw : function( pixels )
    {
        return pixels / $(document).width();             // Can not be parseInt()
    },

    pixels2vh : function( pixels )
    {
        return pixels / $(document).height();            // Can not be parseInt()
    },

    vw2pixels : function( vw )
    {
        return parseInt( $(document).width() * vw );     // Must be parseInt()
    },

    vh2pixels : function( vh )
    {
        return parseInt( $(document).height() * vh );    // Must be parseInt()
    },

    //---------------------------------------------------------------------------//
    // LOCAL STORAGE MANAGEMENT
    //---------------------------------------------------------------------------//

    /**
     * Stores information (key,value) in localStorage.
     *
     * @param {type} key
     * @param {type} value
     * @param {type} nValidFor Seconds from now to consider the information obsolete. x == 0 to last forever, x < 0 uses session-storage. 0 by default.
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
     * @param {Any} def The default value to be returned if the key does not exists.
     * @returns The value associated with received key or the default one if the key does not exists.
     */
    read : function( key, def = null )
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

        return def;
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

    //----------------------------------------------------------------------------------//
    // FILES NAME NORMALIZATION
    //----------------------------------------------------------------------------------//

    normalizeFileName : function( name )
    {
        return name.replace( /[<>:"\/\\|?*]/g, '_' );
    },

    normalizeHtmlName : function( name )
    {
        return this.normalizeFileName( name ) +
               (name.toLowerCase().endsWith('.html') ? '' : '.html');
    },

    //----------------------------------------------------------------------------------//
    // UPLOAD FILES AND IMAGES AND VIEW UPLOADED IMAGES
    //----------------------------------------------------------------------------------//

    uploadImage : function( fnOnOk )
    {
        gt._upload_( { target: 'board_images', board: gum.getDashboardName() }, fnOnOk );

        return this;
    },

    uploadFile : function( sBaseDir, fnOnOK )
    {
        gt._upload_( { target: 'user_files', basedir: sBaseDir }, fnOnOK );

        return this;
    },

    /**
     * Uploads files to server.
     * Currently these are allowed ("target" key in passed object is used to find the
     * type of file that is being upladed).
     * <ul>
     *    <li>"board_images" --> used by dashboards</li>
     *    <li>"user_files"   --> used by file manager</li>
     * </ul>
     * @param {Object} oData
     * @param {Function} fnOnOk
     */
    _upload_ : function( oData, fnOnOk = null )
    {
        function __upload__( event, oData, fnOnOK )
        {
            event.stopPropagation();    // Stops happening stuff
            event.preventDefault();     // Fully stop happening stuff

            if( fnOnOK === null )
                fnOnOK = () => p_app.alert('Upload successfull');

            let uid      = p_app.showLoading();
            let formData = new FormData();
            let files    = event.target.files;          // Access all selected files

            for( let n = 0; n < files.length; n++ )
                formData.append( "file", files[n] );    // Use a common name "file" for all files

            for( const key in oData )
                formData.append( key, oData[key] );     // Info for the server

            event.target.value = "";   // Now that I have all data, I can reset the event. This is needed to allow uploading same file again (needed if user reduced its size: file size is checked by the server)

            $.ajax( { url        : "/gum/upload",
                      type       : "POST",
                      data       : formData,
                      contentType: false,         // Este y el siguiente hay que ponerlos a false, lo dice aquí:
                      processData: false,         // https://developer.mozilla.org/es/docs/Web/Guide/Usando_Objetos_FormData
                      error      : (xhr) => p_app.showAjaxError( xhr ),
                      complete   : ()    => p_app.hideLoading( uid ),
                      success    : fnOnOK } );
        };

        if( p_base.get('input_2_upload_files') === null )
        {
            // To upload files (it is hidden): '.click()' is triggered by a button
            $('body').append('<input type="file" multiple id="input_2_upload_files" style="display:none;">');

            // I add the event to the HTML element that opens the dialog to select files.
            // In this way, when the dialog to select files is closed, the selected files are processed.
            p_base.get('input_2_upload_files').addEventListener( 'change', (evt) => __upload__( evt, oData, fnOnOk ) );
        }

        p_base.get('input_2_upload_files').click();
    },

    getImages : function( fnOnOk )
    {
        let uid = p_app.showLoading();

        $.ajax( {
                  url     : "/gum/ws/images",    // Must be this URL: do not change
                  type    : "GET",
                  data    : { board: gum.getDashboardName() },
                  error   : (xhr ) => p_app.showAjaxError( xhr, "Error loading board images:" ),
                  complete: ()     => p_app.hideLoading( uid ),
                  success : (as)   => fnOnOk( as.sort() )
                } );
    },

    delImage : function( sImage, fnOnOk = null )
    {
        if( ! fnOnOk )
            fnOnOk = () => {};

        p_app.confirm( 'Delete "'+ sImage +'"?',
                        function()        // NOTA: los DELETE exigen q los params se pasen en la URL.
                        {
                            let url = new URL( window.location.origin +'/gum/ws/images' );    // Can not use here: p_base.doURL(...). Because the URL contains the name of the board.
                                url.searchParams.set( 'board', gum.getDashboardName() );
                                url.searchParams.set( 'image', sImage );
                                   
                            $.ajax( {   url    : url.toString(),
                                        type   : "DELETE",
                                        error  : (xhr) => p_app.showAjaxError( xhr ),
                                        success: fnOnOk
                                    } );
                        } );
    },

    selectImg4Text : function( inputText )
    {
        if( ! inputText.is('input[type="text"]') )
            throw "Must be an 'input' of type 'text'";

        gt.selectImageDialog( (sImg) => {
                                          $(inputText).val(sImg);
                                          $(inputText).trigger('change');   // Has to be done programatically
                                        } );
    },

    /**
     * Opens a dialog to select and delete images associated with the dashboard.
     *
     * @param {*} fnOnSelect To be invoked when an image is selected by the user: function receives the image name.
     * @param {*} fnOnDelete To be invoked when an image is deleted  by the user: function receives the image name.
     */
    selectImageDialog : function( fnOnSelect, fnOnDelete = null )
    {
        // __selectImageDialog__(...) is not here because it is too long and decreases code readability

        if( ! p_base.isFunction( fnOnSelect ) )
            fnOnSelect = () => alert( "fnOnSelect is not a function" );

        if( ! fnOnDelete )
            fnOnDelete = (sImg) => gt.delImage( sImg );

        gt.getImages( (as) => gt.__selectImageDialog__( as, fnOnSelect, fnOnDelete ) );
    },

    __selectImageDialog__ : function( asImgesName, fnOnSelect, fnOnDelete )
    {
        const $dialog  = $('<div>').attr('title', 'Dashboard Associated Images');
        const $imgGrid = $('<div>').addClass('image-grid');

        let managedImagesName = [...asImgesName];
        let selectedImageUrl  = null;

        // Populate grid with images
        function refreshImageGrid()
        {
            $imgGrid.empty();

            managedImagesName.forEach( name =>
            {
                // Create container for each image
                const $container = $('<div>').addClass('image-container');

                // Create image
                const $img = $('<img>').attr('src', p_base.doURL('/images/'+ name));

                // Create hover icons container
                const $hoverIcons = $('<div>').addClass('hover-icons');

                // Select icon
                const $selectIcon = $('<div>')
                    .addClass('hover-icon select-icon')
                    .html('&#10004;') // Checkmark
                    .on('click', function(e)
                                    {
                                        e.stopPropagation();
                                        fnOnSelect(name);
                                        $dialog.dialog("close");
                                    });

                // Delete icon
                const $deleteIcon = $('<div>')
                                            .addClass('hover-icon delete-icon')
                                            .html('&#10006;') // Multiplication sign
                                            .on('click', function(e)
                                                            {
                                                                e.stopPropagation();
                                                                // Remove the image from managed URLs
                                                                const index = managedImagesName.indexOf(name);
                                                                if (index > -1)
                                                                {
                                                                    managedImagesName.splice(index, 1);
                                                                    fnOnDelete(name);
                                                                    refreshImageGrid();
                                                                }
                                                            });

                // Combine image and hover icons
                $hoverIcons
                    .append($selectIcon)
                    .append($deleteIcon);

                $container
                    .append($img)
                    .append($hoverIcons);

                $imgGrid.append($container);
            });
        }

        refreshImageGrid();    // Initial grid population

        $dialog.append($imgGrid);    // Append grid to dialog

        $dialog.dialog( {
                            width   : p_app.getBestWidth('70%', null, 680),
                            height  : p_app.getBestWidth('70%', null, 480),
                            modal   : true,
                            autoOpen: true,
                            close   : function() { $(this).dialog('destroy'); }
                        } );
    }
};
}

//---------------------------------------------------------------------------//
// AUTHENTICATION MANAGEMENT
//---------------------------------------------------------------------------//

class SessionManager
{
    constructor()
    {
        this.onSessionExpired = null;
    }

    setSessionExpiration( callback )
    {
        this.onSessionExpired = callback;
        
        $.ajax( {   url     : '/gum/ws/util/sessionTimeout',
                    method  : 'GET',
                    error   : (xhr)  => p_app.setSessionTimeout( this._doSessionExpired_, 30 ),
                    success : (secs) => p_app.setSessionTimeout( this._doSessionExpired_, Number(secs)/60 )    // Received in seconds, sent in minutes
                } );
    }

    _doSessionExpired_()
    {
        $.ajax( {
                    url     : '/gum/logout',
                    method  : 'POST',
                    error   : (xhr) => p_app.showAjaxError( xhr ),
                    success : () => {
                              if( this.onSessionExpired )
                                  this.onSessionExpired();
                            }
                } );
    }
}

class LoginUI
{
    constructor()
    {
        this.container = null;
        this.title     = null;
    }

    injectDialog( container, title )
    {
        this.container = container;
        this.title     = title;
        
        $(container).html( this._getDialogHTML_().replace( '{*Title*}', title ) );
    }

    showDialog()
    {
        $(this.container).show();
    }

    hideDialog()
    {
        $(this.container).hide();
    }

    showMessage( message )
    {
        $('#auth-dlg-msg').text(message).show();
        setTimeout( () => $('#auth-dlg-msg').hide(), 4000 );
    }

    showSessionExpiredMessage()
    {
        $('#auth-dlg-msg').html("Due to inactivity, session expired.<br>You have to login again.").show();
    }

    clearForm()
    {
        $('#auth-login-form input[type="text"]:first'    ).val('');
        $('#auth-login-form input[type="password"]:first').val('');
    }

    focusFirstField()
    {
        $('#auth-login-form :input:first').focus();
    }

    _getDialogHTML_()
    {
        return `
            <div style="display:flex; min-height:100vh; justify-content:center; align-items:center;">
                <div style="position:fixed; top:50%; left:50%; transform:translate(-50%,-50%); width:100%; max-width:400px; padding:2rem; border-radius:8px; box-shadow: 0 2px 10px rgba(10, 10, 10, 0.3); background-color:#FFFAF0;">
                    <p style="font-size:1.4rem; font-weight:500; margin-bottom:2rem; text-align:center; color:#363636;">::: {*Title*} :::</p>

                    <form id="auth-login-form">
                        <div class="field">
                            <div class="control has-icons-left">
                                <input class="input" type="text" placeholder="Username" required>
                                <span class="icon is-small is-left">
                                    <i class="ti ti-user-filled"></i>
                                </span>
                            </div>
                        </div>

                        <div class="field">
                            <div class="control has-icons-left">
                                <input class="input" type="password" placeholder="Password" required>
                                <span class="icon is-small is-left">
                                    <i class="ti ti-lock-filled"></i>
                                </span>
                            </div>
                        </div>

                        <div style="margin-top: 1.5rem;">
                            <div class="control">
                                <button class="button is-primary is-fullwidth">Sign In</button>
                            </div>
                        </div>
                    </form>

                    <p id="auth-dlg-msg" style="display:none; margin:1rem; text-align:center; color:red;"></p>
                </div>
            </div>
        `;
    }
}

class AuthenticationService
{
    constructor( options = {} )
    {
        this.container      = options.container;
        this.title          = options.title || 'Login';
        this.onSuccess      = options.success || (() => {});
        this.sessionManager = options.sessionManager || new SessionManager();
        this.loginUI        = options.loginUI || new LoginUI();
        this.isStarted      = false;
    }

    start()
    {
        if( ! this.container || this.container.charAt(0) !== '#' )
            throw "Container must start by '#'";

        this.isStarted = true;
        
        // Setup UI
        this.loginUI.injectDialog( this.container, this.title );
        
        // Setup session manager callback
        this.sessionManager.onSessionExpired = () => {
            this.loginUI.injectDialog( this.container, this.title );
            this.loginUI.showSessionExpiredMessage();
            this.loginUI.showDialog();
        };

        // Attaches the event handler to the form
        $(document).off( 'submit', '#auth-login-form' )    // To avoid duplicating it
                   .on(  'submit', '#auth-login-form', (event) => this._handleLogin_(event) );

        // Intercepts AJAX errors to show the dialog
        $(document).ajaxError( (event, xhr) => this.doDefaultOnAjaxError( xhr ) );
    }

    throw401AjaxError()
    {
        const fakeXHR = { status      : 401,   // Simulate a 401 Unauthorized error
                          statusText  : "Unauthorized",
                          responseText: "Authentication required" };

        // Create a jqXHR object (jQuery's version of XMLHttpRequest)
        const jqXHR = $.extend( new $.Deferred(), fakeXHR ) ;

        // Options object (ajax settings)
        const ajaxOptions = { url: '/some-fake-url', type: 'GET' };

        // Trigger the ajaxError event manually with proper parameters (it will captured by line '$(document).ajaxError(...)' at ::start())
        $(document).trigger( 'ajaxError', [jqXHR, ajaxOptions, 'Unauthorized'] );
    }

    doDefaultOnAjaxError( xhr )
    {
        if( xhr.status === 401 )
        {
            this.loginUI.showDialog();
        }
        else
        {
            p_app.showAjaxError( xhr );
        }
    }

    _handleLogin_( event )
    {
        event.preventDefault();

        $.ajax( {
                    url        : '/gum/login',
                    method     : 'POST',
                    contentType: 'application/json',
                    data       : JSON.stringify( { username: $('#auth-login-form input[type="text"]:first'    ).val(),
                                                   password: $('#auth-login-form input[type="password"]:first').val() } ),
                    success    : (response) =>
                                {
                                    $('#auth-dlg-msg').hide();            // In case it was showing the "...session expired..." message
                                    this.loginUI.hideDialog();
                                    this.onSuccess( response );
                                    this.sessionManager.setSessionExpiration();
                                },
                    error      : (xhr) =>
                                {
                                    if( xhr.status === 401 )
                                    {
                                        this.loginUI.showMessage('Invalid username and/or password');
                                        this.loginUI.focusFirstField();
                                    }
                                    else
                                    {
                                        p_app.showAjaxError( xhr );
                                    }
                                },
                    complete    : () => {
                                    this.loginUI.clearForm();    // Must set to empty, so when session expires, and the dialog is shown again, the values of the fields are not shown
                                }
                } );
    }
}



//----------------------------------------------------------------------------------//
// Background class
//----------------------------------------------------------------------------------//

class Background
{
    constructor( encoded = null )
    {
        this.imgURL     = (encoded === null ? null : encoded.image.url    );
        this.imgMode    = (encoded === null ? null : encoded.image.mode   );
        this.clrFrom    = (encoded === null ? null : encoded.color.from   );
        this.clrTo      = (encoded === null ? null : encoded.color.to     );
        this.clrDegrees = (encoded === null ? null : encoded.color.degrees);
    }

    encode()
    {
        return { "image": {
                            "url" : this.imgURL,
                            "mode": this.imgMode
                          },
                 "color": {
                            "from"   : this.clrFrom,
                            "to"     : this.clrTo,
                            "degrees": this.clrDegrees
                          } };
    }

    setImageURL( url )
    {
        this.imgURL = url;
        return this;
    }

    setImageMode( mode )
    {
        this.imgMode = mode;
        return this;
    }

    setColorFrom( from )
    {
        this.clrFrom = from;
        return this;
    }

    setColorTo( to )
    {
        this.clrTo = (to ? to : this.clrFrom);;
        return this;
    }

    setColorDegrees( deg )
    {
        deg = (p_base.isNumber( deg ) ? parseInt( deg ) : 0);
        deg = deg < 0 ? 0 : deg;
        deg = deg > 359 ? 0 : deg;

        this.clrDegrees = deg;

        return this;
    }

    getImageURL()
    {
        return this.imgURL;
    }

    getImageMode()
    {
        return this.imgMode;
    }

    getColorFrom()
    {
        return this.clrFrom;
    }

    getColorTo()
    {
        return this.clrTo;
    }

    getColorDegrees()
    {
        return this.clrDegrees;
    }

    applyTo( node = null )    // node == DOM entity
    {
        let sBack = "";

        if( p_base.isNotEmpty( this.getImageURL() ) )
            sBack = 'url(images/'+ this.getImageURL() +') no-repeat';

        if( p_base.isNotEmpty( this.getColorFrom() ) )
        {
            if( sBack )          // if sBack is not empty
                sBack += ', ';

            sBack += 'linear-gradient('+ this.getColorDegrees() +'deg, '+
                                         this.getColorFrom() +', '+
                                         this.getColorTo() +')';
        }

        if( p_base.isNotEmpty( sBack ) )
        {
            $(node).css( 'background', sBack );

            if( p_base.isNotEmpty( this.getImageURL() ) )
                $(node).css( 'background-size', this.getImageMode() );    // mode: 'auto', 'cover' or 'contain'
        }
    }
}

//----------------------------------------------------------------------------------//
// Gadget Style
//----------------------------------------------------------------------------------//

class GadgetStyle
{
    constructor( oGadget )
    {
        this.gadget = oGadget;

        // If oGadget.style is an object, applies all information containded in 'style'

        this.setBackground();

        for( const sWhich of ['top','left','bottom','right'] )
        {
            this.setBorderStyle( sWhich );
            this.setBorderWidth( sWhich );
            this.setBorderColor( sWhich );

            this.setPadding( sWhich );
        }
    }

    //---------------------------------------------------------------------------//

    /**
     *
     * @param {String} sWhich One of: 'top', 'left', 'bottom', 'right'.
     * @returns
     */
    getBorderStyle( sWhich = "" )    // "" will produce no result but will not produce an error on toLowerCase()
    {
        let value = this._valOf_('border-'+ sWhich.toLowerCase() +'-style');

        return (p_base.isNotEmpty( value ) ? p_base.capitalize( value ) : "None");
    }

    /**
     * Returns an Object as: { width: <nWidth>, unit: <sUnit> }
     *
     * @param {String} sWhich One of: 'top', 'left', 'bottom', 'right'.
     * @returns {Object} As  { width: <nWidth>, unit: <sUnit> }
     */
    getBorderWidth( sWhich = "" )    // "" will produce no result but will not produce an error on toLowerCase()
    {
        let oValue = this._valOf_('border-'+ sWhich.toLowerCase() +'-width');

        if( p_base.isUndefined( oValue ) )
            oValue = { width: null, unit: null };

        oValue.width = (oValue.width ? oValue.width : 0   );
        oValue.unit  = (oValue.unit  ? oValue.unit  : 'px');

        return oValue;
    }

    /**
     *
     * @param {String} sWhich  One of: 'top', 'left', 'bottom', 'right'.
     * @returns
     */
    getBorderColor( sWhich = "" )    // "" will produce no result but will not produce an error on toLowerCase()
    {
        let value = this._valOf_('border-'+ sWhich.toLowerCase() +'-color');

        return (p_base.isNotEmpty( value ) ? value : "#000000");
    }

    /**
     * Set gadget::container border style.
     *
     * When 'sStyle' parameter is not passed, it will attempted to be readed
     * from the Gadget properties and set them to the Gadget::getContainer().
     *
     * @param {String} sWhich
     * @param {String} sStyle
     * @returns Itself
     */
    setBorderStyle( sWhich = "",                                // "" will produce no result but will not produce an error on toLowerCase()
                    sStyle = this.getBorderStyle( sWhich ) )    // Defaults to "None"
    {
        let css = 'border-'+ sWhich.toLowerCase() +'-style';

        sStyle = p_base.capitalize( sStyle );

        this._setPair_(  css, sStyle );
        this._applyCSS_( css, sStyle );

        return this;
    }

    /**
     * Set gadget::container border width (including units).
     *
     * When 'nWidth' and 'sUnit' parameters are not passed, they will attempted to be readed
     * from the Gadget properties and set them to the Gadget::getContainer().
     *
     * @param {String} sWhich
     * @param {Number} nWidth
     * @param {String} sUnit
     * @returns Itself
     */
    setBorderWidth( sWhich = "",                                     // "" will produce no result but will not produce an error on toLowerCase()
                    nWidth = this.getBorderWidth( sWhich ).width,    // Defaults to 0
                    sUnit  = this.getBorderWidth( sWhich ).unit )    // Defaults to 'px'
    {
        let css = 'border-'+ sWhich.toLowerCase() +'-width';

        this._setPair_(  css, { width: nWidth, unit: sUnit } );
        this._applyCSS_( css, nWidth + sUnit );

        return this;
    }

    /**
     * Set gadget::container border color.
     *
     * When 'sColor' parameter is not passed, it will attempted to be readed
     * from the Gadget properties and set them to the Gadget::getContainer().
     *
     * @param {String} sWhich
     * @param {String} sColor
     * @returns Itself
     */
    setBorderColor( sWhich = "",                                // "" will produce no result but will not produce an error on toLowerCase()
                    sColor = this.getBorderColor( sWhich ) )    // Defaults to "#000000"
    {
        let css = 'border-'+ sWhich.toLowerCase() +'-color';

        this._setPair_(  css, sColor );
        this._applyCSS_( css, sColor );

        return this;
    }

    /**
     * Returns an Object as: { width: <nWidth>, unit: <sUnit> }
     *
     * @param {String} sWhich
     * @returns {Object} As  { width: <nWidth>, unit: <sUnit> }
     */
    getPadding( sWhich = "" )    // "" will produce no result but will not produce an error on toLowerCase()
    {
        let value = this._valOf_( 'padding-'+ sWhich.toLowerCase() );

        return (p_base.isNotEmpty( value ) ? value : { width: 0, unit: 'px' });
    }

    /**
     * Set gadget::container padding.
     *
     * When optional parameters are not passed, they will attempted to be readed
     * from the Gadget properties and set them to the Gadget::getContainer().
     *
     * @param {String} sWhich
     * @param {Number} nWidth
     * @param {String} sUnit
     * @returns Itself
     */
    setPadding( sWhich = "",                                 // "" will produce no result but will not produce an error on toLowerCase()
                nWidth = this.getPadding( sWhich ).width,    // Defaults to 0
                sUnit  = this.getPadding( sWhich ).unit )    // Defaults to 'px'
    {
        let css = 'padding-'+ sWhich.toLowerCase();

        this._setPair_(  css, { width: nWidth, unit: sUnit } );
        this._applyCSS_( css, nWidth + sUnit );

        return this;
    }

    getBackground()
    {
        return new Background( this._valOf_('background') );
    }

    /**
     * Set gadget::container background.
     *
     * When optional parameters are not passed, they will attempted to be readed
     * from the Gadget properties and set them to the Gadget::getContainer(); but
     * all or none have to be passed.
     *
     * @param {String} imgURL
     * @param {String} imgMode
     * @param {String} clrFrom
     * @param {String} clrTo
     * @param {Number} clrDegrees
     * @returns Itself
     */
    setBackground( imgURL = null, imgMode = null, clrFrom = null, clrTo = null, clrDegrees = null )
    {
        if( imgURL !== null && imgMode !== null && clrFrom !== null && clrTo !== null && clrDegrees !== null )
            this._setPair_( 'background', new Background()
                                                .setImageURL(     imgURL )
                                                .setImageMode(    imgMode )
                                                .setColorFrom(    clrFrom )
                                                .setColorTo(      clrTo )
                                                .setColorDegrees( clrDegrees )
                                                .encode() );

        this.getBackground().applyTo( this.gadget.getContainer() );

        return this;
    }

    //----------------------------------------------------------------------------------//
    // PRIVATE METHODS

    /**
     * If the Gadget has passed sCssName as a key, it is used to set the
     * Gadget::getContainer() (a 'div') CSS value.
     *
     * @param {String} sCssName
     * @param {Any}    value If value is not passed, the value is obtained from the gadget saved keys.
     */
    _applyCSS_( sCssName, value = null )
    {
        if( value === null && this.gadget.style && this.gadget.style[sCssName] )
            value = this.gadget.style[sCssName];

        if( value !== null )
            this.gadget.getContainer().css( sCssName, value );
    }

    _valOf_( sKey, xDefault )
    {
        let value = (this.gadget.style ? this.gadget.style[sKey] : undefined);

        return (value !== undefined ? value : xDefault);
    }

    _setPair_( sKey, value )
    {
        if( ! this.gadget.style )
            this.gadget['style'] = {};

        this.gadget.style[sKey] = value;
    }
}