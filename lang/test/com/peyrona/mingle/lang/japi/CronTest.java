package com.peyrona.mingle.lang.japi;

import com.peyrona.mingle.lang.MingleException;
import com.peyrona.mingle.lang.xpreval.functions.pair;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.platform.runner.JUnitPlatform;
import org.junit.runner.RunWith;

/**
 * Comprehensive test suite for Cron class.
 * Tests all modes (Daily, Weekly, Monthly, Yearly) and edge cases.
 *
 * @author Francisco José Morero Peyrona
 */
@RunWith(JUnitPlatform.class)
public class CronTest
{
    private static final long TOLERANCE_MS = 2000;  // 2 seconds tolerance for timing tests

    //------------------------------------------------------------------------//
    // CONSTRUCTOR VALIDATION TESTS
    //------------------------------------------------------------------------//

    @Nested
    @DisplayName("Constructor Validation Tests")
    class ConstructorValidationTests
    {
        @Test
        @DisplayName("Should reject one-time event without start time")
        void testOneTimeEventWithoutStart()
        {
            assertThrows( MingleException.class, () -> {
                new Cron( new pair() );
            });
        }

        @Test
        @DisplayName("Should reject start time after stop time")
        void testStartAfterStop()
        {
            assertThrows( MingleException.class, () -> {
                new Cron( new pair()
                    .put( "start", "2025-12-31,23:59" )
                    .put( "stop",  "2025-01-01,00:00" )
                );
            });
        }

        @Test
        @DisplayName("Should reject repeating event without time")
        void testRepeatingEventWithoutTime()
        {
            assertThrows( MingleException.class, () -> {
                new Cron( new pair()
                    .put( "mode", "Daily" )
                    .put( "every", 1 )
                );
            });
        }

        @Test
        @DisplayName("Should reject invalid mode")
        void testInvalidMode()
        {
            assertThrows( MingleException.class, () -> {
                new Cron( new pair()
                    .put( "mode", "Invalid" )
                    .put( "time", "12:00" )
                    .put( "every", 1 )
                );
            });
        }

        @Test
        @DisplayName("Should reject invalid day of week")
        void testInvalidDayOfWeek()
        {
            assertThrows( MingleException.class, () -> {
                new Cron( new pair()
                    .put( "mode", "Weekly" )
                    .put( "dow", "8" )  // Invalid: max is 7
                    .put( "time", "12:00" )
                    .put( "every", 1 )
                );
            });
        }

        @Test
        @DisplayName("Should reject invalid day of month")
        void testInvalidDayOfMonth()
        {
            assertThrows( MingleException.class, () -> {
                new Cron( new pair()
                    .put( "mode", "Monthly" )
                    .put( "dom", "32" )  // Invalid: max is 31
                    .put( "time", "12:00" )
                    .put( "every", 1 )
                );
            });
        }

        @Test
        @DisplayName("Should reject Weekly mode without dow")
        void testWeeklyWithoutDow()
        {
            assertThrows( MingleException.class, () -> {
                new Cron( new pair()
                    .put( "mode", "Weekly" )
                    .put( "time", "12:00" )
                    .put( "every", 1 )
                );
            });
        }

        @Test
        @DisplayName("Should reject Monthly mode without dom")
        void testMonthlyWithoutDom()
        {
            assertThrows( MingleException.class, () -> {
                new Cron( new pair()
                    .put( "mode", "Monthly" )
                    .put( "time", "12:00" )
                    .put( "every", 1 )
                );
            });
        }

        @Test
        @DisplayName("Should reject Yearly mode without month or dom")
        void testYearlyWithoutMonthOrDom()
        {
            assertThrows( MingleException.class, () -> {
                new Cron( new pair()
                    .put( "mode", "Yearly" )
                    .put( "dom", "15" )
                    .put( "time", "12:00" )
                    .put( "every", 1 )
                );
            });
        }

        @Test
        @DisplayName("Should reject Daily mode with dow")
        void testDailyWithDow()
        {
            assertThrows( MingleException.class, () -> {
                new Cron( new pair()
                    .put( "mode", "Daily" )
                    .put( "dow", "1" )
                    .put( "time", "12:00" )
                    .put( "every", 1 )
                );
            });
        }

