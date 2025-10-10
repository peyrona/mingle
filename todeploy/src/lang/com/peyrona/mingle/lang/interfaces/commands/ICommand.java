
package com.peyrona.mingle.lang.interfaces.commands;

import com.peyrona.mingle.lang.interfaces.exen.IRuntime;

/**
 * Base interface for: Device, Driver, Rule and Script commands.
 * <p>
 * Implementation notes:
 * <ul>
 *    <li>For classes that are accessed only by Java code (e.g. those used by the 'Tape'),
 *        Collections are preferred over arrays ([]) because they are easier to manage</li>
 *    <li>For classes that can be accessed from Java code as well as other languages (e.g. JS
 *        or Python) arrays are preferred over Collections for their public interfaces, because
 *        they are easier to manage by these languages.</li>
 *    <li>Internally when writting Java code, Collections are preferred over arrays.</li>
 *    <li>Then, all classes used by ExEn will internally use Collections but they will expose
 *        arrays in their public interfaces as well as the methods tro manipulate (add & remove)
 *        array's items.</li>
 * </ul>
 * @author Francisco José Morero Peyrona
 *
 * Official web site at: <a href="https://github.com/peyrona/mingle">https://github.com/peyrona/mingle</a>
 *
 * Official web site at: <a href="https://github.com/peyrona/mingle">https://github.com/peyrona/mingle</a>
 */
public interface ICommand
{
    /**
         * Returns an internal ID that identifies uniquely this entity.
         *
         * @return An internal ID that identifies uniquely this entity.
         */
    String name();

    /**
     * This method is invoked by the Logical Twin after the unit is added into the Twin:
     * it is being to be used by the executable environment and will start receiving and
     * can send messages.
     *
     * @param runtime
     */
    void start( IRuntime runtime );

    /**
     * This method is invoked by the Logical Twin before the unit will be removed from the Twin:
     * it will not be any longer used by the executable environment and will stop receiving
     * messages and can not send any message.
     */
    void stop();
}