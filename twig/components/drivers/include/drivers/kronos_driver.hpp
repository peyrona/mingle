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
/** \file kronos_driver.hpp
 *
 * The "cron" sensor driver: every Device routed to KronosDriver holds its own
 * `naxe::Cron` schedule (start/stop/time/dow/dom/month/every/mode -- see cron.hpp) and
 * its own FreeRTOS one-shot timer, posting a `DeviceChanged` with the trigger's epoch
 * milliseconds each time it fires (ported from Java's
 * `com.peyrona.mingle.controllers.Kronos`, backed by `com.peyrona.mingle.lang.japi.Cron`).
 *
 * Design departures from the Java original (each grounded in what this port's runtime
 * already provides, not guessed):
 *
 *  - **One shared instance holding many independent schedules, not one Kronos per
 *    Device.** Same collapsing `CellSetDriver` already documents for its own driver:
 *    this port's `DriverManager` gives one `DriverOps` per driver NAME, shared by every
 *    Device routed to it. A model can legitimately declare more than one cron device
 *    (see `examples/16.cron_vs_clock.une`'s `cron_1`/`cron_2`, both `DRIVER CronDriver`
 *    with different CONFIG) -- `KronosDriver` is a fixed array of `kMaxCrons`
 *    independent (Cron, FreeRTOS timer) slots, one per registered device, instead of one
 *    `Cron` per controller instance.
 *
 *  - **A one-shot FreeRTOS software timer per slot, rearmed after every fire --
 *    chunked for delays a 32-bit tick count can't directly represent.** Java schedules
 *    each occurrence via `UtilSys.executor(false).delay(millis).execute(...)`, whose
 *    underlying `ScheduledThreadPoolExecutor` accepts an arbitrary `long` delay.
 *    `TimerHandle_t`'s period is a `TickType_t` (typically 32-bit), so a delay beyond
 *    `kMaxTimerDelayMs` (chosen safely below that ceiling regardless of tick rate) is
 *    waited out in successive chunks rather than one `xTimerChangePeriod` call -- see
 *    `armChunk()`. `naxe::Cron::next()` is called exactly ONCE per real occurrence (when
 *    a slot is (re)armed), never per chunk: a chunk firing early just re-arms the
 *    remaining wait, it never re-queries the schedule (which would incorrectly consume
 *    `Cron`'s "last triggered" bookkeeping before the real fire happens).
 *
 *  - **The reported value is epoch milliseconds at the real fire time**, matching
 *    Java's `last = System.currentTimeMillis()` (sampled independently of whatever
 *    `Cron::next()` originally computed, exactly as in Java) -- an `Int` Value, not
 *    `DateTime` (matches `ClockDriver` reporting its own elapsed-ms reading as a plain
 *    number, not a date/time-formatted value).
 *
 * Gated by CONFIG_DRIVER_CRON (see Kconfig) -- FreeRTOS-dependent (owns one software
 * timer per slot); `naxe::Cron` itself (cron.hpp) stays Tier 1/host-testable.
 */
#pragma once

#include <cstdint>
#include <string_view>

#include "cil/model.hpp"
#include "naxe/cron.hpp"
#include "naxe/value.hpp"
#include "stick/driver_manager.hpp"
#include "stick/event_bus.hpp"

namespace drivers
{
    /** The shared cron driver backing every Device routed to KronosDriver -- see this
     *  file's header comment for the full design rationale. */
    class KronosDriver
    {
    public:
        /** Maximum number of independent cron devices one firmware image can host. */
        static constexpr uint32_t kMaxCrons = 8;

        /** Longest single FreeRTOS timer period this driver ever arms, in milliseconds
         *  (10 days) -- comfortably under a 32-bit tick-count ceiling at any plausible
         *  `portTICK_PERIOD_MS`, so a longer wait (e.g. a yearly schedule) is chunked
         *  into successive re-arms of this size instead of one oversized period. */
        static constexpr uint64_t kMaxTimerDelayMs = 10ULL * 24 * 3600 * 1000;

        /** Constructs an empty driver.
         *
         * @param bus The EventBus to post DeviceChanged onto. Must outlive this driver.
         */
        explicit KronosDriver( stick::EventBus & bus );

        /** Deletes every slot's timer (production firmware never destructs its one
         *  driver -- see ClockDriver's own note). */
        ~KronosDriver();

        KronosDriver( const KronosDriver & ) = delete;
        KronosDriver & operator=( const KronosDriver & ) = delete;

        /** Compiles and registers one KronosDriver-routed Device from the model:
         *  parses `parsed.config` into a `naxe::Cron` and, if valid, creates its timer and
         *  arms it for the first occurrence (or leaves it dormant if the schedule has
         *  none left -- e.g. a one-time `start` already in the past). A rejected device
         *  (table full, or an invalid Cron config -- each accumulated error logged
         *  individually, matching Kronos.java's `sendGenericError()` loop) is simply
         *  never scheduled.
         *
         * @param parsed The Device to register (only its name/config are read, not
         *               retained by reference -- `parsed.name` must be stable storage,
         *               matching every other driver's add() in this port).
         * @return true if registered (a valid Cron config), regardless of whether it
         *         still has a next occurrence to arm.
         */
        bool add( const cil::Device & parsed );

        /** @return DriverOps for DriverManager::registerOps(kCronDriverName, ...). */
        stick::DriverOps ops();

    private:
        /** One cron device: its schedule plus its own rearm timer. Self-contained
         *  (carries its own `bus` pointer) so the static timer callback -- which only
         *  gets a `Slot*` back from `pvTimerGetTimerID()`, not the owning `KronosDriver`
         *  -- can post directly without needing a back-pointer to the driver instance. */
        struct Slot
        {
            std::string_view name;
            naxe::Cron cron;
            stick::EventBus * bus = nullptr;
            TimerHandle_t timer = nullptr;
            uint64_t remainingMs = 0; // ms still to wait for the currently-armed occurrence
            int64_t lastEpochMs = -1; // last real fire time (Java's Kronos::last), -1 before any fire
        };

        Slot * find( std::string_view name );

        /** Calls cron.next() ONCE to commit to the next occurrence, then arms the first
         *  chunk of the wait (or leaves the slot unarmed if there is no next occurrence). */
        static void scheduleNext( Slot & slot );

        /** (Re)arms the timer for min(remainingMs, kMaxTimerDelayMs). */
        static void armChunk( Slot & slot );

        /** One chunk elapsed: if that was the last chunk of the committed wait, this is
         *  a real fire (record lastEpochMs, post DeviceChanged, schedule the following
         *  occurrence); otherwise just re-arms the remaining wait. */
        static void onChunkElapsed( Slot & slot );

        void read( std::string_view deviceName );
        void write( std::string_view deviceName, const naxe::Value & value );

        static void timerCallback( TimerHandle_t timer );
        static void readTrampoline( void * ctx, std::string_view deviceName );
        static void writeTrampoline( void * ctx, std::string_view deviceName, const naxe::Value & value );

        stick::EventBus * bus_;
        Slot slots_[ kMaxCrons ];
        uint32_t count_;
    };
} // namespace drivers
