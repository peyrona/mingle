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
/** \file functions_tests.cpp
 *
 * Host/on-device tests for the function/method library. Exercises the dispatch tables
 * and a representative slice of each function family, including the Int-aware behaviour
 * (`Int()`, `abs` on Int, integer-valued date fields/sizes).
 */
#include "naxe/functions_tests.hpp"

#include <cstdio>
#include <string_view>

#include "naxe/arena.hpp"
#include "naxe/ast.hpp"
#include "naxe/datetime.hpp"
#include "naxe/functions.hpp"
#include "naxe/list.hpp"
#include "naxe/pair.hpp"
#include "naxe/value.hpp"

namespace
{
    int failures = 0;

    void check( bool condition, const char * message )
    {
        if( ! condition )
        {
            ++failures;
            std::printf( "FAIL: %s\n", message );
        }
    }

    constexpr size_t kArenaSize = 4096;
    alignas( 8 ) uint8_t arenaBuffer[ kArenaSize ];

    naxe::Value callImpl( const char * name, const naxe::Value * args, uint32_t argCount, naxe::Arena & arena )
    {
        naxe::FunctionPtr fn = naxe::resolveFunction( name );
        return ( fn != nullptr ) ? fn( args, argCount, arena ) : naxe::Value::makeBool( false );
    }

    naxe::Value methodImpl( naxe::ValueType type, const char * name, const naxe::Value & target,
                            const naxe::Value * args, uint32_t argCount, naxe::Arena & arena )
    {
        naxe::MethodPtr m = naxe::resolveMethod( type, name );
        return ( m != nullptr ) ? m( target, args, argCount, arena ) : naxe::Value::makeBool( false );
    }

    /** Invokes a top-level function, copying the given argument Values into a stack array. */
    template <typename... Args>
    naxe::Value call( const char * name, naxe::Arena & arena, const Args &... args )
    {
        const naxe::Value arr[] = { args... };
        return callImpl( name, arr, static_cast<uint32_t>( sizeof...( args ) ), arena );
    }

    /** Invokes a method, copying the given argument Values into a stack array. */
    template <typename... Args>
    naxe::Value method( naxe::ValueType type, const char * name, const naxe::Value & target,
                        naxe::Arena & arena, const Args &... args )
    {
        const naxe::Value arr[] = { args... };
        return methodImpl( type, name, target, arr, static_cast<uint32_t>( sizeof...( args ) ), arena );
    }

    /** Invokes a zero-argument method. */
    naxe::Value method( naxe::ValueType type, const char * name, const naxe::Value & target, naxe::Arena & arena )
    {
        return methodImpl( type, name, target, nullptr, 0, arena );
    }

    void testNumericFunctions()
    {
        naxe::Arena arena( arenaBuffer, kArenaSize );

        // Int(): explicit integer conversion, returns an Int (not Float).
        naxe::Value i = call( "Int", arena, naxe::Value::makeFloat( 3.9f ) );
        check( i.isInt() && i.intValue() == 3, "Int(3.9) truncates to Int 3" );

        naxe::Value hx = call( "Int", arena, naxe::Value::makeString( "0x10" ) );
        check( hx.isInt() && hx.intValue() == 16, "Int(0x10) parses hex" );

        // abs: preserves Int type.
        naxe::Value ai = call( "abs", arena, naxe::Value::makeInt( -7 ) );
        check( ai.isInt() && ai.intValue() == 7, "abs(Int) returns Int" );
        naxe::Value af = call( "abs", arena, naxe::Value::makeFloat( -2.5f ) );
        check( af.isFloat() && af.floatValue() == 2.5f, "abs(Float) returns Float" );

        // floor/round/ceiling/mod (float-domain).
        naxe::Value fl = call( "floor", arena, naxe::Value::makeFloat( 3.7f ) );
        check( fl.isFloat() && fl.floatValue() == 3.0f, "floor(3.7)" );
        naxe::Value rnd = call( "round", arena, naxe::Value::makeFloat( 2.5f ) );
        check( rnd.floatValue() == 3.0f, "round(2.5) HALF-UP" );
        naxe::Value md = call( "mod", arena, naxe::Value::makeInt( -10 ), naxe::Value::makeInt( 3 ) );
        check( md.floatValue() == 2.0f, "mod(-10,3) == 2" );

        // min/max.
        naxe::Value mx = call( "max", arena, naxe::Value::makeInt( 1 ), naxe::Value::makeInt( 9 ) );
        check( mx.floatValue() == 9.0f, "max(1,9)" );
    }

