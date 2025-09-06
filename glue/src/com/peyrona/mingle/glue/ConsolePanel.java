
package com.peyrona.mingle.glue;

import com.peyrona.mingle.lang.japi.UtilANSI;
import com.peyrona.mingle.lang.japi.UtilStr;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Font;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextPane;
import javax.swing.text.BadLocationException;
import javax.swing.text.DefaultCaret;
import javax.swing.text.Style;
import javax.swing.text.StyleConstants;
import javax.swing.text.StyledDocument;

/**
 * Shows transpiler output and ExEn output.
 *
 * @author Francisco José Morero Peyrona
 *
 * Official web site at: <a href="https://mingle.peyrona.com">https://mingle.peyrona.com</a>
 */
public final class ConsolePanel extends JPanel
{
    private static final Color CYAN   = new Color(  38, 189, 219 );   // #26BDDB
    private static final Color PURPLE = new Color( 198, 117, 231 );   // #26BDDB
    private static final Color RED    = new Color( 243,  76,  76 );   // #F34C4C
    private static final Color YELLOW = new Color( 220, 146,  56 );   // #26BDDB
    private static final Color WHITE  = new Color( 220, 220, 220 );

    //------------------------------------------------------------------------//

    private final JTextPane      txt;
    private final StyledDocument doc;
    private final Style          style;
    private final StringBuilder  buffer = new StringBuilder( 2048 );

    //------------------------------------------------------------------------//

    public ConsolePanel()
    {
        txt = new JTextPane();
        txt.setEditable( false );
        txt.setFont( new Font( "Monospaced", Font.PLAIN, 12 ) );
        txt.setOpaque( false );

        // Set the JTextPane to have a DefaultCaret to automatically scroll to the end
        DefaultCaret caret = (DefaultCaret) txt.getCaret();
                     caret.setUpdatePolicy( DefaultCaret.ALWAYS_UPDATE );

        doc   = txt.getStyledDocument();
        style = txt.addStyle( "_An_Style_", null );

        setLayout( new BorderLayout() );
        add( new JScrollPane( txt ), BorderLayout.CENTER );
    }

    //------------------------------------------------------------------------//

    public ConsolePanel clear()
    {
        txt.setText( null );

        return this;
    }

    public ConsolePanel appendln()
    {
        return append( UtilStr.sEoL );
    }

    public ConsolePanel appendln( String str )
    {
        return appendln( str, UtilANSI.nDEFAULT );
    }

    public ConsolePanel appendln( String str, short nClrANSI )
    {
        return append( str + UtilStr.sEoL, nClrANSI );
    }

    public ConsolePanel append( String str )
    {
        return append( str, UtilANSI.nDEFAULT );
    }

    public ConsolePanel append( String str, short nClrANSI )
    {
        try
        {
            doc.insertString( doc.getLength(), str, setStyle( nClrANSI ) );
        }
        catch( BadLocationException ex )
        {
            // Nothing to do
        }

        return this;
    }

    public ConsolePanel append( char ch )
    {
        try
        {
            if( UtilANSI.isBegin( ch ) || (buffer.length() > 0) )
            {
                buffer.append( ch );
            }
            else
            {
                doc.insertString( doc.getLength(), String.valueOf( ch ), null );
            }

            if( UtilANSI.isEnd( buffer ) )
            {
                flush();
                buffer.setLength( 0 );
            }
            else if( ch == '\0' )     // '\0' is sent after the stream is closed
            {
                flush();
                doc.insertString( doc.getLength(), ">>>>>>>> Execution ended <<<<<<<<", null );
            }
        }
        catch( BadLocationException ex )
        {
            // Nothing to do
        }

        return this;
    }

    public ConsolePanel flush()
    {
        String str = buffer.toString();

        try
        {
            doc.insertString( doc.getLength(), UtilANSI.delEsc( str ), setStyle( str ) );
        }
        catch( BadLocationException ex )
        {
            // Nothing to do
        }

        return this;
    }

    //------------------------------------------------------------------------//

    private Style setStyle( String str )
    {
        return setStyle( UtilANSI.toColor( str ) );
    }

    private Style setStyle( short nClr )
    {
        switch( nClr )
        {
            case UtilANSI.nCYAN  : StyleConstants.setForeground( style, CYAN   ); break;
            case UtilANSI.nPURPLE: StyleConstants.setForeground( style, PURPLE ); break;
            case UtilANSI.nRED   : StyleConstants.setForeground( style, RED    ); break;
            case UtilANSI.nYELLOW: StyleConstants.setForeground( style, YELLOW ); break;
            default:               StyleConstants.setForeground( style, WHITE  );
        }

        return style;
    }
}