
package com.peyrona.mingle.glue.codeditor;

import com.peyrona.mingle.glue.JTools;
import com.peyrona.mingle.glue.codeditor.UneEditorTabContent.UneEditorUnit;
import com.peyrona.mingle.lang.interfaces.ILogger;
import com.peyrona.mingle.lang.japi.UtilStr;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.GridLayout;
import java.awt.Toolkit;
import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.DataFlavor;
import java.util.List;
import javax.swing.DefaultComboBoxModel;
import javax.swing.Icon;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.JToolBar;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.border.BevelBorder;
import javax.swing.border.CompoundBorder;
import javax.swing.border.EmptyBorder;
import jiconfont.icons.font_awesome.FontAwesome;
import jiconfont.swing.IconFontSwing;

/**
 *
 * @author Francisco José Morero Peyrona
 *
 * Official web site at: <a href="https://mingle.peyrona.com">https://mingle.peyrona.com</a>
 */
final class UneEditorToolBar extends JToolBar
{
    JButton btnComment;
    JButton btnCopy;
    JButton btnPaste;
    JButton btnRTF;
    JButton btnHelp;
    JButton btnNew;
    JButton btnNext;
    JButton btnOpen;
    JButton btnPrev;
    JButton btnRedo;
    JButton btnRepl;
    JButton btnReplAll;
    JButton btnRuns;
    JButton btnSave;
    JButton btnTranspi;
    JButton btnUndo;
    JCheckBox chk4Grid;
    JCheckBox chkAutoSave;
    JCheckBox chkFaked;
    JComboBox<String> cmbTemplate;
    JLabel lblReplace;
    JLabel lblSearch;
    JTextField txtReplace;
    JTextField txtSearch;
    JComboBox cmbLogLevel;

    private JLabel lblLinCol;

    //------------------------------------------------------------------------//
    // PACKAGE CONSTRUCTOR

    UneEditorToolBar()
    {
        setRollover( true );
        setFloatable( false );
        init();
    }

    //------------------------------------------------------------------------//
    // PACKAGE SCOPE

    UneEditorToolBar updateButtons( UneEditorUnit edtFocused, List<UneEditorUnit> lstAllEditors )
    {
        if( edtFocused == null )
            return this;

        SwingUtilities.invokeLater( () ->
                                    {
                                        Clipboard clipboard = Toolkit.getDefaultToolkit().getSystemClipboard();

                                        lstAllEditors.removeIf( (edt) -> ! edt.isChanged() );

                                        boolean isEditorEmpty = (edtFocused == null) || edtFocused.isEmpty();
                                        boolean isSaveEnabled = ! lstAllEditors.isEmpty();

                                        btnNext.setEnabled( ! txtSearch.getText().isEmpty() );                              // Find next
                                        btnPrev.setEnabled( ! txtSearch.getText().isEmpty() );                              // Find previous
                                        btnRepl.setEnabled(    btnNext.isEnabled() && ! txtReplace.getText().isEmpty() );   // Replace and go to next
                                        btnReplAll.setEnabled( btnRepl.isEnabled() );                                       // Replace All

                                        btnSave.setEnabled( isSaveEnabled );
                                        btnUndo.setEnabled( (edtFocused != null) && edtFocused.canUndo() );
                                        btnRedo.setEnabled( (edtFocused != null) && edtFocused.canRedo() );
                                        btnCopy.setEnabled( (edtFocused != null) && UtilStr.isNotEmpty( edtFocused.getSelectedText() ) );
                                        btnPaste.setEnabled((edtFocused != null) && clipboard.isDataFlavorAvailable( DataFlavor.stringFlavor ) );
                                        btnRTF.setEnabled( ! isEditorEmpty );

                                        btnComment.setEnabled( ! isEditorEmpty );     // As this button is just text (no icon) it depends on the System L&F to disable it or not
                                        btnTranspi.setEnabled( ! isEditorEmpty );
                                        cmbTemplate.setEnabled( edtFocused != null );

                                        btnRuns.setEnabled( (edtFocused != null) && (! edtFocused.isEmpty()) );
                                        btnRuns.setIcon( IconFontSwing.buildIcon( (edtFocused != null && edtFocused.isRunning() ? FontAwesome.STOP : FontAwesome.PLAY), 16f, JTools.getIconColor() ) );

                                        updateSearchAndReplaceUI( edtFocused );

                                        showCaretPos( edtFocused );
                                    } );
        return this;
    }

    UneEditorToolBar updateSearchAndReplaceUI( UneEditorUnit editor )
    {
        if( editor == null )
            return this;

        // JLabels have to be always enabled, otherwise the mouse cursor does not change its shape

        boolean bFound = editor.searchText( txtSearch.getText() );

        txtSearch.setForeground( (bFound ? null : Color.red) );
        btnPrev.setEnabled(    bFound );
        btnNext.setEnabled(    bFound );
        txtReplace.setEnabled( bFound );
        btnRepl.setEnabled(    bFound && (! txtReplace.getText().isEmpty()) );
        btnReplAll.setEnabled( btnRepl.isEnabled() );

        return this;
    }

