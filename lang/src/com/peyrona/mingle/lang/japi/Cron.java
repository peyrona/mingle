
package com.peyrona.mingle.lang.japi;

import com.peyrona.mingle.lang.MingleException;
import com.peyrona.mingle.lang.xpreval.functions.date;
import com.peyrona.mingle.lang.xpreval.functions.pair;
import com.peyrona.mingle.lang.xpreval.functions.time;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

// TODO: this class needs to be finished and to have much more Unit tests
//       (look for other TODO's inside this class)

/**
 * A Linux-like Cron extended to be Mingle-like.
 *
 * @author Francisco José Morero Peyrona
 *
 * Official web site at: <a href="https://github.com/peyrona/mingle">https://github.com/peyrona/mingle</a>
 */
public final class Cron
{
    private LocalDateTime ldtStart = null;    // When to start or null
    private LocalDateTime ldtStop  = null;    // When to finish or null
    private LocalDateTime ldtLast  = null;    // Last event returned by ::next()
    private LocalTime[]   aLtTime  = null;    // Array of times (hh:mm:ss)
    private short[]       aDoW     = null;    // Day of Week : 1 -  7  (0 == Sunday too)
    private short[]       aDoM     = null;    // Day of month: 1 - 31
    private short[]       aMonth   = null;    // Month: 1 - 12
    private int           every    = 0;       // Betwen 2 invocations. In seconds.
    private char          mode     = 0;       // d, w, m, y

    //------------------------------------------------------------------------//

    /**
     * Class constructor.
     *
     * @param args An Une 'pair' with following key, values pairs. All are optional
     *             but all together must be congruent.
     * <ul>
     *   <li>start AS string  -->  As Une "_date_,_time_". When only 'start' is defined, it occurs only once.</li>
     *   <li>stop  AS string  -->  As Une "_date_,_time_". Moment of last repetition, or empty for repeating for ever.</li>
     *   <li>time  AS string  -->  Time of the day to execute: one or more comma separated Une times (hh[:mm[:ss]]).</li>
     *   <li>dow   AS string  -->  DayOfweek: one or more comma separated of: 1 to 7 (Monday to Sunday; 0 is Sunday too).</li>
     *   <li>dom   AS number  -->  DayOfMonth: one or more comma separated of: 1 to 31 (31 is used also as 'last-day').</li>
     *   <li>month AS number  -->  Month: one or more comma separated of: 1 to 12.</li>
     *   <li>every AS number  -->  Repetition interval ("repeat every"). Use 0 or omit it to not repeat.</li>
     *   <li>mode  AS string  -->  Repetition mode: "Daily, "Weekly, "Monthly" or "Yearly" (only the first letter is used).</li>
     * </ul>
     * <br>
     * i.e.: time="12:10, 18", dow="2,5", every=2 mode="M": repeat every Tuesday and Friday on
     * alternate months at 12:10:00 and 18:00:00 (minutes and seconds are optional).
     */
    public Cron( pair args )
    {
        List<String> lstErrs = new ArrayList<>();

        if( args.keys().has( "start" ) )
        {
            ldtStart = getDateTime( args, "start", lstErrs );
        }

        if( args.keys().has( "stop" ) )
        {
            ldtStop = getDateTime( args, "stop", lstErrs );
        }

        if( args.keys().has( "time" ) )
        {
            String[] as = args.get( "time" ).toString().split( "," );
            aLtTime = new LocalTime[as.length];

            try
            {
                for( int n = 0; n < as.length; n++ )
                    aLtTime[n] = new time( as[n] ).asLocalTime();

                Arrays.sort( aLtTime );
            }
            catch( MingleException me )
            {
                lstErrs.add( "Invalid 'time' value" );
            }
        }

        if( args.keys().has( "dow" ) )
        {
            aDoW = getShorts( args, "dow", 0, 7, lstErrs );

            if( aDoW != null )                                  // 0 is accepted as Sunday, but has to be transformed into 7 (ISO for Sunday)
                for( int n = 0; n < aDoW.length; n++ )
                    aDoW[n] = (short) (aDoW[n] == 0 ? 7 : aDoW[n]);
        }

        if( args.keys().has( "dom" ) )
        {
            aDoM = getShorts( args, "dom", 1, 31, lstErrs );
        }

        if( args.keys().has( "month" ) )
        {
            aMonth = getShorts( args, "month", 1, 12, lstErrs );
        }

        if( args.keys().has( "mode" ) )
        {
            String sMode = args.get( "mode" ).toString().toLowerCase();

            if( sMode.length() > 1 )
                sMode = sMode.substring( 0, 1 );

            if( "dwmy".indexOf( sMode.charAt(0) ) == -1 )
            {
                lstErrs.add( "Invalid 'mode' value" );
            }
            else
            {
                mode = sMode.charAt(0);
            }
        }

        if( mode > 0 )
        {
            every = UtilType.toInteger( args.get( "every", 1 ) );
        }

        if( (every == 0) && (ldtStart == null) )     //  This is a one time event but there is no date-time defined.
        {
            lstErrs.add( "There is no repetition but 'start' is not defined" );
        }

        if( (every > 0) && (aLtTime == null) )
        {
            lstErrs.add( "Can not execute: 'time' is not specified or is invalid" );
        }

        switch( mode )
        {
            case 'd':
                if( aDoW != null || aDoM != null )
                    lstErrs.add( "Invalid config: when mode is 'Daily', 'dow' neither 'dom' can be specified" );
                break;

            case 'w':
                if( aDoW == null )
                    lstErrs.add( "Invalid config: mode is 'Weekly', but 'dow' is not specified" );
                if( aDoM != null )
                    lstErrs.add( "Invalid config: when mode is 'Weekly', 'dom' can not be specified" );
                break;

            case 'm':
                if( aDoM == null )
                    lstErrs.add( "Invalid config: mode is 'Monthly', but 'dom' is not specified" );
                if( aDoW != null )
                    lstErrs.add( "Invalid config: when mode is 'Monthly', only 'dom' can be specified" );
                if( every > 1 )
                    lstErrs.add( "Invalid config: when mode is 'Monthly', 'every' has to be 1" );
                break;

            case 'y':
                if( (aDoM == null) || (aMonth == null) )
                    lstErrs.add( "Invalid config: when mode is 'Yearly', both 'dom' and 'month' must be specified" );
                break;
        }

        if( ! lstErrs.isEmpty() )
            throw new MingleException( UtilColls.toString( lstErrs, UtilColls.cRECORD_SEP ) );
    }

