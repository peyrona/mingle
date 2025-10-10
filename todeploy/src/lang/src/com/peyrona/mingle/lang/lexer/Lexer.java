
package com.peyrona.mingle.lang.lexer;

import com.peyrona.mingle.lang.interfaces.ICandi;
import com.peyrona.mingle.lang.japi.UtilColls;
import com.peyrona.mingle.lang.japi.UtilStr;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;

/**
 * A tokenizer (lexer) for the Une language.
 * <p>
 * This implementation is a compromise of simplicity, speed and power.<br>
 * It is more than a pure lexer but less than an parser (e.g. does not generate ASTs).
 * <p>
 * This lexer recognizes operators, recognizes their type (arithmetic, boolean,
 * assign, etc) but not distinguishes between unary and not unary operators, and should
 * not do it because there is a common agreement in regarding this.<br>
 * <br><br>
 * Good starting point to learn how a super-simple lexer, parser and compiler works:
 *     https://github.com/jamiebuilds/the-super-tiny-compiler
 *
 * BinaryMinus vs UnaryMinus:
 *     https://social.msdn.microsoft.com/Forums/en-US/b2424e91-c4fd-48a1-bee5-a7e21536b122/lexer-binaryminus-vs-unaryminus?forum=csharpgeneral
 */
public final class Lexer
{
    //------------------------------------------------------------------------//
    // STATIC METHODS

    /**
     * Receives a list of Lexemes representing a chunk of code and returns its corresponding string.
     *
     * @param lstTokens List of Lexemes representing an expression.
     * @return returns the expression (as string) that corresponds with received Lexemes.
     */
    public static String toCode( Collection<Lexeme> lstTokens )
    {
        if( UtilColls.isEmpty( lstTokens ) )
            return "";

        final StringBuilder sb = new StringBuilder( lstTokens.size() * 8 );

        for( Lexeme lex : lstTokens )
        {
            if( lex.isBoolean() || lex.isExtendedDataType() || lex.isName() || lex.isNumber() )
                sb.append( ' ' ).append( lex.text );

            else if( lex.isCommandWord() )
                sb.append( ' ' ).append( lex.text.toUpperCase() );

            else if( lex.isInline() )
                sb.append( "\n{\n" ).append( lex.text ).append( "\n}\n" );

            else if( lex.isParamSep() )
                sb.append( lex.text() ).append( ' ' );

            else if( lex.isString() )
                sb.append( ' ' ).append( Language.toString( lex.text ) ).append( ' ' );

            else if( Language.isSendOp( lex.text ) )       // Check first if it is specifically the send op
                sb.append( lex.text );

            else if( lex.isOperator() )                    // Now consider the rest of operators
                sb.append( ' ' ).append( lex.text );       // Same

            else
                sb.append( lex.text );
        }

        return UtilStr.removeDoubleSpaces( sb.toString() ).trim();
    }

    //------------------------------------------------------------------------//

    private final String              code;
    private final List<Lexeme>        lstLexeme = new ArrayList<>();
    private final List<ICandi.IError> lstErrors = new ArrayList<>();
    private       int                 offset    = 0;    // 0 based
    private       int                 line      = 1;    // 1 based
    private       int                 column    = 0;    // 1 based (to make it 1 based, must start in 0)

    //------------------------------------------------------------------------//
    // CONSTRUCTOR

    /**
     * Class constructor.
     *
     * @param code Code to analyze.
     */
    public Lexer( String code )
    {
        Objects.requireNonNull( code );

        this.code = code.replaceAll( "\\R", "\n" );   // Only '\n' counts
        lexemize();

        while( (! lstLexeme.isEmpty()) && lstLexeme.get( 0 ).isEoL() )
            lstLexeme.remove( 0 );

        while( (! lstLexeme.isEmpty()) && UtilColls.getAt( lstLexeme, -1 ).isEoL() )
            UtilColls.removeTail( lstLexeme );
    }

