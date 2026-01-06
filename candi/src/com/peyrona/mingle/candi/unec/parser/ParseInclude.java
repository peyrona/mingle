
package com.peyrona.mingle.candi.unec.parser;

import com.peyrona.mingle.lang.interfaces.ICandi;
import com.peyrona.mingle.lang.japi.UtilIO;
import com.peyrona.mingle.lang.japi.UtilStr;
import com.peyrona.mingle.lang.lexer.Lexeme;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;

/**
 * This class represents a transpiled INCLUDE command.
 *
 * @author Francisco José Morero Peyrona
 *
 * Official web site at: <a href="https://github.com/peyrona/mingle">https://github.com/peyrona/mingle</a>
 */
public final class ParseInclude extends ParseBase
{
    private final static String       sKEY = "INCLUDE";
    private final        String[]     asURIs;
    private final        List<Lexeme> lexemes;
    private final        boolean      bAutoIncs;   // Discover automatically which INCLUDEs are needed
    private final        UseAsTable   useTable;    // Table-like USE...AS for parameterized includes

    //------------------------------------------------------------------------//
    // STATIC INTERFACE

    /**
     * Returns true if passed source Une code is a 'INCLUDE' command.<br>
     * The whole syntax is not checked, therefore the command could have errors.
     *
     * @param source Une source code.
     * @return
     */
    public static boolean is( String source )
    {
        return UtilStr.startsWith( source, sKEY );
    }

    //------------------------------------------------------------------------//

    public ParseInclude( List<Lexeme> lexemes )
    {
        super();

        boolean  bAuto = false;
        UseAsTable table = null;

        this.lexemes = lexemes;

        if( lexemes.size() < 2 )     // "INCLUDE" itself is the first token in the List
        {
            addError( '"'+ sKEY +"\" needs one or more URI(s) or wildcards.", lexemes.get(0) );
            asURIs = null;
        }
        else
        {
            // Find where USE clause starts (if present)
            int useIndex = findUseKeyword( lexemes );

            // Extract URIs (everything between INCLUDE and USE, or to the end if no USE)
            int endIndex = (useIndex > 0) ? useIndex : lexemes.size();
            List<Lexeme> lstURIs = new ArrayList<>( lexemes.subList( 1, endIndex ) );     // Not include 1st token: 'INCLUDE' lexeme

            for( ListIterator<Lexeme> itera = lstURIs.listIterator(); itera.hasNext(); )
            {
                Lexeme lex = itera.next();

                if( lex.isString() && lex.text().trim().equals( "*" ) )
                {
                    itera.remove();
                    bAuto = true;
                }
            }

            asURIs = expandURIs( lstURIs );

            if( useIndex > 0 )    // Parse USE table if present
                table = parseUseTable( lexemes, useIndex );
        }

        bAutoIncs = bAuto;
        useTable  = table;
    }

    //------------------------------------------------------------------------//
    // PUBLIC INTERFACE

    public boolean autoInclude()
    {
        return bAutoIncs;
    }

    public String[] getURIs()
    {
        return Arrays.copyOf( asURIs, asURIs.length );     // Defensive copy
    }

    /**
     * Returns true if this INCLUDE has a USE ... AS ... table defined.
     *
     * @return true if USE table is present.
     */
    public boolean hasUseAs()
    {
        return useTable != null && ! useTable.isEmpty();
    }

    /**
     * Returns the USE table for parameterized includes.
     *
     * @return The UseAsTable, or null if not defined.
     */
    public UseAsTable getUseTable()
    {
        return useTable;
    }

    public ICandi.IError addErrorLoadingURI( String sURI, String err )
    {
        for( Lexeme token : lexemes )
        {
            try
            {
                List<URI> list = UtilIO.expandPath( token.text() );

                for( URI uri : list )
                {
                    if( sURI.equals( uri.toString() ) )
                        return addError( err, token );
                }
            }
            catch( IOException | URISyntaxException ioe )
            {
                // Nothing to do
            }
        }

        return addError( err, getStart() );
    }

    @Override
    public String serialize()
    {
        return null;
    }

    //------------------------------------------------------------------------//
    // PROTECTED INTERFACE

    //------------------------------------------------------------------------//
    // PRIVATE INTERFACE

    /**
     * Finds the index of the USE keyword in the lexeme list.
     *
     * @param lexemes The list of lexemes.
     * @return The index of USE keyword, or -1 if not found.
     */
    private int findUseKeyword( List<Lexeme> lexemes )
    {
        for( int i = 1; i < lexemes.size(); i++ )     // Start at 1 to skip INCLUDE keyword
        {
            Lexeme lex = lexemes.get( i );

            if( lex.isCommandWord() && lex.isText( "USE" ) )
                return i;
        }

        return -1;
    }

