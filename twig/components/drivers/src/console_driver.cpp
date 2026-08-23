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
/** \file console_driver.cpp
 * Serial console output driver (see console_driver.hpp).
 */
#include "drivers/console_driver.hpp"

#include <cstdio>

#include "stick/message.hpp"

namespace
{
    /** Prints a value in a readable form to the serial console. */
    void printValue( const naxe::Value & value )
    {
        if( value.isString() )
        {
            const std::string_view s = value.stringValue();
            std::printf( "[console] %.*s\n", static_cast<int>( s.size() ), s.data() );
        }
        else if( value.isInt() )
        {
            std::printf( "[console] %lld\n", static_cast<long long>( value.intValue() ) );
        }
        else if( value.isFloat() )
        {
            std::printf( "[console] %g\n", static_cast<double>( value.floatValue() ) );
        }
        else if( value.isBool() )
        {
            std::printf( "[console] %s\n", value.boolValue() ? "true" : "false" );
        }
        else
        {
            std::printf( "[console] <value>\n" );
        }
    }

    void consoleWrite( void * ctx, std::string_view deviceName, const naxe::Value & value )
    {
        printValue( value );

        // Echo the write back as a DeviceChanged so DeviceManager updates this device's
        // own state and any rule reading it can fire -- matches Java's sendChanged().
        static_cast<stick::EventBus *>( ctx )->post( stick::makeDeviceChanged( deviceName, value ) );
    }

    void consoleRead( void *, std::string_view )
    {
    }
} // namespace

namespace drivers
{
    stick::DriverOps consoleOps( stick::EventBus & bus )
    {
        return stick::DriverOps{ &consoleWrite, &consoleRead, &bus };
    }
} // namespace drivers
