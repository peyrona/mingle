
package com.peyrona.mingle.glue;

import com.eclipsesource.json.JsonArray;
import com.eclipsesource.json.JsonObject;
import com.eclipsesource.json.JsonValue;
import com.peyrona.mingle.glue.gswing.GDialog;
import com.peyrona.mingle.glue.gswing.GList;
import com.peyrona.mingle.lang.japi.UtilStr;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.event.KeyEvent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.KeyStroke;
import javax.swing.SwingConstants;
import jiconfont.icons.font_awesome.FontAwesome;
import jiconfont.swing.IconFontSwing;

/**
 * This class shows a dialog with all options to connect to an ExEn.
 * When user closes the dialog, it is not "dispose()", but "hide()",
 * in this way when the user re-opens it, last configuration is shown.
 *
 * @author Francisco José Morero Peyrona
 *
 * Official web site at: <a href="https://github.com/peyrona/mingle">https://github.com/peyrona/mingle</a>
 */
public final class ConnectDlg extends GDialog
{
    private final JsonArray         jaClients;
    private final GList<JsonObject> ltbProtocols;
    private       String            sSavedName;
    private       JsonObject        joSelected;   // The Protocol the user selected, or null if none

    //------------------------------------------------------------------------//

    public ConnectDlg( JsonArray jaClis )
    {
        super( "Connect with a running ExEn", true );

        jaClients  = jaClis;
        sSavedName = null;
        joSelected = null;    // Only when user clicks 'connect' this becomes not null

        initComponents();

        // Extra init --------------------------

        btnLoad.setIcon(    IconFontSwing.buildIcon( FontAwesome.FOLDER  , 16, JTools.getIconColor() ) );
        btnSave.setIcon(    IconFontSwing.buildIcon( FontAwesome.FLOPPY_O, 16, JTools.getIconColor() ) );
        btnConnect.setIcon( IconFontSwing.buildIcon( FontAwesome.PLUG    , 16, JTools.getIconColor() ) );

        ltbProtocols = new GList<>( lstProtocols )
                            .setSorted( true )
                            .setCaptionFn(  (json) -> json.getString( "name", "NoName" ) )
                            .onHighlighted( (list) -> onProtocolSelected() )
                            .onPicked(      (list) -> btnConnectActionPerformed( null ) );

        for( JsonValue jv : jaClients )
            ltbProtocols.add( jv.asObject() );

        if( ltbProtocols.model.size() > 0 )
            lstProtocols.setSelectedIndex( 0 );

        getRootPane().setDefaultButton( btnConnect );
    }

    //------------------------------------------------------------------------//

    public JsonObject getSelection()
    {
        return joSelected;
    }

    public String getConnName()
    {
        return UtilStr.isNotEmpty( sSavedName ) ? sSavedName : joSelected.getString( "name", "Unknown" );
    }

    //------------------------------------------------------------------------//
    // PRIVATE SCOPE

    private void onProtocolSelected()
    {
        JsonObject joClient  = ltbProtocols.getSelected();
        JPanel     pnlConfig = new ProtocolConfigPanel( joClient );

        spConfig.setViewportView( pnlConfig );
        lblProtDesc.setText( joClient.getString( "description", null ) );
    }

    private void loadSavedConnectionDefinition( JsonObject joConn )
    {
        // Extract the saved connection data
        String     sSavedLabel   = joConn.getString( "label", null );
        JsonObject joSavedConfig = joConn.get( "config" ).asObject();

        if( sSavedLabel == null || joSavedConfig == null )
            return;

        // Find the protocol that matches the saved connection
        String sProtocolName = joSavedConfig.getString( "name", null );

        if( sProtocolName == null )
            return;

        // Search for the protocol in the list
        for( int n = 0; n < ltbProtocols.model.size(); n++ )
        {
            JsonObject joProtocol = ltbProtocols.get( n );

            if( sProtocolName.equals( joProtocol.getString( "name", null ) ) )
            {
                // Select this protocol
                ltbProtocols.setSelected( joProtocol );

                // Trigger the protocol selection to create the config panel
                onProtocolSelected();

                // Get the config panel and load the saved data
                JPanel pnlConfig = (JPanel) spConfig.getViewport().getView();

                if( pnlConfig instanceof ProtocolConfigPanel )
                    loadConfigData( (ProtocolConfigPanel) pnlConfig, joSavedConfig );

                // Set the saved name
                this.sSavedName = sSavedLabel;

                break;
            }
        }
    }

