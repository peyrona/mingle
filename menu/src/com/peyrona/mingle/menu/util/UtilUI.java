
package com.peyrona.mingle.menu.util;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

/**
 *
 * @author francisco
 */
public class UtilUI
{
    // Keep the reader static so we don't buffer-and-lose bytes between calls.
    // We use System.in directly. Closing this reader would close System.in, so we usually keep it open for the app's lifecycle.
    private static final BufferedReader reader = new BufferedReader( new InputStreamReader( System.in, StandardCharsets.UTF_8 ) );

    //------------------------------------------------------------------------//
    // MISCE METHODS

    /**
     * Fills by the left sidfe of the string with indicated char until the
     * string has indicated length. If passed string is null "" is returned.
     *
     * @param string
     * @param padder
     * @param length
     * @return
     */
    public static String leftPad( String string, final char padder, final int length )
    {
        if( string == null )
            string = "";

        if( string.length() < length )
            return fill( padder, length - string.length() ) + string;

        return string;
    }

    /**
     * Fills by the right sidfe of the string with indicated char until the
     * string has indicated length. If passed string is null "" is returned.
     *
     * @param string
     * @param padder
     * @param length
     * @return
     */
    public static String rightPad( String string, final char padder, final int length )
    {
        if( string == null )
            string = "";

        if( string.length() < length )
            return string + fill( padder, length - string.length() );

        return string;
    }

    /**
     * Returns a String with a length of passed parameter and composed only by chars of passed pattern.
     * @param pattern
     * @param length
     * @return A String with a length of passed parameter and composed only by chars of passed pattern.
     */
    public static String fill( final char pattern, final int length )
    {
        if( length <= 0 )
            return "";

        final StringBuilder sb = new StringBuilder( length );

        for( int n = 0; n < length; n++ )
            sb.append( pattern );

        return sb.toString();
    }

    public static String capitalize( String s )
    {
        char[] ac = s.trim().toLowerCase().toCharArray();

        ac[0] = Character.toUpperCase( ac[0] );

        return String.valueOf( ac );
    }

    /**
     * Returns true if the string contains any of passed strings (case is ignored).
     *
     * @param obj Object (converted to String) to search into.
     * @param strs 1 or more strings to check.
     * @return true if the string contains any of passed strings (case is ignored).
     */
    public static boolean contains( Object obj, String... strs )
    {
        if( obj == null )
            return false;

        if( strs == null || strs.length == 0 )
            return false;

        String str = obj.toString().toLowerCase();

        for( String s : strs )
        {
            if( s == null )
                return false;

            if( s.isEmpty() )
                return true;     // Empty string is always contained

            if( str.contains( s.toLowerCase() ) )
                return true;
        }

        return false;
    }

    //------------------------------------------------------------------------//
    // METHODS RELATED WITH USER INPUTS

    public static String readInput( String prompt )
    {
        flushInputBuffer();
        System.out.print( prompt ); // No newline, so user types next to it
        System.out.flush(); // Ensure prompt appears immediately

        try
        {
            String line = reader.readLine();

            if( line == null )
                return "";

            return line.trim().toLowerCase();
        }
        catch( IOException e )
        {
            System.err.println( "Input error: " + e.getMessage() );
            return "";
        }
    }

    public static boolean confirm( String prompt )
    {
        String input = readInput( prompt + " [y/N]: " );
        return input.equals( "y" );
    }

    public static void pause()
    {
        System.out.print( "Press [Enter] to continue..." ); // No println() so cursor is at the end of the msg
        try
        {
            System.in.read();
        }
        catch( IOException e )
        {
            // Ignore
        }
    }

    public static String readFileName( String prompt )
    {
        while( true )
        {
            String input = readInput( prompt );

            if( input.isEmpty() )
            {
                return "";
            }
            if( input.contains( "*" ) || input.contains( "?" ) )
            {
                if( isValidWildcardPattern( input ) )
                    return input;
                else
                    System.out.println( "Invalid use of wildcards. Enter a valid file or press [Enter] to exit." );
            }
            else
            {
                File file = new File( input );

                if( file.exists() )
                    return input;

                System.out.println( "File does not exist. Enter a valid file or press [Enter] to exit." );
            }
        }
    }

    public static void clearScreen()
    {
        try
        {
            if( UtilSys.sOS.contains( "windows" ) )
            {
                new ProcessBuilder( "cmd", "/c", "cls" ).inheritIO().start().waitFor();
            }
            else
            {
                System.out.print( "\033[H\033[2J" );
                System.out.flush();
            }
        }
        catch( IOException | InterruptedException e )
        {
            // Fallback: print newlines
            for( int i = 0; i < 50; i++ )
            {
                System.out.println();
            }
        }
    }

    //------------------------------------------------------------------------//
    // PRIVATE SCOPE

    /**
     * Clears any pending keystrokes from the input buffer.
     * This prevents "type-ahead" or accidental key presses from
     * affecting the next input request.
     */
    private static void flushInputBuffer()
    {
        try
        {
            while( reader.ready() )
                reader.read();
        }
        catch( IOException e )
        {
            // In rare cases where checking readiness fails, we log but continue to ensure the application doesn't crash.
            System.err.println( "Warning: Failed to flush input buffer." );
        }
    }

    private static boolean isValidWildcardPattern( String pattern )
    {
        // Allow alphanumeric, spaces, and common file path characters plus wildcards
        return UtilSys.isNotEmpty( pattern ) &&
               pattern.matches( "^[a-zA-Z0-9_\\-./\\\\*?\\s]+$" );
    }

    //------------------------------------------------------------------------//
    // FILE INFO METHODS

    /**
     * Formats a file size in bytes to a human-readable string.
     * Examples: "1.2 KB", "45 MB", "128 bytes"
     *
     * @param bytes The file size in bytes.
     * @return Human-readable file size string.
     */
    public static String formatFileSize( long bytes )
    {
        if( bytes < 0 )
            return "0 bytes";

        if( bytes < 1024 )
            return bytes + " bytes";

        if( bytes < 1024 * 1024 )
            return String.format( "%.1f KB", bytes / 1024.0 );

        if( bytes < 1024 * 1024 * 1024 )
            return String.format( "%.1f MB", bytes / (1024.0 * 1024) );

        return String.format( "%.1f GB", bytes / (1024.0 * 1024 * 1024) );
    }

    /**
     * Formats a timestamp to a relative time string.
     * Examples: "just now", "5 min ago", "2 hours ago", "yesterday", "3 days ago"
     *
     * @param timestamp The timestamp in milliseconds.
     * @return Human-readable relative time string.
     */
    public static String formatTimeAgo( long timestamp )
    {
        long now  = System.currentTimeMillis();
        long diff = now - timestamp;

        if( diff < 0 )
            return "in the future";

        long seconds = diff / 1000;
        long minutes = seconds / 60;
        long hours   = minutes / 60;
        long days    = hours / 24;

        if( seconds < 60 )
            return "just now";

        if( minutes < 60 )
            return minutes == 1 ? "1 min ago" : minutes + " min ago";

        if( hours < 24 )
            return hours == 1 ? "1 hour ago" : hours + " hours ago";

        if( days == 1 )
            return "yesterday";

        if( days < 7 )
            return days + " days ago";

        // For older files, show the date
        java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat( "MMM d" );
        return sdf.format( new java.util.Date( timestamp ) );
    }
}