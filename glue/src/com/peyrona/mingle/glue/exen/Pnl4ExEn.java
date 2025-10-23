
package com.peyrona.mingle.glue.exen;

import com.peyrona.mingle.glue.exen.commands.PnlAllCmdsSelector;
import com.peyrona.mingle.glue.exen.tables.TabbedPaneTables;
import com.peyrona.mingle.lang.interfaces.commands.IDevice;
import com.peyrona.mingle.lang.interfaces.network.INetClient;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;
import javax.swing.JSplitPane;
import javax.swing.SwingUtilities;

/**
 * Every tab in the TabbedPane is a instance of this class. This panel contains a
 * JSplit with Commands at Top and Tables at Bottom
 *
 * @author Francisco José Morero Peyrona
 *
 * Official web site at: <a href="https://github.com/peyrona/mingle">https://github.com/peyrona/mingle</a>
 */
public final class Pnl4ExEn extends JSplitPane
{
    private final ExEnClient         exenClient   = new ExEnClient();
    private final PnlAllCmdsSelector pnlCommands  = new PnlAllCmdsSelector( exenClient );
    private final TabbedPaneTables   tabbedTables = new TabbedPaneTables( exenClient );

    //------------------------------------------------------------------------//

    public Pnl4ExEn()
    {
        setOpaque( true );
        setResizeWeight( 0.7 );
        setDividerLocation( .7d );
        setOneTouchExpandable(true);
        setContinuousLayout(true);
        setOrientation( JSplitPane.VERTICAL_SPLIT );
        setTopComponent( pnlCommands );
        setBottomComponent( tabbedTables );

        SwingUtilities.invokeLater( () -> setDividerLocation( .7d ) );
    }

    //------------------------------------------------------------------------//

    public void connect( final Consumer<ExEnClient> onConnected )
    {
        exenClient.add( new INetClient.IListener()
                            {
                                @Override
                                public void onConnected( INetClient origin )
                                {
                                    onConnected.accept( exenClient );
                                    exenClient.remove( this );        // This listener is not needed any more
                                }
                                @Override
                                public void onDisconnected(INetClient origin)         { }
                                @Override
                                public void onMessage(INetClient origin, String msg)  { }
                                @Override
                                public void onError(INetClient origin, Exception exc) { }
                            } );

        exenClient.connect();
    }

    public String getUseSourceCode()
    {
        return pnlCommands.getUneSourceCode();
    }

    public void clear()
    {
        pnlCommands.deleteAll();
        tabbedTables.clear();
    }

    public void disconnect()
    {
        exenClient.disconnect();
    }

    public List<IDevice> getDevices()
    {
        return pnlCommands.getDevices();
    }

    public void addExenOutputTab( Process process )
    {
        tabbedTables.addExenOutputTab( process );
    }

 // public void removeExenOutputTab() --> Not needed: when ExEn is stopped, the whole Tab is removed

    @Override
    public int hashCode()
    {
        int hash = 7;
        hash = 89 * hash + Objects.hashCode( exenClient );
        return hash;
    }

    @Override
    public boolean equals(Object obj)
    {
        if( this == obj )
            return true;

        if( obj == null )
            return false;

        if( getClass() != obj.getClass() )
            return false;

        final Pnl4ExEn other = (Pnl4ExEn) obj;

        return Objects.equals( this.exenClient, other.exenClient );
    }
}