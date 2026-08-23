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
/** \file rule_manager.cpp
 * Rule registry (see rule_manager.hpp).
 */
#include "stick/rule_manager.hpp"

#include "naxe/util.hpp"

namespace stick
{
    RuleManager::RuleManager() : count_( 0 )
    {
    }

    bool RuleManager::add( const cil::Rule & parsed, naxe::Arena & arena,
                           naxe::GroupResolverFn groupResolver, void * groupResolverCtx )
    {
        if( count_ >= kMaxRules )
        {
            return false;
        }

        rules_[ count_ ] = Rule( parsed, arena, groupResolver, groupResolverCtx );
        ++count_;
        return true;
    }

    Rule * RuleManager::named( std::string_view name )
    {
        for( uint32_t i = 0; i < count_; ++i )
        {
            if( rules_[ i ].hasName() && naxe::util::asciiEqualsIgnoreCase( rules_[ i ].name(), name ) )
            {
                return &rules_[ i ];
            }
        }
        return nullptr;
    }

    const Rule * RuleManager::named( std::string_view name ) const
    {
        return const_cast<RuleManager *>( this )->named( name );
    }

    bool RuleManager::remove( std::string_view name )
    {
        for( uint32_t i = 0; i < count_; ++i )
        {
            if( rules_[ i ].hasName() && naxe::util::asciiEqualsIgnoreCase( rules_[ i ].name(), name ) )
            {
                for( uint32_t j = i; j + 1 < count_; ++j )
                {
                    rules_[ j ] = rules_[ j + 1 ];
                }
                --count_;
                return true;
            }
        }
        return false;
    }
} // namespace stick
