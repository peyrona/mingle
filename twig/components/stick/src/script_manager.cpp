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
/** \file script_manager.cpp
 * Script registry (see script_manager.hpp).
 */
#include "stick/script_manager.hpp"

#include "naxe/util.hpp"

namespace stick
{
    ScriptManager::ScriptManager() : count_( 0 )
    {
    }

    bool ScriptManager::add( const cil::Script & parsed, naxe::Arena & arena )
    {
        if( count_ >= kMaxScripts )
        {
            return false;
        }

        scripts_[ count_ ] = Script( parsed, arena );
        ++count_;
        return true;
    }

    Script * ScriptManager::named( std::string_view name )
    {
        for( uint32_t i = 0; i < count_; ++i )
        {
            if( naxe::util::asciiEqualsIgnoreCase( scripts_[ i ].name(), name ) )
            {
                return &scripts_[ i ];
            }
        }
        return nullptr;
    }

    const Script * ScriptManager::named( std::string_view name ) const
    {
        return const_cast<ScriptManager *>( this )->named( name );
    }

    bool ScriptManager::remove( std::string_view name )
    {
        for( uint32_t i = 0; i < count_; ++i )
        {
            if( naxe::util::asciiEqualsIgnoreCase( scripts_[ i ].name(), name ) )
            {
                for( uint32_t j = i; j + 1 < count_; ++j )
                {
                    scripts_[ j ] = scripts_[ j + 1 ];
                }
                --count_;
                return true;
            }
        }
        return false;
    }
} // namespace stick
