
package com.peyrona.mingle.lang.xpreval.functions;

import com.eclipsesource.json.Json;
import com.eclipsesource.json.JsonObject;
import com.peyrona.mingle.lang.MingleException;
import com.peyrona.mingle.lang.japi.UtilColls;
import com.peyrona.mingle.lang.japi.UtilType;
import java.time.DateTimeException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.util.Objects;

/**
 * Represents a local date (year, month, day) without a time-zone or time-of-day
 * in the ISO-8601 calendar system, such as {@code 2007-12-03}.
 * <p>
 * This class wraps {@link java.time.LocalDate} to provide a convenient API
 * suitable for use within the Mingle expression evaluation system. While the
 * underlying {@code LocalDate} is immutable, this wrapper allows modification
 * via setter methods which return {@code this} for method chaining.
 * <p>
 * Supported date formats:
 * <ul>
 *   <li>ISO-8601 string: {@code "2022-06-19"}</li>
 *   <li>Numeric components: {@code 2022, 6, 19}</li>
 *   <li>JSON serialization: {@code {"class":"...","data":"2022-06-19"}}</li>
 *   <li>Default: Today's date</li>
 * </ul>
 *
 * @author Francisco José Morero Peyrona
 * @see java.time.LocalDate
 * @see <a href="https://github.com/peyrona/mingle">https://github.com/peyrona/mingle</a>
 */
