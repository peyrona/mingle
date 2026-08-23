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
/** \file checker.cpp
 * Model compatibility checker (see checker.hpp).
 */
#include "compat_checker/checker.hpp"

#include <string_view>

#include "drivers/registry.hpp"
#include "naxe/ast.hpp"
#include "naxe/functions.hpp"
#include "naxe/operators.hpp"
#include "naxe/parser.hpp"
#include "naxe/util.hpp"
#include "naxe/value.hpp"

namespace compat_checker
{
    namespace
    {
        constexpr uint32_t kMaxExecutedNames = 256;

        /** Maps an edge-detection AstOp back to its Une source symbol. */
        const char * edgeOpSymbol( naxe::AstOp op )
        {
            switch( op )
            {
                case naxe::AstOp::Rises: return "?>";
                case naxe::AstOp::Drops: return "?<";
                case naxe::AstOp::Becomes: return "?=";
                case naxe::AstOp::Leaves: return "?!=";
                case naxe::AstOp::Crosses: return "?<>";
                default: return "?";
            }
        }

        bool isEdgeOp( naxe::AstOp op )
        {
            return op == naxe::AstOp::Rises || op == naxe::AstOp::Drops
                   || op == naxe::AstOp::Becomes || op == naxe::AstOp::Leaves
                   || op == naxe::AstOp::Crosses;
        }

        /** True if a method name resolves for no extended type and no global function —
         * i.e. it is on the deferred list (map/filter/reduce/toJson/split, the
         * astronomical date methods, …). */
        bool isUnsupportedMethod( std::string_view name )
        {
            if( naxe::resolveFunction( name ) != nullptr ) return false;
            if( naxe::resolveMethod( naxe::ValueType::DateTime, name ) != nullptr ) return false;
            if( naxe::resolveMethod( naxe::ValueType::List, name ) != nullptr ) return false;
            if( naxe::resolveMethod( naxe::ValueType::Pair, name ) != nullptr ) return false;
            return true;
        }

        /** Recursively flags deferred functions/methods/edge operators in a tree. */
        void walkAst( const naxe::ASTNode * node, int cmdIndex, Report & out )
        {
            if( node == nullptr ) return;

            switch( node->kind )
            {
                case naxe::AstNodeKind::FunctionCall:
                    if( naxe::resolveFunction( node->funcName ) == nullptr )
                    {
                        out.add( FindingKind::UnsupportedFunction, cmdIndex, node->funcName,
                                 std::string_view() );
                    }
                    for( uint32_t i = 0; i < node->argCount; ++i ) walkAst( node->args[ i ], cmdIndex, out );
                    break;

                case naxe::AstNodeKind::MethodCall:
                    walkAst( node->target, cmdIndex, out );
                    if( isUnsupportedMethod( node->funcName ) )
                    {
                        out.add( FindingKind::UnsupportedMethod, cmdIndex, node->funcName,
                                 std::string_view() );
                    }
                    for( uint32_t i = 0; i < node->argCount; ++i ) walkAst( node->args[ i ], cmdIndex, out );
                    break;

                case naxe::AstNodeKind::UnaryOp:
                    walkAst( node->left, cmdIndex, out );
                    break;

                case naxe::AstNodeKind::BinaryOp:
                    if( isEdgeOp( node->op ) )
                    {
                        out.add( FindingKind::EdgeDetectionOperator, cmdIndex,
                                 std::string_view( edgeOpSymbol( node->op ) ), std::string_view() );
                    }
                    walkAst( node->left, cmdIndex, out );
                    walkAst( node->right, cmdIndex, out );
                    break;

                default:
                    break; // Literal/Variable are leaves
            }
        }

        /** Parses one expression and flags deferred features (or the parse failure). */
        void checkExpression( std::string_view text, int cmdIndex, Report & out, naxe::Arena & arena )
        {
            naxe::ParseError err;
            naxe::ASTNode * root = naxe::parseExpression( arena, text, err );

            if( root == nullptr )
            {
                const bool unsupportedOp = ( err.message == "unsupported operator" );
                out.add( unsupportedOp ? FindingKind::UnsupportedOperator : FindingKind::ParseError,
                         cmdIndex, text,
                         naxe::makeArenaString( arena, std::string_view( err.message.data(), err.message.size() ) ).stringValue() );
                return;
            }

            walkAst( root, cmdIndex, out );
        }
    } // namespace

