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
/** \file sysmon_driver.hpp
 *
 * The "system monitor" sensor driver: periodically samples one of five metrics
 * (speed/pending/cpu/jvmram/disk) and posts it as `DeviceChanged`. Ported from Java's
 * `com.peyrona.mingle.controllers.sysmon.SystemMonitor` (backed by
 * `com.peyrona.mingle.controllers.sysmon.SystemMetrics`).
 *
 * CONFIG keys and their accepted string values are UNCHANGED from Java (a `.une` model
 * written against the JVM runtime configures this driver identically here) --
 * `metric` in {"speed","pending","cpu","jvmram","disk"}, `measure` in
 * {"used","used%","free","free%"}, `interval` (ms, clamped to >= 500).
 *
 * Design departures from the Java original -- each is a *metric source* substitution
 * forced by there being no JVM/host-OS underneath this firmware, not a change to the
 * driver's config surface or DriverOps shape:
 *
 *  - **"speed"/"pending"** map onto `stick::EventBus::dispatchedPerMinute()` /
 *    `pendingCount()` directly -- the same underlying concept (this node's own message
 *    bus), so no departure at all.
 *
 *  - **"cpu"** has no `OperatingSystemMXBean.getCpuLoad()` equivalent. Instead this
 *    port samples each CPU core's FreeRTOS idle-task run-time counter
 *    (`ulTaskGetIdleRunTimeCounterForCore()`, gated by `CONFIG_FREERTOS_GENERATE_RUN_TIME_STATS`,
 *    which `CONFIG_DRIVER_SYSMON` `select`s automatically -- see Kconfig) and reports
 *    `1 - (idle time across all cores) / (elapsed time * core count)` as the load
 *    fraction -- the standard FreeRTOS technique for "recent CPU usage", giving the same
 *    *shape* of answer (a 0-100% load average since the last sample) as the JVM's
 *    "recent CPU usage" figure, just sourced from the RTOS instead of the OS. The first
 *    sample after boot has no earlier baseline to diff against, so it is skipped (logged,
 *    no DeviceChanged posted) rather than reporting a meaningless number -- matching
 *    Java's own NaN-means-unavailable path (`Float.isNaN(fUsed)` -> `sendReadError`).
 *
 *  - **"jvmram"** reports the native ESP32 heap (`heap_caps_get_free_size`/
 *    `_get_total_size(MALLOC_CAP_DEFAULT)`) instead of the JVM heap -- there is no JVM
 *    here, and the general-purpose heap is this firmware's closest equivalent of
 *    "the runtime's own dynamically-allocatable memory". The config value stays the
 *    literal string `"jvmram"` (not renamed to e.g. "heap") purely for `.une` model
 *    compatibility with the Java driver's CONFIG contract.
 *
 *  - **"disk" is UNIMPLEMENTED: it always reports -1**, regardless of `measure`. Java
 *    reports real filesystem usage (`java.io.File.getFreeSpace()`/`getTotalSpace()`
 *    over every filesystem root) gated by a `config.json`-level "is the disk allowed to
 *    be used" permission check (`ControllerBase::isDiskWritable`). This port has no
 *    filesystem-space API to call (no SPIFFS/FATFS partition is assumed mounted) and no
 *    equivalent permission system, so porting either honestly would mean inventing
 *    behavior Java never specified for this target. Per explicit instruction, "disk" is
 *    therefore accepted as a valid `metric` (so a model that selects it still loads and
 *    runs) but every read of it returns the constant -1 -- callers must treat -1 from
 *    the "disk" metric as "not available on this target", not as a real byte count.
 *
 * Gated by CONFIG_DRIVER_SYSMON (see Kconfig) -- FreeRTOS-dependent (owns one periodic
 * timer per device, and reads FreeRTOS run-time-stats counters for "cpu").
 */
#pragma once

#include <cstdint>
#include <string_view>

#include "cil/model.hpp"
#include "naxe/value.hpp"
#include "stick/driver_manager.hpp"
#include "stick/event_bus.hpp"

namespace drivers
{
    /** The shared system-monitor driver backing every Device routed to SysmonDriver --
     *  see this file's header comment for the full design rationale. */
    class SysmonDriver
    {
    public:
        /** Maximum number of independent system-monitor devices one firmware image can
         *  host (e.g. one device for "cpu", another for "jvmram", per
         *  `basic-drivers.une`'s own SystemMonitorDriver CONFIG comment). */
        static constexpr uint32_t kMaxSlots = 8;

        /** Minimum accepted 'interval' (ms) -- config values below this are clamped up
         *  to it (matches Java's `setBetween( KEY_INTERVAL, 500L, interval, MAX )`). */
        static constexpr uint32_t kMinIntervalMs = 500;

