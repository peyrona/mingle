
package com.peyrona.mingle.lang.japi;

import com.peyrona.mingle.lang.MingleException;
import com.peyrona.mingle.lang.lexer.Language;
import java.io.BufferedInputStream;
import java.io.BufferedWriter;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileFilter;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Reader;
import java.io.StringReader;
import java.io.Writer;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URLConnection;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.FileSystems;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Function;

/**
 * This class is a set of utility methods related with Input/Output.
 *
 * @author Francisco José Morero Peyrona
 *
 * Official web site at: <a href="https://github.com/peyrona/mingle">https://github.com/peyrona/mingle</a>
 */
public final class UtilIO
{
    //------------------------------------------------------------------------//
    // KNOWN FILE TYPES
    //------------------------------------------------------------------------//

    /** These are the file extensions this implementation recognizes. */
    private static final short UneSource      =  0;
    private static final short UneCompiled    =  1;
    private static final short JSON           =  2;
    private static final short JavaSource     =  3;
    private static final short JavaClass      =  4;
    private static final short JavaArchive    =  5;
    private static final short JavaScript     =  6;
    private static final short PythonSource   =  7;
    private static final short PythonCompiled =  8;
    private static final short NativeLib      =  9;
    private static final short Unknown        = 10;

    /**
     * Checks if the file type of the given URI is recognized by this implementation.
     *
     * @param uri The URI to check.
     * @return true if the file type is known; false otherwise.
     */
    public static boolean isKnownFileType( URI uri )
    {
        return ! isUnknownFileType( uri );
    }

    /**
     * Checks if the file type of the given URI is not recognized by this implementation.
     *
     * @param uri The URI to check.
     * @return true if the file type is unknown; false otherwise.
     */
    public static boolean isUnknownFileType( URI uri )
    {
        return getKnownFileType( uri ) == Unknown;
    }

    /**
     * Returns the file type constant for the given URI based on its file extension.
     * <p>
     * Supported file types:
     * <ul>
     * <li>.une - Une source files
     * <li>.model - Une compiled files
     * <li>.json - JSON files
     * <li>.java - Java source files
     * <li>.class - Java class files
     * <li>.jar - Java archive files
     * <li>.js - JavaScript files
     * <li>.py - Python source files
     * <li>.pyc - Python compiled files
     * <li>.so - Native shared libraries (Unix)
     * <li>.dll - Native dynamic libraries (Windows)
     * </ul>
     *
     * @param uri The URI to check.
     * @return The file type constant, or Unknown if the extension is not recognized.
     */
    public static int getKnownFileType( URI uri )
    {
        String sExt = getExtension( uri ).toLowerCase();

        switch( sExt )
        {
            case "une"  : return UneSource;
            case "model": return UneCompiled;
            case "json" : return JSON;
            case "java" : return JavaSource;
            case "class": return JavaClass;
            case "jar"  : return JavaArchive;
            case "js"   : return JavaScript;
            case "py"   : return PythonSource;
            case "pyc"  : return PythonCompiled;
            case "so"   : return NativeLib;
            case "dll"  : return NativeLib;
        }

        return Unknown;
    }

    //------------------------------------------------------------------------//
    // URI RELATED METHODS
    //------------------------------------------------------------------------//

    /**
     * Check if the scheme is "file".
     *
     * @param uri URI to check.
     *
     * @return
     */
    public static boolean isLocalFile( URI uri )
    {
        return uri != null                                &&
               "file".equalsIgnoreCase( uri.getScheme() ) &&
               uri.getPath() != null;
    }

    /**
     * Reads the content of a file at the given URI as text.
     * <p>
     * The URI can contain wildcards but must resolve to exactly one file.
     * Uses UTF-8 encoding by default for model files, or the system default charset otherwise.
     *
     * @param uri The URI of the file to read. Can contain wildcards.
     * @return The file content as a string, or null if the URI is empty.
     * @throws IOException if no files are found, multiple files are found, or an I/O error occurs.
     */
    public static String getAsText( String uri ) throws IOException
    {
        if( UtilStr.isEmpty( uri ) )
            return null;

        try
        {
            List<URI> lstURIs = expandPath( uri );

            if( lstURIs.isEmpty() )
                throw new IOException( uri +": file(s) not found." );

            if( lstURIs.size() > 1 )
                throw new IOException( "One and only one file needed." );

            return getAsText( lstURIs.get( 0 ) );
        }
        catch( URISyntaxException use )
        {
            throw new IOException( use );
        }
    }

    /**
     * Reads the content of a file as text.
     * <p>
     * Uses UTF-8 encoding for model files, or the system default charset otherwise.
     *
     * @param file The file to read.
     * @return The file content as a string.
     * @throws IOException if an I/O error occurs.
     */
    public static String getAsText( File file ) throws IOException
    {
        return getAsText( file.toURI() );
    }

    /**
     * Reads the content of a URI as text using default charset.
     * <p>
     * Uses UTF-8 encoding for model files, or the system default charset otherwise.
     *
     * @param uri The URI to read from.
     * @return The content as a string.
     * @throws IOException if the file does not exist or an I/O error occurs.
     */
    public static String getAsText( URI uri ) throws IOException
    {
        return getAsText( uri, null );     // The default must be UTF-8 because it is impossible to assume which one
    }                                             // is used at remote-side and because the Mingle compiler always produces UTF-8.

