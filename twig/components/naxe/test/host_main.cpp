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
/** \file host_main.cpp
 * Host-side test runner for the naxe suites (see build_and_run.sh).
 *
 * This is the plain-g++ Tier 1 entry point — no FreeRTOS/ESP-IDF. The same test bodies
 * (value_model_tests.cpp, eval_tests.cpp) are also run on-device from main.cpp under QEMU.
 */
#include <cstdio>

#include "naxe/cron_tests.hpp"
#include "naxe/eval_tests.hpp"
#include "naxe/functions_tests.hpp"
#include "naxe/parser_tests.hpp"
#include "naxe/value_model_tests.hpp"

/** Runs all suites and reports the outcome via the exit code. */
int main()
{
    int failures = 0;
    failures += naxe::runValueModelTests();
    failures += naxe::runEvalTests();
    failures += naxe::runFunctionsTests();
    failures += naxe::runParserTests();
    failures += naxe::runCronTests();

    std::printf( failures == 0 ? "ALL TESTS PASSED\n" : "TESTS FAILED\n" );
    return failures == 0 ? 0 : 1;
}
