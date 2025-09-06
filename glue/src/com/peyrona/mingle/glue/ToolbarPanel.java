
package com.peyrona.mingle.glue;

import com.peyrona.mingle.glue.codeditor.UneMultiEditorPanel;
import com.peyrona.mingle.glue.exen.Pnl4ExEn;
import com.peyrona.mingle.glue.gswing.GButton;
import com.peyrona.mingle.glue.gswing.GDialog;
import com.peyrona.mingle.glue.gswing.GFrame;
import com.peyrona.mingle.lang.japi.UtilSys;
import java.awt.BorderLayout;
import java.awt.Desktop;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ContainerEvent;
import java.awt.event.ContainerListener;
import java.awt.event.KeyEvent;
import java.awt.event.WindowEvent;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.KeyStroke;
import javax.swing.SwingUtilities;
import javax.swing.event.ChangeEvent;
import jiconfont.icons.font_awesome.FontAwesome;
import jiconfont.swing.IconFontSwing;

/**
 *
 * @author Francisco José Morero Peyrona
 *
 * Official web site at: <a href="https://mingle.peyrona.com">https://mingle.peyrona.com</a>
 *
 * Official web site at: <a href="https://mingle.peyrona.com">https://mingle.peyrona.com</a>
 */
final class ToolbarPanel extends javax.swing.JPanel
{
    private Process procExEn  = null;
    private Process procGum   = null;
    private GFrame  frmEditor = null;

    private javax.swing.JButton btnAdd;
    private javax.swing.JButton btnDel;
    private javax.swing.JButton btnEdit;
    private javax.swing.JButton btnExEn;
    private javax.swing.JButton btnGum;
    private javax.swing.JButton btnInfo;
    private javax.swing.JButton btnLoad;
    private javax.swing.JButton btnSave;

    //------------------------------------------------------------------------//

    /**
     * Creates new form PanelToolBar
     */
    public ToolbarPanel()
    {
        setLayout( new FlowLayout( FlowLayout.LEFT )  );

        initComponents();
        initExtra();
        updateButtonsState();
    }

    //------------------------------------------------------------------------//

    public boolean close()
    {
        if( (frmEditor != null) && (! frmEditor.isClosed()) )
        {
            UneMultiEditorPanel umep = JTools.getChild( frmEditor, UneMultiEditorPanel.class );

            if( umep.isAnyScriptUnsaved() && JTools.confirm( "There is one or more unsaved modified scripts.\nDo you want to cancel exiting?" ) )
            {
                return false;
            }

            frmEditor.dispatchEvent( new WindowEvent( frmEditor, WindowEvent.WINDOW_CLOSING ) );    // Gently tells the editor to close
        }

        if( (procExEn != null) && JTools.confirm( "You started an 'ExEn'.\nDo you want to stop it?" ) )
            Util.killProcess( procExEn );

        if( (procGum != null) && JTools.confirm( "You started 'Gum'.\nDo you want to stop it?" ) )
            Util.killProcess( procGum );

        return true;
    }

    //------------------------------------------------------------------------//

