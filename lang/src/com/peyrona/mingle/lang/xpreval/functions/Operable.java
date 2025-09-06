
package com.peyrona.mingle.lang.xpreval.functions;

/**
 * date and time classes (in this module) implement this interface.
 *
 * @author Francisco José Morero Peyrona
 *
 * Official web site at: <a href="https://mingle.peyrona.com">https://mingle.peyrona.com</a>
 * @param <T>
 */
public interface Operable<T>
{
    /**
     * Moves (shifts) in the future if number is positive and in the past if number is negative.
     *
     * @param n Amount to move.
     * @return Same instance after being shifted.
     */
    T move( Object n );

    /**
     * Returns the duration (in seconds for time and in days for dates) from this time
     * (inclusive) and passed time (exclusive).
     *
     * @param t Another time.
     * @return The duration in seconds (in seconds for time and in days for dates) from this time
     *         (inclusive) and passed time (exclusive).
     */
    int duration( T  t );
}