
package com.peyrona.mingle.stick;

import com.peyrona.mingle.lang.MingleException;
import com.peyrona.mingle.lang.interfaces.ILogger;
import com.peyrona.mingle.lang.interfaces.exen.Cancellable;
import com.peyrona.mingle.lang.interfaces.exen.IEventBus;
import com.peyrona.mingle.lang.japi.Dispatcher;
import com.peyrona.mingle.lang.japi.UtilStr;
import com.peyrona.mingle.lang.japi.UtilSys;
import com.peyrona.mingle.lang.messages.Message;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.concurrent.ScheduledFuture;

/**
 * A simple FIFO in memory messaging system that dispatches received messages to
 * registered listeners.
 * <p>
 * This class is thread safe.
 *
 * @author Francisco José Morero Peyrona
 *
 * Official web site at: <a href="https://mingle.peyrona.com">https://mingle.peyrona.com</a>
 */
public class EventBus implements IEventBus
{
    // This map contains all listeners: those that attend to all messages (they key of Map is class
    // Message) and those that only attend certatin type of messages (subclasses of class Message).
    // In both cases, the Value of the Map is the List of interested Listeners.
    // In other words:
    // This map has as keys the Message's classes that listeners are subscribed and as values,
    // a list with all listeners that are subscribed to this class of Message.
    private final Map<Class<? extends Message>, Set<IEventBus.Listener>> mapListeners = new ConcurrentHashMap<>();
    private final Dispatcher<Message> dispatcher;

    //----------------------------------------------------------------------------//

    public EventBus()
    {
        this.dispatcher = new Dispatcher<>( (msg) -> trigger( msg ),
                                            (exc) -> UtilSys.getLogger().log( ILogger.Level.SEVERE, exc ),
                                            getClass().getSimpleName() );
    }

    //----------------------------------------------------------------------------//

    @Override
    public IEventBus start()
    {
        this.dispatcher.start();
        return this;
    }

    @Override
    public IEventBus post( final Message message )
    {
        dispatcher.add( message );
        return this;
    }

    @Override
    public IEventBus post( final Message message, final long delay )
    {
        if( delay <= 0 )
            post( message );
        else
            UtilSys.execute( getClass().getName(), delay, () -> EventBus.this.post( message ) );

        return this;
    }

    @Override
    public Cancellable post( final Message message, final long delay, final long interval )
    {
        if( (delay <= 0) || (interval <= 0) )
            throw new IllegalArgumentException( "Delay and Interval must be greater than 0" );

        return new MyCancellable( UtilSys.executeAtRate( getClass().getName(), delay, interval, () -> EventBus.this.post( message ) ) );
    }

    @Override
    public IEventBus add( IEventBus.Listener listener )
    {
        add( listener, Message.class );
        return this;
    }

    @Override
    public IEventBus add( IEventBus.Listener<?> listener, Class<? extends Message>... eventTypes )
    {
        for( Class<? extends Message> type : eventTypes )
        {
            mapListeners.computeIfAbsent( type, k -> new CopyOnWriteArraySet<>() ).add( listener );
        }

        return this;
    }

    @Override
    public boolean remove( IEventBus.Listener<?> listener )
    {
        // CARE: the listener can be in more than one list (entry in the map)

        boolean bExisted = false;

        for( Set<Listener> set : mapListeners.values() )
        {
            if( set.remove( listener ) )
                bExisted = true;
        }

        return bExisted;
    }

    @Override
    public IEventBus stop()
    {
        dispatcher.stop();
        mapListeners.clear();

        return this;
    }

    @Override
    public String toString()
    {
        return UtilStr.toString( this );
    }

    //------------------------------------------------------------------------//
    // DELEGATED METHODS

    @Override
    public IEventBus pause()
    {
        dispatcher.pause();
        return this;
    }

    @Override
    public IEventBus resume()
    {
        dispatcher.resume();
        return this;
    }

    @Override
    public boolean isPaused()
    {
        return dispatcher.isPaused();
    }

    @Override
    public int getSpeed()
    {
        return dispatcher.getSpeed();
    }

    @Override
    public int getPending()
    {
        return dispatcher.getPending();
    }

    //------------------------------------------------------------------------//
    // PRIVATE SCOPE

    private void trigger( final Message msg )
    {
        try
        {
            // Notify listeners for the specific event type.

            Set<IEventBus.Listener> specificListeners = mapListeners.get( msg.getClass() );

            if( specificListeners != null )
            {
                for( IEventBus.Listener ltnr : specificListeners )
                    ltnr.onMessage( msg );
            }

            // If it's a specific message, also notify listeners subscribed to all messages (Message.class),
            // avoiding double notification if the message itself is of type Message.class.

            if( msg.getClass() != Message.class )
            {
                Set<IEventBus.Listener> generalListeners = mapListeners.get( Message.class );

                if( generalListeners != null )
                {
                    for( IEventBus.Listener ltnr : generalListeners )
                        ltnr.onMessage( msg );
                }
            }
        }
        catch( Exception exc )
        {
            String sMsg;

            try
            {
                sMsg = msg.toString();
            }
            catch( Exception err )
            {
                sMsg = "Error in 'message.toString()'" + err.getMessage();
                err.printStackTrace( System.err );
            }

            UtilSys.getLogger().log( ILogger.Level.SEVERE,
                                     new MingleException( "Error '" + exc.getMessage() + "' while dispatching message: " + sMsg, exc ) );
        }
    }

    //------------------------------------------------------------------------//
    // INNER CLASS
    // Just a wrapper for ScheduledFuture to make it Cancelabe
    //------------------------------------------------------------------------//

    private static final class MyCancellable implements Cancellable
    {
        private final ScheduledFuture future;

        MyCancellable( ScheduledFuture future )
        {
            this.future = future;
        }

        @Override
        public void cancel()
        {
            future.cancel( true );
        }
    }
}