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
/** \file ast.cpp
 * AST node builders, tree-walking evaluator, and variable-name collection (see ast.hpp).
 */
#include "naxe/ast.hpp"

#include <cassert>

#include "naxe/functions.hpp"
#include "naxe/operators.hpp"
#include "naxe/pair.hpp"
#include "naxe/util.hpp"

namespace naxe
{
    // ---- Environment ------------------------------------------------------------

    const Value * Environment::lookup( std::string_view name ) const
    {
        return ( vars_ != nullptr ) ? vars_->get( name ) : nullptr;
    }

    // ---- Node builders ---------------------------------------------------------

    ASTNode * astLiteral( Arena & arena, const Value & v )
    {
        ASTNode * n = arena.emplace<ASTNode>();
        assert( n != nullptr && "arena exhausted building an AST Literal node" );
        n->kind = AstNodeKind::Literal;
        n->literalValue = v;
        return n;
    }

    ASTNode * astVariable( Arena & arena, std::string_view name )
    {
        ASTNode * n = arena.emplace<ASTNode>();
        assert( n != nullptr && "arena exhausted building an AST Variable node" );
        n->kind = AstNodeKind::Variable;
        n->name = name;
        return n;
    }

    ASTNode * astUnary( Arena & arena, AstOp op, ASTNode * operand )
    {
        ASTNode * n = arena.emplace<ASTNode>();
        assert( n != nullptr && "arena exhausted building an AST UnaryOp node" );
        n->kind = AstNodeKind::UnaryOp;
        n->op = op;
        n->left = operand;
        return n;
    }

    ASTNode * astBinary( Arena & arena, AstOp op, ASTNode * left, ASTNode * right )
    {
        ASTNode * n = arena.emplace<ASTNode>();
        assert( n != nullptr && "arena exhausted building an AST BinaryOp node" );
        n->kind = AstNodeKind::BinaryOp;
        n->op = op;
        n->left = left;
        n->right = right;
        return n;
    }

    ASTNode * astCall( Arena & arena, std::string_view funcName, ASTNode ** args, uint32_t argCount )
    {
        ASTNode * n = arena.emplace<ASTNode>();
        assert( n != nullptr && "arena exhausted building an AST FunctionCall node" );
        n->kind = AstNodeKind::FunctionCall;
        n->funcName = funcName;
        n->target = nullptr;
        n->args = args;
        n->argCount = argCount;
        return n;
    }

    ASTNode * astMethodCall( Arena & arena, ASTNode * target, std::string_view methodName,
                             ASTNode ** args, uint32_t argCount )
    {
        ASTNode * n = arena.emplace<ASTNode>();
        assert( n != nullptr && "arena exhausted building an AST MethodCall node" );
        n->kind = AstNodeKind::MethodCall;
        n->target = target;
        n->funcName = methodName;
        n->args = args;
        n->argCount = argCount;
        return n;
    }

    namespace
    {
        /** Evaluates each argument node into an arena-allocated Value array.
         *
         * @return The array (arena-allocated), or nullptr if argCount == 0.
         */
        Value * evalArgs( ASTNode ** argNodes, uint32_t argCount, const Environment & env,
                          Arena & arena, TemporalHook temporalHook, void * temporalCtx )
        {
            if( argCount == 0 )
            {
                return nullptr;
            }

            auto * values = static_cast<Value *>( arena.alloc( sizeof( Value ) * argCount ) );
            assert( values != nullptr && "arena exhausted evaluating arguments" );

            for( uint32_t i = 0; i < argCount; ++i )
            {
                values[ i ] = eval( argNodes[ i ], env, arena, temporalHook, temporalCtx );
            }

            return values;
        }
    } // namespace

