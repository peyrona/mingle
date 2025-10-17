
package com.peyrona.mingle.lang.lexer;

import com.peyrona.mingle.lang.MingleException;
import com.peyrona.mingle.lang.japi.UtilColls;
import com.peyrona.mingle.lang.japi.UtilStr;
import com.peyrona.mingle.lang.japi.UtilType;
import com.peyrona.mingle.lang.japi.UtilUnit;
import java.util.Arrays;

/**
 *
 * @author Francisco José Morero Peyrona
 *
 * Official web site at: <a href="https://github.com/peyrona/mingle">https://github.com/peyrona/mingle</a>
 *
 * Official web site at: <a href="https://github.com/peyrona/mingle">https://github.com/peyrona/mingle</a>
 */
public final class Language
{
    public static final short MAX_NAME_LEN   = 48;      // Maximum ID length in chars for a command name: Rules, Devices, etc. (79 in Python and 255 in JS)

    public static final char QUOTE           = '"';
    public static final char ASSIGN_OP       = '=';
    public static final char SEND_OP         = ':';
           static final char PARAM_SEPARATOR = ',';     // Function parameters separator (',')
           static final char DELIMITER       = ';';     // Can not be ',' because it is used as function params separator
           static final char NATIVE_BEGIN    = '{';
           static final char NATIVE_END      = '}';
           static final char COMMENT         = '#';
           static final char END_OF_LINE     = '\n';
           static final char LINE_CONTINUES  = '\\';    // Line continuation is '\'

    public static final String[] CMD_WORDS = UtilColls.sort( new String[]
                                                             { "INCLUDE", "USE"   , "AS"  ,
                                                               "SCRIPT" , "FROM"  , "CALL", "LANGUAGE", "ONSTART", "ONSTOP",
                                                               "DRIVER" , "CONFIG", "REQUIRED",
                                                               "DEVICE" , "INIT"  ,
                                                               "RULE"   , "WHEN"  , "THEN", "IF",
                                                               "AFTER"  , "WITHIN", "ANY" ,
                                                               "ALL"    , "BY"    , "FOR" , "ONERROR" } );

    private static final String sOPERATORS = "+-*/%^=!><&|~:,";    // ':' is send-op and ',' is function arguments separator

    //------------------------------------------------------------------------//
    // Escape character for Regular Expressions

    public static String escape( char chr )
    {
        return "\\Q"+ chr +"\\E";
    }

    public static String escape( String str )
    {
        if( str.length() == 1 )
            return escape( str.charAt( 0 ) );

        if( str.indexOf( '/' ) > -1 )
            return str.replaceAll( "(?<!/)/(?!/)", "\\\\" );    // Replaces '/' by '\'. e.g.: "/s*,/s*" --> "\s*,\s*"

        return str;
    }

    //------------------------------------------------------------------------//
    // Units conversions

    /**
     * Returns the amount of milliseconds.
     * <p>
     * Note: 5.5 days is allowed. 5.5 hours is wired but it is user's responsibility.
     *
     * @param nTime Time to convert.
     * @param cSuffix u | t | s | m | h | d
     * @return The amount of milliseconds.
     */
    public static long toMillis( float nTime, char cSuffix )
    {
        switch( cSuffix )
        {                                                 // No suffix --> milliseconds
            case 'u':
            case 'U': return (long) (nTime *       10);   // Centésimas
            case 't':
            case 'T': return (long) (nTime *      100);   // Décimas
            case 's':
            case 'S': return (long) (nTime *     1000);   // Segundos
            case 'm':
            case 'M': return (long) (nTime *    60000);   // Minutos
            case 'h':
            case 'H': return (long) (nTime *  3600000);   // Horas
            case 'd':
            case 'D': return (long) (nTime * 86400000);   // Días

            default : throw new IllegalArgumentException();
        }
    }

