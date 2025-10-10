
package com.peyrona.mingle.glue.gswing;

import com.peyrona.mingle.glue.JTools;
import com.peyrona.mingle.glue.Main;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.event.ActionListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import javax.swing.JComponent;
import javax.swing.KeyStroke;
import jiconfont.IconCode;
import jiconfont.swing.IconFontSwing;

/**
 *
 * @author francisco
 */
public class GButton extends javax.swing.JButton
{
    private static final Color ICON_COLOR = JTools.getIconColor();

    //------------------------------------------------------------------------//

    public GButton()
    {
        this( null );
    }

    public GButton( JComponent container )
    {
        super();

        if( container != null )
            container.add( this );

        setFocusPainted( false );

        addMouseListener( new MouseAdapter()
                            {
                                @Override
                                public void mouseEntered( MouseEvent me )
                                {
                                    if( isEnabled() )
                                        setCursor( Cursor.getPredefinedCursor( Cursor.HAND_CURSOR ) );
                                }

                                @Override
                                public void mouseExited( MouseEvent me )
                                {
                                    setCursor( Cursor.getDefaultCursor() );
                                }
                            } );
    }

    //------------------------------------------------------------------------//

    public GButton setIcon( IconCode iconCode, int size )
    {
        setIcon( IconFontSwing.buildIcon( iconCode, size, ICON_COLOR ) );
        return this;
    }

    public GButton setToolTip( String text )
    {
        setToolTipText( text );
        return this;
    }

    public GButton addAction( ActionListener al )
    {
        addAction( al, Integer.MIN_VALUE );
        return this;
    }

    public GButton addAction( ActionListener al, int keyCode )
    {
        addActionListener( al );

        if( keyCode != Integer.MIN_VALUE )
        {
            Main.frame
                .getRootPane()
                .registerKeyboardAction( al,
                                         KeyStroke.getKeyStroke( keyCode, 0 ),
                                         JComponent.WHEN_IN_FOCUSED_WINDOW );
        }

        return this;
    }
}