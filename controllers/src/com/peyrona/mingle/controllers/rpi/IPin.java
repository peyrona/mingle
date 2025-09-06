
package com.peyrona.mingle.controllers.rpi;

/**
 *
 * @author Francisco José Morero Peyrona
 *
 * Official web site at: <a href="https://mingle.peyrona.com">https://mingle.peyrona.com</a>
 */
public interface IPin
{
    Object  read();                   // Boolean when isDigital() and Integer when isAnalog()
    void    write( boolean value );
    void    write( int value );
    void    cleanup();
    boolean isInput();
    boolean isDigital();
}