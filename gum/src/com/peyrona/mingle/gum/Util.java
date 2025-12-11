
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
                            userDir = lst.get( 0 ).toString();
                    }
                    catch( IOException | URISyntaxException exc )
                    {
                        if( exc instanceof IOException )  throw (IOException) exc;
                        else                              throw new IOException( exc );
                    }
                }

                fUserDir = UtilStr.isMeaningless( userDir ) ? new File( UtilSys.getEtcDir(), "gum_user_base" )
                                                            : new File( userDir            , "gum_user_base" );

                if( ! UtilIO.mkdirs( fUserDir ) )
                    throw new MingleException( "Can not create "+ fUserDir );

                String result = UtilIO.canRead( fUserDir, true );

                if( result != null )
                    throw new MingleException( result );

                //----------------------------------------------------
//                if( UtilSys.isDevEnv )
//                    fUserDir = new File( UtilSys.fHomeDir.getParentFile(), "balata/gum_user_base" );
                //----------------------------------------------------
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