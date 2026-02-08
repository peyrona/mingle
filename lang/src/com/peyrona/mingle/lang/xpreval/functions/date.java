
package com.peyrona.mingle.lang.xpreval.functions;

import com.peyrona.mingle.lang.MingleException;
import com.peyrona.mingle.lang.japi.UtilColls;
import com.peyrona.mingle.lang.japi.UtilJson;
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
 * Creates a new instance of this class, which represents a local date.
 *
 * @author Francisco José Morero Peyrona
 *
 * Official web site at: <a href="https://github.com/peyrona/mingle">https://github.com/peyrona/mingle</a>
 */
public final class date
             extends ExtraType
             implements Operable<date>
{
    private LocalDate date;

    //------------------------------------------------------------------------//

    /**
     * Checks if the given string is a valid ISO date (YYYY-MM-DD).
     *
     * @param sDate The string to check.
     * @return {@code true} if the string represents a valid date, {@code false} otherwise.
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
     * Class constructor.<br>
     * A date can be created in the following ways:
     * <ul>
     *     <li>String: <code>date( "2022-06-19" )</code></li>
     *     <li>JSON from <code>serialize().toString()</code>: <code>date( "{\"class\":\"...\", \"data\":\"2022-06-19\"}" )</code></li>
     *     <li>Numbers: <code>date( 2022, 6, 19 )</code></li>
     *     <li>Empty: <code>date()</code> returns today's date</li>
     * </ul>
     * Any other combination of values will produce an error.
     *
     * @param args Optional arguments to initialize the date.
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
                            deserialize( s );
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

    /**
     * Returns the day of the month, from 1 to 31.
     *
     * @return The day of the month, from 1 to 31.
     */
    public int day()
    {
        return date.getDayOfMonth();
    }

    /**
     * Returns the month, from 1 to 12.
     *
     * @return The month, from 1 to 12.
     */
    public int month()
    {
        return date.getMonthValue();
    }

    /**
     * Returns the value for the year.
     *
     * @return The value for the year.
     */
    public int year()
    {
        return date.getYear();
    }

    /**
     * <p>
     * Changes current date day of month.
     *
     * @param n New day value (1-31).
     *
     * @return Itself.
     *
     */
    public date day( Object n )
    {
        try
        {
            date = date.withDayOfMonth( UtilType.toInteger( n ) );
        }
        catch( DateTimeException exc )
        {
            throw new MingleException( "Invalid day" );
        }

        return this;
    }

    /**

     * Changes current date month.

     * @param n New month value (1-12).

     * @return Itself.

     */
    public date month( Object n )
    {
        try
        {
            date = date.withMonth( UtilType.toInteger( n ) );
        }
        catch( DateTimeException exc )
        {
            throw new MingleException( "Invalid month" );
        }

        return this;
    }

    /**

     * Changes current date year.

     * @param n New year value.

     * @return Itself.

     */
    public date year( Object n )
    {
        try
        {
            date = date.withYear( UtilType.toInteger( n ) );
        }
        catch( DateTimeException exc )
        {
            throw new MingleException( "Invalid year" );
        }

        return this;
    }

    /**

     * Checks if the year is a leap year, according to the ISO proleptic calendar system rules.

     *

     * @return {@code true} if the year contained in this date is leap, {@code false} otherwise.

     */
    public boolean isLeap()
    {
        return date.isLeapYear();
    }

    /**

     * Returns the numeric value for the day of the week.<br>

     * The int value follows the ISO-8601 standard, from 1 (Monday) to 7 (Sunday).

     *

     * @return The numeric value for the day of the week.

     */
    public int weekday()    // Named after Excel
    {
        return date.getDayOfWeek().getValue();
    }

    /**

     * Add or subtract days to current date.

     *

     * @param days Number of days to add or subtract.

     * @return Itself.

     */
    @Override
    public date move( Object days )
    {
        date = date.plusDays( UtilType.toLong( days ) );
        return this;
    }

    /**

     * Returns the amount of days between this date and passed date.

     *

     * @param d The other date to calculate the difference in days.

     * @return The amount of days between this date and passed date.

     */

    @Override
    public int duration( date d )
    {
        return (int) ChronoUnit.DAYS.between( this.date, d.date );
    }

    //------------------------------------------------------------------------//
    // OVERRIDEN

    /**

     * Compares this date to another date.

     * <p>

     * Returns:

     * <ul>

     *    <li> 1 : when this date is ahead in time of passed date.</li>

     *    <li> 0 : when this date is the same as the one passed.</li>

     *    <li>-1 : when this date is before in time of passed date.</li>

     * </ul>

     * @param o Date to compare (argument must be of type date).

     * @return 1, 0 or -1

     */

    @Override
    public int compareTo( Object o )
    {
        if( o instanceof date )
            return date.compareTo( ((date)o).date );

        throw new MingleException( "Expecting 'date', but '"+ o.getClass().getSimpleName() +"' found." );
    }

    /**

     * Returns the date in ISO format: "YYYY-MM-DD" (e.g., "2022-06-19").

     *

     * @return The date as an ISO date string.

     */
    @Override
    public String toString()
    {
        return date.format( DateTimeFormatter.ISO_DATE );
    }

    @Override
    public int hashCode()
    {
        int hash = 7;
            hash = 29 * hash + Objects.hashCode( this.date );

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

        final date other = (date) obj;

        return Objects.equals( this.date, other.date );
    }

    //------------------------------------------------------------------------//
    // TO BE USED FROM SCRIPTS

    @Override
    public Object serialize()
    {
        return build( toString() );
    }

    @Override
    public date deserialize( Object o )
    {
        UtilJson json  = parse( o );
        String   sDate = json.getString( "data", null );     // At this point it is never null

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

    public LocalDate asLocalDate()
    {
        return date;
    }

    public LocalDateTime asLocalDateTime( LocalTime time )
    {
        return LocalDateTime.of( date, time );
    }
}