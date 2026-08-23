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
/** \file action.cpp
 * Compiled THEN-action (see action.hpp).
 */
#include "stick/action.hpp"

#include "naxe/parser.hpp"

namespace stick
{
    namespace
    {
        bool spanContains( std::string_view s, char c )
        {
            return s.find( c ) != std::string_view::npos;
        }
    } // namespace

    CompiledAction compileAction( int64_t delayMs, std::string_view target, bool hasValue,
                                  const naxe::Value & value, naxe::Arena & arena )
    {
        CompiledAction a;
        a.delayMs = delayMs;
        a.target = target;
        a.hasValue = hasValue;
        a.targetIsExpression = false;
        a.valueIsExpression = false;
        a.valueExpr = nullptr;

        if( ! hasValue )
        {
            a.targetIsExpression = spanContains( target, '(' );
        }
        else if( value.isString() )
        {
            a.valueIsExpression = true;

            naxe::ParseError perr; // discarded: an unparseable value makes this one action
                                   // inert without invalidating the whole Rule/Script.
            a.valueExpr = naxe::parseExpression( arena, value.stringValue(), perr );
        }
        else
        {
            a.literalValue = value;
        }

        return a;
    }

    void fireCompiledAction( const CompiledAction & a, const naxe::Environment & env,
                             naxe::Arena & cycleArena, ActionCallback cb, void * ctx )
    {
        if( ! a.hasValue )
        {
            ActionResult r{ a.targetIsExpression ? ActionKind::Unsupported : ActionKind::Execute,
                            a.target, naxe::Value(), a.delayMs };
            if( cb ) cb( ctx, r );
            return;
        }

        naxe::Value finalValue = a.literalValue;

        if( a.valueIsExpression )
        {
            if( a.valueExpr == nullptr )
            {
                return; // failed to parse at compile time
            }
            finalValue = naxe::eval( a.valueExpr, env, cycleArena );
        }

        ActionResult r{ ActionKind::ChangeActuator, a.target, finalValue, a.delayMs };
        if( cb ) cb( ctx, r );
    }
} // namespace stick
