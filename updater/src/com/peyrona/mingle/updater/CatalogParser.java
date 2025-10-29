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
        if( catalogContent == null || catalogContent.trim().isEmpty() )
        {
            UtilSys.getLogger().log( ILogger.Level.WARNING, "Catalog content is null or empty" );
            return new ArrayList<>();
        }
        
        List<CatalogFileEntry> entries = new ArrayList<>();
        
        try
        {
            // Simple JSON parsing (avoiding external dependencies)
            java.util.regex.Matcher matcher = CATALOG_PATTERN.matcher( catalogContent );
            
            while( matcher.find() )
            {
                String path = matcher.group( 1 );
                String hash = matcher.group( 2 );
                
                if( path != null && ! path.trim().isEmpty() && 
                    hash != null && ! hash.trim().isEmpty() )
                {
                    CatalogFileEntry entry = new CatalogFileEntry();
                    entry.path = path.trim();
                    entry.hash = hash.trim();
                    entries.add( entry );
                }
                else
                {
                    UtilSys.getLogger().log( ILogger.Level.WARNING, "Invalid catalog entry: path=" + path + ", hash=" + hash );
                }
            }
        }
        catch( Exception e )
        {
            UtilSys.getLogger().log( ILogger.Level.WARNING, e, "Error parsing catalog JSON" );
            return new ArrayList<>();
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
        if( catalogContent == null || catalogContent.trim().isEmpty() )
        {
            return null;
        }
        
        try
        {
            java.util.regex.Matcher matcher = VERSION_REGEX.matcher( catalogContent );
            
            if( matcher.find() )
            {
                String version = matcher.group( 1 );
                return (version != null && ! version.trim().isEmpty()) ? version.trim() : null;
            }
        }
        catch( Exception e )
        {
            UtilSys.getLogger().log( ILogger.Level.WARNING, e, "Error parsing version from catalog" );
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
