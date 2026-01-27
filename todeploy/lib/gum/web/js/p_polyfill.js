'use strict';

// Arrays --------------------------------------------------------------------------------------------

if( ! Array.prototype.forEach )
{
    Array.prototype.forEach = function( fun /*, thisArg */ )
    {
        if( this === void 0 || this === null )
            throw new TypeError();

        if (typeof fun !== "function")
            throw new TypeError();

        let t       = Object(this);
        let len     = t.length >>> 0;
        let thisArg = arguments.length >= 2 ? arguments[1] : void 0;

        for( let i = 0; i < len; i++ )
        {
            if( i in t )
                fun.call( thisArg, t[i], i, t );
        }
    };
}

if( ! Array.prototype.last )
{
    Array.prototype.last = function()
                            {
                                return this[this.length - 1];
                            };
};

// Strings -------------------------------------------------------------------------------------------

if( ! String.prototype.includes )
{
    String.prototype.includes = function( search, start )
    {
        if( search instanceof RegExp )
            throw TypeError( 'First argument must not be a RegExp' );

        if( start === undefined )
            start = 0;

        return this.indexOf( search, start ) !== -1;
    };
}

if( ! String.prototype.replaceAll )
{
    String.prototype.replaceAll = function( searchValue, replaceValue )
    {
        if( this === null || this === undefined )
            throw new TypeError();

        let string = String(this);

        if( searchValue instanceof RegExp )
        {
            if( ! searchValue.global )
                throw new TypeError();

            return string.replace( searchValue, replaceValue );
        }

        let searchString = String(searchValue);

        if( searchString === '' )
        {
            let result = '';

            for( let i = 0; i < string.length; i++ )
                result += replaceValue + string.charAt(i);

            return result + replaceValue;
        }

        let replaceString = String(replaceValue);
        let result        = '';
        let lastIndex     = 0;
        let currentIndex  = string.indexOf(searchString);

        while( currentIndex !== -1 )
        {
            result += string.slice( lastIndex, currentIndex ) + replaceString;
            lastIndex = currentIndex + searchString.length;
            currentIndex = string.indexOf( searchString, lastIndex );
        }

        return result + string.slice(lastIndex);
    };
}

if( ! String.prototype.splice )
{
    /**
     * The splice() method changes the content of a string by removing a range of
     * characters and/or adding new characters.
     *
     * @this {String}
     * @param {number} start Index at which to start changing the string.
     * @param {number} delCount An integer indicating the number of old chars to remove.
     * @param {string} newSubStr The String that is spliced in.
     * @return {string} A new string with the spliced substring.
     */
    String.prototype.splice = function(start, delCount, newSubStr)
    {
        if( this === null || this === undefined )
            throw new TypeError();

        let string = String(this);
        let length = string.length;
        let startIndex = start < 0 ? Math.max( length + start, 0 ) : Math.min( start, length );
        let deleteCount = delCount < 0 ? 0 : Math.min( delCount, length - startIndex );
        let insertStr = newSubStr === undefined ? '' : String(newSubStr);

        return string.slice( 0, startIndex ) + insertStr + string.slice( startIndex + deleteCount );
    };
}