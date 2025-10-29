package com.peyrona.mingle.updater;

import com.peyrona.mingle.lang.interfaces.ILogger;
import com.peyrona.mingle.lang.japi.UtilSys;
import java.io.File;

/**
 * File comparator using hybrid timestamp + SHA approach.
 *
 * @author Francisco José Morero Peyrona
 *
 * Official web site at: <a href="https://github.com/peyrona/mingle">https://github.com/peyrona/mingle</a>
 */
public class HybridFileComparator implements FileComparator
{
    @Override
    public ComparisonResult compare( ComparisonContext context )
    {
        String localHash = null;
        String remoteHash = context.githubInfo != null ? context.githubInfo.sha : null;
        boolean needsUpdate = false;
        String reason;
        
        if( ! context.localFile.exists() )
        {
            needsUpdate = true;
            reason = "missing file";
        }
        else if( context.githubInfo != null && context.githubInfo.lastModified > 0 )
        {
            // Compare timestamps - both should be in milliseconds since epoch
            long localLastModified = context.localFile.lastModified();
            long remoteLastModified = context.githubInfo.lastModified;
            
            // Add tolerance for clock skew (5 minutes)
            final long TIMESTAMP_TOLERANCE_MS = 5 * 60 * 1000;
            
            // Validate timestamp values before comparison
            if( localLastModified <= 0 )
            {
                UtilSys.getLogger().log( ILogger.Level.WARNING, "Invalid local timestamp for: " + context.relativePath );
                // Fall back to hash-only comparison
                localHash = HashCalculator.calculateHash( context.localFile, remoteHash );
                needsUpdate = (localHash == null || remoteHash == null || ! localHash.equals( remoteHash ));
                reason = needsUpdate ? "invalid local timestamp (SHA verified)" : "SHA verified (invalid local timestamp)";
            }
            else if( remoteLastModified <= 0 )
            {
                UtilSys.getLogger().log( ILogger.Level.WARNING, "Invalid remote timestamp for: " + context.relativePath );
                // Fall back to hash-only comparison
                localHash = HashCalculator.calculateHash( context.localFile, remoteHash );
                needsUpdate = (localHash == null || remoteHash == null || ! localHash.equals( remoteHash ));
                reason = needsUpdate ? "invalid remote timestamp (SHA verified)" : "SHA verified (invalid remote timestamp)";
            }
            else if( localLastModified < (remoteLastModified - TIMESTAMP_TOLERANCE_MS) )
            {
                // Remote file is significantly newer, verify with hash
                localHash = HashCalculator.calculateHash( context.localFile, remoteHash );
                if( localHash == null || remoteHash == null || ! localHash.equals( remoteHash ) )
                {
                    needsUpdate = true;
                    reason = "newer remote file (timestamp + SHA verified)";
                }
                else
                {
                    reason = "same content despite newer timestamp";
                }
            }
            else if( localLastModified > (remoteLastModified + TIMESTAMP_TOLERANCE_MS) )
            {
                // Local file is significantly newer, but still verify with hash for safety
                localHash = HashCalculator.calculateHash( context.localFile, remoteHash );
                if( localHash == null || remoteHash == null || ! localHash.equals( remoteHash ) )
                {
                    needsUpdate = true;
                    reason = "different content (local newer but SHA mismatch)";
                }
                else
                {
                    reason = "same content (local newer)";
                }
            }
            else
            {
                // Timestamps are within tolerance, assume up-to-date but verify with hash
                localHash = HashCalculator.calculateHash( context.localFile, remoteHash );
                if( localHash == null || remoteHash == null || ! localHash.equals( remoteHash ) )
                {
                    needsUpdate = true;
                    reason = "different content (timestamps similar but SHA mismatch)";
                }
                else
                {
                    reason = "same content (timestamps similar)";
                }
            }
        }
        else
        {
            // No timestamp info from GitHub or using catalog, fall back to hash only
            localHash = HashCalculator.calculateHash( context.localFile, remoteHash );
            if( localHash == null || remoteHash == null || ! localHash.equals( remoteHash ) )
            {
                needsUpdate = true;
                reason = context.fileEntry.expectedHash != null ? "SHA verification (using catalog)" : "SHA verification (no timestamp available)";
            }
            else
            {
                reason = context.fileEntry.expectedHash != null ? "SHA verified (using catalog)" : "SHA verified (no timestamp available)";
            }
        }
        
        return new ComparisonResult( needsUpdate, reason, localHash, remoteHash );
    }
}
