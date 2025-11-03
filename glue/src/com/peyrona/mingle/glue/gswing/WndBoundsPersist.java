package com.peyrona.mingle.glue.gswing;

import com.eclipsesource.json.Json;
import com.eclipsesource.json.JsonObject;
import com.peyrona.mingle.glue.ConfigManager;
import com.peyrona.mingle.glue.Main;
import com.peyrona.mingle.lang.MingleException;
import com.peyrona.mingle.lang.japi.UtilStr;
import com.peyrona.mingle.lang.lexer.Language;
import java.awt.Dialog;
import java.awt.Frame;
import java.awt.Rectangle;
import java.awt.Window;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.WindowEvent;
import java.awt.event.WindowListener;
import javax.swing.JDialog;
import javax.swing.JFrame;

/**
 * Common bounds persistence functionality for GDialog and GFrame.
 *
 * This class handles saving and restoring window bounds (position and size)
 * to a shared JSON file, allowing windows to maintain their previous
 * position and size across application sessions.
 *
 * Bounds are only saved if the position or size was changed after the window
 * becomes visible and before it's closed.
 *
 * @author Francisco José Morero Peyrona
 *
 * Official web site at: <a href="https://github.com/peyrona/mingle">https://github.com/peyrona/mingle</a>
 */
public final class WndBoundsPersist
{
    private static final Object FILE_LOCK = new Object();

    //------------------------------------------------------------------------//
    // Public interface
    //------------------------------------------------------------------------//

    /**
     * Initializes bounds persistence for a window (JDialog or JFrame).
     * This method should be called once during window construction.
     *
     * @param window The window to enable bounds persistence for
     */
    public static void initialize( Window window )
    {
        WindowBoundsTracker tracker = new WindowBoundsTracker( window );

        window.addComponentListener( tracker );
        window.addWindowListener(    tracker );
    }

    /**
     * Handles window bounds restoration/persistence during pack().
     * This method should be called from the window's pack() method.
     *
     * @param window The window to handle bounds for
     */
    public static void handlePack( Window window )
    {
        String     wndKey = getWindowKey( window );
        JsonObject bounds = ConfigManager.getWindowBounds( wndKey );

        if( bounds != null )   // Window exists: use saved bounds
        {
            int x      = bounds.getInt( "x"     , -1 );
            int y      = bounds.getInt( "y"     , -1 );
            int width  = bounds.getInt( "width" ,  0 );
            int height = bounds.getInt( "height",  0 );

            if( x > -1 && y > -1 && width > 0 && height > 0 )
            {
                window.setBounds( x, y, width, height );
                return;
            }
        }

        // Window doesn't exist: let default size and coordinates (do NOT save bounds)
        window.setLocationRelativeTo( Main.frame );
    }

    public static void reset()
    {
        ConfigManager.resetBounds();
    }

    //------------------------------------------------------------------------//
    // Private methods
    //------------------------------------------------------------------------//

    private static String getWindowKey( Window window )
    {
        String name = window.getName();
        String numb = null;

        // Windows are automatically named by Swing as "dialogN" or "frameN", where N is a number
        // This method filters out auto-generated names to use title instead

        if( name != null )
        {
                 if( window instanceof JDialog && name.startsWith( "dialog" ) )  numb = name.substring( 6 );
            else if( window instanceof JFrame  && name.startsWith( "frame"  ) )  numb = name.substring( 5 );

            if( Language.isNumber( numb ) )
                name = null;
        }
        // --------------------------------------------------------------------------

        if( UtilStr.isEmpty( name ) )
        {
            name = (window instanceof GFrame) ? ((Frame)  window).getTitle()
                                              : ((Dialog) window).getTitle();
        }

        if( UtilStr.isEmpty( name ) )
            throw new MingleException( MingleException.INVALID_ARGUMENTS );

        return name;
    }

    //------------------------------------------------------------------------//
    // Inner class to track window bounds changes
    //------------------------------------------------------------------------//

    private static class WindowBoundsTracker extends ComponentAdapter implements WindowListener
    {
        private final Window    window;
        private final Rectangle initial = new Rectangle( -1, -1, -1, -1);

        public WindowBoundsTracker( Window window )
        {
            this.window = window;
        }

        @Override
        public void componentResized( ComponentEvent e )
        {
        }

        @Override
        public void componentMoved( ComponentEvent e )
        {
        }

        @Override
        public void windowOpened( WindowEvent e )
        {
            initial.x      = window.getX();
            initial.y      = window.getY();
            initial.width  = window.getWidth();
            initial.height = window.getHeight();
        }

        @Override
        public void windowClosing( WindowEvent e )
        {
            Rectangle now = new Rectangle( window.getX(), window.getY(), window.getWidth(), window.getHeight() );

            if( now.equals( initial ) )
                return;

            // Only save bounds if we have initial bounds and now bounds changed
            JsonObject jo = Json.object()
                                .add( "x"     , now.x      )
                                .add( "y"     , now.y      )
                                .add( "width" , now.width  )
                                .add( "height", now.height );

                ConfigManager.setWindowBounds( WndBoundsPersist.getWindowKey( window ), jo );
        }

        @Override
        public void windowClosed( WindowEvent e ) {}
        @Override
        public void windowIconified( WindowEvent e ) {}
        @Override
        public void windowDeiconified( WindowEvent e ) {}
        @Override
        public void windowActivated( WindowEvent e ) {}  // Invoked every time the window get focus
        @Override
        public void windowDeactivated( WindowEvent e ) {}
    }
}