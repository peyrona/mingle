
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
 *
 * @author Francisco José Morero Peyrona
 *
 * Official web site at: <a href="https://github.com/peyrona/mingle">https://github.com/peyrona/mingle</a>
 *
 * Official web site at: <a href="https://github.com/peyrona/mingle">https://github.com/peyrona/mingle</a>
 */
public class UtilStr
{
    /** Predefined (standard) Unicode "Record Separator" as char */
    public static final char cSeparator = '\u241E';

    /** Predefined (standard) Unicode "Record Separator" as String */
    public static final String sSeparator = String.valueOf( cSeparator );

    /**
     * System.getProperty("line.separator");
     */
    public static final String sEoL = System.lineSeparator();

    //----------------------------------------------------------------------------//

    /**
     * Checks if passed String is null, has zero length or all its chars are
     * spaces.
     *
     * @param s String to check.
     * @return true when passed String is null, its length is zero or all its chars are spaces.
     */
    public static boolean isEmpty( final String s )
    {
        return ((s == null) || (s.length() == 0) || (s.trim().length() == 0));
    }

    /**
     * Checks if passed String is not null and length is greater than zero and
     * at least one of its chars is not a space char.
     *
     * @param s String to check.
     * @return true when passed String is not null or length is greater than
     *         zero and at least one of its chars is not a space char.
     */
    public static boolean isNotEmpty( final String s )
    {
        return (! isEmpty( s ));
    }

    public static boolean isEmpty( final Object o )
    {
        if( o == null )
            return true;

        String s = o.toString();

        return ((s.length() == 0) || (s.trim().length() == 0));
    }

    public static boolean isNotEmpty( final Object o )
    {
        return (! isEmpty( o ));
    }

    /**
     * Comprueba que todas las cadenas pasadas como parámetro son vacías.
     * (es decir, con que haya una sóla cadena de las pasadas que sea NO vacía,
     *  este método devuelve false).
     *
     * @param strings
     * @return true si todas las cadenas pasadas son vacías.
     */
    public static boolean areEmpty( final String... strings )
    {
        for( final String s : strings )
        {
            if( (s != null) && (s.trim().length() > 0) )
            {
                return false;
            }
        }

        return true;
    }

    /**
     * Comprueba que todas las cadenas pasadas como parámetro son NO vacías.
     * (es decir, con que haya una sóla cadena de las pasadas que es vacía, este
     * método devuelve false).
     *
     * @param strings
     * @return true si todas las cadenas pasadas son NO vacías.
     */
    public static boolean areNotEmpty( final String... strings )
    {
        for( final String s : strings )
        {
            if( (s == null) || (s.trim().length() == 0) )
            {
                return false;
            }
        }

        return true;
    }

    /**
     * Checks if passed String has meaning chars: only letters and digits count (tabs,
     * spaces, CR, LF, punctuation, underscores, etc does not count).
     *
     * @param s String to check.
     * @return True if passed string contains at least one letter or digit.
     */
    public static boolean isMeaningless( final String s )
    {
        if( s != null )
        {
            for( final char ch : s.toCharArray() )
            {
                if( Character.isLetterOrDigit( ch ) )
                {
                    return false;
                }
            }
        }

        return true;
    }

    public static boolean isGlobalSyntax( String str )
    {
        return contains( str, '?', '*', '[', '{' );
    }

    public static char getLastChar( StringBuilder sb )
    {
        return (sb != null && sb.length() > 0) ? sb.charAt( sb.length() - 1 ) : null;
    }

    public static char getLastChar( String s )
    {
        return (s != null && s.length() > 0) ? s.charAt(s.length() - 1) : null;
    }

    /**
     * Checks if passed char is the last one in the passed StringBuilder.
     *
     * <ul>
     * <li>If string is null, returns false
     * <li>If string is empty, returns false
     * <li>Else, returns true if last char of string == passed char
     * </ul>
     *
     * @param sb StringBuilder to check
     * @param c  Character to check
     * @return true if passed char is the last one in the passed string.
     */
    public static boolean isLastChar( StringBuilder sb, char c )
    {
        return ((sb != null && sb.length() > 0) && sb.charAt( sb.length() - 1 ) == c);
    }

    /**
     * Checks if passed char is the last one in the passed string.
     *
     * <ul>
     * <li>If string is null, returns false
     * <li>If string is empty, returns false
     * <li>Else, returns true if last char of string == passed char
     * </ul>
     *
     * @param s String to check
     * @param c Character to check
     * @return true if passed char is the last one in the passed string.
     */
    public static boolean isLastChar( String s, char c )
    {
        return ((s != null && s.length() > 0) && s.charAt(s.length() - 1) == c);
    }

