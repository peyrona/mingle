//----------------------------------------------------------------------------//
// Base class for all Gadgets                                                 //
//----------------------------------------------------------------------------//

"use strict";

class GumGadget
{
    /**
     * Creates a new instance of the gadget passed at 'props.class'.
     *
     * @param {Object|String} props Either a set of properties to create a Gadget or a Gadget class name.
     * @returns {GumSSD|GumCheck|GumButton|GumChart|GumText|GumGauge} An instance of specified.
     */
    static instantiate( props )
    {
        let sGadgetType;

        if( p_base.isObject( props ) )
        {
            sGadgetType = props.class;
        }
        else
        {
            sGadgetType = props;
            props = null;
        }

        switch( sGadgetType )
        {
            case "button"   : return new GumButton(    props );
            case "chart"    : return new GumChart(     props );
            case "check"    : return new GumCheck(     props );
            case "gauge"    : return new GumGauge(     props );
            case "pie"      : return new GumPie(       props );
            case "ssd"      : return new GumSSD(       props );
            case "text"     : return new GumText(      props );
            case "scheduler": return new GumScheduler( props );
            default         : throw "Invalid gadget type: " + sGadgetType;
        }
    }

    //------------------------------------------------------------------------------------//

    constructor( sClass, props )    // Some gadgets have more than 1 ExEn and/or more than 1 device
    {
        // These props are not saved ----------
        this.id           = p_base.uuid();
        this._sValue_     = null;           // Current value of the gadget (if any) as string: used to avoid unnecessary updates
        this.aListenerIds = [];
        this.fnOnEditEnd  = null;           // Callback used by Grid Layout
        this._isEditing_  = false;
        this._isChanged_  = false;          // If true, this gadget's properties dialog was open at least once
        this._hasErrors_  = false;          // If true, this gadget has an error (used to avoid multiple alerts and to avoid showing the gadget)

        this._$container_ = $('<div class="gadget-container"></div>');
        const $contentArea = $('<div class="gadget-content" style="width: 100%; height: 100%; position: relative;"></div>');
        this._$container_.append($contentArea);
        // ------------------------------------

        if( p_base.isNotEmpty( props ) )
        {
            for( const key in props )
                this[key] = props[key];

            if( p_base.isNotEmpty( this.style ) )
                new GadgetStyle( this );    // This applies all styles to ::getContainer()
        }
        else
        {
            // These properties are common to 'free' layout and 'grid' layout
            this.class = sClass.trim().toLowerCase();

            this.width  = null;    // To be rewritten by subclasses
            this.height = null;    // To be rewritten by subclasses

            // Following propeties are needed by Free layout (when using Grid layout they will be ignored)
            // (they can be rewritten by subclasses)

            this.x =  25;
            this.y =  40;
            this.z = 100;

            // Following propeties are needed by Grid layout (when using Free layout they will be ignored)
            // (they can be rewritten by subclasses)

            // TODO: añadir las props que se necesiten
        }
    }

    //------------------------------------------------------------------------------------//

    /**
     * Returns the DIV used to show the gadget.
     * @returns The DIV used to show the gadget.
     */
    getContainer()
    {
        return this._$container_;
    }

    /**
     * Returns the DIV used to show the gadget's content.
     * @returns The DIV used to show the gadget's content.
     */
    getContentArea()
    {
        return this._$container_.find('.gadget-content');
    }

    isResizable()
    {
        return true;
    }

    isEditing()
    {
        return this._isEditing_;
    }

    isChanged()
    {
        return this._isChanged_;
    }

    edit( bStart )
    {
        if( ! p_base.isBoolean( bStart ) )
            throw "Boolean parameter needed";

        if( this.isEditing() && bStart )
            return;

        if( ! this.isEditing() && ! bStart )
            return;

        this._isEditing_ = bStart;

        if( bStart )
        {
            $('#div-dlg_properties')           // Hides any gadget properties dialog that could be open
                .find('[id^="properties-"]')
                .each( function() { $(this).hide(); } );
        }
        else
        {
            this._isChanged_ = true;

            if( this.fnOnEditEnd )
                this.fnOnEditEnd( this );

            this.show();

            // It is not needed todo: gum.save(); because before 'preview' and before page::unload save is invoked
        }

        return this;
    }

    clone()
    {
        try
        {
            const props    = this.distill();
            const deepCopy = this._deepCopy_( props );

            return GumGadget.instantiate( deepCopy );
        }
        catch( error )
        {
            console.error('Error cloning gadget:', error);
            throw new Error('Failed to clone gadget: ' + error.message);
        }
    }

    destroy()
    {
        this._resetListeners();    // has to be 1st

        if( this._$container_ )
            this._$container_.remove();

        this._$container_ = null;
        this._isEditing_  = false;
        this._isChanged_  = false;
        this._hasErrors_  = false;
        this.id           = null;
        this.class        = null;

        return this;
    }

