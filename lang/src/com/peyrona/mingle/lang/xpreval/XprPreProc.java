
package com.peyrona.mingle.lang.xpreval;

import com.peyrona.mingle.lang.interfaces.ICandi;
import com.peyrona.mingle.lang.japi.UtilColls;
import com.peyrona.mingle.lang.japi.UtilStr;
import com.peyrona.mingle.lang.lexer.CodeError;
import com.peyrona.mingle.lang.lexer.Language;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Function;

/**
 * Using an infix valid Une expression, expands groups (ANY, ALL, and NONE modifiers)
 * and makes boolean and arithmetic optimizations.
 *
 * @author Francisco José Morero Peyrona
 *
 * Official web site at: <a href="https://github.com/peyrona/mingle">https://github.com/peyrona/mingle</a>
 */
/**
 * Using an infix valid Une expression, expands groups (ANY, ALL, and NONE modifiers)
 * and makes boolean and arithmetic optimizations.
 *
 * @author Francisco José Morero Peyrona
 */
final class XprPreProc
{
    private static final short GROUP_NAME_ALL  = -1;
    private static final short GROUP_NAME_ANY  = -2;
    private static final short GROUP_NAME_NONE = -3;

    private final List<XprToken>      lstInfix  = new ArrayList<>();
    private final List<ICandi.IError> lstErrors = new ArrayList<>();

    XprPreProc( String xpr, Function<String,String[]> fnGroupWise )
    {
        XprTokenizer   tokenizer = new XprTokenizer( xpr );
        List<XprToken> lstTmp    = tokenizer.getTokens();

        lstErrors.addAll( tokenizer.getErrors() );

        if( validate( lstTmp, xpr ) )
        {
            if( fnGroupWise != null )
                lstTmp = doQuantifiers( lstTmp, fnGroupWise );

            optimizeBooleans( lstTmp );
        }

        lstInfix.clear();
        lstInfix.addAll( lstTmp );
    }

    List<XprToken>      getAsInfix()  { return Collections.unmodifiableList( lstInfix  ); }
    List<ICandi.IError> getErrors()   { return Collections.unmodifiableList( lstErrors ); }

    @Override
    public String toString()
    {
        StringBuilder sb = new StringBuilder( lstInfix.size() * 6 );

        for( XprToken token : lstInfix )
        {
            if( token.isType( XprToken.STRING ) )  sb.append( ' ' ).append( Language.toString( token.text() ) );
            else                                   sb.append( ' ' ).append( token.text() );
        }

        return sb.toString().trim();
    }

    @SuppressWarnings(value = "empty-statement")
    private List<XprToken> doQuantifiers( List<XprToken> lstTokens, Function<String,String[]> fnGroupWise )
    {
        List<XprToken> lstResult = new ArrayList<>();

        for( int n = 0; n < lstTokens.size(); n++ )
        {
            XprToken token = lstTokens.get( n );

            if( isQuantifier( token ) )
            {
                if( n >= (lstTokens.size() - 1) )
                {
                    lstErrors.add( new CodeError( "No group name found after \"" + token.text() + '"', token ) );
                }
                else
                {
                    XprToken tGroupName = lstTokens.get( n + 1 );
                    // Check if next token exists before accessing index n + 2
                    XprToken tAfter = (n + 2 < lstTokens.size()) ? lstTokens.get( n + 2 ) : null;

                    if( isQuantifier( tAfter ) )
                    {
                        lstErrors.add( new CodeError( "Unexpected quantifier \"" + tAfter.text() + "\" after \"" + token.text() + '"', tAfter ) );
                    }
                    else if( tGroupName.type() == XprToken.VARIABLE )
                    {
                        if( token.isText( XprUtils.sALL ) )       tGroupName.type( GROUP_NAME_ALL  );
                        else if ( token.isText( XprUtils.sANY ) ) tGroupName.type( GROUP_NAME_ANY  );
                        else                                      tGroupName.type( GROUP_NAME_NONE );
                    }
                    else
                    {
                        lstErrors.add( new CodeError( "Group name expected after \"" + token.text() + "\", but found: \"" + tGroupName.text() + '"', tGroupName ) );
                    }
                }
            }
            else if( isInternalGroupName( token.type() ) )
            {
                String[] asGroupMembers = fnGroupWise.apply( token.text() );

                if( UtilColls.isEmpty( asGroupMembers ) )
                {
                    lstErrors.add( new CodeError( '"' + token.text() + "\" is not the name of a group or the group has no members.", token ) );
                }
                else
                {
                    int ndxStart = n + 1;
                    int ndxEnd   = n + 1; // Start looking for the template immediately after the group name

                    // Scan for the end of the template (e.g. "== 5")
                    while( (++ndxEnd < lstTokens.size()) &&
                           (! Language.isBooleanOp(   lstTokens.get( ndxEnd ).text() )) &&
                           (! Language.isParenthesis( lstTokens.get( ndxEnd ).text() )) &&
                           (! lstTokens.get( ndxEnd ).isType(XprToken.RESERVED_WORD )) );

                    List<XprToken> lstTemplate = new ArrayList<>( lstTokens.subList( ndxStart, ndxEnd ) );
                    lstResult.addAll( expand( token, asGroupMembers, lstTemplate ) );
                    n = ndxEnd - 1;
                }
            }
            else
            {
                lstResult.add( token );
            }
        }

        return lstResult;
    }

