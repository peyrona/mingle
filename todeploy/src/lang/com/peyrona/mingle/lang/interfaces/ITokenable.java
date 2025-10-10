
package com.peyrona.mingle.lang.interfaces;

/**
 *
 * @author francisco
 */
public interface ITokenable
{
    int    line();
    int    column();
    String text();
}