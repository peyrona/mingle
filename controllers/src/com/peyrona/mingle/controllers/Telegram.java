
package com.peyrona.mingle.controllers;

import com.eclipsesource.json.JsonArray;
import com.eclipsesource.json.JsonObject;
import com.eclipsesource.json.JsonValue;
import com.peyrona.mingle.lang.MingleException;
import com.peyrona.mingle.lang.interfaces.IController;
import com.peyrona.mingle.lang.interfaces.ILogger;
import com.peyrona.mingle.lang.interfaces.exen.IRuntime;
import com.peyrona.mingle.lang.japi.UtilJson;
import com.peyrona.mingle.lang.japi.UtilSys;
import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

/**
 * This Controller sends device's value to: a Telegram-Bot and receives text messages from the bot.
 * <p>
 * Supports both sending messages via {@link #write} and receiving messages via {@link #read}.
 * Received messages are queued and returned in FIFO order. Only text messages are received;
 * voice, photos, and other message types are ignored.
 * <p>
 * Incoming messages are retrieved using a continuous long-poll loop running on a dedicated
 * daemon thread. The loop immediately re-issues a {@code getUpdates} request after each one
 * completes, delivering new messages within approximately one second of arrival. A transient
 * error causes a short back-off sleep before the next attempt; an {@link InterruptedException}
 * terminates the loop cleanly.
 * <p>
 * <b>Important:</b> Telegram only allows one active {@code getUpdates} connection per bot token
 * at a time. The long-poll thread is therefore only started when {@code receive} is explicitly
 * set to {@code true} in the driver CONFIG. Instances that only send messages must omit
 * {@code receive} (or set it to {@code false}) to avoid conflicting with the receiving instance.
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
    private static final String KEY_CHAT    = "chat";
    private static final String KEY_TOKEN   = "token";
    private static final String KEY_TIMEOUT = "timeout";
    private static final String KEY_RECEIVE = "receive";    // if false (default), no long-poll thread is started

    private static final String TEMPLATE_TO_SEND       = "https://api.telegram.org/bot%s/sendMessage?chat_id=%s&text=%s";
    private static final String TEMPLATE_DELETE_WBHOOK = "https://api.telegram.org/bot%s/deleteWebhook";

    // Square brackets and double-quotes are pre-encoded so that URI.create() never sees bare
    // RFC-3986-invalid characters: [ → %5B, ] → %5D, " → %22.
    // The literal percent signs are doubled (%% → %) so that String.format does not misinterpret
    // the encoded sequences (e.g. %22m) as its own format specifiers.
    private static final String TEMPLATE_TO_RECEIVE    = "https://api.telegram.org/bot%s/getUpdates?offset=%d&timeout=%d&allowed_updates=%%5B%%22message%%22%%5D";

    private static final int DEFAULT_TIMEOUT = 30;
    private static final int BACKOFF_MILLIS  = 5_000;  // back-off after a transient poll error

    private static final java.net.http.HttpClient httpClient = java.net.http.HttpClient.newBuilder()
                                                                            .followRedirects( java.net.http.HttpClient.Redirect.NORMAL )
                                                                            .version( java.net.http.HttpClient.Version.HTTP_1_1 )        // ver 1.1 needed
                                                                            .build();

    /**
     * Last message received from Telegram (used by {@link #read} as a fallback when the queue
     * is empty, so that {@code read()} never accidentally returns an outgoing message).
     */
    private volatile String  sLastReceived = "";
    /** Telegram update_id is a 64-bit integer; using {@code long} avoids overflow. */
    private volatile long    lastUpdateId  = 0;
    /** HTTP timeout shared by both send and poll requests. */
    private final    Duration              httpTimeout  = Duration.ofSeconds( 30 );
    private final    BlockingQueue<String> receivedMsgs = new LinkedBlockingQueue<>();

    private volatile boolean running        = false;
    private          Thread  longPollThread = null;

    //------------------------------------------------------------------------//

    @Override
    public void set( String deviceName, Map<String, Object> deviceConf, IController.Listener listener )
    {
        setDeviceName( deviceName );
        setListener( listener );         // Must be at beginning: in case an error happens, Listener is needed
        setDeviceConfig( deviceConf );   // Can be done because mapConfig values are not modified

        Object oTimeout = get( KEY_TIMEOUT );

        int timeout = (oTimeout != null) ? ((Number) oTimeout).intValue() : DEFAULT_TIMEOUT;

        set( KEY_TIMEOUT, timeout );

        setValid( true );          // This controller is always valid
    }

    @Override
    public boolean start( IRuntime rt )
    {
        if( isInvalid() || (! super.start( rt )) )
            return false;

        boolean receive = Boolean.TRUE.equals( get( KEY_RECEIVE ) );

        if( receive )
        {
            _deleteWebhook_();   // Telegram forbids getUpdates while a webhook is active

            running = true;
            longPollThread = new Thread( this::_longPollLoop_, "telegram-long-poll-" + getDeviceName() );
            longPollThread.setDaemon( true );
            longPollThread.start();
        }

        return isValid();
    }

    @Override
    public void stop()
    {
        running = false;

        if( longPollThread != null )
        {
            longPollThread.interrupt();

            try
            {
                longPollThread.join( 3_000 );
            }
            catch( InterruptedException ie )
            {
                Thread.currentThread().interrupt();
            }

            longPollThread = null;
        }

        receivedMsgs.clear();
        super.stop();
    }

    @Override
    public void read()
    {
        if( isInvalid() )
            return;

        String message;

        if( isFaked() )
        {
            message = sLastReceived;
        }
        else
        {
            message = receivedMsgs.poll();

            if( message != null )  sLastReceived = message;
            else                   message = sLastReceived;
        }

        sendReaded( message );
    }

    @Override
    public void write( Object deviceValue )
    {
        String msg = deviceValue.toString();

        if( isFaked() )  // || isInvalid() --> Is not needed because this controller is always valid
            return;

        UtilSys.executor( true )
               .execute( () ->  {
                                    try
                                    {
                                        _sendIM_( msg );
                                        // Intentionally NOT calling sendChanged here: outgoing messages
                                        // must not re-trigger WHEN rules (that would cause a feedback loop).
                                    }
                                    catch( MingleException ioe )
                                    {
                                        sendWriteError( msg, ioe );
                                    }
                                } );
    }

    //------------------------------------------------------------------------//

    /**
     * Calls the Telegram Bot API {@code deleteWebhook} endpoint to remove any active webhook
     * before starting the long-poll loop. Telegram rejects {@code getUpdates} with HTTP 409
     * when a webhook is registered, so this must be called once during {@link #start}.
     * <p>
     * Failures are logged as warnings rather than errors so that startup is not aborted
     * (the 409 in the poll loop will make the problem visible anyway).
     */
    private void _deleteWebhook_()
    {
        String token = (String) get( KEY_TOKEN );
        URI    uri   = URI.create( String.format( TEMPLATE_DELETE_WBHOOK, token ) );

        HttpRequest request = HttpRequest.newBuilder()
                                         .uri( uri )
                                         .timeout( httpTimeout )
                                         .GET()
                                         .build();

        try
        {
            HttpResponse<String> response = httpClient.send( request, HttpResponse.BodyHandlers.ofString( StandardCharsets.UTF_8 ) );

            if( response.statusCode() < 200 || response.statusCode() >= 300 )
                sendGenericError( ILogger.Level.WARNING, "deleteWebhook returned HTTP "+ response.statusCode() +": "+ response.body() );
        }
        catch( InterruptedException ie )
        {
            Thread.currentThread().interrupt();
        }
        catch( IOException ioe )
        {
            sendGenericError( ILogger.Level.WARNING, "Could not delete Telegram webhook: "+ ioe.getMessage() );
        }
    }

    /**
     * Continuously issues long-poll requests to the Telegram Bot API until the controller
     * is stopped or the thread is interrupted.
     * <p>
     * Each call to {@link #_pollUpdates_()} blocks for up to {@code timeout} seconds (or
     * until Telegram delivers at least one update). Immediately after it returns the loop
     * re-issues the request, providing near-real-time message delivery. A transient error
     * causes a {@value #BACKOFF_MILLIS} ms back-off before the next attempt to avoid
     * hammering the API.
     */
    private void _longPollLoop_()
    {
        String token   = (String) get( KEY_TOKEN );
        String chat    = (String) get( KEY_CHAT );
        int    timeout = ((Number) get( KEY_TIMEOUT )).intValue();

        while( running && ! Thread.currentThread().isInterrupted() )
        {
            URI uri = URI.create( String.format( TEMPLATE_TO_RECEIVE, token, lastUpdateId + 1, timeout ) );    // Has to be re-created each time (bacause: lastUpdateId + 1)

            try
            {
                _pollUpdates_( uri, chat, timeout );
            }
            catch( InterruptedException ie )
            {
                Thread.currentThread().interrupt();
                break;
            }
            catch( Exception exc )
            {
                sendReadError( exc );

                try
                {
                    Thread.sleep( BACKOFF_MILLIS );
                }
                catch( InterruptedException ie )
                {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }
    }

    /**
     * Sends a text message to a Telegram chat via the Bot API.
     *
     * @param message the text to send
     * @throws MingleException if the API returns an error or the HTTP call fails
     */
    private void _sendIM_( String message )
    {
        if( message == null )
            throw new MingleException( "Message must not be null" );

        // Build URI with proper encoding
        String botToken       = (String) get( KEY_TOKEN );
        String chatId         = (String) get( KEY_CHAT  );
        String encodedChatId  = URLEncoder.encode( chatId , StandardCharsets.UTF_8 );
        String encodedMessage = URLEncoder.encode( message, StandardCharsets.UTF_8 );
        URI    uri            = URI.create( String.format( TEMPLATE_TO_SEND, botToken, encodedChatId, encodedMessage ) );

        HttpRequest request = HttpRequest.newBuilder()
                                         .uri( uri )
                                         .timeout( httpTimeout )
                                         .GET()
                                         .build();

        try
        {
            HttpResponse<String> response = httpClient.send( request, HttpResponse.BodyHandlers.ofString( StandardCharsets.UTF_8 ) );

            if( response.statusCode() < 200 || response.statusCode() >= 300 )
                throw new MingleException( "HTTP " + response.statusCode() + ": " + response.body() );
        }
        catch( InterruptedException ie )
        {
            Thread.currentThread().interrupt();  // Restore interrupt status
        }
        catch( IOException ioe )
        {
            throw new MingleException( "I/O error while calling Telegram API", ioe );
        }
    }

    /**
     * Performs a single long-poll request to the Telegram Bot API ({@code getUpdates}).
     * <p>
     * Telegram holds the HTTP connection open for up to {@code timeout} seconds. All
     * text messages that arrive for the configured chat are placed onto the
     * {@link #receivedMsgs} queue and trigger a {@code sendChanged} notification.
     * {@code lastUpdateId} is advanced so the next call retrieves only newer updates.
     *
     * @throws InterruptedException if the calling thread is interrupted while waiting
     *         for the HTTP response (propagated to {@link #_longPollLoop_()} to allow
     *         clean shutdown)
     */
    private void _pollUpdates_( URI uri, String targetChat, int timeout ) throws InterruptedException
    {
        HttpRequest request = HttpRequest.newBuilder()
                                         .uri( uri )
                                         .timeout( Duration.ofSeconds( timeout + 5 ) )
                                         .GET()
                                         .build();

        try
        {
            HttpResponse<String> response = httpClient.send( request, HttpResponse.BodyHandlers.ofString( StandardCharsets.UTF_8 ) );

            if( response.statusCode() < 200 || response.statusCode() >= 300 )
                throw new MingleException( "HTTP "+ response.statusCode() +": "+ response.body() );

            JsonValue jv = UtilJson.parse( response.body() );

            if( jv == null || ! jv.isObject() )
                return;

            JsonObject responseObj = jv.asObject();

            if( ! responseObj.get( "ok" ).asBoolean() )
                return;

            JsonArray results = responseObj.get( "result" ).asArray();

            for( JsonValue updateValue : results )
            {
                JsonObject update   = updateValue.asObject();
                long       updateId = update.get( "update_id" ).asLong();

                if( updateId <= lastUpdateId )
                    continue;

                // Advance the offset for every update seen, so non-matching updates
                // (wrong chat, no text, etc.) do not stall the long-poll loop.
                lastUpdateId = updateId;

                if( update.get( "message" ) == null )
                    continue;

                JsonObject message = update.get( "message" ).asObject();

                if( message.get( "text" ) == null )
                    continue;

                JsonObject chat   = message.get( "chat" ).asObject();
                String     chatId = String.valueOf( chat.get( "id" ).asLong() );

                if( ! chatId.equals( targetChat ) )
                    continue;

                String text = message.get( "text" ).asString();
                receivedMsgs.offer( text );
                sLastReceived = text;

                sendChanged( text );
            }
        }
        catch( InterruptedException ie )
        {
            throw ie;   // Propagate so _longPollLoop_ can shut down cleanly
        }
        catch( IOException ioe )
        {
            throw new MingleException( "I/O error while polling Telegram API", ioe );
        }
    }
}