    //------------------------------------------------------------------------//
    // PUBLIC INTERFACE

    /**
     * Returns the errors found during analyzing the code.
     * @return
     */
    public List<ICandi.IError> getErrors()
    {
        return lstErrors;
    }

    /**
     * Returns the tokens found during analyzing the code.
     * @return
     */
    public List<Lexeme> getLexemes()
    {
        return lstLexeme;
    }

    @Override
    public String toString()
    {
        return toCode( lstLexeme );
    }

    //------------------------------------------------------------------------//
    // PRIVATE INTERFACE

    /**
     * Process the source code building the Lexemes that compose received string.
     */
    private void lexemize()
    {
        while( isNotEoF() )
        {
            char ch = readChar( true );     // true == obey line-continuation-char (do not ignore it)

// FIXME:
// I want to allow comments like following -->
//     DRIVER RPiGpioDriver
//     CONFIG
//         pin    SET 10
//       # pull   SET "up"   <--------------
//         invert SET true
//
// In these cases, it is necessary to jump over the last char: '\n' (so the '\n' will not be taken in consideration)
//
//            if( ch == Language.COMMENT )
//            {
//                while( isNotEoF() && readChar( false ) != Language.END_OF_LINE )
//                    skip( 1 );
//
//                if( isNotEoF() )
//                {
//                    skip( 1 );    // Skip the newline character and
//                    line++;       // update line and
//                    column = 0;   // column tracking
//                }
//
//                continue;
//            }

            if( Language.isBlank( ch ) )    // Language.END_OF_LINE is not blank because it has a special meaning in Une language
            {
                skip( 1 );
            }
            else if( ch == Language.COMMENT )
            {
                while( isNotEoF() && (readChar( true ) != Language.END_OF_LINE) )
                    skip( 1 );

                // skip( 1 ); --> Do not do this becasue offset has to point to the END_OF_LINE, so lines like this can be properly processed: "day AS number     # This is a comment\n"
            }
            else if( ch == Language.END_OF_LINE )
            {
                line++;
                column = 0;

                skip( 1 );

                addLexeme( new Lexeme(), ch, Lexeme.TYPE_DELIMITER );
            }
            else
            {
                Lexeme lex = new Lexeme();

                if( Language.isDigit( ch ) )
                {
                    addLexeme( lex, readNumber(), Lexeme.TYPE_NUMBER );
                    String msg = Language.checkNumber( lex.text );
                    if( msg != null ) { addError( msg, lex ); }
                }
                else if( Language.isOperator( ch ) )
                {
                    addLexeme( lex, readOperator(), Lexeme.TYPE_OPERATOR );
                }
                else if( ch == Language.QUOTE )    // Note: Java uses UTF-16 for the internal text representation: perfect for Une.
                {                                  // See:  https://docs.oracle.com/javase/8/docs/technotes/guides/intl/overview.html
                    lex.line   = line;      // Takes note in case the string is unclosed
                    lex.column = column;    // Takes note in case the string is unclosed

                    String str = readUntil( Language.QUOTE );

                    if( str == null )
                    {
                        lex.text = "\"";
                        addError( "Unclosed string", lex );
                        return;    // Can not continue when string is unclosed
                    }
                    else
                    {
                        addLexeme( lex, str, Lexeme.TYPE_STRING );
                    }
                }
                else if( ch == Language.DELIMITER )         // Treats only with ';' because '\n' was treated previously
                {
                    skip( 1 );
                    addLexeme( lex, ch, Lexeme.TYPE_DELIMITER );
                }
                else if( Language.isParenthesis( ch ) )
                {
                    skip( 1 );
                    addLexeme( lex, ch, Lexeme.TYPE_PARENTHESIS );
                }
                else if( ch == Language.NATIVE_BEGIN )      // Reads source native code but does not check if it has errors or not
                {
                    String str = readUntil( Language.NATIVE_END );

                    if( str == null ) addError( "Unclosed native code: \""+ Language.NATIVE_BEGIN +"\". Needed: \""+ Language.NATIVE_END +'"', lex );
                    else              addLexeme( lex, str.trim(), Lexeme.TYPE_INLINE_CODE );
                }
                else if( Language.isChar4Name( ch ) )       // It is a name: must be at the end because names can include letters and numbers (e.g.: "MyVar99")
                {
                    String name = readName();

                    addLexeme( lex, name, word2Type( name ) );
                }
                else     // Unrecognized char
                {
                    skip( 1 );
                    lex.text = String.valueOf( ch );
                    lex.type = Lexeme.TYPE_ERROR;
                    addError( "Unrecognized char: \""+ ch +'"', lex );
                }
            }
        }
    }

