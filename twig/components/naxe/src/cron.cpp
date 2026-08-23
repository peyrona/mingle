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
/** \file cron.cpp
 * Cron scheduler implementation (see cron.hpp).
 */
#include "naxe/cron.hpp"

#include <cctype>
#include <cstdarg>
#include <cstdio>
#include <cstdlib>
#include <cstring>
#include <ctime>

namespace naxe
{
    namespace
    {
        constexpr int kLastDayMarker = 31;

        // ---- small string helpers (kept local -- see util.hpp's "single-use helper
        // stays in that file" rule; these formats are specific to Cron's own config
        // grammar, not shared elsewhere) ----

        std::string_view trim( std::string_view s )
        {
            while( ! s.empty() && ( s.front() == ' ' || s.front() == '\t' ) ) s.remove_prefix( 1 );
            while( ! s.empty() && ( s.back() == ' ' || s.back() == '\t' ) ) s.remove_suffix( 1 );
            return s;
        }

        /** Parses a signed decimal integer from `text` (must be entirely consumed). */
        bool parseIntToken( std::string_view text, int & out )
        {
            if( text.empty() || text.size() >= 32 ) return false;

            char tmp[ 32 ];
            std::memcpy( tmp, text.data(), text.size() );
            tmp[ text.size() ] = '\0';

            char * endPtr = nullptr;
            const long v = std::strtol( tmp, &endPtr, 10 );

            if( endPtr != tmp + text.size() ) return false;

            out = static_cast<int>( v );
            return true;
        }

        /** Parses "H", "H:M" or "H:M:S" (any extra ':'-separated fields beyond the
         *  third are ignored, matching Java's `toTime()` reading `as[0..2]` off a plain
         *  `String.split(":")` and never checking its length). */
        bool parseTimeOfDay( std::string_view text, int32_t & outSecondsOfDay )
        {
            if( text.empty() ) return false;

            std::string_view fields[ 3 ];
            int fieldCount = 0;
            size_t pos = 0;

            for( ;; )
            {
                const size_t colon = text.find( ':', pos );
                const std::string_view field =
                    text.substr( pos, colon == std::string_view::npos ? std::string_view::npos : colon - pos );

                if( fieldCount < 3 ) fields[ fieldCount++ ] = field;

                if( colon == std::string_view::npos ) break;
                pos = colon + 1;
            }

            int hour = 0, minute = 0, second = 0;

            if( ! parseIntToken( fields[ 0 ], hour ) ) return false;
            if( fieldCount > 1 && ! parseIntToken( fields[ 1 ], minute ) ) return false;
            if( fieldCount > 2 && ! parseIntToken( fields[ 2 ], second ) ) return false;

            if( hour < 0 || hour > 23 || minute < 0 || minute > 59 || second < 0 || second > 59 ) return false;

            outSecondsOfDay = hour * 3600 + minute * 60 + second;
            return true;
        }

        /** Parses "YYYY-MM-DD" (java.time.LocalDate.parse's ISO_LOCAL_DATE shape,
         *  which is what Java's `date(String)` constructor uses -- see date.java). */
        bool parseDigits( std::string_view text, size_t & pos, int digits, int & out )
        {
            if( pos + static_cast<size_t>( digits ) > text.size() ) return false;

            int value = 0;

            for( int i = 0; i < digits; ++i )
            {
                const char c = text[ pos + static_cast<size_t>( i ) ];
                if( c < '0' || c > '9' ) return false;
                value = value * 10 + ( c - '0' );
            }

            pos += static_cast<size_t>( digits );
            out = value;
            return true;
        }

        bool isLeapYear( int y ) { return ( y % 4 == 0 && y % 100 != 0 ) || y % 400 == 0; }

        int lengthOfMonth( int y, int m )
        {
            static const int kDays[] = { 31, 28, 31, 30, 31, 30, 31, 31, 30, 31, 30, 31 };
            if( m == 2 && isLeapYear( y ) ) return 29;
            return kDays[ m - 1 ];
        }

