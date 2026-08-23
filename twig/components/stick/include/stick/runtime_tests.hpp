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
/** \file runtime_tests.hpp
 * Declaration of the on-device runtime test suite (EventBus + Orchestrator).
 *
 * This suite is FreeRTOS-dependent (it drives a real EventBus), so it runs only under
 * QEMU, not the host build.
 */
#pragma once

namespace stick
{
    /** Runs the runtime tests (EventBus + Orchestrator end-to-end).
     *
     * @return The number of failed checks (0 == all pass).
     */
    int runRuntimeTests();
} // namespace stick
