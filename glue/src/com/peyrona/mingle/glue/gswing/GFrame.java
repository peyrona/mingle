
package com.peyrona.mingle.glue.gswing;

import com.peyrona.mingle.glue.JTools;
import com.peyrona.mingle.glue.Main;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Container;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.util.function.Consumer;
import javax.swing.BorderFactory;
import javax.swing.JComponent;
import javax.swing.JFrame;
import javax.swing.KeyStroke;

/**
 * Glue JFrame.
 * 
 * @author Francisco José Morero Peyrona
 *
 * Official web site at: <a href="https://mingle.peyrona.com">https://mingle.peyrona.com</a>
 */
public class GFrame extends JFrame
{
    public static GFrame make()
    {
        return new GFrame();
    }

    public GFrame()
    {
        setAutoRequestFocus( true );
        setIconImage( JTools.getImage( "glue.png" ) );
        setDefaultCloseOperation( JFrame.DO_NOTHING_ON_CLOSE );
        setLocationRelativeTo( Main.frame );
        setAutoRequestFocus( true );
        getRootPane().setBorder( BorderFactory.createEmptyBorder( 4,7,4,7 ) );
        setLayout( new BorderLayout( 9,7 ) );
    }

    public boolean isClosed()
    {
        // After JFrame:dispose(), JFrame:getStatus() == JFrame.NORMAL. and it is until GC acts.
        // So, I empirically checked that after a frame is disposed following flags return false:

        return (! isActive()) && (! isDisplayable()) && (! isValid()) && (! isVisible());
    }

    public GFrame title( String tittle )
    {
        setTitle( tittle );
        return this;
    }
    public GFrame icon( String iconName )
    {
        setIconImage( JTools.getImage( iconName ) );
        return this;
    }

    public GFrame onClose( final Consumer<GFrame> action )
    {
        addWindowListener(  new WindowAdapter()
                            {   @Override
                                public void windowClosing( WindowEvent we )
                                {
                                    GFrame frm = (GFrame) we.getWindow();
                                    action.accept( frm );
                                }
                            } );
        return this;
    }

    public GFrame onClose( int onClose )
    {
        setDefaultCloseOperation( onClose );
        return this;
    }

    public GFrame locatedBy( Component parent )
    {
        setLocationRelativeTo( parent );
        return this;
    }

    public GFrame size( int width, int height )
    {
        JTools.resize( this, width, height );
        return this;
    }

    public GFrame closeOnEsc()
    {
        getRootPane().registerKeyboardAction( (ActionEvent evt) -> GFrame.this.dispatchEvent( new WindowEvent( GFrame.this, WindowEvent.WINDOW_CLOSING ) ),
                                              KeyStroke.getKeyStroke( KeyEvent.VK_ESCAPE, 0 ),
                                              JComponent.WHEN_IN_FOCUSED_WINDOW );
        return this;
    }

    public Container getContent()
    {
        return getContentPane();
    }

    public GFrame setContent( Container c )
    {
        setContentPane( c );
        return this;
    }

    public GFrame put( Component component, Object constraints )
    {
        getContent().add( component, constraints );
        return this;
    }

    public GFrame setVisible()
    {
        pack();
        setVisible( true );
        toFront();
        return this;
    }
}