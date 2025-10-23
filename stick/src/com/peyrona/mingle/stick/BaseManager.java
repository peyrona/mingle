
package com.peyrona.mingle.stick;

import com.peyrona.mingle.lang.interfaces.ILogger;
import com.peyrona.mingle.lang.interfaces.commands.ICommand;
import com.peyrona.mingle.lang.interfaces.exen.IRuntime;
import com.peyrona.mingle.lang.japi.UtilStr;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.function.Predicate;

/**
 * Base class for all managers that manage instances of ICommand.
 * <p>
 * Note: NetworkManager does not belongs (therefore it does not inherits
 * from this class), but has the suffix "Manager" in its named for consistency.
 *
 * @author Francisco José Morero Peyrona
 *
 * Official web site at: <a href="https://github.com/peyrona/mingle">https://github.com/peyrona/mingle</a>
 *
 * Official web site at: <a href="https://github.com/peyrona/mingle">https://github.com/peyrona/mingle</a>
 */
abstract class BaseManager<T extends ICommand>
{
    protected final IRuntime      runtime;
    private   final Map<String,T> map       = new ConcurrentHashMap<>();
    private   final AtomicBoolean isStarted = new AtomicBoolean( false );

    //------------------------------------------------------------------------//
    // PUBLIC SCOPE

    @Override
    public String toString()
    {
        return UtilStr.toString( this );
    }

    //------------------------------------------------------------------------//
    // PACKAGE SCOPE

    /**
     * Returns first T in the internal list that matches the predicate or null if none matched.
     *
     * @param predicate To check list items.
     * @return The first t that matches the predicate or null.
     */
    synchronized T first( Predicate<T> predicate )      // 'synchronized' because this method iterates over the map
    {
        for( T t : map.values() )
            if( predicate.test( t ) )
                return t;

        return null;
    }

    /**
     * Returns the device named as received argument or null if it does not exist.
     *
     * @param name Of the device to be retrieved.
     * @return The device named as received argument or null if it does not exist.
     */
    T named( String name )
    {
        return map.get( name.toLowerCase() );
    }

    boolean add( T cmd )
    {
        String name = cmd.name().toLowerCase();

        if( map.containsKey( name ) )
        {
            runtime.log( ILogger.Level.INFO, "Attempting to add more than once: "+ cmd.getClass().getSimpleName() +':'+ cmd.name() );
            return true;
        }

        map.put( name, cmd );

        if( isStarted() )
            cmd.start( runtime );

        return true;
    }

    /**
     * Removes an existing command.
     *
     * @param cmd This is a copy (via serialization) of the command that has to be removed.
     */
    boolean remove( T cmd )
    {
        T t = map.remove( cmd.name().toLowerCase() );

        if( t == null )   // This is normal because v.g. drivers can be in more than one device
            return false;

        t.stop();         // Stops the command

        return true;
    }

    /**
     * Passes all registered devices to the consumer.
     * <p>
     * The consumer could also apply an action only those devices which names match a regexp
     * (same could be done with device's driver's named).
     *
     * @param consumer
     */
    void forEach( Consumer<T> consumer )
    {
        if( map.isEmpty() )
            return;

        synchronized( map )
        {
            map.values().forEach( cmd -> consumer.accept( cmd ) );
        }
    }

    boolean isEmpty()
    {
        return map.isEmpty();
    }

    synchronized void start()
    {
        if( ! isStarted() )
            forEach( cmd -> cmd.start( runtime ) );    // Method is sync

        isStarted.set( true );
    }

    void stop()
    {
        if( isStarted() )
        {
            forEach( cmd -> cmd.stop() );             // Method is sync

            map.clear();
        }
    }

    boolean isStarted()
    {
        return isStarted.get();
    }

    //------------------------------------------------------------------------//
    // PROTECTED SCOPE

    /**
     * constructor
     */
    protected BaseManager( IRuntime runtime )
    {
        this.runtime = runtime;
    }
}