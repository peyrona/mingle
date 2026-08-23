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
/** \file functions.cpp
 *
 * The top-level function and chained-method dispatch tables, populated with the audited
 * `StdXprFns` set plus the date/time/list/pair extension methods (full parity with the
 * reference implementation, per the plan's scope). Adapted to this port's value model:
 *
 *  - Integer-valued results (date fields, collection sizes/indices, `Int()`, `abs` on an
 *    Int) return `Int` (int64), not the old float32 — matching "integers are stored and
 *    treated as integers".
 *  - Numeric arguments accept Int or Float (coerced via `tryToFloat`/`toInt`).
 *  - Collection results are heap-backed `List`/`Pair`, registered with the caller's arena
 *    via `Arena::defer()` so a cycle-scoped arena reclaims them on `reset()`.
 *
 * Deliberately absent (fail-loud `#UNSUPPORTED_*#` via resolveFunction/resolveMethod
 * returning nullptr): regex-dependent functions (`search`/`substitute`/`split`),
 * nested-Une-parser functions (`map`/`filter`/`reduce`), JSON writing (`toJson` — this
 * project is JSON read-only by design), astronomical functions (`sunRise`/`sunSet`/...),
 * and infrastructure functions (`prev`/`getTriggeredBy`/`exit`/`isReachable`/
 * `enable`/`disable`/`localIPs`) that need later-phase runtime/PAL support.
 *
 * Tier 1 (hardware-agnostic): date/time uses standard C `time.h`, identical on host and
 * target.
 */
#include "naxe/functions.hpp"

#include <cassert>
#include <cmath>
#include <cstdio>
#include <cstdlib>
#include <cstring>
#include <ctime>

#include "naxe/datetime.hpp"
#include "naxe/list.hpp"
#include "naxe/operators.hpp"
#include "naxe/pair.hpp"
#include "naxe/util.hpp"

namespace naxe
{
    namespace
    {
        /** Case-insensitive function/method name match (Une names are case-insensitive). */
        bool nameIs( std::string_view name, const char * s )
        {
            return util::asciiEqualsIgnoreCase( name, s );
        }

        /** Coerces an Int/Float argument to int (for index/count arguments). */
        bool toIntArg( const Value & v, int & out )
        {
            if( v.isInt() )
            {
                out = static_cast<int>( v.intValue() );
                return true;
            }
            if( v.isFloat() )
            {
                out = static_cast<int>( v.floatValue() );
                return true;
            }
            return false;
        }

        // ---- date()/time() construction -----------------------------------------

        Value epochToDateTime( int64_t epoch )
        {
            return Value::makeDateTime( epoch );
        }

        int64_t startOfToday()
        {
            const LocalFields f = localFieldsFromEpoch( nowEpochSeconds() );
            struct tm local {};
            local.tm_year = f.year - 1900;
            local.tm_mon = f.month - 1;
            local.tm_mday = f.day;
            local.tm_isdst = -1;
            return static_cast<int64_t>( mktime( &local ) );
        }

        int64_t epochFromYMD( int year, int month, int day )
        {
            struct tm local {};
            local.tm_year = year - 1900;
            local.tm_mon = month - 1;
            local.tm_mday = day;
            local.tm_isdst = -1;
            return static_cast<int64_t>( mktime( &local ) );
        }

        int64_t epochFromHMS( int hour, int minute, int second )
        {
            const LocalFields f = localFieldsFromEpoch( nowEpochSeconds() );
            struct tm local {};
            local.tm_year = f.year - 1900;
            local.tm_mon = f.month - 1;
            local.tm_mday = f.day;
            local.tm_hour = hour;
            local.tm_min = minute;
            local.tm_sec = second;
            local.tm_isdst = -1;
            return static_cast<int64_t>( mktime( &local ) );
        }

        // ---- Top-level functions --------------------------------------------------

        /** date(): 0 args -> today (midnight); 1 String "YYYY-MM-DD" -> that date;
         *  3 numeric args -> (year,month,day). Malformed calls fall back to today. */
        Value fnDate( const Value * args, uint32_t argCount, Arena & )
        {
            if( argCount == 0 )
            {
                return epochToDateTime( startOfToday() );
            }

            if( argCount == 1 && args[ 0 ].isString() )
            {
                int64_t epoch;
                if( parseYMD( args[ 0 ].stringValue(), epoch ) )
                {
                    return epochToDateTime( epoch );
                }
                return epochToDateTime( startOfToday() );
            }

            if( argCount == 3 )
            {
                int y, m, d;
                if( toIntArg( args[ 0 ], y ) && toIntArg( args[ 1 ], m ) && toIntArg( args[ 2 ], d ) )
                {
                    return epochToDateTime( epochFromYMD( y, m, d ) );
                }
            }

            return epochToDateTime( startOfToday() );
        }

        /** time(): 0 args -> now; 1 String "HH:MM[:ss]" -> today at that time;
         *  2-3 numeric args -> (hour,minute,[second]). Malformed falls back to now. */
        Value fnTime( const Value * args, uint32_t argCount, Arena & )
        {
            if( argCount == 0 )
            {
                return epochToDateTime( nowEpochSeconds() );
            }

            if( argCount == 1 && args[ 0 ].isString() )
            {
                int64_t epoch;
                if( parseHHMM( args[ 0 ].stringValue(), epoch ) )
                {
                    return epochToDateTime( epoch );
                }
                return epochToDateTime( nowEpochSeconds() );
            }

            if( argCount == 2 || argCount == 3 )
            {
                int h, m, s = 0;
                if( toIntArg( args[ 0 ], h ) && toIntArg( args[ 1 ], m )
                    && ( argCount == 2 || toIntArg( args[ 2 ], s ) ) )
                {
                    return epochToDateTime( epochFromHMS( h, m, s ) );
                }
            }

            return epochToDateTime( nowEpochSeconds() );
        }

        /** list(...): builds a growable List from the arguments (arena-deferred). */
        Value fnList( const Value * args, uint32_t argCount, Arena & arena )
        {
            List * list = List::create( argCount > 0 ? argCount : 1 );
            if( list == nullptr )
            {
                return Value::makeString( std::string_view() );
            }
            if( ! arena.defer( list ) )
            {
                List::destroy( list );
                return Value::makeString( std::string_view() );
            }

            for( uint32_t i = 0; i < argCount; ++i )
            {
                list->add( args[ i ] );
            }
            return Value::makeList( list );
        }

        /** pair(k1,v1,k2,v2,...): builds a Pair from alternating key/value args
         *  (String keys only; non-string keys are skipped). */
        Value fnPair( const Value * args, uint32_t argCount, Arena & arena )
        {
            const uint32_t pairCount = argCount / 2;
            Pair * pair = Pair::create( pairCount > 0 ? pairCount : 1 );
            if( pair == nullptr )
            {
                return Value::makeString( std::string_view() );
            }
            if( ! arena.defer( pair ) )
            {
                Pair::destroy( pair );
                return Value::makeString( std::string_view() );
            }

            for( uint32_t i = 0; i + 1 < argCount; i += 2 )
            {
                if( args[ i ].isString() )
                {
                    pair->set( args[ i ].stringValue(), args[ i + 1 ] );
                }
            }
            return Value::makePair( pair );
        }

