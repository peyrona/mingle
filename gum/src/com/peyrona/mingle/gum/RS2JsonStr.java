/*
 * Copyright (C) 2015 Francisco José Morero Peyrona. All Rights Reserved.
 *
 * Peyrona Commons is a set of basic classes that the author created to make
 * easier the development of other software.
 *
 * It is free software; you can redistribute it and/or modify it under the
 * terms of the GNU General Public License as published by the free Software
 * Foundation; either version 3, or (at your option) any later version.
 *
 * This app is distributed in the hope that it will be useful, but WITHOUT ANY
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR
 * A PARTICULAR PURPOSE. See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with
 * this software; see the file COPYING.  If not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA 02110-1301 USA.
 */

package com.peyrona.mingle.gum;

import com.peyrona.mingle.lang.japi.UtilStr;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.List;

/**
  * Creates a welformed JSON string from passed ResultSet: the JSON is an array
  * with two elements, being first element an array of Strings containing the
  * column names and being second element an array which contais as many
  * elements as rows were in the ResultSet, being rows elements the columns'
  * values.
  * <p>
  * As an example
  * <pre>
  *    { "head": ["ColName_1", "ColName_2", ... , "ColName_N" ],
  *      "body":[["Col_1_Val", "Col_2_Val", ... , "Col_N_Val" ],    Row 1
  *              ["Col_1_Val", "Col_2_Val", ... , "Col_N_Val" ],    Row 2
  *              ...........
  *              ["Col_1_Val", "Col_2_Val", ... , "Col_1_Val" ]] }  Row N
  * </pre>
  */
public final class RS2JsonStr
{
    private final ResultSet         resultSet;
    private final ResultSetMetaData rsmd;
    private final List<String>      lstJsonCols;

    //----------------------------------------------------------------------------//

    /**
     * Constructor.
     *
     * @param rs ResultSet to convert into JSON string.
     * @throws java.sql.SQLException
     */
    public RS2JsonStr( ResultSet rs ) throws SQLException
    {
        this( rs, (String[]) null );
    }

    /**
     * Constructor.
     *
     * @param rs        ResultSet to convert into JSON string. It will remain open after the
     *                  process is finished: it is caller responsability to close it.
     * @param colNameWithJsonData All column's name for the columsn that contain JSON data:
     *                  they have to be identified in order to be trated properly.
     * @throws java.sql.SQLException
     */
    public RS2JsonStr( ResultSet rs, String... colNameWithJsonData ) throws SQLException
    {
        this.resultSet   = rs;
        this.rsmd        = rs.getMetaData();
        this.lstJsonCols = (colNameWithJsonData == null || colNameWithJsonData.length == 0) ? null : Arrays.asList( colNameWithJsonData );

        if( this.lstJsonCols != null )
        {
            this.lstJsonCols.replaceAll( String::toUpperCase );      // Col names are used by this class in uppercase
        }
    }

    //----------------------------------------------------------------------------//

    /**
     * Returns head and body.
     *
     * @return "{\"head\": "+ getHead() +", \"body\": "+ getBody() +'}';
     * @throws SQLException
     */
    public String getAll() throws SQLException
    {
        return "{\"head\": "+ getHead() +", \"body\": "+ getBody() +'}';
    }

    /**
     * Returns the JSON representation of an array containing the columns name.
     *
     * @return A JSON representation of an array containing the columns name.
     * @throws SQLException
     */
    public String getHead() throws SQLException
    {
        StringBuilder sbHead = new StringBuilder( 1024 * 2 );
        int           cols   = rsmd.getColumnCount();

        // Añado los nombres de las columnas
        sbHead.append( '[' );

        for( int n = 1; n <= cols; n++ )
        {
            sbHead.append( '\"' ).append( rsmd.getColumnLabel( n ) ).append( "\"," );  // Col Label para q use el AS si lo tiene
        }

        sbHead.deleteCharAt( sbHead.lastIndexOf( "," ) );
        sbHead.append( ']' );

        return sbHead.toString();
    }

    /**
     * Returns rows only (not head).
     *
     * @return Rows only (not head).
     * @throws SQLException
     */
    public String getBody() throws SQLException
    {
        if( resultSet.next() == false )
        {
            return "[]";
        }

        StringBuilder sbRows = new StringBuilder( 1024 * 64 );
        int           cols   = rsmd.getColumnCount();
        int[]         anType = new int[ cols ];          // Cargo los tipos de las columnas
        boolean[]     abJson = new boolean[ cols ];      // Para saber si una col tiene o no JSON data

        // Relleno los arrays para ir luego más rápido
        for( int n = 0; n < cols; n++ )
        {
            anType[n] = rsmd.getColumnType( n + 1 );
            abJson[n] = ((lstJsonCols == null) ? false : lstJsonCols.contains( rsmd.getColumnLabel( n+1 ).toUpperCase() ));
        }

        // Añado todas las columnas para cada fila
        sbRows.append( '[' );

        do                              // I have to do a do...while because I already made a resultSet.next(), and I can
        {                               // not do a resultSet.beforeFirst() bacause I use (for speed) not scrollable RS
            sbRows.append( '[' );

            for( int n = 0; n < cols; n++ )
            {
                appendValue( sbRows, resultSet, n+1, anType[n], abJson[n] );
                sbRows.append( ',' );
            }

            sbRows.deleteCharAt( sbRows.lastIndexOf( "," ) );
            sbRows.append( ']' ).append( ',' );
        } while( resultSet.next() );

        if( sbRows.length() > 1 )     // Puede q el ResultSet estuviera vacío ( > 1 pq comienza añadiendo '[' )
        {
            UtilStr.removeLast( sbRows, 1 );
        }

        sbRows.append( ']' );

        return sbRows.toString();
    }

    //----------------------------------------------------------------------------//

    /**
     * This method is just an instrumental one to make code clearer.
     *
     * @param   sbRows
     * @param   rs
     * @param   nCol
     * @param   nType
     * @boolean bJsonData true if the column is already stored in JSON format
     * @throws SQLException
     */
    private void appendValue( StringBuilder sbRows, ResultSet rs, int nCol, int nType, boolean bJsonData ) throws SQLException
    {
        Object obj = UtilDB.readCol( rs, nCol, nType, true );

        if( obj instanceof String )
        {
            String s = (String) obj;

// SELL: Los datos siempre se envían desde el server hasta el client como JSON, así que es necesario escapar (doblemente) todos los caracteres
//       especiales, pero esto hay que hacerlo una y otra vez en el server (cada vez que se pide un dato): lo suyo es enviar los datos desde el
//       client ya "escapados". Una vez que yo haya hecho eso, hay que recorrer todos los campos VARCHAR de todas las tablas y hacer doble-escape.

            if( bJsonData )  sbRows.append( s );
            else             sbRows.append( '"' )
                                   .append( s.replaceAll( "\\n", "\\\\n" ).replace( '"', '\'' ) )
                                   .append( '"' );
        }
        else
        {
            sbRows.append( obj );      // Note: null is appended as "null"
        }
    }
}