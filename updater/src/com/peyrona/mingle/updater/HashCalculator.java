
package com.peyrona.mingle.updater;

import com.peyrona.mingle.lang.interfaces.ILogger;
import com.peyrona.mingle.lang.japi.UtilSys;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * Utility class for calculating SHA-256 hashes of files.
 *
 * @author Francisco José Morero Peyrona
 *
 * Official web site at: <a href="https://github.com/peyrona/mingle">https://github.com/peyrona/mingle</a>
 */
public class HashCalculator
{
    /**
     * Calculates SHA-256 hash of a file.
     *
     * @param file The file to calculate hash for
     * @return SHA-256 hash as hexadecimal string, or null if error occurs
     */
    public static String calculateSHA256(File file)
    {
        if( ! file.exists() || ! file.isFile() )
        {
            UtilSys.getLogger().log( ILogger.Level.WARNING, "File does not exist or is not a regular file: " + file.getAbsolutePath() );
            return null;
        }

        try
        {
            MessageDigest digest = MessageDigest.getInstance( "SHA-256" );

            try( java.io.InputStream inputStream = Files.newInputStream( file.toPath() ) )
            {
                byte[] buffer = new byte[65536]; // 64KB buffer for better performance
                int bytesRead;

                while( (bytesRead = inputStream.read( buffer )) != -1 )
                {
                    digest.update( buffer, 0, bytesRead );
                }
            }

            byte[] hashBytes = digest.digest();
            return bytesToHex( hashBytes );

        }
        catch( NoSuchAlgorithmException e )
        {
            UtilSys.getLogger().log( ILogger.Level.SEVERE, e, "SHA-256 algorithm not available" );
            return null;
        }
        catch( IOException e )
        {
            UtilSys.getLogger().log( ILogger.Level.WARNING, e, "Error reading file: " + file.getAbsolutePath() );
            return null;
        }
    }

    /**
     * Converts byte array to hexadecimal string.
     *
     * @param bytes Byte array to convert
     * @return Hexadecimal string representation
     */
    private static String bytesToHex(byte[] bytes)
    {
        StringBuilder hexString = new StringBuilder();

        for( byte b : bytes )
        {
            String hex = Integer.toHexString( 0xff & b );

            if( hex.length() == 1 )
                hexString.append( '0' );

            hexString.append( hex );
        }

        return hexString.toString();
    }

    /**
     * Validates if a string is a valid SHA-256 hash (64 hexadecimal characters).
     *
     * @param hash String to validate
     * @return true if valid SHA-256 hash, false otherwise
     */
    public static boolean isValidSHA256(String hash)
    {
        return hash != null && hash.length() == 64 && hash.matches( "[a-fA-F0-9]+" );
    }
}