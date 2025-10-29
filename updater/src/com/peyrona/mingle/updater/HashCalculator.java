
package com.peyrona.mingle.updater;

import com.peyrona.mingle.lang.interfaces.ILogger;
import com.peyrona.mingle.lang.japi.UtilSys;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * Utility class for calculating SHA-1 and SHA-256 hashes of files.
 *
 * @author Francisco José Morero Peyrona
 *
 * Official web site at: <a href="https://github.com/peyrona/mingle">https://github.com/peyrona/mingle</a>
 */
public final class HashCalculator
{
    /**
     * Calculates SHA-1 hash of a file.
     *
     * @param file The file to calculate hash for
     * @return SHA-1 hash as hexadecimal string, or null if error occurs
     */
    public static String calculateSHA1(File file)
    {
        // Input validation
        if( file == null )
        {
            UtilSys.getLogger().log( ILogger.Level.WARNING, "File cannot be null" );
            return null;
        }
        
        if( ! file.exists() || ! file.isFile() )
        {
            UtilSys.getLogger().log( ILogger.Level.WARNING, "File does not exist or is not a regular file: " + file.getAbsolutePath() );
            return null;
        }
        
        try
        {
            return calculateHashWithAlgorithm( file, "SHA-1" );
        }
        catch( Exception e )
        {
            UtilSys.getLogger().log( ILogger.Level.WARNING, e, "Error calculating SHA-1 hash for file: " + file.getAbsolutePath() );
            return null;
        }
    }

    /**
     * Calculates SHA-256 hash of a file.
     *
     * @param file The file to calculate hash for
     * @return SHA-256 hash as hexadecimal string, or null if error occurs
     */
    public static String calculateSHA256(File file)
    {
        // Input validation
        if( file == null )
        {
            UtilSys.getLogger().log( ILogger.Level.WARNING, "File cannot be null" );
            return null;
        }
        
        if( ! file.exists() || ! file.isFile() )
        {
            UtilSys.getLogger().log( ILogger.Level.WARNING, "File does not exist or is not a regular file: " + file.getAbsolutePath() );
            return null;
        }
        
        try
        {
            return calculateHashWithAlgorithm( file, "SHA-256" );
        }
        catch( Exception e )
        {
            UtilSys.getLogger().log( ILogger.Level.WARNING, e, "Error calculating SHA-256 hash for file: " + file.getAbsolutePath() );
            return null;
        }
    }

    /**
     * Calculates hash of a file using the appropriate algorithm based on expected hash length.
     *
     * @param file The file to calculate hash for
     * @param expectedHash The expected hash to determine algorithm (40 chars = SHA-1, 64 chars = SHA-256)
     * @return Hash as hexadecimal string, or null if error occurs
     */
    public static String calculateHash(File file, String expectedHash)
    {
        if( file == null )
        {
            UtilSys.getLogger().log( ILogger.Level.WARNING, "File cannot be null" );
            return null;
        }
        
        if( expectedHash == null || expectedHash.trim().isEmpty() )
        {
            UtilSys.getLogger().log( ILogger.Level.WARNING, "Expected hash cannot be null or empty" );
            return null;
        }
        
        if( ! file.exists() || ! file.isFile() )
        {
            UtilSys.getLogger().log( ILogger.Level.WARNING, "File does not exist or is not a regular file: " + file.getAbsolutePath() );
            return null;
        }

        String algorithm;
        
        // Determine algorithm based on expected hash length
        if( isValidSHA1( expectedHash ) )
        {
            algorithm = "SHA-1";
        }
        else if( isValidSHA256( expectedHash ) )
        {
            algorithm = "SHA-256";
        }
        else
        {
            UtilSys.getLogger().log( ILogger.Level.WARNING, "Cannot determine hash algorithm from expected hash: " + expectedHash );
            return null;
        }

        try
        {
            return calculateHashWithAlgorithm( file, algorithm );
        }
        catch( Exception e )
        {
            UtilSys.getLogger().log( ILogger.Level.WARNING, e, "Error calculating hash for file: " + file.getAbsolutePath() );
            return null;
        }
    }

    private static String calculateHashWithAlgorithm(File file, String algorithm) throws IOException, NoSuchAlgorithmException
    {
        if( file == null )
        {
            throw new IllegalArgumentException( "File cannot be null" );
        }
        
        if( algorithm == null || algorithm.trim().isEmpty() )
        {
            throw new IllegalArgumentException( "Algorithm cannot be null or empty" );
        }
        
        if( ! file.exists() || ! file.isFile() )
        {
            throw new IOException( "File does not exist or is not a regular file: " + file.getAbsolutePath() );
        }

        MessageDigest digest = MessageDigest.getInstance( algorithm );

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
     * Validates if a string is a valid SHA-1 hash (40 hexadecimal characters).
     *
     * @param hash String to validate
     * @return true if valid SHA-1 hash, false otherwise
     */
    public static boolean isValidSHA1(String hash)
    {
        return hash != null && hash.length() == 40 && hash.matches( "[a-fA-F0-9]+" );
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