
package com.peyrona.mingle.lang.interfaces;

import com.peyrona.mingle.lang.MingleException;
import com.peyrona.mingle.lang.japi.GridNode;
import java.io.IOException;
import java.util.List;

/**
 *
 * @author francisco
 */
public interface IConfig
{
    /**
     * Loads configuration from the specified URI.
     *
     * @param sUri The URI of the configuration file to load (e.g., "file:///path/to/config.json" or "http://server/config.json").
     * @return Itself.
     * @throws IOException If an I/O error occurs while loading the configuration.
     */
    IConfig load(String sUri) throws IOException;

    /**
     * Returns the Java value for 'varName' (case is ignored), or 'defValue' if 'varName' was not found.<br>
     * <br>
     * The variable is attemped to be read using following sequence of priority:
     * <ol>
     *    <li>If not empty, it is searched at ::asCliArgs (set by ::setCliArgs(...))</li>
     *    <li>Using Java System.getProperty(...)</li>
     *    <li>O.S. environment variables using System.getenv(...)</li>
     *    <li>Module received in configuration JSON file</li>
     * </ol>
     * If neither found, null is returned.
     *
     * @param <T>      Returned value.
     * @param module   Module to search for the varName (can be empty).
     * @param varName  Name of the variable to retrieve its value.
     * @param defValue To be returned if the variable does not exist or null if 'varName' is an empty string or null.
     * @return The value (an Une valid data value) for passed variable name (case is ignored), or null if not such variable.
     */
    <T> T get(String module, String varName, T defValue);

    /**
     * Replaces an existing value for an existing variable in an existing module by a new value.<br>
     * If the variable does not exists, it will be appended to the module.<br>
     * If new value is a string, it must be enclosed by double quotes ("").<br>
     * If the module does not exists, an exception is thrown.
     *
     * @param module
     * @param varName
     * @param  newValue
     * @return Itself.
     * @throws MingleException
     */
    IConfig set(String module, String varName, Object newValue) throws MingleException;

    /**
     * Add received Command Line arguments to config, so config will also use these arguments to look into.
     *
     * @param as CLI arguments.
     * @return Itself.
     */
    IConfig setCliArgs( String[] as );

    /**
     * Returns the location of the configuration file.
     * @return The location of the configuration file
     */
    String getURI();

    /**
     * Returns a new instance of ICmdEncDecLib.<br>
     * <br>
     * It is guarranted that a new instance is returned, if there is an error, application aborts.
     *
     * @return A new instance of ICmdEncDecLib.
     */
    ICmdEncDecLib newCILBuilder();

    /**
     * Returns a new instance ICandi.IBuilder that allows to manage several languages
     * transpilers, compilers and interpreters, as described in module "candi" in
     * "config.json" file.<br>
     * <br>
     * It is guarranted that a new instance is returned, if there is an error, application aborts.
     *
     * @return An instance ICandi.IBuilder
     */
    ICandi.IBuilder newLanguageBuilder();

    /**
     * Returns an empty instance of IXprEval.<br>
     * <br>
     * It is guarranted that a new instance is returned, if there is an error, application aborts.
     *
     * @return An instance of IXprEval.
     */
    IXprEval newXprEval();

    /**
     * Returns the configuration as a JSON string.
     *
     * @return The configuration encoded as a JSON string.
     */
    String toStrJSON();

    //------------------------------------------------------------------------//
    // GRID RELATED METHODS

    /**
     * Returns the number of nodes in the section "grid" or null if the section does
     * not exists or if the section is empty.
     *
     * @return the number of nodes in the section "grid" or null if the section does
     *         not exists or if the section is empty.
     */
    List<GridNode> getGridNodes();

    /**
     * Checks if the grid is in "deaf" mode, meaning it won't send or receive messages from other grid nodes.
     *
     * @return {@code true} if the grid is deaf, {@code false} otherwise.
     */
    boolean isGridDeaf();

    /**
     * Returns the reconnection interval for grid nodes in seconds.
     *
     * @return The number of seconds to wait between reconnection attempts to grid nodes.
     */
    int getGridReconectInterval();
}
