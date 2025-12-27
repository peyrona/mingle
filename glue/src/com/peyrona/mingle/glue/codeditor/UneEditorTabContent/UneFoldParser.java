
package com.peyrona.mingle.glue.codeditor.UneEditorTabContent;

import java.util.ArrayList;
import java.util.List;
import javax.swing.text.BadLocationException;
import org.fife.ui.rsyntaxtextarea.RSyntaxTextArea;
import org.fife.ui.rsyntaxtextarea.Token;
import org.fife.ui.rsyntaxtextarea.folding.Fold;
import org.fife.ui.rsyntaxtextarea.folding.FoldParser;
import org.fife.ui.rsyntaxtextarea.folding.FoldType;

/**
 * Fold parser for the Une programming language.
 * <p>
 * This class implements code folding for Une source files, allowing users to
 * collapse and expand sections of code for better readability. The following
 * folding regions are supported:
 * <ul>
 *   <li><b>Command blocks</b> - DEVICE, DRIVER, RULE, WHEN, SCRIPT, INCLUDE, USE commands.
 *       When collapsed, only the first line (command declaration) is shown.</li>
 *   <li><b>Inline code blocks</b> - Content between { and } braces
 *       (typically used in SCRIPT FROM clauses)</li>
 *   <li><b>Multi-line comments</b> - Consecutive comment lines starting with #</li>
 * </ul>
 * <p>
 * Une commands are separated by blank lines (two consecutive newlines), so command
 * blocks extend from the first line after the keyword to the next blank line.
 * <p>
 * Example: When folded, this command:
 * <pre>
 * DEVICE clima_sensor
 *     INIT
 *         downtime SET 1h
 *     DRIVER DaikinDriver
 *         CONFIG
 *             address SET "192.168.1.100"
 * </pre>
 * Will display only: {@code DEVICE clima_sensor}
 *
 * @author Francisco José Morero Peyrona
 *
 * Official web site at: <a href="https://github.com/peyrona/mingle">https://github.com/peyrona/mingle</a>
 */
public final class UneFoldParser implements FoldParser
{
    // Command keywords that start foldable blocks (must be at column 0)
    private static final String[] COMMAND_KEYWORDS =
                                    {
                                        "DEVICE", "SENSOR", "ACTUATOR",
                                        "DRIVER",
                                        "RULE",
                                        "WHEN",      // Anonymous rules start with WHEN (without RULE keyword)
                                        "SCRIPT",
                                        "INCLUDE",
                                        "USE"
                                    };

    //------------------------------------------------------------------------//

