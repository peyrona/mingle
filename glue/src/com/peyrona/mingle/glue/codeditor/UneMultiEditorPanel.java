
package com.peyrona.mingle.glue.codeditor;

import com.eclipsesource.json.JsonArray;
import com.eclipsesource.json.JsonObject;
import com.eclipsesource.json.JsonValue;
import com.peyrona.mingle.candi.unec.transpiler.UnecTools;
import com.peyrona.mingle.glue.SettingsManager;
import com.peyrona.mingle.glue.JTools;
import com.peyrona.mingle.glue.codeditor.UneEditorTabContent.UneEditorUnit;
import com.peyrona.mingle.glue.gswing.GFrame;
import com.peyrona.mingle.glue.gswing.GTabbedPane;
import com.peyrona.mingle.glue.gswing.GTip;
import com.peyrona.mingle.lang.MingleException;
import com.peyrona.mingle.lang.japi.UtilColls;
import com.peyrona.mingle.lang.japi.UtilStr;
import com.peyrona.mingle.lang.japi.UtilSys;
import com.peyrona.mingle.lang.japi.UtilUnit;
import java.awt.BorderLayout;
import java.awt.Toolkit;
import java.awt.Window;
import java.awt.datatransfer.StringSelection;
import java.awt.event.ActionEvent;
import java.awt.event.FocusEvent;
import java.awt.event.FocusListener;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.atomic.AtomicReference;
import javax.swing.JButton;
import javax.swing.JEditorPane;
import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.SwingUtilities;
import javax.swing.event.ChangeEvent;
import javax.swing.filechooser.FileNameExtensionFilter;

/**
 *
 * @author Francisco José Morero Peyrona
 *
 * Official web site at: <a href="https://github.com/peyrona/mingle">https://github.com/peyrona/mingle</a>
 */
public final class UneMultiEditorPanel extends JPanel
{
    private final UneEditorToolBar toolBar     = new UneEditorToolBar();
    private final GTabbedPane      tabbedPane  = new GTabbedPane();
    private final AtomicReference<ScheduledFuture> futureSave = new AtomicReference<>();

    //------------------------------------------------------------------------//
    // CONSTRUCTOR

