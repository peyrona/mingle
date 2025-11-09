package com.peyrona.mingle.updater;

import com.peyrona.mingle.lang.interfaces.ILogger;
import com.peyrona.mingle.lang.japi.UtilSys;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

/**
 * File discovery strategy using catalog.json.
 *
 * @author Francisco José Morero Peyrona
 *
 * Official web site at: <a href="https://github.com/peyrona/mingle">https://github.com/peyrona/mingle</a>
 */
public class CatalogFileDiscoveryStrategy implements FileDiscoveryStrategy
{
    private final File catalogFile;
    private final String catalogContent;

    //------------------------------------------------------------------------//

    public CatalogFileDiscoveryStrategy( File catalogFile )
    {
        this.catalogFile = catalogFile;
        this.catalogContent = null;
    }

    public CatalogFileDiscoveryStrategy( String catalogContent )
    {
        this.catalogFile = null;
        this.catalogContent = catalogContent;
    }

    @Override
    public List<FileEntry> discoverFiles()
    {
        List<FileEntry> entries = new ArrayList<>();
        List<FileEntry> catalogEntries = new ArrayList<>();

        try
        {
            String content = this.catalogContent;
            if( content == null )
            {
                content = Files.readString( catalogFile.toPath() );
            }
            List<CatalogParser.CatalogFileEntry> catalogFileEntries = CatalogParser.parseCatalogJson( content );

            for( CatalogParser.CatalogFileEntry entry : catalogFileEntries )
            {
                FileEntry fileEntry = new FileEntry( entry.path, entry.hash );
                
                // Separate catalog.json from other files
                if( "etc/catalog.json".equals( entry.path ) )
                {
                    catalogEntries.add( fileEntry );
                }
                else
                {
                    entries.add( fileEntry );
                }
            }

        }
        catch( IOException e )
        {
            UtilSys.getLogger().log( ILogger.Level.WARNING, e, "Error reading catalog.json" );
        }
        catch( RuntimeException e )
        {
            UtilSys.getLogger().log( ILogger.Level.SEVERE, e, "Runtime error processing catalog.json" );
            throw e;
        }

        // Add catalog.json entries at the end to ensure they're processed last
        entries.addAll( catalogEntries );

        return entries;
    }
}
