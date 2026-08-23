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
/** \file clock_driver.cpp
 * Periodic clock driver (see clock_driver.hpp).
 */
#include "drivers/clock_driver.hpp"

#include <cstdio>

namespace drivers
{
    ClockDriver::ClockDriver( stick::EventBus & bus, std::string_view deviceName, uint32_t intervalMs,
                               ElapsedMsFn elapsedMs, void * elapsedMsCtx )
        : bus_( &bus )
        , deviceName_( deviceName )
        , intervalMs_( intervalMs )
        , elapsedMs_( elapsedMs )
        , elapsedMsCtx_( elapsedMsCtx )
        , timer_( nullptr )
    {
    }

    ClockDriver::~ClockDriver()
    {
        if( timer_ != nullptr )
        {
            xTimerDelete( timer_, 0 );
        }
    }

    void ClockDriver::start()
    {
        timer_ = xTimerCreate( "clock", pdMS_TO_TICKS( intervalMs_ ), pdTRUE, // auto-reload
                               static_cast<void *>( this ), &ClockDriver::timerCallback );
        configASSERT( timer_ != nullptr );
        configASSERT( xTimerStart( timer_, 0 ) == pdPASS );
    }

    stick::DriverOps ClockDriver::ops()
    {
        return stick::DriverOps{ &ClockDriver::writeTrampoline, &ClockDriver::readTrampoline, this };
    }

    void ClockDriver::timerCallback( TimerHandle_t timer )
    {
        static_cast<ClockDriver *>( pvTimerGetTimerID( timer ) )->onTick();
    }

    void ClockDriver::onTick()
    {
        read( deviceName_ );
    }

    void ClockDriver::read( std::string_view deviceName )
    {
        const auto elapsedMs = static_cast<float>( elapsedMs_( elapsedMsCtx_ ) );
        bus_->post( stick::makeDeviceChanged( deviceName, naxe::Value::makeFloat( elapsedMs ) ) );
    }

    void ClockDriver::write( std::string_view deviceName, const naxe::Value & )
    {
        std::printf( "[clock] '%.*s' is read-only, write ignored\n",
                     static_cast<int>( deviceName.size() ), deviceName.data() );
    }

    void ClockDriver::readTrampoline( void * ctx, std::string_view deviceName )
    {
        static_cast<ClockDriver *>( ctx )->read( deviceName );
    }

    void ClockDriver::writeTrampoline( void * ctx, std::string_view deviceName, const naxe::Value & value )
    {
        static_cast<ClockDriver *>( ctx )->write( deviceName, value );
    }

    uint32_t ClockDriver::defaultElapsedMs( void * )
    {
        return static_cast<uint32_t>( xTaskGetTickCount() ) * portTICK_PERIOD_MS;
    }
} // namespace drivers
