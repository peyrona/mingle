
package com.peyrona.mingle.candi.unec.parser;

import com.peyrona.mingle.lang.interfaces.ICandi;
import com.peyrona.mingle.lang.japi.UtilIO;
import com.peyrona.mingle.lang.japi.UtilStr;
import com.peyrona.mingle.lang.lexer.Lexeme;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Arrays;
import java.util.List;
import java.util.ListIterator;

/**
 * This class represents a transpiled INCLUDE command.
 * 
 * @author Francisco José Morero Peyrona
 *
 * Official web site at: <a href="https://github.com/peyrona/mingle">https://github.com/peyrona/mingle</a>
 */
public final class ParseInclude extends ParseBase
{
    private final static  String       sKEY = "INCLUDE";
    private final         String[]     asURIs;
    private final         List<Lexeme> lexemes;
    private final boolean bAutoIncs;                          // Discover automatically which INCLUDEs are needed

    //------------------------------------------------------------------------//
    // STATIC INTERFACE

    /**
     * Returns true if passed source Une code is a 'INCLUDE' command.<br>
     * The whole syntax is not checked, therefore the command could have errors.
     *
     * @param source Une source code.
     * @return
     */
    public static boolean is( String source )
    {
        return UtilStr.startsWith( source, sKEY );
    }

    //------------------------------------------------------------------------//

    public ParseInclude( List<Lexeme> lexemes )
    {
        super();

        boolean bAuto = false;

        this.lexemes = lexemes;

        if( lexemes.size() < 2 )     // "INCLUDE" itself is the first token in the List
        {
            addError( '"'+ sKEY +"\" needs one or more URI(s).", lexemes.get(0) );
            asURIs = null;
        }
        else
        {
            List<Lexeme> lstURIs = lexemes.subList( 1, lexemes.size() );     // Not include 1st token: 'INCLUDE' lexeme

            for( ListIterator<Lexeme> itera = lstURIs.listIterator(); itera.hasNext(); )
            {
                Lexeme lex = itera.next();

                if( lex.isString() && lex.text().trim().equals( "*" ) )
                {
                    itera.remove();
                    bAuto = true;
                }
            }

            asURIs = expandURIs( lstURIs );
        }

        bAutoIncs = bAuto;
    }

    //------------------------------------------------------------------------//
    // PUBLIC INTERFACE

    public boolean autoInclude()
    {
        return bAutoIncs;
    }

    public String[] getURIs()
    {
        return Arrays.copyOf( asURIs, asURIs.length );     // Defensive copy
    }

    public ICandi.IError addErrorLoadingURI( String sURI, String err )
    {
        for( Lexeme token : lexemes )
        {
            try
            {
                List<URI> list = UtilIO.expandPath( token.text() );

                for( URI uri : list )
                {
                    if( sURI.equals( uri.toString() ) )
                        return addError( err, token );
                }
            }
            catch( IOException | URISyntaxException ioe )
            {
                // Nothing to do
            }
        }

        return addError( err, getStart() );
    }

    @Override
    public String serialize()
    {
        return null;
    }

    //------------------------------------------------------------------------//
    // PROTECTED INTERFACE

    //------------------------------------------------------------------------//
    // PRIVATE INTERFACE
}