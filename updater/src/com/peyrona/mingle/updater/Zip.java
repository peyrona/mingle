
package com.peyrona.mingle.updater;

import com.peyrona.mingle.lang.japi.UtilStr;
import com.peyrona.mingle.lang.japi.UtilSys;
import com.peyrona.mingle.lang.japi.UtilUnit;
import java.io.Closeable;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.function.Function;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 *
 * @author Francisco José Morero Peyrona
 *
 * Official web site at: <a href="https://github.com/peyrona/mingle">https://github.com/peyrona/mingle</a>
 */
public class Zip implements Closeable
{
    private File             fZip;
    private FileOutputStream fos = null;
    private ZipOutputStream  zos = null;

    //------------------------------------------------------------------------//

    public Zip( String sFileName )
    {
        sFileName += (UtilStr.endsWith( sFileName, ".zip" ) ? "" : ".zip");

        fZip = new File( UtilSys.getTmpDir(), sFileName );
        fZip.deleteOnExit();
    }

    //------------------------------------------------------------------------//

    @Override
    public void close() throws IOException
    {
        try
        {
            if( zos != null )
            {
                zos.flush();
                zos.close();
            }

            if( fos != null )
            {
                fos.flush();
                fos.close();
            }
        }
        finally
        {
            synchronized( this )
            {
                zos = null;
                fos = null;
            }
        }
    }

    //------------------------------------------------------------------------//

    public File getFile()
    {
        return fZip;
    }

    public Zip add( File folder, Function<File,String> fnName4Zip ) throws IOException
    {
        return add( listAll( folder ), fnName4Zip );
    }

    public synchronized Zip add( Collection<File> lstFiles2Add, Function<File,String> fnName4Zip ) throws IOException
    {
        byte[] abBuffer = new byte[ UtilUnit.KILO_BYTE * 64 ];

        if( fos == null )
            fos = new FileOutputStream( fZip );

        if( zos == null )
            zos = new ZipOutputStream( fos );

        for( File file : lstFiles2Add )
        {
            if( ! file.isDirectory() )
            {
                zos.putNextEntry( new ZipEntry( fnName4Zip.apply( file ) ) );

                try( FileInputStream fis = new FileInputStream( file ) )
                {
                    int length;

                    while( (length = fis.read( abBuffer )) > 0 )
                        zos.write( abBuffer, 0, length );
                }

                zos.closeEntry();
            }
        }

        zos.flush();
        fos.flush();

        return this;
    }

    public Zip moveTo( File folder ) throws IOException
    {
        try
        {
            close();
        }
        catch( IOException e )
        {
            System.err.println( "Warning: Error closing zip file before move: " + e.getMessage() );
        }

        Path zipPath = fZip.toPath();
        Path target  = Files.move( zipPath, folder.toPath().resolve( zipPath.getFileName()), StandardCopyOption.REPLACE_EXISTING );

        synchronized( this )
        {
            fZip = target.toFile();
        }

        return this;
    }

    //------------------------------------------------------------------------//

    private static List<File> listAll( File folder )
    {
        List<File> fileList = new ArrayList<>();
        File[]     files = folder.listFiles();

        if( files != null )
        {
            for( File file : files )
            {
                fileList.add( file );

                if( file.isDirectory() )
                    fileList.addAll( listAll( file ) );
            }
        }

        return fileList;
    }
}