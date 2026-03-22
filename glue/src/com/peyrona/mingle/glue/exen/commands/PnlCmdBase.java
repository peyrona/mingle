
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
import javax.swing.JComboBox;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.event.TableModelListener;
import javax.swing.table.TableModel;
import javax.swing.text.JTextComponent;

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

    protected static String fromClause2Code( String sFrom )
    {
        if( sFrom.isBlank() )
            return "";

        sFrom = sFrom.replace( ';', ',' );   // Both ';' and ',' are allowed as separators

        StringBuilder sb = new StringBuilder( 512 );

        for( String s : sFrom.split( "," ) )
        {
            if( ! s.isBlank() )
                sb.append( "\n\t\t\t\"" ).append( s.trim() ).append( '"' );
        }

        if( sb.isEmpty() )
            return "";

        return "\n\tFROM "+ sb;
    }

    /**
     * Extracts time unit character from a formatted combo box selection.
     * <p>
     * Parses the selected item to extract the unit character from the
     * second-to-last position. Expected format: "Unit Name  (u)" where 'u'
     * is the unit character. Returns empty string for first item (index 0).
     * </p>
     *
     * @param cmbUnits combo box containing time unit selections; should not be null
     * @return unit character (e.g., "s", "m", "h") or empty string
     */
    protected static String getTimeUnitFromComboBox( JComboBox<String> cmbUnits )
    {
        String sItem = cmbUnits.getSelectedItem().toString();
        String sUnit = "";
        if( cmbUnits.getSelectedIndex() > 0 )
        {
            char cUnit = sItem.charAt( sItem.length() - 2 ); // e.g.: "Seconds  (s)"
            sUnit = Character.valueOf( cUnit ).toString();
        }
        return sUnit;
    }

    /**
     * Checks if a text component is configured for UNE name input.
     * <p>
     * Determines if the text component has been marked as containing an UNE name
     * by checking for a specific client property. Used for input validation and
     * key filtering to ensure only valid UNE name characters are entered.
     * </p>
     *
     * @param txt the text component to check; should not be null
     * @return true if the component is configured for UNE name input, false otherwise
     * @see #setJTextIsUneName(JTextField)
     */
    protected static boolean isJTextUneName( JTextComponent txt )
    {
        Object val = txt.getClientProperty( "_THIS_TEXT_COMPONENT_CONTAINS_AN_UNE_NAME_" );
        return (val instanceof Boolean) && ((Boolean) val);
    }

    /**
     * Marks a JTextField as being used for UNE name input.
     * <p>
     * Sets a client property on the text field to indicate it should accept
     * only valid UNE name characters. This enables input filtering and validation
     * specific to UNE naming conventions.
     * </p>
     *
     * @param txt the text field to mark for UNE name input; should not be null
     * @return the same JTextField instance for method chaining
     * @see #isJTextUneName(JTextComponent)
     */
    protected static JTextField setJTextIsUneName( JTextField txt )
    {
        txt.putClientProperty( "_THIS_TEXT_COMPONENT_CONTAINS_AN_UNE_NAME_", true );
        return txt;
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