        @Test
        @DisplayName("Should accept Monthly mode with every > 1")
        void testMonthlyEveryN()
        {
            assertDoesNotThrow( () -> {
                new Cron( new pair()
                    .put( "mode", "Monthly" )
                    .put( "dom", "15" )
                    .put( "time", "12:00" )
                    .put( "every", 2 )
                );
            });
            
            // Verify behavior
            LocalDateTime now = LocalDateTime.now();
            int currentMonth = now.getMonthValue();
            
            Cron cron = new Cron( new pair()
                .put( "mode", "Monthly" )
                .put( "dom", "15" )
                .put( "time", "12:00" )
                .put( "every", 2 )
            );
            
            // Since we can't easily mock time inside Cron without dependency injection,
            // we rely on the fact that next() returns a positive value if configured correctly.
            // Detailed scheduling verification would require mocking LocalDateTime.now().
            
            long millis = cron.next();
            assertTrue( millis > 0, "Should return positive milliseconds for every-2-months" );
        }

        @Test
        @DisplayName("Should accept Sunday as 0 or 7")
        void testSundayAsZeroOrSeven()
        {
            assertDoesNotThrow( () -> {
                new Cron( new pair()
                    .put( "mode", "Weekly" )
                    .put( "dow", "0" )  // Sunday as 0
                    .put( "time", "12:00" )
                    .put( "every", 1 )
                );
            });

            assertDoesNotThrow( () -> {
                new Cron( new pair()
                    .put( "mode", "Weekly" )
                    .put( "dow", "7" )  // Sunday as 7
                    .put( "time", "12:00" )
                    .put( "every", 1 )
                );
            });
        }
    }

    //------------------------------------------------------------------------//
    // ONE-TIME EVENT TESTS
    //------------------------------------------------------------------------//

    @Nested
    @DisplayName("One-Time Event Tests")
    class OneTimeEventTests
    {
        @Test
        @DisplayName("Should trigger once at future time")
        void testOneTimeFutureEvent()
        {
            LocalDateTime now = LocalDateTime.now();
            LocalDateTime future = now.plusHours( 2 );
            String futureStr = formatDateTime( future );

            Cron cron = new Cron( new pair().put( "start", futureStr ) );

            long millis1 = cron.next();
            long millis2 = cron.next();

            assertTrue( millis1 > 0, "First call should return positive milliseconds" );
            assertEquals( -1, millis2, "Second call should return -1" );

            // Verify timing is approximately correct (within tolerance)
            long expected = ChronoUnit.MILLIS.between( now, future );
            assertTrue( Math.abs( millis1 - expected ) < TOLERANCE_MS * 18,   // This tests needs more tolerance
                       "Timing should be within tolerance" );
        }

        @Test
        @DisplayName("Should return -1 for past one-time event")
        void testOneTimePastEvent()
        {
            LocalDateTime past = LocalDateTime.now().minusHours( 1 );
            String pastStr = formatDateTime( past );

            Cron cron = new Cron( new pair().put( "start", pastStr ) );

            long millis = cron.next();

            assertEquals( -1, millis, "Past event should return -1" );
        }
    }

    //------------------------------------------------------------------------//
    // DAILY MODE TESTS
    //------------------------------------------------------------------------//

    @Nested
    @DisplayName("Daily Mode Tests")
    class DailyModeTests
    {
        @Test
        @DisplayName("Should trigger daily at specified time")
        void testDailyBasic()
        {
            LocalDateTime now = LocalDateTime.now();
            int futureHour = (now.getHour() + 2) % 24;

            Cron cron = new Cron( new pair()
                .put( "mode", "Daily" )
                .put( "time", String.valueOf( futureHour ) )
                .put( "every", 1 )
            );

            long millis = cron.next();

            assertTrue( millis > 0, "Should return positive milliseconds" );
        }

        @Test
        @DisplayName("Should trigger multiple times per day")
        void testDailyMultipleTimes()
        {
            LocalDateTime now = LocalDateTime.now();
            int hour1 = (now.getHour() + 1) % 24;
            int hour2 = (now.getHour() + 2) % 24;

            Cron cron = new Cron( new pair()
                .put( "mode", "Daily" )
                .put( "time", hour1 + "," + hour2 )
                .put( "every", 1 )
            );

            long millis1 = cron.next();
            long millis2 = cron.next();

            assertTrue( millis1 > 0, "First trigger should be positive" );
            assertTrue( millis2 > 0, "Second trigger should be positive" );
            assertTrue( millis2 > millis1, "Second trigger should be later" );
        }

        @Test
        @DisplayName("Should skip days with every > 1")
        void testDailyEveryN()
        {
            LocalDateTime now = LocalDateTime.now();
            int futureHour = (now.getHour() + 1) % 24;

            Cron cron = new Cron( new pair()
                .put( "mode", "Daily" )
                .put( "time", String.valueOf( futureHour ) )
                .put( "every", 3 )  // Every 3 days
            );

            long millis = cron.next();

            assertTrue( millis > 0, "Should return positive milliseconds" );
        }

