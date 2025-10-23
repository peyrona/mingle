
package com.peyrona.mingle.glue.codeditor.UneEditorTabContent;

import com.peyrona.mingle.candi.unec.parser.ParseBase;
import com.peyrona.mingle.candi.unec.transpiler.UnecTools;
import com.peyrona.mingle.glue.JTools;
import com.peyrona.mingle.lang.interfaces.ICandi;
import com.peyrona.mingle.lang.japi.Pair;
import com.peyrona.mingle.lang.japi.UtilColls;
import com.peyrona.mingle.lang.japi.UtilIO;
import com.peyrona.mingle.lang.japi.UtilStr;
import com.peyrona.mingle.lang.japi.UtilSys;
import java.awt.Dimension;
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
import javax.swing.DefaultListModel;
import javax.swing.JList;
import javax.swing.JPopupMenu;
import javax.swing.JScrollPane;
import javax.swing.ListSelectionModel;
import javax.swing.SwingUtilities;
import javax.swing.text.BadLocationException;
import org.fife.ui.rsyntaxtextarea.RSyntaxTextArea;

/**
 * Code completion popup for the Une editor providing intelligent suggestions.
 * <p>
 * This class implements a simplified IntelliSense-like functionality that provides
 * context-aware code completion suggestions including:
 * <ul>
 * <li>Available functions from the expression evaluator</li>
 * <li>Driver configurations with their CONFIG blocks</li>
 * <li>Extended data types and their methods</li>
 * <li>Automatic type detection based on current context</li>
 * </ul>
 * The popup is triggered by user interaction and provides keyboard and mouse
 * navigation for selecting suggestions.
 *
 * @author Francisco José Morero Peyrona
 *
 * Official web site at: <a href="https://github.com/peyrona/mingle">https://github.com/peyrona/mingle</a>
 */
final class DumbSense extends JPopupMenu
{
    private static       Map<String,String>         mapDrivers     = null;    // Key == driverName, Value == CONFIG contents
    private static       String[]                   asFunctions    = null;
    private static       Map<String, List<String>>  mapExtended    = null;    // Une Extended Data Types
    private        final JList<String>              jlist          = new JList<>( new DefaultListModel<>() );    // Popup Menu
    private        final RSyntaxTextArea            editor;
    private              Runnable                   fnItemSelected = () -> {};

    //------------------------------------------------------------------------//

    static
    {
        UtilSys.execute( null, () -> mapDrivers  = initDrivers() );
        UtilSys.execute( null, () -> asFunctions = UtilSys.getConfig().newXprEval().getFunctions() );
        UtilSys.execute( null, () -> mapExtended = UtilSys.getConfig().newXprEval().getExtendedTypes() );
    }

    //------------------------------------------------------------------------//

    DumbSense( RSyntaxTextArea edt )
    {
        super.add( new JScrollPane( jlist ) );

        editor = edt;

        setMinimumSize(   new Dimension( 200, 220 ) );
        setPreferredSize( new Dimension( 200, 300 ) );

        jlist.setFont( jlist.getFont().deriveFont( (float) jlist.getFont().getSize() - 2 ) );
        jlist.setSelectionMode( ListSelectionModel.SINGLE_SELECTION );
        jlist.addKeyListener( new KeyAdapter()
                                {
                                    @Override
                                    public void keyPressed( KeyEvent kpe )
                                    {
                                             if( kpe.getKeyCode() == KeyEvent.VK_ESCAPE )  setVisible( false );
                                        else if( kpe.getKeyCode() == KeyEvent.VK_ENTER  )  SwingUtilities.invokeLater( fnItemSelected );
                                    }
                                } );

        jlist.addMouseListener( new MouseAdapter()
                                {
                                    @Override
                                    public void mouseClicked( MouseEvent me )
                                    {
                                        if( me.getClickCount() == 2 )    // Double-click detected
                                              SwingUtilities.invokeLater( fnItemSelected );
                                    }
                                } );
    }

    //------------------------------------------------------------------------//

    void pop()
    {
        if( mapDrivers == null || asFunctions == null || mapExtended == null)
        {
            JTools.info( "DumbSense is initializing, please try again in few seconds" );
            return;
        }

        String   sWord = getPrevWord( editor );
        String[] sExtd = mapExtended.keySet().toArray( String[]::new );
        boolean  bDrvs = UtilColls.isNotEmpty( mapDrivers );

             if( UtilStr.contains( sWord, "driver" ) && bDrvs )  asDriversMenu();
        else if( UtilStr.contains( sWord, sExtd    )          )  asMethodsMenu( sWord );
        else                                                     asGenericMenu();

        try
        {
            int         pos  = editor.getCaretPosition();
            Rectangle2D rect = editor.modelToView2D( pos );
            JScrollPane pane = (JScrollPane) getComponent(0);
            JList       list = (JList)       pane.getViewport().getView();

            pack();
            show( editor, (int) rect.getX(), (int) rect.getY() );

            SwingUtilities.invokeLater( () -> list.grabFocus() );
        }
        catch( BadLocationException ble )
        {
            JTools.alert( "Error showinh popup menu "+ ble.getMessage() );
        }
    }

    //------------------------------------------------------------------------//

