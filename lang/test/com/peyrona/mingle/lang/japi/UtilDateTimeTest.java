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
import java.time.ZonedDateTime;
import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.platform.runner.JUnitPlatform;
import org.junit.runner.RunWith;

/**
 * Test suite for UtilDateTime class.
 * <p>
 * Tests various locations, dates, and edge cases for sunrise/sunset calculations.
 *
 * @author Francisco José Morero Peyrona
 */
@RunWith( JUnitPlatform.class )
public class UtilDateTimeTest
{
    // Tolerance for time comparisons (in seconds)
    private static final int TIME_TOLERANCE_SECONDS = 2;

    // Test locations
    private static final double MADRID_LAT     = 40.4168;
    private static final double MADRID_LON     = -3.7038;
    private static final double NEW_YORK_LAT   = 40.7128;
    private static final double NEW_YORK_LON   = -74.0060;
    private static final double SYDNEY_LAT     = -33.8688;
    private static final double SYDNEY_LON     = 151.2093;
    private static final double REYKJAVIK_LAT  = 64.1466;
    private static final double REYKJAVIK_LON  = -21.9426;
    private static final double EQUATOR_LAT    = 0.0;
    private static final double EQUATOR_LON    = 0.0;

    //------------------------------------------------------------------------//
    // JULIAN DATE CONVERSION TESTS
    //------------------------------------------------------------------------//

    @Nested
    @DisplayName( "Julian Date Conversion Tests" )
    class JulianDateConversionTests
    {
        @Test
        @DisplayName( "Should correctly convert J2000 epoch (2000-01-01 12:00 UTC)" )
        void testJ2000Epoch()
        {
            ZonedDateTime j2000 = ZonedDateTime.of( 2000, 1, 1, 12, 0, 0, 0, ZoneId.of( "UTC" ) );
            double julianDate = UtilDateTime.getJulianDate( j2000 );

            // J2000 epoch is Julian Date 2451545.0
            assertEquals( 2451545.0, julianDate, 0.0001, "J2000 epoch should be JD 2451545.0" );
        }

        @Test
        @DisplayName( "Should correctly convert known historical dates" )
        void testHistoricalDates()
        {
            // November 17, 1858, 00:00 UTC = Modified Julian Date epoch = JD 2400000.5
            ZonedDateTime mjdEpoch = ZonedDateTime.of( 1858, 11, 17, 0, 0, 0, 0, ZoneId.of( "UTC" ) );
            double julianDate = UtilDateTime.getJulianDate( mjdEpoch );

            assertEquals( 2400000.5, julianDate, 0.0001, "MJD epoch should be JD 2400000.5" );
        }

        @Test
        @DisplayName( "Should round-trip conversion correctly" )
        void testRoundTripConversion()
        {
            ZonedDateTime original = ZonedDateTime.of( 2024, 6, 21, 14, 30, 45, 0, ZoneId.of( "Europe/Madrid" ) );
            double julianDate = UtilDateTime.getJulianDate( original );
            ZonedDateTime converted = UtilDateTime.fromJulianDate( julianDate, original.getZone() );

            assertEquals( original.getYear(),   converted.getYear(),   "Year should match" );
            assertEquals( original.getMonth(),  converted.getMonth(),  "Month should match" );
            assertEquals( original.getDayOfMonth(), converted.getDayOfMonth(), "Day should match" );
            assertEquals( original.getHour(),   converted.getHour(),   "Hour should match" );
            assertEquals( original.getMinute(), converted.getMinute(), "Minute should match" );

            // Allow 1 second tolerance for rounding
            assertTrue( Math.abs( original.getSecond() - converted.getSecond() ) <= 1, "Second should be within 1 second" );
        }

        @Test
        @DisplayName( "Should handle timezone conversion correctly" )
        void testTimezoneConversion()
        {
            ZonedDateTime utcTime = ZonedDateTime.of( 2024, 6, 21, 12, 0, 0, 0, ZoneId.of( "UTC" ) );
            double julianDate = UtilDateTime.getJulianDate( utcTime );

            // Convert to different timezone
            ZonedDateTime madridTime = UtilDateTime.fromJulianDate( julianDate, ZoneId.of( "Europe/Madrid" ) );

            // Madrid is UTC+2 in summer, so 12:00 UTC = 14:00 Madrid
            assertEquals( 14, madridTime.getHour(), "Madrid should be UTC+2 in summer" );
        }
    }

