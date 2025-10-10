
package com.peyrona.mingle.glue.exen.tables;

import com.peyrona.mingle.glue.exen.ExEnClient;
import com.peyrona.mingle.glue.gswing.GTable;
import com.peyrona.mingle.glue.gswing.GTableModel;
import com.peyrona.mingle.lang.interfaces.network.INetClient;
import com.peyrona.mingle.lang.japi.ExEnComm;
import com.peyrona.mingle.lang.japi.Pair;
import com.peyrona.mingle.lang.japi.UtilStr;
import com.peyrona.mingle.lang.japi.UtilSys;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.regex.Pattern;

/**
 * Shows messages triggered when a device changes its state.
 *
 * @author Francisco José Morero Peyrona
 *
 * Official web site at: <a href="https://github.com/peyrona/mingle">https://github.com/peyrona/mingle</a>
 */
final class TblChanges
      extends GTable<ExEnComm>
{
    TblChanges( ExEnClient exenClient )
    {
        super( new ThisTableModel(), 10, 10, 15, 65 );

        exenClient.add( new ClientListener() );
    }

    //------------------------------------------------------------------------//

    @Override
    public void setFilter( String sDevName, boolean isRegExp )
    {
        super.setFilter( sDevName, isRegExp );

        if( sDevName.isBlank() )
        {
            model.setFilter( null );
        }
        else if( isRegExp )
        {
            final Pattern pattern = Pattern.compile( sDevName );

            model.setFilter( (ExEnComm ec) -> pattern.matcher( ec.getDeviceName() ).matches() );
        }
        else
        {
            model.setFilter( (ExEnComm ec) -> UtilStr.contains( ec.getDeviceName(), sDevName ) );
        }
    }

    //------------------------------------------------------------------------//
    // INNER CLASS
    // ExEnClient uses this to inform to its listeners when an ExEn informs
    // about changes: it could be that there are more than one MissionControl
    // working with the same ExEn. Changes made by one MissionControl has to
    // be reflected in the other(s).
    // <p>
    // PnlAllCmds panel uses it to inform to the ExEnClient that the user
    // has change a request (added, deleted or edited).
    //------------------------------------------------------------------------//
    private final class ClientListener implements INetClient.IListener
    {
        @Override
        public void onConnected( INetClient origin )
        {
            // Nothing to do
        }

        @Override
        public void onDisconnected( INetClient origin )
        {
            // Nothing to do
        }

        @Override
        public void onMessage( INetClient origin, String msg )
        {
            ExEnComm packet = ExEnComm.fromJSON( msg );

            if( packet.request == ExEnComm.Request.Changed )
                TblChanges.this.model.addRow( packet );
        }

        @Override
        public void onError( INetClient origin, Exception exc )
        {
            // Nothing to do: this is managed by ExEnClient
        }
    }

    //------------------------------------------------------------------------//
    // INNER CLASS
    //------------------------------------------------------------------------//
    private static final class ThisTableModel
                         extends GTableModel<ExEnComm>
    {
        ThisTableModel()
        {
            super( "Date", "Time", "Device", "Value" );
        }

        @Override
        public Class getColumnClass( int column )
        {
            switch( column )
            {
                case 0:  return LocalDate.class;
                case 1:  return LocalTime.class;
                case 2:  return String.class;
                case 3:  return String.class;
                default: return String.class;
            }
        }

        @Override
        public Object getValueAt( int row, int column )
        {
            ExEnComm            msg  = getRow( row );
            Pair<String,Object> pair = msg.getChange();

            switch( column )
            {
                case 0 : return UtilSys.toLocalDate( msg.getWhen() );
                case 1 : return UtilSys.toLocalTime( msg.getWhen() );
                case 2 : return pair.getKey();
                case 3 : return pair.getValue().toString();
                default: return "null";
            }
        }
    }
}