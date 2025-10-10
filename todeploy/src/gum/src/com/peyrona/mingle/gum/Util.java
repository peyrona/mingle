
package com.peyrona.mingle.gum;

import com.peyrona.mingle.lang.MingleException;
import com.peyrona.mingle.lang.japi.UtilIO;
import com.peyrona.mingle.lang.japi.UtilStr;
import com.peyrona.mingle.lang.japi.UtilSys;
import java.io.File;
import java.io.IOException;

/**
 *
 * @author Francisco José Morero Peyrona
 *
 * Official web site at: <a href="https://github.com/peyrona/mingle">https://github.com/peyrona/mingle</a>
 */
final class Util
{
    private static final File fAppDir  = new File( UtilSys.getLibDir(), "/gum/web" );    // Cached in a var because it is heavily used
    private static       File fUserDir = null;

    //------------------------------------------------------------------------//

    /**
     * Returns the folder where my HTML, CSS and JS code for Gum is.
     *
     * @return The folder where my HTML, CSS and JS code for Gum is.
     */
    static File getAppDir()
    {
        return fAppDir;
    }

    static String appendFileMgrCtxTo( String prefix )
    {
        return prefix.concat( "file_mgr/" );
    }

    static String appendUserFilesCtxTo( String prefix )
    {
        return prefix.concat( "user-files/" );
    }

    /**
     * Returns the folder containing static files uploaded by the user.<br>
     * An FTP like server but using HTTP protocol.
     *
     * @return Folder containing static files uploaded by the user.
     * @throws IOException
     */
    static File getServedFilesDir() throws IOException
    {
        return mkDirs( new File( getUserDir(), "served_files" ) );
    }

    /**
     * Returns the folder containing dashboards created by the user.
     *
     * @return Folder containing dashboards created by the user.
     * @throws IOException
     */
    static File getBoardsDir() throws IOException
    {
        return mkDirs( new File( getUserDir(), "dashboards" ) );
    }

    //------------------------------------------------------------------------//

    private Util()
    {
        // Avoid intances of this class
    }

    /**
     * Used for dashboards and static files to be served
     *
     * @return
     */
    private static File getUserDir()
    {
        if( fUserDir == null )
        {
            synchronized( Util.class )
            {
                if( fUserDir == null )
                {
                    String userDir = UtilSys.getConfig().get( "monitoring", "user_files_dir", "" );

                    fUserDir = UtilStr.isEmpty( userDir ) ? new File( UtilSys.getEtcDir(), "gum_user_files" )
                                                          : new File( userDir            , "gum_user_files" );

                    if( ! UtilIO.mkdirs( fUserDir ) )
                        throw new MingleException( "Can not create "+ fUserDir );
                }
            }
        }

        return fUserDir;
    }

    private static File mkDirs( File f ) throws IOException
    {
        if( ! UtilIO.mkdirs( f ) )
            throw new IOException( f +": is not a folder (directory)" );

        return f;
    }
}