    public UneMultiEditorPanel()
    {
        JTools.showWaitFrame( "Loading..." );

        setLayout( new BorderLayout() );

        add( toolBar   , BorderLayout.NORTH  );
        add( tabbedPane, BorderLayout.CENTER );

        toolBar.btnNew.addActionListener(     (e) -> onNew() );
        toolBar.btnOpen.addActionListener(    (e) -> onOpen() );
        toolBar.btnSave.addActionListener(    (e) -> onSave( null ) );    // null == save all
        toolBar.btnUndo.addActionListener(    (e) -> { UneEditorUnit unit = getFocusedUnit(); if(unit != null) unit.undo(); } );
        toolBar.btnRedo.addActionListener(    (e) -> { UneEditorUnit unit = getFocusedUnit(); if(unit != null) unit.redo(); } );
        toolBar.btnRTF.addActionListener(     (e) -> { UneEditorUnit unit = getFocusedUnit(); if(unit != null) unit.copyAsRTF(); } );
        toolBar.btnNext.addActionListener(    (e) -> { UneEditorUnit unit = getFocusedUnit(); if(unit != null) unit.findNextSearch( toolBar.txtSearch.getText() ); } );
        toolBar.btnPrev.addActionListener(    (e) -> { UneEditorUnit unit = getFocusedUnit(); if(unit != null) unit.findPreviousSearch( toolBar.txtSearch.getText() ); } );
        toolBar.btnHelp.addActionListener(    (e) -> onHelp() );
        toolBar.btnRuns.addActionListener(    (e) -> onRun() );
        toolBar.btnRepl.addActionListener(    (e) -> onReplace( false ) );
        toolBar.btnReplAll.addActionListener( (e) -> onReplace( true ) );
        toolBar.btnComment.addActionListener( (e) -> onToogleRem()  );
        toolBar.btnTranspi.addActionListener( (e) -> onTranspile()  );

        // --------------------------------------------------------------------

        tabbedPane.addChangeListener( (ChangeEvent ce) -> onSelectedTabChanged() );

        toolBar.chkAutoSave.addChangeListener( (ChangeEvent ce) -> autoSaveStateChanged() );

        toolBar.cmbTemplate.addActionListener( (ActionEvent ae) -> onInsertCmdTemplate() );

        // About searchText and replaceText --------------------------------------------------------------------------------
        toolBar.txtSearch.addKeyListener( new KeyAdapter()    // Can not use Lambda
                                    {  @Override
                                        public void keyReleased( KeyEvent ke )
                                        {
                                            toolBar.updateSearchAndReplaceUI( getFocusedUnit() );
                                        }
                                    } );

        toolBar.txtReplace.addKeyListener( new KeyAdapter()    // Can not use Lambda
                                    {  @Override
                                        public void keyReleased( KeyEvent ke )
                                        {
                                            toolBar.updateSearchAndReplaceUI( getFocusedUnit() );
                                        }
                                    } );

        toolBar.lblSearch.addMouseListener( new MouseAdapter()
                                    {   @Override
                                        public void mouseClicked( MouseEvent me )
                                        {
                                            toolBar.clearSearchAndReplaceUI( getFocusedUnit() );
                                        }
                                    } );

        toolBar.lblReplace.addMouseListener( new MouseAdapter()
                                    {   @Override
                                        public void mouseClicked( MouseEvent me )
                                        {
                                            toolBar.txtReplace.setText( null );
                                        }
                                    } );
        //------------------------------------------------------------------------------------------------------------------

        toolBar.txtSearch.addFocusListener( new FocusListener()
                                            {
                                                @Override
                                                public void focusGained(FocusEvent fe)
                                                {
                                                    if( (getFocusedUnit() != null) && (! getFocusedUnit().getSelectedText().isEmpty()) )
                                                        toolBar.txtSearch.setText( getFocusedUnit().getSelectedText() );
                                                }

                                                @Override
                                                public void focusLost(FocusEvent fe) { }
                                            });

        //------------------------------------------------------------------------------------------------------------------

        UtilSys.execute( getClass().getName(),
                         250,
                         () -> // Dirty, but works (I tried many other things but none worked)
                                {
                                    JFrame parent = JTools.getParent( this, JFrame.class );

                                    if( parent == null )
                                        throw new MingleException( "This should not happen" );

                                    // Copy, Paste, Undo, Redo and SelectAll already have their shortcuts (done by RSyntaxTextArea)

                                    JTools.setShortCut( getRootPane(), KeyEvent.VK_F1    , false, false, evt -> onHelp()         );
                                    JTools.setShortCut( getRootPane(), KeyEvent.VK_H     , true , false, evt -> onHelp()         );
                                    JTools.setShortCut( getRootPane(), KeyEvent.VK_N     , true , false, evt -> onNew()          );
                                    JTools.setShortCut( getRootPane(), KeyEvent.VK_O     , true , false, evt -> onOpen()         );
                                    JTools.setShortCut( getRootPane(), KeyEvent.VK_S     , true , false, evt -> onSave( getFocusedUnit() ) );
                                    JTools.setShortCut( getRootPane(), KeyEvent.VK_S     , true , true , evt -> onSave( null )   );  // Ctrl+Shift+S
                                    JTools.setShortCut( getRootPane(), KeyEvent.VK_R     , true , false, evt -> onRun()          );
                                    JTools.setShortCut( getRootPane(), KeyEvent.VK_I     , true , false, evt -> onToogleRem()    );
                                    JTools.setShortCut( getRootPane(), KeyEvent.VK_T     , true , false, evt -> onTranspile()    );
                                    JTools.setShortCut( getRootPane(), KeyEvent.VK_F4    , true , false, evt -> close( -1   )    );
                                    JTools.setShortCut( getRootPane(), KeyEvent.VK_F     , true , true , evt -> onCopyFilePath() );
                                    JTools.setShortCut( getRootPane(), KeyEvent.VK_F     , true , false, evt -> toolBar.txtSearch.requestFocus() );
                                    JTools.setShortCut( getRootPane(), KeyEvent.VK_ESCAPE, false, false, evt -> toolBar.clearSearchAndReplaceUI( getFocusedUnit() ) );

                                    parent.setSize( 1024, (int) (Toolkit.getDefaultToolkit().getScreenSize().height * 0.9) );

                                    parent.addWindowListener( new WindowAdapter()
                                                                {
                                                                    @Override
                                                                    public void windowClosing( WindowEvent we )
                                                                    {
                                                                        UneMultiEditorPanel.this.closeAll( we.getWindow() );
                                                                    }
                                                                } );
                                    restoreOpenedFiles();
                                    JTools.hideWaitFrame();

                                    if( tabbedPane.getTabCount() == 0 )     // There were no saved file list
                                        onOpen();
                                } );


        SwingUtilities.invokeLater( () ->
                                    {
                                        autoSaveStateChanged();
                                        toolBar.chk4Grid.setSelected( UtilSys.getConfig().getGridNodes() != null );
                                        toolBar.chkFaked.setSelected( UtilSys.getConfig().get( "exen", "faked_drivers", false ) );
                                        toolBar.setLogLevel( UtilSys.getLogger().getLevel() );
                                        toolBar.updateButtons( getFocusedUnit(), getAllEditors() );
                                    } );
    }