    //------------------------------------------------------------------------//

    /**
     * Returns the amount of milliseconds since method invocation to the next
     * moment when Cron should be triggered, or -1 if there is no next time.
     *
     * @return The amount of milliseconds until the next trigger.
     */
    public long next()
    {
        LocalDateTime now = LocalDateTime.now();

        if( (ldtStop != null) && now.isAfter( ldtStop ) )    // There is an ending time point and it is in the past
            return -1;

        LocalDateTime next;

        if( every == 0 )                     // This is a one time event but...
        {
            if( now.isAfter( ldtStart ) )    // ...it is in the past...
                return -1;                   // ...so forget about it.

            next = ldtStart;                 // Otherwise, this is the time that has to be elapsed
        }
        else                                 // Lets find next occurrence using all other fields
        {
            next = (ldtStart == null) ? now : ldtStart;

            switch( mode )
            {
                case 'd': next = getNextDaily(   next ); break;
                case 'w': next = getNextWeekly(  next ); break;
                case 'm': next = getNextMonthly( next ); break;
                case 'y': next = getNextYearly(  next ); break;
            }
        }

        if( next == null )
            return -1;

        ldtLast = next;

        ZoneOffset zo = ZoneId.systemDefault().getRules().getOffset( now );    // 'next' and 'now' have same Zone
        Instant    i1 = now.toInstant(  zo );
        Instant    i2 = next.toInstant( zo );

        return Duration.between( i1, i2 ).toMillis();
    }

    //------------------------------------------------------------------------//
    // USED BY THIS CLASS CONSTRUCTOR

    private LocalDateTime getDateTime( pair pairs, String which, List<String> lstErrs )
    {
        String[] val = pairs.get( which ).toString().split( "," );

        if( val.length != 2 )
        {
            lstErrs.add( "Invalid '"+ which +"' value" );
            return null;
        }

        try
        {
            date date = new date( val[0] );
            time time = new time( val[1] );

            return LocalDateTime.of( date.asLocalDate(), time.asLocalTime() );
        }
        catch( MingleException me )
        {
            lstErrs.add( "Invalid 'start' value" );
            return null;
        }
    }