        /** floor(x) / floor(x, significance) — float-domain. */
        Value fnFloor( const Value * args, uint32_t argCount, Arena & )
        {
            float n;
            if( argCount == 1 && tryToFloat( args[ 0 ], n ) )
            {
                return Value::makeFloat( std::floor( n ) );
            }
            if( argCount == 2 )
            {
                float sig;
                if( tryToFloat( args[ 0 ], n ) && tryToFloat( args[ 1 ], sig ) )
                {
                    if( sig == 0.0f || ( n > 0.0f && sig < 0.0f ) ) return Value::makeFloat( 0.0f );
                    return Value::makeFloat( std::floor( n / sig ) * sig );
                }
            }
            return Value::makeFloat( 0.0f );
        }

        /** mod(a,b): sign-of-divisor floored modulo (NOT `%`, which is percent-of). */
        Value fnMod( const Value * args, uint32_t argCount, Arena & )
        {
            float n, d;
            if( argCount != 2 || ! tryToFloat( args[ 0 ], n ) || ! tryToFloat( args[ 1 ], d ) )
            {
                return Value::makeFloat( 0.0f );
            }

            float r = std::fmod( n, d );
            if( r != 0.0f && ( ( r < 0.0f ) != ( d < 0.0f ) ) )
            {
                r += d;
            }
            return Value::makeFloat( r );
        }

        /** rand(lower, upper): uniform float, lower inclusive, upper exclusive. */
        Value fnRand( const Value * args, uint32_t argCount, Arena & )
        {
            float lower, upper;
            if( argCount != 2 || ! tryToFloat( args[ 0 ], lower ) || ! tryToFloat( args[ 1 ], upper ) )
            {
                return Value::makeFloat( 0.0f );
            }
            const float unit = static_cast<float>( std::rand() ) / ( static_cast<float>( RAND_MAX ) + 1.0f );
            return Value::makeFloat( unit * ( upper - lower ) + lower );
        }

        /** format(value, pattern) or format(pattern, value) — a scoped DecimalFormat
         *  subset ('#'/'0' decimals, optional ',' thousands). */
        Value fnFormat( const Value * args, uint32_t argCount, Arena & arena )
        {
            if( argCount != 2 )
            {
                return Value::makeString( std::string_view() );
            }

            const Value * numArg = nullptr;
            const Value * patArg = nullptr;

            for( uint32_t i = 0; i < 2; ++i )
            {
                if( args[ i ].isString() ) patArg = &args[ i ];
                else if( args[ i ].isInt() || args[ i ].isFloat() ) numArg = &args[ i ];
            }

            if( numArg == nullptr || patArg == nullptr )
            {
                return Value::makeString( std::string_view() );
            }

            const std::string_view pattern = patArg->stringValue();

            size_t dotPos = pattern.size();
            for( size_t i = 0; i < pattern.size(); ++i )
            {
                if( pattern[ i ] == '.' ) { dotPos = i; break; }
            }

            int decimals = 0;
            if( dotPos < pattern.size() )
            {
                for( size_t i = dotPos + 1; i < pattern.size(); ++i )
                {
                    if( pattern[ i ] == '#' || pattern[ i ] == '0' ) ++decimals;
                }
            }

            bool thousands = false;
            for( size_t i = 0; i < dotPos; ++i )
            {
                if( pattern[ i ] == ',' ) { thousands = true; break; }
            }

            float num;
            if( ! tryToFloat( *numArg, num ) )
            {
                return Value::makeString( std::string_view() );
            }

            char raw[ 64 ];
            const int rawLen = std::snprintf( raw, sizeof( raw ), "%.*f", decimals,
                                              static_cast<double>( num ) );
            if( rawLen <= 0 )
            {
                return Value::makeString( std::string_view() );
            }

            if( ! thousands )
            {
                return makeArenaString( arena, std::string_view( raw, static_cast<size_t>( rawLen ) ) );
            }

            char out[ 96 ];
            size_t outLen = 0;
            const size_t rawULen = static_cast<size_t>( rawLen );
            size_t intEnd = rawULen;
            for( size_t i = 0; i < rawULen; ++i )
            {
                if( raw[ i ] == '.' ) { intEnd = i; break; }
            }

            const size_t signLen = ( raw[ 0 ] == '-' ) ? 1 : 0;
            if( signLen ) out[ outLen++ ] = '-';

            const size_t intDigits = intEnd - signLen;
            for( size_t i = 0; i < intDigits; ++i )
            {
                out[ outLen++ ] = raw[ signLen + i ];
                const size_t remaining = intDigits - i - 1;
                if( remaining > 0 && ( remaining % 3 ) == 0 )
                {
                    out[ outLen++ ] = ',';
                }
            }

            for( size_t i = intEnd; i < rawULen; ++i )
            {
                out[ outLen++ ] = raw[ i ];
            }

            return makeArenaString( arena, std::string_view( out, outLen ) );
        }

        /** iif(cond, onTrue, onFalse): both branches are pre-evaluated by eval() (no
         *  function-level short-circuit, matching Java). */
        Value fnIif( const Value * args, uint32_t argCount, Arena & )
        {
            if( argCount != 3 )
            {
                return Value::makeBool( false );
            }
            const bool cond = args[ 0 ].isBool() && args[ 0 ].boolValue();
            return cond ? args[ 1 ] : args[ 2 ];
        }

        // ---- Global variable map (put/get) --------------------------------------

        /** The module-static global variable Pair (put()/get() store). Heap-backed,
         *  stable for process lifetime. */
        Pair * globalVars()
        {
            static Pair * vars = Pair::create( 32 );
            return vars;
        }

        /** put(name, value): stores a value in the global map.
         *
         * @return True on success.
         */
        Value fnPut( const Value * args, uint32_t argCount, Arena & )
        {
            if( argCount != 2 || ! args[ 0 ].isString() )
            {
                return Value::makeBool( false );
            }
            return Value::makeBool( globalVars()->set( args[ 0 ].stringValue(), args[ 1 ] ) );
        }

        /** get(name[, default]): reads a value from the global map. */
        Value fnGet( const Value * args, uint32_t argCount, Arena & )
        {
            if( argCount < 1 || ! args[ 0 ].isString() )
            {
                return Value::makeString( std::string_view() );
            }

            const Value * found = globalVars()->get( args[ 0 ].stringValue() );
            if( found != nullptr )
            {
                return *found;
            }
            return ( argCount >= 2 ) ? args[ 1 ] : Value::makeString( std::string_view() );
        }

        /** Int(x): explicit integer conversion. Int passes through; Float truncates
         *  toward zero; String parses "0b..."/"0x..."/decimal. */
        Value fnInt( const Value * args, uint32_t argCount, Arena & )
        {
            if( argCount != 1 )
            {
                return Value::makeInt( 0 );
            }

            if( args[ 0 ].isInt() )
            {
                return args[ 0 ];
            }

            if( args[ 0 ].isFloat() )
            {
                return Value::makeInt( static_cast<int64_t>( args[ 0 ].floatValue() ) );
            }

            if( args[ 0 ].isString() )
            {
                const std::string_view s = args[ 0 ].stringValue();
                if( s.size() > 2 && s[ 0 ] == '0' && ( s[ 1 ] == 'b' || s[ 1 ] == 'x' ) )
                {
                    const int base = ( s[ 1 ] == 'b' ) ? 2 : 16;
                    char tmp[ 64 ];
                    if( s.size() - 2 >= sizeof( tmp ) ) return Value::makeInt( 0 );
                    std::memcpy( tmp, s.data() + 2, s.size() - 2 );
                    tmp[ s.size() - 2 ] = '\0';
                    return Value::makeInt( static_cast<int64_t>( std::strtoll( tmp, nullptr, base ) ) );
                }

                int64_t v;
                if( tryToInt( args[ 0 ], v ) )
                {
                    return Value::makeInt( v );
                }
            }

            return Value::makeInt( 0 );
        }

