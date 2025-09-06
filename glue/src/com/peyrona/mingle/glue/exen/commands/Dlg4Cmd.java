
package com.peyrona.mingle.glue.exen.commands;

import com.peyrona.mingle.glue.JTools;
import com.peyrona.mingle.glue.codeditor.UneEditorTabContent.UneEditorPane;
import com.peyrona.mingle.glue.codeditor.UneEditorTabContent.UneEditorUnit;
import com.peyrona.mingle.glue.gswing.GDialog;
import com.peyrona.mingle.lang.japi.UtilSys;
import com.peyrona.mingle.lang.lexer.Language;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.FlowLayout;
import java.awt.Toolkit;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.util.List;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JPanel;
import javax.swing.JSpinner;
import javax.swing.JTextField;
import javax.swing.JToggleButton;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.border.EmptyBorder;
import javax.swing.text.JTextComponent;
import jiconfont.icons.font_awesome.FontAwesome;
import jiconfont.swing.IconFontSwing;

/**
 *
 * @author Francisco José Morero Peyrona
 *
 * Official web site at: <a href="https://mingle.peyrona.com">https://mingle.peyrona.com</a>
 */
final class Dlg4Cmd extends GDialog
{
    // UneEditorPane is where Une source code is shown
    private final UneEditorPane pnlEditor  = UneEditorUnit.newEditor( "" )
                                                          .setEditable( false )
                                                          .setRows( 12 )
                                                          .setColumns( 102 );

    //------------------------------------------------------------------------//

    public Dlg4Cmd( String title, JPanel pnlCentral, ActionListener onOK )
    {
        super( title, ModalityType.DOCUMENT_MODAL );

        pnlEditor.setVisible( false );

        setActionOnOk( onOK );

        prepareOnControlsChanged( pnlCentral );

        add( pnlCentral     , BorderLayout.CENTER );
        add( getSouthPanel(), BorderLayout.SOUTH  );

        SwingUtilities.invokeLater( () -> JTools.getByName( pnlCentral, "ControlName" ).requestFocus() );    // All commands have the same name: "ControlName"
        SwingUtilities.invokeLater( () -> updateTxtCommand( pnlCentral ) );
    }

    //------------------------------------------------------------------------//

    private void onShowHideSourceClicked( ActionEvent evt )
    {
        boolean     bShow = ((JToggleButton) evt.getSource()).isSelected();
        FontAwesome icon  = bShow ? FontAwesome.CARET_UP : FontAwesome.CARET_DOWN;

        ((JToggleButton) evt.getSource()).setIcon( IconFontSwing.buildIcon( icon, 16, JTools.getIconColor() ) );
        pnlEditor.setVisible( bShow );
        pack();

        if( bShow )
            pnlEditor.grabFocus();
    }

    private JPanel getSouthPanel()
    {
        JButton btnOK = new JButton( "OK" );
                btnOK.addActionListener( getActionOnOk() );

        JToggleButton btnHideShow = new JToggleButton( "Command" );
                      btnHideShow.setIcon( IconFontSwing.buildIcon( FontAwesome.CARET_DOWN, 16, JTools.getIconColor() ) );
                      btnHideShow.setHorizontalTextPosition( SwingConstants.LEADING );
                      btnHideShow.addActionListener( (ActionEvent evt) -> onShowHideSourceClicked( evt ) );

        JPanel pnlButtons = new JPanel( new FlowLayout( FlowLayout.TRAILING ) );
               pnlButtons.add( btnHideShow );
               pnlButtons.add( btnOK );

        JPanel pnlSouth = new JPanel( new BorderLayout() );
               pnlSouth.setBorder( new EmptyBorder( 0,12,12,12 ) );
               pnlSouth.add( pnlButtons, BorderLayout.NORTH  );
               pnlSouth.add( pnlEditor , BorderLayout.CENTER );

        getRootPane().setDefaultButton( btnOK );

        return pnlSouth;
    }

