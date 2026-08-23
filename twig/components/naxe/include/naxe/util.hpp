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
/** \file util.hpp
 *
 * Small, shared, hardware-agnostic helpers. Deliberately NOT a grab-bag "utils"
 * dumping ground: this header holds only genuinely shared string logic. A single-use
 * helper local to one file stays in that file.
 *
 * The case-insensitive ASCII comparison here is the one canonical implementation used
 * for name lookups (device/rule/script/driver names, `Pair` keys) across the whole
 * project — keeping it in one place instead of duplicating it per consumer.
 */
#pragma once

#include <string_view>

namespace naxe
{
    namespace util
    {
        /** Compares two byte spans case-insensitively (ASCII only).
         *
         * Matches the Une language's case-insensitive name/key semantics. No Unicode
         * case-folding — strings in this project are ASCII/UTF-8 bytes.
         *
         * @param a First span.
         * @param b Second span.
         * @return true if both spans are equal ignoring ASCII case.
         */
        bool asciiEqualsIgnoreCase( std::string_view a, std::string_view b );
    } // namespace util
} // namespace naxe
