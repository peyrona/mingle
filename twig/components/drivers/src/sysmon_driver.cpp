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
/** \file sysmon_driver.cpp
 * The system monitor driver (see sysmon_driver.hpp).
 */
#include "drivers/sysmon_driver.hpp"

#include "esp_heap_caps.h"
#include "freertos/idf_additions.h"
#include "naxe/pair.hpp"
#include "naxe/util.hpp"
#include "stick/logger.hpp"

namespace drivers
{
    SysmonDriver::SysmonDriver( stick::EventBus & bus )
        : bus_( &bus )
        , slots_()
        , count_( 0 )
    {
    }

    SysmonDriver::~SysmonDriver()
    {
        for( uint32_t i = 0; i < count_; ++i )
        {
            if( slots_[ i ].timer != nullptr )
            {
                xTimerDelete( slots_[ i ].timer, 0 );
            }
        }
    }

    SysmonDriver::Slot * SysmonDriver::find( std::string_view name )
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

    bool SysmonDriver::parseConfig( std::string_view deviceName, const naxe::Value & config, Slot & slot )
    {
        const naxe::Pair * cfg = config.pairValue();

        std::string_view metricStr = "cpu";
        if( const naxe::Value * v = cfg->get( "metric" ); v != nullptr && v->isString() )
        {
            metricStr = v->stringValue();
        }

        bool metricOk = true;

        if( naxe::util::asciiEqualsIgnoreCase( metricStr, "speed" ) )        slot.metric = Metric::Speed;
        else if( naxe::util::asciiEqualsIgnoreCase( metricStr, "pending" ) ) slot.metric = Metric::Pending;
        else if( naxe::util::asciiEqualsIgnoreCase( metricStr, "cpu" ) )     slot.metric = Metric::Cpu;
        else if( naxe::util::asciiEqualsIgnoreCase( metricStr, "jvmram" ) )  slot.metric = Metric::JvmRam;
        else if( naxe::util::asciiEqualsIgnoreCase( metricStr, "disk" ) )    slot.metric = Metric::Disk;
        else metricOk = false;

        if( ! metricOk )
        {
            stick::logger().log( stick::LogLevel::Severe,
                                 "Sysmon: '%.*s': invalid 'metric' \"%.*s\" (valid: speed, pending, cpu, jvmram, disk)",
                                 static_cast<int>( deviceName.size() ), deviceName.data(),
                                 static_cast<int>( metricStr.size() ), metricStr.data() );
        }

        std::string_view measureStr = "used%";
        if( const naxe::Value * v = cfg->get( "measure" ); v != nullptr && v->isString() )
        {
            measureStr = v->stringValue();
        }

        bool measureOk = true;

        if( naxe::util::asciiEqualsIgnoreCase( measureStr, "used" ) )       slot.measure = Measure::Used;
        else if( naxe::util::asciiEqualsIgnoreCase( measureStr, "used%" ) ) slot.measure = Measure::UsedPercent;
        else if( naxe::util::asciiEqualsIgnoreCase( measureStr, "free" ) )  slot.measure = Measure::Free;
        else if( naxe::util::asciiEqualsIgnoreCase( measureStr, "free%" ) ) slot.measure = Measure::FreePercent;
        else measureOk = false;

        if( ! measureOk )
        {
            stick::logger().log( stick::LogLevel::Severe,
                                 "Sysmon: '%.*s': invalid 'measure' \"%.*s\" (valid: used, used%%, free, free%%)",
                                 static_cast<int>( deviceName.size() ), deviceName.data(),
                                 static_cast<int>( measureStr.size() ), measureStr.data() );
        }

        int64_t intervalMs = 1000;
        if( const naxe::Value * v = cfg->get( "interval" ); v != nullptr )
        {
            if( v->isInt() ) intervalMs = v->intValue();
            else if( v->isFloat() ) intervalMs = static_cast<int64_t>( v->floatValue() );
        }

        if( intervalMs < static_cast<int64_t>( kMinIntervalMs ) )
        {
            stick::logger().log( stick::LogLevel::Warning,
                                 "Sysmon: '%.*s': 'interval' was out of range. Changed from %lld to %u",
                                 static_cast<int>( deviceName.size() ), deviceName.data(),
                                 static_cast<long long>( intervalMs ), static_cast<unsigned>( kMinIntervalMs ) );
            intervalMs = kMinIntervalMs;
        }

        slot.intervalMs = static_cast<uint32_t>( intervalMs );

        return metricOk && measureOk;
    }

