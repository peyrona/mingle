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
/** \file script.cpp
 * Compiled Script (see script.hpp).
 */
#include "stick/script.hpp"

#include <cassert>

#include "naxe/parser.hpp"
#include "naxe/util.hpp"

namespace stick
{
    namespace
    {
        constexpr uint32_t kMaxStatements = 64;

        /** Trims leading/trailing whitespace. */
        std::string_view trim( std::string_view s )
        {
            size_t start = 0, end = s.size();
            while( start < end && static_cast<unsigned char>( s[ start ] ) <= 32 ) ++start;
            while( end > start && static_cast<unsigned char>( s[ end - 1 ] ) <= 32 ) --end;
            return s.substr( start, end - start );
        }

        /** Splits inline Une source into trimmed statement spans on ';' or '\n' outside
         * string literals (escape-aware). */
        uint32_t splitStatements( std::string_view src, std::string_view * out, uint32_t outCap )
        {
            uint32_t count = 0;
            size_t start = 0;
            bool inString = false;

            auto emit = [ & ]( size_t end )
            {
                const std::string_view s = trim( src.substr( start, end - start ) );
                if( ! s.empty() && count < outCap )
                {
                    out[ count++ ] = s;
                }
            };

            for( size_t i = 0; i < src.size(); ++i )
            {
                const char c = src[ i ];

                if( inString )
                {
                    if( c == '\\' && i + 1 < src.size() ) { ++i; continue; }
                    if( c == '"' ) inString = false;
                    continue;
                }

                if( c == '"' ) { inString = true; continue; }

                if( c == ';' || c == '\n' )
                {
                    emit( i );
                    start = i + 1;
                }
            }

            emit( src.size() );
            return count;
        }

        /** Splits an assignment statement at the first top-level '=' (not ==/!=/<=/>=). */
        bool splitAssignment( std::string_view stmt, std::string_view & target, std::string_view & value )
        {
            bool inString = false;

            for( size_t i = 0; i < stmt.size(); ++i )
            {
                const char c = stmt[ i ];

                if( inString )
                {
                    if( c == '\\' && i + 1 < stmt.size() ) { ++i; continue; }
                    if( c == '"' ) inString = false;
                    continue;
                }

                if( c == '"' ) { inString = true; continue; }

                if( c == '=' )
                {
                    const char prev = ( i > 0 ) ? stmt[ i - 1 ] : '\0';
                    const char next = ( i + 1 < stmt.size() ) ? stmt[ i + 1 ] : '\0';

                    if( next == '=' || prev == '=' || prev == '!' || prev == '<' || prev == '>' )
                    {
                        continue; // part of ==, !=, <=, >=
                    }

                    target = trim( stmt.substr( 0, i ) );
                    value = trim( stmt.substr( i + 1 ) );
                    return true;
                }
            }

            return false;
        }
    } // namespace

    Script::Script()
        : parsed_( nullptr ), valid_( false ), statements_( nullptr ), statementCount_( 0 )
    {
    }

    Script::Script( const cil::Script & parsed, naxe::Arena & arena )
        : parsed_( &parsed ), valid_( false ), statements_( nullptr ), statementCount_( 0 )
    {
        if( ! naxe::util::asciiEqualsIgnoreCase( parsed.language, "une" ) )
        {
            return; // non-Une language not supported
        }

        if( ! parsed.inlineCode || parsed.fromCount != 1 )
        {
            return; // file-based ("FROM <uri>") scripts not supported
        }

        const std::string_view unescaped = naxe::jsonUnescape( arena, parsed.from[ 0 ] );

        std::string_view rawStatements[ kMaxStatements ];
        const uint32_t rawCount = splitStatements( unescaped, rawStatements, kMaxStatements );

        if( rawCount > 0 )
        {
            statements_ = static_cast<CompiledAction *>( arena.alloc( sizeof( CompiledAction ) * rawCount ) );
            assert( statements_ != nullptr && "arena exhausted building script statements" );

            for( uint32_t i = 0; i < rawCount; ++i )
            {
                std::string_view target, valueText;

                if( splitAssignment( rawStatements[ i ], target, valueText ) )
                {
                    statements_[ i ] = compileAction( 0, target, true, naxe::Value::makeString( valueText ), arena );
                }
                else
                {
                    statements_[ i ] = compileAction( 0, rawStatements[ i ], false, naxe::Value(), arena );
                }
            }
        }

        statementCount_ = rawCount;
        valid_ = true; // an empty body is a valid no-op script
    }

    void Script::execute( const naxe::Environment & env, naxe::Arena & cycleArena,
                          ActionCallback cb, void * ctx ) const
    {
        if( ! valid_ )
        {
            return;
        }

        for( uint32_t i = 0; i < statementCount_; ++i )
        {
            fireCompiledAction( statements_[ i ], env, cycleArena, cb, ctx );
        }
    }
} // namespace stick