    //------------------------------------------------------------------------//
    // PUBLIC SCOPE

    public boolean isAnyScriptUnsaved()
    {
        for( int n = 0; n < tabbedPane.getTabCount(); n++ )
        {
            if( ((UneEditorUnit) tabbedPane.getComponentAt( n )).isChanged() )
                return true;
        }

        return false;
    }

    //------------------------------------------------------------------------//
    // PRIVATE SCOPE

    private void onNew()
    {
        newTab( null );
        toolBar.updateButtons( getFocusedUnit(), getAllEditors() );
        getFocusedUnit().setDividerLocation( .95d );
    }

    private void onOpen()
    {
        GTip.show( "These are the files that are syntax highlighted:\n"+
                  "   a) Une source code (*.une)\n"+
                  "   b) Une transpiled code (*.model)\n"+
                  "   c) JSON configuration (*.json)\n"+
                  "   d) Java source code (*.java)\n"+
                  "   e) Python source code (*.py)\n"+
                  "   f) JavaScript source code (*.js)\n"+
                  "   g) HTML code (*.html)\n"+
                  "   h) SQL source code (*.sql)\n"+
                  "\n"+
                  "Use the filter control below the files to choose a type of file." );

        File[] aFiles = UtilColls.sort( JTools.fileLoader( this, getLastUsedDir(), true,
                                                            new FileNameExtensionFilter( "Une: source code script (*.une)", "une" ),
                                                            new FileNameExtensionFilter( "Model: transpiled script (*.model)", "model" ),
                                                            new FileNameExtensionFilter( "Configuration file (*.json)", "json" ),
                                                            new FileNameExtensionFilter( "Java source code (*.java)", "java" ),
                                                            new FileNameExtensionFilter( "Python source code (*.py)", "py" ),
                                                            new FileNameExtensionFilter( "Javascript source code (*.js)", "js" ),
                                                            new FileNameExtensionFilter( "HTML code (*.html)", "html" ),
                                                            new FileNameExtensionFilter( "SQL source code (*.sql)", "sql" ) ) );

        for( File file : aFiles )
        {
            if( ! isOpen( file ) )
                newTab( file );
        }

        if( aFiles.length > 0 )
            setLastUsedDir( aFiles[0] );

        JTools.getParent( this, JFrame.class ).toFront();
        toolBar.updateButtons( getFocusedUnit(), getAllEditors() );
    }

