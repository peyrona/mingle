
package com.peyrona.mingle.controllers.modbus;

/**
 * Interface for Modbus TCP client implementations.
 * <p>
 * This interface defines the contract for asynchronous Modbus TCP communication.
 * Implementations should handle connection management, data reading/writing,
 * and periodic polling internally.
 *
 * <h3>Lifecycle:</h3>
 * <ol>
 *   <li>Create instance with configuration parameters</li>
 *   <li>Call {@link #open()} to start the client and begin polling</li>
 *   <li>Use {@link #read()} and {@link #write(Object)} for on-demand operations</li>
 *   <li>Call {@link #close()} to stop polling and disconnect</li>
 * </ol>
 *
 * <h3>Thread Safety:</h3>
 * Implementations must be thread-safe. The {@link #open()} method starts a background
 * polling task, and {@link #read()}/{@link #write(Object)} may be called from any thread.
 *
 * @author Francisco José Morero Peyrona
 * @see <a href="https://github.com/peyrona/mingle">Mingle project</a>
 */
public interface IModbusClient
{
    /**
     * Listener interface for receiving Modbus client events.
     * <p>
     * Implementations receive notifications when:
     * <ul>
     *   <li>A value is successfully read from the device (via {@link #onMessage(Object)})</li>
     *   <li>An error occurs during communication (via {@link #onError(Exception)})</li>
     * </ul>
     */
    public static interface Listener
    {
        /**
         * Called when a value is successfully read from the Modbus device.
         * <p>
         * The value type depends on the configured data type:
         * <ul>
         *   <li>"boolean" - {@link Boolean}</li>
         *   <li>"int" - {@link Integer} (unsigned 16-bit value)</li>
         *   <li>"long" - {@link Long} (unsigned 32-bit value)</li>
         *   <li>"float" - {@link Float} (IEEE 754 32-bit)</li>
         * </ul>
         *
         * @param msg The value read from the device
         */
        void onMessage( Object msg );

        /**
         * Called when an error occurs during Modbus communication.
         * <p>
         * Common error types include:
         * <ul>
         *   <li>Connection failures</li>
         *   <li>Timeout errors</li>
         *   <li>Modbus exception responses (illegal address, illegal function, etc.)</li>
         * </ul>
         *
         * @param exc The exception that occurred
         */
        void onError( Exception exc );
    }

    /**
     * Opens the connection and starts the background polling task.
     * <p>
     * After calling this method, the client will periodically read from the
     * configured Modbus address and notify the listener of changes.
     * <p>
     * The polling interval and initial delay are determined by the implementation's
     * configuration parameters.
     */
    void open();

    /**
     * Closes the connection and stops the background polling task.
     * <p>
     * After calling this method, no more listener notifications will be sent.
     * The method is safe to call multiple times.
     */
    void close();

    /**
     * Reads a value from the Modbus device synchronously.
     * <p>
     * This method performs an immediate read operation, independent of the
     * background polling task. It blocks until the read completes or fails.
     *
     * @return The value read from the device. Type depends on configuration:
     *         Boolean for coils/discrete inputs, Integer/Long/Float for registers.
     * @throws Exception If the read operation fails (connection error, timeout,
     *         Modbus exception response, etc.)
     */
    Object read() throws Exception;

    /**
     * Writes a value to the Modbus device synchronously.
     * <p>
     * This method performs an immediate write operation. It blocks until the
     * write completes or fails.
     * <p>
     * Note: Writing is only supported for writable function codes:
     * <ul>
     *   <li>Coils (FC01/FC05) - for boolean values</li>
     *   <li>Holding Registers (FC03/FC06/FC16) - for numeric values</li>
     * </ul>
     * Attempting to write to read-only function codes (discrete inputs, input registers)
     * will throw an {@link UnsupportedOperationException}.
     *
     * @param value The value to write. Must match the configured data type.
     * @return The value read back after writing (for verification)
     * @throws Exception If the write operation fails
     * @throws UnsupportedOperationException If writing is not supported for the
     *         configured function code
     * @throws IllegalArgumentException If the value type doesn't match the
     *         configured data type
     */
    Object write( Object value ) throws Exception;
}
