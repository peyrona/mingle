package com.peyrona.mingle.glue.gswing;

import java.awt.Color;
import java.awt.Graphics;
import java.awt.event.FocusEvent;
import java.awt.event.FocusListener;
import javax.swing.JTextField;

/**
 * A JTextField extension that supports placeholder text.
 * <p>
 * This class provides a text field with the ability to display placeholder text
 * when the field is empty and not focused. The placeholder text appears in gray
 * and disappears automatically when the user starts typing or the field gains focus.
 *
 * @author Francisco José Morero Peyrona
 *
 * Official web site at: <a href="https://github.com/peyrona/mingle">https://github.com/peyrona/mingle</a>
 */
public final class GTextField extends JTextField implements FocusListener
{
    private String ph;

    //------------------------------------------------------------------------//

    public GTextField()
    {
        ph = null;
    }

    public GTextField( String s )
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
}
