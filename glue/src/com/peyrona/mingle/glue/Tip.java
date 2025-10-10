
package com.peyrona.mingle.glue;

import com.peyrona.mingle.glue.gswing.GFrame;
import com.peyrona.mingle.lang.interfaces.ILogger;
import com.peyrona.mingle.lang.japi.UtilIO;
import com.peyrona.mingle.lang.japi.UtilStr;
import com.peyrona.mingle.lang.japi.UtilSys;
import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import javax.swing.JCheckBox;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextArea;

/**
 *
 * @author Francisco José Morero Peyrona
 *
 * Official web site at: <a href="https://github.com/peyrona/mingle">https://github.com/peyrona/mingle</a>
 */
public final class Tip
{
    private static final File file = new File( UtilSys.getEtcDir(), "glue_tips.txt" );

    public static void show( final String msg )
    {
        if( canShow( msg ) )
        {
            JTextArea txt = new JTextArea( msg );
                      txt.setOpaque( false );
                      txt.setEditable( false );

            JCheckBox chk = new JCheckBox( "Do not show this tip again" );   // Not selected

            JPanel    pnl = new JPanel( new FlowLayout( FlowLayout.CENTER, 0, 12 ) );
                      pnl.add( chk );

            GFrame.make()
                  .title( "Tip" )
                  .closeOnEsc()
                  .onClose( JFrame.DISPOSE_ON_CLOSE )
                  .onClose( (GFrame g) -> Tip.save( msg, chk.isSelected() ) )
                  .put( new JLabel( JTools.getIcon( "wizard.png", 64, 94 ) ), BorderLayout.WEST )
                  .put( txt, BorderLayout.CENTER )
                  .put( pnl, BorderLayout.SOUTH )
                  .setVisible()
                  .locatedBy( Main.frame )
                  .setAlwaysOnTop( true );
        }
    }

    public static void reset()
    {
        try
        {
            UtilIO.delete( file );
        }
        catch( IOException ioe )
        {
            JTools.error( ioe );
        }
    }

    //------------------------------------------------------------------------//

    private static void save( final String msg, boolean bDoNotShow )
    {
        if( bDoNotShow )   // User does not want to see the tip again
        {
            try
            {
                UtilIO.newFileWriter()
                      .setFile( file )
                      .append( clean( msg ) + UtilStr.sEoL );
            }
            catch( IOException ioe )
            {
                UtilSys.getLogger().log( ILogger.Level.SEVERE, ioe );
            }
        }
    }

    private static boolean canShow( final String msg )
    {
        if( file.exists() )
        {
            try
            {
                 List<String> lst = Arrays.asList( UtilIO.getAsText( file )
                                          .split( "\\R" ) );

                return ! lst.contains( clean( msg ) );    // If file contains the msg, it can not be shown
            }
            catch( IOException ioe )
            {
                UtilSys.getLogger().log( ILogger.Level.SEVERE, ioe );
            }
        }

        return true;    // The file does not exists or it does not contains the message
    }

    private static String clean( String str )
    {
        final char[] acIn  = str.toCharArray();
        final char[] acOut = new char[ acIn.length ];

        for( int n = 0; n < acIn.length; n++ )
            acOut[n] = (acIn[n] <= 32) ? '_' : acIn[n];

        return String.valueOf( acOut );
    }
}