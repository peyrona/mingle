
package com.peyrona.mingle.glue;

import com.peyrona.mingle.glue.gswing.GDialog;
import com.peyrona.mingle.lang.interfaces.ILogger;
import com.peyrona.mingle.lang.japi.UtilIO;
import com.peyrona.mingle.lang.japi.UtilStr;
import com.peyrona.mingle.lang.japi.UtilSys;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.Image;
import java.awt.Toolkit;
import java.awt.Window;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.InputEvent;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;
import javax.imageio.ImageIO;
import javax.swing.AbstractAction;
import javax.swing.Action;
import javax.swing.ActionMap;
import javax.swing.BorderFactory;
import javax.swing.ImageIcon;
import javax.swing.InputMap;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JProgressBar;
import javax.swing.JRootPane;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.KeyStroke;
import javax.swing.ListModel;
import javax.swing.SwingUtilities;
import javax.swing.border.Border;
import javax.swing.filechooser.FileNameExtensionFilter;
import javax.swing.table.TableColumn;
import javax.swing.table.TableColumnModel;
import javax.swing.text.JTextComponent;

/**
 * Swing related utilities.
 *
 * @author Francisco José Morero Peyrona
 *
 * Official web site at: <a href="https://mingle.peyrona.com">https://mingle.peyrona.com</a>
 *
 * Official web site at: <a href="https://mingle.peyrona.com">https://mingle.peyrona.com</a>
 */
public final class JTools
{
    public static enum FileType
    {
        Une, Model, JSON
    };

    private static JFrame frmWait = null;
    private static Color  iconClr = null;    // Can not init it here because the L&F perhaps is not initialized yet

    //------------------------------------------------------------------------//
    // ABOUT SHOWING MESSAGES

    public static void info( String msg )
    {
        info( msg, Main.frame );
    }

    public static void info( String msg, Component origin )
    {
        // If received string is long and does not contains '\n', then, split it manually
        if( msg.indexOf( '\n' ) == -1 )
        {
            msg = String.join( "\n", UtilStr.splitByLength( msg, 80, "null" ) );
        }

        JOptionPane.showMessageDialog( origin,
                                       msg,
                                       "Information",
                                       JOptionPane.INFORMATION_MESSAGE );
    }

    public static void alert( String msg )
    {
        alert( msg, Main.frame );
    }

    public static void alert( String msg, Component origin )
    {
        // If received string is long and does not contains '\n', then, split it manually
        if( msg.indexOf( '\n' ) == -1 )
        {
            msg = String.join( "\n", UtilStr.splitByLength( msg, 80, "null" ) );
        }

        JOptionPane.showMessageDialog( origin,
                                       msg,
                                       "Alert",
                                       JOptionPane.WARNING_MESSAGE );
    }

    public static boolean confirm( String msg )
    {
        return confirm( msg, null );
    }

    public static boolean confirm( String msg, Component origin )
    {
        origin = (origin == null) ? Main.frame : origin;

        int result = JOptionPane.showConfirmDialog( origin,
                                                    msg,
                                                    "Confirmation",
                                                    JOptionPane.YES_NO_OPTION,
                                                    JOptionPane.QUESTION_MESSAGE );

        return (result == JOptionPane.YES_OPTION);
    }

    public static void error( String msg )
    {
        error( msg, null );
    }

    public static void error( String msg, Component origin )
    {
        boolean bMultiLine = (msg.indexOf( '\n' ) > -1) || msg.contains( UtilStr.sEoL );
        JComponent comp;

        origin = (origin == null) ? Main.frame : origin;

        if( bMultiLine )
        {
            JTextArea ta = new JTextArea( msg );
                      ta.setColumns( 80 );
                      ta.setLineWrap( false );
                      ta.setEditable( false );
            comp = new JScrollPane( ta );
        }
        else
        {
            JTextField txt = new JTextField( msg );
                       txt.setColumns( Math.min( msg.length(), 132 ) );
                       txt.setEditable( false );
            comp = txt;
        }

        hideWaitFrame();    // If the wait frame was open, the Glue freezes

        String[] options = { "Ignore error", "Empty ExEn", "Exit Glue" };
        int      result  = JOptionPane.showOptionDialog( origin, comp, "Error", JOptionPane.DEFAULT_OPTION, JOptionPane.ERROR_MESSAGE, null, options, options[0] );

             if( result == 1 )  Main.frame.getExEnTabsPane().clear();
        else if( result == 2 )  Main.exit();
    }

