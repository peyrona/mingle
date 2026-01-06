
package com.peyrona.mingle.lang.interfaces.commands;

/**
 * The commands values as appear in their JSON serialization.
 *
 * @author Francisco José Morero Peyrona
 *
 * Official web site at: <a href="https://github.com/peyrona/mingle">https://github.com/peyrona/mingle</a>
 */
public interface ICmdKeys
{
    public static final String CMD_CMD  = "cmd";               // CMD_DEVICE | CMD_DRIVER | CMD_RULE | CMD_SCRIPT
    public static final String CMD_NAME = "name";              // Widget's name

    public static final String CMD_DEVICE       = "device";    // Must be lower case

    public static final String CMD_DRIVER       = "driver";    // Must be lower case
    public static final String DRIVER_NAME      = "driver";
    public static final String DRIVER_INIT      = "config";
    public static final String DRIVER_SCRIPT    = "script";
    public static final String DEVICE_INIT      = "init";

    public static final String CMD_RULE         = "rule";      // Must be lower case
    public static final String RULE_WHEN        = "when";
    public static final String RULE_THEN        = "then";      // These are the Actions
    public static final String RULE_IF          = "if";

    public static final String RULE_THEN_TARGET = "target";
    public static final String RULE_THEN_METHOD = "method";
    public static final String RULE_THEN_VALUE  = "value";
    public static final String RULE_THEN_AFTER  = "after";

    public static final String CMD_SCRIPT       = "script";    // Must be lower case
    public static final String SCRIPT_LANGUAGE  = "language";
    public static final String SCRIPT_FROM      = "from";
    public static final String SCRIPT_CALL      = "call";      // Entry Point
    public static final String SCRIPT_ONSTART   = "onstart";
    public static final String SCRIPT_ONSTOP    = "onstop";
    public static final String SCRIPT_INLINE    = "inline";    // Une source code FROM clause contents (SCRIPT command) is in betweeb brackets ({...})
}