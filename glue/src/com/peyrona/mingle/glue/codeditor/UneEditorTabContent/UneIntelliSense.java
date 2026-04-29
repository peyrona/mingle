
package com.peyrona.mingle.glue.codeditor.UneEditorTabContent;

import com.peyrona.mingle.candi.unec.parser.ParseBase;
import com.peyrona.mingle.candi.unec.transpiler.UnecTools;
import com.peyrona.mingle.glue.JTools;
import com.peyrona.mingle.lang.interfaces.ICandi;
import com.peyrona.mingle.lang.interfaces.ILogger;
import com.peyrona.mingle.lang.japi.Pair;
import com.peyrona.mingle.lang.japi.UtilIO;
import com.peyrona.mingle.lang.japi.UtilStr;
import com.peyrona.mingle.lang.japi.UtilSys;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.KeyboardFocusManager;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.Rectangle2D;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.swing.BorderFactory;
import javax.swing.DefaultListCellRenderer;
import javax.swing.DefaultListModel;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPopupMenu;
import javax.swing.JScrollPane;
import javax.swing.ListSelectionModel;
import javax.swing.SwingUtilities;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import javax.swing.text.BadLocationException;
import org.fife.ui.rsyntaxtextarea.RSyntaxTextArea;

/**
 * Intelligent code completion for the Une editor.
 * <p>
 * This class provides context-aware code completion suggestions including:
 * <ul>
 *   <li>Command keywords at line start (DEVICE, DRIVER, RULE, SCRIPT, etc.)</li>
 *   <li>Clause keywords within commands (INIT, CONFIG, WHEN, THEN, etc.)</li>
 *   <li>Driver names with CONFIG templates</li>
 *   <li>Functions from the expression evaluator</li>
 *   <li>Extended data types and their methods</li>
 *   <li>Boolean literals and operator aliases</li>
 *   <li>Predefined macros ({*home*}, {*home.inc*}, etc.)</li>
 * </ul>
 * <p>
 * The completion popup supports:
 * <ul>
 *   <li>Type-ahead filtering as user types</li>
 *   <li>Keyboard navigation (Up/Down, Enter, Escape)</li>
 *   <li>Mouse double-click selection</li>
 *   <li>Category icons and descriptions</li>
 * </ul>
 *
 * @author Francisco José Morero Peyrona
 *
 * Official web site at: <a href="https://github.com/peyrona/mingle">https://github.com/peyrona/mingle</a>
 */
public final class UneIntelliSense extends JPopupMenu
{
    // Completion item categories (numbers are to sort)
    private static final String CAT_TYPE     = "1.type";
    private static final String CAT_FUNCTION = "2.function";
    private static final String CAT_METHOD   = "3.method";
    private static final String CAT_BOOLEAN  = "4.boolean";
    private static final String CAT_OPERATOR = "5.operator";
    private static final String CAT_COMMAND  = "6.command";
    private static final String CAT_CLAUSE   = "7.clause";
    private static final String CAT_DRIVER   = "8.driver";
    private static final String CAT_MACRO    = "9.macro";

    // Cached completion data (loaded once)
    // signaturesCache is used for documentation signatures; functionsCache is the authoritative function list
    // extendedCache is still needed to distinguish types from functions
    private static Map<String, String>        driversCache    = null;
    private static Map<String, List<String>>  extendedCache   = null;
    private static Map<String, List<String>>  signaturesCache = null;
    private static List<String>               functionsCache  = null;

    // Initialization error tracking
    private static final List<Exception>      initErrors      = new ArrayList<>();

    // UI components
    private        final JList<CompletionItem> completionList;
    private        final DefaultListModel<CompletionItem> listModel;
    private        final RSyntaxTextArea       editor;
    private        final UneFunctionDocPopup   docPopup;

    // State
    private              String                filterPrefix   = "";
    private final        List<CompletionItem>  allItems       = new ArrayList<>();
    private              int                   insertOffset   = 0;

    //------------------------------------------------------------------------//
    // STATIC INITIALIZATION

    static
    {
        UtilSys.executor( true )
               .execute( () ->
                        {
                            try
                            {
                                driversCache = initDrivers();
                            }
                            catch( Exception e )
                            {
                                initErrors.add( e );
                                UtilSys.getLogger().log( ILogger.Level.WARNING, e, "Drivers init failed" );
                            }
                        } );

        UtilSys.executor( true )
               .execute( () ->
                        {
                            try
                            {
                                extendedCache = UtilSys.getConfig().newXprEval().getExtendedTypes();
                            }
                            catch( Exception e )
                            {
                                initErrors.add( e );
                                UtilSys.getLogger().log( ILogger.Level.WARNING, e, "Extended types init failed" );
                            }
                        } );

        UtilSys.executor( true )
               .execute( () ->
                        {
                            try
                            {
                                signaturesCache = UneFunctionDocPopup.getSignatures();
                            }
                            catch( Exception e )
                            {
                                initErrors.add( e );
                                UtilSys.getLogger().log( ILogger.Level.WARNING, e, "Signatures init failed" );
                            }
                        } );

        UtilSys.executor( true )
               .execute( () ->
                        {
                            try
                            {
                                List<String> lst = new ArrayList<>();

                                for( String fn : UtilSys.getConfig().newXprEval().getFunctions() )
                                    lst.add( fn );

                                functionsCache = lst;
                            }
                            catch( Exception e )
                            {
                                initErrors.add( e );
                                UtilSys.getLogger().log( ILogger.Level.WARNING, e, "Functions init failed" );
                            }
                        } );
    }

    //------------------------------------------------------------------------//
    // CONSTRUCTOR

    public UneIntelliSense( RSyntaxTextArea editor )
    {
        this.editor = editor;
        this.listModel = new DefaultListModel<>();
        this.completionList = new JList<>( listModel );
        this.docPopup = new UneFunctionDocPopup( editor );

        setupUI();
        setupListeners();
    }

    //------------------------------------------------------------------------//
    // PUBLIC INTERFACE

