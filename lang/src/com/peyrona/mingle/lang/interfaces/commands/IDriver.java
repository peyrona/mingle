
package com.peyrona.mingle.lang.interfaces.commands;

/**
 * A driver is the link that connects Mingle with the physical world.
 * <p>
 * A driver talks with an IController, which is the one that really knows how to interact with
 * the physical world.
 * <p>
 * A driver changes messages sent by ExEn into messages that can be understood by the controller
 * and send these messages to the controller using the channel that the controller handles
 * (RS-232, USB, Sockets, WebSockets, etc).<br>
 * It also listen to messages sent by the controller and changes them into the kind of messages
 * that ExEn handles.<br>
 * In this way, ExEn is agnostic of the type of messages used by different controllers and all
 * pieces inside ExEn need to know only one set of messages: those used by ExEn.
 * <p>
 * When a script using a low level language (e.g.: Java or Python) adds dynamically a new Driver
 * to the ExEn, it is its responsibility to add also all the devices that driver will manage.
 *
 * @author Francisco José Morero Peyrona
 *
 * Official web site at: <a href="https://mingle.peyrona.com">https://mingle.peyrona.com</a>
 *
 * Official web site at: <a href="https://mingle.peyrona.com">https://mingle.peyrona.com</a>
 */
public interface IDriver extends ICommand
{
    /**
     * Adds passed device to the driver.
     *
     * @param device
     */
    void add( IDevice device );

    /**
     * Removes an existing device form the driver.
     *
     * @param device
     * @return true if device was successfully removed.
     */
    boolean remove( IDevice device );

    /**
     * Returns the name of the associated SCRIPT that acts as the controller for this DRIVER.
     *
     * @return The name of the associated SCRIPT that acts as the controller for this DRIVER.
     */
    String getScriptName();

    /**
     * Returns true if this driver has no devices associated.
     *
     * @return true if this driver has no devices associated.
     */
    boolean isEmpty();

    /**
     * Returns true if this driver is the one that is in charge of passed device name.
     * @param deviceName The device name to check.
     * @return true if this driver is the one that is in charge of passed device name.
     */
    boolean has( String deviceName );

    /**
     * Reads device's value (can not be null).
     * <p>
     * IDriver sends a request to the device's controller, which will read the physical world value.
     * <p>
     * @param deviceName Device's name.
     */
    void read( String deviceName );

    /**
     * Changes an ACTUATOR's value.
     * <p>
     * IDriver sends a request to the device's controller, which will change the physical world value.
     * <p>
     * Note: only valid for ACTUATORs.
     *
     * @param actuatorName ACTUATOR's name.
     * @param newValue Boolean, Float or String (can not be null).
     */
    void write( String actuatorName, Object newValue );
}