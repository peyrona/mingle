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
/** \file json_view.hpp
 *
 * Read-only JSON view over jsmn (vendored in third_party/jsmn/).
 *
 * The device only ever *reads* JSON (the transpiled model file, inbound control-socket
 * messages), never writes it, so this wraps jsmn's flat token stream with typed field/
 * array lookup instead of building a DOM. jsmn does zero allocation; the caller supplies
 * the token array (stack- or arena-allocated).
 *
 * String values are returned as raw spans into the source buffer, NOT unescaped — see
 * naxe/parser.hpp's jsonUnescape() for the one place that needs real escape handling.
 */
#pragma once

#include <cstddef>
#include <cstdint>
#include <string_view>

// Declarations only — see src/jsmn_impl.cpp for the one true implementation TU, per
// jsmn's own documented multi-file pattern ("JSMN_HEADER").
#define JSMN_HEADER
#include "jsmn.h"
#undef JSMN_HEADER

namespace jsonview
{
    /** The JSON value kinds a JsonView token can resolve to. */
    enum class Type
    {
        Object,
        Array,
        String,
        Primitive,
        Undefined
    };

    /** Read-only JSON view over a jsmn flat token stream. */
    class JsonView
    {
    public:
        /** Constructs a view over an already-resident buffer, parsing it eagerly.
         *
         * `buf`/`len` must outlive this JsonView — no bytes are copied. `tokens` must
         * have capacity `maxTokens`; parse() fails (ok() == false) if that's not enough.
         */
        JsonView( const char * buf, size_t len, jsmntok_t * tokens, unsigned maxTokens );

        /** @return true if at least one token was parsed. */
        bool ok() const { return parsedCount_ > 0; }

        /** @return The token index of the JSON document's root value (always 0). */
        int root() const { return 0; }

        /** Resolves the JSON type of the token at `idx`. */
        Type type( int idx ) const;

        /** Returns the value token index for `key` within an Object token, or -1. */
        int findField( int idx, const char * key ) const;

        /** @return The element count of an Array token (0 if not an array). */
        int arrayCount( int idx ) const;

        /** @return The token index of an Array token's i-th element. */
        int arrayElement( int idx, int i ) const;

        /** @return The field count of an Object token. */
        int objectFieldCount( int idx ) const;

        /** @return The token index of an Object token's i-th field key. */
        int objectKeyAt( int idx, int i ) const;

        /** @return The token index of an Object token's i-th field value. */
        int objectValueAt( int idx, int i ) const;

        /** Reads a String token's content as a raw, unescaped span.
         *
         * @return true if `idx` is a String token.
         */
        bool getString( int idx, std::string_view & out ) const;

        /** Parses a Primitive token as a float.
         *
         * @return true if `idx` is a numeric Primitive.
         */
        bool getFloat( int idx, float & out ) const;

        /** Parses a Primitive token as a full-precision int64 (rejects trailing garbage,
         *  unlike getFloat()'s lenient strtof — a timestamp/count with junk is malformed).
         *
         * @return true if `idx` is an integral Primitive.
         */
        bool getInt64( int idx, int64_t & out ) const;

        /** Reads a Primitive token as a boolean.
         *
         * @return true if `idx` is the literal `true` or `false`.
         */
        bool getBool( int idx, bool & out ) const;

        /** @return true if the token at `idx` is the JSON null literal. */
        bool isNull( int idx ) const;

        /** Returns the raw source bytes spanned by token `idx`.
         *
         * A String token's span is its content WITHOUT the surrounding quotes; an
         * Object/Array token's span covers its whole subtree inclusive.
         */
        bool getRawSpan( int idx, std::string_view & out ) const;

        /** @return true if token `idx` is a String/Primitive whose content equals `s`. */
        bool stringEquals( int idx, const char * s ) const;

    private:
        /** Returns the index of the first token AFTER the subtree rooted at `idx`. */
        int skipSubtree( int idx ) const;

        const char * buf_;
        size_t len_;
        jsmntok_t * tokens_;
        int parsedCount_;
    };
} // namespace jsonview