    private void onSave( UneEditorUnit editor )
    {
        if( editor != null )
        {
            editor.save();
        }
        else
        {
            for( int n = 0; n < tabbedPane.getTabCount(); n++ )
            {
                ((UneEditorUnit) tabbedPane.getComponentAt( n )).save();
            }
        }

        toolBar.updateButtons( getFocusedUnit(), getAllEditors() );
        updateAllTabTitles();
        JTools.getParent( this, JFrame.class ).toFront();
    }

    private void onReplace( boolean bAll )
    {
        if( getFocusedUnit() == null )
            return;

        getFocusedUnit().replaceSearch( toolBar.txtSearch.getText(),
                                        toolBar.txtReplace.getText(),
                                        bAll );
    }

    private boolean onTranspile()
    {
        if( getFocusedUnit() == null )
            return true;               // Nothing to transpile

        return getFocusedUnit().transpile( toolBar.chk4Grid.isSelected() );
    }

    private void onRun()               // Same button is used to run and stop
    {
        if( getFocusedUnit() == null )
            return;                    // Nothing to run

        if( getFocusedUnit().isRunning() )
        {
            onStop();
        }
        else
        {
            if( onTranspile() )     // isEmpty() == no errors
            {
                GTip.show( "This option runs the script in focused editor inside a tailored ExEn\n\n"+
                           "In this ExEn:\n"+
                           "    a) Only plain old Sockets can be used.\n"+
                           "    b) An unique port number is assigned to it: check it.\n"+
                           "    c) When 'Fake-drivers' is checked, some examples will not work.");

                if( toolBar.chkFaked.isSelected() )
                    GTip.show( "Take into consideration that you are using 'Fake-drivers'" );

                if( getFocusedUnit().run( toolBar.chkFaked.isSelected(), toolBar.getLogLevel() ) )
                    toolBar.updateButtons( getFocusedUnit(), getAllEditors() );
            }
        }
    }

    private void onStop()
    {
        if( getFocusedUnit() == null )
            return;

        if( getFocusedUnit().isRunning() )
        {
            getFocusedUnit().stop();
            toolBar.updateButtons( getFocusedUnit(), getAllEditors() );
        }
    }

    private void onToogleRem()
    {
        if( getFocusedUnit() == null )
            return;

        getFocusedUnit().toggleRem();
    }

    private void onHelp()
    {
        try
        {
            URL         url = getClass().getResource( "UneEditorHelp.html" );
            JEditorPane edt = new JEditorPane( url );

            new GFrame()
                  .title( "Une Script Code Editor Help" )
                  .icon( "editor-256x256.png" )
                  .onClose( JFrame.DISPOSE_ON_CLOSE )
                  .closeOnEsc()
                  .put( new JScrollPane( edt ), BorderLayout.CENTER )
                  .setVisible()
                  .setSize( 800, 940 );
        }
        catch( IOException ex )
        {
            JTools.error( ex, UneMultiEditorPanel.this );
        }
    }

    private void onCopyFilePath()
    {
        if( getFocusedUnit() == null )
        {
            JTools.info( "Can not copy file path: no focused editor" );
        }
        else
        {
            Toolkit.getDefaultToolkit()
                   .getSystemClipboard()
                   .setContents( new StringSelection( getFocusedUnit().getFile().toString() ), null );

            Toolkit.getDefaultToolkit()
                   .beep();

            Toolkit.getDefaultToolkit()
                   .beep();

            JTools.info( "File path copied to the clipboard" );
        }
    }

    private void onSelectedTabChanged()
    {
        if( getFocusedUnit() == null )
            return;

        toolBar.updateButtons( getFocusedUnit(), getAllEditors() );
        getFocusedUnit().onSelected();
    }

