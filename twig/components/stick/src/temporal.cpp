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
/** \file temporal.cpp
 * AFTER/WITHIN window state machine (see temporal.hpp).
 */
#include "stick/temporal.hpp"

namespace stick
{
    TemporalState * TemporalTable::find( const naxe::ASTNode * node )
    {
        TemporalState * freeSlot = nullptr;

        for( uint32_t i = 0; i < kMaxSlots; ++i )
        {
            if( slots_[ i ].node == node )
            {
                return &slots_[ i ];
            }
            if( freeSlot == nullptr && slots_[ i ].node == nullptr )
            {
                freeSlot = &slots_[ i ];
            }
        }

        if( freeSlot != nullptr )
        {
            freeSlot->node = node;
        }

        return freeSlot; // nullptr if the table is full
    }

    int64_t TemporalTable::nextDeadlineMs() const
    {
        int64_t soonest = -1;

        for( uint32_t i = 0; i < kMaxSlots; ++i )
        {
            if( slots_[ i ].active && ( soonest < 0 || slots_[ i ].deadlineMs < soonest ) )
            {
                soonest = slots_[ i ].deadlineMs;
            }
        }

        return soonest;
    }

    naxe::Value temporalHook( void * ctxRaw, const naxe::ASTNode * node,
                              const naxe::Environment & env, naxe::Arena & arena )
    {
        auto * ctx = static_cast<TemporalCtx *>( ctxRaw );
        TemporalState * slot = ctx->table->find( node );

        if( slot == nullptr )
        {
            return naxe::Value::makeBool( false ); // table full — fail safe
        }

        if( ! slot->active )
        {
            // Arm a new window: the duration is evaluated fresh each time (correctly
            // picks up a changed duration expression across re-arms).
            const naxe::Value durationVal = naxe::eval( node->right, env, arena, temporalHook, ctxRaw );
            int64_t durationMs = 0;
            if( durationVal.isInt() ) durationMs = durationVal.intValue();
            else if( durationVal.isFloat() ) durationMs = static_cast<int64_t>( durationVal.floatValue() );
            if( durationMs < 0 ) durationMs = 0;

            slot->active = true;
            slot->deadlineMs = ctx->nowMs + durationMs;

            if( node->op == naxe::AstOp::Within )
            {
                slot->withinInitial = naxe::eval( node->left, env, arena, temporalHook, ctxRaw );

                // Already false at first observation -> resolves false instantly.
                if( slot->withinInitial.isBool() && ! slot->withinInitial.boolValue() )
                {
                    slot->active = false;
                    return naxe::Value::makeBool( false );
                }
            }

            if( ctx->scheduleWake != nullptr )
            {
                ctx->scheduleWake( ctx->scheduleCtx, durationMs );
            }

            return naxe::Value::makeBool( false ); // pending
        }

        // Active: window already in progress.
        if( node->op == naxe::AstOp::Within )
        {
            const naxe::Value cur = naxe::eval( node->left, env, arena, temporalHook, ctxRaw );
            if( ! naxe::valueEquals( cur, slot->withinInitial ) )
            {
                slot->active = false; // resolved false, one-shot
                return naxe::Value::makeBool( false );
            }
        }

        if( ctx->nowMs < slot->deadlineMs )
        {
            // Not due yet — re-arm for the remaining time (a mid-window check must not
            // silently drop the window's only remaining chance to resolve).
            if( ctx->scheduleWake != nullptr )
            {
                ctx->scheduleWake( ctx->scheduleCtx, slot->deadlineMs - ctx->nowMs );
            }
            return naxe::Value::makeBool( false );
        }

        // Deadline reached: resolve.
        const bool result = ( node->op == naxe::AstOp::After )
                                ? naxe::isTruthy( naxe::eval( node->left, env, arena, temporalHook, ctxRaw ) )
                                : true; // Within survived unchanged for the whole window

        slot->active = false; // one-shot: the next reach arms fresh
        return naxe::Value::makeBool( result );
    }
} // namespace stick
