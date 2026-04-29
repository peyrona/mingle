/*
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.peyrona.mingle.lang.japi;

import com.peyrona.mingle.lang.lexer.Language;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Utility methods related to String manipulation and inspection.
 * <p>
 * This class provides static helper methods for common string operations such as
 * null-safe checking, trimming, padding, case-insensitive comparison, and
 * reflection-based string representation.
 * </p>
 *
 * @author Francisco José Morero Peyrona
 * @see <a href="https://github.com/peyrona/mingle">Official Web Site</a>
 */
public class UtilStr
{
    /** * Predefined (standard) Unicode "Record Separator" as a {@code char}.
     * Unicode: \u241E
     */
    public static final char cSeparator = '\u241E';

    /** * Predefined (standard) Unicode "Record Separator" as a {@code String}.
     */
    public static final String sSeparator = String.valueOf( cSeparator );

    /**
     * The system-dependent line separator string.
     * <p>
     * Equivalent to {@code System.lineSeparator()}. On UNIX systems it returns "\n",
     * on Windows systems it returns "\r\n".
     * </p>
     */
    public static final String sEoL = System.lineSeparator();

    //----------------------------------------------------------------------------//

    /**
     * Returns a string whose value is this string, with all leading and trailing
     * spaces removed.
     *
     * <p> This method is similar to {@link String#trim()} but with important
     * differences:
     *
     * <table border="1" cellpadding="3" cellspacing="0" style="margin-top: 0.5em">
     * <caption>Comparison with String.trim()</caption>
     * <tr>
     *   <th>Feature</th>
     *   <th>This trim()</th>
     *   <th>String.trim()</th>
     * </tr>
     * <tr>
     *   <td><b>Whitespace definition</b></td>
     *   <td>Only space character ('\u0020')</td>
     *   <td>Any character with code ≤ '\u0020' (space)</td>
     * </tr>
     * <tr>
     *   <td><b>Unicode handling</b></td>
     *   <td>Limited to ASCII space only</td>
     *   <td>Limited to characters ≤ U+0020 (doesn't handle Unicode whitespace properly)</td>
     * </tr>
     * <tr>
     *   <td><b>Characters removed</b></td>
     *   <td>Space ( ) only</td>
     *   <td>Space, tab, newline, carriage return, form feed</td>
     * </tr>
     * <tr>
     *   <td><b>Null safety</b></td>
     *   <td>Returns null for null input</td>
     *   <td>Throws NullPointerException for null input</td>
     * </tr>
     * <tr>
     *   <td><b>Performance</b></td>
     *   <td>O(n) with direct charAt() access</td>
     *   <td>O(n) with internal implementation</td>
     * </tr>
     * </table>
     *
     * <p> This method specifically targets only the space character (' '),
     * unlike {@code String.trim()} which removes any character whose codepoint
     * is less than or equal to 'U+0020' (space). This includes:
     * <ul>
     *   <li>'\t' (U+0009 - tab)</li>
     *   <li>'\n' (U+000A - newline)</li>
     *   <li>'\f' (U+000C - form feed)</li>
     *   <li>'\r' (U+000D - carriage return)</li>
     *   <li>' ' (U+0020 - space)</li>
     * </ul>
     *
     * <p> For Unicode-aware whitespace removal, consider using
     * {@link String#strip()} which properly handles all Unicode whitespace
     * characters according to Character.isWhitespace().
     *
     * @param s the input string to trim, may be null
     * @return a string with leading and trailing spaces removed;
     *         returns null if the input is null;
     *         returns empty string if input consists entirely of spaces
     *
     * @see String#trim()
     * @see String#strip()
     * @see Character#isWhitespace(int)
     *
     * @since 1.0
     */
    public static String trim( String s )
    {
        if( s == null || s.isEmpty() )
            return s;

        int start = 0;
        int end   = s.length() - 1;

        while( start <= end && s.charAt( start ) == ' ' )
            start++;

        while( end >= start && s.charAt( end ) == ' ' )
            end--;

        if( start > end )    // If all spaces, return empty string
            return "";

        if( start == 0 && end == s.length() - 1 )   // If no trimming needed, return original string
            return s;

        // Use substring which creates a new string without copying the character array
        // (shares the underlying char array in Java 11)
        return s.substring( start, end + 1 );
    }

