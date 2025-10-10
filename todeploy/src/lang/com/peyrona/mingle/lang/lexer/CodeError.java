
package com.peyrona.mingle.lang.lexer;

import com.peyrona.mingle.lang.interfaces.ICandi;
import com.peyrona.mingle.lang.interfaces.ITokenable;
import java.util.Objects;

/**
 * CodeError: an error showed up during parsing, lexing, transpiling, etc.
 *
 * @author Francisco José Morero Peyrona
 *
 * Official web site at: <a href="https://github.com/peyrona/mingle">https://github.com/peyrona/mingle</a>
 *
 * Official web site at: <a href="https://github.com/peyrona/mingle">https://github.com/peyrona/mingle</a>
 */
public final class CodeError implements ICandi.IError
{
    private final String message;
    private final int    line;
    private final int    column;

    //------------------------------------------------------------------------//

    public CodeError( String message, ITokenable token )
    {
        this( message, token, false );
    }

    public CodeError( String message, ITokenable token, boolean bAfterLexeme )
    {
        this( message, token.line(), token.column() + (bAfterLexeme ? token.text().length() : 0) );
    }

    public CodeError( String message, int line, int column )
    {
        this.message = message;
        this.line    = line;
        this.column  = column;
    }

    //------------------------------------------------------------------------//

    @Override
    public String message()
    {
        return message;
    }

    @Override
    public int line()
    {
        return line;
    }

    @Override
    public int column()
    {
        return column;
    }

    @Override
    public String toString()
    {
        return message +" at "+ line +':'+ column;
    }

    @Override
    public int hashCode()
    {
        int hash = 7;
            hash = 31 * hash + Objects.hashCode( this.message );
            hash = 31 * hash + this.line;
            hash = 31 * hash + this.column;
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

        final CodeError other = (CodeError) obj;

        if( this.line != other.line )
            return false;

        if( this.column != other.column )
            return false;

        return Objects.equals( this.message, other.message );
    }
}