    UneEditorToolBar clearSearchAndReplaceUI( UneEditorUnit editor )
    {
        if( editor == null )
            return this;

        // JLabels have to be always enabled, otherwise the mouse cursor does not change its shape

        txtSearch.setText(     null );
        btnPrev.setEnabled(    false );
        btnNext.setEnabled(    false );
        txtReplace.setText(    null );
        btnRepl.setEnabled(    false );
        btnReplAll.setEnabled( false );

        if( editor != null )
            editor.searchText( null );

        return this;
    }

    ILogger.Level getLogLevel()
    {
        String sItem = (String) cmbLogLevel.getSelectedItem();

        return (sItem == null) ? ILogger.Level.WARNING : ILogger.Level.fromName( sItem );
    }

    UneEditorToolBar setLogLevel( ILogger.Level level )
    {
        cmbLogLevel.setSelectedItem( level.toString() );

        return this;
    }

    UneEditorToolBar showCaretPos( UneEditorUnit editor )
    {
        if( editor == null )
            return this;
        
        int line = (editor == null) ? 1 : editor.getCaretLine();
        int col  = (editor == null) ? 1 : editor.getCaretColumn();

        lblLinCol.setText( "lin "+ line +"  :  col "+ col );

        return this;
    }

    //------------------------------------------------------------------------//

    private JButton addButton( JPanel pnlRow, FontAwesome icon, String tooltip )
    {
        Icon ic = IconFontSwing.buildIcon( icon, 16f, JTools.getIconColor() );

        JButton btn = new JButton();
                btn.setFocusable( false );
                btn.setIcon( ic );
                btn.setToolTipText( tooltip );
                btn.setFocusPainted( false );

        return (JButton) pnlRow.add( btn );
    }