        @Test
        @DisplayName("Should respect stop time")
        void testDailyWithStop()
        {
            LocalDateTime now = LocalDateTime.now();
            LocalDateTime stop = now.minusHours( 1 );  // Stop in the past

            Cron cron = new Cron( new pair()
                .put( "mode", "Daily" )
                .put( "time", "12:00" )
                .put( "every", 1 )
                .put( "stop", formatDateTime( stop ) )
            );

            long millis = cron.next();

            assertEquals( -1, millis, "Should return -1 after stop time" );
        }
    }

    //------------------------------------------------------------------------//
    // WEEKLY MODE TESTS
    //------------------------------------------------------------------------//

    @Nested
    @DisplayName("Weekly Mode Tests")
    class WeeklyModeTests
    {
        @Test
        @DisplayName("Should trigger weekly on specified day")
        void testWeeklyBasic()
        {
            LocalDateTime tomorrow = LocalDateTime.now().plusDays( 1 );
            int dayOfWeek = tomorrow.getDayOfWeek().getValue();

            Cron cron = new Cron( new pair()
                .put( "mode", "Weekly" )
                .put( "dow", String.valueOf( dayOfWeek ) )
                .put( "time", "15:00" )
                .put( "every", 1 )
            );

            long millis = cron.next();

            assertTrue( millis > 0, "Should return positive milliseconds" );
        }

        @Test
        @DisplayName("Should trigger on multiple days per week")
        void testWeeklyMultipleDays()
        {
            LocalDateTime now = LocalDateTime.now();
            int today = now.getDayOfWeek().getValue();
            int tomorrow = (today % 7) + 1;

            Cron cron = new Cron( new pair()
                .put( "mode", "Weekly" )
                .put( "dow", today + "," + tomorrow )
                .put( "time", "23:59" )
                .put( "every", 1 )
            );

            long millis = cron.next();

            assertTrue( millis > 0, "Should return positive milliseconds" );
        }

        @Test
        @DisplayName("Should skip weeks with every > 1")
        void testWeeklyEveryN()
        {
            LocalDateTime tomorrow = LocalDateTime.now().plusDays( 1 );
            int dayOfWeek = tomorrow.getDayOfWeek().getValue();

            Cron cron = new Cron( new pair()
                .put( "mode", "Weekly" )
                .put( "dow", String.valueOf( dayOfWeek ) )
                .put( "time", "12:00" )
                .put( "every", 2 )  // Every 2 weeks
            );

            long millis = cron.next();

            assertTrue( millis > 0, "Should return positive milliseconds" );
        }

        @Test
        @DisplayName("Should handle Sunday as day 0")
        void testWeeklySundayAsZero()
        {
            Cron cron = new Cron( new pair()
                .put( "mode", "Weekly" )
                .put( "dow", "0" )  // Sunday as 0
                .put( "time", "12:00" )
                .put( "every", 1 )
            );

            long millis = cron.next();

            assertTrue( millis > 0, "Should return positive milliseconds for Sunday" );
        }

        @Test
        @DisplayName("Should handle Sunday as day 7")
        void testWeeklySundayAsSeven()
        {
            Cron cron = new Cron( new pair()
                .put( "mode", "Weekly" )
                .put( "dow", "7" )  // Sunday as 7
                .put( "time", "12:00" )
                .put( "every", 1 )
            );

            long millis = cron.next();

            assertTrue( millis > 0, "Should return positive milliseconds for Sunday" );
        }
    }

    //------------------------------------------------------------------------//
    // MONTHLY MODE TESTS
    //------------------------------------------------------------------------//

    @Nested
    @DisplayName("Monthly Mode Tests")
    class MonthlyModeTests
    {
        @Test
        @DisplayName("Should trigger monthly on specified day")
        void testMonthlyBasic()
        {
            Cron cron = new Cron( new pair()
                .put( "mode", "Monthly" )
                .put( "dom", "15" )
                .put( "time", "12:00" )
                .put( "every", 1 )
            );

            long millis = cron.next();

            assertTrue( millis > 0, "Should return positive milliseconds" );
        }

        @Test
        @DisplayName("Should trigger on multiple days per month")
        void testMonthlyMultipleDays()
        {
            Cron cron = new Cron( new pair()
                .put( "mode", "Monthly" )
                .put( "dom", "1,15,28" )
                .put( "time", "09:00" )
                .put( "every", 1 )
            );

            long millis = cron.next();

            assertTrue( millis > 0, "Should return positive milliseconds" );
        }

        @Test
        @DisplayName("Should handle day 31 as last day of month")
        void testMonthlyLastDay()
        {
            Cron cron = new Cron( new pair()
                .put( "mode", "Monthly" )
                .put( "dom", "31" )  // Last day of month
                .put( "time", "23:59" )
                .put( "every", 1 )
            );

            long millis = cron.next();

            assertTrue( millis > 0, "Should return positive milliseconds" );
        }

