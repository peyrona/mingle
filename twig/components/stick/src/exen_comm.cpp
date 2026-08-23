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
/** \file exen_comm.cpp
 * Control-channel wire protocol parser/builders (see exen_comm.hpp).
 */
#include "stick/exen_comm.hpp"

#include <cstdio>
#include <cstring>

#include "cil/builder.hpp"
#include "naxe/parser.hpp"

using jsonview::JsonView;
using jsonview::Type;

namespace stick
{
    namespace
    {
        /** Copies raw bytes into `arena` and returns the copy as a std::string_view.
         *
         * See exen_comm.hpp's top comment: copies raw bytes into `arena` so a subsequent
         * re-parse's string_views point into long-lived storage, not a transient receive
         * buffer.
         *
         * @param arena The arena the copy is allocated from.
         * @param src The source bytes to copy.
         * @param out Receives the copy.
         * @return true on success, false if the arena could not satisfy the allocation.
         */
        bool copyRawIntoArena( naxe::Arena & arena, std::string_view src, std::string_view & out )
        {
            if( src.empty() )
            {
                out = std::string_view();
                return true;
            }

            char * dst = static_cast<char *>( arena.alloc( src.size() ) );

            if( dst == nullptr )
            {
                return false;
            }

            std::memcpy( dst, src.data(), src.size() );
            out = std::string_view( dst, src.size() );
            return true;
        }

        /** Maps a JSON object key to its inbound request enum value.
         *
         * @param v The JSON view containing the key.
         * @param keyIdx Index of the key to match.
         * @param out Receives the matched request enum value.
         * @return true if the key matched a known request, false otherwise.
         */
        bool requestFromKey( const JsonView & v, int keyIdx, ExRequest & out )
        {
            struct Entry
            {
                const char * text;
                ExRequest req;
            };

            static const Entry table[] = {
                { "List", ExRequest::List },       { "Listed", ExRequest::Listed },
                { "Add", ExRequest::Add },         { "Added", ExRequest::Added },
                { "Remove", ExRequest::Remove },   { "Removed", ExRequest::Removed },
                { "Changed", ExRequest::Changed }, { "Change", ExRequest::Change },
                { "Read", ExRequest::Read },       { "Readed", ExRequest::Readed },
                { "Execute", ExRequest::Execute }, { "Exit", ExRequest::Exit },
                { "Error", ExRequest::Error },
            };

            for( const Entry & e : table )
            {
                if( v.stringEquals( keyIdx, e.text ) )
                {
                    out = e.req;
                    return true;
                }
            }

            return false;
        }

        /** Parses a payload that is a bare JSON string into an unescaped span.
         *
         * @param v The JSON view containing the payload.
         * @param payloadIdx Index of the payload value.
         * @param arena Arena used for unescaping.
         * @param out Receives the parsed span.
         * @return true if the payload was a string and was parsed.
         */
        bool parseBareString( const JsonView & v, int payloadIdx, naxe::Arena & arena, std::string_view & out )
        {
            std::string_view s;

            if( ! v.getString( payloadIdx, s ) )
            {
                return false;
            }

            out = naxe::jsonUnescape( arena, s );
            return true;
        }

        /** Parses an object payload carrying "name", optional "value", and "when"
         * fields into an inbound message.
         *
         * @param v The JSON view containing the payload.
         * @param payloadIdx Index of the payload object.
         * @param arena Arena used for unescaping and value conversion.
         * @param out Receives the parsed fields.
         * @return true if the payload was a well-formed object with a name.
         */
        bool parseNameValueWhen( const JsonView & v, int payloadIdx, naxe::Arena & arena, ExInMessage & out )
        {
            if( v.type( payloadIdx ) != Type::Object )
            {
                return false;
            }

            const int nameIdx = v.findField( payloadIdx, "name" );

            if( nameIdx < 0 )
            {
                return false;
            }

            std::string_view s;

            if( ! v.getString( nameIdx, s ) )
            {
                return false;
            }

            out.name = naxe::jsonUnescape( arena, s );

            const int valueIdx = v.findField( payloadIdx, "value" );
            out.hasValue = ( valueIdx >= 0 ) && ! v.isNull( valueIdx );

            if( out.hasValue )
            {
                out.value = cil::jsonToUneValue( v, valueIdx, arena );
            }

            const int whenIdx = v.findField( payloadIdx, "when" );
            int64_t when = 0;

            if( whenIdx < 0 || ! v.getInt64( whenIdx, when ) )
            {
                when = 0;
            }

            out.whenMs = when;

            return true;
        }

