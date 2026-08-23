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
/** \file ast.hpp
 *
 * The AST representation and recursive tree-walking evaluator.
 *
 * Expression trees are arena-allocated and built either by hand (tests) or by the Une
 * text parser (a later phase). Evaluation walks the tree against an `Environment` (a
 * variable-name -> Value lookup backed by a `naxe::Pair`), allocating any intermediate
 * string/list results into a per-cycle `Arena`.
 *
 * Two operator families are *recognized* but *not evaluated* here (matching the real
 * Java system's own layering — their `StdXprOps.Operator.eval()` is a stub there too):
 *
 *  - **Edge-detection** (`?=`, `?!=`, `?>`, `?<`, `?<>` — `Rises`/`Drops`/`Becomes`/
 *    `Leaves`/`Crosses`): need per-rule previous-value tracking that the stateless
 *    evaluator cannot hold; `RuleManager` special-cases them before delegating the rest
 *    of a WHEN/IF tree.
 *  - **After/Within** (`After`/`Within`): the window state machine is routed through the
 *    optional `TemporalHook` (an opaque function pointer so this header stays Tier 1),
 *    whose real implementation lives in `stick::` later.
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
    /** Operator values for arithmetic, comparison, logical, edge-detection, and
     *  temporal (After/Within) operators. */
    enum class AstOp
    {
        Add, Sub, Mul, Div,
        Mod, // "%" — Une's percentage-of operator ((l/100)*r), NOT modulo (see operators.cpp)
        Pow, // "^" — power (l raised to r)
        Xor, // "|&" — logical XOR on truthiness (not bitwise)
        Eq, Ne, Gt, Lt, Ge, Le,
        And, Or, Not,
        Neg,
        Rises, Drops, Becomes, Leaves, Crosses,
        After, Within
    };

    /** Discriminates which ASTNode fields are active for a given node. */
    enum class AstNodeKind
    {
        Literal,
        Variable,
        UnaryOp,
        BinaryOp,
        FunctionCall,
        MethodCall
    };

    /** An arena-allocated tree node; the active fields depend on `kind`. */
    struct ASTNode
    {
        AstNodeKind kind;

        // Literal
        Value literalValue;

        // Variable
        std::string_view name;

        // UnaryOp / BinaryOp
        AstOp op;
        ASTNode * left;
        ASTNode * right;

        // FunctionCall (target == nullptr) / MethodCall (target == receiver expression)
        std::string_view funcName;
        ASTNode * target;
        ASTNode ** args;
        uint32_t argCount;
    };

    // ---- Arena-allocated node builders ------------------------------------------

    /** Allocates a Literal node holding `v`. */
    ASTNode * astLiteral( Arena & arena, const Value & v );

    /** Allocates a Variable node referencing `name`. */
    ASTNode * astVariable( Arena & arena, std::string_view name );

    /** Allocates a UnaryOp node. */
    ASTNode * astUnary( Arena & arena, AstOp op, ASTNode * operand );

    /** Allocates a BinaryOp node. */
    ASTNode * astBinary( Arena & arena, AstOp op, ASTNode * left, ASTNode * right );

    /** Allocates a FunctionCall node. */
    ASTNode * astCall( Arena & arena, std::string_view funcName, ASTNode ** args, uint32_t argCount );

    /** Allocates a MethodCall node. */
    ASTNode * astMethodCall( Arena & arena, ASTNode * target, std::string_view methodName,
                             ASTNode ** args, uint32_t argCount );

    // ---- Evaluation environment --------------------------------------------------

    /** Read-only variable-name -> Value lookup for eval(), backed by a Pair.
     *
     * `vars` may be null (meaning "no environment"): lookup() then returns nullptr, and
     * eval() treats an undefined variable as the empty String (see eval()).
     */
    class Environment
    {
    public:
        /** Wraps `vars` (not owned; may be null). */
        explicit Environment( const Pair * vars ) : vars_( vars ) {}

        /** Looks up a variable by name (case-insensitive).
         *
         * @param name Variable name.
         * @return The stored Value, or nullptr if absent (or no environment).
         */
        const Value * lookup( std::string_view name ) const;

    private:
        const Pair * vars_;
    };

    /** Function-pointer type for the After/Within window state machine.
     *
     * Invoked by eval() in place of normal BinaryOp handling at an `After`/`Within`
     * node; the hook decides when/whether to evaluate `node->left` (the condition) or
     * `node->right` (the duration), since that timing *is* the window semantics.
     * Defined here as an opaque pointer (not in stick::) to keep this header Tier 1.
     */
    using TemporalHook = Value ( * )( void * ctx, const ASTNode * node,
                                      const Environment & env, Arena & arena );

    /** Recursively evaluates an AST against the environment.
     *
     * Undefined variable references evaluate to the empty String (a documented,
     * fail-safe fallback matching the "JSON null -> empty string" convention elsewhere,
     * rather than a new sentinel). `temporalHook`/`temporalCtx` default to null; an
     * After/Within node reached with no hook installed returns `false` (fail safe, not
     * silently wrong).
     *
     * @param node Root node to evaluate.
     * @param env Variable environment.
     * @param arena Arena for any intermediate strings/lists produced.
     * @param temporalHook Optional After/Within hook.
     * @param temporalCtx Opaque context for temporalHook.
     * @return The evaluation result.
     */
    Value eval( const ASTNode * node, const Environment & env, Arena & arena,
                TemporalHook temporalHook = nullptr, void * temporalCtx = nullptr );

    /** Collects every distinct Variable-node name referenced in `root`'s tree.
     *
     * Used so a rule can know, once, which devices its WHEN/IF actually depends on
     * (letting the runtime skip re-evaluating rules an unrelated device change is
     * irrelevant to). Names are written into `out` (deduplicated case-insensitively),
     * capped at `outCapacity`; extras are silently dropped.
     *
     * @param root Root of the tree to scan.
     * @param out Destination array for the distinct name views.
     * @param outCapacity Capacity of `out` in entries.
     * @return The number of distinct names written (capped at outCapacity).
     */
    uint32_t collectVariableNames( const ASTNode * root, std::string_view * out, uint32_t outCapacity );
} // namespace naxe
