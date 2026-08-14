
package com.peyrona.mingle.gum;

import com.eclipsesource.json.Json;
import com.eclipsesource.json.JsonArray;
import com.eclipsesource.json.JsonObject;
import com.eclipsesource.json.JsonValue;
import com.peyrona.mingle.lang.japi.UtilColls;
import com.peyrona.mingle.lang.japi.UtilIO;
import com.peyrona.mingle.lang.japi.UtilStr;
import com.peyrona.mingle.lang.japi.UtilSys;
import java.io.IOException;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.util.regex.Pattern;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/**
 * ServiceDB class extends ServiceBase to provide database access functionality
 * through HTTP requests. This class handles the GET requests by retrieving data
 * from a database based on the parameters provided in the HTTP request.
 *
 * <p>Usage example:
 * <pre>{@code
 * ServiceDB service = new ServiceDB(httpExchange);
 * service.doGet();
 * }</pre>
 *
 * <p>
 * HTTP GET parameters:
 * <ul>
 * <li>action    : {@code "list"} returns the configured datasource names (no connection details); any
 *                 other value (or absent) runs the query below.
 * <li>datasource: Name of one of the datasources declared in config.json (required to query)
 * <li>from      : Starting time in milliseconds (optional, default is -1)
 * <li>to        : Ending time in milliseconds (optional, default is -1)
 * <li>tables    : JSON array of table information containing table name, timestamp column, and value column
 * </ul>
 *
 * <p>
 * <b>Where the connection comes from.</b> The driver JARs, driver class, URL and credentials are
 * <b>never</b> taken from the request: they are declared server-side under {@code monitoring.datasources}
 * in config.json and selected by name. This is deliberate — {@code UtilSys.addToClassPath()} accepts
 * {@code http(s)} URIs and {@code Class.forName()} runs the named class' static initialiser, so letting
 * a caller supply those two values would let anyone able to reach this endpoint execute arbitrary code
 * inside the Gum process.
 * <pre>
 * "datasources": [ { "name": "history",
 *                    "jars": "{*home*}lib/h2.jar",
 *                    "jdbc": "org.h2.Driver",
 *                    "url" : "jdbc:h2:{*home*}data/history",
 *                    "user": "sa",
 *                    "pwd" : "" } ]
 * </pre>
 *
 * @author Francisco José Morero Peyrona
 * Official web site at: <a href="https://github.com/peyrona/mingle">https://github.com/peyrona/mingle</a>
 */
final class ServiceDB extends ServiceBase
{
    /** Accepts {@code column} and {@code schema.table}: SQL identifiers are spliced into the query
        unquoted, so anything else has to be rejected before it reaches the statement. */
    private static final Pattern ptrnIdentifier = Pattern.compile( "[A-Za-z_][A-Za-z0-9_]*(\\.[A-Za-z_][A-Za-z0-9_]*)?" );

    //------------------------------------------------------------------------//
    // PACKAGE SCOPE

    ServiceDB( HttpServletRequest request, HttpServletResponse response )
    {
        super( request, response );
    }

    //------------------------------------------------------------------------//
    // PROTECTED SCOPE

