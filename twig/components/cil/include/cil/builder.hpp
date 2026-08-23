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
/** \file builder.hpp
 *
 * Builds `cil::Command` structs from a `jsonview::JsonView` over a transpiled `.model`
 * file (or, later, an inbound ExEnComm-shaped control message — same JSON view, same
 * per-command builders). Mirrors CilBuilder.java's switch on the lowercase "cmd" field.
 *
 * Tier 1 (hardware-agnostic): no FreeRTOS/ESP-IDF dependency.
 */
#pragma once

#include "cil/model.hpp"
#include "jsonview/json_view.hpp"
#include "naxe/arena.hpp"

namespace cil
{
    /** Converts one JSON value (any type) into a `naxe::Value`.
     *
     * Matches UtilJson.toUneType(): string -> String, integral number -> Int, fractional
     * number -> Float, true/false -> Bool, object -> Pair (recursively), array -> List
     * (recursively), JSON null -> empty String.
     *
     * @param v The JsonView owning the token stream to read from.
     * @param idx Token index of the JSON value to convert.
     * @param arena Arena backing the converted Value's string copies and deferred
     *              collections.
     * @return The converted naxe::Value.
     */
    naxe::Value jsonToUneValue( const jsonview::JsonView & v, int idx, naxe::Arena & arena );

    /** Builds one command object (an element of the model's "commands" array) at token
     *  index `cmdIdx`.
     *
     * @return true if the command was built, false otherwise (absent/unknown "cmd" or a
     *         missing required field).
     */
    bool buildCommand( const jsonview::JsonView & v, int cmdIdx, naxe::Arena & arena,
                       Command & out );

    /** Parses a full model file's top-level {"commands":[...]} array.
     *
     * @param v The JsonView owning the token stream.
     * @param arena Arena backing the built Commands' allocations.
     * @param outCommands Receives the built Commands.
     * @param outCapacity Max number of Commands to write.
     * @return The number of commands successfully built (a malformed command is skipped,
     *         not fatal to the rest of the model).
     */
    int buildModel( const jsonview::JsonView & v, naxe::Arena & arena, Command * outCommands,
                    int outCapacity );
} // namespace cil