        /** max(...): largest argument (float-domain; 0 args -> Float.MIN_VALUE sentinel). */
        Value fnMax( const Value * args, uint32_t argCount, Arena & )
        {
            if( argCount == 0 ) return Value::makeFloat( 1.4e-45f );
            float m = 0.0f;
            tryToFloat( args[ 0 ], m );
            for( uint32_t i = 1; i < argCount; ++i )
            {
                float v;
                if( tryToFloat( args[ i ], v ) && v > m ) m = v;
            }
            return Value::makeFloat( m );
        }

        /** min(...): smallest argument (float-domain; 0 args -> Float.MAX_VALUE sentinel). */
        Value fnMin( const Value * args, uint32_t argCount, Arena & )
        {
            if( argCount == 0 ) return Value::makeFloat( 3.402823e38f );
            float m = 0.0f;
            tryToFloat( args[ 0 ], m );
            for( uint32_t i = 1; i < argCount; ++i )
            {
                float v;
                if( tryToFloat( args[ i ], v ) && v < m ) m = v;
            }
            return Value::makeFloat( m );
        }

        /** ceiling(n) / ceiling(n, significance) — Excel-style CEILING. */
        Value fnCeiling( const Value * args, uint32_t argCount, Arena & )
        {
            float n;
            if( argCount == 1 && tryToFloat( args[ 0 ], n ) )
            {
                return Value::makeFloat( std::ceil( n ) );
            }
            if( argCount == 2 )
            {
                float sig;
                if( tryToFloat( args[ 0 ], n ) && tryToFloat( args[ 1 ], sig ) )
                {
                    if( sig == 0.0f ) return Value::makeFloat( 0.0f );
                    float r = std::ceil( n / sig ) * sig;
                    if( n < 0.0f ) r -= sig;
                    return Value::makeFloat( r );
                }
            }
            return Value::makeFloat( 0.0f );
        }

        /** round(n) / round(n, decimals): Java's HALF-UP (floor(x+0.5)). */
        Value fnRound( const Value * args, uint32_t argCount, Arena & )
        {
            auto javaRound = []( float x ) { return std::floor( x + 0.5f ); };

            float n;
            if( argCount == 1 && tryToFloat( args[ 0 ], n ) )
            {
                return Value::makeFloat( javaRound( n ) );
            }
            if( argCount == 2 )
            {
                float decimals;
                if( tryToFloat( args[ 0 ], n ) && tryToFloat( args[ 1 ], decimals ) )
                {
                    const float mult = std::pow( 10.0f, decimals );
                    return Value::makeFloat( javaRound( n * mult ) / mult );
                }
            }
            return Value::makeFloat( 0.0f );
        }

        /** abs(x): absolute value, preserving the Int/Float type. */
        Value fnAbs( const Value * args, uint32_t argCount, Arena & )
        {
            if( argCount != 1 )
            {
                return Value::makeFloat( 0.0f );
            }
            if( args[ 0 ].isInt() )
            {
                const int64_t v = args[ 0 ].intValue();
                return Value::makeInt( v < 0 ? -v : v );
            }
            float v;
            if( tryToFloat( args[ 0 ], v ) )
            {
                return Value::makeFloat( std::fabs( v ) );
            }
            return Value::makeFloat( 0.0f );
        }

        /** isBetween(value, min, max): inclusive both ends; min>max -> false. */
        Value fnIsBetween( const Value * args, uint32_t argCount, Arena & )
        {
            float v, mn, mx;
            if( argCount != 3 || ! tryToFloat( args[ 0 ], v ) || ! tryToFloat( args[ 1 ], mn )
                || ! tryToFloat( args[ 2 ], mx ) )
            {
                return Value::makeBool( false );
            }
            if( mn > mx ) return Value::makeBool( false );
            return Value::makeBool( v >= mn && v <= mx );
        }

        /** setBetween(value, min, max): clamps into [min, max]; min>max returns value. */
        Value fnSetBetween( const Value * args, uint32_t argCount, Arena & )
        {
            float v, mn, mx;
            if( argCount != 3 || ! tryToFloat( args[ 0 ], v ) || ! tryToFloat( args[ 1 ], mn )
                || ! tryToFloat( args[ 2 ], mx ) )
            {
                return Value::makeFloat( 0.0f );
            }
            if( mn > mx ) return Value::makeFloat( v );
            if( v > mx ) return Value::makeFloat( mx );
            if( v < mn ) return Value::makeFloat( mn );
            return Value::makeFloat( v );
        }

        // ---- String functions (byte/ASCII-oriented) -----------------------------

        /** trim(s): strips leading/trailing whitespace. */
        Value fnTrim( const Value * args, uint32_t argCount, Arena & arena )
        {
            if( argCount != 1 || ! args[ 0 ].isString() ) return Value::makeString( std::string_view() );
            const std::string_view s = args[ 0 ].stringValue();
            size_t start = 0, end = s.size();
            while( start < end && static_cast<unsigned char>( s[ start ] ) <= ' ' ) ++start;
            while( end > start && static_cast<unsigned char>( s[ end - 1 ] ) <= ' ' ) --end;
            return makeArenaString( arena, s.substr( start, end - start ) );
        }

        /** reverse(s): byte-reversal (not codepoint-aware — documented limitation). */
        Value fnReverse( const Value * args, uint32_t argCount, Arena & arena )
        {
            if( argCount != 1 || ! args[ 0 ].isString() ) return Value::makeString( std::string_view() );
            const std::string_view s = args[ 0 ].stringValue();
            char * buf = static_cast<char *>( arena.alloc( s.size() ) );
            assert( buf != nullptr && "arena exhausted reversing a string" );
            for( size_t i = 0; i < s.size(); ++i ) buf[ i ] = s[ s.size() - 1 - i ];
            return Value::makeString( std::string_view( buf, s.size() ) );
        }

        /** left(s, n): first n characters (n clamps to length). */
        Value fnLeft( const Value * args, uint32_t argCount, Arena & arena )
        {
            int n;
            if( argCount != 2 || ! args[ 0 ].isString() || ! toIntArg( args[ 1 ], n ) )
            {
                return Value::makeString( std::string_view() );
            }
            const std::string_view s = args[ 0 ].stringValue();
            size_t len = ( n < 0 ) ? 0 : static_cast<size_t>( n );
            if( len > s.size() ) len = s.size();
            return makeArenaString( arena, s.substr( 0, len ) );
        }

        /** right(s, n): last n characters (n clamps to length). */
        Value fnRight( const Value * args, uint32_t argCount, Arena & arena )
        {
            int n;
            if( argCount != 2 || ! args[ 0 ].isString() || ! toIntArg( args[ 1 ], n ) )
            {
                return Value::makeString( std::string_view() );
            }
            const std::string_view s = args[ 0 ].stringValue();
            size_t len = ( n < 0 ) ? 0 : static_cast<size_t>( n );
            if( len > s.size() ) len = s.size();
            return makeArenaString( arena, s.substr( s.size() - len, len ) );
        }