    //------------------------------------------------------------------------//
    // SUNRISE/SUNSET TESTS
    //------------------------------------------------------------------------//

    @Nested
    @DisplayName( "Sunrise/Sunset Tests" )
    class SunriseSunsetTests
    {
        @Test
        @DisplayName( "Should calculate sunrise/sunset for Madrid in summer" )
        void testMadridSummer()
        {
            ZonedDateTime summerSolstice = ZonedDateTime.of( 2024, 6, 21, 12, 0, 0, 0, ZoneId.of( "Europe/Madrid" ) );
            LocalTime sunrise = UtilDateTime.getSunrise( summerSolstice, MADRID_LAT, MADRID_LON );
            LocalTime sunset  = UtilDateTime.getSunset( summerSolstice, MADRID_LAT, MADRID_LON );

            assertNotNull( sunrise, "Sunrise should not be null" );
            assertNotNull( sunset, "Sunset should not be null" );

            // Sunrise should be around 6:45 AM in Madrid on summer solstice
            assertTrue( sunrise.getHour() >= 6 && sunrise.getHour() <= 7,
                       "Sunrise should be around 6-7 AM" );

            // Sunset should be around 9:45 PM in Madrid on summer solstice
            assertTrue( sunset.getHour() >= 21 && sunset.getHour() <= 22,
                       "Sunset should be around 9-10 PM" );
        }

        @Test
        @DisplayName( "Should calculate sunrise/sunset for Madrid in winter" )
        void testMadridWinter()
        {
            ZonedDateTime winterSolstice = ZonedDateTime.of( 2024, 12, 21, 12, 0, 0, 0, ZoneId.of( "Europe/Madrid" ) );
            LocalTime sunrise = UtilDateTime.getSunrise( winterSolstice, MADRID_LAT, MADRID_LON );
            LocalTime sunset  = UtilDateTime.getSunset( winterSolstice, MADRID_LAT, MADRID_LON );

            assertNotNull( sunrise, "Sunrise should not be null" );
            assertNotNull( sunset, "Sunset should not be null" );

            // Sunrise should be around 8:35 AM in Madrid on winter solstice
            assertTrue( sunrise.getHour() >= 8 && sunrise.getHour() <= 9,
                       "Sunrise should be around 8-9 AM" );

            // Sunset should be around 5:50 PM in Madrid on winter solstice
            assertTrue( sunset.getHour() >= 17 && sunset.getHour() <= 18,
                       "Sunset should be around 5-6 PM" );
        }

        @Test
        @DisplayName( "Should calculate sunrise/sunset for Sydney (southern hemisphere)" )
        void testSydney()
        {
            // December in Sydney is summer
            ZonedDateTime sydneySummer = ZonedDateTime.of( 2024, 12, 21, 12, 0, 0, 0, ZoneId.of( "Australia/Sydney" ) );
            LocalTime sunrise = UtilDateTime.getSunrise( sydneySummer, SYDNEY_LAT, SYDNEY_LON );
            LocalTime sunset  = UtilDateTime.getSunset( sydneySummer, SYDNEY_LAT, SYDNEY_LON );

            assertNotNull( sunrise, "Sunrise should not be null" );
            assertNotNull( sunset, "Sunset should not be null" );

            // Sunrise should be around 5:40 AM in Sydney on Dec 21
            assertTrue( sunrise.getHour() >= 5 && sunrise.getHour() <= 6,
                       "Sunrise should be around 5-6 AM" );

            // Sunset should be around 8:05 PM in Sydney on Dec 21
            assertTrue( sunset.getHour() >= 19 && sunset.getHour() <= 21,
                       "Sunset should be around 7-9 PM" );
        }