        bool parseIsoDate( std::string_view text, Cron::CivilDate & out )
        {
            size_t pos = 0;
            int year = 0, month = 0, day = 0;

            if( ! parseDigits( text, pos, 4, year ) ) return false;
            if( pos >= text.size() || text[ pos ] != '-' ) return false;
            ++pos;
            if( ! parseDigits( text, pos, 2, month ) ) return false;
            if( pos >= text.size() || text[ pos ] != '-' ) return false;
            ++pos;
            if( ! parseDigits( text, pos, 2, day ) ) return false;
            if( pos != text.size() ) return false;

            if( month < 1 || month > 12 ) return false;
            if( day < 1 || day > lengthOfMonth( year, month ) ) return false;

            out = Cron::CivilDate{ year, month, day };
            return true;
        }

        template <typename T>
        void insertionSort( T * arr, uint32_t n )
        {
            for( uint32_t i = 1; i < n; ++i )
            {
                const T key = arr[ i ];
                uint32_t j = i;
                while( j > 0 && arr[ j - 1 ] > key )
                {
                    arr[ j ] = arr[ j - 1 ];
                    --j;
                }
                arr[ j ] = key;
            }
        }

        // ---- calendar math: mktime()/localtime_r() round-trips, never a hand-rolled
        // civil-calendar algorithm -- see cron.hpp's header comment. Every anchor uses
        // local NOON, not midnight, so a same-day DST transition can't shift the
        // normalized date backward/forward by a day. ----

        Cron::CivilDate addDays( const Cron::CivilDate & d, int days )
        {
            struct tm t {};
            t.tm_year = d.year - 1900;
            t.tm_mon = d.month - 1;
            t.tm_mday = d.day + days;
            t.tm_hour = 12;
            t.tm_isdst = -1;
            std::mktime( &t ); // normalizes t in place (also fills tm_wday, unused here)
            return Cron::CivilDate{ t.tm_year + 1900, t.tm_mon + 1, t.tm_mday };
        }

        /** ISO weekday: 1=Monday .. 7=Sunday. */
        int isoWeekday( const Cron::CivilDate & d )
        {
            struct tm t {};
            t.tm_year = d.year - 1900;
            t.tm_mon = d.month - 1;
            t.tm_mday = d.day;
            t.tm_hour = 12;
            t.tm_isdst = -1;
            std::mktime( &t );
            return t.tm_wday == 0 ? 7 : t.tm_wday; // tm_wday: 0=Sunday..6=Saturday
        }

        int64_t noonEpoch( const Cron::CivilDate & d )
        {
            struct tm t {};
            t.tm_year = d.year - 1900;
            t.tm_mon = d.month - 1;
            t.tm_mday = d.day;
            t.tm_hour = 12;
            t.tm_isdst = -1;
            return static_cast<int64_t>( std::mktime( &t ) );
        }

        /** The real epoch second for an Instant's actual (date, time-of-day) -- unlike
         *  `noonEpoch()`, this is a direct `mktime()` call at the real target hour, so it
         *  stays exact even when a DST transition falls between local midnight and this
         *  instant's own time-of-day on the same calendar day. */
        int64_t instantEpoch( const Cron::CivilDate & d, int32_t secondsOfDay )
        {
            struct tm t {};
            t.tm_year = d.year - 1900;
            t.tm_mon = d.month - 1;
            t.tm_mday = d.day;
            t.tm_hour = secondsOfDay / 3600;
            t.tm_min = ( secondsOfDay % 3600 ) / 60;
            t.tm_sec = secondsOfDay % 60;
            t.tm_isdst = -1;
            return static_cast<int64_t>( std::mktime( &t ) );
        }