    public static void error( Exception exc )
    {
        error( exc, null );
    }

    public static void error( Exception exc, Component origin )
    {
        error( UtilStr.toString( exc ), origin );
    }

    /**
     * Returns a string or null if filed is empty or Cancel button is clicked.
     *
     * @param msg Prompt to be shown.
     * @return A string or null if filed is empty or Cancel button is clicked.
     */
    public static String ask( String msg )
    {
        return ask( msg, (String) null );
    }

    /**
     * Returns a string or null if filed is empty or Cancel button is clicked.
     *
     * @param msg Prompt to be shown.
     * @param title Dialog window title.
     * @return A string or null if filed is empty or Cancel button is clicked.
     */
    public static String ask( String msg, String title )
    {
        msg   = (msg   == null) ? "Provide"            : msg;
        title = (title == null) ? "Please, provide..." : title;

        String s = (String) JOptionPane.showInputDialog( Main.frame, msg, title, JOptionPane.PLAIN_MESSAGE );

        return UtilStr.isEmpty( s ) ? null : s;
    }

    public static void ask( String title, JComponent panel )
    {
        GDialog dlg = new GDialog(title, true );
                dlg.setContentPane( panel );
                dlg.setVisible();
    }

    public static void hideWaitFrame()
    {
        if( frmWait != null )
        {
            SwingUtilities.invokeLater( () ->
            {
                if( frmWait != null )   // Needed again
                {
                    frmWait.dispose();
                    frmWait = null;
                }
            } );
        }
    }

    public static void showWaitFrame( final String message )
    {
        hideWaitFrame();     // Just in case there were one wnd open

        JProgressBar bar = new JProgressBar();
        bar.setIndeterminate( true );

        Border border = BorderFactory.createCompoundBorder( BorderFactory.createLineBorder( iconClr ),
                BorderFactory.createEmptyBorder( 16, 22, 16, 22 ) );

        frmWait = new JFrame();
        frmWait.setLayout( new BorderLayout( 0, 12 ) );
        frmWait.getRootPane().setBorder( border );
        frmWait.add( new JLabel( (message == null ? "Please, wait..." : message) ), BorderLayout.NORTH );
        frmWait.add( bar, BorderLayout.CENTER );
        frmWait.setDefaultCloseOperation( JFrame.DISPOSE_ON_CLOSE );
        frmWait.setLocationRelativeTo( Main.frame );
        frmWait.setAutoRequestFocus( true );
        frmWait.setAlwaysOnTop( true );
        frmWait.setUndecorated( true );
        frmWait.setIconImage( null );
        frmWait.setIconImages( null );
        frmWait.pack();
        frmWait.setSize( 240, 80 );
        frmWait.setVisible( true );
        frmWait.toFront();
    }

    //------------------------------------------------------------------------//
    // ABOUT UI WIDGETS

    public static BufferedImage getImage( String name )
    {
        name = UtilIO.addExtension( name, ".png" );

        try
        {
            return ImageIO.read( JTools.class.getResourceAsStream( name ) );
        }
        catch( IOException exc )
        {
            try
            {
                return ImageIO.read( JTools.class.getResourceAsStream( "glue.png" ) );
            }
            catch( IOException ex )
            {
                return null;
            }
        }
    }

    public static ImageIcon getIcon( String name )
    {
        Image image = getImage( name );

        return (image == null) ? null : new ImageIcon( image );
    }

    public static ImageIcon getIcon( String name, int width, int height )
    {
        Image image = JTools.getImage( name );

        if( image != null )
        {
            image = image.getScaledInstance( width, height, java.awt.Image.SCALE_SMOOTH );

            return new ImageIcon( image );
        }

        return null;
    }

    public static Color getIconColor()
    {
        if( iconClr == null )
            iconClr = (new JLabel()).getForeground();

        return iconClr;
    }

    public static Action toAction( ActionListener al )
    {
        return new ButtonAction( al );
    }

    public static void setShortCut( JButton btn, int key, boolean bCtrl, boolean bShift )
    {
        int ctrl = (bCtrl ? getPlatformShortcutKey() : 0);    // In Mac it is not Ctrl, but its own key

        if( bShift )
            ctrl = ctrl | InputEvent.SHIFT_DOWN_MASK;

        InputMap  inputMap  = btn.getInputMap( JComponent.WHEN_IN_FOCUSED_WINDOW );
        ActionMap actionMap = btn.getActionMap();
        Action    action    = btn.getAction();
        Object    name      = action.getValue( Action.NAME );

        inputMap.put( KeyStroke.getKeyStroke( key, ctrl ), name );
        actionMap.put( name, action );
    }