    bool Report::add( FindingKind kind, int commandIndex, std::string_view name, std::string_view detail )
    {
        if( count_ >= kMaxFindings ) return false;
        findings_[ count_++ ] = Finding{ kind, commandIndex, name, detail };
        return true;
    }

    void checkModel( const cil::Command * commands, uint32_t count, Report & out, naxe::Arena & arena )
    {
        // Pass 1: collect the set of names the model actually executes as a bare target
        // (a rule/script name in a THEN), plus onstart/prestart scripts. These identify
        // which scripts are "executed" vs. merely a DRIVER's controller script.
        std::string_view executedNames[ kMaxExecutedNames ];
        uint32_t executedCount = 0;
        auto recordExecuted = [ & ]( std::string_view name )
        {
            for( uint32_t i = 0; i < executedCount; ++i )
            {
                if( naxe::util::asciiEqualsIgnoreCase( executedNames[ i ], name ) ) return;
            }
            if( executedCount < kMaxExecutedNames ) executedNames[ executedCount++ ] = name;
        };

        for( uint32_t i = 0; i < count; ++i )
        {
            const cil::Command & cmd = commands[ i ];
            if( cmd.type == cil::CommandType::Rule )
            {
                for( uint32_t a = 0; a < cmd.rule.actionCount; ++a )
                {
                    const cil::RuleAction & act = cmd.rule.actions[ a ];
                    if( ! act.hasValue && act.target.find( '(' ) == std::string_view::npos )
                    {
                        recordExecuted( act.target ); // bare name -> Execute
                    }
                }
            }
            else if( cmd.type == cil::CommandType::Script
                     && ( cmd.script.onstart || cmd.script.prestart ) )
            {
                recordExecuted( cmd.script.name );
            }
        }

        // Pass 2: check each command against the feature/driver surface.
        for( uint32_t i = 0; i < count; ++i )
        {
            const cil::Command & cmd = commands[ i ];

            switch( cmd.type )
            {
                case cil::CommandType::Device:
                    if( drivers::findDriver( cmd.device.driver ) == nullptr )
                    {
                        out.add( FindingKind::UnknownDriver, static_cast<int>( i ), cmd.device.driver,
                                 std::string_view() );
                    }
                    break;

                case cil::CommandType::Driver:
                    if( drivers::findDriver( cmd.driver.name ) == nullptr )
                    {
                        out.add( FindingKind::UnknownDriver, static_cast<int>( i ), cmd.driver.name,
                                 std::string_view() );
                    }
                    break;

                case cil::CommandType::Rule:
                    checkExpression( cmd.rule.when, static_cast<int>( i ), out, arena );
                    if( cmd.rule.hasIf )
                    {
                        checkExpression( cmd.rule.ifExpr, static_cast<int>( i ), out, arena );
                    }
                    for( uint32_t a = 0; a < cmd.rule.actionCount; ++a )
                    {
                        const cil::RuleAction & act = cmd.rule.actions[ a ];
                        if( act.hasValue )
                        {
                            if( act.value.isString() )
                            {
                                checkExpression( act.value.stringValue(), static_cast<int>( i ), out, arena );
                            }
                        }
                        else if( act.target.find( '(' ) != std::string_view::npos )
                        {
                            // A bare expression call as a target (enable(...)/exit()/…):
                            // the runtime routes these to ActionKind::Unsupported.
                            out.add( FindingKind::UnsupportedAction, static_cast<int>( i ), act.target,
                                     std::string_view() );
                        }
                    }
                    break;

                case cil::CommandType::Script:
                {
                    bool executed = ( cmd.script.onstart || cmd.script.prestart );
                    for( uint32_t n = 0; ! executed && n < executedCount; ++n )
                    {
                        if( naxe::util::asciiEqualsIgnoreCase( executedNames[ n ], cmd.script.name ) )
                        {
                            executed = true;
                        }
                    }

                    if( ! executed ) break; // controller script — the native driver substitutes

                    if( ! naxe::util::asciiEqualsIgnoreCase( cmd.script.language, "une" ) )
                    {
                        out.add( FindingKind::NonUneScript, static_cast<int>( i ), cmd.script.name,
                                 cmd.script.language );
                    }
                    if( ! cmd.script.inlineCode || cmd.script.fromCount != 1 )
                    {
                        out.add( FindingKind::FileBasedScript, static_cast<int>( i ), cmd.script.name,
                                 std::string_view() );
                    }
                    break;
                }

                default:
                    break; // Unknown/library commands are skipped by buildModel already
            }
        }
    }
} // namespace compat_checker
