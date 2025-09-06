
package com.peyrona.mingle.controllers.daikin.emura;

import com.peyrona.mingle.lang.japi.UtilStr;
import com.peyrona.mingle.lang.japi.UtilUnit;
import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.charset.StandardCharsets;

/**
 * This is the class that really talks with the Emura machine.
 *
 * @author Francisco José Morero Peyrona
 *
 * Official web site at: <a href="https://mingle.peyrona.com">https://mingle.peyrona.com</a>
 */
final class Talker
{
    private final URL urlCtrlGet;
    private final URL urlCtrlSet;
    private final URL urlSensors;

    //----------------------------------------------------------------------------//

    Talker( String sHost ) throws MalformedURLException
    {
        urlCtrlGet = new URL( "http://"+ sHost +"/aircon/get_control_info" );
        urlCtrlSet = new URL( "http://"+ sHost +"/aircon/set_control_info" );
        urlSensors = new URL( "http://"+ sHost +"/aircon/get_sensor_info"  );
    }

    //----------------------------------------------------------------------------//
    // PACKAGE SCOPE

    synchronized String get() throws IOException
    {
        return read( urlSensors );
    }

    synchronized String read() throws IOException
    {
        return read( urlCtrlGet );
    }

    synchronized String write( String values ) throws IOException
    {
        HttpURLConnection conn = null;

        try
        {
            byte[] aData = values.getBytes(StandardCharsets.UTF_8);

            conn = connect( urlCtrlSet );
            conn.setRequestProperty( "Content-Length", String.valueOf( aData.length ) );

            try( DataOutputStream dos = new DataOutputStream( conn.getOutputStream() ) )
            {
                dos.write( aData );
                dos.flush();
            }

            int n = conn.getResponseCode();

            if( n != HttpURLConnection.HTTP_OK )
                throw new IOException( "HTTP response code="+ n );

            return read();
        }
        finally
        {
            if( conn != null )
                conn.disconnect();
        }
    }

    //------------------------------------------------------------------------//
    // PRIVATE SCOPE

    private HttpURLConnection connect( URL url ) throws IOException
    {
        HttpURLConnection conn = null;

        try
        {
            boolean bPOST = urlCtrlSet.equals( url );

            conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod( (bPOST ? "POST" : "GET") );
            conn.setDoInput( true );
            conn.setDoOutput( bPOST );
            conn.setUseCaches( false );
            conn.setRequestProperty( "Content-Type", "application/x-www-form-urlencoded" );
            conn.setRequestProperty( "User-Agent"  , "Java Daikin Emura Controller");    // Some servers may reject requests without a User-Agent header.
            conn.setConnectTimeout( 2 * UtilUnit.MINUTE );
            conn.setReadTimeout(    2 * UtilUnit.MINUTE );

            return conn;
        }
        catch( IOException ioe )
        {
            if( conn != null )
                conn.disconnect();

            throw ioe;
        }
    }

    private String read( URL url ) throws IOException
    {
        HttpURLConnection conn = null;

        try
        {
            conn = connect( url );

            try( BufferedReader in = new BufferedReader( new InputStreamReader( conn.getInputStream() ) ) )
            {
                String        sLine;
                StringBuilder sbRet = new StringBuilder();

                while( (sLine = in.readLine()) != null )
                {
                    sbRet.append( sLine );
                }

                if( UtilStr.isEmpty( sbRet ) || ! UtilStr.startsWith( sbRet, "ret=OK" ) )
                    throw new IOException( "Invalid response from Emura: " + sbRet );

                return sbRet.toString();
            }
        }
        finally
        {
            if( conn != null )
                conn.disconnect();
        }
    }

    //----------------------------------------------------------------------------//
    // FOR TESTING PURPOSES

//    public static void main( String[] as ) throws MalformedURLException, IOException
//    {
//        Talker et = new Talker( "192.168.7.246" );
//
//        System.out.println( et.read() );
//        System.out.println( et.write("pow=0&mode=6&f_rate=3&f_dir=0&stemp=22.0&shum=75") );   // AC expects at least these values
//    }
}