
package com.peyrona.mingle.gum;

import com.eclipsesource.json.Json;
import com.eclipsesource.json.JsonArray;
import com.eclipsesource.json.JsonObject;
import com.peyrona.mingle.lang.MingleException;
import com.peyrona.mingle.lang.japi.UtilIO;
import com.peyrona.mingle.lang.japi.UtilStr;
import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Comparator;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/**
 * ServiceFileMgr class extends ServiceBase to provide file management operations
 * through HTTP requests. This class handles GET, POST, PUT, and DELETE requests
 * to manage files and directories, being the root file: in "config.json".
 * By default, it is: '[home]/etc/gum_user_files'.
 *
 * <p>
 * HTTP methods and parameters:
 * <ul>
 * <li>GET: Retrieves the contents of a specific file or lists all files in the root directory.
 *   <ul>
 *   <li>file: Name of the file to retrieve (optional)
 *   </ul>
 * <li>PUT: Creates a new folder.
 *   <ul>
 *   <li>parent: Parent directory (optional)
 *   <li>name: Name of the new folder (required)
 *   </ul>
 * <li>POST: Renames a file or folder.
 *   <ul>
 *   <li>old: Current name of the file or folder (required)
 *   <li>new: New name of the file or folder (required)
 *   </ul>
 * <li>DELETE: Deletes specific files or folders.
 *   <ul>
 *   <li>paths: Semicolon-separated list of file or folder paths to delete (required)
 *   </ul>
 * </ul>
 *
 * @author Francisco José Morero Peyrona
 * Official web site at: <a href="https://github.com/peyrona/mingle">https://github.com/peyrona/mingle</a>
 */
final class ServiceFileMgr extends ServiceBase
{
    private final File fRoot;

    //------------------------------------------------------------------------//
    // PROTECTED SCOPE

    ServiceFileMgr( HttpServletRequest request, HttpServletResponse response ) throws IOException
    {
        super( request, response );

        this.fRoot = Util.getServedFilesDir();
    }

    @Override
    protected void doGet() throws IOException
    {
        String sFile  = asString( "file", null );

        if( sFile != null )
        {
            sendText( UtilIO.getAsText( new File( fRoot, sFile ) ) );
        }
        else
        {
            String response = buildFileTree( fRoot, fRoot.toPath().toRealPath() ).toString();

            if( UtilStr.isNotEmpty( response ) )  sendJSON( response );
            else                                  throw new MingleException( MingleException.SHOULD_NOT_HAPPEN );
        }
    }

    /**
     * Create new folder or save a file (existing or new).
     *
     * @param request
     * @param response
     * @throws javax.servlet.ServletException
     * @throws java.io.IOException
     */
    @Override    // PUT == APPEND
    protected void doPut() throws IOException
    {
        String sParent = asString( "parent", null );
        String sFile   = asString( "name"  , ""   );
        String sType   = asString( "type"  , ""   );
        File   fParent = UtilStr.isEmpty( sParent ) ? fRoot : new File( fRoot, sParent );     // 'fRoot' is always needed
        File   fTarget = new File( fParent, sFile );    // file or dir

        if( "dir".equalsIgnoreCase( sType ) )
        {
            UtilIO.mkdirs( fTarget );
        }
        else if( "file".equalsIgnoreCase( sType ) )
        {
            UtilIO.newFileWriter()
                  .setFile( fTarget )
                  .replace( asString( "content", "" ) );
        }
        else
        {
            throw new IOException( "Wrong type "+ sType +" (must be 'dir' or 'file')");
        }
    }

    /**
     * Rename file or folder.
     *
     * @param request
     * @param response
     * @throws javax.servlet.ServletException
     * @throws java.io.IOException
     */
    @Override    // POST == UPDATE
    protected void doPost() throws IOException
    {
        File fOld = new File( fRoot, asString( "old", "" ) );
        File fNew = new File( fRoot, asString( "new", "" ) );

        if( ! fOld.renameTo( fNew ) )
            throw new IOException( "Error renaming ["+ fOld +"] to ["+ fNew +']' );
    }

    /**
     * Deletes the file passed at the request.
     *
     * @param request
     * @param response
     * @throws javax.servlet.ServletException
     * @throws java.io.IOException
     */
    @Override
    protected void doDelete() throws IOException
    {
        String[] asFileNames = asString( "paths", "" ).split( ";" );

        for( String sName : asFileNames )
            UtilIO.delete( new File( fRoot, sName ) );
    }

    //------------------------------------------------------------------------//

    /**
     * Recursive method to build the tree.
     */
    private JsonObject buildFileTree( File currentDir, Path rootPathObj )
    {
        try
        {
            // 1. Resolve canonical/real path for security (handle symlinks)
            Path currentPathObj = currentDir.toPath().toRealPath();

            // 2. Security Check: Ensure current node is actually inside the root
            // (Prevents following symlinks that point outside the base folder)
            if( !currentPathObj.startsWith( rootPathObj ) )
            {
                return null;
            }

            // 3. Create Relative Path string (e.g., "css/styles.css")
            // Relativize handles separator boundaries correctly unlike substring()
            String relativePath = rootPathObj.relativize( currentPathObj )
                                             .toString()
                                             .replace( '\\', '/' ); // Enforce JSON standard

            JsonObject node = new JsonObject();
            node.add( "path", relativePath );

            // 4. Handle File (Leaf node)
            if( ! currentDir.isDirectory() )
            {
                node.add( "nodes", Json.NULL );
                return node;
            }

            // 5. Handle Directory
            JsonArray childrenArray = new JsonArray();
            File[] files = currentDir.listFiles();

            if( files != null )
            {
                // 6. Sort files: Directories first, then alphabetical
                Arrays.sort( files, Comparator.comparing( File::isDirectory ).reversed()
                             .thenComparing( File::getName, String.CASE_INSENSITIVE_ORDER ) );

                for( File file : files )
                {
                    if( file.isHidden() || !file.canRead() )
                    {
                        continue;
                    }

                    // Recurse
                    JsonObject childNode = buildFileTree( file, rootPathObj );
                    if( childNode != null )
                    {
                        childrenArray.add( childNode );
                    }
                }
            }

            node.add( "nodes", childrenArray );
            return node;
        }
        catch( IOException e )
        {
            // If we can't read the real path of a specific node, skip it
            return null;
        }
    }
}