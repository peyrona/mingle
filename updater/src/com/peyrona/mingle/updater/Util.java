
package com.peyrona.mingle.updater;

import com.peyrona.mingle.lang.japi.UtilIO;
import com.peyrona.mingle.lang.japi.UtilSys;
import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

/**
 * Package scope utilities.
 *
 * @author Francisco José Morero Peyrona
 *
 * Official web site at: <a href="https://mingle.peyrona.com">https://mingle.peyrona.com</a>
 */
final class Util
{
    static final String sFILE_LAST_NAME = "last.modified.file.txt";     // The file name that contains the timestamp for the moest recent file
    static final String sFILE_LIST_NAME = "file_list.json";             // The file name that contains the list of files
    static final String sJSON_NAME      = "name";
    static final String sJSON_FOLDER    = "folder";
    static final String sJSON_MODIFIED  = "modified";

    private static       String           sMingleHomeFolder = null;
    private static final Map<String,File> map = new HashMap<>();

    private static final String[] asBinary = { "zip", "jar", "war", "jpg", "png", "ico", "svg", "gz", "odt", "pdf" };
    private static final String[] asASCII  = { "une", "html", "css", "jsp", "txt", "json", "xml", "xsl", "xsd", "sh", "bat", "ps1", "properties", "props", "md", "policy", "" };

    //------------------------------------------------------------------------//

    static boolean isBinary( File f )
    {
        return UtilIO.hasExtension( f, asBinary );
    }

    static boolean isBinary( String name )
    {
        return UtilIO.hasExtension( name, asBinary );
    }

    static boolean isASCII( File f )
    {
        return UtilIO.hasExtension( f, asASCII );
    }

    static boolean isASCII( String name )
    {
        return UtilIO.hasExtension( name, asASCII );
    }

    static Map<String,File> getLocalDistroFiles() throws IOException
    {
        if( map.isEmpty() )
            populateLocalFiles( UtilSys.fHomeDir );

        return map;
    }

    /**
     * Returns full file path (with its name) after removing UtilSys.fHomeDir.
     *
     * @param fLocal
     * @return Full file path (with its name) after removing UtilSys.fHomeDir.
     * @throws IOException
     * @see UtilSys#fHomeDir
     */
    static String getFilePathFromHomeDir( final File fLocal )
    {
        if( fLocal.equals(UtilSys.fHomeDir  ) )
            return "";    // Root folder

        try
        {
            if( sMingleHomeFolder == null )     // Initialize cache if needed
                sMingleHomeFolder = UtilSys.fHomeDir.getCanonicalPath().replace( "\\", "/" );    // To ensure consistent path separators;

            String sCanonicalFileName = fLocal.getCanonicalPath().replace( "\\", "/" );

            if( sCanonicalFileName.startsWith( sMingleHomeFolder ) )
            {
                String s = sCanonicalFileName.substring( sMingleHomeFolder.length() );

                if( s.length() < 2 )
                    throw new RuntimeException( "Empty file name: "+ fLocal );

                return ((s.charAt(0) == File.separatorChar) ? s.substring(1) : s);
            }

            throw new RuntimeException( "File "+ sCanonicalFileName +"\ndoes not belong to: "+ sMingleHomeFolder );
        }
        catch( IOException ioe )
        {
            throw new RuntimeException( ioe );
        }
    }

//    static void cleanFolder( File folder ) throws IOException
//    {
//        if( ! folder.exists() || ! folder.isDirectory() )
//            throw new IllegalArgumentException( "Invalid folder path: " + folder );
//
//        File[] files = folder.listFiles();
//
//        if( files != null )
//        {
//            for( File file : files )
//            {
//                if( file.isDirectory() )  cleanFolder( file );            // Recursively delete subdirectory contents
//                else                      Files.delete( file.toPath() );
//            }
//        }
//    }

//    static void copyFolder( File source, File target ) throws IOException
//    {
//        Path sourceDir = source.toPath();
//        Path targetDir = target.toPath();
//
//        if( ! Files.exists( targetDir ) )
//            Files.createDirectories( targetDir );
//
//        Files.walkFileTree( sourceDir,
//                            new SimpleFileVisitor<Path>()
//                            {
//                                @Override
//                                public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException
//                                {
//                                    Path targetDirPath = targetDir.resolve( sourceDir.relativize( dir ) );
//                                    Files.createDirectories( targetDirPath );
//                                    return FileVisitResult.CONTINUE;
//                                }
//
//                                @Override
//                                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException
//                                {
//                                    Path targetFilePath = targetDir.resolve( sourceDir.relativize( file ) );
//                                    Files.copy( file, targetFilePath, StandardCopyOption.COPY_ATTRIBUTES );
//                                    return FileVisitResult.CONTINUE;
//                                }
//                            } );
//    }

    //------------------------------------------------------------------------//
    // PRIVATE SCOPE

    private static void populateLocalFiles( File folder ) throws IOException    // This method is recursive
    {
        String[] asExcludeExt = { "model", "odt", "odg", "graphml", "conn-def.props" };
        String[] asExcludeDir = { "log", "tmp", "website", "docs/images" };
        File[]   afExcludeDir = new File[ asExcludeDir.length ];

        for( int n = 0; n < asExcludeDir.length; n++ )
            afExcludeDir[n] = new File( UtilSys.fHomeDir, asExcludeDir[n] );

        for( File file : folder.listFiles() )
        {
            boolean bInclude = true;

            if( file.isDirectory() )
            {
                for( File fDir : afExcludeDir )
                {
                    if( fDir.equals( file ) )
                    {
                        bInclude = false;
                        break;
                    }
                }
            }
            else
            {
                for( String s : asExcludeExt )
                {
                    if( UtilIO.hasExtension( file, s ) )
                    {
                        bInclude = false;
                        break;
                    }
                }
            }

            if( bInclude )
            {
                map.put( getFilePathFromHomeDir( file ), file );

                if( file.isDirectory() )
                    populateLocalFiles( file );
            }
        }
    }
}