    /**
     * Temperature units and conversions.
     *
     * @param temperature
     * @param suffix
     *
     * @return The result of converted received temperature into Celsius .
     */
    public static float toCelsius( float temperature, char suffix )
    {
        switch( suffix )
        {
            case 'f':
            case 'F': return UtilUnit.fahrenheit2celsius( temperature );
            case 'k':
            case 'K': return UtilUnit.kelvin2celsius( temperature );
            case 'c':
            case 'C': return temperature;

            default : throw new IllegalArgumentException();
        }
    }

    /**
     * Converts received object into string and prefixes and postfixes using ::QUOTE.
     *
     * @param o Object to convert
     * @return Received object converted into string and prefixes and postfixes using ::QUOTE.
     */
    public static String toString( Object o )
    {
        String s = o.toString();

        if( s == null || s.isEmpty() )
            return "" + QUOTE + QUOTE;    // Checked: returns proper value

        if( s.charAt(0) == QUOTE && UtilStr.isLastChar( s, QUOTE ) )
            return s;

        return QUOTE + s + QUOTE;
    }

    //------------------------------------------------------------------------//
    // AUXILIARY FUNCTIONS
    // MOST OF THESE METHODS HAVE THEIR COUNTERPART IN Lexer.java WHICH PERFORM
    // SIMILAR CHECKS BUT RECEIVING A Token INSTEAD OF A char.
    // Note: I've done it in this way to keep the code as clean as possible.

    /**
     * Returns true if passed char is lower or equals than 32 but not Language.END_OF_LINE (note that '\n' has meaning in Une).
     * @param ch Character to check
     * @return true if passed char is lower or equals than 32 but not Language.END_OF_LINE.
     */
    public static boolean isBlank( char ch )
    {
        return (ch <= 32) && (ch != Language.END_OF_LINE);
    }

    public static boolean isCmdWord( String word )
    {
        return (Arrays.binarySearch( CMD_WORDS, word.toUpperCase() ) >= 0);    // Must use >= 0
    }

    public static boolean isUnitSuffix( String str )
    {
        return (str != null) &&
               (str.length() == 1) &&
               isUnitSuffix( str.charAt( 0 ) );
    }

    public static boolean isUnitSuffix( char ch )
    {
        return isTimeSuffix( ch ) || isTemperatureSuffix( ch );
    }

    public static boolean isTimeSuffix( char ch )
    {
        return ("UTSMHD".indexOf( ch ) > -1) ||
               ("utsmhd".indexOf( ch ) > -1);
    }

    public static boolean isTemperatureSuffix( char ch )
    {
        return ("CFK".indexOf( ch ) > -1) ||
               ("cfk".indexOf( ch ) > -1);
    }

    public static boolean isString( String text )
    {
        return text.charAt( 0 )            == QUOTE &&
               UtilStr.getLastChar( text ) == QUOTE;
    }

    public static boolean isOperator( String text )
    {
        return isArithmeticOp( text ) ||
               isAssignOp(     text ) ||
               isBooleanOp(    text ) ||
               isRelationalOp( text ) ||
               isSendOp(       text ) ||
               isAssignOp(     text );
    }

    /**
     * Returns true if passed char is one of following: + - * / % ^ = ! > < & | : ,
     * @param ch Character to check.
     * @return true if passed char is one of following: + - * / % ^ = ! > < & | : ,
     */
    public static boolean isOperator( char ch )
    {
        return (sOPERATORS.indexOf( ch ) > -1);
    }

    public static boolean isArithmeticOp( String text )
    {
        return (text.length() == 1) &&
               isArithmeticOp( text.charAt( 0 ) );
    }

    public static boolean isArithmeticOp( char ch )
    {
        switch( ch )
        {
            case '+':
            case '-':
            case '*':
            case '/':
            case '%':                  // Modulus
            case '^':  return true;    // Power
        }

        return false;
    }

    public static boolean isAssignOp( String text )
    {
        return ((text.length() == 1) &&
                isAssignOp( text.charAt( 0 ) ));
    }

    public static boolean isAssignOp( char ch )
    {
        return (ch == ASSIGN_OP );
    }

