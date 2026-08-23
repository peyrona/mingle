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
#include "stick/logger.hpp"

#include <cstdio>
#include <cstring>

#include "naxe/datetime.hpp"

namespace stick
{
    namespace
    {
        /** Maps a log level to its printable name.
         *
         * @param level The log level.
         * @return A constant string naming the level, or "?" for unknown levels.
         */
        const char * levelName( LogLevel level )
        {
            switch( level )
            {
                case LogLevel::Info:    return "INFO";
                case LogLevel::Warning: return "WARN";
                case LogLevel::Severe:  return "SEVERE";
            }
            return "?";
        }
    }

    /** Definition of stick::Logger::Logger(); see logger.hpp for the contract. */
    Logger::Logger() : writeIndex_( 0 ), count_( 0 ), totalLogged_( 0 )
    {
    }

    /** Definition of stick::Logger::log(); see logger.hpp for the contract. */
    void Logger::log( LogLevel level, const char * fmt, ... )
    {
        va_list args;
        va_start( args, fmt );
        logV( level, fmt, args );
        va_end( args );
    }

    /** Definition of stick::Logger::logV(); see logger.hpp for the contract. */
    void Logger::logV( LogLevel level, const char * fmt, va_list args )
    {
        char messageCopy[ sizeof( LogEntry::message ) ];

        while( busy_.test_and_set( std::memory_order_acquire ) )
        {
            // spin -- see busy_'s header note on why this isn't a FreeRTOS mutex
        }

        LogEntry & e = entries_[ writeIndex_ ];
        e.level = level;
        e.when = naxe::nowEpochSeconds();
        std::vsnprintf( e.message, sizeof( e.message ), fmt, args );
        std::memcpy( messageCopy, e.message, sizeof( messageCopy ) );

        writeIndex_ = ( writeIndex_ + 1 ) % kCapacity;
        if( count_ < kCapacity ) ++count_;
        ++totalLogged_;

        busy_.clear( std::memory_order_release );

        // Serial I/O happens AFTER the unlock, on the local copy -- printf() is slow
        // enough that doing it under the lock would turn a "few field writes" critical
        // section into an I/O-bound one, exactly what a spinlock must avoid.
        std::printf( "[stick][%s] %s\n", levelName( level ), messageCopy );
    }

    /** Definition of stick::Logger::at(); see logger.hpp for the contract. */
    const LogEntry & Logger::at( uint32_t indexFromOldest ) const
    {
        uint32_t oldest = ( count_ < kCapacity ) ? 0 : writeIndex_;
        return entries_[ ( oldest + indexFromOldest ) % kCapacity ];
    }

    /** Definition of stick::logger(); see logger.hpp for the contract. */
    Logger & logger()
    {
        static Logger instance;
        return instance;
    }
}
