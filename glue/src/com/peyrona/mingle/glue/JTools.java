
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
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.HeadlessException;
import java.awt.Image;
import java.awt.KeyboardFocusManager;
import java.awt.SystemColor;
import java.awt.Toolkit;
import java.awt.Window;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
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
import javax.swing.AbstractButton;
import javax.swing.Action;
import javax.swing.ActionMap;
import javax.swing.BorderFactory;
import javax.swing.ImageIcon;
import javax.swing.InputMap;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.JRootPane;
import javax.swing.JScrollPane;
import javax.swing.JSlider;
import javax.swing.JSpinner;
import javax.swing.JTable;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.JTree;
import javax.swing.KeyStroke;
import javax.swing.ListModel;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
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
 * Official web site at: <a href="https://github.com/peyrona/mingle">https://github.com/peyrona/mingle</a>
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

    /**
     * Displays an informational message dialog with automatically calculated display time.
     * <p>
     * The dialog shows a message with an "Information" title and can be closed by pressing
     * the Escape key or automatically after the calculated reading time expires.
     * </p>
     * <p>
     * Reading time calculation factors:
     * <ul>
     * <li>Base reading speed: 200-250 words per minute (~16.67 characters/second)</li>
     * <li>Character-based calculation: More reliable than word counting</li>
     * <li>Minimum display time: 3 seconds for very short messages</li>
     * <li>Complexity adjustments: Punctuation adds 10% per mark, numbers add 5% per digit</li>
     * <li>Maximum display time: 60 seconds for reasonable UI behavior</li>
     * </ul>
     * </p>
     *
     * @param msg the message to display; null or empty strings are treated as empty messages
     */
    public static void info( String msg )
    {
        msg = msg.trim();

        int chars = msg.length();

        // Base reading speed: 16.67 characters per second (1000 chars/minute)
        double baseSpeed = 16.67;

        // Adjust for complexity factors
        double complexityMultiplier = 1.0;

        // Add time for punctuation (requires brief pause)
        long punctuationCount = msg.chars()
                                   .filter( ch -> ch == '.' || ch == '!' || ch == '?' || ch == ';' || ch == ':' )
                                   .count();

        complexityMultiplier += (punctuationCount * 0.1); // 10% extra per punctuation

        // Add time for numbers (slower to process)
        long numberCount = msg.chars()
                              .filter( Character::isDigit )
                              .count();

        complexityMultiplier += (numberCount * 0.05); // 5% extra per digit

        double adjustedSpeed = baseSpeed / complexityMultiplier;
        double readingTime = chars / adjustedSpeed;

        // Minimum 2 seconds, maximum 60 seconds for reasonable UI display
        int delay = Math.max( 3, Math.min( 60, (int) Math.ceil( readingTime ) ) );

        info( msg, delay );
    }

    /**
     * Displays an informational message dialog with specified display time.
     * <p>
     * Creates a non-modal popup dialog with "Information" title that displays the
     * provided message. The dialog can be closed by pressing Escape key or automatically
     * after the specified delay. If delaySeconds is 0 or negative, the dialog remains
     * open until manually closed.
     * </p>
     * <p>
     * The dialog features:
     * <ul>
     * <li>Undecorated window (no title bar)</li>
     * <li>Always on top behavior</li>
     * <li>Automatic text wrapping for long messages (80 characters per line)</li>
     * <li>Centered positioning relative to focused window</li>
     * <li>Escape key support for immediate closing</li>
     * </ul>
     * </p>
     *
     * @param msg the message to display; HTML {@code <br>} tags are converted to newlines
     * @param delaySeconds number of seconds to display before auto-closing;
     *                     0 or negative values disable auto-close
     */
    public static void info( String msg, int delaySeconds )
    {
        msg = msg.replaceAll( "<br>", "//n" );

        // If received string is long and does not contains '\n', then, split it manually
        if( msg.indexOf( '\n' ) == -1 )
            msg = String.join( "\n", UtilStr.splitByLength( msg, 80, "null" ) );

        // Create frame with no decorations (title bar)
        JFrame frame = new JFrame();
               frame.setUndecorated( true );
               frame.setAlwaysOnTop( true );
               frame.setType( Window.Type.POPUP ); // Lightweight popup window
               frame.setFocusable( true );

        // Close on Esc key
        frame.getRootPane().registerKeyboardAction( e -> frame.dispose(),
                                                    KeyStroke.getKeyStroke( KeyEvent.VK_ESCAPE, 0 ),
                                                    JComponent.WHEN_IN_FOCUSED_WINDOW );

        // Center message area
        JLabel messageLabel = new JLabel( msg, SwingConstants.CENTER );
        Font   messageFont  = messageLabel.getFont();
        messageLabel.setFont( messageFont.deriveFont( (float) (messageFont.getSize() * 1.2f) ) );

        // Bottom label - smaller font
        JLabel bottomLabel = new JLabel( "[Esc] to close", SwingConstants.CENTER );
        Font   bottomFont  = bottomLabel.getFont();
        bottomLabel.setFont( bottomFont.deriveFont( (float) (bottomFont.getSize() * 0.8f) ) );

        // Create main panel with border layout and line border and add to Frame
        Border lineBorder  = BorderFactory.createLineBorder( SystemColor.windowBorder, 1 );
        Border emptyBorder = BorderFactory.createEmptyBorder( 12, 14, 12, 14 );

        JPanel mainPanel = new JPanel( new BorderLayout( 0, 16 ) );
               mainPanel.add( messageLabel, BorderLayout.CENTER );
               mainPanel.add( bottomLabel, BorderLayout.SOUTH );
               mainPanel.setBorder( BorderFactory.createCompoundBorder( lineBorder, emptyBorder ) );

        // Show frame
        frame.add( mainPanel );
        frame.pack();
        frame.setLocationRelativeTo( getFocusedWindow() );
        frame.requestFocusInWindow();
        frame.setVisible( true );

        // Auto-close after delay if specified
        if( delaySeconds > 0 )
        {
            Timer timer = new Timer( delaySeconds * 1000, e ->
                             {
                                 if( frame.isDisplayable() )
                                     frame.dispose();
                             } );

            timer.setRepeats( false );
            timer.start();
        }
    }

    /**
     * Displays a warning alert dialog with default parent window.
     * <p>
     * Shows a standard JOptionPane warning dialog with the provided message.
     * Uses the currently focused window as the parent component.
     * </p>
     *
     * @param msg the warning message to display
     */
    public static void alert( String msg )
    {
        alert( msg, getFocusedWindow() );
    }

    /**
     * Displays a warning alert dialog with specified parent component.
     * <p>
     * Shows a standard JOptionPane warning dialog with the provided message.
     * Long messages are automatically wrapped at 80 characters per line.
     * </p>
     *
     * @param msg the warning message to display
     * @param origin the parent component for the dialog; if null, uses focused window
     */
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

    /**
     * Displays a confirmation dialog with Yes/No options using default parent window.
     * <p>
     * Shows a standard JOptionPane confirmation dialog with a question icon.
     * Uses the currently focused window as the parent component.
     * </p>
     *
     * @param msg the confirmation message to display
     * @return {@code true} if user clicks "Yes", {@code false} if "No" or dialog is closed
     */
    public static boolean confirm( String msg )
    {
        return confirm( msg, null );
    }

    /**
     * Displays a confirmation dialog with Yes/No options using specified parent component.
     * <p>
     * Shows a standard JOptionPane confirmation dialog with a question icon.
     * The dialog blocks execution until the user makes a selection.
     * </p>
     *
     * @param msg the confirmation message to display
     * @param origin the parent component for the dialog; if null, uses focused window
     * @return {@code true} if user clicks "Yes", {@code false} if "No" or dialog is closed
     */
    public static boolean confirm( String msg, Component origin )
    {
        origin = (origin == null) ? getFocusedWindow() : origin;

        int result = JOptionPane.showConfirmDialog( origin,
                                                    msg,
                                                    "Confirmation",
                                                    JOptionPane.YES_NO_OPTION,
                                                    JOptionPane.QUESTION_MESSAGE );

        return (result == JOptionPane.YES_OPTION);
    }

    /**
     * Displays an error dialog with multiple action options using default parent window.
     * <p>
     * Shows an error dialog with options to ignore error, empty ExEn, exit Glue,
     * or copy error message to clipboard. Uses the currently focused window as parent.
     * </p>
     *
     * @param msg the error message to display
     */
    public static void error( String msg )
    {
        error( msg, null );
    }

    /**
     * Displays an error dialog with multiple action options using specified parent component.
     * <p>
     * Shows an error dialog with the following options:
     * <ul>
     * <li>"Ignore error" - dismisses the dialog</li>
     * <li>"Empty ExEn" - clears the ExEn tab pane</li>
     * <li>"Exit Glue" - terminates the application</li>
     * <li>"Copy to clipboard" - copies error message to system clipboard</li>
     * </ul>
     * Multi-line messages are displayed in a scrollable text area, single-line
     * messages in a text field. Automatically hides any visible wait frame.
     * </p>
     *
     * @param msg the error message to display
     * @param origin the parent component for the dialog; if null, uses focused window
     */
    public static void error( String msg, Component origin )
    {
        boolean        bMultiLine = (msg.indexOf( '\n' ) > -1) || msg.contains( UtilStr.sEoL );
        JComponent     comp;
        JTextComponent text;

        origin = (origin == null) ? getFocusedWindow() : origin;

        if( bMultiLine )
        {
            JTextArea  ta = new JTextArea( msg );
                       ta.setColumns( 80 );
                       ta.setLineWrap( false );
                       ta.setEditable( false );

            comp = new JScrollPane( ta );
            text = ta;
        }
        else
        {
            JTextField txt = new JTextField( msg );
                       txt.setColumns( Math.min( msg.length(), 132 ) );
                       txt.setEditable( false );

            comp = txt;
            text = txt;
        }

        hideWaitFrame();    // If the wait frame was open, the Glue freezes

        String[] options = { "Ignore error", "Empty ExEn", "Exit Glue", "Copy to clipboard" };
        int      result  = JOptionPane.showOptionDialog( origin, comp, "Error", JOptionPane.DEFAULT_OPTION, JOptionPane.ERROR_MESSAGE, null, options, options[0] );

        switch( result )
        {
            case 1: Main.frame.getExEnsTabPane().clear(); break;
            case 2: Main.exit();                          break;
            case 3: JTools.toClipboard( text.getText() ); break;
        }
    }

    /**
     * Displays an error dialog for an exception using default parent window.
     * <p>
     * Converts the exception to a string representation and displays it
     * in the standard error dialog with action options.
     * </p>
     *
     * @param exc the exception to display
     */
    public static void error( Exception exc )
    {
        error( exc, null );
    }

    /**
     * Displays an error dialog for an exception using specified parent component.
     * <p>
     * Converts the exception to a string representation using {@link UtilStr#toString(Exception)}
     * and displays it in the standard error dialog with action options.
     * </p>
     *
     * @param exc the exception to display
     * @param origin the parent component for the dialog; if null, uses focused window
     * @see #error(String, Component)
     */
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

        String s = (String) JOptionPane.showInputDialog( getFocusedWindow(), msg, title, JOptionPane.PLAIN_MESSAGE );

        return UtilStr.isEmpty( s ) ? null : s;
    }

    /**
     * Displays a custom dialog containing the specified component.
     * <p>
     * Creates a modal GDialog with the given title and sets the provided
     * component as the content pane. The dialog blocks execution until closed.
     * </p>
     *
     * @param title the dialog window title
     * @param panel the component to display as the dialog content
     */
    public static void ask( String title, JComponent panel )
    {
        GDialog dlg = new GDialog(title, true );
                dlg.setContentPane( panel );
                dlg.setVisible();
    }

    /**
     * Hides and disposes the currently visible wait frame.
     * <p>
     * Safely disposes the wait frame if it exists and resets the static reference
     * to null. The operation is performed on the Event Dispatch Thread to ensure
     * thread safety. If no wait frame is currently visible, this method does nothing.
     * </p>
     */
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

    /**
     * Displays a modal wait dialog with an indeterminate progress bar.
     * <p>
     * Creates a non-decorated, always-on-top dialog showing a message and
     * an animated indeterminate progress bar. The dialog is automatically
     * sized based on message length and positioned relative to the focused window.
     * </p>
     * <p>
     * Features:
     * <ul>
     * <li>Indeterminate progress bar animation</li>
     * <li>Dynamic sizing (250-400px width based on message length)</li>
     * <li>Escape key support for closing</li>
     * <li>Thread-safe execution on EDT</li>
     * <li>Automatic cleanup on errors</li>
     * <li>Safe positioning with fallback to screen center</li>
     * </ul>
     * </p>
     *
     * @param message the message to display; if null, shows "Please, wait..."
     */
    public static void showWaitFrame( final String message )
    {
        SwingUtilities.invokeLater( () ->
        {
            try
            {
                hideWaitFrame();     // Just in case there were one wnd open

                JProgressBar bar = new JProgressBar();
                bar.setIndeterminate( true );

                // Safe border color handling
                Color borderColor = (iconClr != null) ? iconClr : SystemColor.windowBorder;
                Border border = BorderFactory.createCompoundBorder(
                        BorderFactory.createLineBorder( borderColor, 1 ),
                        BorderFactory.createEmptyBorder( 16, 22, 16, 22 )
                );

                // Create message label with proper text
                String displayMessage = (message == null ? "Please, wait..." : message);
                JLabel messageLabel   = new JLabel( displayMessage, SwingConstants.CENTER );

                // Calculate dynamic size based on message length
                FontMetrics fm = messageLabel.getFontMetrics( messageLabel.getFont() );
                int textWidth = fm.stringWidth( displayMessage );
                int preferredWidth = Math.max( 250, Math.min( 400, textWidth + 80 ) ); // Min 250, Max 400
                int preferredHeight = 100;

                // Create message panel for better centering
                JPanel messagePanel = new JPanel( new FlowLayout( FlowLayout.CENTER, 0, 5 ) );
                       messagePanel.add( messageLabel );

                frmWait = new JFrame();
                frmWait.setLayout( new BorderLayout( 0, 8 ) );
                frmWait.getRootPane().setBorder( border );
                frmWait.add( messagePanel, BorderLayout.NORTH );
                frmWait.add( bar, BorderLayout.CENTER );

                // Safe window positioning
                Window referenceWindow = getFocusedWindow();

                if( referenceWindow != null )
                {
                    frmWait.setLocationRelativeTo( referenceWindow );
                }
                else
                {
                    // Center on screen if no parent window
                    Dimension screenSize = Toolkit.getDefaultToolkit().getScreenSize();
                    frmWait.setLocation( (screenSize.width - preferredWidth)   / 2,
                                         (screenSize.height - preferredHeight) / 2 );
                }

                frmWait.setDefaultCloseOperation( JFrame.DISPOSE_ON_CLOSE );
                frmWait.setAutoRequestFocus( true );
                frmWait.setAlwaysOnTop( true );
                frmWait.setUndecorated( true );
                frmWait.setIconImage( null );
                frmWait.setIconImages( null );
                frmWait.pack();
                frmWait.setSize( preferredWidth, preferredHeight );
                frmWait.setVisible( true );
                frmWait.toFront();
            }
            catch( Exception e )
            {
                // Ensure cleanup if something goes wrong
                hideWaitFrame();
                UtilSys.getLogger().log( ILogger.Level.WARNING, e, "Error showing wait frame" );
            }
        });
    }

    /**
     * Returns the currently focused window with fallback strategies.
     * <p>
     * Attempts to locate the active window in the following order:
     * <ol>
     * <li>Current focused window from KeyboardFocusManager</li>
     * <li>Active window as fallback</li>
     * <li>Main application frame as final fallback</li>
     * </ol>
     * </p>
     *
     * @return the focused window, or null if no window can be found
     */
    public static Window getFocusedWindow()
    {
        Window focusedWindow = KeyboardFocusManager.getCurrentKeyboardFocusManager().getFocusedWindow();

        if( focusedWindow == null )
        {
            // Try to get active window as fallback
            focusedWindow = KeyboardFocusManager.getCurrentKeyboardFocusManager().getActiveWindow();
        }

        if( focusedWindow == null && Main.frame != null )
        {
            // Final fallback to main frame
            focusedWindow = Main.frame;
        }

        return focusedWindow;
    }

    /**
     * Copies the specified string to the system clipboard.
     * <p>
     * Places the text content on the system clipboard for pasting into other
     * applications. If running in a headless environment, displays an error dialog.
     * </p>
     *
     * @param str the string to copy to clipboard; null values are ignored
     */
    public static void toClipboard( String str )
    {
        try
        {
            java.awt.datatransfer.Clipboard       clipboard = java.awt.Toolkit.getDefaultToolkit().getSystemClipboard();
            java.awt.datatransfer.StringSelection selection = new java.awt.datatransfer.StringSelection( str );

            if( clipboard != null && selection != null )
                clipboard.setContents( selection, null );
        }
        catch( HeadlessException ex )
        {
            JTools.error( "Failed to copy URL to clipboard: " + ex.getMessage() );
        }
    }

    //------------------------------------------------------------------------//
    // ABOUT UI WIDGETS

    /**
     * Loads an image resource from the classpath.
     * <p>
     * Attempts to load a PNG image from the "./images/" directory relative to
     * this class. If the specified image is not found, falls back to loading
     * the default "glue.png" image from the "/images/" directory.
     * </p>
     *
     * @param name the base name of the image file (without .png extension)
     * @return the loaded BufferedImage, or null if neither the specified nor fallback image can be loaded
     */
    public static BufferedImage getImage( String name )
    {
        name = UtilIO.addExtension( name, ".png" );

        try
        {
            return ImageIO.read( JTools.class.getResourceAsStream( "./images/"+ name ) );
        }
        catch( IOException exc )
        {
            try
            {
                return ImageIO.read( JTools.class.getResourceAsStream( "/images/glue.png" ) );
            }
            catch( IOException ex )
            {
                return null;
            }
        }
    }

    /**
     * Loads an ImageIcon from the classpath.
     * <p>
     * Uses {@link #getImage(String)} to load the image and wraps it in an ImageIcon.
     * Returns null if the image cannot be loaded.
     * </p>
     *
     * @param name the base name of the image file (without .png extension)
     * @return the loaded ImageIcon, or null if the image cannot be loaded
     * @see #getImage(String)
     */
    public static ImageIcon getIcon( String name )
    {
        Image image = getImage( name );

        return (image == null) ? null : new ImageIcon( image );
    }

    /**
     * Loads and scales an ImageIcon to specified dimensions.
     * <p>
     * Loads the specified image and scales it smoothly to the exact width and height
     * using {@link Image#SCALE_SMOOTH}. Returns null if the image cannot be loaded.
     * </p>
     *
     * @param name the base name of the image file (without .png extension)
     * @param width the desired width in pixels
     * @param height the desired height in pixels
     * @return scaled ImageIcon, or null if the image cannot be loaded
     * @see #getImage(String)
     */
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

    /**
     * Returns the default icon color used by the application.
     * <p>
     * Lazily initializes the color by getting the foreground color from a
     * JLabel component. This ensures the color matches the current Look and Feel.
     * </p>
     *
     * @return the default icon color for the current Look and Feel
     */
    public static Color getIconColor()
    {
        if( iconClr == null )
            iconClr = (new JLabel()).getForeground();

        return iconClr;
    }

    /**
     * Assigns a keyboard shortcut to a JButton.
     * <p>
     * Creates a key binding that triggers the button's action when the specified
     * key combination is pressed while the button's window is focused.
     * Automatically handles platform differences (Ctrl on Windows/Linux, Cmd on Mac).
     * </p>
     *
     * @param btn the button to assign shortcut to; should not be null
     * @param key the KeyEvent key code (e.g., KeyEvent.VK_S)
     * @param bCtrl whether Ctrl/Cmd modifier should be included
     * @param bShift whether Shift modifier should be included
     */
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

    /**
     * Assigns a keyboard shortcut to a JRootPane.
     * <p>
     * Creates a key binding that triggers the specified ActionListener when the
     * key combination is pressed while the root pane's window is focused.
     * Automatically handles platform differences (Ctrl on Windows/Linux, Cmd on Mac).
     * </p>
     *
     * @param root the root pane to assign shortcut to; should not be null
     * @param key KeyEvent key code (e.g., KeyEvent.VK_S)
     * @param bCtrl whether Ctrl/Cmd modifier should be included
     * @param bShift whether Shift modifier should be included
     * @param listener the action to execute when shortcut is pressed; should not be null
     */
    public static void setShortCut( JRootPane root, int key, boolean bCtrl, boolean bShift, ActionListener listener )
    {
        int ctrl = (bCtrl ? getPlatformShortcutKey() : 0);    // In Mac it is not Ctrl, but its own key

        if( bShift )
            ctrl = ctrl | InputEvent.SHIFT_DOWN_MASK;

        root.registerKeyboardAction( listener,
                                     KeyStroke.getKeyStroke( key, ctrl ),
                                     JComponent.WHEN_IN_FOCUSED_WINDOW );
    }

    /**
     * Checks if a text component is configured for UNE name input.
     * <p>
     * Determines if the text component has been marked as containing an UNE name
     * by checking for a specific client property. Used for input validation and
     * key filtering to ensure only valid UNE name characters are entered.
     * </p>
     *
     * @param txt the text component to check; should not be null
     * @return true if the component is configured for UNE name input, false otherwise
     * @see #setJTextIsUneName(JTextField)
     */
    public static boolean isJTextUneName( JTextComponent txt )
    {
        Object val = txt.getClientProperty( "_THIS_TEXT_COMPONENT_CONTAINS_AN_UNE_NAME_" );

        return (val instanceof Boolean) && ((Boolean) val);
    }

    /**
     * Marks a JTextField as being used for UNE name input.
     * <p>
     * Sets a client property on the text field to indicate it should accept
     * only valid UNE name characters. This enables input filtering and validation
     * specific to UNE naming conventions.
     * </p>
     *
     * @param txt the text field to mark for UNE name input; should not be null
     * @return the same JTextField instance for method chaining
     * @see #isJTextUneName(JTextComponent)
     */
    public static JTextField setJTextIsUneName( JTextField txt )
    {
        txt.putClientProperty( "_THIS_TEXT_COMPONENT_CONTAINS_AN_UNE_NAME_", true );
        return txt;
    }

    /**
     * Finds the index of the first item in a ListModel that matches the given predicate.
     * <p>
     * Iterates through the model elements and applies the provided function to each.
     * Returns the index of the first element for which the function returns true.
     * </p>
     *
     * @param model the ListModel to search; should not be null
     * @param fnFind the predicate function to apply to each element; should not be null
     * @return the index of the first matching element, or -1 if no match is found
     */
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

    /**
     * Sets preferred widths for table columns.
     * <p>
     * Applies the specified preferred widths to table columns in order.
     * If fewer widths are provided than columns, only the specified number
     * of columns are modified. If more widths are provided than columns,
     * extra widths are ignored.
     * </p>
     *
     * @param table the table whose columns to resize; should not be null
     * @param anWidth variable array of preferred widths in pixels for each column
     * @return the same JTable instance for method chaining
     */
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
     * Resizes a window to specified percentage of screen dimensions.
     * <p>
     * Sets window size based on percentage of screen width and height.
     * A value of -1 for either dimension preserves the current size.
     * Note that -1 values only work correctly after the window has been packed.
     * </p>
     *
     * @param wnd the window to resize; should not be null
     * @param widthPercent percentage of screen width (0-100) or -1 to keep current width
     * @param heightPercent percentage of screen height (0-100) or -1 to keep current height
     */
    public static void resizeAsPercent( Window wnd, int widthPercent, int heightPercent )
    {
        Dimension screen = Toolkit.getDefaultToolkit().getScreenSize();

        int nWidth  = (int) ((widthPercent  < 0) ? wnd.getWidth()  : (screen.width  * (widthPercent  / 100f)));
        int nHeight = (int) ((heightPercent < 0) ? wnd.getHeight() : (screen.height * (heightPercent / 100f)));

        wnd.setSize( nWidth, nHeight );
    }

    /**
     * Resizes a window to specified dimensions with intelligent defaults.
     * <p>
     * Sets window size with special handling for small values:
     * <ul>
     * <li>Values less than 10 are interpreted as percentages of screen size</li>
     * <li>Values 10 or greater are treated as absolute pixel dimensions</li>
     * <li>Final size is limited to not exceed screen dimensions</li>
     * </ul>
     * </p>
     *
     * @param wnd the window to resize; should not be null
     * @param width desired width in pixels, or percentage if < 10
     * @param height desired height in pixels, or percentage if < 10
     */
    public static void resize( Window wnd, int width, int height )
    {
        Dimension screen = Toolkit.getDefaultToolkit().getScreenSize();

        int nWidth  = (int) ((width  < 10) ? screen.width  / 5 : width );
        int nHeight = (int) ((height < 10) ? screen.height / 5 : height);

        nWidth  = Math.min( nWidth , screen.width  );
        nHeight = Math.min( nHeight, screen.height );

        wnd.setSize( nWidth, nHeight );
    }

    /**
     * Recursively removes all components from a container and its sub-containers.
     * <p>
     * Traverses the component hierarchy depth-first, removing all components
     * from containers at every level. After completion, the specified container
     * will be empty and all sub-containers will also be empty.
     * </p>
     *
     * @param from the root container to clear; should not be null
     */
    public static void removeAll( Container from )
    {
        for( Component c : from.getComponents() )
        {
            if( c instanceof Container )
                removeAll( (Container) c );
        }

        from.removeAll();
    }

    /**
     * Recursively collects all components from a container into the provided list.
     * <p>
     * Traverses the component hierarchy depth-first, adding every component
     * encountered to the provided storage list. The original storage list
     * is modified and returned for convenience.
     * </p>
     *
     * @param from the container to traverse; should not be null
     * @param storage list to collect components into; should not be null
     * @return the same storage list containing all found components
     */
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

    /**
     * Finds all components of specified types within a container hierarchy.
     * <p>
     * Recursively searches container and all sub-containers for components
     * that are instances of any of the provided classes. Uses isAssignableFrom()
     * to match classes and their subclasses.
     * </p>
     *
     * @param from container to search within; should not be null
     * @param aComponentClasses variable array of Class objects to match against
     * @return list of components that match any of the specified classes
     */
    public static List<Component> getOfClass( Container from, Class... aComponentClasses )
    {
        List<Class<?>> list = Arrays.asList( aComponentClasses );

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

    /**
     * Finds the first parent component of specified type in component hierarchy.
     * <p>
     * Traverses up the component tree from the child component, looking for
     * a parent that matches the specified class type. Uses multiple matching
     * strategies including instanceof, isAssignableFrom, and subclass checking.
     * </p>
     *
     * @param <T> the expected parent type
     * @param child the component to start searching from; should not be null
     * @param clazzOfParent the Class object representing the expected parent type
     * @return the first parent component of specified type, or null if not found
     */
    public static <T> T getParent( Component child, Class<T> clazzOfParent )
    {
        while( child != null )
        {
            Class clazz = child.getClass();

            if( clazzOfParent.isInstance( child )       ||
                clazz.isAssignableFrom( clazzOfParent ) ||
                isSubclass( clazz, clazzOfParent ) )
            {
                return clazzOfParent.cast( child );
            }

            child = child.getParent();
        }

        return null;
    }

    /**
     * Finds the first child component of specified type within a container.
     * <p>
     * Recursively searches the container and all sub-containers depth-first
     * for the first component that is an instance of the specified class.
     * Returns immediately upon finding the first match.
     * </p>
     *
     * @param <T> the expected child type
     * @param parent the container to search within; should not be null
     * @param clazzOfChild the Class object representing the expected child type
     * @return the first child component of specified type, or null if not found
     */
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

    /**
     * Finds the first component with specified name within a container hierarchy.
     * <p>
     * Recursively searches the container and all sub-containers for a component
     * whose name matches the provided string. Uses exact string comparison.
     * </p>
     *
     * @param from the container to search within; should not be null
     * @param name the component name to search for; should not be null
     * @return the first component with matching name, or null if not found
     */
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

    /**
     * Recursively traverses a container and sets its components' editable/enabled state.
     *
     * <p>This method distinguishes between:
     * <ul>
     * <li><b>JTextComponent</b> (JTextField, JTextArea, etc.): Sets {@code setEditable(editable)}.
     * This makes the field non-editable but still allows text selection and copying.
     * <li><b>Other interactive components</b> (JButton, JComboBox, JCheckBox, JSlider, etc.):
     * Sets {@code setEnabled(editable)}. This typically "grays out" the component,
     * making it non-interactive.
     * </ul>
     * </p>
     *
     * @param container The container (e.g., a JPanel) to traverse.
     * @param editable  {@code true} to make components editable/enabled, {@code false} to
     * make them non-editable/disabled.
     */
    public static void setEditable( Container container, boolean editable )
    {
        for( Component comp : container.getComponents() )
        {
                 if( comp instanceof JTextComponent )  ((JTextComponent) comp).setEditable( editable );
            else if( comp instanceof AbstractButton )  comp.setEnabled( editable );
            else if( comp instanceof JComboBox      )  comp.setEnabled( editable );
            else if( comp instanceof JList          )  comp.setEnabled( editable );
            else if( comp instanceof JSlider        )  comp.setEnabled( editable );
            else if( comp instanceof JSpinner       )  comp.setEnabled( editable );
            else if( comp instanceof JTree          )  comp.setEnabled( editable );
            else if( comp instanceof JTable         )  comp.setEnabled( editable );

            if( comp instanceof Container )
                setEditable( (Container) comp, editable );
        }
    }

    //------------------------------------------------------------------------//

    /**
     * Extracts time unit character from a formatted combo box selection.
     * <p>
     * Parses the selected item to extract the unit character from the
     * second-to-last position. Expected format: "Unit Name  (u)" where 'u'
     * is the unit character. Returns empty string for first item (index 0).
     * </p>
     *
     * @param cmbUnits combo box containing time unit selections; should not be null
     * @return unit character (e.g., "s", "m", "h") or empty string
     */
    public static String getTimeUnitFromComboBox(JComboBox<String> cmbUnits)
    {
        String sItem = cmbUnits.getSelectedItem().toString();
        String sUnit = "";
        if( cmbUnits.getSelectedIndex() > 0 )
        {
            char cUnit = sUnit.charAt( sItem.length() - 2 ); // e.g.: "Seconds  (s)"
            sUnit = Character.valueOf( cUnit ).toString();
        }
        return sUnit;
    }

    //------------------------------------------------------------------------//
    // ABOUT FILE MANAGAMENT

    /**
     * Displays a file chooser dialog for loading files.
     * <p>
     * Opens a system-styled file chooser dialog starting in specified folder.
     * Supports single or multiple file selection based on bMulti parameter.
     * Multiple selection is only available when FileType.Une is included in types.
     * </p>
     *
     * @param invoker parent component for dialog; if null, uses focused window
     * @param fFolder starting directory; if null, uses application home directory
     * @param bMulti whether to allow multiple file selection
     * @param types variable array of file type filters to apply
     * @return array of selected files; empty array if dialog is cancelled
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

    /**
     * Saves content to a file, showing file chooser if needed.
     * <p>
     * If file parameter is null, displays a file chooser dialog for user to select
     * save location. Automatically adds appropriate file extension based on FileType.
     * If file is provided, saves directly to that location.
     * </p>
     *
     * @param type determines file extension and dialog title
     * @param file destination file; if null, shows file chooser dialog
     * @param sContents content to write to file
     * @return the file that was saved (may be newly created from chooser)
     */
    public static File fileSaver( FileType type, File file, String sContents )
    {
        if( file == null )
        {
            JFileChooser jfc = new JFileChooser();
            jfc.setDialogTitle( "Save " + type + " file" );
            jfc.setDialogType( JFileChooser.SAVE_DIALOG );
            jfc.setCurrentDirectory( UtilSys.fHomeDir );
            jfc.setPreferredSize( new Dimension( 800, 800 ) );

            int option = jfc.showSaveDialog( getFocusedWindow() );

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