    /**
     * Returns true if the string contains any of passed chars.
     * <p>
     * Note: to check only 1 char, use String::indexOf(...)
     *
     * @param o Object (converted to String) to search into.
     * @param chars 2 or more chars to check (to check only 1 char, use String:indexOf(...))
     * @return true if the string contains any of passed chars.
     */
    public static boolean contains( Object o, char... chars )
    {
        if( o == null || chars == null || chars.length == 0 )
            return false;

        String str = o.toString();

        if( str == null || str.isEmpty() )    // Defensive check: Handle rare case where toString() returns null or is empty
            return false;

        // Performance: String.indexOf(char) is an intrinsic method in the JVM.
        // It is often SIMD-optimized (AVX) and much faster than manually iterating the string.

        for( char c : chars )
        {
            if( str.indexOf( c ) >= 0 )
                return true;
        }

        return false;
    }

    /**
     * Returns true if the string contains any of passed strings (case is ignored).
     *
     * @param obj Object (converted to String) to search into.
     * @param strs 1 or more strings to check.
     * @return true if the string contains any of passed strings (case is ignored).
     */
    public static boolean contains( Object obj, String... strs )
    {
        if( obj == null )
            return false;

        if( UtilColls.isEmpty( strs ) )
            return false;

        // Use Locale.ROOT to avoid unexpected behavior in languages like Turkish
        String searchStr = obj.toString().toLowerCase( Locale.ROOT );

        for( String s : strs )
        {
            if( s == null )
                continue;                           // Continue with next str

            if( s.isEmpty() )
                return true;

            if( searchStr.length() < s.length() )
                continue;                           // Continue with next str

            if( searchStr.contains( s.toLowerCase( Locale.ROOT ) ) )
                return true;
        }

        return false;
    }

    /**
     * @param as Strings to compare.
     * @return
     * @see #areEquals(java.lang.String...)
     */
    public static boolean areNotEquals( String... as )
    {
        return ! areEquals( as );
    }

    /**
     * Returns true if all received strings are equals ignoring case or all are null.
     *
     * @param as Strings to compare.
     * @return true if all received strings are equals ignoring case or all are null.
     */
    public static boolean areEquals( String... as )
    {
        if( (as == null) || (as.length == 0) )
            return true;

        String s = as[0];     // any one, but taking first makes it more readable

        if( s == null )
            return Arrays.stream( as ).allMatch( i -> i == null );

        for( int n = 1; n < as.length; n++ )
        {
            if( (as[n] == null) || (! as[n].equalsIgnoreCase( s )) )
            {
                return false;
            }
        }

        return true;
    }

    /**
     * Left trim (using Character.isWhitespace(...) which includes besides spaces, tabs and others)..
     *
     * @param s To be left trimmed.
     * @return The trimmed string.
     */
    public static String ltrim( String s )
    {
        int n = 0;

        while( n < s.length() && Character.isWhitespace( s.charAt( n ) ) )
            n++;

        return s.substring( n );
    }

    /**
     * Right trim.
     *
     * @param s To be right trimmed.
     * @return The trimmed string.
     */
    public static String rtrim( String s )
    {
        int n = s.length() - 1;

        while( n >= 0 && Character.isWhitespace( s.charAt( n ) ) )
            n--;

        return s.substring( 0, n+1 );
    }

    /**
     * Splits passed string cutting it in chuncks of specified length.
     *
     * @param text
     * @param len
     * @param def
     * @return
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
     * Fills by the left sidfe of the string with indicated char until the
     * string has indicated length. If passed string is null "" is returned.
     *
     * @param string
     * @param padder
     * @param length
     * @return
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
     * Fills by the right sidfe of the string with indicated char until the
     * string has indicated length. If passed string is null "" is returned.
     *
     * @param string
     * @param padder
     * @param length
     * @return
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
     * Returns a String with a length of passed parameter and composed only by chars of passed pattern.
     * @param pattern
     * @param length
     * @return A String with a length of passed parameter and composed only by chars of passed pattern.
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
     * Removes from the String the last N characters.<br>
     * If passed String is null, null is returned.<br>
     * If nChars is bigger than the length of the passed string, empty string ("") is returned.
     *
     * @param fromString
     * @param nChars
     * @return The string resulting  of removing last N chars.
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
     * Removes from StringBuilder last nChars characters.
     *
     * @param fromSB
     * @param nChars
     * @return Received StringBuilder.
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

            for( int n = 0; n < chars.length; n++ )     // This is faster than "for( char c : chars )" and speed is important here
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
     * Removes duplicate spaces when not in between double-quotes.
     *
     * @param str String to trim.
     * @return The string after removing duplicate spaces when not in between double-quotes.
     */
    public static String removeDoubleSpaces( String str )
    {
        return str.replaceAll( " +(?=(?:[^\"]*\"[^\"]*\")*(?![^\"]*\"))", " " );
    }

    /**
     * Removes Une-style comments from received string.
     *
     * @param input To use
     * @return The input after comments being removed.
     */
    public static String removeComments( String input )
    {
        if( isEmpty( input ) )
            return "";

        StringBuilder output    = new StringBuilder();
        boolean       inComment = false;

        for( int n = 0; n < input.length(); n++ )
        {
            char c = input.charAt( n );

            if( inComment )
            {
                if( c == '\n' )
                    inComment = false;
            }
            else
            {
                if( c == '#' ) inComment = true;
                else           output.append( c );
            }
        }

        return output.toString();
    }

