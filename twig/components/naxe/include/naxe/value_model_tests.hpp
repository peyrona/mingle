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
/** \file value_model_tests.hpp
 * Declaration of the host/on-device value-model test suite (see value_model_tests.cpp).
 *
 * The suite body is shared verbatim between the host build (test/host_main.cpp) and the
 * on-device build (main.cpp calls it under QEMU), matching the project's "identical
 * code, two targets" testing philosophy.
 */
#pragma once

namespace naxe
{
    /** Runs the value-model tests.
     *
     * @return The number of failed checks (0 == all pass).
     */
    int runValueModelTests();
} // namespace naxe
