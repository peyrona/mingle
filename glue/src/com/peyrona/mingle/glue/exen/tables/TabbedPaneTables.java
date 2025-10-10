
package com.peyrona.mingle.glue.exen.tables;

import com.peyrona.mingle.glue.ConsolePanel;
import com.peyrona.mingle.glue.JTools;
import com.peyrona.mingle.glue.Util;
import com.peyrona.mingle.glue.exen.ExEnClient;
import com.peyrona.mingle.glue.gswing.GTabbedPane;
import java.awt.event.ActionEvent;
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
public final class TabbedPaneTables extends GTabbedPane
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
    public TabbedPaneTables( ExEnClient exenClient )
    {
        Icon icon = IconFontSwing.buildIcon( FontAwesome.COG, 12, JTools.getIconColor() );

        tblChanges  = new TblChanges(  exenClient );
        tblEditions = new TblEditions( exenClient );
        tblErrors   = new TblErrors(   exenClient );
        spOutput    = null;

        addTab( "Changes" , new JScrollPane( tblChanges  ), (ActionEvent evt) -> DlgTableConfig.show( tblChanges , "Filter by \"Device\""  ), icon, "Shows messages triggered when devices change their state" );
        addTab( "Editions", new JScrollPane( tblEditions ), (ActionEvent evt) -> DlgTableConfig.show( tblEditions, "Filter by \"Command\"" ), icon, "Shows only following types of message: List, Add, Remove and Replace" );
        addTab( "Errors"  , new JScrollPane( tblErrors   ), (ActionEvent evt) -> DlgTableConfig.show( tblErrors  , "Filter by \"Error\""   ), icon, "Shows errors reported by the associated ExEn" );

        // This is for the tab itself (previous was for the button)
        setToolTipTextAt( 0, "Shows messages triggered when devices change their state" );
        setToolTipTextAt( 1, "Shows only following types of message: List, Add, Remove and Replace" );
        setToolTipTextAt( 2, "Shows errors reported by the associated ExEn" );

        SwingUtilities.invokeLater( () -> setSelectedIndex( 0 ) );
    }

    //------------------------------------------------------------------------//

    public void clean()
    {
        tblChanges.model.removeAllRows();
        tblEditions.model.removeAllRows();
        tblErrors.model.removeAllRows();
    }

    public void addExenOutputTab( Process process )
    {
        final Icon         icon = IconFontSwing.buildIcon( FontAwesome.ERASER, 12, JTools.getIconColor() );
        final ConsolePanel cp   = new ConsolePanel();

        spOutput = new JScrollPane( cp );

        addTab( "ExEn output",
                spOutput,
                (ActionEvent evt) -> { ((ConsolePanel) spOutput.getViewport().getView()).clear(); },
                icon,
                "Execution Environment Output" );

        Util.catchOutput( process, (Character ch) -> cp.append( ch ) );

        SwingUtilities.invokeLater( () -> setSelectedIndex( getTabCount() - 1 ) );
    }

 // public void removeExenOutputTab() --> Not needed: when ExEn is stopped, the whole ExEn Tab is removed
}