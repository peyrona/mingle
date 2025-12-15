
package com.peyrona.mingle.lang.japi.slf4j;

import org.slf4j.LoggerFactory;

public class TestSLF4J
{
    public static void main( String[] args )
    {
        SLF4JBridge.setup();

        org.slf4j.Logger logger = LoggerFactory.getLogger( TestSLF4J.class );
                         logger.info( "This will go to your ILogger system" );
                         logger.error( "Error with exception", new RuntimeException( "Test error" ) );
    }
}