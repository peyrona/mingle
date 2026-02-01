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
package com.peyrona.mingle.lang.japi;

import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;

/**
 * Utility class providing sunrise, sunset, solar noon, and twilight calculations
 * using the modern Java date/time API ({@code java.time.*}).
 * <p>
 * This class replaces the legacy {@code SunriseSunset} class that used the old
 * {@code Calendar}/{@code Date} APIs.
 * <p>
 * The formulas used are from the Wikipedia articles on Julian Day and Sunrise Equation.
 *
 * @author Francisco José Morero Peyrona
 * @see <a href="http://en.wikipedia.org/wiki/Julian_day">Julian Day on Wikipedia</a>
 * @see <a href="http://en.wikipedia.org/wiki/Sunrise_equation">Sunrise equation on Wikipedia</a>
 */
public final class UtilDateTime
{
    /**
     * The altitude of the sun (solar elevation angle) at the moment of sunrise or sunset: -0.833
     */
    public static final double SUN_ALTITUDE_SUNRISE_SUNSET = -0.833;

    /**
     * The altitude of the sun (solar elevation angle) at the moment of civil twilight: -6.0
     */
    public static final double SUN_ALTITUDE_CIVIL_TWILIGHT = -6.0;

    /**
     * The altitude of the sun (solar elevation angle) at the moment of nautical twilight: -12.0
     */
    public static final double SUN_ALTITUDE_NAUTICAL_TWILIGHT = -12.0;

    /**
     * The altitude of the sun (solar elevation angle) at the moment of astronomical twilight: -18.0
     */
    public static final double SUN_ALTITUDE_ASTRONOMICAL_TWILIGHT = -18.0;

    private static final int    JULIAN_DATE_2000_01_01 = 2451545;
    private static final double CONST_0009 = 0.0009;
    private static final double CONST_360  = 360;

    //------------------------------------------------------------------------//

    /**
     * Intermediate variables used in the sunrise equation.
     *
     * @see <a href="http://en.wikipedia.org/wiki/Sunrise_equation">Sunrise equation on Wikipedia</a>
     */
    private static class SolarEquationVariables
    {
        final double n;        // Julian cycle (number of days since 2000-01-01)
        final double m;        // Solar mean anomaly
        final double lambda;   // Ecliptic longitude
        final double jtransit; // Solar transit (hour angle for solar noon)
        final double delta;    // Declination of the sun

        private SolarEquationVariables( double n, double m, double lambda, double jtransit, double delta )
        {
            this.n = n;
            this.m = m;
            this.lambda = lambda;
            this.jtransit = jtransit;
            this.delta = delta;
        }
    }

    //------------------------------------------------------------------------//

    private UtilDateTime()
    {
        // Prevents instantiation of this utility class
    }

    //------------------------------------------------------------------------//
    // PUBLIC API
    //------------------------------------------------------------------------//

    /**
     * Calculate the sunrise time for the given date and location.
     * <p>
     * This is based on the Wikipedia article on the Sunrise equation.
     *
     * @param day       The day for which to calculate sunrise.
     * @param latitude  The latitude of the location in degrees.
     * @param longitude The longitude of the location in degrees (West is negative).
     * @return The sunrise time, or null if there is no sunrise (e.g., polar regions).
     * @see <a href="http://en.wikipedia.org/wiki/Sunrise_equation">Sunrise equation on Wikipedia</a>
     */
    public static LocalTime getSunrise( ZonedDateTime day, double latitude, double longitude )
    {
        return getSunrise( day, latitude, longitude, SUN_ALTITUDE_SUNRISE_SUNSET );
    }

    /**
     * Calculate the sunrise time for the given date, location, and sun altitude.
     * <p>
     * This is based on the Wikipedia article on the Sunrise equation.
     *
     * @param day         The day for which to calculate sunrise.
     * @param latitude    The latitude of the location in degrees.
     * @param longitude   The longitude of the location in degrees (West is negative).
     * @param sunAltitude The angle between the horizon and the center of the sun's disc.
     * @return The sunrise time, or null if there is no sunrise.
     * @see <a href="http://en.wikipedia.org/wiki/Sunrise_equation">Sunrise equation on Wikipedia</a>
     */
    public static LocalTime getSunrise( ZonedDateTime day, double latitude, double longitude, double sunAltitude )
    {
        SunConfiguration config = calculateSunConfiguration( day, latitude, longitude, sunAltitude );

        return (config.state == SunState.NORMAL) ? config.times[0] : null;
    }

