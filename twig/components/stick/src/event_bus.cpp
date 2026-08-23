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
/** \file event_bus.cpp
 * EventBus implementation (see event_bus.hpp).
 */
#include "stick/event_bus.hpp"

namespace stick
{
    EventBus::EventBus( uint32_t queueCapacity, UBaseType_t taskPriority, uint32_t taskStackWords )
        : queue_( xQueueCreate( queueCapacity, sizeof( Message ) ) )
        , consumerTask_( nullptr )
        , taskPriority_( taskPriority )
        , taskStackWords_( taskStackWords )
        , listenerCount_( 0 )
        , dispatchedCount_( 0 )
        , bootTick_( xTaskGetTickCount() )
    {
        configASSERT( queue_ != nullptr );

        for( uint32_t i = 0; i < kMaxPendingDelayed; ++i )
        {
            pending_[ i ].used = false;
        }

        pendingMutex_ = xSemaphoreCreateMutex();
        configASSERT( pendingMutex_ != nullptr );
    }

    EventBus::~EventBus()
    {
        if( consumerTask_ != nullptr )
        {
            vTaskDelete( consumerTask_ );
        }
        vQueueDelete( queue_ );
        vSemaphoreDelete( pendingMutex_ );
    }

    bool EventBus::addListener( Listener fn, void * ctx, uint8_t typeMask )
    {
        if( listenerCount_ >= kMaxListeners )
        {
            return false;
        }
        listeners_[ listenerCount_ ] = ListenerEntry{ fn, ctx, typeMask };
        ++listenerCount_;
        return true;
    }

    void EventBus::start()
    {
        const BaseType_t ok = xTaskCreate( &EventBus::consumerTaskTrampoline, "EventBus",
                                           taskStackWords_, this, taskPriority_, &consumerTask_ );
        configASSERT( ok == pdPASS );
    }

    void EventBus::post( const Message & msg )
    {
        const BaseType_t ok = xQueueSend( queue_, &msg, portMAX_DELAY );
        configASSERT( ok == pdPASS );
    }

    void EventBus::postDelayed( const Message & msg, uint32_t delayMs )
    {
        if( delayMs == 0 )
        {
            post( msg );
            return;
        }

        xSemaphoreTake( pendingMutex_, portMAX_DELAY );

        int32_t slot = -1;
        for( uint32_t i = 0; i < kMaxPendingDelayed; ++i )
        {
            if( ! pending_[ i ].used )
            {
                slot = static_cast<int32_t>( i );
                break;
            }
        }

        if( slot >= 0 )
        {
            pending_[ slot ].used = true; // claimed now, under the lock -- fields filled in below
        }

        xSemaphoreGive( pendingMutex_ );

        configASSERT( slot >= 0 ); // delayed-post pool exhausted

        PendingSlot & ps = pending_[ slot ];
        ps.msg = msg;
        ps.bus = this;

        TimerHandle_t timer = xTimerCreate( "EvBusDelay", pdMS_TO_TICKS( delayMs ), pdFALSE,
                                            static_cast<void *>( &ps ),
                                            &EventBus::delayedPostTimerCallback );
        configASSERT( timer != nullptr );
        configASSERT( xTimerStart( timer, 0 ) == pdPASS );
    }

    uint32_t EventBus::pendingCount() const
    {
        return static_cast<uint32_t>( uxQueueMessagesWaiting( queue_ ) );
    }

    uint32_t EventBus::dispatchedPerMinute() const
    {
        const uint32_t elapsedMs = static_cast<uint32_t>( xTaskGetTickCount() - bootTick_ ) * portTICK_PERIOD_MS;
        const uint32_t minutes = elapsedMs / 60000u;
        const uint32_t divisor = minutes > 0 ? minutes : 1u;

        // Rounded, matching Java's Math.round( msgCount / minutes ).
        return static_cast<uint32_t>( ( dispatchedCount_ + divisor / 2 ) / divisor );
    }

    void EventBus::consumerTaskTrampoline( void * self )
    {
        static_cast<EventBus *>( self )->consumerLoop();
    }

    void EventBus::consumerLoop()
    {
        for( ;; )
        {
            Message msg;
            if( xQueueReceive( queue_, &msg, portMAX_DELAY ) == pdPASS )
            {
                dispatch( msg );
            }
        }
    }

    void EventBus::dispatch( const Message & msg )
    {
        const uint8_t bit = static_cast<uint8_t>( 1u << static_cast<uint8_t>( msg.type ) );

        for( uint32_t i = 0; i < listenerCount_; ++i )
        {
            if( listeners_[ i ].typeMask & bit )
            {
                listeners_[ i ].fn( listeners_[ i ].ctx, msg );
            }
        }

        ++dispatchedCount_; // once per dispatched message, matching Java's Dispatcher::msgCount
    }

    void EventBus::delayedPostTimerCallback( TimerHandle_t timer )
    {
        PendingSlot * slot = static_cast<PendingSlot *>( pvTimerGetTimerID( timer ) );
        EventBus * bus = slot->bus;

        bus->post( slot->msg );

        xSemaphoreTake( bus->pendingMutex_, portMAX_DELAY );
        slot->used = false;
        xSemaphoreGive( bus->pendingMutex_ );

        xTimerDelete( timer, 0 );
    }
} // namespace stick