    private void initExtra()
    {
        JTools.getOfClass( this, JButton.class ).forEach( (btn) -> btn.setPreferredSize( new Dimension( 39,37 ) ) );

        Main.frame
            .getRootPane()
            .registerKeyboardAction( (ActionListener) -> onInfo(),
                                     KeyStroke.getKeyStroke( KeyEvent.VK_F1, 0 ),
                                     JComponent.WHEN_IN_FOCUSED_WINDOW );
        Main.frame
            .getRootPane()
            .registerKeyboardAction( (ActionListener) -> onConnect2ExEn(),
                                     KeyStroke.getKeyStroke( KeyEvent.VK_F2, 0 ),
                                     JComponent.WHEN_IN_FOCUSED_WINDOW );
        Main.frame
            .getRootPane()
            .registerKeyboardAction( (ActionListener) -> onRunLocalExEn(),
                                     KeyStroke.getKeyStroke( KeyEvent.VK_F3, 0 ),
                                     JComponent.WHEN_IN_FOCUSED_WINDOW );
        Main.frame
            .getRootPane()
            .registerKeyboardAction( (ActionListener) -> onUneEditor(),
                                     KeyStroke.getKeyStroke( KeyEvent.VK_F4, 0 ),
                                     JComponent.WHEN_IN_FOCUSED_WINDOW );
        Main.frame
            .getRootPane()
            .registerKeyboardAction( (ActionListener) -> onRunStopGum(),
                                     KeyStroke.getKeyStroke( KeyEvent.VK_F5, 0 ),
                                     JComponent.WHEN_IN_FOCUSED_WINDOW );

        // Tabs added or removed
        Main.frame.getExEnTabsPane().addContainerListener( new ContainerListener()
        {
            @Override
            public void componentAdded( ContainerEvent ce )    { updateButtonsState(); }

            @Override
            public void componentRemoved( ContainerEvent ce )  { updateButtonsState(); }
        } );

        // Selected tab changed
        Main.frame.getExEnTabsPane().addChangeListener( (ChangeEvent ce) -> updateButtonsState() );
    }

    private void updateButtonsState()
    {
        int nTabCount = Main.frame.getExEnTabsPane().getTabCount();

        btnLoad.setEnabled( nTabCount > 0 );     // Load File
        btnSave.setEnabled( nTabCount > 0 );
        btnDel.setEnabled(  nTabCount > 0 );
        btnAdd.setEnabled(  true );
        btnEdit.setEnabled( true );
        btnInfo.setEnabled( true );
    }

    /**
     * This method is called from within the constructor to initialize the form.
     * WARNING: Do NOT modify this code. The content of this method is always
     * regenerated by the Form Editor.
     */
    private void initComponents()
    {
        btnAdd  = new GButton(this).setIcon( FontAwesome.PLUG    , 16 ).addAction( (ActionEvent evt) -> onConnect2ExEn()     ).setToolTip( "Connect to an ExEn [F2]" );
        btnSave = new GButton(this).setIcon( FontAwesome.FOLDER  , 16 ).addAction( (ActionEvent evt) -> onSaveCurrentModel() ).setToolTip( "Save current model" );
        btnDel  = new GButton(this).setIcon( FontAwesome.FLOPPY_O, 16 ).addAction( (ActionEvent evt) -> onEmptyExEn()        ).setToolTip( "Empty current model" );
        btnLoad = new GButton(this).setIcon( FontAwesome.TRASH   , 16 ).addAction( (ActionEvent evt) -> onLoadModel()        ).setToolTip( "Load model from file and deploy it to a ExEn linked in Glue" );
        btnExEn = new GButton(this).setIcon( FontAwesome.PLAY    , 16 ).addAction( (ActionEvent evt) -> onRunLocalExEn()     ).setToolTip( "Executes a local ExEn (Stick) using default local configuration file [F3]" );
        btnEdit = new GButton(this).setIcon( FontAwesome.PENCIL  , 16 ).addAction( (ActionEvent evt) -> onUneEditor()        ).setToolTip( "Editor for Une script and configuration files [F4]" );     // Editor for Une files (always enabled)
        btnGum  = new GButton(this).setIcon( FontAwesome.CLOUD   , 16 ).addAction( (ActionEvent evt) -> onRunStopGum()       ).setToolTip( "Executes WebServer to manage Dashboards (Gum) at 'localhost:8080' [F5]" );
        btnInfo = new GButton(this).setIcon( FontAwesome.INFO    , 16 ).addAction( (ActionEvent evt) -> onInfo()             ).setToolTip( "About dialog with a reset-tool-tips button [F1]" );
    }

    private void onLoadModel() {
        Main.frame.getExEnTabsPane().onLoadModel();
    }

    private void onConnect2ExEn() {
        Main.frame.getExEnTabsPane().add();
    }