    /**
     * Calculate the sunset time for the given date and location.
     * <p>
     * This is based on the Wikipedia article on the Sunrise equation.
     *
     * @param day       The day for which to calculate sunset.
     * @param latitude  The latitude of the location in degrees.
     * @param longitude The longitude of the location in degrees (West is negative).
     * @return The sunset time, or null if there is no sunset (e.g., polar regions).
     * @see <a href="http://en.wikipedia.org/wiki/Sunrise_equation">Sunrise equation on Wikipedia</a>
     */
    public static LocalTime getSunset( ZonedDateTime day, double latitude, double longitude )
    {
        return getSunset( day, latitude, longitude, SUN_ALTITUDE_SUNRISE_SUNSET );
    }

    /**
     * Calculate the sunset time for the given date, location, and sun altitude.
     * <p>
     * This is based on the Wikipedia article on the Sunrise equation.
     *
     * @param day         The day for which to calculate sunset.
     * @param latitude    The latitude of the location in degrees.
     * @param longitude   The longitude of the location in degrees (West is negative).
     * @param sunAltitude The angle between the horizon and the center of the sun's disc.
     * @return The sunset time, or null if there is no sunset.
     * @see <a href="http://en.wikipedia.org/wiki/Sunrise_equation">Sunrise equation on Wikipedia</a>
     */
    public static LocalTime getSunset( ZonedDateTime day, double latitude, double longitude, double sunAltitude )
    {
        SunConfiguration config = calculateSunConfiguration( day, latitude, longitude, sunAltitude );

        return (config.state == SunState.NORMAL) ? config.times[1] : null;
    }

    /**
     * Calculate the solar noon time for the given date and location.
     * <p>
     * Solar noon is when the Sun crosses the local meridian and reaches its highest position in the sky.
     *
     * @param day       The day for which to calculate solar noon.
     * @param latitude  The latitude of the location in degrees.
     * @param longitude The longitude of the location in degrees (West is negative).
     * @return The solar noon time, or null if not applicable (e.g., polar regions).
     * @see <a href="http://en.wikipedia.org/wiki/Sunrise_equation">Sunrise equation on Wikipedia</a>
     */
    public static LocalTime getSolarNoon( ZonedDateTime day, double latitude, double longitude )
    {
        SolarEquationVariables solarVars = getSolarEquationVariables( day, longitude );

        // Solar noon (meridian transit) happens regardless of sunrise/sunset
        ZonedDateTime noonZdt = fromJulianDate( solarVars.jtransit, day.getZone() );

        return noonZdt.toLocalTime();
    }

    /**
     * Calculate the astronomical dawn twilight time for the given date and location.
     * <p>
     * Astronomical twilight is the darkest of the three twilight phases (astronomical, nautical, civil).
     *
     * @param day       The day for which to calculate astronomical dawn.
     * @param latitude  The latitude of the location in degrees.
     * @param longitude The longitude of the location in degrees (West is negative).
     * @return The dawn twilight time, or null if there is no astronomical twilight (e.g., polar regions).
     */
    public static LocalTime getAstronomicalDawn( ZonedDateTime day, double latitude, double longitude )
    {
        return getSunrise( day, latitude, longitude, SUN_ALTITUDE_ASTRONOMICAL_TWILIGHT );
    }

    /**
     * Calculate the astronomical dusk twilight time for the given date and location.
     * <p>
     * Astronomical twilight is the darkest of the three twilight phases (astronomical, nautical, civil).
     *
     * @param day       The day for which to calculate astronomical dusk.
     * @param latitude  The latitude of the location in degrees.
     * @param longitude The longitude of the location in degrees (West is negative).
     * @return The dusk twilight time, or null if there is no astronomical twilight (e.g., polar regions).
     */
    public static LocalTime getAstronomicalDusk( ZonedDateTime day, double latitude, double longitude )
    {
        return getSunset( day, latitude, longitude, SUN_ALTITUDE_ASTRONOMICAL_TWILIGHT );
    }

    /**
     * Calculate the nautical dawn twilight time for the given date and location.
     *
     * @param day       The day for which to calculate nautical dawn.
     * @param latitude  The latitude of the location in degrees.
     * @param longitude The longitude of the location in degrees (West is negative).
     * @return The dawn twilight time, or null if there is no nautical twilight.
     */
    public static LocalTime getNauticalDawn( ZonedDateTime day, double latitude, double longitude )
    {
        return getSunrise( day, latitude, longitude, SUN_ALTITUDE_NAUTICAL_TWILIGHT );
    }

