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
/** \file value.cpp
 * Value factories, accessors, equality and truthiness (see value.hpp).
 */
#include "naxe/value.hpp"

#include <cassert>
#include <cstring>

#include "naxe/list.hpp"
#include "naxe/pair.hpp"
#include "naxe/util.hpp"

namespace naxe
{
    // ---- Factories ------------------------------------------------------------

    Value Value::makeInt( int64_t v )
    {
        Value out;
        out.type_ = ValueType::Int;
        out.i_ = v;
        return out;
    }

    Value Value::makeFloat( float v )
    {
        Value out;
        out.type_ = ValueType::Float;
        out.f_ = v;
        return out;
    }

    Value Value::makeBool( bool v )
    {
        Value out;
        out.type_ = ValueType::Bool;
        out.b_ = v;
        return out;
    }

    Value Value::makeString( std::string_view s )
    {
        Value out;
        out.type_ = ValueType::String;
        out.s_ = s;
        return out;
    }

    Value Value::makeDateTime( int64_t epochSeconds )
    {
        Value out;
        out.type_ = ValueType::DateTime;
        out.i_ = epochSeconds;
        return out;
    }

    Value Value::makeList( List * list )
    {
        Value out;
        out.type_ = ValueType::List;
        out.list_ = list;
        return out;
    }

    Value Value::makePair( Pair * pair )
    {
        Value out;
        out.type_ = ValueType::Pair;
        out.pair_ = pair;
        return out;
    }

    // ---- Accessors ------------------------------------------------------------

    int64_t Value::intValue() const
    {
        assert( type_ == ValueType::Int );
        return i_;
    }

    float Value::floatValue() const
    {
        assert( type_ == ValueType::Float );
        return f_;
    }

    bool Value::boolValue() const
    {
        assert( type_ == ValueType::Bool );
        return b_;
    }

    std::string_view Value::stringValue() const
    {
        assert( type_ == ValueType::String );
        return s_;
    }

    int64_t Value::dateTimeValue() const
    {
        assert( type_ == ValueType::DateTime );
        return i_;
    }

    List * Value::listValue() const
    {
        assert( type_ == ValueType::List );
        return list_;
    }

    Pair * Value::pairValue() const
    {
        assert( type_ == ValueType::Pair );
        return pair_;
    }

    // ---- Comparison -----------------------------------------------------------

    bool valueEquals( const Value & a, const Value & b )
    {
        // Cross-type: Int vs Float promotes the Int to float32 (with the documented
        // precision caveat). No other cross-type pair is equal.
        if( a.type() != b.type() )
        {
            if( a.isInt() && b.isFloat() )
            {
                return static_cast<float>( a.intValue() ) == b.floatValue();
            }
            if( a.isFloat() && b.isInt() )
            {
                return a.floatValue() == static_cast<float>( b.intValue() );
            }
            return false;
        }

        switch( a.type() )
        {
            case ValueType::Int: return a.intValue() == b.intValue();               // exact
            case ValueType::Float: return a.floatValue() == b.floatValue();
            case ValueType::Bool: return a.boolValue() == b.boolValue();
            case ValueType::DateTime: return a.dateTimeValue() == b.dateTimeValue(); // exact
            case ValueType::String:
                return util::asciiEqualsIgnoreCase( a.stringValue(), b.stringValue() ); // Une '==' is case-insensitive
            case ValueType::List: return a.listValue() == b.listValue();             // identity
            case ValueType::Pair: return a.pairValue() == b.pairValue();             // identity
        }

        return false; // unreachable
    }

    // ---- Truthiness -----------------------------------------------------------

    bool isTruthy( const Value & v )
    {
        switch( v.type() )
        {
            case ValueType::Bool: return v.boolValue();
            case ValueType::Int: return v.intValue() != 0;
            case ValueType::Float: return v.floatValue() != 0.0f;
            case ValueType::String: return ! v.stringValue().empty();
            case ValueType::DateTime: return true; // any timestamp is truthy
            case ValueType::List: return v.listValue()->size() > 0;
            case ValueType::Pair: return v.pairValue()->size() > 0;
        }

        return false; // unreachable
    }

    // ---- Arena string copy ----------------------------------------------------

    Value makeArenaString( Arena & arena, std::string_view s )
    {
        if( s.empty() )
        {
            return Value::makeString( s );
        }

        char * buf = static_cast<char *>( arena.alloc( s.size() ) );
        assert( buf != nullptr && "arena exhausted copying a string" );
        std::memcpy( buf, s.data(), s.size() );
        return Value::makeString( std::string_view( buf, s.size() ) );
    }
} // namespace naxe