    bool SysmonDriver::add( const cil::Device & parsed )
    {
        if( count_ >= kMaxSlots )
        {
            stick::logger().log( stick::LogLevel::Severe,
                                 "Sysmon: table full (max %u), cannot add '%.*s'",
                                 static_cast<unsigned>( kMaxSlots ),
                                 static_cast<int>( parsed.name.size() ), parsed.name.data() );
            return false;
        }

        Slot & slot = slots_[ count_ ];
        slot.name = parsed.name;
        slot.bus = bus_;

        if( ! parseConfig( parsed.name, parsed.config, slot ) )
        {
            return false;
        }

        slot.timer = xTimerCreate( "sysmon", pdMS_TO_TICKS( kInitialDelayMs ), pdTRUE, // auto-reload
                                   static_cast<void *>( &slot ), &SysmonDriver::timerCallback );
        configASSERT( slot.timer != nullptr );
        configASSERT( xTimerStart( slot.timer, 0 ) == pdPASS );

        ++count_;
        return true;
    }

    void SysmonDriver::onTimer( Slot & slot )
    {
        if( ! slot.firstFireDone )
        {
            slot.firstFireDone = true;
            xTimerChangePeriod( slot.timer, pdMS_TO_TICKS( slot.intervalMs ), 0 ); // recurring period from here on
        }

        sample( slot );
    }

    bool SysmonDriver::sampleCpuLoad( Slot & slot, float & outUsedFraction )
    {
        const TickType_t nowTick = xTaskGetTickCount();

        uint32_t idleNow[ portNUM_PROCESSORS ];
        for( int core = 0; core < portNUM_PROCESSORS; ++core )
        {
            idleNow[ core ] = static_cast<uint32_t>( ulTaskGetIdleRunTimeCounterForCore( core ) );
        }

        bool ok = false;

        if( slot.hasPrevCpuSample )
        {
            const uint32_t elapsedTicks = static_cast<uint32_t>( nowTick - slot.prevCpuTick );

            // The run-time-stats clock is 1MHz (CONFIG_FREERTOS_RUN_TIME_STATS_USING_ESP_TIMER,
            // the Kconfig default selected alongside CONFIG_FREERTOS_GENERATE_RUN_TIME_STATS --
            // see Kconfig) -- 1000 counter units per elapsed millisecond.
            const uint64_t elapsedRunTimeUnits = static_cast<uint64_t>( elapsedTicks ) * portTICK_PERIOD_MS * 1000ULL;

            if( elapsedRunTimeUnits > 0 )
            {
                uint64_t idleSum = 0;
                for( int core = 0; core < portNUM_PROCESSORS; ++core )
                {
                    idleSum += static_cast<uint32_t>( idleNow[ core ] - slot.prevCpuIdle[ core ] ); // unsigned: wraps correctly
                }

                const uint64_t capacity = elapsedRunTimeUnits * static_cast<uint64_t>( portNUM_PROCESSORS );
                const float idleFraction = static_cast<float>( idleSum ) / static_cast<float>( capacity );
                outUsedFraction = idleFraction >= 1.0f ? 0.0f : ( 1.0f - idleFraction );
                ok = true;
            }
        }

        slot.prevCpuTick = nowTick;
        for( int core = 0; core < portNUM_PROCESSORS; ++core ) slot.prevCpuIdle[ core ] = idleNow[ core ];
        slot.hasPrevCpuSample = true;

        return ok;
    }

