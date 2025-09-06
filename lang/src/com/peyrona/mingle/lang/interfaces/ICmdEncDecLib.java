
package com.peyrona.mingle.lang.interfaces;

import com.peyrona.mingle.lang.interfaces.commands.ICommand;

/**
 * An interface for Command Encoding and Decoding Library.
 * <p>
 * Note: can not exist a method that receives a JSON object because this module has to be
 *       JSON implementation independent (does not need any particular JSON lib).
 *
 * @author Francisco José Morero Peyrona
 *
 * Official web site at: <a href="https://mingle.peyrona.com">https://mingle.peyrona.com</a>
 */
public interface ICmdEncDecLib
{
    /**
     * Returns the runtime command represented by received JSON string.
     * <p>
     * Only runtime commands (IDevice, IDriver, IRule and IScript) are serialized and deserialized.
     * The INCLUDE and USE are commands used only by the transpiler: the ExEn knows nothing
     * about them and therefore there is no need to be serialized and deserialized them.
     *
     * @param sJSON
     * @return The command instance represented by received JSON string.
     */
    ICommand build( String sJSON );

    /**
     * Returns the String that represents this instance in JSON format (according with the Une format specification).
     *
     * @param cmd Command to unbuild.
     * @return The String that represents this instance in JSON format.
     */
    String unbuild( ICommand cmd );

    /**
     * This method allows to check DEVICE INIT parameters.<br>
     * The action is kind of a SET (not a GET): the method can receive a value.
     * <p>
     * For Devices, this method returns:
     * <ul>
     *    <li>Boolean.TRUE if it has a method named sPropertyName that receives a value of type oPropertyType.</li>
     *    <li>Boolean.FALSE if it does not have a method named sPropertyName.</li>
     *    <li>NUmber.class, String.class or Boolean.class if it has a method named sPropertyName but oPropertyType is of wrong type: returned class is the right one.</li>
     * </ul>
     * <p>
     * These are the properties used by DEVICE INIT clause.
     * <p>
     * As CILs are loaded at runtime and their implementations can will be different, the only way
     * to know if they support a property is by asking the CIL implementation.
     * <p>
     * Clearly this arises an incompatibility issue, but we think it is better to accept this. Any
     * case, in the future it can be evaluated the convenience of setting a "common-set" that all
     * CIL implementations have to implement.
     *
     * @param sPropertyName  The property name to check its validity. Can not be null.
     * @param oPropertyValue The value of the property to check its validity.
     * @return Boolean.TRUE  | Boolean.FALSE |  the right proper type if it was wrong
     */
    Object checkProperty( String sPropertyName, Object oPropertyValue );

    /**
     * Returns information about this CIL implementation.
     * @return Information about this CIL implementation
     */
    String about();
}