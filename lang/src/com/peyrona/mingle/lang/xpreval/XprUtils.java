
package com.peyrona.mingle.lang.xpreval;

import com.peyrona.mingle.lang.lexer.Language;
import java.util.List;

/**
 *
 * @author Francisco José Morero Peyrona
 *
 * Official web site at: <a href="https://github.com/peyrona/mingle">https://github.com/peyrona/mingle</a>
 */
final class XprUtils
{
    static final String sALL    = "all";       // must be lower-case
    static final String sANY    = "any";       // must be lower-case
    static final String sNONE   = "none";      // must be lower-case
    static final String sAFTER  = "after";     // must be lower-case
    static final String sWITHIN = "within";    // must be lower-case

    //------------------------------------------------------------------------//

    static String toString( List<XprToken> list )
    {
        if( list == null || list.isEmpty() )
            return "";

        StringBuilder sb = new StringBuilder( list.size() * 6 );

        for( int n = 0; n < list.size(); n++ )
        {
            XprToken token = list.get( n );

            if( token.isType( XprToken.PARENTH_OPEN ) )
            {
                if( n > 0 && list.get( n - 1 ).isType( XprToken.FUNCTION ) )
                {
                    sb.append( token.text() );

                    continue;
                }
            }
            else if( token.isType( XprToken.PARENTH_CLOSED ) )
            {
                if( n > 0 && list.get( n - 1 ).isType( XprToken.PARENTH_OPEN ) )
                {
                    sb.append( token.text() );
                    continue;
                }
            }
            else if( token.text().length() == 1 )
            {
                if( (token.text().charAt( 0 ) == Language.SEND_OP) || token.isType( XprToken.PARAM_SEPARATOR ) )
                {
                    sb.append( token.text() );
                    continue;
                }
            }

            if( token.isType( XprToken.STRING ) )  sb.append( ' ' ).append( Language.toString( token.text() ) );
            else                                   sb.append( ' ' ).append( token.text() );
        }

        return sb.toString();
    }

    //------------------------------------------------------------------------//

    private XprUtils()
    {
        // Avoids creating instances of this class
    }
}