    public static void setShortCut( JRootPane root, int key, boolean bCtrl, boolean bShift, ActionListener listener )
    {
        int ctrl = (bCtrl ? getPlatformShortcutKey() : 0);    // In Mac it is not Ctrl, but its own key

        if( bShift )
            ctrl = ctrl | InputEvent.SHIFT_DOWN_MASK;

        root.registerKeyboardAction( listener,
                                     KeyStroke.getKeyStroke( key, ctrl ),
                                     JComponent.WHEN_IN_FOCUSED_WINDOW );
    }

    // Used to filter key pressed, so only keys can be used by the user
    public static boolean isJTextUneName( JTextComponent txt )
    {
        Object val = txt.getClientProperty( "_THIS_TEXT_COMPONENT_CONTAINS_AN_UNE_NAME_" );

        return (val instanceof Boolean) && ((Boolean) val);
    }

    // Used to filter key pressed, so only keys cvan be used by the user
    public static JTextField setJTextIsUneName( JTextField txt )
    {
        txt.putClientProperty( "_THIS_TEXT_COMPONENT_CONTAINS_AN_UNE_NAME_", true );
        return txt;
    }

    public static int getIndexForItem( ListModel model, Function<Object, Boolean> fnFind )
    {
        for( int n = 0; n < model.getSize(); n++ )
        {
            if( fnFind.apply( model.getElementAt( n ) ) )
            {
                return n;
            }
        }

        return -1;
    }

    public static JTable setTableColWidths( JTable table, int... anWidth )
    {
        TableColumnModel model = table.getColumnModel();

        for( int columnIndex = 0; columnIndex < anWidth.length; columnIndex++ )
        {
            TableColumn column = model.getColumn( columnIndex );
            column.setPreferredWidth( anWidth[columnIndex] );
        }

        return table;
    }

    /**
     * Resize as percent of the size of the scree passed Window.
     *
     * @param wnd           To resize
     * @param widthPercent  A percent or -1 to keep current Window width (-1 works only after wnd:pack())
     * @param heightPercent A percent or -1 to keep current Window height (-1 works only after wnd:pack())
     */
    public static void resize( Window wnd, int widthPercent, int heightPercent )
    {
        Dimension screen = Toolkit.getDefaultToolkit().getScreenSize();
        int nWidth  = (int) ((widthPercent  < 0) ? wnd.getWidth()  : (screen.width  * (widthPercent  / 100f)));
        int nHeight = (int) ((heightPercent < 0) ? wnd.getHeight() : (screen.height * (heightPercent / 100f)));

        wnd.setSize( nWidth, nHeight );
    }

    public static void removeAll( Container from )
    {
        for( Component c : from.getComponents() )
        {
            if( c instanceof Container )
                removeAll( (Container) c );
        }

        from.removeAll();
    }

    public static List<Component> getAll( Container from, List<Component> storage )
    {
        for( Component c : ((Container) from).getComponents() )
        {
            storage.add( c );

            if( c instanceof Container )
            {
                getAll( (Container) c, storage );
            }
        }

        return storage;
    }

    public static List<Component> getOfClass( Container from, Class... aComponentClasses )
    {
        List<Class> list = Arrays.asList( aComponentClasses );

        return getAll( from, new ArrayList<>() ).stream()
                .filter( (Component c) ->
                {
                    for( Class clz : list )
                    {
                        if( clz.isAssignableFrom( c.getClass() ) )
                        {
                            return true;
                        }
                    }
                    return false;
                } )
                .collect( Collectors.toList() );
    }

    public static <T> T getParent( Component child, Class<T> clazzOfParent )
    {
        while( child != null )
        {
            Class clazz = child.getClass();

            if( clazz.isInstance( clazzOfParent )       ||
                clazz.isAssignableFrom( clazzOfParent ) ||
                isSubclass( clazz, clazzOfParent ) )
            {
                return clazzOfParent.cast( child );
            }

            child = child.getParent();
        }

        return null;
    }

    public static <T> T getChild( Container parent, Class<T> clazzOfChild )
    {
        for( Component c : parent.getComponents() )
        {
            if( clazzOfChild.isInstance( c ) )
            {
                return clazzOfChild.cast( c );
            }

            if( c instanceof Container )
            {
                T child = getChild( (Container) c, clazzOfChild );

                if( child != null )
                {
                    return child;
                }
            }
        }

        return null;
    }

