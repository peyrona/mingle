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
/** \file orchestrator.hpp
 *
 * Orchestrator: the central message-routing hub wiring EventBus to the managers (ported
 * from Stick.java's inner `BusListener`). The managers are passive registries; this class
 * queries/mutates them in response to messages.
 *
 * Scope: DeviceChanged (update device + evaluate rules), ChangeActuator (dispatch to the
 * device's driver write op), Execute (trigger rule/script), ReadDevice (dispatch to the
 * driver read op), ControlIn (parse and dispatch one control-channel line -- see
 * handleControlIn()'s own doc comment).
 *
 * FreeRTOS-dependent only via EventBus (attach()); `handleMessage()` and everything it
 * calls is pure logic and testable without a running scheduler.
 */
#pragma once

#include <cstdint>

#include "cil/model.hpp"
#include "naxe/arena.hpp"
#include "stick/action.hpp"
#include "stick/control_channel.hpp"
#include "stick/device_manager.hpp"
#include "stick/driver_manager.hpp"
#include "stick/event_bus.hpp"
#include "stick/exen_comm.hpp"
#include "stick/message.hpp"
#include "stick/rule_manager.hpp"
#include "stick/script_manager.hpp"

namespace stick
{
    /** The central message-routing hub wiring EventBus to the managers. */
    class Orchestrator
    {
    public:
        /** Constructs the orchestrator over its managers, bus and arenas.
         *
         * All references must outlive this Orchestrator. `modelArena` is the long-lived
         * stable pool for model ASTs/strings -- also where addOneCommand() copies each
         * incoming command before handing it to a manager (Device/Rule/Script store a
         * pointer to the cil struct they were built from, so it must outlive them; a
         * command arriving from the control channel or a caller's stack-local array does
         * not, on its own). `cycleArenaBuf` backs the per-message-cycle Environment/eval
         * arena (reset every cycle). `controlChannel` is nullable (default nullptr):
         * callers that don't need live control-channel Add/Remove/reply support can omit
         * it -- handleControlIn()'s ack/reply calls are no-ops without one.
         */
        Orchestrator( DeviceManager & devices, RuleManager & rules, ScriptManager & scripts,
                      DriverManager & drivers, EventBus & bus, naxe::Arena & modelArena,
                      uint8_t * cycleArenaBuf, size_t cycleArenaSize,
                      ControlChannel * controlChannel = nullptr );

        /** Registers this orchestrator as the bus's single root listener. Call once,
         * before bus.start(). */
        void attach();

        /** Boots from a parsed model's command array, in Script -> Driver -> Device ->
         *  Rule order. */
        void boot( const cil::Command * commands, uint32_t count );

        /** Handles one message directly (what attach()'s listener calls internally). */
        void handleMessage( const Message & msg );

    private:
        void handleDeviceChanged( const Message & msg );
        void handleChangeActuator( const Message & msg );
        void handleExecute( const Message & msg );
        void handleReadDevice( const Message & msg );
        void handleTemporalCheck( const Message & msg );

        /** Handles a ControlIn message: parses msg.name as one ExEnComm line and
         *  dispatches on its request verb.
         *
         * Calls stick::parseExenComm(), then ackLineProcessed() IMMEDIATELY (the line
         * buffer is not needed past that call -- see MsgType::ControlIn's own doc
         * comment), THEN dispatches. Change/Read/Execute/Changed/Readed are NOT
         * re-posted onto EventBus -- this method already runs ON the bus consumer task
         * (it's called from handleMessage(), itself the bus listener), so it calls
         * handleChangeActuator()/handleReadDevice()/handleExecute()/handleDeviceChanged()
         * directly, reusing their existing driver-dispatch/rule-trigger logic instead of
         * paying for an unnecessary queue round-trip. Read additionally replies
         * immediately with the device's cached value (if it has one) before triggering a
         * fresh read -- a legitimate two-reply sequence for one request, matching
         * GridManager.ServerListener's own `case Read:`.
         */
        void handleControlIn( const Message & msg );

        /** Adds one command to the matching manager.
         *
         * Copies `cmd` into modelArena_ first -- see the constructor's doc comment on
         * why the manager's runtime object must be built from a stable, long-lived copy,
         * not the caller's own (possibly stack-local, possibly transient
         * control-channel-buffer-backed) argument.
         */
        bool addOneCommand( const cil::Command & cmd );

        /** Removes one command by name from the matching manager.
         *
         * @return false for an anonymous Rule (can't be addressed by name) or if no
         *         command with that name/type exists.
         */
        bool removeOneCommand( const cil::Command & cmd );

        /** Sends one already-built reply line over the control channel, best-effort.
         *
         * No-ops if controlChannel_ is null or body is empty (a buildXxx() returning 0
         * means "didn't fit/unsupported value type", see exen_comm.hpp).
         */
        void sendControlReply( std::string_view body );

        /** Builds the device-name -> current-Value Environment into cycleArena_. */
        naxe::Environment buildEnvironment();

        /** Evaluates the rules that reference `deviceName` and fires their actions. */
        void evaluateRulesForDevice( std::string_view deviceName );

        /** Evaluates every rule unconditionally (for TemporalCheck / Execute). */
        void evaluateAllRulesAndFireActions();

        /** Routes one fired action result to its target (actuator write or execute). */
        void onActionResult( const ActionResult & result );

        /** Writes a value to an actuator device, dispatching through its driver. */
        void writeActuator( std::string_view deviceName, const naxe::Value & value );

        static void busListenerTrampoline( void * ctx, const Message & msg );
        static void actionResultTrampoline( void * ctx, const ActionResult & result );
        static void scheduleWakeTrampoline( void * ctx, int64_t delayMs );

        DeviceManager * devices_;
        RuleManager * rules_;
        ScriptManager * scripts_;
        DriverManager * drivers_;
        EventBus * bus_;
        naxe::Arena * modelArena_;
        naxe::Arena cycleArena_;
        naxe::Pair * environment_;
        ControlChannel * controlChannel_;
    };
} // namespace stick