        /** lower(s): ASCII lowercase. */
        Value fnLower( const Value * args, uint32_t argCount, Arena & arena )
        {
            if( argCount != 1 || ! args[ 0 ].isString() ) return Value::makeString( std::string_view() );
            const std::string_view s = args[ 0 ].stringValue();
            char * buf = static_cast<char *>( arena.alloc( s.size() ) );
            assert( buf != nullptr && "arena exhausted lowercasing a string" );
            for( size_t i = 0; i < s.size(); ++i )
            {
                const char c = s[ i ];
                buf[ i ] = ( c >= 'A' && c <= 'Z' ) ? static_cast<char>( c - 'A' + 'a' ) : c;
            }
            return Value::makeString( std::string_view( buf, s.size() ) );
        }

        /** upper(s): ASCII uppercase. */
        Value fnUpper( const Value * args, uint32_t argCount, Arena & arena )
        {
            if( argCount != 1 || ! args[ 0 ].isString() ) return Value::makeString( std::string_view() );
            const std::string_view s = args[ 0 ].stringValue();
            char * buf = static_cast<char *>( arena.alloc( s.size() ) );
            assert( buf != nullptr && "arena exhausted uppercasing a string" );
            for( size_t i = 0; i < s.size(); ++i )
            {
                const char c = s[ i ];
                buf[ i ] = ( c >= 'a' && c <= 'z' ) ? static_cast<char>( c - 'a' + 'A' ) : c;
            }
            return Value::makeString( std::string_view( buf, s.size() ) );
        }

        /** proper(s): capitalizes only the FIRST character of the trimmed+lowercased
         *  string (matches the real Java behaviour, not its Javadoc). */
        Value fnProper( const Value * args, uint32_t argCount, Arena & arena )
        {
            if( argCount != 1 || ! args[ 0 ].isString() ) return Value::makeString( std::string_view() );
            const Value trimmed = fnTrim( args, 1, arena );
            const Value lowered = fnLower( &trimmed, 1, arena );
            const std::string_view ls = lowered.stringValue();
            if( ls.empty() ) return lowered;

            char * buf = static_cast<char *>( arena.alloc( ls.size() ) );
            assert( buf != nullptr && "arena exhausted title-casing a string" );
            std::memcpy( buf, ls.data(), ls.size() );
            if( buf[ 0 ] >= 'a' && buf[ 0 ] <= 'z' ) buf[ 0 ] = static_cast<char>( buf[ 0 ] - 'a' + 'A' );
            return Value::makeString( std::string_view( buf, ls.size() ) );
        }

        /** unichar(codepoint): UTF-8 encodes a single codepoint (up to 4 bytes). */
        Value fnUnichar( const Value * args, uint32_t argCount, Arena & arena )
        {
            int cp;
            if( argCount != 1 || ! toIntArg( args[ 0 ], cp ) || cp < 0 || cp > 0x10FFFF )
            {
                return Value::makeString( std::string_view() );
            }

            char buf[ 4 ];
            size_t n = 0;
            if( cp <= 0x7F ) { buf[ 0 ] = static_cast<char>( cp ); n = 1; }
            else if( cp <= 0x7FF )
            {
                buf[ 0 ] = static_cast<char>( 0xC0 | ( cp >> 6 ) );
                buf[ 1 ] = static_cast<char>( 0x80 | ( cp & 0x3F ) );
                n = 2;
            }
            else if( cp <= 0xFFFF )
            {
                buf[ 0 ] = static_cast<char>( 0xE0 | ( cp >> 12 ) );
                buf[ 1 ] = static_cast<char>( 0x80 | ( ( cp >> 6 ) & 0x3F ) );
                buf[ 2 ] = static_cast<char>( 0x80 | ( cp & 0x3F ) );
                n = 3;
            }
            else
            {
                buf[ 0 ] = static_cast<char>( 0xF0 | ( cp >> 18 ) );
                buf[ 1 ] = static_cast<char>( 0x80 | ( ( cp >> 12 ) & 0x3F ) );
                buf[ 2 ] = static_cast<char>( 0x80 | ( ( cp >> 6 ) & 0x3F ) );
                buf[ 3 ] = static_cast<char>( 0x80 | ( cp & 0x3F ) );
                n = 4;
            }
            return makeArenaString( arena, std::string_view( buf, n ) );
        }

        /** unicode(s): decodes the first UTF-8 codepoint (malformed -> first byte). */
        Value fnUnicode( const Value * args, uint32_t argCount, Arena & )
        {
            if( argCount != 1 || ! args[ 0 ].isString() ) return Value::makeInt( 0 );
            const std::string_view s = args[ 0 ].stringValue();
            if( s.empty() ) return Value::makeInt( 0 );

            const auto * u = reinterpret_cast<const unsigned char *>( s.data() );
            const unsigned char c0 = u[ 0 ];
            int cp;
            if( c0 < 0x80 ) cp = c0;
            else if( ( c0 & 0xE0 ) == 0xC0 && s.size() >= 2 ) cp = ( ( c0 & 0x1F ) << 6 ) | ( u[ 1 ] & 0x3F );
            else if( ( c0 & 0xF0 ) == 0xE0 && s.size() >= 3 )
                cp = ( ( c0 & 0x0F ) << 12 ) | ( ( u[ 1 ] & 0x3F ) << 6 ) | ( u[ 2 ] & 0x3F );
            else if( ( c0 & 0xF8 ) == 0xF0 && s.size() >= 4 )
                cp = ( ( c0 & 0x07 ) << 18 ) | ( ( u[ 1 ] & 0x3F ) << 12 ) | ( ( u[ 2 ] & 0x3F ) << 6 )
                     | ( u[ 3 ] & 0x3F );
            else cp = c0;
            return Value::makeInt( cp );
        }

        /** equals(a, b, ...): chained equality — all args equal args[0]. */
        Value fnEquals( const Value * args, uint32_t argCount, Arena & )
        {
            if( argCount == 0 ) return Value::makeBool( false );
            for( uint32_t i = 1; i < argCount; ++i )
            {
                if( ! valueEquals( args[ 0 ], args[ i ] ) ) return Value::makeBool( false );
            }
            return Value::makeBool( true );
        }

