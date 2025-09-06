
package com.peyrona.mingle.glue;

import com.peyrona.mingle.lang.MingleException;
import com.peyrona.mingle.lang.japi.Config;
import com.peyrona.mingle.lang.japi.UtilCLI;
import com.peyrona.mingle.lang.japi.UtilIO;
import com.peyrona.mingle.lang.japi.UtilSys;
import com.peyrona.mingle.updater.Updater;
import java.awt.AWTException;
import java.awt.BorderLayout;
import java.awt.Robot;
import java.awt.event.KeyEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.File;
import java.io.IOException;
import java.net.URI;
import javax.swing.JFrame;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import javax.swing.UnsupportedLookAndFeelException;
import jiconfont.icons.font_awesome.FontAwesome;
import jiconfont.swing.IconFontSwing;

// https://github.com/parubok/awesome-swing

// To be added into MANIFEST.MF
// Class-Path: lib/lang.jar lib/candi.jar lib/minimal-json-0.9.5.jar lib/updater.jar lib/glue/jiconfont-1.0.0.jar lib/glue/jiconfont-font_awesome-4.7.0.1.jar lib/glue/jiconfont-swing-1.0.1.jar lib/glue/rsyntaxtextarea-3.3.3.jar

/**
 * A "Mission Control Tool" for the IoT: development and monitoring.
 *
 * @author Francisco José Morero Peyrona
 *
 * Official web site at: <a href="https://mingle.peyrona.com">https://mingle.peyrona.com</a>
 *
 * Official web site at: <a href="https://mingle.peyrona.com">https://mingle.peyrona.com</a>
 */
public class Main
{
    public  static final MainFrame frame = new MainFrame();

    //------------------------------------------------------------------------//

    /**
     * @param as the command line arguments
     * @throws java.io.IOException
     */
    public static void main( String[] as ) throws IOException
    {
        UtilCLI cli = new UtilCLI( as );

        UtilSys.setConfig( new Config().load( cli.getValue( "config", null ) )     // If defined, use this config file (instead of the default one)
                                       .setCliArgs( as ) );

        UtilSys.setLogger( "glue", UtilSys.getConfig() );

        //------------------------------------------------------------------------//
        // START GUI

        try
        {
            IconFontSwing.register( FontAwesome.getIconFont() );

            SwingUtilities.invokeLater( () ->
                {
                    try { UIManager.setLookAndFeel( UIManager.getSystemLookAndFeelClassName() ); }
                    catch( ClassNotFoundException | IllegalAccessException | InstantiationException | UnsupportedLookAndFeelException ex ) { /* Nothing to log */ }

                    frame.showIt();

                    if( (! UtilSys.isDevEnv) && Updater.isNeeded() &&    // To test Updater, use Updater:Test()
                        JTools.confirm( "There is a new version of the MSP (Mingle Standard Platform)\navailable for download.\n\nDo you want to update it now?" ) )
                    {
                        UtilSys.execute( Main.class.getName(), () -> Updater.update( null, (msg) -> JTools.error( msg ), UtilSys.isDevEnv ) );
                    }

                    Tip.show( "Welcome to Glue - The Mingle Swiss-knife tool\n\n"+
                              "Please read the MSP manual to get familiar with Glue.\n\n"+
                              "Suggested actions:\n"+
                              "     a) Connect with a running ExEn ('+' icon)\n"+
                              "     b) Start a local ExEn ('play' icon or F5)\n"+
                              "     c) Open the Script-Editor ('pencil' icon or F2)." );
                } );
        }
        catch( Exception exc )
        {
            JTools.error( exc );
        }
    }

    public static void exit()
    {
        frame.close();
    }

    //------------------------------------------------------------------------//
    // INNER CLASS
    // Main Frame
    //------------------------------------------------------------------------//
    public static final class MainFrame extends JFrame
    {
        private ExEnsTabbedPane tabExEn;
        private ToolbarPanel    toolBar;

        //------------------------------------------------------------------------//

        public ExEnsTabbedPane getExEnTabsPane()
        {
            return tabExEn;
        }

        //------------------------------------------------------------------------//

        private synchronized void showIt()
        {
            if( tabExEn != null || toolBar != null )
                throw new MingleException( "Can not invoke MainFrame::showIt() twice" );

            addWindowListener( new WindowAdapter()
            {
                @Override
                public void windowClosing( WindowEvent we )
                {
                    close();
                }
            } );

            tabExEn = new ExEnsTabbedPane();
            toolBar = new ToolbarPanel();

            ((BorderLayout) getLayout()).setVgap( 0 );

            setTitle( "Glue ::: Mission Control tool" );
            setAutoRequestFocus( true );
            setIconImage( JTools.getImage( "glue.png" ) );
            setDefaultCloseOperation( JFrame.DO_NOTHING_ON_CLOSE );
            add( toolBar, BorderLayout.NORTH  );
            add( tabExEn, BorderLayout.CENTER );
            pack();
            JTools.resize( this, (UtilSys.isDevEnv ? 50 : 85), 90 );
            setLocationRelativeTo( null );
            setVisible( true );

            if( UtilSys.isDevEnv )
            {
                UtilSys.execute( getClass().getName(),
                                 500,
                                 () ->
                                    {
                                        try
                                        {
                                            Robot robot = new Robot();
                                                  robot.keyPress( KeyEvent.VK_F4 );
                                                  robot.keyRelease( KeyEvent.VK_F4 );
                                        }
                                        catch( AWTException ex )
                                        {
                                            JTools.error( ex );
                                        }
                                    } );
            }
        }

        private void close()
        {
            try
            {
                if( Updater.isWorking() )
                {
                    JTools.alert( "Glue cannot be closed because it is being updated\nand could end in a state that would make the whole MSP unusable." );
                    return;
                }

                SwingUtilities.invokeAndWait( () -> JTools.showWaitFrame( "Exiting..." ) );

                if( ! toolBar.close() )    // This allows user to close ExEn and Gum the user started.
                    return;

                tabExEn.close();

                URI uri = UtilIO.expandPath( "{*home.tmp*}" ).get( 0 );
                UtilIO.delete( new File( uri ), (f) -> UtilIO.hasExtension( f, "json" ), false );

                System.exit( 0 );    // Smooth exit
            }
            catch( Throwable th )
            {
                System.exit( 0 );    // Must exit anyway
            }
        }
    }
}