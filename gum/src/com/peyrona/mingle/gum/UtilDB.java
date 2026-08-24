
package com.peyrona.mingle.gum;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

/**
 *
 * @author Francisco José Morero Peyrona
 *
 * Official web site at: <a href="https://github.com/peyrona/mingle">https://github.com/peyrona/mingle</a>
 */
public final class UtilDB
{
    //------------------------------------------------------------------------//
    private UtilDB() {}  // Avoid creating instances of this class
    //------------------------------------------------------------------------//

    /**
     * Creates a PreparedStatement with appropriate types and using passed
     * connection.
     *
     * @param connection
     * @param sql
     * @return
     * @throws java.sql.SQLException
     */
    public static PreparedStatement createPreparedStatement( Connection connection, String sql ) throws SQLException
    {
        String cmd = sql.trim().substring( 0,6 ).toLowerCase();

        if( cmd.equals( "select" ) )
        {
            return connection.prepareStatement( sql,
                                                ResultSet.TYPE_FORWARD_ONLY,
                                                ResultSet.CONCUR_READ_ONLY );
        }
        else
        {
            boolean returnPK = cmd.equals( "insert" );

            return connection.prepareStatement( sql,
                                                (returnPK ? Statement.RETURN_GENERATED_KEYS     // Used for INSERT
                                                          : Statement.NO_GENERATED_KEYS) );     // Used for UPDATE
        }
    }

    public static java.sql.Date toSqlDate( long milliseconds )
    {
        return new java.sql.Date( milliseconds );
    }
}
