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
/** \file console_driver.hpp
 *
 * A serial console output driver: its write op prints the value it receives (the
 * "console" actuator behind `01.clock.model`'s `console` device).
 */
#pragma once

#include <string_view>

#include "naxe/value.hpp"
#include "stick/driver_manager.hpp"
#include "stick/event_bus.hpp"

namespace drivers
{
    /** @return The read/write ops for a serial-console output device: prints the value,
     *  then echoes it back onto `bus` as a DeviceChanged message -- matches Java's
     *  sendChanged(getDeviceName(), value) at the end of OutputStream.write(), an
     *  actuator confirming its new state took effect. Works for any device routed to
     *  this driver (the device name is whatever the caller passes to write()), not just
     *  a single hardcoded name.
     *
     * @param bus The EventBus to post DeviceChanged onto after every write. Must
     *            outlive this DriverOps' registration.
     */
    stick::DriverOps consoleOps( stick::EventBus & bus );
} // namespace drivers
