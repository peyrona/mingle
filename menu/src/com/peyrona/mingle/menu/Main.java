package com.peyrona.mingle.menu;

import com.peyrona.mingle.menu.util.UtilUI;
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
                if( UtilUI.confirm( "No parameter received.\nDo you want to launch 'Glue'?" ) )
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

//    private static void checkPermissions()
//    {
//        if( )
//
//        if(  )
//
//        boolean isFileOwnerAdmin = false;
//
//        if( ! UtilSys.isAdmin() )
//    }
}