
package com.peyrona.mingle.lang.xpreval.functions;

import com.peyrona.mingle.lang.MingleException;
import com.peyrona.mingle.lang.japi.Pair;
import com.peyrona.mingle.lang.japi.UtilColls;
import com.peyrona.mingle.lang.japi.UtilJson;
import com.peyrona.mingle.lang.japi.UtilReflect;
import com.peyrona.mingle.lang.japi.UtilStr;
import com.peyrona.mingle.lang.japi.UtilType;
import com.peyrona.mingle.lang.japi.UtilUnit;
import java.time.DateTimeException;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.util.Calendar;
import java.util.Objects;
import java.util.TimeZone;

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
            this.time = LocalTime.now();
        }
        else
        {
            try
            {
                if( (args.length == 1) && (args[0] instanceof String) )          // e.g: "15:30:42"
                {
                    this.time = toTime( args[0].toString().trim() );
                }
                else if( (args.length == 1) && (args[0] instanceof Number) )     // Seconds since midnight (can not use millis because time precission is seconds)
                {
                    this.time = LocalTime.ofSecondOfDay( UtilType.toInteger( args[0] ) );
                }
                else                                                             // e.g.: 12,30,42
                {
                    if( args.length != 3 )
                        throw new MingleException( MingleException.INVALID_ARGUMENTS );

                    int hour   = UtilType.toInteger( args[0] );
                    int minute = UtilType.toInteger( args[1] );
                    int second = (args.length > 2) ? UtilType.toInteger( args[2] ) : 0;

                    if( ! UtilReflect.areAll( Number.class, hour, minute, second ) )
                        throw new MingleException( MingleException.INVALID_ARGUMENTS );

                    this.time = LocalTime.of( hour, minute, second );
                }
            }
            catch( DateTimeParseException | MingleException exc )
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
     * Add or substract passed hours to current time.
     * @param n number of hours to add or substract.
     * @return Itself.
     */
    public time hour( Object n )
    {
        time = LocalTime.of( UtilType.toInteger( n ), time.getMinute(), time.getSecond(), 0 );
        return this;
    }

    /**
     * Add or substract passed minutes to current time.
     * @param n number of hours to add or minutes.
     * @return Itself.
     */
    public time minute( Object n )
    {
        time = LocalTime.of( time.getHour(), UtilType.toInteger( n ), time.getSecond(), 0 );
        return this;
    }

    /**
     * Add or substract passed seconds to current time.
     * @param n number of seconds to add or substract.
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
        return getSunRiseOrSetTime( toCalendar( date, LocalTime.MIDNIGHT, zone ), latitude, longitude, 0 );
    }

    /**
     * Returns the sunset for the given location, for today and for the default time-zone,
     *
     * @param latitude the latitude of the location in degrees.
     * @param longitude
     * @return The sunset at specified geoposition.
     */
    public time sunSet( Object latitude, Object longitude )
    {
        return sunSet( latitude, longitude, null, null );
    }

    /**
     * Returns the sunset
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
        return getSunRiseOrSetTime( toCalendar( date, LocalTime.MIDNIGHT, zone ), latitude, longitude, 1 );
    }

    /**
     * Returns he moment when the Sun crosses the local meridian and reaches its highest
     * position in the sky, for the given location, for today and for the default time-zone,
     * except at the poles (AKA  solar noon or high noon).
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
     * Returns he moment when the Sun crosses the local meridian and reaches its highest position in the sky,
     * except at the poles (AKA  solar noon or high noon).
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
        return toTime( SunriseSunset.getSolarNoon( toCalendar( date, LocalTime.MIDNIGHT, zone ),
                                                   UtilType.toDouble( latitude  ),
                                                   UtilType.toDouble( longitude ) ) );
    }

    /**
	 * Returns the astronomical morning twilight time for the given location, for today and for the default time-zone.<br>
	 * This is the darkest of the three: Astronomical, Nautical and Civil.
	 *
	 * @param latitude  the latitude of the location in degrees.
	 * @param longitude the longitude of the location in degrees (West is negative).
     * @return The astronomical twilight time for the given date and given location for today.
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
     * @return The astronomical twilight time for the given date and given location.
     */
    public time twilightSunRise( Object latitude, Object longitude, Object dateOrZone )
    {
        Pair<date,ZoneId> pair = getDateAndZone( dateOrZone );

        return twilightSunRise( latitude, longitude, pair.getKey(), pair.getValue() );
    }

    public time twilightSunRise( Object latitude, Object longitude, Object date, Object zone )
    {
        return toTime( SunriseSunset.getAstronomicalTwilight( toCalendar( date, LocalTime.MIDNIGHT, zone ),
                                                              UtilType.toDouble( latitude  ),
                                                              UtilType.toDouble( longitude ) )[0] );
    }

    /**
	 * Returns the astronomical evening twilight time for the given location, for today and for the default time-zone.<br>
	 * Which is the darkest of the three: Astronomical, Nautical and Civil.
     *
	 * @param latitude  the latitude of the location in degrees.
	 * @param longitude the longitude of the location in degrees (West is negative).
     * @return The astronomical twilight time for the given date and given location for today.
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
     * @return The astronomical twilight time for the given date and given location.
     */
    public time twilightSunSet( Object latitude, Object longitude, Object dateOrZone )
    {
        Pair<date,ZoneId> pair = getDateAndZone( dateOrZone );

        return twilightSunSet( latitude, longitude, pair.getKey(), pair.getValue() );
    }

    public time twilightSunSet( Object latitude, Object longitude, Object date, Object zone )
    {
        return toTime( SunriseSunset.getAstronomicalTwilight( toCalendar( date, LocalTime.MIDNIGHT, zone ),
                                                              UtilType.toDouble( latitude  ),
                                                              UtilType.toDouble( longitude ) )[1] );
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
        return SunriseSunset.isDay( toCalendar( date, null, zone ),
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
        return SunriseSunset.isNight( toCalendar( date, null, zone ),
                                      UtilType.toDouble( latitude  ),
                                      UtilType.toDouble( longitude ) );
    }

    //------------------------------------------------------------------------//
    // OVERRIDEN

    /**
     * Shifts current time passed number of seconds.
     * @param seconds Jow many seconds to add or substract to current time.
     * @return Itself.
     */
    @Override
    public time move( Object seconds )
    {
        int n = UtilType.toInteger( seconds );

        if( n != 0 )
        {
            time = (n > 0) ? time.plusSeconds( n )
                           : time.minusSeconds( n * -1 );
        }

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
        if( ! (o instanceof time) )
            throw new MingleException( "Expecting 'time', but '"+ o.getClass().getSimpleName() +"' found." );

        Duration duration = Duration.between( time, ((time)o).time );
        long     seconds  = duration.getSeconds();

        return (seconds == 0) ? 0
                              : (seconds > 0) ? -1 : 1;
    }

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

        int hour = UtilType.toInteger( sTime.substring( 0, 2 ) );
        int min  = UtilType.toInteger( sTime.substring( 3, 5 ) );
        int sec  = UtilType.toInteger( sTime.substring( 6 ) );

        time = LocalTime.of( hour, min, sec );

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
     * Receives a string representing a date in ANSI format (yyyy-mm-dd) and
     * returns the date represented.
     * <p>
     * The string may have different separator characters, these will be ignored.
     * So for example, valid strings are: 2015/03/27, 2015-03-27, 2015.03.27.
     *
     * @param sDateANSI
     * @return
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

        hour = UtilUnit.setBetween( 0, hour, 23 );
        min  = UtilUnit.setBetween( 0, min , 59 );
        sec  = UtilUnit.setBetween( 0, sec , 59 );

        return LocalTime.of( hour, min, sec, 0 );
    }

    /**
     *
     * @param calendar
     * @param nWhich 0 for sunrise and 1 for sunset.
     * @return
     */
    private time getSunRiseOrSetTime( Calendar calendar, Object latitude, Object longitude, int nWhich )
    {
        return toTime( SunriseSunset.getSunriseSunset( calendar,
                                                       UtilType.toDouble( latitude  ),
                                                       UtilType.toDouble( longitude ) )[nWhich] );
    }

    private time toTime( Calendar calendar )
    {
        return new time( calendar.get( Calendar.HOUR_OF_DAY ),
                         calendar.get( Calendar.MINUTE      ),
                         calendar.get( Calendar.SECOND      ) );
    }

    private Calendar toCalendar( Object arg, Object time, Object zone )
    {
        final date date;

             if( arg == null           )  date = new date();
        else if( arg instanceof String )  date = new date( (String) arg );
        else if( arg instanceof date   )  date = (date) arg;
        else
             throw new MingleException( "Invalid date: "+ arg );

        if( time == null )
            time = asLocalTime();

        if( ! (zone instanceof ZoneId ) )
        {
                if( zone == null )            zone = ZoneId.systemDefault();
           else if( zone instanceof String )  try{ zone = ZoneId.of( (String) zone ); } catch( DateTimeException exc ) { zone = null; }
           else                               zone = null;
        }

        if( zone == null )
            throw new MingleException( "Invalid time-zone: "+ zone );

        final LocalDateTime local    = LocalDateTime.of( ((date) date).asLocalDate(), (LocalTime) time );
        final Instant       instant  = local.atZone( (ZoneId) zone ).toInstant();
        final Calendar      calendar = Calendar.getInstance( TimeZone.getTimeZone( (ZoneId) zone ) );
                            calendar.setTimeInMillis( instant.toEpochMilli() );

        return calendar;
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
            else if( dateOrZone instanceof String )   // dateOrZone caoyld be either a date ("2020-05-01") or a zone ("Europe/Madrid")
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