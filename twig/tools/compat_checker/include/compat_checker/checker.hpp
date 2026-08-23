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
/** \file checker.hpp
 *
 * Model compatibility checker: answers "will this transpiled `.model` run on twig-oop?"
 * as a repeatable pre-flight gate, replacing a manual code-reading exercise.
 *
 * Two halves (Phase 11 parts A and B):
 *
 *  - **Language/feature surface (A):** walk every WHEN/IF/action-value expression with
 *    the real `naxe` parser and flag any use of a function/method/operator/feature on the
 *    deferred list. The deferred list is NOT maintained as a parallel table here — it is
 *    derived live from `naxe::resolveFunction()`/`resolveMethod()`/`resolveOperator()`,
 *    so the checker can never drift from what the evaluator actually supports.
 *  - **Driver/controller availability (B):** cross-check every model `DRIVER` command and
 *    `Device.driver` name against `drivers::kDriverRegistry`, flagging names no native
 *    driver implements.
 *
 * Also flags two whole-model features that have their own deferred rules rather than a
 * function table entry: non-`une`/non-inline scripts (only when actually executed, see
 * below) and unsupported THEN-action targets (`enable(...)`/`exit()`/…).
 *
 * **Controller-script exemption:** real models point their `DRIVER` command's `script`
 * field at a Java controller script (e.g. `01.clock.model`'s `SystemClock`,
 * `language:"java"`). Those are loaded but never executed — the native driver substitutes
 * for them. The checker flags a non-`une`/non-inline script only when it is *actually
 * executed* (named as a bare `THEN` action target, or marked `onstart`/`prestart`), not
 * when it is merely the `script` field of a `DRIVER` command.
 *
 * Host-only by design (Tier 1): links `naxe` + `jsonview` + `cil` + the header-only
 * driver registry. It never runs on-device.
 */
#pragma once

#include <cstdint>
#include <string_view>

#include "cil/model.hpp"
#include "naxe/arena.hpp"

namespace compat_checker
{
    /** The kinds of incompatibility a model can have against twig-oop's surface. */
    enum class FindingKind : uint8_t
    {
        UnsupportedFunction,    // resolveFunction(name) == nullptr in an expression
        UnsupportedMethod,      // a method name resolving for no extended type (map/toJson/…)
        UnsupportedOperator,    // a token the parser rejects (bitwise & | ~ >< << >>)
        EdgeDetectionOperator,  // ?> ?< ?= ?!= ?<> — parsed but not evaluated (silent false)
        NonUneScript,           // an executed script whose language != "une"
        FileBasedScript,        // an executed script that is non-inline ("FROM <uri>")
        UnsupportedAction,      // a THEN target that is an expression call (enable()/exit()/…)
        UnknownDriver,          // a DRIVER/Device.driver name with no native driver
        ParseError              // a WHEN/IF/value expression that failed to parse
    };

    /** One reported incompatibility. `name`/`detail` point into `arena` or the model
     * buffer passed to checkModel(); both are valid as long as those stay resident. */
    struct Finding
    {
        FindingKind kind;
        int commandIndex;       // index into the model's commands array, or -1
        std::string_view name;  // the offending function/method/operator/driver/script name
        std::string_view detail;// extra context (e.g. the parse-error message)
    };

    /** A fixed-capacity, append-only list of findings. */
    class Report
    {
    public:
        static constexpr uint32_t kMaxFindings = 256;

        /** Appends a finding (silently drops if the report is already full).
         *
         * @return true if the finding was recorded.
         */
        bool add( FindingKind kind, int commandIndex, std::string_view name, std::string_view detail );

        /** @return The number of recorded findings. */
        uint32_t count() const { return count_; }
        /** @return The finding at `index` (must be < count()). */
        const Finding & at( uint32_t index ) const { return findings_[ index ]; }
        /** @return True if no findings were recorded (the model is compatible). */
        bool empty() const { return count_ == 0; }

    private:
        Finding findings_[ kMaxFindings ];
        uint32_t count_ = 0;
    };

    /** Checks one fully-built model's commands against twig-oop's surface.
     *
     * `commands`/`count` are the output of `cil::buildModel()` over the model file.
     * `arena` must outlive the returned findings (a finding's `name`/`detail` may point
     * into it).
     *
     * @param commands The built model commands.
     * @param count Number of commands.
     * @param out Receives the findings (appended to; not cleared).
     * @param arena Arena for any message/name copies.
     */
    void checkModel( const cil::Command * commands, uint32_t count, Report & out, naxe::Arena & arena );
} // namespace compat_checker
