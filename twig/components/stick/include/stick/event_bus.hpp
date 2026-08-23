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
/** \file event_bus.hpp
 *
 * EventBus: the single message-dispatch hub for this node. One bounded `xQueue` plus one
 * consumer FreeRTOS task, dispatching to a fixed, boot-registered listener table (matching
 * the JVM's already-single-threaded dispatch model).
 *
 * FreeRTOS task/queue/timer primitives are called directly (not via the PAL) — they are
 * already portable across FreeRTOS vendors.
 */
#pragma once

#include <cstdint>

#include "stick/message.hpp"

extern "C"
{
#include "freertos/FreeRTOS.h"
#include "freertos/queue.h"
#include "freertos/semphr.h"
#include "freertos/task.h"
#include "freertos/timers.h"
}

namespace stick
{
    /** EventBus message listener. Runs on the consumer task — keep it fast and
     *  non-blocking. */
    using Listener = void ( * )( void * ctx, const Message & msg );

    /** The single message-dispatch hub. */
    class EventBus
    {
    public:
        /** A typeMask value matching every message type. */
        static constexpr uint8_t kAllTypes = 0xFF;

        /** Constructs the queue (not the consumer task — see start()). */
        explicit EventBus( uint32_t queueCapacity,
                           UBaseType_t taskPriority = tskIDLE_PRIORITY + 1,
                           uint32_t taskStackWords = configMINIMAL_STACK_SIZE * 4 );

        /** Host-test-only teardown (production firmware never destructs its one bus). */
        ~EventBus();

        EventBus( const EventBus & ) = delete;
        EventBus & operator=( const EventBus & ) = delete;

        /** Registers a listener for the message types in `typeMask` (OR together
         *  `1u << (int)MsgType::X`, or pass kAllTypes). Call before start(). */
        bool addListener( Listener fn, void * ctx, uint8_t typeMask );

        /** Starts the consumer task. Call once, after registering listeners. */
        void start();

        /** Posts a message for immediate dispatch (safe from any task or timer). */
        void post( const Message & msg );

        /** Posts a message after `delayMs` via a one-shot software timer. */
        void postDelayed( const Message & msg, uint32_t delayMs );

        /** @return Number of messages currently queued, not yet dispatched. */
        uint32_t pendingCount() const;

        /** @return The approximate number of messages dispatched per minute since this
         *  bus was constructed (matches Java's `Dispatcher::getSpeed()`: total dispatched
         *  count divided by elapsed minutes, floored at 1 minute to avoid an inflated
         *  reading right after boot). Backs SystemMonitorDriver's "speed" metric. */
        uint32_t dispatchedPerMinute() const;

    private:
        static constexpr uint32_t kMaxListeners = 8;
        static constexpr uint32_t kMaxPendingDelayed = 8;

        struct ListenerEntry
        {
            Listener fn;
            void * ctx;
            uint8_t typeMask;
        };

        struct PendingSlot
        {
            Message msg;
            EventBus * bus;
            bool used;
        };

        void dispatch( const Message & msg );
        static void consumerTaskTrampoline( void * self );
        void consumerLoop();
        static void delayedPostTimerCallback( TimerHandle_t timer );

        QueueHandle_t queue_;
        TaskHandle_t consumerTask_;
        UBaseType_t taskPriority_;
        uint32_t taskStackWords_;
        ListenerEntry listeners_[ kMaxListeners ];
        uint32_t listenerCount_;
        PendingSlot pending_[ kMaxPendingDelayed ];
        uint64_t dispatchedCount_; // incremented once per dispatch() call -- see dispatchedPerMinute()
        TickType_t bootTick_;      // xTaskGetTickCount() at construction, this bus's "elapsed" reference

        // Guards pending_[]'s `used` flags: postDelayed() (consumer task) scans them
        // looking for a free slot to claim, while delayedPostTimerCallback() (the FreeRTOS
        // Timer Service task) clears one when its one-shot timer fires -- both without this
        // mutex would race on the same PendingSlot::used field.
        SemaphoreHandle_t pendingMutex_;
    };
} // namespace stick
