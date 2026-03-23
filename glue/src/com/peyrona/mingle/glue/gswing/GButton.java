
package com.peyrona.mingle.glue.gswing;

import com.peyrona.mingle.glue.JTools;
import com.peyrona.mingle.glue.Main;
import com.peyrona.mingle.lang.japi.UtilUnit;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Insets;
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
    private IconCode iconCode = null;
    private int      iconSize = 16;

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

        Insets insets = getMargin();
               insets.left   = UtilUnit.setBetween( 9, insets.left  , 64 );
               insets.top    = UtilUnit.setBetween( 7, insets.top   , 64 );
               insets.bottom = UtilUnit.setBetween( 7, insets.bottom, 64 );
               insets.right  = UtilUnit.setBetween( 9, insets.right , 64 );

        setMargin( insets );

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

    public GButton setIcon( IconCode code, int size )
    {
        iconCode = code;
        iconSize = size;

        setIconColor( null );

        return this;
    }

    public GButton setDefaultIconColor()
    {
        return setIconColor( null );
    }

    public GButton setIconColor( Color color )
    {
        if( color == null )
            color = JTools.getIconColor();

        setIcon( IconFontSwing.buildIcon( iconCode, iconSize, color ) );

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