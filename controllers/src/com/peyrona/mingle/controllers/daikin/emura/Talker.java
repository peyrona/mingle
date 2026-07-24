
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
 * Uses Java&nbsp;11's {@link HttpClient} forced to HTTP/1.1, the only protocol
 * the Emura firmware speaks. That firmware does not honour HTTP keep-alive
 * reliably: it silently closes idle pooled connections, so a request that happens
 * to reuse a just-closed connection fails with
 * {@code "HTTP/1.1 header parser received no bytes"} / {@link java.io.EOFException}
 * before any response byte is read. Every transaction is therefore retried a few
 * times ({@link #MAX_ATTEMPTS}): the failed attempt evicts the dead connection
 * from the pool, so the retry succeeds on a freshly opened one.
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

    /**
     * Number of attempts for a single HTTP transaction. The Emura adapter closes
     * idle keep-alive connections, so a request reusing a just-closed pooled
     * connection fails at once; the next attempt then succeeds on a fresh one.
     */
    private static final int MAX_ATTEMPTS = 3;

    /** Pause between retry attempts (the single-threaded Emura firmware needs a breather). */
    private static final Duration RETRY_PAUSE = Duration.ofMillis( 300 );

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
                               .version( HttpClient.Version.HTTP_1_1 )    // The Emura firmware only speaks HTTP/1.1
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

        HttpResponse<String> response = send( request );

        if( response.statusCode() != 200 )
            throw new IOException( "HTTP response code=" + response.statusCode() );

        return read();
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

        HttpResponse<String> response = send( request );

        if( response.statusCode() != 200 )
            throw new IOException( "HTTP response code=" + response.statusCode() + " from " + uri );

        String sBody = response.body();

        if( UtilStr.isEmpty( sBody ) || ! sBody.startsWith( "ret=OK" ) )
            throw new IOException( "Invalid response from Emura: " + sBody );

        return sBody;
    }

    /**
     * Sends a request, transparently retrying transient connection failures.
     * <p>
     * The Emura's WiFi adapter does not honour HTTP keep-alive reliably: it closes
     * idle pooled connections, so a request reusing a just-closed connection fails
     * with {@code "HTTP/1.1 header parser received no bytes"} /
     * {@link java.io.EOFException} before any response byte arrives. That failure
     * evicts the dead connection from the pool, so retrying succeeds on a freshly
     * opened one. Retrying is always safe here: reads are idempotent and writes
     * send the AC's full absolute state, so re-applying it yields the same result.
     * <p>
     * Only the transport call is retried; status-code and body validation are done
     * by the callers so a genuinely malformed request is not retried.
     *
     * @param request the request to send.
     * @return the HTTP response.
     * @throws IOException if every attempt fails (the last failure is propagated).
     */
    private HttpResponse<String> send( HttpRequest request ) throws IOException
    {
        IOException last = null;

        for( int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++ )
        {
            try
            {
                return httpClient.send( request, HttpResponse.BodyHandlers.ofString( StandardCharsets.UTF_8 ) );
            }
            catch( IOException ioe )
            {
                last = ioe;

                if( attempt < MAX_ATTEMPTS )
                    pause();
            }
            catch( InterruptedException ie )
            {
                Thread.currentThread().interrupt();
                throw new IOException( "HTTP request interrupted", ie );
            }
        }

        throw last;
    }

    /**
     * Sleeps for {@link #RETRY_PAUSE} between two HTTP attempts, converting an
     * interruption into an {@link IOException} so callers deal with one checked type.
     *
     * @throws IOException if the thread is interrupted while pausing.
     */
    private void pause() throws IOException
    {
        try
        {
            Thread.sleep( RETRY_PAUSE.toMillis() );
        }
        catch( InterruptedException ie )
        {
            Thread.currentThread().interrupt();
            throw new IOException( "HTTP request interrupted", ie );
        }
    }
}