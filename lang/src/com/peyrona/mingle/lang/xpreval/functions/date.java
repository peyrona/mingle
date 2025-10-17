
package com.peyrona.mingle.lang.xpreval.functions;

import com.peyrona.mingle.lang.MingleException;
import com.peyrona.mingle.lang.japi.UtilColls;
import com.peyrona.mingle.lang.japi.UtilJson;
import com.peyrona.mingle.lang.japi.UtilReflect;
import com.peyrona.mingle.lang.japi.UtilType;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.util.Objects;

/**
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

    public static boolean isValid( String sDate )
    {
        if( sDate == null )
            return false;

        sDate = sDate.trim();

        return sDate.length() == 10 &&
               areDigits( sDate.substring( 0, 4 ) + sDate.substring( 5, 7 ) + sDate.substring( 8, 10 ) );
    }

    //------------------------------------------------------------------------//

    /**
     * Class constructor.<br>
     * A date can be created in the following ways:
     * <ul>
     *     <li>String: date( "2022-06-19" )</li>
     *     <li>Numbers: date( 2022,06,19 )</li>
     *     <li>Empty: date() --> today's date</li>
     * </ul>
     * Any other combination of values will produce an error.
     *
     * @param args One or more objects.
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
                if( (args.length == 1) && (args[0] instanceof String) )       // e.g.: "2022-06-19"
                {
                    this.date = LocalDate.parse( args[0].toString().trim() );
                }
                else                                                          // e.g.: 2022,06,19
                {
                    if( args.length != 3 )
                        throw new MingleException( MingleException.INVALID_ARGUMENTS );

                    int year  = UtilType.toInteger( args[0] );
                    int month = UtilType.toInteger( args[1] );
                    int day   = UtilType.toInteger( args[2] );

                    if( ! UtilReflect.areAll( Number.class, year, month, day ) )
                        throw new MingleException( MingleException.SHOULD_NOT_HAPPEN );

                    this.date = LocalDate.of( year, month, day );
                }
            }
            catch( DateTimeParseException | MingleException exc )
            {
                throw new MingleException( "Invalid date value" );
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
     * Changes current date day of month.
     * @param n New day.
     * @return Itself.
     */
    public date day( Object n )
    {
        date = LocalDate.of( date.getYear(),
                             date.getMonthValue(),
                             UtilType.toInteger( n ) );

        return this;
    }

    /**
     * Changes current date month.
     * @param n New month.
     * @return Itself.
     */
    public date month( Object n )
    {
        date = LocalDate.of( date.getYear(),
                             UtilType.toInteger( n ),
                             date.getDayOfMonth() );

        return this;
    }

    /**
     * Changes current date year.
     * @param n New year.
     * @return Itself.
     */
    public date year( Object n )
    {
        date = LocalDate.of( UtilType.toInteger( n ),
                             date.getMonthValue(),
                             date.getDayOfMonth() );

        return this;
    }

    /**
     * Checks if the year is a leap year, according to the ISO proleptic calendar system rules.
     *
     * @return true if the year contained in this date is leap.
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
     * Add or substract days to current date.
     *
     * @param days Days to add or substract.
     * @return Itself.
     */
    @Override
    public date move( Object days )
    {
        int n = UtilType.toInteger( days );

        if( n != 0 )
        {
            date = (n > 0) ? date.plusDays( n )
                           : date.minusDays( n * -1 );
        }

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
     *    <li> 1 : when this date is bigger (ahead in time) than passed date.</li>
     *    <li> 0 : when this date is the same as the one passed date.</li>
     *    <li>-1 : when this date is lower (before in time) than passed date.</li>
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
     *
     * @return
     */
    @Override
    public String toString()
    {
        return date.format( DateTimeFormatter.ISO_DATE );
    }

    /**
     *
     * @return
     */
    @Override
    public int hashCode()
    {
        int hash = 7;
            hash = 29 * hash + Objects.hashCode( this.date );
        return hash;
    }

    /**
     *
     * @param obj
     * @return
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

        if( ! isValid( sDate ) )
            throw new MingleException( "Invalid date: "+ sDate );

        int year  = UtilType.toInteger( sDate.substring( 0, 4 ) );
        int month = UtilType.toInteger( sDate.substring( 5, 7 ) );
        int day   = UtilType.toInteger( sDate.substring( 8 ) );

        date = LocalDate.of( year, month, day );

        return this;
    }

    public LocalDate asLocalDate()
    {
        return LocalDate.of( date.getYear(),
                             date.getMonth(),
                             date.getDayOfMonth() );    // Defensive copy
    }

    public LocalDateTime asLocalDateTime( LocalTime time )
    {
        return LocalDateTime.of( date, time );
    }

    //------------------------------------------------------------------------//
    // PRIVATE SCOPE

    private static boolean areDigits( String s )
    {
        for( int n = 0; n < s.length(); n++ )
            if( s.charAt( n ) < '0' || s.charAt( n ) > '9' )
                return false;

        return true;
    }
}