public final class date
             extends ExtraType
             implements Operable<date>
{
    private LocalDate date;

    //------------------------------------------------------------------------//

    /**
     * Checks if the given string is a valid ISO-8601 date (YYYY-MM-DD).
     * <p>
     * This method attempts to parse the string as a local date. Leading and
     * trailing whitespace is ignored.
     *
     * @param sDate The string to validate (may be {@code null}).
     * @return {@code true} if the string represents a valid date in ISO-8601 format,
     *         {@code false} if {@code null}, empty, or invalid.
     */
    public static boolean isValid( String sDate )
    {
        if( sDate == null )
            return false;

        try
        {
            LocalDate.parse( sDate.trim() );
            return true;
        }
        catch( DateTimeParseException e )
        {
            return false;
        }
    }

    //------------------------------------------------------------------------//

    /**
     * Constructs a new {@code date} instance.
     * <p>
     * Accepts various argument combinations to initialize the date:
     * <ul>
     *   <li><b>Empty:</b> {@code new date()} initializes to today's date.</li>
     *   <li><b>String:</b> {@code new date("2022-06-19")} parses an ISO-8601 date string.</li>
     *   <li><b>JSON:</b> {@code new date("{\"data\":\"2022-06-19\"}")} deserializes from JSON.</li>
     *   <li><b>Numbers:</b> {@code new date(2022, 6, 19)} uses year, month, and day.</li>
     * </ul>
     * <p>
     * Valid ranges for numeric components:
     * <ul>
     *   <li>Year: Typically 0-9999 (platform dependent)</li>
     *   <li>Month: 1-12</li>
     *   <li>Day: 1-31 (must be valid for the specific month/year)</li>
     * </ul>
     *
     * @param args Optional arguments. Valid formats are:
     *            <ul>
     *              <li>(empty) - Today's date</li>
     *              <li>(String) - ISO-8601 date string or JSON</li>
     *              <li>(Number, Number, Number) - year, month, day</li>
     *            </ul>
     * @throws MingleException If arguments are invalid or the resulting date is out of range.
     */
    public date( Object... args )
    {
        if( UtilColls.isEmpty( args ) )
        {
            this.date = LocalDate.now();
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
                        catch( Exception e ) { /* Not valid JSON from serialize(), try date string */ }
                    }

                    // Parse as ISO date string, e.g.: "2022-06-19"
                    this.date = LocalDate.parse( s );
                }
                else   // e.g.: 2022,06,19
                {
                    if( args.length != 3 )
                        throw new MingleException( MingleException.INVALID_ARGUMENTS );

                    int year  = UtilType.toInteger( args[0] );
                    int month = UtilType.toInteger( args[1] );
                    int day   = UtilType.toInteger( args[2] );

                    this.date = LocalDate.of( year, month, day );
                }
            }
            catch( DateTimeException | MingleException exc )
            {
                throw new MingleException( "Invalid date" );
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
     * Gets the day-of-month field.
     *
     * @return The day-of-month, from 1 to 31.
     */
    public int day()
    {
        return date.getDayOfMonth();
    }

    /**
     * Gets the month-of-year field.
     *
     * @return The month-of-year, from 1 to 12.
     */
    public int month()
    {
        return date.getMonthValue();
    }

    /**
     * Gets the year field.
     *
     * @return The year.
     */
    public int year()
    {
        return date.getYear();
    }

    /**
     * Sets the day-of-month to the value specified.
     * <p>
     * The day must be valid for the year and month, otherwise an exception is thrown.
     *
     * @param nDay The new day-of-month value (1-31).
     * @return {@code this} for method chaining.
     * @throws MingleException If the day value is invalid.
     */
    public date day( Object nDay )
    {
        try
        {
            date = date.withDayOfMonth( UtilType.toInteger( nDay ) );
        }
        catch( DateTimeException exc )
        {
            throw new MingleException( "Invalid day" );
        }

        return this;
    }

    /**
     * Sets the month-of-year to the value specified.
     * <p>
     * The month must be between 1 and 12. The day-of-month will be adjusted
     * if necessary to ensure the result is a valid date (e.g., setting month
     * to February on a date of Jan 31st results in Feb 28th or 29th).
     *
     * @param nMonth The new month-of-year value (1-12).
     * @return {@code this} for method chaining.
     * @throws MingleException If the month value is invalid.
     */
    public date month( Object nMonth )
    {
        try
        {
            date = date.withMonth( UtilType.toInteger( nMonth ) );
        }
        catch( DateTimeException exc )
        {
            throw new MingleException( "Invalid month" );
        }

        return this;
    }

    /**
     * Sets the year to the value specified.
     *
     * @param nYear The new year value.
     * @return {@code this} for method chaining.
     * @throws MingleException If the year value is invalid.
     */
    public date year( Object nYear )
    {
        try
        {
            date = date.withYear( UtilType.toInteger( nYear ) );
        }
        catch( DateTimeException exc )
        {
            throw new MingleException( "Invalid year" );
        }

        return this;
    }

    //------------------------------------------------------------------------//

    /**
     * Checks if the year is a leap year, according to the ISO proleptic calendar
     * system rules.
     * <p>
     * A leap year occurs if:
     * <ul>
     *   <li>The year is divisible by 4, AND</li>
     *   <li>Not divisible by 100, unless also divisible by 400.</li>
     * </ul>
     *
     * @return {@code true} if the year is a leap year, {@code false} otherwise.
     */
    public boolean isLeap()
    {
        return date.isLeapYear();
    }

    /**
     * Gets the day-of-week field as an integer.
     * <p>
     * This method follows the ISO-8601 standard, where the week runs from Monday
     * to Sunday.
     *
     * @return The day-of-week, from 1 (Monday) to 7 (Sunday).
     */
    public int weekday()    // Named after Excel
    {
        return date.getDayOfWeek().getValue();
    }

    /**
     * Moves this date forward or backward by the specified number of days.
     * <p>
     * Equivalent to adding or subtracting days to the current date. A negative
     * value moves the date backwards.
     *
     * @param nDays The number of days to move (positive to move forward,
     *             negative to move backward).
     * @return {@code this} for method chaining.
     */
    @Override
    public date move( Object nDays )
    {
        date = date.plusDays( UtilType.toLong( nDays ) );
        return this;
    }

    /**
     * Calculates the amount of time between this date and the specified date.
     * <p>
     * The result is calculated as {@code date - this date}.
     * Returns a positive number if the argument date is in the future, and
     * a negative number if it is in the past.
     *
     * @param another The other date to compare to (must not be {@code null}).
     * @return The difference in days.
     */
    @Override
    public int duration( date another )
    {
        return (int) ChronoUnit.DAYS.between( this.date, another.date );
    }

    //------------------------------------------------------------------------//
    // OVERRIDEN

    /**
     * Compares this date to another date.
     * <p>
     * Returns:
     * <ul>
     *   <li>{@code 1}: if this date is ahead in time of the passed date.</li>
     *   <li>{@code 0}: if this date is the same as the one passed.</li>
     *   <li>{@code -1}: if this date is before in time of the passed date.</li>
     * </ul>
     *
     * @param anotherDate Date to compare (argument must be of type {@code date}).
     * @return {@code 1}, {@code 0}, or {@code -1}.
     * @throws MingleException If the argument is not of type {@code date}.
     */
    @Override
    public int compareTo( Object anotherDate )
    {
        if( anotherDate instanceof date )
            return date.compareTo( ((date)anotherDate).date );

        throw new MingleException( "Expecting 'date', but '"+ anotherDate.getClass().getSimpleName() +"' found." );
    }

    /**
     * Returns this date as a string in ISO-8601 format: "YYYY-MM-DD".
     *
     * @return The date as an ISO-8601 date string.
     */
    @Override
    public String toString()
    {
        return date.format( DateTimeFormatter.ISO_DATE );
    }

    /**
     * Returns a hash code value for this date.
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
            hash = 29 * hash + Objects.hashCode( this.date );

        return hash;
    }

    /**
     * Checks if this date is equal to another object.
     * <p>
     * The comparison is based on the underlying {@link LocalDate} value.
     *
     * @param anyDataType The object to compare with (may be {@code null}).
     * @return {@code true} if the other object represents the same date,
     *         {@code false} otherwise.
     */
    @Override
    public boolean equals( Object anyDataType )
    {
        if( this == anyDataType )
            return true;

        if( anyDataType == null )
            return false;

        if( getClass() != anyDataType.getClass() )
            return false;

        final date other = (date) anyDataType;

        return Objects.equals( this.date, other.date );
    }

    //------------------------------------------------------------------------//
    // TO BE USED FROM SCRIPTS

    /**
     * Serializes this date to a JSON object.
     * <p>
     * The JSON structure is:
     * <pre>
     * {
     *   "class": "com.peyrona.mingle.lang.xpreval.functions.date",
     *   "data": "2022-06-19"
     * }
     * </pre>
     *
     * @return A JSON object containing the class name and date string.
     */
    @Override
    public JsonObject serialize()
    {
        return build( toString() );
    }

    /**
     * Deserializes a JSON object to populate this date instance.
     * <p>
     * Expects a JSON object in the format produced by {@link #serialize()}.
     *
     * @param sJson The JSON object containing the date string.
     * @return {@code this} for method chaining.
     * @throws MingleException If the JSON structure is invalid or the date cannot be parsed.
     */
    @Override
    public date deserialize( JsonObject json )
    {
        String sDate = json.getString( "data", null );     // At this point it is never null

        try
        {
            date = LocalDate.parse( sDate );
        }
        catch( DateTimeParseException exc )
        {
            throw new MingleException( "Invalid date: " + sDate );
        }

        return this;
    }

    /**
     * Returns the underlying {@link LocalDate} instance.
     * <p>
     * This method provides access to the standard Java time API date object.
     *
     * @return The underlying {@code LocalDate}.
     */
    public LocalDate asLocalDate()
    {
        return date;
    }

    /**
     * Combines this date with a time to create a {@link LocalDateTime}.
     *
     * @param time The time to combine with this date (must not be {@code null}).
     * @return A {@code LocalDateTime} formed from this date and the specified time.
     * @throws NullPointerException If the time is {@code null}.
     */
    public LocalDateTime asLocalDateTime( LocalTime time )
    {
        return LocalDateTime.of( date, time );
    }
}