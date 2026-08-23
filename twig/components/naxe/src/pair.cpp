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
/** \file pair.cpp
 * Implementation of the growable Pair (see pair.hpp).
 */
#include "naxe/pair.hpp"

#include <cassert>
#include <cstring>

#include "naxe/util.hpp"

namespace naxe
{
    Pair * Pair::create( size_t initialCapacity, const Allocator & alloc )
    {
        if( initialCapacity == 0 )
        {
            initialCapacity = 1;
        }

        void * objMem = alloc.allocate( sizeof( Pair ) );
        if( objMem == nullptr )
        {
            return nullptr;
        }

        void * slotsMem = alloc.allocate( sizeof( Slot ) * initialCapacity );
        if( slotsMem == nullptr )
        {
            alloc.deallocate( objMem );
            return nullptr;
        }

        return new( objMem ) Pair( initialCapacity, static_cast<Slot *>( slotsMem ), alloc );
    }

    void Pair::destroy( Pair * pair )
    {
        if( pair == nullptr )
        {
            return;
        }

        const Allocator alloc = pair->alloc_;
        pair->~Pair();
        alloc.deallocate( pair );
    }

    Pair::Pair( size_t capacity, Slot * slots, const Allocator & alloc )
        : slots_( slots ), count_( 0 ), capacity_( capacity ), alloc_( alloc )
    {
    }

    Pair::~Pair()
    {
        for( size_t i = 0; i < count_; ++i )
        {
            alloc_.deallocate( slots_[ i ].key );
        }
        alloc_.deallocate( slots_ );
    }

    bool Pair::set( std::string_view key, const Value & value )
    {
        for( size_t i = 0; i < count_; ++i )
        {
            if( util::asciiEqualsIgnoreCase( std::string_view( slots_[ i ].key, slots_[ i ].keyLen ), key ) )
            {
                slots_[ i ].value = value;
                return true;
            }
        }

        char * keyCopy = static_cast<char *>( alloc_.allocate( key.size() ) );
        if( keyCopy == nullptr )
        {
            return false;
        }

        if( ! ensureCapacity( count_ + 1 ) )
        {
            alloc_.deallocate( keyCopy );
            return false;
        }

        std::memcpy( keyCopy, key.data(), key.size() );
        slots_[ count_ ] = Slot{ keyCopy, key.size(), value };
        ++count_;
        return true;
    }

    const Value * Pair::get( std::string_view key ) const
    {
        for( size_t i = 0; i < count_; ++i )
        {
            if( util::asciiEqualsIgnoreCase( std::string_view( slots_[ i ].key, slots_[ i ].keyLen ), key ) )
            {
                return &slots_[ i ].value;
            }
        }
        return nullptr;
    }

    bool Pair::remove( std::string_view key )
    {
        for( size_t i = 0; i < count_; ++i )
        {
            if( util::asciiEqualsIgnoreCase( std::string_view( slots_[ i ].key, slots_[ i ].keyLen ), key ) )
            {
                alloc_.deallocate( slots_[ i ].key );
                for( size_t j = i; j + 1 < count_; ++j )
                {
                    slots_[ j ] = slots_[ j + 1 ];
                }
                --count_;
                return true;
            }
        }
        return false;
    }

    void Pair::clear()
    {
        for( size_t i = 0; i < count_; ++i )
        {
            alloc_.deallocate( slots_[ i ].key );
        }
        count_ = 0;
    }

    void Pair::putAll( const Pair & other )
    {
        for( size_t i = 0; i < other.count_; ++i )
        {
            set( other.keyAt( i ), other.valueAt( i ) );
        }
    }

    void Pair::intersect( const Pair & other )
    {
        // Free each dropped key as soon as it's dropped, before it can be silently
        // overwritten by a later kept slot's compaction copy -- deallocating tail slots
        // in a second pass (the previous approach) frees whichever key happens to still be
        // sitting at each tail index, which may by then be a second reference to a key a
        // surviving slot was already compacted to hold, causing a use-after-free.
        size_t writeIdx = 0;
        for( size_t i = 0; i < count_; ++i )
        {
            if( other.get( keyAt( i ) ) != nullptr )
            {
                if( writeIdx != i )
                {
                    slots_[ writeIdx ] = slots_[ i ];
                }
                ++writeIdx;
            }
            else
            {
                alloc_.deallocate( slots_[ i ].key );
            }
        }

        count_ = writeIdx;
    }

    std::string_view Pair::keyAt( size_t index ) const
    {
        assert( index < count_ );
        return std::string_view( slots_[ index ].key, slots_[ index ].keyLen );
    }

    const Value & Pair::valueAt( size_t index ) const
    {
        assert( index < count_ );
        return slots_[ index ].value;
    }

    bool Pair::ensureCapacity( size_t needed )
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

        void * mem = alloc_.allocate( sizeof( Slot ) * newCapacity );
        if( mem == nullptr )
        {
            return false;
        }

        Slot * newSlots = static_cast<Slot *>( mem );
        for( size_t i = 0; i < count_; ++i )
        {
            newSlots[ i ] = slots_[ i ]; // Slot is trivially copyable
        }

        alloc_.deallocate( slots_ );
        slots_ = newSlots;
        capacity_ = newCapacity;
        return true;
    }
} // namespace naxe
