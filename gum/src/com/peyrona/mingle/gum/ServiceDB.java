
package com.peyrona.mingle.gum;

import com.eclipsesource.json.Json;
import com.eclipsesource.json.JsonArray;
import com.eclipsesource.json.JsonObject;
import com.peyrona.mingle.lang.japi.UtilColls;
import com.peyrona.mingle.lang.japi.UtilIO;
import com.peyrona.mingle.lang.japi.UtilStr;
import com.peyrona.mingle.lang.japi.UtilSys;
import com.peyrona.mingle.lang.japi.UtilUnit;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
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
 * <li>from  : Starting time in milliseconds (optional, default is -1)
 * <li>to    : Ending time in milliseconds (optional, default is -1)
 * <li>jars  : Comma-separated list of JAR files to be loaded at runtime (optional)
 * <li>jdbc  : JDBC driver class name (optional)
 * <li>url   : JDBC URL for the database connection (optional)
 * <li>user  : Database username (optional)
 * <li>pwd   : Database password (optional)
 * <li>tables: JSON array of table information containing table name, timestamp column, and value column
 * </ul>
 *
 * @author Francisco José Morero Peyrona
 * Official web site at: <a href="https://github.com/peyrona/mingle">https://github.com/peyrona/mingle</a>
 */
final class ServiceDB extends ServiceBase
{
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
        long   nFrom   = asLong(   "from"  , -1l  );   // Starting millis
        long   nTo     = asLong(   "to"    , -1l  );   // Ending millis
        String sJARs   = asString( "jars"  , null );   // JARs to be loaded at runtime
        String sDriver = asString( "jdbc"  , null );   // JDBC Driver
        String sURL    = asString( "url"   , null );   // JDBC URL
        String sUser   = asString( "user"  , null );   // User name
        String sPwd    = asString( "pwd"   , null );   // User password
        String sTables = asString( "tables", null );   // Array of tables and columns (a string in JSON format)

        sJARs = UtilIO.replaceFileMacros( sJARs );
        sURL  = UtilIO.replaceFileMacros( sURL  );

        UtilSys.addToClassPath( (String[]) UtilColls.toArray( sJARs ) );
        Class.forName( sDriver );

        try( Connection conn = DriverManager.getConnection( sURL, sUser, sPwd ) )
        {
            java.sql.Date dFrom = UtilDB.toSqlDate( nFrom );
            java.sql.Date dTo   = UtilDB.toSqlDate( nTo   );

            JsonArray     ja = Json.parse( sTables ).asArray();
            StringBuilder sb = new StringBuilder( 4 * UtilUnit.MEGA_BYTE )
                                   .append( '[' );

            for( int n = 0; n < ja.size(); n++ )
            {
                JsonObject jo  = ja.get( n ).asObject();
                String     tbl = jo.getString( "table" , null );
                String     tim = jo.getString( "time"  , null );   // col name having the timestamp
                String     val = jo.getString( "values", null );   // col name containing the values to retrieve
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
                  .append( "\"data\": ").append( new RS2JsonStr( ps.executeQuery() ).getBody() )
                  .append( "}," );
            }

            if( UtilStr.isLastChar( sb, ',' ) )
                sb = UtilStr.removeLast( sb, 1 );

            sendJSON( sb.append( ']' ).toString() );
        }
    }
}