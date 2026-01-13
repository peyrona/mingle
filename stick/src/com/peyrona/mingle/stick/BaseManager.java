
package com.peyrona.mingle.stick;

import com.peyrona.mingle.lang.interfaces.ILogger;
import com.peyrona.mingle.lang.interfaces.commands.ICommand;
import com.peyrona.mingle.lang.interfaces.exen.IRuntime;
import com.peyrona.mingle.lang.japi.UtilStr;
import java.util.Collection;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.function.Predicate;

/**
 * Base class for all managers that manage instances of ICommand.
 * <p>
 * Provides thread-safe storage and lifecycle management for command objects.
 * Commands are stored in a concurrent map keyed by their name (case-insensitive).
 *
 * Note: NetworkManager does not belong (therefore it does not inherit
 * from this class), but has the suffix "Manager" in its name for consistency.
 *
 * @param <T> The type of ICommand managed by this class.
 *
 * @author Francisco José Morero Peyrona
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

    /**
     * Returns a string representation of this manager.
     *
     * @return A string containing the manager's class name and the number of managed commands.
     */
    @Override
    public String toString()
    {
        return UtilStr.toString( this );
    }

    //------------------------------------------------------------------------//
    // PACKAGE SCOPE

    /**
     * Returns the first command in the internal map that matches the predicate, or null if none matched.
     *
     * @param predicate To check map items.
     * @return The first command that matches the predicate or null.
     */
    synchronized T first( Predicate<T> predicate )      // 'synchronized' because this method iterates over the map
    {
        for( T t : map.values() )
            if( predicate.test( t ) )
                return t;

        return null;
    }

    /**
     * Returns all ICommands of type T.
     *
     * @return All ICommands of type T.
     */
    Collection<T> getAll()
    {
        return map.values();
    }

    /**
     * Returns the command named as received argument or null if it does not exist.
     *
     * @param name Of the command to be retrieved (case-insensitive).
     * @return The command named as received argument or null if it does not exist.
     */
    T named( String name )
    {
        T t = map.get( name );    // Most probably this will work

        if( t != null )
            return t;

        return map.get( name.toLowerCase() );    // If not, check lower-case
    }

    /**
     * Adds a command to the manager.
     * <p>
     * If a command with the same name already exists, logs an info message and does not add it.
     * If the manager is already started, the command is immediately started.
     *
     * @param cmd The command to add.
     */
    void add( T cmd )
    {
        String name = cmd.name().toLowerCase();

        if( map.containsKey( name ) )
        {
            runtime.log( ILogger.Level.INFO, "Attempting to add more than once: "+ cmd.getClass().getSimpleName() +':'+ cmd.name() );
        }
        else
        {
            map.put( name, cmd );

            if( isStarted() )
                cmd.start( runtime );
        }
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
     * Passes all registered commands to the consumer.
     * <p>
     * The consumer can apply an action to those commands whose names match a regexp
     * (this can also be done for command's driver names).
     *
     * @param consumer The consumer to process each command.
     */
    void forEach( Consumer<T> consumer )
    {
        if( map.isEmpty() )
            return;

        map.values().forEach( cmd -> consumer.accept( cmd ) );
    }

    /**
     * Checks if the manager has no commands.
     *
     * @return true if no commands are registered; false otherwise.
     */
    boolean isEmpty()
    {
        return map.isEmpty();
    }

    /**
     * Starts all registered commands in the manager.
     * <p>
     * If the manager is already started, this method does nothing.
     * Once started, newly added commands are automatically started.
     */
    synchronized void start()
    {
        if( ! isStarted() )
            forEach( cmd -> cmd.start( runtime ) );    // Method is sync

        isStarted.set( true );
    }

    /**
     * Stops all registered commands and clears the manager.
     * <p>
     * If the manager is not started, this method does nothing.
     */
    void stop()
    {
        if( isStarted() )
        {
            forEach( cmd -> cmd.stop() );             // Method is sync

            map.clear();
        }
    }

    /**
     * Checks if the manager is started.
     *
     * @return true if the manager has been started; false otherwise.
     */
    boolean isStarted()
    {
        return isStarted.get();
    }

    //------------------------------------------------------------------------//
    // PROTECTED SCOPE

    /**
     * Creates a new BaseManager instance.
     *
     * @param runtime The runtime environment for this manager.
     */
    protected BaseManager( IRuntime runtime )
    {
        this.runtime = runtime;
    }
}