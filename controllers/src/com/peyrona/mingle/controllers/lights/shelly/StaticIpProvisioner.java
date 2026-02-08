
package com.peyrona.mingle.controllers.lights.shelly;

import com.eclipsesource.json.Json;
import com.eclipsesource.json.JsonObject;
import com.eclipsesource.json.JsonValue;
import com.peyrona.mingle.lang.MingleException;
import com.peyrona.mingle.lang.japi.UtilComm;
import com.peyrona.mingle.lang.japi.UtilStr;
import com.peyrona.mingle.lang.xpreval.functions.pair;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

/**
* Provisions a Shelly device with a static IP address and WiFi credentials.
* <p>
* Supports both Shelly Gen1 and Gen2+ devices by auto-detecting the device
* generation via the {@code /shelly} endpoint:
* <ul>
*   <li><b>Gen1</b> (RGBW2, Dimmer, 1/1PM, etc.): Uses REST API
*       {@code GET /settings/sta?ssid=...&key=...&ipv4_method=static&ip=...}</li>
*   <li><b>Gen2+</b> (Plus RGBW PM, Pro, etc.): Uses JSON-RPC API
*       {@code POST /rpc} with {@code WiFi.SetConfig}</li>
* </ul>
*/
class StaticIpProvisioner
{
    private static final short GEN1 = 1;
    private static final short GEN2 = 2;

    private HttpClient http;

    /**
     * Provisions the Shelly device at {@code nowIp} to connect to the specified WiFi network with a static IP (the {@code address} config value).
     *
     * @param pConfig A pair containing: nowIp, ssid, pass, and optionally gateway, netmask, dns.
     * @throws Exception If provisioning fails.
     */
    private void link( pair pConfig, String newIP ) throws Exception
    {
        String nowIP = getStringFromPair( pConfig, "nowIP", null );

        // If the device is reachable at the new (desired) IP, then there is nothing else
        // to do: most probably this is because this functionality was used previously
        if( UtilComm.isReachable( newIP ) )
        {
            return;
        }

        if( UtilStr.isEmpty( nowIP ) )
        {
            missed( "nowIP" );
        }

        if( !UtilComm.isReachable( nowIP ) )
        {
            throw new IllegalArgumentException( nowIP + " is not reachable, therefore it can't be changed to " + newIP );
        }

        // Extract configuration values from the pair
        String ssid = getStringFromPair( pConfig, "ssid", null );
        String pass = getStringFromPair( pConfig, "pass", null );
        String gateway = getStringFromPair( pConfig, "gateway", null );
        String netmask = getStringFromPair( pConfig, "netmask", "255.255.255.0" );
        String dns = getStringFromPair( pConfig, "dns", "9.9.9.9" );

        // Validate required parameters
        if( ssid == null || pass == null )
        {
            missed( "'ssid' and/or 'pass'" );
        }

        // Compute default gateway if not provided (*.*.*.1 based on newIP)
        if( gateway == null )
        {
            String[] parts = newIP.split( "\\." );

            if( parts.length == 4 )
            {
                gateway = parts[0] + "." + parts[1] + "." + parts[2] + ".1";
            }
            else
            {
                missed( "nowIP" );
            }
        }

        http = HttpClient.newBuilder()
                .connectTimeout( Duration.ofSeconds( 2 ) )
                .build();

        // Detect device generation
        int gen = detectGeneration( nowIP );

        // Apply WiFi configuration using the appropriate API
        if( gen >= GEN2 )
        {
            linkGen2( nowIP, newIP, ssid, pass, gateway, netmask, dns );
        }
        else
        {
            linkGen1( nowIP, newIP, ssid, pass, gateway, netmask, dns );
        }

        // Give it a moment to store config and reconnect
        Thread.sleep( 2000 );

        // Probe the new IP until the device responds
        if( ! waitUntilResponds( newIP, Duration.ofSeconds( 30 ) ) )
        {
            throw new MingleException( "WiFi config sent to Shelly @ " + nowIP + " successfully. "
                                        + "Device not yet reachable at " + newIP + " — this is expected when "
                                        + "provisioning over AP mode (the AP is disabled when STA connects). "
                                        + "Switch your network to the target WiFi and verify the device at " + newIP );
        }
    }

