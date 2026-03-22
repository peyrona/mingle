
package com.peyrona.mingle.glue.exen.commands;

import com.peyrona.mingle.candi.unec.parser.ParseDevice;
import com.peyrona.mingle.glue.ExEnClient;
import com.peyrona.mingle.glue.JTools;
import com.peyrona.mingle.glue.gswing.GDialog;
import com.peyrona.mingle.lang.interfaces.commands.IDevice;
import com.peyrona.mingle.lang.interfaces.commands.IDriver;
import com.peyrona.mingle.lang.japi.UtilColls;
import com.peyrona.mingle.lang.japi.UtilSys;
import com.peyrona.mingle.lang.lexer.Language;
import com.peyrona.mingle.lang.lexer.Lexer;
import java.awt.BorderLayout;
import javax.swing.JButton;
import javax.swing.SpinnerNumberModel;
import jiconfont.icons.font_awesome.FontAwesome;
import jiconfont.swing.IconFontSwing;

/**
 *
 * @author Francisco José Morero Peyrona
 *
 * Official web site at: <a href="https://github.com/peyrona/mingle">https://github.com/peyrona/mingle</a>
 */
final class PnlDevice extends PnlCmdBase
{
    private final ExEnClient    exenClient;
    private final PnlAux4Config config = new PnlAux4Config();

    //------------------------------------------------------------------------//

    public PnlDevice( IDevice device, PnlAllCmdsSelector.CommandWise cmdWise )
    {
        this( device, cmdWise, null );
    }

    public PnlDevice( IDevice device, PnlAllCmdsSelector.CommandWise cmdWise, ExEnClient exenCli )
    {
        super( cmdWise );

        initComponents();
        initExtra();

        exenClient = exenCli;

        if( device != null )
        {
            txtName.setText( device.name() );

            // Device INIT ---------------------------

            if( device.delta() != null )
                spnDelta.getModel().setValue( device.delta() );

            txtGroups.setText( UtilColls.toString( device.groups() ) );

            // DRIVER --------------------------------

            cmbDriverName.setSelectedItem( device.getDriverName() );
            config.setConfig( device.getDriverInit() );
        }
    }

    //------------------------------------------------------------------------//

    @Override
    public String getSourceCode()
    {
        // Device init --------------------------------------------

        float  nDelta    = ((Number) spnDelta.getModel().getValue()).floatValue();
        long   nDowntime = ((Number) spnDowntimed.getModel().getValue()).longValue();
        String sDelta    = ((nDelta    <= 0) ? "" : "\t\tdelta = "+ nDelta);
        String sDowntime = ((nDowntime <= 0) ? "" : ("\t\tdowntimed = "+ nDowntime + PnlCmdBase.getTimeUnitFromComboBox( cmbTimeUnit )));
        String sGroups   = (txtGroups.getText().trim().isEmpty() ? "" : "\t\tgroups = \""+ txtGroups.getText().trim() +'"');
        String rawValue  = txtValue.getText().trim();
        String sValue    = "";

        if( ! rawValue.isEmpty() )
        {
            if( (! Language.isBooleanOp( rawValue )) && (! Language.isNumber( rawValue )) )
                rawValue = Language.toString( rawValue );
            sValue = "\t\tvalue = "+ rawValue;
        }

        String sInit = sValue +'\n'+ sDelta +'\n'+ sDowntime +'\n'+ sGroups;

        while( (! sInit.isEmpty()) && (sInit.charAt(0) == '\n') )
            sInit = sInit.substring(1);

        if( ! sInit.trim().isEmpty() )
            sInit = "\n\tINIT\n"+ sInit;

        // Driver config ------------------------------------------

        String sConfig = (cmbDriverName.getSelectedItem() == null) ? "" : ("\n\tDRIVER "+ cmbDriverName.getSelectedItem());     // DRIVER CONFIG

        if( ! sConfig.isEmpty() )
            sConfig += config.getSourceCode();

        // --------------------------------------------------------

        return "DEVICE " +
               txtName.getText().trim() +
               sInit +
               sConfig;
    }

    @Override
    public String getTranspiled()
    {
        String      src   = getSourceCode();
        Lexer       lexer = new Lexer( src );
        ParseDevice tdevi = new ParseDevice( lexer.getLexemes(), UtilSys.getConfig().newXprEval() );

        return showErrors( src, lexer, tdevi) ? null : tdevi.serialize();
    }

    //------------------------------------------------------------------------//

