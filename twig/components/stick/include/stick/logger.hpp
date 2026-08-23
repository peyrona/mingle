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
// Logger: fixed-size RAM ring buffer + serial output, ported from the SHAPE of
// PLAN.md's "Further simplifications" decision -- "Logging: fixed-size RAM ring buffer +
// optional serial/socket output, not a pluggable multi-appender/rotation system --
// nothing on an embedded node needs log file rotation." See IMPLEMENTATION.md's Phase 6
// item 6.
//
// This is also the single choke point that replaces the ad-hoc std::printf calls
// scattered across device.cpp/orchestrator.cpp during earlier Phase 6 slices (each
// explicitly flagged at the time as "interim, route through the ring-buffer logger once
// it exists" -- this is that point).
//
// Design departures from Java's ILogger:
//
// - **One global Logger, not an injected instance.** Matches this port's "one firmware
//   image, one node" model (same reasoning as DriverManager/DeviceManager/etc. being
//   singletons in practice, not because the TYPE can't have multiple instances, but
//   because production code only ever needs one). Accessed via logger() below, matching
//   Java's `UtilSys.getLogger()` singleton pattern. Tests construct their own local
//   `Logger` instances directly (the type itself has no hidden global state) rather than
//   sharing the singleton, so test runs stay independent.
//
// - **No socket output.** Java's ILogger can fan out to multiple appenders; this port's
//   only sink beyond the ring buffer is immediate serial (std::printf) -- socket-based
//   remote log viewing is a Phase 8 (control channel) concern, not this one.
//
// - **Fixed 128-byte message buffer, printf-style formatting via vsnprintf.** A message
//   longer than that is truncated (vsnprintf's own documented behavior, not silently
//   corrupted) -- generous for the short, specific messages this port's log call sites
//   actually produce (see the same sizing lesson recorded for naxe::ParseError::message
//   in IMPLEMENTATION.md's Phase 6 RuleManager status note: size from real usage).
//
// Tier 1 (hardware-agnostic): no FreeRTOS/ESP-IDF dependency -- device.hpp (also Tier 1)
// can depend on this without losing its own Tier 1 status.
#pragma once

#include <atomic>
#include <cstdarg>
#include <cstdint>

namespace stick
{
    /** Severity levels for logged messages. */
    enum class LogLevel : uint8_t
    {
        Info,
        Warning,
        Severe
    };

    /** One fixed-size entry in the ring buffer. */
    struct LogEntry
    {
        LogLevel level;
        int64_t when; // epoch seconds (naxe::nowEpochSeconds() resolution)
        char message[ 128 ];
    };

    /** Fixed-size RAM ring buffer + immediate serial output.
     *
     * The single logging choke point for this firmware image; see this file's header
     * note for the design departures from Java's ILogger.
     */
    class Logger
    {
    public:
        /** Ring-buffer capacity in entries.
         *
         * Comfortably covers a burst of boot-time/rule-firing log activity between
         * whatever eventually drains the ring (Phase 8's control channel, or just
         * periodic inspection) -- raise if needed, per this port's fixed-capacity
         * convention throughout.
         */
        static constexpr uint32_t kCapacity = 64;

        /** Constructs an empty Logger (not the shared singleton). */
        Logger();

        /** Appends one formatted log message and echoes it to serial.
         *
         * printf-style (vsnprintf under the hood -- see header note on truncation).
         * Always also written to serial (std::printf) immediately, prefixed with the
         * level, matching every log call site's existing "[stick] ..." convention.
         *
         * @param level The message's severity.
         * @param fmt The printf-style format string.
         */
        void log( LogLevel level, const char * fmt, ... );

        /** Number of entries currently held (<= kCapacity). */
        uint32_t count() const { return count_; }
        /** Lifetime count of all entries ever logged.
         *
         * Compare against count() to detect ring overwrap -- if totalLogged() >
         * count(), older entries were overwritten.
         */
        uint32_t totalLogged() const { return totalLogged_; }

        /** Indexed access into the ring, oldest first.
         *
         * 0 == oldest entry currently held, count()-1 == newest.
         *
         * @param indexFromOldest The offset from the oldest held entry.
         * @return The entry at that offset.
         */
        const LogEntry & at( uint32_t indexFromOldest ) const;

    private:
        LogEntry entries_[ kCapacity ];
        uint32_t writeIndex_;
        uint32_t count_;
        uint32_t totalLogged_;

        // Guards entries_/writeIndex_/count_/totalLogged_ against concurrent logV() calls
        // -- log() is called from more than one FreeRTOS task (e.g. ControlChannel's own
        // receive task and EventBus's consumer task both log). A spinlock rather than a
        // FreeRTOS mutex/semaphore, to keep this file's stated Tier 1 (no FreeRTOS/ESP-IDF
        // dependency) contract; logV() keeps the locked section to the field writes plus
        // one vsnprintf() (no I/O), so busy-waiting on it is cheap and never blocks on
        // serial output.
        std::atomic_flag busy_ = ATOMIC_FLAG_INIT;

        /** vsnprintf-backed formatting/sharing half of log(). */
        void logV( LogLevel level, const char * fmt, va_list args );
    };

    /** The one Logger this firmware image uses.
     *
     * See this file's header note on why a global accessor (not an injected instance)
     * fits this port's "one node" model.
     *
     * @return The shared Logger instance.
     */
    Logger & logger();
}
