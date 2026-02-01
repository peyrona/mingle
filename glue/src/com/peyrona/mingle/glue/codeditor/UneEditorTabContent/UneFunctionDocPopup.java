
package com.peyrona.mingle.glue.codeditor.UneEditorTabContent;

import com.peyrona.mingle.lang.interfaces.ILogger;
import com.peyrona.mingle.lang.japi.UtilIO;
import com.peyrona.mingle.lang.japi.UtilSys;
import java.awt.Color;
import java.awt.Dimension;
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
public final class UneFunctionDocPopup extends JPopupMenu
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

        add( scrollPane );

        setBackground( new Color( 45, 45, 45 ) );

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

    //------------------------------------------------------------------------//
    // PUBLIC INTERFACE

    /**
     * Shows the documentation popup for the given function or type.
     *
     * @param name     Function or type name (e.g., "pair", "list.get")
     * @param caretPos Current caret position in editor
     * @return true if documentation was shown, false if not available
     */
    public boolean showDocumentation( String name, int caretPos )
    {
        if( docsCache != null && docsCache.isEmpty() )    // This means that ::loadDocumentation() was invoked but failed
            return false;

        try
        {
            if( docsCache == null )
                docsCache = loadDocumentation();
        }
        catch( URISyntaxException | IOException exc )
        {
            docsCache = new HashMap<>();   // Flag indicating that init failed
            UtilSys.getLogger().log( ILogger.Level.SEVERE, exc, "Failed to load function documentation" );
        }

        String key = name.toLowerCase();
        List<DocEntry> entries = docsCache.get( key );

        // If not found, try finding by signature in the name list (e.g., if "mid(target, from)" was passed)
        if( entries == null )
        {
            for( List<DocEntry> list : docsCache.values() )
            {
                for( DocEntry entry : list )
                {
                    if( entry.signature.equalsIgnoreCase( name ) )
                    {
                        showHtml( entry.html, caretPos );
                        return true;
                    }
                }
            }
            return false;
        }

        // Combine all matching entries
        StringBuilder combinedHtml = new StringBuilder( "<html><body>" );
        for( int n = 0; n < entries.size(); n++ )
        {
            String html = entries.get( n ).html;
            // Strip HTML wrapper tags if present for combining
            // Handle both simple <html><body> and full <html><head>...</head><body> formats
            int bodyStart = html.indexOf( "<body>" );
            int bodyEnd   = html.lastIndexOf( "</body>" );

            if( bodyStart >= 0 && bodyEnd > bodyStart )
            {
                html = html.substring( bodyStart + 6, bodyEnd );
            }
            else
            {
                // Fallback: try simple replacement
                html = html.replace( "<html><body>", "" ).replace( "</body></html>", "" );
            }

            combinedHtml.append( html );
            if( n < entries.size() - 1 )
                combinedHtml.append( "<hr/>" );
        }
        combinedHtml.append( "</body></html>" );

        showHtml( combinedHtml.toString(), caretPos );
        return true;
    }

    private void showHtml( String html, int caretPos )
    {
        // Set the HTML content
        editorPane.setText( html );
        editorPane.setCaretPosition( 0 );  // Scroll to top

        try    // Show popup at caret position
        {
            Rectangle2D rect = editor.modelToView2D( caretPos );
            setPopupSize( scrollPane.getPreferredSize() );

            int x, y;

            if( rect != null )
            {
                x = (int) rect.getX();
                y = (int) rect.getY() + (int) rect.getHeight();
            }
            else
            {
                // Fallback: show near top-left of editor
                x = 10;
                y = 20;
            }

            show( editor, x, y );
            SwingUtilities.invokeLater( () -> editorPane.grabFocus() );
        }
        catch( BadLocationException | RuntimeException exc )
        {
            // Fallback: try showing at a default position
            try
            {
                setPopupSize( scrollPane.getPreferredSize() );
                show( editor, 10, 20 );
                SwingUtilities.invokeLater( () -> editorPane.grabFocus() );
            }
            catch( Exception ignored )
            {
                // If even fallback fails, nothing more we can do
            }
        }
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

    private static String cleanupHtml( String html )
    {
        // Extract just the main content using simple string parsing (no regex)
        StringBuilder cleaned = new StringBuilder();
        cleaned.append( "<html><head><style>" );
        cleaned.append( "body { font-family: sans-serif; }" );
        cleaned.append( "</style></head><body>" );

        // Extract class title using indexOf
        int titleStart = html.indexOf( "class=\"title\"" );

        if( titleStart > 0 )
        {
            int h2Start = html.lastIndexOf( "<h2", titleStart );
            int h2End = html.indexOf( "</h2>", titleStart );

            if( h2Start > 0 && h2End > h2Start )
            {
                cleaned.append( html.substring( h2Start, h2End + 5 ) );
            }
        }

        // Extract first description block
        int blockStart = html.indexOf( "<div class=\"block\">" );

        if( blockStart > 0 )
        {
            int blockEnd = html.indexOf( "</div>", blockStart );

            if( blockEnd > blockStart )
            {
                cleaned.append( html.substring( blockStart, blockEnd + 6 ) );
            }
        }

        // Extract method summary table
        int methodSummaryStart = html.indexOf( "<a id=\"method.summary\">" );

        if( methodSummaryStart > 0 )
        {
            int tableStart = html.indexOf( "<table", methodSummaryStart );
            int tableEnd = html.indexOf( "</table>", tableStart );

            if( tableStart > 0 && tableEnd > tableStart )
            {
                cleaned.append( "<h3>Methods</h3>" );
                cleaned.append( html.substring( tableStart, tableEnd + 8 ) );
            }
        }

        cleaned.append( "</body></html>" );

        return cleaned.toString();
    }

    private static void extractMethodDocs( Map<String, List<DocEntry>> cache, String typeName, String html )
    {
        // Find the method.detail section using simple string parsing (no regex to avoid StackOverflow)
        int methodDetailStart = html.indexOf( "<a id=\"method.detail\">" );

        if( methodDetailStart < 0 )
            return;

        // Find all method anchors like <a id="methodName(...)">
        int pos = methodDetailStart;

        while( true )
        {
            // Find next method anchor
            int anchorStart = html.indexOf( "<a id=\"", pos );

            if( anchorStart < 0 || anchorStart < pos )
                break;

            // Extract method name from anchor id (between <a id=" and the opening parenthesis)
            int idStart = anchorStart + 7;  // Length of '<a id="'
            int parenPos = html.indexOf( "(", idStart );

            if( parenPos < 0 || parenPos > idStart + 100 )  // Sanity check
            {
                pos = anchorStart + 7;
                continue;
            }

            String methodName = html.substring( idStart, parenPos );

            // Skip non-method anchors (like "method.detail", "constructor.detail", etc.)
            if( methodName.contains( "." ) || methodName.equals( typeName ) )
            {
                pos = parenPos;
                continue;
            }

            // Find the <li class="blockList"> that contains this method's content.
            // The distance limit must accommodate long method signatures with multiple parameters
            // (e.g., "mid(java.lang.Object,java.lang.Object,java.lang.Object)" adds ~104 chars).
            int liStart = html.indexOf( "<li class=\"blockList\">", anchorStart );

            if( liStart < 0 || liStart > anchorStart + 200 )
            {
                pos = anchorStart + 7;
                continue;
            }

            // Find the closing </li> for this method - it's followed by </ul> and then next <a id=
            // The method content ends at </li></ul> before the next anchor
            int nextAnchor = html.indexOf( "<a id=\"", liStart + 10 );
            int liEnd;

            if( nextAnchor > 0 )
            {
                // Find the </ul> before the next anchor
                liEnd = html.lastIndexOf( "</ul>", nextAnchor );

                if( liEnd < liStart )
                    liEnd = nextAnchor;
            }
            else
            {
                // Last method - find </section>
                liEnd = html.indexOf( "</section>", liStart );
            }

            if( liEnd < 0 )
                break;

            // Extract method content (from <li> to </ul>)
            String methodContent = html.substring( liStart, liEnd );

            // For StdXprFns, only include protected methods (skip public, private, etc.)
            // Extended types (date, time, list, pair) have public methods that should be included
            if( "StdXprFns".equals( typeName ) )
            {
                int sigStart = methodContent.indexOf( "<pre class=\"methodSignature\">" );
                if( sigStart >= 0 )
                {
                    int sigEnd = methodContent.indexOf( "</pre>", sigStart );
                    if( sigEnd > sigStart )
                    {
                        String methodSig = methodContent.substring( sigStart + 29, sigEnd );
                        if( ! methodSig.startsWith( "protected" ) )
                        {
                            pos = liEnd;
                            continue;
                        }
                    }
                }
            }

            // Build method documentation HTML
            String parameters = extractParamsFromContent( methodContent );
            String displayName = methodName.substring( 0, 1 ).toLowerCase() + methodName.substring( 1 );
            String signature = displayName + "(" + parameters + ")";
            String methodHtml = buildMethodHtml( typeName, displayName, parameters, methodContent );

            DocEntry entry = new DocEntry( displayName, signature, methodHtml );

            // Cache with both "typename.methodname" and just "methodname"
            addDoc( cache, typeName + "." + displayName, entry );
            addDoc( cache, displayName, entry );

            pos = liEnd;
        }
    }

    private static String extractParamsFromContent( String methodContent )
    {
        int sigStart = methodContent.indexOf( "<pre class=\"methodSignature\">" );
        if( sigStart >= 0 )
        {
            int sigEnd = methodContent.indexOf( "</pre>", sigStart );
            if( sigEnd > sigStart )
            {
                String sig = methodContent.substring( sigStart + 29, sigEnd );
                return extractParams( sig );
            }
        }
        return "";
    }

    private static String buildMethodHtml( String typeName, String methodName, String parameters, String methodContent )
    {
        StringBuilder html = new StringBuilder();
        html.append( "<html><body>" );

        String displayType = typeName.equals( "StdXprFns" ) ? "Function" : typeName;

        html.append( "<h3>" ).append( displayType ).append( ":" ).append( methodName ).append( "(" ).append( parameters ).append( ")</h3>" );

        // Extract method description
        int blockStart = methodContent.indexOf( "<div class=\"block\">" );

        if( blockStart >= 0 )
        {
            int blockEnd = methodContent.indexOf( "</div>", blockStart );

            if( blockEnd > blockStart )
            {
                String block = methodContent.substring( blockStart, blockEnd + 6 );
                html.append( block );
            }
        }

        // Extract parameters and return info
        int dlStart = methodContent.indexOf( "<dl>" );

        if( dlStart >= 0 )
        {
            int dlEnd = methodContent.indexOf( "</dl>", dlStart );

            if( dlEnd > dlStart )
            {
                String dl = methodContent.substring( dlStart, dlEnd + 5 );
                html.append( dl );
            }
        }

        html.append( "</body></html>" );

        return html.toString();
    }

    private static String extractParams( String sig )
    {
        int openParen = sig.indexOf( '(' );
        int closeParen = sig.lastIndexOf( ')' );
        if( openParen < 0 || closeParen <= openParen )
            return "";

        String paramsPart = sig.substring( openParen + 1, closeParen ).trim();
        if( paramsPart.isEmpty() )
            return "";

        // Use regex to remove HTML tags and entities, then split by comma
        paramsPart = paramsPart.replaceAll( "<[^>]*>", "" ).replaceAll( "&nbsp;", " " ).replaceAll( "&#8203;", "" );
        String[] parts = paramsPart.split( "," );
        StringBuilder sb = new StringBuilder( " " );
        for( int n = 0; n < parts.length; n++ )
        {
            String p = parts[n].trim();
            // Get the last word (the parameter name)
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