
package com.peyrona.mingle.controllers;

import com.eclipsesource.json.JsonArray;
import com.eclipsesource.json.JsonObject;
import com.eclipsesource.json.JsonValue;
import com.peyrona.mingle.lang.MingleException;
import com.peyrona.mingle.lang.interfaces.IController;
import com.peyrona.mingle.lang.interfaces.exen.IRuntime;
import com.peyrona.mingle.lang.japi.UtilJson;
import com.peyrona.mingle.lang.japi.UtilSys;
import com.peyrona.mingle.lang.japi.UtilUnit;
import com.peyrona.mingle.lang.xpreval.functions.date;
import com.peyrona.mingle.lang.xpreval.functions.list;
import com.peyrona.mingle.lang.xpreval.functions.pair;
import com.peyrona.mingle.lang.xpreval.functions.time;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.time.LocalTime;
import java.util.Map;
import java.util.concurrent.ScheduledFuture;

/**
 * This Controller retrieves weather information from OpenMeteo web service.<br>
 * Maximum allowed forecast is 1 week.
 * <p>
 * See note at ControllerBase.
 *
 * @author Francisco José Morero Peyrona
 *
 * Official web site at: <a href="https://github.com/peyrona/mingle">https://github.com/peyrona/mingle</a>
 */
public final class   WeatherOpenMeteo
             extends ControllerBase
{
    private static final String KEY_LATITUDE  = "latitude";
    private static final String KEY_LONGITUDE = "longitude";
    private static final String KEY_INTERVAL  = "interval";
    private static final String KEY_FORECAST  = "forecast";    // Hours ahead from the moment the WebService is invoked to begin forecasting.
    private static final String KEY_FRAME     = "frame";       // Hours to retrive starting at 'forecast' (when forecast > 0).
    private static final String KEY_TIME_ZONE = "timezone";
    private static final String KEY_METRIC    = "metric";

    private ScheduledFuture timer = null;

    //------------------------------------------------------------------------//

    @Override
    public void set( String deviceName, Map<String, Object> deviceInit, IController.Listener listener )
    {
        setDeviceName( deviceName );
        setListener( listener );     // Must be at begining: in case an error happens, Listener is needed
        setDeviceConfig( deviceInit );

        // Retrieve and validate required parameters (latitude/longitude)
        Object oLatit = get( KEY_LATITUDE );
        Object oLongi = get( KEY_LONGITUDE );

        if( oLatit == null || oLongi == null )
        {
            sendIsInvalid( "latitude and longitude are required" );
            return;
        }

        float latit = ((Number) oLatit).floatValue();
        float longi = ((Number) oLongi).floatValue();

        if( ! isValid( latit, longi ) )
            return;

        // Retrieve optional parameters with defaults (values come in milliseconds from Mingle parser)
        Object oInterval = get( KEY_INTERVAL );
        Object oForecast = get( KEY_FORECAST );
        Object oFrame    = get( KEY_FRAME );
        Object oTimezone = get( KEY_TIME_ZONE );
        Object oMetric   = get( KEY_METRIC );

        long   interval = (oInterval != null) ? ((Number) oInterval).longValue() : 60L * UtilUnit.MINUTE;  // Default: 1 hour
        long   forecast = (oForecast != null) ? ((Number) oForecast).longValue() : 0L;                     // Default: 0 (no forecast)
        long   frame    = (oFrame    != null) ? ((Number) oFrame   ).longValue() : 1L * UtilUnit.HOUR;     // Default: 1 hour
        String timezone = (oTimezone != null) ? oTimezone.toString() : "auto";
        boolean metric  = (oMetric   == null) || Boolean.parseBoolean( oMetric.toString() );               // Default: true

        // Validate interval vs frame relationship (both in millis at this point)
        if( (forecast > 0) && (interval > frame) )
        {
            sendIsInvalid( "'interval' must be smaller than 'frame'" );
            return;
        }

        // Convert from milliseconds to minutes for storage
        int oneWeekMins = 24 * 7 * 60;   // In minutes (10080)

        int intervalMins = (int) (interval / UtilUnit.MINUTE);
        int forecastMins = (int) (forecast / UtilUnit.MINUTE);
        int frameMins    = (int) (frame    / UtilUnit.MINUTE);

        // Validate and store configuration (all time values in MINUTES)
        int validInterval = UtilUnit.setBetween( 15, intervalMins, 60*24*99 );
        int validForecast = UtilUnit.setBetween(  0, forecastMins, oneWeekMins - 60 );   // -60 mins because min frame is 1h
        int validFrame    = UtilUnit.setBetween( 60, frameMins, oneWeekMins - validForecast );  // Min 60 mins (1h), max so it doesn't exceed 1 week

        set( KEY_LATITUDE , String.valueOf( latit ) );
        set( KEY_LONGITUDE, String.valueOf( longi ) );
        set( KEY_INTERVAL , validInterval );   // Stored in minutes
        set( KEY_FORECAST , validForecast );   // Stored in minutes
        set( KEY_FRAME    , validFrame );      // Stored in minutes
        set( KEY_METRIC   , metric );
        set( KEY_TIME_ZONE, timezone );

        setValid( true );
    }

    @Override
    public void read()
    {
        if( isInvalid() || isFaked() )
            return;

        UtilSys.execute( null,
                         () ->
                            {
                                int nForeHrs = ((Number) get( KEY_FORECAST )).intValue() / 60;  // Convert minutes to hours
                                int nFramHrs = ((Number) get( KEY_FRAME    )).intValue() / 60;  // Convert minutes to hours
                                int nDays    = (nForeHrs - nFramHrs) / 24;
                                    nDays += 2;    // +2 no harm and many things can happen (like invoking the service close to midnight): DO NOT DECREASE THIS VALUE

                                String sURL = "https://api.open-meteo.com/v1/forecast?latitude="+
                                              (String) get( KEY_LATITUDE ) +"&longitude="+ (String) get( KEY_LONGITUDE ) + "&timezone="+ (String) get( KEY_TIME_ZONE ) +
                                              "&hourly=temperature_2m,relativehumidity_2m,apparent_temperature,precipitation_probability,rain,showers,snowfall,weathercode,surface_pressure,"+
                                              "cloudcover,windspeed_10m,winddirection_10m&forecast_days="+ nDays;

                                HttpURLConnection conn   = null;
                                BufferedReader    reader = null;

                                try
                                {
                                    conn   = (HttpURLConnection) new URL( sURL ).openConnection();
                                    reader = new BufferedReader( new InputStreamReader( conn.getInputStream(), StandardCharsets.UTF_8 ) );

                                    StringBuilder sbAnswer = new StringBuilder( 1024 * 4 );
                                    String        sLine;

                                    while( (sLine = reader.readLine()) != null )
                                        sbAnswer.append( sLine );

                                    sendReaded( process( sbAnswer.toString() ) );
                                }
                                catch( IOException ioe )
                                {
                                    sendWriteError( sURL, ioe );
                                }
                                finally
                                {
                                    if( reader != null )
                                        try{ reader.close(); } catch( IOException ioe ) {}

                                    if( conn != null )
                                        conn.disconnect();
                                }
                            } );
    }

    @Override
    public void write( Object newValue )
    {
        sendIsNotWritable();
    }

    @Override
    public boolean start( IRuntime rt )
    {
        if( isInvalid() || (! super.start( rt )) )
            return false;

        if( timer == null )
        {
            long intervalMillis = ((Number) get( KEY_INTERVAL )).longValue() * UtilUnit.MINUTE;  // Convert minutes to millis
            timer = UtilSys.executeWithDelay( getClass().getName(), 5000L, intervalMillis, () -> read() );
        }

        return isValid();
    }

    @Override
    public void stop()
    {
        if( timer != null )
        {
            timer.cancel( true );
            timer = null;
        }

        super.stop();
    }

    //------------------------------------------------------------------------//

    private boolean isValid( float latitude, float longitude )
    {
        int maxLat = 90;
        int maxLon = 180;
        String msg = "";

        if( latitude > maxLat || latitude < -maxLat )
            msg = latitude +" is not valid";

        if( longitude > maxLon || longitude < -maxLon )
            msg = (msg.isEmpty() ? (longitude +" is not valid") : (msg +" and "+ longitude +" is not valid"));

        if( ! msg.isEmpty() )
            sendIsInvalid( msg );

        return msg.isEmpty();
    }

    private pair process( String sJSON ) throws IOException
    {
        JsonValue jv = UtilJson.parse( sJSON );

        if( (jv == null) || (! jv.isObject()) || (((JsonObject) jv).get( "hourly" ) == null) )
            sendReadError(new MingleException( "Malformed JSON:\n"+ sJSON ) );

        JsonObject jo  = jv.asObject();                   // Has to be stored before it will be removed
        String     sTZ = jo.get( "timezone" ).asString();
        pair       dict = new pair();

        jo = jo.get( "hourly" )     // Only "hourly" needed
               .asObject()
               .remove( "time" );   // We do not use this "hourly" array

        int forecastHrs = ((Number) get( KEY_FORECAST )).intValue() / 60;  // Convert minutes to hours

        if( forecastHrs == 0 )
        {
            int ndx = LocalTime.now().getHour();    // From 0 to 23 (JSON array also goes from 0 to 23)

            for( String key : jo.names() )
                dict.put( getMyKeyFromOpenMeteoKey( key ), get( jo, key, ndx ) );
        }
        else
        {
            int       frameHrs = ((Number) get( KEY_FRAME )).intValue() / 60;       // Convert minutes to hours
            LocalTime time     = LocalTime.now();
            int       nHour    = time.getHour() + (time.getMinute() > 35 ? 1 : 0);  // When hour is past 35 minutes we want next hour
            int       nBegin   = nHour  + forecastHrs;   // Beginning array index (JSON array is zero based and hour is zero based too)
            int       nEnd     = nBegin + frameHrs;      // Ending array index

            for( String key : jo.names() )
            {
                list lst = new list();

                for( int n = nBegin; n < nEnd; n++ )
                    lst.add( get( jo, key, n ) );

                dict.put( getMyKeyFromOpenMeteoKey( key ), lst );
            }
        }

        return convert( dict ).put( "read_day" , new date() )
                              .put( "read_time", new time() )
                              .put( "timezone" , sTZ        );
    }

    private Object get( JsonObject joHourly, String sArrayName, int index )
    {
        if( joHourly.get( sArrayName ) == null || ! joHourly.get( sArrayName ).isArray() )
            return "Error";

        JsonArray ja = joHourly.get( sArrayName ).asArray();

        try
        {
            return ja.get( index ).asFloat();
        }
        catch( Exception exc )
        {
            return "Error";
        }
    }

    private String getMyKeyFromOpenMeteoKey( String sOpenMeteoKey )
    {
        switch( sOpenMeteoKey )
        {
            case "temperature_2m"           : return "temperature";
            case "apparent_temperature"     : return "temperature_feel";
            case "relativehumidity_2m"      : return "humidity";          // %
            case "precipitation_probability": return "precipitation";     // %
            case "rain"                     : return "rain_amount";       // mm
            case "showers"                  : return "showers_amount";
            case "snowfall"                 : return "snowfall_amount";
            case "weathercode"              : return "code";
            case "surface_pressure"         : return "pressure";          // hPa
            case "cloudcover"               : return "clouds";            // %
            case "windspeed_10m"            : return "windspeed";
            case "winddirection_10m"        : return "wind_direction";    // Degrees in the circunference
        }

        return "Unknown";
    }

    private pair convert( pair dict )
    {
        boolean notMetric = ! (Boolean) get( KEY_METRIC );

        if( notMetric )
        {
            list keys = dict.keys();

            for( int n = 1; n <= keys.size(); n++  )
            {
                String sKey = keys.get( n ).toString();
                Object value = dict.get( sKey );

                if( value instanceof Float )
                {
                    Float converted = convertValue( sKey, (Float) value );

                    if( converted != null )
                        dict.put( sKey, converted );
                }
                else if( value instanceof list )
                {
                    list lstValues = (list) value;
                    list lstConverted = new list();

                    for( int i = 1; i <= lstValues.size(); i++ )
                    {
                        Object item = lstValues.get( i );

                        if( item instanceof Float )
                        {
                            Float converted = convertValue( sKey, (Float) item );
                            lstConverted.add( converted != null ? converted : item );
                        }
                        else
                        {
                            lstConverted.add( item );
                        }
                    }

                    dict.put( sKey, lstConverted );
                }
            }
        }

        return dict;
    }

    private Float convertValue( String sKey, Float value )
    {
        switch( sKey )
        {
            case "temperature"         :
            case "temperature_feel"    :
            case "temperature_max"     :
            case "temperature_min"     :
            case "temperature_max_feel":
            case "temperature_min_feel": return UtilUnit.celsius2fahrenheit( value );

            case "windspeed"           : return UtilUnit.kmhr2mileshr( value );

            case "rain_amount"         :
            case "showers_amount"      :
            case "snowfall_amount"     : return UtilUnit.mm2inches( value );
        }

        return null;
    }
}