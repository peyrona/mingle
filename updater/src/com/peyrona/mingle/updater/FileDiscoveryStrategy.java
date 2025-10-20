package com.peyrona.mingle.updater;

import java.util.List;

/**
 * Strategy interface for discovering files that need to be checked/updated.
 *
 * @author Francisco José Morero Peyrona
 *
 * Official web site at: <a href="https://github.com/peyrona/mingle">https://github.com/peyrona/mingle</a>
 */
public interface FileDiscoveryStrategy
{
    /**
     * Discovers files and returns their entries with paths and expected hashes.
     * 
     * @return List of file entries to process
     */
    List<FileEntry> discoverFiles();
    
    //------------------------------------------------------------------------//
    // INNER CLASS
    //------------------------------------------------------------------------//
    
    /**
     * Represents a file entry with path and optional expected hash.
     */
    class FileEntry
    {
        public final String path;
        public final String expectedHash;
        
        public FileEntry( String path, String expectedHash )
        {
            this.path = path;
            this.expectedHash = expectedHash;
        }
    }
}
