/*
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
/** \file cron_tests.cpp
 *
 * Host/on-device tests for naxe::Cron. Exercises one-time events, each repetition mode
 * (Daily/Weekly/Monthly/Yearly), the day-31 "last day of month" marker, invalid-config
 * rejection, and the default-construct-then-configure() two-phase flow KronosDriver's
 * fixed slot array relies on. Runs under a fully controllable injected clock (never the
 * real wall clock), so results are deterministic regardless of when/where the suite runs.
 */
#include "naxe/cron_tests.hpp"

#include <cstdio>
#include <cstdlib>
#include <ctime>

#include "naxe/cron.hpp"
#include "naxe/pair.hpp"
#include "naxe/value.hpp"

namespace
{
    int failures = 0;

    void check( bool condition, const char * message )
    {
        if( ! condition )
        {
            ++failures;
            std::printf( "FAIL: %s\n", message );
        }
    }

    // A fully controllable clock -- tests never depend on the real wall-clock time.
    int64_t g_now = 0;
    int64_t fixedNow( void * ) { return g_now; }

    /** Builds an epoch value for a local (TZ="UTC", see runCronTests()) date/time. */
    int64_t epochOf( int y, int mo, int d, int h, int mi, int s )
    {
        struct tm t {};
        t.tm_year = y - 1900;
        t.tm_mon = mo - 1;
        t.tm_mday = d;
        t.tm_hour = h;
        t.tm_min = mi;
        t.tm_sec = s;
        t.tm_isdst = -1;
        return static_cast<int64_t>( std::mktime( &t ) );
    }

    void testOneTimeEvent()
    {
        naxe::Pair * p = naxe::Pair::create();
        p->set( "start", naxe::Value::makeString( "2026-08-25,10:00:00" ) );

        naxe::Cron cron( *p, &fixedNow, nullptr );
        check( cron.isValid(), "one-time: valid config" );

        g_now = epochOf( 2026, 8, 25, 9, 0, 0 );
        const int64_t delay = cron.next();
        const int64_t expected = ( epochOf( 2026, 8, 25, 10, 0, 0 ) - g_now ) * 1000;
        check( delay == expected, "one-time: delay to the configured instant" );

        check( cron.next() == -1, "one-time: no second trigger" );

        naxe::Pair::destroy( p );
    }

    void testDailyMode()
    {
        // No 'start' given: startDate_ is captured from "now" at construction time, so
        // the clock must already be set before the Cron is constructed.
        g_now = epochOf( 2026, 8, 22, 9, 0, 0 ); // Saturday 09:00

        naxe::Pair * p = naxe::Pair::create();
        p->set( "time", naxe::Value::makeString( "11:00, 18:00" ) );
        p->set( "mode", naxe::Value::makeString( "Daily" ) );
        p->set( "every", naxe::Value::makeInt( 1 ) );

        naxe::Cron cron( *p, &fixedNow, nullptr );
        check( cron.isValid(), "daily: valid config" );

        int64_t delay = cron.next();
        check( delay == ( epochOf( 2026, 8, 22, 11, 0, 0 ) - g_now ) * 1000, "daily: first trigger today 11:00" );

        delay = cron.next(); // ldtLast is now today 11:00 -> rolls to today 18:00
        check( delay == ( epochOf( 2026, 8, 22, 18, 0, 0 ) - g_now ) * 1000, "daily: second trigger today 18:00" );

        delay = cron.next(); // rolls to tomorrow 11:00
        check( delay == ( epochOf( 2026, 8, 23, 11, 0, 0 ) - g_now ) * 1000, "daily: third trigger tomorrow 11:00" );

        naxe::Pair::destroy( p );
    }

    void testWeeklyMode()
    {
        g_now = epochOf( 2026, 8, 22, 9, 0, 0 ); // Saturday 09:00

        naxe::Pair * p = naxe::Pair::create();
        p->set( "time", naxe::Value::makeString( "11, 18" ) );
        p->set( "dow", naxe::Value::makeString( "1,3,5" ) ); // Mon, Wed, Fri
        p->set( "mode", naxe::Value::makeString( "Weekly" ) );
        p->set( "every", naxe::Value::makeInt( 1 ) );

        naxe::Cron cron( *p, &fixedNow, nullptr );
        check( cron.isValid(), "weekly: valid config" );

        const int64_t delay = cron.next();
        // Next Monday after Saturday 2026-08-22 is 2026-08-24.
        check( delay == ( epochOf( 2026, 8, 24, 11, 0, 0 ) - g_now ) * 1000, "weekly: next occurrence is Monday 11:00" );

        naxe::Pair::destroy( p );
    }

