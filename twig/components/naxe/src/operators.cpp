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
/** \file operators.cpp
 * Operator resolution + evaluation (see operators.hpp), including exact Int semantics.
 */
#include "naxe/operators.hpp"

#include <cassert>
#include <cmath>
#include <cstdio>
#include <cstdlib>
#include <cstring>

#include "naxe/util.hpp"

namespace naxe
{
    namespace
    {
        /** Lowercases one ASCII byte. */
        char asciiLower( char c )
        {
            return ( c >= 'A' && c <= 'Z' ) ? static_cast<char>( c - 'A' + 'a' ) : c;
        }

        /** ASCII case-insensitive lexicographic compare (Une string comparisons are
         * case-insensitive, unlike the case-SENSITIVE `:equals()` method).
         *
         * @return <0 if a<b, 0 if equal, >0 if a>b (ignoring case).
         */
        int compareStringsIgnoreCase( std::string_view a, std::string_view b )
        {
            const size_t minLen = ( a.size() < b.size() ) ? a.size() : b.size();

            for( size_t i = 0; i < minLen; ++i )
            {
                const char ca = asciiLower( a[ i ] );
                const char cb = asciiLower( b[ i ] );
                if( ca != cb )
                {
                    return ( ca < cb ) ? -1 : 1;
                }
            }

            if( a.size() != b.size() )
            {
                return ( a.size() < b.size() ) ? -1 : 1;
            }

            return 0;
        }

        /** Concatenates two spans into a fresh arena String. */
        Value concatStrings( std::string_view a, std::string_view b, Arena & arena )
        {
            char * buf = static_cast<char *>( arena.alloc( a.size() + b.size() ) );
            assert( buf != nullptr && "arena exhausted concatenating strings" );
            if( a.size() > 0 ) std::memcpy( buf, a.data(), a.size() );
            if( b.size() > 0 ) std::memcpy( buf + a.size(), b.data(), b.size() );
            return Value::makeString( std::string_view( buf, a.size() + b.size() ) );
        }

        /** Stringifies any scalar for the "+" string-concatenation fallback.
         *
         * List/Pair have no defined textual form here (unexercised in real rules) and
         * fall back to the empty string.
         */
        Value stringify( const Value & v, Arena & arena )
        {
            switch( v.type() )
            {
                case ValueType::String: return v;

                case ValueType::Int:
                {
                    char buf[ 24 ];
                    const int n = std::snprintf( buf, sizeof( buf ), "%lld",
                                                 static_cast<long long>( v.intValue() ) );
                    return makeArenaString( arena, std::string_view( buf, static_cast<size_t>( n > 0 ? n : 0 ) ) );
                }

                case ValueType::Float:
                {
                    char buf[ 32 ];
                    const int n = std::snprintf( buf, sizeof( buf ), "%g",
                                                 static_cast<double>( v.floatValue() ) );
                    return makeArenaString( arena, std::string_view( buf, static_cast<size_t>( n > 0 ? n : 0 ) ) );
                }

                case ValueType::Bool:
                    return makeArenaString( arena, v.boolValue() ? "true" : "false" );

                case ValueType::DateTime:
                {
                    char buf[ 24 ];
                    const int n = std::snprintf( buf, sizeof( buf ), "%lld",
                                                 static_cast<long long>( v.dateTimeValue() ) );
                    return makeArenaString( arena, std::string_view( buf, static_cast<size_t>( n > 0 ? n : 0 ) ) );
                }

                default:
                    return Value::makeString( std::string_view() );
            }
        }

        /** Java's `==`/`!=` coercion cascade: same-type String -> case-insensitive;
         * same-type otherwise -> valueEquals(); different numeric types -> promote and
         * compare. Non-numeric cross-type pairs are not equal (a safe default). */
        bool looseEquals( const Value & l, const Value & r )
        {
            if( l.type() == r.type() )
            {
                if( l.isString() )
                {
                    return util::asciiEqualsIgnoreCase( l.stringValue(), r.stringValue() );
                }
                return valueEquals( l, r );
            }

            // Cross-type: try integer-exact first, then float promotion, else not equal.
            int64_t li, ri;
            if( tryToInt( l, li ) && tryToInt( r, ri ) )
            {
                return li == ri;
            }

            float lf, rf;
            if( tryToFloat( l, lf ) && tryToFloat( r, rf ) )
            {
                return lf == rf;
            }

            return false;
        }
    } // namespace

    bool tryToFloat( const Value & v, float & out )
    {
        if( v.isFloat() )
        {
            out = v.floatValue();
            return true;
        }

        if( v.isInt() )
        {
            out = static_cast<float>( v.intValue() );
            return true;
        }

        if( v.isString() )
        {
            const std::string_view s = v.stringValue();
            if( s.empty() || s.size() >= 64 )
            {
                return false;
            }

            char tmp[ 64 ];
            std::memcpy( tmp, s.data(), s.size() );
            tmp[ s.size() ] = '\0';

            char * endPtr = nullptr;
            const float f = std::strtof( tmp, &endPtr );

            if( endPtr != tmp + s.size() )
            {
                return false; // trailing garbage — not a clean number
            }

            out = f;
            return true;
        }

        return false;
    }

