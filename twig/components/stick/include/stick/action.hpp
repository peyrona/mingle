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
/** \file action.hpp
 *
 * One compiled THEN/statement action, shared by Rule and Script. A Une SCRIPT's inline
 * body is a sequence of THEN-action-shaped statements with no WHEN condition, so both
 * reuse this exact compilation/firing logic.
 *
 * Tier 1 (hardware-agnostic): no FreeRTOS/ESP-IDF dependency.
 */
#pragma once

#include <cstdint>
#include <string_view>

#include "cil/model.hpp"
#include "naxe/arena.hpp"
#include "naxe/ast.hpp"

namespace stick
{
    /** The kind of THEN-action a compiled statement performs. */
    enum class ActionKind
    {
        ChangeActuator, // target = value (or target := another device's current value)
        Execute,        // a bare rule/script name (no '(')
        Unsupported     // a bare expression call, e.g. put(...)/enable(...)
    };

    /** The resolved result of firing one compiled action, passed to the callback. */
    struct ActionResult
    {
        ActionKind kind;
        std::string_view target;
        naxe::Value value; // meaningful only when kind == ChangeActuator
        int64_t delayMs;
    };

    /** Callback invoked once per fired action, in source order. */
    using ActionCallback = void ( * )( void * ctx, const ActionResult & result );

    /** One action compiled at load time, ready for evaluation per firing cycle. */
    struct CompiledAction
    {
        int64_t delayMs;
        std::string_view target;
        bool hasValue;
        bool targetIsExpression; // only meaningful if !hasValue
        bool valueIsExpression;  // only meaningful if hasValue
        naxe::Value literalValue; // meaningful if hasValue && !valueIsExpression
        naxe::ASTNode * valueExpr; // meaningful if hasValue && valueIsExpression
    };

    /** Compiles one action from its already-split ingredients.
     *
     * If `value` is a String, its text is parsed as a Une expression (JSON-unescaped via
     * parseExpression) and arena-compiled once — the arena must be the long-lived stable
     * pool, not a per-cycle arena.
     */
    CompiledAction compileAction( int64_t delayMs, std::string_view target, bool hasValue,
                                  const naxe::Value & value, naxe::Arena & arena );

    /** Evaluates one compiled action against `env` and invokes `cb` with the result. */
    void fireCompiledAction( const CompiledAction & action, const naxe::Environment & env,
                             naxe::Arena & cycleArena, ActionCallback cb, void * ctx );
} // namespace stick
