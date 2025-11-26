
package com.peyrona.mingle.lang.interfaces;

import com.peyrona.mingle.lang.interfaces.exen.IRuntime;
import java.util.Map;

/**
 * A controller is the piece of software that deals with the physical world.<br>
 * <br>
 * A controller, can be written in any programming language: Java, C, Python, JavaScript, ...<br>
 * <br>
 * Note: OOP Polymorphism is avoided here in method names because some languages (like JS) do not allow it.
 *
 * @author Francisco José Morero Peyrona
 *
 * Official web site at: <a href="https://github.com/peyrona/mingle">https://github.com/peyrona/mingle</a>
 */
public interface IController
{
    interface Listener
    {
        /**
         * Invoked when a device's current value is read.
         *
         * @param deviceName Name of the device.
         * @param newValue Device's current value: Boolean, Integer, Float or String (can not be null).
         */
        void onReaded( String deviceName, Object newValue );

        /**
         * Invoked when a change occurs in the real world (physical device).
         *
         * @param deviceName Name of the device which value changed.
         * @param newValue Device value: Boolean, Integer, Float or String (can not be null).
         */
        void onChanged( String deviceName, Object newValue );

        /**
         * Invoked when an error occurs in the real world (physic device).
         *
         * @param level Une logger error level.
         * @param message Error message
         * @param device Involved in the error. Can be null.
         */
        void onError( ILogger.Level level, String message, String device );
    }

    /**
     * Sets the device that will be managed by this controller.
     * <p>
     * There is no advantage in being the configuration a JSON string because there is no guarantee
     * that native controllers will accept a JSON encoded in a string, neither that the [key,value]
     * pairs will be those that the native driver is expecting.
     *
     * @param deviceName Device name.
     * @param deviceInit Device configuration as propName, propValue pairs (it can be null).
     * @param listener
     */
    void set( String deviceName, Map<String,Object> deviceInit, IController.Listener listener );

    /**
     * Returns the associated device name.
     *
     * @return The associated device name.
     */
    String getDeviceName();

    /**
     * Returns the configuration for the associated device.
     *
     * @return The configuration for the associated device.
     */
    Map<String,Object> getDeviceConfig();

    /**
     * Reads device's value: Boolean, Number or String (can not be null).
     * <p>
     * Controller has to invoke ::Listener:onChanged(...) method passing device's
     * name. If there were a problem reading the value, it has to invoke
     * ::Listener:onError(...) explaining the cause of the problem.
     * <p>
     * Note: this method returns void because its implementation must be asynchronous.
     *
     * @param deviceName Device's name.
     */
    void read();

    /**
     * Changes ACTUATOR's value. Any valid Une data value: Boolean, Float or String
     * (can not be null).
     * <p>
     * If Controller was able to change the value, it has to invoke ::Listener:onChanged(...)
     * method passing the ACTUATOR's name and value. Otherwise it has to invoke
     * ::Listener:onError(...) explaining the cause of the problem.
     * <p>
     * Note: only valid for ACTUATORs.<br>
     * Note: this method's implementation should be asynchronous.<br>
     * Note: if the execution of this method will take some time, it should run inside a thread.
     *
     * @param actuatorName ACTUATOR's name.
     * @param newValue Boolean, Float or String (can not be null).
     */
    void write( Object newValue );

    /**
     * This method provides the Controller the opportunity access the IRuntime (in case it
     * would be needed).Also provides the Controller the opportunity to perform any
     * initialization tasks.
     *
     * @param rt
     */
    void start( IRuntime rt );

    /**
     * This method provides the Controller the opportunity to make some housekeeping before
     * end running.
     */
    void stop();

    boolean isValid();
}