    private short[] getShorts( pair pairs, String which, int min, int max, List<String> lstErr )
    {
        String[] as  = pairs.get( which ).toString().split( "," );
        short[]  ret = new short[as.length];

        try
        {
            for( int n = 0; n < as.length; n++ )
            {
                short val = Short.parseShort( as[n].trim() );

                if( val < min || val > max )
                    throw new MingleException();

                ret[n] = val;
            }

            if( ret.length == 0 )  ret = null;
            else                   Arrays.sort( ret );

            return ret;
        }
        catch( NumberFormatException | MingleException me )
        {
            lstErr.add( "Invalid '"+ which +"' value" );
            return null;
        }
    }

    //------------------------------------------------------------------------//
    // USED BY ::next()

    private boolean has( short[] an, int x )    // 'an' is sorted
    {
        if( an == null )
            return false;

        for( int n = 0; n < an.length; n++ )
        {
                 if( an[n] == x )  return true;
            else if( an[n] >  x )  break;
        }

        return false;
    }

    private LocalDateTime getNextDaily( LocalDateTime when )
    {
        // Constructor checks that ::aLtWhen is not empty when ::every > 0

        LocalTime time = getNextTime( when.toLocalTime() );    // Next iteration time (if any)
        int       days = 0;

        if( time == null )      // If loop was exhausted, it means that there is no time defined after 'time' for
        {                       // today: so, we will take the 1st time (aLtWhen[0]) for the next ('every') day.
            time = aLtTime[0];
            days = every;
        }

        return updateTime( when, time )
               .plusDays( days );       // 0 is checked in plusDays(...)
    }

    private LocalDateTime getNextWeekly( LocalDateTime when )
    {
        // (aDoW != null) --> is cheked at Constructor

        LocalTime time = null;

        if( has( aDoW, when.getDayOfWeek().getValue() ) )    // Received date is one of the days of week.
            time = getNextTime( when.toLocalTime() );        // Next iteration time (if any)

        if( time == null )
        {                                                    // If it is null, it is because there is no time designated for the rest
            when = getNextDoW( when.plusDays( 1 ) );         // of today: we have to check for next day of week considering the 'every'.

            if( isLikeLast( when, 'w' ) )
                when = getNextDoW( when.plusDays( 1 ) );

            time = aLtTime[0];                               // This LoC is equivalent (but faster) to -> getNextTime( LocalTime.of( 0,0,0 ) );
        }                                                    // as the day is not today but in the future: first time of the day can be used.

        return updateTime( when, time );
    }

    private LocalDateTime getNextMonthly( LocalDateTime when )
    {
        // (aDoM != null) --> is cheked at Constructor
        // every is 1

        LocalTime time = null;

        if( has( aDoM, when.getDayOfMonth() ) ||             // Received date is one of the days of month or
            has( aDoW, when.getDayOfWeek().getValue() ) )    // is one of the days of the week.
        {
            time = getNextTime( LocalTime.now() );           // Next iteration time for today (if any)
        }

        if( time == null )
        {                                                    // If it is still null it is because there is no time designated for the rest of today.
            when = when.plusDays( 1 );
            when = getNextDoM( when );                       // empty: this is checked at Constructor

            if( isLikeLast( when, 'm' ) )
                when = getNextDoW( when.plusMonths( 1 ) );

            time = aLtTime[0];                               // As the day is not today but in the future: first time of the day can be used.
        }

        return updateTime( when, time );
    }

    private LocalDateTime getNextYearly( LocalDateTime when )
    {
        // TODO: hay que terminar este método


        // (aDoM != null) || (aMonth != null) --> is cheked at Constructor

        LocalTime time = getNextTime( LocalTime.now() );     // Next iteration time for the remain of the day (if any)

        if( time != null                        &&           // There is at least one valid time left
            has( aDoM  , when.getDayOfMonth() ) &&           // and when DoM is valid
            has( aMonth, when.getMonth().getValue() ) )      // and the month is also valid
        {
            return updateTime( when, time );
        }

        if( time == null )
            time = aLtTime[0];

        when = getNextDoM( getNextMonth( when ) )
               .plusYears( every - 1 );

        return updateTime( when, time );                     // This LoC is equivalent (but faster) to -> getNextTime( LocalTime.of( 0,0,0 ) );
    }                                                        // as the day is not today but in the future: first time of the day can be used.

