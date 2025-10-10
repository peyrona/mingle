
package com.peyrona.mingle.glue.gswing;

import com.peyrona.mingle.glue.JTools;
import java.awt.Container;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import javax.swing.JComponent;
import javax.swing.JTable;
import javax.swing.JViewport;
import javax.swing.event.AncestorEvent;
import javax.swing.event.AncestorListener;
import javax.swing.event.ChangeEvent;
import javax.swing.table.TableColumn;
import javax.swing.table.TableColumnModel;

/**
 *
 * @author Francisco José Morero Peyrona
 *
 * Official web site at: <a href="https://github.com/peyrona/mingle">https://github.com/peyrona/mingle</a>
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
        this.model       = model;
        this.anColWidths = anColWidths;

        setModel( model );
        setAutoResizeMode( JTable.AUTO_RESIZE_OFF );
        setCellSelectionEnabled( false );
        setRowSelectionAllowed(  true  );
        setFillsViewportHeight(  true  );
        setFillsViewportHeight(  true  );

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
                    ((JViewport) parent).addChangeListener( (ChangeEvent ce) -> GTable.this.resizeColumns( ce ) );
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
     * Note: this method does NOT verify that all percentages add up to 100% and for
     * the columns to appear properly, it is recommended that the widths for ALL columns be specified.
     *
     * @param ce
     */
    protected void resizeColumns( ChangeEvent ce )
    {
        int              width = ((JComponent) ce.getSource()).getWidth();
        TableColumnModel tcm   = getColumnModel();

        for( int columnIndex = 0; columnIndex < tcm.getColumnCount(); columnIndex++ )
        {
            TableColumn column = tcm.getColumn( columnIndex );
                        column.setPreferredWidth( anColWidths[columnIndex] * width / 100 );
        }
    }
}