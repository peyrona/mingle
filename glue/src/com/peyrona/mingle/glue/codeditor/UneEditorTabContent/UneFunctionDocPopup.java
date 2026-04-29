
package com.peyrona.mingle.glue.codeditor.UneEditorTabContent;

import com.peyrona.mingle.lang.interfaces.ILogger;
import com.peyrona.mingle.lang.japi.UtilIO;
import com.peyrona.mingle.lang.japi.UtilSys;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Point;
import java.awt.Window;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.geom.Rectangle2D;
import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import javax.swing.BorderFactory;
import javax.swing.JEditorPane;
import javax.swing.JPopupMenu;
import javax.swing.JScrollPane;
import javax.swing.JWindow;
import javax.swing.SwingUtilities;
import javax.swing.text.BadLocationException;
import javax.swing.text.html.HTMLEditorKit;
import javax.swing.text.html.StyleSheet;
import org.fife.ui.rsyntaxtextarea.RSyntaxTextArea;

/**
 * Popup to display function/type documentation from Javadocs.
 * <p>
 * Shows HTML documentation for Une expression functions and extended types
 * when there's exactly one matching completion item. Documentation is loaded
 * from {@code todeploy/docs/javadocs.zip} and cached in memory.
 * </p>
 *
 * @author Francisco José Morero Peyrona
 *
 * Official web site at: <a href="https://github.com/peyrona/mingle">https://github.com/peyrona/mingle</a>
 */
public final class UneFunctionDocPopup
{
    // Path to javadocs ZIP relative to MSP home
    private static final String JAVADOCS_ZIP = "docs/javadocs.zip";

    // Path inside ZIP where function documentation is stored
    private static final String FUNCTIONS_PATH = "javadocs/lang/com/peyrona/mingle/lang/xpreval/functions/";

    // Types that have HTML documentation
    private static final String[] TYPES_WITH_DOCS = { "pair", "list", "date", "time", "StdXprFns" };

    /** Represents a documentation entry for a function or method. */
    public static final class DocEntry
    {
        public final String name;       // e.g., "mid"
        public final String signature;  // e.g., "mid(target, from)"
        public final String html;       // HTML content

        DocEntry( String name, String signature, String html )
        {
            this.name = name;
            this.signature = signature;
            this.html = html;
        }
    }

    // Cache: key → List of DocEntry (e.g., "pair" → [type doc], "mid" → [mid(target, from), mid(where, start, end)])
    private static Map<String, List<DocEntry>> docsCache = null;

    // UI components
    private final JEditorPane     editorPane;
    private final JScrollPane     scrollPane;
    private final RSyntaxTextArea editor;
    // JWindow created lazily on first show (editor must be in a window hierarchy by then).
    // Using JWindow instead of JPopupMenu avoids Swing's MenuSelectionManager, which
    // would auto-dismiss the completion-list popup whenever this doc popup appeared.
    private       JWindow         window;

    //------------------------------------------------------------------------//
    // CONSTRUCTOR

