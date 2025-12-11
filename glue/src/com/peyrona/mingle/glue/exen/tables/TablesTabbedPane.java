
package com.peyrona.mingle.glue.exen.tables;

import com.peyrona.mingle.glue.ExEnClient;
import com.peyrona.mingle.glue.JTools;
import com.peyrona.mingle.glue.gswing.GTabbedPane;
import java.awt.event.ActionEvent;
import javax.swing.BorderFactory;
import javax.swing.Icon;
import javax.swing.JScrollPane;
import javax.swing.SwingUtilities;
import jiconfont.icons.font_awesome.FontAwesome;
import jiconfont.swing.IconFontSwing;

/**
 * A tabbed pane containing 3 tables: Device's changes, Model's changes and Errors.
 *
 * @author Francisco José Morero Peyrona
 *
 * Official web site at: <a href="https://github.com/peyrona/mingle">https://github.com/peyrona/mingle</a>
 */
public final class TablesTabbedPane extends GTabbedPane
{
    private final TblChanges  tblChanges;
    private final TblEditions tblEditions;
    private final TblErrors   tblErrors;
    private       JScrollPane spOutput;

    //------------------------------------------------------------------------//

    /**
     * Creates new form TabbedPaneTables
     * @param exenClient
     */
    public TablesTabbedPane( ExEnClient exenClient )
    {
        setBorder( BorderFactory.createEmptyBorder( 0, 12, 0, 12 ) );

        Icon icon = IconFontSwing.buildIcon( FontAwesome.COG, 12, JTools.getIconColor() );

        tblChanges  = new TblChanges(  exenClient );
        tblEditions = new TblEditions( exenClient );
        tblErrors   = new TblErrors(   exenClient );
        spOutput    = null;

        addTab( "Changes" , new JScrollPane( tblChanges  ), (ActionEvent evt) -> TableConfigDlg.show( tblChanges , "Filter by \"Device\""  ), icon, "Shows messages triggered when devices change their state" );
        addTab( "Editions", new JScrollPane( tblEditions ), (ActionEvent evt) -> TableConfigDlg.show( tblEditions, "Filter by \"Command\"" ), icon, "Shows only following types of message: List, Add, Remove and Replace" );
        addTab( "Errors"  , new JScrollPane( tblErrors   ), (ActionEvent evt) -> TableConfigDlg.show( tblErrors  , "Filter by \"Error\""   ), icon, "Shows errors reported by the associated ExEn" );

        // This is for the tab itself (previous was for the button)
        setToolTipTextAt( 0, "Shows messages triggered when devices change their state" );
        setToolTipTextAt( 1, "Shows only following types of message: List, Add, Remove and Replace" );
        setToolTipTextAt( 2, "Shows errors reported by the associated ExEn" );

        SwingUtilities.invokeLater( () -> setSelectedIndex( 0 ) );
    }

    //------------------------------------------------------------------------//

    public void clear()
    {
        tblChanges.model.removeAllRows();
        tblEditions.model.removeAllRows();
        tblErrors.model.removeAllRows();
    }
}