
package com.peyrona.mingle.controllers;

import com.peyrona.mingle.lang.interfaces.IController;
import com.peyrona.mingle.lang.interfaces.ILogger;
import com.peyrona.mingle.lang.interfaces.exen.IRuntime;
import com.peyrona.mingle.lang.japi.UtilStr;
import com.peyrona.mingle.lang.japi.UtilSys;
import com.peyrona.mingle.lang.japi.UtilUnit;
import java.util.HashMap;
import java.util.Map;

/**
 * This is the base class for controllers written in Java language.
 * <p>
 * Controllers are known only by Drivers, therefore there is no need to check here
 * that a Controller instance is in charge of the received deviceName because this
 * is done at Driver level.
 * <p>
 * Note: even if Controllers are not singletons, at ExEn there is only one instance
 *       per Driver class and Drivers has only one instance of their Controllers and
 *       a Controller class can be used only by one class Driver. Therefore, there
 *       will be only one instance of Controller per each class of Controller.
 *
 * @author Francisco José Morero Peyrona
 *
 * Official web site at: <a href="https://github.com/peyrona/mingle">https://github.com/peyrona/mingle</a>
 */
public abstract class      ControllerBase
                implements IController
{
    protected boolean              isFaked   = false;
    private   IRuntime             runtime   = null;
    private   String               devName   = null;    // device devName
    private   boolean              bValid    = false;
    private   IController.Listener listener  = null;
    private   Map<String,Object>   mapConfig = null;

    //------------------------------------------------------------------------//
    // PROTECTED CONSTRUCTOR

    protected ControllerBase()
    {
    }

    //------------------------------------------------------------------------//

    @Override
    public boolean isValid()
    {
        return bValid;
    }


    @Override
    public String getDeviceName()
    {
        return devName;
    }

    @Override
    public Map<String,Object> getDeviceConfig()
    {
        return mapConfig;
    }

    @Override
    public synchronized void start( IRuntime rt )
    {
        assert runtime == null && rt != null;

        isFaked = rt.getFromConfig( "exen", "faked_drivers", false );

        runtime = rt;
    }

    @Override
    public void stop()
    {
        bValid   = false;
        listener = null;
    }

    @Override
    public String toString()
    {
        return UtilStr.toString( this );
    }

    //------------------------------------------------------------------------//
    // PROTECTEED SCOPE

    protected IRuntime getRuntime()
    {
        return runtime;
    }

    protected ControllerBase setName( String name )
    {
        this.devName = name;
        return this;
    }

    protected ControllerBase setListener( IController.Listener l )
    {
        listener = l;     // Atomic
        return this;
    }

    protected ControllerBase setValid( boolean b )
    {
        bValid = b;

        if( ! b )
            listener = null;

        return this;
    }

    protected boolean isInvalid()
    {
        return ! bValid;
    }

    protected Object get( String name )
    {
        return mapConfig.get( name );
    }

    protected ControllerBase set( Map<String,Object> map )
    {
        if( mapConfig == null )
            mapConfig = new HashMap<>();

        mapConfig.putAll( map );

        return this;
    }
    protected ControllerBase set( String name, Object value )
    {
        if( mapConfig == null )
            mapConfig = new HashMap<>();

        mapConfig.put( name, value );

        return this;
    }

    protected ControllerBase setBetween( String name, int min, int value, int max )
    {
        int newVal = UtilUnit.setBetween( min, value, max );

        if( value != newVal )
            sendIsInvalid( name +" was out of range. Changed from "+ value +" to "+ newVal );

        set( name, newVal );

        return this;
    }

    protected ControllerBase sendReaded( Object newValue )
    {
        sendChanged(getDeviceName(), newValue );
        return this;
    }

    protected ControllerBase sendChanged( String deviceName, Object newValue )
    {
        if( listener != null )
            listener.onChanged( deviceName, newValue );
        else
            sendError( ILogger.Level.SEVERE, "", null );

        return this;
    }

    protected ControllerBase sendReadError( Exception exc )
    {
        sendError(ILogger.Level.SEVERE, "Error reading value for device '"+ getDeviceName() +'\'', exc );
        return this;
    }

    protected ControllerBase sendWriteError( Object value, Exception exc )
    {
        sendError( ILogger.Level.SEVERE, "Error writing value '"+ value +'\'', exc );
        return this;
    }

    protected ControllerBase sendGenericError( ILogger.Level level, String sErr )
    {
        sendError( level, sErr, null );
        return this;
    }

    protected ControllerBase sendIsNotReadable()
    {
        sendError( ILogger.Level.WARNING, "Driver '"+ getClass() +"' is write-only: can not read", null );
        return this;
    }

    protected ControllerBase sendIsNotWritable()
    {
        sendError( ILogger.Level.WARNING, "Driver '"+ getClass() +"' is read-only: can not write", null );
        return this;
    }

    protected ControllerBase sendIsInvalid( Object msg )
    {
        bValid = false;

        msg = "Invalid configuration, driver '"+ getClass() +"'could be inoperative.\n"+ (msg == null ? "" : msg.toString());

        if( msg instanceof Throwable )
            msg += UtilStr.toStringBrief( (Throwable) msg );

        sendError( ILogger.Level.WARNING, msg.toString(), null );

        return this;
    }

    protected boolean checkOfClass( Object instance, Class<?> clazz )
    {
        if( clazz.isInstance( instance ) )
            return true;

        String s = (instance == null) ? "null"
                                      : instance.getClass().getName();

        sendGenericError( ILogger.Level.SEVERE,
                          "Expected '"+ clazz.getName() +"', received '"+ s +'\'' );

        return false;
    }

    //------------------------------------------------------------------------//

    private void sendError( ILogger.Level level, String msg, Exception exc )
    {
        if( exc != null )
            msg += '\n'+ UtilStr.toString( exc );

        if( listener != null )
        {
            listener.onError( level, msg, devName );
        }
        else
        {
            msg = "Error: listener not set. Can not report:\n"+ msg;

            if( UtilSys.getLogger() != null )  UtilSys.getLogger().log( level, msg );
            else                               UtilStr.toString( exc );
        }
    }
}