
package com.peyrona.mingle.glue.exen.tables;

import com.eclipsesource.json.Json;
import com.eclipsesource.json.JsonObject.Member;
import com.eclipsesource.json.JsonValue;
import com.peyrona.mingle.glue.exen.ExEnClient;
import com.peyrona.mingle.glue.gswing.GDialog;
import com.peyrona.mingle.glue.gswing.GTable;
import com.peyrona.mingle.glue.gswing.GTableModel;
import com.peyrona.mingle.lang.interfaces.network.INetClient;
import com.peyrona.mingle.lang.japi.ExEnComm;
import com.peyrona.mingle.lang.japi.UtilStr;
import com.peyrona.mingle.lang.japi.UtilSys;
import java.awt.BorderLayout;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.regex.Pattern;
import javax.swing.JScrollPane;

/**
 * Shows only following types of message: List, Add, Remove and Replace
 *
 * @author Francisco José Morero Peyrona
 *
 * Official web site at: <a href="https://mingle.peyrona.com">https://mingle.peyrona.com</a>
 */
final class TblEditions
      extends GTable<ExEnComm>
{
    TblEditions( ExEnClient exenClient )
    {
        super( new ThisTableModel(), 10, 10, 10, 70 );

        exenClient.add( new ClientListener() );
    }

    //------------------------------------------------------------------------//

    @Override
    protected void onShowRowDetails()
    {
        Member pair = model.getRow( getSelectedRow() )
                           .toJSON()
                           .asObject()
                           .iterator()
                           .next();

        String         sRequest = pair.getName();    // TODO: mirar p q no uso esta var
        JsonValue      jvalue   = (pair.getValue().isNull() ? Json.NULL : pair.getValue());
        GTable<Member> table    = new GTable<>( new DetailsTableModel(), 30, 99 );

        if( jvalue.isArray() )    // ExEnComm.Request.List
        {
            jvalue.asArray().forEach( (JsonValue jv) -> add( table, jv ) );
        }
        else
        {
            add( table, jvalue );
        }

        GDialog dlg = new GDialog( "Edition details", true );
                dlg.add( new JScrollPane( table ), BorderLayout.CENTER );
                dlg.setVisible();
    }

    @Override
    public void setFilter( String sCmd, boolean isRegExp )
    {
        super.setFilter( sCmd, isRegExp );

        if( sCmd.isBlank() )
        {
            model.setFilter( null );
        }
        else if( isRegExp )
        {
            final Pattern pattern = Pattern.compile( sCmd );

            model.setFilter( (ExEnComm ec) -> pattern.matcher( ec.toString() ).matches() );
        }
        else
        {
            model.setFilter( (ExEnComm ec) -> UtilStr.contains( ec.toString(), sCmd ) );
        }
    }

    //------------------------------------------------------------------------//

    private void add( GTable<Member> table, JsonValue jvPayload )
    {
        Json.parse( jvPayload.toString() )
            .asObject()
            .forEach( (Member m) -> table.model.addRow( m ) );
    }

    //------------------------------------------------------------------------//
    // INNER CLASS
    //------------------------------------------------------------------------//
    private final class DetailsTableModel extends GTableModel<Member>
    {
        DetailsTableModel()
        {
            super( "Key", "Value" );
        }

        @Override
        public Class getColumnClass(int column)
        {
            return String.class;
        }

        @Override
        public Object getValueAt(int row, int column)
        {
            return (column == 0) ? getRow( row ).getName()
                                 : getRow( row ).getValue();
        }
    }

    //------------------------------------------------------------------------//
    // INNER CLASS
    // ExEnClient uses this to inform to its listeners when an ExEn informs
    // about changes: it could be that there are more than one Mission Control
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

            if( packet.request == ExEnComm.Request.Error )     // Errors are shwon in their own table
                return;

            if( packet.request == ExEnComm.Request.Changed )   // Device state changes are shown in their own table
                return;

            TblEditions.this.model.addRow( packet );
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
            super( "Date", "Time", "Type", "Command" );
        }

        @Override
        public Class getColumnClass( int column )
        {
            switch( column )
            {
                case 0:  return LocalDate.class;
                case 1:  return LocalTime.class;
                case 2:  return ExEnComm.Request.class;
                case 3:  return String.class;
                default: return String.class;
            }
        }

        @Override
        public Object getValueAt( int row, int column )
        {
            ExEnComm msg  = getRow( row );

            switch( column )
            {
                case 0 : return UtilSys.toLocalDate( msg.getWhen() );
                case 1 : return UtilSys.toLocalTime( msg.getWhen() );
                case 2 : return msg.request;
                case 3 : return msg.toString();
                default: return "null";
            }
        }
    }
}