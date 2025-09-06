
package com.peyrona.mingle.glue.exen.tables;

import com.peyrona.mingle.glue.JTools;
import com.peyrona.mingle.glue.exen.ExEnClient;
import com.peyrona.mingle.glue.gswing.GTable;
import com.peyrona.mingle.glue.gswing.GTableModel;
import com.peyrona.mingle.lang.interfaces.network.INetClient;
import com.peyrona.mingle.lang.japi.ExEnComm;
import com.peyrona.mingle.lang.japi.UtilStr;
import com.peyrona.mingle.lang.japi.UtilSys;
import java.time.LocalDateTime;
import java.util.regex.Pattern;

/**
 * Shows errors reported by the associated ExEn.
 *
 * @author Francisco José Morero Peyrona
 *
 * Official web site at: <a href="https://mingle.peyrona.com">https://mingle.peyrona.com</a>
 */
final class TblErrors
      extends GTable<ExEnComm>
{
    TblErrors( ExEnClient exenClient )
    {
        super( new ThisTableModel(), 10, 10, 80 );

        exenClient.add( new ClientListener() );
    }

    //------------------------------------------------------------------------//

    @Override
    protected void onShowRowDetails()
    {
        JTools.error( model.getRow( getSelectedRow() ).getErrorMsg() );
    }

    @Override
    public void setFilter( String sError, boolean isRegExp )
    {
        super.setFilter( sError, isRegExp );

        if( sError.isBlank() )
        {
            model.setFilter( null );
        }
        else if( isRegExp )
        {
            final Pattern pattern = Pattern.compile( sError );

            model.setFilter( (ExEnComm ec) -> pattern.matcher( ec.getErrorMsg() ).matches() );
        }
        else
        {
            model.setFilter( (ExEnComm ec) -> UtilStr.contains( ec.getErrorMsg(), sError ) );
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

            if( packet.request == ExEnComm.Request.Error )
                TblErrors.this.model.addRow( packet );
        }

        @Override
        public void onError( INetClient origin, Exception exc )
        {
             TblErrors.this.model.addRow( new ExEnComm( exc ) );
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
            super( "Date", "Time", "Error" );
        }

        @Override
        public Class getColumnClass( int column )
        {
            switch( column )
            {
                case 0 : return LocalDateTime.class;
                case 1 : return String.class;
                case 2 : return String.class;
                default: return String.class;
            }
        }

        @Override
        public Object getValueAt( int row, int column )
        {
            ExEnComm msg = getRow( row );

            switch( column )
            {
                case 0 : return UtilSys.toLocalDate( msg.getWhen() );
                case 1 : return UtilSys.toLocalTime( msg.getWhen() );
                case 2 : return msg.getErrorMsg();
                default: return "null";
            }
        }
    }
}