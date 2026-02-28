
package com.peyrona.mingle.lang.xpreval.functions;

import com.eclipsesource.json.Json;
import com.eclipsesource.json.JsonObject;
import com.peyrona.mingle.lang.MingleException;
import com.peyrona.mingle.lang.japi.Pair;
import com.peyrona.mingle.lang.japi.UtilColls;
import com.peyrona.mingle.lang.japi.UtilDateTime;
import com.peyrona.mingle.lang.japi.UtilStr;
import com.peyrona.mingle.lang.japi.UtilType;
import java.time.DateTimeException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.Objects;

/**
 * Represents a local time (hour, minute, second) without a time-zone or date
 * in the ISO-8601 calendar system, such as {@code 13:45:30}.
 * <p>
 * This class wraps {@link java.time.LocalTime} to provide a convenient API
 * suitable for use within the Mingle expression evaluation system. While the
 * underlying {@code LocalTime} is immutable, this wrapper allows modification
 * via setter methods which return {@code this} for method chaining.
 * <p>
 * Supported time formats:
 * <ul>
 *   <li>String: {@code "13:45:30"}, {@code "13:45"}, or {@code "13"}</li>
 *   <li>JSON serialization: {@code {"class":"...","data":"13:45:30"}}</li>
 *   <li>Numeric (seconds since midnight): {@code 49530} (for 13:45:30)</li>
 *   <li>Numeric components: {@code 13, 45, 30}</li>
 *   <li>Default: Current time</li>
 * </ul>
 * <p>
 * All time values are normalized to have zero nanoseconds.
 *
 * @author Francisco José Morero Peyrona
 * @see java.time.LocalTime
 * @see <a href="https://github.com/peyrona/mingle">https://github.com/peyrona/mingle</a>
 */
