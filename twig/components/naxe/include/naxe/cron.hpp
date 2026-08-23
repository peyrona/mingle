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
/** \file cron.hpp
 *
 * A Linux-cron-like scheduler, ported field-for-field from the reference Java
 * `com.peyrona.mingle.lang.japi.Cron` (one-time events, Daily/Weekly/Monthly/Yearly
 * repetition, multiple times-of-day, day-of-week/day-of-month/month filters, day 31 as
 * "last day of month"). Backs the `kronos_driver.hpp` sensor (Java's
 * `com.peyrona.mingle.controllers.Kronos`), the same way this port's `naxe` module backs
 * every other controller's Une-config-driven logic.
 *
 * Design departures from Java (each grounded in what this port already provides, not
 * guessed):
 *
 *  - **No exceptions.** Java's constructor throws `MingleException` with every
 *    accumulated validation message joined by a record separator. `-fno-exceptions`
 *    firmware can't do that: the constructor instead never fails outright -- it always
 *    produces an object, but `isValid()` is false and `errorAt()` exposes each message
 *    individually (capped at `kMaxErrors`) for the caller (`KronosDriver::add()`) to log
 *    one by one, matching Kronos.java's own `sendGenericError()` loop over the split
 *    message.
 *
 *  - **Local date-time is (CivilDate, secondsOfDay), not `java.time.LocalDateTime`.**
 *    All comparison/arithmetic during a `next()` search is pure calendar math -- exactly
 *    as naive of time zones as `LocalDateTime` itself, so it reproduces Java's semantics
 *    bit for bit. Only the FINAL step (converting a resolved local instant into a real
 *    epoch value to hand back a millisecond delay) touches the C library's `mktime()` --
 *    the same "let libc own DST" boundary `datetime.hpp` already draws, not a second
 *    calendar system invented here. Calendar arithmetic itself (add days, add months,
 *    weekday-of-date, days-between, months-between à la `ChronoUnit.MONTHS.between`)
 *    is also implemented via `mktime()`/`localtime_r()` round-trips anchored at local
 *    noon (dodging the one wall-clock ambiguity a DST transition can introduce),
 *    consistent with `datetime.hpp`'s stated "no hand-rolled civil-calendar math" rule.
 *
 *  - **Fixed-capacity arrays, no `List`/`Arrays.sort`.** `aLtTime`/`aDoW`/`aDoM`/`aMonth`
 *    become fixed arrays sized generously above any real config (see `kMaxTimes` etc.),
 *    sorted in place with a tiny insertion sort (the arrays top out at 31 entries -- an
 *    `O(n^2)` sort is not a real cost at that size, and pulls in no `<algorithm>`
 *    dependency this embedded target doesn't already need).
 *
 *  - **A injectable clock**, `NowEpochSecondsFn`, standing in for `LocalDateTime.now()` --
 *    same reasoning as `clock_driver.hpp`'s `ElapsedMsFn`: host tests need a controllable
 *    clock instead of the real one.
 *
 * Tier 1 (hardware-agnostic): no FreeRTOS/ESP-IDF dependency -- host-testable exactly
 * like the rest of naxe.
 */
#pragma once

#include <cstdint>
#include <string_view>

#include "etl/string.h"
#include "naxe/datetime.hpp"
#include "naxe/pair.hpp"

namespace naxe
{
    /** Signature for querying the current UTC epoch second, standing in for
     *  `LocalDateTime.now()` -- injected so host tests can supply a controllable clock
     *  instead of the real one (see `nowEpochSeconds()` in datetime.hpp for the default).
     *
     * @param ctx Caller-supplied context pointer, passed through unchanged.
     * @return Current UTC epoch seconds.
     */
    using NowEpochSecondsFn = int64_t ( * )( void * ctx );

    /** A Linux-cron-like scheduler; see this file's header comment for the full port
     *  rationale and its departures from the Java original. */
    class Cron
    {
    public:
        /** Maximum comma-separated entries the 'time' config value may hold. */
        static constexpr uint32_t kMaxTimes = 8;
        /** Maximum comma-separated entries the 'dow' config value may hold (1 per weekday). */
        static constexpr uint32_t kMaxDow = 7;
        /** Maximum comma-separated entries the 'dom' config value may hold (1 per day-of-month). */
        static constexpr uint32_t kMaxDom = 31;
        /** Maximum comma-separated entries the 'month' config value may hold (1 per month). */
        static constexpr uint32_t kMaxMonth = 12;
        /** Maximum accumulated validation error messages retained (extra ones are
         *  dropped, not silently discarded from `isValid()`'s verdict -- see addError()). */
        static constexpr uint32_t kMaxErrors = 8;

        /** A naive local calendar date, no time-of-day or time zone (mirrors
         *  `java.time.LocalDate`'s role in the original). */
        struct CivilDate
        {
            int32_t year;
            int32_t month; // 1-12
            int32_t day;   // 1-31
        };

        /** Parses and validates `args` (see Kronos.java's CONFIG doc comment for the
         *  full key list: start/stop/time/dow/dom/month/every/mode). Never fails
         *  outright -- check `isValid()` afterward.
         *
         * @param args The driver's CONFIG, as a Pair (string/number values).
         * @param nowFn Clock to use for `next()`; defaults to the real
         *              `naxe::nowEpochSeconds()`.
         * @param nowCtx Passed through unchanged to nowFn.
         */
        explicit Cron( const Pair & args, NowEpochSecondsFn nowFn = &Cron::defaultNow, void * nowCtx = nullptr );

