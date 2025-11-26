
package com.peyrona.mingle.lang.japi;

import com.peyrona.mingle.lang.MingleException;
import com.peyrona.mingle.lang.lexer.Language;

/**
 * Internally Mingle only uses Float for numbers.
 *
 * @author Francisco José Morero Peyrona
 *
 * Official web site at: <a href="https://github.com/peyrona/mingle">https://github.com/peyrona/mingle</a>
 */
public final class UtilUnit
{
    /** 1 millisecond */            public final static long MILLIS = 1;           // Just for code clarity.
    /** 1 second in milliseconds */  public final static long SECOND = 1000;
    /** 1 minute in milliseconds */ public final static long MINUTE = SECOND * 60; // All have to be long because if int overflow
    /** 1 hour in milliseconds */   public final static long HOUR   = MINUTE * 60; // can happen under certain circumstances.
    /** 1 day in milliseconds */    public final static long DAY    = HOUR   * 24; // e.g.: DAY * 30

    public final static int BYTE      = 1;
    public final static int KILO_BYTE = BYTE      * 1024;
    public final static int MEGA_BYTE = KILO_BYTE * 1024;
    public final static int GIGA_BYTE = MEGA_BYTE * 1024;
    public final static int TERA_BYTE = GIGA_BYTE * 1024;
    public final static int PETA_BYTE = TERA_BYTE * 1024;

    //------------------------------------------------------------------------//
    private UtilUnit() {}  // Avoid this class instances creation
    //------------------------------------------------------------------------//

    /**
     * Converts a temperature value from Celsius to Fahrenheit.
     *
     * @param celsius The temperature in Celsius to be converted.
     * @return The equivalent temperature in Fahrenheit.
     */
    public static float celsius2fahrenheit( float celsius )
    {
        return (celsius * 9 / 5) + 32;
    }

    /**
     * Converts a temperature value from Fahrenheit to Celsius.
     *
     * @param fahrenheit The temperature in Fahrenheit to be converted.
     * @return The equivalent temperature in Celsius.
     */
    public static float fahrenheit2celsius( float fahrenheit )
    {
        return (fahrenheit - 32) * 5 / 9;
    }

    /**
     * Converts a temperature value from Kelvin to Celsius.
     *
     * @param kelvin The temperature in Kelvin to be converted.
     * @return The equivalent temperature in Celsius.
     */
    public static float kelvin2celsius( float kelvin )
    {
        return (kelvin - 273.15f);
    }

    /**
     * Converts a temperature value from Celsius to Kelvin.
     *
     * @param celsius The temperature in Celsius to be converted.
     * @return The equivalent temperature in Kelvin.
     */
    public static float celsius2kelvin( float celsius )
    {
        return (celsius + 273.15f);
    }

    /**
     * Converts speed from kilometers per hour to miles per hour.
     *
     * @param kmhr Value to convert.
     * @return Converts speed from miles per hour to kilometers per hour.
     */
    public static float kmhr2mileshr( float kmhr )
    {
        return (kmhr / 1.609344f);
    }

    /**
     * Converts speed from miles per hour to kilometers per hour.
     *
     * @param mhr Value to convert.
     * @return Converts speed from miles per hour to kilometers per hour.
     */
    public static float mileshr2kmhr( float mhr )
    {
        return (mhr * 1.609344f);
    }

    /**
     * Returns true if:<pre>(value >= min) && (value <= max)</pre>
     * @param min Minimum value
     * @param value The value
     * @param max Maximum value
     * @return true if:<pre>(value >= min) && (value <= max)</pre>
     */
    public static boolean isBetween( int min, int value, int max )
    {
        if( min > max )
            throw new MingleException( "Min value is bigger than Max" );

        return (value >= min) && (value <= max);
    }

    /**
     * Normalizes value making it neither greater than max nor less than min.
     *
     * @param min Minimum accepted value
     * @param value Value to normalize
     * @param max Maximum accepted value
     * @return The normalized value.
     */
    public static int setBetween( int min, int value, int max )
    {
        if( min > max )
            throw new MingleException( "Min value is bigger than Max" );

        return ((value > max) ? max
                              : ((value < min) ? min
                                               : value) );
    }

    /**
     * Returns true if:<pre>(value >= min) && (value <= max)</pre>
     * @param min Minimum value
     * @param value The value
     * @param max Maximum value
     * @return true if:<pre>(value >= min) && (value <= max)</pre>
     */
    public static boolean isBetween( long min, long value, long max )
    {
        if( min > max )
            throw new MingleException( "Min value is bigger than Max" );

        return (value >= min) && (value <= max);
    }

