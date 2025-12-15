package com.peyrona.mingle.lang.japi.slf4j;

import com.peyrona.mingle.lang.interfaces.ILogger;
import com.peyrona.mingle.lang.japi.UtilSys;
import org.slf4j.ILoggerFactory;
import org.slf4j.IMarkerFactory;
import org.slf4j.helpers.BasicMarkerFactory;
import org.slf4j.spi.MDCAdapter;
import org.slf4j.spi.SLF4JServiceProvider;

/**
 * SLF4J Service Provider Implementation.
 * This is the entry point that SLF4J discovers via Java's ServiceLoader.
 */
public class MingleSLF4JServiceProvider implements SLF4JServiceProvider
{
    private ILoggerFactory loggerFactory;
    private IMarkerFactory markerFactory;
    private MDCAdapter mdcAdapter;

    //------------------------------------------------------------------------//
    // Static initialization - try to find and use existing ILogger instance

    static
    {
        if( UtilSys.getLogger() == null )
            UtilSys.setLogger( "default-logger-for-slf4j", UtilSys.getConfig() );

        ILogger logger = UtilSys.getLogger();
        MingleSLF4JLoggerFactory.initialize( logger );
    }

    //------------------------------------------------------------------------//

    @Override
    public ILoggerFactory getLoggerFactory()
    {
        return loggerFactory;
    }

    @Override
    public IMarkerFactory getMarkerFactory()
    {
        return markerFactory;
    }

    @Override
    public MDCAdapter getMDCAdapter()
    {
        return mdcAdapter;
    }

    @Override
    public String getRequestedApiVersion()
    {
        return "2.0.99"; // SLF4J API version you're targeting
    }

    @Override
    public void initialize()
    {
        this.loggerFactory = new MingleSLF4JLoggerFactory();
        this.markerFactory = new BasicMarkerFactory();
        this.mdcAdapter = new MingleMDCAdapter(); // Simple implementation
    }
}