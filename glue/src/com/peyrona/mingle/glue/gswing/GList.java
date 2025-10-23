
package com.peyrona.mingle.glue.gswing;

import java.awt.Component;
import java.awt.Container;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;
import javax.swing.DefaultListModel;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.ListCellRenderer;
import javax.swing.ListSelectionModel;

/**
 *
 * @param <T>
 * @author Francisco José Morero Peyrona
 *
 * Official web site at: <a href="https://github.com/peyrona/mingle">https://github.com/peyrona/mingle</a>
 */
public class GList<T>
{
    public  final JList<T> list;
    public  final DefaultListModel<T> model;
    private       boolean bSorted = true;

    //------------------------------------------------------------------------//

    public GList()
    {
        this( new JList<>() );
    }

    public GList( JList<T> list )
    {
        this.model = new DefaultListModel<>();
        this.list  = list;
        this.list.setModel( this.model );
        this.list.setSelectionMode( ListSelectionModel.SINGLE_SELECTION );
        this.list.setCellRenderer( new GCellRenderer() );
    }

    //------------------------------------------------------------------------//

    public GList addTo( Container container, Object constrains )
    {
        container.add( list, constrains );

        return this;
    }

    public boolean isEmpty()
    {
        return model.isEmpty();
    }

    public boolean isNotEmpty()
    {
        return ! model.isEmpty();
    }

    public boolean isSorted()
    {
        return bSorted;
    }

    /**
     * This method has to be invoked previous to add items.
     *
     * @param b
     * @return Itself.
     */
    public GList<T> setSorted( boolean b )
    {
        bSorted = b;
        return this;
    }

    public boolean has( Function<T,Boolean> fn )
    {
        for( T item : getAll() )
        {
            if( fn.apply( item ) )
                return true;
        }

        return false;
    }

    public boolean has( T item )
    {
        return (indexOf( item ) > -1);
    }

    public int indexOf( T item )
    {
        return model.indexOf( item );
    }

    public T get( int atIndex )
    {
        return model.get( atIndex );
    }

    public GList<T> add( T item )
    {
        return add( item, null );
    }

    public GList<T> add( Collection<T> coll )
    {
        for( T t : coll )
            add( t, null );

        return this;
    }

    public GList<T> add( T item, Object oAssociated )
    {
        if( oAssociated != null )
            list.putClientProperty( item, oAssociated );

        int index;

        if( bSorted && (model.size() > 0) )
        {
            index = Collections.binarySearch( getAll(),
                                              item,
                                              (T a, T b) -> getCaptionFn().apply(a).compareTo( getCaptionFn().apply(b) ) );

            if( index < 0 )
                index = -index - 1;
        }
        else
        {
            index = model.getSize() - 1;
            index = Math.max( index, 0 );
        }

        model.add( index, item );

        list.setSelectedIndex( index );
        list.ensureIndexIsVisible( index );
        list.revalidate();     // I do not know why I have to do all this bullshit
        list.repaint();        // but I do not time to investigate Swing'g guts.

        return this;
    }

    /**
     * Removes selected if any.
     * @return
     */
    public T remove()
    {
        if( list.getSelectedIndex() < 0 )
        {
            return null;
        }

        int index = list.getSelectedIndex();

        if( model.getSize() > 1 )           // First we set the new selected item (only if the one to be removed is not the last one)
        {
            int ndx = (index == 0) ? 1 : (index - 1);

            list.setSelectedIndex( ndx );
            list.ensureIndexIsVisible( ndx );
        }

        T item = model.remove( index );     // Now we can remove

        return item;
    }

    public T remove( T item )
    {
        int index = model.indexOf( item );

        if( model.removeElement( item ) && (! isEmpty()) )
        {
            if( index > 0 )
                index--;

            list.setSelectedIndex( index );
            list.ensureIndexIsVisible( index );
        }

        return item;
    }

    /**
     * Updates the selected (highlighted) item with passed one.
     *
     * @param item
     * @return Itself.
     */
    public GList<T> udpate( T item )
    {
        return udpate( item, null );
    }