    //------------------------------------------------------------------------//
    // Aux funcs to the aux funcs used by ::next()

    /**
     * Returns the next time defined in ::aLtWhen (if any), after the received 'from' time.
     *
     * @param from
     * @return the next time defined in ::aLtWhen (if any), after the received 'from' time.
     */
    private LocalTime getNextTime( LocalTime from )
    {
        int n;

        for( n = 0; n < aLtTime.length; n++ )    // Array is sorted
        {
            if( aLtTime[n].isAfter( from ) )
                if( (ldtLast == null) || aLtTime[n].isAfter( ldtLast.toLocalTime() ) )    // For calirty
                    return aLtTime[n];
        }

        return null;    // There is no time defined after 'from' for today
    }

    private LocalDateTime getNextDoW( LocalDateTime when )
    {
        if( aDoW == null )
            return when;

        int dow = when.getDayOfWeek().getValue();

        if( has( aDoW, dow ) )                               // If 'dow' is in ::aDoW, then
            return when;                                     // the received date is a valid date

        if( dow > aDoW[ aDoW.length-1 ] )                    // If 'dow' is after last item in ::aDoW (aDow == [1,3,5] and 'dow' == 6)
        {
            int days2Add = 7 - dow + aDoW[0];                // Days to add to 'when' to set it to ::aDoW[0]

            when = when.plusDays( days2Add );

            if( mode == 'w' )
                when = when.plusWeeks( every - 1 );          // Only add 'every' if it was needed to jum more than one week
        }
        else                                                 // There is at least one day in this week that can be used ::aDoW (aDow == [1,3,7] and 'dow' == 4)
        {
            do
            {
                when = when.plusDays( 1 );
            }
            while( ! has( aDoW, when.getDayOfWeek().getValue() ) );
        }

        return when;
    }

    private LocalDateTime getNextDoM( LocalDateTime when )
    {
        if( aDoM == null )
            return when;

        int dom = when.getDayOfMonth();

        if( has( aDoM, dom ) )
            return when;

        int     daysToAdd = 0;
        int     lastDay   = when.toLocalDate().lengthOfMonth();
        boolean bUseLast  = aDoM[ aDoM.length - 1 ] == 31;    // 31 means either 31th or the last day of the month when the month has less than 31 days

        // From today to the last day of the month

        for( int n = dom + 1; n <= lastDay; n++ )
        {
            daysToAdd++;

            if( has( aDoM, n ) )
                return when.plusDays( daysToAdd );

            if( n == lastDay && bUseLast )
                return when.plusDays( daysToAdd );
        }

        // From 1st day of the month to dom -1

        daysToAdd = lastDay - dom;

        for( int n = 1; n < dom; n++ )
        {
            daysToAdd++;

            if( has( aDoM, n ) )
                return when.plusDays( daysToAdd );
        }

        throw new MingleException();    // This should not happen
    }

    private LocalDateTime getNextMonth( LocalDateTime when )
    {
        if( aMonth == null )
            return when;

        int nMonth = when.getMonthValue();
        int nDay   = when.getDayOfMonth();

        for( int month : aMonth )
        {
            if( month >= nMonth )
                return LocalDateTime.of( when.getYear(),
                                         month,
                                         Math.min( nDay, getDaysInMonth( month, when.getYear() ) ),
                                         when.getHour(),
                                         when.getMinute(),
                                         when.getSecond() );
        }

        // If no greater month is found, return the date in the next year for the earliest month in the array

        int nYear = when.getYear() + 1;

        return LocalDateTime.of( nYear,
                                 aMonth[0],
                                 Math.min( nDay, getDaysInMonth( aMonth[0], nYear ) ),
                                 when.getHour(),
                                 when.getMinute(),
                                 when.getSecond() );
    }

