
package com.peyrona.mingle.glue;

import com.peyrona.mingle.glue.gswing.GTip;
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
import java.util.concurrent.atomic.AtomicBoolean;
import javax.swing.JFrame;
import javax.swing.SwingUtilities;
import jiconfont.icons.font_awesome.FontAwesome;
import jiconfont.swing.IconFontSwing;

/**
 * A "Mission Control Tool" for the MSP: development and monitoring.
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
        //------------------------------------------------------------------------//
        // By using a hook we maximize the possibilities the finalization code will be
        // invoked: even if INTERRUPT signal (Ctrl-C) is used, the JVM will invoke this hook.
        // Even when System.exit(...) is used, the JVM will invoke this hook.

        Runtime.getRuntime()
               .addShutdownHook( new Thread( () -> frame.close( true ) ) );

        //------------------------------------------------------------------------//
        // SETUP

        UtilCLI cli = new UtilCLI( as );

        UtilSys.setConfig( new Config().load( cli.getValue( "config", null ) )     // If defined, use this config file (instead of the default one)
                                       .setCliArgs( as ) );

        UtilSys.setLogger( "glue", UtilSys.getConfig() );

        JTools.setLaF();

        //------------------------------------------------------------------------//
        // START GUI

        try
        {
            IconFontSwing.register( FontAwesome.getIconFont() );

            SwingUtilities.invokeLater(() ->
                {
                    frame.showIt();

                    System.out.println( "Glue started." );

                    GTip.show( "Welcome to Glue - The Mingle Swiss-knife tool\n\n"+
                               "Please read the Mingle Standard Platform manual to get familiar with Glue.\n\n"+
                               "Suggested actions:\n"+
                               "     a) Connect with a running ExEn ('plug' icon or F2)\n"+
                               "     b) Start a local ExEn ('play' icon or F5)\n"+
                               "     c) Open the Script-Editor ('pencil' icon or F4).\n"+
                               "\n"+
                               "By default, configuration is read from: {*home*}config.json\n"+
                               "It can be changed at command line by passing: -config=<URI>" );


                    if( shouldCheckForUpdates() )
                    {
                        new javax.swing.SwingWorker<Void, Void>()
                        {
                            @Override
                            protected Void doInBackground()
                            {
                                Updater.updateIfNeeded( UtilSys.isDevEnv,
                                                        () -> JTools.confirm( "There is a new MSP version available.\nDo you want to update now?" ) );
                                return null;
                            }
                        }.execute();
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
        frame.close( false );
    }

    //------------------------------------------------------------------------//
    // PRIVATE
    //------------------------------------------------------------------------//

    private static boolean shouldCheckForUpdates()
    {
        if( UtilSys.isDevEnv )
            return false;

        String lastCheckDate = SettingsManager.getLastUpdateCheck();
        String todayDate     = LocalDate.now().format( java.time.format.DateTimeFormatter.ISO_LOCAL_DATE );

        if( todayDate.equals( lastCheckDate ) )
            return false;

        SettingsManager.setLastUpdateCheckToToday();
        return true;
    }

    //------------------------------------------------------------------------//
    // INNER CLASS
    // Main Frame
    //------------------------------------------------------------------------//
    public static final class MainFrame extends JFrame
    {
        private AllExEnsTabPane tabExEn;
        private ToolbarPanel    toolBar;
        private AtomicBoolean   isExiting = new AtomicBoolean( false );

        //------------------------------------------------------------------------//

        private MainFrame()
        {
            setTitle( "Glue ::: Mission Control tool" );
            setAutoRequestFocus( true );
            setDefaultCloseOperation( JFrame.DO_NOTHING_ON_CLOSE );

            JTools.setIconImages( this, "glue.png" );
        }

        //------------------------------------------------------------------------//

        public AllExEnsTabPane getExEnsTabPane()
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
                    close( false );
                }
            } );

            tabExEn = new AllExEnsTabPane();
            toolBar = new ToolbarPanel();

            ((BorderLayout) getLayout()).setVgap( 0 );

            add( toolBar, BorderLayout.NORTH  );
            add( tabExEn, BorderLayout.CENTER );
            pack();
            JTools.resizeAsPercent( this, (UtilSys.isDevEnv ? 50 : 85), 90 );
            setLocationRelativeTo( null );
            setVisible( true );
        }

        private void close( boolean bForce )
        {
            if( isExiting.getAndSet( true ) )
                return;

            try
            {
                if( Updater.isWorking() )
                {
                    if( bForce )
                    {
                        Updater.abort();
                    }
                    else
                    {
                        JTools.alert( "Glue cannot be closed because MSP is being updated\n"+
                                      "and could end in a state that would make the whole MSP unusable.\n"+
                                      "Wait for a couple of minutes and try again." );
                        isExiting.set( false );
                        return;
                    }
                }

                if( bForce )
                {
                    toolBar.close( true );    // Will forcebly close the ExEn and Gum that the user started.
                }
                else
                {
                    if( ! toolBar.isUserFeedbackNeeded() )
                        SwingUtilities.invokeLater( () -> JTools.showWaitFrame( "Exiting..." ) );

                    if( ! toolBar.close( false ) )    // false -> allows user to close ExEn and Gum the user started.
                    {
                        SwingUtilities.invokeLater( () -> JTools.hideWaitFrame() );
                        isExiting.set( false );
                        return;
                    }

                    if( toolBar.isUserFeedbackNeeded() )
                        SwingUtilities.invokeLater( () -> JTools.showWaitFrame( "Exiting..." ) );

                    tabExEn.close();    // This is invoked only when not force. When force, the close will not be clean (graceful), but neither will harm.
                }

                // Delete JSON temporal files created by transpilation and execution processes
                URI uri = UtilIO.expandPath( "{*home.tmp*}" ).get( 0 );
                UtilIO.delete( new File( uri ), (f) -> UtilIO.hasExtension( f, "json" ), false );

                System.out.println( "Glue finished." );
                System.exit( 0 );    // Smooth exit
            }
            catch( Exception th )
            {
                UtilSys.getLogger().log( ILogger.Level.SEVERE, th, "Fatal error during shutdown" );
                System.exit( 1 );    // Exit with error code
            }
        }
    }
}