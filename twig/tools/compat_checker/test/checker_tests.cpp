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
/** \file checker_tests.cpp
 *
 * Host tests for the compatibility checker, exercised against embedded representative
 * `.model` text (not paraphrased) — the clean case mirrors `01.clock.model`'s real shape,
 * the negative cases each pin one deferred feature to an exact finding.
 */
#include "checker_tests.hpp"

#include <cstdio>
#include <cstring>
#include <string_view>

#include "cil/builder.hpp"
#include "compat_checker/checker.hpp"
#include "jsonview/json_view.hpp"
#include "naxe/arena.hpp"
#include "naxe/util.hpp"

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

    constexpr size_t kArenaSize = 65536;
    alignas( 8 ) uint8_t arenaBuffer[ kArenaSize ];
    constexpr unsigned kMaxTokens = 256;
    jsmntok_t tokens[ kMaxTokens ];
    constexpr size_t kMaxCommands = 32;

    /** Runs the checker over `model` text, returning the built command count + report. */
    struct Result
    {
        int built;
        compat_checker::Report report;
    };

    Result checkModelText( const char * model )
    {
        naxe::Arena arena( arenaBuffer, kArenaSize );
        arena.reset();

        jsonview::JsonView view( model, std::strlen( model ), tokens, kMaxTokens );

        cil::Command commands[ kMaxCommands ];
        const int built = cil::buildModel( view, arena, commands, static_cast<int>( kMaxCommands ) );

        compat_checker::Report report;
        compat_checker::checkModel( commands, static_cast<uint32_t>( built ), report, arena );
        return Result{ built, report };
    }

    /** @return The number of findings of `kind` in `report`. */
    int countKind( const compat_checker::Report & report, compat_checker::FindingKind kind )
    {
        int n = 0;
        for( uint32_t i = 0; i < report.count(); ++i )
        {
            if( report.at( i ).kind == kind ) ++n;
        }
        return n;
    }

    /** @return True if `report` holds a finding of `kind` whose name matches `name`. */
    bool hasFinding( const compat_checker::Report & report, compat_checker::FindingKind kind,
                     std::string_view name )
    {
        for( uint32_t i = 0; i < report.count(); ++i )
        {
            const compat_checker::Finding & f = report.at( i );
            if( f.kind == kind && naxe::util::asciiEqualsIgnoreCase( f.name, name ) ) return true;
        }
        return false;
    }

    const char * kClockModel =
        R"MODEL({"commands":[)MODEL"
        R"MODEL({"cmd":"device","name":"clock","driver":"clockdriver","config":{"interval":3000},"init":null},)MODEL"
        R"MODEL({"cmd":"device","name":"console","driver":"outputdriver","config":null,"init":null},)MODEL"
        R"MODEL({"cmd":"rule","name":null,"when":"clock","then":[{"after":-1,"target":"console","value":"clock + 1"}],"if":null},)MODEL"
        R"MODEL({"cmd":"driver","name":"clockdriver","script":"clockcontroller"},)MODEL"
        R"MODEL({"cmd":"driver","name":"outputdriver","script":"outputstreamcontroller"},)MODEL"
        R"MODEL({"cmd":"script","name":"clockcontroller","language":"java","onstart":false,"onstop":false,"prestart":false,"prestop":false,"inline":false,"from":["file://x.jar"],"call":"com.x.Clock"},)MODEL"
        R"MODEL({"cmd":"script","name":"outputstreamcontroller","language":"java","onstart":false,"onstop":false,"prestart":false,"prestop":false,"inline":false,"from":["file://x.jar"],"call":"com.x.Out"})MODEL"
        R"MODEL(]})MODEL";

    void testCleanClockModel()
    {
        const Result r = checkModelText( kClockModel );
        check( r.built == 7, "clock model builds 7 commands" );
        check( r.report.empty(), "clock model is compatible (0 findings)" );
    }

    void testUnknownDriver()
    {
        const char * model =
            R"MODEL({"commands":[)MODEL"
            R"MODEL({"cmd":"device","name":"t","driver":"mqttdriver","config":null,"init":null})MODEL"
            R"MODEL(]})MODEL";
        const Result r = checkModelText( model );
        check( countKind( r.report, compat_checker::FindingKind::UnknownDriver ) == 1,
               "unknown driver flagged once" );
        check( hasFinding( r.report, compat_checker::FindingKind::UnknownDriver, "mqttdriver" ),
               "unknown driver named mqttdriver" );
    }

    void testUnsupportedFunction()
    {
        const char * model =
            R"MODEL({"commands":[)MODEL"
            R"MODEL({"cmd":"device","name":"clock","driver":"clockdriver","config":null,"init":null},)MODEL"
            R"MODEL({"cmd":"rule","name":null,"when":"search(clock, \"x\")","then":[],"if":null})MODEL"
            R"MODEL(]})MODEL";
        const Result r = checkModelText( model );
        check( hasFinding( r.report, compat_checker::FindingKind::UnsupportedFunction, "search" ),
               "search() flagged as unsupported function" );
    }

    void testUnsupportedMethod()
    {
        const char * model =
            R"MODEL({"commands":[)MODEL"
            R"MODEL({"cmd":"device","name":"clock","driver":"clockdriver","config":null,"init":null},)MODEL"
            R"MODEL({"cmd":"rule","name":null,"when":"list(1,2):toJson()","then":[],"if":null})MODEL"
            R"MODEL(]})MODEL";
        const Result r = checkModelText( model );
        check( hasFinding( r.report, compat_checker::FindingKind::UnsupportedMethod, "toJson" ),
               "toJson() flagged as unsupported method" );
    }

    void testBitwiseOperator()
    {
        const char * model =
            R"MODEL({"commands":[)MODEL"
            R"MODEL({"cmd":"device","name":"clock","driver":"clockdriver","config":null,"init":null},)MODEL"
            R"MODEL({"cmd":"rule","name":null,"when":"clock & 3","then":[],"if":null})MODEL"
            R"MODEL(]})MODEL";
        const Result r = checkModelText( model );
        check( countKind( r.report, compat_checker::FindingKind::UnsupportedOperator ) == 1,
               "bitwise operator flagged as unsupported operator" );
    }

    void testEdgeDetectionOperator()
    {
        const char * model =
            R"MODEL({"commands":[)MODEL"
            R"MODEL({"cmd":"device","name":"clock","driver":"clockdriver","config":null,"init":null},)MODEL"
            R"MODEL({"cmd":"rule","name":null,"when":"clock ?> 5","then":[],"if":null})MODEL"
            R"MODEL(]})MODEL";
        const Result r = checkModelText( model );
        check( hasFinding( r.report, compat_checker::FindingKind::EdgeDetectionOperator, "?>" ),
               "edge-detection ?> flagged" );
    }

    void testExecutedNonUneScript()
    {
        const char * model =
            R"MODEL({"commands":[)MODEL"
            R"MODEL({"cmd":"device","name":"lamp","driver":"outputdriver","config":null,"init":null},)MODEL"
            R"MODEL({"cmd":"rule","name":null,"when":"lamp","then":[{"after":-1,"target":"myscript","value":null}],"if":null},)MODEL"
            R"MODEL({"cmd":"driver","name":"outputdriver","script":"out"},)MODEL"
            R"MODEL({"cmd":"script","name":"myscript","language":"java","onstart":false,"onstop":false,"prestart":false,"prestop":false,"inline":false,"from":["file://x.jar"],"call":"com.x.S"},)MODEL"
            R"MODEL({"cmd":"script","name":"out","language":"java","onstart":false,"onstop":false,"prestart":false,"prestop":false,"inline":false,"from":["file://x.jar"],"call":"com.x.O"})MODEL"
            R"MODEL(]})MODEL";
        const Result r = checkModelText( model );
        check( hasFinding( r.report, compat_checker::FindingKind::NonUneScript, "myscript" ),
               "executed java script flagged as non-une" );
        check( hasFinding( r.report, compat_checker::FindingKind::FileBasedScript, "myscript" ),
               "executed non-inline script flagged as file-based" );
        // The controller script "out" (only a DRIVER's script field) is exempt.
        check( ! hasFinding( r.report, compat_checker::FindingKind::NonUneScript, "out" ),
               "controller script 'out' is exempt from the non-une flag" );
    }

    void testUnsupportedAction()
    {
        const char * model =
            R"MODEL({"commands":[)MODEL"
            R"MODEL({"cmd":"device","name":"lamp","driver":"outputdriver","config":null,"init":null},)MODEL"
            R"MODEL({"cmd":"rule","name":null,"when":"lamp","then":[{"after":-1,"target":"enable(\"lamp\")","value":null}],"if":null})MODEL"
            R"MODEL(]})MODEL";
        const Result r = checkModelText( model );
        check( countKind( r.report, compat_checker::FindingKind::UnsupportedAction ) == 1,
               "expression action target flagged as unsupported" );
    }

    void testParseError()
    {
        const char * model =
            R"MODEL({"commands":[)MODEL"
            R"MODEL({"cmd":"device","name":"clock","driver":"clockdriver","config":null,"init":null},)MODEL"
            R"MODEL({"cmd":"rule","name":null,"when":"clock + \"unclosed","then":[],"if":null})MODEL"
            R"MODEL(]})MODEL";
        const Result r = checkModelText( model );
        check( countKind( r.report, compat_checker::FindingKind::ParseError ) == 1,
               "unparseable WHEN flagged as parse error" );
    }
} // namespace

namespace compat_checker
{
    int runCheckerTests()
    {
        failures = 0;

        testCleanClockModel();
        testUnknownDriver();
        testUnsupportedFunction();
        testUnsupportedMethod();
        testBitwiseOperator();
        testEdgeDetectionOperator();
        testExecutedNonUneScript();
        testUnsupportedAction();
        testParseError();

        if( failures == 0 )
        {
            std::printf( "[compat_checker] checker tests: PASS\n" );
        }
        else
        {
            std::printf( "[compat_checker] checker tests: %d FAILURE(S)\n", failures );
        }

        return failures;
    }
} // namespace compat_checker