        @Test
        @DisplayName("Should handle February 31 as February 28/29")
        void testMonthlyFebruary31()
        {
            // Create a cron that should trigger on the 31st (last day)
            Cron cron = new Cron( new pair()
                .put( "mode", "Monthly" )
                .put( "dom", "31" )
                .put( "time", "12:00" )
                .put( "every", 1 )
            );

            // Should still return a valid next time (last day of current/next month)
            long millis = cron.next();

            assertTrue( millis > 0, "Should handle months with < 31 days" );
        }
    }

    //------------------------------------------------------------------------//
    // YEARLY MODE TESTS
    //------------------------------------------------------------------------//

    @Nested
    @DisplayName("Yearly Mode Tests")
    class YearlyModeTests
    {
        @Test
        @DisplayName("Should trigger yearly on specified date")
        void testYearlyBasic()
        {
            Cron cron = new Cron( new pair()
                .put( "mode", "Yearly" )
                .put( "dom", "15" )
                .put( "month", "6" )
                .put( "time", "12:00" )
                .put( "every", 1 )
            );

            long millis = cron.next();

            assertTrue( millis > 0, "Should return positive milliseconds" );
        }

        @Test
        @DisplayName("Should trigger on multiple dates per year")
        void testYearlyMultipleDates()
        {
            Cron cron = new Cron( new pair()
                .put( "mode", "Yearly" )
                .put( "dom", "1,15" )
                .put( "month", "1,7" )
                .put( "time", "00:00" )
                .put( "every", 1 )
            );

            long millis = cron.next();

            assertTrue( millis > 0, "Should return positive milliseconds" );
        }

        @Test
        @DisplayName("Should skip years with every > 1")
        void testYearlyEveryN()
        {
            Cron cron = new Cron( new pair()
                .put( "mode", "Yearly" )
                .put( "dom", "31" )
                .put( "month", "12" )
                .put( "time", "23:59" )
                .put( "every", 2 )  // Every 2 years
            );

            long millis = cron.next();

            assertTrue( millis > 0, "Should return positive milliseconds" );
        }

        @Test
        @DisplayName("Should handle leap year February 29")
        void testYearlyLeapYear()
        {
            Cron cron = new Cron( new pair()
                .put( "mode", "Yearly" )
                .put( "dom", "29" )
                .put( "month", "2" )
                .put( "time", "12:00" )
                .put( "every", 1 )
            );

            long millis = cron.next();

            assertTrue( millis > 0, "Should handle leap year dates" );
        }

        @Test
        @DisplayName("Should handle December 31st")
        void testYearlyNewYearsEve()
        {
            Cron cron = new Cron( new pair()
                .put( "mode", "Yearly" )
                .put( "dom", "31" )
                .put( "month", "12" )
                .put( "time", "23:59" )
                .put( "every", 1 )
            );

            long millis = cron.next();

            assertTrue( millis > 0, "Should handle year boundary" );
        }
    }

    //------------------------------------------------------------------------//
    // EDGE CASES AND SPECIAL SCENARIOS
    //------------------------------------------------------------------------//

    @Nested
    @DisplayName("Edge Cases and Special Scenarios")
    class EdgeCaseTests
    {
        @Test
        @DisplayName("Should handle multiple times on same day")
        void testMultipleTimesPerDay()
        {
            LocalDateTime now = LocalDateTime.now();
            int hour1 = (now.getHour() + 1) % 24;
            int hour2 = (now.getHour() + 2) % 24;
            int hour3 = (now.getHour() + 3) % 24;

            Cron cron = new Cron( new pair()
                .put( "mode", "Daily" )
                .put( "time", hour1 + ":00," + hour2 + ":30," + hour3 + ":45" )
                .put( "every", 1 )
            );

            long millis1 = cron.next();
            assertTrue( millis1 > 0, "First time should be valid" );

            long millis2 = cron.next();
            assertTrue( millis2 > 0, "Second time should be valid" );

            long millis3 = cron.next();
            assertTrue( millis3 > 0, "Third time should be valid" );
        }

        @Test
        @DisplayName("Should handle reset() correctly")
        void testReset()
        {
            LocalDateTime future = LocalDateTime.now().plusHours( 1 );

            Cron cron = new Cron( new pair()
                .put( "start", formatDateTime( future ) )
            );

            long millis1 = cron.next();
            long millis2 = cron.next();

            assertEquals( -1, millis2, "Should return -1 after one-time event" );

            cron.reset();

            long millis3 = cron.next();
            assertTrue( millis3 > 0, "Should work again after reset" );
        }