    /**
     * Replaces (ignoring case) first occurrence of 'find' in 'where' by 'replace'.
     *
     * @param where String to make the replacement.
     * @param find What to find in 'where'
     * @param replace The string to be used instead of 'find'.
     * @return The 'where' after replacing.
     */
    public static String replaceFirst( String where, String find, String replace )
    {
        int nIndex = where.indexOf( find );    // 1st we assume 'where' and 'find' are in same case (both lower or both upper case): this could make things much faster

        if( nIndex == -1 )                     // Not found, lets try ignoring the case
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
     * Makes a replaceAll, on whole words only, ignoring the case and not replacing inside double-quotes ("...").
     *
     * @param sWhere String where to make the replacements.
     * @param sOld   What to remove.
     * @param sNew   What to add.
     * @return sWhere after replaced.
     */
    public static String replaceAll( String sWhere, String sOld, String sNew )
    {
        String  regex   = "(?i)(?<!\\\\\")\\b" + Pattern.quote(sOld) + "\\b(?!\\\\\")";
        Pattern pattern = Pattern.compile( regex );
        Matcher matcher = pattern.matcher( sWhere );

        return matcher.replaceAll( sNew );
    }

    public static String get1stMacro( String str )
    {
        int[] frame = Language.getMacroFrame( str );

        if( frame == null )
            return null;

        return str.substring( frame[0], frame[1] );
    }

    /**
     * Finds if a string starts with another string ignoring case.
     * <p>
     * This method is faster than doing:
     * <pre>
     *    return where.trim().toLowerCase().startsWith( what.toLowerCase() );
     * </pre>
     * If any of arguments is null, returns false.
     *
     * @param where Where to search
     * @param what  What to search
     * @return true if where starts with what, false otherwise.
     */
    public static boolean startsWith( StringBuilder where, String what )
    {
        return startsWith( ((where == null) ? null : where.toString()), what );
    }

    /**
     * Finds if a string starts with another string ignoring case.
     * <p>
     * This method is faster than doing:
     * <pre>
     *    return where.trim().toLowerCase().startsWith( what.toLowerCase() );
     * </pre>
     * If any of arguments is null, returns false.
     *
     * @param where Where to search
     * @param what  What to search
     * @return true if where starts with what, false otherwise.
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
     * Returns true if 'str' ends with 'end' (the str is trimmed, but not the end) ignoring the case.
     * <p>
     * This is faster than doing: str.toLowercase().endsWith( end.toLowercase() ), especially for long strings.
     *
     * @param str
     * @param end
     * @return true if str ends with 'start' (the str is trimmed, but not the end) ignoring the case.
     */
    public static boolean endsWith( String str, String end )
    {
        if( str == null )
            return false;

        if( (end == null) || (end.length() == 0) )
            return true;

        str = str.trim();

        if( str.length() < end.length() )
            return false;

        return str.substring( str.length() - end.length() ).toLowerCase().equals( end.toLowerCase() );
    }

    /**
     * Returns true if 'sb' ends with 'end' ignoring the case.
     * <p>
     * This is faster than doing: sb.toLowercase().endsWith( end.toLowercase() ), especially for long strings.
     *
     * @param sb
     * @param end
     * @return true if 'sb' ends with 'end' ignoring the case.
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

    public static String capitalize( String s )
    {
        char[] ac = s.trim().toLowerCase().toCharArray();

        ac[0] = Character.toUpperCase( ac[0] );

        return String.valueOf( ac );
    }

    public static int countChar( final String where, final char what )
    {
        int counter = 0;

        if( where != null )
        {
            for( final char ch : where.toCharArray() )
            {
                if( ch == what )
                {
                    counter++;
                }
            }
        }

        return counter;
    }

    /**
     * Creates a String built upon received chars.
     *
     * @param chars Those that will be the String.
     * @return The creates String.
     */
    public static String asStr( char... chars )
    {
        return String.valueOf( chars );
    }

    /**
     * If passed object is an instance of Throwable, its stack trace is returned, otherwise
     * its string representation is returned (this is useful for Object::toString()).
     * If passed object is null, then "null" is returned.
     *
     * @param o
     * @return Either the stack trace or the string representation of passed object.
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
            th.printStackTrace( pw );

            return sw.toString();
        }

        ArrayList<String> list = new ArrayList<>();

        toString( o, o.getClass(), list );

        return o.getClass().getName().concat( list.toString() );
    }

    /**
     * Returns the message and the cause (if any) of received Throwable (no stack-trace).<br>
     * If passed object is null, then "" is returned.
     *
     * @param th To represent as string.
     * @return The message and the cause (if any) of received Throwable.
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

    private UtilStr() {}  // Avoid creation of instances of this class

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