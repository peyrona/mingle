
package com.peyrona.mingle.controllers.daikin.emura;

import com.peyrona.mingle.lang.japi.UtilStr;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

/**
 * Low-level HTTP transport for communicating with a Daikin Emura air conditioner
 * over its local REST API.
 * <p>
 * Supports three operations against the Emura's built-in HTTP server:
 * <ul>
 *   <li>{@link #get()} &ndash; reads sensor data ({@code /aircon/get_sensor_info})</li>
 *   <li>{@link #read()} &ndash; reads control state ({@code /aircon/get_control_info})</li>
 *   <li>{@link #write(String)} &ndash; sends a control command
 *       ({@code /aircon/set_control_info}) and returns the resulting state</li>
 * </ul>
 * <p>
 * Thread safety: all public methods are {@code synchronized} so that only one
 * HTTP transaction is in-flight at any given time, preventing command collisions
 * on the single-threaded Emura firmware.
 * <p>
 * Uses Java&nbsp;11's {@link HttpClient} with built-in connection pooling and
 * automatic keep-alive, avoiding the overhead of opening a new TCP connection
 * per request.
 *
 * @author Francisco José Morero Peyrona
 *
 * Official web site at: <a href="https://github.com/peyrona/mingle">https://github.com/peyrona/mingle</a>
 */
final class Talker
{
    /** Timeout for establishing a TCP connection to the Emura unit. */
    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds( 30 );

    /** Timeout for receiving the full HTTP response body. */
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds( 30 );

    private static final String USER_AGENT = "Java Daikin Emura Controller";

    private final HttpClient httpClient;
    private final URI        uriCtrlGet;
    private final URI        uriCtrlSet;
    private final URI        uriSensors;

    //----------------------------------------------------------------------------//

    /**
     * Creates a new Talker bound to the given Emura host.
     *
     * @param sHost IP address or hostname of the Emura unit (e.g. {@code "192.168.7.246"}).
     * @throws IOException if {@code sHost} produces an invalid URI.
     */
    Talker( String sHost ) throws IOException
    {
        try
        {
            String sBase = "http://" + sHost;

            uriCtrlGet = URI.create( sBase + "/aircon/get_control_info" );
            uriCtrlSet = URI.create( sBase + "/aircon/set_control_info" );
            uriSensors = URI.create( sBase + "/aircon/get_sensor_info"  );
        }
        catch( IllegalArgumentException iae )
        {
            throw new IOException( "Invalid Emura host address: " + sHost, iae );
        }

        httpClient = HttpClient.newBuilder()
                               .connectTimeout( CONNECT_TIMEOUT )
                               .build();
    }

    //----------------------------------------------------------------------------//
    // PACKAGE SCOPE

    /**
     * Reads current sensor values (inside/outside temperature, humidity).
     *
     * @return raw response body from {@code /aircon/get_sensor_info}
     *         (e.g. {@code "ret=OK,htemp=23.0,otemp=18.5,..."}).
     * @throws IOException if the request fails or the response is invalid.
     */
    synchronized String get() throws IOException
    {
        return doGet( uriSensors );
    }

    /**
     * Reads the current control state (power, mode, fan, wings, targets).
     *
     * @return raw response body from {@code /aircon/get_control_info}
     *         (e.g. {@code "ret=OK,pow=1,mode=3,stemp=22.0,..."}).
     * @throws IOException if the request fails or the response is invalid.
     */
    synchronized String read() throws IOException
    {
        return doGet( uriCtrlGet );
    }

    /**
     * Sends a control command and returns the resulting machine state.
     * <p>
     * The {@code values} string must be URL-encoded form data containing at
     * least the mandatory fields: {@code pow}, {@code mode}, {@code f_rate},
     * {@code f_dir}, {@code stemp}, and {@code shum}.
     * <p>
     * Example: {@code "pow=0&mode=6&f_rate=3&f_dir=0&stemp=22.0&shum=75"}
     *
     * @param values form-encoded control parameters.
     * @return the raw control state after applying the command (via a
     *         follow-up {@link #read()} call).
     * @throws IOException if the POST request fails, returns a non-200 status,
     *         or the subsequent read returns an invalid response.
     */
    synchronized String write( String values ) throws IOException
    {
        HttpRequest request = HttpRequest.newBuilder( uriCtrlSet )
                                         .timeout( REQUEST_TIMEOUT )
                                         .header( "Content-Type", "application/x-www-form-urlencoded" )
                                         .header( "User-Agent", USER_AGENT )
                                         .POST( HttpRequest.BodyPublishers.ofString( values, StandardCharsets.UTF_8 ) )
                                         .build();
        try
        {
            HttpResponse<String> response = httpClient.send( request, HttpResponse.BodyHandlers.ofString( StandardCharsets.UTF_8 ) );

            if( response.statusCode() != 200 )
                throw new IOException( "HTTP response code=" + response.statusCode() );

            return read();
        }
        catch( InterruptedException ie )
        {
            Thread.currentThread().interrupt();
            throw new IOException( "HTTP request interrupted", ie );
        }
    }

    //------------------------------------------------------------------------//
    // PRIVATE SCOPE

    /**
     * Sends a GET request and validates that the response starts with {@code "ret=OK"}.
     *
     * @param uri target endpoint URI.
     * @return validated response body.
     * @throws IOException if the request fails, returns a non-200 status, or
     *         the body does not start with {@code "ret=OK"}.
     */
    private String doGet( URI uri ) throws IOException
    {
        HttpRequest request = HttpRequest.newBuilder( uri )
                                         .timeout( REQUEST_TIMEOUT )
                                         .header( "User-Agent", USER_AGENT )
                                         .GET()
                                         .build();
        try
        {
            HttpResponse<String> response = httpClient.send( request, HttpResponse.BodyHandlers.ofString( StandardCharsets.UTF_8 ) );

            if( response.statusCode() != 200 )
                throw new IOException( "HTTP response code=" + response.statusCode() + " from " + uri );

            String sBody = response.body();

            if( UtilStr.isEmpty( sBody ) || ! sBody.startsWith( "ret=OK" ) )
                throw new IOException( "Invalid response from Emura: " + sBody );

            return sBody;
        }
        catch( InterruptedException ie )
        {
            Thread.currentThread().interrupt();
            throw new IOException( "HTTP request interrupted", ie );
        }
    }
}