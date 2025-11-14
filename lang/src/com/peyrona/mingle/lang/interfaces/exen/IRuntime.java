
package com.peyrona.mingle.lang.interfaces.exen;

import com.peyrona.mingle.lang.interfaces.ICandi;
import com.peyrona.mingle.lang.interfaces.ILogger;
import com.peyrona.mingle.lang.interfaces.IXprEval;
import com.peyrona.mingle.lang.interfaces.commands.ICommand;
import com.peyrona.mingle.lang.interfaces.commands.IDevice;
import java.util.function.Function;

/**
 * This interface represents the ExEn public API.<br>
 * An instance of this is passed to every RunTime Command: SCRIPT, DRIVER, DEVICE and RULE.
 *
 * @author Francisco José Morero Peyrona
 *
 * Official web site at: <a href="https://github.com/peyrona/mingle">https://github.com/peyrona/mingle</a>
 */
public interface IRuntime
{
    /**
     * Returns the configuration file in use.
     * @param <T>
     * @param module
     * @param varName
     * @param defValue
     * @return The configuration file in use.
     */
    <T> T getFromConfig( String module, String varName, T defValue );

    /**
     * Return the instance of IEventBus that the ExEn uses.
     *
     * @return the instance of IEventBus that the ExEn uses.
     */
    IEventBus bus();

    /**
     * Returns a function that receives the name of a group of devices and returns
     * an Array with all the device's names that belong to the group.<br>
     * <br>
     * This function is needed by IXprEval::build(...).
     *
     * @return A function that receives the name of a group of devices and returns
     * an Array with all the device's names that belong to the group.
     */
    Function<String,String[]> newGroupWiseFn();

    /**
     * Returns a new instance of the IXprEval defined in 'config.json' file.
     *
     * @return A new instance of the IXprEval defined in 'config.json' file.
     */
    IXprEval newXprEval();

    /**
     * Returns a new instance of the ICandi.IBuilder defined in config.
     *
     * @return A new instance of the ICandi.IBuilder defined in config.
     */
    ICandi.IBuilder newLanguageBuilder();

    /**
     * Returns true if this ExEn is a node of a Grid.
     *
     * @param level
     * @return true if ExEn is a node of a Grid.
     */
    boolean isGridNode();

    /**
     * A more generic logging: received Exception is logged if the LogLevel is appropriate.<br>
     * When an exception occurs in ExEn, all connected clients has to be informed.<br>
     * Where the log will is stored depends on several parameters: 'use_disk' among them.
     *
     * @param level LogLevel
     * @param message What to log.
     * @return Itself.
     */
    IRuntime log( ILogger.Level level, Object message );

    /**
     * Returns true if passed Level is loggable considering current configuration.
     * <p>
     * Used to save CPU (do not need to compose a long string that later will not be logged).
     *
     * @param level
     * @return true if passed Level is loggable considering current configuration.
     */
    boolean isLoggable( ILogger.Level level );

    /**
     * Gently ends ExEn execution by ending all internal processes.
     *
     * @param millis Delay. 'millis' 0 or negative means immediately.
     * @return Itself.
     */
    IRuntime exit( int millis );

    //------------------------------------------------------------------------//
    // ICommand RELATED API

    /**
     * Returns all living instances for passed command name. When null (or an empty string) is passed, all
     * instances for all commands are returned.
     *
     * @param sCommandType One or more of following: null, "device", "driver", "script", "rule".<br>
     *                     To make code better looking, names are case-insensitive and can be in plural or singular.
     * @return An array with all living instances for passed command type or an empty array if no instance matched.
     */
    ICommand[] all( String... sCommandType );

    /**
     * Returns the living instance which name is equals to passed argument, or null if it does not exist.<br>
     * <br>
     * As names are unique in Une, it is not needed to specify the type of command (Device, Rule, Scrip or Driver).<br>
     * <br>
     * If an empty name or null is passed, null is returned.
     *
     * @param name The Command's name to be retrieved.
     * @return The instance which name equals to second argument or null if it does not exist.
     */
    ICommand get( String name );

    /**
     * Adds passed ICommand to the ExEn.
     * <p>
     * Prior to add a new entity, all its prerequisites must be satisfied: when adding a Device,
     * its Driver must exist; and when adding a Driver, its Script must exist.
     * <p>
     * Note that Rules do not need any other entities. But if any of its actions (THEN clause)
     * triggers a Script and it does not exist when the rule is satisfied, and error will happen.
     *
     * @param command ICommand to be removed.
     */
    void add( ICommand command );

    /**
     * Removes passed ICommand from the ExEn.
     * <p>
     * When an Script or Driver is removed, associated devices (and drivers when removing a Script) will
     * be also removed; but it is not ExEn responsibility to inform about these collateral effects to the
     * listeners.<br>
     * Therefore, applications like the Mission Control Center (Glue) have to send an ExEnComm.Request.List
     * after removing an Script or a Driver.
     * <p>
     *
     * @param command ICommand to be removed.
     * @return true when the ICommand was successfully removed; false otherwise.
     */
    boolean remove( ICommand command );

    //------------------------------------------------------------------------//
    // IDevice GROUPS RELATED API

    /**
     * Returns true if passed name is the name of a group instead of the name of a device.
     *
     * @param name Name to check.
     * @return true if passed name is the name of a group instead of the name of a device.
     */
    boolean isNameOfGroup( String name );

    /**
     * Returns a collection with all devices that appear in at least one of passed group(s) or an empty array.
     *
     * @param group One or more group names to retrieve devices.
     * @return A collection with all devices that appear in all passed group(s) or an empty array.
     */
    IDevice[] getMembersOf( String... group );

    /**
     * Returns a collection with all devices that appear in at least one of passed group(s) or an empty array.
     *
     * @param group One or more group names to retrieve devices.
     * @return A collection with all devices that appear in at least one of passed group(s) or an empty array.
     */
    IDevice[] getInAnyGroup( String... group );

    /**
     * Returns a collection with all devices that appear in every passed group(s) or an empty array.
     *
     * @param group One or more group names to retrieve devices.
     * @return A collection with all devices that appear in every passed group(s) or an empty array.
     */
    IDevice[] getInAllGroups( String... group );
}