    private static List<XprToken> expand( XprToken token, String[] asGroupMembers, List<XprToken> lstTemplate )
    {
        List<XprToken> lstExpanded = new ArrayList<>();

        // Safety check for empty groups to prevent syntax errors
        if( asGroupMembers == null || asGroupMembers.length == 0 )
            return lstExpanded;

        if( token.type() == GROUP_NAME_NONE )
        {
            lstExpanded.add( new XprToken( token, "(", XprToken.PARENTH_OPEN ) );
            lstExpanded.add( new XprToken( token, "!", XprToken.OPERATOR_UNARY ) );
        }

        lstExpanded.add( new XprToken( token, "(", XprToken.PARENTH_OPEN ) );

        String opText = (token.type() == GROUP_NAME_ALL) ? "&&" : "||";

        for( String deviceName : asGroupMembers )
        {
            lstExpanded.add( new XprToken( token, "(", XprToken.PARENTH_OPEN ) ); // Wrap individual member expression
            lstExpanded.add( new XprToken( token, deviceName, XprToken.VARIABLE ) );
            lstExpanded.addAll( lstTemplate );
            lstExpanded.add( new XprToken( token, ")", XprToken.PARENTH_CLOSED ) );
            lstExpanded.add( new XprToken( token, opText, XprToken.OPERATOR ) );  // fresh instance each iteration (XprToken is mutable)
        }

        UtilColls.removeTail( lstExpanded );
        lstExpanded.add( new XprToken( token, ")", XprToken.PARENTH_CLOSED ) );

        if( token.type() == GROUP_NAME_NONE )
            lstExpanded.add( new XprToken( token, ")", XprToken.PARENTH_CLOSED ) );

        return lstExpanded;
    }

    private boolean isQuantifier( XprToken token )
    {
        return token != null && token.isType( XprToken.RESERVED_WORD ) &&
               token.isText( XprUtils.sALL, XprUtils.sANY, XprUtils.sNONE );
    }

    private boolean isInternalGroupName( short type )
    {
        return type == GROUP_NAME_ALL ||
               type == GROUP_NAME_ANY ||
               type == GROUP_NAME_NONE;
    }

    private static void optimizeBooleans( List<XprToken> lstTokens )
    {
        for( int n = 0; n < lstTokens.size() - 1; n++ )
        {
            XprToken token = lstTokens.get( n );

            if( token.isType( XprToken.OPERATOR ) && token.isText( "==" ) )
            {
                if( lstTokens.get( n+1 ).isText( "true" ) )
                {
                    lstTokens.remove( n ); // remove ==
                    lstTokens.remove( n ); // remove true
                    n--; // re-check current position
                }
            }
        }
    }

    private boolean validate( List<XprToken> lstTokens, String xpr )
    {
        if( lstTokens.isEmpty() )
        {
            if( UtilStr.isEmpty( xpr ) )
                lstErrors.add( new CodeError( "Expression is empty", -1, -1 ) );

            return false;
        }
        return true;
    }
}