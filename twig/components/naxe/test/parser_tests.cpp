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
/** \file parser_tests.cpp
 *
 * Host/on-device tests for the Une text parser (parseExpression): tokenization, unary/
 * function classification, precedence, integer-vs-float literals, string/boolean
 * literals, method chaining, AFTER/WITHIN, ANY/ALL/NONE expansion, and error cases.
 */
#include "naxe/parser_tests.hpp"

#include <cstdio>
#include <string_view>

#include "naxe/arena.hpp"
#include "naxe/ast.hpp"
#include "naxe/datetime.hpp"
#include "naxe/parser.hpp"
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

    /** Parses `text` and evaluates it against `env`, writing the result into `out`.
     *
     * @return False if parsing failed (and `out` is left untouched).
     */
    bool parseAndEval( std::string_view text, const naxe::Pair * env, naxe::Value & out )
    {
        naxe::Arena arena( arenaBuffer, kArenaSize );
        naxe::ParseError err;
        naxe::ASTNode * node = naxe::parseExpression( arena, text, err );
        if( node == nullptr )
        {
            return false;
        }
        out = naxe::eval( node, naxe::Environment( env ), arena );
        return true;
    }

    void testArithmetic()
    {
        naxe::Value v;

        check( parseAndEval( "1 + 2", nullptr, v ) && v.isInt() && v.intValue() == 3, "1 + 2 == 3 (Int)" );
        check( parseAndEval( "1 + 2 * 3", nullptr, v ) && v.intValue() == 7, "1 + 2 * 3 == 7 (precedence)" );
        check( parseAndEval( "(1 + 2) * 3", nullptr, v ) && v.intValue() == 9, "(1+2)*3 == 9" );
        check( parseAndEval( "7 / 2", nullptr, v ) && v.isFloat() && v.floatValue() == 3.5f, "7 / 2 == 3.5 (float div)" );
        check( parseAndEval( "2 ^ 3", nullptr, v ) && v.isFloat() && v.floatValue() == 8.0f, "2 ^ 3 == 8" );

        // Integer literal -> Int; fractional -> Float.
        check( parseAndEval( "5", nullptr, v ) && v.isInt() && v.intValue() == 5, "integer literal -> Int" );
        check( parseAndEval( "5.5", nullptr, v ) && v.isFloat() && v.floatValue() == 5.5f, "fractional literal -> Float" );

        // Unary minus.
        check( parseAndEval( "-5", nullptr, v ) && v.isInt() && v.intValue() == -5, "unary -5 -> Int" );
        check( parseAndEval( "-(2 + 3)", nullptr, v ) && v.isInt() && v.intValue() == -5, "-(2+3) -> -5" );
    }

    void testComparisonAndLogic()
    {
        naxe::Value v;

        check( parseAndEval( "5 > 3", nullptr, v ) && v.isBool() && v.boolValue(), "5 > 3 true" );
        check( parseAndEval( "5 == 5", nullptr, v ) && v.boolValue(), "5 == 5 true" );
        check( parseAndEval( "5 != 6", nullptr, v ) && v.boolValue(), "5 != 6 true" );
        check( parseAndEval( "1 && 0", nullptr, v ) && v.isBool() && ! v.boolValue(), "1 && 0 false" );
        check( parseAndEval( "0 || 1", nullptr, v ) && v.boolValue(), "0 || 1 true" );
        check( parseAndEval( "!1", nullptr, v ) && ! v.boolValue(), "!1 false" );
    }

    void testLiteralsAndVariables()
    {
        naxe::Value v;

        // Boolean literals.
        check( parseAndEval( "true", nullptr, v ) && v.isBool() && v.boolValue(), "true literal" );
        check( parseAndEval( "false", nullptr, v ) && v.isBool() && ! v.boolValue(), "false literal" );

        // String literal (JSON-escaped Une source, then unescaped, then lexed).
        check( parseAndEval( R"(\"hello\")", nullptr, v ) && v.isString() && v.stringValue() == "hello",
               "string literal round-trips" );

        // Variable lookup through an environment.
        naxe::Pair * env = naxe::Pair::create();
        env->set( "clock", naxe::Value::makeInt( 42 ) );
        check( parseAndEval( "clock", env, v ) && v.isInt() && v.intValue() == 42, "variable lookup" );
        check( parseAndEval( "clock > 40", env, v ) && v.boolValue(), "variable comparison" );
        naxe::Pair::destroy( env );
    }

    void testFunctionsAndMethods()
    {
        naxe::Value v;

        // Function call.
        check( parseAndEval( "abs(-5)", nullptr, v ) && v.isInt() && v.intValue() == 5, "abs(-5) via parser" );

        // Method chaining: list(1,2,3):size() -> 3.
        check( parseAndEval( "list(1, 2, 3):size()", nullptr, v ) && v.isInt() && v.intValue() == 3,
               "list(1,2,3):size() == 3" );

        // Chained global function on a number: 5:mod(2) -> mod(5,2).
        check( parseAndEval( "5:mod(2)", nullptr, v ) && v.isFloat() && v.floatValue() == 1.0f,
               "5:mod(2) == 1" );
    }

    void testTemporalAndEdge()
    {
        // AFTER/WITHIN parse to a binary node (eval without a hook -> false).
        naxe::Arena arena( arenaBuffer, kArenaSize );
        naxe::ParseError err;
        naxe::ASTNode * node = naxe::parseExpression( arena, "a == 1 AFTER 100", err );
        check( node != nullptr && ! err.hasError, "AFTER parses" );
        naxe::Value v = naxe::eval( node, naxe::Environment( nullptr ), arena );
        check( v.isBool() && ! v.boolValue(), "AFTER without hook -> false" );

        // Edge operator parses (recognized but not evaluated).
        node = naxe::parseExpression( arena, "x ?= 5", err );
        check( node != nullptr, "edge operator ?= parses" );
    }

    /** Resolves the group "sensors" to {s1, s2} for the quantifier test. */
    uint32_t testGroupResolver( void *, std::string_view group, std::string_view * out, uint32_t outCapacity )
    {
        static const std::string_view members[] = { "s1", "s2" };
        if( group != "sensors" )
        {
            return 0;
        }
        const uint32_t n = ( outCapacity < 2 ) ? outCapacity : 2;
        for( uint32_t i = 0; i < n; ++i ) out[ i ] = members[ i ];
        return n;
    }

    void testQuantifiers()
    {
        naxe::Arena arena( arenaBuffer, kArenaSize );
        naxe::ParseError err;

        // ANY sensors > 5 -> ((s1 > 5) || (s2 > 5)).
        naxe::ASTNode * node = naxe::parseExpression( arena, "ANY sensors > 5", err, testGroupResolver, nullptr );
        check( node != nullptr && ! err.hasError, "ANY quantifier expands" );

        naxe::Pair * env = naxe::Pair::create();
        env->set( "s1", naxe::Value::makeInt( 3 ) );
        env->set( "s2", naxe::Value::makeInt( 10 ) );
        naxe::Value v = naxe::eval( node, naxe::Environment( env ), arena );
        check( v.boolValue(), "ANY sensors > 5 true (s2 matches)" );
        naxe::Pair::destroy( env );

        // Missing resolver -> clear error.
        naxe::ASTNode * bad = naxe::parseExpression( arena, "ANY grp > 5", err );
        check( bad == nullptr && err.hasError, "ANY without resolver fails" );
    }

    void testErrors()
    {
        naxe::Arena arena( arenaBuffer, kArenaSize );
        naxe::ParseError err;

        check( naxe::parseExpression( arena, "1 +", err ) == nullptr && err.hasError, "trailing operator fails" );
        check( naxe::parseExpression( arena, "(1 + 2", err ) == nullptr && err.hasError, "mismatched parens fails" );
        check( naxe::parseExpression( arena, "1 @ 2", err ) == nullptr && err.hasError, "unrecognized char fails" );
        check( naxe::parseExpression( arena, "1 & 2", err ) == nullptr && err.hasError, "bitwise & rejected" );
    }

    void testJsonUnescape()
    {
        naxe::Arena arena( arenaBuffer, kArenaSize );

        const std::string_view unescaped = naxe::jsonUnescape( arena, R"(\"hi\")" );
        check( unescaped == "\"hi\"", "jsonUnescape turns \\\" into \"" );
    }
} // namespace

namespace naxe
{
    int runParserTests()
    {
        failures = 0;

        testArithmetic();
        testComparisonAndLogic();
        testLiteralsAndVariables();
        testFunctionsAndMethods();
        testTemporalAndEdge();
        testQuantifiers();
        testErrors();
        testJsonUnescape();

        if( failures == 0 )
        {
            std::printf( "[naxe] parser tests: PASS\n" );
        }
        else
        {
            std::printf( "[naxe] parser tests: %d FAILURE(S)\n", failures );
        }

        return failures;
    }
} // namespace naxe