    /**
     * Loads saved configuration data into a ProtocolConfigPanel.
     *
     * @param pnlConfig The ProtocolConfigPanel to load data into
     * @param joConfig The configuration data to load
     */
    private void loadConfigData( ProtocolConfigPanel pnlConfig, JsonObject joConfig )
    {
        // Get all the keys from the saved config (except "name" which is the protocol identifier)
        for( JsonObject.Member member : joConfig )
        {
            String sKey = member.getName();
            if( "name".equals( sKey ) )
                continue;  // Skip the protocol name

            JsonValue jvValue = member.getValue();
            String sValue = jvValue.isString() ? jvValue.asString() : jvValue.toString();

            // Find the corresponding field in the config panel and set its value
            setConfigFieldValue( pnlConfig, sKey, sValue );
        }
    }

    /**
     * Sets the value of a specific field in the ProtocolConfigPanel.
     *
     * @param pnlConfig The ProtocolConfigPanel containing the field
     * @param sKey The field key/name
     * @param sValue The value to set
     */
    private void setConfigFieldValue( ProtocolConfigPanel pnlConfig, String sKey, String sValue )
    {
        // Use reflection to access the private fields map in ProtocolConfigPanel
        try
        {
            java.lang.reflect.Field field = ProtocolConfigPanel.class.getDeclaredField( "fields" );
            field.setAccessible( true );

            @SuppressWarnings("unchecked")
            java.util.Map<String, Object> fields =
                (java.util.Map<String, Object>) field.get( pnlConfig );

            Object fieldData = fields.get( sKey );
            if( fieldData != null )
            {
                setFieldValue( fieldData, sValue );
            }
        }
        catch( Exception ex )
        {
            // If reflection fails, silently ignore (the field will remain empty)
        }
    }

    /**
     * Sets the value of a specific field component based on its type.
     *
     * @param fieldData The FieldData containing the component
     * @param sValue The value to set
     */
    private void setFieldValue( Object fieldData, String sValue )
    {
        try
        {
            // Use reflection to access the component and type fields
            java.lang.reflect.Field componentField = fieldData.getClass().getDeclaredField( "component" );
            componentField.setAccessible( true );
            java.lang.reflect.Field typeField = fieldData.getClass().getDeclaredField( "sType" );
            typeField.setAccessible( true );

            Object component = componentField.get( fieldData );
            String sType = (String) typeField.get( fieldData );

            if( sValue == null )
                return;

            switch( sType )
            {
                case "integer":
                    if( component instanceof javax.swing.JSpinner )
                    {
                        try
                        {
                            ((javax.swing.JSpinner) component).setValue( Integer.parseInt( sValue ) );
                        }
                        catch( NumberFormatException e )
                        {
                            // Ignore invalid integer values
                        }
                    }
                    break;

                case "password":
                    if( component instanceof javax.swing.JPasswordField )
                    {
                        ((javax.swing.JPasswordField) component).setText( sValue );
                    }
                    break;

                case "file":
                    if( component instanceof javax.swing.JPanel )
                    {
                        javax.swing.JPanel pnl = (javax.swing.JPanel) component;
                        if( pnl.getComponentCount() > 0 && pnl.getComponent( 0 ) instanceof javax.swing.JTextField )
                        {
                            ((javax.swing.JTextField) pnl.getComponent( 0 )).setText( sValue );
                        }
                    }
                    break;

                case "keyvalue":
                    // For key-value pairs, we'd need more complex handling
                    // For now, skip this as it's less commonly used
                    break;

                case "url":
                case "string":
                default:
                    if( component instanceof javax.swing.JTextField )
                    {
                        ((javax.swing.JTextField) component).setText( sValue );
                    }
                    break;
            }
        }
        catch( Exception ex )
        {
            // If anything fails, silently ignore
        }
    }

