/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Interface.java to edit this template
 */
package com.peyrona.mingle.lang.interfaces;

import com.peyrona.mingle.lang.MingleException;
import com.peyrona.mingle.lang.interfaces.network.INetClient;
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

    int getGridReconectInterval();

    /**
     * Used by Gum to decide the type of Comm Client to use.
     *
     * @return An InetClient instance.
     */
    INetClient getHttpServerNetClient();

    /**
     * Returns the JSON code representing an array with all defined "clients" inside "network" module,
     * or null if the module does not exist or it does not contains a JSON array.
     *
     * @return A JSON code with all defined "clients" inside "network" module (or null).
     */
    String getNetworkClientsOutline();

    /**
     * Returns the JSON code representing an array with all defined "servers" inside "network" module,
     * or null if the module does not exist or it does not contains a JSON array.
     *
     * @return A JSON code with all defined "servers" inside "network" module (or null).
     */
    String getNetworkServersOutline();

    /**
     * Returns the location of the configuration file.
     * @return The location of the configuration file
     */
    String getURI();

    boolean isGridDeaf();

    //------------------------------------------------------------------------//
    IConfig load(String sUri) throws IOException;

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

    IConfig setCliArgs( String[] as );

    String toStrJSON();

    @Override
    String toString();
}