    /**
     * Reads the content of a URI as text using the specified charset.
     * <p>
     * If the file type is a compiled Une model file, UTF-8 encoding is always used regardless
     * of the charset parameter, as model files must be UTF-8 encoded.
     *
     * @param uri The URI to read from.
     * @param charset The charset to use for decoding. If null, the system default charset is used.
     * @return The content as a string.
     * @throws IOException if the file does not exist or an I/O error occurs.
     */
    public static String getAsText( URI uri, Charset charset ) throws IOException
    {
        Objects.requireNonNull( uri );

        int nType = getKnownFileType( uri );

        if( nType ==  UneCompiled )
            charset = StandardCharsets.UTF_8;         // These model files must be in UTF-8

        try( InputStream is = uri.toURL().openStream() )
        {
            StringBuilder sb        = new StringBuilder( 128*1024 );
            char          readBuf[] = new char[16*1024];

            if( charset == null )
                charset = Charset.defaultCharset();

            try( Reader reader = new InputStreamReader( is, charset ) )
            {
                int count = reader.read( readBuf );

                while( count > 0 )
                {
                    sb.append( readBuf, 0, count );
                    count = reader.read( readBuf );
                }

                is.close();
            }

            return sb.toString();
        }
        catch( IOException use )
        {
            if( UtilStr.startsWith( uri.toString(), "file:" ) && (! new File( uri ).exists()) )
            {
                throw new IOException( "File does not exist: "+ uri );
            }

            throw new IOException( "Error loading from: "+ uri, use );
        }
    }

    /**
     * Reads the content of a URI as binary data.
     * <p>
     * The content length must be known (Content-Length header must be present).
     * The content type must not start with "text/".
     *
     * @param uri The URI to read from.
     * @return The binary content of the URI.
     * @throws IOException if content length is unknown, content is not binary, or an I/O error occurs.
     */
    public static byte[] getAsBinary( URI uri ) throws IOException
    {
        URLConnection conn  = uri.toURL().openConnection();
        String        sType = conn.getContentType().toLowerCase();
        int           nLen  = conn.getContentLength();

        if( nLen == -1 )
            throw new IOException( uri + ": Content length is unknown, cannot read into fixed buffer." );

        if( sType.startsWith( "text/" ) )
            throw new IOException( uri +": is not a binary file." );

        byte[] data   = new byte[nLen];
        int    offset = 0;

        try( InputStream in = new BufferedInputStream( conn.getInputStream() ) )
        {
            int nRead;

            while( offset < nLen )
            {
                nRead = in.read( data, offset, data.length - offset );

                if( nRead == -1 )
                    break;

                offset += nRead;
            }
        }

        if( offset != nLen )
            throw new IOException( "Only read " + offset + " bytes; Expected " + nLen + " bytes" );

        return data;
    }

    //------------------------------------------------------------------------//
    // FILE NAME AND PATH RELATED METHODS
    //------------------------------------------------------------------------//

    /**
     * Replaces file path macros in the given path with their actual values.
     * <p>
     * Supported macros:
     * <ul>
     * <li>{*home*} - Home directory of the application
     * <li>{*home.inc*} - Include directory
     * <li>{*home.lib*} - Library directory
     * <li>{*home.log*} - Log directory
     * <li>{*home.tmp*} - Temporary directory
     * </ul>
     *
     * @param sPath The path containing macros to replace.
     * @return The path with macros replaced by their actual directory paths.
     * @throws URISyntaxException if the path has invalid syntax.
     * @throws MingleException if macros are used in a non-file protocol path.
     */
    public static String replaceFileMacros( String sPath ) throws URISyntaxException
    {
        if( UtilStr.isEmpty( sPath ) )
            return sPath;

        sPath = sPath.trim();

        if( ! Language.hasMacro( sPath ) )
            return sPath;

        UtilComm.Protocol protocol = UtilComm.getFileProtocol( sPath );
                          protocol = (protocol == null) ? UtilComm.Protocol.file : protocol;

        if( protocol != UtilComm.Protocol.file )
            throw new MingleException( "Macros are only allowed on local files" );

        String sMacro;    // Note: it would be rare, but sPath could have more than one macro (even more than once the same macro is allowed; v.g.: {*home*})

        sMacro = Language.buildMacro( "home" );    // returns "{*home*}"

        if( sPath.contains( sMacro ) )
            sPath = replaceFileMacro( sPath, sMacro, UtilSys.fHomeDir.getAbsolutePath() );

        sMacro = Language.buildMacro( "home.inc" );

        if( sPath.contains( sMacro ) )
            sPath = replaceFileMacro( sPath, sMacro, UtilSys.getIncDir().getAbsolutePath() );

        sMacro = Language.buildMacro( "home.lib" );

        if( sPath.contains( sMacro ) )
            sPath = replaceFileMacro( sPath, sMacro, UtilSys.getLibDir().getAbsolutePath() );

        sMacro = Language.buildMacro( "home.log" );

        if( sPath.contains( sMacro ) )
            sPath = replaceFileMacro( sPath, sMacro, UtilSys.getLogDir().getAbsolutePath() );

        sMacro = Language.buildMacro( "home.tmp" );

        if( sPath.contains( sMacro ) )
            sPath = replaceFileMacro( sPath, sMacro, UtilSys.getTmpDir().getAbsolutePath() );

        return sPath;
    }

    /**
     * Splits a path string into its folder components.
     *
     * @param path The path to split.
     * @return A list of folder names in the path.
     */
    public static List<String> splitByFolder( String path )
    {
        return new ArrayList<>( Arrays.asList( path.split( java.util.regex.Pattern.quote( File.separator ) ) ) );
    }

