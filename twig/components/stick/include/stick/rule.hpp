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
/** \file rule.hpp
 *
 * Compiled, evaluable runtime state for one Rule: WHEN/IF/action-values compiled to ASTs
 * once at load time, plus the evaluation logic. Evaluation is flat (re-walks the whole
 * tree per cycle, matching the arena-per-cycle model); each rule tracks which devices its
 * WHEN/IF references so the runtime can skip rules a device change is irrelevant to.
 *
 * AFTER/WITHIN (temporal) evaluation is NOT wired here yet — that phase comes later; a
 * temporal node evaluates to `false` until the temporal hook is added.
 *
 * Tier 1 (hardware-agnostic): no FreeRTOS/ESP-IDF dependency.
 */
#pragma once

#include <cstdint>
#include <string_view>

#include "cil/model.hpp"
#include "naxe/arena.hpp"
#include "naxe/ast.hpp"
#include "naxe/parser.hpp"
#include "stick/action.hpp"
#include "stick/temporal.hpp"

namespace stick
{
    /** Compiled, evaluable runtime state for one Rule. */
    class Rule
    {
    public:
        /** Constructs an empty placeholder Rule (for a fixed-capacity array slot). */
        Rule();

        /** Compiles a runtime Rule from its parsed model shape.
         *
         * @param parsed The static model shape (must outlive this Rule).
         * @param arena Long-lived arena the ASTs are compiled into.
         * @param groupResolver Optional group-membership resolver for ANY/ALL/NONE.
         * @param groupResolverCtx Opaque context for groupResolver.
         */
        Rule( const cil::Rule & parsed, naxe::Arena & arena,
              naxe::GroupResolverFn groupResolver = nullptr, void * groupResolverCtx = nullptr );

        /** @return True if WHEN (and IF, when present) parsed successfully. */
        bool isValid() const { return whenAst_ != nullptr && ( ! hasIf_ || ifAst_ != nullptr ); }
        /** @return The WHEN parse error, if isValid() is false because of it. */
        const naxe::ParseError & whenError() const { return whenErr_; }
        /** @return The IF parse error, if isValid() is false because of it. */
        const naxe::ParseError & ifError() const { return ifErr_; }

        /** @return True if this rule carries a name in the model. */
        bool hasName() const { return parsed_ != nullptr && parsed_->hasName; }
        /** @return The rule's name view, or empty if !hasName(). */
        std::string_view name() const;

        /** @return True if the rule is enabled. */
        bool isEnabled() const { return enabled_; }
        /** Enables the rule. */
        void enable() { enabled_ = true; }
        /** Disables the rule. */
        void disable() { enabled_ = false; }

        /** Evaluates WHEN (and IF, if present) and fires the actions if both truthy.
         *
         * `nowMs`/`scheduleWake`/`scheduleCtx` feed AFTER/WITHIN's window state machine
         * (see temporal.hpp); a caller whose WHEN/IF never uses AFTER/WITHIN can omit them.
         *
         * @return true if the rule triggered (all actions fired).
         */
        bool evaluate( const naxe::Environment & env, naxe::Arena & cycleArena,
                       ActionCallback cb, void * ctx,
                       int64_t nowMs = 0, ScheduleWakeFn scheduleWake = nullptr,
                       void * scheduleCtx = nullptr ) const;

        /** Fires the rule's actions, forced or re-evaluated.
         *
         * force=true fires unconditionally; force=false re-evaluates WHEN and fires only
         * if true, but refuses if the rule has an IF clause.
         */
        bool trigger( bool force, const naxe::Environment & env, naxe::Arena & cycleArena,
                      ActionCallback cb, void * ctx,
                      int64_t nowMs = 0, ScheduleWakeFn scheduleWake = nullptr,
                      void * scheduleCtx = nullptr ) const;

        /** @return The soonest pending AFTER/WITHIN deadline in epoch ms, or -1. */
        int64_t nextTemporalDeadlineMs() const { return temporal_.nextDeadlineMs(); }

        /** @return True if this rule's WHEN/IF references `name` (or references no
         *         variables at all — a constant WHEN must always evaluate). */
        bool referencesDevice( std::string_view name ) const;

    private:
        static constexpr uint32_t kMaxReferencedVars = 8;

        void fireActions( const naxe::Environment & env, naxe::Arena & cycleArena,
                          ActionCallback cb, void * ctx ) const;

        std::string_view referencedVars_[ kMaxReferencedVars ];
        uint32_t referencedVarCount_;

        const cil::Rule * parsed_;
        naxe::ASTNode * whenAst_;
        naxe::ParseError whenErr_;

        bool hasIf_;
        naxe::ASTNode * ifAst_;
        naxe::ParseError ifErr_;

        bool enabled_;

        CompiledAction * actions_;
        uint32_t actionCount_;

        // AFTER/WITHIN window state — mutable: bookkeeping incidental to evaluation, so
        // evaluate()/trigger() stay logically const.
        mutable TemporalTable temporal_;
    };
} // namespace stick
