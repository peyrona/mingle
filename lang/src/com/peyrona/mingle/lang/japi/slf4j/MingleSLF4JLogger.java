package com.peyrona.mingle.lang.japi.slf4j;

import com.peyrona.mingle.lang.interfaces.ILogger;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import org.slf4j.Marker;
import org.slf4j.event.Level;
import org.slf4j.helpers.AbstractLogger;
import org.slf4j.helpers.FormattingTuple;
import org.slf4j.helpers.MessageFormatter;

public class MingleSLF4JLogger extends AbstractLogger
{
    private static final ConcurrentMap<String, MingleSLF4JLogger> loggerCache = new ConcurrentHashMap<>();

    private final ILogger delegate;

    private MingleSLF4JLogger( String name, ILogger delegate )
    {
        this.delegate = delegate;
    }

    public static MingleSLF4JLogger getLogger( String name, ILogger delegate )
    {
        return loggerCache.computeIfAbsent( name, k -> new MingleSLF4JLogger( k, delegate ) );
    }

    private ILogger.Level toMingleLevel( Level level )
    {
        switch( level )
        {
            case TRACE: return ILogger.Level.MESSAGE;
            case DEBUG: return ILogger.Level.RULE;
            case INFO:  return ILogger.Level.INFO;
            case WARN:  return ILogger.Level.WARNING;
            case ERROR: return ILogger.Level.SEVERE;
            default:
                return ILogger.Level.WARNING;
        }
    }

    @Override
    protected String getFullyQualifiedCallerName()
    {
        return null; // Not tracking caller
    }

    @Override
    protected void handleNormalizedLoggingCall( Level level,
                                                org.slf4j.Marker marker,
                                                String messagePattern,
                                                Object[] arguments,
                                                Throwable throwable )
    {
        // Format the message
        String formattedMessage;

        if( arguments != null && arguments.length > 0 )
        {
            FormattingTuple ft = MessageFormatter.arrayFormat( messagePattern, arguments );
            formattedMessage = ft.getMessage();

            if( throwable == null )
                throwable = ft.getThrowable();
        }
        else
        {
            formattedMessage = messagePattern;
        }

        // Add logger name and log
        String fullMessage = String.format( "[%s] %s", name, formattedMessage );
        delegate.log( toMingleLevel( level ), throwable, fullMessage );
    }

    @Override
    public boolean isTraceEnabled()
    {
        return delegate.isLoggable( ILogger.Level.INFO );
    }

    @Override
    public boolean isTraceEnabled( Marker marker )
    {
        return delegate.isLoggable( ILogger.Level.INFO );
    }

    @Override
    public boolean isDebugEnabled()
    {
        return delegate.isLoggable( ILogger.Level.INFO );
    }

    @Override
    public boolean isDebugEnabled( Marker marker )
    {
        return delegate.isLoggable( ILogger.Level.INFO );
    }

    @Override
    public boolean isInfoEnabled()
    {
        return delegate.isLoggable( ILogger.Level.INFO );
    }

    @Override
    public boolean isInfoEnabled( Marker marker )
    {
        return delegate.isLoggable( ILogger.Level.INFO );
    }

    @Override
    public boolean isWarnEnabled()
    {
        return delegate.isLoggable( ILogger.Level.WARNING );
    }

    @Override
    public boolean isWarnEnabled( Marker marker )
    {
        return delegate.isLoggable( ILogger.Level.WARNING );
    }

    @Override
    public boolean isErrorEnabled()
    {
        return delegate.isLoggable( ILogger.Level.SEVERE );
    }

    @Override
    public boolean isErrorEnabled( Marker marker )
    {
        return delegate.isLoggable( ILogger.Level.SEVERE );
    }
}