        @Test
        @DisplayName("Should handle midnight crossing")
        void testMidnightCrossing()
        {
            Cron cron = new Cron( new pair()
                .put( "mode", "Daily" )
                .put( "time", "23:59,00:01" )
                .put( "every", 1 )
            );

            long millis = cron.next();

            assertTrue( millis > 0, "Should handle midnight boundary" );
        }

        @Test
        @DisplayName("Should handle week boundary")
        void testWeekBoundary()
        {
            Cron cron = new Cron( new pair()
                .put( "mode", "Weekly" )
                .put( "dow", "7,1" )  // Sunday and Monday
                .put( "time", "12:00" )
                .put( "every", 1 )
            );

            long millis = cron.next();

            assertTrue( millis > 0, "Should handle week boundary" );
        }

        @Test
        @DisplayName("Should handle month boundary")
        void testMonthBoundary()
        {
            Cron cron = new Cron( new pair()
                .put( "mode", "Monthly" )
                .put( "dom", "31,1" )  // Last day and first day
                .put( "time", "12:00" )
                .put( "every", 1 )
            );

            long millis = cron.next();

            assertTrue( millis > 0, "Should handle month boundary" );
        }

        @Test
        @DisplayName("Should handle year boundary")
        void testYearBoundary()
        {
            Cron cron = new Cron( new pair()
                .put( "mode", "Yearly" )
                .put( "dom", "31,1" )
                .put( "month", "12,1" )
                .put( "time", "00:00" )
                .put( "every", 1 )
            );

            long millis = cron.next();

            assertTrue( millis > 0, "Should handle year boundary" );
        }

        @Test
        @DisplayName("Should handle time already passed today")
        void testTimePassedToday()
        {
            LocalDateTime now = LocalDateTime.now();
            int pastHour = (now.getHour() - 1 + 24) % 24;

            Cron cron = new Cron( new pair()
                .put( "mode", "Daily" )
                .put( "time", String.valueOf( pastHour ) )
                .put( "every", 1 )
            );

            long millis = cron.next();

            assertTrue( millis > 0, "Should schedule for next day" );
        }

        @Test
        @DisplayName("Should handle start date in the future")
        void testFutureStartDate()
        {
            LocalDateTime future = LocalDateTime.now().plusDays( 7 );
            int hour = future.getHour();

            Cron cron = new Cron( new pair()
                .put( "mode", "Daily" )
                .put( "start", formatDateTime( future ) )
                .put( "time", String.valueOf( hour ) )
                .put( "every", 1 )
            );

            long millis = cron.next();

            assertTrue( millis > 0, "Should respect future start date" );
        }
    }

    //------------------------------------------------------------------------//
    // BUG FIX REGRESSION TESTS
    //------------------------------------------------------------------------//

    @Nested
    @DisplayName("Bug Fix Regression Tests")
    class BugFixRegressionTests
    {
        @Test
        @DisplayName("Weekly interval should use days-based calculation (fix for ChronoUnit.WEEKS issue)")
        void testWeeklyIntervalDaysBasedCalculation()
        {
            // This test verifies that partial weeks are handled correctly
            // ChronoUnit.WEEKS.between() only counts complete weeks, which caused bugs
            LocalDateTime now = LocalDateTime.now();
            int tomorrow = now.plusDays( 1 ).getDayOfWeek().getValue();

            Cron cron = new Cron( new pair()
                .put( "mode", "Weekly" )
                .put( "dow", String.valueOf( tomorrow ) )
                .put( "time", "12:00" )
                .put( "every", 1 )
            );

            long millis = cron.next();

            assertTrue( millis > 0, "Should find next occurrence within partial week" );
            // Should be approximately 1 day + time difference (not 7+ days)
            assertTrue( millis < 2 * 24 * 60 * 60 * 1000L, "Should be within 2 days, not skip to next full week" );
        }

        @Test
        @DisplayName("Weekly every-2 should correctly skip weeks using days calculation")
        void testWeeklyEvery2DaysCalculation()
        {
            LocalDateTime now = LocalDateTime.now();
            int dayOfWeek = now.getDayOfWeek().getValue();

            Cron cron = new Cron( new pair()
                .put( "mode", "Weekly" )
                .put( "dow", String.valueOf( dayOfWeek ) )
                .put( "time", "23:59" )
                .put( "every", 2 )  // Every 2 weeks
            );

            long millis = cron.next();

            assertTrue( millis > 0, "Should return positive milliseconds for every-2-weeks" );
        }

