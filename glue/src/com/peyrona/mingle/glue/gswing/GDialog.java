
package com.peyrona.mingle.glue.gswing;

import com.peyrona.mingle.glue.Main;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Toolkit;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.KeyEvent;
import java.awt.event.WindowEvent;
import javax.swing.BorderFactory;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JRootPane;
import javax.swing.KeyStroke;
import javax.swing.SwingUtilities;
import javax.swing.WindowConstants;

/**
 * Glue JDialog.
 *
 * @author Francisco José Morero Peyrona
 *
 * Official web site at: <a href="https://github.com/peyrona/mingle">https://github.com/peyrona/mingle</a>
 */
public class GDialog extends JDialog
{
    //------------------------------------------------------------------------//

    private ActionListener onOkBtnPressed = null;

    //------------------------------------------------------------------------//

    public GDialog( String title )
    {
        this( title, true );
    }

    public GDialog( String title, boolean modal )
    {
        this( title, modal, true );
    }

    public GDialog( String title, boolean modal, boolean bSaveBounds )
    {
        this( title, (modal ? DEFAULT_MODALITY_TYPE
                            : ModalityType.MODELESS ),
              bSaveBounds );
    }

    public GDialog( String title, ModalityType modelType )
    {
        this( title, modelType, true );
    }

    public GDialog( String title, ModalityType modelType, boolean bSaveBounds )
    {
        super( Main.frame, title, modelType );

        setDefaultCloseOperation( WindowConstants.DISPOSE_ON_CLOSE );
        setLocationRelativeTo( Main.frame );

        setLayout( new BorderLayout() );

        ActionListener escListener = (ActionEvent evt) ->
                                        {
                                            JRootPane rp = (JRootPane) evt.getSource();
                                            JDialog   dl = (JDialog) rp.getParent();
                                            SwingUtilities.invokeLater( () ->
                                                                        {
                                                                            WindowEvent closingEvent = new WindowEvent( dl, WindowEvent.WINDOW_CLOSING );
                                                                            Toolkit.getDefaultToolkit().getSystemEventQueue().postEvent( closingEvent );
                                                                        } );
                                        };

        getRootPane().setBorder( BorderFactory.createEmptyBorder( 4,7,4,7 ) );

        getRootPane().registerKeyboardAction( escListener,
                                              KeyStroke.getKeyStroke( KeyEvent.VK_ESCAPE, 0 ),
                                              JComponent.WHEN_IN_FOCUSED_WINDOW );

        if( bSaveBounds )
            WndBoundsPersist.initialize( this );
    }

    //------------------------------------------------------------------------//

    public GDialog put( Component component, Object constraints )
    {
        add( component, constraints );
        return this;
    }

    @Override
    public void pack()
    {
        super.pack();
        WndBoundsPersist.handlePack( this );
    }

    @Override
    public void setVisible( boolean b )
    {
        if( b )
            pack();

        super.setVisible( b );
    }

    public GDialog setVisible()
    {
        setVisible( true );
        return this;
    }

    public GDialog setFixedSize( boolean b )
    {
        setResizable( ! b );
        return this;
    }

    public ActionListener getActionOnOk()
    {
        return onOkBtnPressed;
    }

    public GDialog setActionOnOk( ActionListener onOK )
    {
        onOkBtnPressed = onOK;
        return this;
    }
}