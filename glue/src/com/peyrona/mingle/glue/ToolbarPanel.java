
package com.peyrona.mingle.glue;

import com.eclipsesource.json.JsonArray;
import com.eclipsesource.json.JsonObject;
import com.peyrona.mingle.glue.codeditor.UneMultiEditorPanel;
import com.peyrona.mingle.glue.gswing.GButton;
import com.peyrona.mingle.glue.gswing.GDialog;
import com.peyrona.mingle.glue.gswing.GFrame;
import com.peyrona.mingle.glue.gswing.GTip;
import com.peyrona.mingle.lang.interfaces.ILogger;
import com.peyrona.mingle.lang.japi.UtilColls;
import com.peyrona.mingle.lang.japi.UtilSys;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Desktop;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ContainerEvent;
import java.awt.event.ContainerListener;
import java.awt.event.KeyEvent;
import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JFrame;
import javax.swing.KeyStroke;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;
import javax.swing.event.ChangeEvent;
import javax.swing.filechooser.FileNameExtensionFilter;
import jiconfont.icons.font_awesome.FontAwesome;
import jiconfont.swing.IconFontSwing;

/**
 *
 * @author Francisco José Morero Peyrona
 *
 * Official web site at: <a href="https://github.com/peyrona/mingle">https://github.com/peyrona/mingle</a>
 */
final class ToolbarPanel extends javax.swing.JPanel
{
    private Process procExEn  = null;
    private Process procGum   = null;
    private GFrame  wndExEn   = null;
    private GFrame  wndGum    = null;
    private GFrame  frmEditor = null;

    private JButton btnAdd;
    private JButton btnDel;
    private JButton btnEdit;
    private JButton btnExEn;
    private JButton btnGum;
    private JButton btnInfo;
    private JButton btnLoad;
    private JButton btnMode;
    private JButton btnSave;

    //------------------------------------------------------------------------//

    /**
     * Creates new form PanelToolBar
     */
    public ToolbarPanel()
    {
        setLayout( new FlowLayout( FlowLayout.LEFT ) );

        initComponents();
        initExtra();
        updateButtonsState();
    }

    //------------------------------------------------------------------------//

    public boolean isUserFeedbackNeeded()
    {
        boolean isUnsaved =   frmEditor != null    &&
                            ! frmEditor.isClosed() &&
                            JTools.getChild( frmEditor, UneMultiEditorPanel.class ).isAnyFileUnsaved();

        boolean isEnExRunning = procExEn != null;

        boolean isGumRunning  = procGum  != null;

        return isUnsaved || isEnExRunning || isGumRunning;
    }

    public boolean close( boolean bForce )
    {
        if( (! bForce)          &&   // When bForced, the open files can not have an opportunity to close or ask user
            (frmEditor != null) &&
            (! frmEditor.isClosed()) )
        {
            JTools.getChild( frmEditor, UneMultiEditorPanel.class ).closeAll( frmEditor );
        }

        if( (procExEn != null) && (bForce || JTools.confirm( "You started an 'ExEn'.\nDo you want to stop it?" )) )
            Util.killProcess( procExEn );    // When bForced, the process has to be killed

        if( (procGum != null) && (bForce || JTools.confirm( "You started 'Gum'.\nDo you want to stop it?" )) )
            Util.killProcess( procGum );     // When bForced, the process has to be killed

        return true;
    }

