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
/** \file list.hpp
 *
 * Growable, heap-backed list of `Value` elements.
 *
 * This is the runtime `List` behind a `Value::List` (see value.hpp). It differs from the
 * original C port's fixed-capacity list: per the plan's decisions, lists must support
 * runtime add/remove without a compile-time cap, so storage is a heap-allocated array
 * that doubles when full (backed by the injected `Allocator`, see memory.hpp).
 *
 * Ownership: `List` is created with `create()` and released with `destroy()` — never
 * `new`/`delete` directly, because `create()` must be able to fail cleanly (return
 * `nullptr`) under `-fno-exceptions`. `Value::makeList` stores only the pointer; the
 * creator owns and destroys the object.
 */
#pragma once

#include <cstddef>
#include <cstdint>

#include "naxe/memory.hpp"
#include "naxe/value.hpp"

namespace naxe
{
    /** A growable, heap-backed list of Values. */
    class List
    {
    public:
        /** Allocates and constructs a List.
         *
         * @param initialCapacity Initial element capacity (clamped to >= 1).
         * @param alloc Heap backend for the object and its storage.
         * @return The new List, or nullptr on allocation failure.
         */
        static List * create( size_t initialCapacity = 4, const Allocator & alloc = kHeapAllocator );

        /** Destroys and releases a List created by `create()`. */
        static void destroy( List * list );

        /** @return The number of elements currently held. */
        size_t size() const { return count_; }
        /** @return The current element capacity (not the count). */
        size_t capacity() const { return capacity_; }
        /** @return True if the list holds no elements. */
        bool empty() const { return count_ == 0; }

        /** @param index Zero-based position (must be < size()).
         *  @return The element at `index`. */
        const Value & operator[]( size_t index ) const;

        /** Appends a copy of `value`.
         *
         * @param value The element to append.
         * @return False only if the heap could not grow the storage.
         */
        bool add( const Value & value );

        /** Removes the element at `index`, shifting later elements left.
         *
         * @param index Zero-based position (must be < size()).
         * @return True if removed; false if `index` was out of range.
         */
        bool remove( size_t index );

        /** Empties the list without shrinking its storage. */
        void clear() { count_ = 0; }

        /** Replaces the element at `index`.
         *
         * @param index Zero-based position (must be < size()).
         * @param value The new value.
         * @return False if `index` was out of range.
         */
        bool set( size_t index, const Value & value );

        /** Removes duplicates in place, preserving first-occurrence order.
         *
         * Equality is valueEquals() (see value.hpp): scalars compare by value (Int and
         * Float interoperate), strings case-insensitively, List/Pair by pointer identity.
         */
        void uniquefy();

        /** Sorts the list in place (stable insertion sort — lists are small).
         *
         * Ints sort numerically (exact int64), Floats numerically, Strings
         * lexicographically (case-sensitive). A heterogeneous element stops the scan at
         * that point rather than throwing (documented fallback).
         */
        void sort();

        /** Reverses the element order in place. */
        void reverse();

        /** Keeps only elements that also appear in `other` (membership by valueEquals()). */
        void intersect( const List & other );

        /** Appends every element of `other` (not a set union — duplicates are kept). */
        void appendAll( const List & other );

        /** Rotates elements right by `places` (negative rotates left).
         *
         * @param places Number of positions to rotate (normalized into the list size).
         */
        void rotate( int places );

        /** @return Pointer to the first element (for range-for / STL-style iteration). */
        const Value * begin() const { return items_; }
        /** @return Pointer one past the last element. */
        const Value * end() const { return items_ + count_; }

    private:
        /** Constructs over pre-allocated storage (see create()). */
        List( size_t capacity, Value * items, const Allocator & alloc );
        /** Frees the element storage; called by destroy(). */
        ~List();

        /** Grows `items_` (doubling) until it can hold `needed` elements.
         *
         * @param needed Minimum element count required.
         * @return False on heap exhaustion.
         */
        bool ensureCapacity( size_t needed );

        Value * items_;
        size_t count_;
        size_t capacity_;
        Allocator alloc_;
    };
} // namespace naxe
