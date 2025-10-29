
package com.peyrona.mingle.controllers;

import com.peyrona.mingle.lang.interfaces.IController;
import java.util.Map;

/**
 * This Controller send device's value to terminal (standard output).
 * <p>
 * This Controller ignores FakeController flag.
 * <p>
 * See note at ControllerBase.
 *
 * @author Francisco José Morero Peyrona
 *
 * Official web site at: <a href="https://github.com/peyrona/mingle">https://github.com/peyrona/mingle</a>
 */
public final class   OutputStream
             extends ControllerBase
{
    private static final String KEY_AUTO_NL = "autofeed";    // Add New Line for every ::write(...)?

    @Override
    public synchronized void set( String deviceName, Map<String,Object> mapConfig, IController.Listener listener )
    {
        setName( deviceName );       // Must be 1st
        setListener( listener );     // Must be at begining: in case an error happens, Listener is needed
        setValid( true );            // Always valid because this driver has no config: mapConfig.size() == 0

        set( KEY_AUTO_NL, (Boolean) mapConfig.getOrDefault( "autofeed", Boolean.TRUE ) );
    }

    @Override
    public void read()
    {
        // Nothing to do.
        // DO NOT DO THIS --> sendIsNotReadable();
    }

    @Override
    public void write( Object value )     // This Controller ignores ::bFaked
    {
        if( (value == null) )             // value is null until device is initialized
            return;

        if( (Boolean) get( KEY_AUTO_NL ) ) System.out.println( value );
        else                               System.out.print(   value );

        sendReaded( value );
    }
}