    public static Component getByName( Container from, String name )
    {
        for( Component c : getAll( from, new ArrayList<>() ) )
        {
            if( name.equals( c.getName() ) )
            {
                return c;
            }
        }

        return null;
    }

    //------------------------------------------------------------------------//
    // ABOUT FILE MANAGAMENT

    /**
     * Returns selected file(s) (multiple can be only when FiltType.Une).If cancelled, returns an empty array.
     *
     * @param invoker Parent component.
     * @param fFolder Where to start.
     * @param bMulti  When true, more than one file can be selected.
     * @param types    Which kind(s).
     * @return Returns selected file(s). If cancelled, an empty array.
     */
    public static File[] fileLoader( Component invoker, File fFolder, boolean bMulti, FileNameExtensionFilter... types )
    {
        String sTitle = "Select one "+ (bMulti ? "or more files" : "file") +" to load";

        JFileChooser jfc = new JFileChooser();        // Opens system L&F dialog (because this app uses the system L&F)
                     jfc.setDialogTitle( sTitle );
                     jfc.setDialogType( JFileChooser.OPEN_DIALOG );
                     jfc.setCurrentDirectory( ((fFolder == null) ? UtilSys.fHomeDir : fFolder) );
                     jfc.setFileSelectionMode( JFileChooser.FILES_ONLY );
                     jfc.setFileHidingEnabled( false );
                     jfc.setMultiSelectionEnabled( bMulti );
                     jfc.setAcceptAllFileFilterUsed( false );
                     jfc.setPreferredSize( new Dimension( 800, 800 ) );

        for( FileNameExtensionFilter type : types )
        {
            jfc.addChoosableFileFilter( type );
        }

        int returnValue = jfc.showOpenDialog( invoker );

        SwingUtilities.invokeLater( () -> invoker.requestFocus() );

        if( returnValue != JFileChooser.APPROVE_OPTION )
        {
            return new File[0];
        }

        if( bMulti )  return jfc.getSelectedFiles();
        else          return new File[] { jfc.getSelectedFile() };
    }

    public static File fileSaver( FileType type, File file, String sContents )
    {
        if( file == null )
        {
            JFileChooser jfc = new JFileChooser();
            jfc.setDialogTitle( "Save " + type + " file" );
            jfc.setDialogType( JFileChooser.SAVE_DIALOG );
            jfc.setCurrentDirectory( UtilSys.fHomeDir );
            jfc.setPreferredSize( new Dimension( 800, 800 ) );

            int option = jfc.showSaveDialog( Main.frame );

            if( option == JFileChooser.APPROVE_OPTION )
            {
                file = jfc.getSelectedFile();

                String sExt = type.toString().toLowerCase();

                if( !UtilIO.getExtension( file ).toLowerCase().equals( sExt ) )
                {
                    file = new File( file.getAbsolutePath() + '.' + sExt );
                }
            }
        }

        if( file != null )
        {
            try
            {
                UtilIO.newFileWriter()
                      .setFile( file )
                      .replace( sContents );
            }
            catch( IOException ex )
            {
                JTools.error( ex );
            }
        }

        return file;
    }

    //------------------------------------------------------------------------//
    // PRIVATE SCOPE

    private JTools()
    {
    }

    private static int getPlatformShortcutKey()
    {
        try
        {
            Class<?> clazz  = Toolkit.getDefaultToolkit().getClass();
            Method   method = clazz.getMethod( "getMenuShortcutKeyMaskEx" );

            return (int) method.invoke( Toolkit.getDefaultToolkit() );
        }
        catch( NoSuchMethodException | IllegalAccessException | InvocationTargetException ex )
        {
            UtilSys.getLogger().log( ILogger.Level.WARNING, ex );
            return -1;
        }
    }

    //------------------------------------------------------------------------//
    // INNER CLASS
    //------------------------------------------------------------------------//
    private static class ButtonAction extends AbstractAction
    {
        private final ActionListener al;

        public ButtonAction( ActionListener al )
        {
            super( null );

            this.al = al;
        }

        @Override
        public void actionPerformed( ActionEvent evt )
        {
            al.actionPerformed( evt );
        }
    }

    private static boolean isSubclass( Class<?> clazz, Class<?> superClass)
    {
        while( clazz != null )
        {
            if( clazz.equals( superClass ) )
            {
                return true;
            }
            clazz = clazz.getSuperclass();
        }

        return false;
    }
}