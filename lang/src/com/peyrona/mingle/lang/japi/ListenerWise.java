package com.peyrona.mingle.lang.japi;

import com.peyrona.mingle.lang.interfaces.ILogger;
import java.util.Collection;
import java.util.Collections;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.function.Consumer;

/**
 * A base class to save code and effort when implementing a class that uses listeners.
 *
 * @author Francisco José Morero Peyrona
 *
 * @param <T>
 */
public abstract class ListenerWise<T>
{
    private final Set<T> listeners = new CopyOnWriteArraySet<>();   // Set instead of List to avoid duplicates

    /**
     * Add a new listener to be informed.
     *
     * @param listener the listener to add.
     * @return true (as specified by Collection.add(E)).
     * @throws UnsupportedOperationException - if the add operation is not supported by this list
     * @throws ClassCastException - if the class of the specified element prevents it from being added to this list
     * @throws NullPointerException - if the specified element is null and this list does not permit null elements
     * @throws IllegalArgumentException - if some property of this element prevents it from being added to this list
     */
    public boolean add( T listener )
    {
        return listeners.add( listener );
    }

    /**
     * Removes an existing listener.
     *
     * @param listener the listener to remove
     * @return true if this list contained the specified element
     */
    public boolean remove( T listener )
    {
        return listeners.remove( listener );
    }

    /**
     * Removes all listeners.
     */
    public void clearListenersList()
    {
        listeners.clear();
    }

    /**
     * Returns an unmodifiable collection of all registered listeners.
     *
     * @return An unmodifiable collection of all registered listeners
     */
    public final Collection<T> getAllListeners()
    {
        return Collections.unmodifiableSet( listeners );
    }

    /**
     * Returns true when there are no listeners.
     *
     * @return true when there are no listeners
     */
    public final boolean isListenersListEmpty()
    {
        return listeners.isEmpty();
    }

    /**
     * Performs the given action for each listener. Exceptions thrown by the action are caught and printed to System.err.
     *
     * @param action The action to be performed for each listener
     * @throws NullPointerException if the action is null
     */
    public final void forEachListener( Consumer<? super T> action )
    {
        for( T listener : listeners )
        {
            try
            {
                action.accept( listener );
            }
            catch( Exception exc )     // Can not afford having an exception here
            {
                if( UtilSys.getLogger() != null )  UtilSys.getLogger().log( ILogger.Level.SEVERE, exc );
                else                               exc.printStackTrace( System.err );
            }
        }
    }
}