        /** Whole days between two calendar dates (b - a), matching
         *  `ChronoUnit.DAYS.between`. Both anchored at local noon (see file note above),
         *  so the raw epoch difference is a multiple of 86400 seconds except within
         *  +/-1h..2h of it across a DST transition strictly between the two dates --
         *  rounded to the nearest day, which is always the intended exact count. */
        int64_t daysBetween( const Cron::CivilDate & a, const Cron::CivilDate & b )
        {
            const int64_t diff = noonEpoch( b ) - noonEpoch( a );
            return ( diff >= 0 ? diff + 43200 : diff - 43200 ) / 86400;
        }

        bool civilLess( const Cron::CivilDate & a, const Cron::CivilDate & b )
        {
            if( a.year != b.year ) return a.year < b.year;
            if( a.month != b.month ) return a.month < b.month;
            return a.day < b.day;
        }

        int64_t totalMonths( const Cron::CivilDate & d )
        {
            return static_cast<int64_t>( d.year ) * 12 + ( d.month - 1 );
        }

        int64_t floorDiv( int64_t a, int64_t b )
        {
            int64_t q = a / b;
            if( ( a % b != 0 ) && ( ( a < 0 ) != ( b < 0 ) ) ) --q;
            return q;
        }

        /** `date.plusMonths(months).withDayOfMonth(1)` -- every real call site resets
         *  the day to 1 immediately after adding months, so this skips Java's
         *  intermediate day-of-month clamp entirely (it would be overwritten anyway). */
        Cron::CivilDate addMonthsSetDay1( const Cron::CivilDate & d, int64_t months )
        {
            const int64_t tm_ = totalMonths( d ) + months;
            const int64_t y = floorDiv( tm_, 12 );
            const int m = static_cast<int>( tm_ - y * 12 ) + 1;
            return Cron::CivilDate{ static_cast<int32_t>( y ), m, 1 };
        }

        /** `ChronoUnit.MONTHS.between(d1, d2)`: whole months, floored by day-of-month
         *  (java.time's own algorithm -- total-months delta, adjusted by one if the
         *  day-of-month comparison disagrees with the delta's sign). */
        int64_t monthsBetween( const Cron::CivilDate & d1, const Cron::CivilDate & d2 )
        {
            int64_t months = totalMonths( d2 ) - totalMonths( d1 );
            const int64_t y = floorDiv( totalMonths( d1 ) + months, 12 );
            const int m = static_cast<int>( totalMonths( d1 ) + months - y * 12 ) + 1;
            const int day = d1.day < lengthOfMonth( static_cast<int>( y ), m ) ? d1.day : lengthOfMonth( static_cast<int>( y ), m );
            const Cron::CivilDate anchor{ static_cast<int32_t>( y ), m, day };

            if( months > 0 && civilLess( d2, anchor ) ) --months;
            else if( months < 0 && civilLess( anchor, d2 ) ) ++months;

            return months;
        }
    } // namespace

    int64_t Cron::defaultNow( void * ) { return naxe::nowEpochSeconds(); }

    // ---- Instant helpers ----

    namespace
    {
        bool instantLess( const Cron::CivilDate & ad, int32_t at, const Cron::CivilDate & bd, int32_t bt )
        {
            if( ! ( ad.year == bd.year && ad.month == bd.month && ad.day == bd.day ) ) return civilLess( ad, bd );
            return at < bt;
        }
    } // namespace

    Cron::Instant Cron::instantFromEpoch( int64_t epochSeconds ) const
    {
        const LocalFields f = localFieldsFromEpoch( epochSeconds );
        Instant i;
        i.date = CivilDate{ f.year, f.month, f.day };
        i.secondsOfDay = f.hour * 3600 + f.minute * 60 + f.second;
        return i;
    }

    // ---- construction / parsing ----

    Cron::Cron( const Pair & args, NowEpochSecondsFn nowFn, void * nowCtx )
    {
        configure( args, nowFn, nowCtx );
    }

