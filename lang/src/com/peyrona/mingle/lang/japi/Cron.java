package com.peyrona.mingle.lang.japi;

import com.peyrona.mingle.lang.MingleException;
import com.peyrona.mingle.lang.xpreval.functions.date;
import com.peyrona.mingle.lang.xpreval.functions.pair;
import com.peyrona.mingle.lang.xpreval.functions.time;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * A Linux-like Cron extended to be Mingle-like.
 * <p>
 * This class provides cron-like scheduling functionality with support for:
 * <ul>
 *   <li>One-time events with specific start time</li>
 *   <li>Daily, Weekly, Monthly, and Yearly repetitions</li>
 *   <li>Multiple execution times per day</li>
 *   <li>Day of week and day of month specifications</li>
 * </ul>
 * <p>
 * <strong>Note:</strong> Day 31 has special meaning in day-of-month specifications.
 * It represents either the 31st day (for months with 31 days) or the last day of the month
 * (for months with fewer than 31 days).
 * <p>
 * This class is thread-safe. All public methods are synchronized.
 *
 * @author Francisco José Morero Peyrona
 * @see <a href="https://github.com/peyrona/mingle">https://github.com/peyrona/mingle</a>
 */
public final class Cron
{
    // Constants for mode values
    private static final char MODE_DAILY   = 'd';
    private static final char MODE_WEEKLY  = 'w';
    private static final char MODE_MONTHLY = 'm';
    private static final char MODE_YEARLY  = 'y';

    // Constants for validation
    private static final int MIN_DAY_OF_WEEK  = 0;  // 0 == Sunday
    private static final int MAX_DAY_OF_WEEK  = 7;
    private static final int MIN_DAY_OF_MONTH = 1;
    private static final int MAX_DAY_OF_MONTH = 31;
    private static final int MIN_MONTH        = 1;
    private static final int MAX_MONTH        = 12;
    private static final int LAST_DAY_MARKER  = 31;     // Special marker for "last day of month"

    // Immutable configuration (set once in constructor)
    private final LocalDateTime ldtStart;    // When to start or null
    private final LocalDateTime ldtStop;     // When to finish or null
    private final LocalTime[]   aLtTime;     // Array of times (hh:mm:ss)
    private final short[]       aDoW;        // Day of Week : 1 - 7  (0 == Sunday too)
    private final short[]       aDoM;        // Day of month: 1 - 31 (31 = last day)
    private final short[]       aMonth;      // Month: 1 - 12
    private final int           every;       // Interval multiplier (e.g., every=2 with mode='w' means every 2 weeks)
    private final char          mode;        // d, w, m, y
    private final LocalDate     startDate;   // Date when cron was configured (for interval calculations)

    // Mutable state (all access is synchronized, so volatile is not needed)
    private LocalDateTime ldtLast = null;              // Last event returned by ::next()
    private boolean oneTimeTriggered = false;          // Track if one-time event has fired

    //------------------------------------------------------------------------//

