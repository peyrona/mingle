
package com.peyrona.mingle.controllers;

import com.peyrona.mingle.lang.interfaces.IController;
import com.peyrona.mingle.lang.interfaces.ILogger;
import com.peyrona.mingle.lang.interfaces.exen.IRuntime;
import com.peyrona.mingle.lang.japi.UtilStr;
import com.peyrona.mingle.lang.japi.UtilSys;
import com.peyrona.mingle.lang.japi.UtilUnit;

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
    protected boolean              isFaked  = false;
    private   IRuntime             runtime  = null;
    private   String               name     = null;    // device name
    private   boolean              bValid   = false;
    private   IController.Listener listener = null;

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

    protected String getName()
    {
        return name;
    }

    protected void setName( String name )
    {
        this.name = name;
    }

    protected void setListener( IController.Listener l )
    {
        listener = l;     // Atomic
    }

    protected void setValid( boolean b )
    {
        bValid = b;

        if( ! b )
            listener = null;
    }

    protected boolean isInvalid()
    {
        return ! bValid;
    }

    protected int setBetween( String name, int min, int value, int max )
    {
        int newVal = UtilUnit.setBetween( min, value, max );

        if( value != newVal )
            sendIsInvalid( name +" was out of range. Changed from "+ value +" to "+ newVal );

        return newVal;
    }

    protected void sendReaded( Object newValue )
    {
        sendChanged( getName(), newValue );
    }

    protected void sendChanged( String deviceName, Object newValue )
    {
        if( listener != null )
            listener.onChanged( deviceName, newValue );
        else
            sendError( ILogger.Level.SEVERE, "", null );
    }

    protected void sendReadError( Exception exc )
    {
        sendError( ILogger.Level.SEVERE, "Error reading value for device '"+ getName() +'\'', exc );
    }

    protected void sendWriteError( Object value, Exception exc )
    {
        sendError( ILogger.Level.SEVERE, "Error writing value '"+ value +'\'', exc );
    }

    protected void sendGenericError( ILogger.Level level, String sErr )
    {
        sendError( level, sErr, null );
    }

    protected void sendIsNotReadable()
    {
        sendError( ILogger.Level.WARNING, "Driver '"+ getClass() +"' is write-only: can not read", null );
    }

    protected void sendIsNotWritable()
    {
        sendError( ILogger.Level.WARNING, "Driver '"+ getClass() +"' is read-only: can not write", null );
    }

    protected void sendIsInvalid( Object msg )
    {
        bValid = false;

        msg = "Invalid configuration, driver '"+ getClass() +"'could be inoperative.\n"+ (msg == null ? "" : msg.toString());

        if( msg instanceof Throwable )
            msg += UtilStr.toStringBrief( (Throwable) msg );

        sendError( ILogger.Level.WARNING, msg.toString(), null );
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
            listener.onError( level, msg, name );
        }
        else
        {
            msg = "Error: listener not set. Can not report:\n"+ msg;

            if( UtilSys.getLogger() != null )  UtilSys.getLogger().log( level, msg );
            else                               UtilStr.toString( exc );
        }
    }
}