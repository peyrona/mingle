package com.peyrona.mingle.updater;

import com.peyrona.mingle.lang.interfaces.ILogger;
import com.peyrona.mingle.lang.japi.UtilSys;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/**
 * Client for interacting with GitHub API to fetch file information and content.
 *
 * @author Francisco José Morero Peyrona
 *
 * Official web site at: <a href="https://github.com/peyrona/mingle">https://github.com/peyrona/mingle</a>
 */
public final class GitHubApiClient
{
    // GitHub repository constants
    private static final String GITHUB_API_BASE = "https://api.github.com";
    private static final String GITHUB_RAW_BASE = "https://raw.githubusercontent.com";
    private static final String REPO_OWNER      = "peyrona";
    private static final String REPO_NAME       = "mingle";
    private static final String REPO_BRANCH     = "main";
    private static final String REPO_PATH       = "todeploy";

    // User agent for anonymous access
    private static final String USER_AGENT = "Mingle-Updater/1.0";

    // Rate limiting constants
    private static final long DEFAULT_DELAY_MS = 2000; // 2 seconds between API calls
    private static final long MAX_BACKOFF_MS   = 32000; // Maximum backoff time
    private static final int  MAX_RETRIES      = 5; // Maximum retry attempts

    // Rate limiting state - made instance-based for better thread safety
    private volatile long lastApiCallTime = 0;
    private final Object rateLimitLock = new Object();

    // Simple cache for file metadata (5 minute TTL)
    private static final java.util.Map<String, CachedMetadata> metadataCache = new java.util.concurrent.ConcurrentHashMap<>();
    private static final long CACHE_TTL_MS = 5 * 60 * 1000; // 5 minutes

    //------------------------------------------------------------------------//

    /**
     * Logs rate limit information from GitHub API response headers.
     */
    private static void logRateLimitInfo( HttpURLConnection connection )
    {
        String limit = connection.getHeaderField( "X-RateLimit-Limit" );
        String remaining = connection.getHeaderField( "X-RateLimit-Remaining" );
        String reset = connection.getHeaderField( "X-RateLimit-Reset" );

        if( limit != null && remaining != null )
        {
            String logMessage = String.format( "Rate limit: %s/%s requests remaining", remaining, limit );

            // Add reset time if available
            if( reset != null )
            {
                try
                {
                    long resetTimestamp = Long.parseLong( reset );
                    String resetTime = java.time.Instant.ofEpochSecond( resetTimestamp )
                                                .atZone( java.time.ZoneOffset.UTC )
                                                .format( java.time.format.DateTimeFormatter.ofPattern( "yyyy-MM-dd HH:mm:ss" ) ) +" UTC";
                    logMessage += String.format( ". Resets at: %s", resetTime );
                }
                catch( NumberFormatException e )
                {
                    // If reset timestamp is invalid, just ignore it
                    logMessage += ". Reset time unavailable";
                }
            }

            UtilSys.getLogger().log( ILogger.Level.INFO, logMessage );
        }
    }

    /**
     * Applies rate limiting by waiting if necessary between API calls.
     */
    private void applyRateLimiting()
    {
        synchronized( rateLimitLock )
        {
            long currentTime = System.currentTimeMillis();
            long timeSinceLastCall = currentTime - lastApiCallTime;

            if( timeSinceLastCall < DEFAULT_DELAY_MS )
            {
                try
                {
                    long sleepTime = DEFAULT_DELAY_MS - timeSinceLastCall;
                    Thread.sleep( sleepTime );
                }
                catch( InterruptedException e )
                {
                    Thread.currentThread().interrupt();
                }
            }

            lastApiCallTime = System.currentTimeMillis();
        }
    }

