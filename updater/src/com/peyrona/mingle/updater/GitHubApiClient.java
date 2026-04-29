package com.peyrona.mingle.updater;

import com.eclipsesource.json.Json;
import com.eclipsesource.json.JsonObject;
import com.peyrona.mingle.lang.interfaces.ILogger;
import com.peyrona.mingle.lang.japi.UtilSys;
import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Client for interacting with GitHub API to fetch file information and content.
 *
 * @author Francisco José Morero Peyrona
 *
 * Official web site at: <a href="https://github.com/peyrona/mingle">https://github.com/peyrona/mingle</a>
 */
public final class GitHubApiClient
{
    private static final String REPO_OWNER  = "peyrona";
    private static final String REPO_NAME   = "mingle";
    private static final String REPO_BRANCH = "main";
    private static final String REPO_PATH   = "todeploy";

    private static final String USER_AGENT = "Mingle-Updater/1.0";

    // Rate limiting
    private static final long DEFAULT_DELAY_MS = 2000;
    private static final long MAX_BACKOFF_MS   = 32000;
    private static final int  MAX_RETRIES      = 5;

    private static volatile long   lastApiCallTime = 0;
    private static final    Object rateLimitLock   = new Object();

    // Metadata cache with LRU eviction and TTL
    private static final int MAX_CACHE_SIZE = 1000;
    private static final Map<String, CachedMetadata> metadataCache = Collections.synchronizedMap(
        new LinkedHashMap<String, CachedMetadata>( MAX_CACHE_SIZE, 0.75f, true )
        {
            @Override
            protected boolean removeEldestEntry( Map.Entry<String, CachedMetadata> eldest )
            {
                boolean remove = size() > MAX_CACHE_SIZE;
                if( remove )
                    cacheEvictions.incrementAndGet();
                return remove;
            }
        } );

    private static final long CACHE_TTL_MS              = 5 * 60 * 1000;
    private static final long CACHE_CLEANUP_INTERVAL_MS = 60 * 1000;
    private static volatile long lastCacheCleanup        = 0;

    private static final java.util.concurrent.atomic.AtomicLong cacheHits      = new java.util.concurrent.atomic.AtomicLong( 0 );
    private static final java.util.concurrent.atomic.AtomicLong cacheMisses    = new java.util.concurrent.atomic.AtomicLong( 0 );
    private static final java.util.concurrent.atomic.AtomicLong cacheEvictions = new java.util.concurrent.atomic.AtomicLong( 0 );

    private GitHubApiClient() {}

    //------------------------------------------------------------------------//

    /**
     * Gets file metadata from GitHub API including SHA hash. Uses caching to avoid repeated requests.
     *
     * @param filePath Relative path within the todeploy directory
     * @return GitHubFileResponse containing file metadata, or null if not found or error occurs
     */
    public static GitHubFileResponse getFileMetadata( String filePath )
    {
        if( filePath == null || filePath.trim().isEmpty() )
        {
            UtilSys.getLogger().log( ILogger.Level.WARNING, "File path cannot be null or empty" );
            return null;
        }

        GitHubFileResponse cached = getCachedMetadata( filePath );
        if( cached != null )
        {
            return cached;
        }

        applyRateLimiting();

        HttpURLConnection connection = null;

        try
        {
            String path = String.format( "/repos/%s/%s/contents/%s/%s", REPO_OWNER, REPO_NAME, REPO_PATH, filePath );
            URI uri = new URI( "https", "api.github.com", path, null, null );
            URL url = uri.toURL();

            connection = executeWithRetry( url, filePath );

            if( connection == null )
            {
                return null;
            }

            try( BufferedReader reader = new BufferedReader( new InputStreamReader( connection.getInputStream() ) ) )
            {
                StringBuilder response = new StringBuilder();
                char[] buffer = new char[8192];
                int bytesRead;

                while( (bytesRead = reader.read( buffer )) != -1 )
                {
                    response.append( buffer, 0, bytesRead );
                }

                GitHubFileResponse metadata = parseGitHubFileResponse( response.toString(), filePath );
                cacheMetadata( filePath, metadata );
                return metadata;
            }
        }
        catch( IOException | URISyntaxException e )
        {
            UtilSys.getLogger().log( ILogger.Level.WARNING, e, "Error fetching file metadata from GitHub: " + filePath );
            return null;
        }
        finally
        {
            if( connection != null )
                connection.disconnect();
        }
    }