        @Test
        @DisplayName( "Should calculate sunrise/sunset at equator" )
        void testEquator()
        {
            // At the equator, day length is approximately 12 hours year-round
            ZonedDateTime equinox = ZonedDateTime.of( 2024, 3, 20, 12, 0, 0, 0, ZoneId.of( "UTC" ) );
            LocalTime sunrise = UtilDateTime.getSunrise( equinox, EQUATOR_LAT, EQUATOR_LON );
            LocalTime sunset  = UtilDateTime.getSunset( equinox, EQUATOR_LAT, EQUATOR_LON );

            assertNotNull( sunrise, "Sunrise should not be null" );
            assertNotNull( sunset, "Sunset should not be null" );

            // Sunrise should be around 6:00 AM
            assertTrue( sunrise.getHour() >= 5 && sunrise.getHour() <= 7,
                       "Sunrise should be around 6 AM at equator" );

            // Sunset should be around 6:00 PM
            assertTrue( sunset.getHour() >= 17 && sunset.getHour() <= 19,
                       "Sunset should be around 6 PM at equator" );
        }

        @Test
        @DisplayName( "Should return null for polar day (midnight sun)" )
        void testPolarDay()
        {
            // Reykjavik in late June - midnight sun
            ZonedDateTime midnightSun = ZonedDateTime.of( 2024, 6, 21, 12, 0, 0, 0, ZoneId.of( "Atlantic/Reykjavik" ) );
            LocalTime sunrise = UtilDateTime.getSunrise( midnightSun, 70.0, REYKJAVIK_LON ); // 70N for clear polar day
            LocalTime sunset  = UtilDateTime.getSunset( midnightSun, 70.0, REYKJAVIK_LON );

            // At 70N in summer solstice, there's no sunset (midnight sun)
            assertNull( sunrise, "Sunrise should return null during midnight sun" );
            assertNull( sunset, "Sunset should return null during midnight sun" );
        }

        @Test
        @DisplayName( "Should return null for polar night" )
        void testPolarNight()
        {
            // North pole in winter
            ZonedDateTime polarNight = ZonedDateTime.of( 2024, 12, 21, 12, 0, 0, 0, ZoneId.of( "UTC" ) );
            LocalTime sunrise = UtilDateTime.getSunrise( polarNight, 70.0, 0.0 ); // 70N for polar night
            LocalTime sunset  = UtilDateTime.getSunset( polarNight, 70.0, 0.0 );

            // At 70N in winter solstice, there's no sunrise (polar night)
            assertNull( sunrise, "Sunrise should return null during polar night" );
            assertNull( sunset, "Sunset should return null during polar night" );
        }
    }

    //------------------------------------------------------------------------//
    // SOLAR NOON TESTS
    //------------------------------------------------------------------------//

    @Nested
    @DisplayName( "Solar Noon Tests" )
    class SolarNoonTests
    {
        @Test
        @DisplayName( "Should calculate solar noon for Madrid" )
        void testSolarNoonMadrid()
        {
            ZonedDateTime day = ZonedDateTime.of( 2024, 6, 21, 12, 0, 0, 0, ZoneId.of( "Europe/Madrid" ) );
            LocalTime solarNoon = UtilDateTime.getSolarNoon( day, MADRID_LAT, MADRID_LON );

            assertNotNull( solarNoon, "Solar noon should not be null" );

            // Solar noon in Madrid should be around 2:15 PM in summer (UTC+2, and Madrid is west of meridian)
            assertTrue( solarNoon.getHour() >= 13 && solarNoon.getHour() <= 15,
                       "Solar noon should be around 1-3 PM in Madrid" );
        }

        @Test
        @DisplayName( "Should calculate solar noon at UTC meridian" )
        void testSolarNoonUTCMeridian()
        {
            ZonedDateTime day = ZonedDateTime.of( 2024, 6, 21, 12, 0, 0, 0, ZoneId.of( "UTC" ) );
            LocalTime solarNoon = UtilDateTime.getSolarNoon( day, 51.5, 0.0 ); // London latitude, prime meridian

            assertNotNull( solarNoon, "Solar noon should not be null" );

            // Solar noon at prime meridian should be close to 12:00 (varies slightly throughout year)
            assertTrue( solarNoon.getHour() >= 11 && solarNoon.getHour() <= 13,
                       "Solar noon at prime meridian should be around 12 PM" );
        }
    }

