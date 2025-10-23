/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package com.peyrona.mingle.glue.codeditor.UneEditorTabContent;

import com.peyrona.mingle.lang.japi.UtilSys;
import com.peyrona.mingle.lang.lexer.Language;
import java.util.List;
import java.util.Map;
import javax.swing.text.Segment;
import org.fife.ui.rsyntaxtextarea.AbstractTokenMaker;
import org.fife.ui.rsyntaxtextarea.RSyntaxUtilities;
import org.fife.ui.rsyntaxtextarea.Token;
import org.fife.ui.rsyntaxtextarea.TokenMap;

/**
 * Syntax highlighting token maker for the Une programming language.
 * <p>
 * This class extends AbstractTokenMaker to provide syntax highlighting for Une code
 * in the RSyntaxTextArea component. It handles tokenization of various language elements
 * including:
 * <ul>
 * <li>Reserved words and commands</li>
 * <li>Functions and extended type methods</li>
 * <li>Operators (arithmetic, boolean, relational, bitwise)</li>
 * <li>Literals (strings, numbers, booleans)</li>
 * <li>Comments and whitespace</li>
 * <li>Identifiers and separators</li>
 * </ul>
 * The token maker dynamically loads language elements from the system configuration
 * to ensure consistent highlighting with the language parser.
 *
 * @author Francisco José Morero Peyrona
 *
 * Official web site at: <a href="https://github.com/peyrona/mingle">https://github.com/peyrona/mingle</a>
 */
final class UneTokenMaker extends AbstractTokenMaker
{
    private static final TokenMap map = createTokenMap();

    //------------------------------------------------------------------------//

    @Override
    public TokenMap getWordsToHighlight()
    {
        return map;
    }

    @Override
    public void addToken( Segment segment, int start, int end, int tokenType, int startOffset )
    {
        switch( tokenType )
        {
            case Token.DATA_TYPE:
            case Token.DEFAULT_NUM_TOKEN_TYPES:
            case Token.FUNCTION:
                break;

            case Token.IDENTIFIER:
                int value = wordsToHighlight.get( segment, start, end );

                if( value != -1 )
                    tokenType = value;

                break;

            case Token.LITERAL_BOOLEAN:
            case Token.LITERAL_NUMBER_DECIMAL_INT:
            case Token.LITERAL_NUMBER_FLOAT:
            case Token.LITERAL_STRING_DOUBLE_QUOTE:
            case Token.OPERATOR:
            case Token.PREPROCESSOR:
            case Token.RESERVED_WORD:
            case Token.RESERVED_WORD_2:
            case Token.SEPARATOR:
            case Token.VARIABLE:
            default:
        }

        super.addToken( segment, start, end, tokenType, startOffset );
    }