    void SysmonDriver::postMeasured( Slot & slot, int64_t used, int64_t free, int64_t total )
    {
        switch( slot.measure )
        {
            case Measure::Used:
                slot.bus->post( stick::makeDeviceChanged( slot.name, naxe::Value::makeInt( used ) ) );
                break;

            case Measure::Free:
                slot.bus->post( stick::makeDeviceChanged( slot.name, naxe::Value::makeInt( free ) ) );
                break;

            case Measure::UsedPercent:
                slot.bus->post( stick::makeDeviceChanged( slot.name,
                    naxe::Value::makeFloat( ( static_cast<float>( used ) / static_cast<float>( total ) ) * 100.0f ) ) );
                break;

            case Measure::FreePercent:
                slot.bus->post( stick::makeDeviceChanged( slot.name,
                    naxe::Value::makeFloat( 100.0f - ( static_cast<float>( used ) / static_cast<float>( total ) ) * 100.0f ) ) );
                break;
        }
    }

    void SysmonDriver::sample( Slot & slot )
    {
        switch( slot.metric )
        {
            case Metric::Speed:
                slot.bus->post( stick::makeDeviceChanged( slot.name, naxe::Value::makeInt( slot.bus->dispatchedPerMinute() ) ) );
                break;

            case Metric::Pending:
                slot.bus->post( stick::makeDeviceChanged( slot.name, naxe::Value::makeInt( slot.bus->pendingCount() ) ) );
                break;

            case Metric::Cpu:
            {
                float usedFraction = 0.0f;

                if( ! sampleCpuLoad( slot, usedFraction ) )
                {
                    stick::logger().log( stick::LogLevel::Info,
                                         "Sysmon: '%.*s': no CPU baseline yet, skipping this sample",
                                         static_cast<int>( slot.name.size() ), slot.name.data() );
                    break;
                }

                const int usedPercent = static_cast<int>( usedFraction * 100.0f );
                const bool wantsUsed = ( slot.measure == Measure::Used || slot.measure == Measure::UsedPercent );

                slot.bus->post( stick::makeDeviceChanged( slot.name,
                    naxe::Value::makeInt( wantsUsed ? usedPercent : ( 100 - usedPercent ) ) ) );
                break;
            }

            case Metric::JvmRam:
            {
                const int64_t free_  = static_cast<int64_t>( heap_caps_get_free_size( MALLOC_CAP_DEFAULT ) );
                const int64_t total_ = static_cast<int64_t>( heap_caps_get_total_size( MALLOC_CAP_DEFAULT ) );
                postMeasured( slot, total_ - free_, free_, total_ );
                break;
            }

            case Metric::Disk:
                // Always -1: no filesystem-space API is ported for this target -- see
                // this file's header comment ("disk" design departure).
                slot.bus->post( stick::makeDeviceChanged( slot.name, naxe::Value::makeInt( -1 ) ) );
                break;
        }
    }

    void SysmonDriver::read( std::string_view deviceName )
    {
        Slot * slot = find( deviceName );

        if( slot != nullptr )
        {
            sample( *slot );
        }
    }

    void SysmonDriver::write( std::string_view deviceName, const naxe::Value & )
    {
        stick::logger().log( stick::LogLevel::Warning, "Sysmon: '%.*s' is read-only, write ignored",
                             static_cast<int>( deviceName.size() ), deviceName.data() );
    }

    stick::DriverOps SysmonDriver::ops()
    {
        return stick::DriverOps{ &SysmonDriver::writeTrampoline, &SysmonDriver::readTrampoline, this };
    }

    void SysmonDriver::timerCallback( TimerHandle_t timer )
    {
        onTimer( *static_cast<Slot *>( pvTimerGetTimerID( timer ) ) );
    }

    void SysmonDriver::readTrampoline( void * ctx, std::string_view deviceName )
    {
        static_cast<SysmonDriver *>( ctx )->read( deviceName );
    }

    void SysmonDriver::writeTrampoline( void * ctx, std::string_view deviceName, const naxe::Value & value )
    {
        static_cast<SysmonDriver *>( ctx )->write( deviceName, value );
    }
} // namespace drivers
