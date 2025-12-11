
package com.peyrona.mingle.glue;

import com.eclipsesource.json.Json;
import com.eclipsesource.json.JsonArray;
import com.eclipsesource.json.JsonObject;
import com.eclipsesource.json.JsonValue;
import com.peyrona.mingle.glue.gswing.GTextField;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JPasswordField;
import javax.swing.JScrollPane;
import javax.swing.JSpinner;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.SpinnerNumberModel;
import javax.swing.border.EmptyBorder;
import javax.swing.filechooser.FileNameExtensionFilter;
import javax.swing.table.DefaultTableModel;

/**
 * A dialog that dynamically builds UI components based on protocol configuration.
 * <p>
 * This class receives a JsonObject containing UI field definitions and creates
 * appropriate Swing components for user input. It handles validation and returns
 * the user's input as a JsonObject.
 *
 * @author Francisco José Morero Peyrona
 */
public final class ProtocolConfigPanel extends JPanel
{
    private final Map<String,FieldData> fields = new HashMap<>();

    //------------------------------------------------------------------------//
    // CONSTRUCTOR

    /**
     * Creates a new protocol configuration dialog.
     *
     * @param config The JsonObject containing the "ui" configuration
     */
    public ProtocolConfigPanel( JsonObject config )
    {
        initPanel( config.get( "ui" ).asArray() );
    }

    //------------------------------------------------------------------------//
    // PUBLIC

    /**
     * Returns the user input as a JsonObject with field keys and values.
     * <p>
     * This method should be called after showDialog() returns true.
     *
     * @return JsonObject with user input, or null if dialog was cancelled
     */
    public JsonObject getConfig()
    {
        if( ! validateFields() )
            return null;

        return collectData();
    }

    //------------------------------------------------------------------------//
    // PRIVATE

    private void initPanel( JsonArray jaFields )
    {
        setBorder( new EmptyBorder( 5, 9, 5, 9 ) );
        setMinimumSize( new Dimension( 400, 200 ) );
        setLayout( new GridBagLayout() );

        GridBagConstraints gbc = new GridBagConstraints();

        gbc.insets = new Insets( 5, 5, 5, 5 );
        gbc.anchor = GridBagConstraints.WEST;
        gbc.fill   = GridBagConstraints.HORIZONTAL;

        int nRow = 0;

        for( JsonValue jv : jaFields )
        {
            JsonObject joField = jv.asObject();
            String     sKey    = joField.getString( "key"  , "" );
            String     sLabel  = joField.getString( "label", "" );
            String     sTooltip= joField.getString( "tooltip", "" );
            boolean    bReq    = joField.getBoolean( "required", false );

            if( sLabel.isEmpty() )
                sLabel = sKey;

            // Label
            gbc.gridx   = 0;
            gbc.gridy   = nRow;
            gbc.weightx = 0.0;

            JLabel lbl = new JLabel( sLabel + (bReq ? " *" : "") + ":" );

            if( ! sTooltip.isEmpty() )
                lbl.setToolTipText( sTooltip );

            add( lbl, gbc );

            // Input component
            gbc.gridx   = 1;
            gbc.weightx = 1.0;

            JComponent comp = createInputComponent( joField );

            if( ! sTooltip.isEmpty() )
                comp.setToolTipText( sTooltip );

            add( comp, gbc );

            // Store field data
            fields.put( sKey, new FieldData( joField, comp ) );

            nRow++;
        }

        // Add glue at bottom
        gbc.gridx   = 0;
        gbc.gridy   = nRow;
        gbc.weighty = 1.0;
        gbc.gridwidth = 2;

        add( new JPanel(), gbc );
    }

    private JComponent createInputComponent( JsonObject joField )
    {
        String sType = joField.getString( "type", "string" );

        switch( sType.toLowerCase() )
        {
            case "integer":
                return createIntegerField( joField );

            case "password":
                return createPasswordField( joField );

            case "file":
                return createFileField( joField );

            case "keyvalue":
                return createKeyValueTable( joField );

            case "url":
            case "string":
            default:
                return createTextField( joField );
        }
    }

    private JComponent createTextField( JsonObject joField )
    {
        String sDefault = joField.getString( "default", "" );
        JTextField txt  = new JTextField( sDefault );
                   txt.setToolTipText( joField.getString( "tooltip", null ) );

        return txt;
    }

