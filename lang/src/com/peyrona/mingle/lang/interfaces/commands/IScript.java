
package com.peyrona.mingle.lang.interfaces.commands;

import com.peyrona.mingle.lang.interfaces.IController;

/**
 *
 * @author Francisco José Morero Peyrona
 *
 * Official web site at: <a href="https://mingle.peyrona.com">https://mingle.peyrona.com</a>
 */
public interface IScript extends ICommand
{
    /**
     * Returns true if this script will be automatically (not triggered by a rule) when ExEn starts.
     *
     * @return true if this script will be automatically (not triggered by a rule) when ExEn starts.
     */
    boolean isOnStart();

    /**
     * Returns true if this script will be automatically (not triggered by a rule) before ExEn stops.
     *
     * @return true if this script will be automatically (not triggered by a rule) before ExEn stops.
     */
    boolean isOnStop();

    /**
     * Returns the programming language used by this SCRIPT.
     *
     * @return The programming language used.
     */
    String getLanguage();

    /**
     * Returns true if the source code is declared in the FROM clause command.
     *
     * @return true if the source code is declared in the FROM clause command.
     */
    boolean isInline();

    /**
     * Returns the FROM clause contents. Either:
     * <ul>
     *    <li>An array of length of N where each item is a file containing the needed code.</li>
     *    <li>An array of length of 1 containing the source code.</li>
     *    <li>Null if the source code was compiled and therefore not available.</li>
     * </ul>
     * @return The FROM clause contents.
     */
    String[] getFrom();

    /**
     * Returns the entry point in code or null if there is not one.
     *
     * @return The entry point in code or null if there is not one.
     */
    String getCall();

    /**
     * Unconditionally executes this script.
     */
    void execute();

    /**
     * Creates an instance of IController: it is used by Drivers to communicate with the real world.
     *
     * @return An instance of IController or null if an error occurred.
     */
    IController newController();
}