    private void autoSaveStateChanged()
    {
        if( ! toolBar.chkAutoSave.isSelected() )
        {
            ScheduledFuture current = futureSave.get();
            if( current != null )
                current.cancel( true );

            futureSave.set( null );
        }
        else
        {
            futureSave.set( UtilSys.executeWithDelay( getClass().getName(), 5 * UtilUnit.MINUTE, 5 * UtilUnit.MINUTE, () -> onSave( null ) ) );
        }
    }

    private void onInsertCmdTemplate()
    {
        if( getFocusedUnit() == null )
            return;

        int offset = getFocusedUnit().getCaretOffset();

        String s = toolBar.cmbTemplate.getSelectedItem().toString().trim().toLowerCase();

        if( s.equals( "comment" ) )
            getFocusedUnit().insertText( '#'+ UtilStr.fill( '=', 130 ) +'\n' );
        else
            getFocusedUnit().insertText( UnecTools.getMapCmdSyntax().get( s ) +'\n' );

        getFocusedUnit().getEditor().requestFocusInWindow();
        getFocusedUnit().setCaretOffset( offset );
    }

    //------------------------------------------------------------------------//
    // AUXILIARY FUNCTIONS (functions not triggered directly by a JComponent

    private UneEditorUnit getFocusedUnit()
    {
        if( tabbedPane.getSelectedIndex() == -1 )
            return null;

        return (UneEditorUnit) tabbedPane.getComponentAt( tabbedPane.getSelectedIndex() );
    }

    private UneEditorUnit newTab( File file )
    {
        UneEditorUnit ueu = null;

        try
        {
            ueu = new UneEditorUnit( file )
                        .addOnCaretChanged( (dot,mark) ->
                                            {
                                                toolBar.btnCopy.setEnabled( ! Objects.equals( dot, mark ) );
                                                toolBar.showCaretPos( getFocusedUnit() );
                                            } )
                        .addOnTextChanged( () ->
                                            {
                                                toolBar.btnSave.setEnabled( true );
                                                updateAllTabTitles();
                                            } );

            tabbedPane.addTab( "", ueu,
                               (ActionEvent evt) -> close( tabbedPane.getTabIndexWhichButtonIs( (JButton) evt.getSource() ) ) );
            updateAllTabTitles();
        }
        catch( IOException ioe )
        {
            JTools.alert( "Can not read file: "+ file +"\nError: "+ ioe.getMessage(), this );
        }

        return ueu;
    }

    private void close( int nTabIndex )
    {
        nTabIndex = (nTabIndex < 0) ? tabbedPane.getSelectedIndex() : nTabIndex;

        UneEditorUnit editor = (UneEditorUnit) tabbedPane.getComponentAt( nTabIndex );

        if( editor != null )
        {
            editor.stop();

            if( editor.isNeededToSave() )
            {
                String sTitle = tabbedPane.getTitleAt( tabbedPane.getSelectedIndex() );
                       sTitle = sTitle.substring( 0, sTitle.length() - 1 ).trim();

                if( JTools.confirm( sTitle +" has unsaved changes.\nSave before closing?", this ) )
                    onSave( editor );
            }
        }

        tabbedPane.remove( nTabIndex );

        toolBar.updateButtons( getFocusedUnit(), getAllEditors() );
    }

    private void closeAll( final Window window )
    {
        saveOpenedFilesList();
        cleanup();

        SwingUtilities.invokeLater( () ->
                                    {
                                        while( tabbedPane.getTabCount() > 0 )
                                            close( 0 );

                                        window.dispose();
                                    } );
    }

