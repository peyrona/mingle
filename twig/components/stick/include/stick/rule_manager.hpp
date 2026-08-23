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
/** \file rule_manager.hpp
 *
 * The registry of all runtime Rules — a thin fixed-capacity array with case-insensitive
 * lookup; all real logic lives in Rule itself.
 *
 * Tier 1 (hardware-agnostic): no FreeRTOS/ESP-IDF dependency.
 */
#pragma once

#include <cstdint>
#include <string_view>

#include "cil/model.hpp"
#include "stick/rule.hpp"

namespace stick
{
    /** The registry of all runtime Rules. */
    class RuleManager
    {
    public:
        static constexpr uint32_t kMaxRules = 64;

        /** Constructs an empty RuleManager. */
        RuleManager();

        /** Builds a runtime Rule from `parsed` and adds it.
         *
         * @param arena Long-lived arena the rule's ASTs are compiled into.
         * @param groupResolver Optional group-membership resolver for ANY/ALL/NONE.
         * @param groupResolverCtx Opaque context for groupResolver.
         * @return true if added; false if the table is full.
         */
        bool add( const cil::Rule & parsed, naxe::Arena & arena,
                  naxe::GroupResolverFn groupResolver = nullptr, void * groupResolverCtx = nullptr );

        /** Case-insensitive lookup by name (anonymous rules are never matched). */
        Rule * named( std::string_view name );
        /** Const overload. */
        const Rule * named( std::string_view name ) const;

        /** Removes a named rule, compacting the array. */
        bool remove( std::string_view name );

        /** @return The number of rules held. */
        uint32_t count() const { return count_; }
        /** The rule at `index` (must be < count()). */
        Rule & at( uint32_t index ) { return rules_[ index ]; }
        /** Const access. */
        const Rule & at( uint32_t index ) const { return rules_[ index ]; }

    private:
        Rule rules_[ kMaxRules ];
        uint32_t count_;
    };
} // namespace stick