    /**
     * Downloads a file from a specific URL.
     *
     * @param downloadUrl The exact URL to download from
     * @param filePath    Relative path of the file (for logging)
     * @param targetPath  Local path where to save the file
     * @return true if download successful, false otherwise
     */
    public static boolean downloadFileFromUrl( String downloadUrl, String filePath, Path targetPath )
    {
        if( downloadUrl == null || downloadUrl.trim().isEmpty() )
        {
            UtilSys.getLogger().log( ILogger.Level.WARNING, "Download URL cannot be null or empty" );
            return false;
        }

        if( targetPath == null )
        {
            UtilSys.getLogger().log( ILogger.Level.WARNING, "Target path cannot be null" );
            return false;
        }

        applyRateLimiting();

        HttpURLConnection connection = null;

        try
        {
            URL url = new URI( downloadUrl ).toURL();
            connection = executeWithRetry( url, filePath );

            if( connection == null )
            {
                return false;
            }

            Files.createDirectories( targetPath.getParent() );

            try( InputStream  inputStream  = new BufferedInputStream( connection.getInputStream() );
                 OutputStream outputStream = Files.newOutputStream( targetPath, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING ) )
            {
                byte[] buffer = new byte[65536];
                int bytesRead;
                long totalBytes = 0;

                while( (bytesRead = inputStream.read( buffer )) != -1 )
                {
                    outputStream.write( buffer, 0, bytesRead );
                    totalBytes += bytesRead;
                }

                UtilSys.getLogger().log( ILogger.Level.INFO, "Successfully downloaded: " + filePath + " (" + totalBytes + " bytes)" );
                return true;
            }
        }
        catch( URISyntaxException | MalformedURLException e )
        {
            UtilSys.getLogger().log( ILogger.Level.WARNING, e, "Malformed download URL: " + downloadUrl );
            return false;
        }
        catch( IOException e )
        {
            UtilSys.getLogger().log( ILogger.Level.WARNING, e, "Error downloading file from URL: " + downloadUrl );
            return false;
        }
        finally
        {
            if( connection != null )
                connection.disconnect();
        }
    }

    /**
     * Downloads a file from the todeploy directory on GitHub.
     *
     * @param filePath   Relative path within the todeploy directory
     * @param targetPath Local path where to save the file
     * @return true if download successful, false otherwise
     */
    public static boolean downloadFile( String filePath, Path targetPath )
    {
        if( filePath == null || filePath.trim().isEmpty() )
        {
            UtilSys.getLogger().log( ILogger.Level.WARNING, "File path cannot be null or empty" );
            return false;
        }

        if( targetPath == null )
        {
            UtilSys.getLogger().log( ILogger.Level.WARNING, "Target path cannot be null" );
            return false;
        }

        applyRateLimiting();

        HttpURLConnection connection = null;

        try
        {
            String path = String.format( "/%s/%s/%s/%s/%s", REPO_OWNER, REPO_NAME, REPO_BRANCH, REPO_PATH, filePath );
            URI uri = new URI( "https", "raw.githubusercontent.com", path, null, null );
            URL url = uri.toURL();

            connection = executeWithRetry( url, filePath );

            if( connection == null )
            {
                return false;
            }

            Files.createDirectories( targetPath.getParent() );

            try( InputStream  inputStream  = new BufferedInputStream( connection.getInputStream() );
                 OutputStream outputStream = Files.newOutputStream( targetPath, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING ) )
            {
                byte[] buffer = new byte[65536];
                int bytesRead;
                long totalBytes = 0;

                while( (bytesRead = inputStream.read( buffer )) != -1 )
                {
                    outputStream.write( buffer, 0, bytesRead );
                    totalBytes += bytesRead;
                }

                UtilSys.getLogger().log( ILogger.Level.INFO, "Successfully downloaded: " + filePath + " (" + totalBytes + " bytes)" );
                return true;
            }
        }
        catch( IOException | URISyntaxException e )
        {
            UtilSys.getLogger().log( ILogger.Level.WARNING, e, "Error downloading file: " + filePath );
            return false;
        }
        finally
        {
            if( connection != null )
                connection.disconnect();
        }
    }