    public static boolean isBooleanOp( String str )
    {
        return  "!".equals( str ) ||
               "&&".equals( str ) ||
               "||".equals( str ) ||
               "|&".equals( str );
    }

    public static boolean isSendOp( String text )
    {
        return ((text.length() == 1) &&
                isSendOp( text.charAt( 0 ) ));
    }

    public static boolean isSendOp( char ch )
    {
        return (ch == SEND_OP);
    }

    public static boolean isBitwiseOp( String str )
    {
        return  "&".equals( str ) ||
                "|".equals( str ) ||
                "~".equals( str ) ||   // NOT
               "><".equals( str ) ||   // XOR
               ">>".equals( str ) ||
               "<<".equals( str );
    }

    public static boolean isRelationalOp( String str )
    {
        return "==".equals( str ) ||
               "!=".equals( str ) ||
                "<".equals( str ) ||
               "<=".equals( str ) ||
                ">".equals( str ) ||
               ">=".equals( str );
    }

    public static boolean isDelimiter( String s )
    {
        return (s != null) && (s.length() == 1) && isDelimiter( s.charAt( 0 ) );
    }

    public static boolean isDelimiter( char c )
    {
        return c == DELIMITER;
    }

    public static boolean isParamSep( char c )
    {
        return c == PARAM_SEPARATOR;
    }

    public static boolean isParamSep( String s )
    {
        return (s != null) && (s.length() == 1) && isParamSep( s.charAt( 0 ) );
    }

    /**
     * Build Relational Arithmetic Boolean and Bitwise Operator.
     *
     * @param charL
     * @param charR
     * @return The operator: +, *, <, >=, &&, etc
     */
    public static String buildRABBO( char charL, char charR )
    {
        if( (charL == ':') || (charL == ',') || (charL == '~') || isArithmeticOp( charL ) )
            return String.valueOf( charL );

        switch( charL )
        {
            case '=':
            case '!':                   // !=
                if( charR == '=' )      // ==
                    return UtilStr.asStr( charL, charR );
                break;

            case '<':
                if( charR == '=' ||     // <=
                    charR == '<' )      // ==
                    return UtilStr.asStr( charL, charR );
                break;

            case '>':
                if( charR == '=' ||     // >=
                    charR == '>' ||     // >>
                    charR == '<' )      // ><  (XOR)
                        return UtilStr.asStr( charL, charR );
                break;

            case '&':
                if( charR == '&' )      // &&
                    return UtilStr.asStr( charL, charR );
                break;

            case '|':
                if( charR == '|' ||     // "||" OR
                    charR == '&')       // "|&" XOR
                        return UtilStr.asStr( charL, charR );
                break;

            default:
                return null;    // If flow arrives here, it means that charL is none of: '=' '!' '<' '>' '&' '|', neither an arithmetic one
        }

        return String.valueOf( charL );
    }

    /**
     * Returns true if passed char is a number or it is '.'
     * @param ch Character to check
     * @return true if passed char is a number or it is '.'
     */
    public static boolean isDigit( char ch )
    {
        return (Character.isDigit( ch ) || ch == '.');
    }

    public static boolean isParenthesis( String str )
    {
        return (str != null) && (str.length() == 1) && isParenthesis( str.charAt(0) );
    }

    public static boolean isParenthesis( char ch )
    {
        return (ch == '(' || ch == ')');
    }

    public static String checkNumber( String sNum )
    {
        try
        {
            Float.valueOf( sNum );
        }
        catch( NumberFormatException nfe )
        {
            return '"'+ sNum +"\": "+ nfe.getMessage();
        }

        return null;
    }

    public static boolean isBooleanValue( Object value )
    {
        try
        {
            UtilType.toBoolean( value );
        }
        catch( MingleException me )
        {
            return false;
        }

        return true;
    }

