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
/** \file builder.cpp
 * CIL command builders from a JsonView over a transpiled .model (see builder.hpp).
 */
#include "cil/builder.hpp"

#include "naxe/list.hpp"
#include "naxe/pair.hpp"

namespace cil
{
    using jsonview::JsonView;
    using jsonview::Type;
    using naxe::Arena;
    using naxe::List;
    using naxe::Pair;
    using naxe::Value;

    namespace
    {
        /** Builds a Device's top-level `config`/`init` Pair value.
         *
         * Absent key OR JSON null -> empty pair. A *present* object's members go through
         * jsonToUneValue() per element (where an individual null member becomes an empty
         * string — a different rule than this top-level case, which becomes an empty Pair).
         *
         * model.hpp documents `config`/`init` as "always a Pair Value (possibly empty)" --
         * callers dereference it via Value::pairValue() with no type check (release builds
         * compile out its assert()), so on allocation failure this reports failure upward
         * (via the bool return) rather than falling back to a non-Pair Value, unlike the
         * generic per-element jsonToUneValue() below, which has no such contract to keep.
         *
         * @param out Receives the built Pair value; untouched on failure.
         * @return True if `out` now holds a valid Pair value.
         */
        bool buildTopLevelConfigPair( const JsonView & v, int cmdIdx, const char * key, Arena & arena, Value & out )
        {
            const int idx = v.findField( cmdIdx, key );

            if( idx < 0 || v.isNull( idx ) || v.type( idx ) != Type::Object )
            {
                Pair * pair = Pair::create( 0 );
                if( pair == nullptr ) return false;
                if( ! arena.defer( pair ) )
                {
                    Pair::destroy( pair );
                    return false;
                }
                out = Value::makePair( pair );
                return true;
            }

            const int n = v.objectFieldCount( idx );
            Pair * pair = Pair::create( static_cast<size_t>( n ) );
            if( pair == nullptr ) return false;
            if( ! arena.defer( pair ) )
            {
                Pair::destroy( pair );
                return false;
            }

            for( int i = 0; i < n; ++i )
            {
                std::string_view keyStr;
                v.getString( v.objectKeyAt( idx, i ), keyStr );
                pair->set( keyStr, jsonToUneValue( v, v.objectValueAt( idx, i ), arena ) );
            }

            out = Value::makePair( pair );
            return true;
        }

        bool buildDevice( const JsonView & v, int cmdIdx, Arena & arena, Device & out )
        {
            const int nameIdx = v.findField( cmdIdx, "name" );
            if( nameIdx >= 0 && v.type( nameIdx ) == Type::String )
            {
                v.getString( nameIdx, out.name );
            }
            else
            {
                out.name = std::string_view();
            }

            const int driverIdx = v.findField( cmdIdx, "driver" );
            if( driverIdx >= 0 && v.type( driverIdx ) == Type::String )
            {
                v.getString( driverIdx, out.driver );
            }
            else
            {
                out.driver = std::string_view();
            }

            if( ! buildTopLevelConfigPair( v, cmdIdx, "config", arena, out.config ) ) return false;
            if( ! buildTopLevelConfigPair( v, cmdIdx, "init", arena, out.init ) ) return false;
            return true;
        }

        bool buildDriver( const JsonView & v, int cmdIdx, Driver & out )
        {
            const int nameIdx = v.findField( cmdIdx, "name" );
            const int scriptIdx = v.findField( cmdIdx, "script" );

            return nameIdx >= 0 && scriptIdx >= 0
                   && v.getString( nameIdx, out.name )
                   && v.getString( scriptIdx, out.script );
        }