    @Override
    public List<Fold> getFolds( RSyntaxTextArea textArea )
    {
        List<Fold> folds = new ArrayList<>();

        try
        {
            int lineCount = textArea.getLineCount();

            if( lineCount == 0 )
                return folds;

            // Track state for different fold types
            Fold currentCommandFold      = null;
            int  commandStartLine        = -1;    // Line where command keyword appears
            Fold currentInlineCodeFold   = null;
            int  inlineCodeBraceDepth    = 0;
            int  consecutiveCommentStart = -1;
            int  consecutiveCommentLines = 0;

            for( int line = 0; line < lineCount; line++ )
            {
                Token token = textArea.getTokenListForLine( line );
                String lineText = getLineText( textArea, line );
                boolean isBlankLine = lineText.trim().isEmpty();
                boolean isCommentLine = isCommentOnlyLine( token );

                // Handle consecutive comment folding
                if( isCommentLine && ! isBlankLine )
                {
                    if( consecutiveCommentStart == -1 )
                    {
                        consecutiveCommentStart = line;
                        consecutiveCommentLines = 1;
                    }
                    else
                    {
                        consecutiveCommentLines++;
                    }
                }
                else if( consecutiveCommentStart != -1 )
                {
                    // End of comment block
                    if( consecutiveCommentLines >= 3 )
                    {
                        // Create fold for 3+ consecutive comment lines
                        Fold commentFold = createFold( textArea, FoldType.COMMENT,
                                                       consecutiveCommentStart,
                                                       line - 1 );
                        if( commentFold != null )
                            folds.add( commentFold );
                    }

                    consecutiveCommentStart = -1;
                    consecutiveCommentLines = 0;
                }

                // Handle inline code blocks { }
                if( currentInlineCodeFold == null )
                {
                    int bracePos = findOpeningBrace( lineText );

                    if( bracePos >= 0 )
                    {
                        inlineCodeBraceDepth = 1;
                        currentInlineCodeFold = new Fold( FoldType.CODE, textArea, textArea.getLineStartOffset( line ) + bracePos );
                    }
                }
                else
                {
                    // We're inside an inline code block - track brace depth
                    inlineCodeBraceDepth += countBraces( lineText );

                    if( inlineCodeBraceDepth <= 0 )
                    {
                        // Found closing brace
                        int closingBracePos = lineText.lastIndexOf( '}' );

                        if( closingBracePos >= 0 )
                        {
                            currentInlineCodeFold.setEndOffset( textArea.getLineStartOffset( line ) + closingBracePos );

                            if( currentInlineCodeFold.getLineCount() > 1 )
                                folds.add( currentInlineCodeFold );
                        }

                        currentInlineCodeFold = null;
                        inlineCodeBraceDepth = 0;
                    }
                }

                // Handle command blocks (only if not inside inline code)
                if( currentInlineCodeFold == null )
                {
                    // Create fold for previous command if we're on the second line
                    if( commandStartLine >= 0 && currentCommandFold == null && line > commandStartLine )
                    {
                        // Start fold from the end of the first line (so only first line shows when collapsed)
                        currentCommandFold = new Fold( FoldType.CODE, textArea, textArea.getLineEndOffset( commandStartLine ) - 1 );
                    }

                    if( isBlankLine && currentCommandFold != null )
                    {
                        // Blank line ends command block
                        int endLine = line - 1;

                        // Skip trailing blank/comment lines to find actual content end
                        while( endLine > commandStartLine && getLineText( textArea, endLine ).trim().isEmpty() )
                            endLine--;

                        if( endLine > commandStartLine )
                        {
                            currentCommandFold.setEndOffset( textArea.getLineEndOffset( endLine ) - 1 );
                            folds.add( currentCommandFold );
                        }

                        currentCommandFold = null;
                        commandStartLine = -1;
                    }
                    else if( ! isBlankLine && ! isCommentLine )
                    {
                        // Check if line starts a new command block (only at column 0, not indented clauses)
                        String commandKeyword = getCommandKeyword( token, lineText );

                        if( commandKeyword != null )
                        {
                            // Close previous command fold if any
                            if( currentCommandFold != null )
                            {
                                int endLine = line - 1;

                                while( endLine > commandStartLine && getLineText( textArea, endLine ).trim().isEmpty() )
                                    endLine--;

                                if( endLine > commandStartLine )
                                {
                                    currentCommandFold.setEndOffset( textArea.getLineEndOffset( endLine ) - 1 );
                                    folds.add( currentCommandFold );
                                }

                                currentCommandFold = null;
                            }

                            // Mark start of new command (fold will be created on next line)
                            commandStartLine = line;
                        }
                    }
                }
            }

            // Close any open folds at end of document
            if( consecutiveCommentStart != -1 && consecutiveCommentLines >= 3 )
            {
                Fold commentFold = createFold( textArea, FoldType.COMMENT,
                                               consecutiveCommentStart,
                                               lineCount - 1 );
                if( commentFold != null )
                    folds.add( commentFold );
            }

            // Handle command fold at end of document
            if( commandStartLine >= 0 )
            {
                // Create fold if not already created
                if( currentCommandFold == null && lineCount > commandStartLine + 1 )
                {
                    currentCommandFold = new Fold( FoldType.CODE, textArea, textArea.getLineEndOffset( commandStartLine ) - 1 );
                }

                if( currentCommandFold != null )
                {
                    int endLine = lineCount - 1;

                    while( endLine > commandStartLine && getLineText( textArea, endLine ).trim().isEmpty() )
                        endLine--;

                    if( endLine > commandStartLine )
                    {
                        currentCommandFold.setEndOffset( textArea.getLineEndOffset( endLine ) - 1 );
                        folds.add( currentCommandFold );
                    }
                }
            }

            if( currentInlineCodeFold != null )
            {
                // Unclosed inline code - fold to end of document
                currentInlineCodeFold.setEndOffset( textArea.getLineEndOffset( lineCount - 1 ) - 1 );

                if( currentInlineCodeFold.getLineCount() > 1 )
                    folds.add( currentInlineCodeFold );
            }
        }
        catch( BadLocationException ble )
        {
            ble.printStackTrace( System.err );   // Should not happen with a valid document
        }

        return folds;
    }