    private JComponent createPasswordField( JsonObject joField )
    {
        JPasswordField pwd = new JPasswordField();
                       pwd.setToolTipText( joField.getString( "tooltip", null ) );

        return pwd;
    }

    private JComponent createIntegerField( JsonObject joField )
    {
        int nDefault = joField.getInt( "default", 0 );
        int nMin     = Integer.MIN_VALUE;
        int nMax     = Integer.MAX_VALUE;

        JsonValue jvValidation = joField.get( "validation" );

        if( jvValidation != null && jvValidation.isObject() )
        {
            JsonObject joVal = jvValidation.asObject();

            nMin = joVal.getInt( "min", nMin );
            nMax = joVal.getInt( "max", nMax );
        }

        SpinnerNumberModel model = new SpinnerNumberModel( nDefault, nMin, nMax, 1 );
        JSpinner           spin  = new JSpinner( model );
                           spin.setToolTipText( joField.getString( "tooltip", null ) );

        return spin;
    }

    private JComponent createFileField( JsonObject joField )
    {
        JPanel pnl = new JPanel( new BorderLayout( 5, 0 ) );
        GTextField txt = new GTextField();
                   txt.setColumns( 15 );
                   txt.setEditable( false );
                   txt.setPlaceholder( "Use [...] to select a file" );
                   txt.setToolTipText( joField.getString( "tooltip", null ) );
        JButton    btn = new JButton( "..." );
                   btn.setToolTipText( joField.getString( "tooltip", null ) );

        pnl.add( txt, BorderLayout.CENTER );
        pnl.add( btn, BorderLayout.EAST   );

        // Get accepted extensions
        List<String> lstExt = new ArrayList<>();
        JsonValue    jvAccept = joField.get( "accept" );

        if( jvAccept != null && jvAccept.isArray() )
            jvAccept.asArray().forEach( jv -> lstExt.add( jv.asString() ) );

        btn.addActionListener( e ->
        {
            JFileChooser fc = new JFileChooser();

            fc.setFileSelectionMode( JFileChooser.FILES_ONLY );

            if( ! lstExt.isEmpty() )
            {
                String[] asExt = lstExt.stream()
                                       .map( s -> s.startsWith( "." ) ? s.substring( 1 ) : s )
                                       .toArray( String[]::new );

                String sDesc = String.join( ", ", asExt );

                fc.setFileFilter( new FileNameExtensionFilter( sDesc, asExt ) );
            }

            if( fc.showOpenDialog( this ) == JFileChooser.APPROVE_OPTION )
            {
                File file = fc.getSelectedFile();

                txt.setText( file.getAbsolutePath() );
            }
        } );

        return pnl;
    }

    private JComponent createKeyValueTable( JsonObject joField )
    {
        DefaultTableModel model  = new DefaultTableModel( new String[] { "Key", "Value" }, 0 );
        JTable            tbl    = new JTable( model );
        JScrollPane       scr    = new JScrollPane( tbl );
        JPanel            pnlAll = new JPanel( new BorderLayout( 5, 5 ) );
        JPanel            pnlBtn = new JPanel( new FlowLayout( FlowLayout.LEFT, 5, 0 ) );
        JButton           btnAdd = new JButton( "Add" );
        JButton           btnDel = new JButton( "Remove" );

        scr.setPreferredSize( new Dimension( 210, 200 ) );

        btnAdd.addActionListener( e -> model.addRow( new Object[] { "", "" } ) );

        btnDel.addActionListener( e ->
        {
            int nRow = tbl.getSelectedRow();

            if( nRow >= 0 )
                model.removeRow( nRow );
        } );

        pnlBtn.add( btnAdd );
        pnlBtn.add( btnDel );

        pnlAll.add( scr, BorderLayout.CENTER );
        pnlAll.add( pnlBtn, BorderLayout.SOUTH );

        return pnlAll;
    }