    /**
     * Class constructor.
     *
     * @param args A Mingle 'pair' with following key-value pairs. All are optional
     *             but all together must be congruent.
     * <ul>
     *   <li><strong>start</strong> (string) - As Mingle "_date_,_time_". When only 'start' is defined, it occurs only once.</li>
     *   <li><strong>stop</strong> (string) - As Mingle "_date_,_time_". Moment of last repetition, or empty for repeating forever.</li>
     *   <li><strong>time</strong> (string) - Time of the day to execute: one or more comma-separated Mingle times (hh[:mm[:ss]]).</li>
     *   <li><strong>dow</strong> (string) - DayOfWeek: one or more comma-separated: 1 to 7 (Monday to Sunday; 0 is Sunday too).</li>
     *   <li><strong>dom</strong> (number) - DayOfMonth: one or more comma-separated: 1 to 31 (31 means 'last-day' for months with &lt;31 days).</li>
     *   <li><strong>month</strong> (number) - Month: one or more comma-separated: 1 to 12.</li>
     *   <li><strong>every</strong> (number) - Repetition interval. Use 1 for standard intervals. Must be positive for repeating events.</li>
     *   <li><strong>mode</strong> (string) - Repetition mode: "Daily", "Weekly", "Monthly" or "Yearly" (only first letter used).</li>
     * </ul>
     * <br>
     * Example: time="12:10,18", dow="2,5", every=2, mode="w" means:
     * repeat every 2 weeks on Tuesday and Friday at 12:10:00 and 18:00:00.
     *
     * @throws MingleException if configuration is invalid or inconsistent
     */
    public Cron( pair args )
    {
        List<String> lstErrs = new ArrayList<>();

        // Parse all parameters
        this.ldtStart  = parseStartTime(  args, lstErrs );
        this.ldtStop   = parseStopTime(   args, lstErrs );
        this.aLtTime   = parseTimeArray(  args, lstErrs );
        this.aDoW      = parseDayOfWeek(  args, lstErrs );
        this.aDoM      = parseDayOfMonth( args, lstErrs );
        this.aMonth    = parseMonth(      args, lstErrs );
        this.mode      = parseMode(       args, lstErrs );
        this.every     = parseEvery(      args, mode, lstErrs );
        this.startDate = (ldtStart != null) ? ldtStart.toLocalDate() : LocalDate.now();

        // Validate cross-field constraints
        validateStartStopOrder( ldtStart, ldtStop, lstErrs );
        validateOneTimeEvent( every, ldtStart, lstErrs );
        validateRepeatingEvent( every, aLtTime, lstErrs );
        validateModeSpecificConstraints( mode, aDoW, aDoM, aMonth, every, lstErrs );

        // Throw exception if any errors were found
        if( ! lstErrs.isEmpty() )
            throw new MingleException( UtilColls.toString( lstErrs, UtilColls.cRECORD_SEP ) );
    }

    //------------------------------------------------------------------------//
    // PUBLIC API
    //------------------------------------------------------------------------//

    /**
     * Returns the amount of milliseconds from the method invocation time to the next
     * moment when Cron should be triggered, or -1 if there is no next time.
     * <p>
     * This method is thread-safe.
     *
     * @return The amount of milliseconds until the next trigger, or -1 if no more triggers
     */
    public synchronized long next()
    {
        LocalDateTime now = LocalDateTime.now();

        // Check if we're past the stop time
        if( ldtStop != null && now.isAfter( ldtStop ) )
            return -1;

        // For one-time events, check if already triggered
        if( every == 0 && oneTimeTriggered )
            return -1;

        LocalDateTime next = calculateNext( now );

        if( next == null )
            return -1;

        // Final check: ensure calculated time doesn't exceed stop time
        if( ldtStop != null && next.isAfter( ldtStop ) )
        {
            return -1;
        }

        // Mark one-time event as triggered
        if( every == 0 )
            oneTimeTriggered = true;

        ldtLast = next;

        ZoneId  zone = ZoneId.systemDefault();
        Instant i1   = now.atZone( zone ).toInstant();
        Instant i2   = next.atZone( zone ).toInstant();

        return Duration.between( i1, i2 ).toMillis();
    }

    /**
     * Cleanup method to reset internal state.
     * Useful for long-running applications to prevent memory leaks.
     * <p>
     * This method is thread-safe.
     */
    public synchronized void reset()
    {
        ldtLast = null;
        oneTimeTriggered = false;
    }

    //---------------------------------------------------------------------------------------------------------------------------//
    // PRIVATE API
    //---------------------------------------------------------------------------------------------------------------------------//

    //------------------------------------------------------------------------//
    // CONSTRUCTOR PARSING METHODS
    //------------------------------------------------------------------------//

    /**
     * Parses the start time from arguments.
     */
    private LocalDateTime parseStartTime( pair args, List<String> lstErrs )
    {
        return args.keys().has( "start" ) ? parseDateTime( args, "start", lstErrs ) : null;
    }