    /**
     * Shows the completion popup at the current cursor position.
     * Analyzes context to provide relevant suggestions.
     */
    public void showCompletions()
    {
        if( driversCache == null || extendedCache == null || signaturesCache == null || functionsCache == null )
        {
            if( ! initErrors.isEmpty() )
            {
                showInitializationErrors();
                return;
            }

            JTools.info( "IntelliSense is initializing, please try again in a few seconds" );

            return;
        }

        // Analyze context and populate suggestions
        analyzeContextAndPopulate();

        if( allItems.isEmpty() )
            return;

        // Apply any existing filter
        if( applyFilter() )
            return;

        // Show popup at caret position
        try
        {
            Rectangle2D rect = editor.modelToView2D( editor.getCaretPosition() );
            show( editor, (int) rect.getX(), (int) rect.getY() + (int) rect.getHeight() );
            SwingUtilities.invokeLater( () -> { completionList.grabFocus(); showDocForSelected(); } );

        }
        catch( BadLocationException e )
        {
            JTools.alert( "Error showing completion popup: " + e.getMessage() );
        }
    }

    //------------------------------------------------------------------------//
    // PRIVATE METHODS - UI SETUP

    private void setupUI()
    {
        JScrollPane scrollPane = new JScrollPane( completionList );
                    scrollPane.setBorder( BorderFactory.createLineBorder( Color.GRAY ) );
        add( scrollPane );

        setMinimumSize( new Dimension( 280, 200 ) );
        setPreferredSize( new Dimension( 320, 280 ) );

        completionList.setFont( new Font( Font.MONOSPACED, Font.PLAIN, 12 ) );
        completionList.setSelectionMode( ListSelectionModel.SINGLE_SELECTION );
        completionList.setCellRenderer( new CompletionRenderer() );
        completionList.setBackground( new Color( 45, 45, 45 ) );
        completionList.setForeground( new Color( 220, 220, 220 ) );
        completionList.setSelectionBackground( new Color( 60, 90, 120 ) );
        completionList.setSelectionForeground( Color.WHITE );
    }

    private void setupListeners()
    {
        completionList.addKeyListener( new KeyAdapter()
            {
                @Override
                public void keyPressed( KeyEvent e )
                {
                    switch( e.getKeyCode() )
                    {
                        case KeyEvent.VK_UP:
                            int upIndex = completionList.getSelectedIndex();
                            int upSize  = listModel.getSize();
                            if( upSize > 0 )
                            {
                                // If at the top (0 or -1), wrap to the bottom (size - 1)
                                int nextIndex = (upIndex <= 0) ? upSize - 1 : upIndex - 1;
                                completionList.setSelectedIndex( nextIndex );
                                completionList.ensureIndexIsVisible( nextIndex );
                            }
                            e.consume();
                            break;

                        case KeyEvent.VK_DOWN:
                            int downIndex = completionList.getSelectedIndex();
                            int downSize  = listModel.getSize();
                            if( downSize > 0 )
                            {
                                // If at the bottom or no selection, wrap to the top (0)
                                int nextIndex = (downIndex >= downSize - 1) ? 0 : downIndex + 1;
                                completionList.setSelectedIndex( nextIndex );
                                completionList.ensureIndexIsVisible( nextIndex );
                            }
                            e.consume();
                            break;

                        case KeyEvent.VK_ESCAPE:
                            dismiss();
                            e.consume();
                            break;

                        case KeyEvent.VK_ENTER:
                        case KeyEvent.VK_TAB:
                            insertSelectedCompletion();
                            e.consume();
                            break;

                        case KeyEvent.VK_BACK_SPACE:
                            if( filterPrefix.length() > 0 )
                            {
                                filterPrefix = filterPrefix.substring( 0, filterPrefix.length() - 1 );
                                applyFilter();
                            }
                            e.consume();
                            break;

                        // Up/Down/PgUp/PgDown/Home/End fall through to JList's default
                        // navigation actions in BasicListUI — selection moves and the
                        // ListSelectionListener updates the doc popup automatically.
                    }
                }

                @Override
                public void keyTyped( KeyEvent e )
                {
                    // Filter input + suppress JList's built-in "first-letter selection"
                    // (BasicListUI keyTyped handler) which would otherwise fight our filter.
                    char c = e.getKeyChar();

                    if( Character.isLetterOrDigit( c ) || c == '_' )
                    {
                        filterPrefix += c;
                        applyFilter();
                        e.consume();
                    }
                }
            } );

        completionList.addMouseListener( new MouseAdapter()
            {
                @Override
                public void mouseClicked( MouseEvent e )
                {
                    if( e.getClickCount() == 2 )
                        insertSelectedCompletion();
                }
            } );

        // Show API docs alongside the completion list whenever the selection changes.
        // Using only valueChanged events that have settled (not isAdjusting) prevents
        // mid-drag intermediate fires from causing flicker during arrow / page navigation.
        completionList.addListSelectionListener( new ListSelectionListener()
            {
                @Override
                public void valueChanged( ListSelectionEvent e )
                {
                    if( ! e.getValueIsAdjusting() )
                        showDocForSelected();
                }
            } );

        // Show API docs when the user types '(' directly in the editor after a known
        // function or type name (no IntelliSense list active).
        editor.getDocument().addDocumentListener( new DocumentListener()
            {
                @Override public void removeUpdate(  DocumentEvent e ) {}
                @Override public void changedUpdate( DocumentEvent e ) {}

                @Override
                public void insertUpdate( DocumentEvent e )
                {
                    if( e.getLength() == 1 )
                    {
                        try
                        {
                            if( "(".equals( editor.getDocument().getText( e.getOffset(), 1 ) ) )
                                onOpenParenTyped( e.getOffset() );
                        }
                        catch( BadLocationException ignored ) {}
                    }
                }
            } );
    }

    /**
     * Dismisses both the completion list and the doc popup, returning focus to the editor.
     * Centralises the "user wants out" behaviour so it is consistent across paths
     * (Escape, no items match, etc.).
     */
    private void dismiss()
    {
        setVisible( false );
        docPopup.setVisible( false );
        editor.grabFocus();
    }

    //------------------------------------------------------------------------//
    // PRIVATE METHODS - CONTEXT ANALYSIS

