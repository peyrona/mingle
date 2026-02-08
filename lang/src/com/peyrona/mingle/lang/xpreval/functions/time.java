
package com.peyrona.mingle.lang.xpreval.functions;

import com.peyrona.mingle.lang.MingleException;
import com.peyrona.mingle.lang.japi.Pair;
import com.peyrona.mingle.lang.japi.UtilColls;
import com.peyrona.mingle.lang.japi.UtilDateTime;
import com.peyrona.mingle.lang.japi.UtilJson;
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
 * Creates a new instance of this class, which represents a local time.
 *
 * @author Francisco José Morero Peyrona
 *
 * Official web site at: <a href="https://github.com/peyrona/mingle">https://github.com/peyrona/mingle</a>
 */
public final class time
             extends ExtraType
             implements Operable<time>
{
    private LocalTime time;

    //------------------------------------------------------------------------//

    /**
     * Class constructor.<br>
     * A time can be created in the following ways:
     * <ul>
     *     <li>A string with following format: "hh[:mm[:ss]]"</li>
     *     <li>JSON from serialize().toString(): time( "{\"class\":\"...\", \"data\":\"12:30:42\"}" )</li>
     *     <li>A number representing elapsed milliseconds since midnight</li>
     *     <li>2 or 3 numbers: hours (0 to 23), minutes (0 to 59) and optionally seconds (0 to 59, if absent 0 is used)</li>
     * </ul>
     * Any other combination of values will produce an error.
     *
     * @param args One or more objects.
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
                            deserialize( s );
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

    public int hour()
    {
        return time.getHour();
    }

    public int minute()
    {
        return time.getMinute();
    }

    public int second()
    {
        return time.getSecond();
    }

    /**
     * Sets the hour component of this time.
     * @param n The hour value to set (0-23).
     * @return Itself.
     */
    public time hour( Object n )
    {
        time = LocalTime.of( UtilType.toInteger( n ), time.getMinute(), time.getSecond(), 0 );
        return this;
    }

    /**
     * Sets the minute component of this time.
     * @param n The minute value to set (0-59).
     * @return Itself.
     */
    public time minute( Object n )
    {
        time = LocalTime.of( time.getHour(), UtilType.toInteger( n ), time.getSecond(), 0 );
        return this;
    }

    /**
     * Sets the second component of this time.
     * @param n The second value to set (0-59).
     * @return Itself.
     */
    public time second( Object n )
    {
        time = LocalTime.of( time.getHour(), time.getMinute(), UtilType.toInteger( n ), 0 );
        return this;
    }

    /**
     * Returns number of seconds elapsed since midnight.
     * @return Number of seconds elapsed since midnight.
     */
    public int sinceMidnight()
    {
        return time.toSecondOfDay();
    }

    /**
     * Adds or subtracts passed hours to current time.
     * @param n Number of hours to add or subtract.
     * @return Itself.
     */
    public time addHours( Object n )
    {
        time = time.plusHours( UtilType.toLong( n ) );
        return this;
    }

    /**
     * Adds or subtracts passed minutes to current time.
     * @param n Number of minutes to add or subtract.
     * @return Itself.
     */
    public time addMinutes( Object n )
    {
        time = time.plusMinutes( UtilType.toLong( n ) );
        return this;
    }

    /**
     * Returns the sunrise for the given location, for today and for the default time-zone,
     *
     * @param latitude the latitude of the location in degrees.
     * @param longitude the longitude of the location in degrees (West is negative).
     * @return The sunrise at specified geoposition.
     */
    public time sunRise( Object latitude, Object longitude )
    {
        return sunRise( latitude, longitude, null, null );
    }

    /**
     * Returns the sunrise.
     *
     * @param latitude the latitude of the location in degrees.
     * @param longitude the longitude of the location in degrees.
     * @param dateOrZone Either a valid date (as an instance of class date or as an string) or a time zone as an String.
     *                   If a date is passed, the zone will be the default one; and if a time-zone is passed, the date will be today.
     * @return The sunrise at specified geoposition and date.
     */
    public time sunRise( Object latitude, Object longitude, Object dateOrZone )
    {
        Pair<date,ZoneId> pair = getDateAndZone( dateOrZone );

        return sunRise( latitude, longitude, pair.getKey(), pair.getValue() );
    }

    public time sunRise( Object latitude, Object longitude, Object date, Object zone )
    {
        return getSunRiseTime( toZonedDateTime( date, LocalTime.MIDNIGHT, zone ), latitude, longitude );
    }

    /**
     * Returns the sunset for the given location, for today and for the default time-zone,
     *
     * @param latitude the latitude of the location in degrees.
     * @param longitude the longitude of the location in degrees.
     * @return The sunset at specified geoposition.
     */
    public time sunSet( Object latitude, Object longitude )
    {
        return sunSet( latitude, longitude, null, null );
    }

    /**
     * Returns the sunset.
     *
     * @param latitude the latitude of the location in degrees.
     * @param longitude the longitude of the location in degrees.
     * @param dateOrZone Either a valid date (as an instance of class date or as an string) or a time zone as an String.
     *                   If a date is passed, the zone will be the default one; and if a time-zone is passed, the date will be today.
     * @return The sunset at specified geoposition and date.
     */
    public time sunSet( Object latitude, Object longitude, Object dateOrZone )
    {
        Pair<date,ZoneId> pair = getDateAndZone( dateOrZone );

        return sunSet( latitude, longitude, pair.getKey(), pair.getValue() );
    }

    public time sunSet( Object latitude, Object longitude, Object date, Object zone )
    {
        return getSunSetTime( toZonedDateTime( date, LocalTime.MIDNIGHT, zone ), latitude, longitude );
    }

    /**
     * Returns the moment when the Sun crosses the local meridian and reaches its highest
     * position in the sky, for the given location, for today and for the default time-zone,
     * except at the poles (AKA solar noon or high noon).
     *
     * @param latitude the latitude of the location in degrees.
     * @param longitude the longitude of the location in degrees (West is negative).
     * @return The solar noon at specified geoposition.
     */
    public time sunNoon( Object latitude, Object longitude )
    {
        return sunNoon( latitude, longitude, null, null );
    }

    /**
     * Returns the moment when the Sun crosses the local meridian and reaches its highest position in the sky,
     * except at the poles (AKA solar noon or high noon).
     *
     * @param latitude the latitude of the location in degrees.
     * @param longitude the longitude of the location in degrees (West is negative).
     * @param dateOrZone Either a valid date (as an instance of class date or as an string) or a time zone as an String.
     *                   If a date is passed, the zone will be the default one; and if a time-zone is passed, the date will be today.
     * @return The solar noon at specified geoposition.
     */
    public time sunNoon( Object latitude, Object longitude, Object dateOrZone )
    {
        Pair<date,ZoneId> pair = getDateAndZone( dateOrZone );

        return sunNoon( latitude, longitude, pair.getKey(), pair.getValue() );
    }

    public time sunNoon( Object latitude, Object longitude, Object date, Object zone )
    {
        LocalTime noon = UtilDateTime.getSolarNoon( toZonedDateTime( date, LocalTime.MIDNIGHT, zone ),
                                                    UtilType.toDouble( latitude  ),
                                                    UtilType.toDouble( longitude ) );
        return toTime( noon );
    }

    /**
	 * Returns the astronomical morning twilight time for the given location, for today and for the default time-zone.<br>
	 * This is the darkest of the three: Astronomical, Nautical and Civil.
	 *
	 * @param latitude  the latitude of the location in degrees.
	 * @param longitude the longitude of the location in degrees (West is negative).
     * @return The astronomical twilight time for specified geoposition for today.
     */
    public time twilightSunRise( Object latitude, Object longitude )
    {
        return twilightSunRise( latitude, longitude, null, null );
    }

    /**
	 * Returns the astronomical morning twilight time for the given date and given location.<br>
	 * This is the darkest of the three: Astronomical, Nautical and Civil.
	 *
	 * @param latitude  the latitude of the location in degrees.
	 * @param longitude the longitude of the location in degrees (West is negative).
     * @param dateOrZone Either a valid date (as an instance of class date or as an string) or a time zone as an String.
     *                   If a date is passed, the zone will be the default one; and if a time-zone is passed, the date will be today.
     * @return The astronomical twilight time for the given date and location.
     */
    public time twilightSunRise( Object latitude, Object longitude, Object dateOrZone )
    {
        Pair<date,ZoneId> pair = getDateAndZone( dateOrZone );

        return twilightSunRise( latitude, longitude, pair.getKey(), pair.getValue() );
    }

    public time twilightSunRise( Object latitude, Object longitude, Object date, Object zone )
    {
        LocalTime dawn = UtilDateTime.getAstronomicalDawn( toZonedDateTime( date, LocalTime.MIDNIGHT, zone ),
                                                           UtilType.toDouble( latitude  ),
                                                           UtilType.toDouble( longitude ) );
        return toTime( dawn );
    }

    /**
	 * Returns the astronomical evening twilight time for the given location, for today and for the default time-zone.<br>
	 * Which is the darkest of the three: Astronomical, Nautical and Civil.
     *
	 * @param latitude  the latitude of the location in degrees.
	 * @param longitude the longitude of the location in degrees (West is negative).
     * @return The astronomical twilight time for specified geoposition for today.
     */
    public time twilightSunSet( Object latitude, Object longitude )
    {
        return twilightSunSet( latitude, longitude, null, null );
    }

    /**
	 * Returns the astronomical evening twilight time for the given date and given location.<br>
	 * Which is the darkest of the three: Astronomical, Nautical and Civil.
	 *
	 * @param latitude The latitude of the location in degrees.
	 * @param longitude The longitude of the location in degrees (West is negative).
     * @param dateOrZone Either a valid date (as an instance of class date or as an string) or a time zone as an String.
     *                   If a date is passed, the zone will be the default one; and if a time-zone is passed, the date will be today.
     * @return The astronomical twilight time for specified geoposition and date.
     */
    public time twilightSunSet( Object latitude, Object longitude, Object dateOrZone )
    {
        Pair<date,ZoneId> pair = getDateAndZone( dateOrZone );

        return twilightSunSet( latitude, longitude, pair.getKey(), pair.getValue() );
    }

    public time twilightSunSet( Object latitude, Object longitude, Object date, Object zone )
    {
        LocalTime dusk = UtilDateTime.getAstronomicalDusk( toZonedDateTime( date, LocalTime.MIDNIGHT, zone ),
                                                           UtilType.toDouble( latitude  ),
                                                           UtilType.toDouble( longitude ) );
        return toTime( dusk );
    }

    /**
     * Returns true if it is day time at specified latitude and longitude for today and for the default time-zone.
     *
     * @param latitude
     * @param longitude
     * @return true if it is day time at specified latitude and longitude.
     */
    public boolean isDay( Object latitude, Object longitude )
    {
        return isDay( latitude, longitude, null );
    }

    /**
     * Returns true if it is day time at specified latitude and longitude and at specified day (and using this instance time).
     *
     * @param latitude
     * @param longitude
     * @param dateOrZone Either a valid date (as an instance of class date or as an string) or a time zone as an String.
     *                   If a date is passed, the zone will be the default one; and if a time-zone is passed, the date will be today.
     * @return true if it is day time at specified latitude and longitude and at specified day or time-zone (and using this instance time).
     */
    public boolean isDay( Object latitude, Object longitude, Object dateOrZone )
    {
        Pair<date,ZoneId> pair = getDateAndZone( dateOrZone );

        return isDay( latitude, longitude, pair.getKey(), pair.getValue() );
    }

    public boolean isDay( Object latitude, Object longitude, Object date, Object zone )
    {
        return UtilDateTime.isDay( toZonedDateTime( date, asLocalTime(), zone ),
                                   UtilType.toDouble( latitude  ),
                                   UtilType.toDouble( longitude ) );
    }

    /**
     * Returns true if it is night time at specified latitude and longitude for today and for the default time-zone.
     *
     * @param latitude
     * @param longitude
     * @return true if it is night time at specified latitude and longitude.
     */
    public boolean isNight( Object latitude, Object longitude )
    {
        return isNight( latitude, longitude, null, null );
    }

    /**
     * Returns true if it is night time at specified latitude and longitude and at specified day (and using this instance time).
     *
     * @param latitude
     * @param longitude
     * @param dateOrZone Either a valid date (as an instance of class date or as an string) or a time zone as an String.
     *                   If a date is passed, the zone will be the default one; and if a time-zone is passed, the date will be today.
     * @return true if it is night time at specified latitude and longitude and at specified day or time-zone (and using this instance time).
     */
    public boolean isNight( Object latitude, Object longitude, Object dateOrZone )
    {
        Pair<date,ZoneId> pair = getDateAndZone( dateOrZone );

        return isNight( latitude, longitude, pair.getKey(), pair.getValue() );
    }

    public boolean isNight( Object latitude, Object longitude, Object date, Object zone )
    {
        return UtilDateTime.isNight( toZonedDateTime( date, asLocalTime(), zone ),
                                     UtilType.toDouble( latitude  ),
                                     UtilType.toDouble( longitude ) );
    }

    //------------------------------------------------------------------------//
    // OVERRIDEN

    /**
     * Shifts current time by adding or subtracting the specified number of seconds.
     * @param seconds Number of seconds to add or subtract.
     * @return Itself.
     */
    @Override
    public time move( Object seconds )
    {
        time = time.plusSeconds( UtilType.toLong( seconds ) );
        return this;
    }

    /**
     * Returns seconds elapsed between this time and passed time.
     * @param t
     * @return Seconds elapsed between this time and passed time.
     */
    @Override
    public int duration( time t )
    {
        return (int) ChronoUnit.SECONDS.between( this.time, t.time );
    }

    @Override
    public int compareTo( Object o )
    {
        if( o instanceof time )
            return time.compareTo( ((time)o).time );

        throw new MingleException( "Expecting 'time', but '"+ o.getClass().getSimpleName() +"' found." );
    }

    /**
     * Returns the time in "HH:mm:ss" format (e.g., "12:30:42").
     *
     * @return The time as a formatted string.
     */
    @Override
    public String toString()
    {
        return time.format( DateTimeFormatter.ofPattern( "HH:mm:ss" ) );
    }

    @Override
    public int hashCode()
    {
        int hash = 7;
            hash = 29 * hash + Objects.hashCode( this.time );
        return hash;
    }

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

    @Override
    public Object serialize()
    {
        return build( toString() );
    }

    @Override
    public time deserialize( Object o )
    {
        UtilJson json  = parse( o );
        String   sTime = json.getString( "data", null );     // At this point it is never null

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

    public LocalTime asLocalTime()
    {
        return LocalTime.of( time.getHour(),
                             time.getMinute(),
                             time.getSecond() );
    }

    public LocalDateTime asLocalDateTime()
    {
        return asLocalDateTime( LocalDate.now() );
    }

    public LocalDateTime asLocalDateTime( LocalDate date )
    {
        return LocalDateTime.of( date, time );
    }

    //------------------------------------------------------------------------//
    // PRIVATE

    /**
     * Receives a string representing a time in "HH:mm:ss" format and
     * returns the LocalTime represented.
     *
     * @param s Time string to parse.
     * @return The parsed LocalTime.
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
        // This ensures the time value is trustable and matches user input.

        return LocalTime.of( hour, min, sec, 0 );
    }

    /**
     * Gets sunrise time for the given datetime and location.
     *
     * @param dateTime The datetime to use.
     * @param latitude The latitude of the location in degrees.
     * @param longitude The longitude of the location in degrees.
     * @return The sunrise time, or null if not applicable (polar regions).
     */
    private time getSunRiseTime( ZonedDateTime dateTime, Object latitude, Object longitude )
    {
        LocalTime sunrise = UtilDateTime.getSunrise( dateTime,
                                                     UtilType.toDouble( latitude  ),
                                                     UtilType.toDouble( longitude ) );
        return toTime( sunrise );
    }

    /**
     * Gets sunset time for the given datetime and location.
     *
     * @param dateTime The datetime to use.
     * @param latitude The latitude of the location in degrees.
     * @param longitude The longitude of the location in degrees.
     * @return The sunset time, or null if not applicable (polar regions).
     */
    private time getSunSetTime( ZonedDateTime dateTime, Object latitude, Object longitude )
    {
        LocalTime sunset = UtilDateTime.getSunset( dateTime,
                                                   UtilType.toDouble( latitude  ),
                                                   UtilType.toDouble( longitude ) );
        return toTime( sunset );
    }

    /**
     * Converts a LocalTime to a time instance.
     *
     * @param localTime The local time to convert.
     * @return A new time instance, or null if input is null.
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
     * Converts the given arguments to a ZonedDateTime.
     *
     * @param arg  A date object, date string, or null for today.
     * @param time The time to use, or null to use this instance's time.
     * @param zone The timezone, or null for system default.
     * @return A ZonedDateTime combining the date, time, and zone.
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