    /**
     * Splits a file path into its folder components.
     * <p>
     * If the path points to a file, uses the parent directory instead.
     *
     * @param path The file or directory to split.
     * @return A list of folder names in the path, or empty list if path is null.
     */
    public static List<String> splitByFolder( File path )
    {
        if( path.isFile() )
            path = path.getParentFile();

        if( path == null )
            return new ArrayList<>();

        return new ArrayList<>( Arrays.asList( path.getAbsolutePath().split( java.util.regex.Pattern.quote( File.separator ) ) ) );
    }

    /**
     * Returns all except file name (with its extension if any). If file has no parent folder(s),
     * empty string ("") is returned.
     *
     * @param file file File to get its path.
     * @return All except file name (with its extension if any). If file has no parent folder(s), empty string ("") is returned.
     */
    public static String getPath( final File file )
    {
        String path  = file.getPath();
        int    index = path.lastIndexOf( File.separator );

        if( index == -1 )
            return "";    // Single file without parent directorie(s)

        return path.substring( 0, index );
    }

    /**
     * Expands passed path returning a List of URIs.
     * <p>
     * MACROS FOR FILE PATHs:
     * <ul>
     *    <li>{*home*} - Where the MSP is installed</li>
     *    <li>{*home.lib*} - Where the additional MSP libraries are</li>
     *    <li>{*home.log*} - Where log files are</li>
     *    <li>{*home.tmp*} - Used for temporary files: its contents can be safely deleted</li>
     * </ul>
     * <p>
     * Example:
     * <code>
     *    INCLUDE "file://{*home*}include/standard-includes.une"
     *    INCLUDE "file://{*home.lib*}gum/*"
     *    INCLUDE "file://{*home.lib*}controllers/**"
     * </code>
     * <p>
     * <p>
     * WILDCARDS:
     * <ul>
     *    <li>"*" - All files in the specified folder only (non-recursive)</li>
     *    <li>"**" - All files in the specified folder and in all sub-folders (recursive)</li>
     * </ul>
     * <p>
     * Examples:
     * <code>
     *    {*home.lib*}my_libs/*      // Files only in my_libs folder (non-recursive)
     *    {*home.tmp*}my_temps/**    // Files in my_temps folder and all sub-folders (recursive)
     *    *.java                     // All .java files in current directory only
     *    **<kbd>/<kbd>*.txt         // All .txt files in current directory and sub-directories
     * </code>
     *
     * @param asPaths[n]
     * @return A List of URIs.
     * @throws java.net.URISyntaxException
     * @throws java.io.IOException
     */
    public static List<URI> expandPath( String... asPaths ) throws URISyntaxException, IOException
    {
        List<URI> lstURIs = new ArrayList<>();

        if( UtilColls.isEmpty( asPaths ) )
            return lstURIs;

        for( int n = 0; n < asPaths.length; n++ )
        {
            UtilComm.Protocol protocol = UtilComm.getFileProtocol( asPaths[n] );
                              protocol = (protocol == null) ? UtilComm.Protocol.file : protocol;

            if( (protocol != UtilComm.Protocol.file) &&
                asPaths[n].contains( "."+ File.separatorChar ) )     // e.g.: "./" or "../"
            {
                throw new MingleException( "Relative paths are not allowed for remote files:\n"+ asPaths[n] );
            }

            switch( protocol )
            {
                case http:
                case https:
                    if( Language.hasMacro( asPaths[n] ) )      // This 'if' must be prior to the next one
                        throw new MingleException( "Macros not allowed with remote files: "+ asPaths[n] );

                    if( asPaths[n].contains( "*" ) )
                        throw new MingleException( "Wildcards not allowed with remote files: "+ asPaths[n] );

                    lstURIs.add( new URI( asPaths[n] ) );

                    break;

                case file:
                    asPaths[n] = replaceFileMacros( asPaths[n] );
                    lstURIs.addAll( listFiles( asPaths[n] ) );

                    break;

                default:
                    throw new IllegalStateException();
            }
        }

        return lstURIs;
    }

    /**
     * Returns the file name without its extension and without the extension separator ('.').
     *
     * @param file file File to get its name.
     * @return the file name without its extension and without the extension separator ('.').
     */
    public static String getName( final File file )
    {
        final String name  = file.getName();
        final int    index = name.lastIndexOf( '.' );

        return ((index == -1) ? name : name.substring( 0, index ));
    }

    /**
     * Extracts the base name (without extension) from a file path.
     *
     * @param path The file path.
     * @return The file name or "" if path is empty.
     */
    public static String getName( String path )
    {
        if( UtilStr.isEmpty( path ) )
            return "";

        int lastSlash = Math.max( path.lastIndexOf( '/' ), path.lastIndexOf( '\\' ) );
        int lastDot   = path.lastIndexOf( '.' );

        String fileName = (lastSlash >= 0) ? path.substring( lastSlash + 1 ) : path;

        if( lastDot > lastSlash )
            return fileName.substring( 0, fileName.lastIndexOf( '.' ) );

        return fileName;
    }

    /**
     * Returns the file extension without (not including) the '.' or "" if file
     * has no extension.
     *
     * @param file
     * @return
     */
    public static String getExtension( final File file )
    {
        if( file == null )
            return "";

        return getExtension( file.toURI() );
    }

    /**
     * Returns the file extension without (not including) the '.' or "" if file
     * has no extension.
     *
     * @param uri
     * @return The file extension without (not including) the '.' or "".
     */
    public static String getExtension( final URI uri )
    {
        if( uri == null )
            return "";

        return getExtension( uri.toString() );
    }

