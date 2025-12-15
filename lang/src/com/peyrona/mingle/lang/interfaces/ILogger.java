
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

    public ILogger init( String sFileName, boolean bUseDisk, boolean bUseConsole );

    public String  getName();
    public Level   getLevel();
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
    public boolean log( Level l, String msg );
    public boolean log( Level l, Throwable exc );
    public boolean log( Level l, Throwable exc, String msg );
}