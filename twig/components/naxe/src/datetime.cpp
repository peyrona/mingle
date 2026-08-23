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
/** \file datetime.cpp
 * Date/time implementation (see datetime.hpp).
 */
#include "naxe/datetime.hpp"

#include <cstdlib>
#include <ctime>

namespace naxe
{
    void initTimeZone()
    {
        // POSIX TZ string pulled directly from this machine's own zoneinfo database
        // (Europe/Madrid's RFC 8536 footer), not hand-derived — see the plan's date/time
        // decision. ESP-IDF newlib and glibc both implement standard tzset() against this
        // format at zero extra link cost.
        setenv( "TZ", "CET-1CEST,M3.5.0,M10.5.0/3", 1 );
        tzset();
    }

    int64_t nowEpochSeconds()
    {
        return static_cast<int64_t>( time( nullptr ) );
    }

    namespace
    {
        // Plain bool, not std::atomic<bool>: this flag only ever transitions false->true
        // exactly once per boot (TimeSync never un-syncs), from one writer task. A
        // reader racing the write sees the old or new value, never a torn one, and the
        // only consequence of a stale read is a rule's timeValid() check staying false a
        // few microseconds too long — not worth a real atomic for.
        bool g_timeValid = false;
    } // namespace

    void setTimeValid( bool valid )
    {
        g_timeValid = valid;
    }

    bool isTimeValid()
    {
        return g_timeValid;
    }

    LocalFields localFieldsFromEpoch( int64_t epochSeconds )
    {
        time_t t = static_cast<time_t>( epochSeconds );
        struct tm local;
        localtime_r( &t, &local );

        LocalFields f;
        f.year = local.tm_year + 1900;
        f.month = local.tm_mon + 1;
        f.day = local.tm_mday;
        f.weekday = local.tm_wday;
        f.hour = local.tm_hour;
        f.minute = local.tm_min;
        f.second = local.tm_sec;
        return f;
    }

    namespace
    {
        /** Parses exactly `digits` decimal digits starting at `pos`, advancing `pos`. */
        bool parseUint( std::string_view text, size_t & pos, int digits, int & out )
        {
            if( pos + static_cast<size_t>( digits ) > text.size() )
            {
                return false;
            }

            int value = 0;

            for( int i = 0; i < digits; ++i )
            {
                const char c = text[ pos + static_cast<size_t>( i ) ];
                if( c < '0' || c > '9' )
                {
                    return false;
                }
                value = value * 10 + ( c - '0' );
            }

            pos += static_cast<size_t>( digits );
            out = value;
            return true;
        }
    } // namespace

    bool parseHHMM( std::string_view text, int64_t & outEpochSeconds )
    {
        // "HH:MM" or "HH:MM:SS".
        size_t pos = 0;
        int hour = 0, minute = 0, second = 0;

        if( ! parseUint( text, pos, 2, hour ) ) return false;
        if( pos >= text.size() || text[ pos ] != ':' ) return false;
        ++pos;
        if( ! parseUint( text, pos, 2, minute ) ) return false;

        if( pos < text.size() && text[ pos ] == ':' )
        {
            ++pos;
            if( ! parseUint( text, pos, 2, second ) ) return false;
        }

        if( pos != text.size() ) return false; // trailing garbage
        if( hour > 23 || minute > 59 || second > 60 ) return false;

        time_t now = time( nullptr );
        struct tm local;
        localtime_r( &now, &local );

        local.tm_hour = hour;
        local.tm_min = minute;
        local.tm_sec = second;

        const time_t result = mktime( &local );
        if( result == static_cast<time_t>( -1 ) )
        {
            return false;
        }

        outEpochSeconds = static_cast<int64_t>( result );
        return true;
    }

    bool parseYMD( std::string_view text, int64_t & outEpochSeconds )
    {
        // "YYYY-MM-DD".
        size_t pos = 0;
        int year = 0, month = 0, day = 0;

        if( ! parseUint( text, pos, 4, year ) ) return false;
        if( pos >= text.size() || text[ pos ] != '-' ) return false;
        ++pos;
        if( ! parseUint( text, pos, 2, month ) ) return false;
        if( pos >= text.size() || text[ pos ] != '-' ) return false;
        ++pos;
        if( ! parseUint( text, pos, 2, day ) ) return false;

        if( pos != text.size() ) return false; // trailing garbage
        if( month < 1 || month > 12 || day < 1 || day > 31 ) return false;

        struct tm local {};
        local.tm_year = year - 1900;
        local.tm_mon = month - 1;
        local.tm_mday = day;
        local.tm_isdst = -1;

        const time_t result = mktime( &local );
        if( result == static_cast<time_t>( -1 ) )
        {
            return false;
        }

        outEpochSeconds = static_cast<int64_t>( result );
        return true;
    }
} // namespace naxe
