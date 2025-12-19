
package com.peyrona.mingle.gum;

import com.eclipsesource.json.Json;
import com.eclipsesource.json.JsonArray;
import com.eclipsesource.json.JsonObject;
import com.eclipsesource.json.JsonValue;
import com.peyrona.mingle.lang.MingleException;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Types;
import java.util.HashSet;
import java.util.Set;

/**
 * Utility to convert a JDBC ResultSet into a standard JSON structure.
 *
 * <p>Output Format:
 * <pre>
 * {
 * "head": ["ColName_1", "ColName_2", ... ],
 * "body": [
 * [ Value_1_1, Value_1_2, ... ],
 * [ Value_2_1, Value_2_2, ... ]
 * ]
 * }
 * </pre>
 *
 * @author Francisco José Morero Peyrona
 */
public final class RS2Json
{
    /**
     * Converts a ResultSet to a JSON string representation.
     *
     * @param rs The JDBC ResultSet to process.
     * @param jsonCols A list of column names that contain raw JSON strings (optional).
     * If a column name is in this list, its content is parsed as JSON
     * rather than treated as a string literal.
     * @return A string containing the JSON object.
     * @throws MingleException If SQL or JSON errors occur.
     */
    public static JsonObject toJson( ResultSet rs )
    {
        try
        {
            ResultSetMetaData rsmd    = rs.getMetaData();
            int               nCols   = rsmd.getColumnCount();
            Set<String>       jFields = new HashSet<>();

            // 1. Build Header
            JsonArray headArray = new JsonArray();

            for( int n = 1; n <= nCols; n++ )
                headArray.add( rsmd.getColumnLabel( n ) );

            // 2. Build Body
            JsonArray bodyArray = new JsonArray();

            while( rs.next() )
            {
                JsonArray rowArray = new JsonArray();

                for( int i = 1; i <= nCols; i++ )
                    rowArray.add( extractColumnValue( rs, rsmd, i, jFields ) );

                bodyArray.add( rowArray );
            }

            // 3. Assemble Result
            JsonObject root = new JsonObject();
            root.add( "head", headArray );
            root.add( "body", bodyArray );

            return root;
        }
        catch( SQLException e )
        {
            throw new MingleException( "Error converting ResultSet to JSON", e );
        }
    }

    //------------------------------------------------------------------------//

    private RS2Json()
    {
        // Prevent instantiation of utility class
    }

    /**
     * Extracts a value from the ResultSet and converts it to the appropriate JsonValue.
     */
    private static JsonValue extractColumnValue( ResultSet rs, ResultSetMetaData rsmd, int colIndex, Set<String> jsonFields )
        throws SQLException
    {
        int    colType  = rsmd.getColumnType( colIndex );
        String colName  = rsmd.getColumnLabel( colIndex );

        // 1. Check for specific "Already JSON" columns first
        if( jsonFields.contains( colName ) )
        {
            String rawJson = rs.getString( colIndex );
            return (rawJson == null || rawJson.isBlank()) ? Json.NULL : Json.parse( rawJson );
        }

        // 2. Map JDBC Types to JSON Types
        // Note: checking wasNull() is crucial for primitive getters (int, double, boolean)

        switch( colType )
        {
            case Types.INTEGER:
            case Types.TINYINT:
            case Types.SMALLINT:
            {
                int val = rs.getInt( colIndex );
                return rs.wasNull() ? Json.NULL : Json.value( val );
            }

            case Types.BIGINT:
            {
                long val = rs.getLong( colIndex );
                return rs.wasNull() ? Json.NULL : Json.value( val );
            }

            case Types.FLOAT:
            case Types.REAL:
            case Types.DOUBLE:
            {
                double val = rs.getDouble( colIndex );
                return rs.wasNull() ? Json.NULL : Json.value( val );
            }

            case Types.DECIMAL:
            case Types.NUMERIC:
            {
                // BigDecimal is safer for currency/precision
                java.math.BigDecimal val = rs.getBigDecimal( colIndex );
                // Minimal JSON treats big numbers as strings or requires specific handling.
                // Using double is usually safe for display, but passing as String
                // preserves precision if the client handles it.
                // Here we stick to standard JSON number (double) behavior:
                return val == null ? Json.NULL : Json.value( val.doubleValue() );
            }

            case Types.BOOLEAN:
            case Types.BIT:
            {
                boolean val = rs.getBoolean( colIndex );
                return rs.wasNull() ? Json.NULL : Json.value( val );
            }

            case Types.VARCHAR:
            case Types.CHAR:
            case Types.LONGVARCHAR:
            case Types.CLOB:
            {
                String val = rs.getString( colIndex );
                return val == null ? Json.NULL : Json.value( val );
            }

            default:
            {
                // Fallback for Dates, Timestamps, Blobs, etc. -> String
                Object val = rs.getObject( colIndex );
                return val == null ? Json.NULL : Json.value( val.toString() );
            }
        }
    }
}