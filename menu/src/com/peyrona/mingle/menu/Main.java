package com.peyrona.mingle.menu;

import com.peyrona.mingle.menu.util.UtilUI;
import com.peyrona.mingle.updater.Updater;
import java.util.Arrays;
import java.util.List;

/**
 * The main entry point for Mingle Menu application.
 *
 * @author Francisco José Morero Peyrona
 *
 * Official web site at: <a href="https://github.com/peyrona/mingle">https://github.com/peyrona/mingle</a>
 */
public final class Main
{
    private static boolean isInteractive     = false;
    private static int     idleTimeoutHours  = 0;   // 0 = disabled

    //------------------------------------------------------------------------//

    public static void main( String[] args )
    {
        idleTimeoutHours = getIntFlag( args, "-idle", 5 );
        args = removeKeyFlag( args, "-idle" );

        boolean noUpdate = hasFlag( args, "-nu" );
        args = removeFlag( args, "-nu" );

        if( noUpdate )
        {
            System.out.println( "[INFO] Update check skipped (-nu)." );
        }
        else
        {
            System.out.println( "[INFO] Checking for updates..." );

            Updater.updateIfNeeded( false,
                                    () ->
                                    {   // This callback is invoked only if a newer version exists
                                        System.out.println( "A newer version of Mingle is available.\n" );
                                        System.out.println( "New versions will take effect the next time each tool is launched.\n" );
                                        System.out.println( "Note that the update would run in background." );

                                        return UtilUI.confirm( "Do you want to update now?" );
                                    } );
        }

        final Menu menu = new Menu();

        if( args != null && args.length > 0 )
        {
            String       _1stArg = args[0].trim();
            List<String> lstHelp = Arrays.asList( new String[] { "h", "?", "-h", "-?", "-help", "--help" } );

            if( lstHelp.contains( _1stArg ) )
            {
                args = new String[] { "h" };
                isInteractive = true;
            }

            menu.run( args );
        }
        else   // No arguments provided: the user will interact with the menu
        {
            isInteractive = true;
            menu.run();
        }
    }

    /** Returns true if the given flag is present in the args array (case-insensitive). */
    private static boolean hasFlag( String[] args, String flag )
    {
        if( args == null )
            return false;

        for( String arg : args )
        {
            if( flag.equalsIgnoreCase( arg.trim() ) )
                return true;
        }

        return false;
    }

    /** Returns a copy of args with all occurrences of the given flag removed. */
    private static String[] removeFlag( String[] args, String flag )
    {
        if( args == null )
            return args;

        return Arrays.stream( args )
                     .filter( arg -> ! flag.equalsIgnoreCase( arg.trim() ) )
                     .toArray( String[]::new );
    }

    /** Returns a copy of args with all entries matching the given key=value prefix removed. */
    private static String[] removeKeyFlag( String[] args, String key )
    {
        if( args == null )
            return args;

        String prefix = key + "=";

        return Arrays.stream( args )
                     .filter( arg -> ! arg.toLowerCase().startsWith( prefix.toLowerCase() ) )
                     .toArray( String[]::new );
    }

    /**
     * Returns the integer value of a "-key=N" flag from args, or defaultValue if absent or unparseable.
     *
     * @param args         The command-line arguments to search.
     * @param key          The flag name (e.g. "-idle").
     * @param defaultValue Value returned when the flag is absent or has an invalid value.
     * @return Parsed integer value, or defaultValue.
     */
    private static int getIntFlag( String[] args, String key, int defaultValue )
    {
        if( args == null )
            return defaultValue;

        String prefix = key + "=";

        for( String arg : args )
        {
            if( arg.toLowerCase().startsWith( prefix.toLowerCase() ) )
            {
                try
                {
                    return Integer.parseInt( arg.substring( prefix.length() ).trim() );
                }
                catch( NumberFormatException e )
                {
                    return defaultValue;
                }
            }
        }

        return defaultValue;
    }

    public static boolean isInteractive()
    {
        return isInteractive;
    }

    /** Returns the configured idle timeout in hours, or 0 if disabled. */
    public static int getIdleTimeoutHours()
    {
        return idleTimeoutHours;
    }
}