    void Cron::configure( const Pair & args, NowEpochSecondsFn nowFn, void * nowCtx )
    {
        *this = Cron(); // reset to a clean default-constructed slate (see configure()'s doc comment)

        nowFn_ = nowFn;
        nowCtx_ = nowCtx;

        if( args.get( "start" ) != nullptr ) hasStart_ = parseInstant( args, "start", start_ );
        if( args.get( "stop" ) != nullptr ) hasStop_ = parseInstant( args, "stop", stop_ );

        parseTimeList( args );
        parseNumberList( args, "dow", 0, 7, dow_, dowCount_, kMaxDow, true );
        parseNumberList( args, "dom", 1, 31, dom_, domCount_, kMaxDom, false );
        parseNumberList( args, "month", 1, 12, month_, monthCount_, kMaxMonth, false );

        mode_ = parseMode( args );
        every_ = parseEvery( args );

        startDate_ = hasStart_ ? start_.date : instantFromEpoch( nowFn_( nowCtx_ ) ).date;

        validateStartStopOrder();
        validateOneTimeEvent();
        validateRepeatingEvent();
        validateModeSpecificConstraints();
    }

    std::string_view Cron::errorAt( uint32_t index ) const
    {
        return std::string_view( errors_[ index ].c_str(), errors_[ index ].size() );
    }

    void Cron::addError( const char * fmt, ... )
    {
        if( errorCount_ >= kMaxErrors ) return;

        char buf[ 96 ];
        va_list args;
        va_start( args, fmt );
        std::vsnprintf( buf, sizeof( buf ), fmt, args );
        va_end( args );

        errors_[ errorCount_ ] = buf;
        ++errorCount_;
    }

    bool Cron::parseInstant( const Pair & args, std::string_view key, Instant & out )
    {
        const Value * raw = args.get( key );
        const std::string_view text = ( raw != nullptr && raw->isString() ) ? raw->stringValue() : std::string_view();

        const size_t comma = text.find( ',' );

        if( comma == std::string_view::npos || text.find( ',', comma + 1 ) != std::string_view::npos )
        {
            addError( "Invalid '%.*s' value: expected format is 'date,time'",
                     static_cast<int>( key.size() ), key.data() );
            return false;
        }

        const std::string_view datePart = trim( text.substr( 0, comma ) );
        const std::string_view timePart = trim( text.substr( comma + 1 ) );

        CivilDate date;
        int32_t secondsOfDay;

        if( ! parseIsoDate( datePart, date ) || ! parseTimeOfDay( timePart, secondsOfDay ) )
        {
            addError( "Invalid '%.*s' value: invalid date or time",
                     static_cast<int>( key.size() ), key.data() );
            return false;
        }

        out = Instant{ date, secondsOfDay };
        return true;
    }

    bool Cron::parseTimeList( const Pair & args )
    {
        timeCount_ = 0;
        const Value * raw = args.get( "time" );
        if( raw == nullptr ) return true;

        const std::string_view text = raw->isString() ? raw->stringValue() : std::string_view();
        size_t pos = 0;

        while( pos <= text.size() )
        {
            const size_t comma = text.find( ',', pos );
            const std::string_view token =
                trim( text.substr( pos, comma == std::string_view::npos ? std::string_view::npos : comma - pos ) );

            int32_t secondsOfDay;

            if( ! parseTimeOfDay( token, secondsOfDay ) )
            {
                addError( "Invalid 'time' value: '%.*s' is not a valid time",
                         static_cast<int>( token.size() ), token.data() );
                timeCount_ = 0;
                return false;
            }

            if( timeCount_ < kMaxTimes ) times_[ timeCount_++ ] = secondsOfDay;

            if( comma == std::string_view::npos ) break;
            pos = comma + 1;
        }

        insertionSort( times_, timeCount_ );
        return true;
    }

