package com.peyrona.mingle.lang.japi.slf4j;

import com.peyrona.mingle.lang.interfaces.ILogger;
import com.peyrona.mingle.lang.japi.Logger;
import org.slf4j.ILoggerFactory;

/**
 * SLF4J ILoggerFactory implementation that creates MingleSLF4JLogger instances.
 */
public class MingleSLF4JLoggerFactory implements ILoggerFactory
{
    private static ILogger mingleLogger;
    private static boolean initialized = false;

    /**
     * Initialize with your ILogger instance. This should be called once at application startup.
     * @param logger
     */
    public static synchronized void initialize( ILogger logger )
    {
        if( ! initialized )
        {
            mingleLogger = logger;
            initialized = true;
        }
    }

    /**
     * Initialize with default configuration.
     */
    public static synchronized void initialize()
    {
        if( ! initialized )
        {                   // Create a default logger instance
            Logger logger = new Logger();
                   logger.init( "slf4j-bridge.log", true, true );
                   logger.setLevel( ILogger.Level.INFO );  // Default to INFO level

            mingleLogger = logger;
            initialized  = true;
        }
    }

    @Override
    public org.slf4j.Logger getLogger( String string )
    {
        if( ! initialized )
            initialize();

        return MingleSLF4JLogger.getLogger( "slf4j-bridge.log", mingleLogger );
    }

    /**
     * Get the underlying ILogger instance for configuration
     * @return .
     */
    public static ILogger getMingleLogger()
    {
        return mingleLogger;
    }
}