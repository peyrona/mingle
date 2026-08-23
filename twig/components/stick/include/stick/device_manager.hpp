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
/** \file device_manager.hpp
 *
 * The registry of all runtime Devices: a fixed-capacity array with case-insensitive
 * linear-scan lookup. Devices are added once at boot; dispatch is single-threaded, so no
 * concurrent-access guard is needed.
 *
 * Tier 1 (hardware-agnostic): no FreeRTOS/ESP-IDF dependency.
 */
#pragma once

#include <cstdint>
#include <string_view>

#include "cil/model.hpp"
#include "stick/device.hpp"

namespace stick
{
    /** The registry of all runtime Devices. */
    class DeviceManager
    {
    public:
        /** Covers every real deployed model in the corpus several times over. */
        static constexpr uint32_t kMaxDevices = 64;

        /** Constructs an empty DeviceManager. */
        DeviceManager();

        /** Builds a runtime Device from `parsed` and adds it.
         *
         * @return true if added; false if a device of the same name exists or the table
         *         is full.
         */
        bool add( const cil::Device & parsed );

        /** Case-insensitive lookup by name. */
        Device * named( std::string_view name );
        /** Const overload. */
        const Device * named( std::string_view name ) const;

        /** Removes the named device, compacting the array (invalidates held pointers). */
        bool remove( std::string_view name );

        /** @return The number of devices held. */
        uint32_t count() const { return count_; }
        /** The device at `index` (must be < count()). */
        Device & at( uint32_t index ) { return devices_[ index ]; }
        /** Const access. */
        const Device & at( uint32_t index ) const { return devices_[ index ]; }

        /** Writes the names of every device belonging to `group` into `out` (capped).
         *
         * @return The number of names written.
         */
        uint32_t getGroupMemberNames( std::string_view group, std::string_view * out,
                                      uint32_t outCapacity ) const;

    private:
        Device devices_[ kMaxDevices ];
        uint32_t count_;
    };
} // namespace stick
