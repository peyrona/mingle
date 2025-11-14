
package com.peyrona.mingle.stick;

import com.peyrona.mingle.lang.MingleException;
import com.peyrona.mingle.lang.interfaces.ILogger;
import com.peyrona.mingle.lang.interfaces.commands.ICommand;
import com.peyrona.mingle.lang.interfaces.commands.IDevice;
import com.peyrona.mingle.lang.interfaces.commands.IDriver;
import com.peyrona.mingle.lang.interfaces.exen.IRuntime;

/**
 * This class maintains drivers instances: there is only one instance per driver type
 * because one driver instance attends as many devices as needed.
 * <p>
 * Initially I instantiated the Drivers when I received from a Guest a message that involved a
 * device that required that driver, but the problem with this is that if e.g. a human pushes a
 * push button on the wall and the driver (e.g. RPi) is not instantiated, there will be no one
 * to receive (who is listening) the notification that the driver sends.
 *
 * @author Francisco José Morero Peyrona
 *
 * Official web site at: <a href="https://github.com/peyrona/mingle">https://github.com/peyrona/mingle</a>
 */
final class   DriverManager
      extends BaseManager<IDriver>
{
    //----------------------------------------------------------------------------//
    // PACKAGE SCOPE CONSTRUCTOR

    DriverManager( IRuntime runtime )
    {
        super( runtime );
    }

    //------------------------------------------------------------------------//
    // PACKAGE SCOPE

    /**
     * Device's driver must be added prior to add the device. And drivers can be added only once.<br>
     * <br>
     * Best thing is to check if the driver exists (pseudo-code):
     * <code>
     *   if( IRuntime::get( "driver", device.getDriverName() ) == null )
     *       IRuntime::add( ... )    // Adds the driver
     * </code>
     *
     * @param device To be managed by its driver.
     * @return true if the device was successfully added to the driver.
     */
    synchronized boolean add( IDevice device )
    {
        IDriver driver = named( device.getDriverName() );

        if( driver == null )
        {
            runtime.log( ILogger.Level.SEVERE, new MingleException( err( device, true ) ) );
            return false;
        }

        driver.add( device );

        return true;
    }

    synchronized boolean remove( IDevice device )
    {
        IDriver driver = named( device.getDriverName() );

        if( driver == null )
        {
            runtime.log(ILogger.Level.SEVERE, new MingleException( err( device, false ) ) );
            return false;
        }

        driver.remove( device );

        if( driver.isEmpty() )
        {
            driver.stop();
            runtime.remove( driver );      // Better to use Stick:remove(...) than using ::remove(...)
        }

        return true;
    }

    @Override
    synchronized boolean remove( IDriver driver )         // There is no need to remove Rules
    {
        if( super.remove( driver ) )
        {
            ICommand[] aCommands = runtime.all( "devices" );

            for( ICommand command : aCommands )
            {
                if( command instanceof IDevice )
                {
                    if( driver.has( ((IDevice) command).name() ) )
                        driver.remove( (IDevice) command );
                }
            }

            return true;
        }

        return false;
    }

    synchronized void clean()
    {
        forEach( driver ->
                    {
                        if( driver.isEmpty() )
                            super.remove( driver );
                    } );
    }

    //------------------------------------------------------------------------//

    private String err( IDevice device, boolean adding )
    {
        return "Can not "+ (adding ? "add" : "remove")+" device \""+ device.name() +
               "\" "+ (adding ? "to" : "from") +" driver \""+ device.getDriverName() +
               "\": driver does not exist";
    }
}