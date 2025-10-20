package com.peyrona.mingle.updater;

import com.peyrona.mingle.lang.interfaces.ILogger;
import com.peyrona.mingle.lang.japi.UtilSys;
import java.util.ArrayList;
import java.util.List;

/**
 * Utility class for parsing catalog.json content.
 *
 * @author Francisco José Morero Peyrona
 *
 * Official web site at: <a href="https://github.com/peyrona/mingle">https://github.com/peyrona/mingle</a>
 */
public final class CatalogParser
{
    private static final String CATALOG_FILE_PATTERN = "\\{\\s*\"path\"\\s*:\\s*\"([^\"]+)\"\\s*,\\s*\"hash\"\\s*:\\s*\"([a-fA-F0-9]{40}|[a-fA-F0-9]{64})\"\\s*\\}";
    private static final java.util.regex.Pattern CATALOG_PATTERN = java.util.regex.Pattern.compile( CATALOG_FILE_PATTERN, java.util.regex.Pattern.CASE_INSENSITIVE );
    
    private static final String VERSION_PATTERN = "\"version\"\\s*:\\s*\"([^\"]+)\"";
    private static final java.util.regex.Pattern VERSION_REGEX = java.util.regex.Pattern.compile( VERSION_PATTERN );
    
    /**
     * Parses catalog.json content to extract file entries.
     *
     * @param catalogContent JSON content
     * @return List of file entries
     */
    public static List<CatalogFileEntry> parseCatalogJson( String catalogContent )
    {
        List<CatalogFileEntry> entries = new ArrayList<>();
        
        // Simple JSON parsing (avoiding external dependencies)
        java.util.regex.Matcher matcher = CATALOG_PATTERN.matcher( catalogContent );
        
        while( matcher.find() )
        {
            CatalogFileEntry entry = new CatalogFileEntry();
            entry.path = matcher.group( 1 );
            entry.hash = matcher.group( 2 );
            entries.add( entry );
        }
        
        UtilSys.getLogger().log( ILogger.Level.INFO, "Parsed " + entries.size() + " file entries from catalog.json" );
        return entries;
    }
    
    /**
     * Extracts version from catalog.json content.
     *
     * @param catalogContent JSON content
     * @return Version string or null if not found
     */
    public static String parseVersionFromCatalog( String catalogContent )
    {
        java.util.regex.Matcher matcher = VERSION_REGEX.matcher( catalogContent );
        
        if( matcher.find() )
        {
            return matcher.group( 1 );
        }
        
        return null;
    }
    
    //------------------------------------------------------------------------//
    // INNER CLASS
    //------------------------------------------------------------------------//
    
    /**
     * Represents a file entry from catalog.json.
     */
    public static class CatalogFileEntry
    {
        public String path;
        public String hash;
    }
}