    //------------------------------------------------------------------------//
    /**
     * Detects the Shelly device generation by querying {@code GET /shelly}.
     * <p>
     * Gen2+ devices include a {@code "gen"} field (value 2 or higher). Gen1 devices do not include this field (they have {@code "type"} instead).
     *
     * @param ip The device IP address.
     * @return {@link #GEN2} for Gen2+ devices, {@link #GEN1} for Gen1 devices.
     * @throws Exception If the device is not reachable or the response is invalid.
     */
    private int detectGeneration( String ip ) throws Exception
    {
        HttpRequest req = HttpRequest.newBuilder()
                .uri( URI.create( "http://" + ip + "/shelly" ) )
                .timeout( Duration.ofSeconds( 5 ) )
                .GET()
                .build();

        HttpResponse<String> resp = http.send( req, HttpResponse.BodyHandlers.ofString() );

        if( resp.statusCode() / 100 != 2 )
        {
            throw new RuntimeException( "Cannot identify Shelly device at " + ip + ": HTTP " + resp.statusCode() );
        }

        JsonObject info = Json.parse( resp.body() ).asObject();
        JsonValue gen = info.get( "gen" );

        if( gen != null && gen.isNumber() && gen.asInt() >= 2 )
        {
            System.out.println( "Shelly @ " + ip + " identified as Gen" + gen.asInt() );
            return GEN2;
        }

        System.out.println( "Shelly @ " + ip + " identified as Gen1" );
        return GEN1;
    }

    /**
     * Provisions a Gen2+ Shelly device using the JSON-RPC {@code WiFi.SetConfig} method.
     */
    private void linkGen2( String nowIP, String newIP, String ssid, String pass,
                           String gateway, String netmask, String dns ) throws Exception
    {
        JsonObject staConfig = Json.object()
                .add( "ssid", ssid )
                .add( "pass", pass )
                .add( "enable", true )
                .add( "ipv4mode", "static" )
                .add( "ip", newIP )
                .add( "netmask", netmask )
                .add( "nameserver", dns );

        if( gateway != null )
        {
            staConfig.add( "gw", gateway );
        }

        JsonObject config = Json.object().add( "sta", staConfig );
        JsonObject params = Json.object().add( "config", config );

        JsonObject requestBody = Json.object()
                .add( "id", 1 )
                .add( "method", "WiFi.SetConfig" )
                .add( "params", params );

        rpcPost( nowIP, requestBody.toString() );
    }

    /**
     * Provisions a Gen1 Shelly device using the REST API {@code GET /settings/sta}.
     * <p>
     * Gen1 devices use query parameters instead of JSON-RPC: {@code /settings/sta?enabled=1&ssid=...&key=...&ipv4_method=static&ip=...&mask=...&gw=...&dns=...}
     */
    private void linkGen1( String nowIP, String newIP, String ssid, String pass,
                           String gateway, String netmask, String dns ) throws Exception
    {
        StringBuilder url = new StringBuilder( "http://" + nowIP + "/settings/sta" );
        url.append( "?enabled=1" );
        url.append( "&ssid=" ).append( URLEncoder.encode( ssid, StandardCharsets.UTF_8 ) );
        url.append( "&key=" ).append( URLEncoder.encode( pass, StandardCharsets.UTF_8 ) );
        url.append( "&ipv4_method=static" );
        url.append( "&ip=" ).append( URLEncoder.encode( newIP, StandardCharsets.UTF_8 ) );
        url.append( "&mask=" ).append( URLEncoder.encode( netmask, StandardCharsets.UTF_8 ) );
        url.append( "&dns=" ).append( URLEncoder.encode( dns, StandardCharsets.UTF_8 ) );

        if( gateway != null )
        {
            url.append( "&gw=" ).append( URLEncoder.encode( gateway, StandardCharsets.UTF_8 ) );
        }

        HttpRequest req = HttpRequest.newBuilder()
                .uri( URI.create( url.toString() ) )
                .timeout( Duration.ofSeconds( 5 ) )
                .GET()
                .build();

        HttpResponse<String> resp = http.send( req, HttpResponse.BodyHandlers.ofString() );

        if( resp.statusCode() / 100 != 2 )
        {
            throw new RuntimeException( "HTTP " + resp.statusCode() + " from Shelly Gen1 @ " + nowIP + ": " + resp.body() );
        }
    }

