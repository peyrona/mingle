package com.peyrona.mingle.updater;

import com.peyrona.mingle.lang.interfaces.ILogger;
import com.peyrona.mingle.lang.japi.UtilSys;
import java.util.ArrayList;
import java.util.List;

/**
 * File discovery strategy using catalog.json content.
 *
 * @author Francisco José Morero Peyrona
 *
 * Official web site at: <a href="https://github.com/peyrona/mingle">https://github.com/peyrona/mingle</a>
 */
public class CatalogFileDiscoveryStrategy implements FileDiscoveryStrategy
{
    private final String catalogContent;

    //------------------------------------------------------------------------//

    public CatalogFileDiscoveryStrategy( String catalogContent )
    {
        this.catalogContent = catalogContent;
    }

    @Override
    public List<FileEntry> discoverFiles()
    {
        List<FileEntry> entries        = new ArrayList<>();
        List<FileEntry> catalogEntries = new ArrayList<>();

        try
        {
            List<CatalogParser.CatalogFileEntry> catalogFileEntries = CatalogParser.parseCatalogJson( catalogContent );

            for( CatalogParser.CatalogFileEntry entry : catalogFileEntries )
            {
                FileEntry fileEntry = new FileEntry( entry.path, entry.hash, entry.isProtected );

                // Separate catalog.json from other files to ensure it is processed last
                if( "catalog.json".equals( entry.path ) )
                    catalogEntries.add( fileEntry );
                else
                    entries.add( fileEntry );
            }
        }
        catch( RuntimeException e )
        {
            UtilSys.getLogger().log( ILogger.Level.SEVERE, e, "Runtime error processing catalog.json" );
            throw e;
        }

        entries.addAll( catalogEntries );
        return entries;
    }
}