        /** Builds one cil::Command by re-parsing a copy of the subtree at `idx`.
         *
         * Re-parses a COPY of the subtree at `idx` (see exen_comm.hpp's top comment for
         * why the copy is necessary) and builds one cil::Command from it.
         *
         * @param srcView The JSON view containing the subtree.
         * @param idx Index of the subtree root.
         * @param arena Arena the copy and command are allocated from.
         * @param out Receives the built command.
         * @return true if the command was built.
         */
        bool buildOneCommandFromCopy( const JsonView & srcView, int idx, naxe::Arena & arena, cil::Command & out )
        {
            std::string_view raw;

            if( ! srcView.getRawSpan( idx, raw ) )
            {
                return false;
            }

            std::string_view copy;

            if( ! copyRawIntoArena( arena, raw, copy ) )
            {
                return false;
            }

            // A single Add/Remove command's own JSON is comparable in size/shape to one
            // element of a transpiled model's commands[] array -- 64 tokens generously
            // covers real Device/Rule/Script shapes without being arena-sized (this
            // buffer is stack-local, freed when this function returns, never crosses
            // into `arena`).
            jsmntok_t localTokens[ 64 ];
            JsonView copyView( copy.data(), copy.size(), localTokens, 64 );

            if( ! copyView.ok() )
            {
                return false;
            }

            return cil::buildCommand( copyView, copyView.root(), arena, out );
        }

        /** Parses an Add/Remove payload, which is either a single command or an array
         * of commands, into the message's command list.
         *
         * @param v The JSON view containing the payload.
         * @param payloadIdx Index of the payload value.
         * @param arena Arena used for command building.
         * @param out Receives the parsed commands.
         * @return true if at least one command was parsed.
         */
        bool parseAddRemove( const JsonView & v, int payloadIdx, naxe::Arena & arena, ExInMessage & out )
        {
            out.commandCount = 0;

            if( v.type( payloadIdx ) == Type::Array )
            {
                const int n = v.arrayCount( payloadIdx );

                for( int i = 0; i < n && out.commandCount < ExInMessage::kMaxBatchCommands; ++i )
                {
                    const int elemIdx = v.arrayElement( payloadIdx, i );
                    cil::Command cmd;

                    if( buildOneCommandFromCopy( v, elemIdx, arena, cmd ) )
                    {
                        out.commands[ out.commandCount++ ] = cmd;
                    }
                }
            }
            else
            {
                cil::Command cmd;

                if( buildOneCommandFromCopy( v, payloadIdx, arena, cmd ) )
                {
                    out.commands[ out.commandCount++ ] = cmd;
                }
            }

            return out.commandCount > 0;
        }
    } // namespace

    bool parseExenComm( std::string_view line, jsmntok_t * tokens, unsigned maxTokens,
                         naxe::Arena & arena, ExInMessage & out )
    {
        out = ExInMessage{};

        JsonView v( line.data(), line.size(), tokens, maxTokens );

        if( ! v.ok() )
        {
            return false;
        }

        const int root = v.root();

        if( v.type( root ) != Type::Object || v.objectFieldCount( root ) != 1 )
        {
            return false;
        }

        const int keyIdx = v.objectKeyAt( root, 0 );
        const int payloadIdx = v.objectValueAt( root, 0 );

        ExRequest req;

        if( ! requestFromKey( v, keyIdx, req ) )
        {
            return false;
        }

        bool ok;

        switch( req )
        {
            case ExRequest::List:
            case ExRequest::Exit:
                ok = true;
                break;

            case ExRequest::Read:
            case ExRequest::Execute:
            case ExRequest::Error:
                ok = parseBareString( v, payloadIdx, arena, out.name );
                break;

            case ExRequest::Change:
            case ExRequest::Changed:
            case ExRequest::Readed:
                ok = parseNameValueWhen( v, payloadIdx, arena, out );
                break;

            case ExRequest::Add:
            case ExRequest::Remove:
                ok = parseAddRemove( v, payloadIdx, arena, out );
                break;

            default:
                // Listed/Added/Removed/Unknown never arrive INBOUND in this port's role
                // -- see exen_comm.hpp's header note on which verbs flow which direction.
                ok = false;
                break;
        }

        out.request = ok ? req : ExRequest::Unknown;
        return ok;
    }

    // ---- Outbound builders -------------------------------------------------------

    namespace
    {
        /** Bounded buffer writer used to serialize outbound ExEn JSON lines.
         *
         * Appends raw bytes and JSON-escaped fragments into a caller-supplied buffer,
         * clearing `ok` when the buffer would overflow.
         */
        struct Writer
        {
            char * buf;
            size_t cap;
            size_t pos;
            bool ok;

            /** Appends raw bytes to the buffer. */
            void raw( std::string_view s )
            {
                if( ! ok )
                {
                    return;
                }

                if( pos + s.size() > cap )
                {
                    ok = false;
                    return;
                }

                std::memcpy( buf + pos, s.data(), s.size() );
                pos += s.size();
            }

