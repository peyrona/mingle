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
/** \file script.hpp
 *
 * Compiled, evaluable runtime state for one Script. A Une script's inline body is a
 * sequence of THEN-action-shaped statements (no WHEN), each compiled via action.hpp.
 * Only `language=="une"` inline scripts are supported (non-Une/file-based scripts fail
 * isValid() cleanly, per the plan's "SCRIPT, Une-only" decision).
 *
 * Tier 1 (hardware-agnostic): no FreeRTOS/ESP-IDF dependency.
 */
#pragma once

#include <cstdint>
#include <string_view>

#include "cil/model.hpp"
#include "naxe/arena.hpp"
#include "stick/action.hpp"

namespace stick
{
    /** Compiled, evaluable runtime state for one Script. */
    class Script
    {
    public:
        /** Constructs an empty placeholder Script (for a fixed-capacity array slot). */
        Script();

        /** Compiles a runtime Script from its parsed model shape.
         *
         * @param parsed The static model shape (must outlive this Script).
         * @param arena Long-lived arena the compiled statements are allocated into.
         */
        Script( const cil::Script & parsed, naxe::Arena & arena );

        /** @return True if this is a supported, compilable Une script. */
        bool isValid() const { return valid_; }

        /** @return The script's name view (empty if placeholder). */
        std::string_view name() const { return parsed_ != nullptr ? parsed_->name : std::string_view(); }

        /** @return True if the model marks this script to run at boot. */
        bool isOnStart() const { return parsed_ != nullptr && parsed_->onstart; }

        /** Executes every compiled statement in source order via `cb` (unconditional). */
        void execute( const naxe::Environment & env, naxe::Arena & cycleArena,
                      ActionCallback cb, void * ctx ) const;

    private:
        const cil::Script * parsed_;
        bool valid_;
        CompiledAction * statements_;
        uint32_t statementCount_;
    };
} // namespace stick
