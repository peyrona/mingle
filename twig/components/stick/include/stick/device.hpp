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
/** \file device.hpp
 *
 * Runtime Device state: the mutable layer over a parsed `cil::Device` — current value,
 * delta filtering, downtime detection, and groups.
 *
 * `cil::Device` is the static, load-time-parsed shape (name/driver/config/init spans);
 * this wraps it with everything Java's `Device`/`DeviceValue` do after construction.
 *
 * Tier 1 (hardware-agnostic): no FreeRTOS/ESP-IDF dependency.
 */
#pragma once

#include <cstdint>
#include <string_view>

#include "cil/model.hpp"
#include "naxe/value.hpp"

namespace stick
{
    /** Mutable runtime state for one Device. */
    class Device
    {
    public:
        /** Maximum number of groups a single device can belong to. */
        static constexpr uint32_t kMaxGroups = 8;

        /** Constructs an empty placeholder Device (for a fixed-capacity array slot). */
        Device();

        /** Constructs a runtime Device from its parsed model shape.
         *
         * @param parsed The static model shape this Device wraps (must outlive it).
         */
        explicit Device( const cil::Device & parsed );

        /** @return The device's name view into the stable model. */
        std::string_view name() const { return parsed_->name; }
        /** @return The device's driver name view into the stable model. */
        std::string_view driverName() const { return parsed_->driver; }

        /** @return True once the device has received its first value. */
        bool hasValue() const { return hasValue_; }
        /** @return The device's current value (asserts hasValue()). */
        const naxe::Value & value() const;

        /** Applies a newly-read device value (with delta filtering).
         *
         * @return true if the value was accepted — callers use this to decide whether to
         *         re-evaluate rules.
         */
        bool setValue( const naxe::Value & newValue );

        /** @return true if the device has not been updated within its downtime interval
         *         (resets the clock on fire so it reports once per interval). */
        bool isDowntimed();

        /** @return The number of groups this device belongs to. */
        uint32_t groupCount() const { return groupCount_; }
        /** @return True if the device belongs to `name` (case-insensitive). */
        bool isMemberOfGroup( std::string_view name ) const;

    private:
        /** Applies the numeric delta filter to a candidate new value. */
        bool isEnough( const naxe::Value & newValue );
        /** Slices the model's comma-separated groups string into per-group spans. */
        void parseGroups();

        const cil::Device * parsed_;

        naxe::Value value_;
        bool hasValue_;

        bool hasDelta_;
        float delta_;
        bool deltaErrorLogged_; // logs the non-numeric-delta warning once per device, not every setValue()

        int64_t downtimeSeconds_; // 0 == disabled
        int64_t lastSetEpochSeconds_;

        std::string_view groups_[ kMaxGroups ];
        uint32_t groupCount_;
    };
} // namespace stick
