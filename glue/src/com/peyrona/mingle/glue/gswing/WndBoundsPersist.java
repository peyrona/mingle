package com.peyrona.mingle.glue.gswing;

import com.eclipsesource.json.Json;
import com.eclipsesource.json.JsonObject;
import com.eclipsesource.json.JsonValue;
import com.peyrona.mingle.glue.JTools;
import com.peyrona.mingle.glue.Main;
import com.peyrona.mingle.lang.MingleException;
import com.peyrona.mingle.lang.japi.UtilIO;
import com.peyrona.mingle.lang.japi.UtilStr;
import com.peyrona.mingle.lang.japi.UtilSys;
import com.peyrona.mingle.lang.lexer.Language;
import java.awt.Dialog;
import java.awt.Frame;
import java.awt.Window;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.File;
import java.io.IOException;
import javax.swing.JDialog;
import javax.swing.JFrame;

/**
 * Common bounds persistence functionality for GDialog and GFrame.
 *
 * This class handles saving and restoring window bounds (position and size)
 * to a shared JSON file, allowing windows to maintain their previous
 * position and size across application sessions.
 *
 * @author Francisco José Morero Peyrona
 *
 * Official web site at: <a href="https://github.com/peyrona/mingle">https://github.com/peyrona/mingle</a>
 */
public final class WndBoundsPersist
{
    private static final File   WINDOWS_FILE = new File( UtilSys.getEtcDir(), "glue_wnds.txt" );
    private static final Object FILE_LOCK    = new Object();

    private static JsonObject joWindowSizes = null;

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
        loadWindowSizes();

        // Resize listener - updates in-memory bounds only
        window.addComponentListener( new ComponentAdapter()
        {
            @Override
            public void componentResized( ComponentEvent e )
            {
                joWindowSizes.set( getWindowKey( window ), getWindowBounds( window ) );
            }
        } );

        // Window closing listener - saves bounds to file
        window.addWindowListener( new WindowAdapter()
        {
            @Override
            public void windowClosing( WindowEvent we )
            {
                saveWindowSizes();
            }
        } );
    }

    /**
     * Handles window bounds restoration/persistence during pack().
     * This method should be called from the window's pack() method.
     *
     * @param window The window to handle bounds for
     */
    public static void handlePack( Window window )
    {
        String    wndKey = getWindowKey( window );
        JsonValue jvSize = joWindowSizes.get( wndKey );

        if( jvSize != null )   // Window exists: use saved bounds
        {
            JsonObject bounds = jvSize.asObject();
            int        x      = bounds.getInt( "x"     , -1 );
            int        y      = bounds.getInt( "y"     , -1 );
            int        width  = bounds.getInt( "width" ,  0 );
            int        height = bounds.getInt( "height",  0 );

            if( x > -1 && y > -1 && width > 0 && height > 0 )
            {
                window.setBounds( x, y, width, height );
                return;
            }
        }

        // Window doesn't exist: save bounds and let default size and coordinates
        joWindowSizes.set( wndKey, getWindowBounds( window ) );
        window.setLocationRelativeTo( Main.frame );
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

    private static JsonObject getWindowBounds( Window window )
    {
        return Json.object()
                   .add( "x"     , window.getX()      )
                   .add( "y"     , window.getY()      )
                   .add( "width" , window.getWidth()  )
                   .add( "height", window.getHeight() );
    }

    private static void loadWindowSizes()
    {
        synchronized( FILE_LOCK )
        {
            if( joWindowSizes != null )
                return;

            try
            {
                if( WINDOWS_FILE.exists() )
                {
                    String content = UtilIO.getAsText( WINDOWS_FILE );
                    joWindowSizes = Json.parse( content ).asObject();
                }
                else
                {
                    joWindowSizes = Json.object();
                }
            }
            catch( IOException ioe )
            {
                JTools.error( ioe );
                joWindowSizes = Json.object();
            }
            catch( Exception exc )
            {
                JTools.error( exc );
                joWindowSizes = Json.object();
            }
        }
    }

    private static void saveWindowSizes()
    {
        synchronized( FILE_LOCK )
        {
            try
            {
                UtilIO.newFileWriter()
                      .setFile( WINDOWS_FILE )
                      .replace( joWindowSizes.toString() );
            }
            catch( IOException ioe )
            {
                JTools.error( ioe );
            }
        }
    }
}