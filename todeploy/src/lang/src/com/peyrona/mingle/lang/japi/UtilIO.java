
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
import java.nio.file.StandardWatchEventKinds;
import java.nio.file.WatchEvent;
import java.nio.file.WatchKey;
import java.nio.file.WatchService;
import java.nio.file.attribute.BasicFileAttributes;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Function;

/**
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

    public static boolean isKnownFileType( URI uri )
    {
        return ! isUnknownFileType( uri );
    }

    public static boolean isUnknownFileType( URI uri )
    {
        return getKnownFileType( uri ) == Unknown;
    }

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

    public static String getAsText( File file ) throws IOException
    {
        return getAsText( file.toURI() );
    }

    public static String getAsText( URI uri ) throws IOException
    {
        return UtilIO.getAsText( uri, null );     // The default must be UTF-8 because it is impossible to assume which one
    }                                             // is used at remote-side and because the Mingle compiler always produces UTF-8.

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

            String text = sb.toString();

            if( ((nType == UneSource) || (nType == JSON)) && UtilStr.isMeaningless( text ) )
                throw new IOException( uri +": file is meaningless." );

            return text;
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

    public static byte[] getAsBinary( URI uri ) throws IOException
    {
        URLConnection conn  = uri.toURL().openConnection();
        String        sType = conn.getContentType().toLowerCase();
        int           nLen  = conn.getContentLength();

        if( nLen == -1 )
            throw new IOException( uri +": file not found." );

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

    public static List<String> splitByFolder( String path )
    {
        return new ArrayList<>( Arrays.asList( path.split( File.separator ) ) );    // Can not invoke ::splitByFolder( File )
    }

    public static List<String> splitByFolder( File path )
    {
        if( path.isFile() )
            path = path.getParentFile();

        if( path == null )
            return new ArrayList<>();

        return new ArrayList<>( Arrays.asList( path.getAbsolutePath().split( File.separator ) ) );
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
     *  <li>{*home*} - Where the MSP is installed</li>
     *  <li>{*home.lib*} - Where the additional MSP libraries are</li>
     *  <li>{*home.log*} - Where log files are</li>
     *  <li>{*home.tmp*} - Used for temporary files: its contents can be safely deleted</li>
     * </ul>
     * <p>
     * Example:
     * <code>
     *  INCLUDE "file://{*home*}include/standard-includes.une"
     * </code>
     * <p>
     * <p>
     * WILDCARDS:
     * <ul>
     *  <li>"*" - All files in the folder will be used</li>
     *  <li>"**" - All files in the folder and in sub-folders will be used</li>
     * </ul>
     * <p>
     * Examples:
     * <code>
     *  {*home.lib*}my_libs/*
     *  {*home.tmp*}my_temps/**
     * </code>
     *
     * @param asPaths[n]
     * @return
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

    public static List<File> listFiles( File fFolder )
    {
        return listFiles( fFolder, null );
    }

    public static List<File> listFiles( final File folder, final Function<File,Boolean> fn )
    {
        if( (folder == null) || (! folder.exists()) )
            return new ArrayList<>();

        if( fn == null )
            return Arrays.asList( folder.listFiles() );

        return Arrays.asList( folder.listFiles( (File file) -> fn.apply( file ) ) );
    }

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

    public static InputStream fromStringsToInputStream( String...  str )
    {
        if( str.length == 1 )    // To save CPU
        {
            return new ByteArrayInputStream( str[0].getBytes() );
        }

        StringBuilder sb = new StringBuilder( 1024 * str.length );

        for( String s : str )
        {
            sb.append( s );
        }

        return new ByteArrayInputStream( sb.toString().getBytes() );
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

    public static FileMonitor newFileMonitor( File file )
    {
        return new FileMonitor( file );
    }

    //------------------------------------------------------------------------//
    // WRITTING RELATED METHODS
    // Meanwhile read can be done from local or meote files, write is only local
    //------------------------------------------------------------------------//

    public static FileWriter newFileWriter()
    {
        return new FileWriter();
    }

    /**
     * Copy all contents from InputStream to the OutputStream using the Java 8 way.<br>
     * This method always attempt to close both streams.
     *
     * @param from
     * @param to
     * @throws java.io.IOException
     */
    public static void copy( InputStream from, OutputStream to ) throws IOException
    {
        try
        {
            int    length;
            byte[] bytes = new byte[ 1024 * 16 ];

            while( (length = from.read( bytes ) ) != -1 )
                to.write( bytes, 0, length );

            to.flush();
        }
        finally
        {
            try{ from.close(); } catch( IOException e ) { /* Nothing to do */ }
            try{ to.close();   } catch( IOException e ) { /* Nothing to do */ }
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

    public static void delete( File folder, FileFilter filter, boolean bSubFolders ) throws IOException
    {
        if( ! folder.exists() )
            return;

        if( ! folder.isDirectory() )
            throw new IOException( folder +": is not a folder" );

        if( bSubFolders )
            throw new IOException( "Not yet implemented" );
        // TODO: add the code needed for the option bSubFolders

        for( File f : folder.listFiles( filter ) )
            f.delete();
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
        if( f == null )
            return "ERROR: file can not be null";

        if( ! f.exists() )
            return "ERROR: file does not exists:\n\t"+ f.getAbsolutePath();

        if( f.isDirectory() )
            return "ERROR: file is a folder:\n\t"+ f.getAbsolutePath();

        if( ! f.isFile() )
            return "ERROR: file is not a regular file:\n\t"+ f.getAbsolutePath();

        if( ! f.canRead() )
            return "ERROR: file can not be readed:\n\t"+ f.getAbsolutePath();

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

    private static List<URI> listFiles( String sPath ) throws IOException     // sPath can be a remote URL
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
            String sGlob   = sPath.substring( sParent.length() + File.separator.length() -1 );
            File   fParent = new File( sParent );

            PathMatcher pathMatcher = FileSystems.getDefault().getPathMatcher( "glob:"+ sGlob  );

            Files.walkFileTree( fParent.toPath(),
                                new SimpleFileVisitor<Path>()
                                {
                                    @Override
                                    public FileVisitResult visitFile( Path file, BasicFileAttributes attrs )
                                    {
                                        if( pathMatcher.matches( file ) )
                                            lst.add( file.toUri() );

                                        return FileVisitResult.CONTINUE;
                                    }
                                } );

            // I do noty know why, but the previous Files.walkFileTree(...) does not work with things like: "12.*.une"
            // And next Files.newDirectoryStream(...) does not work with things like: "**/*"

            try( DirectoryStream<Path> directoryStream = Files.newDirectoryStream( fParent.toPath() ) )
            {
                for( Path path : directoryStream )
                {
                    if( pathMatcher.matches( path.getFileName() ) )
                    {
                        URI uri = path.toUri();

                        if( ! lst.contains( uri ) )
                            lst.add( uri );
                    }
                }
            }
        }

        return lst;
    }

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
    // INNER CLASS: FileReader
    // Thread safe when a new instance is created by user at run-time
    //------------------------------------------------------------------------//
// NEXT: terminarlo algún día
//    public final static class UriReader
//    {
//        private URI     uri     = null;
//        private Charset charset = Charset.defaultCharset();
//
//        private FileReader()    // Only accesible from this class
//        {
//        }
//
//        //------------------------------------------------------------------------//
//
//        public FileReader fromURI( String str )
//        {
//            uri = toURI( str );
//            return this;
//        }
//
//        public FileReader fromURI( File file  )
//        {
//            uri = file.toURI();
//            return this;
//        }
//
//        public FileReader fromURI( URI u )
//        {
//            uri = u;
//            return this;
//        }
//
//        public String asText()
//        {
//
//        }
//
//        public byte[] asBinary()
//        {
//
//        }
//    }

    //------------------------------------------------------------------------//
    // INNER CLASS: FileWriter
    // Thread safe when a new instance is created by user at run-time
    //------------------------------------------------------------------------//

    public static final class FileWriter
    {
        private File    file       = null;
        private boolean isTemporal = false;
        private String  extension  = null;
        private Charset charset    = Charset.defaultCharset();

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

        private void check() throws IOException
        {
            if( isTemporal )
            {
                if( file != null )
                    throw new IOException( "File is temporal but name was specified" );

                file = newTempFile();
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

    //------------------------------------------------------------------------//
    // INNER CLASS: FileMonitor
    // Thread safe when a new instance is created by user at run-time
    //------------------------------------------------------------------------//

    public static final class FileMonitor
    {
        private final Path     filePath;
        private       Consumer onCreated;
        private       Consumer onModified;
        private       Consumer onDeleted;

        private FileMonitor( File file )
        {
            filePath = file.toPath();

            Path   parentDir = filePath.getParent();
            String fileName  = filePath.getFileName().toString();

            UtilSys.execute( getClass().getSimpleName(),
                             () ->
                            {
                                boolean bOK = true;

                                try( WatchService watchService = FileSystems.getDefault().newWatchService() )
                                {
                                    parentDir.register( watchService,
                                                        StandardWatchEventKinds.ENTRY_MODIFY,
                                                        StandardWatchEventKinds.ENTRY_CREATE,
                                                        StandardWatchEventKinds.ENTRY_DELETE );

                                    while( bOK )
                                    {
                                        WatchKey key = watchService.take();    // Wait for an event

                                        for( WatchEvent<?> event : key.pollEvents() )
                                        {
                                            WatchEvent.Kind<?> kind    = event.kind();
                                            Path               evtFile = (Path) event.context();

                                            if( evtFile.toString().equals( fileName ) )
                                            {
                                                     if( kind == StandardWatchEventKinds.ENTRY_MODIFY )  onModified.accept( filePath );
                                                else if( kind == StandardWatchEventKinds.ENTRY_DELETE )  onDeleted.accept(  filePath );
                                                else if( kind == StandardWatchEventKinds.ENTRY_CREATE )  onCreated.accept(  filePath );
                                            }
                                        }

                                        boolean valid = key.reset();

                                        if( ! valid )
                                            break;    // WatchKey no longer valid, exiting...
                                    }
                                }
                                catch( IOException | InterruptedException e )
                                {
                                    // Nothing to do
                                }
                            } );
        }

        public FileMonitor onCreated( Consumer action )
        {
            onCreated = action;
            return this;
        }

        public FileMonitor onModified( Consumer action )
        {
            onModified = action;
            return this;
        }

        public FileMonitor onDeleted( Consumer action )
        {
            onDeleted = action;
            return this;
        }
    }
}