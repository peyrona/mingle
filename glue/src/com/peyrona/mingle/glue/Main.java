
package com.peyrona.mingle.glue;

import com.peyrona.mingle.lang.MingleException;
import com.peyrona.mingle.lang.interfaces.ILogger;
import com.peyrona.mingle.lang.japi.Config;
import com.peyrona.mingle.lang.japi.UtilCLI;
import com.peyrona.mingle.lang.japi.UtilIO;
import com.peyrona.mingle.lang.japi.UtilSys;
import com.peyrona.mingle.updater.Updater;
import java.awt.BorderLayout;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.function.Supplier;
import javax.swing.JFrame;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import javax.swing.UnsupportedLookAndFeelException;
import jiconfont.icons.font_awesome.FontAwesome;
import jiconfont.swing.IconFontSwing;

/**
 * A "Mission Control Tool" for the IoT: development and monitoring.
 *
 * @author Francisco José Morero Peyrona
 *
 * Official web site at: <a href="https://github.com/peyrona/mingle">https://github.com/peyrona/mingle</a>
 */
public class Main
{
    public static final MainFrame frame = new MainFrame();

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

                    Tip.show( "Welcome to Glue - The Mingle Swiss-knife tool\n\n"+
                              "Please read the Mingle Standard Platform manual to get familiar with Glue.\n\n"+
                              "Suggested actions:\n"+
                              "     a) Connect with a running ExEn ('+' icon)\n"+
                              "     b) Start a local ExEn ('play' icon or F5)\n"+
                              "     c) Open the Script-Editor ('pencil' icon or F2)." );

                    if( shouldCheckForUpdates() )
                    {
                        File              fBase   = new File( (UtilSys.isDevEnv ? "../todeploy" : ".") );
                        boolean           bDryRun = UtilSys.isDevEnv;
                        Supplier<Boolean> fnAsk   = () -> { return JTools.confirm( "There is a new MSP version available.\nDo you want to update now?" ); };

                        Updater.updateIfNeeded( fBase, bDryRun, fnAsk );
                    }
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
    // PRIVATE
    //------------------------------------------------------------------------//

    private static boolean shouldCheckForUpdates()
    {
        if( UtilSys.isDevEnv )
            return false;

        File file = new File( UtilSys.getEtcDir(), "glue_last_update_check.txt" );

        try
        {
            String todayDate     = LocalDate.now().format( DateTimeFormatter.ISO_LOCAL_DATE );
            String lastCheckDate = null;

            if( file.exists() )
                lastCheckDate = UtilIO.getAsText( file ).trim();

            if( todayDate.equals( lastCheckDate ) )
                return false;

            UtilIO.newFileWriter()
                  .setFile( file )
                  .append( todayDate );

            return true;
        }
        catch( IOException ex )
        {
            UtilSys.getLogger().log( ILogger.Level.WARNING, ex, "Error managing update check file" );
            return true;  // If we can't manage the file, check for updates
        }
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

        private MainFrame()
        {
            setTitle( "Glue ::: Mission Control tool" );
            setAutoRequestFocus( true );
            setIconImage( JTools.getImage( "glue.png" ) );
            setDefaultCloseOperation( JFrame.DO_NOTHING_ON_CLOSE );
        }

        //------------------------------------------------------------------------//

        public ExEnsTabbedPane getExEnTabsPane()
        {
            return tabExEn;
        }

        //------------------------------------------------------------------------//
        // PRIVATE SCOPE

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

            add( toolBar, BorderLayout.NORTH  );
            add( tabExEn, BorderLayout.CENTER );
            pack();
            JTools.resizeAsPercent( this, (UtilSys.isDevEnv ? 50 : 85), 90 );
            setLocationRelativeTo( null );
            setVisible( true );

//            if( UtilSys.isDevEnv )
//            {
//                UtilSys.execute( getClass().getName(),
//                                 500,
//                                 () ->
//                                    {
//                                        try
//                                        {
//                                            Robot robot = new Robot();
//                                                  robot.keyPress( KeyEvent.VK_F4 );
//                                                  robot.keyRelease( KeyEvent.VK_F4 );
//                                        }
//                                        catch( AWTException ex )
//                                        {
//                                            JTools.error( ex );
//                                        }
//                                    } );
//            }
        }

        private void close()
        {
            try
            {
                if( Updater.isWorking() )
                {
                    JTools.alert( "Glue cannot be closed because MSP is being updated\n"+
                                  "and could end in a state that would make the whole MSP unusable.\n"+
                                  "Wait for acouple of minutes and try again." );
                    return;
                }

                SwingUtilities.invokeLater( () -> JTools.showWaitFrame( "Exiting..." ) );

                if( ! toolBar.close() )    // This allows user to close ExEn and Gum the user started.
                    return;

                tabExEn.close();

                URI uri = UtilIO.expandPath( "{*home.tmp*}" ).get( 0 );
                UtilIO.delete( new File( uri ), (f) -> UtilIO.hasExtension( f, "json" ), false );

                System.exit( 0 );    // Smooth exit
            }
            catch( Throwable th )
            {
                UtilSys.getLogger().log( ILogger.Level.SEVERE, th, "Fatal error during shutdown" );
                System.exit( 1 );    // Exit with error code
            }
        }
    }
}