    public UneFunctionDocPopup( RSyntaxTextArea uneEditor )
    {
        editor = uneEditor;

        // Setup HTML editor pane
        editorPane = new JEditorPane();
        editorPane.setEditable( false );
        editorPane.setContentType( "text/html" );

        // Setup dark theme CSS
        HTMLEditorKit kit = new HTMLEditorKit();
        StyleSheet styleSheet = kit.getStyleSheet();
                   styleSheet.addRule( "body { background-color: #2D2D2D; color: #D4D4D4; font-family: sans-serif; font-size: 12px; padding: 5px; margin: 0; }" );
                   styleSheet.addRule( "h3 { color: #569CD6; margin-top: 5px; margin-bottom: 0px; font-size: 12px; }" );
                   styleSheet.addRule( "h1, h2, h4 { color: #569CD6; margin-top: 5px; margin-bottom: 0px; }" );
                   styleSheet.addRule( "a { color: #4EC9B0; text-decoration: none; }" );
                   styleSheet.addRule( "code, pre { color: #CE9178; background-color: #1E1E1E; padding: 2px 4px; }" );
                   styleSheet.addRule( "pre { padding: 8px; border-radius: 4px; overflow-x: auto; white-space: pre-wrap; }" );
                   styleSheet.addRule( ".methodSignature { color: #DCDCAA; background-color: #1E1E1E; padding: 4px; display: block; margin: 4px 0; }" );
                   styleSheet.addRule( ".block { margin: 5px 0 0 0; }" );
                   styleSheet.addRule( "dl { margin: 0; }" );
                   styleSheet.addRule( "dt { color: #9CDCFE; font-weight: bold; margin-top: 10px; }" );
                   styleSheet.addRule( "dd { margin-left: 20px; margin-bottom: 4px; }" );
                   styleSheet.addRule( "table { border-collapse: collapse; width: 100%; }" );
                   styleSheet.addRule( "th, td { border: 1px solid #3C3C3C; padding: 4px 8px; text-align: left; }" );
                   styleSheet.addRule( "th { background-color: #3C3C3C; color: #9CDCFE; }" );
                   styleSheet.addRule( ".typeNameLabel, .memberNameLink { color: #4EC9B0; }" );

        editorPane.setEditorKit( kit );
        editorPane.setBackground( new Color( 45, 45, 45 ) );

        // Setup scroll pane
        scrollPane = new JScrollPane( editorPane );
        scrollPane.setBorder( BorderFactory.createLineBorder( new Color( 60, 60, 60 ) ) );
        scrollPane.setBackground( new Color( 45, 45, 45 ) );
        scrollPane.getViewport().setBackground( new Color( 45, 45, 45 ) );
        scrollPane.setPreferredSize( new Dimension( 480, 360 ) );

        // Setup keyboard listener
        editorPane.addKeyListener( new KeyAdapter()
        {
            @Override
            public void keyPressed( KeyEvent e )
            {
                if( e.getKeyCode() == KeyEvent.VK_ESCAPE )
                {
                    setVisible( false );
                    editor.grabFocus();
                }
            }
        });
    }

    /**
     * Hides the documentation window.
     *
     * @param visible pass {@code false} to hide; {@code true} has no effect (use the
     *                {@code showDocumentation} / {@code showDocumentationNextTo} methods to show).
     */
    public void setVisible( boolean visible )
    {
        if( ! visible && window != null )
            window.setVisible( false );
    }

    //------------------------------------------------------------------------//
    // PUBLIC INTERFACE

    /**
     * Shows the documentation popup for the given function or type, positioned below the caret.
     *
     * @param name     Function or type name (e.g., "pair", "list.get", "mid(target, from)")
     * @param caretPos Current caret position in the editor document
     * @return true if documentation was found and shown, false if not available
     */
    public boolean showDocumentation( String name, int caretPos )
    {
        String html = resolveHtml( name );
        if( html == null ) return false;

        try
        {
            Rectangle2D rect      = editor.modelToView2D( caretPos );
            Point       editorLoc = editor.getLocationOnScreen();
            int         screenX   = editorLoc.x + (rect != null ? (int) rect.getX() : 10);
            int         screenY   = editorLoc.y + (rect != null ? (int) rect.getY() + (int) rect.getHeight() : 20);
            showHtml( html, screenX, screenY );
        }
        catch( BadLocationException | RuntimeException exc )
        {
            Point editorLoc = editor.getLocationOnScreen();
            showHtml( html, editorLoc.x + 10, editorLoc.y + 20 );
        }

        return true;
    }

