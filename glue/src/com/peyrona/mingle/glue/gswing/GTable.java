
package com.peyrona.mingle.glue.gswing;

import com.peyrona.mingle.glue.JTools;
import com.peyrona.mingle.lang.MingleException;
import java.awt.Container;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import javax.swing.JTable;
import javax.swing.JViewport;
import javax.swing.SwingUtilities;
import javax.swing.event.AncestorEvent;
import javax.swing.event.AncestorListener;
import javax.swing.event.ChangeEvent;
import javax.swing.table.TableColumnModel;

/**
 *
 * @author Francisco José Morero Peyrona
 *
 * Official web site at: <a href="https://github.com/peyrona/mingle">https://github.com/peyrona/mingle</a>
 * @param <T>
 */
public class GTable<T> extends JTable
{
    public  final GTableModel<T> model;
    private       String         sFilter = null;
    private       boolean        bRegExp = false;
    private final int[]          anColWidths;       // The widths of the columns as percentages

    //------------------------------------------------------------------------//

    public GTable( GTableModel<T> model, int... anColWidths )
    {
        if( model == null )
            throw new MingleException( MingleException.INVALID_ARGUMENTS );

        this.model = model;

        setModel( model );
        setAutoResizeMode( JTable.AUTO_RESIZE_SUBSEQUENT_COLUMNS );
        setCellSelectionEnabled( false );
        setRowSelectionAllowed(  true  );
        setFillsViewportHeight(  true  );

        // Verify column widths -----------------------
        int nCols = getColumnModel().getColumnCount();

        if( anColWidths.length > nCols )
            throw new MingleException( "There are more column widths than table columns" );

        if( anColWidths.length < nCols )
        {
            int totalPercent = 0;

            for( int n = 0; n < nCols; n++ )
            {
                anColWidths[n] = Math.abs( anColWidths[n] );
                totalPercent += anColWidths[n];
            }

            anColWidths[ anColWidths.length - 1 ] = 100 - totalPercent;
        }

        this.anColWidths = anColWidths;

        // Events ------------------------------------
        addMouseListener( new MouseAdapter()
        {
            @Override
            public void mousePressed( MouseEvent mouseEvent)
            {
                if( (mouseEvent.getClickCount() == 2) && (GTable.this.getSelectedRow() > -1) )
                    onShowRowDetails();
            }
        } );

        addAncestorListener( new AncestorListener()
        {
            @Override
            public void ancestorAdded(AncestorEvent event)    // Sun missnamed this method: it should be "ancestorShown"
            {
                Container parent = event.getAncestorParent();

                if( parent instanceof JViewport )
                {
                    ((JViewport) parent).addChangeListener( (ChangeEvent ce) -> GTable.this.resizeColumns() );

                    // Also listen to component resize events
                    GTable.this.addComponentListener( new ComponentAdapter()
                    {
                        @Override
                        public void componentResized( ComponentEvent ce )
                        {
                            GTable.this.resizeColumns();
                        }
                    } );

                    // Initial resize
                    GTable.this.resizeColumns();
                }
            }

            @Override
            public void ancestorRemoved(AncestorEvent event)
            {
            }

            @Override
            public void ancestorMoved(AncestorEvent event)
            {
            }
        } );
    }

    //------------------------------------------------------------------------//
    // PUBLIC SCOPE

    public boolean isEmpty()
    {
        return (model.getRowCount() == 0);
    }

    public void empty()
    {
        model.removeAllRows();
    }

    public int getMaxRows()
    {
        return model.getMaxRows();
    }

    public void setMaxRows( int n )
    {
        model.setMaxRows( n );
    }

    public String getFilter()
    {
        return sFilter;
    }

    public boolean isFilterRegExp()
    {
        return bRegExp;
    }

    public void setFilter( String filter, boolean isRegExp )
    {
        sFilter = filter;
        bRegExp = isRegExp;
    }

    //------------------------------------------------------------------------//
    // PROTECTED SCOPE

    protected void onShowRowDetails()
    {
        JTools.info( "No details to show" );
    }

    /**
     * Set the width of the columns as percentages.
     * <p>
     * This method calculates column widths based on the percentage values provided
     * in the constructor. If no percentage is specified for a column, a default
     * percentage is calculated. The method ensures minimum column widths for usability
     * and handles edge cases gracefully.
     *
     * @param ce ChangeEvent from viewport (can be null for direct calls)
     */
    protected void resizeColumns()
    {
        if( anColWidths == null || anColWidths.length == 0 )
            return;

        SwingUtilities.invokeLater( () ->
            {
                int nTotalWidth = 0;

                if( getParent() != null )
                    nTotalWidth = getParent().getWidth();

                if( nTotalWidth <= 0 )
                    return;     // Still no valid width, skip resizing

                TableColumnModel tcm   = getColumnModel();
                int              nCols = tcm.getColumnCount();

                for( int nCol = 0; nCol < nCols; nCol++ )
                {
                    int nColWidth = Math.max( 50, anColWidths[nCol] * nTotalWidth / 100 ); // Minimum 50 pixels

                    tcm.getColumn( nCol ).setPreferredWidth( nColWidth );
                }
            } );
    }
}