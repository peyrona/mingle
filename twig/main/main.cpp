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
/** \file main.cpp
 *
 * Phase 1: toolchain + project skeleton + vendored ETL + build-flag verification.
 *
 * Proves four things before any Mingle logic exists:
 *
 *  1. The ESP32-S3 image builds with this project's toolchain/flags (C++20,
 *     `-fno-exceptions`, `-fno-rtti`, `-Os`) and boots to `app_main()`.
 *  2. The vendored ETL compiles and works: an owning `etl::string<N>`, a fixed
 *     `etl::array<T,N>`, and `std::string_view` (the project's zero-copy span type).
 *  3. The memory contract the whole design rests on: `std::string_view` is trivially
 *     copyable (so it can cross a FreeRTOS `xQueue` by `memcpy`), while `etl::string`
 *     is NOT — see the note below. This is verified at compile time, not guessed.
 *  4. RAM/PSRAM totals are reported, matching twig/'s Phase 0 probe.
 *
 * See the project PLAN.md and twig/IMPLEMENTATION.md for the design these checks
 * underpin.
 */
#include <cstdio>
#include <cstring>
#include <string_view>
#include <type_traits>

#include "freertos/FreeRTOS.h"
#include "freertos/task.h"
#include "esp_heap_caps.h"
#include "sdkconfig.h"
#include "etl/array.h"
#include "etl/string.h"
#include "model_data.hpp"
#include "cil/builder.hpp"
#include "cil/model.hpp"
#include "drivers/clock_driver.hpp"
#include "drivers/console_driver.hpp"
#include "drivers/registry.hpp"
#include "jsonview/json_view.hpp"
#include "naxe/arena.hpp"
#include "naxe/datetime.hpp"
#include "naxe/pair.hpp"
#include "pal/time_sync.hpp"
#include "stick/control_channel.hpp"
#include "stick/device_manager.hpp"
#include "stick/driver_manager.hpp"
#include "stick/event_bus.hpp"
#include "stick/orchestrator.hpp"
#include "stick/rule_manager.hpp"
#include "stick/script_manager.hpp"

// On-device self-test suite (CONFIG_TWIG_ONDEVICE_SELFTEST, see main/Kconfig.projbuild):
// each header below pulls in a test file with its own static buffers, sized the same as
// -- and separate from -- runAutonomousDemo()'s own, so a deployment image can opt out of
// the RAM cost. See naxe/CMakeLists.txt's note for the full rationale.
#ifdef CONFIG_TWIG_ONDEVICE_SELFTEST
#include "cil/cil_tests.hpp"
#include "naxe/cron_tests.hpp"
#include "naxe/eval_tests.hpp"
#include "naxe/functions_tests.hpp"
#include "naxe/parser_tests.hpp"
#include "naxe/value_model_tests.hpp"
#include "stick/runtime_tests.hpp"
#include "stick/stick_tests.hpp"
#include "stick/temporal_tests.hpp"
#endif

namespace
{
    /** The project's zero-copy span type (replaces raw `const char*` + `size_t`). */
    using StringView = std::string_view;

    // ---- Compile-time memory contract (PLAN.md "Memory model" / EventBus design) ----
    //
    // A FreeRTOS queue copies items by `memcpy`, so any type embedded in a queued
    // message must be trivially copyable (its value is fully captured by its bytes: no
    // vtable, no internal pointers, no external resources). std::string_view qualifies
    // (it is just a pointer + length). etl::string deliberately does NOT: it holds an
    // internal T* p_buffer pointing at its own inline storage, so a raw memcpy would
    // leave that pointer aimed at the object's previous address — and its `string_base`
    // base declares a user-provided destructor, so it is not trivially destructible
    // either (verified at compile time below, not assumed). The consequence is a
    // load-bearing design rule: *views* (std::string_view) cross the queue inside
    // Message/Value; *owning* strings (etl::string) never do — they live in stable
    // storage (name pool, log ring, parse errors) that outlives any single message.

    static_assert( std::is_trivially_copyable_v<StringView>,
                   "std::string_view must be trivially copyable to cross an xQueue" );
    static_assert( !std::is_trivially_copyable_v<etl::string<16>>,
                   "etl::string is expected to be non-trivial (internal p_buffer) — it must not cross an xQueue" );
    static_assert( std::is_trivially_copyable_v<etl::array<int, 4>>,
                   "etl::array over trivial elements must be trivially copyable" );