    bool Cron::parseNumberList( const Pair & args, std::string_view key, int minVal, int maxVal,
                                int16_t * out, uint32_t & outCount, uint32_t maxCount, bool mapZeroToSeven )
    {
        outCount = 0;
        const Value * raw = args.get( key );
        if( raw == nullptr ) return true;

        const std::string_view text = raw->isString() ? raw->stringValue() : std::string_view();
        size_t pos = 0;

        while( pos <= text.size() )
        {
            const size_t comma = text.find( ',', pos );
            const std::string_view token =
                trim( text.substr( pos, comma == std::string_view::npos ? std::string_view::npos : comma - pos ) );

            int value;

            if( ! parseIntToken( token, value ) )
            {
                addError( "Invalid '%.*s' value: not a valid number", static_cast<int>( key.size() ), key.data() );
                outCount = 0;
                return false;
            }

            if( value < minVal || value > maxVal )
            {
                addError( "Invalid '%.*s' value: %d is out of range [%d-%d]",
                         static_cast<int>( key.size() ), key.data(), value, minVal, maxVal );
                outCount = 0;
                return false;
            }

            if( mapZeroToSeven && value == 0 ) value = 7;
            if( outCount < maxCount ) out[ outCount++ ] = static_cast<int16_t>( value );

            if( comma == std::string_view::npos ) break;
            pos = comma + 1;
        }

        insertionSort( out, outCount );
        return true;
    }

    char Cron::parseMode( const Pair & args )
    {
        const Value * raw = args.get( "mode" );
        if( raw == nullptr ) return 0;

        if( ! raw->isString() )
        {
            addError( "Invalid 'mode' value: must be 'Daily', 'Weekly', 'Monthly' or 'Yearly'" );
            return 0;
        }

        const std::string_view s = trim( raw->stringValue() );

        if( s.empty() )
        {
            addError( "Invalid 'mode' value: mode cannot be empty" );
            return 0;
        }

        const char c = static_cast<char>( std::tolower( static_cast<unsigned char>( s.front() ) ) );

        if( c != 'd' && c != 'w' && c != 'm' && c != 'y' )
        {
            addError( "Invalid 'mode' value: must be 'Daily', 'Weekly', 'Monthly' or 'Yearly'" );
            return 0;
        }

        return c;
    }

    int Cron::parseEvery( const Pair & args )
    {
        if( mode_ == 0 ) return 0;

        int value = 1;
        const Value * raw = args.get( "every" );

        if( raw != nullptr )
        {
            if( raw->isInt() )
            {
                value = static_cast<int>( raw->intValue() );
            }
            else if( raw->isFloat() )
            {
                value = static_cast<int>( raw->floatValue() );
            }
            else if( raw->isString() && ! parseIntToken( trim( raw->stringValue() ), value ) )
            {
                addError( "Invalid 'every' value: not a valid number" );
                return 0;
            }
        }

        if( value < 1 )
        {
            addError( "Invalid 'every' value: must be 1 or greater for repeating events" );
            return 0;
        }

        return value;
    }

    // ---- validation ----

    void Cron::validateStartStopOrder()
    {
        if( hasStart_ && hasStop_ &&
            ! instantLess( start_.date, start_.secondsOfDay, stop_.date, stop_.secondsOfDay ) )
        {
            addError( "Invalid configuration: 'start' must be before 'stop'" );
        }
    }

    void Cron::validateOneTimeEvent()
    {
        if( every_ == 0 && ! hasStart_ )
        {
            addError( "Invalid configuration: one-time event requires 'start' to be specified" );
        }
    }

    void Cron::validateRepeatingEvent()
    {
        if( every_ > 0 && timeCount_ == 0 )
        {
            addError( "Invalid configuration: repeating event requires 'time' to be specified" );
        }
    }

