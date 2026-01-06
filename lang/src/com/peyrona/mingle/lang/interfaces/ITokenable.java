
package com.peyrona.mingle.lang.interfaces;

/**
 * Interface for objects that represent tokens with positional information.
 *
 * @author francisco
 */
public interface ITokenable
{
    /**
     * Returns the line number where this token is located in the source.
     *
     * @return The line number (1-based).
     */
    int    line();

    /**
     * Returns the column number where this token is located in the source.
     *
     * @return The column number (1-based).
     */
    int    column();

    /**
     * Returns the text content of this token.
     *
     * @return The token's text.
     */
    String text();
}