    // Helper method to find the number of days in a given month and year (handling leap years)
    private static int getDaysInMonth( int month, int year )
    {
        return LocalDate.of( year, month, 1 ).lengthOfMonth();
    }

    private boolean isLikeLast( LocalDateTime ldt, char mode )
    {
        if( ldtLast == null )
            return false;


        switch( mode )
        {
            case 'w': return ldtLast.toLocalDate().equals( ldt.toLocalDate() );
            case 'm': return ldtLast.getYear() == ldt.getYear() && ldtLast.getMonth() == ldt.getMonth();
        }

        throw new MingleException();
    }

    private LocalDateTime updateTime( LocalDateTime ldt, LocalTime lt )
    {
        return ldt.withHour(   lt.getHour()   )
                  .withMinute( lt.getMinute() )
                  .withSecond( lt.getSecond() );
    }

    //------------------------------------------------------------------------//
    // TESTING
    //------------------------------------------------------------------------//

    public static void main( String[] as )
    {
        LocalDateTime ldt;
        Cron cron;
        long millis1;
        long millis2;

        //---------------------------------------------------------------------------------------------- NO REPEAT

        ldt = LocalDateTime.now();
        int year = ldt.getYear();

        cron = new Cron( new pair().put( "start", (year+1)+"-01-01 , 18:00" ) );

        millis1 = cron.next() - diff( LocalDateTime.now(), LocalDateTime.of( year+1, 1, 1, 18, 0 ) );
        millis2 = cron.next();

        assert millis1 <= 1 : "NO REPEAT - El cálculo está mal. Diff = "+ millis1;
        assert millis2 - millis1 > 500 : "NO REPEAT - No se está usando la siguente iteración";

        //---------------------------------------------------------------------------------------------- DAILY

        int hour = ldt.getHour();

        cron = new Cron( new pair().put( "mode", "Daily" ).put( "time", hour+2 ) );

        millis1 = cron.next() - diff( ldt, ldt.withHour( hour+2 ).withMinute( 0 ).withSecond( 0 ) );
        millis2 = cron.next();

        assert millis1 <= 1 : "DAILY - El cálculo está mal. Diff = "+ millis1;
        assert millis2 - millis1 > 50*60*1000 : "DAILY - No se está usando la siguente iteración";

        //---------------------------------------------------------------------------------------------- WEEKLY

        ldt = LocalDateTime.now().plusDays( 1 );

        String sDow = String.valueOf( ldt.getDayOfWeek().getValue() );

        cron = new Cron( new pair().put( "mode", "Weekly" ).put( "dow", sDow ).put( "time", "15:00" ) );

        millis1 = cron.next() - diff( ldt, ldt.withHour(15).withMinute(0).withSecond(0) );
        millis2 = cron.next();

        assert millis1 <= 1 : "WEEKLY - El cálculo está mal. Diff = "+ millis1;

        assert UtilSys.toLocalDate( System.currentTimeMillis() + millis2 ).equals( UtilSys.toLocalDate( System.currentTimeMillis() + millis1 ).plusDays( 7 ) )
               : "WEEKLY - La siguente iteración está mal";

        //---------------------------------------------------------------------------------------------- MONTHLY

        cron = new Cron( new pair().put( "mode", "Monthly" ).put( "dom", "30" ).put( "time", "15:00" ) );

        millis1 = cron.next() - diff( LocalDateTime.now(), LocalDateTime.now().withDayOfMonth(30).withHour(15).withMinute(0).withSecond(0) );
        millis2 = cron.next();

        assert millis1 <= 1 : "MONTHLY - El cálculo está mal";

        assert UtilSys.toLocalDate( System.currentTimeMillis() + millis2 ).equals( UtilSys.toLocalDate( System.currentTimeMillis() + millis1 ).plusMonths( 1 ) )
               : "MONTHLY - La siguente iteración está mal";

        //----------------------------------------------------------------------------------------------
        // TODO: falta el yearly
    }

    private static long diff( LocalDateTime ldt1, LocalDateTime ldt2 )
    {
        ZoneOffset zo = ZoneId.systemDefault().getRules().getOffset( ldt1 );
        Instant    i1 = ldt1.toInstant( zo );
        Instant    i2 = ldt2.toInstant( zo );

        return Duration.between( i1, i2 ).toMillis();
    }
}