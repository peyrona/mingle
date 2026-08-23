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
/** \file control_channel.hpp
 *
 * ControlChannel: the outbound TCP client socket + framing + reconnect/heartbeat
 * machinery for the control channel (PLAN.md: "keep a remote channel post-deployment --
 * rule push / live state inspection -- not flash-and-forget"). Owns exactly one
 * pal::net_* connection (this device always dials a known host:port, never accepts
 * inbound connections) and one dedicated FreeRTOS task that both manages that
 * connection's lifecycle (connect, detect dead, reconnect-with-backoff) and reassembles
 * inbound bytes into newline-delimited lines (the real GridManager wire framing).
 *
 * Design: how a line gets from the socket to whoever handles it
 * ----------------------------------------------------------------
 * This class does NOT call stick::parseExenComm() itself, and does NOT know about
 * Orchestrator, DeviceManager, or any of the higher-level machinery those inbound lines
 * eventually drive -- it only knows "bytes arrived, here is one complete line." Parsing
 * happens on EventBus's single consumer task instead (via a MsgType::ControlIn listener,
 * Orchestrator), for the same reason DeviceManager/RuleManager/etc. are only ever
 * mutated from that one task: naxe::Arena::alloc() has no locking, and
 * cil::buildCommand()'s spans must end up in the SAME long-lived arena the rest of this
 * ExEn's runtime state already lives in -- mixing that in with the receive task's own
 * bytes-in-flight state would reintroduce the cross-thread-mutation risk this whole port
 * has otherwise designed away.
 *
 * That leaves one real question: HOW does a line survive the trip from "bytes reassembled
 * on the receive task" to "parsed by a listener running on a DIFFERENT task, at some
 * later, arbitrary time"? stick::Message is a fixed-size POD struct copied by value
 * through EventBus's xQueue -- it cannot carry a whole line's bytes inline. The chosen
 * design: ControlChannel owns ONE stable line buffer (lineBuf_, member storage, never
 * stack-local), reused for every inbound line. The receive task reassembles into it,
 * posts a MsgType::ControlIn message whose `name` views AT that buffer (see message.hpp's
 * own doc comment on this message type's unusual lifetime), then BLOCKS on a semaphore
 * before reading any more bytes. The listener -- on EventBus's consumer task, at its own
 * pace -- reads lineBuf_ (typically by immediately calling stick::parseExenComm(), which
 * copies everything it needs into its own long-lived arena before returning) and MUST
 * call ackLineProcessed() once done, which unblocks the receive task to read the next
 * line. This serializes control-channel throughput to one in-flight line at a time -- an
 * accepted, deliberate limitation: this is a low-traffic administrative channel
 * (occasional model pushes, device-change notifications), not AFTER/WITHIN's hot path, so
 * there's no real throughput to lose. Forgetting to ack is a real deadlock risk for
 * whoever writes that listener -- documented here and on MsgType::ControlIn itself, not
 * just once.
 *
 * Heartbeat: a bare blank line ("\n", zero JSON content) sent periodically on an
 * otherwise-idle connection, and silently skipped (not posted as a ControlIn message, not
 * treated as an error) when received -- a common, low-risk convention for line-oriented
 * protocols, and importantly NOT a new verb added to stick::exen_comm's JSON vocabulary,
 * so it can never collide with anything the real ExEnComm protocol does or might do
 * later. The receive task's own recv-timeout tick (not a second independent FreeRTOS
 * timer) is reused to check "is it time to send one" -- see kRecvTimeoutMs below.
 *
 * Reconnect-with-backoff runs on the same receive task: whenever the connection isn't
 * currently up, attempt pal::net_connect() (bounded by kConnectTimeoutMs); on failure,
 * sleep for the current backoff duration and double it (capped at kMaxBackoffMs) before
 * retrying; on success, reset the backoff and the line-reassembly state (a half-read line
 * from a dead connection is meaningless to a fresh one) before entering the normal recv
 * loop.
 *
 * Thread safety of the shared socket handle: sendLine() (called from whatever OTHER task
 * wants to publish a device-change notification, e.g. Orchestrator on EventBus's consumer
 * task) and the receive task's own heartbeat/reconnect logic both touch `handle_` and
 * both call pal::net_send() on it. Two independent net_send() calls racing on the same fd
 * could interleave their bytes mid-message, corrupting the newline-delimited framing -- a
 * real correctness issue, not a hypothetical one, so `handle_` plus every send/close/
 * reassign of it is protected by `handleMutex_`. This includes sendWithNewline()'s own
 * failure path: it closes and reassigns handle_ too, and it runs on WHICHEVER task called
 * sendLine() or maybeSendHeartbeat() -- so close/reassign is not confined to the receive
 * task, and every read of handle_ (not just isConnected()'s) goes through handleMutex_ as
 * a short take/copy/give, even on the receive task itself. The one deliberate exception is
 * the receive task's blocking pal::net_recv() call: it runs on a LOCAL copy of handle_
 * taken under the mutex moments before, with the mutex released for the call itself --
 * otherwise a slow/idle connection would starve sendLine() callers for the entire
 * kRecvTimeoutMs on every single poll. sendLine() briefly blocking (up to
 * kConnectTimeoutMs, worst case, if it happens to land while a reconnect's handle_
 * reassignment is in flight) is an accepted, honest tradeoff for a "low-traffic
 * administrative channel," not a bug -- and so is the receive task's local copy racing a
 * concurrent sendWithNewline() close (recv() on a handle number that's just been closed by
 * another task): the OS call fails safely, the receive task's own next mutex-protected read
 * observes the reassigned handle_, and it reconnects.
 *
 * Tier 1/2 split: FreeRTOS-dependent (owns a real task and a real semaphore, wraps
 * pal::net_* which itself wraps real blocking sockets) -- same category as event_bus.hpp.
 */
