package com.peyrona.mingle.updater;

import java.io.File;

/**
 * Strategy interface for comparing local and remote files.
 *
 * @author Francisco José Morero Peyrona
 *
 * Official web site at: <a href="https://github.com/peyrona/mingle">https://github.com/peyrona/mingle</a>
 */
public interface FileComparator
{
    /**
     * Compares a local file with remote information.
     *
     * @param context Comparison context containing file information
     * @return Comparison result
     */
    ComparisonResult compare( ComparisonContext context );

    //------------------------------------------------------------------------//
    // INNER CLASSES
    //------------------------------------------------------------------------//

    /**
     * Context for file comparison.
     */
    class ComparisonContext
    {
        public final String relativePath;
        public final FileDiscoveryStrategy.FileEntry fileEntry;
        public final File localFile;
        public final GitHubApiClient.GitHubFileResponse githubInfo;

        public ComparisonContext( String relativePath, FileDiscoveryStrategy.FileEntry fileEntry,
                                  File localFile, GitHubApiClient.GitHubFileResponse githubInfo )
        {
            this.relativePath = relativePath;
            this.fileEntry = fileEntry;
            this.localFile = localFile;
            this.githubInfo = githubInfo;
        }
    }

    /**
     * Result of file comparison.
     */
    class ComparisonResult
    {
        public final boolean needsUpdate;
        public final String reason;
        public final String localHash;
        public final String remoteHash;

        public ComparisonResult( boolean needsUpdate, String reason, String localHash, String remoteHash )
        {
            this.needsUpdate = needsUpdate;
            this.reason = reason;
            this.localHash = localHash;
            this.remoteHash = remoteHash;
        }
    }
}