        /** mid(where, start[, end]): substring extraction (1-based numeric indices;
         *  string start/end search, case-sensitivity matching the real Java). */
        Value fnMid( const Value * args, uint32_t argCount, Arena & arena )
        {
            if( argCount < 2 || ! args[ 0 ].isString() ) return Value::makeString( std::string_view() );
            const std::string_view s = args[ 0 ].stringValue();
            long n1;

            int startIdx;
            if( toIntArg( args[ 1 ], startIdx ) )
            {
                n1 = static_cast<long>( startIdx ) - 1; // 1-based -> 0-based
            }
            else if( args[ 1 ].isString() )
            {
                const std::string_view needle = args[ 1 ].stringValue();
                n1 = -1;
                if( needle.size() <= s.size() )
                {
                    for( size_t i = 0; i + needle.size() <= s.size(); ++i )
                    {
                        if( std::memcmp( s.data() + i, needle.data(), needle.size() ) == 0 )
                        {
                            n1 = static_cast<long>( i );
                            break;
                        }
                    }
                }
                if( n1 >= 0 ) n1 += static_cast<long>( needle.size() );
            }
            else
            {
                return Value::makeString( std::string_view() );
            }

            long n2;
            if( argCount < 3 )
            {
                n2 = static_cast<long>( s.size() );
            }
            else
            {
                int endIdx;
                if( toIntArg( args[ 2 ], endIdx ) )
                {
                    n2 = n1 + static_cast<long>( endIdx );
                }
                else if( args[ 2 ].isString() )
                {
                    const std::string_view needle = args[ 2 ].stringValue();
                    n2 = -1;
                    if( n1 >= 0 && ! needle.empty() )
                    {
                        for( size_t i = static_cast<size_t>( n1 ); i + needle.size() <= s.size(); ++i )
                        {
                            if( util::asciiEqualsIgnoreCase( s.substr( i, needle.size() ), needle ) )
                            {
                                n2 = static_cast<long>( i );
                                break;
                            }
                        }
                    }
                }
                else
                {
                    n2 = static_cast<long>( s.size() );
                }
            }

            if( n2 > n1 && n1 >= 0 && static_cast<size_t>( n1 ) <= s.size() )
            {
                const size_t end = ( static_cast<size_t>( n2 ) > s.size() ) ? s.size() : static_cast<size_t>( n2 );
                return makeArenaString( arena, s.substr( static_cast<size_t>( n1 ), end - static_cast<size_t>( n1 ) ) );
            }
            return Value::makeString( std::string_view() );
        }

        /** timeValid(): whether the clock has been SNTP-synced at least once (this
         *  port's own function, see datetime.hpp). */
        Value fnTimeValid( const Value *, uint32_t, Arena & )
        {
            return Value::makeBool( isTimeValid() );
        }

        /** isEmpty(x): true for whitespace-only/empty strings and empty List/Pair. */
        Value fnIsEmpty( const Value * args, uint32_t argCount, Arena & arena )
        {
            if( argCount != 1 ) return Value::makeBool( false );
            if( args[ 0 ].isString() )
            {
                const Value trimmed = fnTrim( args, 1, arena );
                return Value::makeBool( trimmed.stringValue().empty() );
            }
            if( args[ 0 ].isList() ) return Value::makeBool( args[ 0 ].listValue()->empty() );
            if( args[ 0 ].isPair() ) return Value::makeBool( args[ 0 ].pairValue()->empty() );
            return Value::makeBool( false );
        }

        /** type(x): one-letter type tag. DateTime -> "date" (unified type, documented). */
        Value fnType( const Value * args, uint32_t argCount, Arena & )
        {
            if( argCount != 1 ) return Value::makeString( std::string_view() );
            switch( args[ 0 ].type() )
            {
                case ValueType::Int: return Value::makeString( "I" );
                case ValueType::Float: return Value::makeString( "N" );
                case ValueType::Bool: return Value::makeString( "B" );
                case ValueType::String: return Value::makeString( "S" );
                case ValueType::DateTime: return Value::makeString( "date" );
                case ValueType::List: return Value::makeString( "list" );
                case ValueType::Pair: return Value::makeString( "pair" );
            }
            return Value::makeString( std::string_view() );
        }

        /** size(x)/len(x): String length or List/Pair element count. */
        Value fnSize( const Value * args, uint32_t argCount, Arena & )
        {
            if( argCount != 1 ) return Value::makeInt( 0 );
            if( args[ 0 ].isString() ) return Value::makeInt( static_cast<int64_t>( args[ 0 ].stringValue().size() ) );
            if( args[ 0 ].isList() ) return Value::makeInt( static_cast<int64_t>( args[ 0 ].listValue()->size() ) );
            if( args[ 0 ].isPair() ) return Value::makeInt( static_cast<int64_t>( args[ 0 ].pairValue()->size() ) );
            return Value::makeInt( 0 );
        }

        // ---- DateTime chained methods -------------------------------------------

        Value mDay( const Value & target, const Value *, uint32_t, Arena & )
        {
            return Value::makeInt( localFieldsFromEpoch( target.dateTimeValue() ).day );
        }
        Value mMonth( const Value & target, const Value *, uint32_t, Arena & )
        {
            return Value::makeInt( localFieldsFromEpoch( target.dateTimeValue() ).month );
        }
        /** ISO-8601 weekday: 1=Monday .. 7=Sunday. */
        Value mWeekday( const Value & target, const Value *, uint32_t, Arena & )
        {
            const int tmWday = localFieldsFromEpoch( target.dateTimeValue() ).weekday; // 0=Sun..6=Sat
            return Value::makeInt( ( tmWday == 0 ) ? 7 : tmWday );
        }
        Value mHour( const Value & target, const Value *, uint32_t, Arena & )
        {
            return Value::makeInt( localFieldsFromEpoch( target.dateTimeValue() ).hour );
        }
        Value mMinute( const Value & target, const Value *, uint32_t, Arena & )
        {
            return Value::makeInt( localFieldsFromEpoch( target.dateTimeValue() ).minute );
        }
        Value mSecond( const Value & target, const Value *, uint32_t, Arena & )
        {
            return Value::makeInt( localFieldsFromEpoch( target.dateTimeValue() ).second );
        }
        Value mIsAfter( const Value & target, const Value * args, uint32_t argCount, Arena & )
        {
            if( argCount != 1 || ! args[ 0 ].isDateTime() ) return Value::makeBool( false );
            return Value::makeBool( target.dateTimeValue() > args[ 0 ].dateTimeValue() );
        }
        Value mYear( const Value & target, const Value * args, uint32_t argCount, Arena & )
        {
            if( argCount == 0 )
            {
                return Value::makeInt( localFieldsFromEpoch( target.dateTimeValue() ).year );
            }
            int year;
            if( argCount == 1 && toIntArg( args[ 0 ], year ) )
            {
                const LocalFields f = localFieldsFromEpoch( target.dateTimeValue() );
                struct tm local {};
                local.tm_year = year - 1900;
                local.tm_mon = f.month - 1;
                local.tm_mday = f.day;
                local.tm_hour = f.hour;
                local.tm_min = f.minute;
                local.tm_sec = f.second;
                local.tm_isdst = -1;
                return Value::makeDateTime( static_cast<int64_t>( mktime( &local ) ) );
            }
            return target;
        }
        Value mIsLeap( const Value & target, const Value *, uint32_t, Arena & )
        {
            const int y = localFieldsFromEpoch( target.dateTimeValue() ).year;
            return Value::makeBool( ( y % 4 == 0 && y % 100 != 0 ) || ( y % 400 == 0 ) );
        }
        /** date:move(days): whole-day shift (date semantics for the unified DateTime). */
        Value mMove( const Value & target, const Value * args, uint32_t argCount, Arena & )
        {
            int days;
            if( argCount != 1 || ! toIntArg( args[ 0 ], days ) ) return target;
            return Value::makeDateTime( target.dateTimeValue() + static_cast<int64_t>( days ) * 86400 );
        }
        /** date:duration(other): whole days, other minus this. */
        Value mDuration( const Value & target, const Value * args, uint32_t argCount, Arena & )
        {
            if( argCount != 1 || ! args[ 0 ].isDateTime() ) return Value::makeInt( 0 );
            return Value::makeInt( ( args[ 0 ].dateTimeValue() - target.dateTimeValue() ) / 86400 );
        }
        Value mCompareTo( const Value & target, const Value * args, uint32_t argCount, Arena & )
        {
            if( argCount != 1 || ! args[ 0 ].isDateTime() ) return Value::makeInt( 0 );
            if( target.dateTimeValue() < args[ 0 ].dateTimeValue() ) return Value::makeInt( -1 );
            if( target.dateTimeValue() > args[ 0 ].dateTimeValue() ) return Value::makeInt( 1 );
            return Value::makeInt( 0 );
        }
        Value mSinceMidnight( const Value & target, const Value *, uint32_t, Arena & )
        {
            const LocalFields f = localFieldsFromEpoch( target.dateTimeValue() );
            return Value::makeInt( f.hour * 3600 + f.minute * 60 + f.second );
        }
        Value mAddHours( const Value & target, const Value * args, uint32_t argCount, Arena & )
        {
            int hours;
            if( argCount != 1 || ! toIntArg( args[ 0 ], hours ) ) return target;
            return Value::makeDateTime( target.dateTimeValue() + static_cast<int64_t>( hours ) * 3600 );
        }
        Value mAddMinutes( const Value & target, const Value * args, uint32_t argCount, Arena & )
        {
            int minutes;
            if( argCount != 1 || ! toIntArg( args[ 0 ], minutes ) ) return target;
            return Value::makeDateTime( target.dateTimeValue() + static_cast<int64_t>( minutes ) * 60 );
        }
        Value mIsBefore( const Value & target, const Value * args, uint32_t argCount, Arena & )
        {
            if( argCount != 1 || ! args[ 0 ].isDateTime() ) return Value::makeBool( false );
            return Value::makeBool( target.dateTimeValue() < args[ 0 ].dateTimeValue() );
        }

