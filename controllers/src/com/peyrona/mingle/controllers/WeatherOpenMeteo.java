
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

    private ScheduledFuture timer;

    //------------------------------------------------------------------------//

    @Override
    public void set( String deviceName, Map<String, Object> deviceInit, IController.Listener listener )
    {
        setName( deviceName );
        setListener( listener );     // Must be at begining: in case an error happens, Listener is needed

        float  latit    = (float)   deviceInit.get( KEY_LATITUDE  );                                             // This is REQUIRED
        float  longi    = (float)   deviceInit.get( KEY_LONGITUDE );                                             // This is REQUIRED
        int    interval = ((Number) deviceInit.getOrDefault( KEY_INTERVAL , 60 * UtilUnit.MINUTE )).intValue();   // Number of minutes between 2 consecutives calls to the Weather Web API
        int    forecast = ((Number) deviceInit.getOrDefault( KEY_FORECAST ,  0 * UtilUnit.HOUR   )).intValue();   // In millis but need to be converted into hours
        int    frame    = ((Number) deviceInit.getOrDefault( KEY_FRAME    ,  1 * UtilUnit.HOUR   )).intValue();   // In millis but need to be converted into hours (valid only when forecast > 0)
        String timezone = (String)  deviceInit.getOrDefault( KEY_TIME_ZONE, "auto" );

        if( isValid( latit, longi ) )
        {
            if( (forecast > 0) && (interval > frame) )    // interval frame are both in millis
            {
                sendIsInvalid( "'interval' must be smaller than 'frame'" );
            }
            else
            {
                short oneWeek = 24 * 7;   // In hours

                interval = (int) interval / UtilUnit.MINUTE;
                forecast = (int) forecast / UtilUnit.HOUR;
                frame    = (int) frame    / UtilUnit.HOUR;

                set( KEY_LATITUDE , String.valueOf( latit ) );
                set( KEY_LONGITUDE, String.valueOf( longi ) );
                set( KEY_INTERVAL , UtilUnit.setBetween( 15, interval, 60*24*99 ) * UtilUnit.MINUTE );           // Need to convert into millis
                set( KEY_FORECAST , UtilUnit.setBetween(  0, forecast, oneWeek -1 ) );                           // -1 == 1 hour before the limit (because minium frame is 1 hour)
                set( KEY_FRAME    , UtilUnit.setBetween(  1, frame   , oneWeek - (int) get( KEY_FORECAST ) ) );  // Needed to do "- nForecast", so nFrame does not go beyond 1 week
                set( KEY_METRIC   , (Boolean) deviceInit.getOrDefault( "metric", true ) );
                set( KEY_TIME_ZONE, timezone );

                setValid( true );
            }
        }
    }

    @Override
    public void read()
    {
        if( isInvalid() || isFaked() )
            return;

        UtilSys.execute( getClass().getName(),
                         () ->
                            {
                                int nDays  = ((int) get( KEY_FORECAST ) + (int) get( KEY_FRAME )) / 24;
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
                                    reader = new BufferedReader( new InputStreamReader( conn.getInputStream() ) );

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
    public void start( IRuntime rt )
    {
        if( isInvalid() )
            return;

        super.start( rt );

        if( timer == null )
            timer = UtilSys.executeAtRate( getClass().getName(), (int) get( KEY_INTERVAL ), (int) get( KEY_INTERVAL ), () -> read() );
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

        if( (int) get( KEY_FORECAST ) == 0 )
        {
            int ndx = LocalTime.now().getHour();    // From 0 to 23 (JSON array also goes from 0 to 23)

            for( String key : jo.names() )
                dict.put( getMyKeyFromOpenMeteoKey( key ), get( jo, key, ndx ) );
        }
        else
        {
            LocalTime time   = LocalTime.now();
            int       nHour  = time.getHour() + (time.getMinute() > 35 ? 1 : 0);   // When hour is past 35 minutes we want next hour
            int       nBegin = nHour  + (int) get( KEY_FORECAST );                 // Begining array index (JSON array is zero based and hour is zero based too)
            int       nEnd   = nBegin + (int) get( KEY_FRAME    );                                    // Ending array index

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

                if( (dict.get( sKey ) instanceof Float) && notMetric )
                {
                    switch( sKey )
                    {
                        case "temperature"         :
                        case "temperature_feel"    :
                        case "temperature_max"     :
                        case "temperature_min"     :
                        case "temperature_max_feel":
                        case "temperature_min_feel": dict.put( sKey, UtilUnit.celsius2fahrenheit( (Float) dict.get( sKey ) ) );
                                                     break;
                        case "windspeed"           : dict.put( sKey, UtilUnit.kmhr2mileshr( (Float) dict.get( sKey ) ) );
                                                     break;
                    }
                }
            }
        }

        return dict;
    }
}