    bool tryToInt( const Value & v, int64_t & out )
    {
        if( v.isInt() )
        {
            out = v.intValue();
            return true;
        }

        if( v.isString() )
        {
            const std::string_view s = v.stringValue();
            if( s.empty() || s.size() >= 64 )
            {
                return false;
            }

            char tmp[ 64 ];
            std::memcpy( tmp, s.data(), s.size() );
            tmp[ s.size() ] = '\0';

            char * endPtr = nullptr;
            const long long ll = std::strtoll( tmp, &endPtr, 10 );

            if( endPtr != tmp + s.size() )
            {
                return false;
            }

            out = static_cast<int64_t>( ll );
            return true;
        }

        return false;
    }

    bool resolveOperator( const char * token, size_t len, AstOp & out )
    {
        #define TOK_IS( s ) ( len == ( sizeof( s ) - 1 ) && std::memcmp( token, s, sizeof( s ) - 1 ) == 0 )

        // Arithmetic
        if( TOK_IS( "+" ) ) { out = AstOp::Add; return true; }
        if( TOK_IS( "-" ) ) { out = AstOp::Sub; return true; }
        if( TOK_IS( "*" ) ) { out = AstOp::Mul; return true; }
        if( TOK_IS( "/" ) ) { out = AstOp::Div; return true; }
        if( TOK_IS( "%" ) ) { out = AstOp::Mod; return true; }
        if( TOK_IS( "^" ) ) { out = AstOp::Pow; return true; }

        // Comparison (symbols only — see operators.hpp)
        if( TOK_IS( "==" ) ) { out = AstOp::Eq; return true; }
        if( TOK_IS( "!=" ) ) { out = AstOp::Ne; return true; }
        if( TOK_IS( ">" ) )  { out = AstOp::Gt; return true; }
        if( TOK_IS( "<" ) )  { out = AstOp::Lt; return true; }
        if( TOK_IS( ">=" ) ) { out = AstOp::Ge; return true; }
        if( TOK_IS( "<=" ) ) { out = AstOp::Le; return true; }

        // Logical
        if( TOK_IS( "&&" ) ) { out = AstOp::And; return true; }
        if( TOK_IS( "||" ) ) { out = AstOp::Or; return true; }
        if( TOK_IS( "|&" ) ) { out = AstOp::Xor; return true; }
        if( TOK_IS( "!" ) )  { out = AstOp::Not; return true; }

        // Edge-detection (recognized, not evaluated here — see ast.hpp)
        if( TOK_IS( "?>" ) )  { out = AstOp::Rises; return true; }
        if( TOK_IS( "?<" ) )  { out = AstOp::Drops; return true; }
        if( TOK_IS( "?=" ) )  { out = AstOp::Becomes; return true; }
        if( TOK_IS( "?!=" ) ) { out = AstOp::Leaves; return true; }
        if( TOK_IS( "?<>" ) ) { out = AstOp::Crosses; return true; }

        #undef TOK_IS

        // Bitwise operators (&,|,~,><,shifts) deliberately absent — see operators.hpp.
        return false;
    }