    private void analyzeContextAndPopulate()
    {
        allItems.clear();
        filterPrefix = "";

        try
        {
            int    caretPos    = editor.getCaretPosition();
            int    line        = editor.getLineOfOffset( caretPos );
            int    lineStart   = editor.getLineStartOffset( line );
            String rawLineText = editor.getText( lineStart, caretPos - lineStart );
            String lineText    = rawLineText.toUpperCase();       // Preserve leading whitespace for indentation check
            String trimmedLine = lineText.trim();                 // Trimmed version for keyword matching

            insertOffset = caretPos;

            // Determine filter prefix (word being typed at cursor position)
            // Use lineText (with whitespace) so trailing space means empty filter
            filterPrefix = extractWordAtCursor( lineText );
            insertOffset = caretPos - filterPrefix.length();

            // Determine context and add appropriate completions
            Context ctx = determineContext( lineText, trimmedLine, line );

            // If the filter prefix is actually a keyword that triggered the context, clear it
            // (e.g., user typed "DRIVER" to get driver names, not to filter by "DRIVER")
            if( ctx == Context.AFTER_DRIVER_KEYWORD && filterPrefix.equalsIgnoreCase( "DRIVER" ) )
                filterPrefix = "";
            else if( ctx == Context.AFTER_LANGUAGE && filterPrefix.equalsIgnoreCase( "LANGUAGE" ) )
                filterPrefix = "";
            else if( ctx == Context.AFTER_CONFIG && filterPrefix.equalsIgnoreCase( "CONFIG" ) )
                filterPrefix = "";

            switch( ctx )
            {
                case LINE_START:
                    addCommandKeywords();
                    break;

                case AFTER_DEVICE:
                case AFTER_SENSOR:
                case AFTER_ACTUATOR:
                    addDeviceClauses();
                    break;

                case AFTER_DRIVER_KEYWORD:
                    addDriverNames();
                    break;

                case AFTER_DRIVER_COMMAND:
                    addDriverCommandClauses();
                    break;

                case AFTER_RULE:
                    addRuleClauses();
                    break;

                case AFTER_SCRIPT:
                    addScriptClauses();
                    break;

                case AFTER_LANGUAGE:
                    addLanguageNames();
                    break;

                case AFTER_FROM:
                    addMacros();
                    break;

                case IN_EXPRESSION:
                case AFTER_WHEN:
                case AFTER_THEN:
                case AFTER_IF:
                    addExpressionCompletions();
                    break;

                case AFTER_COLON:
                    addMethodsForContext( lineText );
                    break;

                case AFTER_CONFIG:
                    addConfigKeywords();
                    break;

                default:
                    addGeneralCompletions();
            }

            // Sort items by category and then by text to ensure alphabetical order within groups
            allItems.sort( Comparator.comparing( (CompletionItem item) -> item.category )
                                     .thenComparing( item -> item.text ) );
        }
        catch( BadLocationException e )
        {
            addGeneralCompletions();
        }
    }

    private String extractWordAtCursor( String lineText )
    {
        StringBuilder word = new StringBuilder();

        for( int i = lineText.length() - 1; i >= 0; i-- )
        {
            char c = lineText.charAt( i );

            if( Character.isLetterOrDigit( c ) || c == '_' )
                word.insert( 0, c );
            else
                break;
        }

        return word.toString();
    }

    private Context determineContext( String lineText, String trimmedLine, int line )
    {
        if( trimmedLine.isEmpty() )
            return Context.LINE_START;

        // Check if we are after a colon (optionally with some prefix already typed)
        int prefixStart = lineText.length() - filterPrefix.length();
        String beforePrefix = lineText.substring( 0, prefixStart ).trim();
        if( beforePrefix.endsWith( ":" ) )
            return Context.AFTER_COLON;

        // Check if line has leading whitespace (indicates clause within a command block)
        boolean hasIndentation = lineText.length() > trimmedLine.length()
                                 && Character.isWhitespace( lineText.charAt( 0 ) );

        if( trimmedLine.startsWith( "DEVICE" ) || trimmedLine.startsWith( "SENSOR" ) || trimmedLine.startsWith( "ACTUATOR" ) )
        {
            if( trimmedLine.contains( "DRIVER" ) )
            {
                String[] parts = trimmedLine.split( "\\s+" );
                int lastPartIndex = parts.length - 1;

                while( lastPartIndex >= 0 && parts[lastPartIndex].isEmpty() )
                    lastPartIndex--;

                if( lastPartIndex >= 0 && parts[lastPartIndex].equals( "DRIVER" ) )
                    return Context.AFTER_DRIVER_KEYWORD;
            }

            return Context.AFTER_DEVICE;
        }

        // Handle DRIVER keyword - could be a clause within DEVICE or a standalone DRIVER command
        if( trimmedLine.startsWith( "DRIVER" ) && ! trimmedLine.contains( "SCRIPT" ) )
        {
            if( containsOnlyKeyword( trimmedLine, "DRIVER" ) )
            {
                // If indented, it's a DRIVER clause within a DEVICE block - show driver names
                if( hasIndentation || isInsideDeviceBlock( line ) )
                    return Context.AFTER_DRIVER_KEYWORD;

                // Non-indented "DRIVER" alone at line start is a DRIVER command definition
                return Context.LINE_START;
            }

            // DRIVER followed by something else
            if( hasIndentation || isInsideDeviceBlock( line ) )
            {
                // Inside DEVICE block with driver name already typed - could want CONFIG
                return Context.AFTER_DEVICE;
            }

            return Context.AFTER_DRIVER_COMMAND;
        }

        if( trimmedLine.startsWith( "RULE" ) )
            return Context.AFTER_RULE;

        if( trimmedLine.startsWith( "SCRIPT" ) )
            return Context.AFTER_SCRIPT;

        if( trimmedLine.contains( "LANGUAGE" ) && trimmedLine.endsWith( "LANGUAGE" ) )
            return Context.AFTER_LANGUAGE;

        if( trimmedLine.contains( "FROM" ) )
            return Context.AFTER_FROM;

        if( trimmedLine.contains( "WHEN" ) )
            return Context.AFTER_WHEN;

        if( trimmedLine.contains( "THEN" ) )
            return Context.AFTER_THEN;

        if( trimmedLine.contains( "IF" ) )
            return Context.AFTER_IF;

        if( trimmedLine.contains( "CONFIG" ) )
            return Context.AFTER_CONFIG;

        // Check if we're at the start of a line (after whitespace only)
        if( isAtLineStart( line ) )
            return Context.LINE_START;

        return Context.IN_EXPRESSION;
    }

