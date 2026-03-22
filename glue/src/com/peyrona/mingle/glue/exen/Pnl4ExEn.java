
package com.peyrona.mingle.glue.exen;

import com.peyrona.mingle.glue.ExEnClient;
import com.peyrona.mingle.glue.JTools;
import com.peyrona.mingle.glue.exen.commands.PnlAllCmdsSelector;
import com.peyrona.mingle.glue.exen.tables.TablesTabbedPane;
import com.peyrona.mingle.lang.interfaces.commands.ICommand;
import com.peyrona.mingle.lang.interfaces.commands.IDevice;
import com.peyrona.mingle.lang.japi.ExEnComm;
import java.util.List;
import javax.swing.JSplitPane;
import javax.swing.SwingUtilities;

/**
 * This panel contains a JSplit with Commands at Top and Tables at Bottom.<br>
 * Every tab in the ExEnsTabbedPane class is a instance of this class.
 *
 * @author Francisco José Morero Peyrona
 *
 * Official web site at: <a href="https://github.com/peyrona/mingle">https://github.com/peyrona/mingle</a>
 */
public final class Pnl4ExEn extends JSplitPane
{
    private final ExEnClient         exenClient;
    private final PnlAllCmdsSelector pnlCommands;
    private final TablesTabbedPane   tab4Tables;

    //------------------------------------------------------------------------//

    public Pnl4ExEn( ExEnClient exenCli )
    {
JTools.info( "WARNING!  This is a work in progress", 5 );   // FIXME: hacerlo

        exenClient  = exenCli;
        pnlCommands = new PnlAllCmdsSelector( exenClient );
        tab4Tables  = new TablesTabbedPane(   exenClient );

        setOpaque( true );
        setOneTouchExpandable(true);
        setContinuousLayout(true);
        setOrientation( JSplitPane.VERTICAL_SPLIT );
        setTopComponent( pnlCommands );
        setBottomComponent( tab4Tables );

        SwingUtilities.invokeLater( () -> setDividerLocation( .5d ) );
    }

    //------------------------------------------------------------------------//

    public String getUseSourceCode()
    {
        return pnlCommands.getUneSourceCode();
    }

    public void clear()
    {
        pnlCommands.deleteAll();
        tab4Tables.clear();
    }

    public void add( ICommand... cmd )
    {
        exenClient.sendSetOfCmds( ExEnComm.Request.Add, cmd );
    }

    public void disconnect()
    {
        exenClient.disconnect();
    }

    public List<IDevice> getDevices()
    {
        return pnlCommands.getDevices();
    }
}