    /** Runs the Phase 1 ETL smoke checks and reports PASS/FAIL over serial.
     *
     * @return 0 on success, non-zero failure count otherwise.
     */
    int runEtlSmokeTest()
    {
        int failures = 0;

        // Owning fixed-capacity string: append + access.
        etl::string<32> greeting;
        greeting = "etl::string works: ";
        greeting += "hello";
        if( greeting != "etl::string works: hello" ) ++failures;

        // Fixed array: sized, filled, and read back.
        etl::array<int, 4> numbers = { 1, 2, 3, 4 };
        int sum = 0;
        for( auto n : numbers ) sum += n;
        if( sum != 10 ) ++failures;

        // Zero-copy span over a string literal.
        const StringView tag = "twig-oop";
        if( tag != "twig-oop" ) ++failures;

        std::printf( "[twig-oop] etl smoke test: %s\n", failures == 0 ? "PASS" : "FAIL" );
        return failures;
    }

    // ---- Autonomous demo (Phase 9): run a real .model with a periodic clock driver ----

    // Control-channel peer (PLAN.md: "single source of truth for config... compile-time
    // constants" decision). Placeholder -- point this at a real GridManager-equivalent
    // host before deploying. reconnect-with-backoff (control_channel.hpp) handles an
    // unreachable/refused peer gracefully in the meantime, so an unset/wrong host here
    // does not block the rest of the demo.
    constexpr const char * kControlHost = "127.0.0.1";
    constexpr uint16_t kControlPort = 8080;

    /** Starts the node running `01.clock.model`-shaped rules autonomously: the clock
     *  driver posts the current time every `interval` ms, the rule fires, and the console
     *  driver prints the formatted value. Runs forever (the timer + EventBus task keep it
     *  alive independently of app_main). */
    void runAutonomousDemo()
    {
        const char * model = app::kModelJson;

        // Live in BSS, not the (small, shared) main-task stack — the managers are large
        // fixed-capacity arrays.
        alignas( 8 ) static uint8_t modelArenaBuf[ 8192 ];
        alignas( 8 ) static uint8_t cycleArenaBuf[ 8192 ];
        static jsmntok_t tokens[ 256 ];
        static naxe::Arena modelArena( modelArenaBuf, sizeof( modelArenaBuf ) );
        static stick::DeviceManager devices;
        static stick::RuleManager rules;
        static stick::ScriptManager scripts;
        static stick::DriverManager drivers;
        static stick::EventBus bus( 16 );
        static stick::ControlChannel controlChannel( bus, kControlHost, kControlPort );
        static stick::Orchestrator orch( devices, rules, scripts, drivers, bus, modelArena,
                                         cycleArenaBuf, sizeof( cycleArenaBuf ), &controlChannel );

        jsonview::JsonView view( model, std::strlen( model ), tokens, 256 );
        cil::Command commands[ 8 ];
        const int built = cil::buildModel( view, modelArena, commands, 8 );

        if( built <= 0 )
        {
            std::printf( "[demo] model failed to load\n" );
            return;
        }

        orch.boot( commands, static_cast<uint32_t>( built ) );

#ifdef CONFIG_DRIVER_CONSOLE
        // Register the console actuator's write op (prints to serial). The driver name
        // comes from the registry (single source of truth, see drivers/registry.hpp).
        if( ! drivers.registerOps( drivers::kOutputDriverName, drivers::consoleOps( bus ) ) )
        {
            std::printf( "[demo] WARNING: driver '%.*s' not found in model -- console actuator inert\n",
                         static_cast<int>( drivers::kOutputDriverName.size() ), drivers::kOutputDriverName.data() );
        }
#endif

        // Read the clock sensor's interval from its config (default 2000 ms).
        uint32_t intervalMs = 2000;
        for( int i = 0; i < built; ++i )
        {
            if( commands[ i ].type == cil::CommandType::Device && commands[ i ].device.name == "clock" )
            {
                const naxe::Value * iv = commands[ i ].device.config.pairValue()->get( "interval" );
                if( iv != nullptr && iv->isInt() )
                {
                    intervalMs = static_cast<uint32_t>( iv->intValue() );
                }
            }
        }

        orch.attach();
        bus.start();
        controlChannel.start();

        // Sync the wall clock over SNTP (via QEMU's SLIRP NAT to the host's internet) for
        // the status line below -- the "clock" device itself reports elapsed-ms-since-boot
        // (see ClockDriver), not wall-clock time, so this sync doesn't affect its value.
        if( pal::timeSyncInit() )
        {
            pal::timeSyncWait( 10000 );
        }
        std::printf( "[demo] clock is %s (epoch %lld)\n",
                     naxe::isTimeValid() ? "valid" : "NOT synced",
                     static_cast<long long>( naxe::nowEpochSeconds() ) );

#ifdef CONFIG_DRIVER_CLOCK
        static drivers::ClockDriver clock( bus, "clock", intervalMs );
        clock.start();
        if( ! drivers.registerOps( drivers::kClockDriverName, clock.ops() ) )
        {
            std::printf( "[demo] WARNING: driver '%.*s' not found in model -- clock sensor inert\n",
                         static_cast<int>( drivers::kClockDriverName.size() ), drivers::kClockDriverName.data() );
        }
#endif

        std::printf( "[demo] autonomous clock demo started (clock every %u ms)\n",
                     static_cast<unsigned>( intervalMs ) );
        std::printf( "[demo] control channel dialing %s:%u (reconnects with backoff if unreachable)\n",
                     kControlHost, static_cast<unsigned>( kControlPort ) );
    }
} // namespace