    /**
     * Shows the documentation popup for the given function or type, positioned to the right of
     * a sibling popup (e.g., the IntelliSense completion list). Falls back to a fixed offset
     * if screen coordinates cannot be determined.
     *
     * @param name    Function or type name (e.g., "pair", "floor")
     * @param sibling Popup whose right edge defines the x anchor for this popup
     * @return true if documentation was found and shown, false if not available
     */
    public boolean showDocumentationNextTo( String name, JPopupMenu sibling )
    {
        String html = resolveHtml( name );
        if( html == null ) return false;

        try
        {
            Point siblingLoc = sibling.getLocationOnScreen();
            showHtml( html, siblingLoc.x + sibling.getWidth(), siblingLoc.y );
        }
        catch( Exception exc )
        {
            try
            {
                Point editorLoc = editor.getLocationOnScreen();
                showHtml( html, editorLoc.x + sibling.getWidth() + 10, editorLoc.y + 10 );
            }
            catch( Exception ignored ) {}
        }

        return true;
    }

    /**
     * Resolves the HTML documentation for the given name.
     * Handles: simple names ("floor"), qualified names ("list.get"),
     * and full signatures ("mid(target, from)").
     *
     * @return Combined HTML string, or null if not found or cache unavailable
     */
    private String resolveHtml( String name )
    {
        if( docsCache != null && docsCache.isEmpty() )    // loadDocumentation() was invoked but failed
            return null;

        try
        {
            if( docsCache == null )
                docsCache = loadDocumentation();
        }
        catch( URISyntaxException | IOException exc )
        {
            docsCache = new HashMap<>();
            UtilSys.getLogger().log( ILogger.Level.SEVERE, exc, "Failed to load function documentation" );
            return null;
        }

        String key = name.toLowerCase();
        List<DocEntry> entries = docsCache.get( key );

        // If not found by name, try matching by full signature (e.g., "mid(target, from)")
        if( entries == null )
        {
            for( List<DocEntry> list : docsCache.values() )
            {
                for( DocEntry entry : list )
                {
                    if( entry.signature.equalsIgnoreCase( name ) )
                        return entry.html;
                }
            }
            return null;
        }

        // Combine all matching entries (handles overloaded functions)
        StringBuilder combinedHtml = new StringBuilder( "<html><body>" );
        for( int n = 0; n < entries.size(); n++ )
        {
            String html = entries.get( n ).html;
            int bodyStart = html.indexOf( "<body>" );
            int bodyEnd   = html.lastIndexOf( "</body>" );

            if( bodyStart >= 0 && bodyEnd > bodyStart )
                html = html.substring( bodyStart + 6, bodyEnd );
            else
                html = html.replace( "<html><body>", "" ).replace( "</body></html>", "" );

            combinedHtml.append( html );
            if( n < entries.size() - 1 )
                combinedHtml.append( "<hr/>" );
        }
        combinedHtml.append( "</body></html>" );
        return combinedHtml.toString();
    }

    private void showHtml( String html, int screenX, int screenY )
    {
        editorPane.setText( html );
        editorPane.setCaretPosition( 0 );

        JWindow w = getWindow();
        w.setSize( scrollPane.getPreferredSize() );
        w.setLocation( screenX, screenY );
        w.setVisible( true );
    }

    /** Returns the lazily-created documentation window. */
    private JWindow getWindow()
    {
        if( window == null )
        {
            Window owner = SwingUtilities.getWindowAncestor( editor );
            window = new JWindow( owner );
            window.setFocusableWindowState( false );   // never steal focus from the completion list
            window.add( scrollPane );
            window.pack();
        }
        return window;
    }

    //------------------------------------------------------------------------//
    // PUBLIC INTERFACE - CACHE ACCESS

