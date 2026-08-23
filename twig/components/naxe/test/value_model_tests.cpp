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
/** \file value_model_tests.cpp
 *
 * Host/on-device tests for the value model (Phase 2): Arena, Allocator, Value (with the
 * Int type), List and Pair. Covers the behaviours the rest of the engine depends on,
 * including the integer-precision and heap-growth cases that motivated this rewrite.
 */
#include "naxe/value_model_tests.hpp"

#include <cstdio>
#include <cstring>
#include <string_view>
#include <type_traits>

#include "naxe/arena.hpp"
#include "naxe/list.hpp"
#include "naxe/memory.hpp"
#include "naxe/pair.hpp"
#include "naxe/util.hpp"
#include "naxe/value.hpp"

namespace
{
    int failures = 0;

    /** Records a failed check (no exception mechanism: count and report). */
    void check( bool condition, const char * message )
    {
        if( ! condition )
        {
            ++failures;
            std::printf( "FAIL: %s\n", message );
        }
    }

    /** A non-trivial type used to prove Arena::emplace runs a constructor. */
    struct Box
    {
        int value;

        explicit Box( int v ) : value( v ) {}
    };

    /** Reusable arena buffer for the arena tests. */
    constexpr size_t kArenaSize = 1024;
    alignas( 8 ) uint8_t arenaBuffer[ kArenaSize ];

    void testArena()
    {
        naxe::Arena arena( arenaBuffer, kArenaSize );

        // Basic alloc returns a pointer and advances the offset (8-byte aligned).
        void * p1 = arena.alloc( 1 );
        check( p1 != nullptr, "arena.alloc(1) returns non-null" );
        check( arena.used() == 8, "arena.alloc(1) rounds up to 8-byte alignment" );

        // Alignment holds for a larger, misaligned request.
        void * p2 = arena.alloc( 13 );
        check( p2 != nullptr, "arena.alloc(13) returns non-null" );
        check( ( reinterpret_cast<uintptr_t>( p2 ) & 7u ) == 0u, "arena pointers are 8-byte aligned" );

        // reset() reuses the same buffer; high-water mark survives a reset.
        const size_t highWater = arena.highWaterMark();
        check( highWater > 0, "arena records a non-zero high-water mark" );
        arena.reset();
        check( arena.used() == 0, "arena.reset() zeroes used()" );
        void * p3 = arena.alloc( 1 );
        check( p3 == p1, "arena.reset() reuses the buffer from the start" );
        check( arena.highWaterMark() == highWater, "high-water mark is not cleared by reset()" );

        // Exhaustion returns nullptr rather than growing or crashing.
        naxe::Arena tiny( arenaBuffer, 16 );
        check( tiny.alloc( 17 ) == nullptr, "arena.alloc past capacity returns nullptr" );

        // emplace<T> runs a real constructor; alloc<T> does not.
        naxe::Arena a2( arenaBuffer, kArenaSize );
        Box * box = a2.emplace<Box>( 42 );
        check( box != nullptr, "arena.emplace<Box> returns non-null" );
        check( box->value == 42, "arena.emplace<Box> runs the constructor" );

        // Trivial-copyability contract (the whole design rests on this).
        static_assert( std::is_trivially_copyable_v<naxe::Value>, "Value must be trivially copyable" );
    }

    void testValueScalars()
    {
        // Int construction/access.
        naxe::Value i = naxe::Value::makeInt( 12345 );
        check( i.type() == naxe::ValueType::Int, "makeInt type is Int" );
        check( i.isInt(), "isInt()" );
        check( i.intValue() == 12345, "intValue round-trips" );

        // Integer precision: values beyond float32's 24-bit mantissa stay exact.
        const int64_t big = 2000000001;
        const int64_t big2 = 2000000000;
        naxe::Value ib = naxe::Value::makeInt( big );
        check( ib.intValue() == big, "int64 exact at 2e9" );
        check( naxe::valueEquals( ib, naxe::Value::makeInt( big2 ) ) == false,
               "Int 2000000001 != 2000000000 (float32 would conflate them)" );

        // Float.
        naxe::Value f = naxe::Value::makeFloat( 1.5f );
        check( f.isFloat() && f.floatValue() == 1.5f, "makeFloat round-trips" );

        // Bool.
        naxe::Value b = naxe::Value::makeBool( true );
        check( b.isBool() && b.boolValue(), "makeBool round-trips" );

        // String is a non-owning view.
        const char * text = "hello";
        naxe::Value s = naxe::Value::makeString( std::string_view( text, 5 ) );
        check( s.isString() && s.stringValue() == "hello", "makeString view round-trips" );

        // DateTime is a distinct int64 type.
        naxe::Value dt = naxe::Value::makeDateTime( 1750000000 );
        check( dt.isDateTime() && dt.dateTimeValue() == 1750000000, "makeDateTime round-trips" );
        check( dt.type() != naxe::ValueType::Int, "DateTime is not an Int" );
    }