    /**
     * Downloads a file from the GitHub repository root.
     *
     * @param filePath   Relative path from repository root
     * @param targetPath Local path where to save the file
     * @return true if download successful, false otherwise
     */
    public static boolean downloadFileFromRoot( String filePath, Path targetPath )
    {
        if( filePath == null || filePath.trim().isEmpty() )
        {
            UtilSys.getLogger().log( ILogger.Level.WARNING, "File path cannot be null or empty" );
            return false;
        }

        if( targetPath == null )
        {
            UtilSys.getLogger().log( ILogger.Level.WARNING, "Target path cannot be null" );
            return false;
        }

        applyRateLimiting();

        HttpURLConnection connection = null;

        try
        {
            String path = String.format( "/%s/%s/%s/%s", REPO_OWNER, REPO_NAME, REPO_BRANCH, filePath );
            URI uri = new URI( "https", "raw.githubusercontent.com", path, null, null );
            URL url = uri.toURL();

            connection = executeWithRetry( url, filePath );

            if( connection == null )
            {
                return false;
            }

            Files.createDirectories( targetPath.getParent() );

            try( InputStream  inputStream  = new BufferedInputStream( connection.getInputStream() );
                 OutputStream outputStream = Files.newOutputStream( targetPath, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING ) )
            {
                byte[] buffer = new byte[65536];
                int bytesRead;
                long totalBytes = 0;

                while( (bytesRead = inputStream.read( buffer )) != -1 )
                {
                    outputStream.write( buffer, 0, bytesRead );
                    totalBytes += bytesRead;
                }

                UtilSys.getLogger().log( ILogger.Level.INFO, "Successfully downloaded from root: " + filePath + " (" + totalBytes + " bytes)" );
                return true;
            }
        }
        catch( IOException | URISyntaxException e )
        {
            UtilSys.getLogger().log( ILogger.Level.WARNING, e, "Error downloading file from root: " + filePath );
            return false;
        }
        finally
        {
            if( connection != null )
                connection.disconnect();
        }
    }

    /**
     * Logs cache statistics for monitoring.
     */
    public static void logCacheStatistics()
    {
        long hits    = cacheHits.get();
        long misses  = cacheMisses.get();
        long total   = hits + misses;
        double hitRate = total > 0 ? (double) hits / total * 100 : 0;

        UtilSys.getLogger().log( ILogger.Level.INFO, String.format(
            "Cache stats: %d hits, %d misses, %.1f%% hit rate, %d entries cached, %d evictions",
            hits, misses, hitRate, metadataCache.size(), cacheEvictions.get() ) );
    }

    //------------------------------------------------------------------------//
    // PRIVATE METHODS
    //------------------------------------------------------------------------//

    private static void applyRateLimiting()
    {
        synchronized( rateLimitLock )
        {
            long currentTime      = System.currentTimeMillis();
            long timeSinceLastCall = currentTime - lastApiCallTime;

            if( timeSinceLastCall < DEFAULT_DELAY_MS )
            {
                try
                {
                    Thread.sleep( DEFAULT_DELAY_MS - timeSinceLastCall );
                }
                catch( InterruptedException e )
                {
                    Thread.currentThread().interrupt();
                }
            }

            lastApiCallTime = System.currentTimeMillis();
        }
    }