    /**
     * Checks if the current line is inside a DEVICE/SENSOR/ACTUATOR block by looking at previous lines.
     */
    private boolean isInsideDeviceBlock( int currentLine )
    {
        try
        {
            // Look at previous lines (up to 20 lines back) to find context
            for( int i = currentLine - 1; i >= 0 && i >= currentLine - 20; i-- )
            {
                int lineStart = editor.getLineStartOffset( i );
                int lineEnd = editor.getLineEndOffset( i );
                String prevLine = editor.getText( lineStart, lineEnd - lineStart ).trim().toUpperCase();

                // Skip empty lines and comments
                if( prevLine.isEmpty() || prevLine.startsWith( "#" ) )
                    continue;

                // If we hit a DEVICE/SENSOR/ACTUATOR, we're inside its block
                if( prevLine.startsWith( "DEVICE" ) || prevLine.startsWith( "SENSOR" ) || prevLine.startsWith( "ACTUATOR" ) )
                    return true;

                // If we hit another top-level command (RULE, SCRIPT, standalone DRIVER), we're not in a device block
                if( prevLine.startsWith( "RULE" ) || prevLine.startsWith( "INCLUDE" ) || prevLine.startsWith( "USE" ) )
                    return false;

                // SCRIPT at line start (no indentation) indicates we've left any device block
                if( prevLine.startsWith( "SCRIPT" ) )
                {
                    String originalLine = editor.getText( lineStart, lineEnd - lineStart );
                    if( originalLine.length() > 0 && ! Character.isWhitespace( originalLine.charAt( 0 ) ) )
                        return false;
                }

                // DRIVER at line start with SCRIPT clause indicates a driver definition block
                if( prevLine.startsWith( "DRIVER" ) && prevLine.contains( "SCRIPT" ) )
                    return false;
            }
        }
        catch( BadLocationException e )
        {
            // Ignore - return false as fallback
        }

        return false;
    }

    private boolean containsOnlyKeyword( String line, String keyword )
    {
        String[] parts = line.trim().split( "\\s+" );
        return parts.length == 1 && parts[0].equalsIgnoreCase( keyword );
    }

    private boolean isAtLineStart( int line )
    {
        try
        {
            int lineStart = editor.getLineStartOffset( line );
            String text = editor.getText( lineStart, editor.getCaretPosition() - lineStart );
            return text.trim().isEmpty() || text.trim().length() == filterPrefix.length();
        }
        catch( BadLocationException e )
        {
            return false;
        }
    }

    //------------------------------------------------------------------------//
    // PRIVATE METHODS - COMPLETION POPULATION

    private void addCommandKeywords()
    {
        addItem( "DEVICE",  CAT_COMMAND, "Define a sensor or actuator" );
        addItem( "DRIVER",  CAT_COMMAND, "Define a custom driver" );
        addItem( "RULE",    CAT_COMMAND, "Define a trigger-action rule" );
        addItem( "SCRIPT",  CAT_COMMAND, "Define embedded code" );
        addItem( "INCLUDE", CAT_COMMAND, "Include external files" );
        addItem( "USE",     CAT_COMMAND, "Define text substitution" );
    }

    private void addDeviceClauses()
    {
        addItem( "INIT",   CAT_CLAUSE, "Initialize device properties" );
        addItem( "DRIVER", CAT_CLAUSE, "Specify device driver" );
        addItem( "CONFIG", CAT_CLAUSE, "Driver configuration" );
    }

    private void addDriverNames()
    {
        if( driversCache != null )
        {
            List<String> sorted = new ArrayList<>( driversCache.keySet() );
            Collections.sort( sorted );

            for( String driver : sorted )
                addItem( driver, CAT_DRIVER, "Driver with CONFIG template" );
        }
    }

    private void addDriverCommandClauses()
    {
        addItem( "SCRIPT", CAT_CLAUSE, "Script implementing the driver" );
        addItem( "CONFIG", CAT_CLAUSE, "Configuration parameters" );
    }

    private void addRuleClauses()
    {
        addItem( "WHEN",   CAT_CLAUSE, "Trigger condition" );
        addItem( "THEN",   CAT_CLAUSE, "Action to execute" );
        addItem( "IF",     CAT_CLAUSE, "Future condition" );
        addItem( "USE",    CAT_CLAUSE, "Local alias definition" );
        addItem( "AFTER",  CAT_CLAUSE, "Delay before action" );
        addItem( "WITHIN", CAT_CLAUSE, "Time window condition" );
        addItem( "ANY",    CAT_CLAUSE, "Any device in the group" );
        addItem( "ALL",    CAT_CLAUSE, "All devices in the group" );
        addItem( "NONE",   CAT_CLAUSE, "None of the devices in the group" );
    }

    private void addScriptClauses()
    {
        addItem( "LANGUAGE", CAT_CLAUSE, "Programming language" );
        addItem( "FROM",     CAT_CLAUSE, "Source code or URI" );
        addItem( "CALL",     CAT_CLAUSE, "Entry point function" );
        addItem( "ONSTART",  CAT_CLAUSE, "Execute on startup" );
        addItem( "ONSTOP",   CAT_CLAUSE, "Execute on shutdown" );
    }

    private void addLanguageNames()
    {
        addItem( "Java",       CAT_TYPE, "Java programming language" );
        addItem( "JavaScript", CAT_TYPE, "JavaScript/ECMAScript" );
        addItem( "Python",     CAT_TYPE, "Python (Jython)" );
        addItem( "Une",        CAT_TYPE, "Une language (nested)" );
    }

    private void addMacros()
    {
        addItem( "{*home*}",     CAT_MACRO, "MSP home directory" );
        addItem( "{*home.inc*}", CAT_MACRO, "Includes directory" );
        addItem( "{*home.lib*}", CAT_MACRO, "Libraries directory" );
        addItem( "{*home.log*}", CAT_MACRO, "Logs directory" );
        addItem( "{*home.tmp*}", CAT_MACRO, "Temporary directory" );
    }

