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
/** \file model.hpp
 *
 * CIL command structs — Device/Driver/Rule/Script — the static, load-time-parsed shape
 * of a transpiled `.model` file.
 *
 * All strings are zero-copy `std::string_view`s into the caller-owned JSON buffer (the
 * loaded `.model` text), valid as long as that buffer stays resident. Converted Une
 * values (`naxe::Value`, for Device config/init and Rule action values) and the
 * `RuleAction`/`Script.from` arrays are arena/heap-allocated, fixed at build time.
 *
 * Tier 1 (hardware-agnostic): no FreeRTOS/ESP-IDF dependency.
 */
#pragma once

#include <cstdint>
#include <string_view>

#include "naxe/value.hpp"

namespace cil
{
    /** Discriminates which concrete CIL command a Command union holds. */
    enum class CommandType
    {
        Device,
        Driver,
        Rule,
        Script,
        Unknown
    };

    /** A transpiled DEVICE command.
     *
     * `name`/`driver` optional (default empty if absent); `config`/`init` optional
     * objects-or-null, absent or JSON null -> empty pair.
     */
    struct Device
    {
        std::string_view name;
        std::string_view driver;
        naxe::Value config; // always a Pair Value (possibly empty)
        naxe::Value init;   // always a Pair Value (possibly empty)
    };

    /** A transpiled DRIVER command.
     *
     * `name` and `script` both required (absent key is a hard failure, not a null).
     */
    struct Driver
    {
        std::string_view name;
        std::string_view script;
    };

    /** One element of a Rule's `then` array. */
    struct RuleAction
    {
        int64_t after;              // delay in milliseconds
        std::string_view target;    // action target (rule/script name or expression)
        bool hasValue;              // false means JSON null ("pure expression, no assignment")
        naxe::Value value;
    };

    /** A transpiled RULE command.
     *
     * `name`/`if` are required KEYS but nullable VALUES (hasName/hasIf track that);
     * `when` is required and always a real string.
     */
    struct Rule
    {
        bool hasName;
        std::string_view name;
        std::string_view when;      // required, always a real string
        bool hasIf;
        std::string_view ifExpr;
        RuleAction * actions;       // arena-allocated, fixed size
        uint32_t actionCount;
    };

    /** A transpiled SCRIPT command — every field is a required key. */
    struct Script
    {
        std::string_view name;
        std::string_view language;
        bool onstart;
        bool onstop;
        bool prestart;
        bool prestop;
        bool inlineCode;            // JSON key is `inline`, a C++ reserved word
        std::string_view * from;    // arena-allocated, fixed size
        uint32_t fromCount;
        std::string_view call;
    };

    /** A fully-parsed CIL command: a type tag plus the concrete command struct.
     *
     * A plain struct (not a union) — the four command structs contain `std::string_view`
     * members whose non-trivial default constructors make an anonymous-union payload
     * ill-formed. Commands are few (a model holds tens), so the extra bytes are a
     * non-issue against the clarity this buys.
     */
    struct Command
    {
        CommandType type = CommandType::Unknown;
        Device device;
        Driver driver;
        Rule rule;
        Script script;
    };
} // namespace cil
