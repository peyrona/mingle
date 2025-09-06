'use strict';

// Arrays --------------------------------------------------------------------------------------------

if( ! Array.prototype.forEach )
{
    Array.prototype.forEach = function(fun /*, thisArg */)
    {
        if( this === void 0 || this === null )
            throw new TypeError();

        if (typeof fun !== "function")
            throw new TypeError();

        let t       = Object(this);
        let len     = t.length >>> 0;
        let thisArg = arguments.length >= 2 ? arguments[1] : void 0;

        for (let i = 0; i < len; i++)
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
    String.prototype.replaceAll = function( sReplace, sWith )
    {
        // See http://stackoverflow.com/a/3561711/556609

        let esc = sReplace.replace(/[-\/\\^$*+?.()|[\]{}]/g, '\\$&');
        let reg = new RegExp(esc, 'ig');

        return this.replace( reg, sWith );
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
        if( ! this )        // undefined or null
            return this;

        return str.slice(0, start) + newSubStr + str.slice(start + Math.abs(delCount));
    };
}
