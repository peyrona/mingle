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
/** \file driver_manager.hpp
 *
 * The registry of drivers named in the loaded model, and their read/write ops.
 *
 * Per the plan: "collapse DriverManager into a fixed compile-time table" — one firmware
 * image has one fixed set of drivers. `DriverOps` (two function pointers + context) stands
 * in for Java's `IController` abstraction, avoiding virtual dispatch.
 *
 * Tier 1 (hardware-agnostic): no FreeRTOS/ESP-IDF dependency — the ops' bodies may use
 * ESP-IDF peripherals internally, but that's inside their own functions.
 */
#pragma once

#include <cstdint>
#include <string_view>

#include "cil/model.hpp"
#include "naxe/value.hpp"

namespace stick
{
    /** Function-pointer driver operations, standing in for Java's IController. */
    struct DriverOps
    {
        void ( *write )( void * ctx, std::string_view deviceName, const naxe::Value & value ); // writes value to an actuator; a no-op function for a read-only (Sensor) driver
        void ( *read )( void * ctx, std::string_view deviceName );  // requests a fresh read from a sensor; a no-op function for a write-only (Actuator) driver
        void * ctx;  // opaque context forwarded unchanged to write()/read()
    };

    /** One registry slot: a model-named driver and its optional registered ops. */
    struct DriverEntry
    {
        std::string_view name;
        std::string_view script; // associated script name (informational until a real controller exists)
        bool hasOps;
        DriverOps ops;
    };

    /** The registry of drivers named in the loaded model. */
    class DriverManager
    {
    public:
        static constexpr uint32_t kMaxDrivers = 16;

        /** Constructs an empty DriverManager. */
        DriverManager();

        /** Loads a cil::Driver's name/script mapping from the model. */
        bool add( const cil::Driver & parsed );

        /** Registers real read/write ops for an already-added driver name. */
        bool registerOps( std::string_view driverName, const DriverOps & ops );

        /** Case-insensitive lookup by name. */
        const DriverEntry * named( std::string_view name ) const;

        /** Removes a driver by name, compacting the array. */
        bool remove( std::string_view name );

        /** @return The number of drivers held. */
        uint32_t count() const { return count_; }
        /** Const access to the driver at `index`. */
        const DriverEntry & at( uint32_t index ) const { return drivers_[ index ]; }

    private:
        DriverEntry drivers_[ kMaxDrivers ];
        uint32_t count_;
    };
} // namespace stick