    void Cron::validateModeSpecificConstraints()
    {
        switch( mode_ )
        {
            case 'd':
                if( dowCount_ > 0 ) addError( "Invalid configuration: 'Daily' mode cannot specify 'dow'" );
                if( domCount_ > 0 ) addError( "Invalid configuration: 'Daily' mode cannot specify 'dom'" );
                if( monthCount_ > 0 ) addError( "Invalid configuration: 'Daily' mode cannot specify 'month'" );
                break;

            case 'w':
                if( dowCount_ == 0 ) addError( "Invalid configuration: 'Weekly' mode requires 'dow' to be specified" );
                if( domCount_ > 0 ) addError( "Invalid configuration: 'Weekly' mode cannot specify 'dom'" );
                if( monthCount_ > 0 ) addError( "Invalid configuration: 'Weekly' mode cannot specify 'month'" );
                if( every_ < 1 ) addError( "Invalid configuration: 'Weekly' mode requires 'every' to be at least 1" );
                break;

            case 'm':
                if( domCount_ == 0 ) addError( "Invalid configuration: 'Monthly' mode requires 'dom' to be specified" );
                if( dowCount_ > 0 ) addError( "Invalid configuration: 'Monthly' mode cannot specify 'dow'" );
                if( monthCount_ > 0 ) addError( "Invalid configuration: 'Monthly' mode cannot specify 'month'" );
                break;

            case 'y':
                if( domCount_ == 0 || monthCount_ == 0 )
                    addError( "Invalid configuration: 'Yearly' mode requires both 'dom' and 'month' to be specified" );
                if( dowCount_ > 0 ) addError( "Invalid configuration: 'Yearly' mode cannot specify 'dow'" );
                break;

            case 0:
                break;

            default:
                addError( "Internal error: unknown mode '%c'", mode_ );
        }
    }

    // ---- helpers ----

    bool Cron::contains16( const int16_t * arr, uint32_t count, int value ) const
    {
        for( uint32_t i = 0; i < count; ++i )
            if( arr[ i ] == value ) return true;
        return false;
    }

    bool Cron::getNextTimeOfDay( int32_t fromSecondsOfDay, const CivilDate & date, int32_t & out ) const
    {
        if( timeCount_ == 0 ) return false;

        const bool sameDayAsLast = hasLast_ && last_.date.year == date.year &&
                                    last_.date.month == date.month && last_.date.day == date.day;

        for( uint32_t i = 0; i < timeCount_; ++i )
        {
            const int32_t candidate = times_[ i ];

            if( sameDayAsLast )
            {
                if( candidate > last_.secondsOfDay ) { out = candidate; return true; }
            }
            else if( candidate > fromSecondsOfDay )
            {
                out = candidate;
                return true;
            }
        }
        return false;
    }

    bool Cron::isInValidWeeklyInterval( const CivilDate & date ) const
    {
        const int64_t daysSinceStart = daysBetween( startDate_, date );
        if( daysSinceStart < 0 ) return false;
        return ( daysSinceStart / 7 ) % every_ == 0;
    }

    bool Cron::isValidDayOfMonth( const Instant & when ) const
    {
        const int day = when.date.day;
        const int lastDay = lengthOfMonth( when.date.year, when.date.month );
        if( contains16( dom_, domCount_, day ) ) return true;
        return day == lastDay && contains16( dom_, domCount_, kLastDayMarker );
    }

    bool Cron::isLastDayOfMonth( const Instant & when ) const
    {
        if( domCount_ == 0 ) return false;
        const int daysInMonth = lengthOfMonth( when.date.year, when.date.month );
        return when.date.day == daysInMonth && contains16( dom_, domCount_, kLastDayMarker );
    }

    // ---- next() calculation ----

    bool Cron::calculateOneTimeEvent( const Instant & now, Instant & out )
    {
        if( ! hasStart_ ) return false;
        if( instantLess( now.date, now.secondsOfDay, start_.date, start_.secondsOfDay ) )
        {
            out = start_;
            return true;
        }
        return false;
    }