    /**
     * Extracts a String value from a pair, returning a default if absent or empty.
     */
    private String getStringFromPair( pair p, String key, String def )
    {
        Object value = p.get( key );

        return UtilStr.isEmpty( value ) ? def : value.toString();
    }

    /**
     * Polls the device at the given IP until it responds to {@code GET /shelly}.
     * <p>
     * Uses the {@code /shelly} endpoint because it is available on both Gen1 and Gen2+ devices, unlike {@code /rpc/Sys.GetStatus} which is Gen2+ only.
     *
     * @param ip      The IP address to probe.
     * @param timeout Maximum time to wait for a response.
     * @return {@code true} if the device responded, {@code false} if timed out.
     * @throws InterruptedException If the thread is interrupted while sleeping.
     */
    private boolean waitUntilResponds( String ip, Duration timeout ) throws InterruptedException
    {
        long deadline = System.currentTimeMillis() + timeout.toMillis();
        int attempts = 0;

        while( System.currentTimeMillis() < deadline )
        {
            try
            {
                attempts++;
                httpGet( ip, "/shelly" );
                System.out.println( "Shelly device responding on new IP " + ip + " after " + attempts + " attempts" );
                return true;
            }
            catch( Exception e )
            {
                Thread.sleep( 500 );
            }
        }

        return false;
    }

    /**
     * Sends a JSON-RPC POST request to the Gen2+ {@code /rpc} endpoint.
     */
    private JsonObject rpcPost( String ip, String jsonBody ) throws Exception
    {
        HttpRequest req = HttpRequest.newBuilder()
                .uri( URI.create( "http://" + ip + "/rpc" ) )
                .timeout( Duration.ofSeconds( 5 ) )
                .header( "Content-Type", "application/json" )
                .POST( HttpRequest.BodyPublishers.ofString( jsonBody ) )
                .build();

        HttpResponse<String> resp = http.send( req, HttpResponse.BodyHandlers.ofString() );

        if( resp.statusCode() / 100 != 2 )
        {
            throw new RuntimeException( "HTTP " + resp.statusCode() + " from Shelly @ " + ip + ": " + resp.body() );
        }

        return Json.parse( resp.body() ).asObject();
    }

    /**
     * Sends a GET request to the specified path on the Shelly device.
     *
     * @param ip   The device IP address.
     * @param path The URL path (e.g., "/shelly", "/settings").
     * @return The response body as a string.
     * @throws Exception If the request fails or returns a non-2xx status.
     */
    private String httpGet( String ip, String path ) throws Exception
    {
        HttpRequest req = HttpRequest.newBuilder()
                .uri( URI.create( "http://" + ip + path ) )
                .timeout( Duration.ofSeconds( 5 ) )
                .GET()
                .build();

        HttpResponse<String> resp = http.send( req, HttpResponse.BodyHandlers.ofString() );

        if( resp.statusCode() / 100 != 2 )
        {
            throw new RuntimeException( "HTTP " + resp.statusCode() + " from Shelly @ " + ip + ": " + resp.body() );
        }

        return resp.body();
    }

    private void missed( String param )
    {
        throw new IllegalArgumentException( "Missing required configure parameter: " + param );
    }
}