    /**
     * This method is called from within the constructor to initialize the form.
     * WARNING: Do NOT modify this code. The content of this method is always
     * regenerated by the Form Editor.
     */
    @SuppressWarnings("unchecked")
    // <editor-fold defaultstate="collapsed" desc="Generated Code">//GEN-BEGIN:initComponents
    private void initComponents()
    {

        radGroupSecurity = new javax.swing.ButtonGroup();
        btnConnect = new javax.swing.JButton();
        jLabel4 = new javax.swing.JLabel();
        jScrollPane1 = new javax.swing.JScrollPane();
        lstProtocols = new javax.swing.JList<>();
        btnLoad = new javax.swing.JButton();
        btnSave = new javax.swing.JButton();
        spConfig = new javax.swing.JScrollPane();
        jLabel5 = new javax.swing.JLabel();
        lblProtDesc = new javax.swing.JLabel();

        btnConnect.setText("Connect");
        btnConnect.setToolTipText("Connect with the ExEn");
        btnConnect.setSelected(true);
        btnConnect.addActionListener(new java.awt.event.ActionListener()
        {
            public void actionPerformed(java.awt.event.ActionEvent evt)
            {
                btnConnectActionPerformed(evt);
            }
        });

        jLabel4.setText("Protocol");

        lstProtocols.setSelectionMode(javax.swing.ListSelectionModel.SINGLE_SELECTION);
        jScrollPane1.setViewportView(lstProtocols);

        btnLoad.setText("Load");
        btnLoad.setToolTipText("Open a previously saved connection definition");
        btnLoad.addActionListener(new java.awt.event.ActionListener()
        {
            public void actionPerformed(java.awt.event.ActionEvent evt)
            {
                btnLoadActionPerformed(evt);
            }
        });

        btnSave.setText("Save");
        btnSave.setToolTipText("Saves current connection definition (if Label is empty, URL will be used)");
        btnSave.addActionListener(new java.awt.event.ActionListener()
        {
            public void actionPerformed(java.awt.event.ActionEvent evt)
            {
                btnSaveActionPerformed(evt);
            }
        });

        jLabel5.setText("Configuration");

        lblProtDesc.setText("...");

        javax.swing.GroupLayout layout = new javax.swing.GroupLayout(getContentPane());
        getContentPane().setLayout(layout);
        layout.setHorizontalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(layout.createSequentialGroup()
                .addContainerGap()
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addGroup(layout.createSequentialGroup()
                        .addComponent(lblProtDesc, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                        .addGap(18, 18, 18)
                        .addComponent(btnLoad)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(btnSave)
                        .addGap(18, 18, 18)
                        .addComponent(btnConnect))
                    .addGroup(layout.createSequentialGroup()
                        .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.TRAILING)
                            .addComponent(jScrollPane1, javax.swing.GroupLayout.Alignment.LEADING, javax.swing.GroupLayout.PREFERRED_SIZE, 207, javax.swing.GroupLayout.PREFERRED_SIZE)
                            .addComponent(jLabel4, javax.swing.GroupLayout.Alignment.LEADING))
                        .addGap(18, 18, 18)
                        .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                            .addComponent(spConfig)
                            .addGroup(layout.createSequentialGroup()
                                .addComponent(jLabel5)
                                .addGap(0, 320, Short.MAX_VALUE)))))
                .addContainerGap())
        );
        layout.setVerticalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(layout.createSequentialGroup()
                .addContainerGap()
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(jLabel4)
                    .addComponent(jLabel5))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(jScrollPane1, javax.swing.GroupLayout.DEFAULT_SIZE, 318, Short.MAX_VALUE)
                    .addComponent(spConfig))
                .addGap(18, 18, 18)
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                        .addComponent(btnSave)
                        .addComponent(btnConnect)
                        .addComponent(btnLoad))
                    .addComponent(lblProtDesc))
                .addContainerGap())
        );

        pack();
    }// </editor-fold>//GEN-END:initComponents

    private void btnConnectActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_btnConnectActionPerformed

        joSelected = ltbProtocols.getSelected();
        dispose();
    }//GEN-LAST:event_btnConnectActionPerformed

    private void btnSaveActionPerformed(java.awt.event.ActionEvent evt)//GEN-FIRST:event_btnSaveActionPerformed
    {//GEN-HEADEREND:event_btnSaveActionPerformed
        String name = sSavedName;   // So, in case of exc, the sSavedName is not altered (can be still null if it was)

        if( name == null )          // The connection did not have a name: it was not previously saved and now restored
        {
            name = JTools.ask( "Connection name", "Saving connection definition" );

            if( name == null )
                return;
        }

        ConfigManager.setConnection( name, ltbProtocols.getSelected() );

        sSavedName = name;    // Because no problem on saving
    }//GEN-LAST:event_btnSaveActionPerformed

    private void btnLoadActionPerformed(java.awt.event.ActionEvent evt)//GEN-FIRST:event_btnLoadActionPerformed
    {//GEN-HEADEREND:event_btnLoadActionPerformed
        JsonArray connections = ConfigManager.getConnections();

        if( connections.isEmpty() )
        {
            JTools.info( "There are no saved connection definitions.\n"+
                         "Fill this dialog with proper information and click 'Save' button." );
            return;
        }

        JLabel lbl = new JLabel( "Select: [ ↑ ]  [ ↓ ]      Delete: [Del]      Select: [Enter] or Dbl-click      Close: [Esc]" );
               lbl.setHorizontalAlignment( SwingConstants.CENTER );

        JPanel pnlAll = new JPanel( new BorderLayout( 0, 10 ) );
               pnlAll.add( lbl, BorderLayout.SOUTH );

        GDialog dlg = new GDialog( "Select a saved connection", true );
                dlg.setMinimumSize(   new Dimension( 320, 340 ) );
                dlg.setPreferredSize( new Dimension( 420, 460 ) );
                dlg.put( pnlAll, BorderLayout.CENTER );

        GList<JsonObject> ltb = new GList<>()
                                .addTo( pnlAll, BorderLayout.CENTER )
                                .setCaptionFn(  (json) -> ((JsonObject) json).getString( "label", "NoName" ) )
                                .setKey( KeyStroke.getKeyStroke( KeyEvent.VK_DELETE, 0 ),
                                         (list) ->
                                         {
                                             if( JTools.confirm( "Delete the selected saved connection?" ) )
                                             {
                                                JsonObject jo = ((GList<JsonObject>) list).remove();
                                                ConfigManager.removeConnection( jo.getString( "name", null ) );
                                             }
                                         } )
                                .onPicked( (list) ->
                                            {
                                                dlg.dispose();
                                                loadSavedConnectionDefinition( ((GList<JsonObject>) list).getSelected() );
                                            } );

        for( JsonValue jv : connections )
            ltb.add( jv.asObject() );

        dlg.setVisible();
    }//GEN-LAST:event_btnLoadActionPerformed

    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.JButton btnConnect;
    private javax.swing.JButton btnLoad;
    private javax.swing.JButton btnSave;
    private javax.swing.JLabel jLabel4;
    private javax.swing.JLabel jLabel5;
    private javax.swing.JScrollPane jScrollPane1;
    private javax.swing.JLabel lblProtDesc;
    private javax.swing.JList<JsonObject> lstProtocols;
    private javax.swing.ButtonGroup radGroupSecurity;
    private javax.swing.JScrollPane spConfig;
    // End of variables declaration//GEN-END:variables
}
