package com.peyrona.mingle.glue.gswing;

import com.peyrona.mingle.glue.JTools;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Component;
import java.awt.Container;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.event.ActionListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.awt.event.MouseMotionAdapter;
import javax.swing.AbstractButton;
import javax.swing.BorderFactory;
import javax.swing.Icon;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTabbedPane;
import javax.swing.SwingUtilities;
import javax.swing.event.ChangeEvent;
import javax.swing.plaf.basic.BasicButtonUI;
import jiconfont.icons.font_awesome.FontAwesome;
import jiconfont.swing.IconFontSwing;

/**
 * An improved JTabPane which main new thing is tabs having a custom button.
 * Now includes Drag and Drop functionality for reordering tabs.
 *
 * @author Francisco José Morero Peyrona
 *
 * Official web site at: <a href="https://github.com/peyrona/mingle">https://github.com/peyrona/mingle</a>
 */
public class GTabbedPane extends JTabbedPane
{
    private boolean dragging = false;
    private int draggedTabIndex = -1;

    public GTabbedPane()
    {
        addChangeListener( (ChangeEvent ce) -> updateSelectedTab() );
        initDragAndDrop();
    }

    /**
     * Initializes the mouse listeners required for tab dragging.
     */
    private void initDragAndDrop()
    {
        addMouseListener( new MouseAdapter()
        {
            @Override
            public void mousePressed( MouseEvent e )
            {
                draggedTabIndex = indexAtLocation( e.getX(), e.getY() );
            }

            @Override
            public void mouseReleased( MouseEvent e )
            {
                if( dragging )
                    setCursor( Cursor.getPredefinedCursor( Cursor.DEFAULT_CURSOR ) );

                dragging = false;
                draggedTabIndex = -1;
            }
        } );

        addMouseMotionListener( new MouseMotionAdapter()
        {
            @Override
            public void mouseDragged( MouseEvent e )
            {
                if( draggedTabIndex == -1 )
                    return;

                if( ! dragging )
                {
                    dragging = true;
                    setCursor( Cursor.getPredefinedCursor( Cursor.MOVE_CURSOR ) );
                }

                int targetTabIndex = indexAtLocation( e.getX(), e.getY() );

                if( targetTabIndex != -1 && targetTabIndex != draggedTabIndex )
                {
                    boolean forward = targetTabIndex > draggedTabIndex;
                    moveTab( draggedTabIndex, targetTabIndex, forward );
                    draggedTabIndex = targetTabIndex;
                }
            }
        } );
    }

    /**
     * Moves a tab from one index to another.
     * Logic: removes the data from the source and inserts it at the target.
     */
    private void moveTab( int srcIndex, int destIndex, boolean forward )
    {
        // Save current tab properties
        String title = getTitleAt( srcIndex );
        Component content = getComponentAt( srcIndex );
        Component tabComponent = getTabComponentAt( srcIndex );
        Icon icon = getIconAt( srcIndex );
        String tip = getToolTipTextAt( srcIndex );
        boolean isEnabled = isEnabledAt( srcIndex );
        Color bg = getBackgroundAt( srcIndex );
        Color fg = getForegroundAt( srcIndex );

        // Remove and Insert
        removeTabAt( srcIndex );
        insertTab( title, icon, content, tip, destIndex );

        // Restore custom tab component (the panel with the button)
        setTabComponentAt( destIndex, tabComponent );

        // Restore other properties
        setEnabledAt( destIndex, isEnabled );
        setBackgroundAt( destIndex, bg );
        setForegroundAt( destIndex, fg );

        // Set focus
        setSelectedIndex( destIndex );
    }

    //------------------------------------------------------------------------//

    public void addTab( String title, Component component, ActionListener al )
    {
        addTab( title, component, al, IconFontSwing.buildIcon( FontAwesome.TIMES, 12, JTools.getIconColor() ), "Close this tab" );
    }

    public void addTab( String title, Component component, ActionListener al, Icon icon, String sTooltipText )
    {
        super.addTab( title, component );

        int newTabIndex = getTabCount() - 1;

        if( newTabIndex >= 0 )
        {
            setTabComponentAt( newTabIndex, new TabPanel( this, icon, sTooltipText, al ) );

            // Delay selection to prevent race condition with UI state changes
            SwingUtilities.invokeLater( () ->
            {
                if( newTabIndex < getTabCount() )   // Double-check tab still exists
                {
                    setSelectedIndex( newTabIndex );
                    updateSelectedTab();
                }
            } );
        }
    }

    @Override
    public void removeTabAt( int index )
    {
        // Validate index to prevent race conditions
        if( index < 0 || index >= getTabCount() )
            return;

        super.removeTabAt( index );

        // Force UI update after removal
        revalidate();
        repaint();
    }

    public int findIndex4( Component component )
    {
        int tabCount = getTabCount();

        for( int n = 0; n < tabCount; n++ )
        {
            // Bounds check to prevent race conditions
            if( n >= getTabCount() )
                break;

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

        return -1;    // Component not found in any tab
    }

    /**
     * Returns the index of the tab that contains the specified button.
     * Returns -1 if the button is not found in any tab.
     * @param button
     * @return
     */
    public int getTabIndexWhichButtonIs( JButton button )
    {
        int tabCount = getTabCount();
        for( int i = 0; i < tabCount; i++ )
        {
            // Bounds check to prevent race conditions
            if( i >= getTabCount() )
                break;

            Component tabComponent = getTabComponentAt( i );
            if( tabComponent instanceof TabPanel )
            {
                TabPanel panel = (TabPanel) tabComponent;

                for( Component comp : panel.getComponents() )
                {
                    if( comp == button )
                        return i;
                }
            }
        }

        return -1; // Button not found
    }

    //------------------------------------------------------------------------//

    private void updateSelectedTab()
    {
        int tabCount = getTabCount();
        int selectedIndex = getSelectedIndex();

        for( int n = 0; n < tabCount; n++ )
        {
            // Bounds check to prevent race conditions
            if( n >= getTabCount() )
                break;

            Component tabComponent = getTabComponentAt( n );
            if( tabComponent instanceof TabPanel )
            {
                TabPanel btn = (TabPanel) tabComponent;

                if( btn != null )    // Because the event is triggered before the TabPanel can be added
                    btn.setSelected( n == selectedIndex );
            }
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
    //------------------------------------------------------------------------//
    private final class TheButton extends JButton
    {
        private final Icon icon;

        TheButton( Icon icon, String sToolTipText, ActionListener al )
        {
            this.icon = icon;

            setPreferredSize( new Dimension( 19, 19 ) );
            setToolTipText( sToolTipText );
            setUI( new BasicButtonUI() );             // Make the button looks the same for all Laf's
            setContentAreaFilled( false );            // Make it transparent
            setFocusable( false );                    // No need to be focusable
            setBorder( BorderFactory.createEtchedBorder() );
            setBorderPainted( false );
            addMouseListener( buttonMouseListener );  // Making nice rollover effect: we use the same listener for all buttons
            setRolloverEnabled( true );
            addActionListener( al );                  // Launches the action by clicking the button
        }

        @Override
        public void updateUI()
        {
        }

        @Override
        protected void paintComponent(Graphics g)
        {
            super.paintComponent( g );
            Graphics2D g2 = (Graphics2D) g.create();

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