    /**
     * Returns the file extension from a path string.
     * <p>
     * The extension is returned without the '.' character.
     * Returns empty string if the path has no extension or ends with '.'.
     *
     * @param s The file path string.
     * @return The file extension without '.', or empty string if no extension exists.
     */
    public static String getExtension( String s )
    {
        if( s == null )
            return "";

        int index = s.lastIndexOf( File.separatorChar );

        if( index > -1 )
            s = s.substring( index+1 );

        index = s.lastIndexOf( '.' );

        return (((index == -1) || UtilStr.isLastChar( s, '.' )) ? "" : s.substring( index+1 ));
    }

    /**
     * Returns true if received file has one of received extensions.
     * <ul>
     *     <li>Case is ignored</li>
     *     <li>It should not include '.'</li>
     * </ul>
     *
     * @param name
     * @param as
     * @return true if received name has one of received extensions (case is ignored).
     */
    public static boolean hasExtension( final String name, String... as )
    {
        return UtilColls.contains( as, getExtension( name ) );
    }

    /**
     * Returns true if received file has one of received extensions.
     * <ul>
     *     <li>Case is ignored</li>
     *     <li>It should not include '.'</li>
     * </ul>
     *
     * @param file
     * @param as
     * @return true if received file has one of received extensions (case is ignored).
     */
    public static boolean hasExtension( final File file, String... as )
    {
        return UtilColls.contains( as, getExtension( file ) );
    }

    /**
     * Returns true if received URI has one of received extensions.
     * <ul>
     *     <li>Case is ignored</li>
     *     <li>You should not include '.'</li>
     * </ul>
     *
     * @param uri
     * @param as
     * @return true if received file has one of received extensions (case is ignored).
     */
    public static boolean hasExtension( final URI uri, String... as )
    {
        return UtilColls.contains( as, getExtension( uri ) );
    }

    /**
     * Adds passed extension to passed file name: if file already has this extension this method
     * does nothing. If '.' is part of the extension, it has to be in the extension.
     *
     * @param file
     * @param newExtension
     * @return
     */
    public static File addExtension( File file, String newExtension )
    {
        return new File( addExtension( file.getAbsolutePath(), newExtension ) );
    }

    /**
     * Adds passed extension to passed name: if name already has this extension this method
     * does nothing. If '.' is part of the extension, it has to be in the extension.
     *
     * @param name
     * @param extension
     * @return
     */
    public static String addExtension( String name, String extension )
    {
        if( UtilStr.isEmpty( extension ) )
            return name;

        if( extension.trim().charAt( 0 ) != '.' )
            extension = '.'+ extension;

        if( UtilStr.isEmpty( name ) )
            return extension;

        if( name.toLowerCase().endsWith( extension.toLowerCase() ) )
            return name;

        return name + extension;
    }

    /**
     * Returns an Universal Unique File Name.
     * @return An Universal Unique File Name.
     */
    public static String UUFileName()
    {
        return UUID.randomUUID().toString();    // All chars are valid
    }

    //------------------------------------------------------------------------//
    // LISTING AND READING RELATED METHODS (besides getAsText and getAsBinary)
    //------------------------------------------------------------------------//

    /**
     * Lists all files and directories in the specified folder.
     *
     * @param fFolder The folder to list contents from.
     * @return A list of files and directories, or empty list if folder is null or doesn't exist.
     */
    public static List<File> listFiles( File fFolder )
    {
        return listFiles( fFolder, null );
    }

    /**
     * Lists files and directories in the specified folder, optionally filtered.
     *
     * @param folder The folder to list contents from.
     * @param fn A filter function that returns true to include a file, or null to include all.
     * @return A list of files and directories matching the filter, or empty list if folder is null or doesn't exist.
     */
    public static List<File> listFiles( final File folder, final Function<File,Boolean> fn )
    {
        if( (folder == null) || (! folder.exists()) )
            return new ArrayList<>();

        if( fn == null )
            return Arrays.asList( folder.listFiles() );

        return Arrays.asList( folder.listFiles( (File file) -> fn.apply( file ) ) );
    }

    /**
     * Recursively lists files in a directory tree, optionally filtered.
     * <p>
     * Traverses all subdirectories and returns files matching the filter.
     * Directories are not included in the result.
     *
     * @param dir The root directory to start traversal from.
     * @param fn A filter function that returns true to include a file, or null to include all files.
     * @return A list of files matching the filter, or empty list if directory is null or doesn't exist.
     */
    public static List<File> listFilesInTree( final File dir, final Function<File, Boolean> fn )
    {
        List<File> list = new ArrayList<>();

        if( dir == null || ! dir.exists() )
            return list;

        File[] files = dir.listFiles();    // List all files and directories in the folder

        if( files == null )
            return list;

        for( File file : files )
        {
                 if( file.isDirectory() )              list.addAll( listFiles( file, fn ) );   // Recursively list files in the subdirectory
            else if( fn == null || fn.apply( file ) )  list.add( file );                       // Add the file if it matches the condition or if no filter is provided
        }

        return list;
    }

    /**
     * Searches recursively the file which name was passed as argument in passed folder.
     *
     * @param fBaseDir Initial folder to search.
     * @param sFileName What to search.
     * @param isCaseSensitive true or false
     * @return The file if found or null.
     */
    public static File find( File fBaseDir, String sFileName, boolean isCaseSensitive )
    {
        if( (fBaseDir == null)         ||
            (! fBaseDir.isDirectory()) ||
            (! fBaseDir.canRead()) )
        {
            return null;
        }

        if( UtilStr.isEmpty( sFileName) )
        {
            return null;
        }

        for( File f : fBaseDir.listFiles() )
        {
            if( f.canRead() )
            {
                if( f.isDirectory() )
                {
                    File found = find( f, sFileName, isCaseSensitive );

                    if( found != null )
                    {
                        return found;
                    }
                }
                else
                {
                    String sName = f.getName();

                    if( (isCaseSensitive && sName.equals( sFileName ) )
                        ||
                        (sName.equalsIgnoreCase( sFileName )) )
                    {
                        return f;
                    }
                }
            }
        }

        return null;
    }

