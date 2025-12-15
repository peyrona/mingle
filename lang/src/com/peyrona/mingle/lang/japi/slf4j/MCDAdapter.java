package com.peyrona.mingle.lang.japi.slf4j;

import java.util.ArrayDeque;
import java.util.Collections;
import java.util.Deque;
import java.util.Map;
import org.slf4j.spi.MDCAdapter;

final class MingleMDCAdapter implements MDCAdapter
{
    @Override
    public void put( String key, String val )
    {
        /* No-op */
    }

    @Override
    public String get( String key )
    {
        return null;
    }

    @Override
    public void remove( String key )
    {
        /* No-op */
    }

    @Override
    public void clear()
    {
        /* No-op */
    }

    @Override
    public Map<String, String> getCopyOfContextMap()
    {
        return Collections.emptyMap();
    }

    @Override
    public void setContextMap( Map<String, String> contextMap )
    {
        /* No-op */
    }

    @Override
    public void pushByKey( String key, String value )
    {
        /* No-op */
    }

    @Override
    public String popByKey( String key )
    {
        return null;
    }

    @Override
    public Deque<String> getCopyOfDequeByKey( String key )
    {
        return new ArrayDeque<>();
    }

    @Override
    public void clearDequeByKey( String key )
    {
        /* No-op */
    }
}