            /** Appends a JSON-escaped quoted string to the buffer. */
            void jsonString( std::string_view s )
            {
                raw( "\"" );

                for( size_t i = 0; i < s.size() && ok; ++i )
                {
                    const char c = s[ i ];

                    switch( c )
                    {
                        case '"':  raw( "\\\"" ); break;
                        case '\\': raw( "\\\\" ); break;
                        case '\n': raw( "\\n" ); break;
                        case '\r': raw( "\\r" ); break;
                        case '\t': raw( "\\t" ); break;
                        default:
                            if( static_cast<unsigned char>( c ) < 0x20 )
                            {
                                char esc[ 7 ];
                                std::snprintf( esc, sizeof( esc ), "\\u%04x", static_cast<unsigned char>( c ) );
                                raw( std::string_view( esc, 6 ) );
                            }
                            else
                            {
                                raw( std::string_view( &c, 1 ) );
                            }
                    }
                }

                raw( "\"" );
            }

            /** Appends a signed 64-bit integer to the buffer. */
            void int64( int64_t n )
            {
                char tmp[ 24 ];
                const int len = std::snprintf( tmp, sizeof( tmp ), "%lld", static_cast<long long>( n ) );

                if( len <= 0 )
                {
                    ok = false;
                    return;
                }

                raw( std::string_view( tmp, static_cast<size_t>( len ) ) );
            }

            /** Appends a JSON representation of a value to the buffer.
             *
             * Returns false (and ok is left cleared) for value types this module doesn't
             * support serializing outbound -- see exen_comm.hpp's header note.
             *
             * @param v The value to append.
             * @return true if the value was appended successfully.
             */
            bool value( const naxe::Value & v )
            {
                if( v.isInt() )
                {
                    int64( v.intValue() );
                    return ok;
                }

                if( v.isFloat() )
                {
                    char tmp[ 32 ];
                    const int len = std::snprintf( tmp, sizeof( tmp ), "%.6g", static_cast<double>( v.floatValue() ) );

                    if( len <= 0 )
                    {
                        ok = false;
                        return false;
                    }

                    raw( std::string_view( tmp, static_cast<size_t>( len ) ) );
                    return ok;
                }

                if( v.isBool() )
                {
                    raw( v.boolValue() ? "true" : "false" );
                    return ok;
                }

                if( v.isString() )
                {
                    jsonString( v.stringValue() );
                    return ok;
                }

                ok = false;
                return false;
            }
        };

        /** Maps a command type to its JSON type-name string. */
        const char * commandTypeName( cil::CommandType type )
        {
            switch( type )
            {
                case cil::CommandType::Device: return "device";
                case cil::CommandType::Driver: return "driver";
                case cil::CommandType::Rule:   return "rule";
                case cil::CommandType::Script: return "script";
                default:                       return "unknown";
            }
        }

        /** Serializes a name/value/when outbound line for the given verb. */
        size_t buildNameValueWhen( char * buf, size_t cap, const char * verb,
                                    std::string_view name, const naxe::Value & value, int64_t whenMs )
        {
            Writer w{ buf, cap, 0, true };

            w.raw( "{\"" );
            w.raw( verb );
            w.raw( "\":{\"name\":" );
            w.jsonString( name );
            w.raw( ",\"value\":" );

            if( ! w.value( value ) )
            {
                return 0;
            }

            w.raw( ",\"when\":" );
            w.int64( whenMs );
            w.raw( "}}" );

            return w.ok ? w.pos : 0;
        }

        /** Serializes an acknowledgement-reply outbound line for the given verb. */
        size_t buildAckReply( char * buf, size_t cap, const char * verb, cil::CommandType type,
                               bool hasName, std::string_view name )
        {
            Writer w{ buf, cap, 0, true };

            w.raw( "{\"" );
            w.raw( verb );
            w.raw( "\":{\"name\":" );

            if( hasName )
            {
                w.jsonString( name );
            }
            else
            {
                w.raw( "null" );
            }

            w.raw( ",\"type\":\"" );
            w.raw( commandTypeName( type ) );
            w.raw( "\"}}" );

            return w.ok ? w.pos : 0;
        }
    } // namespace

    size_t buildChanged( char * buf, size_t cap, std::string_view name,
                          const naxe::Value & value, int64_t whenMs )
    {
        return buildNameValueWhen( buf, cap, "Changed", name, value, whenMs );
    }

    size_t buildReaded( char * buf, size_t cap, std::string_view name,
                         const naxe::Value & value, int64_t whenMs )
    {
        return buildNameValueWhen( buf, cap, "Readed", name, value, whenMs );
    }

    size_t buildAdded( char * buf, size_t cap, cil::CommandType type, bool hasName, std::string_view name )
    {
        return buildAckReply( buf, cap, "Added", type, hasName, name );
    }

    size_t buildRemoved( char * buf, size_t cap, cil::CommandType type, bool hasName, std::string_view name )
    {
        return buildAckReply( buf, cap, "Removed", type, hasName, name );
    }

    size_t buildError( char * buf, size_t cap, std::string_view message )
    {
        Writer w{ buf, cap, 0, true };

        w.raw( "{\"Error\":" );
        w.jsonString( message );
        w.raw( "}" );

        return w.ok ? w.pos : 0;
    }
} // namespace stick