    /**
     * Parses a USE table from the lexeme list starting at the USE keyword.
     * <p>
     * Table syntax:
     * <pre>
     * USE col1, col2, ... AS
     *     val1a, val2a, ...
     *     val1b, val2b, ...
     * </pre>
     * Columns are comma-separated, rows are separated by newline or semicolon.
     *
     * @param lexemes  The complete lexeme list.
     * @param useIndex The index of the USE keyword.
     * @return Parsed UseAsTable, or empty table on errors.
     */
    private UseAsTable parseUseTable( List<Lexeme> lexemes, int useIndex )
    {
        List<String>              columns = new ArrayList<>();
        List<List<UseAsTable.Value>> rows   = new ArrayList<>();

        int n = useIndex + 1;     // Start after USE keyword

        // Phase 1: Parse column names (comma-separated until AS)
        while( n < lexemes.size() )
        {
            Lexeme lex = lexemes.get( n );

            if( lex.isText( "AS" ) )
            {
                n++;     // Skip AS
                break;
            }

            if( lex.isText( "," ) || lex.isDelimiter() )
            {
                n++;     // Skip comma or newline (AS can be on next line)
                continue;
            }

            if( lex.isName() || lex.isString() )
            {
                columns.add( lex.text() );
                n++;
            }
            else
            {
                addError( "Expected column name in USE clause", lex );
                n++;
            }
        }

        if( columns.isEmpty() )
        {
            addError( "USE clause requires at least one column name", lexemes.get( useIndex ) );
            return new UseAsTable( columns, rows );
        }

        // Phase 2: Parse data rows
        List<UseAsTable.Value> currentRow = new ArrayList<>();

        while( n < lexemes.size() )
        {
            Lexeme lex = lexemes.get( n );

            // Row separator: semicolon or newline (delimiter)
            if( lex.isText( ";" ) || lex.isDelimiter() )
            {
                if( ! currentRow.isEmpty() )
                {
                    if( currentRow.size() != columns.size() )
                    {
                        addError( "Row has " + currentRow.size() + " values, expected " + columns.size(), lex );
                    }

                    rows.add( new ArrayList<>( currentRow ) );
                    currentRow.clear();
                }

                n++;
                continue;
            }

            // Value separator: comma
            if( lex.isText( "," ) )
            {
                n++;
                continue;
            }

            // Value: string, number, or name
            if( lex.isString() || lex.isNumber() || lex.isName() )
            {
                currentRow.add(new UseAsTable.Value( lex.text(), lex.isString() ) );
                n++;
            }
            else
            {
                addError( "Unexpected token in USE clause data row", lex );
                n++;
            }
        }

        // Don't forget last row if not terminated by delimiter
        if( ! currentRow.isEmpty() )
        {
            if( currentRow.size() != columns.size() )
            {
                addError( "Row has " + currentRow.size() + " values, expected " + columns.size(),
                          lexemes.get( lexemes.size() - 1 ) );
            }

            rows.add( currentRow );
        }

        return new UseAsTable( columns, rows );
    }

    //------------------------------------------------------------------------//
    // INNER CLASS
    //------------------------------------------------------------------------//

    /**
     * Represents a table of USE...AS parameters for parameterized includes.
     * <p>
     * Table syntax:
     * <pre>
     * USE col1, col2, ... AS
     *     val1a, val2a, ...
     *     val1b, val2b, ...
     * </pre>
     * Each row represents one instantiation of the included file.
     */
    public static final class UseAsTable
    {
        private final List<String>       columns;
        private final List<List<Value>>  rows;

        UseAsTable( List<String> columns, List<List<Value>> rows )
        {
            this.columns = Collections.unmodifiableList( new ArrayList<>( columns ) );
            this.rows    = Collections.unmodifiableList( rows );
        }

        /**
         * Returns true if the table has no rows.
         *
         * @return true if empty.
         */
        public boolean isEmpty()
        {
            return rows.isEmpty();
        }

        /**
         * Returns the column names.
         *
         * @return Unmodifiable list of column names.
         */
        public List<String> getColumns()
        {
            return columns;
        }

        /**
         * Returns the number of instantiations (rows).
         *
         * @return Number of rows.
         */
        public int getRowCount()
        {
            return rows.size();
        }

        /**
         * Returns a row as a map of column name to value.
         * Convenient for applying substitutions.
         *
         * @param rowIndex The row index (0-based).
         * @return Map of column name to Value.
         */
        public Map<String, Value> getRowAsMap( int rowIndex )
        {
            List<Value>       row    = rows.get( rowIndex );
            Map<String,Value> result = new LinkedHashMap<>();

            for( int i = 0; i < columns.size(); i++ )
                result.put( columns.get( i ), row.get( i ) );

            return result;
        }

        @Override
        public String toString()
        {
            StringBuilder sb = new StringBuilder();

            sb.append( "USE " );
            sb.append( String.join( ", ", columns ) );
            sb.append( " AS\n" );

            for( List<Value> row : rows )
            {
                sb.append( "    " );

                for( int i = 0; i < row.size(); i++ )
                {
                    if( i > 0 )
                        sb.append( ", " );

                    sb.append( row.get( i ) );
                }

                sb.append( "\n" );
            }

            return sb.toString();
        }

        //--------------------------------------------------------------------//

        /**
         * Represents a single value in the USE table.
         */
        public static final class Value
        {
            private final String  text;
            private final boolean isString;     // true if value was a quoted string

            public Value( String text, boolean isString )
            {
                this.text     = text;
                this.isString = isString;
            }

            /**
             * Returns the value for use in macro replacement (inside strings).
             * Never includes quotes.
             *
             * @return The raw text value.
             */
            public String getText()
            {
                return text;
            }

            /**
             * Returns the value for use in identifier replacement (bare symbols).
             * Includes quotes if the original was a string.
             *
             * @return The quoted value if string, raw text otherwise.
             */
            public String getQuotedText()
            {
                return isString ? ('"' + text + '"') : text;
            }

            /**
             * Returns true if this value was a quoted string.
             *
             * @return true if string.
             */
            public boolean isString()
            {
                return isString;
            }

            @Override
            public String toString()
            {
                return isString ? ('"' + text + '"') : text;
            }
        }
    }
}