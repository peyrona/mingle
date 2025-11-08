
package com.peyrona.mingle.lang.xpreval.functions;

import com.eclipsesource.json.Json;
import com.eclipsesource.json.JsonArray;
import com.eclipsesource.json.JsonValue;
import com.peyrona.mingle.lang.MingleException;
import com.peyrona.mingle.lang.interfaces.IXprEval;
import com.peyrona.mingle.lang.japi.UtilColls;
import com.peyrona.mingle.lang.japi.UtilJson;
import com.peyrona.mingle.lang.japi.UtilStr;
import com.peyrona.mingle.lang.japi.UtilType;
import com.peyrona.mingle.lang.lexer.Language;
import com.peyrona.mingle.lang.xpreval.NAXE;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.ListIterator;
import java.util.Objects;
import java.util.Set;

/**
 * In Une, lists are 1 based instead of 0 based.
 *
 * @author Francisco José Morero Peyrona
 *
 * Official web site at: <a href="https://github.com/peyrona/mingle">https://github.com/peyrona/mingle</a>
 */
public final class list
             extends ExtraTypeCollection<list>
{
    private final List inner  = Collections.synchronizedList( new ArrayList() );
    private       int  maxLen = Integer.MAX_VALUE;

    //------------------------------------------------------------------------//

    /**
     * Class constructor.<br>
     * <br>
     * Note: string items that can be converted to numbers or booleans, will be converted.
     *
     * @param items
     */
    public list( Object... items )
    {
        if( UtilColls.isNotEmpty( items ) )    // can be null
            for( Object item : items )
                add( item );
    }

    //------------------------------------------------------------------------//

    /**
     * Returns the size (number of items) of the list.
     *
     * @return The size (number of items) of the list.
     */
    public int size()
    {
        return inner.size();
    }

    /**
     * Set maximum list size.
     *
     * @param maxSize
     * @return Itself.
     */
    public list size( int maxSize )
    {
        if( maxSize <= 0 )
            throw new MingleException( "Invalid max size" );

        synchronized( inner )
        {
            maxLen = maxSize;

            while( inner.size() > maxLen )
                inner.remove( 0 );
        }

        return this;
    }

    /**
     * Returns the size (number of items) of the list.
     *
     * @return The size (number of items) of the list.
     */
    public int len()
    {
        return inner.size();
    }

    /**
     * Set maximum list size.
     *
     * @param maxSize
     * @return Itself.
     */
    public list len( int maxSize )
    {
        return size( maxSize );
    }

    /**
     * Returns true if the list has no elements.<br>
     * <br>
     * This method is equivalent to: size() == 0
     *
     * @return true if the list has no elements.
     * @see #size()
     */
    public boolean isEmpty()
    {
        return inner.isEmpty();
    }

    /**
     * Deletes all items.
     *
     * @return The list itself.
     */
    public list empty()
    {
        Object[] values = inner.toArray();

        inner.clear();

        for( Object value : values )
            firePropertyChanged( value, "" );

        return this;
    }

    /**
     * Returns the index of the first occurrence of the specified element in this list,
     * or 0 if this list does not contain the element.
     *
     * @param item What to check.
     * @return The index of the first occurrence of the specified element in this list,
     *         or 0 if this list does not contain the element.
     * @see #has(java.lang.Object)
     */
    public int index( Object item )
    {
        int index = inner.indexOf( item );

        if( index > -1 )        // This should be the most of tha cases
            return index + 1;

        if( item instanceof Integer )
        {
            return inner.indexOf( ((Integer) item).floatValue() ) + 1;
        }

        if( item instanceof String )
        {
            String sItem = (String) item;

            synchronized( inner )
            {
                for( int n = 0; n < inner.size(); n++ )
                    if( (inner.get( n ) instanceof String) && sItem.equalsIgnoreCase( (String) inner.get( n ) ) )
                        return n + 1;
            }
        }

        return 0;
    }

    /**
     * Returns true if the list contains passed item.
     * <pre>has( item )</pre>
     * is equivalent to
     * <pre>index( item ) > 0</pre>
     *
     * @param item What to check.
     * @return true if the list contains passed item.
     * @see #index(java.lang.Object)
     */
    public boolean has( Object item )
    {
        return index( item ) > 0;
    }

    /**
     * Returns the item at the ordinal position of passed index (list are 1 based: first item is 1).<br>
     * Negative index counts back  the end of the list, so -1 is the last item of the list, -2
     * is the last before the last item and so on.<br>
     *
     * @param index The ordinal position of the item to be retrieved.
     * @return The item at the ordinal position of passed index (list are 1 based: first item is 1).
     */
    public Object get( Object index )
    {
        return inner.get( toIndex( index ) );
    }

    /**
     * Replaces the item at the 'index' ordinal position (1 based) by the passed 'item'.<br>
     * Negative index counts back from the end of the list, so -1 is the last item of
     * the list, -2 is the last before the last item and so on.
     *
     * @param item The new value (any valid Une data type).
     * @param index The ordinal position of the item to be replaced.
     * @return The list itself.
     */
    public list set( Object index, Object item )
    {
        Object old = inner.set( toIndex( index ), item );

        firePropertyChanged( old, item );

        return this;
    }

    /**
     * Returns the last item in the list.
     * <pre>last()</pre>
     * is equivalent to
     * <pre>get( -1 )</pre>
     *
     * @return The last item in the list.
     * @see #get(java.lang.Object)
     */
    public Object last()
    {
        checkNotEmpty( inner );

        return inner.get( inner.size() -1 );
    }

    /**
     * Returns the last item in the list.
     * <pre>last()</pre>
     * is equivalent to
     * <pre>get( -1 )</pre>
     *
     * @param def Value to return if list is empty
     * @return The last item in the list.
     * @see #get(java.lang.Object)
     */
    public Object last( Object def )
    {
        if( inner.isEmpty() )
            return def;

        return inner.get( inner.size() -1 );
    }

    /**
     * Adds passed 'item' at the end (tail) of the list.<br>
     *
     * @param item Item to be added (any valid Une data type).
     * @return The list itself.
     */
    public list add( Object item )
    {
        if( item instanceof String )
            item = UtilType.toUneBasics( item.toString() );      // v.g.: "12.5" --> 12.5f

        synchronized( inner )
        {
            if( inner.size() > maxLen )
            {
                Object old = inner.get( 0 );
                inner.remove( 0 );
                firePropertyChanged( old, inner.get( 0 ) );   // 1 because lists are 1 based
            }

            inner.add( item );
            firePropertyChanged( "", item );      // size() because lists are 1 based
        }

        return this;
    }

    /**
     * Inserts passed 'item' at the ordinal position of 'index' shifting right all items after 'index'.<br>
     * Negative index counts back from the end of the list, so -1 is the last item of the list, -2 is the
     * last before the last item and so on.
     * <br>
     * If received parameter is instance of this class (list), the list itself will be appended as one single item.
     *
     * @param item Item (any valid Une data type) to be added.
     * @param index The ordinal position of the item to be added.
     * @return The list itself.
     */
    public list add( Object item, Object index )
    {
        if( item instanceof String )
            item = UtilType.toUne( item.toString() );     // v.g.: "12.5" --> 12.5f

        int    ndx;
        Object old = "";

        synchronized( inner )
        {
            if( inner.size() > maxLen )
            {
                Object _old = inner.get( 0 );
                inner.remove( 0 );
                firePropertyChanged( _old, inner.get( 0 ) );   // 1 because lists are 1 based
            }

            ndx = toIndex( index );

            if( ndx < inner.size() )
                old = inner.get( ndx );

            inner.add( ndx, item );
        }

        firePropertyChanged( old, item );

        return this;
    }

    /**
     * Appends all items in passed 'lst' list at the end (tail) of the list..<br>
     *
     * @param lst List to be added.
     * @return The list itself.
     */
    public list addAll( Object lst )
    {
        checkOfClass( lst, list.class );

        List l = ((list) lst).inner;

        synchronized( l )
        {
            for( Object item : l )
                add( item );
        }

        return this;
    }

    /**
     * Inserts all items in passed 'lst' list at the ordinal position of 'index' shifting right all items after 'index'.<br>
     * Negative index counts back from the end of the list, so -1 is the last item of the list, -2 is the last before
     * the last item and so on.
     *
     * @param lst List to be added.
     * @param index The ordinal position of the item to be added.
     * @return The list itself.
     */
    public list addAll( Object lst, Object index )
    {
        checkOfClass( lst, list.class );

        List l   = ((list) lst).inner;
        int  ndx = UtilType.toInteger( index );     // Do not do ::toIndex() because this method invokes ::add(... ) which makes it

        synchronized( l )
        {
            for( int n = 0; n < l.size(); n++ )
                add( l.get( n ), ndx + n );
        }

        return this;
    }

    /**
     * Deletes 'item' from the list.<br>
     * <ul>
     *     <li>If received 'value' is a number, the item at the ordinal position of passed 'value' is deleted, shifting left all items after 'value'.<br>
     *         Negative index counts back from the end of the list, so -1 is the last item of the list, -2 is the last before the last item and so on.</li>
     *     <li>If received 'value' is a string and the list contains it, this item will be deleted. But if the string represents a number, it is
     *         converted into an integer and the item at this index will be deleted.<br>
     *         In both cases, items after the deleted one will shifted left.</li>
     *     <li>Otherwise, 'value' will be searched into the list and if it exist, it will deleted and items after the deleted one will shifted left.</li>
     * </ul>
     *
     * @param value The item to delete or the ordinal position of the item to be deleted.
     * @return The list itself.
     */
    public list del( Object value )
    {
        if( (value instanceof Number) ||
           ((value instanceof String) && Language.isNumber( value.toString() )) )
        {
            int ndx = UtilType.toInteger( value );
            int n   = toIndex( ndx );

            inner.remove( n );

            firePropertyChanged( value, "" );

            return this;
        }

        if( ! inner.remove( value ) )
            throw new MingleException( value +" does not exist in list" );

        firePropertyChanged( value, "" );

        return this;
    }

    /**
     * Creates a deep clone of this list.
     * <p>
     * The returned list is a completely independent copy with all elements cloned:
     * <ul>
     *   <li>Primitive types (Boolean, Number, String, date, time) are copied by reference (they are immutable)</li>
     *   <li>Complex types (other ExtraTypeCollection instances) are recursively cloned</li>
     *   <li>The new list has the same maximum length constraint as the original</li>
     *   <li>Property change listeners are NOT copied to the cloned list</li>
     * </ul>
     * Modifications to the cloned list or its elements do not affect the original list.
     *
     * @return A deep clone of this list with all elements independently cloned.
     */
    @Override
    public list clone()
    {
        // super.clone() should not be invoked in this case. Here's why:
        //    1. The parent class ExtraType doesn't implement Cloneable - It's an abstract class that doesn't have a clone() method.
        //    2. The intermediate class ExtraTypeCollection declares clone() as abstract - It doesn't provide an implementation.
        //    3. The list class performs a deep clone manually - The current implementation creates a new instance and copies each element using cloneValue(), which is appropriate for this use case.

        list cloned = new list();

        synchronized( inner )
        {
            for( Object item : inner )
                cloned.add( cloneValue( item ) );
        }

        return cloned;
    }

    /**
     * Rotates all elements one position to the right, moving the last element to the front.
     *
     * @return Itself.
     */
    public list rotate()
    {
        rotate( 1 );

        return this;
    }

    /**
     * Rotates the elements in the list.
     * <ul>
     *    <li>If no parameter is passed, shifts all elements 1 position to the right.</li>
     *    <li>If 'places' is positive, rotates all elements N positions to the right.</li>
     *    <li>If 'places' is negative, rotates all elements N positions to the left.</li>
     * </ul>
     *
     * @param places Number of positions to rotate (optional, default is 1).
     * @return Itself.
     */
    public list rotate( Object places )
    {
        int n = UtilType.toInteger( places );

        if( (n != 0) && (inner.size() > 1) )
            Collections.rotate( inner, n );

        return this;
    }

    /**
     * Remove duplicates.
     *
     * @return Itself.
     */
    public list uniquefy()
    {
        if( inner.isEmpty() )
            return this;

        // inner.clear().addAll( new HashSet( inner ) ); --> Can not do this because Set has no order and list must keep the adding order

        Set set = new HashSet();

        synchronized( inner )
        {
            for( ListIterator itera = inner.listIterator(); itera.hasNext(); )
            {
                Object item = itera.next();

                if( set.contains( item ) )
                {
                    itera.remove();
                    firePropertyChanged( item, "" );
                }
                else
                {
                    set.add( item );
                }
            }
        }

        return this;
    }

    /**
     * Sorts the list into ascending order, according to the natural ordering of its elements.
     *
     * @return The list itself.
     */
    public list sort()
    {
        Collections.sort( inner );

        return this;
    }

    /**
     * Reverses the order of the elements in the specified list.
     *
     * @return The list itself.
     */
    public list reverse()
    {
        Collections.reverse( inner );

        return this;
    }

    public list split( Object items )
    {
        return split( items, "," );
    }

    /**
     * Splits a string into its elements using received separator and adds all new items at the end of the list.<br>
     * <br>
     * NOTE: separator is used as a Java regular expression, unless it is one single character: in this case it
     * will be properly escaped.
     *
     * @param items
     * @param separator
     * @return Itself
     */
    public list split( Object items, Object separator )
    {
        if( UtilStr.isEmpty( items ) )
            return this;

        if( UtilStr.isEmpty( separator ) )
        {
            add( items );
            return this;
        }

        String ite = items.toString();
        String sep = Language.escape( separator.toString() );

        for( String s : ite.split( sep ) )
            add( s );

        return this;
    }

    //------------------------------------------------------------------------//
    // OVERRIDEN

    @Override
    public list map( Object expr )
    {
        list     l2Ret = new list();
        IXprEval yajer = new NAXE().build( expr.toString().replace( '\'', '"' ) );    // e.g.: "x == 'paco'"

        synchronized( inner )
        {
            for( int n = 0; n < inner.size(); n++ )
            {
                Object result = yajer.eval( "x", inner.get( n ) );

                if( result == null )
                    throw new MingleException( expr +": returns no value" );

                l2Ret.add( result );
            }
        }

        return l2Ret;
    }

    @Override
    public list filter( Object expr )
    {
        checkNotEmpty( inner );

        list     l2Ret = new list();
        String   sExpr = expr.toString().replace( '\'', '"' );     // e.g.: "x == 'paco'"
        IXprEval yajer = new NAXE().build( sExpr );

        synchronized( inner )
        {
            for( int n = 0; n < inner.size(); n++ )
            {
                Object result = yajer.eval( "x", inner.get( n ) );

                checkIsBoolean( sExpr, result );

                if( (boolean) result )
                    l2Ret.add( inner.get( n ) );
            }
        }

        return l2Ret;
    }

    @Override
    public Object reduce( Object expr )
    {
        checkNotEmpty( inner );

        if( inner.size() == 1 )
            return inner.get( 0 );

        IXprEval naxe = new NAXE().build( expr.toString().replace( '\'', '"' ) );    // e.g.: "x == 'paco'"
                 naxe.set( "x", inner.get(0) );
                 naxe.set( "y", inner.get(1) );

        Object result = naxe.eval();

        synchronized( inner )
        {
            for( int n = 2; n < inner.size(); n++ )
            {
                naxe.set( "x", result );
                naxe.set( "y", inner.get(n) );
                result = naxe.eval();
            }
        }

        return result;
    }

    /**
     * Deletes from this list all items that does not exist in passed 'list'.
     *
     * @param oList List to compare with.
     * @return The list itself.
     */
    @Override
    public list intersect( Object oList )
    {
        checkOfClass( oList, list.class );

        list lst = (list) oList;

        synchronized( inner )
        {
            ListIterator itera = inner.listIterator();

            while( itera.hasNext() )
            {
                Object item = itera.next();

                if( item instanceof String )
                {
                    boolean bFound = false;

                    synchronized( lst.inner )
                    {
                        for( Object o : lst.inner )
                        {
                            if( (o instanceof String) && (o.toString().equalsIgnoreCase( item.toString() ) ) )
                            {
                                bFound = true;
                                break;
                            }
                        }
                    }

                    if( ! bFound )
                    {
                        itera.remove();
                        firePropertyChanged( item, "" );
                    }
                }
                else if( ! lst.inner.contains( item ) )
                {
                    itera.remove();
                    firePropertyChanged( item, "" );
                }
            }
        }

        return this;
    }

    /**
     * Adds all items in passed 'list' to this list.
     *
     * @param oList List to be added.
     * @return The list itself.
     */
    @Override
    public list union( Object oList )
    {
        addAll( (list) oList );

        return this;
    }

    /**
     * Compares this list to another list.
     * <p>
     * Returns:
     * <ul>
     *    <li> 1 : when this list is bigger (has more items) than passed list.</li>
     *    <li> 0 : when this list is the same as passed list despiting the order of the items.</li>
     *    <li>-1 : when this list is lower (has less items) than passed list or at least one item in this list is not in passed list.</li>
     * </ul>
     * @param o Date to compare (argument must be of type list).
     * @return 1, 0 or -1
     */
    @Override
    public int compareTo( Object o )
    {
        if( this == o )
            return 0;

        checkOfClass( o, list.class );

        List other = ((list) o).inner;

        if( inner.size() < other.size() ) return -1;
        if( inner.size() > other.size() ) return  1;

        // Both lists have same length

        synchronized( inner )
        {
            for( int n = 0; n < inner.size(); n++ )
            {
                Object o1 = inner.get( n );

                if( o1 instanceof String )
                {
                    boolean has = false;

                    for( Object o2 : other )     // Search if same string (ignoring case) is in the received list
                    {
                        if( o2 instanceof String && ((String) o2).equalsIgnoreCase( (String) o1 ) )
                        {
                            has = true;
                            break;
                        }
                    }

                    if( ! has )
                        return -1;
                }
                else if( ! other.contains( o1 ) )
                {
                    return -1;
                }
            }
        }

        return 0;
    }

    @Override
    public list fromJSON( Object o )
    {
        checkOfClass( o, String.class );

        Object l = UtilJson.toUneType( o.toString() );

        checkOfClass( l, list.class );

        return (list) l;
    }

    @Override
    public String toString()    // This method is invoked (among other places) from UtilType:uneToJson(...)
    {
        return inner.toString();
    }

    @Override
    public int hashCode()
    {
        int hash = 7;
            hash = 53 * hash + Objects.hashCode( this.inner );
        return hash;
    }

    @Override
    public boolean equals( Object obj )
    {
        if( this == obj )
            return true;

        if( obj == null )
            return false;

        if( getClass() != obj.getClass() )
            return false;

        final list other = (list) obj;

        return (compareTo( other ) == 0);
    }

    //------------------------------------------------------------------------//
    // TO USE A 'LIST' AS IF IT WERE A 'STACK'

    /**
     * Looks at the object at the top of this stack (last added item) without removing it from the stack.
     *
     * @return The object at the top of this stack.
     */
    public Object peek()
    {
        return last();
    }

    /**
     * Removes the object at the top of this stack and returns that object as the value of this function.
     *
     * @return The value of the removed object.
     */
    public Object pop()
    {
        Object item = last();

        del( 1 );

        return item;
    }

    /**
     * Pushes an item onto the top (as first element) of this stack.
     *
     * @param item Item to be pushed.
     * @return Itself.
     */
    public list push( Object item )
    {
        add( 1, item );

        return this;
    }

    //------------------------------------------------------------------------//
    // TO BE USED FROM SCRIPTS

    @Override
    public Object serialize()
    {
        JsonArray ja = Json.array();

        synchronized( inner )
        {
            for( Object item : inner )
                ja.add( UtilType.toJson( item ) );
        }

        return build( ja );
    }

    @Override
    public list deserialize( Object o )
    {
        UtilJson  json = parse( o );
        JsonArray ja   = json.getArray( "data", null );     // At this point it is never null

        inner.clear();

        synchronized( inner )
        {
            for( JsonValue jv : ja.values() )
                inner.add( UtilType.toUne( jv ) );
        }

        return this;
    }

    @Override
    public Iterator iterator()
    {
        return inner.iterator();
    }

    public List<Object> asList()
    {
        return new ArrayList( inner );
    }

    public <T> List<T> asListOf( Class<T> clazz )
    {
        List<T> l = new ArrayList<>( inner.size() );

        synchronized( inner )
        {
            for( Object item : inner )
                if( clazz.isAssignableFrom( item.getClass() ) )
                    l.add( (T) item );
        }

        return l;
    }

    //------------------------------------------------------------------------//
    // PRIVATE

    private int toIndex( Object index )
    {
        int n = UtilType.toInteger( index );

        if( n < 0 )  n = inner.size() + n;    // + beacuse is a negative number
        else         n--;

        if( n < 0 || n >= inner.size() )
            throw new MingleException( index +": index out of bounds" );

        return n;
    }
}