    /**
     * Creates a Reader from one or more strings.
     *
     * @param str One or more strings to create a reader from.
     * @return A StringReader containing the concatenated strings.
     */
    public static Reader fromStringToReader( String... str )
    {
        if( str.length == 1 )    // To save CPU
        {
            return new StringReader( str[0] );
        }

        StringBuilder sb = new StringBuilder( 1024 * str.length );

        for( String s : str )
        {
            sb.append( s );
        }

        return new StringReader( sb.toString() );
    }

    /**
     * Creates an InputStream from one or more strings.
     * <p>
     * Strings are encoded to bytes using UTF-8 charset.
     *
     * @param str One or more strings to create an input stream from.
     * @return A ByteArrayInputStream containing the UTF-8 encoded bytes of concatenated strings.
     */
    public static InputStream fromStringsToInputStream( String...  str )
    {
        if( str.length == 1 )    // To save CPU
        {
            return new ByteArrayInputStream( str[0].getBytes( StandardCharsets.UTF_8 ) );
        }

        StringBuilder sb = new StringBuilder( 1024 * str.length );

        for( String s : str )
        {
            sb.append( s );
        }

        return new ByteArrayInputStream( sb.toString().getBytes( StandardCharsets.UTF_8 ) );
    }

    /**
     *
     * @param is
     * @param encoding Charset to be used, If null, then default (OS) will be taken.
     * @return The stream contents until the end of it.
     * @throws IOException
     */
    public static String getAsText( InputStream is, Charset encoding ) throws IOException
    {
        StringBuilder sb        = new StringBuilder( 128*1024 );
        char          readBuf[] = new char[16*1024];

        if( encoding == null )
            encoding = Charset.defaultCharset();

        try( Reader reader = new InputStreamReader( is, encoding ); is )
        {
            int count = reader.read( readBuf );

            while( count > 0 )
            {
                sb.append( readBuf, 0, count );
                count = reader.read( readBuf );
            }

            return sb.toString();
        }
    }

    //------------------------------------------------------------------------//
    // WRITTING RELATED METHODS
    // Meanwhile read can be done from local or meote files, write is only local
    //------------------------------------------------------------------------//

    /**
     * Creates a new FileWriter instance for writing files.
     * <p>
     * The FileWriter provides a fluent API for configuring and writing files.
     * Use methods like setFile(), setCharset(), and then append() or replace().
     *
     * @return A new FileWriter instance.
     */
    public static FileWriter newFileWriter()
    {
        return new FileWriter();
    }

    /**
     * Copy all contents from InputStream to the OutputStream using the Java 8 way.<br>
     * This method always attempt to close both streams.
     *
     * @param isFrom
     * @param osTo
     * @throws java.io.IOException
     */
    public static void copy( InputStream isFrom, OutputStream osTo ) throws IOException
    {
        try
        {
            int    length;
            byte[] bytes = new byte[ 1024 * 16 ];

            while( (length = isFrom.read( bytes ) ) != -1 )
                osTo.write( bytes, 0, length );

            osTo.flush();
        }
        finally
        {
            try{ isFrom.close(); } catch( IOException e ) { /* Nothing to do */ }
            try{ osTo.close();   } catch( IOException e ) { /* Nothing to do */ }
        }
    }

