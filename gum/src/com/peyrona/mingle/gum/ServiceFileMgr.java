
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
 * <li>PUT: Creates a new folder/file OR updates existing file content.
 *   <ul>
 *   <li>Mode 1 - Create: type=dir|file, name=..., [parent=...], [content=...] (query params)
 *   <li>Mode 2 - Update: file=... (query param), content in request body
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
            sendText( UtilIO.getAsText( resolveSecurePath(sFile) ) );
        }
        else
        {
            String response = buildFileTree( fRoot, fRoot.toPath().toRealPath() ).toString();

            if( UtilStr.isNotEmpty( response ) )  sendJSON( response );
            else                                  throw new MingleException( MingleException.SHOULD_NOT_HAPPEN );
        }
    }

    /**
     * Create new folder, create a new file, or update existing file content.
     *
     * Two modes of operation:
     * 1. Create: type=dir|file, name=..., [parent=...], [content=...] (query params)
     * 2. Update: file=... (query param), content in request body
     *
     * @throws java.io.IOException
     */
    @Override    // PUT == APPEND
    protected void doPut() throws IOException
    {
        String sFilePath = asString( "file", null );

        // Mode 2: Update existing file content (file path in query, content in body)
        if( sFilePath != null )
        {
            File fTarget = resolveSecurePath( sFilePath );

            // Read content from request body
            StringBuilder sb = new StringBuilder();
            String line;

            try( java.io.BufferedReader reader = request.getReader() )
            {
                while( (line = reader.readLine()) != null )
                {
                    if( sb.length() > 0 )
                        sb.append( '\n' );

                    sb.append( line );
                }
            }

            UtilIO.newFileWriter()
                  .setFile( fTarget )
                  .replace( sb.toString() );

            sendText( "OK" );
            return;
        }

        // Mode 1: Create new folder or file (all params in query string)
        String sParent = asString( "parent", null );
        String sName   = asString( "name"  , ""   );
        String sType   = asString( "type"  , ""   );
        File   fParent = UtilStr.isEmpty( sParent ) ? fRoot : resolveSecurePath( sParent );
        File   fTarget = resolveSecurePath( new File(fParent, sName).getPath() );

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

        sendText( "OK" );
    }

    /**
     * Rename file or folder.
     *
     * @throws java.io.IOException
     */
    @Override    // POST == UPDATE
    protected void doPost() throws IOException
    {
        File fOld = resolveSecurePath( asString( "old", "" ) );
        File fNew = resolveSecurePath( asString( "new", "" ) );

        if( ! fOld.renameTo( fNew ) )
            throw new IOException( "Error renaming ["+ fOld +"] to ["+ fNew +']' );

        sendText( "OK" );
    }

    /**
     * Deletes the file passed at the request.
     *
     * @throws java.io.IOException
     */
    @Override
    protected void doDelete() throws IOException
    {
        String[] asFileNames = asString( "paths", "" ).split( ";" );

        for( String sName : asFileNames )
            UtilIO.delete( resolveSecurePath( sName ) );

        sendText( "OK" );
    }

    //------------------------------------------------------------------------//
    // PRIVATE SCOPE

    private File resolveSecurePath( String relativePath ) throws IOException
    {
        // Normalize and validate input first
        if( relativePath == null || relativePath.trim().isEmpty() )
            throw new IOException( "Path cannot be null or empty" );

        // Reject dangerous patterns upfront
        if( relativePath.contains( ".." )  ||
            relativePath.contains( "//" )  ||
            relativePath.contains( "\\" )  ||
            relativePath.startsWith( "/" ) ||
            relativePath.contains( "\0" ) )
        {
            throw new IOException( "Path traversal attempt detected: " + relativePath );
        }

        File userFile = new File( relativePath );

        if( userFile.isAbsolute() )
            throw new IOException( "Absolute paths are not allowed." );

        File finalPath     = new File( fRoot, relativePath ).getCanonicalFile();
        File rootCanonical = fRoot.getCanonicalFile();

        // More robust check using String comparison after canonicalization
        if( ! finalPath.getPath().startsWith( rootCanonical.getPath() ) )
            throw new IOException( "Path traversal attempt detected: " + relativePath );

        return finalPath;
    }

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
            if( ! currentPathObj.startsWith( rootPathObj ) )
                return null;

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
            JsonArray jaChildren = new JsonArray();
            File[]    aFiles     = currentDir.listFiles();

            if( aFiles != null )
            {
                // 6. Sort files: Directories first, then alphabetical
                Arrays.sort( aFiles, Comparator.comparing( File::isDirectory )
                       .reversed()
                       .thenComparing( File::getName, String.CASE_INSENSITIVE_ORDER ) );

                for( File file : aFiles )
                {
                    if( file.isHidden() || ! file.canRead() )
                        continue;

                    // Recurse
                    JsonObject childNode = buildFileTree( file, rootPathObj );

                    if( childNode != null )
                        jaChildren.add( childNode );
                }
            }

            node.add( "nodes", jaChildren );
            return node;
        }
        catch( IOException e )
        {
            return null;   // If we can't read the real path of a specific node, skip it
        }
    }
}