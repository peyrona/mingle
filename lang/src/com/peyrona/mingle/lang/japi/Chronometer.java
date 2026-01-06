
package com.peyrona.mingle.lang.japi;

import java.util.Objects;

/**
 * Reports the time elapsed since the instance was created or the last call to ::reset().
 *
 * @author Francisco José Morero Peyrona
 *
 * Official web site at: <a href="https://github.com/peyrona/mingle">https://github.com/peyrona/mingle</a>
 *
 * Official web site at: <a href="https://github.com/peyrona/mingle">https://github.com/peyrona/mingle</a>
 */
public final class Chronometer
{
  // Following line will be thread safe for 64 bits CPUs but not for 32 bits CPUs -->
  // private volatile long time = System.currentTimeMillis();

    private long time;

    //----------------------------------------------------------------------------//

    /**
     * Class constructor.
     */
    public Chronometer()
    {
        synchronized( this )
        {
            time = System.currentTimeMillis();
        }
    }

    //----------------------------------------------------------------------------//

    /**
     * Updates its internal time-stamp to now.
     *
     * @return Itself.
     */
    public synchronized Chronometer reset()
    {
        time = System.currentTimeMillis();

        return this;
    }

    /**
     * Returns its internal time: either when the instance was created ot last
     * time ::reset() method was invoked.
     *
     * @return Its internal time: either when the instance was created ot last
     *         time ::reset() method was invoked.
     */
    public synchronized long get()
    {
        return time;
    }

    /**
     * Returns milliseconds since the instance was created or last call to ::reset.
     *
     * @return Milliseconds since the instance was created or last call to ::reset.
     */
    public synchronized long getElapsed()
    {
        return System.currentTimeMillis() - time;
    }

    /**
     * Returns true if the result of invoking ::getElapsed() is greater or equals
     * to passed amount of milliseconds.
     * <p>
     * Note: parameter has to be a long because an it only handles up to 24 days.
     *
     * @param amountInMillis
     * @return true if the result of invoking ::getElapsed() is greater or equals
     *         to passed amount of milliseconds.
     */
    public synchronized boolean isElapsed( long amountInMillis )
    {
        return (getElapsed() >= amountInMillis);
    }

    @Override
    public int hashCode()
    {
        int hash = 5;
            hash = 97 * hash + Objects.hashCode( this.time );
        return hash;
    }

    @Override
    public boolean equals(Object obj)
    {
        if( this == obj )
            return true;

        if( obj == null )
            return false;

        if( getClass() != obj.getClass() )
            return false;

        final Chronometer other = (Chronometer) obj;

        return this.time == other.time;
    }

    @Override
    public String toString()
    {
        return UtilStr.toString( this );
    }
}