    //------------------------------------------------------------------------//
    // PRIVATE METHODS

    /**
     * Gets the text content of a line.
     */
    private String getLineText( RSyntaxTextArea textArea, int line ) throws BadLocationException
    {
        int start = textArea.getLineStartOffset( line );
        int end   = textArea.getLineEndOffset( line );

        return textArea.getText( start, end - start );
    }

    /**
     * Checks if a line contains only a comment (no code).
     */
    private boolean isCommentOnlyLine( Token token )
    {
        while( token != null && token.isPaintable() )
        {
            int type = token.getType();

            if( type != Token.WHITESPACE && type != Token.COMMENT_EOL )
                return false;

            if( type == Token.COMMENT_EOL )
                return true;

            token = token.getNextToken();
        }

        return false;
    }

    /**
     * Checks if the token list starts with a command keyword at column 0 (not indented).
     * Returns the keyword if found at column 0, null otherwise.
     * <p>
     * This ensures that only top-level commands create folds, not clauses like
     * DRIVER inside a DEVICE block.
     */
    private String getCommandKeyword( Token token, String lineText )
    {
        // Check if line starts with whitespace - if so, it's indented (a clause, not a command)
        if( lineText.length() > 0 && Character.isWhitespace( lineText.charAt( 0 ) ) )
            return null;

        // Skip leading whitespace tokens (shouldn't be any if line starts at column 0)
        while( token != null && token.getType() == Token.WHITESPACE )
            token = token.getNextToken();

        if( token == null || ! token.isPaintable() )
            return null;

        // Check for command keywords
        if( token.getType() == Token.RESERVED_WORD || token.getType() == Token.PREPROCESSOR )
        {
            String text = token.getLexeme().toUpperCase();

            for( String keyword : COMMAND_KEYWORDS )
            {
                if( keyword.equals( text ) )
                    return keyword;
            }
        }

        return null;
    }

    /**
     * Finds the position of an opening brace that starts an inline code block.
     * Returns -1 if not found.
     */
    private int findOpeningBrace( String lineText )
    {
        // Look for '{' that's not part of a macro '{*'
        int pos = 0;

        while( (pos = lineText.indexOf( '{', pos )) >= 0 )
        {
            // Check it's not a macro start
            if( pos + 1 >= lineText.length() || lineText.charAt( pos + 1 ) != '*' )
            {
                // Check if we're in FROM clause context (simple heuristic)
                String beforeBrace = lineText.substring( 0, pos ).toUpperCase();

                if( beforeBrace.contains( "FROM" ) )
                    return pos;
            }

            pos++;
        }

        return -1;
    }

    /**
     * Counts net brace changes in a line (opening minus closing).
     * Ignores braces inside strings and macros.
     */
    private int countBraces( String line )
    {
        int count = 0;
        boolean inString = false;
        boolean inMacro = false;

        for( int n = 0; n < line.length(); n++ )
        {
            char c = line.charAt( n );

            if( inString )
            {
                if( c == '"' )
                    inString = false;
            }
            else if( inMacro )
            {
                if( c == '*' && n + 1 < line.length() && line.charAt( n + 1 ) == '}' )
                {
                    inMacro = false;
                    n++; // Skip '}'
                }
            }
            else
            {
                switch( c )
                {
                    case '"':
                        inString = true;
                        break;

                    case '{':
                        if( n + 1 < line.length() && line.charAt( n + 1 ) == '*' )
                        {
                            inMacro = true;
                            n++; // Skip '*'
                        }
                        else
                        {
                            count++;
                        }
                        break;

                    case '}':
                        count--;
                        break;
                }
            }
        }

        return count;
    }

    /**
     * Creates a fold from start line to end line.
     */
    private Fold createFold( RSyntaxTextArea textArea, int foldType, int startLine, int endLine )
    {
        try
        {
            if( endLine <= startLine )
                return null;

            Fold fold = new Fold( foldType, textArea, textArea.getLineStartOffset( startLine ) );
            fold.setEndOffset( textArea.getLineEndOffset( endLine ) - 1 );

            return fold;
        }
        catch( BadLocationException e )
        {
            return null;
        }
    }
}