        // ---- List/Pair chained methods ------------------------------------------

        Value mSize( const Value & target, const Value *, uint32_t, Arena & )
        {
            const int64_t count = target.isList() ? static_cast<int64_t>( target.listValue()->size() )
                                                  : static_cast<int64_t>( target.pairValue()->size() );
            return Value::makeInt( count );
        }
        Value mIsEmpty( const Value & target, const Value *, uint32_t, Arena & )
        {
            return Value::makeBool( target.isList() ? target.listValue()->empty()
                                                    : target.pairValue()->empty() );
        }
        /** empty(): clears and returns the collection for chaining. */
        Value mEmptyMutator( const Value & target, const Value *, uint32_t, Arena & )
        {
            if( target.isList() ) target.listValue()->clear();
            else if( target.isPair() ) target.pairValue()->clear();
            return target;
        }
        /** get(index) for List (1-based, negative from end); get(key[,default]) for Pair. */
        Value mGet( const Value & target, const Value * args, uint32_t argCount, Arena & )
        {
            if( target.isPair() )
            {
                if( argCount < 1 || ! args[ 0 ].isString() ) return Value::makeString( std::string_view() );
                const Value * found = target.pairValue()->get( args[ 0 ].stringValue() );
                if( found != nullptr ) return *found;
                return ( argCount >= 2 ) ? args[ 1 ] : Value::makeString( std::string_view() );
            }

            int idx;
            if( argCount != 1 || ! toIntArg( args[ 0 ], idx ) ) return Value::makeString( std::string_view() );

            List * list = target.listValue();
            const int count = static_cast<int>( list->size() );
            const int zeroBased = ( idx < 0 ) ? ( count + idx ) : ( idx - 1 );
            if( zeroBased < 0 || zeroBased >= count ) return Value::makeString( std::string_view() );
            return ( *list )[ static_cast<size_t>( zeroBased ) ];
        }
        /** put(key,value): Pair only (List has no put). */
        Value mPut( const Value & target, const Value * args, uint32_t argCount, Arena & )
        {
            if( target.isPair() && argCount == 2 && args[ 0 ].isString() )
            {
                target.pairValue()->set( args[ 0 ].stringValue(), args[ 1 ] );
            }
            return target;
        }
        /** del(key) for Pair; del(index) for List (1-based, negative from end). */
        Value mDel( const Value & target, const Value * args, uint32_t argCount, Arena & )
        {
            if( target.isPair() )
            {
                if( argCount == 1 && args[ 0 ].isString() )
                {
                    target.pairValue()->remove( args[ 0 ].stringValue() );
                }
                return target;
            }

            int idx;
            if( target.isList() && argCount == 1 && toIntArg( args[ 0 ], idx ) )
            {
                List * list = target.listValue();
                const int count = static_cast<int>( list->size() );
                const int zeroBased = ( idx < 0 ) ? ( count + idx ) : ( idx - 1 );
                if( zeroBased >= 0 && zeroBased < count )
                {
                    list->remove( static_cast<size_t>( zeroBased ) );
                }
            }
            return target;
        }
        /** list:has(item) — membership by valueEquals(). */
        Value mHas( const Value & target, const Value * args, uint32_t argCount, Arena & )
        {
            if( ! target.isList() || argCount != 1 ) return Value::makeBool( false );
            List * list = target.listValue();
            for( const Value & item : *list )
            {
                if( valueEquals( item, args[ 0 ] ) ) return Value::makeBool( true );
            }
            return Value::makeBool( false );
        }
        /** list:index(item): 1-based position, 0 if not found. */
        Value mIndex( const Value & target, const Value * args, uint32_t argCount, Arena & )
        {
            if( ! target.isList() || argCount != 1 ) return Value::makeInt( 0 );
            List * list = target.listValue();
            for( size_t i = 0; i < list->size(); ++i )
            {
                if( valueEquals( ( *list )[ i ], args[ 0 ] ) )
                {
                    return Value::makeInt( static_cast<int64_t>( i + 1 ) );
                }
            }
            return Value::makeInt( 0 );
        }
        /** list:set(index, item): replace an element (1-based, negative from end). */
        Value mListSet( const Value & target, const Value * args, uint32_t argCount, Arena & )
        {
            int idx;
            if( ! target.isList() || argCount != 2 || ! toIntArg( args[ 0 ], idx ) ) return target;
            List * list = target.listValue();
            const int count = static_cast<int>( list->size() );
            const int zeroBased = ( idx < 0 ) ? ( count + idx ) : ( idx - 1 );
            if( zeroBased >= 0 && zeroBased < count )
            {
                list->set( static_cast<size_t>( zeroBased ), args[ 1 ] );
            }
            return target;
        }
        /** negate(index|key): flips a Bool, or toggles a 0<->1 number. */
        Value mNegate( const Value & target, const Value * args, uint32_t argCount, Arena & )
        {
            if( argCount != 1 ) return target;

            if( target.isList() )
            {
                int idx;
                if( ! toIntArg( args[ 0 ], idx ) ) return target;
                List * list = target.listValue();
                const int count = static_cast<int>( list->size() );
                const int zeroBased = ( idx < 0 ) ? ( count + idx ) : ( idx - 1 );
                if( zeroBased < 0 || zeroBased >= count ) return target;

                const Value v = ( *list )[ static_cast<size_t>( zeroBased ) ];
                Value toggled = v;
                if( v.isBool() ) toggled = Value::makeBool( ! v.boolValue() );
                else if( v.isInt() && v.intValue() == 0 ) toggled = Value::makeInt( 1 );
                else if( v.isInt() && v.intValue() == 1 ) toggled = Value::makeInt( 0 );
                else if( v.isFloat() && v.floatValue() == 0.0f ) toggled = Value::makeFloat( 1.0f );
                else if( v.isFloat() && v.floatValue() == 1.0f ) toggled = Value::makeFloat( 0.0f );
                list->set( static_cast<size_t>( zeroBased ), toggled );
                return target;
            }

            if( target.isPair() && args[ 0 ].isString() )
            {
                const std::string_view key = args[ 0 ].stringValue();
                const Value * found = target.pairValue()->get( key );
                if( found != nullptr )
                {
                    Value toggled = *found;
                    if( found->isBool() ) toggled = Value::makeBool( ! found->boolValue() );
                    else if( found->isInt() && found->intValue() == 0 ) toggled = Value::makeInt( 1 );
                    else if( found->isInt() && found->intValue() == 1 ) toggled = Value::makeInt( 0 );
                    else if( found->isFloat() && found->floatValue() == 0.0f ) toggled = Value::makeFloat( 1.0f );
                    else if( found->isFloat() && found->floatValue() == 1.0f ) toggled = Value::makeFloat( 0.0f );
                    target.pairValue()->set( key, toggled );
                }
            }
            return target;
        }
        /** clone(): shallow copy into a new heap collection (nested collections shared
         *  by pointer). */
        Value mClone( const Value & target, const Value *, uint32_t, Arena & arena )
        {
            if( target.isList() )
            {
                List * src = target.listValue();
                List * out = List::create( src->size() );
                if( out == nullptr ) return target;
                if( ! arena.defer( out ) )
                {
                    List::destroy( out );
                    return target;
                }
                for( const Value & item : *src ) out->add( item );
                return Value::makeList( out );
            }
            if( target.isPair() )
            {
                Pair * src = target.pairValue();
                Pair * out = Pair::create( src->size() );
                if( out == nullptr ) return target;
                if( ! arena.defer( out ) )
                {
                    Pair::destroy( out );
                    return target;
                }
                for( size_t i = 0; i < src->size(); ++i )
                {
                    out->set( src->keyAt( i ), src->valueAt( i ) );
                }
                return Value::makePair( out );
            }
            return target;
        }
        Value mUniquefy( const Value & target, const Value *, uint32_t, Arena & )
        {
            if( target.isList() ) target.listValue()->uniquefy();
            return target;
        }
        Value mSort( const Value & target, const Value *, uint32_t, Arena & )
        {
            if( target.isList() ) target.listValue()->sort();
            return target;
        }
        Value mListReverse( const Value & target, const Value *, uint32_t, Arena & )
        {
            if( target.isList() ) target.listValue()->reverse();
            return target;
        }
        Value mListIntersect( const Value & target, const Value * args, uint32_t argCount, Arena & )
        {
            if( target.isList() && argCount == 1 && args[ 0 ].isList() )
            {
                target.listValue()->intersect( *args[ 0 ].listValue() );
            }
            return target;
        }
        Value mListUnion( const Value & target, const Value * args, uint32_t argCount, Arena & )
        {
            if( target.isList() && argCount == 1 && args[ 0 ].isList() )
            {
                target.listValue()->appendAll( *args[ 0 ].listValue() );
            }
            return target;
        }
        Value mPeek( const Value & target, const Value *, uint32_t, Arena & )
        {
            if( ! target.isList() ) return Value::makeString( std::string_view() );
            List * list = target.listValue();
            if( list->empty() ) return Value::makeString( std::string_view() );
            return ( *list )[ list->size() - 1 ];
        }
        Value mPop( const Value & target, const Value *, uint32_t, Arena & )
        {
            if( ! target.isList() ) return Value::makeString( std::string_view() );
            List * list = target.listValue();
            if( list->empty() ) return Value::makeString( std::string_view() );
            const Value v = ( *list )[ list->size() - 1 ];
            list->remove( list->size() - 1 );
            return v;
        }
        Value mPush( const Value & target, const Value * args, uint32_t argCount, Arena & )
        {
            if( target.isList() && argCount == 1 ) target.listValue()->add( args[ 0 ] );
            return target;
        }
        Value mAdd( const Value & target, const Value * args, uint32_t argCount, Arena & )
        {
            if( target.isList() && argCount == 1 ) target.listValue()->add( args[ 0 ] );
            return target;
        }
        Value mLast( const Value & target, const Value * args, uint32_t argCount, Arena & )
        {
            if( ! target.isList() || target.listValue()->empty() )
            {
                return ( argCount >= 1 ) ? args[ 0 ] : Value::makeString( std::string_view() );
            }
            return ( *target.listValue() )[ target.listValue()->size() - 1 ];
        }
        Value mAddAll( const Value & target, const Value * args, uint32_t argCount, Arena & )
        {
            if( target.isList() && argCount == 1 && args[ 0 ].isList() )
            {
                target.listValue()->appendAll( *args[ 0 ].listValue() );
            }
            return target;
        }
        Value mRotate( const Value & target, const Value * args, uint32_t argCount, Arena & )
        {
            if( ! target.isList() ) return target;
            int places = 1;
            if( argCount >= 1 ) toIntArg( args[ 0 ], places );
            target.listValue()->rotate( places );
            return target;
        }
        Value mKeys( const Value & target, const Value *, uint32_t, Arena & arena )
        {
            if( ! target.isPair() ) return Value::makeString( std::string_view() );
            Pair * src = target.pairValue();
            List * out = List::create( src->size() );
            if( out == nullptr ) return Value::makeString( std::string_view() );
            if( ! arena.defer( out ) )
            {
                List::destroy( out );
                return Value::makeString( std::string_view() );
            }
            for( size_t i = 0; i < src->size(); ++i )
            {
                out->add( Value::makeString( src->keyAt( i ) ) );
            }
            return Value::makeList( out );
        }
        Value mValues( const Value & target, const Value *, uint32_t, Arena & arena )
        {
            if( ! target.isPair() ) return Value::makeString( std::string_view() );
            Pair * src = target.pairValue();
            List * out = List::create( src->size() );
            if( out == nullptr ) return Value::makeString( std::string_view() );
            if( ! arena.defer( out ) )
            {
                List::destroy( out );
                return Value::makeString( std::string_view() );
            }
            for( size_t i = 0; i < src->size(); ++i )
            {
                out->add( src->valueAt( i ) );
            }
            return Value::makeList( out );
        }
        Value mHasKey( const Value & target, const Value * args, uint32_t argCount, Arena & )
        {
            if( ! target.isPair() || argCount != 1 || ! args[ 0 ].isString() ) return Value::makeBool( false );
            return Value::makeBool( target.pairValue()->get( args[ 0 ].stringValue() ) != nullptr );
        }
        Value mHasValue( const Value & target, const Value * args, uint32_t argCount, Arena & )
        {
            if( ! target.isPair() || argCount != 1 ) return Value::makeBool( false );
            Pair * pair = target.pairValue();
            for( size_t i = 0; i < pair->size(); ++i )
            {
                if( valueEquals( pair->valueAt( i ), args[ 0 ] ) ) return Value::makeBool( true );
            }
            return Value::makeBool( false );
        }
        Value mPutAll( const Value & target, const Value * args, uint32_t argCount, Arena & )
        {
            if( target.isPair() && argCount == 1 && args[ 0 ].isPair() )
            {
                target.pairValue()->putAll( *args[ 0 ].pairValue() );
            }
            return target;
        }
        Value mPairIntersect( const Value & target, const Value * args, uint32_t argCount, Arena & )
        {
            if( target.isPair() && argCount == 1 && args[ 0 ].isPair() )
            {
                target.pairValue()->intersect( *args[ 0 ].pairValue() );
            }
            return target;
        }
        Value mPairUnion( const Value & target, const Value * args, uint32_t argCount, Arena & arena )
        {
            return mPutAll( target, args, argCount, arena );
        }
    } // namespace

