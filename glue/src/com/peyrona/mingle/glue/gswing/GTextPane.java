package com.peyrona.mingle.glue.gswing;

/**
 *
 * @author francisco
 */
import com.peyrona.mingle.glue.JTools;
import com.peyrona.mingle.lang.japi.UtilStr;
import java.awt.Toolkit;
import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.StringSelection;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import javax.swing.JTextPane;
import javax.swing.event.CaretEvent;
import javax.swing.event.CaretListener;

/**
 * A JTextPane that automatically copies selected text to the system clipboard when the user finishes marking/selecting a block of text using mouse or keyboard.
 */
public class GTextPane extends JTextPane
{
    private int     lastSelectionStart = -1;
    private int     lastSelectionEnd   = -1;
    private boolean isMouseSelecting   = false;

    /**
     * Constructs a new GTextPane with auto-copy-on-select functionality.
     */
    public GTextPane()
    {
        super();

        initializeCaretListener();
        initializeMouseListener();
        initializeKeyListener();
    }

    /**
     * Initializes the mouse listener to detect when mouse selection ends.
     */
    private void initializeMouseListener()
    {
        addMouseListener( new MouseAdapter()
        {
            @Override
            public void mousePressed(MouseEvent e)
            {
                isMouseSelecting = true;
            }

            @Override
            public void mouseReleased(MouseEvent e)
            {
                if( isMouseSelecting && UtilStr.isNotEmpty( getSelectedText() ) )
                    copyCurrentSelectionToClipboard();

                isMouseSelecting = false;
            }
        } );
    }

    /**
     * Initializes the caret listener that monitors text selection changes.
     */
    private void initializeCaretListener()
    {
        addCaretListener( new CaretListener()
        {
            @Override
            public void caretUpdate(CaretEvent e)
            {
                int dot  = e.getDot();
                int mark = e.getMark();

                // Check if there's a selection (dot and mark are different)
                if( dot != mark )
                {
                    // User is currently selecting text
                    lastSelectionStart = Math.min( dot, mark );
                    lastSelectionEnd   = Math.max( dot, mark );
                }
                else
                {
                    // No current selection (caret just moved)
                    // Check if we had a previous selection and it wasn't from mouse
                    if( ! isMouseSelecting       &&
                        lastSelectionStart != -1 &&
                        lastSelectionEnd   != -1 &&
                        lastSelectionStart != lastSelectionEnd )
                    {
                        copyLastSelectionToClipboard();
                    }

                    lastSelectionStart = -1;
                    lastSelectionEnd   = -1;
                }
            }
        } );
    }

    /**
     * Initializes the key listener to detect Ctrl-A (Select All).
     */
    private void initializeKeyListener()
    {
        addKeyListener( new java.awt.event.KeyAdapter()
        {
            @Override
            public void keyReleased( java.awt.event.KeyEvent e )
            {
                // Check for Ctrl+A
                if( e.isControlDown() && e.getKeyCode() == java.awt.event.KeyEvent.VK_A )
                {
                    // A selection has just been made, copy it now.
                    copyCurrentSelectionToClipboard();

                    // CRITICAL: Reset the CaretListener's memory.
                    // This prevents the CaretListener from firing a *second*
                    // copy when the user deselects this text.
                    lastSelectionStart = -1;
                    lastSelectionEnd   = -1;
                }
            }
        } );
    }

    /**
     * Copies the currently selected text to the system clipboard.
     */
    private void copyCurrentSelectionToClipboard()
    {
        try
        {
            String selectedText = getSelectedText();

            if( selectedText != null && UtilStr.isNotEmpty( selectedText ) )
            {
                StringSelection strSelect = new StringSelection( selectedText );
                Clipboard       clipboard = Toolkit.getDefaultToolkit().getSystemClipboard();

                clipboard.setContents( strSelect, null );
                JTools.info( "Text copied to clipboard" );
            }
        }
        catch( Exception ex )
        {
            JTools.error( ex );
        }
    }

    /**
     * Copies the last selected text to the system clipboard.
     */
    private void copyLastSelectionToClipboard()
    {
        try
        {
            String text = getText();

            if( text != null && lastSelectionStart >= 0 &&
                lastSelectionEnd <= text.length()       &&
                lastSelectionStart < lastSelectionEnd )
            {

                String selectedText = text.substring( lastSelectionStart, lastSelectionEnd );

                if( UtilStr.isNotEmpty( selectedText ) )
                {
                    StringSelection strSelect = new StringSelection( selectedText );
                    Clipboard       clipboard = Toolkit.getDefaultToolkit().getSystemClipboard();

                    clipboard.setContents( strSelect, null );
                }
            }
        }
        catch( Exception ex )
        {
            JTools.error( ex );
        }
    }
}