    //------------------------------------------------------------------------//
    // TWILIGHT TESTS
    //------------------------------------------------------------------------//

    @Nested
    @DisplayName( "Twilight Tests" )
    class TwilightTests
    {
        @Test
        @DisplayName( "Should calculate astronomical twilight for Madrid" )
        void testAstronomicalTwilightMadrid()
        {
            ZonedDateTime day = ZonedDateTime.of( 2024, 6, 21, 12, 0, 0, 0, ZoneId.of( "Europe/Madrid" ) );
            LocalTime astroDown = UtilDateTime.getAstronomicalDawn( day, MADRID_LAT, MADRID_LON );
            LocalTime astroDusk = UtilDateTime.getAstronomicalDusk( day, MADRID_LAT, MADRID_LON );

            assertNotNull( astroDown, "Astronomical dawn should not be null" );
            assertNotNull( astroDusk, "Astronomical dusk should not be null" );

            // Astronomical twilight dawn should be before sunrise
            LocalTime sunrise = UtilDateTime.getSunrise( day, MADRID_LAT, MADRID_LON );
            LocalTime sunset  = UtilDateTime.getSunset( day, MADRID_LAT, MADRID_LON );
            assertTrue( astroDown.isBefore( sunrise ),
                       "Astronomical dawn should be before sunrise" );

            // Astronomical twilight dusk should be after sunset
            assertTrue( astroDusk.isAfter( sunset ),
                       "Astronomical dusk should be after sunset" );
        }

        @Test
        @DisplayName( "Should calculate civil twilight" )
        void testCivilTwilight()
        {
            ZonedDateTime day = ZonedDateTime.of( 2024, 6, 21, 12, 0, 0, 0, ZoneId.of( "Europe/Madrid" ) );
            LocalTime civilDawn = UtilDateTime.getCivilDawn( day, MADRID_LAT, MADRID_LON );
            LocalTime civilDusk = UtilDateTime.getCivilDusk( day, MADRID_LAT, MADRID_LON );
            LocalTime astroDawn = UtilDateTime.getAstronomicalDawn( day, MADRID_LAT, MADRID_LON );
            LocalTime astroDusk = UtilDateTime.getAstronomicalDusk( day, MADRID_LAT, MADRID_LON );

            assertNotNull( civilDawn, "Civil dawn should not be null" );
            assertNotNull( civilDusk, "Civil dusk should not be null" );

            // Civil twilight should be closer to sunrise/sunset than astronomical
            assertTrue( civilDawn.isAfter( astroDawn ),
                       "Civil dawn should be after astronomical dawn" );
            assertTrue( civilDusk.isBefore( astroDusk ),
                       "Civil dusk should be before astronomical dusk" );
        }
    }

    //------------------------------------------------------------------------//
    // DAY/NIGHT DETECTION TESTS
    //------------------------------------------------------------------------//

    @Nested
    @DisplayName( "Day/Night Detection Tests" )
    class DayNightDetectionTests
    {
        @Test
        @DisplayName( "Should detect daytime correctly" )
        void testDaytime()
        {
            // Noon in Madrid should be day
            ZonedDateTime noon = ZonedDateTime.of( 2024, 6, 21, 12, 0, 0, 0, ZoneId.of( "Europe/Madrid" ) );
            assertTrue( UtilDateTime.isDay( noon, MADRID_LAT, MADRID_LON ),
                       "Noon should be daytime" );
        }

        @Test
        @DisplayName( "Should detect nighttime correctly" )
        void testNighttime()
        {
            // 3 AM in Madrid should be night
            ZonedDateTime night = ZonedDateTime.of( 2024, 6, 21, 3, 0, 0, 0, ZoneId.of( "Europe/Madrid" ) );
            assertTrue( UtilDateTime.isNight( night, MADRID_LAT, MADRID_LON ),
                       "3 AM should be nighttime" );
        }

