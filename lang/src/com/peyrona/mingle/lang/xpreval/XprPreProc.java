
package com.peyrona.mingle.lang.xpreval;

import com.peyrona.mingle.lang.interfaces.ICandi;
import com.peyrona.mingle.lang.japi.UtilColls;
import com.peyrona.mingle.lang.lexer.CodeError;
import com.peyrona.mingle.lang.lexer.Language;
import com.peyrona.mingle.lang.lexer.Lexeme;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Function;

/**
 * Using an infix valid Une expression, expands groups (ANY and ALL modifiers) and
 * makes boolean and arithmetic optimizations.<br>
 * <br>
 * This class is public to be used by compilers and interpreters and also by
 * other implementations of IXprEval.
 *
 * @author Francisco José Morero Peyrona
 *
 * Official web site at: <a href="https://github.com/peyrona/mingle">https://github.com/peyrona/mingle</a>
 */
final class XprPreProc
{
    private static final short GROUP_NAME_ALL = -1;   // A group name that has an ALL modifier
    private static final short GROUP_NAME_ANY = -2;   // A group name that has an ANY modifier

    private final List<XprToken>      lstInfix  = new ArrayList<>();
    private final List<ICandi.IError> lstErrors = new ArrayList<>();

    //------------------------------------------------------------------------//

    /**
     * Creates the instance and pre-processes the expression:
     * <ul>
     *   <li>Expands groups (ANY and ALL modifiers).</li>
     *   <li>Makes boolean optimizations.</li>
     *   <li>Makes arithmetic optimizations.</li>
     * </ul>
     *
     * @param fnGroupWise A function that receives the name of a group and returns the list of devices belonging to this group.
     * @return Itself.
     */
    XprPreProc( List<Lexeme> lexemes, Function<String,String[]> fnGroupWise )
    {
        XprTokenizer   tokenizer = new XprTokenizer( lexemes );
        List<XprToken> lstTmp    = tokenizer.getTokens();

        lstErrors.addAll( tokenizer.getErrors() );     // If tokenizer.getErrors() is empty, no harm

        if( validate( lstTmp ) )
        {
            if( fnGroupWise != null )
                lstTmp = doAllAny( lstTmp, fnGroupWise );

            optimizeBooleans( lstTmp );
        }

        lstInfix.clear();
        lstInfix.addAll( lstTmp );
    }

    //------------------------------------------------------------------------//

    /**
     * Returns the tokens that represent the infix string expression.
     *
     * @return The tokens that represent the infix string expression.
     */
    List<XprToken> getAsInfix()
    {
        return Collections.unmodifiableList( lstInfix );
    }

    /**
     * Returns found errors.
     *
     * @return Where to place found errors: key is the error description and
     */
    List<ICandi.IError> getErrors()
    {
        return Collections.unmodifiableList( lstErrors );
    }

    @Override
    public String toString()
    {
        StringBuilder sb = new StringBuilder( lstInfix.size() * 6 );

        for( XprToken token : lstInfix )
        {
            if( token.isType( XprToken.STRING ) )  sb.append( ' ' ).append( Language.toString( token.text() ) );
            else                                   sb.append( ' ' ).append( token.text() );
        }

        return sb.toString();
    }

    //------------------------------------------------------------------------//