    private void addConfigKeywords()
    {
        addItem( "AS",       CAT_CLAUSE, "Type specification" );
        addItem( "REQUIRED", CAT_CLAUSE, "Mark as required" );
        addItem( "BOOLEAN",  CAT_TYPE,   "Boolean data type" );
        addItem( "NUMBER",   CAT_TYPE,   "Numeric data type" );
        addItem( "STRING",   CAT_TYPE,   "String data type" );
     // addItem( "ANY",      CAT_TYPE,   "Any data type" );    To not interfere with addRuleClasuses() --> ANY
    }

    private void addExpressionCompletions()
    {
        java.util.Set<String> added = new java.util.HashSet<>();

        // Pass 1: types from signaturesCache (with their documented signatures)
        if( signaturesCache != null && extendedCache != null )
        {
            for( String type : extendedCache.keySet() )
            {
                List<String> sigs = signaturesCache.get( type );

                if( sigs != null && ! sigs.isEmpty() )
                {
                    for( String sig : sigs )
                        addItem( sig, CAT_TYPE, "Extended data type" );
                }
                else
                {
                    addItem( type + "()", CAT_TYPE, "Extended data type" );
                }

                added.add( type );
            }
        }

        // Pass 2: types not covered by signaturesCache
        if( extendedCache != null )
        {
            for( String type : extendedCache.keySet() )
            {
                if( ! added.contains( type ) )
                {
                    addItem( type + "()", CAT_TYPE, "Extended data type" );
                    added.add( type );
                }
            }
        }

        // Pass 3: functions from the evaluator (authoritative source)
        if( functionsCache != null )
        {
            for( String fn : functionsCache )
            {
                if( added.contains( fn ) )
                    continue;  // Already emitted as a type

                List<String> sigs = (signaturesCache != null) ? signaturesCache.get( fn ) : null;

                if( sigs != null && ! sigs.isEmpty() )
                {
                    for( String sig : sigs )
                        addItem( sig, CAT_FUNCTION, "Function" );
                }
                else
                {
                    addItem( fn + "()", CAT_FUNCTION, "Function" );
                }
            }
        }

        // Boolean values
        addItem( "TRUE",  CAT_BOOLEAN, "Boolean true" );
        addItem( "FALSE", CAT_BOOLEAN, "Boolean false" );
    }

    private void addMethodsForContext( String lineText )
    {
        // Find the type before the colon
        int colonPos = lineText.lastIndexOf( ':' );

        if( colonPos < 0 )
        {
            // No colon found - show all methods
            addAllMethods();
            return;
        }

        String beforeColon = lineText.substring( 0, colonPos ).trim();
        String typeName = extractLastWord( beforeColon ).toLowerCase();

        // Remove parentheses if present
        if( typeName.endsWith( "()" ) )
            typeName = typeName.substring( 0, typeName.length() - 2 );

        if( extendedCache != null && extendedCache.containsKey( typeName ) )
        {
            for( String method : extendedCache.get( typeName ) )
            {
                String qualified = typeName + "." + method;
                List<String> sigs = (signaturesCache != null) ? signaturesCache.get( qualified.toLowerCase() ) : null;

                if( sigs != null && ! sigs.isEmpty() )
                {
                    for( String sig : sigs )
                        addItem( sig, CAT_METHOD, "Method of " + typeName );
                }
                else
                {
                    addItem( method + "()", CAT_METHOD, "Method of " + typeName );
                }
            }
        }
        else
        {
            addAllMethods();
        }
    }

    private void addAllMethods()
    {
        // Show all methods from all types
        if( extendedCache != null )
        {
            for( Map.Entry<String, List<String>> entry : extendedCache.entrySet() )
            {
                String typeName = entry.getKey();
                for( String method : entry.getValue() )
                {
                    String qualified = typeName + "." + method;
                    List<String> sigs = (signaturesCache != null) ? signaturesCache.get( qualified.toLowerCase() ) : null;

                    if( sigs != null && ! sigs.isEmpty() )
                    {
                        for( String sig : sigs )
                            addItem( sig, CAT_METHOD, "Method" );
                    }
                    else
                    {
                        addItem( method + "()", CAT_METHOD, "Method" );
                    }
                }
            }
        }
    }

    private String extractLastWord( String text )
    {
        String[] parts = text.split( "\\s+" );
        return parts.length > 0 ? parts[parts.length - 1] : "";
    }

    /**
     * Checks if the given name is a method of any extended type.
     * This is used to exclude methods from the function/type completion list,
     * since methods should only be shown in the AFTER_COLON context.
     *
     * @param name the name to check
     * @return true if the name is a method of any type in extendedCache
     */
    private boolean isMethodOfAnyType( String name )
    {
        if( extendedCache == null )
            return false;

        String lowerName = name.toLowerCase();

        for( List<String> methods : extendedCache.values() )
        {
            for( String method : methods )
            {
                if( method.toLowerCase().equals( lowerName ) )
                    return true;
            }
        }

        return false;
    }

    private void addGeneralCompletions()
    {
        addCommandKeywords();
        addExpressionCompletions();
    }

    private void addItem( String text, String category, String description )
    {
        allItems.add( new CompletionItem( text, category, description ) );
    }

    //------------------------------------------------------------------------//
    // PRIVATE METHODS - DOCUMENTATION DISPLAY

    /**
     * Shows API documentation for the currently selected item in the completion list,
     * positioned to the right of the completion popup.
     * <p>
     * If the selected item is not a callable (function/type/method), the doc popup
     * is left in its previous state — neither shown nor hidden. This prevents flicker
     * when navigating with PgUp/PgDown across mixed item types (e.g. functions then
     * booleans). The popup is only ever hidden via {@link #dismiss()} or by Swing's
     * built-in click-outside auto-hide.
     */
    private void showDocForSelected()
    {
        if( ! isVisible() )
            return;

        CompletionItem item = completionList.getSelectedValue();

        if( item == null )
            return;

        boolean isCallable = CAT_FUNCTION.equals( item.category )
                          || CAT_TYPE    .equals( item.category )
                          || CAT_METHOD  .equals( item.category );

        if( ! isCallable )
            return;   // keep whatever was showing — don't flicker on non-callable items

        // Extract base name from signature, e.g., "mid(target, from)" → "mid"
        String name     = item.text;
        int    parenPos = name.indexOf( '(' );
        if( parenPos > 0 )
            name = name.substring( 0, parenPos );

        // For methods, try the qualified name first (e.g., "pair.put"), then fall back to simple name
        if( CAT_METHOD.equals( item.category ) &&
            item.description != null           &&
            item.description.startsWith( "Method of " ) )
        {
            String typeName  = item.description.substring( 10 ).trim();
            String qualified = typeName + "." + name;

            if( ! docPopup.showDocumentationNextTo( qualified, this ) )
                docPopup.showDocumentationNextTo( name, this );
        }
        else
        {
            docPopup.showDocumentationNextTo( name, this );
        }
    }