    @Override
    public Token getTokenList( Segment segment, int startTokenType, int startOffset )
    {
        resetTokenList();

        char[] array  = segment.array;
        int    offset = segment.offset;
        int    count  = segment.count;
        int    end    = offset + count;

        // Token starting offsets are always of the form:
        // 'startOffset + (currentTokenStart-offset)', but since startOffset and
        // offset are constant, tokens' starting positions become:
        // 'newStartOffset+currentTokenStart'.
        int newStartOffset = startOffset - offset;

        int currentTokenStart = offset;
        int currentTokenType = startTokenType;

        for( int n = offset; n < end; n++ )
        {
            char ch = array[n];

            switch( currentTokenType )
            {

                case Token.NULL:

                    currentTokenStart = n;   // Starting a new token here.

                    switch( ch )
                    {
                        case ' ':
                        case '\t':
                            currentTokenType = Token.WHITESPACE;
                            break;

                        case '"':
                            currentTokenType = Token.LITERAL_STRING_DOUBLE_QUOTE;
                            break;

                        case '#':
                            currentTokenType = Token.COMMENT_EOL;
                            break;

                        default:
                            if( RSyntaxUtilities.isDigit( ch ) || ch == '.' )
                            {
                                currentTokenType = Token.LITERAL_NUMBER_FLOAT;
                                break;
                            }
                            else if( RSyntaxUtilities.isLetter( ch ) || ch == '_' )
                            {
                                currentTokenType = Token.IDENTIFIER;
                                break;
                            }

                            // Anything not currently handled - mark as an identifier
                            currentTokenType = Token.IDENTIFIER;
                            break;

                    } // End of switch (c).

                    break;

                case Token.WHITESPACE:
                    switch( ch )
                    {
                        case ' ':
                        case '\t':
                            break;  // Still whitespace.

                        case '"':
                            addToken( segment, currentTokenStart, n - 1, Token.WHITESPACE, newStartOffset + currentTokenStart );
                            currentTokenStart = n;
                            currentTokenType = Token.LITERAL_STRING_DOUBLE_QUOTE;
                            break;

                        case '#':
                            addToken( segment, currentTokenStart, n - 1, Token.WHITESPACE, newStartOffset + currentTokenStart );
                            currentTokenStart = n;
                            currentTokenType = Token.COMMENT_EOL;
                            break;

                        case ',':
                        case ':':
                            addToken( segment, currentTokenStart, n - 1, Token.WHITESPACE, newStartOffset + currentTokenStart );
                            addToken( segment, n, n, Token.SEPARATOR, newStartOffset + n );
                            currentTokenStart = n + 1;
                            break;
// NEXT: no funcionan lo paréntesis: seguramente no sólo hay que tocar aquí, sino tb en ::addToken(...)
//                        case '(':
//                        case ')':
//                            addToken(segment, currentTokenStart, n - 1, Token.WHITESPACE, newStartOffset + currentTokenStart);
//                            addToken(segment, n, n, Token.SEPARATOR, newStartOffset + n);
//                            currentTokenStart = n + 1;
//                            currentTokenType = Token.WHITESPACE;
//                            break;

                        default:  // Add the whitespace token and start a new.
                            addToken( segment, currentTokenStart, n - 1, Token.WHITESPACE, newStartOffset + currentTokenStart );
                            currentTokenStart = n;

                            if( RSyntaxUtilities.isDigit( ch ) )
                            {
                                currentTokenType = Token.LITERAL_NUMBER_DECIMAL_INT;
                                break;
                            }
                            else if( RSyntaxUtilities.isLetter( ch ) || ch == '_' )
                            {
                                currentTokenType = Token.IDENTIFIER;
                                break;
                            }

                            // Anything not currently handled - mark as identifier
                            currentTokenType = Token.IDENTIFIER;
                    }

                    break;

                case Token.IDENTIFIER:

                    switch( ch )
                    {
                        case ' ':
                        case ',':    // Yo añadí este case
                        case ';':    // Yo añadí este case
                        case '\t':
                            addToken( segment, currentTokenStart, n - 1, Token.IDENTIFIER, newStartOffset + currentTokenStart );
                            currentTokenStart = n;
                            currentTokenType = Token.WHITESPACE;
                            break;

                        case '"':
                            addToken( segment, currentTokenStart, n - 1, Token.IDENTIFIER, newStartOffset + currentTokenStart );
                            currentTokenStart = n;
                            currentTokenType = Token.LITERAL_STRING_DOUBLE_QUOTE;
                            break;

                        default:
                            if( RSyntaxUtilities.isLetterOrDigit( ch ) || ch == '/' || ch == '_' )
                            {
                                break;   // Still an identifier of some type.
                            }
                        // Otherwise, we're still an identifier (?).

                    } // End of switch (c).

                    break;

                case Token.LITERAL_NUMBER_DECIMAL_INT:

                    switch( ch )
                    {
                        case ' ':
                        case '\t':
                            addToken( segment, currentTokenStart, n - 1, Token.LITERAL_NUMBER_DECIMAL_INT, newStartOffset + currentTokenStart );
                            currentTokenStart = n;
                            currentTokenType = Token.WHITESPACE;
                            break;

                        case '"':
                            addToken( segment, currentTokenStart, n - 1, Token.LITERAL_NUMBER_DECIMAL_INT, newStartOffset + currentTokenStart );
                            currentTokenStart = n;
                            currentTokenType = Token.LITERAL_STRING_DOUBLE_QUOTE;
                            break;

                        default:
                            if( RSyntaxUtilities.isDigit( ch ) )
                            {
                                break;   // Still a literal number.
                            }

                            // Otherwise, remember this was a number and start over.
                            addToken( segment, currentTokenStart, n - 1, Token.LITERAL_NUMBER_DECIMAL_INT, newStartOffset + currentTokenStart );
                            n--;
                            currentTokenType = Token.NULL;

                    } // End of switch (c).

                    break;

                case Token.COMMENT_EOL:
                    n = end - 1;
                    addToken( segment, currentTokenStart, n, currentTokenType, newStartOffset + currentTokenStart );
                    // We need to set token type to null so at the bottom we don't add one more token.
                    currentTokenType = Token.NULL;
                    break;

                case Token.LITERAL_STRING_DOUBLE_QUOTE:
                    if( ch == '"' )
                    {
                        addToken( segment, currentTokenStart, n, Token.LITERAL_STRING_DOUBLE_QUOTE, newStartOffset + currentTokenStart );
                        currentTokenType = Token.NULL;
                    }
                    break;

            } // End of switch (currentTokenType).

        } // End of for (int i=offset; i<end; i++).

        switch( currentTokenType )
        {

            // Remember what token type to begin the next line with.
            case Token.LITERAL_STRING_DOUBLE_QUOTE:
                addToken( segment, currentTokenStart, end - 1, currentTokenType, newStartOffset + currentTokenStart );
                break;

            // Do nothing if everything was okay.
            case Token.NULL:
                addNullToken();
                break;

            // All other token types don't continue to the next line...
            default:
                addToken( segment, currentTokenStart, end - 1, currentTokenType, newStartOffset + currentTokenStart );
                addNullToken();
        }

        // Return the first token in our linked list.
        return firstToken;
    }

