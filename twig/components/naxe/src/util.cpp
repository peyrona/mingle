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
/** \file util.cpp
 * Implementation of naxe::util::asciiEqualsIgnoreCase (see util.hpp).
 */
#include "naxe/util.hpp"

namespace naxe
{
    namespace util
    {
        bool asciiEqualsIgnoreCase( std::string_view a, std::string_view b )
        {
            if( a.size() != b.size() )
            {
                return false;
            }

            for( size_t i = 0; i < a.size(); ++i )
            {
                char ca = a[ i ];
                char cb = b[ i ];

                if( ca >= 'A' && ca <= 'Z' ) ca = static_cast<char>( ca + ( 'a' - 'A' ) );
                if( cb >= 'A' && cb <= 'Z' ) cb = static_cast<char>( cb + ( 'a' - 'A' ) );

                if( ca != cb )
                {
                    return false;
                }
            }

            return true;
        }
    } // namespace util
} // namespace naxe
