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
/** \file datetime.hpp
 *
 * Date/time support: epoch representation plus DST via the C library's standard POSIX
 * `TZ`/`tzset()`/`localtime_r()`/`mktime()` — no hand-rolled civil-calendar math or
 * custom DST function; the target's own libc computes DST transitions at runtime from
 * the TZ string set at boot (see the plan's date/time decision).
 *
 * Tier 1 (hardware-agnostic): `<time.h>` is standard C, available identically on host
 * and target.
 */
#pragma once

#include <cstdint>
#include <string_view>

namespace naxe
{
    /** One-time startup init: sets TZ to the POSIX rule from this machine's zoneinfo.
     *
     * Must be called before any other date/time function. On host this OVERRIDES the
     * host's own system timezone for the duration of the process (deliberate: tests
     * must see the target's fixed EU rule, not whatever timezone the build runs on).
     */
    void initTimeZone();

    /** @return The current UTC epoch seconds.
     *
     * Real wall-clock accuracy depends on SNTP sync (a later phase); pre-sync this
     * reflects the target's power-on default clock, same as real hardware.
     */
    int64_t nowEpochSeconds();

    /** Local (TZ-aware) calendar/clock fields extracted via localtime_r(). */
    struct LocalFields
    {
        int year;    // full year, e.g. 2026
        int month;   // 1-12
        int day;     // 1-31
        int weekday; // 0=Sunday .. 6=Saturday (struct tm's tm_wday convention)
        int hour;    // 0-23
        int minute;  // 0-59
        int second;  // 0-60 (leap second)
    };

    /** Extracts the local calendar/clock fields for an epoch instant.
     *
     * @param epochSeconds UTC epoch seconds.
     * @return The corresponding LocalFields.
     */
    LocalFields localFieldsFromEpoch( int64_t epochSeconds );

    /** Sets whether the device clock has been synced at least once (a later-phase
     * `stick::TimeSync` calls this after its first successful SNTP exchange). */
    void setTimeValid( bool valid );

    /** @return Whether the device clock has been synced at least once since boot. */
    bool isTimeValid();

    /** Parses "HH:MM" or "HH:MM:SS" — the only date/time literal form the corpus audit
     *  found in real .une files (e.g. `time("03:00")`).
     *
     * The result is an epoch value for TODAY's local date at that local time.
     *
     * @param text Input text.
     * @param outEpochSeconds Receives the epoch value on success.
     * @return True on success, false on malformed input.
     */
    bool parseHHMM( std::string_view text, int64_t & outEpochSeconds );

    /** Parses "YYYY-MM-DD" (the ISO date literal form exercised by the reference Java
     *  test suite), mirroring parseHHMM()'s exact shape. The result is midnight local
     *  time on that date.
     *
     * @param text Input text.
     * @param outEpochSeconds Receives the epoch value on success.
     * @return True on success, false on malformed input.
     */
    bool parseYMD( std::string_view text, int64_t & outEpochSeconds );
} // namespace naxe
