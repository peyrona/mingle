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
/** \file functions.hpp
 *
 * Top-level function and chained-method dispatch tables.
 *
 * This phase establishes the dispatch *infrastructure* (the function-pointer types and
 * the `resolveFunction`/`resolveMethod` lookups); the actual function/method
 * implementations (the audited `StdXprFns` set plus the chained date/time/list/pair
 * methods) are populated in the next phase. Until then both tables are empty and any
 * call resolves to the `#UNSUPPORTED_*#` sentinel produced by `eval()` — a clear,
 * fail-loud error rather than a silent no-op.
 *
 * Tier 1 (hardware-agnostic): no FreeRTOS/ESP-IDF dependency.
 */
#pragma once

#include <cstddef>
#include <cstdint>
#include <string_view>

#include "naxe/arena.hpp"
#include "naxe/value.hpp"

namespace naxe
{
    /** Signature of a top-level function implementation.
     *
     * @param args Evaluated argument values (arena-backed; not owned by the callee).
     * @param argCount Number of arguments.
     * @param arena Arena for any allocated result.
     * @return The function result.
     */
    using FunctionPtr = Value ( * )( const Value * args, uint32_t argCount, Arena & arena );

    /** Signature of a chained-method implementation on an extended type.
     *
     * @param target The receiver value (DateTime/List/Pair).
     * @param args Evaluated argument values.
     * @param argCount Number of arguments.
     * @param arena Arena for any allocated result.
     * @return The method result.
     */
    using MethodPtr = Value ( * )( const Value & target, const Value * args, uint32_t argCount,
                                   Arena & arena );

    /** Looks up a top-level function by name (case-insensitive).
     *
     * @param name Function name.
     * @return The matching function, or nullptr if `name` isn't in the table.
     */
    FunctionPtr resolveFunction( std::string_view name );

    /** Looks up a chained method for an extended type by name (case-insensitive).
     *
     * @param type Extended receiver type (DateTime/List/Pair).
     * @param name Method name.
     * @return The matching method, or nullptr if `name` isn't recognized for `type`.
     */
    MethodPtr resolveMethod( ValueType type, std::string_view name );
} // namespace naxe
