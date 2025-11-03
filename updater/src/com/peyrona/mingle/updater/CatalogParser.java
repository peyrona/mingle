package com.peyrona.mingle.updater;

import com.eclipsesource.json.Json;
import com.eclipsesource.json.JsonArray;
import com.eclipsesource.json.JsonObject;
import com.eclipsesource.json.JsonValue;
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
            JsonObject catalog = Json.parse(catalogContent).asObject();
            JsonArray files = catalog.get("files").asArray();

            for (JsonValue fileValue : files) {
                JsonObject fileObject = fileValue.asObject();
                String path = fileObject.getString("path", null);
                String hash = fileObject.getString("hash", null);

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
            JsonObject catalog = Json.parse(catalogContent).asObject();
            String version = catalog.getString("version", null);
            return (version != null && ! version.trim().isEmpty()) ? version.trim() : null;
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