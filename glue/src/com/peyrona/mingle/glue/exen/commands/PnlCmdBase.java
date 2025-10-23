
package com.peyrona.mingle.glue.exen.commands;

import com.peyrona.mingle.candi.unec.parser.ParseBase;
import com.peyrona.mingle.glue.JTools;
import com.peyrona.mingle.glue.codeditor.UneEditorTabContent.UneEditorUnit;
import com.peyrona.mingle.glue.gswing.GDialog;
import com.peyrona.mingle.lang.interfaces.ICandi;
import com.peyrona.mingle.lang.interfaces.ICmdEncDecLib;
import com.peyrona.mingle.lang.japi.UtilSys;
import com.peyrona.mingle.lang.lexer.Lexer;
import java.awt.BorderLayout;
import java.awt.Dialog;
import java.util.ArrayList;
import java.util.List;
import javax.swing.JButton;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.event.TableModelListener;
import javax.swing.table.TableModel;

/**
 * Base panel for panels showing commands: Scripts, Drivers, Devices and Rules.
 *
 * @author Francisco José Morero Peyrona
 *
 * Official web site at: <a href="https://github.com/peyrona/mingle">https://github.com/peyrona/mingle</a>
 */
abstract class PnlCmdBase extends JPanel
{
    protected final PnlAllCmdsSelector.CommandWise cmdWise;
    protected final ICmdEncDecLib cmdCodec = UtilSys.getConfig().newCILBuilder();
    protected final List<JButton> lstBtn2Check4Changes = new ArrayList<>();     // Dlg4Cmd has also to invoke ::getSourceCode() when these buttons are clicked

    //------------------------------------------------------------------------//
    // PROTECTED CONSTRUCTOR

    protected PnlCmdBase( PnlAllCmdsSelector.CommandWise cmdWise )
    {
        this.cmdWise = cmdWise;
    }

    //------------------------------------------------------------------------//
    // PROTECTED SCOPE

    /**
     * Returns the command source Une code as it is in the moment this method is invoked.
     *
     * @return the command source Une code as it is in the moment this method is invoked.
     */
    protected abstract String getSourceCode();

    /**
     * Returns the command in its JSON representation as it is defined in the moment this method is invoked.
     *
     * @return the command in its JSON representation as it is defined in the moment this method is invoked.
     */
    protected abstract String getTranspiled();

    protected boolean showErrors( String sCmd, Lexer lexer, ParseBase trcmd )
    {
        List<ICandi.IError> lstErrors = lexer.getErrors();
                            lstErrors.addAll( trcmd.getErrors() );

        if( ! lstErrors.isEmpty() )
        {
            JTable  tbl = JTools.setTableColWidths( new JTable( new ErrorTableModel( lstErrors ) ), 580, 85, 55, 55 );

            GDialog dlg = new GDialog( "Errors", Dialog.ModalityType.DOCUMENT_MODAL );
                    dlg.add( new JScrollPane( tbl )         , BorderLayout.CENTER );
                    dlg.add( UneEditorUnit.newEditor( sCmd ), BorderLayout.SOUTH  );
                    dlg.setVisible();

            return true;
        }

        return false;
    }

    //------------------------------------------------------------------------//
    // PRIVATE SCOPE


    //------------------------------------------------------------------------//
    // INNER CLASS: Table model for errors
    //------------------------------------------------------------------------//
    private final class ErrorTableModel implements TableModel
    {
        private final List<ICandi.IError> lstErrors;
        private final String[] columnNames = { "Message", "Line", "Col" };

        ErrorTableModel( List<ICandi.IError> lstErrors )
        {
            this.lstErrors = lstErrors;
        }

        @Override
        public int getRowCount()
        {
            return lstErrors.size();
        }

        @Override
        public int getColumnCount()
        {
            return columnNames.length;
        }

        @Override
        public Object getValueAt( int row, int column )
        {
            ICandi.IError error = lstErrors.get( row );

            switch( column )
            {
                case 0: return error.message();
                case 1: return error.line();
                case 2: return error.column();
                case 3: return "";  // Empty string for column 3
                default: return null;
            }
        }

        @Override
        public String getColumnName( int column )
        {
            return columnNames[column];
        }

        @Override
        public Class getColumnClass( int column )
        {
            switch( column )
            {
                case 0: return String.class;
                case 1: return Integer.class;
                case 2: return Integer.class;
            }

            return String.class;
        }

        @Override
        public boolean isCellEditable(int rowIndex, int columnIndex)
        {
            return false;
        }

        @Override
        public void setValueAt(Object o, int i, int i1)
        {
        }

        @Override
        public void addTableModelListener(TableModelListener l)
        {
        }

        @Override
        public void removeTableModelListener(TableModelListener l)
        {
        }
    }
}