    private void init()
    {
        JPanel row1 = new JPanel( new FlowLayout( FlowLayout.LEFT ) );
        JPanel row2 = new JPanel( new FlowLayout( FlowLayout.LEFT ) );

        btnNew     = addButton( row1, FontAwesome.PLUS       , "New empty script code editor [Ctrl+N]" );
        btnOpen    = addButton( row1, FontAwesome.FOLDER_OPEN, "Open an Une or a JSON file [Ctrl+O]" );
        btnSave    = addButton( row1, FontAwesome.FLOPPY_O   , "Save all open files [Ctrl+Shift+S] (to save current file [Ctrl+S])" );
        btnCopy    = addButton( row1, FontAwesome.CLONE      , "Copy selected text into clipboard [Ctrl+C]" );
        btnPaste   = addButton( row1, FontAwesome.CLIPBOARD  , "Paste from clipboard into caret position  [Ctrl+V]" );
        btnUndo    = addButton( row1, FontAwesome.UNDO       , "Undo (Ctrl+Z)" );
        btnRedo    = addButton( row1, FontAwesome.REPEAT     , "Redo [Ctrl+Y]" );
        btnComment = addButton( row1, FontAwesome.HASHTAG    , "Toggle commented for selected lines or current line if none selected (Ignore lines) [Ctrl+I]" );
        btnRTF     = addButton( row1, FontAwesome.CODE       , "Copy to clipboard as RTF selected text or all text if nothing selected" );

        row1.add( newSeparator() );

        row1.add( new JLabel( "Templates" ) );
        cmbTemplate = new javax.swing.JComboBox<>();
        cmbTemplate.setModel( new DefaultComboBoxModel<>( new String[] { "Device", "Driver", "Include", "Rule", "Script", "Use", "Comment" } ) );
        cmbTemplate.setToolTipText("Inserts a command template at caret position");
        row1.add( cmbTemplate );

        row1.add( newSeparator() );

        btnTranspi = addButton( row1, FontAwesome.COGS, "Transpile file [Ctrl+T]" );
        btnRuns    = addButton( row1, FontAwesome.PLAY, "Run (or stop if already running) script inside a constrained ExEn  [Ctrl+R]" );
        btnHelp    = addButton( row1, FontAwesome.INFO, "Shows Une Script Editor help [Ctrl-H] or [F1]" );

        row1.add( newSeparator() );

        lblLinCol = new JLabel();
        lblLinCol.setToolTipText("Caret position: line and column");
        lblLinCol.setHorizontalAlignment( SwingConstants.LEADING );
        lblLinCol.setMaximumSize(   new Dimension(145, 18) );
        lblLinCol.setMinimumSize(   new Dimension(115, 18) );
        lblLinCol.setPreferredSize( new Dimension(115, 18) );
        row1.add( lblLinCol );

        // ROW 2 ---------------------------------------------------------------------------------------

        // Search -----------------------
        lblSearch = new JLabel( IconFontSwing.buildIcon( FontAwesome.SEARCH, 16, JTools.getIconColor() ) );
        lblSearch.setToolTipText( "Click to clear search and replace texts (or press ESC)" );
        lblSearch.setCursor( Cursor.getPredefinedCursor( Cursor.HAND_CURSOR ) );
        row2.add( lblSearch );

        txtSearch = new JTextField();
        txtSearch.setToolTipText( "Text to search" );
        txtSearch.setPreferredSize( new Dimension(120, 31) );
        row2.add( txtSearch );

        JPanel pnlSearch = new JPanel( new GridLayout( 1, 3, 0, 0 ) );
        // Next -------------------------
        btnNext = addButton( pnlSearch, FontAwesome.CARET_DOWN, "Go to next occurrence" );
        btnNext.setPreferredSize( new Dimension( btnNext.getPreferredSize().width, txtSearch.getPreferredSize().height ) );
        btnNext.setEnabled(false);

        // Previous ---------------------
        btnPrev = addButton( pnlSearch, FontAwesome.CARET_UP, "Go to previous occurrence" );
        btnPrev.setPreferredSize( new Dimension( btnPrev.getPreferredSize().width, txtSearch.getPreferredSize().height ) );
        btnPrev.setEnabled(false);

        row2.add( pnlSearch );
        row2.add( newSpace() );

        // Replace ----------------------
        lblReplace = new JLabel();
        lblReplace.setIcon( IconFontSwing.buildIcon( FontAwesome.RETWEET, 16, JTools.getIconColor() ) );
        lblReplace.setToolTipText( "Click to clear replace text" );
        lblReplace.setCursor( Cursor.getPredefinedCursor( Cursor.HAND_CURSOR ) );
        row2.add( lblReplace );

        txtReplace = new JTextField();
        txtReplace.setToolTipText("Text to replace");
        txtReplace.setEnabled(false);
        txtReplace.setPreferredSize( new Dimension(120, 31) );
        row2.add(txtReplace);

        btnRepl = addButton( row2, FontAwesome.CHECK, "Replace highlighted occurrence" );
        btnRepl.setPreferredSize( new Dimension( btnRepl.getPreferredSize().width, txtSearch.getPreferredSize().height ) );
        btnRepl.setEnabled(false);
        row2.add(btnRepl);

        btnReplAll = addButton( row2, FontAwesome.BOLT, "Replace all occurrences" );
        btnReplAll.setPreferredSize( new Dimension( btnRepl.getPreferredSize().width, txtSearch.getPreferredSize().height ) );
        btnReplAll.setEnabled(false);
        row2.add(btnReplAll);

        row2.add( newSeparator() );

        chkAutoSave = new JCheckBox( "Autosave" );
        chkAutoSave.setToolTipText("Saves changed files every 5 minutes");
        row2.add(chkAutoSave);

        row2.add( newSpace() );

        chk4Grid = new JCheckBox( "Grid" );
        chk4Grid.setToolTipText("Check it when the code will be used in a Grid environment");
        chk4Grid.setFocusable(false);
        chk4Grid.setHorizontalAlignment(javax.swing.SwingConstants.TRAILING);
        row2.add( chk4Grid );

        row2.add( newSpace() );

        chkFaked = new JCheckBox( "Fake-drivers" );
        chkFaked.setToolTipText("Use fake drivers (this is a hint for the driver: they can ignore this request)");
        chkFaked.setFocusable(false);
        chkFaked.setHorizontalAlignment(javax.swing.SwingConstants.TRAILING);
        row2.add( chkFaked );

        row2.add( newSpace() );

        row2.add( new JLabel( "Log" ) );
        cmbLogLevel = new JComboBox( new String[] { "OFF", "SEVERE", "WARNING",  "INFO", "RULE", "MESSAGE", "ALL" } );
        cmbLogLevel.setToolTipText("Selects log level");
        row2.add( cmbLogLevel );

        // ADDING TO TOOLBAR ---------------------------------------------------------------------------

        setLayout( new GridLayout(2,1) );
        add( row1 );
        add( row2 );

        JTools.getOfClass( row1, JButton.class ).forEach( (btn) -> btn.setPreferredSize( new Dimension( 39,37 ) ) );
        JTools.getOfClass( row2, JButton.class ).forEach( (btn) -> btn.setPreferredSize( new Dimension( 39,37 ) ) );
    }

    private JPanel newSeparator()
    {
        JPanel sep = new JPanel();
               sep.setMaximumSize(   new Dimension( 26, 22 ) );
               sep.setMinimumSize(   new Dimension( 26, 22 ) );
               sep.setPreferredSize( new Dimension( 26, 22 ) );
               sep.setBorder( new CompoundBorder( new EmptyBorder( 0, 12, 0, 12 ),
                                                  new BevelBorder( BevelBorder.LOWERED ) ) );
        return sep;
    }

    private JPanel newSpace()
    {
        JPanel sep = new JPanel();
               sep.setMaximumSize(   new Dimension( 12, 22 ) );
               sep.setMinimumSize(   new Dimension( 12, 22 ) );
               sep.setPreferredSize( new Dimension( 12, 22 ) );

        return sep;
    }
}