    /**
     * Handles rate limit and authentication responses with exponential backoff.
     *
     * @param responseCode HTTP response code
     * @param connection HTTP connection to check rate limit headers
     * @param filePath File path for logging
     * @return true if should retry, false if should give up
     */
    private static boolean handleRateLimit(int responseCode, HttpURLConnection connection, String filePath)
    {
        if( responseCode == 429 )
        {
            UtilSys.getLogger().log( ILogger.Level.WARNING, "Rate limit hit for: " + filePath );
            return true; // Should retry
        }
        else if( responseCode == 403 )
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
                        long currentTime = System.currentTimeMillis() / 1000;
                        long waitTime = resetTimestamp - currentTime;
                        
                        if( waitTime > 0 )
                        {
                            UtilSys.getLogger().log( ILogger.Level.WARNING, 
                                String.format( "Rate limit exceeded for: %s. Reset in %d seconds", filePath, waitTime ) );
                        }
                        else
                        {
                            UtilSys.getLogger().log( ILogger.Level.WARNING, "Rate limit exceeded for: " + filePath + " (should reset soon)" );
                        }
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
                return true; // Should retry after backoff
            }
            else
            {
                UtilSys.getLogger().log( ILogger.Level.WARNING, "Access forbidden for: " + filePath + " (repository may be private)" );
                return false; // Don't retry real access issues
            }
        }
        return false;
    }

    /**
     * Executes HTTP request with retry logic for rate limiting.
     *
     * @param url URL to connect to
     * @param filePath File path for logging
     * @return response code, or -1 if all retries failed
     */
    private static int executeWithRetry(URL url, String filePath) throws IOException
    {
        int retryCount = 0;
        long backoffTime = 2000; // Start with 2 seconds

        while( retryCount <= MAX_RETRIES )
        {
            HttpURLConnection connection = null;

            try
            {
                connection = (HttpURLConnection) url.openConnection();
                connection.setRequestMethod( "GET" );
                connection.setRequestProperty( "Accept", "application/vnd.github.v3+json" );
                connection.setRequestProperty( "User-Agent", USER_AGENT );

                connection.setConnectTimeout( 10000 );
                connection.setReadTimeout( 30000 );

                int responseCode = connection.getResponseCode();

                if( responseCode == 200 )
                {
                    logRateLimitInfo( connection );
                    return responseCode;
                }

                if( ! handleRateLimit( responseCode, connection, filePath ) )
                {
                    return responseCode; // Not a rate limit issue, return immediately
                }

                if( retryCount == MAX_RETRIES )
                {
                    UtilSys.getLogger().log( ILogger.Level.SEVERE, "Max retries exceeded for: " + filePath );
                    return responseCode;
                }

                // Exponential backoff with jitter to avoid thundering herd
                long jitter = (long) (Math.random() * 1000); // Add up to 1 second jitter
                long totalWaitTime = backoffTime + jitter;
                
                UtilSys.getLogger().log( ILogger.Level.INFO, 
                    String.format( "Rate limited, waiting %dms before retry (%d/%d) for: %s", 
                        totalWaitTime, retryCount + 1, MAX_RETRIES, filePath ) );
                Thread.sleep( totalWaitTime );
                
                backoffTime = Math.min( backoffTime * 2, MAX_BACKOFF_MS );
                retryCount++;

                // Connection will be closed in finally block, new connection created on next iteration
            }
            catch( InterruptedException e )
            {
                Thread.currentThread().interrupt();
                return -1;
            }
            finally
            {
                if( connection != null )
                    connection.disconnect();
            }
        }

        return -1;
    }

    /**
     * Gets cached metadata if available and not expired.
     *
     * @param filePath File path to check in cache
     * @return Cached metadata or null if not available/expired
     */
    private static GitHubFileResponse getCachedMetadata(String filePath)
    {
        CachedMetadata cached = metadataCache.get( filePath );

        if( cached != null && (System.currentTimeMillis() - cached.timestamp) < CACHE_TTL_MS )
        {
            UtilSys.getLogger().log( ILogger.Level.INFO, "Using cached metadata for: " + filePath );
            return cached.metadata;
        }

        // Remove expired entry
        if( cached != null )
            metadataCache.remove( filePath );

        return null;
    }

    /**
     * Caches file metadata with timestamp.
     *
     * @param filePath File path
     * @param metadata Metadata to cache
     */
    private static void cacheMetadata(String filePath, GitHubFileResponse metadata)
    {
        if( metadata != null )
        {
            CachedMetadata cached = new CachedMetadata();
            cached.metadata = metadata;
            cached.timestamp = System.currentTimeMillis();
            metadataCache.put( filePath, cached );
        }
    }

    // Instance for rate limiting
    private static final GitHubApiClient rateLimitInstance = new GitHubApiClient();

    /**
     * Gets file metadata from GitHub API including SHA hash.
     * Uses caching to avoid repeated requests for the same file.
     *
     * @param filePath Relative path within the todeploy directory
     * @return GitHubFileResponse containing file metadata, or null if not found or error occurs
     */
    public static GitHubFileResponse getFileMetadata(String filePath)
    {
        // Check cache first
        GitHubFileResponse cached = getCachedMetadata( filePath );

        if( cached != null )
            return cached;

        // Apply rate limiting before making API call
        rateLimitInstance.applyRateLimiting();

        HttpURLConnection connection = null;

        try
        {
            String apiUrl = String.format( "%s/repos/%s/%s/contents/%s/%s", GITHUB_API_BASE, REPO_OWNER, REPO_NAME, REPO_PATH, filePath );

            URL url = new URL( apiUrl );
            int responseCode = executeWithRetry( url, filePath );

            if( responseCode != 200 )
            {
                switch( responseCode )
                {
                    case 404:
                        UtilSys.getLogger().log( ILogger.Level.INFO, "File not found on GitHub: " + filePath );
                        break;
                    case 403:
                        UtilSys.getLogger().log( ILogger.Level.WARNING, "Access forbidden for GitHub API: " + filePath );
                        break;
                    default:
                        UtilSys.getLogger().log( ILogger.Level.WARNING, "GitHub API returned status " + responseCode + " for: " + filePath );
                        break;
                }
                return null;
            }

            // Create a new connection for reading the response
            connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod( "GET" );
            connection.setRequestProperty( "Accept", "application/vnd.github.v3+json" );
            connection.setRequestProperty( "User-Agent", USER_AGENT );

            connection.setConnectTimeout( 10000 );
            connection.setReadTimeout( 30000 );

            try( BufferedReader reader = new BufferedReader( new InputStreamReader( connection.getInputStream() ) ) )
            {
                StringBuilder response = new StringBuilder();
                String line;

                while( (line = reader.readLine()) != null )
                    response.append( line );

                GitHubFileResponse metadata = parseGitHubFileResponse( response.toString(), filePath );

                // Cache the result
                cacheMetadata( filePath, metadata );

                return metadata;
            }

        }
        catch( IOException e )
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
     * Downloads file content from GitHub.
     *
     * @param filePath   Relative path within the todeploy directory
     * @param targetPath Local path where to save the file
     * @return true if download successful, false otherwise
     */
    public static boolean downloadFile(String filePath, Path targetPath)
    {
        // Apply rate limiting before making download call
        rateLimitInstance.applyRateLimiting();

        HttpURLConnection connection = null;

        try
        {
            // Use proper URI building to handle path encoding correctly
            String pathToEncode = REPO_PATH + "/" + filePath;
            String encodedPath = java.net.URLEncoder.encode( pathToEncode, "UTF-8" )
                    .replace( "+", "%20" )
                    .replace( "%2F", "/" ); // Preserve forward slashes
            String rawUrl = String.format( "%s/%s/%s/%s/%s", GITHUB_RAW_BASE, REPO_OWNER, REPO_NAME, REPO_BRANCH, encodedPath );

            URL url = new URL( rawUrl );
            int responseCode = executeWithRetry( url, filePath );

            if( responseCode != 200 )
            {
                UtilSys.getLogger().log( ILogger.Level.WARNING, "Failed to download file, status " + responseCode + ": " + filePath );
                return false;
            }

            // Create a new connection for reading the response
            connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod( "GET" );
            connection.setRequestProperty( "User-Agent", USER_AGENT );

            connection.setConnectTimeout( 10000 );
            connection.setReadTimeout( 60000 );

            // Create parent directories if they don't exist
            Files.createDirectories( targetPath.getParent() );

            // Download file
            Files.copy( connection.getInputStream(), targetPath, StandardCopyOption.REPLACE_EXISTING );
            UtilSys.getLogger().log( ILogger.Level.INFO, "Successfully downloaded: " + filePath );
            return true;

        }
        catch( IOException e )
        {
            UtilSys.getLogger().log( ILogger.Level.WARNING, e, "Error downloading file: " + filePath );
            return false;
        }
        finally
        {
            if( connection != null )
            {
                connection.disconnect();
            }
        }
    }

    /**
     * Downloads file content from GitHub repository root.
     *
     * @param filePath   Relative path from repository root
     * @param targetPath Local path where to save the file
     * @return true if download successful, false otherwise
     */
    public static boolean downloadFileFromRoot(String filePath, Path targetPath)
    {
        // Apply rate limiting before making download call
        rateLimitInstance.applyRateLimiting();

        HttpURLConnection connection = null;
        try
        {
            // Use proper URI building to handle path encoding correctly
            String encodedPath = java.net.URLEncoder.encode( filePath, "UTF-8" )
                    .replace( "+", "%20" )
                    .replace( "%2F", "/" ); // Preserve forward slashes
            String rawUrl = String.format( "%s/%s/%s/%s/%s", GITHUB_RAW_BASE, REPO_OWNER, REPO_NAME, REPO_BRANCH, encodedPath );

            URL url = new URL( rawUrl );
            int responseCode = executeWithRetry( url, filePath );

            if( responseCode != 200 )
            {
                UtilSys.getLogger().log( ILogger.Level.WARNING, "Failed to download file from root, status " + responseCode + ": " + filePath );
                return false;
            }

            // Create a new connection for reading the response
            connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod( "GET" );
            connection.setRequestProperty( "User-Agent", USER_AGENT );

            connection.setConnectTimeout( 10000 );
            connection.setReadTimeout( 60000 );

            // Create parent directories if they don't exist
            Files.createDirectories( targetPath.getParent() );

            // Download file
            Files.copy( connection.getInputStream(), targetPath, StandardCopyOption.REPLACE_EXISTING );
            UtilSys.getLogger().log( ILogger.Level.INFO, "Successfully downloaded from root: " + filePath );

            return true;
        }
        catch( IOException e )
        {
            UtilSys.getLogger().log( ILogger.Level.WARNING, e, "Error downloading file from root: " + filePath );
            return false;
        }
        finally
        {
            if( connection != null )
            {
                connection.disconnect();
            }
        }
    }

    /**
     * Parses GitHub API response for file metadata.
     *
     * @param jsonResponse JSON response from GitHub API
     * @param filePath     File path for logging
     * @return GitHubFileResponse object
     */
    private static GitHubFileResponse parseGitHubFileResponse(String jsonResponse, String filePath)
    {
        try
        {
            // Simple JSON parsing (avoiding external dependencies)
            GitHubFileResponse response = new GitHubFileResponse();
            response.path = filePath;

            // Extract SHA hash
            String shaPattern = "\"sha\":\"([^\"]+)\"";
            java.util.regex.Pattern shaRegex = java.util.regex.Pattern.compile( shaPattern );
            java.util.regex.Matcher shaMatcher = shaRegex.matcher( jsonResponse );
            if( shaMatcher.find() )
            {
                response.sha = shaMatcher.group( 1 );
            }

            // Extract download URL
            String urlPattern = "\"download_url\":\"([^\"]+)\"";
            java.util.regex.Pattern urlRegex = java.util.regex.Pattern.compile( urlPattern );
            java.util.regex.Matcher urlMatcher = urlRegex.matcher( jsonResponse );

            if( urlMatcher.find() )
                response.downloadUrl = urlMatcher.group( 1 ).replace( "\\/", "/" );

            // Extract file size
            String sizePattern = "\"size\":(\\d+)";
            java.util.regex.Pattern sizeRegex = java.util.regex.Pattern.compile( sizePattern );
            java.util.regex.Matcher sizeMatcher = sizeRegex.matcher( jsonResponse );

            if( sizeMatcher.find() )
                response.size = Long.parseLong( sizeMatcher.group( 1 ) );

            // Extract last modified date
            String datePattern = "\"last_modified\":\"([^\"]+)\"";
            java.util.regex.Pattern dateRegex = java.util.regex.Pattern.compile( datePattern );
            java.util.regex.Matcher dateMatcher = dateRegex.matcher( jsonResponse );

            if( dateMatcher.find() )
            {
                String dateStr = "???";

                try
                {
                    dateStr = dateMatcher.group( 1 );
                    // Parse ISO 8601 date format: "2023-10-17T12:34:56Z"
                    java.time.Instant instant = java.time.Instant.parse( dateStr );
                    response.lastModified = instant.toEpochMilli();
                }
                catch( Exception e )
                {
                    UtilSys.getLogger().log( ILogger.Level.WARNING, "Failed to parse last_modified date '" + dateStr + "' for: " + filePath + " - " + e.getMessage() );
                    response.lastModified = 0;
                }
            }
            else
            {
                // last_modified field not found in response
                response.lastModified = 0;
            }

            return response;

        }
        catch( Exception e )
        {
            UtilSys.getLogger().log( ILogger.Level.WARNING, e, "Error parsing GitHub API response for: " + filePath );
            return null;
        }
    }

    //------------------------------------------------------------------------//
    // INNER CLASS
    //------------------------------------------------------------------------//

    /**
     * Response object for GitHub file metadata.
     */
    public static class GitHubFileResponse
    {
        public String path;
        public String sha;
        public String downloadUrl;
        public long size;
        public long lastModified;  // Timestamp in milliseconds

        @Override
        public String toString()
        {
            return "GitHubFileResponse{path='" + path + "', sha='" + sha + "', size=" + size + ", lastModified=" + lastModified + "}";
        }
    }

    /**
     * Cached metadata entry with timestamp.
     */
    private static class CachedMetadata
    {
        public GitHubFileResponse metadata;
        public long timestamp; // When this entry was cached
    }
}