    /**
     * Updates the selected (highlighted) item with passed one and also updates its associated.
     *
     * @param item
     * @param oAssociated
     * @return Itself.
     */
    public GList<T> udpate( T item, Object oAssociated )
    {
        if( getSelected() == null )
            throw new IndexOutOfBoundsException( "Listbox is empty or no item selected" );

        list.putClientProperty( list.getSelectedValue(), null );
        list.putClientProperty( item, oAssociated );
        model.set( list.getSelectedIndex(), item );

        return this;
    }

    /**
     * Searches for the old one and replaces it by the new one.
     *
     * @param itemOld
     * @param itemNew
     * @return
     */
    public GList<T> replace( T itemOld, T itemNew )
    {
        int index = model.indexOf( itemOld );

        if( index < 0 )
            throw new IndexOutOfBoundsException( "Item to be searched not found" );

        model.set( index, itemNew );

        return this;
    }

    public void clear()
    {
        model.clear();
    }

    public List<T> getAll()
    {
        return Collections.list( model.elements() );
    }

    public Object getAssociated( T item )
    {
        return list.getClientProperty( item );
    }

    public T getSelected()
    {
        return list.getSelectedValue();
    }

    public GList<T> setSelected( T item )
    {
        list.setSelectedValue( item, true );

        return this;
    }

    public GList<T> setSelected( Function<T,Boolean> fn )
    {
        for( int n = 0;  n < model.size(); n++ )
        {
            if( fn.apply( get( n ) ) )
                list.addSelectionInterval( n, n );
        }

        list.ensureIndexIsVisible( list.getSelectedIndex() );

        return this;
    }

    public Object getSelectedAssociated()
    {
        return list.getClientProperty( getSelected() );
    }

    public Function<T,String> getCaptionFn()
    {
        return ((GCellRenderer) list.getCellRenderer()).fnCaption;
    }

    public GList<T> setCaptionFn( Function<T,String> fn )    // Function receives a T and returns a String
    {
        ((GCellRenderer) list.getCellRenderer()).fnCaption = fn;
        return this;
    }

    public GList<T> shiftUp()
    {
        int index = list.getSelectedIndex();

        if( index > 0 )     // Can not move up item at index 0
        {
            model.add( index - 1, model.remove( index ) );
            list.setSelectedIndex( index - 1 );
            list.ensureIndexIsVisible( index - 1 );
        }

        return this;
    }

    public GList<T> shiftDown()
    {
        int index = list.getSelectedIndex();

        if( (index > -1) &&                       // The list is not empty and there is one item selected
            (index < (model.getSize() - 1)) )     // Can not move up item at last index
        {
            model.add( index + 1, model.remove( index ) );
            list.setSelectedIndex( index + 1 );
            list.ensureIndexIsVisible( index + 1 );
        }

        return this;
    }

    /**
     * Action top be executed when used double-clicks or press Enter on an item.
     *
     * @param action
     * @return Itself.
     */
    public GList<T> onPicked( final Consumer<T> action )
    {
        list.addMouseListener( new MouseAdapter()
                                {
                                    @Override
                                    public void mouseClicked( MouseEvent me )
                                    {
                                        if( me.getClickCount() == 2 )
                                            action.accept( getSelected() );
                                    }
                                } );

        list.addKeyListener( new KeyAdapter()
                               {
                                    @Override
                                    public void keyPressed( KeyEvent ke )
                                    {
                                        if( ke.getKeyCode() == KeyEvent.VK_ENTER )
                                            action.accept( getSelected() );
                                    }
                               } );
        return this;
    }

    //------------------------------------------------------------------------//
    // INNER CLASS
    // Cell Renderer
    //------------------------------------------------------------------------//
    private final class GCellRenderer extends JLabel implements ListCellRenderer<T>
    {
        private Function<T,String> fnCaption;

        GCellRenderer()
        {
            setOpaque( true );
            fnCaption = (T i) -> i.toString();      // This is the default caption function
        }

        @Override
        public Component getListCellRendererComponent( JList<? extends T> list,
                                                       T value,
                                                       int index,
                                                       boolean isSelected,
                                                       boolean cellHasFocus )
        {
            if( isSelected )
            {
                setBackground( list.getSelectionBackground() );
                setForeground( list.getSelectionForeground() );
            }
            else
            {
                setBackground( list.getBackground() );
                setForeground( list.getForeground() );
            }

            setText( fnCaption.apply( value ) );

            return this;
        }
    }
}