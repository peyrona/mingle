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
/** \file memory.cpp
 * Standard-heap implementation of the `naxe::Allocator` backend (see memory.hpp).
 */
#include "naxe/memory.hpp"

#include <cstdlib>

namespace naxe
{
    void * heapAllocate( size_t size )
    {
        // std::malloc, not ::operator new: the nothrow operator new goes through the
        // throwing new (which raises bad_alloc) on failure, and on ESP-IDF with
        // -fno-exceptions that throw path is stubbed to abort(). malloc simply returns
        // nullptr — the correct "report failure, don't abort" behaviour for an embedded
        // target.
        return std::malloc( size );
    }

    void heapDeallocate( void * p )
    {
        std::free( p ); // nullptr is a safe no-op
    }
} // namespace naxe