    /**
     * Parses the stop time from arguments.
     */
    private LocalDateTime parseStopTime( pair args, List<String> lstErrs )
    {
        return args.keys().has( "stop" ) ? parseDateTime( args, "stop", lstErrs ) : null;
    }

    /**
     * Parses a date-time value from the arguments.
     */
    private LocalDateTime parseDateTime( pair pairs, String which, List<String> lstErrs )
    {
        String[] val = pairs.get( which ).toString().split( "," );

        if( val.length != 2 )
        {
            lstErrs.add( "Invalid '"+ which +"' value: expected format is 'date,time'" );
            return null;
        }

        try
        {
            date date = new date( val[0].trim() );
            time time = new time( val[1].trim() );
            return LocalDateTime.of( date.asLocalDate(), time.asLocalTime() );
        }
        catch( MingleException me )
        {
            lstErrs.add( "Invalid '"+ which +"' value: " + me.getMessage() );
            return null;
        }
    }

    /**
     * Parses the time array from arguments.
     */
    private LocalTime[] parseTimeArray( pair args, List<String> lstErrs )
    {
        if( !args.keys().has( "time" ) )
        {
            return null;
        }

        String[] as = args.get( "time" ).toString().split( "," );
        LocalTime[] result = new LocalTime[as.length];

        try
        {
            for( int n = 0; n < as.length; n++ )
            {
                result[n] = new time( as[n].trim() ).asLocalTime();
            }

            Arrays.sort( result );

            return result;
        }
        catch( MingleException me )
        {
            lstErrs.add( "Invalid 'time' value: " + me.getMessage() );
            return null;
        }
    }

    /**
     * Parses the day-of-week array from arguments.
     */
    private short[] parseDayOfWeek( pair args, List<String> lstErrs )
    {
        if( !args.keys().has( "dow" ) )
        {
            return null;
        }

        short[] result = parseShortArray( args, "dow", MIN_DAY_OF_WEEK, MAX_DAY_OF_WEEK, lstErrs );

        if( result != null )
        {
            // Convert 0 (Sunday) to 7 (ISO standard for Sunday)
            for( int n = 0; n < result.length; n++ )
            {
                result[n] = (short) (result[n] == 0 ? 7 : result[n]);
            }
        }

        return result;
    }

    /**
     * Parses the day-of-month array from arguments.
     */
    private short[] parseDayOfMonth( pair args, List<String> lstErrs )
    {
        return args.keys().has( "dom" )
               ? parseShortArray( args, "dom", MIN_DAY_OF_MONTH, MAX_DAY_OF_MONTH, lstErrs )
               : null;
    }

    /**
     * Parses the month array from arguments.
     */
    private short[] parseMonth( pair args, List<String> lstErrs )
    {
        return args.keys().has( "month" )
               ? parseShortArray( args, "month", MIN_MONTH, MAX_MONTH, lstErrs )
               : null;
    }

    /**
     * Generic method to parse an array of short values.
     */
    private short[] parseShortArray( pair pairs, String which, int min, int max, List<String> lstErr )
    {
        String[] as  = pairs.get( which ).toString().split( "," );
        short[]  ret = new short[as.length];

        try
        {
            for( int n = 0; n < as.length; n++ )
            {
                short val = Short.parseShort( as[n].trim() );

                if( val < min || val > max )
                {
                    lstErr.add( "Invalid '"+ which +"' value: " + val + " is out of range [" + min + "-" + max + "]" );
                    return null;
                }

                ret[n] = val;
            }

            Arrays.sort( ret );
            return ret;
        }
        catch( NumberFormatException nfe )
        {
            lstErr.add( "Invalid '"+ which +"' value: not a valid number" );
            return null;
        }
    }

    /**
     * Parses the mode from arguments.
     */
    private char parseMode( pair args, List<String> lstErrs )
    {
        if( !args.keys().has( "mode" ) )
            return 0;  // No mode specified

        String sMode = args.get( "mode" ).toString().toLowerCase().trim();

        if( sMode.isEmpty() )
        {
            lstErrs.add( "Invalid 'mode' value: mode cannot be empty" );
            return 0;
        }

        char modeChar = sMode.charAt( 0 );

        if( "dwmy".indexOf( modeChar ) == -1 )
        {
            lstErrs.add( "Invalid 'mode' value: must be 'Daily', 'Weekly', 'Monthly' or 'Yearly'" );
            return 0;
        }

        return modeChar;
    }