    bool Cron::getNextDaily( const Instant & when, Instant & out )
    {
        const CivilDate dateNow = when.date;
        const int64_t daysSinceStart = daysBetween( startDate_, dateNow );

        if( daysSinceStart < 0 )
        {
            out = Instant{ startDate_, times_[ 0 ] };
            return true;
        }

        if( daysSinceStart % every_ == 0 )
        {
            int32_t tod;
            if( getNextTimeOfDay( when.secondsOfDay, dateNow, tod ) )
            {
                out = Instant{ dateNow, tod };
                return true;
            }
        }

        const int64_t remainder = daysSinceStart % every_;
        const int64_t daysToAdd = every_ - remainder;
        out = Instant{ addDays( when.date, static_cast<int>( daysToAdd ) ), times_[ 0 ] };
        return true;
    }

    bool Cron::getNextWeekly( const Instant & when, Instant & out )
    {
        int32_t tod;
        if( contains16( dow_, dowCount_, isoWeekday( when.date ) ) && getNextTimeOfDay( when.secondsOfDay, when.date, tod ) )
        {
            out = Instant{ when.date, tod };
            return true;
        }

        CivilDate candidate = addDays( when.date, 1 );
        const int maxIterations = 7 * every_ + 7;

        for( int iter = 0; iter < maxIterations; ++iter )
        {
            if( contains16( dow_, dowCount_, isoWeekday( candidate ) ) && isInValidWeeklyInterval( candidate ) )
            {
                out = Instant{ candidate, times_[ 0 ] };
                return true;
            }
            candidate = addDays( candidate, 1 );
        }
        return false;
    }

    bool Cron::findNextDayInMonth( const Instant & from, Instant & out )
    {
        CivilDate candidate = addDays( from.date, 1 );

        while( candidate.month == from.date.month )
        {
            if( isValidDayOfMonth( Instant{ candidate, 0 } ) )
            {
                out = Instant{ candidate, times_[ 0 ] };
                return true;
            }
            candidate = addDays( candidate, 1 );
        }
        return false;
    }

    bool Cron::findFirstValidDayInMonth( const CivilDate & monthStart, Instant & out )
    {
        const int len = lengthOfMonth( monthStart.year, monthStart.month );

        for( int day = 1; day <= len; ++day )
        {
            if( contains16( dom_, domCount_, day ) || ( day == len && contains16( dom_, domCount_, kLastDayMarker ) ) )
            {
                out = Instant{ CivilDate{ monthStart.year, monthStart.month, day }, times_[ 0 ] };
                return true;
            }
        }
        return false;
    }

    bool Cron::getNextMonthly( const Instant & when, Instant & out )
    {
        const CivilDate dateNow = when.date;
        const int64_t monthsDiff = monthsBetween( startDate_, dateNow );

        if( monthsDiff >= 0 && monthsDiff % every_ == 0 )
        {
            if( isValidDayOfMonth( when ) )
            {
                int32_t tod;
                if( getNextTimeOfDay( when.secondsOfDay, dateNow, tod ) )
                {
                    out = Instant{ when.date, tod };
                    return true;
                }
            }

            Instant nextInMonth;
            if( findNextDayInMonth( when, nextInMonth ) )
            {
                out = nextInMonth;
                return true;
            }
        }

        int64_t monthsToAdd;
        if( monthsDiff < 0 )
        {
            monthsToAdd = -monthsDiff;
        }
        else
        {
            const int64_t remainder = monthsDiff % every_;
            monthsToAdd = ( remainder == 0 ) ? every_ : ( every_ - remainder );
        }

        CivilDate nextMonthDate = addMonthsSetDay1( dateNow, monthsToAdd );
        int maxIter = 100;

        while( maxIter-- > 0 )
        {
            Instant validDay;
            if( findFirstValidDayInMonth( nextMonthDate, validDay ) )
            {
                out = validDay;
                return true;
            }
            nextMonthDate = addMonthsSetDay1( nextMonthDate, every_ );
        }
        return false;
    }