    /**
     * Returns a copy of this gadget properties (without unneeded properties).
     */
    distill( asExclude = [] )    // Received arguments (array of strings) will not be included in returning object
    {
        if( ! p_base.isArray( asExclude ) )
            throw "Must be an array of strings";

        asExclude.push( "id"           );
        asExclude.push( "_$container_" );
        asExclude.push( "_sValue_"     );
        asExclude.push( "_isEditing_"  );
        asExclude.push( "_isChanged_"  );
        asExclude.push( "_hasErrors_"  );
        asExclude.push( "aListenerIds"   );
        asExclude.push( "fnOnEditEnd"    );
        asExclude.push( "_devValueType_" );

        let props = {};

        for( const key in this )
        {
            if( this.hasOwnProperty( key ) &&
                (! asExclude.includes( key )) )
            {
                props[key] = this[key];
            }
        }

        return props;
    }

    show(isOngoing = false)
    {
        // The isOngoing flag check remains, to avoid re-rendering during a resize/drag operation.
        if( isOngoing )
            return this;

        const $contentArea = this.getContentArea();

        // 1. Remove any previously rendered caption
        this._$container_.find('.gadget-title, .gadget-title-center').remove();

        // 2. Reset styles to a default state (no title)
        this._$container_.css({ 'display': '', 'flex-direction': '' });
        $contentArea.css({ 'flex-grow': '', 'height': '100%' });

        // 3. If no caption, the content area simply fills the container. We're done.
        if( p_base.isEmpty(this.card_title) )
            return this;

        // 4. If a caption exists, apply a flexbox layout to the main container.
        this._$container_.css({ 'display': 'flex', 'flex-direction': 'column' });

        // 5. Let the content area grow to fill the remaining space.
        $contentArea.css({ 'flex-grow': '1', 'height': '0' }); // Using height:0 with flex-grow:1 is a robust flexbox technique.

        // 6. Resolve horizontal alignment (default: center)
        let sAlign = this.card_title_align || 'center';

        // 7. Append the elements in the correct order.
        if( this.card_title_location === 'south' )
        {
            const $title = $('<div class="gadget-title"></div>').css('text-align', sAlign).html(this.card_title);
            this._$container_.append($title);
        }
        else if( this.card_title_location === 'center' )
        {
            // For center positioning, we need to overlay the title on the content area
            $contentArea.css('position', 'relative');
            const $centeredTitle = $('<div class="gadget-title-center"></div>').css('text-align', sAlign).html(this.card_title);

            if( sAlign === 'left' )
                $centeredTitle.css({ 'left': '0', 'transform': 'translateY(-55%)' });
            else if( sAlign === 'right' )
                $centeredTitle.css({ 'left': 'auto', 'right': '0', 'transform': 'translateY(-55%)' });

            this._$container_.append($centeredTitle);
        }
        else   // 'north' or undefined
        {
            const $title = $('<div class="gadget-title"></div>').css('text-align', sAlign).html(this.card_title);
            this._$container_.prepend($title);
        }

        return this;
    }

    //------------------------------------------------------------------------------------//
    // STATIC METHODS

    /**
     * Clones a &lt;template&gt; element by its ID and returns the content as a jQuery object.
     * Templates are defined in chunks/gadget_templates.html and loaded at startup.
     *
     * @param {String} sTemplateId The template element ID (e.g. "tpl-gauge", "tpl-chart").
     * @returns {jQuery} A jQuery object wrapping the cloned template content.
     */
    static cloneTemplate( sTemplateId )
    {
        let tpl = document.getElementById( sTemplateId );

        if( ! tpl )
            throw "Template not found: " + sTemplateId;

        let clone = tpl.content.cloneNode( true );

        return $(clone).children();    // Returns the top-level children as a jQuery set
    }

    //------------------------------------------------------------------------------------//
    // PROTECTED SCOPE

    // Helper method for deep copying properties
    _deepCopy_ ( obj )
    {
        if( obj === null || typeof obj !== 'object' )
            return obj;

        if( obj instanceof Date )
            return new Date(obj.getTime());

        if( Array.isArray(obj) )
            return obj.map( item => this._deepCopy_( item ) );

        // Handle regular objects
        const cloned = {};

        for( const key in obj )
        {
            if( obj.hasOwnProperty( key ) )
                cloned[key] = this._deepCopy_(obj[key]);
        }

        return cloned;
    }

    _addListener( oExEn, sDevName, fnCallBack )
    {
        let uid = gum_ws_boards.addListener( oExEn, sDevName, fnCallBack );

        this.aListenerIds.push( uid );
        gum_ws_boards.requestValue( oExEn, sDevName );
    }

    _resetListeners()
    {
        for( let uid of this.aListenerIds )
            gum_ws_boards.delListener( uid );

        this.aListenerIds = [];
    }

