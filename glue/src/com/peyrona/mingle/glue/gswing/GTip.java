
package com.peyrona.mingle.glue.gswing;

import com.peyrona.mingle.glue.ConfigManager;
import com.peyrona.mingle.glue.JTools;
import com.peyrona.mingle.glue.Main;
import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
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
public final class GTip
{
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

            new GFrame( false )
                  .title( "Tip" )
                  .closeOnEsc()
                  .onClose( JFrame.DISPOSE_ON_CLOSE )
                  .onClose((GFrame g) -> GTip.save( msg, chk.isSelected() ) )
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
        ConfigManager.resetTips();
    }

    //------------------------------------------------------------------------//

    private static void save( final String msg, boolean bDoNotShow )
    {
        if( bDoNotShow )                                   // User does not want to see the tip again
            ConfigManager.addHiddenTip( toUUID( msg ) );
    }

    private static boolean canShow( final String msg )
    {
        return ! ConfigManager.getHiddenTips().contains( toUUID( msg ) );    // If tips list contains the msg, it can not be shown
    }

    private static String toUUID( String msg )
    {
        byte[] bytes = msg.getBytes( StandardCharsets.UTF_8 );
        UUID   uuid  = UUID.nameUUIDFromBytes( bytes );      // UUID.nameUUIDFromBytes() uses MD5 hashing internally.

        return uuid.toString();
    }
}