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
/** \file json_view.cpp
 * Implementation of the read-only JsonView (see json_view.hpp).
 */
#include "jsonview/json_view.hpp"

#include <cstdlib>
#include <cstring>

namespace jsonview
{
    JsonView::JsonView( const char * buf, size_t len, jsmntok_t * tokens, unsigned maxTokens )
        : buf_( buf ), len_( len ), tokens_( tokens ), parsedCount_( 0 )
    {
        jsmn_parser parser;
        jsmn_init( &parser );
        parsedCount_ = jsmn_parse( &parser, buf_, len_, tokens_, maxTokens );
    }

    Type JsonView::type( int idx ) const
    {
        switch( tokens_[ idx ].type )
        {
            case JSMN_OBJECT:    return Type::Object;
            case JSMN_ARRAY:     return Type::Array;
            case JSMN_STRING:    return Type::String;
            case JSMN_PRIMITIVE: return Type::Primitive;
            default:             return Type::Undefined;
        }
    }

    int JsonView::skipSubtree( int idx ) const
    {
        const jsmntok_t & t = tokens_[ idx ];

        if( t.type == JSMN_OBJECT )
        {
            int next = idx + 1;
            for( int i = 0; i < t.size; ++i )
            {
                next = skipSubtree( next ); // key (string)
                next = skipSubtree( next ); // value
            }
            return next;
        }

        if( t.type == JSMN_ARRAY )
        {
            int next = idx + 1;
            for( int i = 0; i < t.size; ++i )
            {
                next = skipSubtree( next );
            }
            return next;
        }

        return idx + 1; // String / Primitive / Undefined
    }

    int JsonView::findField( int idx, const char * key ) const
    {
        if( tokens_[ idx ].type != JSMN_OBJECT )
        {
            return -1;
        }

        int pos = idx + 1;

        for( int i = 0; i < tokens_[ idx ].size; ++i )
        {
            const int keyIdx = pos;
            const int valIdx = skipSubtree( keyIdx );

            if( stringEquals( keyIdx, key ) )
            {
                return valIdx;
            }

            pos = skipSubtree( valIdx );
        }

        return -1;
    }

    int JsonView::arrayCount( int idx ) const
    {
        return ( tokens_[ idx ].type == JSMN_ARRAY ) ? tokens_[ idx ].size : 0;
    }

    int JsonView::arrayElement( int idx, int i ) const
    {
        int pos = idx + 1;
        for( int k = 0; k < i; ++k )
        {
            pos = skipSubtree( pos );
        }
        return pos;
    }

    int JsonView::objectFieldCount( int idx ) const
    {
        return ( tokens_[ idx ].type == JSMN_OBJECT ) ? tokens_[ idx ].size : 0;
    }

    int JsonView::objectKeyAt( int idx, int i ) const
    {
        int pos = idx + 1;
        for( int k = 0; k < i; ++k )
        {
            pos = skipSubtree( pos ); // key
            pos = skipSubtree( pos ); // value
        }
        return pos;
    }

    int JsonView::objectValueAt( int idx, int i ) const
    {
        return skipSubtree( objectKeyAt( idx, i ) );
    }

    bool JsonView::getString( int idx, std::string_view & out ) const
    {
        if( tokens_[ idx ].type != JSMN_STRING )
        {
            return false;
        }
        out = std::string_view( buf_ + tokens_[ idx ].start,
                                static_cast<size_t>( tokens_[ idx ].end - tokens_[ idx ].start ) );
        return true;
    }

    bool JsonView::getFloat( int idx, float & out ) const
    {
        if( tokens_[ idx ].type != JSMN_PRIMITIVE )
        {
            return false;
        }

        const int spanLen = tokens_[ idx ].end - tokens_[ idx ].start;
        char tmp[ 64 ];
        if( spanLen <= 0 || static_cast<size_t>( spanLen ) >= sizeof( tmp ) )
        {
            return false;
        }

        std::memcpy( tmp, buf_ + tokens_[ idx ].start, static_cast<size_t>( spanLen ) );
        tmp[ spanLen ] = '\0';

        char * end = nullptr;
        out = std::strtof( tmp, &end );
        return end != tmp;
    }

    bool JsonView::getInt64( int idx, int64_t & out ) const
    {
        if( tokens_[ idx ].type != JSMN_PRIMITIVE )
        {
            return false;
        }

        const int spanLen = tokens_[ idx ].end - tokens_[ idx ].start;
        char tmp[ 64 ];
        if( spanLen <= 0 || static_cast<size_t>( spanLen ) >= sizeof( tmp ) )
        {
            return false;
        }

        std::memcpy( tmp, buf_ + tokens_[ idx ].start, static_cast<size_t>( spanLen ) );
        tmp[ spanLen ] = '\0';

        char * end = nullptr;
        out = std::strtoll( tmp, &end, 10 );
        return end != tmp && *end == '\0';
    }

    bool JsonView::getBool( int idx, bool & out ) const
    {
        if( tokens_[ idx ].type != JSMN_PRIMITIVE )
        {
            return false;
        }
        if( stringEquals( idx, "true" ) )
        {
            out = true;
            return true;
        }
        if( stringEquals( idx, "false" ) )
        {
            out = false;
            return true;
        }
        return false;
    }

    bool JsonView::isNull( int idx ) const
    {
        return tokens_[ idx ].type == JSMN_PRIMITIVE && stringEquals( idx, "null" );
    }

    bool JsonView::getRawSpan( int idx, std::string_view & out ) const
    {
        const int start = tokens_[ idx ].start;
        const int end = tokens_[ idx ].end;
        if( start < 0 || end < start )
        {
            return false;
        }
        out = std::string_view( buf_ + start, static_cast<size_t>( end - start ) );
        return true;
    }

    bool JsonView::stringEquals( int idx, const char * s ) const
    {
        if( tokens_[ idx ].type != JSMN_STRING && tokens_[ idx ].type != JSMN_PRIMITIVE )
        {
            return false;
        }

        const size_t tokenLen = static_cast<size_t>( tokens_[ idx ].end - tokens_[ idx ].start );
        const size_t sLen = std::strlen( s );

        return tokenLen == sLen && std::memcmp( buf_ + tokens_[ idx ].start, s, sLen ) == 0;
    }
} // namespace jsonview
