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
/** \file device.cpp
 * Runtime Device state (see device.hpp).
 */
#include "stick/device.hpp"

#include <cassert>

#include "naxe/datetime.hpp"
#include "naxe/pair.hpp"
#include "naxe/util.hpp"
#include "stick/logger.hpp"

namespace stick
{
    Device::Device()
        : parsed_( nullptr )
        , hasValue_( false )
        , hasDelta_( false )
        , delta_( 0.0f )
        , deltaErrorLogged_( false )
        , downtimeSeconds_( 0 )
        , lastSetEpochSeconds_( 0 )
        , groupCount_( 0 )
    {
        value_ = naxe::Value::makeFloat( 0.0f ); // placeholder until hasValue_
    }

    Device::Device( const cil::Device & parsed )
        : parsed_( &parsed )
        , hasValue_( false )
        , hasDelta_( false )
        , delta_( 0.0f )
        , deltaErrorLogged_( false )
        , downtimeSeconds_( 0 )
        , lastSetEpochSeconds_( naxe::nowEpochSeconds() )
        , groupCount_( 0 )
    {
        value_ = naxe::Value::makeFloat( 0.0f );

        // init is always a Pair Value (cil::buildDevice guarantees it).
        const naxe::Pair * init = parsed_->init.pairValue();

        if( const naxe::Value * deltaVal = init->get( "delta" ) )
        {
            if( deltaVal->isInt() || deltaVal->isFloat() )
            {
                float d = deltaVal->isInt() ? static_cast<float>( deltaVal->intValue() )
                                            : deltaVal->floatValue();
                if( d < 0 ) d = -d;
                if( d >= 0.0001f )
                {
                    hasDelta_ = true;
                    delta_ = d;
                }
            }
        }

        if( const naxe::Value * downtimeVal = init->get( "downtime" ) )
        {
            if( downtimeVal->isInt() || downtimeVal->isFloat() )
            {
                float dtMs = downtimeVal->isInt() ? static_cast<float>( downtimeVal->intValue() )
                                                  : downtimeVal->floatValue();
                if( dtMs < 0 ) dtMs = -dtMs;
                downtimeSeconds_ = static_cast<int64_t>( dtMs / 1000.0f );
            }
        }

        parseGroups();
    }

    const naxe::Value & Device::value() const
    {
        assert( hasValue_ && "Device::value() before first value" );
        return value_;
    }

    bool Device::setValue( const naxe::Value & newValue )
    {
        lastSetEpochSeconds_ = naxe::nowEpochSeconds();

        if( isEnough( newValue ) )
        {
            value_ = newValue;
            hasValue_ = true;
            return true;
        }

        return false;
    }

    bool Device::isDowntimed()
    {
        if( downtimeSeconds_ <= 0 )
        {
            return false;
        }

        const int64_t nowSec = naxe::nowEpochSeconds();
        const bool elapsed = ( nowSec - lastSetEpochSeconds_ ) >= downtimeSeconds_;

        if( elapsed )
        {
            lastSetEpochSeconds_ = nowSec;
        }

        return elapsed;
    }

    bool Device::isMemberOfGroup( std::string_view name ) const
    {
        for( uint32_t i = 0; i < groupCount_; ++i )
        {
            if( naxe::util::asciiEqualsIgnoreCase( groups_[ i ], name ) )
            {
                return true;
            }
        }
        return false;
    }

    bool Device::isEnough( const naxe::Value & newValue )
    {
        if( ! hasDelta_ || ! hasValue_ )
        {
            return true;
        }

        if( newValue.isFloat() && value_.isFloat() )
        {
            float diff = newValue.floatValue() - value_.floatValue();
            if( diff < 0 ) diff = -diff;
            return diff >= delta_;
        }

        // Non-numeric value with a configured delta: ignore the delta, warn once (not on
        // every setValue()) so this doesn't spam the ring buffer for a config mistake.
        if( ! deltaErrorLogged_ )
        {
            deltaErrorLogged_ = true;
            logger().log( LogLevel::Warning,
                          "device '%.*s' has a delta configured but its value is not "
                          "numeric -- delta ignored from now on",
                          static_cast<int>( name().size() ), name().data() );
        }

        return true;
    }

    void Device::parseGroups()
    {
        groupCount_ = 0;

        const naxe::Value * groupsVal = parsed_->init.pairValue()->get( "groups" );

        if( groupsVal == nullptr || ! groupsVal->isString() )
        {
            return;
        }

        const std::string_view data = groupsVal->stringValue();
        size_t start = 0;

        for( size_t i = 0; i <= data.size(); ++i )
        {
            if( i == data.size() || data[ i ] == ',' )
            {
                size_t tokenStart = start;
                size_t tokenEnd = i;

                while( tokenStart < tokenEnd && data[ tokenStart ] == ' ' ) ++tokenStart;
                while( tokenEnd > tokenStart && data[ tokenEnd - 1 ] == ' ' ) --tokenEnd;

                if( tokenEnd > tokenStart && groupCount_ < kMaxGroups )
                {
                    groups_[ groupCount_++ ] = data.substr( tokenStart, tokenEnd - tokenStart );
                }

                start = i + 1;
            }
        }
    }
} // namespace stick