    /**
     * Expand groups.
     *
     * @param lstTokens
     * @param fnGroupWise
     * @param mapErrors
     * @return
     */
    @SuppressWarnings(value = "empty-statement")
    private List<XprToken> doAllAny( List<XprToken> lstTokens, Function<String,String[]> fnGroupWise )
    {
        List<XprToken> lstResult = new ArrayList<>();

        if( lstTokens.isEmpty() )
            return lstResult;

        for( int n = 0; n < lstTokens.size(); n++ )
        {
            XprToken token = lstTokens.get( n );

            if( isAllOrAny( token ) )    // If this token is ANY or ALL, then next token must be a group-name
            {
                if( n == (lstTokens.size() - 1) )
                {
                    lstErrors.add( new CodeError( "No group name found after \"" + token.text() + '"', token ) );
                }
                else
                {
                    XprToken tGroupName = UtilColls.getAt( lstTokens, n + 1 );   // Following token after ANY or ALL
                    XprToken tAfter     = UtilColls.getAt( lstTokens, n + 4 );   // In "ANY window > ALL doors", tAfter == "ALL"

                    if( isAllOrAny( tAfter ) )
                    {
                        lstErrors.add( new CodeError( "No group name found after \"" + token.text() + '"', token ) );
                    }
                    else
                    {
                        if( tGroupName.type() == XprToken.VARIABLE )    // We mark next (n+1) token as GROUP_NAME_XXX so in next for iteration it will be properly processed
                        {
                            if( token.isText( XprUtils.sALL ) )  tGroupName.type( XprPreProc.GROUP_NAME_ALL );
                            else                                 tGroupName.type( XprPreProc.GROUP_NAME_ANY );
                        }
                        else
                        {
                            lstErrors.add( new CodeError( "Group name expected, but found: \"" + token.text() + '"', token ) );
                        }
                    }
                } // Do not add this token ("ANY" or "ALL") to the resulting list (lstResult): it is not usefull anymore
            }
            else if( (token.type() == XprPreProc.GROUP_NAME_ALL) ||   // If this token is a group-name, then expand the group
                     (token.type() == XprPreProc.GROUP_NAME_ANY) )
            {
                String[] asGroupMembers = fnGroupWise.apply( token.text() );    // We obtain all members of the group

                if( UtilColls.isEmpty( asGroupMembers ) )
                {
                    lstErrors.add( new CodeError( '"' + token.text() + "\" is not the name of a group or the group has no members.", token ) );
                }
                else
                {
                    int ndxStart = n + 1;   // Token at n is the gropup name: token after is the operator
                    int ndxEnd   = n + 2;   // Token after the operator aafter the group name (start of expression or constant)

                    while( (++ndxEnd < lstTokens.size()) &&                                  // e.g.: "ALL windows == (true || false)
                           (! Language.isBooleanOp(   lstTokens.get( ndxEnd ).text() )) &&
                           (! Language.isParenthesis( lstTokens.get( ndxEnd ).text() )) &&
                           (! lstTokens.get( ndxEnd ).isType(XprToken.RESERVED_WORD )) );

                    ndxEnd -= ((ndxStart == lstTokens.size()) && Language.isParenthesis( lstTokens.get( ndxEnd ).text() )) ? 1 : 0;

                    // Can't send 'lstTokens.subList( ndxStart, ndxEnd )' to expand because alterations made in the sublist are also made in the list
                    // Therefore I have to create a new List containing all the sublist's items.

                    List<XprToken> lstCopy = new ArrayList<>( lstTokens.subList( ndxStart, ndxEnd ) );

                    lstResult.addAll( expand( token, asGroupMembers, lstCopy ) );

                    n = ndxEnd - 1;   // -1 because 'for' loop will incr. into 1
                }
            }
            else
            {
                lstResult.add( token );
            }
        }

        // System.out.print( "Entra: "+ XprUtils.toString( lstTokens ) ); System.out.println();
        // System.out.print( "Salen: "+ XprUtils.toString( lstResult ) ); System.out.println();

        return lstResult;
    }

    /**
     * Auxiliary function invoked from ::doAllAny(...)
     *
     * @param token
     * @param asGroupMembers Name of the groups members (device's names).
     * @param lstTemplate v.g.: "== (8 || 10)"
     * @return
     */
    private static List<XprToken> expand( XprToken token, String[] asGroupMembers, List<XprToken> lstTemplate )
    {
        // 'line' and 'columnEnd' can not be changed: must be the original (before expansion to show the right place to the user)

        List<XprToken> lstExpanded = new ArrayList<>();
                       lstExpanded.add( new XprToken( token, "(", XprToken.PARENTH_OPEN ) );

        XprToken tLogicalOp = new XprToken( token, ((token.type() == XprPreProc.GROUP_NAME_ANY) ? "||" : "&&"), XprToken.OPERATOR );

        // Repeat the template for every member of the group

        for( String deviceName : asGroupMembers )
        {
            lstExpanded.add( new XprToken( token, deviceName, XprToken.VARIABLE ) );
            lstExpanded.addAll( lstTemplate );
            lstExpanded.add( tLogicalOp );
        }

        UtilColls.removeTail( lstExpanded );     // Removes last "&&" or "||"

        lstExpanded.add( new XprToken( token, ")", XprToken.PARENTH_CLOSED ) );

        return lstExpanded;
    }

    // Aux func invoked from ::doAllAny(...)
    private boolean isAllOrAny( XprToken token )
    {
        return  token != null                      &&
                token.isType( XprToken.RESERVED_WORD ) &&
                token.isText( XprUtils.sALL, XprUtils.sANY );
    }

    /**
     * Optimizes trivial boolean expressions:
     * <ol>
     *    <li>"X == true"  by removing last 2 tokens ('==' , 'true').</li>
     *    <li>"X == false" by changing for: "! X"</li>
     * </ol>
     * <br>
     * Expressions like: "true || false", "true && false", etc will be resolved by EvalByAST:solveConstantNodes(...)
     *
     * @param lstTokens
     */
    private static void optimizeBooleans( List<XprToken> lstTokens )
    {
        for( int n = 0; n < lstTokens.size(); n++ )
        {
            XprToken token = lstTokens.get( n );

            if( token.isType( XprToken.OPERATOR ) && (n < lstTokens.size() - 1) )    // n is not the last token
            {
                if( token.isText( "==" ) )                                           // We are interested only in "==" (it is harder to optimize the "!=" op)
                {
                    if( lstTokens.get( n+1 ).isText( "true" ) )
                    {
                        lstTokens.remove( n );          // Removes "=="
                        lstTokens.remove( n );          // Removes "true"
                    }
                 // else if( lstTokens.get( n+1 ).isType( "false" ) )                // Can not be done here: has to be done at the AST
                }
            }
        }
    }

    private boolean validate( List<XprToken> lstTokens )    // Invoked after preprocessed: no "ANY", no "ALL", but with "AFTER" and "WITHIN"
    {
        if( lstTokens.isEmpty() )
        {
            lstErrors.add( new CodeError( "Expression is empty", -1, -1 ) );
            return false;
        }

        return true;
    }
}