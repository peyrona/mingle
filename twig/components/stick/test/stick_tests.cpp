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
/** \file stick_tests.cpp
 *
 * Host/on-device tests for the stick core (Phase 7): Message, Device/DeviceManager,
 * DriverManager, Rule/RuleManager (with WHEN/THEN evaluation), and Script/ScriptManager.
 * Exercises an end-to-end "load a small model, set a device value, evaluate a rule"
 * scenario without the EventBus (which is FreeRTOS-dependent and comes next).
 */
#include "stick/stick_tests.hpp"

#include <cstdio>
#include <cstring>
#include <string_view>

#include "cil/builder.hpp"
#include "cil/model.hpp"
#include "jsonview/json_view.hpp"
#include "naxe/arena.hpp"
#include "naxe/pair.hpp"
#include "stick/action.hpp"
#include "stick/device.hpp"
#include "stick/device_manager.hpp"
#include "stick/driver_manager.hpp"
#include "stick/message.hpp"
#include "stick/rule.hpp"
#include "stick/rule_manager.hpp"
#include "stick/script.hpp"
#include "stick/script_manager.hpp"

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

    constexpr size_t kArenaSize = 16384;
    alignas( 8 ) uint8_t arenaBuffer[ kArenaSize ];

    constexpr unsigned kMaxTokens = 256;
    jsmntok_t tokens[ kMaxTokens ];

    void testMessage()
    {
        naxe::Value v = naxe::Value::makeInt( 42 );
        stick::Message m = stick::makeDeviceChanged( "clock", v, true );
        check( m.type == stick::MsgType::DeviceChanged, "makeDeviceChanged type" );
        check( m.name == "clock", "makeDeviceChanged name" );
        check( m.hasValue && m.value.isInt() && m.value.intValue() == 42, "makeDeviceChanged value" );

        stick::Message r = stick::makeReadDevice( "clock" );
        check( r.type == stick::MsgType::ReadDevice && ! r.hasValue, "makeReadDevice hasValue false" );

        stick::Message e = stick::makeExecute( "myrule", true );
        check( e.type == stick::MsgType::Execute && e.value.boolValue(), "makeExecute force" );
    }

    void testDevice()
    {
        // Build a cil::Device with init {delta: 5, groups: "a, b"}.
        const char json[] =
            R"({"cmd":"device","name":"sensor","driver":"d","config":null,"init":{"delta":5,"groups":"a, b"}})";
        jsonview::JsonView view( json, std::strlen( json ), tokens, kMaxTokens );
        naxe::Arena arena( arenaBuffer, kArenaSize );
        cil::Command cmd;
        check( cil::buildCommand( view, view.root(), arena, cmd ), "build device command" );

        stick::Device d( cmd.device );
        check( d.name() == "sensor", "device name" );
        check( d.driverName() == "d", "device driver" );
        check( ! d.hasValue(), "device has no value initially" );

        // First value always accepted.
        check( d.setValue( naxe::Value::makeFloat( 10.0f ) ), "first value accepted" );
        check( d.hasValue() && d.value().floatValue() == 10.0f, "value stored" );

        // Delta of 5: a change of 2 is suppressed, 6 is accepted.
        check( ! d.setValue( naxe::Value::makeFloat( 12.0f ) ), "delta suppresses small change" );
        check( d.setValue( naxe::Value::makeFloat( 20.0f ) ), "delta accepts large change" );

        // Groups.
        check( d.isMemberOfGroup( "A" ), "group match case-insensitive" );
        check( ! d.isMemberOfGroup( "c" ), "no false group match" );
    }

    void testDeviceManager()
    {
        stick::DeviceManager dm;
        check( dm.count() == 0, "empty device manager" );

        const char json[] =
            R"({"cmd":"device","name":"clock","driver":"d","config":null,"init":null})";
        jsonview::JsonView view( json, std::strlen( json ), tokens, kMaxTokens );
        naxe::Arena arena( arenaBuffer, kArenaSize );
        cil::Command cmd;
        check( cil::buildCommand( view, view.root(), arena, cmd ), "build device" );
        check( dm.add( cmd.device ), "add device" );
        check( dm.count() == 1, "count after add" );
        check( dm.named( "CLOCK" ) != nullptr, "named case-insensitive" );
        check( ! dm.add( cmd.device ), "duplicate device rejected" );
        check( dm.remove( "clock" ), "remove device" );
        check( dm.count() == 0, "count after remove" );
    }

    void testDriverManager()
    {
        stick::DriverManager dm;
        const char json[] =
            R"({"cmd":"driver","name":"clockdriver","script":"clockcontroller"})";
        jsonview::JsonView view( json, std::strlen( json ), tokens, kMaxTokens );
        naxe::Arena arena( arenaBuffer, kArenaSize );
        cil::Command cmd;
        check( cil::buildCommand( view, view.root(), arena, cmd ), "build driver" );
        check( dm.add( cmd.driver ), "add driver" );
        check( dm.named( "clockdriver" ) != nullptr, "driver named" );
        check( ! dm.named( "clockdriver" )->hasOps, "driver has no ops yet" );

        stick::DriverOps ops{};
        check( dm.registerOps( "CLOCKDRIVER", ops ), "registerOps case-insensitive" );
        check( dm.named( "clockdriver" )->hasOps, "driver ops registered" );
    }

    struct Recorder
    {
        int count = 0;
        stick::ActionResult results[ 8 ];
    };

    void recordAction( void * ctx, const stick::ActionResult & r )
    {
        Recorder * rec = static_cast<Recorder *>( ctx );
        if( rec->count < 8 )
        {
            rec->results[ rec->count++ ] = r;
        }
    }

    /** Builds an Environment (device name -> value) from a DeviceManager. */
    naxe::Pair * buildEnvironment( stick::DeviceManager & dm )
    {
        naxe::Pair * env = naxe::Pair::create( dm.count() );
        for( uint32_t i = 0; i < dm.count(); ++i )
        {
            stick::Device & d = dm.at( i );
            if( d.hasValue() )
            {
                env->set( d.name(), d.value() );
            }
        }
        return env;
    }

    void testRuleEvaluation()
    {
        // A small model: clock device + console device + WHEN clock THEN console = "..." + clock.
        const char * model =
            R"({"commands":[)"
            R"({"cmd":"device","name":"clock","driver":"d1","config":null,"init":null},)"
            R"({"cmd":"device","name":"console","driver":"d2","config":null,"init":null},)"
            R"({"cmd":"rule","name":null,"when":"clock","then":[{"after":-1,"target":"console","value":"\"'clock' value is: \" + clock"}],"if":null})"
            R"(]})";

        jsonview::JsonView view( model, std::strlen( model ), tokens, kMaxTokens );
        naxe::Arena arena( arenaBuffer, kArenaSize );
        cil::Command commands[ 4 ];
        const int built = cil::buildModel( view, arena, commands, 4 );
        check( built == 3, "model builds 3 commands" );

        stick::DeviceManager dm;
        stick::RuleManager rm;

        for( int i = 0; i < built; ++i )
        {
            if( commands[ i ].type == cil::CommandType::Device )
            {
                dm.add( commands[ i ].device );
            }
            else if( commands[ i ].type == cil::CommandType::Rule )
            {
                rm.add( commands[ i ].rule, arena );
            }
        }

        check( dm.count() == 2 && rm.count() == 1, "managers populated" );

        // Set the clock value and evaluate the rule.
        stick::Device * clock = dm.named( "clock" );
        clock->setValue( naxe::Value::makeInt( 42 ) );

        naxe::Pair * env = buildEnvironment( dm );
        naxe::Arena cycle( arenaBuffer, kArenaSize );
        Recorder rec;
        const bool triggered = rm.at( 0 ).evaluate( naxe::Environment( env ), cycle, recordAction, &rec );

        check( triggered, "rule triggers" );
        check( rec.count == 1, "one action fired" );
        check( rec.results[ 0 ].kind == stick::ActionKind::ChangeActuator, "action is ChangeActuator" );
        check( rec.results[ 0 ].target == "console", "action target console" );
        check( rec.results[ 0 ].value.isString()
               && rec.results[ 0 ].value.stringValue() == "'clock' value is: 42",
               "action value is the formatted string" );

        // referencesDevice: the rule references "clock" but not "console".
        check( rm.at( 0 ).referencesDevice( "clock" ), "rule references clock" );
        check( ! rm.at( 0 ).referencesDevice( "console" ), "rule does not reference console" );

        naxe::Pair::destroy( env );
    }

    void testScript()
    {
        const char * model =
            R"({"commands":[)"
            R"({"cmd":"script","name":"init","language":"une","onstart":true,"onstop":false,"prestart":false,"prestop":false,"inline":true,"from":["a = 1\nb = \"hello\""],"call":""})"
            R"(]})";

        jsonview::JsonView view( model, std::strlen( model ), tokens, kMaxTokens );
        naxe::Arena arena( arenaBuffer, kArenaSize );
        cil::Command commands[ 2 ];
        const int built = cil::buildModel( view, arena, commands, 2 );
        check( built == 1, "script model builds" );

        stick::ScriptManager sm;
        check( sm.add( commands[ 0 ].script, arena ), "add script" );
        check( sm.count() == 1, "one script" );
        check( sm.at( 0 ).isValid(), "script valid" );
        check( sm.at( 0 ).isOnStart(), "script onstart" );
        check( sm.named( "INIT" ) != nullptr, "script named case-insensitive" );

        // Execute the script: two ChangeActuator actions (a=1, b="hello").
        naxe::Arena cycle( arenaBuffer, kArenaSize );
        Recorder rec;
        sm.at( 0 ).execute( naxe::Environment( nullptr ), cycle, recordAction, &rec );
        check( rec.count == 2, "script executes 2 statements" );
        check( rec.results[ 0 ].target == "a" && rec.results[ 0 ].value.isInt() && rec.results[ 0 ].value.intValue() == 1,
               "statement 1: a = 1" );
        check( rec.results[ 1 ].target == "b" && rec.results[ 1 ].value.isString() && rec.results[ 1 ].value.stringValue() == "hello",
               "statement 2: b = hello" );
    }
} // namespace

namespace stick
{
    int runStickTests()
    {
        failures = 0;

        testMessage();
        testDevice();
        testDeviceManager();
        testDriverManager();
        testRuleEvaluation();
        testScript();

        if( failures == 0 )
        {
            std::printf( "[stick] core tests: PASS\n" );
        }
        else
        {
            std::printf( "[stick] core tests: %d FAILURE(S)\n", failures );
        }

        return failures;
    }
} // namespace stick
