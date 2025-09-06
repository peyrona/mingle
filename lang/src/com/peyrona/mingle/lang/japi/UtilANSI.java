
package com.peyrona.mingle.lang.japi;

/**
 *
 *
 * @author Francisco José Morero Peyrona
 *
 * Official web site at: <a href="https://mingle.peyrona.com">https://mingle.peyrona.com</a>
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

    public static boolean isBegin( char ch )
    {
        return ch == PREFIX;
    }

    public static boolean isEnd( String str )
    {
        return UtilStr.endsWith( str, RESET );
    }

    public static boolean isEnd( StringBuilder sb )
    {
        return UtilStr.endsWith( sb, RESET );
    }

    public static boolean isCls( String str )
    {
        return str.startsWith( CLS ) && UtilStr.endsWith( str, RESET );
    }

    public static String cls()
    {
        if( isUnix )
            return CLS + RESET;

        return "";
    }

    public static String toRed( String str )
    {
        if( isUnix )
            return RED + str + RESET;

        return str;
    }

    public static String toYellow( String str )
    {
        if( isUnix )
            return YELLOW + str + RESET;

        return str;
    }

    public static String toPurple( String str )
    {
        if( isUnix )
            return PURPLE + str + RESET;

        return str;
    }

    public static String toCyan( String str )
    {
        if( isUnix )
            return CYAN + str + RESET;

        return str;
    }

    public static void outCls()
    {
        if( isUnix )
        {
            System.out.print( cls() );     // ANSI code to clear the screen
            System.out.flush();
        }
    }

    public static void outRed( String str )
    {
        System.out.println( (isUnix ? toRed( str ) : str) );
        System.out.flush();
    }

    public static void outYellow( String str )
    {
        System.out.println( (isUnix ? toYellow( str ) : str) );
        System.out.flush();
    }

    public static void outPurple( String str )
    {
        System.out.println( (isUnix ? toPurple( str ) : str) );
        System.out.flush();
    }

    public static void outCyan( String str )
    {
        System.out.println( (isUnix ? toCyan( str ) : str) );
        System.out.flush();
    }

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

    public static short toColor( String str )
    {
        if( str.startsWith( CYAN   ) )  return nCYAN;
        if( str.startsWith( PURPLE ) )  return nPURPLE;
        if( str.startsWith( RED    ) )  return nRED;
        if( str.startsWith( YELLOW ) )  return nYELLOW;

        return nDEFAULT;
    }
}