    FunctionPtr resolveFunction( std::string_view name )
    {
        if( nameIs( name, "date" ) ) return fnDate;
        if( nameIs( name, "time" ) ) return fnTime;
        if( nameIs( name, "list" ) ) return fnList;
        if( nameIs( name, "pair" ) ) return fnPair;
        if( nameIs( name, "floor" ) ) return fnFloor;
        if( nameIs( name, "mod" ) ) return fnMod;
        if( nameIs( name, "rand" ) ) return fnRand;
        if( nameIs( name, "format" ) ) return fnFormat;
        if( nameIs( name, "iif" ) ) return fnIif;
        if( nameIs( name, "put" ) ) return fnPut;
        if( nameIs( name, "get" ) ) return fnGet;
        if( nameIs( name, "Int" ) ) return fnInt;
        if( nameIs( name, "max" ) ) return fnMax;
        if( nameIs( name, "min" ) ) return fnMin;
        if( nameIs( name, "ceiling" ) ) return fnCeiling;
        if( nameIs( name, "round" ) ) return fnRound;
        if( nameIs( name, "abs" ) ) return fnAbs;
        if( nameIs( name, "isBetween" ) ) return fnIsBetween;
        if( nameIs( name, "setBetween" ) ) return fnSetBetween;
        if( nameIs( name, "trim" ) ) return fnTrim;
        if( nameIs( name, "reverse" ) ) return fnReverse;
        if( nameIs( name, "left" ) ) return fnLeft;
        if( nameIs( name, "right" ) ) return fnRight;
        if( nameIs( name, "lower" ) ) return fnLower;
        if( nameIs( name, "upper" ) ) return fnUpper;
        if( nameIs( name, "proper" ) ) return fnProper;
        if( nameIs( name, "unichar" ) ) return fnUnichar;
        if( nameIs( name, "unicode" ) ) return fnUnicode;
        if( nameIs( name, "equals" ) ) return fnEquals;
        if( nameIs( name, "mid" ) ) return fnMid;
        if( nameIs( name, "isEmpty" ) ) return fnIsEmpty;
        if( nameIs( name, "type" ) ) return fnType;
        if( nameIs( name, "timeValid" ) ) return fnTimeValid;
        if( nameIs( name, "size" ) || nameIs( name, "len" ) ) return fnSize;

        // Deferred (need later-phase infrastructure or a regex/parser dependency):
        // prev, getTriggeredBy, exit, isReachable, enable, disable, search, substitute,
        // localIPs.
        return nullptr;
    }