    Value eval( const ASTNode * node, const Environment & env, Arena & arena,
                TemporalHook temporalHook, void * temporalCtx )
    {
        switch( node->kind )
        {
            case AstNodeKind::Literal:
                return node->literalValue;

            case AstNodeKind::Variable:
            {
                const Value * v = env.lookup( node->name );
                return ( v != nullptr ) ? *v : Value::makeString( std::string_view() );
            }

            case AstNodeKind::UnaryOp:
                return evalUnaryOp( node->op, eval( node->left, env, arena, temporalHook, temporalCtx ) );

            case AstNodeKind::BinaryOp:
            {
                if( node->op == AstOp::After || node->op == AstOp::Within )
                {
                    // The window state machine lives entirely in the hook: neither
                    // operand is evaluated here — the hook decides when/whether to
                    // evaluate node->left (condition) or node->right (duration).
                    if( temporalHook != nullptr )
                    {
                        return temporalHook( temporalCtx, node, env, arena );
                    }
                    return Value::makeBool( false ); // no hook installed
                }

                const Value l = eval( node->left, env, arena, temporalHook, temporalCtx );
                const Value r = eval( node->right, env, arena, temporalHook, temporalCtx );
                return evalBinaryOp( node->op, l, r, arena );
            }

            case AstNodeKind::FunctionCall:
            {
                FunctionPtr fn = resolveFunction( node->funcName );

                if( fn == nullptr )
                {
                    // Unsupported function: fail loudly with a visible sentinel rather
                    // than silently no-op (mirrors the plan's "unsupported function"
                    // failure mode).
                    return makeArenaString( arena, "#UNSUPPORTED_FUNCTION#" );
                }

                Value * argValues = evalArgs( node->args, node->argCount, env, arena, temporalHook, temporalCtx );
                return fn( argValues, node->argCount, arena );
            }

            case AstNodeKind::MethodCall:
            {
                const Value targetValue = eval( node->target, env, arena, temporalHook, temporalCtx );

                // Extended types (DateTime/List/Pair) use their own method table
                // exclusively; Float/Bool/String/Int are not extended types — a chained
                // call like `n:mod(2)` resolves to the GLOBAL function table with the
                // receiver prepended as the first argument.
                const bool isExtendedType = targetValue.isDateTime()
                                            || targetValue.isList()
                                            || targetValue.isPair();

                if( isExtendedType )
                {
                    MethodPtr method = resolveMethod( targetValue.type(), node->funcName );

                    if( method == nullptr )
                    {
                        return makeArenaString( arena, "#UNSUPPORTED_METHOD#" );
                    }

                    Value * argValues = evalArgs( node->args, node->argCount, env, arena, temporalHook, temporalCtx );
                    return method( targetValue, argValues, node->argCount, arena );
                }

                FunctionPtr fn = resolveFunction( node->funcName );

                if( fn == nullptr )
                {
                    return makeArenaString( arena, "#UNSUPPORTED_FUNCTION#" );
                }

                auto * combinedArgs = static_cast<Value *>(
                    arena.alloc( sizeof( Value ) * ( node->argCount + 1 ) ) );
                assert( combinedArgs != nullptr && "arena exhausted building method args" );
                combinedArgs[ 0 ] = targetValue;

                for( uint32_t i = 0; i < node->argCount; ++i )
                {
                    combinedArgs[ i + 1 ] = eval( node->args[ i ], env, arena, temporalHook, temporalCtx );
                }

                return fn( combinedArgs, node->argCount + 1, arena );
            }
        }

        return Value::makeString( std::string_view() ); // unreachable
    }

    namespace
    {
        /** Appends `name` to out[0..*count) if not already present (case-insensitive)
         * and there is room; silently drops it otherwise. */
        void addVariableNameIfNew( std::string_view name, std::string_view * out,
                                   uint32_t * count, uint32_t outCapacity )
        {
            for( uint32_t i = 0; i < *count; ++i )
            {
                if( util::asciiEqualsIgnoreCase( out[ i ], name ) )
                {
                    return;
                }
            }

            if( *count < outCapacity )
            {
                out[ ( *count )++ ] = name;
            }
        }

        /** Recursive worker behind collectVariableNames(). */
        void collectVariableNamesInto( const ASTNode * node, std::string_view * out,
                                       uint32_t * count, uint32_t outCapacity )
        {
            if( node == nullptr )
            {
                return;
            }

            switch( node->kind )
            {
                case AstNodeKind::Literal:
                    break;

                case AstNodeKind::Variable:
                    addVariableNameIfNew( node->name, out, count, outCapacity );
                    break;

                case AstNodeKind::UnaryOp:
                    collectVariableNamesInto( node->left, out, count, outCapacity );
                    break;

                case AstNodeKind::BinaryOp:
                    collectVariableNamesInto( node->left, out, count, outCapacity );
                    collectVariableNamesInto( node->right, out, count, outCapacity );
                    break;

                case AstNodeKind::FunctionCall:
                    for( uint32_t i = 0; i < node->argCount; ++i )
                    {
                        collectVariableNamesInto( node->args[ i ], out, count, outCapacity );
                    }
                    break;

                case AstNodeKind::MethodCall:
                    collectVariableNamesInto( node->target, out, count, outCapacity );
                    for( uint32_t i = 0; i < node->argCount; ++i )
                    {
                        collectVariableNamesInto( node->args[ i ], out, count, outCapacity );
                    }
                    break;
            }
        }
    } // namespace

    uint32_t collectVariableNames( const ASTNode * root, std::string_view * out, uint32_t outCapacity )
    {
        uint32_t count = 0;
        collectVariableNamesInto( root, out, &count, outCapacity );
        return count;
    }
} // namespace naxe