    //------------------------------------------------------------------------//
    // PRIVATE SCOPE

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
                                     KeyStroke.getKeyStroke( KeyEvent.VK_F4, 0 ),
                                     JComponent.WHEN_IN_FOCUSED_WINDOW );
        Main.frame
            .getRootPane()
            .registerKeyboardAction( (ActionListener) -> onRunStopExEn(),
                                     KeyStroke.getKeyStroke( KeyEvent.VK_F5, 0 ),
                                     JComponent.WHEN_IN_FOCUSED_WINDOW );
        Main.frame
            .getRootPane()
            .registerKeyboardAction( (ActionListener) -> onOpenEditor(),
                                     KeyStroke.getKeyStroke( KeyEvent.VK_F2, 0 ),
                                     JComponent.WHEN_IN_FOCUSED_WINDOW );
        Main.frame
            .getRootPane()
            .registerKeyboardAction( (ActionListener) -> onRunStopGum(),
                                     KeyStroke.getKeyStroke( KeyEvent.VK_F8, 0 ),
                                     JComponent.WHEN_IN_FOCUSED_WINDOW );

        // Tabs added or removed
        Main.frame.getExEnsTabPane().addContainerListener( new ContainerListener()
        {
            @Override
            public void componentAdded( ContainerEvent ce )    { updateButtonsState(); }

            @Override
            public void componentRemoved( ContainerEvent ce )  { updateButtonsState(); }
        } );

        // Selected tab changed
        Main.frame.getExEnsTabPane().addChangeListener( (ChangeEvent ce) -> updateButtonsState() );
    }

    private void updateButtonsState()
    {
        boolean bExEn = Main.frame.getExEnsTabPane().getTabCount() > 0
                        ||
                        procExEn != null;

        btnLoad.setEnabled( bExEn );
        btnSave.setEnabled( bExEn );
        btnDel.setEnabled(  bExEn );
        btnAdd.setEnabled(  true  );
        btnEdit.setEnabled( true  );
        btnMode.setEnabled( true  );
        btnInfo.setEnabled( true  );
    }

    /**
     * This method is called from within the constructor to initialize the form.
     * WARNING: Do NOT modify this code. The content of this method is always
     * regenerated by the Form Editor.
     */
    private void initComponents()
    {
        FontAwesome icnMode = SettingsManager.isDarkMode() ? FontAwesome.SUN_O : FontAwesome.MOON_O;

        btnAdd  = new GButton(this).setIcon( FontAwesome.PLUG    , 16 ).addAction( (ActionEvent evt) -> onConnect2ExEn() ).setToolTip( "Connect to an ExEn already running [F4]" );
        btnExEn = new GButton(this).setIcon( FontAwesome.PLAY    , 16 ).addAction( (ActionEvent evt) -> onRunStopExEn()  ).setToolTip( "Execute a local empty ExEn (Stick) using default local configuration file [F5]" );
        btnLoad = new GButton(this).setIcon( FontAwesome.FOLDER  , 16 ).addAction( (ActionEvent evt) -> onLoadModel()    ).setToolTip( "Inject an existing model to selected ExEn" );
        btnSave = new GButton(this).setIcon( FontAwesome.FLOPPY_O, 16 ).addAction( (ActionEvent evt) -> onSaveModel()    ).setToolTip( "Save selected ExEn model to file" );
        btnDel  = new GButton(this).setIcon( FontAwesome.TRASH   , 16 ).addAction( (ActionEvent evt) -> onClearExEn()    ).setToolTip( "Empty selected ExEn model: delete all rules and devices" );
        btnGum  = new GButton(this).setIcon( FontAwesome.CLOUD   , 16 ).addAction( (ActionEvent evt) -> onRunStopGum()   ).setToolTip( "Executes WebServer to manage Dashboards (Gum) at 'localhost:8080' [F8]" );
        btnEdit = new GButton(this).setIcon( FontAwesome.PENCIL  , 16 ).addAction( (ActionEvent evt) -> onOpenEditor()   ).setToolTip( "Editor for Une scripts, configuration files and other types of files [F2]" );  // Editor is always enabled
        btnMode = new GButton(this).setIcon( icnMode             , 16 ).addAction( (ActionEvent evt) -> onToggleMode()   ).setToolTip( "Alternate between Light and Dark modes" );
        btnInfo = new GButton(this).setIcon( FontAwesome.INFO    , 16 ).addAction( (ActionEvent evt) -> onInfo()         ).setToolTip( "About dialog with two reset buttons [F1]" );
    }

    private void onConnect2ExEn()
    {
        JsonArray jaClients = UtilSys.getConfig().get( "network", "clients", new JsonArray() );

        final ConnectDlg dlg = new ConnectDlg( jaClients );
                         dlg.setVisible();

        final String     sConnName  = dlg.getConnName();
        final JsonObject joProtocol = dlg.getSelectedProtocol();

        dlg.dispose();

        if( joProtocol == null )
            return;

        JTools.showWaitFrame( "Connecting to " + sConnName );

        final ExEnClient client = new ExEnClient( joProtocol, sConnName );

        SwingWorker<Void, Void> worker = new SwingWorker<Void, Void>()
        {
            private Exception connectionError = null;

            @Override
            protected Void doInBackground()
            {
                try
                {
                    client.connect();
                }
                catch( Exception exc )
                {
                    connectionError = exc;
                }

                return null;
            }

            @Override
            protected void done()
            {
                JTools.hideWaitFrame();
                updateButtonsState();

                if( connectionError == null )
                {
                    Main.frame.getExEnsTabPane().add( sConnName, client );
                    validate();
                }
                else
                {
                    UtilSys.getLogger().log( ILogger.Level.WARNING, connectionError );
                    JTools.error( connectionError );
                }
            }
        };

        worker.execute();
    }

    /**
     * Starts an ExEn in localhost or stops it if it was previously started.
     *
     * @return The process instance or null if any error.
     */
    private void onRunStopExEn()
    {
        if( procExEn != null )     // Internal ExEn is running (the icon now is not a "play" but a "stop")
        {
            if( JTools.confirm( "Internal Stick (ExEn) is running.\nDo you want to stop it?" ) )
            {
                if( wndExEn != null )
                {
                    wndExEn.dispose();
                    wndExEn = null;
                }

                Util.killProcess( procExEn );
                procExEn = null;

                Main.frame.getExEnsTabPane().del();
                btnExEn.setIcon( IconFontSwing.buildIcon( FontAwesome.PLAY, 16, JTools.getIconColor() ) );
                btnExEn.setToolTipText( "Executes internally a local Stick (ExEn) using its configuration file" );
            }
        }
        else                       // Internal ExEn is not running (the icon now is not a "stop" but a "play")
        {
            JTools.showWaitFrame( "Starting internal local Stick (ExEn)" );

            procExEn = Util.runStick();

            if( procExEn == null )
            {
                JTools.hideWaitFrame();
                JTools.error( "Unable to start local internal ExEn.\nIt could be there is another instance of ExEn already running." );
            }
            else
            {
                wndExEn = new GFrame()
                              .title( "'Stick (ExEn)' console" )
                              .icon( "exen-256x256.png" )
                              .closeOnEsc()
                              .onClose( JFrame.DISPOSE_ON_CLOSE )
                              .onClose( (frm) -> onRunStopExEn() )
                              .setContent( new ConsolePanel() )
                              .setVisible()                       // Has to be before to setSize(...)
                              .size( 800, 500 );

                Util.catchOutput( procExEn, (str) -> ((ConsolePanel) wndExEn.getContent()).append( str ) );

                UtilSys.executor( true )
                       .delay( 1500 )
                       .execute( () ->     // Stick needs some time to be ready
                                    {
                                        if( procExEn == null || ! procExEn.isAlive() )
                                            return;

                                        SwingUtilities.invokeLater( () ->
                                            {
                                                btnExEn.setIcon( IconFontSwing.buildIcon( FontAwesome.STOP, 16, JTools.getIconColor() ) );
                                                btnExEn.setToolTipText( "Stops local internal Stick (ExEn)" );
                                                JTools.hideWaitFrame();
                                            } );

                                        JsonArray  jaClients  = UtilSys.getConfig().get( "network", "clients", new JsonArray() );
                                        JsonObject joProtocol = jaClients.isEmpty() ? null : jaClients.get( 0 ).asObject();

                                        if( joProtocol == null )
                                        {
                                            JTools.error( "No network client configured to connect to local ExEn" );
                                            return;
                                        }

                                        final String     sLocalExEn  = "Local ExEn";
                                        final ExEnClient localClient = new ExEnClient( joProtocol, sLocalExEn );

                                        try
                                        {
                                            localClient.connect();

                                            SwingUtilities.invokeLater( () ->
                                                {
                                                    Main.frame.getExEnsTabPane().add( sLocalExEn, localClient );
                                                    GTip.show( "ExEn is running. You can now:\n\n"+
                                                               "   * Load a '.model' file (click 'folder' icon in toolbar)\n\n"+
                                                               "   * Create new commands using this application (Glue)" );
                                                } );
                                        }
                                        catch( Exception exc )
                                        {
                                            JTools.error( "Could not connect to the local ExEn: " + exc.getMessage() );
                                        }
                                    } );
            }
        }

        updateButtonsState();
    }

    private void onLoadModel()
    {
        File[] aFiles = JTools.fileLoader( Main.frame, null, false,
                                           new FileNameExtensionFilter( "Select a model to load", "model" ) );

        if( UtilColls.isNotEmpty( aFiles ) )
            Main.frame.getExEnsTabPane().load( aFiles[0] );
    }

    private void onSaveModel()
    {
        Main.frame.getExEnsTabPane().save();
    }

    /**
     * This action removes all commands from highlighted ExEn sending requests to the ExEn.
     * @param evt
     */
    private void onClearExEn()
    {
        if( JTools.confirm( "Do you want to remove all commands?" ) )
            Main.frame.getExEnsTabPane().clear();
    }

    private void onOpenEditor()
    {
        if( frmEditor != null && ! frmEditor.isClosed() )
        {
            frmEditor.toFront();
        }
        else
        {
            frmEditor = new GFrame()
                              .title( "Editor for the Mingle Standard Platform (MSP)" )
                              .icon( "editor-256x256.png" )
                              .put( new UneMultiEditorPanel(), BorderLayout.CENTER )
                              .onClose( (frm) -> JTools.getChild( frm, UneMultiEditorPanel.class ).closeAll( frm ) )
                              .setVisible()
                              .sizeAsPercent( -1, 90 );    // -1 keeps width, but works only after :pack()
        }
    }

    private void onRunStopGum()
    {
        if( procGum != null )
        {
            if( wndGum != null )
            {
                wndGum.dispose();
                wndGum = null;
            }

            Util.killProcess( procGum );
            procGum = null;

            // Reset Gum icon to original color when it stops
            SwingUtilities.invokeLater( () ->
                {
                    btnGum.setIcon( IconFontSwing.buildIcon( FontAwesome.CLOUD, 16, JTools.getIconColor() ) );
                } );
        }
        else
        {
            JTools.showWaitFrame( "Starting Gum and default WebBrowser..." );

            procGum = Util.runGum();

            if( procGum == null )
            {
                JTools.hideWaitFrame();
                JTools.error( "Unable to start Gum." );
                return;
            }

            wndGum = new GFrame()
                         .title( "'Gum' console" )
                         .icon( "gum-256x256.png" )
                         .closeOnEsc()
                         .onClose( JFrame.DISPOSE_ON_CLOSE )
                         .onClose( (frm) -> { if( JTools.confirm( "Stop also Gum?", wndGum ) ) onRunStopGum(); } )
                         .setContent( new ConsolePanel() )
                         .setVisible()                       // Has to be before to setSize(...)
                         .size( 800, 500 );

            Util.catchOutput( procGum, (str) -> ((ConsolePanel) wndGum.getContent()).append( str ) );

            // Change Gum icon to blue when it starts
            SwingUtilities.invokeLater( () ->
                {
                    btnGum.setIcon( IconFontSwing.buildIcon( FontAwesome.CLOUD, 16, Color.PINK ) );
                    btnGum.setToolTipText( "Stops Gum WebServer" );
                } );

            // Awful but useful
            UtilSys.executor( true )
                   .delay( 2000 )
                   .execute( () ->
                                {
                                    if( isDesktopBrowseSupported() )
                                    {
                                        try
                                        {
                                           Desktop.getDesktop().browse( new URI( "http://localhost:8080/gum/index.html" ) );
                                           JTools.hideWaitFrame();
                                        }
                                        catch( IOException | URISyntaxException exc )
                                        {
                                            JTools.hideWaitFrame();
                                            JTools.error( "Failed to open browser: " + exc.getMessage() );
                                        }
                                    }
                                    else
                                    {
                                        JTools.hideWaitFrame();
                                        showBrowserNotSupportedDialog();
                                    }
                                } );
        }
    }

    private void onToggleMode()
    {
        if( JTools.toggleLaF() )
        {
            FontAwesome icon = SettingsManager.isDarkMode() ? FontAwesome.SUN_O : FontAwesome.MOON_O;
            ((GButton) btnMode).setIcon( icon, 16 );
        }
    }

    private void onInfo()
    {
        new GDialog( "About...", false )
               .setFixedSize( false )
               .put( new InfoPanel(), BorderLayout.CENTER )
               .setVisible();
    }

    //------------------------------------------------------------------------//
    // PRIVATE METHODS
    //------------------------------------------------------------------------//

    private static boolean isDesktopBrowseSupported()
    {
        return Desktop.isDesktopSupported() &&
               Desktop.getDesktop().isSupported( Desktop.Action.BROWSE );
    }

    private static void showBrowserNotSupportedDialog()
    {
        String url = "http://localhost:8080/gum";
        String message = "Cannot automatically open browser.\n\n" +
                        "Please manually open this URL:\n" +
                        url + "\n\n" +
                        "Would you like to copy this URL to clipboard?";

        if( JTools.confirm( message ) )
        {
            JTools.toClipboard( url );
            JTools.info( "URL copied to clipboard!\n\nPaste it in your browser to access Gum WebServer." );
        }
    }
}