        bool buildRule( const JsonView & v, int cmdIdx, Arena & arena, Rule & out )
        {
            const int nameIdx = v.findField( cmdIdx, "name" );
            if( nameIdx < 0 )
            {
                return false;
            }

            out.hasName = ! v.isNull( nameIdx );
            if( out.hasName )
            {
                if( ! v.getString( nameIdx, out.name ) ) return false;
            }
            else
            {
                out.name = std::string_view();
            }

            const int whenIdx = v.findField( cmdIdx, "when" );
            if( whenIdx < 0 || ! v.getString( whenIdx, out.when ) ) return false;

            const int ifIdx = v.findField( cmdIdx, "if" );
            if( ifIdx < 0 ) return false;

            out.hasIf = ! v.isNull( ifIdx );
            if( out.hasIf )
            {
                if( ! v.getString( ifIdx, out.ifExpr ) ) return false;
            }
            else
            {
                out.ifExpr = std::string_view();
            }

            const int thenIdx = v.findField( cmdIdx, "then" );
            if( thenIdx < 0 || v.type( thenIdx ) != Type::Array ) return false;

            const int count = v.arrayCount( thenIdx );
            out.actions = static_cast<RuleAction *>(
                arena.alloc( sizeof( RuleAction ) * static_cast<size_t>( count ) ) );
            out.actionCount = static_cast<uint32_t>( count );

            for( int i = 0; i < count; ++i )
            {
                const int actIdx = v.arrayElement( thenIdx, i );
                const int afterIdx = v.findField( actIdx, "after" );
                const int targetIdx = v.findField( actIdx, "target" );
                const int valueIdx = v.findField( actIdx, "value" );

                if( afterIdx < 0 || targetIdx < 0 || valueIdx < 0 ) return false;

                int64_t after;
                if( ! v.getInt64( afterIdx, after ) )
                {
                    float f;
                    if( ! v.getFloat( afterIdx, f ) ) return false;
                    after = static_cast<int64_t>( f );
                }
                out.actions[ i ].after = after;

                if( ! v.getString( targetIdx, out.actions[ i ].target ) ) return false;

                out.actions[ i ].hasValue = ! v.isNull( valueIdx );
                if( out.actions[ i ].hasValue )
                {
                    out.actions[ i ].value = jsonToUneValue( v, valueIdx, arena );
                }
            }

            return true;
        }

        bool buildScript( const JsonView & v, int cmdIdx, Arena & arena, Script & out )
        {
            auto reqStr = [ & ]( const char * key, std::string_view & dst ) -> bool
            {
                const int idx = v.findField( cmdIdx, key );
                return idx >= 0 && v.getString( idx, dst );
            };
            auto reqBool = [ & ]( const char * key, bool & dst ) -> bool
            {
                const int idx = v.findField( cmdIdx, key );
                return idx >= 0 && v.getBool( idx, dst );
            };

            if( ! reqStr( "name", out.name ) ) return false;
            if( ! reqStr( "language", out.language ) ) return false;
            if( ! reqBool( "onstart", out.onstart ) ) return false;
            if( ! reqBool( "onstop", out.onstop ) ) return false;
            if( ! reqBool( "prestart", out.prestart ) ) return false;
            if( ! reqBool( "prestop", out.prestop ) ) return false;
            if( ! reqBool( "inline", out.inlineCode ) ) return false;
            if( ! reqStr( "call", out.call ) ) return false;

            const int fromIdx = v.findField( cmdIdx, "from" );
            if( fromIdx < 0 || v.type( fromIdx ) != Type::Array ) return false;

            const int count = v.arrayCount( fromIdx );
            out.from = static_cast<std::string_view *>(
                arena.alloc( sizeof( std::string_view ) * static_cast<size_t>( count ) ) );
            out.fromCount = static_cast<uint32_t>( count );

            for( int i = 0; i < count; ++i )
            {
                if( ! v.getString( v.arrayElement( fromIdx, i ), out.from[ i ] ) ) return false;
            }

            return true;
        }
    } // namespace