    /**
     * Checks if the passed String is empty, null, or whitespace-only.
     * <p>
     * This method considers a string "empty" if:
     * <ul>
     * <li>It is {@code null}</li>
     * <li>Its length is 0</li>
     * <li>It contains only whitespace characters (after trimming)</li>
     * </ul>
     *
     * @param s The String to check.
     * @return {@code true} if the String is null, empty, or whitespace-only; {@code false} otherwise.
     */
    public static boolean isEmpty( final String s )
    {
        return ((s == null) || (s.length() == 0) || (trim( s ).length() == 0));
    }

    /**
     * Checks if the passed String is <b>not</b> empty.
     * <p>
     * This is the logical inverse of {@link #isEmpty(String)}.
     * </p>
     *
     * @param s The String to check.
     * @return {@code true} if the String is not null, has length > 0, and contains at least one non-space character.
     */
    public static boolean isNotEmpty( final String s )
    {
        return ! isEmpty( s );
    }

    /**
     * Checks if the string representation of an Object is empty, null, or whitespace-only.
     *
     * @param o The Object to check.
     * @return {@code true} if the object is null, or its {@code toString()} result is empty/whitespace.
     * @see #isEmpty(String)
     */
    public static boolean isEmpty( final Object o )
    {
        if( o == null )
            return true;

        return isEmpty( o.toString() );
    }

    /**
     * Checks if the string representation of an Object is <b>not</b> empty.
     *
     * @param o The Object to check.
     * @return {@code true} if the object is not null and has a valid string representation.
     * @see #isNotEmpty(String)
     */
    public static boolean isNotEmpty( final Object o )
    {
        return ! isEmpty( o );
    }

    /**
     * Checks that <b>all</b> strings passed as parameters are empty.
     * <p>
     * If even one of the passed strings contains text (is not empty), this method returns {@code false}.
     * </p>
     *
     * @param strings Variable arguments of Strings to check.
     * @return {@code true} if all passed strings are null, empty, or whitespace-only.
     */
    public static boolean areEmpty( final String... strings )
    {
        for( final String s : strings )
            if( isNotEmpty( s ) )
                return false;

        return true;
    }

    /**
     * Checks that <b>all</b> strings passed as parameters are <b>not</b> empty.
     * <p>
     * If even one of the passed strings is empty or null, this method returns {@code false}.
     * </p>
     *
     * @param strings Variable arguments of Strings to check.
     * @return {@code true} if every single passed string contains valid text.
     */
    public static boolean areNotEmpty( final String... strings )
    {
        return ! areEmpty( strings );
    }

    /**
     * Checks if the passed String is devoid of alphanumeric characters.
     * <p>
     * A string is considered "meaningless" if it contains NO letters and NO digits.
     * Punctuation, symbols, whitespace, and control characters are considered meaningless.
     * </p>
     *
     * @param s The String to check.
     * @return {@code true} if the string is null or contains no letters/digits.
     */
    public static boolean isMeaningless( final String s )
    {
        if( s != null )
        {
            for( final char ch : s.toCharArray() )
            {
                if( Character.isLetterOrDigit( ch ) )
                    return false;
            }
        }

        return true;
    }

    /**
     * Checks if the string contains global syntax wildcards.
     * <p>
     * Checks for the presence of '?', '*', '[', or '{'.
     * </p>
     *
     * @param str The string to check.
     * @return {@code true} if any wildcard character is found.
     */
    public static boolean isGlobalSyntax( String str )
    {
        return contains( str, '?', '*', '[', '{' );
    }

    /**
     * Gets the last character of a StringBuilder in a null-safe manner.
     *
     * @param sb The StringBuilder.
     * @return The last char, or {@code 0} (null char behavior depends on implementation context, here it returns implicit char cast) if null/empty.
     * <i>Note: Original code logic implies returning a char type, though the original doc said 'null'. primitives cannot be null.</i>
     */
    public static char getLastChar( StringBuilder sb )
    {
        // Note: The original code returned 'null' for a char return type which is technically invalid in strict contexts
        // unless boxing occurs or it's a compile error, but strictly in Java `return null` for `char` throws an error or is (char)0.
        // Assuming (char)0 or standard exception is acceptable, but keeping logic as close to original structure.
        return (sb != null && sb.length() > 0) ? sb.charAt( sb.length() - 1 ) : '\0';
    }

    /**
     * Gets the last character of a String in a null-safe manner.
     *
     * @param s The String.
     * @return The last char, or {@code '\0'} if the string is null or empty.
     */
    public static char getLastChar( String s )
    {
        return (s != null && s.length() > 0) ? s.charAt(s.length() - 1) : '\0';
    }

