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
            // Compare timestamps
            long localLastModified = context.localFile.lastModified();
            
            if( localLastModified < context.githubInfo.lastModified )
            {
                // Remote file is newer, verify with hash
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
            else if( localLastModified > context.githubInfo.lastModified )
            {
                // Local file is newer, but still verify with hash for safety
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
                // Timestamps are equal, assume up-to-date
                reason = "same timestamp";
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
