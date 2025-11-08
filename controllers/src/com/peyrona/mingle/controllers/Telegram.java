
package com.peyrona.mingle.controllers;

import com.peyrona.mingle.lang.interfaces.IController;
import com.peyrona.mingle.lang.japi.UtilSys;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.util.Map;

/**
 * This Controller send device's value to: a Telegram-Bot.
 * <p>
 * See note at ControllerBase.
 *
 * @author Francisco José Morero Peyrona
 *
 * Official web site at: <a href="https://github.com/peyrona/mingle">https://github.com/peyrona/mingle</a>
 */
public final class   Telegram
             extends ControllerBase
{
    private static final String KEY_CHAT  = "chat";
    private static final String KEY_TOKEN = "token";

    private String sLastSent = "";

    //------------------------------------------------------------------------//

    @Override
    public void set( String deviceName, Map<String, Object> deviceInit, IController.Listener listener )
    {
        setName( deviceName );
        setListener( listener );   // Must be at begining: in case an error happens, Listener is needed
        setValid( true );          // This controller is always valid
        set( deviceInit );         // Can be done because mapConfig values are not modified
    }

    @Override
    public void read()
    {
        // isInvalid() --> Is not needed because this controller is always valid

        sendReaded( sLastSent );
    }

    @Override
    public void write( Object deviceValue )
    {
        if( isFaked() )  // || isInvalid() --> Is not needed because this controller is always valid
            return;

        UtilSys.execute( getClass().getName(),
                         () ->  {
                                    String msg = deviceValue.toString();

                                    try
                                    {
                                        _sendIM_( (String) get( KEY_TOKEN ), (String) get( KEY_CHAT ), msg );
                                        sLastSent = msg;
                                        sendReaded( msg );
                                    }
                                    catch( IOException ioe )
                                    {
                                        sLastSent = "";
                                        sendWriteError( msg, ioe );
                                    }
                                } );
    }

    //------------------------------------------------------------------------//

    private void _sendIM_( String sToken, String sChat, String sMsg ) throws IOException
    {
        URL url  = new URL( "https://api.telegram.org/bot" + sToken +
                            "/sendMessage?chat_id=" + URLEncoder.encode( sChat, "UTF-8" ) +
                            "&text=" + URLEncoder.encode( sMsg, "UTF-8" ) );

        HttpURLConnection conn = null;

        try
        {
            conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod( "GET" );

            int code = conn.getResponseCode();

            if( code == HttpURLConnection.HTTP_OK )
            {
                try( BufferedReader in = new BufferedReader( new InputStreamReader( conn.getInputStream() ) ) )
                {
                    String        inputLine;
                    StringBuilder response = new StringBuilder();

                    while( (inputLine = in.readLine()) != null )
                        response.append( inputLine );

                    if( ! response.toString().startsWith( "{\"ok\":true" ) )
                        throw new IOException( "Telegram did not return OK, but: "+ response.toString() );
                }
            }
            else
            {
                throw new IOException( "GET request not worked. Response code = "+ code );
            }
        }
        finally
        {
            if( conn != null )
                conn.disconnect();
        }
    }
}