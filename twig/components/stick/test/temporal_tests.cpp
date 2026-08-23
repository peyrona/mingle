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
/** \file temporal_tests.cpp
 *
 * Host/on-device tests for AFTER/WITHIN (the temporal state machine). The window state is
 * driven by hand-advancing `nowMs` across synthetic `Rule::evaluate()` calls on the SAME
 * rule (no real timer needed), matching the "abstract schedule callback" design.
 */
#include "stick/temporal_tests.hpp"

#include <cstdio>
#include <string_view>

#include "cil/model.hpp"
#include "naxe/arena.hpp"
#include "naxe/pair.hpp"
#include "naxe/value.hpp"
#include "stick/action.hpp"
#include "stick/rule.hpp"

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
    alignas( 8 ) uint8_t cycleBuffer[ kArenaSize ];

    struct Recorder
    {
        int count = 0;
    };

    void recordAction( void * ctx, const stick::ActionResult & )
    {
        static_cast<Recorder *>( ctx )->count++;
    }

    /** A rule-under-test with a persistent window state, driven by repeated `step` calls. */
    struct Scenario
    {
        naxe::Arena arena{ arenaBuffer, kArenaSize };
        naxe::Arena cycle{ cycleBuffer, kArenaSize };
        cil::RuleAction action{};
        cil::Rule parsed{};
        stick::Rule rule;
        naxe::Pair * env;
        Recorder rec;

        explicit Scenario( const char * whenText )
        {
            parsed.hasName = false;
            parsed.when = whenText;
            parsed.hasIf = false;
            parsed.ifExpr = std::string_view();

            action.after = -1;
            action.target = "console";
            action.hasValue = true;
            action.value = naxe::Value::makeInt( 1 );

            parsed.actions = &action;
            parsed.actionCount = 1;

            rule = stick::Rule( parsed, arena );
            env = naxe::Pair::create();
        }

        ~Scenario()
        {
            naxe::Pair::destroy( env );
        }

        /** Sets the clock value and evaluates the rule at nowMs; returns fired-action count. */
        int step( int64_t clockValue, int64_t nowMs )
        {
            env->set( "clock", naxe::Value::makeInt( clockValue ) );
            rec.count = 0;
            rule.evaluate( naxe::Environment( env ), cycle, recordAction, &rec, nowMs );
            return rec.count;
        }
    };

    void testAfter()
    {
        // AFTER: one-shot delayed sample — the condition is evaluated only at expiry.
        {
            Scenario s( "clock == 1 AFTER 100" );
            check( s.rule.isValid(), "AFTER rule parses" );
            check( s.step( 1, 0 ) == 0, "AFTER arms without firing" );
            check( s.step( 1, 150 ) == 1, "AFTER fires at expiry" );
        }

        // The condition is NOT sampled at arm time: false at arm, true at expiry -> fires.
        {
            Scenario s( "clock == 1 AFTER 100" );
            check( s.step( 2, 0 ) == 0, "AFTER ignores arm-time value" );
            check( s.step( 1, 150 ) == 1, "AFTER samples at expiry only" );
        }

        // True at arm, false at expiry -> does NOT fire.
        {
            Scenario s( "clock == 1 AFTER 100" );
            check( s.step( 1, 0 ) == 0, "AFTER arm (true now)" );
            check( s.step( 0, 150 ) == 0, "AFTER no fire (false at expiry)" );
        }
    }

    void testWithin()
    {
        // WITHIN: true only if the condition stays unchanged for the whole window.
        {
            Scenario s( "clock == 1 WITHIN 100" );
            check( s.rule.isValid(), "WITHIN rule parses" );
            check( s.step( 1, 0 ) == 0, "WITHIN arms" );
            check( s.step( 1, 150 ) == 1, "WITHIN fires after stable window" );
        }

        // Already false at arm -> resolves false instantly.
        {
            Scenario s( "clock == 1 WITHIN 100" );
            check( s.step( 0, 0 ) == 0, "WITHIN false at arm" );
        }

        // Change mid-window -> resolves false before the deadline.
        {
            Scenario s( "clock == 1 WITHIN 100" );
            check( s.step( 1, 0 ) == 0, "WITHIN mid-window: arms" );
            check( s.step( 2, 50 ) == 0, "WITHIN resolves false on change" );
        }
    }
} // namespace

namespace stick
{
    int runTemporalTests()
    {
        failures = 0;

        testAfter();
        testWithin();

        if( failures == 0 )
        {
            std::printf( "[stick] temporal tests: PASS\n" );
        }
        else
        {
            std::printf( "[stick] temporal tests: %d FAILURE(S)\n", failures );
        }

        return failures;
    }
} // namespace stick
