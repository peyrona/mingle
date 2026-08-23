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
/** \file temporal.hpp
 *
 * AFTER/WITHIN window state, ported from the reference Java `ASTNode.Future` but reshaped
 * for this port's flat, stateless-per-cycle evaluation model.
 *
 * Confirmed semantics (grounded, not guessed):
 *  - `cond AFTER ms`: a one-shot delayed sample — the window arms on first reach, and the
 *    condition is evaluated ONCE at expiry (whatever it did in between is irrelevant).
 *  - `cond WITHIN ms`: true only if `cond`'s value stays UNCHANGED for the whole window
 *    (captured at arm time; any change resolves false immediately; already-false at arm
 *    resolves false instantly).
 *  - Both are one-shot: once resolved, the next reach arms a brand-new window.
 *
 * A real timer integration is required: a window must resolve at its deadline even if no
 * device change happens to trigger a re-evaluation. `ScheduleWakeFn` lets the caller
 * (Orchestrator, which owns a real EventBus) guarantee that wake-up.
 *
 * Tier 1 (hardware-agnostic): no FreeRTOS dependency; the schedule callback is an opaque
 * function pointer, so tests can hand-advance `nowMs` with a null scheduler.
 */
#pragma once

#include <cstdint>

#include "naxe/ast.hpp"

namespace stick
{
    /** Callback asking the caller to schedule a re-evaluation in `delayMs` (nullable). */
    using ScheduleWakeFn = void ( * )( void * ctx, int64_t delayMs );

    /** One slot of per-AFTER/WITHIN-node window state. */
    struct TemporalState
    {
        const naxe::ASTNode * node = nullptr; // key; nullptr = free slot
        bool active = false;                  // window counting down to deadlineMs
        int64_t deadlineMs = 0;
        naxe::Value withinInitial{};          // Within only: value snapshot at arm time
    };

    /** Fixed-capacity, node-pointer-keyed table of window states, one per Rule. */
    class TemporalTable
    {
    public:
        /** Maximum simultaneously-tracked AFTER/WITHIN windows per Rule. */
        static constexpr uint32_t kMaxSlots = 4;

        /** Finds the slot for `node`, allocating an unused one on first sight.
         *
         * @return The node's slot, or nullptr if the table is full (fails safe).
         */
        TemporalState * find( const naxe::ASTNode * node );

        /** @return The soonest active deadlineMs, or -1 if none active. */
        int64_t nextDeadlineMs() const;

    private:
        TemporalState slots_[ kMaxSlots ];
    };

    /** Context passed to naxe::eval()'s TemporalHook via Rule::evaluate()/trigger(). */
    struct TemporalCtx
    {
        TemporalTable * table;
        int64_t nowMs;
        ScheduleWakeFn scheduleWake; // nullable
        void * scheduleCtx;
    };

    /** The TemporalHook implementation (ast.hpp's naxe::TemporalHook signature). */
    naxe::Value temporalHook( void * ctx, const naxe::ASTNode * node,
                              const naxe::Environment & env, naxe::Arena & arena );
} // namespace stick
