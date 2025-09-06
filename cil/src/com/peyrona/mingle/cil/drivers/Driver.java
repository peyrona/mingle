
package com.peyrona.mingle.cil.drivers;

import com.peyrona.mingle.cil.Command;
import com.peyrona.mingle.lang.MingleException;
import com.peyrona.mingle.lang.interfaces.IController;
import com.peyrona.mingle.lang.interfaces.ILogger;
import com.peyrona.mingle.lang.interfaces.commands.IDevice;
import com.peyrona.mingle.lang.interfaces.commands.IDriver;
import com.peyrona.mingle.lang.interfaces.commands.IScript;
import com.peyrona.mingle.lang.interfaces.exen.IRuntime;
import com.peyrona.mingle.lang.messages.MsgDeviceReaded;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * A Driver is an intermediate between devices and Controllers.
 * <p>
 * At ExEn there will be only one instance of Driver class per each driver (drivers
 * are identified by their names). This means that there will also only one instance
 * of every type of Controller.
 *
 * @author Francisco José Morero Peyrona
 *
 * Official web site at: <a href="https://mingle.peyrona.com">https://mingle.peyrona.com</a>
 */
public final class      Driver
             extends    Command
             implements IDriver
{
    private final String scriptName;
    private final IController.Listener listener;
    private final Map<String,IController> map = new ConcurrentHashMap<>();    // Controllers must be accessed only by the Driver class

    //------------------------------------------------------------------------//
    // PACKAGE SCOPE CONSTRUCTOR

    /**
     * Constructor.
     *
     * @param name
     * @param script
     */
    Driver( String name, String script )
    {
        super( name );        // A DRIVER can be created from a native SCRIPT embeded in Une

        assert ! isStarted();

        scriptName = script;

        // We need to add the Driver to the Controller as a listener, so the Controller
        // will inform the driver after every change that happens in the real world (this
        // mechanism allows an async communication between Drivers and Controllers).

        listener = new IController.Listener()
                    {
                        @Override
                        public void onChanged( String name, Object value )
                        {
                                 if( name  == null )  getRuntime().log( ILogger.Level.SEVERE, "Error: device name is 'null'" );
                            else if( value == null )  getRuntime().log( ILogger.Level.SEVERE, "Error: 'null' notified as new value for '"+ name +'\'' );
                            else                      getRuntime().bus().post( new MsgDeviceReaded( name, value ) );
                        }

                        @Override
                        public void onError( ILogger.Level level, String message, String device )
                        {
                            getRuntime().log( level, message + (device == null ? "" : " Device; '"+ device +'\'') );
                        }
                    };
    }

    //------------------------------------------------------------------------//

    @Override
    public void start( IRuntime rt )
    {
        assert ! isStarted();

        super.start( rt );

        for( Iterator<String> itera = map.keySet().iterator(); itera.hasNext(); )
        {
            IDevice     device = (IDevice) rt.get( itera.next() );
            IController cntrlr = setController( device );

            if( cntrlr == null )   // Invalid entries...
                itera.remove();    // have to be removed
        }

        map.values().forEach( (IController ctrlr) -> ctrlr.start( rt ) );
    }

    @Override
    public void stop()
    {
        assert isStarted();    // Only for testing under development

        if( isStarted() )
        {
            map.values().forEach( (IController ctrlr) -> ctrlr.stop() );
            map.clear();

            super.stop();
        }
    }

    @Override
    public void add( IDevice device )
    {
        assert ! has( device.name() );

        if( isStarted() )
            setController( device );
        else
            map.put( device.name(), new NullController() );
    }

    @Override
    public boolean remove( IDevice device )
    {
        if( ! has( device.name() ) )
        {
            notFound( device.name(), null );
            return false;
        }

        device.stop();
     // map.get( device.name() ).stop(); --> can not do this because other devices could be using the same Controller
        map.remove( device.name() );

        return true;
    }

    @Override
    public String getScriptName()
    {
        return scriptName;
    }

    @Override
    public boolean isEmpty()
    {
        return map.isEmpty();
    }

    @Override
    public boolean has( String devName )
    {
        return map.containsKey( devName );
    }

    @Override
    public void read( String devName )
    {
        try
        {
            map.get( devName ).read();
        }
        catch( Exception exc )
        {
            notFound( devName, exc );
        }
    }

    @Override
    public void write( String devName, Object newValue )
    {
        try
        {
            map.get( devName ).write( newValue );
        }
        catch( Exception exc )
        {
            notFound( devName, exc );
        }
    }

    //------------------------------------------------------------------------//

    private IController setController( IDevice device )
    {
        IController cntrlr = ((IScript) getRuntime().get( scriptName )).newController();

        if( cntrlr == null )
        {
            getRuntime().log( ILogger.Level.SEVERE, scriptName +": controller not found" );
        }
        else
        {
            Map<String,Object> mapDevInit = device.getDriverInit();
                               mapDevInit = (mapDevInit == null) ? new HashMap<>() : mapDevInit;   // So, nullity it is not needed to be checked in every driver

            cntrlr.set( device.name(), mapDevInit, listener );

            if( cntrlr.isValid() )
            {
                map.put( device.name(), cntrlr );
            }
            else
            {
                getRuntime().log( ILogger.Level.SEVERE, scriptName +": is invalid after initialization" );
                cntrlr = null;
            }
        }

        return cntrlr;
    }

    private void notFound( String devName, Exception exc )
    {
        assert isStarted();

        if( ! has( devName ) )  getRuntime().log( ILogger.Level.SEVERE, new MingleException( devName +": device not found in driver: "+ name() ) );
        else                    getRuntime().log( ILogger.Level.SEVERE, new MingleException( exc ) );
    }

    //------------------------------------------------------------------------//
    // INNER CLASS
    //------------------------------------------------------------------------//
    private final class NullController implements IController
    {
        @Override
        public void set(String deviceName, Map<String, Object> deviceInit, IController.Listener listener)
        {
        }

        @Override
        public void read()
        {
        }

        @Override
        public void write(Object newValue)
        {
        }

        @Override
        public void start(IRuntime rt)
        {
        }

        @Override
        public void stop()
        {
        }

        @Override
        public boolean isValid()
        {
            return false;
        }
    }
}