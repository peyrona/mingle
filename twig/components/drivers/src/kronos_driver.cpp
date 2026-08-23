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
/** \file kronos_driver.cpp
 * The cron sensor driver (see kronos_driver.hpp).
 */
#include "drivers/kronos_driver.hpp"

#include "naxe/datetime.hpp"
#include "naxe/util.hpp"
#include "stick/logger.hpp"

namespace drivers
{
    KronosDriver::KronosDriver( stick::EventBus & bus )
        : bus_( &bus )
        , slots_()
        , count_( 0 )
    {
    }

    KronosDriver::~KronosDriver()
    {
        for( uint32_t i = 0; i < count_; ++i )
        {
            if( slots_[ i ].timer != nullptr )
            {
                xTimerDelete( slots_[ i ].timer, 0 );
            }
        }
    }

    KronosDriver::Slot * KronosDriver::find( std::string_view name )
    {
        for( uint32_t i = 0; i < count_; ++i )
        {
            if( naxe::util::asciiEqualsIgnoreCase( slots_[ i ].name, name ) )
            {
                return &slots_[ i ];
            }
        }
        return nullptr;
    }

    bool KronosDriver::add( const cil::Device & parsed )
    {
        if( count_ >= kMaxCrons )
        {
            stick::logger().log( stick::LogLevel::Severe,
                                 "Kronos: table full (max %u), cannot add '%.*s'",
                                 static_cast<unsigned>( kMaxCrons ),
                                 static_cast<int>( parsed.name.size() ), parsed.name.data() );
            return false;
        }

        Slot & slot = slots_[ count_ ];
        slot.name = parsed.name;
        slot.bus = bus_;
        slot.cron.configure( *parsed.config.pairValue() );

        if( ! slot.cron.isValid() )
        {
            for( uint32_t i = 0; i < slot.cron.errorCount(); ++i )
            {
                const std::string_view msg = slot.cron.errorAt( i );
                stick::logger().log( stick::LogLevel::Severe, "Kronos: '%.*s': %.*s",
                                     static_cast<int>( parsed.name.size() ), parsed.name.data(),
                                     static_cast<int>( msg.size() ), msg.data() );
            }
            return false;
        }

        slot.timer = xTimerCreate( "kronos", pdMS_TO_TICKS( 1 ), pdFALSE, // one-shot
                                   static_cast<void *>( &slot ), &KronosDriver::timerCallback );
        configASSERT( slot.timer != nullptr );

        ++count_;
        scheduleNext( slot );
        return true;
    }

    void KronosDriver::scheduleNext( Slot & slot )
    {
        const int64_t delayMs = slot.cron.next();

        if( delayMs < 0 )
        {
            return; // no next occurrence -- the timer stays wherever it was (stopped or idle)
        }

        slot.remainingMs = static_cast<uint64_t>( delayMs );
        armChunk( slot );
    }

    void KronosDriver::armChunk( Slot & slot )
    {
        uint64_t chunk = slot.remainingMs > kMaxTimerDelayMs ? kMaxTimerDelayMs : slot.remainingMs;
        if( chunk == 0 ) chunk = 1; // a FreeRTOS timer period must be at least one tick

        xTimerChangePeriod( slot.timer, pdMS_TO_TICKS( chunk ), 0 );
        xTimerStart( slot.timer, 0 );
    }

    void KronosDriver::onChunkElapsed( Slot & slot )
    {
        const uint64_t chunk = slot.remainingMs > kMaxTimerDelayMs ? kMaxTimerDelayMs : slot.remainingMs;
        slot.remainingMs -= chunk;

        if( slot.remainingMs > 0 )
        {
            armChunk( slot );
            return;
        }

        slot.lastEpochMs = naxe::nowEpochSeconds() * 1000;
        slot.bus->post( stick::makeDeviceChanged( slot.name, naxe::Value::makeInt( slot.lastEpochMs ) ) );
        scheduleNext( slot ); // commit to the following occurrence, if any
    }

    void KronosDriver::read( std::string_view deviceName )
    {
        const Slot * slot = find( deviceName );

        if( slot != nullptr )
        {
            bus_->post( stick::makeDeviceChanged( deviceName, naxe::Value::makeInt( slot->lastEpochMs ) ) );
        }
    }

    void KronosDriver::write( std::string_view, const naxe::Value & )
    {
        // Read-only device: matches Java's Kronos::write(), which is intentionally empty.
    }

    stick::DriverOps KronosDriver::ops()
    {
        return stick::DriverOps{ &KronosDriver::writeTrampoline, &KronosDriver::readTrampoline, this };
    }

    void KronosDriver::timerCallback( TimerHandle_t timer )
    {
        onChunkElapsed( *static_cast<Slot *>( pvTimerGetTimerID( timer ) ) );
    }

    void KronosDriver::readTrampoline( void * ctx, std::string_view deviceName )
    {
        static_cast<KronosDriver *>( ctx )->read( deviceName );
    }

    void KronosDriver::writeTrampoline( void * ctx, std::string_view deviceName, const naxe::Value & value )
    {
        static_cast<KronosDriver *>( ctx )->write( deviceName, value );
    }
} // namespace drivers
