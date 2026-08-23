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
/** \file parser.hpp
 *
 * Une expression text -> AST parser.
 *
 * Parses the raw Une source text found verbatim in a transpiled `.model` file's
 * WHEN/IF/action-value fields (e.g. `"when":"clock > 5"`) into an `ASTNode` tree, ready
 * for `naxe::eval()`. Pipeline:
 *
 *   1. `jsonUnescape()` — recovers the true Une source text CANDI originally wrote (the
 *      model's strings are JSON-encoded; jsonview never unescapes).
 *   2. `lexRaw()` — tokenizes (numbers, strings, booleans, names, operators, parens).
 *   3. `classify()` — detects unary +/-/! and name-vs-function.
 *   4. `expandQuantifiers()` — rewrites `ANY`/`ALL`/`NONE` group quantifiers into
 *      explicit `||`/`&&` chains (matching real Java's `XprPreProc`).
 *   5. `toRPN()` — shunting-yard infix -> RPN.
 *   6. `rpnToAst()` — RPN -> AST.
 *
 * Design departures from Java (each grounded, not guessed — see the reference port's
 * IMPLEMENTATION.md):
 *  - English keyword aliases (AND/OR/IS/ABOVE/...) are NOT handled: CANDI resolves them
 *    to raw symbols at transpile time.
 *  - Function/method name validity is checked at *evaluation* time, not parse time.
 *  - Numeric literals without a `.` parse as `Int` (int64), with a `.` as `Float`.
 *
 * Tier 1 (hardware-agnostic): no FreeRTOS/ESP-IDF dependency.
 */
#pragma once

#include <cstdint>
#include <string_view>

#include "etl/string.h"
#include "naxe/arena.hpp"
#include "naxe/ast.hpp"

namespace naxe
{
    /** Parser error state: a flag plus a fixed-size human-readable message. */
    struct ParseError
    {
        bool hasError = false;
        etl::string<64> message;
    };

    /** Function-pointer type resolving a device-group name to its member names.
     *
     * Resolves a device-group name to its member device names, backing the
     * `ANY`/`ALL`/`NONE` expansion. Returns the number of members written into `out`
     * (<= outCapacity), or 0 for an unknown/empty group.
     */
    using GroupResolverFn = uint32_t ( * )( void * ctx, std::string_view group,
                                             std::string_view * out, uint32_t outCapacity );

    /** Recovers the true Une source text from its raw JSON-encoded form.
     *
     * @param arena Arena the unescaped result is allocated from.
     * @param text Raw JSON-encoded bytes.
     * @return The unescaped span (arena-allocated).
     */
    std::string_view jsonUnescape( Arena & arena, std::string_view text );

    /** Parses Une expression source text into an AST.
     *
     * Both a working copy of the JSON-unescaped text and every AST node are
     * arena-allocated — `arena` must outlive the returned tree. Returns nullptr on
     * error; `err.hasError` is set and `err.message` explains why.
     *
     * `groupResolver`/`groupResolverCtx` default to null: an expression that uses a
     * quantifier with no resolver installed fails with a clear parse error.
     *
     * @param arena Arena for the unescaped text and all nodes.
     * @param text Expression source bytes.
     * @param err Receives error state on failure.
     * @param groupResolver Optional group-name resolver for ANY/ALL/NONE expansion.
     * @param groupResolverCtx Opaque context passed to `groupResolver`.
     * @return The root AST node, or nullptr on error.
     */
    ASTNode * parseExpression( Arena & arena, std::string_view text, ParseError & err,
                               GroupResolverFn groupResolver = nullptr, void * groupResolverCtx = nullptr );
} // namespace naxe
