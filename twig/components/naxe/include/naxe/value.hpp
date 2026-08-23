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
/** \file value.hpp
 *
 * The runtime value model: a trivially-copyable tagged union of the seven NAXE types.
 *
 * Types (from the plan's value-model decisions):
 *
 *  - **Int**      — `int64_t`. The integer type introduced to avoid the precision loss
 *                   of the earlier float-only model (a `float32` cannot represent an
 *                   integer exactly past its 24-bit mantissa, ~16.7M). Integer values
 *                   are stored and compared exactly; see the plan's "Integer model".
 *  - **Float**    — `float` (32-bit), matching NAXE's embedded-oriented choice.
 *  - **Bool**     — `bool`.
 *  - **String**   — a non-owning `std::string_view` (length-prefixed bytes, not
 *                   NUL-terminated, no Unicode case-folding).
 *  - **DateTime** — `int64_t` UTC epoch seconds, kept separate from `Int` because it
 *                   carries date/time semantics and formatting (see datetime.hpp in a
 *                   later phase).
 *  - **List**     — pointer to a growable `naxe::List` (list.hpp), heap-backed.
 *  - **Pair**     — pointer to a growable `naxe::Pair` (pair.hpp), heap-backed.
 *
 * Why a hand-rolled tagged union rather than `etl::variant`/`std::variant`: `Value` must
 * be **trivially copyable** — it is embedded by value in `Message`, which a FreeRTOS
 * `xQueue` copies with `memcpy`, and it is arena-allocated without running constructors.
 * A hand-rolled union keeps that guarantee explicit and cheap; the `static_assert` below
 * enforces it at compile time.
 *
 * Construction is via static factories (`Value::makeInt`, ...) rather than a public
 * constructor, so the resulting type is unambiguous at the call site.
 */
#pragma once

#include <cstdint>
#include <string_view>
#include <type_traits>

#include "naxe/arena.hpp"

namespace naxe
{
    class List;
    class Pair;

    /** The seven runtime Value variants. */
    enum class ValueType : uint8_t
    {
        Int,
        Float,
        Bool,
        String,
        DateTime,
        List,
        Pair
    };

    /** A trivially-copyable tagged union of the seven NAXE value types. */
    class Value
    {
    public:
        /** Default-constructs a safe, meaningless `Bool(false)`.
         *
         * Required so `Value` can be a default-constructible element of arrays (and a
         * memcpy-safe queue payload) before a real value is assigned into it.
         */
        Value() : type_( ValueType::Bool ), b_( false ) {}

        // ---- Factories --------------------------------------------------------

        /** @return An Int Value wrapping `v`. */
        static Value makeInt( int64_t v );
        /** @return A Float Value wrapping `v`. */
        static Value makeFloat( float v );
        /** @return A Bool Value wrapping `v`. */
        static Value makeBool( bool v );
        /** @return A String Value viewing `s` (non-owning). */
        static Value makeString( std::string_view s );
        /** @return A DateTime Value wrapping `epochSeconds`. */
        static Value makeDateTime( int64_t epochSeconds );
        /** @return A List Value referencing `list` (caller owns the List). */
        static Value makeList( List * list );
        /** @return A Pair Value referencing `pair` (caller owns the Pair). */
        static Value makePair( Pair * pair );

        // ---- Accessors --------------------------------------------------------

        /** @return The value's type tag. */
        ValueType type() const { return type_; }

        /** @return True if the value is an Int. */
        bool isInt() const { return type_ == ValueType::Int; }
        /** @return True if the value is a Float. */
        bool isFloat() const { return type_ == ValueType::Float; }
        /** @return True if the value is a Bool. */
        bool isBool() const { return type_ == ValueType::Bool; }
        /** @return True if the value is a String. */
        bool isString() const { return type_ == ValueType::String; }
        /** @return True if the value is a DateTime. */
        bool isDateTime() const { return type_ == ValueType::DateTime; }
        /** @return True if the value is a List. */
        bool isList() const { return type_ == ValueType::List; }
        /** @return True if the value is a Pair. */
        bool isPair() const { return type_ == ValueType::Pair; }

        /** @return The Int payload (asserts isInt()). */
        int64_t intValue() const;
        /** @return The Float payload (asserts isFloat()). */
        float floatValue() const;
        /** @return The Bool payload (asserts isBool()). */
        bool boolValue() const;
        /** @return The String view (asserts isString()). */
        std::string_view stringValue() const;
        /** @return The DateTime epoch seconds (asserts isDateTime()). */
        int64_t dateTimeValue() const;
        /** @return The List pointer (asserts isList()). */
        List * listValue() const;
        /** @return The Pair pointer (asserts isPair()). */
        Pair * pairValue() const;

    private:
        ValueType type_;

        union
        {
            int64_t i_;
            float f_;
            bool b_;
            std::string_view s_;
            List * list_;
            Pair * pair_;
        };
    };

    // Value must remain memcpy-safe: it crosses an xQueue by value and is arena-copied.
    static_assert( std::is_trivially_copyable_v<Value>,
                   "Value must be trivially copyable (xQueue memcpy + arena)" );

    // ---- Comparison ----------------------------------------------------------
    //
    // Scalar types compare by value; Int and Float interoperate by promoting the Int to
    // Float (with the documented float32 precision caveat); List/Pair compare by pointer
    // identity only (deep structural equality depends on Une equality semantics for
    // those types, deferred to the evaluator phase).

    /** Compares two Values for equality.
     *
     * Same-type scalars compare exactly (Int uses int64 exact comparison, String is
     * ASCII case-insensitive, matching Une's `==`). An Int vs a Float promotes the Int
     * to float32 first. List/Pair compare by pointer identity.
     *
     * @param a First value.
     * @param b Second value.
     * @return True if equal.
     */
    bool valueEquals( const Value & a, const Value & b );

    /** Coerces any Value to a boolean (Une truthiness).
     *
     * Bool returns itself; Int/Float are truthy iff non-zero; String/List/Pair are
     * truthy iff non-empty; DateTime is always truthy.
     *
     * @param v The value to coerce.
     * @return The value's truthiness.
     */
    bool isTruthy( const Value & v );

    /** Copies `s` into the arena and returns a String Value viewing the copy.
     *
     * The evaluator's operators/functions produce new strings (concatenation, string
     * coercion, formatting); those bytes must outlive the producing expression, so they
     * are copied into the caller's arena. Contrast with `Value::makeString`, which is a
     * non-owning view into already-stable storage.
     *
     * On arena exhaustion this asserts (a full arena is a sizing bug, not a runtime
     * condition to route around — see arena.hpp).
     *
     * @param arena Arena the copy is allocated from.
     * @param s The bytes to copy.
     * @return A String Value viewing the arena copy.
     */
    Value makeArenaString( Arena & arena, std::string_view s );
} // namespace naxe