    MethodPtr resolveMethod( ValueType type, std::string_view name )
    {
        if( type == ValueType::DateTime )
        {
            if( nameIs( name, "day" ) ) return mDay;
            if( nameIs( name, "month" ) ) return mMonth;
            if( nameIs( name, "weekday" ) ) return mWeekday;
            if( nameIs( name, "hour" ) ) return mHour;
            if( nameIs( name, "minute" ) ) return mMinute;
            if( nameIs( name, "second" ) ) return mSecond;
            if( nameIs( name, "isAfter" ) ) return mIsAfter;
            if( nameIs( name, "year" ) ) return mYear;
            if( nameIs( name, "isLeap" ) ) return mIsLeap;
            if( nameIs( name, "move" ) ) return mMove;
            if( nameIs( name, "duration" ) ) return mDuration;
            if( nameIs( name, "compareTo" ) ) return mCompareTo;
            if( nameIs( name, "sinceMidnight" ) ) return mSinceMidnight;
            if( nameIs( name, "addHours" ) ) return mAddHours;
            if( nameIs( name, "addMinutes" ) ) return mAddMinutes;
            if( nameIs( name, "isBefore" ) ) return mIsBefore;
            // twilightSunRise/twilightSunSet/sunRise/sunSet/sunNoon/isDay/isNight deferred.
            return nullptr;
        }

        if( type == ValueType::List || type == ValueType::Pair )
        {
            if( nameIs( name, "size" ) || nameIs( name, "len" ) ) return mSize;
            if( nameIs( name, "isEmpty" ) ) return mIsEmpty;
            if( nameIs( name, "empty" ) ) return mEmptyMutator;
            if( nameIs( name, "get" ) ) return mGet;
            if( nameIs( name, "put" ) && type == ValueType::Pair ) return mPut;
            if( nameIs( name, "del" ) ) return mDel;
            if( nameIs( name, "has" ) && type == ValueType::List ) return mHas;
            if( nameIs( name, "index" ) && type == ValueType::List ) return mIndex;
            if( nameIs( name, "set" ) && type == ValueType::List ) return mListSet;
            if( nameIs( name, "negate" ) ) return mNegate;
            if( nameIs( name, "clone" ) ) return mClone;
            if( nameIs( name, "uniquefy" ) && type == ValueType::List ) return mUniquefy;
            if( nameIs( name, "sort" ) && type == ValueType::List ) return mSort;
            if( nameIs( name, "reverse" ) && type == ValueType::List ) return mListReverse;
            if( nameIs( name, "intersect" ) )
            {
                return ( type == ValueType::List ) ? mListIntersect : mPairIntersect;
            }
            if( nameIs( name, "union" ) )
            {
                return ( type == ValueType::List ) ? mListUnion : mPairUnion;
            }
            if( nameIs( name, "peek" ) && type == ValueType::List ) return mPeek;
            if( nameIs( name, "pop" ) && type == ValueType::List ) return mPop;
            if( nameIs( name, "push" ) && type == ValueType::List ) return mPush;
            if( nameIs( name, "add" ) && type == ValueType::List ) return mAdd;
            if( nameIs( name, "last" ) && type == ValueType::List ) return mLast;
            if( nameIs( name, "addAll" ) && type == ValueType::List ) return mAddAll;
            if( nameIs( name, "rotate" ) && type == ValueType::List ) return mRotate;
            if( nameIs( name, "keys" ) && type == ValueType::Pair ) return mKeys;
            if( nameIs( name, "values" ) && type == ValueType::Pair ) return mValues;
            if( nameIs( name, "hasKey" ) && type == ValueType::Pair ) return mHasKey;
            if( nameIs( name, "hasValue" ) && type == ValueType::Pair ) return mHasValue;
            if( nameIs( name, "putAll" ) && type == ValueType::Pair ) return mPutAll;
            // split/map/filter/reduce/toJson still deferred — see file header.
            return nullptr;
        }

        // Int/Float/Bool/String are not extended types: numeric chained calls resolve
        // to the global function table (ast.cpp prepends the receiver as arg0).
        return nullptr;
    }
} // namespace naxe
