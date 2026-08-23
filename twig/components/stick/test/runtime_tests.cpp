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
/** \file runtime_tests.cpp
 *
 * On-device test for the runtime (Phase 8): a real EventBus + Orchestrator executing a
 * small model end-to-end — post a DeviceChanged, the bus dispatches it, the orchestrator
 * updates the device and re-evaluates the rule, and the rule's action writes to the
 * console driver.
 */
#include "stick/runtime_tests.hpp"

#include <cstdio>
#include <cstring>
#include <string_view>

#include "freertos/FreeRTOS.h"
#include "freertos/task.h"

#include "cil/builder.hpp"
#include "cil/model.hpp"
#include "jsonview/json_view.hpp"
#include "naxe/arena.hpp"
#include "stick/device_manager.hpp"
#include "stick/driver_manager.hpp"
#include "stick/event_bus.hpp"
#include "stick/message.hpp"
#include "stick/orchestrator.hpp"
#include "stick/rule_manager.hpp"
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

    constexpr size_t kModelArenaSize = 8192;
    constexpr size_t kCycleArenaSize = 8192;
    alignas( 8 ) uint8_t modelArenaBuf[ kModelArenaSize ];
    alignas( 8 ) uint8_t cycleArenaBuf[ kCycleArenaSize ];

    constexpr unsigned kMaxTokens = 256;
    jsmntok_t tokens[ kMaxTokens ];

    // The managers are large (fixed-capacity arrays) — they live in BSS, not on the
    // main-task stack (which is small and shared with the test-runner's deep call chains).
    stick::DeviceManager g_devices;
    stick::RuleManager g_rules;
    stick::ScriptManager g_scripts;
    stick::DriverManager g_drivers;

    /** Records writes the console driver receives, for the test assertions. */
    struct Recorder
    {
        int count = 0;
        naxe::Value lastValue;
    };

    Recorder g_recorder;

    void consoleWrite( void * ctx, std::string_view, const naxe::Value & value )
    {
        Recorder * r = static_cast<Recorder *>( ctx );
        r->lastValue = value;
        ++r->count;
        std::printf( "[demo] console <- %.*s\n",
                     value.isString() ? static_cast<int>( value.stringValue().size() ) : 0,
                     value.isString() ? value.stringValue().data() : "" );
    }

    void consoleRead( void *, std::string_view )
    {
    }
} // namespace

namespace stick
{
    int runRuntimeTests()
    {
        failures = 0;

        // Model: clock device + console device + WHEN clock THEN console = "..." + clock.
        const char * model =
            R"({"commands":[)"
            R"({"cmd":"device","name":"clock","driver":"clockdriver","config":null,"init":null},)"
            R"({"cmd":"device","name":"console","driver":"outputdriver","config":null,"init":null},)"
            R"({"cmd":"rule","name":null,"when":"clock","then":[{"after":-1,"target":"console","value":"\"'clock' value is: \" + clock"}],"if":null},)"
            R"({"cmd":"driver","name":"clockdriver","script":""},)"
            R"({"cmd":"driver","name":"outputdriver","script":""})"
            R"(]})";

        jsonview::JsonView view( model, std::strlen( model ), tokens, kMaxTokens );
        naxe::Arena modelArena( modelArenaBuf, kModelArenaSize );
        cil::Command commands[ 8 ];
        const int built = cil::buildModel( view, modelArena, commands, 8 );
        check( built == 5, "runtime model builds 5 commands" );

        stick::DeviceManager & devices = g_devices;
        stick::RuleManager & rules = g_rules;
        stick::ScriptManager & scripts = g_scripts;
        stick::DriverManager & drivers = g_drivers;
        stick::EventBus bus( 16 );
        stick::Orchestrator orch( devices, rules, scripts, drivers, bus, modelArena,
                                  cycleArenaBuf, kCycleArenaSize );

        orch.boot( commands, static_cast<uint32_t>( built ) );
        check( devices.count() == 2, "devices registered" );
        check( rules.count() == 1, "rule registered" );
        check( drivers.count() == 2, "drivers registered" );

        // Register the console driver's write op (recording) and the clock's read op.
        const stick::DriverOps consoleOps{ &consoleWrite, &consoleRead, &g_recorder };
        check( drivers.registerOps( "outputdriver", consoleOps ), "console ops registered" );

        orch.attach();
        bus.start();

        // Post a device change and let the consumer dispatch it.
        bus.post( makeDeviceChanged( "clock", naxe::Value::makeInt( 42 ) ) );
        vTaskDelay( pdMS_TO_TICKS( 500 ) );

        check( g_recorder.count == 1, "console driver received one write" );
        check( g_recorder.lastValue.isString()
               && g_recorder.lastValue.stringValue() == "'clock' value is: 42",
               "rule fired with the formatted value" );

        // A second change re-fires (level-triggered rule evaluates on every change).
        bus.post( makeDeviceChanged( "clock", naxe::Value::makeInt( 99 ) ) );
        vTaskDelay( pdMS_TO_TICKS( 500 ) );

        check( g_recorder.count == 2, "console driver received a second write" );
        check( g_recorder.lastValue.stringValue() == "'clock' value is: 99",
               "second value formatted correctly" );

        if( failures == 0 )
        {
            std::printf( "[stick] runtime tests: PASS\n" );
        }
        else
        {
            std::printf( "[stick] runtime tests: %d FAILURE(S)\n", failures );
        }

        return failures;
    }
} // namespace stick
