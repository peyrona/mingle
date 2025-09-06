
package com.peyrona.mingle.controllers;

import com.eclipsesource.json.Json;
import com.eclipsesource.json.JsonObject;
import com.eclipsesource.json.ParseException;
import com.peyrona.mingle.lang.interfaces.IController;
import com.peyrona.mingle.lang.interfaces.exen.IRuntime;
import com.peyrona.mingle.lang.japi.UtilSys;
import com.peyrona.mingle.lang.japi.UtilUnit;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.ScheduledFuture;

/**
 * This driver exists just for teaching purposes: it is not intended to be used in real scenarios.
 *
 * This this the JSON returned by the URL:
 *
 *   {
 *      "time": {
 *        "updated": "Nov 7, 2023 14:50:00 UTC",
 *        "updatedISO": "2023-11-07T14:50:00+00:00",
 *        "updateduk": "Nov 7, 2023 at 14:50 GMT"
 *      },
 *      "disclaimer": "This data was produced from the CoinDesk Bitcoin Price Index (USD). Non-USD currency data converted using hourly conversion rate from openexchangerates.org",
 *      "chartName": "Bitcoin",
 *      "bpi": {
 *        "USD": {
 *          "code": "USD",
 *          "symbol": "&#36;",
 *          "rate": "34,811.5975",
 *          "description": "United States Dollar",
 *          "rate_float": 34811.5975
 *        },
 *        "GBP": {
 *          "code": "GBP",
 *          "symbol": "&pound;",
 *          "rate": "29,088.2924",
 *          "description": "British Pound Sterling",
 *          "rate_float": 29088.2924
 *        },
 *        "EUR": {
 *          "code": "EUR",
 *          "symbol": "&euro;",
 *          "rate": "33,911.5785",
 *          "description": "Euro",
 *          "rate_float": 33911.5785
 *        }
 *      }
 *   }
 *
 * @author Francisco José Morero Peyrona
 *
 * Official web site at: <a href="https://mingle.peyrona.com">https://mingle.peyrona.com</a>
 */
public class BitcoinUSDPrice
       extends ControllerBase
{
    private static final String          sURL   = "https://api.coindesk.com/v1/bpi/currentprice.json";
    private              ScheduledFuture future = null;
    private              int             interval;

    //------------------------------------------------------------------------//

    @Override
    public void set( String deviceName, Map<String, Object> deviceInit, IController.Listener listener )
    {
        setName( deviceName );
        setListener( listener );    // Must be at begining: in case an error happens, Listener is needed

        interval = ((Number) deviceInit.getOrDefault( "interval", 30 * UtilUnit.SECOND )).intValue();
        interval = setBetween( "interval", 5, interval, UtilUnit.HOUR );

        setValid( true );
    }

    @Override
    public void start( IRuntime rt )
    {
        super.start( rt );

        if( future == null )
            future = UtilSys.executeAtRate( getClass().getName(), interval, interval, () -> read() );
    }

    @Override
    public void stop()
    {
        if( future != null )
        {
            future.cancel( true );
            future = null;
        }

        super.stop();
    }

    @Override
    public void read()
    {
        if( isFaked )
            sendReaded( new Random().nextFloat() * 201 + 100 );    // generate random number between 100 and 300

        try
        {
            float fPrice = getJSON().get( "bpi" ).asObject()
                                    .get( "USD" ).asObject()
                                    .getFloat( "rate_float", -1 );

            sendReaded( fPrice );
        }
        catch( IOException | ParseException ex )
        {
            sendReadError( ex );
        }
    }

    @Override
    public void write( Object value )
    {
        sendIsNotWritable();
    }

    //------------------------------------------------------------------------//

    private JsonObject getJSON() throws IOException
    {
        HttpURLConnection conn = (HttpURLConnection) new URL( sURL ).openConnection();
                          conn.setRequestMethod( "GET" );

        StringBuffer response;

        try( BufferedReader in = new BufferedReader( new InputStreamReader( conn.getInputStream() ) ) )
        {
            String sLine;
            response = new StringBuffer();

            while( (sLine = in.readLine()) != null )
                response.append( sLine );
        }

        return Json.parse( response.toString() ).asObject();
    }
}
