
package com.peyrona.mingle.stick;

import com.peyrona.mingle.lang.MingleException;
import com.peyrona.mingle.lang.interfaces.ILogger;
import com.peyrona.mingle.lang.interfaces.commands.ICommand;
import com.peyrona.mingle.lang.interfaces.commands.IDevice;
import com.peyrona.mingle.lang.interfaces.commands.IDriver;
import com.peyrona.mingle.lang.interfaces.exen.IRuntime;

/**
 * Manages DRIVER instances in the runtime environment.
 * <p>
 * Maintains one instance per driver type because one driver instance can handle
 * multiple devices of that type.
 * <p>
 * Important: Drivers must be instantiated and added before any devices that
 * require them, to ensure the driver is available to receive notifications
 * from hardware (e.g., push buttons, sensors).
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

    /**
     * Creates a new DriverManager instance.
     *
     * @param runtime The runtime environment for this manager.
     */
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

    /**
     * Removes a device from its driver.
     * <p>
     * If the driver becomes empty after removal, the driver is stopped and removed from manager.
     *
     * @param device The device to remove from its driver.
     * @return true if device was successfully removed; false if driver was not found.
     */
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

    /**
     * Removes a driver from the manager along with all its associated devices.
     *
     * @param driver The driver to remove.
     * @return true if driver was successfully removed; false otherwise.
     */
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

    /**
     * Removes all drivers that have no devices assigned to them.
     */
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