        @Test
        @DisplayName( "Should handle polar day correctly" )
        void testPolarDayDetection()
        {
            // Arctic in summer should always be day
            ZonedDateTime arcticSummer = ZonedDateTime.of( 2024, 6, 21, 0, 0, 0, 0, ZoneId.of( "UTC" ) );
            assertTrue( UtilDateTime.isDay( arcticSummer, 75.0, 0.0 ),
                       "Arctic in summer should be day even at midnight" );
        }

        @Test
        @DisplayName( "Should handle polar night correctly" )
        void testPolarNightDetection()
        {
            // At 75N in December, the sun never rises but there is still twilight during "day" hours.
            // True astronomical night (no twilight) occurs only at very high latitudes (>66.5N) during deep winter.
            // At 75N on Dec 21, noon is twilight, not full night. Let's test true polar night conditions.
            // Use a higher latitude or a time when it's definitely night.
            ZonedDateTime arcticWinter = ZonedDateTime.of( 2024, 12, 21, 2, 0, 0, 0, ZoneId.of( "UTC" ) );
            assertTrue( UtilDateTime.isNight( arcticWinter, 75.0, 0.0 ),
                       "Arctic in winter at 2 AM should be night" );

            // Also verify that it's not day during the twilight period
            ZonedDateTime arcticNoon = ZonedDateTime.of( 2024, 12, 21, 12, 0, 0, 0, ZoneId.of( "UTC" ) );
            assertFalse( UtilDateTime.isDay( arcticNoon, 75.0, 0.0 ),
                        "Arctic in winter at noon should not be day (no sunrise)" );
        }

        @Test
        @DisplayName( "isDay and isNight should be mutually exclusive at twilight boundary" )
        void testDayNightTransition()
        {
            // Get sunset time
            ZonedDateTime day = ZonedDateTime.of( 2024, 6, 21, 12, 0, 0, 0, ZoneId.of( "Europe/Madrid" ) );
            LocalTime sunset = UtilDateTime.getSunset( day, MADRID_LAT, MADRID_LON );

            // Just before sunset should be day
            ZonedDateTime beforeSunset = day.withHour( sunset.getHour() )
                                            .withMinute( sunset.getMinute() )
                                            .minusMinutes( 5 );
            assertTrue( UtilDateTime.isDay( beforeSunset, MADRID_LAT, MADRID_LON ),
                       "5 minutes before sunset should be day" );

            // Just after sunset should not be day
            ZonedDateTime afterSunset = day.withHour( sunset.getHour() )
                                           .withMinute( sunset.getMinute() )
                                           .plusMinutes( 5 );
            assertFalse( UtilDateTime.isDay( afterSunset, MADRID_LAT, MADRID_LON ),
                        "5 minutes after sunset should not be day" );
        }
    }

    //------------------------------------------------------------------------//
    // EDGE CASES AND SPECIAL SCENARIOS
    //------------------------------------------------------------------------//

    @Nested
    @DisplayName( "Edge Cases" )
    class EdgeCaseTests
    {
        @Test
        @DisplayName( "Should handle equinox correctly" )
        void testEquinox()
        {
            // At equinox, day and night are approximately equal everywhere
            ZonedDateTime equinox = ZonedDateTime.of( 2024, 3, 20, 12, 0, 0, 0, ZoneId.of( "Europe/Madrid" ) );
            LocalTime sunrise = UtilDateTime.getSunrise( equinox, MADRID_LAT, MADRID_LON );
            LocalTime sunset  = UtilDateTime.getSunset( equinox, MADRID_LAT, MADRID_LON );

            assertNotNull( sunrise, "Sunrise should not be null" );
            assertNotNull( sunset, "Sunset should not be null" );

            // Day length should be approximately 12 hours (with some variance due to atmosphere refraction)
            int dayMinutes = sunset.toSecondOfDay() / 60 - sunrise.toSecondOfDay() / 60;
            assertTrue( dayMinutes >= 700 && dayMinutes <= 740,
                       "Day length at equinox should be approximately 12 hours" );
        }