    /**
     * Checks if the passed char is the last character in the passed StringBuilder.
     * <ul>
     * <li>If the StringBuilder is {@code null}, returns {@code false}.</li>
     * <li>If the StringBuilder is empty, returns {@code false}.</li>
     * <li>Otherwise, returns {@code true} if the last char equals the passed parameter.</li>
     * </ul>
     *
     * @param sb The StringBuilder to check.
     * @param c  The character to compare against.
     * @return {@code true} if the StringBuilder ends with the specified character.
     */
    public static boolean isLastChar( StringBuilder sb, char c )
    {
        return ((sb != null && sb.length() > 0) && sb.charAt( sb.length() - 1 ) == c);
    }

    /**
     * Checks if the passed char is the last character in the passed String.
     * <ul>
     * <li>If the String is {@code null}, returns {@code false}.</li>
     * <li>If the String is empty, returns {@code false}.</li>
     * <li>Otherwise, returns {@code true} if the last char equals the passed parameter.</li>
     * </ul>
     *
     * @param s The String to check.
     * @param c The character to compare against.
     * @return {@code true} if the String ends with the specified character.
     */
    public static boolean isLastChar( String s, char c )
    {
        return ((s != null && s.length() > 0) && s.charAt(s.length() - 1) == c);
    }

    /**
     * Returns true if the object's string representation contains <b>any</b> of the passed characters.
     * <p>
     * Note: To check for a single character, usage of {@link String#indexOf(int)} is recommended.
     * </p>
     *
     * @param o     The Object (converted to String) to search within.
     * @param chars Variable arguments of chars to look for.
     * @return {@code true} if the string contains any one of the passed characters.
     */
    public static boolean contains( Object o, char... chars )
    {
        if( o == null || chars == null || chars.length == 0 )
            return false;

        String str = o.toString();

        if( str == null || str.isEmpty() )    // Defensive check
            return false;

        // Performance: String.indexOf(char) is often SIMD-optimized (AVX) in the JVM.
        for( char c : chars )
        {
            if( str.indexOf( c ) >= 0 )
                return true;
        }

        return false;
    }

    /**
     * Returns true if the object's string representation contains <b>any</b> of the passed strings (case-insensitive).
     *
     * @param obj  The Object (converted to String) to search within.
     * @param strs Variable arguments of strings to look for.
     * @return {@code true} if the string contains any of the passed substrings (ignoring case).
     */
    public static boolean contains( Object obj, String... strs )
    {
        if( obj == null )
            return false;

        if( UtilColls.isEmpty( strs ) ) // Assumes existence of UtilColls
            return false;

        // Use Locale.ROOT to avoid unexpected behavior in languages like Turkish
        String searchStr = obj.toString().toLowerCase( Locale.ROOT );

        for( String s : strs )
        {
            if( s == null )
                continue;

            if( s.isEmpty() )
                return true;

            if( searchStr.length() < s.length() )
                continue;

            if( searchStr.contains( s.toLowerCase( Locale.ROOT ) ) )
                return true;
        }

        return false;
    }

    /**
     * Checks if the provided strings are <b>not</b> all equal (case-insensitive).
     *
     * @param as Variable arguments of Strings to compare.
     * @return {@code true} if any string differs from the others.
     * @see #areEquals(java.lang.String...)
     */
    public static boolean areNotEquals( String... as )
    {
        return ! areEquals( as );
    }

    /**
     * Checks if all received strings are equal, ignoring case.
     * <p>
     * If the array is null or empty, returns {@code true}.
     * If the first element is null, checks if all others are null.
     * </p>
     *
     * @param as Variable arguments of Strings to compare.
     * @return {@code true} if all strings are equal (ignoring case) or all are null.
     */
    public static boolean areEquals( String... as )
    {
        if( (as == null) || (as.length == 0) )
            return true;

        String s = as[0];

        if( s == null )
            return Arrays.stream( as ).allMatch( i -> i == null );

        for( int n = 1; n < as.length; n++ )
        {
            if( (as[n] == null) || (! as[n].equalsIgnoreCase( s )) )
                return false;
        }

        return true;
    }

    /**
     * Trims whitespace from the leading (left / start) side of the string.
     *
     * @param s The string to trim.
     * @return The string with leading whitespace removed.
     */
    public static String ltrim( String s )
    {
        return (s == null) ? null : s.stripLeading();
    }

