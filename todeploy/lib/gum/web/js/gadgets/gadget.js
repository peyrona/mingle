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
            case "button": return new GumButton( props );
            case "chart" : return new GumChart(  props );
            case "check" : return new GumCheck(  props );
            case "gauge" : return new GumGauge(  props );
            case "ssd"   : return new GumSSD(    props );
            case "text"  : return new GumText(   props );
            default      : throw "Invalid gadget type: " + sGadgetType;
        }
    }

    //------------------------------------------------------------------------------------//

    constructor( sClass, props )    // Some gadgets have more than 1 ExEn and/or more than 1 device
    {
        // These props are not saved ----------
        this.id           = p_base.uuid();
        this.aListenerIds = [];
        this.fnOnEditEnd  = null;           // Callback used by Grid Layout
        this._isEditing_  = false;
        this._isChanged_  = false;          // If true, this gadget's properties dialog was open at least once
        this._$container_ = $('<div class="gadget-container"></div>');
        this._hasErrors_  = false;          // If true, this gadget has an error (used to avoid multiple alerts and to avoid showing the gadget)
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
        asExclude.push( "_isEditing_"  );
        asExclude.push( "_isChanged_"  );
        asExclude.push( "_hasErrors_"  );
        asExclude.push( "aListenerIds" );

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

    show( isOngoing = false )
    {
        if( isOngoing )
            return this;

        // --- Fix for title handling ---
        this._$container_.find('.gadget-title').remove();

        // Reset styles
        this._$container_.css({ 'display': '', 'flex-direction': '' });
        this._$container_.children().css('flex-grow', '');

        if( p_base.isEmpty( this.card_title ) )
            return this;

        // Use flexbox for proper layout
        this._$container_.css({ 'display': 'flex', 'flex-direction': 'column' });

        const $title = $('<div class="gadget-title" style="flex-shrink: 0;"></div>').html(this.card_title);

        if( this.card_title_location === 'south' )
        {
            this._$container_.children().css('flex-grow', '1');
            this._$container_.append($title);
        }
        else // 'north' or undefined
        {
            this._$container_.prepend($title);
            this._$container_.children().not('.gadget-title').css('flex-grow', '1');
        }

        return this;
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
        let uid = gum_ws.addListener( oExEn, sDevName, fnCallBack );

        this.aListenerIds.push( uid );
        gum_ws.requestValue( oExEn, sDevName );
    }

    _resetListeners()
    {
        for( let uid of this.aListenerIds )
            gum_ws.delListener( uid );

        this.aListenerIds = [];
    }

    _isProperValue( sType, xValue, sDevName )
    {
        if( ! xValue )     // If it is Undefined, value will not be shown, but no eeror will be shown
            return false;

        switch( sType.charAt( 0 ).toLowerCase() )
        {
            case 's':
                if( p_base.isString( xValue ) )
                    return true;

                this._showError( 'Device "'+ sDevName +'" is not a String; its value is: '+ xValue );
                return false

            case 'n':
                if( p_base.isNumber( xValue ) )
                    return true;

                this._showError( 'Device "'+ sDevName +'" is not a Number; its value is: '+ xValue );
                return false

            case 'b':
                if( p_base.isBoolean(  xValue ) )
                    return true;

                this._showError( 'Device "'+ sDevName +'" is not a Boolean; its value is: '+ xValue );
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

    //------------------------------------------------------------------------------------//
    // PRIVATE SCOPE
}