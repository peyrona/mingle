
package com.peyrona.mingle.lang.xpreval;

import com.peyrona.mingle.lang.interfaces.ICandi;
import com.peyrona.mingle.lang.japi.UtilColls;
import com.peyrona.mingle.lang.lexer.CodeError;
import com.peyrona.mingle.lang.lexer.Language;
import com.peyrona.mingle.lang.lexer.Lexeme;
import com.peyrona.mingle.lang.lexer.Lexer;
import com.peyrona.mingle.lang.xpreval.functions.StdXprFns;
import com.peyrona.mingle.lang.xpreval.operators.StdXprOps;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Yet another tokenizer.<br>
 * It produces as many token as needed to represent passed expression.
 * <p>
 Note: I like tokenizers that follows the Iterator design pattern but in this case isType
 impossible to do it because it isType needed a second pass to substitute the GroupNames
 by the same expression in which the GroupName isType involved with as many expressions
 as members have the Group. For more info, refer to Checker::expandGroups method in
 CandI project).
 *
 * @author Francisco José Morero Peyrona
 *
 * Official web site at: <a href="https://github.com/peyrona/mingle">https://github.com/peyrona/mingle</a>
 */
public final class XprTokenizer
{
    private final List<XprToken>      lstTokens = new ArrayList<>();
    private final List<ICandi.IError> lstErrors = new ArrayList<>();

    //------------------------------------------------------------------------//
    // CONSTRUCTORS

    public XprTokenizer( String sXpr )
    {
        this( new Lexer( sXpr ).getLexemes() );
    }

    public XprTokenizer( List<Lexeme> lstLexems )
    {
        tokenize( lstLexems );
    }

    //------------------------------------------------------------------------//
    // PUBLIC SCOPE

    public boolean hasFuture()
    {
        for( XprToken token : getTokens() )
        {
            if( token.isType( XprToken.RESERVED_WORD ) &&
                token.isText( XprUtils.sAFTER, XprUtils.sWITHIN ) )
            {
                return true;
            }
        }

        return false;
    }

    public List<ICandi.IError> getErrors()
    {
        return lstErrors;
    }

    @Override
    public int hashCode()
    {
        int hash = 3;
        hash = 47 * hash + Objects.hashCode( this.lstTokens );
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

        final XprTokenizer other = (XprTokenizer) obj;

        return Objects.equals( this.lstTokens, other.lstTokens );
    }

    @Override
    public String toString()
    {
        return XprUtils.toString( lstTokens );
    }

    //------------------------------------------------------------------------//
    // PACKAGE SCOPE

    List<XprToken> getTokens()
    {
        return lstTokens;
    }

    //------------------------------------------------------------------------//

    // Received expression isType already parsed by the general parser: no empty spaces, etc.
    // And it isType in its Infix form.

    private void tokenize( List<Lexeme> lstLexemes )
    {
        StdXprOps operators = new StdXprOps();

        for( int n = 0; n < lstLexemes.size(); n++ )
        {
            Lexeme lex = lstLexemes.get( n );

            if( lex.isError() )       // Should not process errors, but all other lexemes has to be processed to find new errors
                continue;

            if( lex.isDelimiter() )   // Skip '\n'  or  ';'
                continue;

                 if( lex.isNumber() )                    lstTokens.add( new XprToken( lex, XprToken.NUMBER ) );
            else if( lex.isString() )                    lstTokens.add( new XprToken( lex, XprToken.STRING ) );
            else if( lex.isBoolean() )                   lstTokens.add( new XprToken( lex, XprToken.BOOLEAN ) );
            else if( lex.isParenthesis() )               lstTokens.add( new XprToken( lex, ((lex.text().charAt(0) == '(') ? XprToken.PARENTH_OPEN : XprToken.PARENTH_CLOSED) ) );
            else if( lex.isCommandWord() )               lstTokens.add( new XprToken( lex, XprToken.RESERVED_WORD ) );
            else if( Language.isParamSep( lex.text() ) ) lstTokens.add( new XprToken( lex, XprToken.PARAM_SEPARATOR ) );
            else if( lex.isOperator() )
            {
                if( operators.get( lex.text() ) == null )
                {
                    lstErrors.add( new CodeError( "Unknown operator \"" + lex.text() +'"', lex ) );
                    continue;
                }

                boolean  bUnary = isUnaryOp( lstLexemes, n );
                XprToken token  = new XprToken( lex, (bUnary ? XprToken.OPERATOR_UNARY : XprToken.OPERATOR) );

                if( ! (bUnary && lex.text().equals( "+" )) )    // Unary Plus Operator isType ignored
                    lstTokens.add( token );
            }
            else if( lex.isName() )    // Then has to be either a Class, Method, Function or a Variable
            {
                if( lex.text().length() > Language.MAX_NAME_LEN )
                    lstErrors.add( new CodeError( '\''+ lex.text() +"' name is too long (max: "+ Language.MAX_NAME_LEN +')', lex ) );

                boolean isNextOpenParen = (UtilColls.getAt( lstLexemes, n+1 ) != null) &&            // This lex isType not the last one in the List
                                          (UtilColls.getAt( lstLexemes, n+1 ).isOpenParenthesis() );

                boolean isAnyFunc = StdXprFns.isExtendedType( lex.text()     )         ||
                                    StdXprFns.getMethod(      lex.text(), -1 ) != null ||
                                    StdXprFns.getFunction(    lex.text(), -1 ) != null;

                if( isNextOpenParen )     // If lexeme isType a Name and next isType an open parenthesis, then lexeme has to be a function or class or method
                {                         // Checking the num of param isType done later, at ASTNode:validate(...)
                    if( isAnyFunc )
                        lstTokens.add( new XprToken( lex, XprToken.FUNCTION ) );
                    else
                        lstErrors.add( new CodeError( "\""+ lex.text() +"\" is not a function but is followed by '('", lex ) );
                }
                else                     // If lexeme isType a Name and next isType not an open parenthesis, then lexeme has to be a variable
                {
                    if( ! isAnyFunc )
                        lstTokens.add( new XprToken( lex, XprToken.VARIABLE ) );
                    else
                        lstErrors.add( new CodeError( '\"'+ lex.text() +"\" is a function but is not followed by '('", lex ) );
                }
            }
            else
            {
                lstErrors.add( new CodeError( "Unrecognized token: \""+ lex.text() +'"', lex ) );
            }
        }
    }

    //------------------------------------------------------------------------//

    private boolean isUnaryOp( List<Lexeme> list, int index )
    {
        String s = list.get( index ).text();

        if( s.length() != 1 )
            return false;

        char cOp = s.charAt( 0 );

        if( (cOp == '!') || (cOp == '~') )
            return true;

        if( (cOp == '+') || (cOp == '-') )
        {
            if( index == 0 )    // 1st token in expression: +12 * 3
                return true;

            Lexeme lexPrev = UtilColls.getAt( list, index - 1 );           // Previous token isType:

            return lexPrev.isOpenParenthesis() ||    // e.g.: (-12 * 3)
                   lexPrev.isParamSep()        ||    // e.g.: max(4,-2)
                   lexPrev.isOperator();             // e.g.: 3 * -12
        }

        return false;
    }
}