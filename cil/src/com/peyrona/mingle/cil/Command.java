
package com.peyrona.mingle.cil;

import com.peyrona.mingle.lang.interfaces.commands.ICommand;
import com.peyrona.mingle.lang.interfaces.exen.IRuntime;
import com.peyrona.mingle.lang.japi.UtilStr;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;

/**
 *
 * @author Francisco José Morero Peyrona
 *
 * Official web site at: <a href="https://mingle.peyrona.com">https://mingle.peyrona.com</a>
 */
public abstract class      Command
                implements ICommand
{
    private static final AtomicInteger counter = new AtomicInteger( 0 );

    private final    String   name;
    private volatile IRuntime rt = null;    // In MSP Stick implements IRuntime

    //------------------------------------------------------------------------//

    protected Command( String name )
    {
        this.name = (name == null) ? getClass().getSimpleName() +'_'+ counter.incrementAndGet()
                                   : name.trim().toLowerCase();
    }

    //------------------------------------------------------------------------//
    // PUBLIC INTERFACE

    /**
     * This method is invoked for every command and only once (successive invocations
     * for same command will result in erratic behavior). The ExEn invokes this method
     * just after the command is added to the ExEn (only after the thread started).
     * <p>
     * Implementation note: at this class this method assigns passed bus to an
     * interval variable.
     *
     * @param rt
     */
    @Override
    public void start( IRuntime rt )
    {
        assert (this.rt == null) && (rt != null);    // IRuntime is always needed

        this.rt = rt;    // Atomic
    }

    /**
     * This method is invoked for every command and only once (subsequent invocations
     * for same command will result in erratic behavior). The ExEn invokes this method
     * just before the command is about to be removed from the ExEn.
     * <p>
     * At this class this method makes the bus null (to be collected by the GC).
     */
    @Override
    public void stop()
    {
        assert isStarted();

        this.rt = null;    // Atomic
    }

    @Override
    public String name()
    {
        return name;
    }

    @Override
    public int hashCode()
    {
        int hash = 3;
            hash = 89 * hash + Objects.hashCode( this.name );
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

        final Command other = (Command) obj;

        return (this.name != null) &&
               Objects.equals( this.name, other.name );
    }

    @Override
    public String toString()
    {
        return UtilStr.toString( this );
    }

    //------------------------------------------------------------------------//
    // PROTECTED INTERFACE

    protected IRuntime getRuntime()
    {
        return rt;
    }

    protected boolean isStarted()
    {
        return (rt != null);
    }
}