    private boolean validateFields()
    {
        for( Map.Entry<String,FieldData> entry : fields.entrySet() )
        {
            FieldData fd = entry.getValue();

            if( fd.isRequired() && ! fd.hasValue() )
            {
                javax.swing.JOptionPane.showMessageDialog( this, "Field '" + fd.getLabel() + "' is required", "Validation Error",
                                                          javax.swing.JOptionPane.ERROR_MESSAGE );
                return false;
            }

            if( ! fd.validate() )
            {
                javax.swing.JOptionPane.showMessageDialog( this, "Field '" + fd.getLabel() + "' has invalid value", "Validation Error",
                                                          javax.swing.JOptionPane.ERROR_MESSAGE );
                return false;
            }
        }

        return true;
    }

    private JsonObject collectData()
    {
        JsonObject jo = Json.object();

        for( Map.Entry<String,FieldData> entry : fields.entrySet() )
        {
            String    sKey = entry.getKey();
            FieldData fd   = entry.getValue();
            Object    val  = fd.getValue();

            if( val != null )
            {
                if( val instanceof Integer )
                    jo.add( sKey, (Integer) val );
                else if( val instanceof JsonObject )
                    jo.add( sKey, (JsonObject) val );
                else
                    jo.add( sKey, val.toString() );
            }
        }

        return jo;
    }

    //------------------------------------------------------------------------//
    // INNER CLASS

    private static final class FieldData
    {
        private final JsonObject joField;
        private final JComponent component;
        private final String     sType;

        FieldData( JsonObject joField, JComponent component )
        {
            this.joField   = joField;
            this.component = component;
            this.sType     = joField.getString( "type", "string" ).toLowerCase();
        }

        String getLabel()
        {
            return joField.getString( "label", joField.getString( "key", "" ) );
        }

        boolean isRequired()
        {
            return joField.getBoolean( "required", false );
        }

        boolean hasValue()
        {
            Object val = getValue();

            if( val == null )
                return false;

            if( val instanceof String )
                return ! ((String) val).trim().isEmpty();

            if( val instanceof JsonObject )
                return ! ((JsonObject) val).isEmpty();

            return true;
        }

        Object getValue()
        {
            switch( sType )
            {
                case "integer":
                    JSpinner spin = (JSpinner) component;
                    return (Integer) spin.getValue();

                case "password":
                    JPasswordField pwd = (JPasswordField) component;
                    char[] achPwd = pwd.getPassword();
                    return (achPwd.length == 0) ? null : new String( achPwd );

                case "file":
                    JPanel pnl = (JPanel) component;
                    JTextField txt = (JTextField) pnl.getComponent( 0 );
                    String sFile = txt.getText().trim();
                    return sFile.isEmpty() ? null : sFile;

                case "keyvalue":
                    return getKeyValueData();

                case "url":
                case "string":
                default:
                    JTextField txtField = (JTextField) component;
                    String sVal = txtField.getText().trim();
                    return sVal.isEmpty() ? null : sVal;
            }
        }

        private JsonObject getKeyValueData()
        {
            JPanel pnl = (JPanel) component;
            JScrollPane scr = (JScrollPane) pnl.getComponent( 0 );
            JTable tbl = (JTable) scr.getViewport().getView();
            DefaultTableModel model = (DefaultTableModel) tbl.getModel();
            JsonObject jo = Json.object();

            for( int n = 0; n < model.getRowCount(); n++ )
            {
                String sKey = (String) model.getValueAt( n, 0 );
                String sVal = (String) model.getValueAt( n, 1 );

                if( sKey != null && ! sKey.trim().isEmpty() )
                {
                    jo.add( sKey.trim(), (sVal == null) ? "" : sVal.trim() );
                }
            }

            return jo.isEmpty() ? null : jo;
        }

        boolean validate()
        {
            JsonValue jvValidation = joField.get( "validation" );

            if( jvValidation == null || ! jvValidation.isObject() )
                return true;

            JsonObject joVal = jvValidation.asObject();
            Object     val   = getValue();

            if( val == null )
                return ! isRequired();

            switch( sType )
            {
                case "integer":
                    int nVal = (Integer) val;
                    int nMin = joVal.getInt( "min", Integer.MIN_VALUE );
                    int nMax = joVal.getInt( "max", Integer.MAX_VALUE );

                    return (nVal >= nMin && nVal <= nMax);

                case "string":
                case "url":
                    String sVal = val.toString();
                    String sPattern = joVal.getString( "pattern", null );

                    if( sPattern != null )
                        return sVal.matches( sPattern );

                    return true;

                default:
                    return true;
            }
        }
    }
}