public final class time
             extends ExtraType
             implements Operable<time>
{
    private LocalTime time;

    //------------------------------------------------------------------------//

    /**
     * Constructs a new {@code time} instance.
     * <p>
     * Accepts various argument combinations to initialize the time:
     * <ul>
     *   <li><b>Empty:</b> {@code new time()} initializes to current time.</li>
     *   <li><b>String:</b> {@code new time("13:45:30")} parses a time string in format "hh:mm:ss".</li>
     *   <li><b>JSON:</b> {@code new time("{\"data\":\"13:45:30\"}")} deserializes from JSON.</li>
     *   <li><b>Number (seconds since midnight):</b> {@code new time(49530)} for 13:45:30.</li>
     *   <li><b>Numbers:</b> {@code new time(13, 45, 30)} uses hour, minute, and second.</li>
     * </ul>
     * <p>
     * Valid ranges for numeric components:
     * <ul>
     *   <li>Hour: 0-23</li>
     *   <li>Minute: 0-59</li>
     *   <li>Second: 0-59 (defaults to 0 if not provided)</li>
     * </ul>
     * <p>
     * Time strings support flexible formats:
     * <ul>
     *   <li>{@code "13"} - hour only (minutes and seconds default to 0)</li>
     *   <li>{@code "13:45"} - hour and minute (seconds defaults to 0)</li>
     *   <li>{@code "13:45:30"} - hour, minute, and second</li>
     * </ul>
     *
     * @param args Optional arguments. Valid formats are:
     *            <ul>
     *              <li>(empty) - Current time</li>
     *              <li>(String) - Time string or JSON</li>
     *              <li>(Number) - Seconds since midnight</li>
     *              <li>(Number, Number, [Number]) - hour, minute, [second]</li>
     *            </ul>
     * @throws MingleException If arguments are invalid or resulting time is out of range.
     */
    public time( Object... args )
    {
        if( UtilColls.isEmpty( args ) )
        {
            this.time = LocalTime.now().withNano( 0 );
        }
        else
        {
            try
            {
                if( (args.length == 1) && (args[0] instanceof String) )
                {
                    String s = args[0].toString().trim();

                    // Try JSON deserialization first (from serialize().toString())
                    if( s.charAt( 0 ) == '{' )
                    {
                        try
                        {
                            deserialize( Json.parse( s ).asObject() );
                            return;
                        }
                        catch( Exception e ) { /* Not valid JSON from serialize(), try time string */ }
                    }

                    // Parse as time string, e.g: "15:30:42"
                    this.time = toTime( s );
                }
                else if( (args.length == 1) && (args[0] instanceof Number) )     // Seconds since midnight (can not use millis because time precission is seconds)
                {
                    this.time = LocalTime.ofSecondOfDay( UtilType.toInteger( args[0] ) );
                }
                else                                                             // e.g.: 12,30,42
                {
                    if( args.length < 2 || args.length > 3 )
                        throw new MingleException( MingleException.INVALID_ARGUMENTS );

                    int hour   = UtilType.toInteger( args[0] );
                    int minute = UtilType.toInteger( args[1] );
                    int second = (args.length > 2) ? UtilType.toInteger( args[2] ) : 0;

                    this.time = LocalTime.of( hour, minute, second );
                }
            }
            catch( DateTimeException | MingleException exc )
            {
                throw new MingleException( "Invalid time" );
            }
        }
    }

    //------------------------------------------------------------------------//
    // PUBLIC INTERFACE

    /**
     * Returns the class simple name in lower case.
     *
     * @return The class simple name in lower case.
     */
    public String type()
    {
       return getClass().getSimpleName().toLowerCase();
    }

    /**
     * Gets the hour-of-day field.
     *
     * @return The hour-of-day, from 0 to 23.
     */
    public int hour()
    {
        return time.getHour();
    }

    /**
     * Gets the minute-of-hour field.
     *
     * @return The minute-of-hour, from 0 to 59.
     */
    public int minute()
    {
        return time.getMinute();
    }

    /**
     * Gets the second-of-minute field.
     *
     * @return The second-of-minute, from 0 to 59.
     */
    public int second()
    {
        return time.getSecond();
    }

    /**
     * Sets the hour component of this time.
     * <p>
     * The hour must be between 0 and 23, otherwise an exception is thrown.
     *
     * @param nHour The new hour value (0-23).
     * @return {@code this} for method chaining.
     * @throws MingleException If the hour value is invalid.
     */
    public time hour( Object nHour )
    {
        time = LocalTime.of( UtilType.toInteger( nHour ), time.getMinute(), time.getSecond(), 0 );
        return this;
    }

    /**
     * Sets the minute component of this time.
     * <p>
     * The minute must be between 0 and 59, otherwise an exception is thrown.
     *
     * @param nMinute The new minute value (0-59).
     * @return {@code this} for method chaining.
     * @throws MingleException If the minute value is invalid.
     */
    public time minute( Object nMinute )
    {
        time = LocalTime.of( time.getHour(), UtilType.toInteger( nMinute ), time.getSecond(), 0 );
        return this;
    }

    /**
     * Sets the second component of this time.
     * <p>
     * The second must be between 0 and 59, otherwise an exception is thrown.
     *
     * @param nSecond The new second value (0-59).
     * @return {@code this} for method chaining.
     * @throws MingleException If the second value is invalid.
     */
    public time second( Object nSecond )
    {
        time = LocalTime.of( time.getHour(), time.getMinute(), UtilType.toInteger( nSecond ), 0 );
        return this;
    }

    /**
     * Gets the number of seconds elapsed since midnight.
     * <p>
     * Returns the time as the number of seconds from {@code 00:00:00}.
     * Range is 0 to 86399 (for {@code 23:59:59}).
     *
     * @return The number of seconds elapsed since midnight.
     */
    public int sinceMidnight()
    {
        return time.toSecondOfDay();
    }

    /**
     * Adds or subtracts the specified number of hours to this time.
     * <p>
     * A negative value moves the time backwards. The result wraps around
     * midnight if necessary (e.g., subtracting 2 hours from {@code 01:00:00}
     * results in {@code 23:00:00}).
     *
     * @param nHours The number of hours to add (positive) or subtract (negative).
     * @return {@code this} for method chaining.
     */
    public time addHours( Object nHours )
    {
        time = time.plusHours( UtilType.toLong( nHours ) );
        return this;
    }

    /**
     * Adds or subtracts the specified number of minutes to this time.
     * <p>
     * A negative value moves the time backwards. The result wraps around
     * midnight if necessary.
     *
     * @param nMinutes The number of minutes to add (positive) or subtract (negative).
     * @return {@code this} for method chaining.
     */
    public time addMinutes( Object nMinutes )
    {
        time = time.plusMinutes( UtilType.toLong( nMinutes ) );
        return this;
    }

    /**
     * Returns the sunrise time for the given geographic location, using today's date
     * and the system default time zone.
     * <p>
     * This method calculates the time when the upper limb of the Sun becomes
     * visible on the horizon. May return {@code null} in polar regions
     * during periods of continuous day or night.
     *
     * @param nLatitude The latitude of the location in degrees.
     * @param nLongitude The longitude of the location in degrees (West is negative).
     * @return The sunrise time, or {@code null} if not applicable.
     */
    public time sunRise( Object nLatitude, Object nLongitude )
    {
        return sunRise( nLatitude, nLongitude, null, null );
    }

    /**
     * Returns the sunrise time for the given geographic location and date.
     * <p>
     * The {@code dateOrZone} parameter can be either:
     * <ul>
     *   <li>A date (as a {@link date} instance or ISO-8601 string) - uses default time zone</li>
     *   <li>A time zone string (e.g., "Europe/Madrid") - uses today's date</li>
     * </ul>
     * <p>
     * This method calculates the time when the upper limb of the Sun becomes
     * visible on the horizon. May return {@code null} in polar regions.
     *
     * @param nLatitude The latitude of the location in degrees.
     * @param nLongitude The longitude of the location in degrees (West is negative).
     * @param dateOrZone Either a {@link date} instance, ISO-8601 date string, or time zone string.
     * @return The sunrise time, or {@code null} if not applicable.
     */
    public time sunRise( Object nLatitude, Object nLongitude, Object dateOrZone )
    {
        Pair<date,ZoneId> pair = getDateAndZone( dateOrZone );

        return sunRise( nLatitude, nLongitude, pair.getKey(), pair.getValue() );
    }

    /**
     * Returns the sunrise time for the given geographic location, date, and time zone.
     * <p>
     * This method calculates the time when the upper limb of the Sun becomes
     * visible on the horizon. May return {@code null} in polar regions
     * during periods of continuous day or night.
     *
     * @param nLatitude The latitude of the location in degrees.
     * @param nLongitude The longitude of the location in degrees (West is negative).
     * @param date The date for which to calculate sunrise (may be {@code null} for today).
     * @param zone The time zone ID (e.g., "Europe/Madrid"), or {@code null} for default.
     * @return The sunrise time, or {@code null} if not applicable.
     */
    public time sunRise( Object nLatitude, Object nLongitude, Object date, Object zone )
    {
        return getSunRiseTime( toZonedDateTime( date, LocalTime.MIDNIGHT, zone ), nLatitude, nLongitude );
    }

    /**
     * Returns日落 for the given geographic location, using today's date
     * and the system default time zone.
     * <p>
     * This method calculates the time when the upper limb of the Sun disappears
     * below the horizon. May return {@code null} in polar regions
     * during periods of continuous day or night.
     *
     * @param nLatitude The latitude of the location in degrees.
     * @param nLongitude The longitude of the location in degrees (West is negative).
     * @return The 日落 at the specified geographic location, or {@code null} if not applicable.
     */
    public time sunSet( Object nLatitude, Object nLongitude )
    {
        return sunSet( nLatitude, nLongitude, null, null );
    }

    /**
     * Returns日落 for the given geographic location and date.
     * <p>
     * The {@code dateOrZone} parameter can be either:
     * <ul>
     *   <li>A date (as a {@link date} instance or ISO-8601 string) - uses the default time zone</li>
     *   <li>A time zone string (e.g., "Europe/Madrid") - uses today's date</li>
     * </ul>
     * <p>
     * This method calculates the time when the upper limb of the Sun disappears
     * below the horizon. May return {@code null} in polar regions.
     *
     * @param nLatitude The latitude of the location in degrees.
     * @param nLongitude The longitude of the location in degrees (West is negative).
     * @param dateOrZone Either a {@link date} instance, ISO-8601 date string, or time zone string.
     * @return The 日落 at the specified geographic location and date, or {@code null} if not applicable.
     */
    public time sunSet( Object nLatitude, Object nLongitude, Object dateOrZone )
    {
        Pair<date,ZoneId> pair = getDateAndZone( dateOrZone );

        return sunSet( nLatitude, nLongitude, pair.getKey(), pair.getValue() );
    }

    /**
     * Returns日落 for the given geographic location, date, and time zone.
     * <p>
     * This method calculates the time when the upper limb of the Sun disappears
     * below the horizon. May return {@code null} in polar regions
     * during periods of continuous day or night.
     *
     * @param nLatitude The latitude of the location in degrees.
     * @param nLongitude The longitude of the location in degrees (West is negative).
     * @param date The date for which to calculate sunset (may be {@code null} for today).
     * @param zone The time zone ID (e.g., "Europe/Madrid"), or {@code null} for default.
     * @return The 日落 at the specified geographic location, or {@code null} if not applicable.
     */
    public time sunSet( Object nLatitude, Object nLongitude, Object date, Object zone )
    {
        return getSunSetTime( toZonedDateTime( date, LocalTime.MIDNIGHT, zone ), nLatitude, nLongitude );
    }

    /**
     * Returns the solar noon time for the given geographic location, using today's date
     * and the system default time zone.
     * <p>
     * Solar noon is the moment when the Sun crosses the local meridian and
     * reaches its highest position in the sky (also known as high noon).
     * This differs from 12:00:00 local time due to the equation of time.
     * <p>
     * May return {@code null} in polar regions during periods of continuous
     * day or night where the Sun does not cross the meridian.
     *
     * @param nLatitude The latitude of the location in degrees.
     * @param nLongitude The longitude of the location in degrees (West is negative).
     * @return The solar noon time, or {@code null} if not applicable.
     */
    public time sunNoon( Object nLatitude, Object nLongitude )
    {
        return sunNoon( nLatitude, nLongitude, null, null );
    }

    /**
     * Returns the solar noon time for the given geographic location and date.
     * <p>
     * Solar noon is the moment when the Sun crosses the local meridian and
     * reaches its highest position in the sky (also known as high noon).
     * This differs from 12:00:00 local time due to the equation of time.
     * <p>
     * The {@code dateOrZone} parameter can be either:
     * <ul>
     *   <li>A date (as a {@link date} instance or ISO-8601 string) - uses the default time zone</li>
     *   <li>A time zone string (e.g., "Europe/Madrid") - uses today's date</li>
     * </ul>
     * <p>
     * May return {@code null} in polar regions.
     *
     * @param nLatitude The latitude of the location in degrees.
     * @param nLongitude The longitude of the location in degrees (West is negative).
     * @param dateOrZone Either a {@link date} instance, ISO-8601 date string, or time zone string.
     * @return The solar noon time, or {@code null} if not applicable.
     */
    public time sunNoon( Object nLatitude, Object nLongitude, Object dateOrZone )
    {
        Pair<date,ZoneId> pair = getDateAndZone( dateOrZone );

        return sunNoon( nLatitude, nLongitude, pair.getKey(), pair.getValue() );
    }

    /**
     * Returns the solar noon time for the given geographic location, date, and time zone.
     * <p>
     * Solar noon is the moment when the Sun crosses the local meridian and
     * reaches its highest position in the sky (also known as high noon).
     * This differs from 12:00:00 local time due to the equation of time.
     * <p>
     * May return {@code null} in polar regions during periods of continuous
     * day or night where the Sun does not cross the meridian.
     *
     * @param nLatitude The latitude of the location in degrees.
     * @param nLongitude The longitude of the location in degrees (West is negative).
     * @param date The date for which to calculate solar noon (may be {@code null} for today).
     * @param zone The time zone ID (e.g., "Europe/Madrid"), or {@code null} for default.
     * @return The solar noon time, or {@code null} if not applicable.
     */
    public time sunNoon( Object nLatitude, Object nLongitude, Object date, Object zone )
    {
        LocalTime noon = UtilDateTime.getSolarNoon( toZonedDateTime( date, LocalTime.MIDNIGHT, zone ),
                                                    UtilType.toDouble( nLatitude  ),
                                                    UtilType.toDouble( nLongitude ) );
        return toTime( noon );
    }

    /**
     * Returns the astronomical morning twilight (dawn) time for the given geographic location,
     * using today's date and the system default time zone.
     * <p>
     * Astronomical twilight is the darkest of the three twilight phases:
     * <ul>
     *   <li><b>Astronomical:</b> Sun is 18° below the horizon</li>
     *   <li><b>Nautical:</b> Sun is 12° below the horizon</li>
     *   <li><b>Civil:</b> Sun is 6° below the horizon</li>
     * </ul>
     * At astronomical dawn, the sky is dark enough for astronomical observations.
     * <p>
     * May return {@code null} in polar regions during periods of continuous day.
     *
     * @param nLatitude The latitude of the location in degrees.
     * @param nLongitude The longitude of the location in degrees (West is negative).
     * @return The astronomical dawn time, or {@code null} if not applicable.
     */
    public time twilightSunRise( Object nLatitude, Object nLongitude )
    {
        return twilightSunRise( nLatitude, nLongitude, null, null );
    }

    /**
     * Returns the astronomical morning twilight (dawn) time for the given geographic location and date.
     * <p>
     * Astronomical twilight is the darkest of the three twilight phases:
     * <ul>
     *   <li><b>Astronomical:</b> Sun is 18° below the horizon</li>
     *   <li><b>Nautical:</b> Sun is 12° below the horizon</li>
     *   <li><b>Civil:</b> Sun is 6° below the horizon</li>
     * </ul>
     * At astronomical dawn, the sky is dark enough for astronomical observations.
     * <p>
     * The {@code dateOrZone} parameter can be either:
     * <ul>
     *   <li>A date (as a {@link date} instance or ISO-8601 string) - uses the default time zone</li>
     *   <li>A time zone string (e.g., "Europe/Madrid") - uses today's date</li>
     * </ul>
     * <p>
     * May return {@code null} in polar regions.
     *
     * @param nLatitude The latitude of the location in degrees.
     * @param nLongitude The longitude of the location in degrees (West is negative).
     * @param dateOrZone Either a {@link date} instance, ISO-8601 date string, or time zone string.
     * @return The astronomical dawn time, or {@code null} if not applicable.
     */
    public time twilightSunRise( Object nLatitude, Object nLongitude, Object dateOrZone )
    {
        Pair<date,ZoneId> pair = getDateAndZone( dateOrZone );

        return twilightSunRise( nLatitude, nLongitude, pair.getKey(), pair.getValue() );
    }

    /**
     * Returns the astronomical morning twilight (dawn) time for the given geographic location, date, and time zone.
     * <p>
     * Astronomical twilight is the darkest of the three twilight phases:
     * <ul>
     *   <li><b>Astronomical:</b> Sun is 18° below the horizon</li>
     *   <li><b>Nautical:</b> Sun is 12° below the horizon</li>
     *   <li><b>Civil:</b> Sun is 6° below the horizon</li>
     * </ul>
     * At astronomical dawn, the sky is dark enough for astronomical observations.
     * <p>
     * May return {@code null} in polar regions during periods of continuous day.
     *
     * @param nLatitude The latitude of the location in degrees.
     * @param nLongitude The longitude of the location in degrees (West is negative).
     * @param date The date for which to calculate astronomical dawn (may be {@code null} for today).
     * @param zone The time zone ID (e.g., "Europe/Madrid"), or {@code null} for default.
     * @return The astronomical dawn time, or {@code null} if not applicable.
     */
    public time twilightSunRise( Object nLatitude, Object nLongitude, Object date, Object zone )
    {
        LocalTime dawn = UtilDateTime.getAstronomicalDawn( toZonedDateTime( date, LocalTime.MIDNIGHT, zone ),
                                                           UtilType.toDouble( nLatitude  ),
                                                           UtilType.toDouble( nLongitude ) );
        return toTime( dawn );
    }

    /**
     * Returns the astronomical evening twilight (dusk) time for the given geographic location,
     * using today's date and the system default time zone.
     * <p>
     * Astronomical twilight is the darkest of the three twilight phases:
     * <ul>
     *   <li><b>Astronomical:</b> Sun is 18° below the horizon</li>
     *   <li><b>Nautical:</b> Sun is 12° below the horizon</li>
     *   <li><b>Civil:</b> Sun is 6° below the horizon</li>
     * </ul>
     * At astronomical dusk, the sky becomes dark enough for astronomical observations.
     * <p>
     * May return {@code null} in polar regions during periods of continuous day.
     *
     * @param nLatitude The latitude of the location in degrees.
     * @param nLongitude The longitude of the location in degrees (West is negative).
     * @return The astronomical dusk time, or {@code null} if not applicable.
     */
    public time twilightSunSet( Object nLatitude, Object nLongitude )
    {
        return twilightSunSet( nLatitude, nLongitude, null, null );
    }

    /**
     * Returns the astronomical evening twilight (dusk) time for the given geographic location and date.
     * <p>
     * Astronomical twilight is the darkest of the three twilight phases:
     * <ul>
     *   <li><b>Astronomical:</b> Sun is 18° below the horizon</li>
     *   <li><b>Nautical:</b> Sun is 12° below the horizon</li>
     *   <li><b>Civil:</b> Sun is 6° below the horizon</li>
     * </ul>
     * At astronomical dusk, the sky becomes dark enough for astronomical observations.
     * <p>
     * The {@code dateOrZone} parameter can be either:
     * <ul>
     *   <li>A date (as a {@link date} instance or ISO-8601 string) - uses the default time zone</li>
     *   <li>A time zone string (e.g., "Europe/Madrid") - uses today's date</li>
     * </ul>
     * <p>
     * May return {@code null} in polar regions.
     *
     * @param nLatitude The latitude of the location in degrees.
     * @param nLongitude The longitude of the location in degrees (West is negative).
     * @param dateOrZone Either a {@link date} instance, ISO-8601 date string, or time zone string.
     * @return The astronomical dusk time, or {@code null} if not applicable.
     */
    public time twilightSunSet( Object nLatitude, Object nLongitude, Object dateOrZone )
    {
        Pair<date,ZoneId> pair = getDateAndZone( dateOrZone );

        return twilightSunSet( nLatitude, nLongitude, pair.getKey(), pair.getValue() );
    }

    /**
     * Returns the astronomical evening twilight (dusk) time for the given geographic location, date, and time zone.
     * <p>
     * Astronomical twilight is the darkest of the three twilight phases:
     * <ul>
     *   <li><b>Astronomical:</b> Sun is 18° below the horizon</li>
     *   <li><b>Nautical:</b> Sun is 12° below the horizon</li>
     *   <li><b>Civil:</b> Sun is 6° below the horizon</li>
     * </ul>
     * At astronomical dusk, the sky becomes dark enough for astronomical observations.
     * <p>
     * May return {@code null} in polar regions during periods of continuous day.
     *
     * @param nLatitude The latitude of the location in degrees.
     * @param nLongitude The longitude of the location in degrees (West is negative).
     * @param date The date for which to calculate astronomical dusk (may be {@code null} for today).
     * @param zone The time zone ID (e.g., "Europe/Madrid"), or {@code null} for default.
     * @return The astronomical dusk time, or {@code null} if not applicable.
     */
    public time twilightSunSet( Object nLatitude, Object nLongitude, Object date, Object zone )
    {
        LocalTime dusk = UtilDateTime.getAstronomicalDusk( toZonedDateTime( date, LocalTime.MIDNIGHT, zone ),
                                                           UtilType.toDouble( nLatitude  ),
                                                           UtilType.toDouble( nLongitude ) );
        return toTime( dusk );
    }

    /**
     * Checks if it is daytime at the specified geographic location, using today's date,
     * the system default time zone, and this instance's time.
     * <p>
     * Daytime is defined as the period when the Sun is above the horizon.
     * This method returns {@code true} between sunrise and sunset at the
     * specified location.
     *
     * @param nLatitude The latitude of the location in degrees.
     * @param nLongitude The longitude of the location in degrees (West is negative).
     * @return {@code true} if it is daytime at the specified location, {@code false} otherwise.
     */
    public boolean isDay( Object nLatitude, Object nLongitude )
    {
        return isDay( nLatitude, nLongitude, null );
    }

    /**
     * Checks if it is daytime at the specified geographic location and date, using this instance's time.
     * <p>
     * Daytime is defined as the period when the Sun is above the horizon.
     * This method returns {@code true} between sunrise and sunset at the
     * specified location and date.
     * <p>
     * The {@code dateOrZone} parameter can be either:
     * <ul>
     *   <li>A date (as a {@link date} instance or ISO-8601 string) - uses the default time zone</li>
     *   <li>A time zone string (e.g., "Europe/Madrid") - uses today's date</li>
     * </ul>
     *
     * @param nLatitude The latitude of the location in degrees.
     * @param nLongitude The longitude of the location in degrees (West is negative).
     * @param dateOrZone Either a {@link date} instance, ISO-8601 date string, or time zone string.
     * @return {@code true} if it is daytime at the specified location, {@code false} otherwise.
     */
    public boolean isDay( Object nLatitude, Object nLongitude, Object dateOrZone )
    {
        Pair<date,ZoneId> pair = getDateAndZone( dateOrZone );

        return isDay( nLatitude, nLongitude, pair.getKey(), pair.getValue() );
    }

    /**
     * Checks if it is daytime at the specified geographic location, date, and time zone, using this instance's time.
     * <p>
     * Daytime is defined as the period when the Sun is above the horizon.
     * This method returns {@code true} between sunrise and sunset at the
     * specified location and date.
     *
     * @param nLatitude The latitude of the location in degrees.
     * @param nLongitude The longitude of the location in degrees (West is negative).
     * @param date The date for which to check (may be {@code null} for today).
     * @param zone The time zone ID (e.g., "Europe/Madrid"), or {@code null} for default.
     * @return {@code true} if it is daytime at the specified location, {@code false} otherwise.
     */
    public boolean isDay( Object nLatitude, Object nLongitude, Object date, Object zone )
    {
        return UtilDateTime.isDay( toZonedDateTime( date, asLocalTime(), zone ),
                                   UtilType.toDouble( nLatitude  ),
                                   UtilType.toDouble( nLongitude ) );
    }

    /**
     * Checks if it is nighttime at the specified geographic location, using today's date,
     * the system default time zone, and this instance's time.
     * <p>
     * Nighttime is defined as the period when the Sun is below the horizon.
     * This method returns {@code true} between sunset and sunrise at the
     * specified location.
     *
     * @param nLatitude The latitude of the location in degrees.
     * @param nLongitude The longitude of the location in degrees (West is negative).
     * @return {@code true} if it is nighttime at the specified location, {@code false} otherwise.
     */
    public boolean isNight( Object nLatitude, Object nLongitude )
    {
        return isNight( nLatitude, nLongitude, null, null );
    }

    /**
     * Checks if it is nighttime at the specified geographic location and date, using this instance's time.
     * <p>
     * Nighttime is defined as the period when the Sun is below the horizon.
     * This method returns {@code true} between sunset and sunrise at the
     * specified location and date.
     * <p>
     * The {@code dateOrZone} parameter can be either:
     * <ul>
     *   <li>A date (as a {@link date} instance or ISO-8601 string) - uses the default time zone</li>
     *   <li>A time zone string (e.g., "Europe/Madrid") - uses today's date</li>
     * </ul>
     *
     * @param nLatitude The latitude of the location in degrees.
     * @param nLongitude The longitude of the location in degrees (West is negative).
     * @param dateOrZone Either a {@link date} instance, ISO-8601 date string, or time zone string.
     * @return {@code true} if it is nighttime at the specified location, {@code false} otherwise.
     */
    public boolean isNight( Object nLatitude, Object nLongitude, Object dateOrZone )
    {
        Pair<date,ZoneId> pair = getDateAndZone( dateOrZone );

        return isNight( nLatitude, nLongitude, pair.getKey(), pair.getValue() );
    }

    /**
     * Checks if it is nighttime at the specified geographic location, date, and time zone, using this instance's time.
     * <p>
     * Nighttime is defined as the period when the Sun is below the horizon.
     * This method returns {@code true} between sunset and sunrise at the
     * specified location and date.
     *
     * @param nLatitude The latitude of the location in degrees.
     * @param nLongitude The longitude of the location in degrees (West is negative).
     * @param date The date for which to check (may be {@code null} for today).
     * @param zone The time zone ID (e.g., "Europe/Madrid"), or {@code null} for default.
     * @return {@code true} if it is nighttime at the specified location, {@code false} otherwise.
     */
    public boolean isNight( Object nLatitude, Object nLongitude, Object date, Object zone )
    {
        return UtilDateTime.isNight( toZonedDateTime( date, asLocalTime(), zone ),
                                     UtilType.toDouble( nLatitude  ),
                                     UtilType.toDouble( nLongitude ) );
    }

    /**
     * Checks if this time is strictly after the specified time.
     *
     * @param t The time to compare against (must not be {@code null}).
     * @return {@code true} if this time is after the specified time, {@code false} otherwise.
     * @throws MingleException If the argument is {@code null}.
     */
    public boolean isAfter( time t )
    {
        return time.isAfter( t.time );
    }

    /**
     * Checks if this time is strictly before the specified time.
     *
     * @param t The time to compare against (must not be {@code null}).
     * @return {@code true} if this time is before the specified time, {@code false} otherwise.
     * @throws MingleException If the argument is {@code null}.
     */
    public boolean isBefore( time t )
    {
        return time.isBefore( t.time );
    }

    //------------------------------------------------------------------------//
    // OVERRIDEN

    /**
     * Moves this time forward or backward by the specified number of seconds.
     * <p>
     * Equivalent to adding or subtracting seconds to the current time.
     * A negative value moves the time backwards. The result wraps around
     * midnight if necessary.
     *
     * @param seconds The number of seconds to move (positive to move forward,
     *                negative to move backward).
     * @return {@code this} for method chaining.
     */
    @Override
    public time move( Object seconds )
    {
        time = time.plusSeconds( UtilType.toLong( seconds ) );
        return this;
    }

    /**
     * Calculates the amount of time between this time and the specified time.
     * <p>
     * The result is calculated as {@code time - this time}.
     * Returns a positive number if the argument time is in the future,
     * and a negative number if it is in the past.
     *
     * @param t The other time to compare to (must not be {@code null}).
     * @return The difference in seconds.
     */
    @Override
    public int duration( time t )
    {
        return (int) ChronoUnit.SECONDS.between( this.time, t.time );
    }

    /**
     * Compares this time to another time.
     * <p>
     * Returns:
     * <ul>
     *   <li>{@code 1}: if this time is ahead in time of the passed time.</li>
     *   <li>{@code 0}: if this time is the same as the one passed.</li>
     *   <li>{@code -1}: if this time is before in time of the passed time.</li>
     * </ul>
     *
     * @param o Time to compare (argument must be of type {@code time}).
     * @return {@code 1}, {@code 0}, or {@code -1}.
     * @throws MingleException If the argument is not of type {@code time}.
     */
    @Override
    public int compareTo( Object o )
    {
        if( o instanceof time )
            return time.compareTo( ((time)o).time );

        throw new MingleException( "Expecting 'time', but '"+ o.getClass().getSimpleName() +"' found." );
    }

    /**
     * Returns this time as a string in "HH:mm:ss" format.
     * <p>
     * Hours are in 24-hour format (00-23).
     *
     * @return The time as a formatted string (e.g., "13:45:30").
     */
    @Override
    public String toString()
    {
        return time.format( DateTimeFormatter.ofPattern( "HH:mm:ss" ) );
    }

    /**
     * Returns a hash code value for this time.
     * <p>
     * This method is supported for the benefit of hash tables such as those provided by
     * {@link java.util.HashMap}.
     *
     * @return a hash code value for this object.
     */
    @Override
    public int hashCode()
    {
        int hash = 7;
            hash = 29 * hash + Objects.hashCode( this.time );
        return hash;
    }

    /**
     * Checks if this time is equal to another object.
     * <p>
     * The comparison is based on the underlying {@link LocalTime} value.
     *
     * @param obj The object to compare with (may be {@code null}).
     * @return {@code true} if the other object represents the same time,
     *         {@code false} otherwise.
     */
    @Override
    public boolean equals(Object obj)
    {
        if( this == obj )
            return true;

        if( obj == null )
            return false;

        if( getClass() != obj.getClass() )
            return false;

        final time other = (time) obj;

        return Objects.equals( this.time, other.time );
    }

    //------------------------------------------------------------------------//
    // TO BE USED FROM SCRIPTS

    /**
     * Serializes this time to a JSON object.
     * <p>
     * The JSON structure is:
     * <pre>
     * {
     *   "class": "com.peyrona.mingle.lang.xpreval.functions.time",
     *   "data": "13:45:30"
     * }
     * </pre>
     *
     * @return A JSON object containing the class name and time string.
     */
    @Override
    public JsonObject serialize()
    {
        return build( toString() );
    }

    /**
     * Deserializes a JSON object to populate this time instance.
     * <p>
     * Expects a JSON object in the format produced by {@link #serialize()}.
     * The time string must be in "HH:mm:ss" format.
     *
     * @param json The JSON object containing the time string.
     * @return {@code this} for method chaining.
     * @throws MingleException If the JSON structure is invalid or the time cannot be parsed.
     */
    @Override
    public time deserialize( JsonObject json )
    {
        String sTime = json.getString( "data", null );     // At this point it is never null

        // Validate format before parsing (expected: "HH:mm:ss")
        if( sTime == null || sTime.length() < 8 )
            throw new MingleException( "Invalid time format for deserialization. Expected 'HH:mm:ss', got: " + sTime );

        try
        {
            int hour = UtilType.toInteger( sTime.substring( 0, 2 ) );
            int min  = UtilType.toInteger( sTime.substring( 3, 5 ) );
            int sec  = UtilType.toInteger( sTime.substring( 6, 8 ) );

            time = LocalTime.of( hour, min, sec );
        }
        catch( NumberFormatException | DateTimeException e )
        {
            throw new MingleException( "Invalid time format for deserialization: " + sTime, e );
        }

        return this;
    }

    /**
     * Returns the underlying {@link LocalTime} instance.
     * <p>
     * This method provides access to the standard Java time API time object.
     *
     * @return The underlying {@code LocalTime}.
     */
    public LocalTime asLocalTime()
    {
        return LocalTime.of( time.getHour(),
                             time.getMinute(),
                             time.getSecond() );
    }

    /**
     * Combines this time with today's date to create a {@link LocalDateTime}.
     *
     * @return A {@code LocalDateTime} formed from today's date and this time.
     */
    public LocalDateTime asLocalDateTime()
    {
        return asLocalDateTime( LocalDate.now() );
    }

    /**
     * Combines this time with a date to create a {@link LocalDateTime}.
     *
     * @param date The date to combine with this time (must not be {@code null}).
     * @return A {@code LocalDateTime} formed from the specified date and this time.
     * @throws NullPointerException If the date is {@code null}.
     */
    public LocalDateTime asLocalDateTime( LocalDate date )
    {
        return LocalDateTime.of( date, time );
    }

    //------------------------------------------------------------------------//
    // PRIVATE

    /**
     * Parses a time string in "HH:mm:ss" format and returns the {@link LocalTime} represented.
     * <p>
     * The time string can be:
     * <ul>
     *   <li>{@code "13"} - hour only</li>
     *   <li>{@code "13:45"} - hour and minute</li>
     *   <li>{@code "13:45:30"} - hour, minute, and second</li>
     * </ul>
     * Missing minutes or seconds default to 0. No clamping is performed;
     * invalid values throw a {@code DateTimeException}.
     *
     * @param s The time string to parse.
     * @return The parsed {@code LocalTime}.
     * @throws MingleException If the time string is invalid or the resulting time is out of range.
     */
    private LocalTime toTime( String s )
    {
        if( UtilStr.isEmpty( s ) )
            throw new MingleException( "Invalid time: \""+ s +'\"' );

        String[] as = s.split( ":" );

        int hour, min, sec;

        try
        {
            hour = UtilType.toInteger( as[0] );
            min  = (as.length > 1) ? UtilType.toInteger( as[1] ) : 0;
            sec  = (as.length > 2) ? UtilType.toInteger( as[2] ) : 0;
        }
        catch( NumberFormatException nfe )
        {
            throw new MingleException( "Invalid time: \""+ s +'\"' );
        }

        // Removed silent clamping. LocalTime.of will throw DateTimeException if values are invalid.
        // This ensures that the time value is trustable and matches the user input.

        return LocalTime.of( hour, min, sec, 0 );
    }

    /**
     * Gets the sunrise time for the given date-time and geographic location.
     *
     * @param dateTime The date-time to use for calculations.
     * @param nLatitude The latitude of the location in degrees.
     * @param nLongitude The longitude of the location in degrees.
     * @return The sunrise time, or {@code null} if not applicable (e.g., polar regions).
     */
    private time getSunRiseTime( ZonedDateTime dateTime, Object nLatitude, Object nLongitude )
    {
        LocalTime sunrise = UtilDateTime.getSunrise( dateTime,
                                                     UtilType.toDouble( nLatitude  ),
                                                     UtilType.toDouble( nLongitude ) );
        return toTime( sunrise );
    }

    /**
     * Gets the sunset time for the given date-time and geographic location.
     *
     * @param dateTime The date-time to use for calculations.
     * @param nLatitude The latitude of the location in degrees.
     * @param nLongitude The longitude of the location in degrees.
     * @return The sunset time, or {@code null} if not applicable (e.g., polar regions).
     */
    private time getSunSetTime( ZonedDateTime dateTime, Object nLatitude, Object nLongitude )
    {
        LocalTime sunset = UtilDateTime.getSunset( dateTime,
                                                    UtilType.toDouble( nLatitude  ),
                                                    UtilType.toDouble( nLongitude ) );
        return toTime( sunset );
    }

    /**
     * Converts a {@link LocalTime} to a {@code time} instance.
     * <p>
     * Nanoseconds are discarded in the conversion.
     *
     * @param localTime The local time to convert.
     * @return A new {@code time} instance, or {@code null} if the input is {@code null}.
     */
    private time toTime( LocalTime localTime )
    {
        if( localTime == null )
            return null;

        return new time( localTime.getHour(),
                         localTime.getMinute(),
                         localTime.getSecond() );
    }

    /**
     * Converts the given arguments to a {@link ZonedDateTime}.
     * <p>
     * This method combines a date, time, and time zone into a single date-time object.
     *
     * @param arg A {@link date} instance, ISO-8601 date string, or {@code null} for today.
     * @param time The time to use, or {@code null} to use this instance's time.
     * @param zone The time zone ID, or {@code null} for the system default.
     * @return A {@code ZonedDateTime} combining the date, time, and zone.
     * @throws MingleException If any of the parameters are invalid.
     */
    private ZonedDateTime toZonedDateTime( Object arg, Object time, Object zone )
    {
        final date date;

             if( arg == null           )  date = new date();
        else if( arg instanceof String )  date = new date( (String) arg );
        else if( arg instanceof date   )  date = (date) arg;
        else
             throw new MingleException( "Invalid date: "+ arg );

        if( time == null )
            time = asLocalTime();

        ZoneId zoneId;

        if( zone instanceof ZoneId )
        {
            zoneId = (ZoneId) zone;
        }
        else if( zone == null )
        {
            zoneId = ZoneId.systemDefault();
        }
        else if( zone instanceof String )
        {
            try
            {
                zoneId = ZoneId.of( (String) zone );
            }
            catch( DateTimeException exc )
            {
                throw new MingleException( "Invalid time-zone: "+ zone );
            }
        }
        else
        {
            throw new MingleException( "Invalid time-zone: "+ zone );
        }

        LocalDateTime local = LocalDateTime.of( date.asLocalDate(), (LocalTime) time );

        return local.atZone( zoneId );
    }

    /**
     * Parses a date or time zone string into a {@link Pair} containing both.
     * <p>
     * This method determines whether the string represents:
     * <ul>
     *   <li>A valid ISO-8601 date (e.g., "2022-06-19")</li>
     *   <li>A valid time zone ID (e.g., "Europe/Madrid")</li>
     * </ul>
     * If the string is a valid date, it returns a pair with the date
     * and a {@code null} time zone. If it's a valid time zone, it
     * returns a pair with a {@code null} date and the time zone.
     *
     * @param dateOrZone A date string, time zone string, or {@code null}.
     * @return A {@link Pair} containing the {@link date} and {@link ZoneId},
     *         where one component may be {@code null}.
     * @throws MingleException If the string is neither a valid date nor a valid time zone.
     */
    private Pair<date,ZoneId> getDateAndZone( Object dateOrZone )
    {
        date   d = null;
        ZoneId z = null;

        if( dateOrZone != null )
        {
            if( dateOrZone instanceof date )
            {
                d = (date) dateOrZone;
            }
            else if( dateOrZone instanceof String )   // dateOrZone could be either a date ("2020-05-01") or a zone ("Europe/Madrid")
            {
                String s = (String) dateOrZone;

                if( date.isValid( s ) )
                    d = new date( s );
                else
                    try{ z = ZoneId.of( s ); }
                    catch( DateTimeException exc ) { throw new MingleException( "Invalid time zone: "+ s ); }
            }
            else
            {
                throw new MingleException( "Invalid argument: "+ dateOrZone );
            }
        }

        return new Pair( d,z );
    }
}