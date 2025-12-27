
package com.peyrona.mingle.gum;

import com.peyrona.mingle.lang.MingleException;
import com.peyrona.mingle.lang.japi.UtilIO;
import com.peyrona.mingle.lang.japi.UtilStr;
import com.peyrona.mingle.lang.japi.UtilSys;
import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.List;

/**
 * Gum utility methods.
 *
 * @author Francisco José Morero Peyrona
 *
 * Official web site at: <a href="https://github.com/peyrona/mingle">https://github.com/peyrona/mingle</a>
 */
final class Util
{
    private static final File fAppDir  = new File( UtilSys.getLibDir(), "gum/web" );    // Cached in a var because it is heavily used
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

    /**
     * Returns the folder containing static files uploaded by the user.<br>
     * An FTP like server but using HTTP protocol.
     *
     * @return Folder containing static files uploaded by the user.
     * @throws IOException
     */
    static File getServedFilesDir() throws IOException
    {
        File fDir = mkDirs( new File( getUserDir(), "served_files" ) );

        if( ! fDir.exists() )
            throw new IOException( fDir +" does not exist and can not be created" );

        return fDir;
    }

    static String getServedFilesContext()
    {
        return "/gum/user-files";
    }

    static File getdFileManagerDir() throws IOException
    {
        File fDir = new File( Util.getAppDir(), "file_mgr" );

        if( ! new File( fDir, "index.html" ).exists() )
            throw new IOException( "FileMgr 'index.html' not found in: " + fDir );

        return fDir.getCanonicalFile();
    }

    static String getFileManagerContext()
    {
        return "/gum/file_mgr";
    }

    static File getDashboardManagerDir() throws IOException
    {
        File fDir = Util.getAppDir(); // e.g. .../gum_user_base/

        if( ! new File( fDir, "index.html" ).exists() )
            throw new IOException( "Dashboard 'index.html' not found in: " + fDir );

        return fDir.getCanonicalFile();
    }

    static String getDashboardManagerContext() throws IOException
    {
        return "/gum";
    }

    /**
     * Returns the folder containing dashboards created by the user.
     *
     * @return Folder containing dashboards created by the user.
     * @throws IOException
     */
    static File getBoardsDir() throws IOException
    {
        File fDir = mkDirs( new File( getUserDir(), "dashboards" ) );

        if( ! fDir.exists() )
            throw new IOException( fDir +" does not exist and can not be created" );

        return fDir.getCanonicalFile();
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
    private static File getUserDir() throws IOException
    {
        if( fUserDir == null )
        {
            synchronized( Util.class )
            {
                String userDir = UtilSys.getConfig().get( "monitoring", "user_base", "" );

                if( ! UtilStr.isMeaningless( userDir ) )
                {
                    try
                    {
                        List<URI> lst = UtilIO.expandPath( userDir );

                        if( ! lst.isEmpty() )
                            userDir = new File( lst.get( 0 ) ).getAbsolutePath();
                    }
                    catch( IOException | URISyntaxException exc )
                    {
                        if( exc instanceof IOException )  throw (IOException) exc;
                        else                              throw new IOException( exc );
                    }
                }

                fUserDir = UtilStr.isMeaningless( userDir ) ? new File( UtilSys.getEtcDir(), "gum_user_base" )
                                                            : new File( userDir );

                if( ! UtilIO.mkdirs( fUserDir ) )
                    throw new MingleException( "Can not create "+ fUserDir );

                String result = UtilIO.canRead( fUserDir, true );

                if( result != null )
                    throw new MingleException( result );
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