    void testStringFunctions()
    {
        naxe::Arena arena( arenaBuffer, kArenaSize );

        naxe::Value t = call( "trim", arena, naxe::Value::makeString( "  hi  " ) );
        check( t.stringValue() == "hi", "trim" );
        naxe::Value u = call( "upper", arena, naxe::Value::makeString( "ab" ) );
        check( u.stringValue() == "AB", "upper" );
        naxe::Value l = call( "lower", arena, naxe::Value::makeString( "AB" ) );
        check( l.stringValue() == "ab", "lower" );
        naxe::Value r = call( "reverse", arena, naxe::Value::makeString( "abc" ) );
        check( r.stringValue() == "cba", "reverse" );
        naxe::Value le = call( "left", arena, naxe::Value::makeString( "hello" ), naxe::Value::makeInt( 2 ) );
        check( le.stringValue() == "he", "left" );
        naxe::Value sz = call( "size", arena, naxe::Value::makeString( "hello" ) );
        check( sz.isInt() && sz.intValue() == 5, "size(String) returns Int 5" );
        naxe::Value eq = call( "equals", arena, naxe::Value::makeInt( 1 ), naxe::Value::makeInt( 1 ), naxe::Value::makeInt( 1 ) );
        check( eq.boolValue(), "equals(1,1,1)" );
    }

    void testGlobalMap()
    {
        naxe::Arena arena( arenaBuffer, kArenaSize );

        check( call( "put", arena, naxe::Value::makeString( "count" ), naxe::Value::makeInt( 42 ) ).boolValue(),
               "put(count,42)" );

        naxe::Value got = call( "get", arena, naxe::Value::makeString( "COUNT" ) ); // case-insensitive
        check( got.isInt() && got.intValue() == 42, "get(COUNT) case-insensitive -> 42" );

        naxe::Value missing = call( "get", arena, naxe::Value::makeString( "nope" ) );
        check( missing.isString() && missing.stringValue().empty(), "get(missing) -> empty" );
    }

    void testListFunctions()
    {
        naxe::Arena arena( arenaBuffer, kArenaSize );

        // list(1,2,3) -> size 3, get(1) == 1.
        naxe::Value list = call( "list", arena, naxe::Value::makeInt( 1 ), naxe::Value::makeInt( 2 ), naxe::Value::makeInt( 3 ) );
        check( list.isList(), "list() returns a List" );
        check( list.listValue()->size() == 3, "list(1,2,3) size 3" );

        // list:get is 1-based.
        naxe::Value first = method( naxe::ValueType::List, "get", list, arena, naxe::Value::makeInt( 1 ) );
        check( first.isInt() && first.intValue() == 1, "list:get(1) == 1" );

        // list:add / list:push grow the list.
        naxe::Value added = method( naxe::ValueType::List, "add", list, arena, naxe::Value::makeInt( 99 ) );
        check( added.listValue()->size() == 4, "list:add grows to 4" );
        method( naxe::ValueType::List, "push", list, arena, naxe::Value::makeInt( 100 ) );
        check( list.listValue()->size() == 5, "list:push grows to 5" );

        // list:size returns Int.
        naxe::Value sz = method( naxe::ValueType::List, "size", list, arena );
        check( sz.isInt() && sz.intValue() == 5, "list:size returns Int" );

        // list:last / list:pop.
        naxe::Value last = method( naxe::ValueType::List, "last", list, arena );
        check( last.isInt() && last.intValue() == 100, "list:last == 100" );
        naxe::Value popped = method( naxe::ValueType::List, "pop", list, arena );
        check( popped.intValue() == 100 && list.listValue()->size() == 4, "list:pop" );

        // list:del(1) removes first.
        method( naxe::ValueType::List, "del", list, arena, naxe::Value::makeInt( 1 ) );
        check( list.listValue()->size() == 3, "list:del(1)" );

        // list:sort / list:reverse.
        method( naxe::ValueType::List, "sort", list, arena );
        check( list.listValue()->size() == 3, "list:sort runs" );
        method( naxe::ValueType::List, "reverse", list, arena );
        check( list.listValue()->size() == 3, "list:reverse runs" );

        // list is arena-deferred (fnList), so no explicit destroy here.
    }

