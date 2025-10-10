
package com.peyrona.mingle.controllers.modbus;

/**
 * Mingle MQTT clients are always asynchronous.
 *
 * @author Francisco José Morero Peyrona
 *
 * Official web site at: <a href="https://github.com/peyrona/mingle">https://github.com/peyrona/mingle</a>
 */
public interface IModbusClient
{
    public static interface Listener
    {
        void onMessage( Object msg );
        void onError( Exception exc );
    }

    void   open();
    void   close();
    Object read()                throws Exception;
    Object write( Object state ) throws Exception;
}