    @Override
    protected void doGet() throws Exception
    {
        if( "list".equals( asString( "action", null ) ) )
        {
            sendJSON( listDataSourceNames().toString() );
            return;
        }

        long   nFrom   = asLong(   "from"      , -1l  );   // Starting millis
        long   nTo     = asLong(   "to"        , -1l  );   // Ending millis
        String sDSName = asString( "datasource", null );   // Name of a datasource declared in config.json
        String sTables = asString( "tables"    , null );   // Array of tables and columns (a string in JSON format)

        JsonObject joDS = findDataSource( sDSName );

        if( joDS == null )
        {
            sendError( "Unknown datasource: "+ sDSName, HttpServletResponse.SC_NOT_FOUND );
            return;
        }

        String sJARs   = UtilIO.replaceFileMacros( joDS.getString( "jars", null ) );   // JARs to be loaded at runtime
        String sDriver = joDS.getString( "jdbc", null );                               // JDBC Driver
        String sURL    = UtilIO.replaceFileMacros( joDS.getString( "url" , null ) );   // JDBC URL
        String sUser   = joDS.getString( "user", null );                               // User name
        String sPwd    = joDS.getString( "pwd" , null );                               // User password

        if( UtilStr.isNotEmpty( sJARs ) )
            UtilSys.addToClassPath( (String[]) UtilColls.toArray( sJARs ) );

        if( UtilStr.isNotEmpty( sDriver ) )
            Class.forName( sDriver );

        try( Connection conn = DriverManager.getConnection( sURL, sUser, sPwd ) )
        {
            java.sql.Date dFrom = UtilDB.toSqlDate( nFrom );
            java.sql.Date dTo   = UtilDB.toSqlDate( nTo   );

            JsonArray     ja = Json.parse( sTables ).asArray();
            StringBuilder sb = new StringBuilder( 1024 )
                                   .append( '[' );

            for( int n = 0; n < ja.size(); n++ )
            {
                JsonObject jo  = ja.get( n ).asObject();
                String     tbl = asIdentifier( jo.getString( "table" , null ) );
                String     tim = asIdentifier( jo.getString( "time"  , null ) );   // col name having the timestamp
                String     val = asIdentifier( jo.getString( "values", null ) );   // col name containing the values to retrieve
                String     sql = "select "+ tim +','+ val +" from "+ tbl +
                                 "   where "+ tim +" >= ? and "+ tim +" <= ?";

                PreparedStatement ps = UtilDB.createPreparedStatement( conn, sql );
                                  ps.clearParameters();
                                  ps.setDate( 1, dFrom );
                                  ps.setDate( 2, dTo   );
                                  ps.closeOnCompletion();

                sb.append( '{' )
                  .append( "\"label\": \"" ).append( tbl ).append( '.' ).append( val ).append( '"' )   // DataSerie -> "label": "table.values"
                  .append( ',' )
                  .append( "\"data\": " ).append( RS2Json.toJson( ps.executeQuery() ).get( "body" ).toString() )
                  .append( "}," );
            }

            if( UtilStr.isLastChar( sb, ',' ) )
                sb = UtilStr.removeLast( sb, 1 );

            sendJSON( sb.append( ']' ).toString() );
        }
    }

    //------------------------------------------------------------------------//
    // PRIVATE SCOPE

    /**
     * Lists the names of the datasources declared under {@code monitoring.datasources} — never their
     * connection details (jars, driver class, URL, credentials), which stay server-side always. Used
     * by the chart gadget's properties dialog to populate its datasource picker.
     *
     * @return A JSON array of datasource names (possibly empty).
     */
    private static JsonArray listDataSourceNames()
    {
        JsonArray ja      = UtilSys.getConfig().get( "monitoring", "datasources", Json.array() );
        JsonArray jaNames = Json.array();

        for( JsonValue jv : ja )
        {
            if( jv.isObject() )
            {
                String sName = jv.asObject().getString( "name", null );

                if( UtilStr.isNotEmpty( sName ) )
                    jaNames.add( sName );
            }
        }

        return jaNames;
    }

    /**
     * Finds a datasource by name among those declared under {@code monitoring.datasources}.
     *
     * @param sName The requested datasource name, may be {@code null}.
     * @return Its declaration, or {@code null} when the name is empty or unknown.
     */
    private static JsonObject findDataSource( String sName )
    {
        if( UtilStr.isEmpty( sName ) )
            return null;

        JsonArray ja = UtilSys.getConfig().get( "monitoring", "datasources", Json.array() );

        for( JsonValue jv : ja )
        {
            if( jv.isObject() )
            {
                JsonObject jo = jv.asObject();

                if( sName.equals( jo.getString( "name", null ) ) )
                    return jo;
            }
        }

        return null;
    }

    /**
     * Validates a caller-supplied SQL identifier (table or column name).
     * <p>
     * These names cannot be passed as statement parameters, so they are concatenated into the query
     * verbatim; anything that is not a plain identifier has to be rejected here.
     *
     * @param s The name to validate.
     * @return The same name, when it is a valid identifier.
     * @throws IOException when it is not.
     */
    private static String asIdentifier( String s ) throws IOException
    {
        if( (s == null) || ! ptrnIdentifier.matcher( s ).matches() )
            throw new IOException( "Invalid SQL identifier: "+ s );

        return s;
    }
}