    //------------------------------------------------------------------------//

    private static TokenMap createTokenMap()
    {
        TokenMap tm = new TokenMap( true );    // true == ignore-case

        for( String sCMD : Language.CMD_WORDS )
        {
            tm.put( sCMD, Token.RESERVED_WORD );
        }

        for( String sFn : UtilSys.getConfig().newXprEval().getFunctions() )
        {
            tm.put( sFn, Token.FUNCTION );
        }

        for( Map.Entry<String, List<String>> entry : UtilSys.getConfig().newXprEval().getExtendedTypes().entrySet() )
        {
            tm.put( entry.getKey(), Token.FUNCTION );

            for( String method : entry.getValue() )
                tm.put( method, Token.FUNCTION );
        }

        for( String sOp : UtilSys.getConfig().newXprEval().getOperators() )
        {
                 if( Language.isArithmeticOp( sOp ) )  tm.put( sOp, Token.OPERATOR );
            else if( Language.isBitwiseOp(    sOp ) )  tm.put( sOp, Token.OPERATOR );
            else if( Language.isBooleanOp(    sOp ) )  tm.put( sOp, Token.RESERVED_WORD_2 );
            else if( Language.isRelationalOp( sOp ) )  tm.put( sOp, Token.OPERATOR );
            else if( Language.isSendOp(       sOp ) )  tm.put( sOp, Token.RESERVED_WORD_2 );
            else                                       tm.put( sOp, Token.OPERATOR );
        }

        // Preprocessor ------------------------------

        tm.put( "TRUE"    , Token.PREPROCESSOR );
        tm.put( "FALSE"   , Token.PREPROCESSOR );
        tm.put( "CLOSED"  , Token.PREPROCESSOR );
        tm.put( "ON"      , Token.PREPROCESSOR );
        tm.put( "YES"     , Token.PREPROCESSOR );
        tm.put( "OPEN"    , Token.PREPROCESSOR );
        tm.put( "OFF"     , Token.PREPROCESSOR );
        tm.put( "NO"      , Token.PREPROCESSOR );
        tm.put( "IS"      , Token.OPERATOR );
        tm.put( "EQUALS"  , Token.OPERATOR );
        tm.put( "ARE"     , Token.OPERATOR );
        tm.put( "BELOW"   , Token.OPERATOR );
        tm.put( "ABOVE"   , Token.OPERATOR );
        tm.put( "LEAST"   , Token.OPERATOR );
        tm.put( "MOST"    , Token.OPERATOR );
        tm.put( "UNEQUALS", Token.OPERATOR );
        tm.put( "IS_NOT"  , Token.OPERATOR );
        tm.put( "SET"     , Token.OPERATOR );
        tm.put( "BAND"    , Token.OPERATOR );
        tm.put( "BOR"     , Token.OPERATOR );
        tm.put( "BXOR"    , Token.OPERATOR );
        tm.put( "BNOT"    , Token.OPERATOR );
        tm.put( "\\"      , Token.OPERATOR );
        tm.put( "AND"     , Token.RESERVED_WORD_2 );
        tm.put( "OR"      , Token.RESERVED_WORD_2 );
        tm.put( "XOR"     , Token.RESERVED_WORD_2 );
        tm.put( "NOT"     , Token.RESERVED_WORD_2 );

        return tm;
    }
}