    /**
     * Trims whitespace from the trailing (right / end) side of the string.
     *
     * @param s The string to trim.
     * @return The string with trailing whitespace removed.
     */
    public static String ttrim( String s )
    {
        return (s == null) ? null : s.stripTrailing();
    }

    /**
     * Splits text into a list of strings, each having a maximum specified length.
     *
     * @param text The text to split.
     * @param len  The maximum length of each resulting string chunk.
     * @param def  The default string to return if the input text is {@code null}.
     * @return A List of strings split by the specified length.
     */
    public static List<String> splitByLength( String text, int len, String def )
    {
        List<String> as = new ArrayList<>();

        text = (text == null) ? def : text;

        if( text != null )
        {
            int index = 0;

            while( index < text.length() )
            {
                as.add( text.substring( index, Math.min( index + len, text.length() ) ) );
                index += len;
            }
        }

        return as;
    }

    /**
     * Pads the string on the left side with the specified character until it reaches the target length.
     * <p>
     * If the passed string is {@code null}, it is treated as an empty string.
     * If the string is already longer than the target length, the original string is returned (no truncation).
     * </p>
     *
     * @param string The string to pad.
     * @param padder The character to use for padding.
     * @param length The desired total length.
     * @return The padded string.
     */
    public static String leftPad( String string, final char padder, final int length )
    {
        if( string == null )
            string = "";

        if( string.length() < length )
            return UtilStr.fill( padder, length - string.length() ) + string;

        return string;
    }

    /**
     * Pads the string on the right side with the specified character until it reaches the target length.
     * <p>
     * If the passed string is {@code null}, it is treated as an empty string.
     * </p>
     *
     * @param string The string to pad.
     * @param padder The character to use for padding.
     * @param length The desired total length.
     * @return The padded string.
     */
    public static String rightPad( String string, final char padder, final int length )
    {
        if( string == null )
            string = "";

        if( string.length() < length )
            return string + UtilStr.fill( padder, length - string.length() );

        return string;
    }

    /**
     * Creates a String composed of the specified character repeated a specific number of times.
     *
     * @param pattern The character to repeat.
     * @param length  The length of the resulting string.
     * @return A string composed only of the pattern char. Returns empty string if length <= 0.
     */
    public static String fill( final char pattern, final int length )
    {
        if( length <= 0 )
            return "";

        final StringBuilder sb = new StringBuilder( length );

        for( int n = 0; n < length; n++ )
            sb.append( pattern );

        return sb.toString();
    }

    /**
     * Creates a String composed of the specified pattern string repeated to fit the length.
     *
     * @param pattern The string pattern to repeat.
     * @param length  The number of times to repeat the pattern.
     * @return A string composed of the pattern repeated 'length' times.
     */
    public static String fill( final String pattern, int length )
    {
        if( length <= 0 )
            return "";

        final StringBuilder sb = new StringBuilder( length );

        length /= pattern.length();

        for( int n = 0; n <  length; n++ )
            sb.append( pattern );

        return sb.toString();
    }

    /**
     * Removes the last N characters from the String.
     * <ul>
     * <li>If the passed String is {@code null}, returns {@code null}.</li>
     * <li>If nChars is greater than or equal to the string length, returns an empty string ("").</li>
     * </ul>
     *
     * @param fromString The source string.
     * @param nChars     The number of characters to remove from the end.
     * @return The string with the last N chars removed.
     */
    public static String removeLast( String fromString, int nChars )
    {
        if( fromString == null )
            return null;

        if( fromString.length() <= nChars)
            return "";

        return fromString.substring( 0, fromString.length() - nChars );
    }

    /**
     * Removes the last N characters from a StringBuilder.
     * <p>
     * Modifies the passed StringBuilder in place.
     * </p>
     *
     * @param fromSB The source StringBuilder.
     * @param nChars The number of characters to remove.
     * @return The same StringBuilder instance (modified).
     */
    public static StringBuilder removeLast( StringBuilder fromSB, int nChars )
    {
        if( fromSB != null )
        {
            if( fromSB.length() <= nChars)
                fromSB.setLength( 0 );
            else
                fromSB = fromSB.delete( fromSB.length() - nChars, fromSB.length() );
        }

        return fromSB;
    }