    bool Cron::findNextYearlyOccurrence( const Instant & when, Instant & out )
    {
        const int64_t maxYears = 100LL * every_;
        const CivilDate candidate = addDays( when.date, 1 );

        for( int64_t yearOffset = 0; yearOffset < maxYears; ++yearOffset )
        {
            const int64_t targetYearLong = static_cast<int64_t>( startDate_.year ) + yearOffset * static_cast<int64_t>( every_ );
            if( targetYearLong > 9999 ) break;
            const int targetYear = static_cast<int>( targetYearLong );

            if( targetYear < candidate.year ) continue;

            for( uint32_t monthIdx = 0; monthIdx < monthCount_; ++monthIdx )
            {
                const int targetMonth = month_[ monthIdx ];
                if( targetYear == candidate.year && targetMonth < candidate.month ) continue;

                for( uint32_t dayIdx = 0; dayIdx < domCount_; ++dayIdx )
                {
                    const int targetDay = dom_[ dayIdx ];
                    const int daysInMonth = lengthOfMonth( targetYear, targetMonth );
                    const int actualDay = targetDay < daysInMonth ? targetDay : daysInMonth;

                    const CivilDate possible{ targetYear, targetMonth, actualDay };

                    if( ! instantLess( possible, 0, when.date, when.secondsOfDay ) ) // possible >= when
                    {
                        out = Instant{ possible, 0 };
                        return true;
                    }
                }
            }
        }
        return false;
    }

    bool Cron::getNextYearly( const Instant & when, Instant & out )
    {
        if( contains16( month_, monthCount_, when.date.month ) &&
            ( contains16( dom_, domCount_, when.date.day ) || isLastDayOfMonth( when ) ) )
        {
            int32_t tod;
            if( getNextTimeOfDay( when.secondsOfDay, when.date, tod ) )
            {
                out = Instant{ when.date, tod };
                return true;
            }
        }

        Instant next;
        if( ! findNextYearlyOccurrence( when, next ) ) return false;

        out = Instant{ next.date, times_[ 0 ] };
        return true;
    }

    bool Cron::calculateNext( const Instant & now, Instant & out )
    {
        if( every_ == 0 ) return calculateOneTimeEvent( now, out );

        const bool nowAfterStart = ! hasStart_ || instantLess( start_.date, start_.secondsOfDay, now.date, now.secondsOfDay );
        const Instant start = nowAfterStart ? now : start_;

        switch( mode_ )
        {
            case 'd': return getNextDaily( start, out );
            case 'w': return getNextWeekly( start, out );
            case 'm': return getNextMonthly( start, out );
            case 'y': return getNextYearly( start, out );
            default: return false;
        }
    }

    // ---- public API ----

    int64_t Cron::next()
    {
        if( ! isValid() ) return -1;

        const int64_t nowEpoch = nowFn_( nowCtx_ );
        const Instant now = instantFromEpoch( nowEpoch );

        if( hasStop_ && instantLess( stop_.date, stop_.secondsOfDay, now.date, now.secondsOfDay ) ) return -1;
        if( every_ == 0 && oneTimeTriggered_ ) return -1;

        Instant next;
        if( ! calculateNext( now, next ) ) return -1;

        if( hasStop_ && instantLess( stop_.date, stop_.secondsOfDay, next.date, next.secondsOfDay ) ) return -1;

        if( every_ == 0 ) oneTimeTriggered_ = true;

        hasLast_ = true;
        last_ = next;

        const int64_t nextEpoch = instantEpoch( next.date, next.secondsOfDay );
        return ( nextEpoch - nowEpoch ) * 1000;
    }

    void Cron::reset()
    {
        hasLast_ = false;
        oneTimeTriggered_ = false;
    }
} // namespace naxe