    private static HttpURLConnection executeWithRetry( URL url, String filePath ) throws IOException
    {
        long backoffTime = 2000;

        for( int attempt = 0; attempt < MAX_RETRIES; attempt++ )
        {
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
                              connection.setRequestMethod( "GET" );
                              connection.setRequestProperty( "Accept", "application/vnd.github.v3+json" );
                              connection.setRequestProperty( "User-Agent", USER_AGENT );
                              connection.setConnectTimeout( 10000 );
                              connection.setReadTimeout( 30000 );

            int responseCode = connection.getResponseCode();

            if( responseCode == 200 )
            {
                logRateLimitInfo( connection );
                return connection;
            }

            boolean shouldRetry = handleRateLimit( responseCode, connection, filePath );
            consumeErrorStream( connection );
            connection.disconnect();

            if( ! shouldRetry )
            {
                UtilSys.getLogger().log( ILogger.Level.WARNING, "GitHub API request failed with status " + responseCode + " for: " + filePath );
                return null;
            }

            if( attempt < MAX_RETRIES - 1 )
            {
                try
                {
                    long jitter        = (long) (Math.random() * 1000);
                    long totalWaitTime = backoffTime + jitter;

                    UtilSys.getLogger().log( ILogger.Level.INFO,
                        String.format( "Rate limited, waiting %dms before retry (%d/%d) for: %s",
                                       totalWaitTime, attempt + 1, MAX_RETRIES, filePath ) );
                    Thread.sleep( totalWaitTime );
                    backoffTime = Math.min( backoffTime * 2, MAX_BACKOFF_MS );
                }
                catch( InterruptedException e )
                {
                    Thread.currentThread().interrupt();
                    return null;
                }
            }
        }

        UtilSys.getLogger().log( ILogger.Level.SEVERE, "Max retries exceeded for: " + filePath );
        return null;
    }

    private static void consumeErrorStream( HttpURLConnection connection )
    {
        InputStream errorStream = connection.getErrorStream();
        if( errorStream == null )
            return;

        try( BufferedReader reader = new BufferedReader( new InputStreamReader( errorStream ) ) )
        {
            while( reader.readLine() != null )
            {
                /* consume */
            }
        }
        catch( Exception e )
        {
            /* ignore */
        }
    }

    private static boolean handleRateLimit( int responseCode, HttpURLConnection connection, String filePath )
    {
        if( responseCode == 429 )
        {
            UtilSys.getLogger().log( ILogger.Level.WARNING, "Rate limit hit for: " + filePath );
            return true;
        }

        if( responseCode == 403 )
        {
            String rateLimitRemaining = connection.getHeaderField( "X-RateLimit-Remaining" );

            if( "0".equals( rateLimitRemaining ) )
            {
                String reset = connection.getHeaderField( "X-RateLimit-Reset" );
                if( reset != null )
                {
                    try
                    {
                        long resetTimestamp = Long.parseLong( reset );
                        long waitTime       = resetTimestamp - System.currentTimeMillis() / 1000;

                        if( waitTime > 0 )
                            UtilSys.getLogger().log( ILogger.Level.WARNING, String.format( "Rate limit exceeded for: %s. Reset in %d seconds", filePath, waitTime ) );
                        else
                            UtilSys.getLogger().log( ILogger.Level.WARNING, "Rate limit exceeded for: " + filePath + " (should reset soon)" );
                    }
                    catch( NumberFormatException e )
                    {
                        UtilSys.getLogger().log( ILogger.Level.WARNING, "Rate limit exceeded for: " + filePath );
                    }
                }
                else
                {
                    UtilSys.getLogger().log( ILogger.Level.WARNING, "Rate limit exceeded for: " + filePath );
                }
                return true;
            }

            UtilSys.getLogger().log( ILogger.Level.WARNING, "Access forbidden for: " + filePath + " (repository may be private)" );
            return false;
        }

        return false;
    }

