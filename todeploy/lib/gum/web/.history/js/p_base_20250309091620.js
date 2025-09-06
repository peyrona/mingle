// ---------------------------------------------------------------------------//
//                         All my basic functions
// ---------------------------------------------------------------------------//
//  This module has no dependencies: not even JQuery.
// ---------------------------------------------------------------------------//

"use strict";

/* global HTMLInputElement, Infinity */

if( typeof p_base === "undefined" )
{
var p_base =
{
    //------------------------------------------------------------------------//
    // About variables
    //------------------------------------------------------------------------//

    /**
     * Devuelve true si la variable pasada ha sido declarada y no es null.
     *
     * @param {type} aVar
     * @returns {Boolean}
     */
    isDefined : function( aVar )
    {
        return (! this.isUndefined( aVar ));
    },

    /**
     * Devuelve true si la variable pasada no ha sido declarada o es null.
     *
     * @param {any} aVar
     * @returns {Boolean}
     */
    isUndefined : function( aVar )
    {
        return (typeof aVar === "undefined")
               ||
               (aVar === null);
    },

    isBoolean : function( aVar )
    {
        return this.isDefined( aVar ) && (typeof aVar === "boolean");
    },

    /**
     * Returns true if pased value is a number (is a numeric value).
     *
     * @param {type} aVar Variable to check.
     * @returns {Boolean} Returns true if pased value is a number (is a numeric value).
     */
    isNumeric : function( aVar )
    {
        // First: Check typeof and make sure it returns number
        // This code coerces neither booleans nor strings to numbers,
        // although it would be possible to do so if desired.

        if( typeof aVar !== 'number' )
            return false;

        // Reference for typeof:
        // https://developer.mozilla.org/en-US/docs/Web/JavaScript/Reference/Operators/typeof
        // Second: Check for NaN, as NaN is a number to typeof.
        // NaN is the only JavaScript value that never equals itself.

        if( aVar !== Number( aVar ) )
            return false;

        // Reference for NaN: https://developer.mozilla.org/en-US/docs/Web/JavaScript/Reference/Global_Objects/isNaN
        // Note isNaN() is a broken function, but checking for self-equality works as NaN !== NaN
        // Alternatively check for NaN using Number.isNaN(), an ES2015 feature that works how one would expect

        // Third: Check for Infinity and -Infinity.
        // Realistically we want finite numbers, or there was probably a division by 0 somewhere.

        if( aVar === Infinity || aVar === (! Infinity) )
            return false;

        return true;
    },

    /**
     * Returns true if pased value is a number or any value parseable by Number.parseFloat(...)
     *
     * @param {Any} aVar Value to check.
     * @returns {Boolean} Returns true if pased value is a number or any value parseable by Number.parseFloat(...)
     */
    isNumber : function( aVar )
    {
        return this.isNumeric( parseFloat( aVar ) );
    },

    isString : function( aVar )
    {
        return this.isDefined( aVar ) && (typeof aVar === "string");
    },

    isDate : function( aVar )
    {
        return this.isDefined( aVar ) && (aVar instanceof Date);
    },

    isFunction : function( aVar )
    {
        return this.isDefined( aVar ) && (typeof aVar === 'function');    // JQuery dice que NO se use $.isFunction(), sino q se haga así
    },

    isArray : function( aVar )
    {
        return this.isDefined( aVar ) && Array.isArray( aVar );
    },

    isObject : function( aVar )
    {
        return this.isDefined( aVar )    &&
               (! this.isArray( aVar ))  &&    // Needed because Arrays are also objects
               (typeof aVar === 'object');
    },

    isDOMEntity : function( aVar )
    {
        return this.isObject( aVar ) && (aVar.nodeType !== undefined);
    },

    /**
     * Devuelve true is la variable pasada es undefined o si es NaN o si la length
     * del trim() es cero o si es un array con 0 elementos o si es un objeto con
     * 0 keys.<br>
     * Sólo se aceptan variables de tipo: undefined, null, string, array, number
     * y Object. Cualquier otro tipo devolverá un resutado inesperado.
     *
     * @param {any} aVar La variable a comprobar.
     * @returns {Boolean} true si está vacía o false en caso contrario.
     */
    isEmpty : function( aVar )
    {
        if( this.isDOMEntity( aVar ) )      // This case has to be treated separetely
            return false;

        if( aVar instanceof jQuery )        // This case has to be treated separetely
            return aVar.length === 0;

        return  this.isUndefined( aVar )                                     ||
               (this.isString( aVar ) && (aVar.trim().length === 0))         ||
               (this.isArray( aVar )  && (aVar.length === 0))                ||
               (this.isObject( aVar ) && (Object.keys( aVar ).length === 0)) ||
               Number.isNaN( aVar );   // Has to be the last one
    },

    isNotEmpty : function( aVar )
    {
        return ! this.isEmpty( aVar );
    },

    /**
     * Returns true if the passed value is a valid email address.
     */
    areEquals( ...args )
    {
        const allStrings = args.every( arg => typeof arg === 'string' );


    },

    isValidURL : function( str )
    {
        let pattern = new RegExp( '^(https?:\\/\\/)?'+                                  // protocol
                                  '((([a-z\\d]([a-z\\d-]*[a-z\\d])*)\\.)+[a-z]{2,}|'+   // domain name
                                  '((\\d{1,3}\\.){3}\\d{1,3}))'+                        // OR ip (v4) address
                                  '(\\:\\d+)?(\\/[-a-z\\d%_.~+]*)*'+                    // port and path
                                  '(\\?[;&a-z\\d%_.~+=-]*)?'+                           // query string
                                  '(\\#[-a-z\\d_]*)?$','i' );                           // fragment locator
        return !!pattern.test( str );
    },

    /**
     * Returns true if the variable 'str' contains HTML text.<br>
     * <br>
     * In JavaScript, there isn't a built-in way to directly determine if a string variable
     * contains plain text or HTML text.<br>
     * <br>
     * This approach uses a regular expression to check if the string contains any HTML tags.<br>
     * Note that this is a basic check and may not cover all edge cases.
     *
     * @param {type} str
     * @returns {Boolean}
     */
    isHTML : function( str )
    {
        return /<[a-z][\s\S]*>/i.test(str);
    },

    isValidEmail : function( sEmail )
    {
        if( this.isEmpty( sEmail ) )
            return false;

        sEmail = sEmail.trim();

        let regexp = /^(([^<>()\[\]\\.,;:\s@"]+(\.[^<>()\[\]\\.,;:\s@"]+)*)|(".+"))@((\[[0-9]{1,3}\.[0-9]{1,3}\.[0-9]{1,3}\.[0-9]{1,3}\])|(([a-zA-Z\-0-9]+\.)+[a-zA-Z]{2,}))$/;

        return regexp.test( String( sEmail ).toLowerCase() );
    },

    capitalize : function( str )
    {
        if( ! this.isString( str ) )
            return str;

        str = str.trim();

        return str.charAt( 0 ).toUpperCase() + ((str.length > 1) ? str.substring( 1 ) : "");
    },

    setBetween : function( min, value, max )
    {
        return ((value > max) ? max
                              : ((value < min) ? min
                                               : value) );
    },

    /**
     * Returns a 36 chars length UUID.
     *
     * @returns {String} A 36 chars length UUID.
     */
    uuid : function()
    {
        let uuid = "", random;

        for( let n = 0; n < 32; n++ )
        {
            random = Math.random() * 16 | 0;

            if( n === 8 || n === 12 || n === 16 || n === 20 )
            {
                uuid += "-";
            }

            uuid += (n === 12 ? 4 : (n === 16 ? (random & 3 | 8) : random)).toString( 16 );
        }

        return uuid;
    },

    /**
     * A combination of String::replace and String::replaceAll that also allows to control the case.
     *
     * @param {String} sWhere2Look
     * @param {String} sWhat2Find
     * @param {String} sReplaceWith
     * @param {boolean} bAll All occurences or only first. By default false (only first).
     * @param {boolean} bIgnoreCase By default false (case sensitive).
     * @returns {String} Resulting string.
     */
     replace : function( sWhere2Look, sWhat2Find, sReplaceWith, bAll = false, bIgnoreCase = false )
     {
         if( this.isEmpty( sWhere2Look ) || this.isEmpty( sWhat2Find ) )
         {
             return sWhere2Look;
         }

         if( (! bAll) && (! bIgnoreCase) )
         {
            sWhere2Look = sWhere2Look.replace( sWhat2Find, sReplaceWith );
         }
         else
         {
            let esc = sReplaceWith.replace(/[-\/\\^$*+?.()|[\]{}]/g, '\\$&');
            let mod = (bAll ? 'g' : '') + (bIgnoreCase ? 'i' : '');
            let reg = new RegExp( esc, mod );

            sWhere2Look = this.replace( reg, sReplaceWith );
         }

         return sWhere2Look;
     },

    /**
     * Returns the portion of text between both marks (including the marks themselves).
     *
     * @param {string} sWhere2Look
     * @param {string} sMarkBegin
     * @param {string} sMarkEnd
     * @returns {String} The portion of text between both marks (including or not the marks themselves).
     */
    getFragment : function( sWhere2Look, sMarkBegin, sMarkEnd )
    {
        if( this.isEmpty( sWhere2Look ) || this.isEmpty( sMarkBegin ) || this.isEmpty( sMarkEnd ) )
        {
            return "";
        }

        sMarkBegin = sMarkBegin.toLowerCase();
        sMarkEnd   = sMarkEnd.toLowerCase();

        let lower  = sWhere2Look.toLowerCase();
        let nStart = lower.indexOf( sMarkBegin );
        let nEnd   = lower.indexOf( sMarkEnd   );

        if( (nStart > -1) && (nEnd > -1) )
        {
            return sWhere2Look.substring( nStart, nEnd + sMarkEnd.length );
        }

        return "";
    },

    arraysAreEqual : function( aoA, aoB )
    {
        if( ! this.isArray( aoA ) )
            return false;

        if( ! this.isArray( aoB ) )
            return false;

        if( aoA.length !== aoB.length )
            return false;

        if( aoA === aoB )    // Two different object instances will never be equal: {x:20} != {x:20}
            return true;

        for( let n = 0, l = aoB.length; n < l; n++ )
        {
            // Check if we have nested arrays
            if( (aoB[n] instanceof Array) && (aoA[n] instanceof Array) )
            {
                if( ! aoB[n].equals( aoA[n] ) )  // recurse into the nested arrays
                    return false;
            }
            else if( aoB[n] !== aoA[n] )
            {
                // Two different object instances will never be equal: {x:20} != {x:20}
                return false;
            }
        }

        return true;
    },

    /**
     * This function performs the mathematical set operation "difference": the
     * set A − B consists of A items that are not present in B.
     * <p>
     * More than 2 parameters can be passed:
     *    * If there are only 3 and the third parameter is a string's array: all these string will be used.
     *    * If more than 3 are passed, all these extra parameters will be used (all of them must be strings).
     * <p>
     * In both cases, these strings are the keys to be ignored when comparing both JSONs.
     *
     * @param {JSON array} aoA Array of JSON objects
     * @param {JSON array} aoB Array of JSON objects
     * @returns {aoResult} The difference or an empty array if both contain same elements.
     */
    arrayDifference : function( aoA, aoB )
    {
        let aoRes    = [];      // All aoA items that are not present in aoB
        let asIgnore = null;    // Keys to ignore when comparing both JSONs

        if( (! this.isArray( aoA )) )
            throw "First parameter is not an array";

        if( (! this.isArray( aoA )) )
            throw "Second parameter is not an array";

        if( arguments.length > 2 )
        {
            if( this.isArray( arguments[2] ) )
            {
                asIgnore = arguments[2];
            }
            else
            {
                asIgnore = [];

                for( var n = 2; n < arguments.length; n++ )
                {
                    asIgnore.push( arguments[n] );
                }
            }
        }

        for( let a = 0; a < aoA.length; a++ )
        {
            let exists = false;

            for( let b = 0; b < aoB.length; b++ )
            {
                if( this.jsonAreEquals( aoA[a], aoB[b], asIgnore ) )
                {
                    exists = true;
                    break;
                }
            }

            if( ! exists )
            {
                aoRes.push( aoA[a] );
            }
        }

        return aoRes;
    },

    /**
     * Find duplicated items in passed array (items must be non null strings or numbers).
     *
     * @param {Array} array To be scanned (primitive types or objects).
     * @param {Boolean} bIgnoreCase By default false (do not ignore case).
     * @param {Array} asKeys2Ignore Array of strings containing the keys to be ignored when comparing JSONs (objects).
     * @return {Array} With repeated items (or empty if none is duplicated).
     */
    arrayDuplicates : function( array, bIgnoreCase, asKeys2Ignore )
    {
        let aDups   = [];
        let aSorted = array.slice().sort();

        bIgnoreCase = this.isBoolean( bIgnoreCase ) ? bIgnoreCase : false;

        for( let n = 0; n < aSorted.length - 1; n++ )
        {
            if( this.isObject( aSorted[n] ) )                    // In this case, having a sorted array is meaningless:
            {                                                    // a full search must be performed,
                for( let x = 0; x < aSorted.length; x++ )        // but aSorted is needed because we need item's ordinal position.
                {
                    if( (n !== x) && this.jsonAreEquals( aSorted[n], aSorted[x], asKeys2Ignore ) )
                    {
                        aDups.push( aSorted[x] );
                        aSorted.splice( x, 1 );                  // Must remove this item (2nd parameter means remove one item only)
                        break;
                    }
                }
            }
            else
            {
                let itemThis = bIgnoreCase ? aSorted[n  ].toLocaleLowerCase() : aSorted[n  ];
                let itemNext = bIgnoreCase ? aSorted[n+1].toLocaleLowerCase() : aSorted[n+1];

                if( itemThis == itemNext )      // Better to use == instead of ===
                    aDups.push( itemNext );
            }
        }

        return aDups;
    },

    //------------------------------------------------------------------------//
    // About JSON
    //------------------------------------------------------------------------//

    /**
     * Scapes passed String.
     *
     * @param {Any} value If value is not a String this functions does nothing.
     * @param {Boolean} bRemove When true \n, \r and \t are removed and \" are replaced by '.
     *                  When false they are replaced by the same char with double escape ('\\').
     * @return {String} The passed string after being scaped.
     */
    escapeStr : function ( value, bRemove = false )
    {
        if( ! this.isString( value ) )
            return value;

        if( bRemove )
        {
            value = value.replace( /\s+/g, ' ' )
                         .replace( /\"/g , "'" );    // In some places I do not allow double quotes: required by my mental sanity. All " are replaced by '
        }
        else
        {
            value = value.replace( /\"/gm, "\\\""  )
                         .replace( /\n/gm, "\\\\n" )
                         .replace( /\r/gm, ""      )
                         .replace( /\t/gm, "    "  );
        }

        return value;
    },

    /**
     * Receives a JSON in a compacted way (as it is returned by the server):
     *
     *     {"head": ["h1","h2",...], "body": [{d11,d12,...}, {d21,d22,...}, ...]}
     *
     * and returns another JSON as follows:
     *
     *     [{"h1":d11, "h2":d12, ...}, {"h1":d21, "h2":d22, ...}, ...]
     *
     * @param {type} oJSON
     * @param {type} bHeads2LowerCase Pass heads to lowercase, defaults to true.
     * @returns {JSON} An array with the all the results (or an empty one).
     */
    inflate : function( oJSON, bHeads2LowerCase = true )
    {
        let aRet = [];

        if( this.isEmpty( oJSON ) )
            return aRet;

        oJSON = this.isString( oJSON ) ? JSON.parse( oJSON ) : oJSON;

        if( bHeads2LowerCase )
        {
            for( let m = 0; m < oJSON.head.length; m++ )
            {
                oJSON.head[m] = oJSON.head[m].toLowerCase();
            }
        }

        for( let n = 0; n < oJSON.body.length; n++ )
        {
            var oLine = {};

            for( let m = 0; m < oJSON.head.length; m++ )
            {
                oLine[ oJSON.head[m] ] = oJSON.body[n][m];
            }

            aRet.push( oLine );
        }

        return aRet;
    },

    /**
     * Returns true if both passed JSON objects has same keys and values (ignoring keys to be ignored if any).
     * This funciton is not recursive: it does not treat properly inner objects.
     *
     * @param {JSON} json1
     * @param {JSON} json2
     * @param {Array} asKeys2Ignore Array of strings containing the keys to be ignored when comparing both JSONs (objects).
     */
    jsonAreEquals : function( json1, json2, asKeys2Ignore = [] )
    {
        if( this.isUndefined( json1 ) && this.isUndefined( json2 ) )
            return true;

        if( typeof json1 !== 'object' || typeof json2 !== 'object' )
          return false;

        const asKeys1 = Object.keys( json1 ).filter( key => ! asKeys2Ignore.includes(key) );   // Remove keys to be ignored
        const asKeys2 = Object.keys( json2 ).filter( key => ! asKeys2Ignore.includes(key) );

        if( asKeys1.length !== asKeys2.length )    // Check if the objects have the same number of non-ignored keys
          return false;

        for( const key of asKeys1 )     // Recursively compare the values of each non-ignored key
        {
            if( typeof json1[key] === 'object' && typeof json2[key] === 'object' )
            {
                if( ! jsonAreEquals( json1[key], json2[key], asKeys2Ignore ) )
                    return false;
            }
            else if( json1[key] != json2[key] )     // Has to be !=, not !==
            {
                return false;
            }
        }

        return true;
      },

    //------------------------------------------------------------------------//
    // About date, time, numbers and currencies
    //------------------------------------------------------------------------//

    asInt : function( aVar, defVal )
    {
        return (this.isEmpty( aVar ) ? defVal : Number.parseInt( Number( aVar ) ));
    },

    asFloat : function( aVar, defVal )
    {
        return (this.isEmpty( aVar ) ? defVal : Number.parseFloat( Number( aVar ) ));
    },

    asBoolean : function( aVar, defVal )
    {
        if( this.isBoolean( aVar ) )  return aVar;
        if( this.isString(  aVar ) )  return aVar.trim().toLowerCase() === 'true';
        if( this.isNumeric( aVar ) )  return aVar !== 0;     // Every non-zero value corresponds to true

        return defVal;
    },

    /**
     * Converts passed value (v.g. a string) into a number, but if it is
     * undefined or null or empty string or NaN, it returns the same value as
     * received.
     *
     * @param {Any} aVar
     * @returns {Number}
     */
    toNumber : function( aVar )
    {
        return this.isNumber( aVar ) ? Number( aVar )      // Note: do not use: new Number(...)
                                     : aVar;
    },

    toDate : function( aVar )
    {
        if( ! this.isDate( aVar ) )
        {
            if( this.isUndefined( aVar ) )
            {
                aVar = new Date();
            }
            else if( Number.isInteger( aVar ) )
            {
                aVar = new Date( aVar );    // Asumo q son millis desde 1970
            }
            else if( this.isString( aVar ) )
            {
                try
                {
                    // This constructor can return a wrong date because of default Time and TimeZone
                    // aVar = new Date( aVar );
                    // Better use this approach -->
                    let as = aVar.match( /(\d+)/g );
                    aVar = new Date( as[0], as[1]-1, as[2] );   // months are 0-based
                }
                catch( error )     // Si no es una fecha en formato de string, quizás sean mills pero como cadena: p.ej. "6789124545565"
                {
                    aVar = (this.isNumber( aVar ) ? new Date( Number.parseInt( aVar ) ) : null);
                }
            }
        }

        return aVar;
    },

    /**
     * Clears time for passed day: an instance of date an integer and null are
     * allowed as argument. If argument is ommited, new Date() will be used.
     *
     * @param {Date} date By default 'new Date()'.
     * @returns {Date} Passed date which time is 00:00:00:00.
     */
    clearTime : function( date )
    {
        return new Date( this.toDate( date ).setHours( 0,0,0,0 ) );
    },

    /**
     * Returns the date for today with no time info (begining of the day).
     * <p>
     * Note: this is a shorthand for <pre>::clearTime()</pre>
     *
     * @returns {Date} today with no time info (begining od the day).
     */
    today : function()
    {
       return this.clearTime();
    },

    isWeekend : function( date )
    {
        let nDoW = date.getDay();    // Devuelve el día de la semana (0 - 6)

        return (nDoW === 0 || nDoW === 6);
    },

    areSameDay : function( date1, date2 )
    {
        if( (date1 instanceof Date) && (date2 instanceof Date) )
        {
            return (date1.getDate()  === date2.getDate() ) &&
                   (date1.getMonth() === date2.getMonth()) &&
                   (date1.getYear()  === date2.getYear() );
        }

        throw "One or both argument(s) is/are not date(s)";
    },

    addDays : function( date, days )
    {
        if( days === 0 )
        {
            return date;
        }

        days = (this.isString( days ) ? Number.parseInt( days ) : days);

        var result = new Date( date );
            result.setDate( result.getDate() + days );

      return result;
    },

    /**
     * Returns the difference (in days) between the two passed dates.
     * <ul>
     *   <li>If x < 0 then date1 is before date2</li>
     *   <li>If x > 0 then date2 is before date1</li>
     *   <li>If x = 0 then date1 is same day as date2</li>
     * </ul>
     *
     * @param {type} date1
     * @param {type} date2
     * @returns {Number} The difference (in days) between the two passed dates.
     */
    dateDiff : function( date1, date2 = new Date() )
    {
        date1 = this.clearTime( this.toDate( date1 ) ).getTime();
        date2 = this.clearTime( this.toDate( date2 ) ).getTime();

        // Take the difference between the dates and divide by milliseconds per day.
        // Round to nearest whole number to deal with DST.

        return Math.round( (date1 - date2) / (24*60*60*1000) );
    },

    /**
     * Convierte la fecha pasada a cadena en el formato indicado por el segundo
     * parámetro.
     * <ul>
     *    <li>Si no se pasa la fecha o es null, se utiliza: new Date().</li>
     *    <li>Si el formato no se pasa o es null, se utilizará el del locale del navegador.</li>
     *    <li>Los caracteres reconocidos para el formato son: 'y', 'm' y 'd' (es case-sensitive)</li>
     *    <li>Se usan 2 dígitos para el mes y para el día</li>
     *    <li>Los meses y días tienen que aparecer duplicados: 'mm' y 'dd'</li>
     *    <li>Si aparece 'yy' se usarán 2 dígitos para el año, si aparece 'yyyy' se usarán 4</li>
     *    <li>Sólo se reemplaza la primera ocurrencia de los caracteres especiales</li>
     *    <li>Cualquiera otros caracteres que aparezcan en el formato permanecerán inalterados</li>
     * </ul>
     *
     * @param {Date} date Un dato de tipo fecha.
     * @param {String} sFormat La platilla para dar formato; por defecto el del locale del navegador.
     * @returns {String}
     */
    date2String : function( date, sFormat )
    {
        date = this.toDate( date );

        if( this.isUndefined( sFormat ) )
        {
            return date.toLocaleDateString();
        }

        var nYear  = date.getFullYear();
        var sMonth = date.getMonth() + 1;
            sMonth = (sMonth < 10 ? "0"+ sMonth : sMonth);
        var sDoM   = date.getDate();
            sDoM   = (sDoM < 10 ? "0"+ sDoM : sDoM);

        sFormat = sFormat.replace( "mm", sMonth )
                         .replace( "dd", sDoM );

        sFormat = (sFormat.indexOf( "yyyy" ) > -1) ? sFormat.replace( "yyyy", nYear )
                                                   : sFormat.replace( "yy", nYear.toString().substr( 2 ) );

        return sFormat;
    },

    /**
     * Convierte la hora de la fecha pasada a una cadena siguiendo el formato
     * indicado por el segundo parámetro.
     * <ul>
     *    <li>Si no se pasa la fecha o es null, se utiliza: new Date().</li>
     *    <li>Si el formato no se pasa o es null, se utilizará el del locale del navegador.</li>
     *    <li>Los caracteres reconocidos para el formato son: 'h', 'm' y 's' (es case-sensitive)</li>
     *    <li>Se usan 2 caracteres para la hora, los minutos y los segundos (p.ej. hh:mm:ss)</li>
     *    <li>Sólo se reemplaza la primera ocurrencia de los caracteres especiales</li>
     *    <li>Cualquiera otros caracteres que aparezcan en el formato permanecerán inalterados</li>
     * </ul>
     *
     * @param {Date} date Un dato de tipo fecha.
     * @param {String} sFormat La plantilla para dar formato; por defecto la deel locale del navegador.
     * @returns {String}
     */
    time2String : function( date, sFormat )
    {
        date = this.toDate( date );

        if( this.isUndefined( sFormat ) )
        {
            return date.toLocaleTimeString();
        }

        function pad( n )
        {
             return (n < 10) ? '0' + n : n;
        }

        return sFormat.replace( "hh", pad( date.getHours()   ) )
                      .replace( "mm", pad( date.getMinutes() ) )
                      .replace( "ss", pad( date.getSeconds() ) );
    },

    /**
     * Convierte la fecha pasada a cadena en el formato ISO para fechas
     *
     * @param {aVar} date Un dato de tipo fecha, cadena o numérico (milisegundos).
     *                    Si está vacío, se usará new Date().
     *
     * @returns {@var;format|String}
     */
    date2ISO : function( date )
    {
        return this.date2String( date, "yyyy-mm-dd" );
    },

    ISO2Millis : function( str )
    {
        var ret = null;

        if( this.isDefined( str ) && this.isNotEmpty( str ) )
        {
            ret = Date.parse( str );
        }

        return ret;
    },

    millis2UTC : function( millis )
    {
        function pad( n )
        {
            n = Math.floor( n );
            return (n < 10) ? '0' + n : n;
        }

        let today = (millis / 1000) % 86400;   // Millis since midnight
        let sec   = today % 60;
		let min   = (today / 60) % 60;
		let hour  = (today / 60) / 60;

        return pad( hour ) +':'+ pad( min ) +':'+ pad( sec );
    },

    millisSinceMidnight : function( date )
    {
        date = this.toDate( date );
        let midnight = new Date( date.getTime() ).setHours( 0, 0, 0, 0 );
        return date.getTime() - midnight.getTime();
    },

    /**
     * Return the string that corresponds to received UNIX time with following
     * format: "yyyy-MM-dd - hh:mm".
     *
     * @param {*} nMillis
     * @returns
     */
    toDateTime : function( nMillis )
    {
        return this.date2ISO( nMillis ) +" - "+ this.time2String( nMillis, "hh:mm" );
    },

    /**
     * Convierte una cadena a número. La cadena puede tener símbolos no numéricos como '€'.
     * @param {String}    sAmount Cadena a convertir.
     * @param {String}    sLocale Locale a usar (por defecto el del navegador).
     * @param {type} sAmount
     * @returns {Number}
     */
    string2Number : function( sAmount, sLocale )
    {
        if( ! this.isString( sAmount ) )
        {
            sAmount = new String( sAmount );
        }

        sLocale = this.isUndefined( sLocale ) ? getNavigatorLanguage() : sLocale;

        var cThousandSep = (12345.6789).toLocaleString( sLocale ).match( /12(.*)345/ )[1];
        var cDecimalSep  = (12345.6789).toLocaleString( sLocale ).match( /345(.*)67/ )[1];
// SELL: mejorarlo (p.ej. el '-' sólo puede estar al principio o al final, sólo puede haber 1 '.', etc)
        sAmount = sAmount.split( cThousandSep ).join( '' );                                    // Elimina el separador de millares
        sAmount = (cDecimalSep === '.') ? sAmount : sAmount.split( cDecimalSep ).join( '.' );  // Cambia  el separador decimal por '.'
        sAmount = sAmount.replace( /[^0-9.-]/g, '' );                                          // Elimina lo q no sean números, '.' o '-'

        return Number( sAmount );
    },

    /**
     * Devuelve la cantidad (float) pasada como cadena formateada como moneda (con
     * 2 decimales). Se utiliza el locale del navegador.
     *
     * @param {Number}    nAmount     Cantidad a formatear (como número).
     * @param {String}    sLocale     Locale a usar (por defecto el del navegador).
     * @param {Boolean}   bWithSymbol Inlcude or not the currency symbol for passed locale (by default, true).
     * @returns {String}  La cantidad formateada como moneda (con símbolo o sin él).
     */
    toCurrency : function( nAmount, sLocale, bWithSymbol )
    {
        bWithSymbol = this.isUndefined( bWithSymbol ) ? true : bWithSymbol;

        nAmount = this.toNumber( nAmount );

        var sAmount = Number.parseFloat( nAmount ).toFixed( 2 );

        return this.formatNumber( sAmount, sLocale ) + (bWithSymbol ? " €" : "");
    },

    /**
     *
     * @param {string} sAmount
     * @param {string} sLocale A valid locale
     * @returns {string}
     */
    formatNumber : function( sAmount, sLocale )
    {
        // SELL: hay q buscar el símbolo del locale (habrá algún fichero .json q tenga todos)
        sLocale = this.isUndefined( sLocale ) ? this.getBrowserLocale() : sLocale;

        let cThousandSep = (12345.6789).toLocaleString( sLocale ).match( /12(.*)345/ )[1];
        let cDecimalSep  = (12345.6789).toLocaleString( sLocale ).match( /345(.*)67/ )[1];
        let nDotPos      = sAmount.indexOf( "." );
        let sIntPart     = sAmount.substring( 0, nDotPos );
        let sDecPart     = sAmount.substring( nDotPos + 1 );

        sIntPart = sIntPart.replace( /\B(?=(\d{3})+(?!\d))/g, cThousandSep );      // Pone un "." por cada 3 dígitos (separador de miles)

        return sIntPart + cDecimalSep + sDecPart;
    },

    //------------------------------------------------------------------------//
    // About the DOM and the navigator
    //------------------------------------------------------------------------//

    /**
     * Composes a valid URL by appending received fragment to 'window.location'
     * 'origin' and 'pathname'.
     *
     * @param s2Append
     * @returns The composed URL.
     */
    doURL : function( s2Append )
    {
        if( this.isEmpty( s2Append ) )
        {
            s2Append = "";
        }
        else
        {
            s2Append = s2Append.trim();
            s2Append = ((s2Append.charAt(0) === '/') ? "" : "/") + s2Append;
        }

        let sOrig = window.location.origin;
        let sPath = window.location.pathname;

        return sOrig + sPath.substring( 0, sPath.lastIndexOf( '/' ) ) + s2Append;
    },

    /**
     * Returns true when the server is localhost (normally during developing stage).
     *
     * @returns true when the server is localhost (normally during developing stage)
     */
    isLocalHost : function()
    {
        return this.doURL().indexOf( 'localhost' ) > -1;
    },

    /**
     * Returns the element associateed with the id passed as argument. It can start or not
     * with '#' (JQuery id selector).
     *
     * @param {String | HTMLElement} what
     * @return The DOM element or null if it does not represent a valid DOM element.
     */
    get : function( what )
    {
        if( what === null )
            return null;

        if( this.isDOMEntity( what ) )
            return what;

        if( what instanceof jQuery )
            return what[0];                            // The DOM element

        if( this.isString( what ) )                    // A DOM Element ID string
        {
            what = what.trim();

            if( what.charAt( 0 ) === "#" )             // Le quito el prefijo
                what = what.substring( 1 );

            what = document.getElementById( what );    // Obtengo el element input
        }

        return document.body.contains( what ) ? what : null;
    },

    /**
     * Analiza el FORM pasado buscando los INPUT (cualquier tipo) que éste contiene y devuelve un JSON
     * en el que las keys son el 'name' de cada INPUT y los 'values' son su valor.
     *
     * @param {HTMLElement} form div, form o lo que sea.
     * @return {JSON} En el que las keys son el 'name' del INPUT y los values son su valor.
     */
    getFormFields : function( form = null )
    {
        if( form === null )
            throw "Passed form is null";

        if( form instanceof jQuery )
            form = form[0];

        if( form.tagName.toUpperCase() !== "FORM" )
            throw "Container must be an HTML <form></form>";

        let obj = {};

        for( let n = 0; n < form.elements.length; n++ )
        {
            let elem  = form.elements[n];

            if( elem.name )      // If element has no name it does not make any sense to add it as part of the form because it can not be used
            {
                let value = this.getFieldValue( elem );

                if( value !== null )
                    obj[elem.name] = value;
            }
        }

        return obj;
    },

    getFieldValue : function( elem )   // TODO: pensar en si hay q hacer escapeString o no y qué habría q hacer para q esta func y su contraria funcionases de la mejor forma posible
    {
        function isNumber( val )
        {
            return ! isNaN( val );
        }

        elem = this.get( elem );                            // elem can arrive as a normal DOM object or as a JQuery object

        let value = null;

        switch( elem.type )
        {
            case "password":                                  // Nada que hacer: una passwrod puede tener de todo
                break;

            case "email":
            case "month":
            case "tel":
            case "text":
            case "textarea":
            case "time":
            case "url":
            case "week":
                value = this.escapeStr( elem.value.trim() );  // Lo mejor es hacerlo aquí y no más tarde en cada acceso
                break;

            case "hidden":                                    // Aunque al server le llegue como string, yo puedo usarlo en el cliente como número; para p.ej. hacer validaciones
                value = this.escapeStr( elem.value.trim() );
                if( isNumber( value ) )                       // Los ids (q son hidden muchas veces) son casi siempre numéricos
                {
                    let bDec  = (value.indexOf('.') > 0);
                    value = (value.length > 0) ? (bDec ? parseFloat(value) : parseInt(value)) : null;
                }
                break;

            case "range":
            case "number":                                    // Aunque al server le llegue como string, yo puedo usarlo en el cliente para p.ej. hacer validaciones
                value = elem.value.trim();
                let bDec = (value.indexOf('.') > 0);
                value = (value.length > 0) ? (bDec ? parseFloat(value) : parseInt(value)) : null;
                break;

            case "date":
            case "datetime-local":
             // value = this.toDate( elem.value );            --> Esto sería lo lógico, pero los input type="date" devuelven la fecha como String en formato ISO
                value = elem.value;                           // (puto HTML) y mis apps por tanto las tienen que usar en ese mismo formato (no voy a ir contracorriente).
                break;

            case "checkbox":
                value = elem.checked;
                break;

            case "radio":                                     // Si un grupo de radios no tiene ninguno seleccionado, lo ignoro (HTML hace lo mismo)
                if( elem.checked )
                    value = this.escapeStr( elem.value.trim() );
                break;

            case "select-one":                                // Comprobado q se llama así (si el 'value' está vacío, devuelve el 'text')
                if( elem.selectedIndex >= 0 )
                {
                    value = elem.options[ elem.selectedIndex ].value.trim();

                    if( this.isEmpty( value ) )
                        value = elem.options[ elem.selectedIndex ].text.trim();

                    value = this.escapeStr( value );
                }
                break;

            case "color":
                value = elem.value;
                break;
        }

        return value;
    },

    setFormFields : function( form, fieldset )
    {
        if( ! (this.get( form ) instanceof HTMLFormElement) )
            throw form +" is not a form";

        $(form).trigger( "reset" );

        form = this.get( form );       // In case it was a JQuery object

        for( let n = 0; n < form.elements.length; n++ )
        {
            let field = form.elements[n];

            if( fieldset.hasOwnProperty( field.name ) )
                this.setFieldValue( field, fieldset[field.name] );
        }
    },

    setFieldValue : function( field, value )
    {
        field = this.get( field );    // Para admitir tanto Elements puros como de JQuery

        switch( field.type.toLowerCase() )
        {
            case "email":
            case "month":
            case "tel":
            case "text":
            case "textarea":
            case "time":
            case "url":
            case "week":
            case "hidden":
                value = (this.isString( value ) ? value.trim() : value);
                $(field).val( this.escapeStr( value ) );          // Lo mejor es hacerlo aquí y no más tarde en cada acceso
                break;

            case "password":
            case "range":
            case "number":
            case "date":
            case "datetime-local":
                $(field).val( value );
                break;

            case "checkbox":
                if( ! this.isBoolean( value ) )
                {
                    if( ! this.isString( value ) )
                        throw "value must be boolean or string";

                    value = value.trim().toLowerCase();

                    if( (value !== "true") && (value !== "false") )
                        throw "value must be 'true' or 'false'";

                    value = (value === "true");
                }

                $(field).prop( "checked", value );
                break;

            case "radio":
                if( this.isString( value ) )                     // Sometimes I use booleans for radios (bad practice but I do)
                    $(field).filter('[value='+ value.trim() +']').prop( 'checked', true );
                break;

            case "select-one":    // Comprobado q se llama así
                value = (value === null ? "null" : value);       // Sometimes I use "null" for an OPTION value

                if( ! this.isString( value ) )
                    throw "Value must be an string, but is: "+ value;

                value = value.trim();

                let aIndexes = [];

                for( let n = 0; n < field.length; n++ )
                {
                    if( (field.options[n].value === value) || (field.options[n].text === value) )
                        aIndexes.push( n );
                }

                     if( aIndexes.length === 1 )  field.selectedIndex = aIndexes[0];
                else if( aIndexes.length === 0 )  field.selectedIndex = -1;
                else                              throw "There is more than one option in select with a value named: "+ value;

                break;

            case "color":
                $(field).val( (this.isEmpty( value ) ? '#000000' : value) );    // null is not a valid value, neither ""
                break;

            default:
                throw "Unknown tag type: "+ field.type;
        }
    },

    isOfType : function( element, sType )
    {
        return ((element instanceof HTMLInputElement) &&
                (element.getAttribute( 'type' ) === sType.toLowerCase()));
    },

    getMaxZIndex : function()
    {
        return 2147483646;
    },

    showNewPage : function( sHTML, sTitle = "" )
    {
        let win = window.open();
            win.focus();
            win.document.write( sHTML );
            win.document.title = sTitle;
    },

    getBrowserLocale : function()
    {
        if( window.navigator.languages )
        {
            return window.navigator.languages[0];
        }

        return (window.navigator.userLanguage || window.navigator.language);
    },

    isFirefox : function()
    {
        return (navigator.userAgent.toLowerCase().indexOf( 'firefox' ) !== -1);
    },

    isSafari : function()
    {
        return /^((?!chrome|android).)*safari/i.test( navigator.userAgent );
    },

    /**
     * Returns true if the HTML render engine is Blink (the one developed by Google).
     *
     * @returns {Boolean} true if the HTML render engine is Blink.
     */
    isChromeBased : function()
    {
        return ((window.chrome || (window.Intl && window.Intl.v8BreakIterator)) && 'CSS' in window);
    },

    /**
     * Comprueba si el navegador corre en un móvil
     *
     * @returns {boolean} true if the browser is running inside a mobile device.
     */
    isMobile : function()
    {
        if( /Android|webOS|iPhone|iPad|iPod|BlackBerry|IEMobile|Opera Mini/i.test( navigator.userAgent ) )
        {
            return true;
        }

        if(    navigator.userAgent.match(/Android/i)
            || navigator.userAgent.match(/webOS/i)
            || navigator.userAgent.match(/iPhone/i)
            || navigator.userAgent.match(/iPad/i)
            || navigator.userAgent.match(/iPod/i)
            || navigator.userAgent.match(/BlackBerry/i)
            || navigator.userAgent.match(/Windows Phone/i) )
        {
            return true;
        }

        var check = false;

        (function(a){if(/(android|bb\d+|meego).+mobile|avantgo|bada\/|blackberry|blazer|compal|elaine|fennec|hiptop|iemobile|ip(hone|od)|iris|kindle|lge |maemo|midp|mmp|mobile.+firefox|netfront|opera m(ob|in)i|palm( os)?|phone|p(ixi|re)\/|plucker|pocket|psp|series(4|6)0|symbian|treo|up\.(browser|link)|vodafone|wap|windows ce|xda|xiino/i.test(a)||/1207|6310|6590|3gso|4thp|50[1-6]i|770s|802s|a wa|abac|ac(er|oo|s\-)|ai(ko|rn)|al(av|ca|co)|amoi|an(ex|ny|yw)|aptu|ar(ch|go)|as(te|us)|attw|au(di|\-m|r |s )|avan|be(ck|ll|nq)|bi(lb|rd)|bl(ac|az)|br(e|v)w|bumb|bw\-(n|u)|c55\/|capi|ccwa|cdm\-|cell|chtm|cldc|cmd\-|co(mp|nd)|craw|da(it|ll|ng)|dbte|dc\-s|devi|dica|dmob|do(c|p)o|ds(12|\-d)|el(49|ai)|em(l2|ul)|er(ic|k0)|esl8|ez([4-7]0|os|wa|ze)|fetc|fly(\-|_)|g1 u|g560|gene|gf\-5|g\-mo|go(\.w|od)|gr(ad|un)|haie|hcit|hd\-(m|p|t)|hei\-|hi(pt|ta)|hp( i|ip)|hs\-c|ht(c(\-| |_|a|g|p|s|t)|tp)|hu(aw|tc)|i\-(20|go|ma)|i230|iac( |\-|\/)|ibro|idea|ig01|ikom|im1k|inno|ipaq|iris|ja(t|v)a|jbro|jemu|jigs|kddi|keji|kgt( |\/)|klon|kpt |kwc\-|kyo(c|k)|le(no|xi)|lg( g|\/(k|l|u)|50|54|\-[a-w])|libw|lynx|m1\-w|m3ga|m50\/|ma(te|ui|xo)|mc(01|21|ca)|m\-cr|me(rc|ri)|mi(o8|oa|ts)|mmef|mo(01|02|bi|de|do|t(\-| |o|v)|zz)|mt(50|p1|v )|mwbp|mywa|n10[0-2]|n20[2-3]|n30(0|2)|n50(0|2|5)|n7(0(0|1)|10)|ne((c|m)\-|on|tf|wf|wg|wt)|nok(6|i)|nzph|o2im|op(ti|wv)|oran|owg1|p800|pan(a|d|t)|pdxg|pg(13|\-([1-8]|c))|phil|pire|pl(ay|uc)|pn\-2|po(ck|rt|se)|prox|psio|pt\-g|qa\-a|qc(07|12|21|32|60|\-[2-7]|i\-)|qtek|r380|r600|raks|rim9|ro(ve|zo)|s55\/|sa(ge|ma|mm|ms|ny|va)|sc(01|h\-|oo|p\-)|sdk\/|se(c(\-|0|1)|47|mc|nd|ri)|sgh\-|shar|sie(\-|m)|sk\-0|sl(45|id)|sm(al|ar|b3|it|t5)|so(ft|ny)|sp(01|h\-|v\-|v )|sy(01|mb)|t2(18|50)|t6(00|10|18)|ta(gt|lk)|tcl\-|tdg\-|tel(i|m)|tim\-|t\-mo|to(pl|sh)|ts(70|m\-|m3|m5)|tx\-9|up(\.b|g1|si)|utst|v400|v750|veri|vi(rg|te)|vk(40|5[0-3]|\-v)|vm40|voda|vulc|vx(52|53|60|61|70|80|81|83|85|98)|w3c(\-| )|webc|whit|wi(g |nc|nw)|wmlb|wonu|x700|yas\-|your|zeto|zte\-/i.test(a.substr(0,4))) check = true;})(navigator.userAgent||navigator.vendor||window.opera);

        return check;
    }
};
}