
package com.peyrona.mingle.glue.exen;

import com.eclipsesource.json.Json;
import com.eclipsesource.json.JsonObject;
import com.peyrona.mingle.glue.JTools;
import com.peyrona.mingle.glue.gswing.GDialog;
import com.peyrona.mingle.glue.gswing.GList;
import com.peyrona.mingle.lang.interfaces.network.INetClient;
import com.peyrona.mingle.lang.japi.UtilIO;
import com.peyrona.mingle.lang.japi.UtilStr;
import com.peyrona.mingle.lang.japi.UtilSys;
import com.peyrona.mingle.lang.japi.UtilType;
import com.peyrona.mingle.network.NetworkBuilder;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.event.ActionEvent;
import java.awt.event.WindowEvent;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.OutputStream;
import java.util.List;
import java.util.Properties;
import javax.swing.JPanel;
import javax.swing.SpinnerNumberModel;
import javax.swing.WindowConstants;
import javax.swing.border.EmptyBorder;
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
final class DlgConnect extends GDialog
{
    private final GList<JsonObject> ltbChannels;
    private       boolean           bCancelled;     // Did the user cancell the dialog?
    private       File              fileCert;       // SSL certificate file
    private       File              fileKey;        // SSL private key file

    private final static String sCONN_DEF_EXT = ".conn-def.props";

    //------------------------------------------------------------------------//

    DlgConnect()
    {
        super( "Connect with a running ExEn", true );

        initComponents();

        ltbChannels = new GList<>( lstChannels ).setCaptionFn( (json) -> json.getString( "name", "NoName" ) );
        ltbChannels.onPicked( (jo) -> btnConnectActionPerformed( null ) );

        initExtra();
        setDefaultCloseOperation( WindowConstants.HIDE_ON_CLOSE );
    }

    //------------------------------------------------------------------------//

    @Override
    public void setVisible( boolean b )
    {
        if( b )
            bCancelled = true;     // Has to be reseted everytime the dialog is opened (to know if user cancells it or not)

        super.setVisible( b );
    }

    //------------------------------------------------------------------------//

    String getConnName()
    {
        String s = txtConnName.getText().trim();

        return UtilStr.isEmpty( s ) ?  getHost() +':'+ getPort() : s;
    }

    boolean isCancelled()
    {
        return bCancelled;
    }

    INetClient createNetworkClient()
    {
        JsonObject channelConfig = ltbChannels.getSelected();

        if( useSSL() && fileCert != null && fileKey != null )
        {
            channelConfig.set( "certFile", fileCert.getAbsolutePath() );   // TODO: creo q esta prop (certFile) y la siguiente (keyFile) no tienen
            channelConfig.set( "keyFile",  fileKey.getAbsolutePath() );    //       el nombre apropiado para que las lea el SocketClient
        }

        return NetworkBuilder.buildClient( channelConfig.toString() );    // This JSON contains only one client defintion
    }

    public String getHost()
    {
        return txtLocation.getText().trim();
    }

    public int getPort()
    {
        return (int) spnPort.getValue();
    }

    public boolean useSSL()
    {
        return chkUseSSL.isSelected();
    }

    public File getCertFile()
    {
        return fileCert;
    }

    public File getKeyFile()
    {
        return fileKey;
    }

    //------------------------------------------------------------------------//

    private void loadSavedConnectionDefinition( File file )
    {
        try( FileReader reader = new FileReader( file ) )
        {
            Properties props = new Properties();
                       props.load( reader );

            txtConnName.setText( props.getProperty( "label" ) );
            txtLocation.setText( props.getProperty( "url"   ) );
            spnPort.setValue( UtilType.toInteger( props.getProperty( "port" ) ) );

            boolean bSSL = Boolean.parseBoolean( props.getProperty( "ssl" ) );

            chkUseSSL.setSelected(  bSSL );
            btnFileCert.setEnabled( bSSL );
            btnFileKey.setEnabled(  bSSL );

            String sCertFile = props.getProperty( "certFile" );
            if( ! UtilStr.isEmpty( sCertFile ) )
            {
                fileCert = new File( sCertFile );
                btnFileCert.setText( fileCert.getName() );
                btnFileCert.setToolTipText( fileCert.getAbsolutePath() );
            }

            String sKeyFile = props.getProperty( "keyFile" );
            if( ! UtilStr.isEmpty( sKeyFile ) )
            {
                fileKey = new File( sKeyFile );
                btnFileKey.setText( fileKey.getName() );
                btnFileKey.setToolTipText( fileKey.getAbsolutePath() );
            }

            String sChannel = props.getProperty( "channel" );

            ltbChannels.setSelected( (jo) -> { return sChannel.equals( jo.getString( "name", "" ) ); } );
        }
        catch( IOException ioe )
        {
            JTools.error( ioe );
        }
    }