    /**
     * Returns all available signatures for functions and methods.
     *
     * @return Map where key is name/qualified name and value is list of signatures.
     */
    public static Map<String, java.util.List<String>> getSignatures()
    {
        if( docsCache != null && docsCache.isEmpty() )
            return new HashMap<>();

        try
        {
            if( docsCache == null )
                docsCache = loadDocumentation();
        }
        catch( URISyntaxException | IOException exc )
        {
            docsCache = new HashMap<>();
            UtilSys.getLogger().log( ILogger.Level.SEVERE, exc, "Failed to load signatures" );
        }

        Map<String, java.util.List<String>> signatures = new HashMap<>();
        for( Map.Entry<String, List<DocEntry>> entry : docsCache.entrySet() )
        {
            java.util.List<String> sigs = new java.util.ArrayList<>();
            for( DocEntry doc : entry.getValue() )
            {
                if( doc.signature != null && ! doc.signature.isEmpty() )
                    sigs.add( doc.signature );
            }
            if( ! sigs.isEmpty() )
                signatures.put( entry.getKey(), sigs );
        }

        return signatures;
    }

    //------------------------------------------------------------------------//
    // PRIVATE METHODS - CACHE INITIALIZATION

    private static Map<String, List<DocEntry>> loadDocumentation() throws URISyntaxException, IOException
    {
        Map<String, List<DocEntry>> mapCache = new HashMap<>();
        File                        fZip     = findJavadocsZip();

        try( ZipFile zip = new ZipFile( fZip ) )    // Load documentation for each type
        {
            for( String typeName : TYPES_WITH_DOCS )
            {
                String   path  = FUNCTIONS_PATH + typeName + ".html";
                ZipEntry entry = zip.getEntry( path );

                if( entry != null )
                {
                    String html = readZipEntry( zip, entry );
                    parseAndCacheTypeDoc( mapCache, typeName, html );
                }
                else
                {
                    UtilSys.getLogger().log( ILogger.Level.WARNING, "UneFunctionDocPopup: Entry not found: " + path );
                }
            }
        }

        return mapCache;
    }

    private static File findJavadocsZip() throws URISyntaxException, IOException
    {
        List<URI> uris = UtilIO.expandPath( "{*home*}" + JAVADOCS_ZIP );

        if( ! uris.isEmpty() )
        {
            File zipFile = new File( uris.get( 0 ) );

            if( zipFile.exists() )
                return zipFile;
        }

        throw new IOException( "Javadocs ZIP not found" );
    }

    private static String readZipEntry( ZipFile zip, ZipEntry entry ) throws IOException
    {
        StringBuilder content = new StringBuilder();

        try( InputStream is = zip.getInputStream( entry );
             BufferedReader reader = new BufferedReader( new InputStreamReader( is, StandardCharsets.UTF_8 ) ) )
        {
            String line;

            while( (line = reader.readLine()) != null )
            {
                content.append( line ).append( "\n" );
            }
        }

        return content.toString();
    }

    //------------------------------------------------------------------------//
    // PRIVATE METHODS - HTML PARSING

    private static void parseAndCacheTypeDoc( Map<String, List<DocEntry>> cache, String typeName, String fullHtml )
    {
        // Store full type documentation (cleaned up)
        String cleanedHtml = cleanupHtml( fullHtml );
        addDoc( cache, typeName.toLowerCase(), new DocEntry( typeName, "", cleanedHtml ) );

        // Extract and cache individual method documentation
        extractMethodDocs( cache, typeName, fullHtml );
    }

    private static void addDoc( Map<String, List<DocEntry>> cache, String key, DocEntry entry )
    {
        cache.computeIfAbsent( key.toLowerCase(), k -> new java.util.ArrayList<>() ).add( entry );
    }

    // NOTE: All HTML parsing below targets the Java 17+ Javadoc output format.
    // Key differences from the older Java 11 format:
    //   - Method detail section:  id="method-detail" on a <section> (was <a id="method.detail">)
    //   - Individual methods:     <section class="detail" id="name(params)"> (was <a id="..."> + <li class="blockList">)
    //   - Modifier visibility:    <span class="modifiers"> inside <div class="member-signature">
    //   - Parameter names:        <span class="parameters"> (was <pre class="methodSignature">)
    //   - Method summary:         id="method-summary" on a <section> with <div class="summary-table"> (was <a id="method.summary"> + <table>)
    //   - Class heading:          <h1 class="title"> (was <h2 class="title">)