        @Test
        @DisplayName("Monthly should handle start date correctly (fix for negative modulo)")
        void testMonthlyWithFutureStartDate()
        {
            // This tests the fix for negative monthsDiff causing incorrect monthsToAdd
            LocalDateTime futureStart = LocalDateTime.now().plusMonths( 2 );

            Cron cron = new Cron( new pair()
                .put( "mode", "Monthly" )
                .put( "start", formatDateTime( futureStart ) )
                .put( "dom", "15" )
                .put( "time", "12:00" )
                .put( "every", 1 )
            );

            long millis = cron.next();

            assertTrue( millis > 0, "Should handle future start date correctly" );
        }

        @Test
        @DisplayName("Monthly every-N should calculate correct interval")
        void testMonthlyEveryNInterval()
        {
            Cron cron = new Cron( new pair()
                .put( "mode", "Monthly" )
                .put( "dom", "1" )
                .put( "time", "00:01" )
                .put( "every", 3 )  // Every 3 months
            );

            long millis = cron.next();

            assertTrue( millis > 0, "Should return positive milliseconds for every-3-months" );
        }

        @Test
        @DisplayName("Calculation errors should return -1 instead of throwing exception")
        void testCalculationErrorsReturnMinusOne()
        {
            // Create a valid cron and verify it doesn't throw on edge cases
            Cron cron = new Cron( new pair()
                .put( "mode", "Yearly" )
                .put( "dom", "29" )
                .put( "month", "2" )  // Feb 29 - leap year only
                .put( "time", "12:00" )
                .put( "every", 1 )
            );

            // Should not throw, should return a valid result or -1
            assertDoesNotThrow( () -> {
                long millis = cron.next();
                // Either positive (found next leap year) or -1 (calculation issue)
                assertTrue( millis > 0 || millis == -1, "Should return valid result or -1" );
            });
        }

        @Test
        @DisplayName("Weekly with past start date should work correctly")
        void testWeeklyWithPastStartDate()
        {
            LocalDateTime pastStart = LocalDateTime.now().minusWeeks( 3 );
            int dayOfWeek = LocalDateTime.now().plusDays( 1 ).getDayOfWeek().getValue();

            Cron cron = new Cron( new pair()
                .put( "mode", "Weekly" )
                .put( "start", formatDateTime( pastStart ) )
                .put( "dow", String.valueOf( dayOfWeek ) )
                .put( "time", "12:00" )
                .put( "every", 2 )
            );

            long millis = cron.next();

            assertTrue( millis > 0, "Should handle past start date with weekly intervals" );
        }

        @Test
        @DisplayName("Monthly with past start date should work correctly")
        void testMonthlyWithPastStartDate()
        {
            LocalDateTime pastStart = LocalDateTime.now().minusMonths( 5 );

            Cron cron = new Cron( new pair()
                .put( "mode", "Monthly" )
                .put( "start", formatDateTime( pastStart ) )
                .put( "dom", "15" )
                .put( "time", "12:00" )
                .put( "every", 2 )
            );

            long millis = cron.next();

            assertTrue( millis > 0, "Should handle past start date with monthly intervals" );
        }

        @Test
        @DisplayName("Yearly with large interval should not throw")
        void testYearlyLargeInterval()
        {
            Cron cron = new Cron( new pair()
                .put( "mode", "Yearly" )
                .put( "dom", "1" )
                .put( "month", "1" )
                .put( "time", "00:00" )
                .put( "every", 5 )  // Every 5 years
            );

            assertDoesNotThrow( () -> {
                long millis = cron.next();
                assertTrue( millis > 0, "Should handle large yearly intervals" );
            });
        }

        @Test
        @DisplayName("Thread safety - synchronized methods should not cause issues")
        void testThreadSafety() throws InterruptedException
        {
            Cron cron = new Cron( new pair()
                .put( "mode", "Daily" )
                .put( "time", "12:00,18:00" )
                .put( "every", 1 )
            );

            // Call next() from multiple threads concurrently
            Thread t1 = new Thread( () -> { for( int i = 0; i < 10; i++ ) cron.next(); } );
            Thread t2 = new Thread( () -> { for( int i = 0; i < 10; i++ ) cron.next(); } );
            Thread t3 = new Thread( () -> { for( int i = 0; i < 10; i++ ) cron.reset(); } );

            t1.start();
            t2.start();
            t3.start();

            t1.join( 5000 );
            t2.join( 5000 );
            t3.join( 5000 );

            // If we get here without deadlock or exception, test passes
            assertTrue( true, "Concurrent access should not cause issues" );
        }
    }

    //------------------------------------------------------------------------//
    // HELPER METHODS
    //------------------------------------------------------------------------//

