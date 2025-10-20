package com.peyrona.mingle.updater;

import com.peyrona.mingle.lang.interfaces.ILogger;
import com.peyrona.mingle.lang.japi.UtilSys;
import java.io.File;

/**
 * File comparator using hash comparison only.
 *
 * @author Francisco José Morero Peyrona
 *
 * Official web site at: <a href="https://github.com/peyrona/mingle">https://github.com/peyrona/mingle</a>
 */
public class HashFileComparator implements FileComparator
{
    @Override
    public ComparisonResult compare( ComparisonContext context )
    {
        String remoteHash = context.githubInfo != null ? context.githubInfo.sha : null;
        
        if( remoteHash == null )
        {
            return new ComparisonResult( true, "missing remote hash", null, null );
        }
        
        String localHash = HashCalculator.calculateHash( context.localFile, remoteHash );
        boolean needsUpdate = (localHash == null || ! localHash.equals( remoteHash ));
        String reason = needsUpdate ? "hash mismatch" : "hashes match";
        
        return new ComparisonResult( needsUpdate, reason, localHash, remoteHash );
    }
}