    private static String cleanupHtml( String html )
    {
        StringBuilder cleaned = new StringBuilder();
        cleaned.append( "<html><head><style>body { font-family: sans-serif; }</style></head><body>" );

        // Class title — Java 17 uses <h1 class="title">, look for any <h1..h4 containing class="title"
        int titleStart = html.indexOf( "class=\"title\"" );
        if( titleStart > 0 )
        {
            int hStart = html.lastIndexOf( "<h", titleStart );
            int hEnd   = html.indexOf( ">", hStart );                // end of opening tag
            if( hStart > 0 && hEnd > hStart )
            {
                String tag    = html.substring( hStart + 1, hStart + 3 );   // e.g. "h1"
                int    closeH = html.indexOf( "</" + tag + ">", hEnd );
                if( closeH > hEnd )
                    cleaned.append( html, hStart, closeH + tag.length() + 3 );  // include </hN>
            }
        }

        // First class-level description block
        int blockStart = html.indexOf( "<div class=\"block\">" );
        if( blockStart > 0 )
        {
            int blockEnd = html.indexOf( "</div>", blockStart );
            if( blockEnd > blockStart )
                cleaned.append( html, blockStart, blockEnd + 6 );
        }

        // Method summary — Java 17 uses <div class="summary-table …"> inside id="method-summary"
        int methodSummaryStart = html.indexOf( "id=\"method-summary\"" );
        if( methodSummaryStart > 0 )
        {
            int divStart = html.indexOf( "<div class=\"summary-table", methodSummaryStart );
            if( divStart > 0 )
            {
                // Find matching </div> by tracking nesting depth
                int depth  = 0;
                int divEnd = -1;
                int scan   = divStart;
                while( scan < html.length() )
                {
                    int open  = html.indexOf( "<div", scan );
                    int close = html.indexOf( "</div>", scan );
                    if( close < 0 ) break;
                    if( open > 0 && open < close ) { depth++; scan = open + 4; }
                    else                           { if( depth == 0 ) { divEnd = close; break; }
                                                     depth--; scan = close + 6; }
                }
                if( divEnd > divStart )
                {
                    cleaned.append( "<h3>Methods</h3>" );
                    cleaned.append( html, divStart, divEnd + 6 );
                }
            }
        }

        cleaned.append( "</body></html>" );
        return cleaned.toString();
    }

    private static void extractMethodDocs( Map<String, List<DocEntry>> cache, String typeName, String html )
    {
        // Java 17: method detail section is a <section> with id="method-detail"
        int methodDetailStart = html.indexOf( "id=\"method-detail\"" );
        if( methodDetailStart < 0 )
            return;

        int pos = methodDetailStart;

        while( true )
        {
            // Each method lives in: <section class="detail" id="methodName(params)">
            int sectionStart = html.indexOf( "<section class=\"detail\" id=\"", pos );
            if( sectionStart < 0 )
                break;

            int idStart = sectionStart + 28;    // length of '<section class="detail" id="'
            int idEnd   = html.indexOf( "\"", idStart );
            if( idEnd < 0 )
                break;

            String anchorId = html.substring( idStart, idEnd );

            // IDs look like "methodName(fully.qualified.Params,...)"
            int parenPos = anchorId.indexOf( "(" );
            if( parenPos < 0 )
            {
                pos = sectionStart + 28;
                continue;
            }

            String methodName = anchorId.substring( 0, parenPos );

            if( methodName.contains( "." ) || methodName.equals( typeName ) )
            {
                pos = sectionStart + 28;
                continue;
            }

            // Find the closing </section> — method sections are flat (no nested <section>)
            int sectionEnd = html.indexOf( "</section>", idEnd );
            if( sectionEnd < 0 )
                break;

            String sectionContent = html.substring( sectionStart, sectionEnd );

            // For StdXprFns only keep protected methods; extended types are all public
            if( "StdXprFns".equals( typeName ) )
            {
                int modStart = sectionContent.indexOf( "<span class=\"modifiers\">" );
                if( modStart >= 0 )
                {
                    int modEnd = sectionContent.indexOf( "</span>", modStart );
                    if( modEnd > modStart )
                    {
                        String mods = sectionContent.substring( modStart + 24, modEnd )
                                                    .replaceAll( "<[^>]*>", "" ).trim();
                        if( ! mods.contains( "protected" ) )
                        {
                            pos = sectionEnd;
                            continue;
                        }
                    }
                }
            }

            String displayName = methodName.substring( 0, 1 ).toLowerCase() + methodName.substring( 1 );
            String parameters  = extractParamsFromSection( sectionContent );
            String signature   = displayName + "(" + parameters + ")";
            String methodHtml  = buildMethodHtml( typeName, displayName, parameters, sectionContent );

            DocEntry entry = new DocEntry( displayName, signature, methodHtml );
            addDoc( cache, typeName + "." + displayName, entry );
            addDoc( cache, displayName, entry );

            pos = sectionEnd;
        }
    }