    /**
     * Cleanup method to prevent memory leaks by removing listeners and canceling tasks.
     */
    private void cleanup()
    {
        // Cancel auto-save task
        ScheduledFuture current = futureSave.get();

        if( current != null )
        {
            current.cancel( false );
            futureSave.set( null );
        }

        // Remove listeners from all editor units
        for( UneEditorUnit unit : getAllEditors() )
        {
            if( unit != null )
            {
                // Remove document and caret listeners to prevent memory leaks
                // Note: RSyntaxTextArea doesn't provide direct access to remove listeners,
                // but the units will be garbage collected when the panel is disposed
            }
        }
    }

    File getLastUsedDir()
    {
        String lastDirPath = SettingsManager.getLastUsedDir();
        return new File( lastDirPath );
    }

    void setLastUsedDir( File fNewLastDir )
    {
        if( fNewLastDir == null )
            return;

        fNewLastDir = fNewLastDir.isDirectory() ? fNewLastDir : fNewLastDir.getParentFile();
        SettingsManager.setLastUsedDir( fNewLastDir.getAbsolutePath() );
    }

    private boolean isOpen( File file )
    {
        for( int n = 0; n < tabbedPane.getTabCount(); n++ )
        {
            UneEditorUnit editor = (UneEditorUnit) tabbedPane.getComponentAt( n );

            if( file.equals( editor.getFile() ) )
            {
                tabbedPane.setSelectedIndex( n );
                return true;
            }
        }

        return false;
    }

    private void saveOpenedFilesList()
    {
        JsonArray jaEdit = new JsonArray();

        for( UneEditorUnit editor : getAllEditors() )
        {
            if( editor.getFile() != null )
            {
                jaEdit.add( new JsonObject().add( "file" , editor.getFile().getAbsolutePath() )
                                            .add( "caret", editor.getCaretOffset() ) );
            }
        }

        SettingsManager.setEditorFiles( jaEdit );
    }

    private void restoreOpenedFiles()
    {
        try
        {
            JsonArray editorFiles = SettingsManager.getEditorFiles();

            editorFiles.forEach( (JsonValue jv) ->
                                    {
                                        String s = jv.asObject().getString( "file", null );
                                        File   f = (s == null) ? null : new File( s );

                                        if( (f != null) && f.exists() )
                                        {
                                            UneEditorUnit ueu = newTab( f );

                                            if( ueu != null )
                                                ueu.setCaretOffset( jv.asObject().getInt( "caret", 0 ) );
                                        }
                                    } );

            if( tabbedPane.getTabCount() > 0 )
                tabbedPane.setSelectedIndex( 0 );
        }
        catch( Exception exc )
        {
            JTools.error( exc );
        }
    }

    // It does not worth it to have two methods: one to update the focused editor and another one to update all editors
    private void updateAllTabTitles()   // It is important to invoke here after updating toolbar (not before)
    {
        final char cBullet = '\u25CF';

        for( int n = 0; n < tabbedPane.getTabCount(); n++ )
        {
            UneEditorUnit editor = ((UneEditorUnit) tabbedPane.getComponentAt( n ));
            String        sTitle = editor.getFile() == null ? "New file" : editor.getFile().getName();

            if( editor.isChanged() )
            {
                if( ! UtilStr.isLastChar( sTitle, cBullet ) )
                    sTitle += "  "+ cBullet;
            }
            else if( UtilStr.isLastChar( sTitle, cBullet ) )
            {
                sTitle = sTitle.substring( 0, sTitle.length() - 1 ).trim();
            }

            tabbedPane.setTitleAt( n, sTitle );

            if( editor.getFile() != null )
                tabbedPane.setToolTipTextAt( n, editor.getFile().getAbsolutePath() );
        }

        SwingUtilities.invokeLater( () -> tabbedPane.updateUI() );   // Needed to repaint and readjust tab components
    }

    private List<UneEditorUnit> getAllEditors()
    {
        List<UneEditorUnit> lst = new ArrayList<>();

        for( int n = 0; n < tabbedPane.getTabCount(); n++ )
        {
            lst.add( ((UneEditorUnit) tabbedPane.getComponentAt( n )) );
        }

        return lst;
    }
}