    Value jsonToUneValue( const JsonView & v, int idx, Arena & arena )
    {
        if( v.isNull( idx ) )
        {
            return Value::makeString( std::string_view() ); // UtilJson.toUneType(null) -> ""
        }

        switch( v.type( idx ) )
        {
            case Type::String:
            {
                std::string_view s;
                v.getString( idx, s );
                return naxe::makeArenaString( arena, s ); // copy: the source buffer may be transient
            }

            case Type::Primitive:
            {
                bool b;
                if( v.getBool( idx, b ) )
                {
                    return Value::makeBool( b );
                }

                std::string_view span;
                v.getRawSpan( idx, span );
                if( span.find( '.' ) != std::string_view::npos )
                {
                    float f;
                    v.getFloat( idx, f );
                    return Value::makeFloat( f );
                }

                int64_t i;
                if( v.getInt64( idx, i ) )
                {
                    return Value::makeInt( i ); // integral JSON number -> Int
                }

                float f;
                v.getFloat( idx, f );
                return Value::makeFloat( f );
            }

            case Type::Object:
            {
                const int n = v.objectFieldCount( idx );
                Pair * pair = Pair::create( static_cast<size_t>( n ) );
                if( pair == nullptr ) return Value::makeString( std::string_view() );
                if( ! arena.defer( pair ) )
                {
                    Pair::destroy( pair );
                    return Value::makeString( std::string_view() );
                }

                for( int i = 0; i < n; ++i )
                {
                    std::string_view key;
                    v.getString( v.objectKeyAt( idx, i ), key );
                    pair->set( key, jsonToUneValue( v, v.objectValueAt( idx, i ), arena ) );
                }

                return Value::makePair( pair );
            }

            case Type::Array:
            {
                const int n = v.arrayCount( idx );
                List * list = List::create( static_cast<size_t>( n ) );
                if( list == nullptr ) return Value::makeString( std::string_view() );
                if( ! arena.defer( list ) )
                {
                    List::destroy( list );
                    return Value::makeString( std::string_view() );
                }

                for( int i = 0; i < n; ++i )
                {
                    list->add( jsonToUneValue( v, v.arrayElement( idx, i ), arena ) );
                }

                return Value::makeList( list );
            }

            default:
                return Value::makeString( std::string_view() );
        }
    }

    bool buildCommand( const JsonView & v, int cmdIdx, Arena & arena, Command & out )
    {
        const int cmdFieldIdx = v.findField( cmdIdx, "cmd" );

        if( cmdFieldIdx < 0 )
        {
            out.type = CommandType::Unknown;
            return false;
        }

        if( v.stringEquals( cmdFieldIdx, "device" ) )
        {
            out.type = CommandType::Device;
            return buildDevice( v, cmdIdx, arena, out.device );
        }
        if( v.stringEquals( cmdFieldIdx, "driver" ) )
        {
            out.type = CommandType::Driver;
            return buildDriver( v, cmdIdx, out.driver );
        }
        if( v.stringEquals( cmdFieldIdx, "rule" ) )
        {
            out.type = CommandType::Rule;
            return buildRule( v, cmdIdx, arena, out.rule );
        }
        if( v.stringEquals( cmdFieldIdx, "script" ) )
        {
            out.type = CommandType::Script;
            return buildScript( v, cmdIdx, arena, out.script );
        }

        // "library" (dropped) and anything else.
        out.type = CommandType::Unknown;
        return false;
    }

    int buildModel( const JsonView & v, Arena & arena, Command * outCommands, int outCapacity )
    {
        const int commandsIdx = v.findField( v.root(), "commands" );

        if( commandsIdx < 0 || v.type( commandsIdx ) != Type::Array )
        {
            return 0;
        }

        const int total = v.arrayCount( commandsIdx );
        int built = 0;

        for( int i = 0; i < total && built < outCapacity; ++i )
        {
            if( buildCommand( v, v.arrayElement( commandsIdx, i ), arena, outCommands[ built ] ) )
            {
                ++built;
            }
        }

        return built;
    }
} // namespace cil
