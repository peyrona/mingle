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
/** \file script_manager.hpp
 *
 * The registry of all runtime Scripts — a thin fixed-capacity array with case-insensitive
 * lookup. Boot-sequence lifecycle-running (runPreStartScripts etc.) belongs on whatever
 * owns the boot sequence (Orchestrator), not this registry.
 *
 * Tier 1 (hardware-agnostic): no FreeRTOS/ESP-IDF dependency.
 */
#pragma once

#include <cstdint>
#include <string_view>

#include "cil/model.hpp"
#include "stick/script.hpp"

namespace stick
{
    /** The registry of all runtime Scripts. */
    class ScriptManager
    {
    public:
        static constexpr uint32_t kMaxScripts = 32;

        /** Constructs an empty ScriptManager. */
        ScriptManager();

        /** Builds a runtime Script from `parsed` and adds it.
         *
         * @param arena Long-lived arena the compiled statements are allocated into.
         * @return true if added; false if the table is full.
         */
        bool add( const cil::Script & parsed, naxe::Arena & arena );

        /** Case-insensitive lookup by name. */
        Script * named( std::string_view name );
        /** Const overload. */
        const Script * named( std::string_view name ) const;

        /** Removes a script by name, compacting the array. */
        bool remove( std::string_view name );

        /** @return The number of scripts held. */
        uint32_t count() const { return count_; }
        /** The script at `index` (must be < count()). */
        Script & at( uint32_t index ) { return scripts_[ index ]; }
        /** Const access. */
        const Script & at( uint32_t index ) const { return scripts_[ index ]; }

    private:
        Script scripts_[ kMaxScripts ];
        uint32_t count_;
    };
} // namespace stick
