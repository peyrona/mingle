
package com.peyrona.mingle.gum;

import java.net.InetAddress;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Per-IP brute-force throttle for {@code POST /gum/login}.
 * <p>
 * Guessing the master password grants full control of the house, and this endpoint is reachable from
 * the open internet whenever HTTPS is configured — there is no LAN or guest-subnet boundary standing
 * between an attacker and unlimited attempts unless something here does. Three wrong passwords lock
 * the source IP out for a growing interval (30s, 2m, 10m, 30m — capped), reset by a single success.
 * A quiet hour forgets the count entirely, so a mistyped password from months ago never lingers.
 * <p>
 * In-memory only: a Gum restart clears every lockout. Acceptable for this threat model (a small
 * property, not a bank) — not worth the complexity of persisting it.
 *
 * @author Francisco José Morero Peyrona
 *
 * Official web site at: <a href="https://github.com/peyrona/mingle">https://github.com/peyrona/mingle</a>
 */
final class LoginRateLimiter
{
    private static final int    ATTEMPTS_PER_TIER  = 3;
    private static final long[] LOCKOUT_MS_BY_TIER  = { 30_000L, 120_000L, 600_000L, 1_800_000L };   // 30s, 2m, 10m, 30m
    private static final long   FORGET_AFTER_MS     = 60 * 60 * 1000L;                               // an hour of quiet
    private static final long   SWEEP_EVERY_N_CALLS = 256;                                           // How often stale entries are purged

    private static final ConcurrentHashMap<String, Entry> map       = new ConcurrentHashMap<>();
    private static final AtomicLong                       callCount = new AtomicLong();

    private LoginRateLimiter() {}

    /**
     * Atomically checks whether this IP may attempt a login right now and, if it may, immediately
     * counts the attempt as a tentative failure (undone by {@link #onSuccess} when the credential
     * turns out right). Doing the check and the count in one atomic map operation — instead of a
     * check now and a separate {@code onFailure} call once the credential has been verified — closes
     * the race where concurrent requests from the same IP could all observe "not locked yet" before
     * any of them got counted, letting a parallel brute-force burst skip the throttle almost entirely.
     *
     * @param ip the caller's address; a {@code null} (unresolvable) address is never throttled.
     * @return how many seconds remain locked out, or {@code 0} when the attempt may proceed (and has
     *         already been counted).
     */
    static long tryAttempt( InetAddress ip )
    {
        if( ip == null )
            return 0;

        long   now    = System.currentTimeMillis();
        long[] result = { 0 };

        if( (callCount.incrementAndGet() % SWEEP_EVERY_N_CALLS) == 0 )
            sweep( now );

        map.compute( ip.getHostAddress(), (key, e) ->
        {
            if( (e == null) || ((now - e.lastFailure) > FORGET_AFTER_MS) )
                e = new Entry();

            long remaining = Math.max( 0, (e.lockedUntil - now + 999) / 1000 );   // Round up: never report "0s left" while still locked.

            if( remaining > 0 )
            {
                result[0] = remaining;
                return e;       // Still locked: this attempt is refused, so it is not counted.
            }

            e.fails++;
            e.lastFailure = now;

            if( (e.fails % ATTEMPTS_PER_TIER) == 0 )
            {
                int tier = Math.min( (e.fails / ATTEMPTS_PER_TIER) - 1, LOCKOUT_MS_BY_TIER.length - 1 );
                e.lockedUntil = now + LOCKOUT_MS_BY_TIER[ tier ];
            }

            return e;
        } );

        return result[0];
    }

    /** Clears whatever failure history this IP had — a correct password earns a clean slate. */
    static void onSuccess( InetAddress ip )
    {
        if( ip != null )
            map.remove( ip.getHostAddress() );
    }

    /**
     * Drops every entry that has been quiet for over {@link #FORGET_AFTER_MS}. Piggybacked on every
     * {@value #SWEEP_EVERY_N_CALLS}th call instead of a dedicated background thread — no new moving
     * parts for a table that is normally tiny. Without this, an entry is otherwise only ever revisited
     * (and so only ever evicted) by a later call from that same IP: a distributed source using a fresh
     * address per attempt would accumulate one abandoned entry per address forever.
     *
     * @param now The current time in millis, as already computed by the caller.
     */
    private static void sweep( long now )
    {
        map.entrySet().removeIf( e -> (now - e.getValue().lastFailure) > FORGET_AFTER_MS );
    }

    //------------------------------------------------------------------------//

    /** Per-IP failure state: how many, when the last one was, and until when (if any) it is locked out. */
    private static final class Entry
    {
        int  fails;         // Consecutive wrong passwords since the last success or the last quiet hour
        long lastFailure;   // millis; drives both FORGET_AFTER_MS staleness and sweep() eviction
        long lockedUntil;   // millis; 0 (or past) means not currently locked
    }
}
