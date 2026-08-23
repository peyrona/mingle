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
/** \file list.cpp
 * Implementation of the growable List (see list.hpp).
 */
#include "naxe/list.hpp"

#include <cassert>

#include "naxe/util.hpp"

namespace naxe
{
    List * List::create( size_t initialCapacity, const Allocator & alloc )
    {
        if( initialCapacity == 0 )
        {
            initialCapacity = 1;
        }

        void * objMem = alloc.allocate( sizeof( List ) );
        if( objMem == nullptr )
        {
            return nullptr;
        }

        void * itemsMem = alloc.allocate( sizeof( Value ) * initialCapacity );
        if( itemsMem == nullptr )
        {
            alloc.deallocate( objMem );
            return nullptr;
        }

        return new( objMem ) List( initialCapacity, static_cast<Value *>( itemsMem ), alloc );
    }

    void List::destroy( List * list )
    {
        if( list == nullptr )
        {
            return;
        }

        const Allocator alloc = list->alloc_;
        list->~List();
        alloc.deallocate( list );
    }

    List::List( size_t capacity, Value * items, const Allocator & alloc )
        : items_( items ), count_( 0 ), capacity_( capacity ), alloc_( alloc )
    {
    }

    List::~List()
    {
        alloc_.deallocate( items_ );
    }

    const Value & List::operator[]( size_t index ) const
    {
        assert( index < count_ );
        return items_[ index ];
    }

    bool List::add( const Value & value )
    {
        if( ! ensureCapacity( count_ + 1 ) )
        {
            return false;
        }

        items_[ count_++ ] = value;
        return true;
    }

    bool List::remove( size_t index )
    {
        if( index >= count_ )
        {
            return false;
        }

        for( size_t i = index; i + 1 < count_; ++i )
        {
            items_[ i ] = items_[ i + 1 ];
        }

        --count_;
        return true;
    }

    bool List::ensureCapacity( size_t needed )
    {
        if( needed <= capacity_ )
        {
            return true;
        }

        size_t newCapacity = capacity_ * 2;
        if( newCapacity < needed )
        {
            newCapacity = needed;
        }

        void * mem = alloc_.allocate( sizeof( Value ) * newCapacity );
        if( mem == nullptr )
        {
            return false;
        }

        Value * newItems = static_cast<Value *>( mem );
        for( size_t i = 0; i < count_; ++i )
        {
            newItems[ i ] = items_[ i ]; // Value is trivially copyable
        }

        alloc_.deallocate( items_ );
        items_ = newItems;
        capacity_ = newCapacity;
        return true;
    }

    bool List::set( size_t index, const Value & value )
    {
        if( index >= count_ )
        {
            return false;
        }
        items_[ index ] = value;
        return true;
    }

    void List::uniquefy()
    {
        size_t writeIdx = 0;
        for( size_t i = 0; i < count_; ++i )
        {
            bool dup = false;
            for( size_t j = 0; j < writeIdx; ++j )
            {
                if( valueEquals( items_[ i ], items_[ j ] ) )
                {
                    dup = true;
                    break;
                }
            }
            if( ! dup )
            {
                items_[ writeIdx++ ] = items_[ i ];
            }
        }
        count_ = writeIdx;
    }

    namespace
    {
        /** Numeric sort comparison for an Int or Float Value. */
        double numericKey( const Value & v )
        {
            return v.isInt() ? static_cast<double>( v.intValue() )
                             : static_cast<double>( v.floatValue() );
        }
    } // namespace

    void List::sort()
    {
        for( size_t i = 1; i < count_; ++i )
        {
            const Value key = items_[ i ];
            size_t j = i;

            while( j > 0 )
            {
                const Value & prev = items_[ j - 1 ];
                bool shouldSwap = false;

                if( ( prev.isInt() || prev.isFloat() ) && ( key.isInt() || key.isFloat() ) )
                {
                    shouldSwap = numericKey( prev ) > numericKey( key );
                }
                else if( prev.isString() && key.isString() )
                {
                    shouldSwap = prev.stringValue() > key.stringValue(); // byte-wise
                }
                else
                {
                    break; // incompatible types: leave `key` here, documented fallback
                }

                if( ! shouldSwap )
                {
                    break;
                }

                items_[ j ] = items_[ j - 1 ];
                --j;
            }

            items_[ j ] = key;
        }
    }

    namespace
    {
        /** Reverses the elements in items_[from .. to) in place. */
        void reverseRange( Value * items, size_t from, size_t to )
        {
            while( from < to )
            {
                --to;
                const Value t = items[ from ];
                items[ from ] = items[ to ];
                items[ to ] = t;
                ++from;
            }
        }
    } // namespace

    void List::reverse()
    {
        reverseRange( items_, 0, count_ );
    }

    void List::intersect( const List & other )
    {
        size_t writeIdx = 0;
        for( size_t i = 0; i < count_; ++i )
        {
            bool found = false;
            for( size_t j = 0; j < other.count_ && ! found; ++j )
            {
                found = valueEquals( items_[ i ], other.items_[ j ] );
            }
            if( found )
            {
                items_[ writeIdx++ ] = items_[ i ];
            }
        }
        count_ = writeIdx;
    }

    void List::appendAll( const List & other )
    {
        // Snapshot the count before appending: matches Java's list.addAll(), which takes a
        // defensive copy up front specifically so that self-append (`x.addAll(x)`/`x.union(x)`)
        // duplicates the original elements once rather than re-reading `other.count_` live --
        // which, when `other` aliases `this`, grows in lockstep with the loop and never
        // terminates.
        //
        // Each element is copied to a LOCAL before the call, rather than passed as
        // `other.items_[i]` directly: `add()` takes its parameter by const reference, and
        // when `other` aliases `this`, add()'s own ensureCapacity() may reallocate and free
        // the very buffer that reference points into (items_ == other.items_) before add()
        // reads it back out -- a self-append use-after-free. A local Value copy has no such
        // dependency on which buffer items_ currently is.
        const size_t n = other.count_;
        for( size_t i = 0; i < n; ++i )
        {
            const Value item = other.items_[ i ]; // copy BEFORE add() can reallocate other.items_
            add( item ); // grows as needed
        }
    }

    void List::rotate( int places )
    {
        if( count_ <= 1 )
        {
            return;
        }

        const int n = static_cast<int>( count_ );
        const int rightAmount = ( ( places % n ) + n ) % n; // normalize into [0, n)
        const int leftShift = ( n - rightAmount ) % n;

        if( leftShift == 0 )
        {
            return;
        }

        // Rotate left by `leftShift` via the classic three-reversal algorithm.
        reverseRange( items_, 0, static_cast<size_t>( leftShift ) );
        reverseRange( items_, static_cast<size_t>( leftShift ), count_ );
        reverseRange( items_, 0, count_ );
    }
} // namespace naxe