    private void updateTxtCommand( JPanel pnlCentral )
    {
        pnlEditor.setText( ((PnlCmdBase) pnlCentral).getSourceCode() );
    }

    private void prepareOnControlsChanged( JPanel pnlCentral )
    {
        if( pnlCentral.getClientProperty( "_PANEL_IS_DONE_" ) != null )
            return;

        pnlCentral.putClientProperty( "_PANEL_IS_DONE_", true );     // This flag is to avoid adding events everytime the panel is passed to the dialog

        KeyListener kl4UnaNames = new KeyListener()
                                    {
                                        @Override
                                        public void keyTyped( KeyEvent evt )
                                        {
                                            char ch = evt.getKeyChar();

                                            if( ((((JTextField) evt.getComponent()).getCaretPosition() == 0) && Character.isDigit( ch ))       // 1st char can not be a number
                                                ||
                                                (! (Language.isChar4Name( ch ) || ch == KeyEvent.VK_BACK_SPACE || ch == KeyEvent.VK_DELETE))   // Only valid chars and edition keys
                                                ||
                                                (((JTextField) evt.getComponent()).getText().length() == Language.MAX_NAME_LEN) )              // Max name len
                                            {
                                                evt.consume();                                                                                 // Ignore the event
                                                Toolkit.getDefaultToolkit().beep();
                                            }
                                        }

                                        @Override
                                        public void keyPressed(KeyEvent e)  { }

                                        @Override
                                        public void keyReleased(KeyEvent e) { }
                                    };

        List<Component> list = JTools.getOfClass( pnlCentral,
                                                     new Class[] { JTextComponent.class, JComboBox.class, JToggleButton.class, JSpinner.class } );

        for( Component c : list )
        {
            if( JTextComponent.class.isAssignableFrom( c.getClass() ) )                                 // JTextField.class, JTextArea.class, JEditorPane.class
            {
                ((JTextComponent) c).addKeyListener( new KeyAdapter()
                    {   @Override
                        public void keyReleased( KeyEvent e ) { updateTxtCommand( pnlCentral ); }
                    } );

                if( JTools.isJTextUneName( (JTextComponent) c) )    // Better to this manually
                {
                    c.addKeyListener( kl4UnaNames );
                }
            }
            else if( JToggleButton.class.isAssignableFrom( c.getClass() ) )                             // JCheckBox.class, JRadioButton.class
            {
                ((JToggleButton) c).addItemListener( (e) -> updateTxtCommand( pnlCentral ) );
            }
            else if( JComboBox.class.isAssignableFrom( c.getClass() ) )                                 // JComboBox.class
            {
                ((JComboBox) c).addItemListener( (e) -> updateTxtCommand( pnlCentral ) );
            }
            else if( JSpinner.class.isAssignableFrom( c.getClass() ) )                                  // JSpinner.class
            {
                ((JSpinner) c).addChangeListener( (e) -> updateTxtCommand( pnlCentral ) );

                ((JSpinner) c).addKeyListener( new KeyAdapter()     // To attend changes done via keyboard
                    {   @Override
                        public void keyReleased( KeyEvent e ) { updateTxtCommand( pnlCentral ); }
                    } );
            }
        }

        // This method does not supervise buttons: do not invoke pnlCentral:getSourceCode(),
        // beacuse normally buttons are not involved in changes in sourcve code (e.g. [OK]).
        // But sometimes there are buttons that are involved in this. It is panel's
        // resposability to add these buttons to the List

        for( JButton btn : ((PnlCmdBase) pnlCentral).lstBtn2Check4Changes )
        {
            btn.addActionListener((ActionEvent e) ->
            {
                UtilSys.execute( getClass().getName(),
                                 99,    // Using a Timer sucks, but is the simplest way to ensure that all previous Actions had been executed
                                 () -> SwingUtilities.invokeLater( () -> updateTxtCommand( pnlCentral ) ) );
            } );
        }
    }
}