        /** Delay before the FIRST read of a newly-armed slot (matches Java's
         *  `.delay(5000L)` -- lets the node finish booting before the first sample,
         *  particularly relevant for "speed", which would otherwise report on a
         *  near-zero time base). Every read after the first uses the configured
         *  `interval` (see armFirstFire()/onTimer()). */
        static constexpr uint32_t kInitialDelayMs = 5000;

        /** Constructs an empty driver.
         *
         * @param bus The EventBus to post DeviceChanged onto, and to read
         *            speed/pending from. Must outlive this driver.
         */
        explicit SysmonDriver( stick::EventBus & bus );

        /** Deletes every slot's timer (production firmware never destructs its one
         *  driver -- see ClockDriver's own note). */
        ~SysmonDriver();

        SysmonDriver( const SysmonDriver & ) = delete;
        SysmonDriver & operator=( const SysmonDriver & ) = delete;

        /** Compiles and registers one SysmonDriver-routed Device from the model:
         *  validates `parsed.config`'s `metric`/`measure`/`interval` and, if valid,
         *  creates and arms that slot's periodic timer. A rejected device (table full,
         *  or an invalid metric/measure -- each error logged, matching Java's
         *  `sendIsInvalid()`) is simply never scheduled. An out-of-range `interval` is
         *  clamped and logged (a warning, not a rejection) -- also matching Java, whose
         *  final `setValid()` call in `set()` overrides whatever `setBetween()` did to
         *  validity.
         *
         * @param parsed The Device to register (only its name/config are read, not
         *               retained by reference -- `parsed.name` must be stable storage,
         *               matching every other driver's add() in this port).
         * @return true if registered.
         */
        bool add( const cil::Device & parsed );

        /** @return DriverOps for DriverManager::registerOps(kSysmonDriverName, ...). */
        stick::DriverOps ops();

    private:
        enum class Metric : uint8_t { Speed, Pending, Cpu, JvmRam, Disk };
        enum class Measure : uint8_t { Used, UsedPercent, Free, FreePercent };

        /** One monitored device: its metric/measure/interval plus its own timer and
         *  (for "cpu" only) the previous-sample baseline needed to compute a load
         *  delta. Self-contained (carries its own `bus`) so the static timer callback --
         *  which only gets a `Slot*` back from `pvTimerGetTimerID()` -- can post
         *  directly, matching KronosDriver's own Slot shape. */
        struct Slot
        {
            std::string_view name;
            Metric metric = Metric::Cpu;
            Measure measure = Measure::UsedPercent;
            uint32_t intervalMs = kMinIntervalMs;
            stick::EventBus * bus = nullptr;
            TimerHandle_t timer = nullptr;
            bool firstFireDone = false; // false until the kInitialDelayMs warm-up has elapsed once

            // "cpu"-only delta-sampling baseline (see sampleCpuLoad()):
            bool hasPrevCpuSample = false;
            TickType_t prevCpuTick = 0;
            uint32_t prevCpuIdle[ portNUM_PROCESSORS ] = {};
        };

        Slot * find( std::string_view name );

        /** Parses `metric`/`measure`/`interval` out of `config` into `slot`.
         *  @return false (and logs each problem) if `metric` or `measure` is missing/
         *          invalid; an out-of-range `interval` is clamped + logged but does not
         *          fail parsing (matches Java -- see add()'s doc comment). */
        bool parseConfig( std::string_view deviceName, const naxe::Value & config, Slot & slot );

        /** Timer fire: on a slot's very first fire, retargets its period from
         *  kInitialDelayMs to slot.intervalMs (see kInitialDelayMs) before sampling. */
        static void onTimer( Slot & slot );

        /** Samples `slot`'s configured metric and posts DeviceChanged, if the metric has
         *  a value to report right now (see sampleCpuLoad()'s first-sample note). */
        static void sample( Slot & slot );

        /** Delta-samples CPU load across every core since the slot's previous sample.
         *  @return false on the first call for this slot (no baseline yet -- the load
         *          fraction is left unset). */
        static bool sampleCpuLoad( Slot & slot, float & outUsedFraction );

        /** Posts `value` scaled/selected by slot.measure, given raw used/free/total
         *  byte (or count) figures -- matches Java's private `send(used,free,total)`. */
        static void postMeasured( Slot & slot, int64_t used, int64_t free, int64_t total );

        void read( std::string_view deviceName );
        void write( std::string_view deviceName, const naxe::Value & value );

        static void timerCallback( TimerHandle_t timer );
        static void readTrampoline( void * ctx, std::string_view deviceName );
        static void writeTrampoline( void * ctx, std::string_view deviceName, const naxe::Value & value );

        stick::EventBus * bus_;
        Slot slots_[ kMaxSlots ];
        uint32_t count_;
    };
} // namespace drivers
