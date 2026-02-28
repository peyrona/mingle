
package com.peyrona.mingle.lang.messages;

/**
 * Requests to the IDriver (which request to IController) to read current device's value.<br>
 * <br>
 * This is used just when a device is created to find out its value.
 *
 * @author Francisco José Morero Peyrona
 *
 * Official web site at: <a href="https://github.com/peyrona/mingle">https://github.com/peyrona/mingle</a>
 */
public class MsgReadDevice extends Message
{
    public MsgReadDevice( String device )
    {
        this( device, true );
    }

    public MsgReadDevice( String device, boolean isOwn )
    {
        super( device, null, isOwn );
    }
}