        /** Default-constructs an unconfigured Cron (isValid() is trivially true, but
         *  next() always returns -1 -- there is nothing to schedule yet). Exists so a
         *  driver can hold a fixed array of not-yet-configured slots (matching
         *  `CellSetDriver::Cell`'s own default-member-initializer pattern) and configure()
         *  each one individually once its Device is known -- see kronos_driver.hpp. */
        Cron() = default;

        /** Parses and validates `args` into this (already-constructed) Cron, exactly as
         *  the constructor above does -- fully re-initializes every field first, so
         *  calling this more than once on the same instance is safe (each call starts
         *  from a clean slate, matching the constructor's own semantics).
         *
         * @param args The driver's CONFIG, as a Pair (string/number values).
         * @param nowFn Clock to use for `next()`; defaults to the real
         *              `naxe::nowEpochSeconds()`.
         * @param nowCtx Passed through unchanged to nowFn.
         */
        void configure( const Pair & args, NowEpochSecondsFn nowFn = &Cron::defaultNow, void * nowCtx = nullptr );

        /** @return True if `args` produced a usable configuration (no accumulated errors). */
        bool isValid() const { return errorCount_ == 0; }
        /** @return The number of accumulated validation error messages (<= kMaxErrors). */
        uint32_t errorCount() const { return errorCount_; }
        /** @param index Zero-based error index (must be < errorCount()).
         *  @return The error message. */
        std::string_view errorAt( uint32_t index ) const;

        /** Returns the milliseconds from now to the next trigger, or -1 if there is
         *  none (config invalid, one-time event already fired, or past 'stop'). Mutates
         *  internal state (the "last time returned" bookkeeping Java's `ldtLast`/
         *  `oneTimeTriggered` track) exactly once per call -- matches Java's `next()`
         *  being the single point where a schedule decision is committed.
         *
         * @return Milliseconds until the next trigger, or -1.
         */
        int64_t next();

        /** Resets the "last triggered" bookkeeping (matches Java's `reset()`). */
        void reset();

    private:
        /** A naive local (CivilDate, seconds-of-day) instant -- this port's
         *  `LocalDateTime` stand-in (see file header comment). */
        struct Instant
        {
            CivilDate date{};
            int32_t secondsOfDay = 0; // 0..86399
        };

        static int64_t defaultNow( void * ctx );

        // ---- parsing ----
        bool parseInstant( const Pair & args, std::string_view key, Instant & out );
        bool parseTimeList( const Pair & args );
        bool parseNumberList( const Pair & args, std::string_view key, int minVal, int maxVal,
                              int16_t * out, uint32_t & outCount, uint32_t maxCount, bool mapZeroToSeven );
        char parseMode( const Pair & args );
        int parseEvery( const Pair & args );
        void addError( const char * fmt, ... );

        // ---- validation ----
        void validateStartStopOrder();
        void validateOneTimeEvent();
        void validateRepeatingEvent();
        void validateModeSpecificConstraints();

        // ---- next() calculation (mirrors Java method-for-method) ----
        bool calculateNext( const Instant & now, Instant & out );
        bool calculateOneTimeEvent( const Instant & now, Instant & out );
        bool getNextDaily( const Instant & when, Instant & out );
        bool getNextWeekly( const Instant & when, Instant & out );
        bool getNextMonthly( const Instant & when, Instant & out );
        bool getNextYearly( const Instant & when, Instant & out );
        bool findNextDayInMonth( const Instant & from, Instant & out );
        bool findFirstValidDayInMonth( const CivilDate & monthStart, Instant & out );
        bool findNextYearlyOccurrence( const Instant & when, Instant & out );

        // ---- helpers ----
        bool contains16( const int16_t * arr, uint32_t count, int value ) const;
        bool getNextTimeOfDay( int32_t fromSecondsOfDay, const CivilDate & date, int32_t & out ) const;
        bool isInValidWeeklyInterval( const CivilDate & date ) const;
        bool isValidDayOfMonth( const Instant & when ) const;
        bool isLastDayOfMonth( const Instant & when ) const;
        Instant instantFromEpoch( int64_t epochSeconds ) const;

        bool hasStart_ = false;
        Instant start_{};
        bool hasStop_ = false;
        Instant stop_{};

        int32_t times_[ kMaxTimes ] = {}; // seconds-of-day, sorted ascending
        uint32_t timeCount_ = 0;
        int16_t dow_[ kMaxDow ] = {}; // ISO 1-7, sorted
        uint32_t dowCount_ = 0;
        int16_t dom_[ kMaxDom ] = {}; // 1-31 (31 = last day), sorted
        uint32_t domCount_ = 0;
        int16_t month_[ kMaxMonth ] = {}; // 1-12, sorted
        uint32_t monthCount_ = 0;

        int every_ = 0;
        char mode_ = 0; // 'd','w','m','y', or 0 (one-time)
        CivilDate startDate_{};

        bool hasLast_ = false;
        Instant last_{};
        bool oneTimeTriggered_ = false;

        NowEpochSecondsFn nowFn_ = &Cron::defaultNow;
        void * nowCtx_ = nullptr;

        etl::string<96> errors_[ kMaxErrors ];
        uint32_t errorCount_ = 0;
    };
} // namespace naxe