    private void skip( int chars )
    {
        assert (chars == 1 || chars == -1);

        offset += chars;
        column += chars;
    }

    /**
     * Reads one single char (considering line continuation char).
     * <p>
     * NOTE about line continuation:
     * <pre>
     *     "Line 1
     *      Line 2"  --> "Line1\nLine2"
     *
     *     "Line 1\"
     *      Line 2"  --> "Line1Line2"
     *
     *     "Line 1 \"
     *      Line 2" --> "Line1 Line2"
     *
     *     "Line 1\"
     *       Line 2" --> "Line1 Line2"
     * </pre>
     * @return
     */
    @SuppressWarnings("empty-statement")
    private char readChar( boolean bUseLineContinue )
    {
        char c = (isNotEoF() ? code.charAt( offset ) : (char) 0);

        if( bUseLineContinue  &&
            (c == Language.LINE_CONTINUES) )    // Readed char was LINE_CONTINUES, so we need next char
        {
            while( isNotEoF() && (code.charAt( ++offset ) != Language.END_OF_LINE) );    // Ignores everything after LINE.CONTINUES until the end of the line (v.g. "Start of string \   # My comment)

            if( isNotEoF() )
            {
                offset++;   // Jumps the \n
                line++;
                column = 0;
            }

            c = isEoF() ? 0 : code.charAt( offset );
        }

        return c;   // readchar(...) does not move ::offset unless the char that it readed was a line-contine and it is sayd that line-continue has to be followed
    }

    /**
     * Returns the name that starts at current offset.
     * <p>
     * Even if Une is case insensitive, this method does not change the case of the found name
     * because in the ExEn needs the original case to be shown to the user (the ExEn will
     * UpperCase it to be used internally).
     *
     * @return The name that starts at current offset.
     */
    private String readName()
    {
        StringBuilder sb = new StringBuilder( Language.MAX_NAME_LEN );

        do
        {
            sb.append( readChar( true ) );
            skip( 1 );
        }
        while( isNotEoF() && Language.isChar4Name( readChar( true ) ) );

        return sb.toString();    // Now offset points to next char after the readed name
    }

    private String readNumber()
    {
        StringBuilder sb = new StringBuilder( 64 );

        do
        {
            sb.append( readChar( true ) );
            skip( 1 );
        }
        while( isNotEoF() &&
               (Language.isDigit( readChar( true ) ) || (readChar( true ) == '_')) );

                                                    // Now offset points to next char after the readed number.
        return sb.toString().replace( "_", "" );    // This return value will be checked and an exception thrown if it is invalid
    }

    private String readOperator()
    {
        char current = readChar( true );

        skip( 1 );         // Positions the next char to be readed after the just readed char

        if( ! isEoF() )    // Lets check if next char is part of a 2 chars operator (v.g. ==)
        {
            String sOp = Language.buildRABBO( current, readChar( true ) );

            if( sOp.length() > 1 )
                skip( 1 );            // To point to next char after the Op. If len == 1, ::offset is already pointing to next char after Op

            return sOp;
        }

        return String.valueOf( current );    // Now offset points to next char after the readed operator
    }

