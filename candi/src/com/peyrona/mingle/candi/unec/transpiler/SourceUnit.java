
package com.peyrona.mingle.candi.unec.transpiler;

import com.peyrona.mingle.lang.japi.UtilIO;
import com.peyrona.mingle.lang.japi.UtilStr;
import java.io.IOException;
import java.net.URI;
import java.nio.charset.Charset;
import java.util.Objects;

/**
 * Represents one and only one Une file.
 *
 * @author Francisco José Morero Peyrona
 *
 * Official web site at: <a href="https://mingle.peyrona.com">https://mingle.peyrona.com</a>
 */
public final class SourceUnit
{
    public final String  uri;     // An URI or null if the code was directly passed to Tape
    public final Charset charset;
    public final String  code;    // The source code
    public final String  error;

    //------------------------------------------------------------------------//

    SourceUnit( URI uri, Charset charset )
    {
        String sCode = null;
        String sError;

        try
        {
            sCode  = UtilIO.getAsText( uri, charset );
            sCode  = UtilStr.isMeaningless( sCode ) ? null : sCode;
            sError = (sCode == null) ? "URI \""+ uri +"\" is empty: no code" : null;
        }
        catch( IOException ioe )
        {
            sError = "Error reading: "+ uri +"\nError: "+ ioe.getMessage();
        }

        this.uri     = uri.toString();
        this.charset = (charset == null) ? Charset.defaultCharset() : charset;
        this.code    = sCode;
        this.error   = sError;
    }

    //------------------------------------------------------------------------//
    // PUBLIC SCOPE

    @Override
    public int hashCode()
    {
        int hash = 7;
            hash = 13 * hash + Objects.hashCode( this.uri );
            hash = 13 * hash + Objects.hashCode( this.code );
        return hash;
    }

    @Override
    public boolean equals(Object obj)
    {
        if( this == obj )
        {
            return true;
        }
        if( obj == null )
        {
            return false;
        }
        if( getClass() != obj.getClass() )
        {
            return false;
        }
        final SourceUnit other = (SourceUnit) obj;
        if( !Objects.equals( this.uri, other.uri ) )
        {
            return false;
        }
        return Objects.equals( this.code, other.code );
    }

    @Override
    public String toString()
    {
        return UtilStr.toString( this );
    }
}