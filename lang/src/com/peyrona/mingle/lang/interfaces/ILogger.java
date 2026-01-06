
package com.peyrona.mingle.lang.interfaces;

/**
 *
 * @author Francisco José Morero Peyrona
 *
 * Official web site at: <a href="https://github.com/peyrona/mingle">https://github.com/peyrona/mingle</a>
 */
public interface ILogger
{
    public static enum Level
    {
        OFF(     60 ),
        SEVERE(  50 ),
        WARNING( 40 ),
        INFO(    30 ),
        RULE(    20 ),     // Trace rules only
        MESSAGE( 10 ),     // Trace all messages that are sent to the event bus
        ALL(      0 );

        public final int weight;

        //------------------------------------------------------------------------//

        private Level( int weight )
        {
            this.weight = weight;
        }

        //------------------------------------------------------------------------//

        /**
         * Converts passed string into its corresponding Level ignoring the case.
         *
         * @param s A string representing a Level (case-no-sensitive).
         * @return The type.
         * @throws IllegalArgumentException If conversion failed.
         */
        public static Level fromName( String s )
        {
            return valueOf( s.trim().toUpperCase() );
        }
    }

    /**
     * Initializes the logger with specified configuration.
     *
     * @param sFileName  The base name for log files (without extension). If null or empty, no file logging.
     * @param bUseDisk   If true, logs will be written to disk files.
     * @param bUseConsole If true, logs will be printed to console.
     * @return Itself.
     */
    public ILogger init( String sFileName, boolean bUseDisk, boolean bUseConsole );

    /**
     * Returns the logger name.
     *
     * @return The logger name.
     */
    public String  getName();

    /**
     * Returns the current minimum logging level.
     *
     * @return The current minimum logging level.
     */
    public Level   getLevel();

    /**
     * Sets the minimum logging level.
     *
     * @param l The minimum logging level to set.
     * @return Itself.
     */
    public ILogger setLevel( Level  l );

    /**
     * Assigns minimum logging level based on received level string.<br>
     * <br>Normally this string is defined in Config.json.
     * <p>
     * Can be built by passing fromJson CLI "-D=log_level=RULE" or setting it in the configuration file.
     *
     * @param s The string representing the minimum logging to be sat.
     * @return Itself.
     */
    public ILogger setLevel( String s );

    /**
     * Checks if a message at the given level would be logged.
     *
     * @param level The level to check.
     * @return {@code true} if a message at this level would be logged, {@code false} otherwise.
     */
    public boolean isLoggable( ILogger.Level level );

    /**
     * Deletes Log files older than the argument (in days).<br>
     * <br>
     * If days == 0, all log files will be deleted. If days lower than 0, no action is taken.
     *
     * @param days Number of days to decide which files has to be deleted.
     * @return Itself.
     */
    public ILogger deleteOlderThan( int days );

    /**
     * Unconditionally logs the message and shows it the console if ::isPrintable() returns true.
     *
     * @param msg Message to log.
     * @return Always true.
     */
    public boolean say( String msg );

    /**
     * Logs a message at the specified level.
     *
     * @param l   The log level.
     * @param msg The message to log.
     * @return {@code true} if message was logged, {@code false} if level was below minimum.
     */
    public boolean log( Level l, String msg );

    /**
     * Logs a throwable exception at the specified level.
     *
     * @param l   The log level.
     * @param exc The exception to log.
     * @return {@code true} if exception was logged, {@code false} if level was below minimum.
     */
    public boolean log( Level l, Throwable exc );

    /**
     * Logs a message with a throwable exception at the specified level.
     *
     * @param l   The log level.
     * @param exc The exception to log.
     * @param msg The message to log.
     * @return {@code true} if message was logged, {@code false} if level was below minimum.
     */
    public boolean log( Level l, Throwable exc, String msg );
}