    /**
     * Calculate the nautical dusk twilight time for the given date and location.
     *
     * @param day       The day for which to calculate nautical dusk.
     * @param latitude  The latitude of the location in degrees.
     * @param longitude The longitude of the location in degrees (West is negative).
     * @return The dusk twilight time, or null if there is no nautical twilight.
     */
    public static LocalTime getNauticalDusk( ZonedDateTime day, double latitude, double longitude )
    {
        return getSunset( day, latitude, longitude, SUN_ALTITUDE_NAUTICAL_TWILIGHT );
    }

    /**
     * Calculate the civil dawn twilight time for the given date and location.
     *
     * @param day       The day for which to calculate civil dawn.
     * @param latitude  The latitude of the location in degrees.
     * @param longitude The longitude of the location in degrees (West is negative).
     * @return The dawn twilight time, or null if there is no civil twilight.
     */
    public static LocalTime getCivilDawn( ZonedDateTime day, double latitude, double longitude )
    {
        return getSunrise( day, latitude, longitude, SUN_ALTITUDE_CIVIL_TWILIGHT );
    }

    /**
     * Calculate the civil dusk twilight time for the given date and location.
     *
     * @param day       The day for which to calculate civil dusk.
     * @param latitude  The latitude of the location in degrees.
     * @param longitude The longitude of the location in degrees (West is negative).
     * @return The dusk twilight time, or null if there is no civil twilight.
     */
    public static LocalTime getCivilDusk( ZonedDateTime day, double latitude, double longitude )
    {
        return getSunset( day, latitude, longitude, SUN_ALTITUDE_CIVIL_TWILIGHT );
    }

    /**
     * Determines if it is currently daytime at the given location.
     *
     * @param latitude  The latitude of the location in degrees.
     * @param longitude The longitude of the location in degrees (West is negative).
     * @return True if the current time is between sunrise and sunset.
     */
    public static boolean isDay( double latitude, double longitude )
    {
        return isDay( ZonedDateTime.now(), latitude, longitude );
    }

    /**
     * Determines if it is daytime at the given location and datetime.
     * <p>
     * Returns true if the given datetime is after sunrise and before sunset.
     *
     * @param dateTime  The datetime to check.
     * @param latitude  The latitude of the location in degrees.
     * @param longitude The longitude of the location in degrees (West is negative).
     * @return True if the given datetime is between sunrise and sunset.
     */
    public static boolean isDay( ZonedDateTime dateTime, double latitude, double longitude )
    {
        SunConfiguration config = calculateSunConfiguration( dateTime, latitude, longitude, SUN_ALTITUDE_SUNRISE_SUNSET );

        if( config.state == SunState.ALWAYS_DAY )
        {
            return true;
        }

        if( config.state == SunState.ALWAYS_NIGHT )
        {
            return false;
        }

        LocalTime currentTime = dateTime.toLocalTime();
        LocalTime sunrise     = config.times[0];
        LocalTime sunset      = config.times[1];

        return currentTime.isAfter( sunrise ) && currentTime.isBefore( sunset );
    }

    /**
     * Determines if it is currently nighttime at the given location.
     * <p>
     * Night is defined as the time when it is darker than astronomical twilight.
     *
     * @param latitude  The latitude of the location in degrees.
     * @param longitude The longitude of the location in degrees (West is negative).
     * @return True if the current time is after astronomical dusk and before astronomical dawn.
     */
    public static boolean isNight( double latitude, double longitude )
    {
        return isNight( ZonedDateTime.now(), latitude, longitude );
    }

    /**
     * Determines if it is nighttime at the given location and datetime.
     * <p>
     * Returns true if the given datetime is after astronomical twilight dusk
     * and before astronomical twilight dawn.
     *
     * @param dateTime  The datetime to check.
     * @param latitude  The latitude of the location in degrees.
     * @param longitude The longitude of the location in degrees (West is negative).
     * @return True if the given datetime is during astronomical night.
     */
    public static boolean isNight( ZonedDateTime dateTime, double latitude, double longitude )
    {
        SunConfiguration config = calculateSunConfiguration( dateTime, latitude, longitude, SUN_ALTITUDE_ASTRONOMICAL_TWILIGHT );

        if( config.state == SunState.ALWAYS_DAY )
        {
            // Sun never goes below astronomical twilight -> Never fully dark
            return false;
        }

        if( config.state == SunState.ALWAYS_NIGHT )
        {
            // Sun never rises above astronomical twilight -> Always dark
            return true;
        }

        LocalTime currentTime = dateTime.toLocalTime();
        LocalTime dawn        = config.times[0];
        LocalTime dusk        = config.times[1];

        return currentTime.isBefore( dawn ) || currentTime.isAfter( dusk );
    }

