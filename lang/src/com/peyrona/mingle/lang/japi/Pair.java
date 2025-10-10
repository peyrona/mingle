
package com.peyrona.mingle.lang.japi;

import java.util.Objects;

/**
 * A simple thread-safe pair of key and value structure.
 *
 * @author Francisco José Morero Peyrona
 *
 * Official web site at: <a href="https://github.com/peyrona/mingle">https://github.com/peyrona/mingle</a>
 *
 * Official web site at: <a href="https://github.com/peyrona/mingle">https://github.com/peyrona/mingle</a>
 * @param <K>
 * @param <V>
 */
public final class Pair<K,V>
{
    private final K key;
    private final V val;

    //----------------------------------------------------------------------------//

    public Pair( K key, V value )
    {
        this.key = key;
        this.val = value;
    }

    //----------------------------------------------------------------------------//

    public K getKey()
    {
        return key;
    }

    public V getValue()
    {
        return val;
    }

    @Override
    public int hashCode()
    {
        return Objects.hash( key, val );
    }

    @Override
    public boolean equals( Object obj )
    {
        if( obj == null )
            return false;

        if( getClass() != obj.getClass() )
            return false;

        final Pair<?, ?> other = (Pair<?, ?>) obj;

        return Objects.equals( getKey(), other.getKey() )
               &&
               Objects.equals( getValue(), other.getValue() );
    }

    @Override
    public String toString()
    {
        return UtilStr.toString( this );
    }
}