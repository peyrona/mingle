
package com.peyrona.mingle.glue.gswing;

import com.peyrona.mingle.lang.japi.UtilColls;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;
import javax.swing.table.AbstractTableModel;

/**
 * This table model inserts new rows at begining of the table and it can
 * keep the table not bigger than certain number of rows.
 *
 * @author Francisco José Morero Peyrona
 *
 * Official web site at: <a href="https://github.com/peyrona/mingle">https://github.com/peyrona/mingle</a>
 * @param <T>
 */
public abstract class GTableModel<T>
       extends AbstractTableModel
{
    private final String[]            columnNames;
    private final List<T>             lstRows  = new ArrayList<>();  // Pair.getKey() == true --> visible row
    private       int                 nMaxRows = 200;                // 0 or lower == infinite
    private       Function<T,Boolean> fnFilter = null;               // To detect visible rows
    private final Object              locker   = new Object();

    //------------------------------------------------------------------------//

    protected GTableModel( String... columnNames )
    {
        this.columnNames = columnNames;
    }

    //------------------------------------------------------------------------//

    @Override
    public int getRowCount()
    {
        return lstRows.size();
    }

    @Override
    public int getColumnCount()
    {
        return columnNames.length;
    }

    @Override
    public String getColumnName( int column )
    {
        return columnNames[column];
    }

    @Override
    public abstract Class getColumnClass( int column );

    //------------------------------------------------------------------------//
    // ADDED PUBLIC API

    public int getMaxRows()
    {
        return nMaxRows;
    }

    public GTableModel<T> setMaxRows( int max )
    {
        if( nMaxRows == max )
            return this;

        if( max <= 0 )
        {
            nMaxRows = 0;      // Atomic
        }
        else
        {
            int size = lstRows.size();

            synchronized( locker )
            {
                while( lstRows.size() > max )    // Now remove the rest until size is appropriate
                    UtilColls.removeTail( lstRows );
            }

            if( size > lstRows.size() )
                fireTableRowsDeleted( lstRows.size() - 1, size - 1 );

            nMaxRows = max;    // Atomic
        }

        return this;
    }

    public GTableModel<T> addRow( T row )
    {
        boolean isVisible = (fnFilter == null) ? true : fnFilter.apply( row );

        if( isVisible )
        {
            lstRows.add( 0, row );

            if( (nMaxRows > 0) && (getRowCount() >= nMaxRows) )
            {
                synchronized( locker )
                {
                    UtilColls.removeTail( lstRows );
                }

                fireTableRowsDeleted( lstRows.size() - 1, lstRows.size() - 1 );
            }

            fireTableRowsInserted( 0, 0 );
        }

        return this;
    }

    public GTableModel<T> removeAllRows()
    {
        synchronized( locker )
        {
            lstRows.clear();
        }

        fireTableDataChanged();
        return this;
    }

    public T getRow( int ndx )
    {
        return lstRows.get( ndx );
    }

    public GTableModel<T> setFilter( Function<T,Boolean> fnFilter )
    {
        if( Objects.equals( fnFilter, this.fnFilter ) )
            return this;

        this.fnFilter = fnFilter;

        if( fnFilter != null )
        {
            synchronized( locker )
            {
                for( Iterator<T> itera = lstRows.iterator(); itera.hasNext(); )
                    if( ! fnFilter.apply( itera.next() ) )
                        itera.remove();
            }
        }

        fireTableDataChanged();

        return this;
    }
}