    void testMonthlyLastDayMarker()
    {
        // 2026-02-01: February has 28 days -> "31" (last-day marker) resolves to the 28th.
        g_now = epochOf( 2026, 2, 1, 0, 0, 0 );

        naxe::Pair * p = naxe::Pair::create();
        p->set( "time", naxe::Value::makeString( "9:00" ) );
        p->set( "dom", naxe::Value::makeString( "31" ) );
        p->set( "mode", naxe::Value::makeString( "Monthly" ) );
        p->set( "every", naxe::Value::makeInt( 1 ) );

        naxe::Cron cron( *p, &fixedNow, nullptr );
        check( cron.isValid(), "monthly: valid config" );

        const int64_t delay = cron.next();
        check( delay == ( epochOf( 2026, 2, 28, 9, 0, 0 ) - g_now ) * 1000,
              "monthly: last-day-of-February marker resolves to the 28th" );

        naxe::Pair::destroy( p );
    }

    void testYearlyMode()
    {
        g_now = epochOf( 2026, 8, 22, 9, 0, 0 );

        naxe::Pair * p = naxe::Pair::create();
        p->set( "time", naxe::Value::makeString( "9:00" ) );
        p->set( "dom", naxe::Value::makeString( "25" ) );
        p->set( "month", naxe::Value::makeString( "12" ) );
        p->set( "mode", naxe::Value::makeString( "Yearly" ) );
        p->set( "every", naxe::Value::makeInt( 1 ) );

        naxe::Cron cron( *p, &fixedNow, nullptr );
        check( cron.isValid(), "yearly: valid config" );

        const int64_t delay = cron.next();
        check( delay == ( epochOf( 2026, 12, 25, 9, 0, 0 ) - g_now ) * 1000, "yearly: next Dec 25 09:00" );

        naxe::Pair::destroy( p );
    }

    void testInvalidConfigs()
    {
        {
            naxe::Pair * p = naxe::Pair::create();
            p->set( "time", naxe::Value::makeString( "9:00" ) );
            p->set( "mode", naxe::Value::makeString( "Weekly" ) ); // missing required 'dow'

            naxe::Cron cron( *p, &fixedNow, nullptr );
            check( ! cron.isValid(), "invalid: weekly without dow is rejected" );
            check( cron.errorCount() > 0, "invalid: weekly without dow reports an error message" );

            naxe::Pair::destroy( p );
        }
        {
            naxe::Pair * p = naxe::Pair::create();
            p->set( "time", naxe::Value::makeString( "9:00" ) );
            p->set( "dom", naxe::Value::makeString( "32" ) ); // out of range
            p->set( "mode", naxe::Value::makeString( "Monthly" ) );

            naxe::Cron cron( *p, &fixedNow, nullptr );
            check( ! cron.isValid(), "invalid: dom=32 out of range is rejected" );

            naxe::Pair::destroy( p );
        }
    }

    void testDefaultConstructThenConfigure()
    {
        naxe::Cron cron; // matches KronosDriver's fixed Slot array usage (Slot::cron default-constructed)
        check( cron.isValid(), "default-constructed Cron reports valid (nothing to reject yet)" );

        g_now = epochOf( 2026, 8, 22, 9, 0, 0 );
        check( cron.next() == -1, "default-constructed Cron never schedules anything" );

        naxe::Pair * p = naxe::Pair::create();
        p->set( "start", naxe::Value::makeString( "2026-08-25,10:00:00" ) );
        cron.configure( *p, &fixedNow, nullptr );

        check( cron.isValid(), "configure() on a reused slot produces a valid config" );
        const int64_t delay = cron.next();
        check( delay == ( epochOf( 2026, 8, 25, 10, 0, 0 ) - g_now ) * 1000, "configure()'d Cron schedules correctly" );

        naxe::Pair::destroy( p );
    }
} // namespace

namespace naxe
{
    int runCronTests()
    {
        failures = 0;

        // Cron's internal calendar arithmetic goes through mktime()/localtime_r(); pin
        // TZ to UTC so every expected epoch value above is reproducible everywhere,
        // independent of the host/target's own timezone (same reasoning as
        // eval_tests.cpp/functions_tests.cpp calling initTimeZone() before their own
        // date/time checks).
        setenv( "TZ", "UTC", 1 );
        tzset();

        testOneTimeEvent();
        testDailyMode();
        testWeeklyMode();
        testMonthlyLastDayMarker();
        testYearlyMode();
        testInvalidConfigs();
        testDefaultConstructThenConfigure();

        if( failures == 0 )
        {
            std::printf( "[naxe] cron tests: PASS\n" );
        }
        else
        {
            std::printf( "[naxe] cron tests: %d FAILURE(S)\n", failures );
        }

        return failures;
    }
} // namespace naxe
