
package com.peyrona.mingle.lang.xpreval;

import com.peyrona.mingle.lang.lexer.Language;
import java.util.List;

/**
 *
 * @author Francisco José Morero Peyrona
 *
 * Official web site at: <a href="https://mingle.peyrona.com">https://mingle.peyrona.com</a>
 */
final class XprUtils
{
    static final String sALL    = "all";       // must be lower-case
    static final String sANY    = "any";       // must be lower-case
    static final String sAFTER  = "after";     // must be lower-case
    static final String sWITHIN = "within";    // must be lower-case

    //------------------------------------------------------------------------//

    static String toString( List<XprToken> list )
    {
        StringBuilder sb = new StringBuilder( list.size() * 6 );

        for( XprToken token : list )
        {
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