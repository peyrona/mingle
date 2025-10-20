package com.peyrona.mingle.updater;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * File discovery strategy using directory traversal.
 *
 * @author Francisco José Morero Peyrona
 *
 * Official web site at: <a href="https://github.com/peyrona/mingle">https://github.com/peyrona/mingle</a>
 */
public class TraversalFileDiscoveryStrategy implements FileDiscoveryStrategy
{
    private final File baseDir;
    
    public TraversalFileDiscoveryStrategy( File baseDir )
    {
        this.baseDir = baseDir;
    }
    
    @Override
    public List<FileEntry> discoverFiles()
    {
        List<FileEntry> entries = new ArrayList<>();
        List<FileEntry> catalogEntries = new ArrayList<>();
        traverseDirectory( baseDir, "", entries, catalogEntries );
        
        // Add catalog.json entries at the end to ensure they're processed last
        entries.addAll( catalogEntries );
        return entries;
    }
    
    private void traverseDirectory( File dir, String relativePath, List<FileEntry> entries, List<FileEntry> catalogEntries )
    {
        File[] files = dir.listFiles();
        if( files == null )
        {
            return;
        }
        
        for( File file : files )
        {
            if( file.isDirectory() )
            {
                String newPath = relativePath.isEmpty() ? file.getName() : relativePath + "/" + file.getName();
                traverseDirectory( file, newPath, entries, catalogEntries );
            }
            else if( file.isFile() )
            {
                String filePath = relativePath.isEmpty() ? file.getName() : relativePath + "/" + file.getName();
                FileEntry fileEntry = new FileEntry( filePath, null ); // No expected hash when traversing
                
                // Separate catalog.json from other files
                if( "catalog.json".equals( filePath ) )
                {
                    catalogEntries.add( fileEntry );
                }
                else
                {
                    entries.add( fileEntry );
                }
            }
        }
    }
}
