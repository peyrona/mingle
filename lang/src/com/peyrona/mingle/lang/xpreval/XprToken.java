
package com.peyrona.mingle.lang.xpreval;

import com.peyrona.mingle.lang.MingleException;
import com.peyrona.mingle.lang.interfaces.ITokenable;
import com.peyrona.mingle.lang.japi.UtilType;
import com.peyrona.mingle.lang.lexer.Lexeme;
import java.util.Objects;

/**
 * Merely a struct.<br>
 * This class does not need sync because a new instance of it is created for every
 * expression and expressions are evaluated by one and only one IXprEval.
 *
 * @author Francisco José Morero Peyrona
 *
 * Official web site at: <a href="https://mingle.peyrona.com">https://mingle.peyrona.com</a>
 */
final class XprToken implements ITokenable
{
    public static final short BOOLEAN         =  1;
    public static final short NUMBER          =  2;
    public static final short STRING          =  3;
    public static final short OPERATOR        =  4;
    public static final short OPERATOR_UNARY  =  5;
    public static final short RESERVED_WORD   =  6;
    public static final short FUNCTION        =  7;
    public static final short VARIABLE        =  8;   // e.g. myDevice
    public static final short PARENTH_OPEN    =  9;
    public static final short PARENTH_CLOSED  = 10;
    public static final short PARAM_SEPARATOR = 11;

    // NOTE: NULL is not part of the Une language

    private final String text;
    private       short  type;
    private final int    line;
    private final int    column;
    private       Object value  = null;    // ::text converted to its value (e.g. "true" -> true, "-12" -> -12)


    //------------------------------------------------------------------------//

    XprToken( Lexeme lex, short type )
    {
        assert type >= 1 && type <= 11;

        this.text   = ((type == FUNCTION || type == VARIABLE) ? lex.text().toLowerCase() : lex.text());    // In fact FUNCTION is not needed because funcs are searched ignoring case, but it is more clear in this way
        this.type   = type;
        this.line   = lex.line();
        this.column = lex.column();
    }

    XprToken( XprToken token, String newText, short newType )
    {
        assert newType >= 1 && newType <= 11;

        this.text     = ((type == FUNCTION || type == VARIABLE) ? newText.toLowerCase() : newText);    // In fact FUNCTION is not needed because funcs are searched ignoring case, but it is more clear in this way
        this.type     = newType;
        this.line     = token.line;
        this.column   = token.column;
    }

    //------------------------------------------------------------------------//
    // PUBLIC SCOPE

    @Override
    public String   toString()      { return "{text=\""+ text +"\", type="+ type +'}'; }
    @Override
    public String   text()          { return text;     }
    @Override
    public int      line()          { return line;     }
    @Override
    public int      column()        { return column;   }

    @Override
    public int hashCode()
    {
        int hash = 5;
            hash = 41 * hash + Objects.hashCode( this.text );
            hash = 41 * hash + this.type;
            hash = 41 * hash + this.line;
            hash = 41 * hash + this.column;
        return hash;
    }

    @Override
    public boolean equals(Object obj)
    {
        if( this == obj )
            return true;

        if( obj == null )
            return false;

        if( getClass() != obj.getClass() )
            return false;

        final XprToken other = (XprToken) obj;

        if( this.line != other.line )
            return false;

        if( this.column != other.column )
            return false;

        if( this.type != other.type )
            return false;

        return Objects.equals( text, other.text );
    }

    //------------------------------------------------------------------------//
    // PACKAGE SCOPE

    short type()
    {
        return type;
    }

    XprToken type( short n )
    {
        type = n; return this;
    }

    boolean isType( short t )
    {
        return type == t;
    }

    boolean isType( short... t )        // Is one of passed types
    {
        for( short n : t )
            if( n == type )
                return true;

        return false;
    }

    boolean isNotType( short t )
    {
        return t != type;
    }

    boolean isNotType( short... at )   // Is none of passed
    {
        for( short t : at )               // It is not the same this for that: ! isType( t )
            if( t == type )
                return false;

        return true;
    }

    boolean isText( char ch )
    {
        return (text.length() == 1) && (text.charAt(0) == ch);
    }

// NOT USED
//    boolean isText( char... ac )      // Is none of passed
//    {
//        for( char c : ac )
//            if( XprToken.this.isText( c ) )
//                return true;
//
//        return false;
//    }
//
//    boolean isNotText( char ch )
//    {
//        return ! XprToken.this.isText( ch );
//    }
//
//    boolean isNotText( char... ac )   // Is none of passed
//    {
//        for( char c : ac )
//            if( XprToken.this.isText( c ) )
//                return false;
//
//        return true;
//    }

    boolean isText( String s )
    {
        return text.equalsIgnoreCase( s );
    }

    boolean isText( String... as )
    {
        for( String s : as )
            if( XprToken.this.isText( s ) )
                return true;

        return false;
    }

// NOT USED
//    boolean isNotText( String s )
//    {
//        return ! XprToken.this.isText( s );
//    }
//
//    boolean isNotText( String... as )
//    {
//        for( String s : as )
//            if( XprToken.this.isText( s ) )
//                return false;
//
//        return true;
//    }

    Object value()
    {
        if( value == null )
        {
            switch( type )
            {
                case BOOLEAN: value = UtilType.toBoolean( text ); break;
                case NUMBER : value = UtilType.toFloat(   text ); break;
                case STRING : value = text;                       break;
                default     : throw new MingleException( MingleException.SHOULD_NOT_HAPPEN );
            }
        }

        return value;
    }
}