    /**
     * Returns the text from current char until passed char is found (not
     * including passed char).
     *
     * @param char What to find as last char.
     * @return The string until passed char is found (nesting if necessary) or
     *         null if last char was not found (considering nesting if necessary).
     */
    private String readUntil( final char ch )
    {
        StringBuilder sbValue  = new StringBuilder( 1024 );
        char          c1stChar = readChar( true );
        boolean       bCanNest = (c1stChar != ch);    // When both are the same (e.g. '"', it is impossible to nest)
        int           nNested  = 1;                   // Nesting level

        skip( 1 );                                    // Skips current char, e.g.: '"' or '{'

        while( isNotEoF() )
        {
            char c = readChar( ch != Language.NATIVE_END );   // When reading native languages (not Une), Language.LINE_CONTINUES can not be taken in consideration

            if( c == Language.END_OF_LINE ) // NEXT: allow '\n' and other escapes inside strings --> && (! isValidEscape( ch )) )
            {
                line++;
                column = 0;
            }

            if( bCanNest )
                nNested += ((c == c1stChar) ? 1  : ((c == ch) ? -1 : 0));

            if( (c == ch) && ((! bCanNest) || (nNested == 0)) )
                break;

            sbValue.append( c );
            skip( 1 );
        }

        if( isNotEoF() )
        {
            skip( 1 );                   // Needed to jump the last '"' or '}'
            return sbValue.toString();   // Now offset points to next char
        }

        return null;    // Unclosed char pair
    }

    private void addLexeme( Lexeme lex, char ch, short type )
    {
        addLexeme( lex, String.valueOf( ch ), type );
    }

    // For Lexeme::column all calculations are zero based and the final value must be 1 based.
    // This is taken into account when making the calculations in this method.
    // When this method is invoked, the pointer is just after the lexeme end.
    private void addLexeme( Lexeme lex, String text, short type )
    {
        // TODO: revisar esto -->
        int back = text.length(); //  + (type == Lexeme.TYPE_STRING ? 2 : 0));   // When String, the 2 quotes ("a string") has to be added to the length because the quotes themselves are not part of the String
        //------------------------------

        lex.text   = text;
        lex.type   = type;
        lex.line   = line;
        lex.column = column - back;
        lex.offset = offset - back;

        lstLexeme.add( lex );
    }

    private void addError( String message, Lexeme fragment )
    {
        lstErrors.add( new CodeError( message, fragment ) );
    }

    //------------------------------------------------------------------------//
    // AUXILIARY FUNCTIONS

    private boolean isEoF()
    {
        return ! isNotEoF();
    }

    private boolean isNotEoF()
    {
        return (offset < code.length());
    }

//    private boolean isValidEscape( char ch )
//    {
//        if( ch != Language.QUOTE )
//            return false;
//
//        char next = readChar( false );
//
//        if( next == '\n' )
//            return false;
//
//        if( next != 'n' && next != 'r' && next != 't' )     // Only following escapes are Une valid: '\n', '\r', '\t'
//        {
//            Lexeme lex = new Lexeme();
//                   lex.text   = "\\" + readChar( false );
//                   lex.type   = Lexeme.TYPE_STRING;
//                   lex.line   = line;
//                   lex.column = column;
//                   lex.offset = offset;
//
//            addError( "Invalid escape [\\"+ readChar( false ) +']', lex );
//        }
//
//        return true;
//    }

    private static short word2Type( String word )
    {
        if( Language.isCmdWord(      word ) )  return Lexeme.TYPE_CMD_WORD;
        if( Language.isBooleanValue( word ) )  return Lexeme.TYPE_BOOLEAN;
        if( Language.isUnitSuffix(   word ) )  return Lexeme.TYPE_UNIT_SUFFIX;

        return Lexeme.TYPE_NAME;
    }

//    public static void main( String[] as )
//    {
//        Lexer lexer = new Lexer( "\"This is \n a string\"" );
//
//        System.out.println( lexer.getErrors() );
//    }
}