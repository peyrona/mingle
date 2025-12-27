
package com.peyrona.mingle.glue.codeditor.UneEditorTabContent;

import com.peyrona.mingle.candi.unec.parser.ParseBase;
import com.peyrona.mingle.candi.unec.transpiler.UnecTools;
import com.peyrona.mingle.glue.JTools;
import com.peyrona.mingle.lang.interfaces.ICandi;
import com.peyrona.mingle.lang.japi.Pair;
import com.peyrona.mingle.lang.japi.UtilIO;
import com.peyrona.mingle.lang.japi.UtilStr;
import com.peyrona.mingle.lang.japi.UtilSys;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;
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
    // Completion item categories
    private static final String CAT_COMMAND   = "command";
    private static final String CAT_CLAUSE    = "clause";
    private static final String CAT_FUNCTION  = "function";
    private static final String CAT_METHOD    = "method";
    private static final String CAT_TYPE      = "type";
    private static final String CAT_DRIVER    = "driver";
    private static final String CAT_BOOLEAN   = "boolean";
    private static final String CAT_OPERATOR  = "operator";
    private static final String CAT_MACRO     = "macro";

    // Cached completion data (loaded once)
    private static Map<String, String>        driversCache   = null;
    private static String[]                   functionsCache = null;
    private static Map<String, List<String>>  extendedCache  = null;

    // UI components
    private final JList<CompletionItem>       completionList;
    private final DefaultListModel<CompletionItem> listModel;
    private final RSyntaxTextArea             editor;

    // State
    private String                            filterPrefix   = "";
    private List<CompletionItem>              allItems       = new ArrayList<>();
    private int                               insertOffset   = 0;

    //------------------------------------------------------------------------//
    // STATIC INITIALIZATION

    static
    {
        UtilSys.execute( null, () -> driversCache   = initDrivers() );
        UtilSys.execute( null, () -> functionsCache = UtilSys.getConfig().newXprEval().getFunctions() );
        UtilSys.execute( null, () -> extendedCache  = UtilSys.getConfig().newXprEval().getExtendedTypes() );
    }

    //------------------------------------------------------------------------//
    // CONSTRUCTOR

    public UneIntelliSense( RSyntaxTextArea editor )
    {
        this.editor = editor;
        this.listModel = new DefaultListModel<>();
        this.completionList = new JList<>( listModel );

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
        if( driversCache == null || functionsCache == null || extendedCache == null )
        {
            JTools.info( "IntelliSense is initializing, please try again in a few seconds" );
            return;
        }

        // Analyze context and populate suggestions
        analyzeContextAndPopulate();

        if( allItems.isEmpty() )
            return;

        // Apply any existing filter
        applyFilter();

        // Show popup at caret position
        try
        {
            Rectangle2D rect = editor.modelToView2D( editor.getCaretPosition() );
            pack();
            show( editor, (int) rect.getX(), (int) rect.getY() + (int) rect.getHeight() );
            SwingUtilities.invokeLater( () -> completionList.grabFocus() );
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
                    case KeyEvent.VK_ESCAPE:
                        setVisible( false );
                        editor.grabFocus();
                        break;

                    case KeyEvent.VK_ENTER:
                    case KeyEvent.VK_TAB:
                        insertSelectedCompletion();
                        break;

                    case KeyEvent.VK_BACK_SPACE:
                        if( filterPrefix.length() > 0 )
                        {
                            filterPrefix = filterPrefix.substring( 0, filterPrefix.length() - 1 );
                            applyFilter();
                        }
                        break;

                    default:
                        // Handle typing for filter
                        char c = e.getKeyChar();

                        if( Character.isLetterOrDigit( c ) || c == '_' )
                        {
                            filterPrefix += c;
                            applyFilter();
                        }
                }
            }
        });

        completionList.addMouseListener( new MouseAdapter()
        {
            @Override
            public void mouseClicked( MouseEvent e )
            {
                if( e.getClickCount() == 2 )
                    insertSelectedCompletion();
            }
        });
    }

    //------------------------------------------------------------------------//
    // PRIVATE METHODS - CONTEXT ANALYSIS

    private void analyzeContextAndPopulate()
    {
        allItems.clear();
        filterPrefix = "";

        try
        {
            int caretPos = editor.getCaretPosition();
            int line = editor.getLineOfOffset( caretPos );
            int lineStart = editor.getLineStartOffset( line );
            String lineText = editor.getText( lineStart, caretPos - lineStart );
            String trimmedLine = lineText.trim().toUpperCase();

            insertOffset = caretPos;

            // Determine filter prefix (word being typed)
            filterPrefix = extractWordAtCursor( lineText );
            insertOffset = caretPos - filterPrefix.length();

            // Determine context and add appropriate completions
            Context ctx = determineContext( trimmedLine, line );

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

    private Context determineContext( String trimmedLine, int line )
    {
        if( trimmedLine.isEmpty() )
            return Context.LINE_START;

        // Check for specific contexts based on line content
        if( trimmedLine.endsWith( ":" ) )
            return Context.AFTER_COLON;

        if( trimmedLine.startsWith( "DEVICE" ) || trimmedLine.startsWith( "SENSOR" ) || trimmedLine.startsWith( "ACTUATOR" ) )
        {
            if( trimmedLine.contains( "DRIVER" ) && ! trimmedLine.endsWith( "DRIVER" ) )
                return Context.AFTER_DRIVER_KEYWORD;

            return Context.AFTER_DEVICE;
        }

        if( trimmedLine.startsWith( "DRIVER" ) && ! trimmedLine.contains( "SCRIPT" ) )
        {
            if( containsOnlyKeyword( trimmedLine, "DRIVER" ) )
                return Context.LINE_START;  // DRIVER command definition

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
        addItem( "SCRIPT",   CAT_CLAUSE, "Script implementing the driver" );
        addItem( "CONFIG",   CAT_CLAUSE, "Configuration parameters" );
    }

    private void addRuleClauses()
    {
        addItem( "WHEN",   CAT_CLAUSE, "Trigger condition" );
        addItem( "THEN",   CAT_CLAUSE, "Action to execute" );
        addItem( "IF",     CAT_CLAUSE, "Future condition" );
        addItem( "USE",    CAT_CLAUSE, "Local alias definition" );
        addItem( "AFTER",  CAT_CLAUSE, "Delay before action" );
        addItem( "WITHIN", CAT_CLAUSE, "Time window condition" );
        addItem( "ANY",    CAT_CLAUSE, "Any device in group" );
        addItem( "ALL",    CAT_CLAUSE, "All devices in group" );
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
        addItem( "SET",      CAT_OPERATOR, "Assignment operator" );
        addItem( "AS",       CAT_CLAUSE,   "Type specification" );
        addItem( "REQUIRED", CAT_CLAUSE,   "Mark as required" );
        addItem( "BOOLEAN",  CAT_TYPE,     "Boolean data type" );
        addItem( "NUMBER",   CAT_TYPE,     "Numeric data type" );
        addItem( "STRING",   CAT_TYPE,     "String data type" );
        addItem( "ANY",      CAT_TYPE,     "Any data type" );
    }

    private void addExpressionCompletions()
    {
        // Functions
        if( functionsCache != null )
        {
            for( String fn : functionsCache )
                addItem( fn + "()", CAT_FUNCTION, "Function" );
        }

        // Extended types
        if( extendedCache != null )
        {
            for( String type : extendedCache.keySet() )
                addItem( type + "()", CAT_TYPE, "Extended data type" );
        }

        // Boolean values
        addItem( "TRUE",   CAT_BOOLEAN, "Boolean true" );
        addItem( "FALSE",  CAT_BOOLEAN, "Boolean false" );
        addItem( "ON",     CAT_BOOLEAN, "Alias for TRUE" );
        addItem( "OFF",    CAT_BOOLEAN, "Alias for FALSE" );
        addItem( "YES",    CAT_BOOLEAN, "Alias for TRUE" );
        addItem( "NO",     CAT_BOOLEAN, "Alias for FALSE" );
        addItem( "OPEN",   CAT_BOOLEAN, "Alias for FALSE" );
        addItem( "CLOSED", CAT_BOOLEAN, "Alias for TRUE" );

        // Operator aliases
        addItem( "IS",     CAT_OPERATOR, "Equals (==)" );
        addItem( "SET",    CAT_OPERATOR, "Assignment (=)" );
        addItem( "AND",    CAT_OPERATOR, "Logical AND (&&)" );
        addItem( "OR",     CAT_OPERATOR, "Logical OR (||)" );
        addItem( "NOT",    CAT_OPERATOR, "Logical NOT (!)" );
        addItem( "ABOVE",  CAT_OPERATOR, "Greater than (>)" );
        addItem( "BELOW",  CAT_OPERATOR, "Less than (<)" );
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
                addItem( method + "()", CAT_METHOD, "Method of " + typeName );
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
                for( String method : entry.getValue() )
                    addItem( method + "()", CAT_METHOD, "Method" );
            }
        }
    }

    private String extractLastWord( String text )
    {
        String[] parts = text.split( "\\s+" );
        return parts.length > 0 ? parts[parts.length - 1] : "";
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
    // PRIVATE METHODS - FILTERING AND INSERTION

    private void applyFilter()
    {
        listModel.clear();

        String filter = filterPrefix.toLowerCase();

        for( CompletionItem item : allItems )
        {
            if( filter.isEmpty() || item.text.toLowerCase().startsWith( filter ) )
                listModel.addElement( item );
        }

        if( listModel.size() > 0 )
            completionList.setSelectedIndex( 0 );
        else
            setVisible( false );
    }

    private void insertSelectedCompletion()
    {
        CompletionItem selected = completionList.getSelectedValue();

        if( selected == null )
            return;

        setVisible( false );

        try
        {
            String insertion = selected.text;

            // For drivers, insert the CONFIG template
            if( CAT_DRIVER.equals( selected.category ) && driversCache != null )
            {
                String config = driversCache.get( selected.text );

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
            }

            // Remove the filter prefix before inserting
            editor.replaceRange( insertion, insertOffset, editor.getCaretPosition() );
        }
        catch( Exception e )
        {
            JTools.alert( "Error inserting completion: " + e.getMessage() );
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

                if( ! isSelected )
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
                                boolean isDuplicate = map.keySet().stream()
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
                line = line.trim();

                if( line.isEmpty() )
                {
                    configSection = false;

                    if( driverName != null && configBuilder != null )
                        driverConfigMap.put( driverName, configBuilder.toString().trim() );

                    driverName = null;
                    configBuilder = null;
                }
                else if( line.toUpperCase().startsWith( "DRIVER" ) )
                {
                    String[] parts = line.split( "\\s+" );

                    if( parts.length > 1 )
                        driverName = parts[1];
                }
                else if( line.toUpperCase().startsWith( "CONFIG" ) )
                {
                    configSection = true;
                    configBuilder = new StringBuilder();
                }
                else if( configSection )
                {
                    configBuilder.append( line ).append( System.lineSeparator() );
                }
            }

            if( driverName != null && configBuilder != null )
                driverConfigMap.put( driverName, configBuilder.toString().trim() );
        }

        return driverConfigMap;
    }
}
