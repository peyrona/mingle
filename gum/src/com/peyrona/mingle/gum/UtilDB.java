
package com.peyrona.mingle.gum;

import com.peyrona.mingle.lang.japi.UtilIO;
import com.peyrona.mingle.lang.japi.UtilStr;
import java.awt.Graphics;
import java.awt.Image;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.sql.Blob;
import java.sql.Clob;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.sql.Types;
import java.time.LocalDate;
import java.time.LocalDateTime;
import javax.imageio.ImageIO;
import javax.swing.ImageIcon;

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
     * Creates a Statement using passed connection and following types:
     * <ul>
     *    <li>ResultSet.TYPE_FORWARD_ONLY</li>
     *    <li>ResultSet.CONCUR_READ_ONLY</li>
     * </ul>
     *
     * @param connection
     * @return
     * @throws java.sql.SQLException
     */
    public static Statement createStatement( Connection connection ) throws SQLException
    {
        return connection.createStatement( ResultSet.TYPE_FORWARD_ONLY,
                                           ResultSet.CONCUR_READ_ONLY );
    }

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

    /**
     * Creates a PreparedStatement with appropriate types and using passed
     * connection. This method allows also to set the poolable flag.
     *
     * @param connection
     * @param sql
     * @param poolable
     * @return
     * @throws java.sql.SQLException
     */
    public static PreparedStatement createPreparedStatement( Connection connection, String sql, boolean poolable ) throws SQLException
    {
        PreparedStatement ps = createPreparedStatement( connection, sql );
                          ps.setPoolable( poolable );
        return ps;
    }

    /**
     * Creates a Statement using this connection and executes passed SQL string
     * command that must be and INSERT, UPDATE or APPEND.
     *
     * @param connection
     * @param sCommand
     * @throws java.sql.SQLException
     */
    public static void executeUpdate( Connection connection, String sCommand ) throws SQLException
    {
        try( Statement stmt = createStatement( connection ) )
        {
            stmt.executeUpdate( sCommand );
        }
    }

    /**
     * Set current (default) schema to the passed one.
     *
     * @param connection
     * @param schema The schema to set.
     * @throws java.sql.SQLException
     */
    public static void setSchema( Connection connection, String schema ) throws SQLException
    {
        try( Statement stmt = createStatement( connection ) )
        {
            stmt.execute( "SET SCHEMA "+ schema );
        }
    }

    /**
     *
     * @param dbmd
     * @param sTableName
     * @return
     * @throws SQLException
     */
    public static boolean existTable( DatabaseMetaData dbmd, String sTableName ) throws SQLException
    {
        boolean exists = false;

        sTableName = sTableName.trim().toUpperCase();

        try( ResultSet rs = dbmd.getTables( null, null, null, null ) )
        {
            while( rs.next() )
            {
                if( rs.getString( "TABLE_NAME" ).toUpperCase().equals( sTableName ) )
                {
                    exists = true;
                    break;
                }
            }
        }

        return exists;
    }

    public static java.sql.Timestamp now()
    {
        return new java.sql.Timestamp( System.currentTimeMillis() );
    }

    /**
     * Returns java.sql.Date( System.currentTimeMillis() )
     * @return java.sql.Date( System.currentTimeMillis() )
     */
    public static java.sql.Date today()
    {
        return new java.sql.Date( System.currentTimeMillis() );
    }

    public static java.sql.Date toSqlDate( Long milliseconds )
    {
        return (milliseconds == null) ? null : toSqlDate( milliseconds.longValue() );
    }

    public static java.sql.Date toSqlDate( long milliseconds )
    {
        return new java.sql.Date( milliseconds );
    }

    public static java.sql.Date toSqlDate( LocalDate date )
    {
        return java.sql.Date.valueOf( date );
    }

    public static java.sql.Date toSqlDate( LocalDateTime datetime )
    {
        return java.sql.Date.valueOf( datetime.toLocalDate() );
    }

    //----------------------------------------------------------------------------//
    // OPERATIONS ABOUT CLOBS & BLOBS

    public static String getClob( ResultSet rs, String columnName ) throws SQLException
    {
        return UtilDB.getClob( rs, rs.findColumn( columnName ) );
    }

    public static String getClob( ResultSet rs, int columnIndex ) throws SQLException
    {
        Clob   clob = rs.getClob( columnIndex );
        String sRet = null;

        if( clob != null )
        {
            sRet = clob.getSubString( 1, (int) clob.length() );
            clob.free();
        }

        return sRet;
    }

    public static void setClob( int index, PreparedStatement ps, String content ) throws SQLException
    {
        if( UtilStr.isEmpty( content ) )
        {
            ps.setClob( index, (Clob) null );
        }
        else
        {
            ps.setClob( index, UtilIO.fromStringToReader( content ), content.length() );
        }
    }

    /**
     *
     * @param rs
     * @param column
     * @return
     * @throws SQLException
     */
    public static Image getImageFromBlob( ResultSet rs, String column ) throws SQLException
    {
        byte[] binImage = getBlob( rs, column );

        return ((binImage == null) ? null : new ImageIcon( binImage ).getImage());
    }

    /**
     *
     * @param column
     * @param psmt
     * @param image
     * @throws SQLException
     * @throws IOException
     */
    public static void setImageInBlob( int column, PreparedStatement psmt, Image image ) throws SQLException, IOException
    {
        ImageIcon icon = null;

        if( image != null )
        {
            icon = new ImageIcon( image );
        }

        setBlob( column, psmt, imageToBytes( icon ) );
    }

    /**
     *
     * @param rs
     * @param column
     * @return
     * @throws SQLException
     */
    public static byte[] getBlob( ResultSet rs, String column ) throws SQLException
    {
        byte[] ret  = null;
        Blob   blob = rs.getBlob( column );

        if( blob != null )
        {
            try
            {
                long length = blob.length();

                if( length == 0 )    // Cuando (length == 0) => returns byte[0] en lugar de null
                {
                    ret = new byte[0];
                }
                else
                {
                    ret = blob.getBytes( 1, (int) length );
                }
            }
            finally
            {
                blob.free();
            }
        }

        return ret;
    }

    /**
     * Adds to passed PrepparedStatement (ps.setXXX()) passed byte array.
     * @param index
     * @param ps
     * @param data
     * @throws SQLException
     */
    public static void setBlob( int index, PreparedStatement ps, byte[] data ) throws SQLException
    {
        ByteArrayInputStream bais = null;
        int                  len  = 0;

        if( data != null )
        {
            bais = new ByteArrayInputStream( data );
            len  = data.length;
        }

        ps.setBinaryStream( index, bais, len );
    }

    /**
     *
     * @param icon
     * @return
     * @throws IOException
     */
    public static byte[] imageToBytes( ImageIcon icon ) throws IOException
    {
        if( icon == null )
        {
            return null;
        }

        Image img = icon.getImage();

        if( img.getWidth( null ) < 1 && img.getHeight( null ) < 1 )
        {
            return null;
        }

        BufferedImage         image = getBufferedImageFromImage( img );
        ByteArrayOutputStream baos  = new ByteArrayOutputStream();

        ImageIO.write( image, "png", baos );

        return baos.toByteArray();
    }

    /**
     * Returns the value for the column designated by received parameters.
     * <br>
     * Different RDBMS interpret the same Type constant (v.g. 'Types.REAL') in different ways.
     * Because of that, thje worst scenario has to be taken (the biggest Java data type capacity).
     *
     * @param rs
     * @param nCol
     * @param nType
     * @param is4JSON true when the result has to be JSON compatible.
     * @return
     * @throws SQLException
     */
    public static Object readCol( ResultSet rs, int nCol, int nType, boolean is4JSON ) throws SQLException
    {
        switch( nType )
        {
            case Types.NULL:
                return null;

            case Types.BIT:
            case Types.BOOLEAN:
                return rs.getBoolean( nCol );

            case Types.SMALLINT:
            case Types.INTEGER:
            case Types.TINYINT:
                return rs.getInt( nCol );

            case Types.BIGINT:
                return rs.getLong( nCol );

            case Types.FLOAT:
            case Types.REAL:
            case Types.DOUBLE:
                return rs.getDouble( nCol );

            case Types.NUMERIC:
            case Types.DECIMAL:
                BigDecimal bd = rs.getBigDecimal( nCol );
                return ((bd == null) ? null : (is4JSON ? bd.doubleValue() : bd ));

            case Types.DATE:
            case Types.TIME:
            case Types.TIMESTAMP:
                Timestamp ts = rs.getTimestamp( nCol );
                return ((ts == null) ? null : (is4JSON ? ts.getTime() : ts ));

            case Types.CHAR:
            case Types.LONGVARCHAR:
            case Types.NCHAR:
            case Types.NVARCHAR:
            case Types.VARCHAR:
                return rs.getString( nCol );

            case Types.CLOB:
                return getClob( rs, nCol );
        }

        throw new SQLException( "Type #"+ nType +": is a not yet supported SQL type" );
    }

    //----------------------------------------------------------------------------//

    private static BufferedImage getBufferedImageFromImage( Image img )
    {
        // Crea un objeto BufferedImage con el ancho y alto de la Image
        BufferedImage bufferedImage = new BufferedImage( img.getWidth( null ), img.getHeight( null ),
                                                         BufferedImage.TYPE_INT_RGB );
        Graphics g = bufferedImage.createGraphics();
                 g.drawImage( img, 0, 0, null );
                 g.dispose();

        return bufferedImage;
    }
}