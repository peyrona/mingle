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
/** \file driver_manager.cpp
 * Driver registry (see driver_manager.hpp).
 */
#include "stick/driver_manager.hpp"

#include "naxe/util.hpp"

namespace stick
{
    DriverManager::DriverManager() : count_( 0 )
    {
    }

    bool DriverManager::add( const cil::Driver & parsed )
    {
        if( count_ >= kMaxDrivers )
        {
            return false;
        }

        DriverEntry & e = drivers_[ count_ ];
        e.name = parsed.name;
        e.script = parsed.script;
        e.hasOps = false;
        ++count_;
        return true;
    }

    bool DriverManager::registerOps( std::string_view driverName, const DriverOps & ops )
    {
        for( uint32_t i = 0; i < count_; ++i )
        {
            if( naxe::util::asciiEqualsIgnoreCase( drivers_[ i ].name, driverName ) )
            {
                drivers_[ i ].ops = ops;
                drivers_[ i ].hasOps = true;
                return true;
            }
        }
        return false;
    }

    const DriverEntry * DriverManager::named( std::string_view name ) const
    {
        for( uint32_t i = 0; i < count_; ++i )
        {
            if( naxe::util::asciiEqualsIgnoreCase( drivers_[ i ].name, name ) )
            {
                return &drivers_[ i ];
            }
        }
        return nullptr;
    }

    bool DriverManager::remove( std::string_view name )
    {
        for( uint32_t i = 0; i < count_; ++i )
        {
            if( naxe::util::asciiEqualsIgnoreCase( drivers_[ i ].name, name ) )
            {
                for( uint32_t j = i; j + 1 < count_; ++j )
                {
                    drivers_[ j ] = drivers_[ j + 1 ];
                }
                --count_;
                return true;
            }
        }
        return false;
    }
} // namespace stick
