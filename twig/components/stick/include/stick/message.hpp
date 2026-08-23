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
/** \file message.hpp
 *
 * The fixed-size message passed through the EventBus (built in a later phase), and its
 * factory helpers. One POD struct tagged by `MsgType`, not a class hierarchy — it must be
 * trivially copyable because FreeRTOS `xQueue` copies items by `memcpy` (see the
 * `static_assert` below).
 *
 * `name` is a non-owning `std::string_view` into stable storage (device/rule/script
 * names from the loaded model), never a per-cycle arena string: a queued message can be
 * read by the consumer after the producer returns.
 *
 * Tier 1 (hardware-agnostic): no FreeRTOS/ESP-IDF dependency — only the EventBus (which
 * wraps the queue) is FreeRTOS-dependent.
 */
#pragma once

#include <cstdint>
#include <string_view>
#include <type_traits>

#include "naxe/value.hpp"

namespace stick
{
    /** Message kinds dispatched through the EventBus. */
    enum class MsgType : uint8_t
    {
        DeviceChanged,  // a driver reports a device has a new value
        ChangeActuator, // the virtual world requests a change to a physical actuator
        ReadDevice,     // requests the current value be re-read from the physical device
        Execute,        // run a SCRIPT's or RULE's actions unconditionally
        TemporalCheck,  // an AFTER/WITHIN window's deadline arrived — re-evaluate rules
                        // even though no device changed (carries no meaningful payload)
        ControlIn       // one newline-stripped line arrived over the control channel;
                        // `name` (NOT owned, see the struct-level note) views
                        // ControlChannel's own stable line buffer, valid only until the
                        // consumer calls ControlChannel::ackLineProcessed() — the
                        // listener must finish reading it (typically via
                        // stick::parseExenComm(), which copies everything it needs into
                        // its own arena) BEFORE acking, never after
    };

    /** One fixed-size POD message passed by value through the EventBus's queue.
     *
     * `name` is NOT owned and is not NUL-terminated (see the header note); `value` may
     * reference heap/arena-backed List/Pair with the same lifetime caution.
     */
    struct Message
    {
        MsgType type;
        int64_t when;          // epoch milliseconds
        std::string_view name; // device/rule/script name; NOT owned
        bool isOwn;            // true: generated in this node
        bool hasValue;         // false only for ReadDevice
        naxe::Value value;     // meaningful only when hasValue
    };

    // Message must remain memcpy-safe for the FreeRTOS queue.
    static_assert( std::is_trivially_copyable_v<Message>,
                   "Message must be trivially copyable (xQueue memcpy)" );

    /** Builds a DeviceChanged message: a driver reports a device has a new value. */
    Message makeDeviceChanged( std::string_view device, const naxe::Value & newValue, bool isOwn = true );

    /** Builds a ChangeActuator message: a request to change a physical actuator. */
    Message makeChangeActuator( std::string_view device, const naxe::Value & newValue, bool isOwn = true );

    /** Builds a ReadDevice message: requests the current value be re-read. */
    Message makeReadDevice( std::string_view device, bool isOwn = true );

    /** Builds an Execute message: run a SCRIPT's or RULE's actions unconditionally. */
    Message makeExecute( std::string_view scriptOrRuleName, bool force, bool isOwn = true );

    /** Builds a TemporalCheck message: an AFTER/WITHIN window's deadline arrived.
     *
     * Carries no meaningful payload — the orchestrator just re-evaluates the rules.
     */
    Message makeTemporalCheck();

    /** Builds a ControlIn message: one line arrived over the control channel.
     *
     * @param line A view into ControlChannel's own stable line buffer -- see
     *             MsgType::ControlIn's own doc comment on this message's lifetime.
     */
    Message makeControlIn( std::string_view line );

    /** @return Current epoch time in milliseconds (whole-second resolution). */
    int64_t nowEpochMillis();
} // namespace stick
