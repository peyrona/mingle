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
/** \file cellset_driver.cpp
 * The spreadsheet driver (see cellset_driver.hpp).
 */
#include "drivers/cellset_driver.hpp"

#include <cstdio>

#include "naxe/util.hpp"
#include "stick/logger.hpp"

namespace drivers
{
    CellSetDriver::CellSetDriver( stick::EventBus & bus, naxe::Arena & arena,
                                  naxe::GroupResolverFn groupResolver, void * groupResolverCtx )
        : bus_( &bus )
        , arena_( &arena )
        , groupResolver_( groupResolver )
        , groupResolverCtx_( groupResolverCtx )
        , env_( naxe::Pair::create( kMaxCells ) )
        , count_( 0 )
    {
        configASSERT( env_ != nullptr );
    }

    CellSetDriver::~CellSetDriver()
    {
        if( env_ != nullptr )
        {
            naxe::Pair::destroy( env_ );
        }
    }

    void CellSetDriver::attach()
    {
        const uint8_t mask = static_cast<uint8_t>( 1u << static_cast<uint8_t>( stick::MsgType::DeviceChanged ) );
        bus_->addListener( &CellSetDriver::busListenerTrampoline, this, mask );
    }

    CellSetDriver::Cell * CellSetDriver::find( std::string_view name )
    {
        for( uint32_t i = 0; i < count_; ++i )
        {
            if( naxe::util::asciiEqualsIgnoreCase( cells_[ i ].name, name ) )
            {
                return &cells_[ i ];
            }
        }
        return nullptr;
    }

    const CellSetDriver::Cell * CellSetDriver::find( std::string_view name ) const
    {
        return const_cast<CellSetDriver *>( this )->find( name );
    }

    bool CellSetDriver::referencesVar( const Cell & cell, std::string_view name ) const
    {
        for( uint32_t i = 0; i < cell.referencedVarCount; ++i )
        {
            if( naxe::util::asciiEqualsIgnoreCase( cell.referencedVars[ i ], name ) )
            {
                return true;
            }
        }
        return false;
    }

    bool CellSetDriver::dfsHasCycle( std::string_view originName, const Cell & cell,
                                     std::string_view * visited, uint32_t & visitedCount )
    {
        for( uint32_t i = 0; i < cell.referencedVarCount; ++i )
        {
            const std::string_view var = cell.referencedVars[ i ];

            if( naxe::util::asciiEqualsIgnoreCase( var, originName ) )
            {
                return true;
            }

            bool alreadyVisited = false;
            for( uint32_t j = 0; j < visitedCount; ++j )
            {
                if( naxe::util::asciiEqualsIgnoreCase( visited[ j ], var ) )
                {
                    alreadyVisited = true;
                    break;
                }
            }

            if( alreadyVisited )
            {
                continue;
            }

            if( visitedCount < kMaxCells )
            {
                visited[ visitedCount++ ] = var;
            }

            const Cell * ref = find( var );

            if( ref != nullptr && ref->isFormula && dfsHasCycle( originName, *ref, visited, visitedCount ) )
            {
                return true;
            }
        }

        return false;
    }

    bool CellSetDriver::compile( Cell & cell, const naxe::Value & raw )
    {
        cell.isFormula = false;
        cell.ast = nullptr;
        cell.referencedVarCount = 0;

        if( raw.isString() )
        {
            std::string_view str = raw.stringValue();
            size_t i = 0;

            while( i < str.size() && ( str[ i ] == ' ' || str[ i ] == '\t' ) )
            {
                ++i;
            }

            if( i < str.size() && str[ i ] == '=' )
            {
                const std::string_view formula = str.substr( i + 1 );
                naxe::ParseError err;
                naxe::ASTNode * ast = naxe::parseExpression( *arena_, formula, err, groupResolver_, groupResolverCtx_ );

                if( ast == nullptr )
                {
                    char buf[ 96 ];
                    const int n = std::snprintf( buf, sizeof( buf ), "Error: %s", err.message.c_str() );
                    cell.value = naxe::makeArenaString( *arena_,
                                    std::string_view( buf, n > 0 ? static_cast<size_t>( n ) : 0 ) );
                    cell.hasValue = true;
                    return false;
                }

                cell.ast = ast;
                cell.isFormula = true;
                cell.referencedVarCount = naxe::collectVariableNames( ast, cell.referencedVars, kMaxReferencedVars );
                cell.hasValue = false; // not evaluated yet -- see class doc comment
                return true;
            }

            // Copied into arena_ (not a bare Value::makeString() view): unlike add()'s
            // stable modelArena_-backed config text, a write()-supplied value may be
            // backed by a transient per-cycle arena the caller resets right after -- see
            // the class doc comment's Arena note.
            cell.value = naxe::makeArenaString( *arena_, str.substr( i ) );
            cell.hasValue = true;
            return true;
        }

        cell.value = raw;
        cell.hasValue = true;
        return true;
    }