    /**
     * Removes all occurrences of specific characters from a string.
     *
     * @param str   The source string.
     * @param chars The characters to remove.
     * @return A new string with the specified characters deleted.
     */
    public static String removeAll( String str, char... chars )
    {
        if( isEmpty( str ) )
            return str;

        if( (chars == null) || (chars.length == 0) )
            return str;

        StringBuilder sbToRet = new StringBuilder( str.length() );

        for( char ch : str.toCharArray() )
        {
            boolean bAdd = true;

            // This inner loop is often faster than standard collection lookups for small arrays
            for( int n = 0; n < chars.length; n++ )
            {
                if( ch == chars[n] )
                {
                    bAdd = false;
                    break;
                }
            }

            if( bAdd )
                sbToRet.append( ch );
        }

        return sbToRet.toString();
    }

    /**
     * Removes duplicate spaces from a string, but preserves spaces inside double-quotes.
     * <p>
     * Example: {@code "a   b   \"c   d\""} becomes {@code "a b \"c   d\""}.
     * </p>
     *
     * @param str The string to process.
     * @return The string with duplicate external spaces removed.
     */
    public static String removeDoubleSpaces( String str )
    {
        return str.replaceAll( " +(?=(?:[^\"]*\"[^\"]*\")*(?![^\"]*\"))", " " );
    }

    /**
     * Replaces the first occurrence of a substring with another, ignoring case.
     *
     * @param where   The string in which to make the replacement.
     * @param find    The substring to search for (case-insensitive).
     * @param replace The string to use as a replacement.
     * @return The modified string.
     */
    public static String replaceFirst( String where, String find, String replace )
    {
        int nIndex = where.indexOf( find );    // 1st we assume 'where' and 'find' are in same case (optimization)

        if( nIndex == -1 )                     // Not found, try ignoring case
        {
            nIndex = where.toLowerCase().indexOf( find.toLowerCase() );
        }

        if( nIndex > -1 )
        {
            where = where.substring( 0, nIndex ) + replace + where.substring( nIndex + find.length() );
        }

        return where;
    }

    /**
     * Replaces all occurrences of a word with another, ignoring case and whole-word boundaries.
     * <p>
     * Does <b>not</b> replace text inside double-quotes ("...").
     * </p>
     *
     * @param sWhere The string in which to make replacements.
     * @param sOld   The word to remove.
     * @param sNew   The word to insert.
     * @return The modified string.
     */
    public static String replaceAll( String sWhere, String sOld, String sNew )
    {
        String  regex   = "(?i)(?<!\\\\\")\\b" + Pattern.quote(sOld) + "\\b(?!\\\\\")";
        Pattern pattern = Pattern.compile( regex );
        Matcher matcher = pattern.matcher( sWhere );

        return matcher.replaceAll( sNew );
    }

    /**
     * Extracts the first macro definition from a string.
     * <p>
     * Delegates logic to {@code Language.getMacroFrame(str)}.
     * </p>
     *
     * @param str The string containing potential macros.
     * @return The substring containing the macro, or {@code null} if not found.
     */
    public static String get1stMacro( String str )
    {
        int[] frame = Language.getMacroFrame( str );

        if( frame == null )
            return null;

        return str.substring( frame[0], frame[1] );
    }

    /**
     * Checks if a StringBuilder starts with a specific string (ignoring case).
     * <p>
     * Optimization: faster than converting to string and lower-casing the entire object.
     * </p>
     *
     * @param where The StringBuilder to check.
     * @param what  The prefix to look for.
     * @return {@code true} if 'where' starts with 'what' (case-insensitive). Returns false if arguments are null.
     */
    public static boolean startsWith( StringBuilder where, String what )
    {
        return startsWith( ((where == null) ? null : where.toString()), what );
    }

    /**
     * Checks if a String starts with a specific prefix (ignoring case).
     *
     * @param where The string to check.
     * @param what  The prefix to look for.
     * @return {@code true} if 'where' starts with 'what' (case-insensitive).
     */
    public static boolean startsWith( String where, String what )
    {
        if( where == null )
            return false;

        if( what.length() == 0 )
            return true;

        if( where.length() < what.length() )
            return false;

        return where.substring( 0, what.length() ).toLowerCase().equals( what.toLowerCase() );  // Only the portion to compare is lower-cased
    }


