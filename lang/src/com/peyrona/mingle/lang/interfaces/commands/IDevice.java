
package com.peyrona.mingle.lang.interfaces.commands;

import java.util.Map;

/**
 *
 *
 * @author Francisco José Morero Peyrona
 *
 * Official web site at: <a href="https://github.com/peyrona/mingle">https://github.com/peyrona/mingle</a>
 *
 * Official web site at: <a href="https://github.com/peyrona/mingle">https://github.com/peyrona/mingle</a>
 */
public interface IDevice extends ICommand
{
    Map<String,Object> getDeviceInit();

    String             getDriverName();
    Map<String,Object> getDriverInit();

    /**
     * Returns current device value or null if device is not yet initilized or there was a
     * problem (error) retrieving device's value.
     *
     * @return current device value.
     */
    Object value();

    /**
     * Changes current device's state (for consistency the name is 'value').
     *
     * @param newValue New device's value.
     * @return true if the new value effectively changed device's value (DELTA is used).
     */
    boolean value( Object newValue );

    /**
     * Returns the delta value for this device: 0 means no delta.<br>
     * <br>
     * Note: 'delta' is also known as 'threshold' or 'hysteresis'.
     *
     * @return The delta value for this device.
     */
    Float delta();

    /**
     * Returns true if device's value was not updated during at least the value of 'downtime' property.
     *
     * @return true if device's value was not updated during at least the value of 'downtime' property.
     */
    boolean isDowntimed();

    /**
     * Returns all groups a device belong to or an empty array if the device belongs to no group.
     *
     * @return All groups a device belong to or an empty array if the device belongs to no group.
     */
    String[] groups();

    /**
     * Returns true if this device belongs to passed group.
     *
     * @param group Group name to check.
     * @return true if this device belongs to passed group.
     */
    boolean isMemberOfGroup( String group );
}