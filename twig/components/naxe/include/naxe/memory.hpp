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
/** \file memory.hpp
 *
 * Heap-allocation abstraction backing the growable containers (`List`/`Pair`).
 *
 * The project's memory model is static-by-default, with one deliberate exception: the
 * runtime-growable `List`/`Pair` collections are heap-backed so they can add/remove
 * elements without a compile-time cap (see the plan's allocation & lifetime section).
 * Those containers don't call the allocator directly; they receive an `Allocator`
 * (two function pointers) at construction, so the *backend* is swappable per target:
 *
 *  - Host tests and the default: `kHeapAllocator` (nothrow `operator new` / `operator
 *    delete`) — returns `nullptr` on failure, never throws (required under
 *    `-fno-exceptions`).
 *  - On-device (later phase): the PAL's `platform_alloc`/`platform_free`, which map to
 *    `heap_caps_malloc(..., MALLOC_CAP_SPIRAM)` on ESP32-S3.
 *
 * Keeping naxe itself hardware-agnostic is exactly why the backend is injected rather
 * than hard-coded.
 */
#pragma once

#include <cstddef>

namespace naxe
{
    /** A non-owning view of a memory allocator: two free functions. */
    struct Allocator
    {
        /** Allocates `size` bytes.
         *
         * @return Pointer to the allocation, or nullptr on failure (never throws).
         */
        void *( *allocate )( size_t size );

        /** Releases a pointer previously returned by `allocate` (nullptr is a no-op). */
        void ( *deallocate )( void * p );
    };

    /** Standard heap backend: nothrow `operator new` / `operator delete`. */
    void * heapAllocate( size_t size );
    /** Standard heap backend: `operator delete`. */
    void heapDeallocate( void * p );

    /** The default `Allocator`, backed by the standard heap.
     *
     * `inline const` (C++17) so every translation unit shares one definition.
     */
    inline const Allocator kHeapAllocator{ &heapAllocate, &heapDeallocate };
} // namespace naxe