    /**
     * Called when '(' is typed directly in the editor (outside of an active completion list).
     * Looks up the word immediately before the '(' and shows its API docs if it is a known callable.
     */
    private void onOpenParenTyped( int parenOffset )
    {
        if( isVisible() )
            return;   // Active completion list will handle docs via selection listener

        try
        {
            int    line      = editor.getLineOfOffset( parenOffset );
            int    lineStart = editor.getLineStartOffset( line );
            String before    = editor.getText( lineStart, parenOffset - lineStart ).trim();
            String name      = extractLastWord( before );

            if( name.isEmpty() )
                return;

            if( isKnownCallable( name ) )
                docPopup.showDocumentation( name, parenOffset );
        }
        catch( BadLocationException ignored ) {}
    }

    /**
     * Returns true if the given name (case-insensitive) is a known function, extended type, or method
     * that has API documentation.
     */
    private boolean isKnownCallable( String name )
    {
        String lower = name.toLowerCase();

        if( extendedCache   != null && extendedCache  .containsKey( lower ) ) return true;
        if( signaturesCache != null && signaturesCache.containsKey( lower ) ) return true;

        if( functionsCache != null )
        {
            for( String fn : functionsCache )
            {
                if( fn.equalsIgnoreCase( name ) )
                    return true;
            }
        }

        return false;
    }

    //------------------------------------------------------------------------//
    // PRIVATE METHODS - FILTERING AND INSERTION

    private boolean applyFilter()
    {
        listModel.clear();

        String filter = filterPrefix.toLowerCase();

        for( CompletionItem item : allItems )
        {
            if( filter.isEmpty() || item.text.toLowerCase().startsWith( filter ) )
            {
                listModel.addElement( item );
            }
        }

        // Check if there are multiple items with the same function name but different signatures
        // (e.g., "mid(target, from)" and "mid(where, start, end)")
        // In this case, show the list so user can choose, even if filter matches exactly
        if( listModel.size() > 1 && ! filter.isEmpty() )
        {
            // Count how many items have the same base name as the filter
            int exactMatches = 0;
            for( int i = 0; i < listModel.size(); i++ )
            {
                String itemText = listModel.get( i ).text;
                int parenPos = itemText.indexOf( '(' );
                String baseName = (parenPos > 0) ? itemText.substring( 0, parenPos ) : itemText;
                if( baseName.equalsIgnoreCase( filter ) )
                    exactMatches++;
            }

            // If multiple signatures match the same name, show the list for user selection
            if( exactMatches > 1 )
            {
                completionList.setSelectedIndex( 0 );
                return false;
            }
        }

        // If exactly one match for a callable AND the filter exactly matches the
        // base name, auto-insert it. Delegates to insertSelectedCompletion so the
        // single insertion path manages doc popup lifecycle consistently.
        if( listModel.size() == 1 )
        {
            CompletionItem item = listModel.get( 0 );

            if( CAT_FUNCTION.equals( item.category ) || CAT_TYPE.equals( item.category )
                || CAT_METHOD.equals( item.category ) )
            {
                String itemName = item.text;
                int    parenPos = itemName.indexOf( '(' );
                if( parenPos > 0 )
                    itemName = itemName.substring( 0, parenPos );

                if( itemName.equalsIgnoreCase( filter ) )
                {
                    completionList.setSelectedIndex( 0 );
                    insertSelectedCompletion();
                    return true;
                }
            }
        }

        if( listModel.size() > 0 )
        {
            completionList.setSelectedIndex( 0 );
            return false;
        }
        else
        {
            dismiss();
            return true;
        }
    }

    private void showInitializationErrors()
    {
        boolean driversLoaded    = (driversCache    != null);
        boolean extendedLoaded   = (extendedCache   != null);
        boolean signaturesLoaded = (signaturesCache != null);
        boolean functionsLoaded  = (functionsCache  != null);

        if( ! driversLoaded && ! extendedLoaded && ! signaturesLoaded && ! functionsLoaded )
        {
            StringBuilder msg = new StringBuilder();
            msg.append( "IntelliSense initialization failed.\n\n" );
            msg.append( "The following errors occurred:\n\n" );

            for( Exception e : initErrors )
            {
                msg.append( "  " ).append( e.getMessage() ).append( "\n" );
            }

            msg.append( "\nNone of the IntelliSense features are available." );
            JTools.error( msg.toString(), getFocusedWindow() );
            return;
        }

        StringBuilder msg = new StringBuilder();
        msg.append( "IntelliSense partially initialized.\n\n" );
        msg.append( "The following components failed to load:\n\n" );

        int failedCount = 0;

        if( ! driversLoaded )
        {
            msg.append( "  - Drivers (driver completion not available)\n" );
            failedCount++;
        }

        if( ! functionsLoaded )
        {
            msg.append( "  - Functions (function completion not available)\n" );
            failedCount++;
        }

        if( ! signaturesLoaded )
        {
            msg.append( "  - Signatures (function documentation not available)\n" );
            failedCount++;
        }

        if( ! extendedLoaded )
        {
            msg.append( "  - Extended types (method completion not available)\n" );
            failedCount++;
        }

        if( failedCount > 0 )
        {
            msg.append( "\nError details:\n\n" );
            for( Exception e : initErrors )
            {
                msg.append( "  " ).append( e.getMessage() ).append( "\n" );
            }
        }

        if( driversLoaded || functionsLoaded || signaturesLoaded || extendedLoaded )
        {
            msg.append( "\nAvailable IntelliSense features:\n\n" );

            if( driversLoaded    )  msg.append( "  - Driver completion (works)\n" );
            if( functionsLoaded  )  msg.append( "  - Function completion (works)\n" );
            if( signaturesLoaded )  msg.append( "  - Function documentation (works)\n" );
            if( extendedLoaded   )  msg.append( "  - Extended type methods (works)\n" );
        }

        msg.append( "\nSome IntelliSense features may not be available." );
        JTools.error( msg.toString(), getFocusedWindow() );
    }