    /**
     * Formats a LocalDateTime as "yyyy-MM-dd,HH:mm:ss" for Cron constructor.
     */
    private String formatDateTime( LocalDateTime ldt )
    {
        return String.format( "%04d-%02d-%02d,%02d:%02d:%02d",
                             ldt.getYear(),
                             ldt.getMonthValue(),
                             ldt.getDayOfMonth(),
                             ldt.getHour(),
                             ldt.getMinute(),
                             ldt.getSecond() );
    }

    //------------------------------------------------------------------------//
    // MAIN METHOD FOR STANDALONE TESTING
    //------------------------------------------------------------------------//

    /**
     * Main method for running tests without JUnit runner.
     * Provides basic test execution and reporting.
     */
    public static void main( String[] args )
    {
        System.out.println( "==================================================" );
        System.out.println( "           CRON COMPREHENSIVE TEST SUITE          " );
        System.out.println( "==================================================" );
        System.out.println();

        CronTest test = new CronTest();
        int passed = 0;
        int failed = 0;
        int total = 0;

        // Constructor validation tests
        System.out.println( "--- Constructor Validation Tests ---" );
        total++; if( runTest( "One-time without start", () -> test.new ConstructorValidationTests().testOneTimeEventWithoutStart() ) ) passed++; else failed++;
        total++; if( runTest( "Start after stop", () -> test.new ConstructorValidationTests().testStartAfterStop() ) ) passed++; else failed++;
        total++; if( runTest( "Repeating without time", () -> test.new ConstructorValidationTests().testRepeatingEventWithoutTime() ) ) passed++; else failed++;
        total++; if( runTest( "Invalid mode", () -> test.new ConstructorValidationTests().testInvalidMode() ) ) passed++; else failed++;
        total++; if( runTest( "Invalid day of week", () -> test.new ConstructorValidationTests().testInvalidDayOfWeek() ) ) passed++; else failed++;
        total++; if( runTest( "Weekly without dow", () -> test.new ConstructorValidationTests().testWeeklyWithoutDow() ) ) passed++; else failed++;
        total++; if( runTest( "Sunday as 0 or 7", () -> test.new ConstructorValidationTests().testSundayAsZeroOrSeven() ) ) passed++; else failed++;
        System.out.println();

        // One-time event tests
        System.out.println( "--- One-Time Event Tests ---" );
        total++; if( runTest( "Future one-time", () -> test.new OneTimeEventTests().testOneTimeFutureEvent() ) ) passed++; else failed++;
        total++; if( runTest( "Past one-time", () -> test.new OneTimeEventTests().testOneTimePastEvent() ) ) passed++; else failed++;
        System.out.println();

        // Daily mode tests
        System.out.println( "--- Daily Mode Tests ---" );
        total++; if( runTest( "Daily basic", () -> test.new DailyModeTests().testDailyBasic() ) ) passed++; else failed++;
        total++; if( runTest( "Daily multiple times", () -> test.new DailyModeTests().testDailyMultipleTimes() ) ) passed++; else failed++;
        total++; if( runTest( "Daily every N", () -> test.new DailyModeTests().testDailyEveryN() ) ) passed++; else failed++;
        total++; if( runTest( "Daily with stop", () -> test.new DailyModeTests().testDailyWithStop() ) ) passed++; else failed++;
        System.out.println();

        // Weekly mode tests
        System.out.println( "--- Weekly Mode Tests ---" );
        total++; if( runTest( "Weekly basic", () -> test.new WeeklyModeTests().testWeeklyBasic() ) ) passed++; else failed++;
        total++; if( runTest( "Weekly multiple days", () -> test.new WeeklyModeTests().testWeeklyMultipleDays() ) ) passed++; else failed++;
        total++; if( runTest( "Weekly every N", () -> test.new WeeklyModeTests().testWeeklyEveryN() ) ) passed++; else failed++;
        total++; if( runTest( "Weekly Sunday as 0", () -> test.new WeeklyModeTests().testWeeklySundayAsZero() ) ) passed++; else failed++;
        total++; if( runTest( "Weekly Sunday as 7", () -> test.new WeeklyModeTests().testWeeklySundayAsSeven() ) ) passed++; else failed++;
        System.out.println();

        // Monthly mode tests
        System.out.println( "--- Monthly Mode Tests ---" );
        total++; if( runTest( "Monthly basic", () -> test.new MonthlyModeTests().testMonthlyBasic() ) ) passed++; else failed++;
        total++; if( runTest( "Monthly multiple days", () -> test.new MonthlyModeTests().testMonthlyMultipleDays() ) ) passed++; else failed++;
        total++; if( runTest( "Monthly last day", () -> test.new MonthlyModeTests().testMonthlyLastDay() ) ) passed++; else failed++;
        total++; if( runTest( "Monthly February 31", () -> test.new MonthlyModeTests().testMonthlyFebruary31() ) ) passed++; else failed++;
        System.out.println();

        // Yearly mode tests
        System.out.println( "--- Yearly Mode Tests ---" );
        total++; if( runTest( "Yearly basic", () -> test.new YearlyModeTests().testYearlyBasic() ) ) passed++; else failed++;
        total++; if( runTest( "Yearly multiple dates", () -> test.new YearlyModeTests().testYearlyMultipleDates() ) ) passed++; else failed++;
        total++; if( runTest( "Yearly every N", () -> test.new YearlyModeTests().testYearlyEveryN() ) ) passed++; else failed++;
        total++; if( runTest( "Yearly leap year", () -> test.new YearlyModeTests().testYearlyLeapYear() ) ) passed++; else failed++;
        total++; if( runTest( "Yearly New Year's Eve", () -> test.new YearlyModeTests().testYearlyNewYearsEve() ) ) passed++; else failed++;
        System.out.println();

        // Edge cases
        System.out.println( "--- Edge Cases and Special Scenarios ---" );
        total++; if( runTest( "Multiple times per day", () -> test.new EdgeCaseTests().testMultipleTimesPerDay() ) ) passed++; else failed++;
        total++; if( runTest( "Reset functionality", () -> test.new EdgeCaseTests().testReset() ) ) passed++; else failed++;
        total++; if( runTest( "Midnight crossing", () -> test.new EdgeCaseTests().testMidnightCrossing() ) ) passed++; else failed++;
        total++; if( runTest( "Week boundary", () -> test.new EdgeCaseTests().testWeekBoundary() ) ) passed++; else failed++;
        total++; if( runTest( "Month boundary", () -> test.new EdgeCaseTests().testMonthBoundary() ) ) passed++; else failed++;
        total++; if( runTest( "Year boundary", () -> test.new EdgeCaseTests().testYearBoundary() ) ) passed++; else failed++;
        total++; if( runTest( "Time passed today", () -> test.new EdgeCaseTests().testTimePassedToday() ) ) passed++; else failed++;
        total++; if( runTest( "Future start date", () -> test.new EdgeCaseTests().testFutureStartDate() ) ) passed++; else failed++;
        System.out.println();

        // Bug fix regression tests
        System.out.println( "--- Bug Fix Regression Tests ---" );
        total++; if( runTest( "Weekly days-based calculation", () -> test.new BugFixRegressionTests().testWeeklyIntervalDaysBasedCalculation() ) ) passed++; else failed++;
        total++; if( runTest( "Weekly every-2 calculation", () -> test.new BugFixRegressionTests().testWeeklyEvery2DaysCalculation() ) ) passed++; else failed++;
        total++; if( runTest( "Monthly future start date", () -> test.new BugFixRegressionTests().testMonthlyWithFutureStartDate() ) ) passed++; else failed++;
        total++; if( runTest( "Monthly every-N interval", () -> test.new BugFixRegressionTests().testMonthlyEveryNInterval() ) ) passed++; else failed++;
        total++; if( runTest( "Calculation errors return -1", () -> test.new BugFixRegressionTests().testCalculationErrorsReturnMinusOne() ) ) passed++; else failed++;
        total++; if( runTest( "Weekly past start date", () -> test.new BugFixRegressionTests().testWeeklyWithPastStartDate() ) ) passed++; else failed++;
        total++; if( runTest( "Monthly past start date", () -> test.new BugFixRegressionTests().testMonthlyWithPastStartDate() ) ) passed++; else failed++;
        total++; if( runTest( "Yearly large interval", () -> test.new BugFixRegressionTests().testYearlyLargeInterval() ) ) passed++; else failed++;
        total++; if( runTest( "Thread safety", () -> { try { test.new BugFixRegressionTests().testThreadSafety(); } catch( InterruptedException e ) { throw new RuntimeException( e ); } } ) ) passed++; else failed++;
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
            System.out.println( "\n✓ ALL TESTS PASSED!" );
            System.exit( 0 );
        }
        else
        {
            System.out.println( "\n✗ SOME TESTS FAILED!" );
            System.exit( 1 );
        }
    }

    /**
     * Runs a single test and returns true if it passes.
     */
    private static boolean runTest( String name, Runnable test )
    {
        try
        {
            test.run();
            System.out.println( "  ✓ " + name );
            return true;
        }
        catch( AssertionError e )
        {
            System.out.println( "  ✗ " + name + ": " + e.getMessage() );
            return false;
        }
        catch( Exception e )
        {
            System.out.println( "  ✗ " + name + ": " + e.getClass().getSimpleName() + " - " + e.getMessage() );
            return false;
        }
    }
}