    void testPairFunctions()
    {
        naxe::Arena arena( arenaBuffer, kArenaSize );

        // pair("a",1,"b",2).
        naxe::Value pair = call( "pair", arena, naxe::Value::makeString( "a" ), naxe::Value::makeInt( 1 ),
                                 naxe::Value::makeString( "b" ), naxe::Value::makeInt( 2 ) );
        check( pair.isPair() && pair.pairValue()->size() == 2, "pair() builds a Pair" );

        naxe::Value got = method( naxe::ValueType::Pair, "get", pair, arena, naxe::Value::makeString( "B" ) );
        check( got.isInt() && got.intValue() == 2, "pair:get(B) case-insensitive" );

        check( method( naxe::ValueType::Pair, "hasKey", pair, arena, naxe::Value::makeString( "a" ) ).boolValue(),
               "pair:hasKey(a)" );
        check( method( naxe::ValueType::Pair, "hasValue", pair, arena, naxe::Value::makeInt( 1 ) ).boolValue(),
               "pair:hasValue(1)" );

        naxe::Value keys = method( naxe::ValueType::Pair, "keys", pair, arena );
        check( keys.isList() && keys.listValue()->size() == 2, "pair:keys -> List of 2" );

        // put + del.
        method( naxe::ValueType::Pair, "put", pair, arena, naxe::Value::makeString( "c" ), naxe::Value::makeInt( 3 ) );
        check( pair.pairValue()->size() == 3, "pair:put grows" );
        method( naxe::ValueType::Pair, "del", pair, arena, naxe::Value::makeString( "a" ) );
        check( pair.pairValue()->size() == 2, "pair:del" );

        // pair is arena-deferred (fnPair), so no explicit destroy here.
    }

    void testDateTimeMethods()
    {
        naxe::initTimeZone();
        naxe::Arena arena( arenaBuffer, kArenaSize );

        int64_t epoch;
        check( naxe::parseYMD( "2021-04-08", epoch ), "parseYMD for date methods" );
        naxe::Value dt = naxe::Value::makeDateTime( epoch );

        naxe::Value day = method( naxe::ValueType::DateTime, "day", dt, arena );
        check( day.isInt() && day.intValue() == 8, "date:day -> Int 8" );
        naxe::Value month = method( naxe::ValueType::DateTime, "month", dt, arena );
        check( month.isInt() && month.intValue() == 4, "date:month -> Int 4" );
        naxe::Value year = method( naxe::ValueType::DateTime, "year", dt, arena );
        check( year.isInt() && year.intValue() == 2021, "date:year -> Int 2021" );

        naxe::Value leap = method( naxe::ValueType::DateTime, "isLeap", dt, arena );
        check( ! leap.boolValue(), "date:isLeap(2021) false" );

        naxe::Value after = method( naxe::ValueType::DateTime, "move", dt, arena, naxe::Value::makeInt( 1 ) );
        check( after.isDateTime() && after.dateTimeValue() == epoch + 86400, "date:move(1) +1 day" );
    }

    void testDispatchTables()
    {
        check( naxe::resolveFunction( "DATE" ) != nullptr, "resolveFunction is case-insensitive" );
        check( naxe::resolveFunction( "match" ) == nullptr, "regex match() absent" );
        check( naxe::resolveMethod( naxe::ValueType::List, "SIZE" ) != nullptr, "resolveMethod List:size" );
        check( naxe::resolveMethod( naxe::ValueType::Float, "mod" ) == nullptr,
               "Float is not an extended type (no per-type method table)" );
    }

    void testAstFunctionCall()
    {
        // Full AST path: eval abs(-5) == 5.
        naxe::Arena arena( arenaBuffer, kArenaSize );

        naxe::ASTNode * arg = naxe::astUnary( arena, naxe::AstOp::Neg, naxe::astLiteral( arena, naxe::Value::makeInt( 5 ) ) );
        naxe::ASTNode * argsArr[ 1 ] = { arg };
        naxe::ASTNode * callNode = naxe::astCall( arena, "abs", argsArr, 1 );

        naxe::Value r = naxe::eval( callNode, naxe::Environment( nullptr ), arena );
        check( r.isInt() && r.intValue() == 5, "eval abs(-5) via AST" );
    }
} // namespace

namespace naxe
{
    int runFunctionsTests()
    {
        failures = 0;

        testNumericFunctions();
        testStringFunctions();
        testGlobalMap();
        testListFunctions();
        testPairFunctions();
        testDateTimeMethods();
        testDispatchTables();
        testAstFunctionCall();

        if( failures == 0 )
        {
            std::printf( "[naxe] functions tests: PASS\n" );
        }
        else
        {
            std::printf( "[naxe] functions tests: %d FAILURE(S)\n", failures );
        }

        return failures;
    }
} // namespace naxe
