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
/** \file eval_tests.cpp
 *
 * Host/on-device tests for the evaluator core (Phase 3): operators (including the new
 * exact-Int semantics), AST evaluation + Environment, variable-name collection, and
 * date/time. The function/method tables are empty in this phase, so function calls are
 * tested for their fail-loud `#UNSUPPORTED_*#` behaviour.
 */
#include "naxe/eval_tests.hpp"

#include <cstdio>

#include "naxe/arena.hpp"
#include "naxe/ast.hpp"
#include "naxe/datetime.hpp"
#include "naxe/operators.hpp"
#include "naxe/pair.hpp"
#include "naxe/util.hpp"
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

    constexpr size_t kArenaSize = 2048;
    alignas( 8 ) uint8_t arenaBuffer[ kArenaSize ];

    void testCoercion()
    {
        float f;

        check( naxe::tryToFloat( naxe::Value::makeFloat( 1.5f ), f ) && f == 1.5f, "tryToFloat(Float)" );
        check( naxe::tryToFloat( naxe::Value::makeInt( 42 ), f ) && f == 42.0f, "tryToFloat(Int)" );
        check( naxe::tryToFloat( naxe::Value::makeString( "3.5" ), f ) && f == 3.5f, "tryToFloat(numeric String)" );
        check( ! naxe::tryToFloat( naxe::Value::makeString( "3.5x" ), f ), "tryToFloat rejects trailing garbage" );
        check( ! naxe::tryToFloat( naxe::Value::makeBool( true ), f ), "tryToFloat rejects Bool" );

        int64_t i;
        check( naxe::tryToInt( naxe::Value::makeInt( 7 ), i ) && i == 7, "tryToInt(Int)" );
        check( naxe::tryToInt( naxe::Value::makeString( "123" ), i ) && i == 123, "tryToInt(numeric String)" );
        check( ! naxe::tryToInt( naxe::Value::makeString( "12.5" ), i ), "tryToInt rejects fractional String" );
        check( ! naxe::tryToInt( naxe::Value::makeFloat( 2.0f ), i ), "tryToInt rejects Float (no silent truncation)" );
    }

    void testOperatorsInt()
    {
        using naxe::AstOp;
        naxe::Arena arena( arenaBuffer, kArenaSize );

        // Exact int64 arithmetic (values past float32's mantissa stay exact).
        naxe::Value sum = naxe::evalBinaryOp( AstOp::Add, naxe::Value::makeInt( 2000000000 ),
                                              naxe::Value::makeInt( 1 ), arena );
        check( sum.isInt() && sum.intValue() == 2000000001, "Int + Int is exact at 2e9" );

        naxe::Value diff = naxe::evalBinaryOp( AstOp::Sub, naxe::Value::makeInt( 5 ),
                                               naxe::Value::makeInt( 3 ), arena );
        check( diff.isInt() && diff.intValue() == 2, "Int - Int" );

        naxe::Value prod = naxe::evalBinaryOp( AstOp::Mul, naxe::Value::makeInt( 6 ),
                                               naxe::Value::makeInt( 7 ), arena );
        check( prod.isInt() && prod.intValue() == 42, "Int * Int" );

        // Mixed Int/Float promotes to Float.
        naxe::Value mixed = naxe::evalBinaryOp( AstOp::Add, naxe::Value::makeInt( 1 ),
                                                naxe::Value::makeFloat( 0.5f ), arena );
        check( mixed.isFloat() && mixed.floatValue() == 1.5f, "Int + Float promotes to Float" );

        // Division is always float (matches Java), even Int/Int.
        naxe::Value q = naxe::evalBinaryOp( AstOp::Div, naxe::Value::makeInt( 5 ),
                                            naxe::Value::makeInt( 2 ), arena );
        check( q.isFloat() && q.floatValue() == 2.5f, "Int / Int is float division" );

        // Exact Int comparison (float32 would conflate these two).
        check( naxe::evalBinaryOp( AstOp::Gt, naxe::Value::makeInt( 2000000001 ),
                                   naxe::Value::makeInt( 2000000000 ), arena ).boolValue(),
               "Int > Int exact at 2e9" );
        check( naxe::evalBinaryOp( AstOp::Eq, naxe::Value::makeInt( 2000000001 ),
                                   naxe::Value::makeInt( 2000000001 ), arena ).boolValue(),
               "Int == Int exact" );
        check( ! naxe::evalBinaryOp( AstOp::Eq, naxe::Value::makeInt( 2000000001 ),
                                     naxe::Value::makeInt( 2000000000 ), arena ).boolValue(),
               "Int != Int exact" );

        // Unary negation on Int.
        check( naxe::evalUnaryOp( AstOp::Neg, naxe::Value::makeInt( 7 ) ).intValue() == -7, "unary -Int" );
        check( naxe::evalUnaryOp( AstOp::Neg, naxe::Value::makeFloat( 2.5f ) ).floatValue() == -2.5f, "unary -Float" );
    }

    void testOperatorsStrings()
    {
        using naxe::AstOp;
        naxe::Arena arena( arenaBuffer, kArenaSize );

        naxe::Value cat = naxe::evalBinaryOp( AstOp::Add, naxe::Value::makeString( "foo" ),
                                              naxe::Value::makeString( "bar" ), arena );
        check( cat.isString() && cat.stringValue() == "foobar", "String + String concatenates" );

        naxe::Value removed = naxe::evalBinaryOp( AstOp::Sub, naxe::Value::makeString( "Caco malo" ),
                                                  naxe::Value::makeString( "o" ), arena );
        check( removed.stringValue() == "Cac mal", "String - String removes occurrences" );

        // Case-insensitive lexicographic comparison.
        check( naxe::evalBinaryOp( AstOp::Lt, naxe::Value::makeString( "abc" ),
                                   naxe::Value::makeString( "ABD" ), arena ).boolValue(),
               "String < String case-insensitive" );
        check( naxe::evalBinaryOp( AstOp::Eq, naxe::Value::makeString( "Hello" ),
                                   naxe::Value::makeString( "HELLO" ), arena ).boolValue(),
               "String == String case-insensitive" );

        // Numeric String + Int coerces (JS-wise), e.g. "10" + 2 == 12.
        naxe::Value numeric = naxe::evalBinaryOp( AstOp::Add, naxe::Value::makeString( "10" ),
                                                  naxe::Value::makeInt( 2 ), arena );
        check( numeric.isFloat() && numeric.floatValue() == 12.0f, "\"10\" + 2 == 12" );
    }

    void testOperatorsLogical()
    {
        using naxe::AstOp;
        naxe::Arena arena( arenaBuffer, kArenaSize );

        check( naxe::evalBinaryOp( AstOp::And, naxe::Value::makeInt( 1 ), naxe::Value::makeInt( 2 ), arena ).boolValue(),
               "truthy AND truthy" );
        check( ! naxe::evalBinaryOp( AstOp::And, naxe::Value::makeInt( 0 ), naxe::Value::makeInt( 2 ), arena ).boolValue(),
               "falsy AND truthy" );
        check( naxe::evalBinaryOp( AstOp::Or, naxe::Value::makeInt( 0 ), naxe::Value::makeInt( 2 ), arena ).boolValue(),
               "falsy OR truthy" );
        check( naxe::evalBinaryOp( AstOp::Xor, naxe::Value::makeInt( 0 ), naxe::Value::makeInt( 2 ), arena ).boolValue(),
               "Xor of differing truthiness" );
        check( ! naxe::evalUnaryOp( AstOp::Not, naxe::Value::makeInt( 1 ) ).boolValue(), "Not truthy -> false" );
    }

    void testResolveOperator()
    {
        naxe::AstOp op;

        check( naxe::resolveOperator( "+", 1, op ) && op == naxe::AstOp::Add, "resolve '+'" );
        check( naxe::resolveOperator( "==", 2, op ) && op == naxe::AstOp::Eq, "resolve '=='" );
        check( naxe::resolveOperator( "?=", 2, op ) && op == naxe::AstOp::Becomes, "resolve edge '?='" );
        check( ! naxe::resolveOperator( "AND", 3, op ), "English alias 'AND' not resolved here" );
        check( ! naxe::resolveOperator( "&", 1, op ), "bitwise '&' not resolved" );
    }

    void testAstEval()
    {
        naxe::Arena arena( arenaBuffer, kArenaSize );

        // Literal.
        naxe::ASTNode * lit = naxe::astLiteral( arena, naxe::Value::makeInt( 5 ) );
        naxe::Value r = naxe::eval( lit, naxe::Environment( nullptr ), arena );
        check( r.isInt() && r.intValue() == 5, "eval Literal" );

        // (10 + 5) * 2 == 30.
        naxe::ASTNode * sum = naxe::astBinary( arena, naxe::AstOp::Add,
                                               naxe::astLiteral( arena, naxe::Value::makeInt( 10 ) ),
                                               naxe::astLiteral( arena, naxe::Value::makeInt( 5 ) ) );
        naxe::ASTNode * tree = naxe::astBinary( arena, naxe::AstOp::Mul, sum,
                                                naxe::astLiteral( arena, naxe::Value::makeInt( 2 ) ) );
        naxe::Value result = naxe::eval( tree, naxe::Environment( nullptr ), arena );
        check( result.isInt() && result.intValue() == 30, "eval (10+5)*2" );

        // Variable lookup through an Environment.
        naxe::Pair * vars = naxe::Pair::create();
        vars->set( "clock", naxe::Value::makeInt( 42 ) );
        naxe::ASTNode * var = naxe::astVariable( arena, "CLOCK" ); // case-insensitive lookup
        naxe::Value got = naxe::eval( var, naxe::Environment( vars ), arena );
        check( got.isInt() && got.intValue() == 42, "eval Variable (case-insensitive)" );

        // Undefined variable -> empty String.
        naxe::ASTNode * missing = naxe::astVariable( arena, "nope" );
        naxe::Value mv = naxe::eval( missing, naxe::Environment( vars ), arena );
        check( mv.isString() && mv.stringValue().empty(), "eval undefined variable -> empty String" );

        // Function call with an unregistered name -> fail-loud sentinel.
        naxe::ASTNode * call = naxe::astCall( arena, "match", nullptr, 0 );
        naxe::Value unsupported = naxe::eval( call, naxe::Environment( nullptr ), arena );
        check( unsupported.isString() && unsupported.stringValue() == "#UNSUPPORTED_FUNCTION#",
               "unregistered function -> sentinel" );

        naxe::Pair::destroy( vars );
    }

    void testCollectVariableNames()
    {
        naxe::Arena arena( arenaBuffer, kArenaSize );

        // (a + b) * (a + c) -> distinct names {a, b, c} (dedup a).
        naxe::ASTNode * left = naxe::astBinary( arena, naxe::AstOp::Add,
                                                naxe::astVariable( arena, "a" ),
                                                naxe::astVariable( arena, "b" ) );
        naxe::ASTNode * right = naxe::astBinary( arena, naxe::AstOp::Add,
                                                 naxe::astVariable( arena, "A" ), // dup, different case
                                                 naxe::astVariable( arena, "c" ) );
        naxe::ASTNode * root = naxe::astBinary( arena, naxe::AstOp::Mul, left, right );

        constexpr uint32_t kMax = 8;
        std::string_view names[ kMax ];
        const uint32_t count = naxe::collectVariableNames( root, names, kMax );

        check( count == 3, "collectVariableNames dedups to 3" );

        bool hasA = false, hasB = false, hasC = false;
        for( uint32_t i = 0; i < count; ++i )
        {
            hasA |= naxe::util::asciiEqualsIgnoreCase( names[ i ], "a" );
            hasB |= naxe::util::asciiEqualsIgnoreCase( names[ i ], "b" );
            hasC |= naxe::util::asciiEqualsIgnoreCase( names[ i ], "c" );
        }
        check( hasA && hasB && hasC, "collectVariableNames finds a,b,c" );
    }

    void testDateTime()
    {
        naxe::initTimeZone();

        // nowEpochSeconds() magnitude is environment-dependent (real on host; 1970 on a
        // pre-SNTP device/QEMU, per datetime.hpp) — assert only that it is callable and
        // non-negative, not a specific epoch.
        const int64_t now = naxe::nowEpochSeconds();
        check( now >= 0, "nowEpochSeconds is non-negative" );

        // Time-validity flag flips once and stays.
        check( ! naxe::isTimeValid(), "time valid starts false" );
        naxe::setTimeValid( true );
        check( naxe::isTimeValid(), "time valid flips true" );

        // parseHHMM round-trips through local fields.
        int64_t epoch = 0;
        check( naxe::parseHHMM( "03:00", epoch ), "parseHHMM(03:00)" );
        naxe::LocalFields f = naxe::localFieldsFromEpoch( epoch );
        check( f.hour == 3 && f.minute == 0 && f.second == 0, "parseHHMM round-trips hour/minute" );

        check( naxe::parseHHMM( "23:59:59", epoch ), "parseHHMM(23:59:59)" );
        check( ! naxe::parseHHMM( "25:00", epoch ), "parseHHMM rejects hour>23" );
        check( ! naxe::parseHHMM( "3:00", epoch ), "parseHHMM rejects 1-digit hour" );
        check( ! naxe::parseHHMM( "03:00x", epoch ), "parseHHMM rejects trailing garbage" );

        // parseYMD round-trips.
        check( naxe::parseYMD( "2021-04-08", epoch ), "parseYMD(2021-04-08)" );
        f = naxe::localFieldsFromEpoch( epoch );
        check( f.year == 2021 && f.month == 4 && f.day == 8, "parseYMD round-trips date" );

        check( ! naxe::parseYMD( "2021-13-01", epoch ), "parseYMD rejects month 13" );
        check( ! naxe::parseYMD( "2021-1-01", epoch ), "parseYMD rejects 1-digit month" );
    }
} // namespace

namespace naxe
{
    int runEvalTests()
    {
        failures = 0;

        testCoercion();
        testOperatorsInt();
        testOperatorsStrings();
        testOperatorsLogical();
        testResolveOperator();
        testAstEval();
        testCollectVariableNames();
        testDateTime();

        if( failures == 0 )
        {
            std::printf( "[naxe] evaluator tests: PASS\n" );
        }
        else
        {
            std::printf( "[naxe] evaluator tests: %d FAILURE(S)\n", failures );
        }

        return failures;
    }
} // namespace naxe