    private static java.awt.Window getFocusedWindow()
    {
        return KeyboardFocusManager.getCurrentKeyboardFocusManager().getFocusedWindow();
    }

    private void insertCompletion( CompletionItem item )
    {
        try
        {
            String insertion = item.text;

            // For functions/methods/types, extract just the name (without signature parameters)
            if( CAT_FUNCTION.equals( item.category ) || CAT_TYPE.equals( item.category )
                || CAT_METHOD.equals( item.category ) )
            {
                int parenPos = insertion.indexOf( '(' );
                if( parenPos > 0 )
                    insertion = insertion.substring( 0, parenPos );  // Extract just the name

                // Check if parentheses already exist after cursor position
                boolean hasParentheses = false;
                int caretPos = editor.getCaretPosition();
                if( caretPos < editor.getDocument().getLength() )
                {
                    char nextChar = editor.getText( caretPos, 1 ).charAt( 0 );
                    hasParentheses = (nextChar == '(');
                }

                if( ! hasParentheses )
                    insertion += "()";

                // For methods, check if colon is needed before the method name
                if( CAT_METHOD.equals( item.category ) )
                {
                    boolean needsColon = true;
                    if( insertOffset > 0 )
                    {
                        char charBefore = editor.getText( insertOffset - 1, 1 ).charAt( 0 );
                        needsColon = (charBefore != ':');
                    }

                    if( needsColon )
                        insertion = ":" + insertion;
                }
            }

            editor.replaceRange( insertion, insertOffset, editor.getCaretPosition() );

            // If it ends with "()", move cursor inside the parentheses
            if( insertion.endsWith( "()" ) )
            {
                editor.setCaretPosition( insertOffset + insertion.length() - 1 );
            }
        }
        catch( Exception e )
        {
            // Ignore
        }
    }

    private void insertSelectedCompletion()
    {
        CompletionItem selected = completionList.getSelectedValue();

        if( selected == null )
            return;

        // Hide the completion list first; the doc popup is managed explicitly below
        // so it does not flicker through a hide/show cycle.
        setVisible( false );

        if( CAT_DRIVER.equals( selected.category ) && driversCache != null )
        {
            docPopup.setVisible( false );

            try
            {
                String insertion = selected.text;
                String config    = driversCache.get( selected.text );

                if( config != null && ! config.isEmpty() )
                {
                    StringBuilder sb = new StringBuilder();
                    sb.append( selected.text ).append( "\n" );
                    sb.append( "        CONFIG\n" );

                    for( String line : config.split( "\n" ) )
                    {
                        sb.append( "            " )
                          .append( UtilStr.replaceFirst( line.trim(), "AS", "SET" ) )
                          .append( "\n" );
                    }

                    insertion = sb.toString();
                }

                editor.replaceRange( insertion, insertOffset, editor.getCaretPosition() );
            }
            catch( Exception e )
            {
                JTools.alert( "Error inserting completion: " + e.getMessage() );
            }
        }
        else
        {
            insertCompletion( selected );

            boolean isCallable = CAT_FUNCTION.equals( selected.category )
                              || CAT_TYPE    .equals( selected.category )
                              || CAT_METHOD  .equals( selected.category );

            if( isCallable )
            {
                // Anchor the doc popup at the new caret position (now between the inserted
                // parentheses) so it is visually attached to where the user will type args.
                String name     = selected.text;
                int    parenPos = name.indexOf( '(' );
                if( parenPos > 0 )
                    name = name.substring( 0, parenPos );

                int caret = editor.getCaretPosition();

                if( CAT_METHOD.equals( selected.category )
                    && selected.description != null
                    && selected.description.startsWith( "Method of " ) )
                {
                    String type = selected.description.substring( 10 ).trim();

                    if( ! docPopup.showDocumentation( type + "." + name, caret ) )
                        docPopup.showDocumentation( name, caret );
                }
                else
                {
                    docPopup.showDocumentation( name, caret );
                }
            }
            else
            {
                docPopup.setVisible( false );
            }
        }

        editor.grabFocus();
    }

    //------------------------------------------------------------------------//
    // INNER CLASSES

    private enum Context
    {
        LINE_START,
        AFTER_DEVICE,
        AFTER_SENSOR,
        AFTER_ACTUATOR,
        AFTER_DRIVER_KEYWORD,
        AFTER_DRIVER_COMMAND,
        AFTER_RULE,
        AFTER_SCRIPT,
        AFTER_LANGUAGE,
        AFTER_FROM,
        AFTER_WHEN,
        AFTER_THEN,
        AFTER_IF,
        AFTER_CONFIG,
        AFTER_COLON,
        IN_EXPRESSION
    }

    private static final class CompletionItem
    {
        final String text;
        final String category;
        final String description;

        CompletionItem( String text, String category, String description )
        {
            this.text = text;
            this.category = category;
            this.description = description;
        }

        @Override
        public String toString()
        {
            return text;
        }
    }

    private static final class CompletionRenderer extends DefaultListCellRenderer
    {
        private static final Color COLOR_COMMAND  = new Color( 86, 156, 214 );   // Blue
        private static final Color COLOR_CLAUSE   = new Color( 156, 220, 254 );  // Light blue
        private static final Color COLOR_FUNCTION = new Color( 220, 220, 170 );  // Yellow
        private static final Color COLOR_METHOD   = new Color( 220, 220, 170 );  // Yellow
        private static final Color COLOR_TYPE     = new Color( 78, 201, 176 );   // Cyan
        private static final Color COLOR_DRIVER   = new Color( 206, 145, 120 );  // Orange
        private static final Color COLOR_BOOLEAN  = new Color( 86, 156, 214 );   // Blue
        private static final Color COLOR_OPERATOR = new Color( 212, 212, 212 );  // Gray
        private static final Color COLOR_MACRO    = new Color( 181, 206, 168 );  // Green

