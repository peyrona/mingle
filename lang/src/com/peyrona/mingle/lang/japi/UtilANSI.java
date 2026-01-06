
package com.peyrona.mingle.lang.japi;

/**
 * Utility class for ANSI escape sequences and colored terminal output.
 * <p>
 * Provides methods to wrap text with ANSI color codes and to clear the terminal.
 * ANSI codes only work on Unix-based terminals (Linux, macOS); on Windows,
 * the methods return plain text.
 *
 * @author Francisco José Morero Peyrona
 *
 * Official web site at: <a href="https://github.com/peyrona/mingle">https://github.com/peyrona/mingle</a>
 */
public final class UtilANSI
{
    public static final short nDEFAULT = 0;
    public static final short nCYAN    = 1;
    public static final short nPURPLE  = 2;
    public static final short nRED     = 3;
    public static final short nYELLOW  = 4;

    private static final char    PREFIX = '\u001B';
    private static final String  RESET  = PREFIX +"[0m";
    private static final String  RED    = PREFIX +"[31m";
    private static final String  YELLOW = PREFIX +"[33m";
    private static final String  PURPLE = PREFIX +"[35m";
    private static final String  CYAN   = PREFIX +"[36m";
    private static final String  CLS    = "\033[H\033[2J";

    private static final boolean isUnix = UtilSys.isUnix();    // Just 1 byte and saves CPU

    //------------------------------------------------------------------------//

    /**
     * Checks if a character is an ANSI escape sequence prefix.
     *
     * @param ch character to check
     * @return {@code true} if character is the ANSI ESC character (0x1B), {@code false} otherwise
     */
    public static boolean isBegin( char ch )
    {
        return ch == PREFIX;
    }

    /**
     * Checks if a string ends with ANSI reset code.
     *
     * @param str string to check
     * @return {@code true} if string ends with ANSI reset code, {@code false} otherwise
     */
    public static boolean isEnd( String str )
    {
        return UtilStr.endsWith( str, RESET );
    }

    /**
     * Checks if a StringBuilder ends with ANSI reset code.
     *
     * @param sb StringBuilder to check
     * @return {@code true} if StringBuilder ends with ANSI reset code, {@code false} otherwise
     */
    public static boolean isEnd( StringBuilder sb )
    {
        return UtilStr.endsWith( sb, RESET );
    }

    /**
     * Checks if a string is a clear screen command.
     *
     * @param str string to check
     * @return {@code true} if string starts with clear screen code and ends with reset, {@code false} otherwise
     */
    public static boolean isCls( String str )
    {
        return str.startsWith( CLS ) && UtilStr.endsWith( str, RESET );
    }

    /**
     * Returns ANSI clear screen command.
     *
     * @return Clear screen ANSI sequence on Unix systems, empty string on Windows.
     */
    public static String cls()
    {
        if( isUnix )
            return CLS + RESET;

        return "";
    }

    /**
     * Wraps string in red ANSI color codes.
     *
     * @param str string to color
     * @return String wrapped in red ANSI codes on Unix systems, original string on Windows.
     */
    public static String toRed( String str )
    {
        if( isUnix )
            return RED + str + RESET;

        return str;
    }

    /**
     * Wraps string in yellow ANSI color codes.
     *
     * @param str string to color
     * @return String wrapped in yellow ANSI codes on Unix systems, original string on Windows.
     */
    public static String toYellow( String str )
    {
        if( isUnix )
            return YELLOW + str + RESET;

        return str;
    }

    /**
     * Wraps string in purple ANSI color codes.
     *
     * @param str string to color
     * @return String wrapped in purple ANSI codes on Unix systems, original string on Windows.
     */
    public static String toPurple( String str )
    {
        if( isUnix )
            return PURPLE + str + RESET;

        return str;
    }

    /**
     * Wraps string in cyan ANSI color codes.
     *
     * @param str string to color
     * @return String wrapped in cyan ANSI codes on Unix systems, original string on Windows.
     */
    public static String toCyan( String str )
    {
        if( isUnix )
            return CYAN + str + RESET;

        return str;
    }

    /**
     * Prints ANSI clear screen command to stdout.
     * On Windows systems, does nothing.
     */
    public static void outCls()
    {
        if( isUnix )
        {
            System.out.print( cls() );     // ANSI code to clear the screen
            System.out.flush();
        }
    }

    /**
     * Prints a red-colored string to stdout followed by a newline.
     * On Windows systems, prints the string without color.
     *
     * @param str string to print in red
     */
    public static void outRed( String str )
    {
        System.out.println( (isUnix ? toRed( str ) : str) );
        System.out.flush();
    }

    /**
     * Prints a yellow-colored string to stdout followed by a newline.
     * On Windows systems, prints the string without color.
     *
     * @param str string to print in yellow
     */
    public static void outYellow( String str )
    {
        System.out.println( (isUnix ? toYellow( str ) : str) );
        System.out.flush();
    }

    /**
     * Prints a purple-colored string to stdout followed by a newline.
     * On Windows systems, prints the string without color.
     *
     * @param str string to print in purple
     */
    public static void outPurple( String str )
    {
        System.out.println( (isUnix ? toPurple( str ) : str) );
        System.out.flush();
    }

    /**
     * Prints a cyan-colored string to stdout followed by a newline.
     * On Windows systems, prints the string without color.
     *
     * @param str string to print in cyan
     */
    public static void outCyan( String str )
    {
        System.out.println( (isUnix ? toCyan( str ) : str) );
        System.out.flush();
    }

    /**
     * Removes ANSI escape codes from the beginning and end of a string.
     *
     * @param str string to strip ANSI codes from
     * @return String with ANSI codes removed from the beginning and end
     */
    public static String delEsc( String str )
    {
             if( str.startsWith( CYAN   ) )  str = UtilStr.replaceFirst( str, CYAN  , "" );
        else if( str.startsWith( PURPLE ) )  str = UtilStr.replaceFirst( str, PURPLE, "" );
        else if( str.startsWith( RED    ) )  str = UtilStr.replaceFirst( str, RED   , "" );
        else if( str.startsWith( YELLOW ) )  str = UtilStr.replaceFirst( str, YELLOW, "" );
        else if( str.startsWith( CLS    ) )  str = UtilStr.replaceFirst( str, CLS   , "" );

        if( str.endsWith( RESET ) )
            str = UtilStr.removeLast( str, RESET.length() );

        return str;
    }

    /**
     * Determines the color constant based on ANSI escape sequence prefix.
     *
     * @param str string to analyze
     * @return Color constant (nCYAN, nPURPLE, nRED, nYELLOW) if string starts with corresponding ANSI code,
     *         otherwise nDEFAULT
     */
    public static short toColor( String str )
    {
        if( str.startsWith( CYAN   ) )  return nCYAN;
        if( str.startsWith( PURPLE ) )  return nPURPLE;
        if( str.startsWith( RED    ) )  return nRED;
        if( str.startsWith( YELLOW ) )  return nYELLOW;

        return nDEFAULT;
    }
}