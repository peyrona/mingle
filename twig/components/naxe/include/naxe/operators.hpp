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
/** \file operators.hpp
 *
 * Operator resolution and evaluation (the Une arithmetic/logical/comparison operators).
 *
 * Integer semantics (from the plan's "Integer model" decision): when BOTH operands of
 * `+`/`-`/`*`/unary-minus are Int, the arithmetic is done exactly in int64 — no float32
 * precision loss. When an Int meets a Float (or a numeric String), the Int promotes to
 * float32. Division and power are always float32 (matching the reference Java
 * implementation). Int-vs-Int comparisons are exact; Int-vs-Float promotes the Int.
 *
 * English keyword aliases (AND/OR/NOT/IS/ABOVE/...) are NOT handled here: CANDI's
 * transpiler rewrites them to raw symbols before the model is serialized, so by the time
 * an expression reaches this layer only symbol tokens remain (same as the Java system).
 *
 * Tier 1 (hardware-agnostic): no FreeRTOS/ESP-IDF dependency.
 */
#pragma once

#include <cstddef>
#include <cstdint>

#include "naxe/ast.hpp"
#include "naxe/value.hpp"

namespace naxe
{
    /** Coerces `v` to a float.
     *
     * Float is used as-is; Int is widened to float32 (with the documented precision
     * caveat); String is parsed with `strtof`, requiring the WHOLE string to be consumed
     * (matching Java's fail-fast numeric parsing). Anything else fails.
     *
     * @param v Value to coerce.
     * @param out Receives the parsed float on success.
     * @return true if `v` is numeric (Int/Float/numeric String).
     */
    bool tryToFloat( const Value & v, float & out );

    /** Coerces `v` to an int64.
     *
     * Int is used as-is; a numeric String is parsed with `strtoll`, requiring the whole
     * string to be consumed. Float (and everything else) fails — a Float is not silently
     * truncated here.
     *
     * @param v Value to coerce.
     * @param out Receives the parsed int on success.
     * @return true if `v` is an Int or an integral String.
     */
    bool tryToInt( const Value & v, int64_t & out );

    /** Maps a symbol operator token to its AstOp value.
     *
     * Symbol-only (see the file header on English aliases). Bitwise operators are
     * absent — confirmed unused in the corpus audit. Edge-detection operators are
     * recognized (so a parser can build a well-formed AST) but not evaluated here.
     *
     * @param token Operator token bytes.
     * @param len Token length.
     * @param out Receives the resolved operator on success.
     * @return True if `token` is a recognized operator.
     */
    bool resolveOperator( const char * token, size_t len, AstOp & out );

    /** Evaluates a binary operator over two values (see the file header for integer
     *  semantics and coercion rules).
     *
     * @param op Operator to apply.
     * @param l Left operand.
     * @param r Right operand.
     * @param arena Arena for any allocated string result.
     * @return The operation result.
     */
    Value evalBinaryOp( AstOp op, const Value & l, const Value & r, Arena & arena );

    /** Evaluates a unary operator over a single value.
     *
     * @param op Operator to apply (Not or Neg).
     * @param v Operand.
     * @return The operation result.
     */
    Value evalUnaryOp( AstOp op, const Value & v );
} // namespace naxe