    /** Extracts human-readable parameter names from the Java 17 {@code <span class="parameters">} element. */
    private static String extractParamsFromSection( String sectionContent )
    {
        // Java 17: <span class="parameters">(<Type>&nbsp;name, ...)</span>
        int paramStart = sectionContent.indexOf( "<span class=\"parameters\">" );
        if( paramStart < 0 )
            return "";

        int paramEnd = sectionContent.indexOf( "</span>", paramStart );
        if( paramEnd <= paramStart )
            return "";

        return extractParams( sectionContent.substring( paramStart + 25, paramEnd ) );
    }

    private static String buildMethodHtml( String typeName, String methodName, String parameters, String methodContent )
    {
        StringBuilder html = new StringBuilder();
        html.append( "<html><body>" );

        String displayType = typeName.equals( "StdXprFns" ) ? "Function" : typeName;
        html.append( "<h3>" ).append( displayType ).append( ":" )
            .append( methodName ).append( "(" ).append( parameters ).append( ")</h3>" );

        // Description block
        int blockStart = methodContent.indexOf( "<div class=\"block\">" );
        if( blockStart >= 0 )
        {
            int blockEnd = methodContent.indexOf( "</div>", blockStart );
            if( blockEnd > blockStart )
                html.append( methodContent, blockStart, blockEnd + 6 );
        }

        // Parameters / returns / throws — Java 17 uses <dl class="notes">
        int dlStart = methodContent.indexOf( "<dl" );
        if( dlStart >= 0 )
        {
            int dlEnd = methodContent.indexOf( "</dl>", dlStart );
            if( dlEnd > dlStart )
                html.append( methodContent, dlStart, dlEnd + 5 );
        }

        html.append( "</body></html>" );
        return html.toString();
    }

    private static String extractParams( String sig )
    {
        int openParen  = sig.indexOf( '(' );
        int closeParen = sig.lastIndexOf( ')' );
        if( openParen < 0 || closeParen <= openParen )
            return "";

        String paramsPart = sig.substring( openParen + 1, closeParen ).trim();
        if( paramsPart.isEmpty() )
            return "";

        // Strip HTML tags and entities, then reduce to just parameter names
        paramsPart = paramsPart.replaceAll( "<[^>]*>", "" )
                               .replaceAll( "&nbsp;",  " " )
                               .replaceAll( "&#8203;", "" )
                               .replaceAll( "\n",      " " );
        String[]      parts = paramsPart.split( "," );
        StringBuilder sb    = new StringBuilder( " " );
        for( int n = 0; n < parts.length; n++ )
        {
            String p = parts[n].trim();
            int lastSpace = p.lastIndexOf( ' ' );
            if( lastSpace >= 0 )
                p = p.substring( lastSpace + 1 );

            sb.append( p );
            if( n < parts.length - 1 )
                sb.append( ", " );
        }
        sb.append( " " );
        return sb.toString();
    }
}