    /**
     * Normalizes value making it neither greater than max nor less than min.
     *
     * @param min Minimum accepted value
     * @param value Value to normalize
     * @param max Maximum accepted value
     * @return The normalized value.
     */
    public static long setBetween( long min, long value, long max )
    {
        if( min > max )
            throw new MingleException( "Min value is bigger than Max" );

        return ((value > max) ? max
                              : ((value < min) ? min
                                               : value) );
    }

    /**
     * Returns true if:<pre>(value >= min) && (value <= max)</pre>
     * @param min Minimum value
     * @param value The value
     * @param max Maximum value
     * @return true if:<pre>(value >= min) && (value <= max)</pre>
     */
    public static boolean isBetween( float min, float value, float max )
    {
        if( min > max )
            throw new MingleException( "Min value is bigger than Max" );

        return (value >= min) && (value <= max);
    }

    /**
     * Normalizes value making it neither greater than max nor less than min.
     *
     * @param min Minimum accepted value
     * @param value Value to normalize
     * @param max Maximum accepted value
     * @return The normalized value.
     */
    public static float setBetween( float min, float value, float max )
    {
        if( min > max )
            throw new MingleException( "Min value is bigger than Max" );

        return ((value > max) ? max
                              : ((value < min) ? min
                                               : value) );
    }

    /**
 * Converts a percentage to an absolute value within a specified range.
 *
 * For example, 50% of a range between 0 and 255 is 127:
 * <pre>
 *    percent2abs(50, 0, 255) == 127
 * </pre>
 *
 * @param percent The percentage value (0 to 100).
 * @param min     The minimum value of the range.
 * @param max     The maximum value of the range.
 * @return The absolute value corresponding to the percentage within the range.
 */
    public static int percent2abs( int percent, int min, int max )
    {
        percent = Math.max( 0, Math.min( 100, percent ) );

        return min + (percent * (max - min)) / 100;
    }

    /**
     * Converts a percentage to an absolute value within a specified range.
     *
     * For example, 50% of a range between 0 and 255 is 127:
     * <pre>
     *    percent2abs(50, 0, 255) == 127
     * </pre>
     *
     * @param percent The percentage value (0 to 100).
     * @param min     The minimum value of the range.
     * @param max     The maximum value of the range.
     * @return The absolute value corresponding to the percentage within the range.
     */
    public static float percent2abs( float percent, float min, float max )
    {
        percent = Math.max( 0, Math.min( 100, percent ) );

        return min + (percent * (max - min)) / 100;
    }

    /**
     * Converts an absolute value within a specified range to a percentage.
     *
     * For example, 127 in a range between 0 and 255 is 50%:
     * <pre>
     *    abs2percent(127, 0, 255) == 50
     * </pre>
     *
     * @param abs The absolute value within the range [min, max].
     * @param min The minimum value of the range.
     * @param max The maximum value of the range.
     * @return The percentage corresponding to the absolute value within the range.
     */
    public static int abs2percent(int abs, int min, int max)
    {
        if( min >= max )
            throw new IllegalArgumentException( "min must be less than max" );

        abs = Math.max( min, Math.min( max, abs ) );

        return ((abs - min) * 100) / (max - min);
    }

    /**
     * Converts an absolute value within a specified range to a percentage.
     *
     * For example, 127 in a range between 0 and 255 is 50%:
     * <pre>
     *    abs2percent(127, 0, 255) == 50
     * </pre>
     *
     * @param abs The absolute value within the range [min, max].
     * @param min The minimum value of the range.
     * @param max The maximum value of the range.
     * @return The percentage corresponding to the absolute value within the range.
     */
    public static float abs2percent( float abs, float min, float max )
    {
        if( min >= max )
            throw new IllegalArgumentException( "min must be less than max" );

        abs = Math.max( min, Math.min( max, abs ) );

        return ((abs - min) * 100) / (max - min);
    }

    /**
     * Returns the number of milliseconds that corresponds to received argument.<br>
     * <ul>
     *  <li>If String, it can comes with or without unit suffix.</li>
     *  <li>If number, can be integer or decimal.</li>
     * </ul>
     *
     * @param o
     * @return The number of milliseconds that corresponds to received argument.
     */
    public static long toMillis( Object o )
    {
        if( (o instanceof String) && (((String) o).length() > 1) )    // length < 2 --> Does not have a suffix: it is a number
        {
            String s = (String) o;
            char   c = s.charAt( s.length() -1 );

            if( Language.isTimeSuffix( c ) )
            {
                float time = UtilType.toFloat( s.substring( 0, s.length() -1 ) );
                return Language.toMillis( time, c );
            }
        }

        return UtilType.toLong( o );
    }
}