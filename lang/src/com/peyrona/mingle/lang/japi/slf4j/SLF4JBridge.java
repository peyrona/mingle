package com.peyrona.mingle.lang.japi.slf4j;

import com.peyrona.mingle.lang.interfaces.ILogger;
import com.peyrona.mingle.lang.japi.Logger;

/**
 * Helper class to configure the SLF4J bridge.
 */
public final class SLF4JBridge
{
    private SLF4JBridge()
    {
        // Utility class
    }

    /**
     * Initialize the SLF4J bridge with your ILogger instance. Call this once at application startup.
     *
     * @param logger Your ILogger instance
     * @param level  The minimum logging level
     */
    public static void setup( ILogger logger, ILogger.Level level )
    {
        if( logger == null )
        {
            throw new IllegalArgumentException( "Logger cannot be null" );
        }

        logger.setLevel( level );
        MingleSLF4JLoggerFactory.initialize( logger );

        // Verify SLF4J is using our implementation
        org.slf4j.Logger slf4jLogger = org.slf4j.LoggerFactory.getLogger( SLF4JBridge.class );
        slf4jLogger.info( "SLF4J bridge initialized with Mingle Logger" );
    }

    /**
     * Initialize with default settings.
     */
    public static void setup()
    {
        Logger logger = new Logger();
                logger.init( "websocket.log", true, true );
                logger.setLevel( ILogger.Level.WARNING ); // Default to WARNING for WebSocket
        setup( logger, ILogger.Level.WARNING );
    }
}