    /**
     * This action removes all commands from highlighted ExEn sending requests to the ExEn.
     * @param evt
     */
    private void onEmptyExEn() {

        if( JTools.confirm( "Do you want to remove all commands?" ) )
            Main.frame.getExEnTabsPane().clear();
    }

    /**
     * Starts an ExEn in localhost or stops it if it was previously started.
     *
     * @return The process instance or null if any error.
     */
    private void onRunLocalExEn()
    {
        if( procExEn != null )     // The icon now is not a "play" but a "stop"
        {
            if( JTools.confirm( "Internal Stick (ExEn) is running.\nDo you want to stop it?" ) )
            {
                Util.killProcess( procExEn );
                procExEn = null;

                Main.frame.getExEnTabsPane().del();
                btnExEn.setIcon( IconFontSwing.buildIcon( FontAwesome.PLAY, 16, JTools.getIconColor() ) );
                btnExEn.setToolTipText( "Executes internally a local Stick (ExEn) using its configuration file" );
            }
        }
        else
        {
            JTools.showWaitFrame( "Starting internal local Stick (ExEn)" );

            procExEn = Util.runStick();

            if( procExEn == null )
            {
                JTools.hideWaitFrame();
                JTools.error( "Unable to start local internal ExEn.\nIt could be there is another instance of ExEn already running." );
                return;
            }

            UtilSys.execute( getClass().getName(),
                             1500,
                             () ->     // Stick needs some time to be ready
                                {
                                    JTools.hideWaitFrame();

                                    Main.frame.getExEnTabsPane().add( (Pnl4ExEn pnl) -> pnl.addExenOutputTab( procExEn ) );

                                    SwingUtilities.invokeLater( () ->
                                        {
                                            btnExEn.setIcon( IconFontSwing.buildIcon( FontAwesome.STOP, 16, JTools.getIconColor() ) );
                                            btnExEn.setToolTipText( "Stops local internal Stick (ExEn)" );
                                            Tip.show( "After the ExEn is running, you can:\n\n"+
                                                      "   * Load a '.model' file (click 'folder' icon in toolbar)\n\n"+
                                                      "   * Create new commands using this application (Glue)" );
                                        } );
                                } );
        }
    }

    private void onSaveCurrentModel()
    {
        Main.frame.getExEnTabsPane().save();
    }

    private void onUneEditor()
    {
        frmEditor = GFrame.make()
                          .title( "Editor for the Mingle Standard Platform (MSP)" )
                          .icon( "editor.png" )
                          .put( new UneMultiEditorPanel(), BorderLayout.CENTER )
                          .onClose( GFrame.DISPOSE_ON_CLOSE )
                          .setVisible()
                          .size( -1, 90 );    // -1 keeps width, but works only after :pack()
    }

    private void onRunStopGum()
    {
        if( ! UtilSys.isAtLeastJava11() )
        {
            JTools.alert( "Java version at folder: "+ UtilSys.getJavaHome() +'\n'+
                          "is "+ System.getProperty( "java.version" ) +". But minimum needed to run Gum is Java 11." );
            return;
        }

        if( procGum == null )
        {
            JTools.showWaitFrame( "Starting Gum and default WebBrowser..." );

            procGum = Util.runGum();

            UtilSys.execute( getClass().getName(),
                             1500,
                             () ->    // Awful but useful
                                {
                                    try
                                    {
                                       Desktop.getDesktop().browse( new URI( "http://localhost:8080/gum" ) );
                                       JTools.hideWaitFrame();
                                    }
                                    catch( IOException | URISyntaxException exc )
                                    {
                                        JTools.error( exc );
                                    }
                                } );
        }
        else if( JTools.confirm( "Are you sure you want to stop Gum WebServer?" ) )
        {
            Util.killProcess( procGum );
            procGum = null;
        }
    }

    private void onInfo()
    {
        GDialog dlg = new GDialog( "About...", false );
                dlg.add( new pnlInfo(), BorderLayout.CENTER );
                dlg.setResizable( false );
                dlg.setVisible();
    }
}