    bool CellSetDriver::add( const cil::Device & parsed )
    {
        if( count_ >= kMaxCells )
        {
            stick::logger().log( stick::LogLevel::Severe,
                                 "CellSet: table full (max %u), cannot add '%.*s'",
                                 static_cast<unsigned>( kMaxCells ),
                                 static_cast<int>( parsed.name.size() ), parsed.name.data() );
            return false;
        }

        const naxe::Value * raw = parsed.config.pairValue()->get( "value" );

        if( raw == nullptr )
        {
            stick::logger().log( stick::LogLevel::Severe,
                                 "CellSet: missing required 'value' config, unusable device '%.*s'",
                                 static_cast<int>( parsed.name.size() ), parsed.name.data() );
            return false;
        }

        Cell candidate;
        candidate.name = parsed.name;

        if( ! compile( candidate, *raw ) )
        {
            stick::logger().log( stick::LogLevel::Severe,
                                 "CellSet: formula has errors, unusable device '%.*s'",
                                 static_cast<int>( parsed.name.size() ), parsed.name.data() );
            return false;
        }

        if( candidate.isFormula )
        {
            std::string_view visited[ kMaxCells ];
            uint32_t visitedCount = 0;

            if( dfsHasCycle( parsed.name, candidate, visited, visitedCount ) )
            {
                stick::logger().log( stick::LogLevel::Severe,
                                     "CellSet: circular reference detected, unusable device '%.*s'",
                                     static_cast<int>( parsed.name.size() ), parsed.name.data() );
                return false;
            }
        }

        cells_[ count_ ] = candidate;
        ++count_;
        return true;
    }

    void CellSetDriver::onDeviceChanged( std::string_view name, const naxe::Value & value )
    {
        env_->set( name, value );

        for( uint32_t i = 0; i < count_; ++i )
        {
            Cell & cell = cells_[ i ];

            if( ! cell.isFormula || ! referencesVar( cell, name ) )
            {
                continue;
            }

            const naxe::Environment env( env_ );
            const naxe::Value result = naxe::eval( cell.ast, env, *arena_ );

            if( ! cell.hasValue || ! naxe::valueEquals( result, cell.value ) )
            {
                cell.value = result;
                cell.hasValue = true;
                bus_->post( stick::makeDeviceChanged( cell.name, result ) );
            }
        }
    }

    void CellSetDriver::read( std::string_view deviceName )
    {
        const Cell * cell = find( deviceName );

        if( cell != nullptr && cell->hasValue )
        {
            bus_->post( stick::makeDeviceChanged( deviceName, cell->value ) );
        }
    }

    void CellSetDriver::write( std::string_view deviceName, const naxe::Value & newValue )
    {
        Cell * cell = find( deviceName );

        if( cell == nullptr )
        {
            return;
        }

        compile( *cell, newValue ); // errors surface as the cell's own value -- see compile()'s doc comment

        if( cell->hasValue )
        {
            bus_->post( stick::makeDeviceChanged( cell->name, cell->value ) );
        }
    }

    stick::DriverOps CellSetDriver::ops()
    {
        return stick::DriverOps{ &CellSetDriver::writeTrampoline, &CellSetDriver::readTrampoline, this };
    }

    void CellSetDriver::readTrampoline( void * ctx, std::string_view deviceName )
    {
        static_cast<CellSetDriver *>( ctx )->read( deviceName );
    }

    void CellSetDriver::writeTrampoline( void * ctx, std::string_view deviceName, const naxe::Value & value )
    {
        static_cast<CellSetDriver *>( ctx )->write( deviceName, value );
    }

    void CellSetDriver::busListenerTrampoline( void * ctx, const stick::Message & msg )
    {
        if( msg.type == stick::MsgType::DeviceChanged && msg.hasValue )
        {
            static_cast<CellSetDriver *>( ctx )->onDeviceChanged( msg.name, msg.value );
        }
    }
} // namespace drivers