    void testValueEquality()
    {
        // Same-type scalar equality.
        check( naxe::valueEquals( naxe::Value::makeInt( 7 ), naxe::Value::makeInt( 7 ) ), "Int == Int" );
        check( ! naxe::valueEquals( naxe::Value::makeInt( 7 ), naxe::Value::makeInt( 8 ) ), "Int != Int" );

        // Int vs Float promotes to float.
        check( naxe::valueEquals( naxe::Value::makeInt( 5 ), naxe::Value::makeFloat( 5.0f ) ), "Int(5) == Float(5.0)" );

        // String equality is case-insensitive (Une semantics).
        check( naxe::valueEquals( naxe::Value::makeString( "AbC" ), naxe::Value::makeString( "aBc" ) ),
               "String == is case-insensitive" );

        // Different unrelated types are not equal.
        check( ! naxe::valueEquals( naxe::Value::makeInt( 1 ), naxe::Value::makeBool( true ) ), "Int != Bool" );
        check( ! naxe::valueEquals( naxe::Value::makeString( "x" ), naxe::Value::makeDateTime( 0 ) ), "String != DateTime" );

        // DateTime compares exactly (never through float32).
        check( naxe::valueEquals( naxe::Value::makeDateTime( 2000000001 ), naxe::Value::makeDateTime( 2000000001 ) ),
               "DateTime == DateTime exact at 2e9" );
    }

    void testTruthiness()
    {
        check( naxe::isTruthy( naxe::Value::makeBool( true ) ), "Bool true" );
        check( ! naxe::isTruthy( naxe::Value::makeBool( false ) ), "Bool false" );
        check( naxe::isTruthy( naxe::Value::makeInt( 1 ) ), "Int 1" );
        check( ! naxe::isTruthy( naxe::Value::makeInt( 0 ) ), "Int 0" );
        check( naxe::isTruthy( naxe::Value::makeFloat( 0.5f ) ), "Float non-zero" );
        check( ! naxe::isTruthy( naxe::Value::makeFloat( 0.0f ) ), "Float 0" );
        check( naxe::isTruthy( naxe::Value::makeString( "x" ) ), "String non-empty" );
        check( ! naxe::isTruthy( naxe::Value::makeString( "" ) ), "String empty" );
        check( naxe::isTruthy( naxe::Value::makeDateTime( 0 ) ), "DateTime always truthy" );
    }

    void testList()
    {
        naxe::List * list = naxe::List::create( 2 ); // small to force growth
        check( list != nullptr, "List::create returns non-null" );
        check( list->empty(), "new List is empty" );
        check( list->capacity() == 2, "List respects initial capacity" );

        // Grow beyond initial capacity; reallocation must preserve data.
        for( int n = 0; n < 100; ++n )
        {
            check( list->add( naxe::Value::makeInt( n ) ), "List::add grows" );
        }
        check( list->size() == 100, "List size after 100 adds" );
        check( list->capacity() >= 100, "List capacity grew past 100" );

        // Indexing and iteration.
        check( list->operator[]( 0 ).intValue() == 0, "List[0]" );
        check( list->operator[]( 99 ).intValue() == 99, "List[99]" );
        int sum = 0;
        for( const naxe::Value & v : *list ) sum += static_cast<int>( v.intValue() );
        check( sum == 4950, "List range-for sum" );

        // Remove (shift left).
        check( list->remove( 0 ), "List::remove(0)" );
        check( list->size() == 99, "List size after remove" );
        check( list->operator[]( 0 ).intValue() == 1, "List shifts left on remove" );
        check( ! list->remove( 1000 ), "List::remove out of range returns false" );

        // clear keeps storage, empties content.
        const size_t capBefore = list->capacity();
        list->clear();
        check( list->empty(), "List::clear empties" );
        check( list->capacity() == capBefore, "List::clear keeps capacity" );

        naxe::List::destroy( list );
    }