#pragma once

#include <cstddef>
#include <cstdint>
#include <string_view>

#include "pal/net.hpp"
#include "stick/event_bus.hpp"
#include "stick/logger.hpp"

extern "C"
{
#include "freertos/FreeRTOS.h"
#include "freertos/semphr.h"
#include "freertos/task.h"
}

namespace stick
{
    /** Outbound TCP client for the control channel.
     *
     * Owns one pal::net_* connection, its lifecycle (connect, detect dead,
     * reconnect-with-backoff) and a dedicated FreeRTOS task that reassembles inbound
     * bytes into newline-delimited lines. See this file's header note for the full
     * design rationale.
     */
    class ControlChannel
    {
    public:
        /** Constructs the channel's peer configuration and task parameters.
         *
         * @param bus The EventBus inbound ControlIn messages are posted to. Must
         *            outlive this ControlChannel and already have start() called (or be
         *            started before this channel's own receive task begins posting).
         * @param host The control-channel peer host (must outlive this instance).
         * @param port The control-channel peer TCP port.
         * @param taskPriority Priority of the receive/reconnect task.
         * @param taskStackWords Stack depth (in words) of the receive/reconnect task.
         */
        ControlChannel( EventBus & bus, const char * host, uint16_t port,
                         UBaseType_t taskPriority = tskIDLE_PRIORITY + 1,
                         uint32_t taskStackWords = configMINIMAL_STACK_SIZE * 4 );

        /** Destructor.
         *
         * Host-test-only teardown (deletes the task/semaphore/socket); production
         * firmware never destructs its one ControlChannel instance, matching EventBus's
         * own contract.
         */
        ~ControlChannel();

        ControlChannel( const ControlChannel & ) = delete;
        ControlChannel & operator=( const ControlChannel & ) = delete;

        /** Starts the receive/reconnect task. Call once. */
        void start();

        /** Unblocks the receive task to read the next line.
         *
         * Must be called by whatever handles a MsgType::ControlIn message, once it has
         * finished reading that message's `name` (see this class's header note and
         * MsgType::ControlIn's doc comment). Safe to call from any task (a plain
         * semaphore give).
         */
        void ackLineProcessed();

