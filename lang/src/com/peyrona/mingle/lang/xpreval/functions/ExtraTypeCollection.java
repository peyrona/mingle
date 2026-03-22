
package com.peyrona.mingle.lang.xpreval.functions;

import com.peyrona.mingle.lang.MingleException;
import com.peyrona.mingle.lang.japi.UtilReflect;
import java.beans.PropertyChangeListener;
import java.beans.PropertyChangeSupport;
import java.util.AbstractMap;
import java.util.Collection;

/**
 *
 * @author Francisco José Morero Peyrona
 *
 * Official web site at: <a href="https://github.com/peyrona/mingle">https://github.com/peyrona/mingle</a>
 * @param <T>
 */
public abstract class ExtraTypeCollection<T>
                extends ExtraType
                implements Iterable
{
    private volatile PropertyChangeSupport support;

    //------------------------------------------------------------------------//
    // PUBLIC ABSTRACT

    /**
     * Returns a deep clone of itself.
     *
     * @return A deep clone of itself.
     */
    @Override
    public abstract T clone();

    public abstract T intersect( Object o );

    public abstract T union( Object o );

    /**
     * Takes an expression and returns a new collection (list or pair) by applying
     * the expression to each item in the initial iterable.
     *
     * @param expr To be applied to every item in the list.
     * @return The new list.
     */
    public abstract T map( Object expr );

    /**
     * Takes an expression (that returns a boolean value) and returns a new collection
     * (list or pair) containing those items for which the expression returned true.
     *
     * @param expr
     * @return
     */
    public abstract T filter( Object expr );

    /**
     * Allows to compute a result using all the elements present in the iterable.
     *
     * @param expr
     * @return
     */
    public abstract Object reduce( Object expr );

    /**
     * Returns a JSON string representation of this instance's data.
     *
     * @return A JSON string representation.
     */
    public abstract String toJson();

    //------------------------------------------------------------------------//

    /**
     * Adds a property change listener to this collection.
     * <p>
     * The listener will be notified when collection elements are added, removed, or modified.
     *
     * @param pcl The property change listener to add.
     */
    public void addPropertyChangeListener( PropertyChangeListener pcl )
    {
        // Thread-safe lazy initialization using double-check locking with volatile field
        PropertyChangeSupport localSupport = support;  // Read volatile once

        if( localSupport == null )
        {
            synchronized( this )
            {
                localSupport = support;  // Read again inside synchronized block
                if( localSupport == null )
                {
                    localSupport = new PropertyChangeSupport( this );
                    support = localSupport;  // Write to volatile field
                }
            }
        }

        localSupport.addPropertyChangeListener( pcl );
    }

    public void removePropertyChangeListener( PropertyChangeListener pcl )
    {
        if( support != null )
            support.removePropertyChangeListener( pcl );
    }

    //------------------------------------------------------------------------//
    // PROTECTED SCOPE

    /**
     * Clones a value based on its type.
     *
     * @param value The value to clone.
     * @return The cloned value.
     */
    protected Object cloneValue( Object value )
    {
        if( value instanceof Boolean ||
            value instanceof Number  ||
            value instanceof String  ||
            value instanceof date    ||
            value instanceof time )
        {
            return value;     // because all these types are inmutables
        }

        return ((ExtraTypeCollection) value).clone();
    }

    // Used for congruency, but also to save RAM

    /**
     *
     * @param oldValue
     * @param newValue null when the property was deleted.
     * @return Itself.
     */
    protected T firePropertyChanged( Object oldValue, Object newValue )
    {
        if( support != null )
        {
            String propName = UtilReflect.getCallerMethodName( 2 );

            support.firePropertyChange( propName, oldValue, newValue );
        }

        return (T) this;
    }

    protected void checkNotEmpty( Object inner )
    {
        boolean isEmpty;

        if( inner instanceof Collection ) isEmpty = ((Collection)  inner).isEmpty();
        else                              isEmpty = ((AbstractMap) inner).isEmpty();

        if( isEmpty )
            throw new MingleException( getClass().getSimpleName() +" is empty" );
    }

    protected void checkOfClass( Object obj, Class<?> clazz )
    {
        if( ! clazz.isInstance( obj ) )
            throw new MingleException( "Expecting '"+ getClass().getSimpleName() +"', but '"+ obj.getClass().getSimpleName() +"' found." );
    }

    protected void checkIsBoolean( String sExpr, Object obj )
    {
        if( ! (obj instanceof Boolean) )
            throw new MingleException( sExpr +": should return a 'Boolean', but returns a '"+ (obj == null ? "null" : obj.getClass().getSimpleName()) +'\'' );
    }
}