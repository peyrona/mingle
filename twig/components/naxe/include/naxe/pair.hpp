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
/** \file pair.hpp
 *
 * Growable, heap-backed key/value association array (Une's `pair`).
 *
 * Keys are ASCII byte spans compared case-insensitively (matching Une's `pair` key
 * semantics); they are *copied* into heap storage on `set()`, so a `Pair` is fully
 * self-contained and its keys stay valid for the object's lifetime. Lookup is a linear
 * scan — pairs in practice hold a handful of keys, and at that scale a linear scan beats
 * a hash table on both code size and constant-factor speed (per the plan).
 *
 * Ownership mirrors `List` (list.hpp): `create()`/`destroy()`, never `new`/`delete`.
 */
#pragma once

#include <cstddef>
#include <cstdint>
#include <string_view>

#include "naxe/memory.hpp"
#include "naxe/value.hpp"

namespace naxe
{
    /** A growable, heap-backed association array of string keys to Values. */
    class Pair
    {
    public:
        /** Allocates and constructs a Pair.
         *
         * @param initialCapacity Initial entry capacity (clamped to >= 1).
         * @param alloc Heap backend for the object and its storage.
         * @return The new Pair, or nullptr on allocation failure.
         */
        static Pair * create( size_t initialCapacity = 4, const Allocator & alloc = kHeapAllocator );

        /** Destroys and releases a Pair created by `create()`. */
        static void destroy( Pair * pair );

        /** @return The number of entries currently held. */
        size_t size() const { return count_; }
        /** @return The current entry capacity (not the count). */
        size_t capacity() const { return capacity_; }
        /** @return True if the pair holds no entries. */
        bool empty() const { return count_ == 0; }

        /** Sets `key` to `value`, overwriting an existing key.
         *
         * The key is copied into heap storage. Key match is ASCII case-insensitive.
         *
         * @param key The key bytes (copied).
         * @param value The value to store.
         * @return False only if the heap could not grow or copy the key.
         */
        bool set( std::string_view key, const Value & value );

        /** Looks up a key (ASCII case-insensitive).
         *
         * @param key The key to find.
         * @return Pointer to the stored value, or nullptr if absent.
         */
        const Value * get( std::string_view key ) const;

        /** Removes the entry with `key`, if present.
         *
         * @param key The key to remove.
         * @return True if an entry was removed.
         */
        bool remove( std::string_view key );

        /** Empties the pair, releasing each key (keeps storage). */
        void clear();

        /** Merges every entry of `other` into this pair, overwriting key conflicts. */
        void putAll( const Pair & other );

        /** Keeps only entries whose key also exists in `other` (values from this pair). */
        void intersect( const Pair & other );

        /** @param index Zero-based entry position (must be < size()).
         *  @return The entry's key view. */
        std::string_view keyAt( size_t index ) const;
        /** @param index Zero-based entry position (must be < size()).
         *  @return The entry's value. */
        const Value & valueAt( size_t index ) const;

    private:
        /** One entry: an owned key copy plus its value. */
        struct Slot
        {
            char * key;   // owned, length-prefixed (keyLen)
            size_t keyLen;
            Value value;
        };

        /** Constructs over pre-allocated storage (see create()). */
        Pair( size_t capacity, Slot * slots, const Allocator & alloc );
        /** Frees each key and the slot storage; called by destroy(). */
        ~Pair();

        /** Grows `slots_` (doubling) until it can hold `needed` entries. */
        bool ensureCapacity( size_t needed );

        Slot * slots_;
        size_t count_;
        size_t capacity_;
        Allocator alloc_;
    };
} // namespace naxe
