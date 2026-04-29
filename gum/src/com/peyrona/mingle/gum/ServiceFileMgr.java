
package com.peyrona.mingle.gum;

import com.eclipsesource.json.Json;
import com.eclipsesource.json.JsonArray;
import com.eclipsesource.json.JsonObject;
import com.peyrona.mingle.lang.MingleException;
import com.peyrona.mingle.lang.japi.UtilIO;
import com.peyrona.mingle.lang.japi.UtilStr;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
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
    private static final HmacAuthenticator hmacAuthenticator = new HmacAuthenticator();
    private static final String            RESERVED_PREFIX   = "_";

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
        if( ! validateRequest() )
            return;

        String sFile  = asString( "file", null );

        if( sFile != null )
        {
            File   fTarget  = resolveSecurePath( sFile );
            String mimeType = detectMimeType( fTarget );

            if( isTextMimeType( mimeType ) )
                sendText( UtilIO.getAsText( fTarget ) );
            else
                sendBinary( Files.readAllBytes( fTarget.toPath() ), mimeType );
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
        if( ! validateRequest() )
            return;

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

        if( sName.startsWith( RESERVED_PREFIX ) )
            throw new IOException( "Names starting with '"+ RESERVED_PREFIX +"' are reserved" );

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
        if( ! validateRequest() )
            return;

        File fOld = resolveSecurePath( asString( "old", "" ) );
        File fNew = resolveSecurePath( asString( "new", "" ) );

        if( fNew.getName().startsWith( RESERVED_PREFIX ) )
            throw new IOException( "Names starting with '"+ RESERVED_PREFIX +"' are reserved" );

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
        if( ! validateRequest() )
            return;

        String[] asFileNames = asString( "paths", "" ).split( ";" );

        for( String sName : asFileNames )
            UtilIO.delete( resolveSecurePath( sName ) );

        sendText( "OK" );
    }

    //------------------------------------------------------------------------//
    // PRIVATE SCOPE

    /**
     * File extensions that are always served as UTF-8 text regardless of what
     * {@link Files#probeContentType} may return (or not return) for them.
     */
    private static final java.util.Set<String> TEXT_EXTENSIONS = java.util.Set.of(
        "bat", "cfg", "conf", "csv", "css", "h", "htm", "html", "ini",
        "java", "js", "json", "log", "md", "model", "ps1", "py", "r",
        "sh", "sql", "tex", "toml", "ts", "txt", "une", "xml", "yaml", "yml"
    );

    /**
     * Returns the MIME type for the given file.
     * Known text extensions always map to {@code text/plain}.
     * For everything else, {@link Files#probeContentType} is tried first;
     * unknown types fall back to {@code application/octet-stream}.
     *
     * @param file The file whose MIME type is to be determined.
     * @return A non-null MIME type string.
     */
    private String detectMimeType( File file )
    {
        String name = file.getName().toLowerCase();
        int    dot  = name.lastIndexOf( '.' );
        String ext  = (dot >= 0) ? name.substring( dot + 1 ) : "";

        if( TEXT_EXTENSIONS.contains( ext ) )
            return "text/plain";

        try
        {
            String mimeType = Files.probeContentType( file.toPath() );
            return (mimeType != null) ? mimeType : "application/octet-stream";
        }
        catch( IOException e )
        {
            return "application/octet-stream";
        }
    }

    /**
     * Returns {@code true} when the MIME type indicates a text-based format
     * that can be safely transmitted with UTF-8 character encoding.
     *
     * @param mimeType A non-null MIME type string.
     * @return {@code true} for text MIME types; {@code false} for binary ones.
     */
    private boolean isTextMimeType( String mimeType )
    {
        return mimeType.startsWith( "text/"                   ) ||
               mimeType.equals(    "application/json"         ) ||
               mimeType.equals(    "application/xml"          ) ||
               mimeType.equals(    "application/javascript"   );
    }

    private boolean validateRequest() throws IOException
    {
        String result = hmacAuthenticator.validateRequest( request );

        if( result != null )    // null == valid
            sendError( result, HttpServletResponse.SC_UNAUTHORIZED );

        return result == null;
    }

    private File resolveSecurePath( String path ) throws IOException
    {
        // Normalize and validate input first
        if( path == null || path.trim().isEmpty() )
            throw new IOException( "Path cannot be null or empty" );

        if( path.contains( "\0" ) )
            throw new IOException( "Path cannot contain null characters" );

        File   userFile = new File( path );
        String relativePath;

        if( userFile.isAbsolute() )
        {
            File canonicalPath = userFile.getCanonicalFile();
            File rootCanonical = fRoot.getCanonicalFile();

            if( ! canonicalPath.getPath().startsWith( rootCanonical.getPath() ) )
                throw new IOException( "Absolute path is outside allowed root: " + path );

            relativePath = rootCanonical.toPath().relativize( canonicalPath.toPath() ).toString();
        }
        else
        {
            if( path.contains( ".." )  ||
                path.contains( "//" )  ||
                path.contains( "\\" )  ||
                path.startsWith( "/" ) )
            {
                throw new IOException( "Path traversal attempt detected: " + path );
            }

            relativePath = path;
        }

        File finalPath     = new File( fRoot, relativePath ).getCanonicalFile();
        File rootCanonical = fRoot.getCanonicalFile();

        if( ! finalPath.getPath().startsWith( rootCanonical.getPath() ) )
            throw new IOException( "Path traversal attempt detected: " + path );

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