        @Override
        public Component getListCellRendererComponent( JList<?> list, Object value, int index,
                                                       boolean isSelected, boolean cellHasFocus )
        {
            JLabel label = (JLabel) super.getListCellRendererComponent( list, value, index, isSelected, cellHasFocus );

            if( value instanceof CompletionItem )
            {
                CompletionItem item = (CompletionItem) value;
                label.setText( item.text );

                if( isSelected )
                {
                    // Force selection colours regardless of focus state so the highlight
                    // is always visible (DefaultListCellRenderer dims it when unfocused).
                    label.setBackground( list.getSelectionBackground() );
                    label.setForeground( list.getSelectionForeground() );
                }
                else
                {
                    switch( item.category )
                    {
                        case CAT_COMMAND:  label.setForeground( COLOR_COMMAND );  break;
                        case CAT_CLAUSE:   label.setForeground( COLOR_CLAUSE );   break;
                        case CAT_FUNCTION: label.setForeground( COLOR_FUNCTION ); break;
                        case CAT_METHOD:   label.setForeground( COLOR_METHOD );   break;
                        case CAT_TYPE:     label.setForeground( COLOR_TYPE );     break;
                        case CAT_DRIVER:   label.setForeground( COLOR_DRIVER );   break;
                        case CAT_BOOLEAN:  label.setForeground( COLOR_BOOLEAN );  break;
                        case CAT_OPERATOR: label.setForeground( COLOR_OPERATOR ); break;
                        case CAT_MACRO:    label.setForeground( COLOR_MACRO );    break;
                    }
                }

                label.setToolTipText( item.description );
            }

            return label;
        }
    }

    //------------------------------------------------------------------------//
    // STATIC INITIALIZATION - DRIVER CACHE

    private static Map<String, String> initDrivers()
    {
        Map<String, String> map = new HashMap<>();
        List<String>        err = new ArrayList<>();
        List<String>        ioe = new ArrayList<>();
        List<String>        dup = new ArrayList<>();
        Charset             cs  = Charset.defaultCharset();

        try
        {
            for( URI uri : UtilIO.expandPath( "{*home.inc*}/**" ) )
            {
                if( UtilIO.hasExtension( uri, "une" ) )
                {
                    try
                    {
                        Pair<Collection<ParseBase>, Collection<ICandi.IError>> result =
                            UnecTools.transpile( uri, cs, UtilSys.getConfig().newXprEval() );

                        if( result.getValue().isEmpty() )
                        {
                            Map<String, String> m = parseDriverFile( new File( uri ) );

                            for( Map.Entry<String, String> entry : m.entrySet() )
                            {
                                String driverKey = entry.getKey();

                                // Check for duplicates (case-insensitive)
                                boolean isDuplicate = map.keySet()
                                                         .stream()
                                                         .anyMatch( k -> k.equalsIgnoreCase( driverKey ) );

                                if( ! isDuplicate )
                                    map.put( driverKey, entry.getValue() );
                                else
                                    dup.add( driverKey );
                            }
                        }
                        else
                        {
                            err.add( uri.toString() );
                        }
                    }
                    catch( IOException exc )
                    {
                        ioe.add( exc.getMessage() );
                    }
                }
            }
        }
        catch( URISyntaxException | IOException use )
        {
            ioe.add( use.getMessage() );
        }

        // Log errors but don't block initialization
        if( ! err.isEmpty() || ! ioe.isEmpty() || ! dup.isEmpty() )
        {
            StringBuilder msg = new StringBuilder();

            if( ! err.isEmpty() )
                msg.append( "Transpiler errors in: " ).append( err.size() ).append( " file(s)\n" );

            if( ! ioe.isEmpty() )
                msg.append( "IO errors reading: " ).append( ioe.size() ).append( " file(s)\n" );

            if( ! dup.isEmpty() )
                msg.append( "Duplicated drivers: " ).append( dup.size() ).append( "\n" );

            System.err.println( "UneIntelliSense initialization warnings:\n" + msg );
        }

        return map;
    }

    private static Map<String, String> parseDriverFile( File file ) throws IOException
    {
        Map<String, String> driverConfigMap = new HashMap<>();

        try( BufferedReader br = new BufferedReader( new FileReader( file ) ) )
        {
            String line;
            String driverName = null;
            StringBuilder configBuilder = null;
            boolean configSection = false;

            while( (line = br.readLine()) != null )
            {
                String trimmed = line.trim();
                String upper = trimmed.toUpperCase();

                if( trimmed.isEmpty() )
                {
                    // Empty line ends the current driver definition
                    configSection = false;

                    if( driverName != null )
                    {
                        String config = (configBuilder != null) ? configBuilder.toString().trim() : "";
                        driverConfigMap.put( driverName, config );
                    }

                    driverName = null;
                    configBuilder = null;
                }
                else if( upper.startsWith( "DRIVER " ) && ! upper.contains( "SCRIPT" ) )
                {
                    // New DRIVER definition - store previous if any
                    if( driverName != null )
                    {
                        String config = (configBuilder != null) ? configBuilder.toString().trim() : "";
                        driverConfigMap.put( driverName, config );
                    }

                    // Extract new driver name (second word, removing any trailing comment)
                    String[] parts = trimmed.split( "\\s+" );

                    if( parts.length > 1 )
                    {
                        driverName = parts[1];
                        configBuilder = null;
                        configSection = false;
                    }
                }
                else if( upper.startsWith( "CONFIG" ) )
                {
                    configSection = true;
                    configBuilder = new StringBuilder();
                }
                else if( upper.startsWith( "SCRIPT" ) )
                {
                    // SCRIPT clause ends CONFIG section but driver continues
                    configSection = false;
                }
                else if( configSection && ! upper.startsWith( "#" ) )
                {
                    // Capture config parameter line (skip comment-only lines)
                    configBuilder.append( trimmed ).append( System.lineSeparator() );
                }
            }

            // Store last driver if any
            if( driverName != null )
            {
                String config = (configBuilder != null) ? configBuilder.toString().trim() : "";
                driverConfigMap.put( driverName, config );
            }
        }

        return driverConfigMap;
    }
}
