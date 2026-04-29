
package com.peyrona.mingle.glue.codeditor.UneEditorTabContent;

import com.peyrona.mingle.glue.JTools;
import com.peyrona.mingle.lang.japi.UtilSys;
import com.peyrona.mingle.lang.lexer.Language;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
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
            {
                tokenType = value;

                // Highlight as FUNCTION only when actually called: name must be followed by '('
                if( tokenType == Token.FUNCTION && !isFollowedByParen( segment, end ) )
                    tokenType = Token.IDENTIFIER;
            }
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
                    else if( c == '\\' && i + 1 < end )
                    {
                        // Check if it's a valid escape sequence: \n, \r, \t, \\, \"
                        char nextChar = array[i + 1];

                        if( nextChar == 'n' || nextChar == 'r' || nextChar == 't' || nextChar == '\\' || nextChar == '"' )
                        {
                            // First, add the string content before the escape sequence
                            if( i > currentTokenStart )
                                addToken( segment, currentTokenStart, i - 1, Token.LITERAL_STRING_DOUBLE_QUOTE, newStartOffset + currentTokenStart );

                            // Add the escape sequence as a separate token
                            addToken( segment, i, i + 1, Token.REGEX, newStartOffset + i );

                            // Skip the escaped character
                            i++;

                            // Start a new string token
                            currentTokenStart = i + 1;
                        }
                        // If not a recognized escape, treat as normal string content
                    }
                    else if( c == '{' && i + 1 < end && array[i + 1] == '*' )    // Macro inside string - tokenize as separate ANNOTATION token
                    {
                        if( i > currentTokenStart )    // First, add the string content before the macro
                            addToken( segment, currentTokenStart, i - 1, Token.LITERAL_STRING_DOUBLE_QUOTE, newStartOffset + currentTokenStart );

                        int macroEnd = -1;   // Find the closing *}

                        for( int j = i + 2; j < end - 1; j++ )
                        {
                            if( array[j] == '*' && array[j + 1] == '}' )
                            {
                                macroEnd = j + 1;
                                break;
                            }
                        }

                        if( macroEnd != -1 )    // Found complete macro - add it as ANNOTATION token
                        {
                            addToken( segment, i, macroEnd, Token.ANNOTATION, newStartOffset + i );

                            i = macroEnd;       // Update position and continue string tokenization after macro
                            currentTokenStart = macroEnd + 1;
                        }
                        else
                        {
                            // Unclosed macro - treat the opening {* as normal string content
                            // This handles multi-line macros gracefully
                        }
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
                    if( c == '*' && i + 1 < end && array[i + 1] == '}' )   // End of macro
                    {
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
     * Returns true if the first non-whitespace character after {@code afterEnd} in the segment
     * is '(', i.e. the preceding identifier is actually being called as a function.
     * Look-ahead is bounded by the current segment (single line).
     */
    private boolean isFollowedByParen( Segment segment, int afterEnd )
    {
        char[] array   = segment.array;
        int    scanEnd = segment.offset + segment.count;

        for( int i = afterEnd + 1; i < scanEnd; i++ )
        {
            char c = array[i];

            if( c == '(' )              return true;
            if( c != ' ' && c != '\t' ) return false;
        }

        return false;
    }

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

        // Boolean literals (core language — not from standard-replaces.une)
        tm.put( "TRUE",  Token.LITERAL_BOOLEAN );
        tm.put( "FALSE", Token.LITERAL_BOOLEAN );

        // Language names for LANGUAGE clause in SCRIPT command only
        tm.put( "UNE",        Token.DATA_TYPE );

        // Language names for LANGUAGE clause in LIBRARY command only (native shared libraries via JNA)
        tm.put( "JVM",        Token.DATA_TYPE );
        tm.put( "C",          Token.DATA_TYPE );
        tm.put( "RUST",       Token.DATA_TYPE );

        // Language names valid in both SCRIPT and LIBRARY commands
        tm.put( "JAVA",       Token.DATA_TYPE );
        tm.put( "JAVASCRIPT", Token.DATA_TYPE );
        tm.put( "JS",         Token.DATA_TYPE );
        tm.put( "PYTHON",     Token.DATA_TYPE );
        tm.put( "RUBY",       Token.DATA_TYPE );
        tm.put( "R",          Token.DATA_TYPE );

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
                else if( Language.isEdgeOp( op ) )
                {
                    tm.put( op, Token.OPERATOR );
                }
            }
        }
        catch( Exception e )
        {
            JTools.error( e );
        }

        // USE-clause aliases from standard-replaces.une (boolean literals, operators, day/month names…)
        // Loaded last so user-defined aliases always take precedence over built-in function/method names
        // (e.g. list.set() would otherwise override the SET alias for the assignment operator).
        try
        {
            loadUseAliases( new File( UtilSys.getIncDir(), "standard-replaces.une" ), tm );
        }
        catch( Exception e )
        {
            // Include dir not yet configured (e.g. unit tests) — aliases simply won't be highlighted.
        }

        return tm;
    }

    /**
     * Reads a Une source file and registers every {@code USE word AS replacement} pair
     * in {@code tm} with the appropriate token type. Silently ignores lines that do not
     * match the expected format so that future file extensions do not break highlighting.
     *
     * @param file Une source file containing USE directives (e.g. standard-replaces.une).
     * @param tm   The token map to populate.
     * @throws IOException If the file cannot be read.
     */
    private static void loadUseAliases( File file, TokenMap tm ) throws IOException
    {
        for( String rawLine : Files.readAllLines( file.toPath(), StandardCharsets.UTF_8 ) )
        {
            // Strip inline comment (everything from the first '#' that is not inside a string literal).
            // Simple heuristic: scan left-to-right, toggle inside-string on '"', stop at '#' outside string.
            boolean inStr = false;
            int     cutAt = -1;

            for( int n = 0; n < rawLine.length(); n++ )
            {
                char ch = rawLine.charAt( n );

                if( ch == '"' )
                {
                    inStr = !inStr;
                }
                else if( ! inStr && ch == '#' )
                {
                    cutAt = n; break;
                }
            }

            String line = ((cutAt >= 0) ? rawLine.substring( 0, cutAt ) : rawLine).trim();

            if( line.isEmpty() )
                continue;

            // Strip leading "USE " keyword if present (first pair on a USE block line).
            if( line.regionMatches( true, 0, "USE ", 0, 4 ) )
                line = line.substring( 4 ).trim();

            // Must contain " AS " to be a valid alias pair.
            int asIdx = line.toUpperCase().indexOf( " AS " );

            if( asIdx < 0 )
                continue;

            String word        = line.substring( 0, asIdx  ).trim();
            String replacement = line.substring( asIdx + 4 ).trim();

            // Strip surrounding double quotes from the replacement (e.g. "\n" → \n).
            if( replacement.startsWith( "\"" ) && replacement.endsWith( "\"" ) && replacement.length() >= 2 )
                replacement = replacement.substring( 1, replacement.length() - 1 );

            if( word.isEmpty() || replacement.isEmpty() )
                continue;

            tm.put( word, classifyReplacement( replacement ) );
        }
    }

    /**
     * Maps a USE replacement value to the RSyntaxTextArea token type that best represents
     * how the aliased word is used in Une source code.
     *
     * @param replacement The right-hand side of a {@code USE word AS <replacement>} pair.
     * @return An RSyntaxTextArea {@link Token} type constant.
     */
    private static int classifyReplacement( String replacement )
    {
        if( "true".equalsIgnoreCase( replacement ) || "false".equalsIgnoreCase( replacement ) )
            return Token.LITERAL_BOOLEAN;

        if( replacement.matches( "\\d+" ) )
            return Token.LITERAL_NUMBER_DECIMAL_INT;

        if( replacement.startsWith( "\"" ) || replacement.startsWith( "\\" ) )
            return Token.LITERAL_STRING_DOUBLE_QUOTE;

        // Boolean operators (&&, ||, |&, !) → RESERVED_WORD_2 for distinct colour.
        if( Language.isBooleanOp( replacement ) )
            return Token.RESERVED_WORD_2;

        // Relational, edge-detection (?>, ?<, ?=, ?!=, ?<>), bitwise, assignment → OPERATOR.
        return Token.OPERATOR;
    }
}