    /**
     * Parses the 'every' interval from arguments.
     */
    private int parseEvery( pair args, char mode, List<String> lstErrs )
    {
        if( mode == 0 )
            return 0;  // No mode, so no repetition

        int everyValue = UtilType.toInteger( args.get( "every", 1 ) );

        if( everyValue < 1 )
        {
            lstErrs.add( "Invalid 'every' value: must be 1 or greater for repeating events" );
            return 0;
        }

        return everyValue;
    }

    //------------------------------------------------------------------------//
    // CONSTRUCTOR VALIDATION METHODS
    //------------------------------------------------------------------------//

    /**
     * Validates that start time is before stop time.
     */
    private void validateStartStopOrder( LocalDateTime start, LocalDateTime stop, List<String> lstErrs )
    {
        if( start != null && stop != null && !start.isBefore( stop ) )
        {
            lstErrs.add( "Invalid configuration: 'start' must be before 'stop'" );
        }
    }

    /**
     * Validates one-time event configuration.
     */
    private void validateOneTimeEvent( int every, LocalDateTime start, List<String> lstErrs )
    {
        if( every == 0 && start == null )
        {
            lstErrs.add( "Invalid configuration: one-time event requires 'start' to be specified" );
        }
    }

    /**
     * Validates repeating event configuration.
     */
    private void validateRepeatingEvent( int every, LocalTime[] times, List<String> lstErrs )
    {
        if( every > 0 && (times == null || times.length == 0) )
        {
            lstErrs.add( "Invalid configuration: repeating event requires 'time' to be specified" );
        }
    }

    /**
     * Validates mode-specific constraints.
     */
    private void validateModeSpecificConstraints( char mode, short[] dow, short[] dom, short[] month, int every, List<String> lstErrs )
    {
        switch( mode )
        {
            case MODE_DAILY:
                if( dow   != null )  lstErrs.add( "Invalid configuration: 'Daily' mode cannot specify 'dow'" );
                if( dom   != null )  lstErrs.add( "Invalid configuration: 'Daily' mode cannot specify 'dom'" );
                if( month != null )  lstErrs.add( "Invalid configuration: 'Daily' mode cannot specify 'month'" );
                break;

            case MODE_WEEKLY:
                if( dow   == null )  lstErrs.add( "Invalid configuration: 'Weekly' mode requires 'dow' to be specified" );
                if( dom   != null )  lstErrs.add( "Invalid configuration: 'Weekly' mode cannot specify 'dom'" );
                if( month != null )  lstErrs.add( "Invalid configuration: 'Weekly' mode cannot specify 'month'" );
                if( every < 1     )  lstErrs.add( "Invalid configuration: 'Weekly' mode requires 'every' to be at least 1" );
                break;

            case MODE_MONTHLY:
                if( dom   == null )  lstErrs.add( "Invalid configuration: 'Monthly' mode requires 'dom' to be specified" );
                if( dow   != null )  lstErrs.add( "Invalid configuration: 'Monthly' mode cannot specify 'dow'" );
                if( month != null )  lstErrs.add( "Invalid configuration: 'Monthly' mode cannot specify 'month'" );
                break;

            case MODE_YEARLY:
                if( dom == null || month == null ) lstErrs.add( "Invalid configuration: 'Yearly' mode requires both 'dom' and 'month' to be specified" );
                if( dow != null )                  lstErrs.add( "Invalid configuration: 'Yearly' mode cannot specify 'dow'" );
                break;

            case 0:
                // No mode - one-time event, no additional validation needed
                break;

            default:
                lstErrs.add( "Internal error: unknown mode '" + mode + "'" );
        }
    }

    //------------------------------------------------------------------------//
    // NEXT CALCULATION METHODS
    //------------------------------------------------------------------------//

