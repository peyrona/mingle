package com.peyrona.mingle.glue.gswing;

import java.awt.Color;
import java.awt.Graphics;
import java.awt.event.FocusEvent;
import java.awt.event.FocusListener;
import javax.swing.JTextField;

/**
 *
 * @author francisco
 */
public final class GTextField extends JTextField implements FocusListener
{
    private String ph;

    //------------------------------------------------------------------------//
    public GTextField()
    {
        ph = null;
    }

    public GTextField(String s)
    {
        super( s );

        addFocusListener( GTextField.this );
    }

    //------------------------------------------------------------------------//

    public GTextField setPlaceholder(String s)
    {
        ph = s;

        return this;
    }

    @Override
    public void paint( Graphics g )
    {
        super.paint( g );

        if( getText().isEmpty() && ! hasFocus() )
        {
            g.setColor( Color.GRAY );
            g.drawString( ph, getInsets().left, g.getFontMetrics().getMaxAscent() + getInsets().top );
        }
    }

    @Override
    public void focusGained(FocusEvent fe)
    {
        repaint();
    }

    @Override
    public void focusLost(FocusEvent fe)
    {
        repaint();
    }

//    @Override
//    public void paintComponent(Graphics g)
//    {
//        super.paintComponent( g );
//
//        if( getText().isEmpty() && (ph != null) )
//        {
//            Graphics2D g2 = (Graphics2D) g;
//
//            g2.setRenderingHint( RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON );
//            g2.setColor( super.getDisabledTextColor() );
//            g2.drawString( ph, getInsets().left, g.getFontMetrics().getMaxAscent() + getInsets().top );
//        }
//    }
}
