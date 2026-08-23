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
/** \file device_manager.cpp
 * Device registry (see device_manager.hpp).
 */
#include "stick/device_manager.hpp"

#include "naxe/util.hpp"

namespace stick
{
    DeviceManager::DeviceManager() : count_( 0 )
    {
    }

    bool DeviceManager::add( const cil::Device & parsed )
    {
        if( named( parsed.name ) != nullptr || count_ >= kMaxDevices )
        {
            return false;
        }

        devices_[ count_ ] = Device( parsed );
        ++count_;
        return true;
    }

    Device * DeviceManager::named( std::string_view name )
    {
        for( uint32_t i = 0; i < count_; ++i )
        {
            if( naxe::util::asciiEqualsIgnoreCase( devices_[ i ].name(), name ) )
            {
                return &devices_[ i ];
            }
        }
        return nullptr;
    }

    const Device * DeviceManager::named( std::string_view name ) const
    {
        return const_cast<DeviceManager *>( this )->named( name );
    }

    bool DeviceManager::remove( std::string_view name )
    {
        for( uint32_t i = 0; i < count_; ++i )
        {
            if( naxe::util::asciiEqualsIgnoreCase( devices_[ i ].name(), name ) )
            {
                for( uint32_t j = i; j + 1 < count_; ++j )
                {
                    devices_[ j ] = devices_[ j + 1 ];
                }
                --count_;
                return true;
            }
        }
        return false;
    }

    uint32_t DeviceManager::getGroupMemberNames( std::string_view group, std::string_view * out,
                                                 uint32_t outCapacity ) const
    {
        uint32_t written = 0;

        for( uint32_t i = 0; i < count_ && written < outCapacity; ++i )
        {
            if( devices_[ i ].isMemberOfGroup( group ) )
            {
                out[ written++ ] = devices_[ i ].name();
            }
        }

        return written;
    }
} // namespace stick
