
package com.peyrona.mingle.gum;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Enumeration;
import java.util.Set;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/**
 * A simple HTTP reverse-proxy servlet.
 * <p>
 * Strips the configured context-path prefix from the incoming URI and forwards
 * the request to the configured target base URL, streaming the response back.
 * Hop-by-hop headers are filtered in both directions.
 * <p>
 * Usage example (in config.json, under "monitoring"):
 * <pre>
 *   "proxies": [
 *       { "path": "/gum/proxy/rpi", "target": "http://192.168.7.9:8080" }
 *   ]
 * </pre>
 * With this configuration, an incoming request to
 * {@code GET /gum/proxy/rpi/gum/index.html} is forwarded as
 * {@code GET http://192.168.7.9:8080/gum/index.html}.
 * <p>
 * <b>WebSocket limitation:</b> {@code HttpURLConnection} does not support
 * WebSocket upgrades. Only plain HTTP methods (GET, POST, PUT, DELETE, PATCH,
 * HEAD) are proxied.
 *
 * @author Francisco José Morero Peyrona
 *
 * Official web site at: <a href="https://github.com/peyrona/mingle">https://github.com/peyrona/mingle</a>
 */
final class ProxyServlet extends HttpServlet
{
    /** Hop-by-hop headers that must not be forwarded in either direction. */
    private static final Set<String> HOP_BY_HOP = Set.of( "connection", "keep-alive", "transfer-encoding", "te", "trailer", "upgrade", "proxy-authorization", "host" );

    private final String target;       // e.g. "http://192.168.7.9:8080"  (no trailing slash)
    private final String contextPath;  // e.g. "/gum/proxy/rpi"

    //------------------------------------------------------------------------//
    // CONSTRUCTOR

    /**
     * Creates a new ProxyServlet.
     *
     * @param target      Base URL of the downstream server (e.g. {@code http://192.168.7.9:8080}).
     * @param contextPath Context path registered for this servlet on the local server
     *                    (e.g. {@code /gum/proxy/rpi}), used to strip the prefix before forwarding.
     */
    ProxyServlet( String target, String contextPath )
    {
        this.target      = target.endsWith( "/" ) ? target.substring( 0, target.length() - 1 ) : target;
        this.contextPath = contextPath;
    }

    //------------------------------------------------------------------------//
    // OVERRIDE

    /**
     * Proxies every incoming HTTP request to the configured target server.
     *
     * @param req  the incoming servlet request
     * @param resp the servlet response to write into
     * @throws ServletException on servlet errors
     * @throws IOException      on I/O errors
     */
    @Override
    protected void service( HttpServletRequest req, HttpServletResponse resp )
            throws ServletException, IOException
    {
        // Build target URL: strip the context prefix, keep the rest + query string
        String pathInfo  = req.getRequestURI().substring( contextPath.length() );
        String query     = req.getQueryString();
        String targetUrl = target + (pathInfo.isEmpty() ? "/" : pathInfo) + (query != null ? '?' + query : "");

        HttpURLConnection conn = (HttpURLConnection) new URL( targetUrl ).openConnection();
                          conn.setRequestMethod( req.getMethod() );
                          conn.setConnectTimeout( 10_000 );
                          conn.setReadTimeout( 30_000 );
                          conn.setInstanceFollowRedirects( false );

        // Forward request headers (except hop-by-hop ones)
        Enumeration<String> headerNames = req.getHeaderNames();

        while( headerNames != null && headerNames.hasMoreElements() )
        {
            String name = headerNames.nextElement();

            if( ! HOP_BY_HOP.contains( name.toLowerCase() ) )
                conn.setRequestProperty( name, req.getHeader( name ) );
        }

        // Forward request body for methods that carry one
        String method = req.getMethod().toUpperCase();

        if( "POST".equals( method ) || "PUT".equals( method ) || "PATCH".equals( method ) )
        {
            conn.setDoOutput( true );

            try( InputStream  in  = req.getInputStream();
                 OutputStream out = conn.getOutputStream() )
            {
                in.transferTo( out );
            }
        }

        // Read response status and forward response headers (except hop-by-hop ones)
        int status = conn.getResponseCode();
        resp.setStatus( status );

        conn.getHeaderFields().forEach( (name, values) ->
        {
            if( name != null && !HOP_BY_HOP.contains( name.toLowerCase() ) )
                values.forEach( v -> resp.addHeader( name, v ) );
        } );

        // Stream response body back to the client
        InputStream src = (status >= 400) ? conn.getErrorStream() : conn.getInputStream();

        if( src != null )
        {
            try( src; OutputStream out = resp.getOutputStream() )
            {
                src.transferTo( out );
            }
        }
    }
}