    _isValidValue( sType, xValue, sDevName )
    {
        if( p_base.isUndefined( xValue ) )     // If it is Undefined or null, value will not be shown, but no error will be shown neither.
            return false;                      // Care, because false or 0 are valid values.

        let strValue = new String( xValue );

        // Finally, this is not a good idea: it saves CPU sometimes, but others makes a mess -->
        // if( strValue === this._sValue_ )
        //    return false;

        switch( sType.charAt( 0 ) )
        {
            case 'N':
                if( p_base.isNumber( xValue ) )
                {
                    this._sValue_ = strValue;
                    return true;
                }

                this._showError( 'Device "'+ sDevName +'" is not a Number; its value is: '+ xValue );
                return false

            case 'B':
                if( p_base.isBoolean(  xValue ) )
               {
                    this._sValue_ = strValue;
                    return true;
                }

                this._showError( 'Device "'+ sDevName +'" is not a Boolean; its value is: '+ xValue );
                return false

            case 'S':
                if( p_base.isString( xValue ) )
                {
                    this._sValue_ = strValue;
                    return true;
                }

                this._showError( 'Device "'+ sDevName +'" is not a String; its value is: '+ xValue );
                return false

            default :
                throw "Invalid type: " + sType;
        }
    }

    _showError( sMsg )
    {
        if( ! this._hasErrors() )
            p_app.alert( sMsg );

        this._hasErrors( true );

        return this;
    }

    _hasErrors( bValue = null )
    {
        if( bValue !== null )
            this._hasErrors_ = bValue;

        return this._hasErrors_;
    }

    /**
     * Executes user-defined custom JavaScript code associated with this gadget.
     * The code receives the action, payload, and a reference to the gadget itself.
     *
     * @param {String} action  The action name from the listener callback.
     * @param {Object} payload The payload object from the listener callback.
     */
    _executeUserCode_( action, payload )
    {
        if( p_base.isEmpty( this.code_js ) )
            return;

        try
        {
            let fn = new Function( 'action', 'payload', 'gadget', this.code_js );
            fn( action, payload, this );
        }
        catch( error )
        {
            console.error( 'Gadget user code error:', error.message );
        }
    }

    /**
     * Detects whether a value is a list, pair, or scalar type.
     *
     * @param {any} xValue The raw value from the WebSocket payload.
     * @returns {string} "list", "pair", or "scalar".
     */
    _detectValueType_( xValue )
    {
        if( xValue && typeof xValue === 'object' && xValue.data !== undefined )
        {
            if( Array.isArray( xValue.data ) )
                return "list";

            if( typeof xValue.data === 'object' )
                return "pair";
        }

        return "scalar";
    }

    /**
     * Extracts a scalar value from a list or pair device value using the given accessor.
     *
     * For list values: accessor is a 1-based numeric index (Une convention).
     * For pair values: accessor is a string key.
     * When accessor is null/empty, the original value is returned unchanged.
     *
     * @param {any}         xValue    The raw value from the WebSocket payload.
     * @param {string|null} sAccessor The accessor (numeric index for list, string key for pair), or null.
     * @param {string}      sDevName  Device name (for error messages).
     * @returns {any} The extracted scalar, the original value, or null on error.
     */
    _resolveAccessor_( xValue, sAccessor, sDevName )
    {
        if( sAccessor === null || sAccessor === undefined || sAccessor === "" )
        {
            if( xValue && typeof xValue === 'object' && xValue.data !== undefined )
                return null;    // Composite value (list/pair) requires an accessor to extract a scalar

            return xValue;
        }

        if( ! xValue || typeof xValue !== 'object' || xValue.data === undefined )
        {
            this._showError( 'Device "'+ sDevName +'": accessor "'+ sAccessor +'" requires a list or pair value' );
            return null;
        }

        if( Array.isArray( xValue.data ) )     // list type: accessor is a 1-based index
        {
            let n = parseInt( sAccessor );

            if( isNaN( n ) || n < 1 )
            {
                this._showError( 'Device "'+ sDevName +'": list index must be a positive integer, got: "'+ sAccessor +'"' );
                return null;
            }

            if( n > xValue.data.length )
            {
                this._showError( 'Device "'+ sDevName +'": list index '+ n +' out of range (size: '+ xValue.data.length +')' );
                return null;
            }

            return xValue.data[n - 1];
        }

        if( typeof xValue.data === 'object' )    // pair type: accessor is a key
        {
            let sKey  = sAccessor.trim().toLowerCase();    // pair keys are case-insensitive (normalized to lowercase)
            let value = xValue.data[sKey];

            if( value === undefined )
            {
                this._showError( 'Device "'+ sDevName +'": key "'+ sAccessor +'" not found in pair' );
                return null;
            }

            return value;
        }

        this._showError( 'Device "'+ sDevName +'": unexpected value structure for accessor "'+ sAccessor +'"' );
        return null;
    }

    //------------------------------------------------------------------------------------//
    // PRIVATE SCOPE
}