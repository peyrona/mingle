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
/** \file arena.hpp
 *
 * Bump (arena) allocator: the per-cycle memory strategy from the project plan.
 *
 * No `malloc`/`free` per allocation, no per-object destructor calls, no GC. Two
 * lifetimes are expected in the real engine:
 *
 *  - A per-cycle `Arena` scoped to one rule/script evaluation cycle, `reset()` every
 *    cycle so the whole buffer is reused without ever freeing individual objects.
 *  - A long-lived `Arena` for stable state (device/rule definitions) that is simply
 *    never reset during normal operation.
 *
 * Design rules (load-bearing, see the plan's memory model):
 *
 *  - `alloc()`/`emplace()` return `nullptr` on exhaustion — arena capacity is a
 *    load-time sizing decision, not something to grow at runtime on-device.
 *  - `reset()` does *not* run destructors. Only types with no-op (or trivial)
 *    destructors may live in the arena — i.e. types that hold no heap/resources
 *    (e.g. `naxe::Value`, AST nodes). Owning, heap-backed types (`List`/`Pair`) are
 *    heap-allocated and destroyed explicitly; see list.hpp/pair.hpp.
 *  - The arena is deliberately hardware-agnostic (Tier 1): no FreeRTOS/ESP-IDF calls,
 *    so it compiles and runs identically on host and target.
 */
#pragma once

#include <cstddef>
#include <cstdint>
#include <new>
#include <utility>

namespace naxe
{
    /** Bump allocator backed by a fixed caller-supplied buffer. */
    class Arena
    {
    public:
        /** Constructs an Arena over the given buffer.
         *
         * `buffer` must be aligned to at least 8 bytes (e.g. `alignas(8)`): alloc()
         * aligns the *offset* within the buffer, so the base address itself must already
         * be aligned for the returned pointers to be.
         *
         * @param buffer Backing storage for all allocations (must outlive the Arena).
         * @param capacity Buffer size in bytes.
         */
        Arena( uint8_t * buffer, size_t capacity )
            : buffer_( buffer ), capacity_( capacity ), offset_( 0 ), highWater_( 0 ), deferredCount_( 0 )
        {
        }

        /** Destroys any deferred heap objects, then the arena storage itself (which is
         * caller-owned, so only the deferred objects need explicit cleanup here). */
        ~Arena()
        {
            runDeferredDestructors();
        }

        /** Registers a heap-allocated object to be destroyed by the next reset() (or by
         * this Arena's own destruction).
         *
         * This is how a cycle-scoped Arena reclaims the growable `List`/`Pair` objects
         * the evaluator produces as intermediate results: those are heap-backed (see
         * list.hpp/pair.hpp), so the bump allocator cannot free them on reset() by
         * itself. `T` must expose `static void destroy(T*)` (both List and Pair do).
         *
         * @param obj The heap object to own (nullptr is ignored).
         * @return true if `obj` is now owned by this Arena (or was nullptr); false if
         *         this Arena's deferred-object slots (kMaxDeferred) are already full --
         *         matching alloc()/emplace()'s own "return a failure signal, caller
         *         decides how to fail" convention. On false, `obj` is still un-owned:
         *         the caller must destroy it itself (e.g. `T::destroy(obj)`) or it leaks
         *         for the rest of the cycle.
         */
        template <typename T>
        bool defer( T * obj )
        {
            if( obj == nullptr )
            {
                return true;
            }
            if( deferredCount_ >= kMaxDeferred )
            {
                return false;
            }
            deferred_[ deferredCount_++ ] = DestructorEntry{
                []( void * p ) { T::destroy( static_cast<T *>( p ) ); }, obj
            };
            return true;
        }

        /** Allocates `size` bytes, 8-byte aligned.
         *
         * @param size Number of bytes to allocate.
         * @return Pointer to the allocated bytes, or nullptr on exhaustion.
         */
        void * alloc( size_t size )
        {
            const size_t aligned = ( size + 7u ) & ~static_cast<size_t>( 7 );

            if( offset_ + aligned > capacity_ )
            {
                return nullptr; // caller decides how to fail (assert/log)
            }

            void * p = buffer_ + offset_;
            offset_ += aligned;

            if( offset_ > highWater_ )
            {
                highWater_ = offset_;
            }

            return p;
        }

        /** Allocates raw storage for a single `T` (does NOT run its constructor).
         *
         * Prefer emplace() when the type needs construction; this is the raw-byte path
         * for trivially-copyable PODs that are field-assigned afterwards.
         *
         * @return Pointer to the allocated `T`, or nullptr on exhaustion.
         */
        template <typename T>
        T * alloc()
        {
            return static_cast<T *>( alloc( sizeof( T ) ) );
        }

        /** Allocates and constructs a `T` in-place with placement `new`.
         *
         * The object is NOT destroyed by reset() — see the class comment's destructor
         * rule. Prefer this for non-trivial types (e.g. AST nodes with constructors).
         *
         * @param args Arguments forwarded to T's constructor.
         * @return Pointer to the constructed `T`, or nullptr on exhaustion.
         */
        template <typename T, typename... Args>
        T * emplace( Args &&... args )
        {
            void * p = alloc( sizeof( T ) );
            if( p == nullptr )
            {
                return nullptr;
            }
            return new( p ) T( std::forward<Args>( args )... );
        }

        /** Resets the allocation offset (reclaiming all bump-allocated storage) and
         * destroys any deferred heap objects. */
        void reset()
        {
            runDeferredDestructors();
            offset_ = 0;
        }

        /** @return Bytes allocated since construction or the last reset(). */
        size_t used() const { return offset_; }

        /** @return Peak `used()` seen since construction. */
        size_t highWaterMark() const { return highWater_; }

        /** @return The buffer capacity in bytes. */
        size_t capacity() const { return capacity_; }

    private:
        /** Maximum number of heap objects this Arena can own at once. Sized generously
         * for a single evaluation cycle (each list()/pair() result registers one). */
        static constexpr size_t kMaxDeferred = 32;

        /** A (destructor, object) pair for a deferred heap object. */
        struct DestructorEntry
        {
            void ( *dtor )( void * );
            void * obj;
        };

        /** Runs every registered destructor and clears the list. */
        void runDeferredDestructors()
        {
            for( size_t i = 0; i < deferredCount_; ++i )
            {
                deferred_[ i ].dtor( deferred_[ i ].obj );
            }
            deferredCount_ = 0;
        }

        uint8_t * buffer_;
        size_t capacity_;
        size_t offset_;
        size_t highWater_;
        DestructorEntry deferred_[ kMaxDeferred ];
        size_t deferredCount_;
    };
} // namespace naxe