    /**
     * <p>Checks whether the given String is a parsable number.</p>
     *
     * <p>Parsable numbers include those Strings understood by {@link Integer#parseInt(String)},
     * {@link Long#parseLong(String)}, {@link Float#parseFloat(String)} or
     * {@link Double#parseDouble(String)}. This method can be used instead of catching {@link java.text.ParseException}
     * when calling one of those methods.</p>
     *
     * <p>Hexadecimal and scientific notations are <strong>not</strong> considered parsable.
     * See {@link #isCreatable(String)} on those cases.</p>
     *
     * <p>{@code Null} and empty String will return {@code false}.</p>
     *
     * <p>
     * <b>CREDITS: taken from Apache NumberUtils</b>
     *
     * @param str the String to check.
     * @return {@code true} if the string is a parsable number.
     */
    public static boolean isNumber( String str )
    {
        if( str == null )
            return false;

        str = str.trim();

        if( str.isEmpty() )
            return false;

        if( str.charAt( str.length() - 1 ) == '.' )
            return false;

        if( str.charAt( 0 ) == '-' || str.charAt( 0 ) == '+' )
        {
            if( str.length() == 1 )
                return false;

            return withDecimalsParsing( str, 1 );
        }

        return withDecimalsParsing( str, 0 );
    }

    /**
     * Returns true if passed char is a letter or a digit or '_'
     *
     * @param ch Character to check
     * @return Returns true if passed char is a letter or a digit or '_'
     */
    public static boolean isChar4Name( char ch )
    {
        return (ch == '_') ||
               Character.isAlphabetic( ch ) ||
               Character.isDigit( ch );
    }

    public static String isValidName( String s )
    {
        s = s.trim();

        if( s.isEmpty() )
            return "is empty";

        if( s.length() > MAX_NAME_LEN )
            return "is too long: max is 48 chars";

        if( Character.isDigit( s.charAt( 0 ) ) )
            return "can not start with a digit";

        StringBuilder sb = new StringBuilder();

        for( int n = 0; n < s.length(); n++ )
        {
            if( ! isChar4Name( s.charAt( n ) ) )
                sb.append( s.charAt( n ) ).append(',');
        }

        if( sb.length() > 0 )
            return "invalid char(s): "+ UtilStr.removeLast( sb, 1 ) +". Only letters, digits and '_' are valid";

        return null;
    }

    //----------------------------------------------------------------------------//
    // Macros

    /**
     * Returns true if received string contains (at any point) a macro.
     *
     * @param s String to check.
     * @return true if received string contains (at any point) a macro.
     */
    public static boolean hasMacro( String s )
    {
        if( (s == null) || (s.length() < 5) )
            return false;

        int n1 = s.indexOf( "{*" );

        if( n1 > -1 )
            return s.indexOf( "*}" ) > n1;

        return false;
    }

    public static String buildMacro( String s )
    {
        return "{*"+ s +"*}";
    }

    /**
     * Returns the first macro found in passed string: A 2 items array: being the first one the start index inside
     * passed string and the second one the end index.<br>
     * If received string has no macro, null is returned.
     *
     * @param str String to search for macro.
     * @return A 2 items array with the start index and the end index of the macro, or null if there is no macro.
     */
    public static int[] getMacroFrame( String str )
    {
        if( str == null )
            return null;

        int n1 = str.indexOf( "{*" );

        if( n1 > -1 )
        {
            int n2 = str.indexOf( "*}", n1 + 2 );

            if( n2 > -1 )
                return new int[] { n1, n2+2 };
        }

        return null;
    }

    //----------------------------------------------------------------------------//

    private Language()
    {
        // Avoids creation of instances of this class
    }

    // <b>CREDITS: taken from Apache NumberUtils</b>
    private static boolean withDecimalsParsing( final String str, final int beginIdx )
    {
        int decimalPoints = 0;

        for( int i = beginIdx; i < str.length(); i++ )
        {
            final boolean isDecimalPoint = str.charAt( i ) == '.';

            if( isDecimalPoint )
                decimalPoints++;

            if( decimalPoints > 1 )
                return false;

            if( ! isDecimalPoint && ! Character.isDigit( str.charAt( i ) ) )
                return false;
        }

        return true;
    }
}