/** ESP-IDF application entry point. */
extern "C" void app_main()
{
    std::printf( "[twig-oop] Phase 1 hello from ESP32-S3 target (QEMU) — "
                 "C++20, -fno-exceptions, -fno-rtti, vendored ETL.\n" );

    const size_t psramTotal = heap_caps_get_total_size( MALLOC_CAP_SPIRAM );
    const size_t psramFree  = heap_caps_get_free_size( MALLOC_CAP_SPIRAM );

    std::printf( "[twig-oop] PSRAM: total=%u bytes (%u KiB), free=%u bytes\n",
                 static_cast<unsigned>( psramTotal ),
                 static_cast<unsigned>( psramTotal / 1024 ),
                 static_cast<unsigned>( psramFree ) );

    runEtlSmokeTest();

#ifdef CONFIG_TWIG_ONDEVICE_SELFTEST
    // Phase 2: value model & memory (same test body as the host build).
    std::printf( "[twig-oop] value-model tests: %s\n",
                 naxe::runValueModelTests() == 0 ? "PASS" : "FAIL" );

    // Phase 3: evaluator core (AST/operators/datetime).
    std::printf( "[twig-oop] evaluator tests: %s\n",
                 naxe::runEvalTests() == 0 ? "PASS" : "FAIL" );

    // Phase 4: function/method library.
    std::printf( "[twig-oop] functions tests: %s\n",
                 naxe::runFunctionsTests() == 0 ? "PASS" : "FAIL" );

    // Phase 5: Une text parser.
    std::printf( "[twig-oop] parser tests: %s\n",
                 naxe::runParserTests() == 0 ? "PASS" : "FAIL" );

    // Cron scheduler (backs the CronDriver/Kronos sensor).
    std::printf( "[twig-oop] cron tests: %s\n",
                 naxe::runCronTests() == 0 ? "PASS" : "FAIL" );

    // Phase 6: JSON read path + CIL builders.
    std::printf( "[twig-oop] json+cil tests: %s\n",
                 cil::runCilTests() == 0 ? "PASS" : "FAIL" );

    // Phase 7: stick runtime core.
    std::printf( "[twig-oop] stick core tests: %s\n",
                 stick::runStickTests() == 0 ? "PASS" : "FAIL" );

    // Phase 10: AFTER/WITHIN temporal operators.
    std::printf( "[twig-oop] temporal tests: %s\n",
                 stick::runTemporalTests() == 0 ? "PASS" : "FAIL" );

    // Phase 8: runtime (EventBus + Orchestrator) — runs a real model end-to-end.
    std::printf( "[twig-oop] runtime tests: %s\n",
                 stick::runRuntimeTests() == 0 ? "PASS" : "FAIL" );
#else
    // CONFIG_TWIG_ONDEVICE_SELFTEST=n (tools/build_model.sh's deployment images set this
    // to reclaim the ~115 KB of BSS each test file's own static buffers would otherwise
    // cost -- see main/Kconfig.projbuild).
    std::printf( "[twig-oop] on-device self-test suite: SKIPPED (CONFIG_TWIG_ONDEVICE_SELFTEST=n)\n" );
#endif

    // Phase 9: autonomous demo — the node now runs its model on its own.
    runAutonomousDemo();

    // Keep app_main from returning: the EventBus consumer task and the drivers keep
    // running independently. Returning here triggers ESP-IDF's main_task repeat-call
    // anomaly (twig/IMPLEMENTATION.md Phase 5), re-invoking app_main and overflowing the
    // small main-task stack.
    for( ;; )
    {
        vTaskDelay( pdMS_TO_TICKS( 1000 ) );
    }
}
