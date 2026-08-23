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
/** \file registry.hpp
 *
 * The fixed compile-time registry of the native drivers this firmware can host.
 *
 * This is the single machine-readable source of truth for three consumers, all reading
 * the SAME table so they can never drift apart:
 *
 *  - **the compatibility checker** (`components/compat_checker`, host-side): looks a
 *    model's `DRIVER`/`Device.driver` names up here to answer "does a native driver for
 *    this name exist?" (Phase 11 part B).
 *  - **`main.cpp`'s boot wiring** (target-side): iterates this table, filtered by the
 *    per-driver `CONFIG_DRIVER_*` Kconfig symbols, to construct/register each enabled
 *    driver.
 *  - **`tools/build_model.sh`** (selective build): reads the checker's
 *    `--emit-selection` output (which maps each referenced driver to its `kconfig`
 *    symbol here) to compile in exactly the drivers a model needs.
 *
 * Deliberately data-only (host-buildable): no FreeRTOS/ESP-IDF includes, so the checker
 * and the selective-build tool can consume it without the target toolchain. The
 * construction code (`ClockDriver`, `consoleOps`) lives in the driver .cpp files.
 *
 * Tier 1 (hardware-agnostic).
 */
#pragma once

#include <cstddef>
#include <cstdint>
#include <string_view>

#include "naxe/util.hpp"

namespace drivers
{
    /** What I/O a driver exposes, so a caller knows whether read/write are meaningful. */
    enum class DriverKind : uint8_t
    {
        Sensor,   // actively reads a device and posts DeviceChanged (write is a no-op)
        Actuator, // writes to a device on ChangeActuator (read is a no-op)
        Mixed     // both read and write are meaningful (e.g. CellSetDriver's cells)
    };

    /** One registry entry: a model-named driver and the Kconfig symbol gating its build. */
    struct DriverDescriptor
    {
        std::string_view name;   // the driver name as it appears in the model's DRIVER command
        const char * kconfig;    // CONFIG_DRIVER_* symbol gating compilation (Kconfig)
        DriverKind kind;
    };

    /** Driver names, as constants so the registry table and any caller that needs to
     * address a driver by name (e.g. main.cpp's boot wiring) share one literal. */
    inline constexpr std::string_view kClockDriverName = "clockdriver";
    inline constexpr std::string_view kOutputDriverName = "outputdriver";
    inline constexpr std::string_view kCellSetDriverName = "cellsetdriver";
    inline constexpr std::string_view kCronDriverName = "crondriver";
    inline constexpr std::string_view kSysmonDriverName = "systemmonitordriver";

    /** The full fixed driver registry.
     *
     * Add a new native driver by appending one entry and its Kconfig toggle; the checker,
     * main.cpp wiring, and build_model.sh all pick it up from here with no other change.
     */
    inline constexpr DriverDescriptor kDriverRegistry[] = {
        { kClockDriverName,   "CONFIG_DRIVER_CLOCK",   DriverKind::Sensor },
        { kOutputDriverName,  "CONFIG_DRIVER_CONSOLE", DriverKind::Actuator },
        { kCellSetDriverName, "CONFIG_DRIVER_CELLSET", DriverKind::Mixed },
        { kCronDriverName,    "CONFIG_DRIVER_CRON",    DriverKind::Sensor },
        { kSysmonDriverName,  "CONFIG_DRIVER_SYSMON",  DriverKind::Sensor },
    };

    /** @return The number of entries in kDriverRegistry. */
    inline constexpr size_t kDriverRegistryCount =
        sizeof( kDriverRegistry ) / sizeof( kDriverRegistry[ 0 ] );

    /** Case-insensitive lookup of a driver name in the registry.
     *
     * @param name Driver name to look up.
     * @return The matching descriptor, or nullptr if `name` isn't a native driver.
     */
    inline const DriverDescriptor * findDriver( std::string_view name )
    {
        for( size_t i = 0; i < kDriverRegistryCount; ++i )
        {
            if( naxe::util::asciiEqualsIgnoreCase( kDriverRegistry[ i ].name, name ) )
            {
                return &kDriverRegistry[ i ];
            }
        }
        return nullptr;
    }
} // namespace drivers