    private boolean initExtra()
    {
        PnlCmdBase.setJTextIsUneName( txtName );

        cmbTimeUnit.setSelectedIndex( 0 );   // Millis
        spnDowntimed.setModel( new SpinnerNumberModel( 0,              // initial value
                                                       0,              // min
                                                       Long.MAX_VALUE, // max
                                                       1 ) );          // step;

        spnDelta.setModel( new SpinnerNumberModel( 0,               // initial value
                                                   0,               // min
                                                   Float.MAX_VALUE, // max
                                                   1 ) );           // step;

        pnl4Config.add( config, BorderLayout.CENTER );;

        btnSendMsg2Actuator.setIcon( IconFontSwing.buildIcon( FontAwesome.TELEGRAM, 16, JTools.getIconColor() ) );
        btnSendMsg2Actuator.setEnabled( exenClient != null );

        for( IDriver driver : cmdWise.getDrivers() )
            cmbDriverName.addItem( driver.name() );

        return (cmbDriverName.getItemCount() > 0);
    }

    /**
     * This method is called from within the constructor to initialize the form. WARNING: Do NOT modify this code. The content of this method is always regenerated by the Form Editor.
     */
    @SuppressWarnings("unchecked")
    // <editor-fold defaultstate="collapsed" desc="Generated Code">//GEN-BEGIN:initComponents
    private void initComponents()
    {

        radGroupDeviceType = new javax.swing.ButtonGroup();
        txtName = new javax.swing.JTextField();
        jLabel2 = new javax.swing.JLabel();
        btnSendMsg2Actuator = new javax.swing.JButton();
        pnlDeviceInit = new javax.swing.JPanel();
        lblDelta = new javax.swing.JLabel();
        spnDelta = new javax.swing.JSpinner();
        jLabel8 = new javax.swing.JLabel();
        txtGroups = new javax.swing.JTextField();
        lblDelta1 = new javax.swing.JLabel();
        spnDowntimed = new javax.swing.JSpinner();
        jLabel1 = new javax.swing.JLabel();
        txtValue = new javax.swing.JTextField();
        cmbTimeUnit = new javax.swing.JComboBox<>();
        pnlDriver = new javax.swing.JPanel();
        cmbDriverName = new javax.swing.JComboBox<>();
        pnl4Config = new javax.swing.JPanel();

        txtName.setColumns(24);
        txtName.setCursor(new java.awt.Cursor(java.awt.Cursor.DEFAULT_CURSOR));
        txtName.setName("ControlName"); // NOI18N

        jLabel2.setText("Name");

        btnSendMsg2Actuator.setText("Send msg");
        btnSendMsg2Actuator.addActionListener(new java.awt.event.ActionListener()
        {
            public void actionPerformed(java.awt.event.ActionEvent evt)
            {
                btnSendMsg2ActuatorActionPerformed(evt);
            }
        });

        pnlDeviceInit.setBorder(javax.swing.BorderFactory.createTitledBorder(" Device INIT "));

        lblDelta.setText("Delta");

        spnDelta.setModel(new javax.swing.SpinnerNumberModel(0.0f, 0.0f, null, 1.0f));

        jLabel8.setText("Groups (comma separated)");

        lblDelta1.setText("Downtimed");

        spnDowntimed.setModel(new javax.swing.SpinnerNumberModel(0.0f, 0.0f, null, 1.0f));

        jLabel1.setText("Value");

        cmbTimeUnit.setModel(new javax.swing.DefaultComboBoxModel<>(new String[] { "1/1000 of a second", "1/100 of a second (u)", "1/10 of a second (t)", "Seconds (s)", "Minutes (m)", "Hours (h)", "Days (d)" }));

        javax.swing.GroupLayout pnlDeviceInitLayout = new javax.swing.GroupLayout(pnlDeviceInit);
        pnlDeviceInit.setLayout(pnlDeviceInitLayout);
        pnlDeviceInitLayout.setHorizontalGroup(
            pnlDeviceInitLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(pnlDeviceInitLayout.createSequentialGroup()
                .addContainerGap()
                .addGroup(pnlDeviceInitLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addGroup(pnlDeviceInitLayout.createSequentialGroup()
                        .addComponent(lblDelta)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(spnDelta, javax.swing.GroupLayout.PREFERRED_SIZE, 96, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addGap(18, 18, 18)
                        .addComponent(jLabel8)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(txtGroups))
                    .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, pnlDeviceInitLayout.createSequentialGroup()
                        .addComponent(jLabel1)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(txtValue)
                        .addGap(18, 18, 18)
                        .addComponent(lblDelta1)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(spnDowntimed, javax.swing.GroupLayout.PREFERRED_SIZE, 87, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(cmbTimeUnit, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)))
                .addContainerGap())
        );
        pnlDeviceInitLayout.setVerticalGroup(
            pnlDeviceInitLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(pnlDeviceInitLayout.createSequentialGroup()
                .addGap(11, 11, 11)
                .addGroup(pnlDeviceInitLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(jLabel1)
                    .addComponent(txtValue, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(lblDelta1)
                    .addComponent(spnDowntimed, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(cmbTimeUnit, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addGap(18, 18, 18)
                .addGroup(pnlDeviceInitLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(lblDelta)
                    .addComponent(spnDelta, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(jLabel8)
                    .addComponent(txtGroups, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addContainerGap(javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
        );

        pnlDriver.setBorder(javax.swing.BorderFactory.createTitledBorder(" DRIVER "));

        pnl4Config.setLayout(new java.awt.BorderLayout());

        javax.swing.GroupLayout pnlDriverLayout = new javax.swing.GroupLayout(pnlDriver);
        pnlDriver.setLayout(pnlDriverLayout);
        pnlDriverLayout.setHorizontalGroup(
            pnlDriverLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, pnlDriverLayout.createSequentialGroup()
                .addContainerGap()
                .addGroup(pnlDriverLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.TRAILING)
                    .addComponent(pnl4Config, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                    .addComponent(cmbDriverName, 0, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
                .addContainerGap())
        );
        pnlDriverLayout.setVerticalGroup(
            pnlDriverLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(pnlDriverLayout.createSequentialGroup()
                .addContainerGap()
                .addComponent(cmbDriverName, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addGap(18, 18, 18)
                .addComponent(pnl4Config, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                .addContainerGap())
        );

        javax.swing.GroupLayout layout = new javax.swing.GroupLayout(this);
        this.setLayout(layout);
        layout.setHorizontalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, layout.createSequentialGroup()
                .addContainerGap()
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.TRAILING)
                    .addComponent(pnlDriver, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                    .addComponent(pnlDeviceInit, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                    .addGroup(javax.swing.GroupLayout.Alignment.LEADING, layout.createSequentialGroup()
                        .addComponent(jLabel2)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(txtName, javax.swing.GroupLayout.DEFAULT_SIZE, 429, Short.MAX_VALUE)
                        .addGap(18, 18, 18)
                        .addComponent(btnSendMsg2Actuator, javax.swing.GroupLayout.PREFERRED_SIZE, 116, javax.swing.GroupLayout.PREFERRED_SIZE)))
                .addContainerGap())
        );
        layout.setVerticalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(layout.createSequentialGroup()
                .addContainerGap()
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(jLabel2)
                    .addComponent(txtName, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(btnSendMsg2Actuator))
                .addGap(18, 18, 18)
                .addComponent(pnlDeviceInit, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addGap(18, 18, 18)
                .addComponent(pnlDriver, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                .addContainerGap())
        );
    }// </editor-fold>//GEN-END:initComponents

    private void btnSendMsg2ActuatorActionPerformed(java.awt.event.ActionEvent evt)//GEN-FIRST:event_btnSendMsg2ActuatorActionPerformed
    {//GEN-HEADEREND:event_btnSendMsg2ActuatorActionPerformed
        GDialog dlg = new GDialog( "Change Actuator State", true );

        PnlChangeActuator pnl = new PnlChangeActuator();

        JButton btn = new JButton( "Send" );
                btn.addActionListener( (ae) ->
                                        {
                                            if( pnl.getValue() != null )
                                            {
                                                dlg.dispose();
                                                exenClient.sendRequest2ChangeActuator( txtName.getText().trim().toLowerCase(), pnl.getValue() );
                                            }
                                        }  );

        dlg.add( pnl, BorderLayout.CENTER );
        dlg.add( btn, BorderLayout.SOUTH  );
        dlg.getRootPane().setDefaultButton( btn );
        dlg.setVisible();
    }//GEN-LAST:event_btnSendMsg2ActuatorActionPerformed

    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.JButton btnSendMsg2Actuator;
    private javax.swing.JComboBox<String> cmbDriverName;
    private javax.swing.JComboBox<String> cmbTimeUnit;
    private javax.swing.JLabel jLabel1;
    private javax.swing.JLabel jLabel2;
    private javax.swing.JLabel jLabel8;
    private javax.swing.JLabel lblDelta;
    private javax.swing.JLabel lblDelta1;
    private javax.swing.JPanel pnl4Config;
    private javax.swing.JPanel pnlDeviceInit;
    private javax.swing.JPanel pnlDriver;
    private javax.swing.ButtonGroup radGroupDeviceType;
    private javax.swing.JSpinner spnDelta;
    private javax.swing.JSpinner spnDowntimed;
    private javax.swing.JTextField txtGroups;
    private javax.swing.JTextField txtName;
    private javax.swing.JTextField txtValue;
    // End of variables declaration//GEN-END:variables
}