    private void initExtra()
    {
        btnLoad.setIcon(     IconFontSwing.buildIcon( FontAwesome.FOLDER  , 16, JTools.getIconColor() ) );
        btnSave.setIcon(     IconFontSwing.buildIcon( FontAwesome.FLOPPY_O, 16, JTools.getIconColor() ) );
        btnConnect.setIcon(  IconFontSwing.buildIcon( FontAwesome.PLUG    , 16, JTools.getIconColor() ) );
        btnFileCert.setIcon( IconFontSwing.buildIcon( FontAwesome.FOLDER  , 16, JTools.getIconColor() ) );
        btnFileKey.setIcon(  IconFontSwing.buildIcon( FontAwesome.FOLDER  , 16, JTools.getIconColor() ) );

        chkUseSSL.addActionListener( (ActionEvent e) -> {
            boolean sslEnabled = chkUseSSL.isSelected();
            btnFileCert.setEnabled( sslEnabled );
            btnFileKey.setEnabled( sslEnabled );

            if( ! sslEnabled )
            {
                fileCert = null;
                fileKey  = null;
                btnFileCert.setText( "Certific." );
                btnFileCert.setToolTipText( "SSL Certificate file" );
                btnFileKey.setText( "Key" );
                btnFileKey.setToolTipText( "SSL Key file" );
            }
        } );

        String sJSON = UtilSys.getConfig().getNetworkClientsOutline();

        if( sJSON != null )
        {
            Json.parse( sJSON )
                .asArray()
                .forEach( jv -> ltbChannels.add( jv.asObject() ) );
        }

        txtLocation.setText( "localhost" );
        spnPort.setModel( new SpinnerNumberModel( 55886, 1025, 65535, 1 ) );

        if( ltbChannels.model.size() > 0 )
            lstChannels.setSelectedIndex( 0 );

        getRootPane().setDefaultButton( btnConnect );
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
        jPanel2 = new javax.swing.JPanel();
        txtLocation = new javax.swing.JTextField();
        jLabel2 = new javax.swing.JLabel();
        spnPort = new javax.swing.JSpinner();
        jLabel1 = new javax.swing.JLabel();
        jLabel3 = new javax.swing.JLabel();
        txtConnName = new javax.swing.JTextField();
        btnLoad = new javax.swing.JButton();
        btnConnect = new javax.swing.JButton();
        jPanel3 = new javax.swing.JPanel();
        btnFileCert = new javax.swing.JButton();
        btnFileKey = new javax.swing.JButton();
        chkUseSSL = new javax.swing.JCheckBox();
        jLabel4 = new javax.swing.JLabel();
        jScrollPane1 = new javax.swing.JScrollPane();
        lstChannels = new javax.swing.JList<>();
        btnSave = new javax.swing.JButton();

        jPanel2.setBorder(javax.swing.BorderFactory.createTitledBorder("ExEn location"));

        txtLocation.setColumns(32);

        jLabel2.setText("Port");

        spnPort.setModel(new javax.swing.SpinnerNumberModel(55887, 0, 65535, 1));

        jLabel1.setText("URL");

        jLabel3.setText("Label");

        btnLoad.addActionListener(new java.awt.event.ActionListener()
        {
            public void actionPerformed(java.awt.event.ActionEvent evt)
            {
                btnLoadActionPerformed(evt);
            }
        });

        javax.swing.GroupLayout jPanel2Layout = new javax.swing.GroupLayout(jPanel2);
        jPanel2.setLayout(jPanel2Layout);
        jPanel2Layout.setHorizontalGroup(
            jPanel2Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanel2Layout.createSequentialGroup()
                .addContainerGap()
                .addGroup(jPanel2Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(jLabel2)
                    .addComponent(jLabel1))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(jPanel2Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addGroup(jPanel2Layout.createSequentialGroup()
                        .addComponent(spnPort, javax.swing.GroupLayout.PREFERRED_SIZE, 87, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addGap(18, 18, 18)
                        .addComponent(jLabel3)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(txtConnName, javax.swing.GroupLayout.DEFAULT_SIZE, 177, Short.MAX_VALUE))
                    .addGroup(jPanel2Layout.createSequentialGroup()
                        .addComponent(txtLocation, javax.swing.GroupLayout.PREFERRED_SIZE, 1, Short.MAX_VALUE)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(btnLoad)))
                .addContainerGap())
        );
        jPanel2Layout.setVerticalGroup(
            jPanel2Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanel2Layout.createSequentialGroup()
                .addContainerGap()
                .addGroup(jPanel2Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(txtLocation, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(jLabel1)
                    .addComponent(btnLoad))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                .addGroup(jPanel2Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(jLabel2)
                    .addComponent(spnPort, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(jLabel3)
                    .addComponent(txtConnName, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addContainerGap(javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
        );

        jPanel2Layout.linkSize(javax.swing.SwingConstants.VERTICAL, new java.awt.Component[] {btnLoad, txtLocation});

        btnConnect.setText("Connect");
        btnConnect.setSelected(true);
        btnConnect.addActionListener(new java.awt.event.ActionListener()
        {
            public void actionPerformed(java.awt.event.ActionEvent evt)
            {
                btnConnectActionPerformed(evt);
            }
        });

        jPanel3.setBorder(javax.swing.BorderFactory.createTitledBorder("Security"));

        btnFileCert.setText("Certific.");
        btnFileCert.setToolTipText("SSL Certificate file");
        btnFileCert.setEnabled(false);
        btnFileCert.setHorizontalAlignment(javax.swing.SwingConstants.LEADING);
        btnFileCert.addActionListener(new java.awt.event.ActionListener()
        {
            public void actionPerformed(java.awt.event.ActionEvent evt)
            {
                btnFileCertActionPerformed(evt);
            }
        });

        btnFileKey.setText("Key");
        btnFileKey.setToolTipText("SSL Key file");
        btnFileKey.setEnabled(false);
        btnFileKey.setHorizontalAlignment(javax.swing.SwingConstants.LEADING);
        btnFileKey.addActionListener(new java.awt.event.ActionListener()
        {
            public void actionPerformed(java.awt.event.ActionEvent evt)
            {
                btnFileKeyActionPerformed(evt);
            }
        });

        chkUseSSL.setText("Use SSL");

        javax.swing.GroupLayout jPanel3Layout = new javax.swing.GroupLayout(jPanel3);
        jPanel3.setLayout(jPanel3Layout);
        jPanel3Layout.setHorizontalGroup(
            jPanel3Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanel3Layout.createSequentialGroup()
                .addContainerGap()
                .addGroup(jPanel3Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(btnFileCert, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                    .addComponent(btnFileKey, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                    .addGroup(jPanel3Layout.createSequentialGroup()
                        .addComponent(chkUseSSL)
                        .addGap(0, 0, Short.MAX_VALUE)))
                .addContainerGap())
        );
        jPanel3Layout.setVerticalGroup(
            jPanel3Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanel3Layout.createSequentialGroup()
                .addContainerGap()
                .addComponent(chkUseSSL)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED, 18, Short.MAX_VALUE)
                .addComponent(btnFileCert, javax.swing.GroupLayout.PREFERRED_SIZE, 27, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(btnFileKey, javax.swing.GroupLayout.PREFERRED_SIZE, 27, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addContainerGap())
        );

        jLabel4.setText("Transport channel");

        lstChannels.setSelectionMode(javax.swing.ListSelectionModel.SINGLE_SELECTION);
        jScrollPane1.setViewportView(lstChannels);

        btnSave.setText("Save");
        btnSave.setToolTipText("Saves current connection definition (if Label is empty, URL will be used)");
        btnSave.addActionListener(new java.awt.event.ActionListener()
        {
            public void actionPerformed(java.awt.event.ActionEvent evt)
            {
                btnSaveActionPerformed(evt);
            }
        });

        javax.swing.GroupLayout layout = new javax.swing.GroupLayout(getContentPane());
        getContentPane().setLayout(layout);
        layout.setHorizontalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(layout.createSequentialGroup()
                .addContainerGap()
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(jPanel2, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                    .addGroup(layout.createSequentialGroup()
                        .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                            .addGroup(layout.createSequentialGroup()
                                .addComponent(jLabel4)
                                .addGap(0, 160, Short.MAX_VALUE))
                            .addGroup(layout.createSequentialGroup()
                                .addGap(12, 12, 12)
                                .addComponent(jScrollPane1, javax.swing.GroupLayout.PREFERRED_SIZE, 0, Short.MAX_VALUE)))
                        .addGap(29, 29, 29)
                        .addComponent(jPanel3, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                    .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, layout.createSequentialGroup()
                        .addGap(0, 0, Short.MAX_VALUE)
                        .addComponent(btnSave)
                        .addGap(18, 18, 18)
                        .addComponent(btnConnect)))
                .addContainerGap())
        );
        layout.setVerticalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(layout.createSequentialGroup()
                .addContainerGap()
                .addComponent(jPanel2, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addGap(18, 18, 18)
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING, false)
                    .addGroup(layout.createSequentialGroup()
                        .addComponent(jLabel4)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(jScrollPane1, javax.swing.GroupLayout.PREFERRED_SIZE, 0, Short.MAX_VALUE))
                    .addComponent(jPanel3, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
                .addGap(18, 18, 18)
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(btnConnect)
                    .addComponent(btnSave))
                .addContainerGap(javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
        );

        pack();
    }// </editor-fold>//GEN-END:initComponents

    private void btnConnectActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_btnConnectActionPerformed

        if( UtilStr.isEmpty( getHost() ) )
        {
            JTools.alert( "Location can not be empty" );
            return;
        }

        if( useSSL() )
        {
            if( fileCert == null || ! fileCert.exists() )
            {
                JTools.alert( "SSL certificate file is required when SSL is enabled" );
                return;
            }
            if( fileKey == null || ! fileKey.exists() )
            {
                JTools.alert( "SSL key file is required when SSL is enabled" );
                return;
            }
        }

        bCancelled = false;

        dispatchEvent( new WindowEvent( this, WindowEvent.WINDOW_CLOSING ) );
    }//GEN-LAST:event_btnConnectActionPerformed

    private void btnSaveActionPerformed(java.awt.event.ActionEvent evt)//GEN-FIRST:event_btnSaveActionPerformed
    {//GEN-HEADEREND:event_btnSaveActionPerformed
        if( UtilStr.isEmpty( getHost() ) )
        {
            JTools.error( "URL is empty: can not save" );
            return;
        }

        String sFileName = UtilIO.addExtension( getConnName(), sCONN_DEF_EXT );
        String sChannel  = ltbChannels.getSelected().getString( "name", null );

        Properties props = new Properties();
                   props.setProperty( "label"   , getConnName() );
                   props.setProperty( "url"     , getHost() );
                   props.setProperty( "port"    , String.valueOf( getPort() ) );
                   props.setProperty( "ssl"     , String.valueOf( useSSL()  ) );
                   props.setProperty( "channel" , sChannel );
                   props.setProperty( "certFile", (fileCert != null) ? fileCert.getAbsolutePath() : "" );
                   props.setProperty( "keyFile" , (fileKey  != null) ? fileKey.getAbsolutePath()  : "" );

        try( OutputStream os = new ByteArrayOutputStream() )
        {
            props.store( os, "Glue connection definition file" );

            File file = new File( UtilSys.getEtcDir(), sFileName );

            UtilIO.newFileWriter()
                  .setFile( file )
                  .replace( os.toString() );

            JTools.info( "Saved as: \n"+ file, this );
        }
        catch( IOException ioe )
        {
            JTools.error( ioe );
        }
    }//GEN-LAST:event_btnSaveActionPerformed

    private void btnLoadActionPerformed(java.awt.event.ActionEvent evt)//GEN-FIRST:event_btnLoadActionPerformed
    {//GEN-HEADEREND:event_btnLoadActionPerformed
        List<File> lstFiles = UtilIO.listFiles( UtilSys.getEtcDir(),
                                                (file) -> { return file.isFile() && file.getName().endsWith( sCONN_DEF_EXT ); } );

        if( lstFiles.isEmpty() )
        {
            JTools.info( "There are no saved 'connection definition' files.\n"+
                         "Fill this dialog with proper information and click 'Save' button." );
        }
        else
        {
            JPanel pnl = new JPanel( new BorderLayout() );
                   pnl.setBorder( new EmptyBorder(5, 5, 5, 5) );

            GDialog dlg = new GDialog( "Select a saved connection", ModalityType.APPLICATION_MODAL );
                    dlg.setMinimumSize( new Dimension( 320, 240 ) );

            new GList<File>()
                    .add( lstFiles )
                    .setCaptionFn( (file) -> file.getName() )
                    .onPicked( (file) ->
                               {
                                   dlg.dispose();
                                   loadSavedConnectionDefinition( file );
                               } )
                    .addTo( pnl, BorderLayout.CENTER );

            dlg.add( pnl );
            dlg.setVisible();
        }
    }//GEN-LAST:event_btnLoadActionPerformed

    private void btnFileCertActionPerformed(java.awt.event.ActionEvent evt)//GEN-FIRST:event_btnFileCertActionPerformed
    {//GEN-HEADEREND:event_btnFileCertActionPerformed
        File[] files = JTools.fileLoader( this, null, false,
                                          new javax.swing.filechooser.FileNameExtensionFilter( "Certificate files (*.pem, *.crt, *.cer)", "pem", "crt", "cer" ),
                                          new javax.swing.filechooser.FileNameExtensionFilter( "All files (*.*)", "*" ) );

        if( files.length > 0 )
        {
            fileCert = files[0];
            btnFileCert.setText( fileCert.getName() );
            btnFileCert.setToolTipText( fileCert.getAbsolutePath() );
        }
    }//GEN-LAST:event_btnFileCertActionPerformed

    private void btnFileKeyActionPerformed(java.awt.event.ActionEvent evt)//GEN-FIRST:event_btnFileKeyActionPerformed
    {//GEN-HEADEREND:event_btnFileKeyActionPerformed
        File[] files = JTools.fileLoader( this, null, false,
                                          new javax.swing.filechooser.FileNameExtensionFilter( "Key files (*.key, *.pem)", "key", "pem" ),
                                          new javax.swing.filechooser.FileNameExtensionFilter( "All files (*.*)", "*" ) );

        if( files.length > 0 )
        {
            fileKey = files[0];
            btnFileKey.setText( fileKey.getName() );
            btnFileKey.setToolTipText( fileKey.getAbsolutePath() );
        }
    }//GEN-LAST:event_btnFileKeyActionPerformed

    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.JButton btnConnect;
    private javax.swing.JButton btnFileCert;
    private javax.swing.JButton btnFileKey;
    private javax.swing.JButton btnLoad;
    private javax.swing.JButton btnSave;
    private javax.swing.JCheckBox chkUseSSL;
    private javax.swing.JLabel jLabel1;
    private javax.swing.JLabel jLabel2;
    private javax.swing.JLabel jLabel3;
    private javax.swing.JLabel jLabel4;
    private javax.swing.JPanel jPanel2;
    private javax.swing.JPanel jPanel3;
    private javax.swing.JScrollPane jScrollPane1;
    private javax.swing.JList<JsonObject> lstChannels;
    private javax.swing.ButtonGroup radGroupSecurity;
    private javax.swing.JSpinner spnPort;
    private javax.swing.JTextField txtConnName;
    private javax.swing.JTextField txtLocation;
    // End of variables declaration//GEN-END:variables
}
