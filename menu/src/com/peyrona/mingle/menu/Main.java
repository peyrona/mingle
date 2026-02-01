package com.peyrona.mingle.menu;

import com.peyrona.mingle.menu.util.UtilUI;
import com.peyrona.mingle.updater.Updater;
import java.awt.GraphicsEnvironment;
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
    private static boolean isInteractive = false;

    //------------------------------------------------------------------------//

    public static void main( String[] args )
    {
        checkForUpdates();

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

            if( GraphicsEnvironment.isHeadless() )
            {
                menu.run();
            }
            else    // GUI mode with no args: directly launch Glue
            {
                if( UtilUI.confirm( "No parameter received. Do you want to launch 'Glue'?" ) )
                {
                    menu.run( "g" );
                    isInteractive = false;
                }
                else
                {
                   menu.run();
                }
            }
        }
    }

    public static boolean isInteractive()
    {
        return isInteractive;
    }

    //------------------------------------------------------------------------//

    private static void checkForUpdates()
    {
        Updater.updateIfNeeded( false,
                                () ->
                                {   // This callback is invoked only if a newer version exists
                                    System.out.println( "\nA newer version of Mingle is available." );
                                    System.out.println( "\nNew versions will take effect the next time each tool is launched.\n" );
                                    System.out.println( "\nNote that the update would run in background." );

                                    return UtilUI.confirm( "Do you want to update now?" );
                                } );
    }
}