    void testPair()
    {
        naxe::Pair * pair = naxe::Pair::create( 2 );
        check( pair != nullptr, "Pair::create returns non-null" );
        check( pair->empty(), "new Pair is empty" );

        // Set + get, case-insensitive key match.
        check( pair->set( "Key", naxe::Value::makeInt( 1 ) ), "Pair::set new key" );
        check( pair->size() == 1, "Pair size after set" );

        const naxe::Value * v = pair->get( "key" ); // different case
        check( v != nullptr, "Pair::get is case-insensitive" );
        check( v != nullptr && v->intValue() == 1, "Pair::get returns stored value" );

        // Overwrite an existing key (no growth, no duplicate).
        check( pair->set( "KEY", naxe::Value::makeInt( 2 ) ), "Pair::set overwrite" );
        check( pair->size() == 1, "Pair overwrite keeps size" );
        check( pair->get( "key" )->intValue() == 2, "Pair overwrite updates value" );

        // Grow past initial capacity.
        for( int n = 0; n < 20; ++n )
        {
            char buf[ 16 ];
            std::snprintf( buf, sizeof( buf ), "key%d", n );
            check( pair->set( std::string_view( buf ), naxe::Value::makeInt( n ) ), "Pair::set grows" );
        }
        check( pair->size() == 21, "Pair size after growth" );

        // Key iteration exposes the copied, self-contained keys.
        bool found = false;
        for( size_t i = 0; i < pair->size(); ++i )
        {
            if( naxe::util::asciiEqualsIgnoreCase( pair->keyAt( i ), "key7" ) )
            {
                found = true;
                check( pair->valueAt( i ).intValue() == 7, "Pair keyAt/valueAt" );
            }
        }
        check( found, "Pair iteration finds a key" );

        // Remove.
        check( pair->remove( "KEY" ), "Pair::remove" );
        check( pair->get( "key" ) == nullptr, "Pair::remove then get is null" );
        check( pair->size() == 20, "Pair size after remove" );
        check( ! pair->remove( "nope" ), "Pair::remove missing returns false" );

        // clear releases keys but keeps storage.
        const size_t capBefore = pair->capacity();
        pair->clear();
        check( pair->empty(), "Pair::clear empties" );
        check( pair->capacity() == capBefore, "Pair::clear keeps capacity" );

        naxe::Pair::destroy( pair );
    }

    void testValueContainers()
    {
        // A List Value references its List and reports the right type.
        naxe::List * list = naxe::List::create();
        list->add( naxe::Value::makeInt( 1 ) );
        naxe::Value lv = naxe::Value::makeList( list );
        check( lv.isList() && lv.listValue() == list, "Value::makeList references the List" );
        check( naxe::isTruthy( lv ), "non-empty List Value is truthy" );

        // A Pair Value references its Pair.
        naxe::Pair * pair = naxe::Pair::create();
        naxe::Value pv = naxe::Value::makePair( pair );
        check( pv.isPair() && pv.pairValue() == pair, "Value::makePair references the Pair" );
        check( ! naxe::isTruthy( pv ), "empty Pair Value is falsy" );

        // List/Pair equality is pointer identity.
        naxe::List * list2 = naxe::List::create();
        check( naxe::valueEquals( lv, naxe::Value::makeList( list ) ), "List identity equality (same)" );
        check( ! naxe::valueEquals( lv, naxe::Value::makeList( list2 ) ), "List identity inequality (different)" );

        naxe::List::destroy( list2 );
        naxe::Pair::destroy( pair );
        naxe::List::destroy( list );
    }
} // namespace

namespace naxe
{
    int runValueModelTests()
    {
        failures = 0;

        testArena();
        testValueScalars();
        testValueEquality();
        testTruthiness();
        testList();
        testPair();
        testValueContainers();

        if( failures == 0 )
        {
            std::printf( "[naxe] value-model tests: PASS\n" );
        }
        else
        {
            std::printf( "[naxe] value-model tests: %d FAILURE(S)\n", failures );
        }

        return failures;
    }
} // namespace naxe