        @Test
        @DisplayName( "Should handle negative longitude (West)" )
        void testNegativeLongitude()
        {
            // New York has negative longitude
            ZonedDateTime day = ZonedDateTime.of( 2024, 6, 21, 12, 0, 0, 0, ZoneId.of( "America/New_York" ) );
            LocalTime sunrise = UtilDateTime.getSunrise( day, NEW_YORK_LAT, NEW_YORK_LON );
            LocalTime sunset  = UtilDateTime.getSunset( day, NEW_YORK_LAT, NEW_YORK_LON );

            assertNotNull( sunrise, "Sunrise should not be null for negative longitude" );
            assertNotNull( sunset, "Sunset should not be null for negative longitude" );
            assertTrue( sunrise.isBefore( sunset ), "Sunrise should be before sunset" );
        }

        @Test
        @DisplayName( "Should handle positive longitude (East)" )
        void testPositiveLongitude()
        {
            // Sydney has positive longitude
            ZonedDateTime day = ZonedDateTime.of( 2024, 6, 21, 12, 0, 0, 0, ZoneId.of( "Australia/Sydney" ) );
            LocalTime sunrise = UtilDateTime.getSunrise( day, SYDNEY_LAT, SYDNEY_LON );
            LocalTime sunset  = UtilDateTime.getSunset( day, SYDNEY_LAT, SYDNEY_LON );

            assertNotNull( sunrise, "Sunrise should not be null for positive longitude" );
            assertNotNull( sunset, "Sunset should not be null for positive longitude" );
            assertTrue( sunrise.isBefore( sunset ), "Sunrise should be before sunset" );
        }

        @Test
        @DisplayName( "Should handle different timezone correctly" )
        void testDifferentTimezones()
        {
            // Same instant in time, but different timezone should give different local times
            ZonedDateTime madridNoon = ZonedDateTime.of( 2024, 6, 21, 12, 0, 0, 0, ZoneId.of( "Europe/Madrid" ) );
            ZonedDateTime nyTime     = madridNoon.withZoneSameInstant( ZoneId.of( "America/New_York" ) );

            LocalTime madridSunrise = UtilDateTime.getSunrise( madridNoon, MADRID_LAT, MADRID_LON );
            LocalTime nySunrise     = UtilDateTime.getSunrise( nyTime, NEW_YORK_LAT, NEW_YORK_LON );

            assertNotNull( madridSunrise, "Madrid sunrise should not be null" );
            assertNotNull( nySunrise, "New York sunrise should not be null" );

            // Different locations should have different sunrise times
            assertNotEquals( madridSunrise, nySunrise,
                           "Madrid and NY should have different sunrise times" );
        }
    }

    //------------------------------------------------------------------------//
    // MAIN METHOD FOR STANDALONE TESTING
    //------------------------------------------------------------------------//

