package com.peyrona.mingle.lang.japi;

import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import org.junit.jupiter.api.Test;
import org.junit.platform.runner.JUnitPlatform;
import org.junit.runner.RunWith;
import static org.junit.jupiter.api.Assertions.*;

@RunWith(JUnitPlatform.class)
public class ReproduceBugsTest {

    @Test
    public void testFromJulianDateOverflow() {
        // Construct a Julian Date that is extremely close to the next day's midnight.
        // JD for 2000-01-01 12:00 UTC is 2451545.0
        // JD for 2000-01-02 00:00 UTC is 2451545.5
        // We want 23:59:59.999... which is just under 2451545.5
        // 0.5 is 12 hours. 1 sec = 1/86400 approx 0.00001157...
        // Let's try to hit the rounding edge case.
        
        // This specific value should trigger the rounding to 60 seconds -> 60 minutes -> 24 hours
        // if not handled correctly.
        double almostMidnight = 2451545.4999999; 
        
        try {
            ZonedDateTime zdt = UtilDateTime.fromJulianDate(almostMidnight, ZoneId.of("UTC"));
            System.out.println("Converted ZDT: " + zdt);
            // Should be 2000-01-02 00:00:00
            assertEquals(2, zdt.getDayOfMonth());
            assertEquals(0, zdt.getHour());
        } catch (java.time.DateTimeException e) {
            fail("Caught DateTimeException: " + e.getMessage());
        }
    }

    @Test
    public void testGetSunriseSunsetInputTimeSensitivity() {
        // Sunrise/Sunset should be the same for the same day, regardless of the input time.
        // Using Madrid (approx 40N, 3W).

        ZonedDateTime noon = ZonedDateTime.of(2024, 6, 21, 12, 0, 0, 0, ZoneId.of("Europe/Madrid"));
        ZonedDateTime lateNight = ZonedDateTime.of(2024, 6, 21, 23, 59, 0, 0, ZoneId.of("Europe/Madrid"));

        LocalTime sunriseNoon = UtilDateTime.getSunrise(noon, 40.4168, -3.7038);
        LocalTime sunsetNoon  = UtilDateTime.getSunset(noon, 40.4168, -3.7038);
        LocalTime sunriseLate = UtilDateTime.getSunrise(lateNight, 40.4168, -3.7038);
        LocalTime sunsetLate  = UtilDateTime.getSunset(lateNight, 40.4168, -3.7038);

        assertNotNull(sunriseNoon);
        assertNotNull(sunsetNoon);
        assertNotNull(sunriseLate);
        assertNotNull(sunsetLate);

        assertEquals(sunriseNoon.getHour(), sunriseLate.getHour(), "Sunrise hour should match");
        assertEquals(sunriseNoon.getMinute(), sunriseLate.getMinute(), "Sunrise minute should match");
        assertEquals(sunsetNoon.getHour(), sunsetLate.getHour(), "Sunset hour should match");
        assertEquals(sunsetNoon.getMinute(), sunsetLate.getMinute(), "Sunset minute should match");
    }

    @Test
    public void testIsDayPolarAccurracy() {
        // North Pole (90N).
        // Vernal Equinox is approx March 20. Sun rises.
        // On March 25, the sun is definitely up at the North Pole.
        // The old logic checked if month >= 4 (April) and <= 10 (October).
        // So March 25 failed this check and returned false (Night).
        // New logic should return TRUE.
        
        ZonedDateTime march25 = ZonedDateTime.of(2024, 3, 25, 12, 0, 0, 0, ZoneId.of("UTC"));
        boolean isDay = UtilDateTime.isDay(march25, 90.0, 0.0);
        
        assertTrue(isDay, "March 25th at North Pole should be Day (Sun is up).");
        
        // Check September 25 (Autumnal Equinox passed, Sun is down)
        // Old logic: month 9 is >= 4 and <= 10, so it returned TRUE (Day).
        // New logic should return FALSE (Night).
        ZonedDateTime sept25 = ZonedDateTime.of(2024, 9, 25, 12, 0, 0, 0, ZoneId.of("UTC"));
        boolean isDaySept = UtilDateTime.isDay(sept25, 90.0, 0.0);
        assertFalse(isDaySept, "Sept 25th at North Pole should be Night (Sun is down).");
    }
}
