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
/** \file rule.cpp
 * Compiled Rule (see rule.hpp).
 */
#include "stick/rule.hpp"

#include <cassert>

#include "naxe/util.hpp"

namespace stick
{
    Rule::Rule()
        : referencedVarCount_( 0 )
        , parsed_( nullptr )
        , whenAst_( nullptr )
        , hasIf_( false )
        , ifAst_( nullptr )
        , enabled_( false )
        , actions_( nullptr )
        , actionCount_( 0 )
    {
    }

    Rule::Rule( const cil::Rule & parsed, naxe::Arena & arena,
                naxe::GroupResolverFn groupResolver, void * groupResolverCtx )
        : referencedVarCount_( 0 )
        , parsed_( &parsed )
        , whenAst_( nullptr )
        , hasIf_( parsed.hasIf )
        , ifAst_( nullptr )
        , enabled_( true )
        , actions_( nullptr )
        , actionCount_( parsed.actionCount )
    {
        whenAst_ = naxe::parseExpression( arena, parsed.when, whenErr_, groupResolver, groupResolverCtx );

        if( hasIf_ )
        {
            ifAst_ = naxe::parseExpression( arena, parsed.ifExpr, ifErr_, groupResolver, groupResolverCtx );
        }

        referencedVarCount_ = naxe::collectVariableNames( whenAst_, referencedVars_, kMaxReferencedVars );

        if( hasIf_ && referencedVarCount_ < kMaxReferencedVars )
        {
            referencedVarCount_ += naxe::collectVariableNames( ifAst_, referencedVars_ + referencedVarCount_,
                                                               kMaxReferencedVars - referencedVarCount_ );
        }

        if( actionCount_ > 0 )
        {
            actions_ = static_cast<CompiledAction *>( arena.alloc( sizeof( CompiledAction ) * actionCount_ ) );
            assert( actions_ != nullptr && "arena exhausted building rule actions" );

            for( uint32_t i = 0; i < actionCount_; ++i )
            {
                const cil::RuleAction & src = parsed.actions[ i ];
                actions_[ i ] = compileAction( ( src.after < 0 ) ? 0 : src.after, src.target,
                                               src.hasValue, src.value, arena );
            }
        }
    }

    std::string_view Rule::name() const
    {
        return hasName() ? parsed_->name : std::string_view();
    }

    bool Rule::evaluate( const naxe::Environment & env, naxe::Arena & cycleArena,
                         ActionCallback cb, void * ctx,
                         int64_t nowMs, ScheduleWakeFn scheduleWake, void * scheduleCtx ) const
    {
        if( ! enabled_ || ! isValid() )
        {
            return false;
        }

        TemporalCtx tctx{ &temporal_, nowMs, scheduleWake, scheduleCtx };

        const naxe::Value whenResult = naxe::eval( whenAst_, env, cycleArena, temporalHook, &tctx );
        if( ! naxe::isTruthy( whenResult ) )
        {
            return false;
        }

        if( hasIf_ )
        {
            const naxe::Value ifResult = naxe::eval( ifAst_, env, cycleArena, temporalHook, &tctx );
            if( ! naxe::isTruthy( ifResult ) )
            {
                return false;
            }
        }

        fireActions( env, cycleArena, cb, ctx );
        return true;
    }

    bool Rule::trigger( bool force, const naxe::Environment & env, naxe::Arena & cycleArena,
                        ActionCallback cb, void * ctx,
                        int64_t nowMs, ScheduleWakeFn scheduleWake, void * scheduleCtx ) const
    {
        if( ! enabled_ || ! isValid() )
        {
            return false;
        }

        if( force )
        {
            fireActions( env, cycleArena, cb, ctx );
            return true;
        }

        if( hasIf_ )
        {
            return false; // matches Java: "can not be triggered because it has an IF clause"
        }

        TemporalCtx tctx{ &temporal_, nowMs, scheduleWake, scheduleCtx };

        const naxe::Value whenResult = naxe::eval( whenAst_, env, cycleArena, temporalHook, &tctx );
        if( naxe::isTruthy( whenResult ) )
        {
            fireActions( env, cycleArena, cb, ctx );
            return true;
        }

        return false;
    }

    void Rule::fireActions( const naxe::Environment & env, naxe::Arena & cycleArena,
                            ActionCallback cb, void * ctx ) const
    {
        for( uint32_t i = 0; i < actionCount_; ++i )
        {
            fireCompiledAction( actions_[ i ], env, cycleArena, cb, ctx );
        }
    }

    bool Rule::referencesDevice( std::string_view name ) const
    {
        if( referencedVarCount_ == 0 )
        {
            return true; // constant WHEN, nothing to scope by
        }

        for( uint32_t i = 0; i < referencedVarCount_; ++i )
        {
            if( naxe::util::asciiEqualsIgnoreCase( referencedVars_[ i ], name ) )
            {
                return true;
            }
        }

        return false;
    }
} // namespace stick