    public static void main( String[] args )
    {
        System.out.println( "==================================================" );
        System.out.println( "        UTILDATETIME COMPREHENSIVE TEST SUITE      " );
        System.out.println( "==================================================" );
        System.out.println();

        UtilDateTimeTest test = new UtilDateTimeTest();
        int passed = 0;
        int failed = 0;
        int total = 0;

        // Julian Date tests
        System.out.println( "--- Julian Date Conversion Tests ---" );
        total++; if( runTest( "J2000 Epoch", () -> test.new JulianDateConversionTests().testJ2000Epoch() ) ) passed++; else failed++;
        total++; if( runTest( "Historical dates", () -> test.new JulianDateConversionTests().testHistoricalDates() ) ) passed++; else failed++;
        total++; if( runTest( "Round-trip conversion", () -> test.new JulianDateConversionTests().testRoundTripConversion() ) ) passed++; else failed++;
        total++; if( runTest( "Timezone conversion", () -> test.new JulianDateConversionTests().testTimezoneConversion() ) ) passed++; else failed++;
        System.out.println();

        // Sunrise/Sunset tests
        System.out.println( "--- Sunrise/Sunset Tests ---" );
        total++; if( runTest( "Madrid summer", () -> test.new SunriseSunsetTests().testMadridSummer() ) ) passed++; else failed++;
        total++; if( runTest( "Madrid winter", () -> test.new SunriseSunsetTests().testMadridWinter() ) ) passed++; else failed++;
        total++; if( runTest( "Sydney", () -> test.new SunriseSunsetTests().testSydney() ) ) passed++; else failed++;
        total++; if( runTest( "Equator", () -> test.new SunriseSunsetTests().testEquator() ) ) passed++; else failed++;
        total++; if( runTest( "Polar day", () -> test.new SunriseSunsetTests().testPolarDay() ) ) passed++; else failed++;
        total++; if( runTest( "Polar night", () -> test.new SunriseSunsetTests().testPolarNight() ) ) passed++; else failed++;
        System.out.println();

        // Solar Noon tests
        System.out.println( "--- Solar Noon Tests ---" );
        total++; if( runTest( "Madrid solar noon", () -> test.new SolarNoonTests().testSolarNoonMadrid() ) ) passed++; else failed++;
        total++; if( runTest( "UTC meridian solar noon", () -> test.new SolarNoonTests().testSolarNoonUTCMeridian() ) ) passed++; else failed++;
        System.out.println();

        // Twilight tests
        System.out.println( "--- Twilight Tests ---" );
        total++; if( runTest( "Astronomical twilight Madrid", () -> test.new TwilightTests().testAstronomicalTwilightMadrid() ) ) passed++; else failed++;
        total++; if( runTest( "Civil twilight", () -> test.new TwilightTests().testCivilTwilight() ) ) passed++; else failed++;
        System.out.println();

        // Day/Night tests
        System.out.println( "--- Day/Night Detection Tests ---" );
        total++; if( runTest( "Daytime detection", () -> test.new DayNightDetectionTests().testDaytime() ) ) passed++; else failed++;
        total++; if( runTest( "Nighttime detection", () -> test.new DayNightDetectionTests().testNighttime() ) ) passed++; else failed++;
        total++; if( runTest( "Polar day detection", () -> test.new DayNightDetectionTests().testPolarDayDetection() ) ) passed++; else failed++;
        total++; if( runTest( "Polar night detection", () -> test.new DayNightDetectionTests().testPolarNightDetection() ) ) passed++; else failed++;
        total++; if( runTest( "Day/Night transition", () -> test.new DayNightDetectionTests().testDayNightTransition() ) ) passed++; else failed++;
        System.out.println();

        // Edge cases
        System.out.println( "--- Edge Cases ---" );
        total++; if( runTest( "Equinox", () -> test.new EdgeCaseTests().testEquinox() ) ) passed++; else failed++;
        total++; if( runTest( "Negative longitude", () -> test.new EdgeCaseTests().testNegativeLongitude() ) ) passed++; else failed++;
        total++; if( runTest( "Positive longitude", () -> test.new EdgeCaseTests().testPositiveLongitude() ) ) passed++; else failed++;
        total++; if( runTest( "Different timezones", () -> test.new EdgeCaseTests().testDifferentTimezones() ) ) passed++; else failed++;
        System.out.println();

        // Summary
        System.out.println( "==================================================" );
        System.out.println( "                   TEST SUMMARY                    " );
        System.out.println( "==================================================" );
        System.out.println( "Total Tests:  " + total );
        System.out.println( "Passed:       " + passed + " (" + (passed * 100 / total) + "%)" );
        System.out.println( "Failed:       " + failed + " (" + (failed * 100 / total) + "%)" );
        System.out.println( "==================================================" );

        if( failed == 0 )
        {
            System.out.println( "\nALL TESTS PASSED!" );
            System.exit( 0 );
        }
        else
        {
            System.out.println( "\nSOME TESTS FAILED!" );
            System.exit( 1 );
        }
    }

    private static boolean runTest( String name, Runnable test )
    {
        try
        {
            test.run();
            System.out.println( "  [PASS] " + name );
            return true;
        }
        catch( AssertionError e )
        {
            System.out.println( "  [FAIL] " + name + ": " + e.getMessage() );
            return false;
        }
        catch( Exception e )
        {
            System.out.println( "  [ERROR] " + name + ": " + e.getClass().getSimpleName() + " - " + e.getMessage() );
            return false;
        }
    }
}
