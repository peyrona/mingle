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
/** \file clock_driver.hpp
 *
 * A periodic clock driver: a FreeRTOS timer that posts a `DeviceChanged` message with the
 * milliseconds elapsed since boot on every tick -- matches Java's `SystemClock.java`
 * (`sendReaded(UtilSys.elapsed())`) and twig's own port of it. This is the "clock" sensor
 * behind `01.clock.model`'s `clock` device.
 *
 * FreeRTOS-dependent (owns a software timer), so this runs on-device only.
 */
#pragma once

#include <cstdint>
#include <string_view>

#include "stick/driver_manager.hpp"
#include "stick/event_bus.hpp"
#include "stick/message.hpp"

namespace drivers
{
    /** Signature for querying elapsed time since boot, standing in for
     * xTaskGetTickCount() -- injected so host tests can supply a controllable clock
     * instead of a real FreeRTOS timer tick.
     *
     * @param ctx Caller-supplied context pointer, passed through unchanged.
     * @return Milliseconds elapsed since some fixed reference point (real boot time in
     *         the default implementation).
     */
    using ElapsedMsFn = uint32_t ( * )( void * ctx );

    /** A periodic clock sensor: posts elapsed-ms-since-boot to the bus every `intervalMs`. */
    class ClockDriver
    {
    public:
        /** Constructs the driver.
         *
         * @param bus The EventBus to post to (must outlive this driver).
         * @param deviceName The device name to report (a view into stable model storage).
         * @param intervalMs Tick period in milliseconds.
         * @param elapsedMs Called once per tick; defaults to a real
         *                  xTaskGetTickCount()-based implementation.
         * @param elapsedMsCtx Passed through unchanged to elapsedMs.
         */
        ClockDriver( stick::EventBus & bus, std::string_view deviceName, uint32_t intervalMs,
                     ElapsedMsFn elapsedMs = &ClockDriver::defaultElapsedMs, void * elapsedMsCtx = nullptr );

        /** Deletes the timer (production firmware never destructs its one driver). */
        ~ClockDriver();

        ClockDriver( const ClockDriver & ) = delete;
        ClockDriver & operator=( const ClockDriver & ) = delete;

        /** Creates and starts the periodic timer. */
        void start();

        /** Returns stick::DriverOps bound to this instance, ready for
         *  DriverManager::registerOps() -- lets DriverManager route an explicit
         *  ReadDevice request to this driver, not just its own periodic push. */
        stick::DriverOps ops();

    private:
        static void timerCallback( TimerHandle_t timer );
        void onTick();

        /** Posts the current elapsed-ms reading for `deviceName` (the caller-supplied
         *  name from the read request, matching ConsoleDriver's own generic-by-name
         *  convention -- not necessarily this instance's own deviceName_, in case one
         *  driver registration ever serves more than one device). */
        void read( std::string_view deviceName );

        /** Read-only device: logs and ignores. */
        void write( std::string_view deviceName, const naxe::Value & value );

        /** DriverOps trampolines -- ctx is always the ClockDriver instance. */
        static void readTrampoline( void * ctx, std::string_view deviceName );
        static void writeTrampoline( void * ctx, std::string_view deviceName, const naxe::Value & value );

        /** Default ElapsedMsFn: xTaskGetTickCount() converted to milliseconds -- real
         *  elapsed time since the FreeRTOS scheduler started. */
        static uint32_t defaultElapsedMs( void * ctx );

        stick::EventBus * bus_;
        std::string_view deviceName_;
        uint32_t intervalMs_;
        ElapsedMsFn elapsedMs_;
        void * elapsedMsCtx_;
        TimerHandle_t timer_;
    };
} // namespace drivers