    /**
     * Copy (or rename) files and or folders.
     * <ul>
     *  <li>If both are equals, nothing is done.</li>
     *  <li>If both are files, fFrom is renamed as fTo.</li>
     *  <li>If both are folders, fFrom tree <u>contents</u> are copied into fTo (fTo is created if needed).</li>
     *  <li>If fFrom is a file and fTo is a folder, fFrom is copied into fTo (fTo is created if needed).</li>
     *  <li>If fFrom is a folder and fTo is not a folder, an IOException is thrown.</li>
     *  <li>If fTo does not exist and can not be created, an IOException is thrown.</li>
     * </ul>
     *
     * @param fFrom
     * @param fTo
     * @throws IOException
     */
    public static void copy( File fFrom, File fTo ) throws IOException
    {
        if( fFrom.equals( fTo ) )    // Are same file or folder
            return;

        if( fFrom.isDirectory() && (! fTo.isDirectory()) )
            throw new IOException( fFrom +" is a folder but "+ fTo +" is not" );

        if( fFrom.isDirectory() && fTo.getCanonicalPath().startsWith( fFrom.getCanonicalPath() ) )
            throw new IOException( "Cannot copy parent directory into a child directory: " + fFrom + " -> " + fTo );   // When copying a directory into its own subdirectory

        if( fFrom.isFile() && fTo.isFile() )
        {
            fFrom.renameTo( fTo );
            return;
        }

        if( ! mkdirs( fTo ) )
            throw new IOException( "Error creating "+ fTo +" folder" );

        if( fFrom.isFile() && fTo.isDirectory() )
        {
            Files.copy( fFrom.toPath(), fTo.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.COPY_ATTRIBUTES );
            return;
        }

        // Both (fFrom and fTo) are folders: Walk through the source directory recursively

        Path sourceDir = fFrom.toPath();
        Path targetDir = fTo.toPath();

        Files.walkFileTree( sourceDir, new SimpleFileVisitor<Path>()
        {
            @Override
            public FileVisitResult visitFile( Path file, BasicFileAttributes attrs ) throws IOException
            {
                Path targetFile = targetDir.resolve( sourceDir.relativize( file ) );
                Files.copy( file, targetFile, StandardCopyOption.REPLACE_EXISTING );
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult preVisitDirectory( Path dir, BasicFileAttributes attrs ) throws IOException
            {
                Path targetDirToCreate = targetDir.resolve( sourceDir.relativize( dir ) );
                Files.createDirectories( targetDirToCreate );
                return FileVisitResult.CONTINUE;
            }
        } );
    }

    /**
     * Move files or folders to a new destination.
     * This method works as ::copy(...), but deletes original file or folder.
     * @param fFrom
     * @param fTo
     * @throws IOException
     * @see #copy(java.io.File, java.io.File)
     */
    public static void move( File fFrom, File fTo ) throws IOException
    {
        copy( fFrom, fTo );
        delete( fFrom );
    }

    /**
     * Delete file or folder (recursively).<br>
     * This method does not follow symbolic links.
     *
     * @param file
     * @throws IOException
     */
    public static void delete( File file ) throws IOException
    {
        if( ! file.exists() )
            return;

        if( file.isFile() )
        {
            Files.delete( file.toPath() );
            return;
        }

        if( ! file.isDirectory() )
            throw new IOException( file +" is not a file neither a folder: can not delete" );

        Files.walkFileTree( file.toPath(),
                            new SimpleFileVisitor<Path>()
                            {
                                @Override
                                public FileVisitResult visitFile( Path pFile, BasicFileAttributes attrs ) throws IOException
                                {
                                    Files.delete( pFile );
                                    return FileVisitResult.CONTINUE;
                                }

                                @Override
                                public FileVisitResult postVisitDirectory( Path pDir, IOException exc ) throws IOException
                                {
                                    Files.delete( pDir );
                                    return FileVisitResult.CONTINUE;
                                }
                            } );

        if( file.exists() )
            Files.delete( file.toPath() );    // Removes the folder itself
    }

    /**
     * Deletes files within a folder that match the specified filter.
     * <p>
     * When {@code bSubFolders} is {@code true}, the method recursively enters all
     * subdirectories (regardless of the filter) and deletes matching files within them.
     * Subdirectories themselves are not deleted.
     * </p>
     *
     * @param folder      The folder to process. Must exist and be a directory.
     * @param filter      A filter to select which files to delete. If {@code null}, all files are deleted.
     * @param bSubFolders If {@code true}, recursively process subdirectories.
     * @throws IOException If folder is null, does not exist, is not a directory,
     *                     cannot be listed, or if any file deletion fails.
     */
    public static void delete( File folder, FileFilter filter, boolean bSubFolders ) throws IOException
    {
        if( folder == null )
            throw new IOException( "Folder cannot be null" );

        if( ! folder.exists() )
            throw new IOException( folder + ": does not exist" );

        if( ! folder.isDirectory() )
            throw new IOException( folder + ": is not a folder" );

        File[] files = folder.listFiles();

        if( files == null )
            throw new IOException( folder + ": unable to list files (I/O error or access denied)" );

        for( File f : files )
        {
            if( f.isDirectory() )
            {
                if( bSubFolders )
                    delete( f, filter, true );
            }
            else if( filter == null || filter.accept( f ) )
            {
                Files.delete( f.toPath() );
            }
        }
    }

    /**
     * Creates the folder identified by passed File (and all parent folders if
     * needed) only if it did not existed.
     *
     * @param folder
     * @return true if file exists and it is a folder or if folder was successfully created.
     */
    public static boolean mkdirs( File folder )
    {
        if( folder == null )
            return false;

        return (folder.exists() ? folder.isDirectory() : folder.mkdirs());
    }

    /**
     * Checks if file can be read.
     *
     * @param f File to check.
     * @return An error or null if file can be read
     */
    public static String canRead( File f )
    {
        return canRead( f, false );
    }

    /**
     * Checks if file or dir can be read.
     *
     * @param f File to check.
     * @param bDir true to treat f as a folder (dir), false for a regular file.
     * @return null if file can be read or an string explaining why the file can not be read.
     */
    public static String canRead( File f, boolean bDir )
    {
        if( f == null )
            return "ERROR: " + (bDir ? "directory" : "file") + " cannot be null";

        if( ! f.exists() )
            return "ERROR: " + (bDir ? "directory" : "file") + " does not exist:\n\t" + f.getAbsolutePath();

        if( bDir )   // Directory-specific checks
        {
            if( ! f.isDirectory() )
                return "ERROR: path is not a directory:\n\t" + f.getAbsolutePath();
        }
        else         // File-specific checks
        {
            if( f.isDirectory() )
                return "ERROR: path is a directory, not a file:\n\t" + f.getAbsolutePath();

            if( ! f.isFile() )
                return "ERROR: path is not a regular file:\n\t" + f.getAbsolutePath();
        }

        if( ! f.canRead() )
            return "ERROR: " + (bDir ? "directory" : "file") + " cannot be read:\n\t" + f.getAbsolutePath();

        return null;
    }

    //------------------------------------------------------------------------//
    // PRIVATE SCOPE

    private UtilIO()
    {
        // Avoids creation of this class instances
    }

    //------------------------------------------------------------------------//
    // PRIVATE INTERFACE

    /**
     * Locates and returns a list of file {@link URI}s matching the specified path,
     * which can include local filesystem glob-syntax ({@code *} and {@code **}).
     *
     * <p>This method handles three types of paths:</p>
     * <ol>
     * <li><b>Simple Path:</b> A direct path or URL with no glob characters.</li>
     * <li><b>Flat Glob ({@code *}) :</b> A path containing wildcards that only match files within the immediate
     * parent directory (non-recursive).</li>
     * <li><b>Recursive Glob ({@code **}):</b> A path containing the recursive wildcard, matching files in the parent
     * directory and all subdirectories.</li>
     * </ol>
     *
     * <p>The method is designed to resolve local filesystem paths even if they are
     * prefixed with {@code file://}.</p>
     * * @param sPath The file path or URI to resolve. This may include the {@code file://} scheme,
     * and local paths can contain glob patterns ({@code *} for single-level matching,
     * {@code **} for multi-level/recursive matching). Macros are not supported and
     * must be resolved before calling this method.
     * @return A list of {@link URI} objects corresponding to the files found. Returns an empty
     * list if the path is invalid or no files match the glob pattern.
     * @throws IOException If an I/O error occurs while accessing the file system (e.g., during directory traversal).
     */
    private static List<URI> listFiles( String sPath ) throws IOException
    {
        assert ! Language.hasMacro( sPath );

        List<URI> lst = new ArrayList<>();

        if( ! UtilStr.isGlobalSyntax( sPath ) )         // No glob-syntax chars were found
        {
            lst.add( toURI( sPath ) );
        }
        else                                            // Having glob-syntax can only be a local file(s)
        {
            if( UtilStr.startsWith( sPath, "file://" ) )
                sPath = sPath.substring( 7 );

            String sParent = getFoldersUntilGlob( sPath );

            // Calculate the glob pattern (remove the parent path and the separator)
            String sGlob = sPath.substring( sParent.length() );

            if( sGlob.startsWith( File.separator ) || sGlob.startsWith( "/" ) || sGlob.startsWith( "\\" ) )
                sGlob = sGlob.substring( 1 );

            File fParent = new File( sParent );

            if( !fParent.exists() )
                return lst;

            final PathMatcher pathMatcher = FileSystems.getDefault().getPathMatcher( "glob:" + sGlob );

            // Crucial: Only use walkFileTree for deep recursion ("**")
            // Simple wildcards like "12.*.une" should be handled by DirectoryStream for finding files in place.
            boolean isRecursive = sGlob.contains( "**" );

            if( isRecursive )
            {
                Files.walkFileTree( fParent.toPath(), new SimpleFileVisitor<Path>()
                {
                    @Override
                    public FileVisitResult visitFile( Path file, BasicFileAttributes attrs )
                    {
                        // Match the path relative to the start directory
                        Path relativePath = fParent.toPath().relativize( file );

                        if( pathMatcher.matches( relativePath ) )
                            lst.add( file.toUri() );

                        return FileVisitResult.CONTINUE;
                    }
                });
            }
            else
            {
                // Use newDirectoryStream for flat "*" patterns.
                // This correctly handles specific file patterns like "12.*.une" in the immediate directory.
                try( DirectoryStream<Path> directoryStream = Files.newDirectoryStream( fParent.toPath() ) )
                {
                    for( Path path : directoryStream )
                    {
                        // For flat scans, we usually match against the file name
                        if( pathMatcher.matches( path.getFileName() ) )
                        {
                            URI uri = path.toUri();

                            if( ! lst.contains( uri ) )
                                lst.add( uri );
                        }
                    }
                }
            }
        }

        return lst;
    }

    /**
     * Replaces a single file macro with its actual path value.
     * <p>
     * Ensures proper path separator between macro replacement and path suffix.
     * Macros are case-sensitive. Multiple occurrences of the same macro are replaced.
     *
     * @param sPath The path containing the macro.
     * @param sMacro The macro string (e.g., "{*home*}") to replace.
     * @param sNew The replacement path.
     * @return The path with macro(s) replaced.
     */
    private static String replaceFileMacro( String sPath, String sMacro, String sNew )
    {
        if( sPath.equals( sMacro ) )    // Do not use equalsIgnoreCase because macros are case sensistive
            return sNew;

        char cAfterMacro = sPath.charAt( sMacro.length() );

        if( (cAfterMacro != File.separatorChar) && (! UtilStr.isLastChar( sNew, File.separatorChar )) )
        {
            sNew += File.separatorChar;
        }

        while( sPath.contains( sMacro ) )
            sPath = UtilStr.replaceFirst( sPath, sMacro, sNew );    // My UtilStr.replaceFirst(...) replaces ignoring the case

        return sPath;
    }

    /**
     * Extracts the parent directory path up to the first glob pattern.
     * <p>
     * Stops traversal when a directory component contains glob syntax characters.
     *
     * @param sPath The path to extract parent directory from.
     * @return The parent directory path before the first glob pattern.
     */
    private static String getFoldersUntilGlob( String sPath )
    {
        List<String> lstDirs = UtilIO.splitByFolder( sPath );

        sPath = "";

        for( String sDir : lstDirs )
        {
            if( UtilStr.isGlobalSyntax( sDir ) )
                break;

            sPath += sDir + File.separator;
        }

        return sPath;
    }

    /**
     * In received string, replaces reserved chars by '_'
     *
     * @param str String to be transformed.
     * @return Transformed string.
     */
    private static String normalizeFileName( String str )
    {
        str = Normalizer.normalize( str, Normalizer.Form.NFC )  // Remove diacritics (accents) and normalize to NFC form
                        .replaceAll("[^\\w.-]", "_")            // Replace characters not allowed in file names with an underscore
                        .trim();                                // Remove leading and trailing whitespaces

        int maxLength = 255;    // Maximum file name length for most file systems

        if( str.length() > maxLength )
            str = str.substring( 0, maxLength );

        return str;
    }

    private static URI toURI( String str )
    {
        return (UtilComm.getFileProtocol( str ) == null) ? new File( str ).toURI()    // When no protocol is declared, "file://" is assumed
                                                         : URI.create( str );
    }

    //------------------------------------------------------------------------//
    // INNER CLASS: FileWriter
    // Thread safe when a new instance is created by user at run-time
    //------------------------------------------------------------------------//

    public static final class FileWriter
    {
        private File    file        = null;
        private boolean isTemporal  = false;
        private String  extension   = null;
        private Charset charset     = Charset.defaultCharset();
        private boolean isGenerated = false;

        private FileWriter()    // Only accesible from this class
        {
        }

        //------------------------------------------------------------------------//

        public FileWriter setFile( File f )
        {
            if( ! f.getParentFile().exists() )
                mkdirs( f.getParentFile() );

            file = new File( f.getParentFile(), normalizeFileName( f.getName() ) );

            return this;
        }

        public FileWriter setFile( String nameAndPath )
        {
            return setFile( new File( nameAndPath ) );
        }

        public FileWriter setCharset( Charset set )
        {
            if( set != null )
                charset = set;

            return this;
        }

        public FileWriter setTemporal( String ext )
        {
            extension  = ext;
            isTemporal = true;
            return this;
        }

        public File replace( String str ) throws IOException
        {
         // check(); --> Do not do this

            if( (file != null) && file.exists() )
                Files.delete( file.toPath() );

            return append( str );
        }

        public File replace( byte[] data ) throws IOException
        {
         // check(); <-- Do not do this

            if( (file != null) && file.exists() )
                Files.delete( file.toPath() );

            return append( data );
        }

        public File append( String str ) throws IOException
        {
            check();

            try( Writer writer = new BufferedWriter( new OutputStreamWriter( new FileOutputStream( file, true ), charset ) ) )
            {
                writer.append( str );
                writer.flush();
            }

            return file;
        }

        public File append( byte[] data ) throws IOException
        {
            check();

            try( FileOutputStream fos = new FileOutputStream( file, true ))
            {
                fos.write( data );
                fos.flush();
            }

            return file;
        }

        //------------------------------------------------------------------------//

        private void check() throws IOException   // Has to distinguish between a user-supplied file and the internally generated temp file.
        {
            if( isTemporal )
            {
                // [FIX] Only throw if the file exists AND it was not generated by us
                if( (file != null) && (! isGenerated) )
                    throw new IOException( "File is temporal but name was specified" );

                if( file == null )
                {
                    file        = newTempFile();
                    isGenerated = true;      // [FIX] Mark as internally generated
                }
            }
            else
            {
                if( file == null )
                    throw new IOException( "No target file, use: setFile(...)" );
            }
        }

        private File newTempFile()
        {
            File f = new File( UtilSys.getTmpDir(),
                               addExtension( UUFileName(), extension ) );

            f.deleteOnExit();

            return f;
        }
    }

//    //------------------------------------------------------------------------//
//    // INNER CLASS: FileMonitor
//    // Thread safe when a new instance is created by user at run-time
//    //------------------------------------------------------------------------//
//
//    public static final class FileMonitor
//    {
//        private final Path     filePath;
//        private       Consumer onCreated;
//        private       Consumer onModified;
//        private       Consumer onDeleted;
//
//        private FileMonitor( File file )
//        {
//            filePath = file.toPath();
//
//            Path   parentDir = filePath.getParent();
//            String fileName  = filePath.getFileName().toString();
//
//            UtilSys.execute( getClass().getSimpleName(),
//                             () ->
//                            {
//                                boolean bOK = true;
//
//                                try( WatchService watchService = FileSystems.getDefault().newWatchService() )
//                                {
//                                    parentDir.register( watchService,
//                                                        StandardWatchEventKinds.ENTRY_MODIFY,
//                                                        StandardWatchEventKinds.ENTRY_CREATE,
//                                                        StandardWatchEventKinds.ENTRY_DELETE );
//
//                                    while( bOK )
//                                    {
//                                        WatchKey key = watchService.take();    // Wait for an event
//
//                                        for( WatchEvent<?> event : key.pollEvents() )
//                                        {
//                                            WatchEvent.Kind<?> kind    = event.kind();
//                                            Path               evtFile = (Path) event.context();
//
//                                            if( evtFile.toString().equals( fileName ) )
//                                            {
//                                                     if( kind == StandardWatchEventKinds.ENTRY_MODIFY )  onModified.accept( filePath );
//                                                else if( kind == StandardWatchEventKinds.ENTRY_DELETE )  onDeleted.accept(  filePath );
//                                                else if( kind == StandardWatchEventKinds.ENTRY_CREATE )  onCreated.accept(  filePath );
//                                            }
//                                        }
//
//                                        boolean valid = key.reset();
//
//                                        if( ! valid )
//                                            break;    // WatchKey no longer valid, exiting...
//                                    }
//                                }
//                                catch( IOException | InterruptedException e )
//                                {
//                                    // Nothing to do
//                                }
//                            } );
//        }
//
//        public FileMonitor onCreated( Consumer action )
//        {
//            onCreated = action;
//            return this;
//        }
//
//        public FileMonitor onModified( Consumer action )
//        {
//            onModified = action;
//            return this;
//        }
//
//        public FileMonitor onDeleted( Consumer action )
//        {
//            onDeleted = action;
//            return this;
//        }
//    }
}
