
package com.peyrona.mingle.lang.interfaces.commands;

/**
 * <p>
 * To manually trigger a RULE, a message of class MsgExecute has to be sent to the bus.
 *
 * @author Francisco José Morero Peyrona
 *
 * Official web site at: <a href="https://mingle.peyrona.com">https://mingle.peyrona.com</a>
 */
public interface IRule extends ICommand
{
    /**
     * An action associated with a RULE.
     */
    interface IAction
    {
        /**
         * Returns the name of: an Actuator or a Script or a Rule.
         * @return the name of: an Actuator or a Script or a Rule.
         */
        String getTarget();

        Object getValueToSet();

        long   getDelay();

        /**
         * Triggers the action.
         *
         * @param sDevName The name of the device that triggered the rule.
         * @param oDevValue The value of the device that triggered the rule.
         */
        void   trigger( String sDevName, Object oDevValue );
    }

    /**
     * Evaluates the rule if it is affected by the device.
     * <p>
     * If WHEN clause is satisfied (the expression returns true), the IF clause (if any) will be evaluated.<br>
     * When no IF or IF is satisfied, all actions will be executed in same order as they appear in the command.
     *
     * @param deviceName Device's unique name (id).
     * @param deviceValue Device's new value.
     * @return Itself.
     */
    IRule eval( String deviceName, Object deviceValue );

    /**
     * Returns the expression used to evaluate Rule's WHEN.
     * @return The expression used to evaluate Rule's WHEN.
     */
    String getWhen();

    /**
     * Returns the expression used to evaluate Rule's IF (null when RULE has no IF).
     * @return The expression used to evaluate Rule's IF (null when RULE has no IF).
     */
    String getIf();

    /**
     * Returns all existing actions for this rule.
     *
     * @return All existing actions for this rule.
     */
    IAction[] getActions();

    /**
     * Add a new Action at runtime if it did not existed yet.
     *
     * @param delay
     * @param targetName
     * @param valueToSet
     * @return The new added action or null if the Action existed.
     */
    IAction addAction( long delay, String targetName, Object valueToSet );

    /**
     * Removes an existing Action.
     *
     * @param action To be removed.
     * @return true if the Action existed, false otherwise.
     */
    boolean removeAction( IAction action );

    /**
     * This method evaluates the Rule and triggers its Actions if Rule When conditions are satisfied
     * or unconditionally triggers the Rule when it is forced to do so.<br>
     * <br>
     * <ul>
     *    <li>The Rule will not be triggered and an error will be reported if the Rule has Rule has errors.</li>
     *    <li>The Rule will not be triggered and an error will be reported if the Rule has an IF clause and parameter 'force' is false.</li>
     *    <li>The Rule will not be triggered if the Rule 'when' condition is not satisfied and parameter 'force' is false.</li>
     * </ul>
     *
     * @param force
     * @return Itself.
     */
    IRule trigger( boolean force );
}