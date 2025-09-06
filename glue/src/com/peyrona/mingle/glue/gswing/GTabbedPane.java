
package com.peyrona.mingle.glue.gswing;

import com.peyrona.mingle.glue.JTools;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Component;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.event.ActionListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.util.List;
import javax.swing.AbstractButton;
import javax.swing.BorderFactory;
import javax.swing.Icon;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTabbedPane;
import javax.swing.event.ChangeEvent;
import javax.swing.plaf.basic.BasicButtonUI;
import jiconfont.icons.font_awesome.FontAwesome;
import jiconfont.swing.IconFontSwing;

/**
 * An improved JTabPane which main new thing is tabs having a custom button.
 *
 * @author Francisco José Morero Peyrona
 *
 * Official web site at: <a href="https://mingle.peyrona.com">https://mingle.peyrona.com</a>
 */
public class GTabbedPane extends JTabbedPane
{
    public GTabbedPane()
    {
        addChangeListener( (ChangeEvent ce) -> updateSelectedTab() );
    }

    //------------------------------------------------------------------------//

    public void addTab( String title, Component component, ActionListener al )
    {
        addTab( title, component, al, IconFontSwing.buildIcon( FontAwesome.TIMES, 12, JTools.getIconColor() ), "close this tab" );
    }

    public void addTab( String title, Component component, ActionListener al, Icon icon, String sTooltipText )
    {
        super.addTab( title, component );

        setTabComponentAt( getTabCount() - 1, new TabPanel( this, icon, sTooltipText, al ) );
        setSelectedIndex(  getTabCount() - 1 );
        updateSelectedTab();
    }

    /**
     * Returns the tab index which contains passed button or -1 if not found.
     * @param btn Button contained in the tab that is being searched.
     * @return the tab index which contains passed button or -1 if not found.
     */
    public int getTabIndexWhichButtonIs( JButton btn )
    {
        for( int n = 0; n < getTabCount(); n++ )
        {
            List<Component> lstBtn = JTools.getOfClass( (TabPanel) getTabComponentAt( n ), JButton.class );

            if( (! lstBtn.isEmpty()) && btn == lstBtn.get( 0 ) )
                return n;
        }
throw new RuntimeException();
        ////return -1;
    }

    public int findIndex4( Component component )
    {
        for( int n = 0; n < getTabCount(); n++ )
        {
            Component tabComponent = getComponentAt( n );

            if( tabComponent == component )
                return n;

            if( tabComponent instanceof Container )
            {
                Component child = JTools.getChild( (Container) tabComponent, component.getClass() );

                if( child == component )
                    return n;    // Return the index of the tab containing the component
            }
        }
throw new RuntimeException();
        ////return -1;    // Component not found in any tab
    }

    //------------------------------------------------------------------------//

    private void updateSelectedTab()
    {
        for( int n = 0; n < getTabCount(); n++ )
        {
            TabPanel btn = (TabPanel) getTabComponentAt( n );

            if( btn != null )    // Because the event is triggered before the TabPanel can be added
                btn.setSelected( n == getSelectedIndex() );
        }
    }

    //------------------------------------------------------------------------//
    // INNER CLASS
    //------------------------------------------------------------------------//

    /**
         * Component to be used as tabComponent.
         * Contains a JLabel to show the text and a JButton to close the tab it belongs to.
         */
    private final class TabPanel extends JPanel
    {
        TabPanel( JTabbedPane tabbedPane, Icon icon, String sTooltipText, ActionListener al )
        {
            //unset default FlowLayout' gaps
            super( new FlowLayout( FlowLayout.LEFT, 0, 0 ) );

            setOpaque( false );

            //make JLabel read titles from JTabbedPane
            JLabel label = new JLabel()
            {
                @Override
                public String getText()
                {
                    int n = tabbedPane.indexOfTabComponent(TabPanel.this );

                    return ((n == -1) ? null : tabbedPane.getTitleAt( n ));
                }
            };

            add( label );
            //add more space between the label and the button
            label.setBorder( BorderFactory.createEmptyBorder( 0, 0, 0, 5 ) );
            //tab button
            JButton button = new TheButton( icon, sTooltipText, al );
            add( button );
            //add more space to the top of the component
            setBorder( BorderFactory.createEmptyBorder( 2, 0, 0, 0 ) );
        }

        //------------------------------------------------------------------------//

        void setSelected( boolean b )
        {
            JLabel label = ((JLabel) getComponent( 0 ));
            Font   font  = label.getFont();

            if( b )  label.setFont( font.deriveFont( font.getStyle() |  Font.BOLD ) );
            else     label.setFont( font.deriveFont( font.getStyle() & ~Font.BOLD ) );
        }
    }

    //------------------------------------------------------------------------//
    // INNER CLASS
    // Thanks to: unknown author
    //------------------------------------------------------------------------//
    private final class TheButton extends JButton
    {
        private final Icon icon;

        TheButton( Icon icon, String sToolTipText, ActionListener al )
        {
            this.icon = icon;

            int size = 19;

            setPreferredSize( new Dimension( size, size ) );
            setToolTipText( sToolTipText );
            //Make the button looks the same for all Laf's
            setUI( new BasicButtonUI() );
            //Make it transparent
            setContentAreaFilled( false );
            //No need to be focusable
            setFocusable( false );
            setBorder( BorderFactory.createEtchedBorder() );
            setBorderPainted( false );
            //Making nice rollover effect: we use the same listener for all buttons
            addMouseListener( buttonMouseListener );
            setRolloverEnabled( true );
            //Launches the action by clicking the button
            addActionListener( al );
        }

        //we don't want to update UI for this button
        @Override
        public void updateUI()
        {
        }

        //paint the cross
        @Override
        protected void paintComponent(Graphics g)
        {
            super.paintComponent( g );
            Graphics2D g2 = (Graphics2D) g.create();

            //shift the image for pressed buttons
            if( getModel().isPressed() )
                g2.translate( 1, 1 );

            g2.setStroke( new BasicStroke( 2 ) );
            g2.setColor( Color.BLACK );

            if( getModel().isRollover() )
                g2.setColor( Color.RED );

            icon.paintIcon( this, g, 5, 2 );
            g2.dispose();
        }
    }

    private final MouseListener buttonMouseListener = new MouseAdapter()
    {
        @Override
        public void mouseEntered(MouseEvent me)
        {
            Component component = me.getComponent();

            if( component instanceof AbstractButton )
            {
                AbstractButton button = (AbstractButton) component;

                button.setBorderPainted( true );
            }
        }

        @Override
        public void mouseExited(MouseEvent me)
        {
            Component component = me.getComponent();

            if( component instanceof AbstractButton )
            {
                AbstractButton button = (AbstractButton) component;

                button.setBorderPainted( false );
            }
        }
    };
}