    //------------------------------------------------------------------------//
    // JULIAN DATE CONVERSION
    //------------------------------------------------------------------------//

    /**
     * Convert a ZonedDateTime to a Julian date.
     * <p>
     * Accuracy is to the second. This is based on the Wikipedia article for Julian day.
     *
     * @param dateTime The datetime to convert.
     * @return The Julian date.
     * @see <a href="http://en.wikipedia.org/wiki/Julian_day#Converting_Julian_or_Gregorian_calendar_date_to_Julian_Day_Number">Converting to Julian day number on Wikipedia</a>
     */
    public static double getJulianDate( ZonedDateTime dateTime )
    {
        // Convert to UTC
        ZonedDateTime utc = dateTime.withZoneSameInstant( ZoneOffset.UTC );

        int year   = utc.getYear();
        int month  = utc.getMonthValue();
        int day    = utc.getDayOfMonth();
        int hour   = utc.getHour();
        int minute = utc.getMinute();
        int second = utc.getSecond();

        // Using the formula from Wikipedia
        int a = (14 - month) / 12;
        int y = year + 4800 - a;
        int m = month + 12 * a - 3;

        int julianDay = day + (153 * m + 2) / 5 + 365 * y + (y / 4) - (y / 100) + (y / 400) - 32045;

        return julianDay + ((double) hour - 12) / 24
                         + ((double) minute) / 1440
                         + ((double) second) / 86400;
    }

    /**
     * Convert a Julian date to a ZonedDateTime.
     * <p>
     * Accuracy is to the second. This is based on the Wikipedia article for Julian day.
     *
     * @param julianDate The Julian date to convert.
     * @param zone       The timezone for the resulting datetime.
     * @return A ZonedDateTime in the specified timezone.
     * @see <a href="http://en.wikipedia.org/wiki/Julian_day#Gregorian_calendar_from_Julian_day_number">Converting from Julian day to Gregorian date, on Wikipedia</a>
     */
    public static ZonedDateTime fromJulianDate( double julianDate, ZoneId zone )
    {
        final int DAYS_PER_4000_YEARS = 146097;
        final int DAYS_PER_CENTURY    = 36524;
        final int DAYS_PER_4_YEARS    = 1461;
        final int DAYS_PER_5_MONTHS   = 153;

        // Let J = JD + 0.5: (shifts the epoch back by one half day to start at 00:00 UTC)
        int J = (int) (julianDate + 0.5);

        // Let j = J + 32044 (shifts epoch back to astronomical year -4800)
        int j = J + 32044;

        int g  = j / DAYS_PER_4000_YEARS;
        int dg = j % DAYS_PER_4000_YEARS;

        int c  = ((dg / DAYS_PER_CENTURY + 1) * 3) / 4;
        int dc = dg - c * DAYS_PER_CENTURY;

        int b  = dc / DAYS_PER_4_YEARS;
        int db = dc % DAYS_PER_4_YEARS;

        int a  = ((db / 365 + 1) * 3) / 4;
        int da = db - a * 365;

        // Full years since March 1, 4801 BC
        int y = g * 400 + c * 100 + b * 4 + a;

        // Full months since last March 1
        int m = (da * 5 + 308) / DAYS_PER_5_MONTHS - 2;

        // Day of month
        int d = da - ((m + 4) * DAYS_PER_5_MONTHS) / 5 + 122;

        int year  = y - 4800 + (m + 2) / 12;
        int month = (m + 2) % 12 + 1;
        int day   = d + 1;

        // Apply the fraction of the day
        double dayFraction = (julianDate + 0.5) - J;

        // Convert fraction of day to seconds (rounded to nearest second)
        long secondsInDay = Math.round( dayFraction * 86400.0 );

        // Create base date at 00:00 UTC and add seconds
        // This handles overflow to the next day automatically
        ZonedDateTime utc = ZonedDateTime.of( year, month, day, 0, 0, 0, 0, ZoneOffset.UTC )
                                         .plusSeconds( secondsInDay );

        return utc.withZoneSameInstant( zone );
    }

    //------------------------------------------------------------------------//
    // INTERNAL HELPERS
    //------------------------------------------------------------------------//

    private enum SunState
    {
        NORMAL,
        ALWAYS_DAY,   // Polar Day (Midnight Sun)
        ALWAYS_NIGHT  // Polar Night
    }