    /**
     * Calculates the next occurrence based on current configuration.
     * Returns null if unable to calculate next occurrence (converted to -1 in next()).
     */
    private LocalDateTime calculateNext( LocalDateTime now )
    {
        try
        {
            if( every == 0 )  // One-time event
                return calculateOneTimeEvent( now );

            // Repeating event - use appropriate mode calculation
            LocalDateTime start = (ldtStart == null || now.isAfter( ldtStart )) ? now : ldtStart;

            switch( mode )
            {
                case MODE_DAILY:   return getNextDaily( start );
                case MODE_WEEKLY:  return getNextWeekly( start );
                case MODE_MONTHLY: return getNextMonthly( start );
                case MODE_YEARLY:  return getNextYearly( start );
                default:           return null;  // Unknown mode
            }
        }
        catch( MingleException me )
        {
            // Convert internal calculation errors to null (next() returns -1)
            return null;
        }
    }

    /**
     * Calculates next occurrence for one-time events.
     */
    private LocalDateTime calculateOneTimeEvent( LocalDateTime now )
    {
        if( ldtStart == null )
            throw new MingleException( "Internal error: one-time event without start time" );

        return now.isBefore( ldtStart ) ? ldtStart : null;
    }

    /**
     * Calculates next daily occurrence.
     */
    private LocalDateTime getNextDaily( LocalDateTime when )
    {
        LocalDate dateNow = when.toLocalDate();
        long daysSinceStart = ChronoUnit.DAYS.between( startDate, dateNow );
        
        // Ensure we handle cases where 'when' might be before startDate
        if( daysSinceStart < 0 )
        {
            return updateTime( startDate.atStartOfDay(), aLtTime[0] );
        }

        if( daysSinceStart % every == 0 )
        {
            LocalTime time = getNextTime( when.toLocalTime(), dateNow );
            
            if( time != null )
            {
                return updateTime( when, time );
            }
        }

        // Calculate days to add to reach start of next interval
        long remainder = daysSinceStart % every;
        long daysToAdd = every - remainder;
        
        return updateTime( when.plusDays( daysToAdd ), aLtTime[0] );
    }

    /**
     * Calculates next weekly occurrence.
     * BUGFIX: Properly handle weekly intervals using reference date calculation.
     */
    private LocalDateTime getNextWeekly( LocalDateTime when )
    {
        LocalTime time = null;

        // If today is one of the specified days, check for remaining times
        if( contains( aDoW, when.getDayOfWeek().getValue() ) )
            time = getNextTime( when.toLocalTime(), when.toLocalDate() );

        if( time != null )
            return updateTime( when, time );

        // Need to find next valid day
        LocalDateTime candidate = when.plusDays( 1 );
        int maxIterations = 7 * every + 7;  // Safety limit
        int iterations = 0;

        while( iterations < maxIterations )
        {
            int dayOfWeek = candidate.getDayOfWeek().getValue();

            if( contains( aDoW, dayOfWeek ) )
            {
                // Check if this date is in a valid interval period
                if( isInValidInterval( candidate.toLocalDate(), 'w' ) )
                    return updateTime( candidate, aLtTime[0] );
            }

            candidate = candidate.plusDays( 1 );
            iterations++;
        }

        throw new MingleException( "Internal error: unable to find next weekly occurrence" );
    }

