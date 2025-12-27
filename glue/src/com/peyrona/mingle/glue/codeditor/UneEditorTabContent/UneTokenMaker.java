
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
 * This class extends AbstractTokenMaker to provide comprehensive syntax highlighting
 * for Une code in the RSyntaxTextArea component. Token types are mapped according to
 * the Une EBNF grammar specification.
 * <p>
 * Token type mapping:
 * <ul>
 *   <li>{@link Token#RESERVED_WORD} - Command keywords: DEVICE, DRIVER, RULE, SCRIPT, etc.</li>
 *   <li>{@link Token#RESERVED_WORD_2} - Boolean operators: AND, OR, XOR, NOT</li>
 *   <li>{@link Token#FUNCTION} - Functions and extended types: date(), time(), list(), etc.</li>
 *   <li>{@link Token#LITERAL_BOOLEAN} - Boolean literals: TRUE, FALSE, ON, OFF, YES, NO, OPEN, CLOSED</li>
 *   <li>{@link Token#LITERAL_NUMBER_DECIMAL_INT} - Integer numbers</li>
 *   <li>{@link Token#LITERAL_NUMBER_FLOAT} - Floating point numbers</li>
 *   <li>{@link Token#LITERAL_STRING_DOUBLE_QUOTE} - String literals</li>
 *   <li>{@link Token#OPERATOR} - Operators: arithmetic, relational, bitwise, assignment</li>
 *   <li>{@link Token#PREPROCESSOR} - Preprocessor directives: INCLUDE, USE, macros</li>
 *   <li>{@link Token#COMMENT_EOL} - Comments starting with #</li>
 *   <li>{@link Token#MARKUP_TAG_DELIMITER} - Inline code braces</li>
 *   <li>{@link Token#MARKUP_CDATA} - Inline code content</li>
 *   <li>{@link Token#DATA_TYPE} - Data type keywords: BOOLEAN, NUMBER, STRING, ANY</li>
 *   <li>{@link Token#ANNOTATION} - Macros: {*name*}</li>
 *   <li>{@link Token#VARIABLE} - Unit suffixes: s, m, h, d, c, f, k</li>
 * </ul>
 *
 * @author Francisco José Morero Peyrona
 *
 * Official web site at: <a href="https://github.com/peyrona/mingle">https://github.com/peyrona/mingle</a>
 */
final class UneTokenMaker extends AbstractTokenMaker
{
    // Token states for multi-line tokens
    private static final int STATE_NONE   = Token.NULL;
    private static final int STATE_STRING = Token.LITERAL_STRING_DOUBLE_QUOTE;
    private static final int STATE_MACRO  = Token.ANNOTATION;

    private static final TokenMap tokenMap = createTokenMap();

    //------------------------------------------------------------------------//

    @Override
    public TokenMap getWordsToHighlight()
    {
        return tokenMap;
    }

    @Override
    public void addToken( Segment segment, int start, int end, int tokenType, int startOffset )
    {
        // Check if identifier matches a keyword
        if( tokenType == Token.IDENTIFIER )
        {
            int value = wordsToHighlight.get( segment, start, end );

            if( value != -1 )
                tokenType = value;
        }

        super.addToken( segment, start, end, tokenType, startOffset );
    }

    @Override
    public Token getTokenList( Segment segment, int initialTokenType, int startOffset )
    {
        resetTokenList();

        char[] array  = segment.array;
        int    offset = segment.offset;
        int    count  = segment.count;
        int    end    = offset + count;

        int newStartOffset    = startOffset - offset;
        int currentTokenStart = offset;
        int currentTokenType  = initialTokenType;

        // Handle continuation from previous line
        if( currentTokenType == STATE_MACRO )
        {
            // Continue in macro mode
        }
        else if( currentTokenType != STATE_STRING )
        {
            currentTokenType = STATE_NONE;
        }

        for( int i = offset; i < end; i++ )
        {
            char c = array[i];

            switch( currentTokenType )
            {
                case STATE_NONE:
                    currentTokenStart = i;
                    currentTokenType = determineTokenType( array, i, end, c );
                    break;

                case Token.WHITESPACE:
                    if( c == ' ' || c == '\t' )
                    {
                        // Continue whitespace
                    }
                    else
                    {
                        addToken( segment, currentTokenStart, i - 1, Token.WHITESPACE, newStartOffset + currentTokenStart );
                        currentTokenStart = i;
                        currentTokenType = determineTokenType( array, i, end, c );
                    }
                    break;

                case Token.IDENTIFIER:
                    if( RSyntaxUtilities.isLetterOrDigit( c ) || c == '_' )
                    {
                        // Continue identifier
                    }
                    else
                    {
                        // Check for unit suffix after number-like identifier
                        addToken( segment, currentTokenStart, i - 1, Token.IDENTIFIER, newStartOffset + currentTokenStart );
                        currentTokenStart = i;
                        currentTokenType = determineTokenType( array, i, end, c );
                    }
                    break;

                case Token.LITERAL_NUMBER_DECIMAL_INT:
                case Token.LITERAL_NUMBER_FLOAT:
                    if( RSyntaxUtilities.isDigit( c ) || c == '_' )
                    {
                        // Continue number (underscore is visual separator)
                    }
                    else if( c == '.' && currentTokenType == Token.LITERAL_NUMBER_DECIMAL_INT )
                    {
                        // Switch to float
                        currentTokenType = Token.LITERAL_NUMBER_FLOAT;
                    }
                    else if( isUnitSuffix( c ) )
                    {
                        // End number and add unit suffix token
                        addToken( segment, currentTokenStart, i - 1, currentTokenType, newStartOffset + currentTokenStart );
                        addToken( segment, i, i, Token.VARIABLE, newStartOffset + i );
                        currentTokenType = STATE_NONE;
                    }
                    else
                    {
                        addToken( segment, currentTokenStart, i - 1, currentTokenType, newStartOffset + currentTokenStart );
                        currentTokenStart = i;
                        currentTokenType = determineTokenType( array, i, end, c );
                    }
                    break;

                case Token.LITERAL_STRING_DOUBLE_QUOTE:
                    if( c == '"' )
                    {
                        addToken( segment, currentTokenStart, i, Token.LITERAL_STRING_DOUBLE_QUOTE, newStartOffset + currentTokenStart );
                        currentTokenType = STATE_NONE;
                    }
                    else if( c == '{' && i + 1 < end && array[i + 1] == '*' )
                    {
                        // Macro inside string - highlight but stay in string
                        // Just continue, we'll handle the whole string as one token
                    }
                    // Continue string (including newlines for multi-line strings)
                    break;

                case Token.COMMENT_EOL:
                    // Comments extend to end of line
                    i = end - 1;
                    addToken( segment, currentTokenStart, i, Token.COMMENT_EOL, newStartOffset + currentTokenStart );
                    currentTokenType = STATE_NONE;
                    break;

                case Token.OPERATOR:
                    // Operators are mostly single char, but some are double
                    addToken( segment, currentTokenStart, i - 1, Token.OPERATOR, newStartOffset + currentTokenStart );
                    currentTokenStart = i;
                    currentTokenType = determineTokenType( array, i, end, c );
                    break;

                case STATE_MACRO:
                    if( c == '*' && i + 1 < end && array[i + 1] == '}' )
                    {
                        // End of macro
                        addToken( segment, currentTokenStart, i + 1, Token.ANNOTATION, newStartOffset + currentTokenStart );
                        i++; // Skip the '}'
                        currentTokenType = STATE_NONE;
                    }
                    // Continue macro
                    break;

                case Token.SEPARATOR:
                    addToken( segment, currentTokenStart, i - 1, Token.SEPARATOR, newStartOffset + currentTokenStart );
                    currentTokenStart = i;
                    currentTokenType = determineTokenType( array, i, end, c );
                    break;

                default:
                    // Handle any other state by resetting
                    addToken( segment, currentTokenStart, i - 1, currentTokenType, newStartOffset + currentTokenStart );
                    currentTokenStart = i;
                    currentTokenType = determineTokenType( array, i, end, c );
            }
        }

        // Handle end of line
        switch( currentTokenType )
        {
            case STATE_NONE:
                addNullToken();
                break;

            case Token.LITERAL_STRING_DOUBLE_QUOTE:
                // String continues to next line
                addToken( segment, currentTokenStart, end - 1, Token.LITERAL_STRING_DOUBLE_QUOTE, newStartOffset + currentTokenStart );
                break;

            case STATE_MACRO:
                // Unclosed macro (error state, but handle gracefully)
                addToken( segment, currentTokenStart, end - 1, Token.ANNOTATION, newStartOffset + currentTokenStart );
                addNullToken();
                break;

            default:
                addToken( segment, currentTokenStart, end - 1, currentTokenType, newStartOffset + currentTokenStart );
                addNullToken();
        }

        return firstToken;
    }

    //------------------------------------------------------------------------//
    // PRIVATE METHODS

    /**
     * Determines the token type based on the current character and context.
     */
    private int determineTokenType( char[] array, int pos, int end, char c )
    {
        switch( c )
        {
            case ' ':
            case '\t':
                return Token.WHITESPACE;

            case '"':
                return Token.LITERAL_STRING_DOUBLE_QUOTE;

            case '#':
                return Token.COMMENT_EOL;

            case '{':
                if( pos + 1 < end && array[pos + 1] == '*' )
                {
                    return STATE_MACRO;
                }
                else
                {
                    // Opening brace - could be inline code or just a brace
                    // Will be handled as SEPARATOR; inline code detection is in fold parser
                    return Token.SEPARATOR;
                }

            case '}':
                // Closing brace
                return Token.SEPARATOR;

            case '(':
            case ')':
                return Token.SEPARATOR;

            case ';':
            case ',':
                return Token.SEPARATOR;

            case ':':
                return Token.OPERATOR;

            case '\\':
                // Line continuation
                return Token.OPERATOR;

            case '+':
            case '-':
                // Could be operator or sign before number
                if( pos + 1 < end && RSyntaxUtilities.isDigit( array[pos + 1] ) )
                {
                    return Token.LITERAL_NUMBER_DECIMAL_INT;
                }
                return Token.OPERATOR;

            case '*':
            case '/':
            case '%':
            case '^':
            case '~':
                return Token.OPERATOR;

            case '=':
            case '!':
            case '<':
            case '>':
            case '&':
            case '|':
                // These might be part of two-char operators
                return Token.OPERATOR;

            case '.':
                if( pos + 1 < end && RSyntaxUtilities.isDigit( array[pos + 1] ) )
                {
                    return Token.LITERAL_NUMBER_FLOAT;
                }
                return Token.OPERATOR;

            default:
                if( RSyntaxUtilities.isDigit( c ) )
                {
                    return Token.LITERAL_NUMBER_DECIMAL_INT;
                }
                else if( RSyntaxUtilities.isLetter( c ) || c == '_' )
                {
                    return Token.IDENTIFIER;
                }
                return Token.IDENTIFIER;
        }
    }

    /**
     * Checks if a character is a unit suffix (time or temperature).
     */
    private boolean isUnitSuffix( char c )
    {
        return Language.isUnitSuffix( c );
    }

    /**
     * Creates the token map with all Une language keywords and symbols.
     */
    private static TokenMap createTokenMap()
    {
        TokenMap tm = new TokenMap( true );  // true = case-insensitive

        // Command keywords (primary reserved words)
        for( String cmd : Language.CMD_WORDS )
        {
            tm.put( cmd, Token.RESERVED_WORD );
        }

        // Preprocessor-related (special highlighting)
        tm.put( "INCLUDE", Token.PREPROCESSOR );
        tm.put( "USE",     Token.PREPROCESSOR );

        // Device aliases
        tm.put( "SENSOR",   Token.RESERVED_WORD );
        tm.put( "ACTUATOR", Token.RESERVED_WORD );

        // Data types
        tm.put( "BOOLEAN", Token.DATA_TYPE );
        tm.put( "NUMBER",  Token.DATA_TYPE );
        tm.put( "STRING",  Token.DATA_TYPE );
        tm.put( "ANY",     Token.DATA_TYPE );

        // Boolean literals and aliases
        tm.put( "TRUE",   Token.LITERAL_BOOLEAN );
        tm.put( "FALSE",  Token.LITERAL_BOOLEAN );
        tm.put( "ON",     Token.LITERAL_BOOLEAN );
        tm.put( "OFF",    Token.LITERAL_BOOLEAN );
        tm.put( "YES",    Token.LITERAL_BOOLEAN );
        tm.put( "NO",     Token.LITERAL_BOOLEAN );
        tm.put( "OPEN",   Token.LITERAL_BOOLEAN );
        tm.put( "CLOSED", Token.LITERAL_BOOLEAN );

        // Boolean/Conditional operator aliases
        tm.put( "AND", Token.RESERVED_WORD_2 );
        tm.put( "OR",  Token.RESERVED_WORD_2 );
        tm.put( "XOR", Token.RESERVED_WORD_2 );
        tm.put( "NOT", Token.RESERVED_WORD_2 );

        // Relational operator aliases
        tm.put( "IS",      Token.OPERATOR );
        tm.put( "EQUALS",  Token.OPERATOR );
        tm.put( "ARE",     Token.OPERATOR );
        tm.put( "BELOW",   Token.OPERATOR );
        tm.put( "ABOVE",   Token.OPERATOR );
        tm.put( "LEAST",   Token.OPERATOR );
        tm.put( "MOST",    Token.OPERATOR );
        tm.put( "UNEQUAL", Token.OPERATOR );
        tm.put( "IS_NOT",  Token.OPERATOR );

        // Assignment operator alias
        tm.put( "SET", Token.OPERATOR );

        // Bitwise operator aliases
        tm.put( "BAND", Token.OPERATOR );
        tm.put( "BOR",  Token.OPERATOR );
        tm.put( "BXOR", Token.OPERATOR );
        tm.put( "BNOT", Token.OPERATOR );

        // Language names (for SCRIPT LANGUAGE clause)
        tm.put( "JAVA",       Token.DATA_TYPE );
        tm.put( "JAVASCRIPT", Token.DATA_TYPE );
        tm.put( "JS",         Token.DATA_TYPE );
        tm.put( "PYTHON",     Token.DATA_TYPE );
        tm.put( "UNE",        Token.DATA_TYPE );

        // Functions from expression evaluator
        try
        {
            for( String fn : UtilSys.getConfig().newXprEval().getFunctions() )
            {
                tm.put( fn, Token.FUNCTION );
            }

            // Extended types and their methods
            for( Map.Entry<String, List<String>> entry : UtilSys.getConfig().newXprEval().getExtendedTypes().entrySet() )
            {
                tm.put( entry.getKey(), Token.FUNCTION );

                for( String method : entry.getValue() )
                {
                    tm.put( method, Token.FUNCTION );
                }
            }

            // Operators from expression evaluator
            for( String op : UtilSys.getConfig().newXprEval().getOperators() )
            {
                if( Language.isArithmeticOp( op ) ||
                    Language.isBitwiseOp( op )    ||
                    Language.isRelationalOp( op ) ||
                    Language.isSendOp( op ) )
                {
                    tm.put( op, Token.OPERATOR );
                }
                else if( Language.isBooleanOp( op ) )
                {
                    tm.put( op, Token.RESERVED_WORD_2 );
                }
            }
        }
        catch( Exception e )
        {
            // Config may not be available during static initialization in some contexts
            // Fall back to basic function set
            String[] basicFunctions = { "floor", "ceil", "round", "abs", "min", "max",
                                        "sin", "cos", "tan", "sqrt", "log", "rand",
                                        "len", "upper", "lower", "trim", "left", "right", "mid",
                                        "contains", "startswith", "endswith", "replace", "split", "format",
                                        "date", "time", "list", "pair", "pairs",
                                        "year", "month", "day", "weekday", "hour", "minute", "second", "millis",
                                        "add", "put", "get", "has", "size", "empty", "build", "keys", "values",
                                        "str", "num", "bool", "del" };

            for( String fn : basicFunctions )
            {
                tm.put( fn, Token.FUNCTION );
            }
        }

        return tm;
    }
}