    private static class SunConfiguration
    {
        SunState    state;
        LocalTime[] times; // [0] = rise/start, [1] = set/end
    }

    /**
     * Internal helper to calculate sun state and times.
     */
    private static SunConfiguration calculateSunConfiguration( ZonedDateTime day, double latitude, double longitude, double sunAltitude )
    {
        SolarEquationVariables solarVars = getSolarEquationVariables( day, longitude );

        double longNegated = -longitude;
        double latitudeRad = Math.toRadians( latitude );

        // Hour angle
        // cos(omega) = (sin(alt) - sin(lat)*sin(delta)) / (cos(lat)*cos(delta))
        double cosOmega = (Math.sin( Math.toRadians( sunAltitude ) ) -
                          Math.sin( latitudeRad ) * Math.sin( solarVars.delta )) /
                         (Math.cos( latitudeRad ) * Math.cos( solarVars.delta ));

        SunConfiguration config = new SunConfiguration();

        if( cosOmega > 1.0 )
        {
            config.state = SunState.ALWAYS_NIGHT; // Sun never rises above altitude
            return config;
        }

        if( cosOmega < -1.0 )
        {
            config.state = SunState.ALWAYS_DAY; // Sun never sets below altitude
            return config;
        }

        config.state = SunState.NORMAL;
        double omega = Math.acos( cosOmega );

        // Sunset (Julian date)
        double jset = JULIAN_DATE_2000_01_01 + CONST_0009 +
                     ((Math.toDegrees( omega ) + longNegated) / CONST_360 + solarVars.n +
                      0.0053 * Math.sin( solarVars.m ) - 0.0069 * Math.sin( 2 * solarVars.lambda ));

        // Sunrise (Julian date)
        double jrise = solarVars.jtransit - (jset - solarVars.jtransit);

        // Convert to ZonedDateTime in the target timezone
        ZonedDateTime sunriseZdt = fromJulianDate( jrise, day.getZone() );
        ZonedDateTime sunsetZdt  = fromJulianDate( jset, day.getZone() );

        config.times = new LocalTime[]
        {
            sunriseZdt.toLocalTime(),
            sunsetZdt.toLocalTime()
        };

        return config;
    }

    //------------------------------------------------------------------------//
    // PRIVATE METHODS
    //------------------------------------------------------------------------//

    /**
     * Calculate intermediate variables used for sunrise, sunset, and solar noon calculations.
     *
     * @param day       The day for which to calculate the variables.
     * @param longitude The longitude of the location in degrees (West is negative).
     * @return The solar equation variables.
     * @see <a href="http://en.wikipedia.org/wiki/Sunrise_equation">Sunrise equation on Wikipedia</a>
     */
    private static SolarEquationVariables getSolarEquationVariables( ZonedDateTime day, double longitude )
    {
        // Normalize to Noon to ensure consistent "n" (Julian cycle) calculation regardless of input time
        ZonedDateTime noon = day.withHour( 12 ).withMinute( 0 ).withSecond( 0 ).withNano( 0 );
        
        double longNegated = -longitude;

        // Get the given date as a Julian date
        double julianDate = getJulianDate( noon );

        // Calculate current Julian cycle (number of days since 2000-01-01)
        double nstar = julianDate - JULIAN_DATE_2000_01_01 - CONST_0009 - longNegated / CONST_360;
        double n = Math.round( nstar );

        // Approximate solar noon
        double jstar = JULIAN_DATE_2000_01_01 + CONST_0009 + longNegated / CONST_360 + n;

        // Solar mean anomaly
        double m = Math.toRadians( (357.5291 + 0.98560028 * (jstar - JULIAN_DATE_2000_01_01)) % CONST_360 );

        // Equation of center
        double c = 1.9148 * Math.sin( m ) + 0.0200 * Math.sin( 2 * m ) + 0.0003 * Math.sin( 3 * m );

        // Ecliptic longitude
        double lambda = Math.toRadians( (Math.toDegrees( m ) + 102.9372 + c + 180) % CONST_360 );

        // Solar transit (hour angle for solar noon)
        double jtransit = jstar + 0.0053 * Math.sin( m ) - 0.0069 * Math.sin( 2 * lambda );

        // Declination of the sun
        double delta = Math.asin( Math.sin( lambda ) * Math.sin( Math.toRadians( 23.439 ) ) );

        return new SolarEquationVariables( n, m, lambda, jtransit, delta );
    }
}