    private static void logRateLimitInfo( HttpURLConnection connection )
    {
        String limit     = connection.getHeaderField( "X-RateLimit-Limit" );
        String remaining = connection.getHeaderField( "X-RateLimit-Remaining" );
        String reset     = connection.getHeaderField( "X-RateLimit-Reset" );

        if( limit == null || remaining == null )
            return;

        String logMessage = String.format( "Rate limit: %s/%s requests remaining", remaining, limit );

        if( reset != null )
        {
            try
            {
                long resetTimestamp = Long.parseLong( reset );
                String resetTime = java.time.Instant.ofEpochSecond( resetTimestamp )
                        .atZone( java.time.ZoneOffset.UTC )
                        .format( java.time.format.DateTimeFormatter.ofPattern( "yyyy-MM-dd HH:mm:ss" ) ) + " UTC";
                logMessage += ". Resets at: " + resetTime;
            }
            catch( NumberFormatException e )
            {
                logMessage += ". Reset time unavailable";
            }
        }

        UtilSys.getLogger().log( ILogger.Level.INFO, logMessage );
    }

    private static GitHubFileResponse parseGitHubFileResponse( String jsonResponse, String filePath )
    {
        try
        {
            JsonObject fileData = Json.parse( jsonResponse ).asObject();
            GitHubFileResponse response = new GitHubFileResponse();
            response.path        = fileData.getString( "path", filePath );
            response.sha         = fileData.getString( "sha", null );
            response.downloadUrl = fileData.getString( "download_url", null );
            response.size        = fileData.getLong( "size", 0 );
            return response;
        }
        catch( Exception e )
        {
            UtilSys.getLogger().log( ILogger.Level.WARNING, e, "Error parsing GitHub API response for: " + filePath );
            return null;
        }
    }

    private static GitHubFileResponse getCachedMetadata( String filePath )
    {
        cleanupCacheIfNeeded();

        CachedMetadata cached = metadataCache.get( filePath );

        if( cached != null )
        {
            long currentTime = System.currentTimeMillis();
            if( (currentTime - cached.timestamp) < CACHE_TTL_MS )
            {
                cacheHits.incrementAndGet();
                UtilSys.getLogger().log( ILogger.Level.INFO, "Using cached metadata for: " + filePath + " (hit #" + cacheHits.get() + ")" );
                return cached.metadata;
            }

            metadataCache.remove( filePath, cached );
        }

        cacheMisses.incrementAndGet();
        return null;
    }

    private static void cacheMetadata( String filePath, GitHubFileResponse metadata )
    {
        if( metadata != null )
        {
            CachedMetadata cached = new CachedMetadata();
            cached.metadata  = metadata;
            cached.timestamp = System.currentTimeMillis();
            metadataCache.put( filePath, cached );
        }
    }

    private static void cleanupCacheIfNeeded()
    {
        long currentTime = System.currentTimeMillis();
        if( currentTime - lastCacheCleanup <= CACHE_CLEANUP_INTERVAL_MS )
            return;

        synchronized( metadataCache )
        {
            if( currentTime - lastCacheCleanup <= CACHE_CLEANUP_INTERVAL_MS )
                return;

            int removedCount = 0;
            java.util.Iterator<java.util.Map.Entry<String, CachedMetadata>> iterator = metadataCache.entrySet().iterator();

            while( iterator.hasNext() )
            {
                CachedMetadata cached = iterator.next().getValue();
                if( cached != null && (currentTime - cached.timestamp) >= CACHE_TTL_MS )
                {
                    iterator.remove();
                    removedCount++;
                }
            }

            if( removedCount > 0 )
                UtilSys.getLogger().log( ILogger.Level.INFO, "Cache cleanup: removed " + removedCount + " expired entries" );

            lastCacheCleanup = currentTime;
        }
    }

    //------------------------------------------------------------------------//
    // INNER CLASSES
    //------------------------------------------------------------------------//

    /**
     * Response object for GitHub file metadata.
     */
    public static class GitHubFileResponse
    {
        public String path;
        public String sha;
        public String downloadUrl;
        public long   size;

        @Override
        public String toString()
        {
            return "GitHubFileResponse{path='" + path + "', sha='" + sha + "', size=" + size + "}";
        }
    }

    private static class CachedMetadata
    {
        public GitHubFileResponse metadata;
        public long timestamp;
    }
}