        /** Sends one already-built ExEnComm line's body.
         *
         * This method appends the trailing '\n' itself (see exen_comm.hpp's own note on
         * why its builders don't). Returns false without sending anything if not
         * currently connected -- this channel does not queue or retry outbound sends for
         * a down connection, matching a "best-effort telemetry channel" contract, not a
         * guaranteed-delivery one.
         *
         * @param body The line body to send; must not include any trailing newline.
         * @return true if the line was sent; false if not currently connected.
         */
        bool sendLine( std::string_view body );

        /** Whether the channel currently has a live connection.
         *
         * Reads handle_ under handleMutex_ -- NOT just pal::net_valid(handle_) inline
         * here. handle_ is written from the receive task (tryConnect()/error paths) and
         * read from any other task calling this; without going through the same mutex as
         * every other access, the compiler is free to treat handle_ as unchanged across a
         * caller's polling loop and cache a stale read indefinitely.
         *
         * @return true if the underlying socket is currently connected.
         */
        bool isConnected() const;

    private:
        // Real deployed control messages (a Device/Rule/Script's JSON, or a
        // Changed/Readed notification) are comparable in size to one model-file command.
        // Generous headroom over that, a load-time sizing decision like every other
        // kMax* constant in this port, not a data-derived one.
        static constexpr size_t kMaxLineLen = 2048;

        static constexpr uint32_t kConnectTimeoutMs = 5000;
        static constexpr uint32_t kRecvTimeoutMs = 2000;      // also the heartbeat-check tick, see header note
        static constexpr uint32_t kHeartbeatIntervalMs = 30000;
        static constexpr uint32_t kInitialBackoffMs = 1000;
        static constexpr uint32_t kMaxBackoffMs = 30000;

        EventBus * bus_;
        const char * host_;
        uint16_t port_;
        UBaseType_t taskPriority_;
        uint32_t taskStackWords_;

        pal::NetHandle handle_;
        TaskHandle_t task_;
        SemaphoreHandle_t lineAckSem_;
        SemaphoreHandle_t handleMutex_;   // see header note above -- guards handle_ + any send/close/reassign of it

        char lineBuf_[ kMaxLineLen ];
        size_t lineLen_;
        bool lineOverflow_;   // true while dropping bytes past kMaxLineLen, until the next '\n'

        uint32_t backoffMs_;
        int64_t lastSendMs_;   // epoch ms (stick::nowEpochMillis()), see .cpp

        /** FreeRTOS task-entry trampoline for taskLoop(). */
        static void taskTrampoline( void * self );
        /** The receive/reconnect task body: connect, read, reassemble, heartbeat. */
        void taskLoop();

        /** Attempts one connect (blocking up to kConnectTimeoutMs).
         *
         * NOT holding handleMutex_ during the blocking call itself -- see header note;
         * on success resets backoff/line state, on failure sleeps the current backoff and
         * doubles it.
         *
         * @return true if now connected.
         */
        bool tryConnect();

        /** Feeds one recv()'d chunk through line reassembly.
         *
         * Posts a ControlIn message (and blocks on the ack semaphore) for each complete
         * '\n'-terminated line. Blank lines (heartbeats, see header note) are consumed
         * silently, never posted. A line exceeding kMaxLineLen is dropped (logged once)
         * rather than posted truncated/corrupted.
         *
         * @param data The chunk's bytes.
         */
        void feedBytes( std::string_view data );

        /** Sends a blank heartbeat line if the connection has been idle long enough. */
        void maybeSendHeartbeat();

        /** Sends body then a trailing '\n' as one mutex-held unit.
         *
         * Shared by sendLine() and maybeSendHeartbeat() -- see header note on
         * handleMutex_. On any failure, closes and invalidates handle_ (the receive task
         * will notice on its next recv()/reconnect check).
         *
         * @param body The line body to send.
         * @return true on a fully-sent line.
         */
        bool sendWithNewline( std::string_view body );
    };
} // namespace stick