    /**
     * Checks if a string ends with a specific suffix (ignoring case).
     * <p>
     * Note: The source string {@code str} is trimmed before checking, but the suffix {@code end} is not.
     * </p>
     *
     * @param str The string to check.
     * @param end The suffix to look for.
     * @return {@code true} if 'str' (trimmed) ends with 'end' (case-insensitive).
     */
    public static boolean endsWith( String str, String end )
    {
        if( str == null )
            return false;

        if( (end == null) || (end.length() == 0) )
            return true;

        str = trim( str );

        if( str.length() < end.length() )
            return false;

        return str.substring( str.length() - end.length() ).toLowerCase().equals( end.toLowerCase() );
    }

    /**
     * Checks if a StringBuilder ends with a specific suffix (ignoring case).
     *
     * @param sb  The StringBuilder to check.
     * @param end The suffix to look for.
     * @return {@code true} if 'sb' ends with 'end' (case-insensitive).
     */
    public static boolean endsWith( StringBuilder sb, String end )
    {
        if( sb == null )
            return false;

        if( (end == null) || (end.length() == 0) )
            return true;

        if( sb.length() < end.length() )
            return false;

        return sb.substring( sb.length() - end.length() ).toLowerCase().equals( end.toLowerCase() );
    }

    /**
     * Capitalizes the first character of the string and lowercases the rest.
     *
     * @param s The string to capitalize.
     * @return The capitalized string (e.g., "HELLO" becomes "Hello").
     */
    public static String capitalize( String s )
    {
        if( isEmpty( s ) )
            return s;

        char[] ac = trim( s ).toLowerCase().toCharArray();

        ac[0] = Character.toUpperCase( ac[0] );

        return String.valueOf( ac );
    }

    /**
     * Counts the occurrences of a specific character in a string.
     *
     * @param where The string to search in.
     * @param what  The character to count.
     * @return The number of times 'what' appears in 'where'.
     */
    public static int countChar( final String where, final char what )
    {
        int counter = 0;

        if( where != null )
        {
            for( final char ch : where.toCharArray() )
            {
                if( ch == what )
                    counter++;
            }
        }

        return counter;
    }

    /**
     * Factory method to create a String from a char array (varargs).
     *
     * @param chars The characters that will form the String.
     * @return A new String containing the characters.
     */
    public static String asStr( char... chars )
    {
        return String.valueOf( chars );
    }

    /**
     * Generates a string representation of an object using reflection or stack traces.
     * <ul>
     * <li>If the object is {@code null}, returns "null".</li>
     * <li>If the object is a {@link Throwable}, returns the stack trace.</li>
     * <li>Otherwise, uses reflection to build a string of all declared fields and superclass fields.</li>
     * </ul>
     *
     * @param o The object to represent.
     * @return The stack trace or the reflective string representation.
     */
    public static String toString( Object o )
    {
        if( o == null )
            return "null";

        if( o instanceof Throwable )
        {
            Throwable    th = (Throwable) o;
            StringWriter sw = new StringWriter();
            PrintWriter  pw = new PrintWriter( sw );

            if( isEmpty( th.getMessage() ) )
                sw.append( th.getClass().getSimpleName() + sEoL );

            th.printStackTrace( pw );

            return sw.toString();
        }

        ArrayList<String> list = new ArrayList<>();

        toString( o, o.getClass(), list );

        return o.getClass().getName().concat( list.toString() );
    }

    /**
     * Returns a brief description of a Throwable (Message + Cause).
     * <p>
     * Does not include the full stack trace.
     * </p>
     *
     * @param th The Throwable to describe.
     * @return The message and the cause (if any), or an empty string if null.
     */
    public static String toStringBrief( Throwable th )
    {
        if( th == null )
            return "";

        String str = ((th.getMessage() == null) ? th.getClass().getSimpleName() : th.getMessage());

        if( th.getCause() != null )
            str += "\nCause: "+ th.getCause().getMessage();

        return str;
    }

    //------------------------------------------------------------------------//
    // PRIVATE SCOPE

    /** * Private constructor to prevent instantiation of utility class.
     */
    private UtilStr() {}

    /**
     * Recursive helper for reflection-based toString.
     */
    private static void toString( Object o, Class<?> clazz, List<String> list )
    {
        Field[] fileds = clazz.getDeclaredFields();

        for( Field f : fileds )
        {
            try
            {
                f.setAccessible( true );

                list.add( f.getName() + "=" + f.get( o ) );
            }
            catch( IllegalAccessException e )
            {
                e.printStackTrace( System.err );
            }
        }

        if( clazz.getSuperclass().getSuperclass() != null )
            toString( o, clazz.getSuperclass(), list );
    }
}