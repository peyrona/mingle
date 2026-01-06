
package com.peyrona.mingle.lang.interfaces.exen;

import com.peyrona.mingle.lang.messages.Message;

/**
 * This interface has to be implemented by the Event Bus used by an ExEn.
 *
 * @author Francisco José Morero Peyrona
 *
 * Official web site at: <a href="https://github.com/peyrona/mingle">https://github.com/peyrona/mingle</a>
 */
public interface IEventBus
{
    interface Listener<T extends Message>
    {
        void onMessage( T message );
    }

    /**
     * Posts a new message into the bus to be delivered ASAP.
     *
     * @param message To be posted.
     * @return Itself.
     */
    IEventBus post( Message message );

    /**
     * Posts a new message into the bus to be delivered ASAP after delay expired.
     *
     * @param message To be posted.
     * @param delay Delay to deliver (in milliseconds).
     * @return Itself.
     */
    IEventBus post( Message message, long delay );

    /**
     * Posts same message into the bus every 'interval' after an initial delay of 'delay'.
     *
     * @param message To be posted.
     * @param delay Delay to deliver (in milliseconds).
     * @param interval Interval between two consecutive posts (in milliseconds).
     * @return An instance of Cancellable interface (used to stop posting).
     */
    Cancellable post( Message message, long delay, long interval );

    /**
     * Pauses delivering messages: those that arrive while being paused will be delivered after bus resumed.
     *
     * @return Itself.
     * @see #resume()
     */
    IEventBus pause();

    /**
     * Delivers all pending messages (arrived while bus was paused).
     *
     * @return Itself.
     * @see #pause()
     */
    IEventBus resume();

    /**
     * Returns true if the EventBus is pause mode: accumulating messages but not delivering them.
     *
     * @return true if the EventBus is pause mode, false otherwise.
     */
    boolean isPaused();

    /**
     * Start the internal thread to dispatch messages.
     *
     * @return Itself.
     */
    IEventBus start();

    /**
     * House keeping.
     *
     * @return Itself.
     */
    IEventBus stop();

    /**
     * Adds a new listener, which will receive all events that are posted into the bus.
     *
     * @param listener The listener.
     * @return Itself.
     */
    IEventBus add( Listener listener );

    /**
     * Adds a new listener, which will receive only the events of specified class(es) that are posted into the bus.
     *
     * @param listener The listener.
     * @param eventTypes One or more Message class(es).
     * @return Itself.
     * @see Message
     */
    IEventBus add( Listener<?> listener, Class<? extends Message>... eventTypes );

    /**
     * Removes an existing listener.
     *
     * @param listener To be removed.
     * @return true if listener was successfully removed.
     */
    boolean remove( Listener<?> listener );

    /**
     * Returns current bus speed: amount of messages per minute that are being processed.
     *
     * @return Current bus speed: amount of messages per minute that are being processed.
     */
    int getSpeed();

    /**
     * Returns the amount of messages waiting in the queue (pending to be processed).
     *
     * @return The amount of messages waiting in the queue (pending to be processed).
     */
    int getPending();
}