    /**
     * Calculates next monthly occurrence.
     */
    private LocalDateTime getNextMonthly( LocalDateTime when )
    {
        LocalDate dateNow      = when.toLocalDate();
        long      monthsDiff   = ChronoUnit.MONTHS.between( startDate, dateNow );

        // 1. Check current month if valid interval
        if( monthsDiff >= 0 && monthsDiff % every == 0 )
        {
            // Check if we can run today later
            if( isValidDayOfMonth( when ) )
            {
                LocalTime time = getNextTime( when.toLocalTime(), dateNow );
                
                if( time != null )
                    return updateTime( when, time );
            }

            // Check remaining days in this month
            LocalDateTime nextInMonth = findNextDayInMonth( when ); // search from when + 1 day
            
            if( nextInMonth != null )
                return nextInMonth;
        }

        // 2. Jump to next valid month
        // Handle negative monthsDiff (when current date is before startDate)
        long monthsToAdd;
        if( monthsDiff < 0 )
        {
            // Jump to startDate's month
            monthsToAdd = -monthsDiff;
        }
        else
        {
            long remainder = monthsDiff % every;
            monthsToAdd = (remainder == 0) ? every : (every - remainder);
        }
        LocalDate nextMonthDate = dateNow.plusMonths( monthsToAdd ).withDayOfMonth( 1 );

        // 3. Find first valid day in that month
        int maxIter = 100; // safety

        while( maxIter-- > 0 )
        {
            LocalDateTime candidate = nextMonthDate.atStartOfDay();

            // Find first valid day in this month
            LocalDateTime validDay = findFirstValidDayInMonth( candidate.toLocalDate() );
            
            if( validDay != null )
                return updateTime( validDay, aLtTime[0] );

            // Try next interval
            nextMonthDate = nextMonthDate.plusMonths( every ).withDayOfMonth( 1 );
        }

        throw new MingleException( "Internal error: unable to find next monthly occurrence" );
    }

    /**
     * Finds the next valid day in the current month (starting from tomorrow).
     */
    private LocalDateTime findNextDayInMonth( LocalDateTime from )
    {
        LocalDateTime candidate = from.plusDays( 1 );
        
        while( candidate.getMonth() == from.getMonth() )
        {
            if( isValidDayOfMonth( candidate ) )
            {
                return updateTime( candidate, aLtTime[0] );
            }
            candidate = candidate.plusDays( 1 );
        }
        
        return null;
    }

    /**
     * Finds the first valid day in the specified month.
     */
    private LocalDateTime findFirstValidDayInMonth( LocalDate monthStart )
    {
        int lengthOfMonth = monthStart.lengthOfMonth();
        
        for( int day = 1; day <= lengthOfMonth; day++ )
        {
            LocalDate candidate = monthStart.withDayOfMonth( day );
            
            // Check if this day matches any of the configured days
            if( contains( aDoM, day ) )
            {
                return candidate.atStartOfDay();
            }
            
            // Check special case for "last day of month" (31)
            if( day == lengthOfMonth && contains( aDoM, LAST_DAY_MARKER ) )
            {
                return candidate.atStartOfDay();
            }
        }
        
        return null;
    }

    /**
     * Calculates next yearly occurrence.
     * BUGFIX: Properly handle yearly intervals using reference date.
     */
    private LocalDateTime getNextYearly( LocalDateTime when )
    {
        // Check if we can use a time later today
        if( contains( aMonth, when.getMonthValue() ) &&
            (contains( aDoM, when.getDayOfMonth() ) || isLastDayOfMonth( when )) )
        {
            LocalTime time = getNextTime( when.toLocalTime(), when.toLocalDate() );

            if( time != null )
                return updateTime( when, time );
        }

        // Need to find next valid month/day combination
        LocalDateTime next = findNextYearlyOccurrence( when );

        if( next == null )
            throw new MingleException( "Internal error: unable to find next yearly occurrence" );

        return updateTime( next, aLtTime[0] );
    }

