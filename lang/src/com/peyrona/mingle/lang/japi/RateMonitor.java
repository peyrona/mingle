
package com.peyrona.mingle.lang.japi;

import java.util.LinkedList;
import java.util.Queue;

/**
 * This class is designed to detect if events are happening too fast.<br>
 * It stores a specified number of event timestamps and checks if the
 * time elapsed between the first and last event is equal to or exceeds
 * a specified threshold.
 *
 * @author Francisco José Morero Peyrona
 *
 * Official web site at: <a href="https://mingle.peyrona.com">https://mingle.peyrona.com</a>
 */
public class RateMonitor
{
    private final Queue<Long> when;        // Stores timestamps of events (FIFO)
    private final int         max;         // Maximum number of events to store
    private final long        threshold;   // Time threshold in milliseconds

    /**
     * Constructs an EventRateMonitor with the specified maximum number of
     * events and the time threshold to consider events as happening too fast.
     *
     * @param maxEvents           The maximum number of events to store.
     * @param timeThresholdMillis The time threshold in milliseconds to consider events as happening too fast.
     * @throws IllegalArgumentException If maxEvents or timeThresholdMillis is less than or equal to 0.
     */
    public RateMonitor( int maxEvents, long timeThresholdMillis )
    {
        if( maxEvents <= 0 || timeThresholdMillis <= 0 )
            throw new IllegalArgumentException( "maxEvents and timeThresholdMillis must be greater than 0." );

        this.max       = maxEvents;
        this.threshold = timeThresholdMillis;
        this.when      = new LinkedList<>();
    }

    /**
     * Notifies the monitor of a new event.<br>
     * This method stores the current timestamp and checks if the events are
     * happening too fast based on the configured threshold.
     *
     * @return true if there are enough events and the time elapsed between
     *         the first and last event is equal to or exceeds the specified
     *         threshold; false otherwise.
     */
    public boolean notifyTooFast()
    {
        long currentTimestamp = System.currentTimeMillis();

        when.add( currentTimestamp );

        if( when.size() > max )
            when.poll();           // Remove the oldest event if the queue exceeds the maximum size (the head of the queue)

        if( when.size() == max )   // Check if there are enough events and if the time elapsed exceeds the threshold
        {
            long first   = when.peek();                // Retrieves, but does not remove, the head (oldest) of this queue
            long elapsed = currentTimestamp - first;

            return elapsed <= threshold;
        }

        return false;
    }

    /**
     * Returns the timestamp of the first event in the queue.
     *
     * @return The timestamp of the first event, or 0 if the queue is empty.
     */
    public long getFirst()
    {
        return when.isEmpty() ? 0 : when.peek();
    }

    /**
     * Returns the timestamp of the last event in the queue.
     *
     * @return The timestamp of the last event, or 0 if the queue is empty.
     */
    public long getLast()
    {
        return when.isEmpty() ? 0 : ((LinkedList<Long>) when).getLast();
    }

    /**
     * Checks if the elapsed time since the last event is equal to or exceeds the specified milliseconds.
     *
     * @param millis The time in milliseconds to compare against.
     * @return true if the elapsed time since the last event is equal to or exceeds the specified milliseconds;
     *         false otherwise or if the queue is empty.
     */
    public boolean isElapsedFromLast(long millis)
    {
        if( isEmpty() )
            return false;

        long lastTimestamp = getLast();
        long currentTimestamp = System.currentTimeMillis();

        return (currentTimestamp - lastTimestamp) >= millis;
    }

    /**
     * Checks if the elapsed time since the first event is equal to or exceeds the specified milliseconds.
     *
     * @param millis The time in milliseconds to compare against.
     * @return true if the elapsed time since the first event is equal to or exceeds the specified milliseconds; false otherwise or if the queue is empty.
     */
    public boolean isElapsedFromFirst(long millis)
    {
        if( isEmpty() )
            return false;

        long firstTimestamp = getFirst();
        long currentTimestamp = System.currentTimeMillis();

        return (currentTimestamp - firstTimestamp) >= millis;
    }

    /**
     * Checks if the event queue is empty.
     *
     * @return true if the queue is empty; false otherwise.
     */
    public boolean isEmpty()
    {
        return when.isEmpty();
    }

    /**
     * Clears all stored event timestamps. This can be used to reset the monitor.
     */
    public void clear()
    {
        when.clear();
    }
}