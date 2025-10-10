
package com.peyrona.mingle.updater;

/**
 *
 * @author Francisco José Morero Peyrona
 *
 * Official web site at: <a href="https://github.com/peyrona/mingle">https://github.com/peyrona/mingle</a>
 */
public final class Test
{
    /**
     * @param as the command line arguments
     */
    public static void main( String[] as )
    {
        String sPwd = null;

//        try( Scanner scanner = new Scanner( System.in ) )
//        {
//            System.out.println( "Enter password and press Enter to continue..." );
//            sPwd = scanner.nextLine();
//        }

        Uploader.upload( sPwd );
        ///////////////////////////////////////////Updater.update();
    }
}