    /**
     * Finds the next valid date for yearly mode.
     * BUGFIX: Properly calculate yearly intervals from reference date.
     */
    private LocalDateTime findNextYearlyOccurrence( LocalDateTime when )
    {
        int maxYears = 100 * every;  // Safety limit
        LocalDateTime candidate = when.plusDays( 1 );

        for( int yearOffset = 0; yearOffset < maxYears; yearOffset++ )
        {
            int targetYear = startDate.getYear() + (yearOffset * every);

            // Skip years before current year
            if( targetYear < candidate.getYear() )
                continue;

            // Try each specified month in this year
            for( int monthIdx = 0; monthIdx < aMonth.length; monthIdx++ )
            {
                int targetMonth = aMonth[monthIdx];

                // Skip if this month is in the past
                if( targetYear == candidate.getYear() && targetMonth < candidate.getMonthValue() )
                    continue;

                // Try each specified day in this month
                for( int dayIdx = 0; dayIdx < aDoM.length; dayIdx++ )
                {
                    int targetDay = aDoM[dayIdx];
                    int daysInMonth = getDaysInMonth( targetMonth, targetYear );
                    int actualDay = Math.min( targetDay, daysInMonth );

                    LocalDateTime possible = LocalDateTime.of(
                        targetYear,
                        targetMonth,
                        actualDay,
                        0, 0, 0
                    );

                    // Must be after current time
                    if( !possible.isBefore( when ) )
                        return possible;
                }
            }
        }

        throw new MingleException( "Unable to find next yearly occurrence within reasonable time frame" );
    }

    //------------------------------------------------------------------------//
    // HELPER METHODS
    //------------------------------------------------------------------------//

    /**
     * Checks if array contains the given value (array must be sorted).
     */
    private boolean contains( short[] array, int value )
    {
        if( array == null )
            return false;

        return Arrays.binarySearch( array, (short) value ) >= 0;
    }

    /**
     * Returns the next time from aLtTime that is after 'from' on the given date.
     * BUGFIX: Simplified logic to avoid complex date comparisons.
     */
    private LocalTime getNextTime( LocalTime from, LocalDate date )
    {
        if( aLtTime == null || aLtTime.length == 0 )
            return null;

        for( LocalTime candidate : aLtTime )
        {
            // For same date as last execution, must be after last execution time
            if( ldtLast != null && ldtLast.toLocalDate().equals( date ) )
            {
                if( candidate.isAfter( ldtLast.toLocalTime() ) )
                    return candidate;
            }
            // For different date, just needs to be after current time
            else if( candidate.isAfter( from ) )
            {
                return candidate;
            }
        }

        return null;  // No valid time found for today
    }

    /**
     * Checks if the given date is in a valid interval period for weekly mode.
     * Uses days for accurate week calculation (ChronoUnit.WEEKS only counts complete weeks).
     */
    private boolean isInValidInterval( LocalDate date, char checkMode )
    {
        if( checkMode != MODE_WEEKLY )
            return true;

        // Use days for more accurate week calculation
        long daysSinceStart = ChronoUnit.DAYS.between( startDate, date );

        // Handle dates before startDate
        if( daysSinceStart < 0 )
            return false;

        long weeksSinceStart = daysSinceStart / 7;

        return weeksSinceStart % every == 0;
    }

    /**
     * Checks if the given date-time has a valid day of month according to configuration.
     */
    private boolean isValidDayOfMonth( LocalDateTime when )
    {
        int day = when.getDayOfMonth();
        int lastDay = when.toLocalDate().lengthOfMonth();

        // Check if it's a configured day
        if( contains( aDoM, day ) )
            return true;

        // Check if it's the last day and 31 is configured
        return day == lastDay && contains( aDoM, LAST_DAY_MARKER );
    }

    /**
     * Checks if the given date is the last day of its month and LAST_DAY_MARKER (31) is configured.
     */
    private boolean isLastDayOfMonth( LocalDateTime when )
    {
        if( aDoM == null )
            return false;

        int daysInMonth = when.toLocalDate().lengthOfMonth();

        return when.getDayOfMonth() == daysInMonth && contains( aDoM, LAST_DAY_MARKER );
    }

    /**
     * Gets the number of days in a given month and year.
     */
    private static int getDaysInMonth( int month, int year )
    {
        return LocalDate.of( year, month, 1 ).lengthOfMonth();
    }

    /**
     * Updates the time portion of a LocalDateTime.
     */
    private LocalDateTime updateTime( LocalDateTime ldt, LocalTime lt )
    {
        return ldt.withHour( lt.getHour() )
                  .withMinute( lt.getMinute() )
                  .withSecond( lt.getSecond() )
                  .withNano( 0 );
    }
}