    private void asGenericMenu()
    {
        final String s1stItem = "functions > ";
        List<String> list = new ArrayList<>();
                     list.add( s1stItem );

        for( String sExtended : mapExtended.keySet() )
            list.add( sExtended + "()" );

        setItems( list.toArray( String[]::new ) );

        fnItemSelected = () ->
                            {
                                if( jlist.getModel().getElementAt( 0 ).equals( s1stItem ) )     // It is been shown the generic popup menu
                                {
                                    if( jlist.getSelectedIndex() == 0 )                         // Selected option is "Funcs >"
                                    {
                                        setItems( asFunctions );
                                        fnItemSelected = () ->
                                                            {
                                                                editor.insert( jlist.getSelectedValue(), editor.getCaretPosition() );
                                                                setVisible( false );
                                                            };
                                    }
                                    else
                                    {
                                        editor.insert( jlist.getSelectedValue(), editor.getCaretPosition() );
                                        setVisible( false );
                                    }
                                }
                                else   // It is been shown either all funcs or all methods belonging to a extended type
                                {

                                }
                            };
    }

    private void asMethodsMenu( String sType )
    {
        for( String s : mapExtended.keySet() )
        {
            if( UtilStr.contains( sType, s ) )
            {
                setItems( mapExtended.get( s ).toArray( String[]::new ) );
                break;
            }
        }

        fnItemSelected = () ->
                            {
                                editor.insert( jlist.getSelectedValue() + "()", editor.getCaretPosition() );
                                setVisible( false );
                            };
    }

    private void asDriversMenu()
    {
        ArrayList<String> list = new ArrayList<>( mapDrivers.keySet() );
        Collections.sort( list );

        setItems( list.toArray( String[]::new ) );

        fnItemSelected = () ->
                            {
                                StringBuilder sbIns = new StringBuilder()
                                                          .append( jlist.getSelectedValue() )
                                                          .append( "\n\t\tCONFIG\n" );

                                mapDrivers.get( jlist.getSelectedValue() )
                                                     .lines()
                                                     .forEach( (sLine) -> sbIns.append( "\t\t\t" ).append( UtilStr.replaceFirst( sLine, "AS", "SET" ) ).append( '\n') );

                                editor.insert( sbIns.toString(), editor.getCaretPosition() );
                                setVisible( false );
                            };
    }

    private String getPrevWord( RSyntaxTextArea editor )
    {
        int caretPos = editor.getCaretPosition();
        int start    = Math.max( 0, caretPos - 7 );    // Ensure the start position is not negative  (7 -> 'date():')

        return editor.getText().substring( start, caretPos );
    }

    private void setItems( String[] list )
    {
        DefaultListModel model = (DefaultListModel) jlist.getModel();

        model.clear();

        if( UtilColls.isNotEmpty( list ) )
        {
            for( String sItem : list )
                model.addElement( sItem );

            jlist.setSelectedIndex( 0 );
        }
    }

    //------------------------------------------------------------------------//

    private static Map<String,String> initDrivers()
    {
        Map<String,String> map = new HashMap<>();     // Key == driverName, Value == CONFIG contents
        List<String>       err = new ArrayList<>();   // Parser Errors
        List<String>       ioe = new ArrayList<>();   // IO Errors
        List<String>       dup = new ArrayList<>();   // Duplicated DRIVER names

        Charset cs  = Charset.defaultCharset();

        try
        {
            for( URI uri : UtilIO.expandPath( "{*home.inc*}/**" ) )
            {
                if( UtilIO.hasExtension( uri, "une" ) )
                {
                    try
                    {
                        Pair<Collection<ParseBase>,Collection<ICandi.IError>> result = UnecTools.transpile( uri, cs, UtilSys.getConfig().newXprEval() );

                        if( result.getValue().isEmpty() )     // No transpiler errors
                        {
                            Map<String,String> m = parseDriverFile( new File( uri ) );

                            for( Map.Entry<String,String> entry : m.entrySet() )
                            {
                                if( UtilColls.find( map.keySet(), (name) -> name.equalsIgnoreCase( entry.getKey() ) ) == null )
                                {
                                    map.put( entry.getKey(), entry.getValue() );
                                }
                                else
                                {
                                    dup.add( entry.getKey() );
                                }
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

        String msg = "";

        if( ! err.isEmpty() )
            msg += "Transpiler reported errors for following Une file(s) (correct the errors now):\n"+ UtilColls.toString( err ) + "\n\n";

        if( ! ioe.isEmpty() )
            msg += "IO error reading following Une file(s) (DRIVERs list is incomplete):\n"+ UtilColls.toString( ioe ) + "\n\n";

        if( ! dup.isEmpty() )
            msg += "Following DRIVER(s) are duplicated (only first found is used (correct this now):\n"+ UtilColls.toString( dup ) + "\n\n";

        if( ! msg.isBlank() )
            JTools.alert( UtilStr.removeLast( msg, 2 ) );

        return map;
    }

    private static Map<String,String> parseDriverFile( File file ) throws IOException
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
                line = line.trim(); // Remove leading and trailing whitespace
                if( line.isEmpty() )
                {
                    configSection = false;
                    if( driverName != null && configBuilder != null )
                    {
                        driverConfigMap.put( driverName, configBuilder.toString().trim() );
                    }
                    driverName = null;
                    configBuilder = null;
                }
                else if( line.toUpperCase().startsWith( "DRIVER" ) )
                {
                    driverName = line.split( "\\s+" )[1];
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
            // Add the last driver's configuration if it exists
            if( driverName != null && configBuilder != null )
            {
                driverConfigMap.put( driverName, configBuilder.toString().trim() );
            }
        }

        return driverConfigMap;
    }
}