    Value evalBinaryOp( AstOp op, const Value & l, const Value & r, Arena & arena )
    {
        switch( op )
        {
            case AstOp::Add:
            {
                if( l.isString() && r.isString() )
                {
                    return concatStrings( l.stringValue(), r.stringValue(), arena );
                }

                if( l.isInt() && r.isInt() )
                {
                    return Value::makeInt( l.intValue() + r.intValue() );
                }

                float lf, rf;
                if( tryToFloat( l, lf ) && tryToFloat( r, rf ) )
                {
                    return Value::makeFloat( lf + rf );
                }

                // Java's final fallback: stringify and concatenate.
                return concatStrings( stringify( l, arena ).stringValue(),
                                      stringify( r, arena ).stringValue(), arena );
            }

            case AstOp::Sub:
            {
                if( l.isString() && r.isString() )
                {
                    // Removes every occurrence of r from l (Java's String.replace).
                    const std::string_view ls = l.stringValue();
                    const std::string_view rs = r.stringValue();
                    if( rs.empty() || rs.size() > ls.size() )
                    {
                        return l;
                    }

                    char * buf = static_cast<char *>( arena.alloc( ls.size() ) );
                    assert( buf != nullptr && "arena exhausted subtracting strings" );
                    size_t outLen = 0;

                    for( size_t i = 0; i < ls.size(); )
                    {
                        const bool matches = ( i + rs.size() <= ls.size() )
                                             && std::memcmp( ls.data() + i, rs.data(), rs.size() ) == 0;
                        if( matches )
                        {
                            i += rs.size();
                        }
                        else
                        {
                            buf[ outLen++ ] = ls.data()[ i ];
                            ++i;
                        }
                    }

                    return Value::makeString( std::string_view( buf, outLen ) );
                }

                if( l.isInt() && r.isInt() )
                {
                    return Value::makeInt( l.intValue() - r.intValue() );
                }

                float lf, rf;
                if( tryToFloat( l, lf ) && tryToFloat( r, rf ) )
                {
                    return Value::makeFloat( lf - rf );
                }

                return Value::makeFloat( 0.0f );
            }

            case AstOp::Mul:
            {
                if( l.isInt() && r.isInt() )
                {
                    return Value::makeInt( l.intValue() * r.intValue() );
                }

                float lf, rf;
                if( tryToFloat( l, lf ) && tryToFloat( r, rf ) )
                {
                    return Value::makeFloat( lf * rf );
                }

                return Value::makeFloat( 0.0f );
            }

            case AstOp::Div:
            {
                float lf, rf;
                if( tryToFloat( l, lf ) && tryToFloat( r, rf ) )
                {
                    return Value::makeFloat( lf / rf ); // IEEE-754: /0 -> +/-Infinity
                }

                return Value::makeFloat( 0.0f );
            }

            case AstOp::Mod: // percent-of: (l/100)*r — NOT modulo
            {
                float lf, rf;
                if( tryToFloat( l, lf ) && tryToFloat( r, rf ) )
                {
                    return Value::makeFloat( ( lf / 100.0f ) * rf );
                }

                return Value::makeFloat( 0.0f );
            }

            case AstOp::Pow:
            {
                float lf, rf;
                if( tryToFloat( l, lf ) && tryToFloat( r, rf ) )
                {
                    return Value::makeFloat( static_cast<float>(
                        std::pow( static_cast<double>( lf ), static_cast<double>( rf ) ) ) );
                }

                return Value::makeFloat( 0.0f );
            }

            case AstOp::Eq: return Value::makeBool( looseEquals( l, r ) );
            case AstOp::Ne: return Value::makeBool( ! looseEquals( l, r ) );

            case AstOp::Gt:
            case AstOp::Lt:
            case AstOp::Ge:
            case AstOp::Le:
            {
                bool result = false;

                if( l.isString() && r.isString() )
                {
                    const int cmp = compareStringsIgnoreCase( l.stringValue(), r.stringValue() );
                    switch( op )
                    {
                        case AstOp::Gt: result = cmp > 0; break;
                        case AstOp::Lt: result = cmp < 0; break;
                        case AstOp::Ge: result = cmp >= 0; break;
                        case AstOp::Le: result = cmp <= 0; break;
                        default: break;
                    }
                }
                else if( l.isDateTime() && r.isDateTime() )
                {
                    switch( op )
                    {
                        case AstOp::Gt: result = l.dateTimeValue() > r.dateTimeValue(); break;
                        case AstOp::Lt: result = l.dateTimeValue() < r.dateTimeValue(); break;
                        case AstOp::Ge: result = l.dateTimeValue() >= r.dateTimeValue(); break;
                        case AstOp::Le: result = l.dateTimeValue() <= r.dateTimeValue(); break;
                        default: break;
                    }
                }
                else if( l.isInt() && r.isInt() )
                {
                    // Exact int64 comparison — the plan's integer improvement (the Java
                    // reference promotes to float here, losing precision past 2^24).
                    switch( op )
                    {
                        case AstOp::Gt: result = l.intValue() > r.intValue(); break;
                        case AstOp::Lt: result = l.intValue() < r.intValue(); break;
                        case AstOp::Ge: result = l.intValue() >= r.intValue(); break;
                        case AstOp::Le: result = l.intValue() <= r.intValue(); break;
                        default: break;
                    }
                }
                else
                {
                    float lf, rf;
                    if( tryToFloat( l, lf ) && tryToFloat( r, rf ) )
                    {
                        switch( op )
                        {
                            case AstOp::Gt: result = lf > rf; break;
                            case AstOp::Lt: result = lf < rf; break;
                            case AstOp::Ge: result = lf >= rf; break;
                            case AstOp::Le: result = lf <= rf; break;
                            default: break;
                        }
                    }
                    // Else: no defined cross-type ordering — conservatively false.
                }

                return Value::makeBool( result );
            }

            case AstOp::And: return Value::makeBool( isTruthy( l ) && isTruthy( r ) );
            case AstOp::Or:  return Value::makeBool( isTruthy( l ) || isTruthy( r ) );
            case AstOp::Xor: return Value::makeBool( isTruthy( l ) != isTruthy( r ) );

            default:
                return Value::makeBool( false );
        }
    }

    Value evalUnaryOp( AstOp op, const Value & v )
    {
        switch( op )
        {
            case AstOp::Not: return Value::makeBool( ! isTruthy( v ) );

            case AstOp::Neg:
                if( v.isInt() )
                {
                    return Value::makeInt( -v.intValue() );
                }
                if( v.isFloat() )
                {
                    return Value::makeFloat( -v.floatValue() );
                }
                return Value::makeFloat( 0.0f ); // non-number: conservative default

            default: return v;
        }
    }
} // namespace naxe
