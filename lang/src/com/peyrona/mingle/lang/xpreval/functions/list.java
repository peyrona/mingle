
package com.peyrona.mingle.lang.xpreval.functions;

import com.eclipsesource.json.Json;
import com.eclipsesource.json.JsonArray;
import com.eclipsesource.json.JsonObject;
import com.eclipsesource.json.JsonValue;
import com.eclipsesource.json.ParseException;
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
 * A dynamic list container for storing and manipulating collections of items in the Une language.
 * <p>
 * This class provides a comprehensive API for list operations including adding, removing,
 * filtering, mapping, and accessing elements. All operations are thread-safe
 * via synchronization on the internal list.
 * <p>
 * <b>Indexing:</b> Lists in Une are 1-based (first element is at index 1),
 * unlike traditional programming languages which use 0-based indexing.
 * <p>
 * <b>Maximum Size:</b> A maximum capacity constraint can be set via the
 * {@code size()} method. When this limit is reached, adding new elements
 * automatically removes the oldest element (FIFO behavior).
 * <p>
 * <b>Thread Safety:</b> All mutating operations are synchronized, making this class
 * safe for concurrent access from multiple threads.
 *
 * @author Francisco José Morero Peyrona
 * @see com.peyrona.mingle.lang.xpreval.functions.pair
 * @see <a href="https://github.com/peyrona/mingle">https://github.com/peyrona/mingle</a>
 */
public final class list
             extends ExtraTypeCollection<list>
{
    private final List inner  = Collections.synchronizedList( new ArrayList() );
    private       int  maxLen = Integer.MAX_VALUE;

    //------------------------------------------------------------------------//

    /**
     * Constructs a new {@code list} instance.
     * <p>
     * Accepts various argument combinations to initialize the list:
     * <ul>
     *   <li><b>Empty:</b> {@code new list()} creates an empty list.</li>
     *   <li><b>Values:</b> {@code new list("A", 2022, true)} adds all arguments as items.</li>
     *   <li><b>Split:</b> {@code new list().split("A,2022|true")} splits string by comma (default).</li>
     *   <li><b>Split with separator:</b> {@code new list().split("A|2022|true", "|")} uses custom separator.</li>
     *   <li><b>JSON array:</b> {@code new list("[1,2,3]")} parses a JSON array.</li>
     *   <li><b>JSON serialization:</b> {@code new list("{\"class\":\"...\",\"data\":[1,2,3]}")} deserializes from JSON.</li>
     * </ul>
     * <p>
     * When adding string values, they are automatically converted to their appropriate
     * numeric types (e.g., "12.5" becomes {@code 12.5f}) for consistency.
     *
     * @param args Optional arguments. Valid formats are:
     *            <ul>
     *              <li>(empty) - Empty list</li>
     *              <li>(values...) - Items to add</li>
     *              <li>(String) - JSON array to parse</li>
     *              <li>(String, String) - String and separator (use with split())</li>
     *            </ul>
     */
    public list( Object... args )
    {
        if( UtilColls.isEmpty( args ) )
            return;

        // Single string argument: try JSON parsing

        if( args.length == 1 && (args[0] instanceof String) )
        {
            String s = args[0].toString().trim();

            if( s.charAt( 0 ) == '{' || s.charAt( 0 ) == '[' )
            {
                try
                {
                    JsonValue jv = Json.parse( s );

                    if( jv.isObject() )
                    {
                        // From serialize().toString() format: { "class": "...", "data": [...] }
                        deserialize( jv.asObject() );
                        return;
                    }
                    else if( jv.isArray() )
                    {
                        // Plain JSON array
                        list l = (list) UtilJson.toUneType( jv );
                        inner.addAll( l.inner );
                        return;
                    }
                }
                catch( ParseException pe )
                {
                    // Not valid JSON, will be added as a string item below
                }
            }
        }

        // Add all items to the inner list

        for( Object item : args )
            add( item );
    }

    //------------------------------------------------------------------------//
    // PUBLIC INTERFACE

    /**
     * Returns the class simple name in lower case.
     *
     * @return The class simple name in lower case.
     */
    public String type()
    {
       return getClass().getSimpleName().toLowerCase();
    }

    /**
     * Gets the number of items in the list.
     *
     * @return The number of items currently stored in the list.
     */
    public int size()
    {
        return inner.size();
    }

    /**
     * Sets the maximum size constraint for the list.
     * <p>
     * When the maximum size is reached, adding new elements automatically
     * removes the oldest element (FIFO behavior) to maintain the constraint.
     * <p>
     * For example, setting {@code size(10)} on a list with 15 elements
     * will remove the 5 oldest elements.
     *
     * @param maxSize The maximum number of items allowed in the list (must be positive).
     * @return {@code this} for method chaining.
     * @throws MingleException If the specified maximum size is less than or equal to 0.
     */
    public list size( Object maxSize )
    {
        int max = UtilType.toInteger( maxSize );

        if( max <= 0 )
            throw new MingleException( "Invalid max size" );

        synchronized( inner )
        {
            maxLen = max;

            while( inner.size() > maxLen )
                inner.remove( 0 );
        }

        return this;
    }

    /**
     * Gets the number of items in the list (alias for {@link #size()}).
     *
     * @return The number of items currently stored in the list.
     */
    public int len()
    {
        return inner.size();
    }

    /**
     * Sets the maximum size constraint for the list (alias for {@link #size(Object)}).
     *
     * @param maxSize The maximum number of items allowed in the list (must be positive).
     * @return {@code this} for method chaining.
     * @throws MingleException If the specified maximum size is less than or equal to 0.
     */
    public list len( Object maxSize )
    {
        return size( maxSize );
    }

    /**
     * Checks if the list contains no elements.
     * <p>
     * This method is equivalent to: {@code size() == 0}
     *
     * @return {@code true} if the list has no elements, {@code false} otherwise.
     * @see #size()
     */
    public boolean isEmpty()
    {
        return inner.isEmpty();
    }

    /**
     * Removes all items from the list.
     * <p>
     * This method clears the entire list and fires property change notifications
     * for each removed element.
     *
     * @return {@code this} for method chaining.
     */
    public list empty()
    {
        Object[] values;

        synchronized( inner )
        {
            values = inner.toArray();
            inner.clear();
        }

        for( Object value : values )
            firePropertyChanged( value, "" );

        return this;
    }

    /**
     * Returns the 1-based index of the first occurrence of the specified element in this list.
     * <p>
     * If the element is not found, returns {@code 0}. This method performs
     * type-aware comparison:
     * <ul>
     *   <li><b>Numbers:</b> Compared by numeric value (e.g., {@code Integer 5} equals {@code Float 5.0f})</li>
     *   <li><b>Strings:</b> Compared case-insensitively</li>
     *   <li><b>Other types:</b> Use standard {@code equals()} comparison</li>
     * </ul>
     *
     * @param item The element to search for in the list.
     * @return The 1-based index of the first occurrence, or {@code 0} if not found.
     * @see #has(java.lang.Object)
     */
    public int index( Object item )
    {
        int index = inner.indexOf( item );

        if( index > -1 )        // This should be the most of the cases
            return index + 1;

        // Handle numeric type comparison (Integer vs Float)
        if( item instanceof Number )
        {
            float itemValue = ((Number) item).floatValue();

            synchronized( inner )
            {
                for( int n = 0; n < inner.size(); n++ )
                {
                    Object element = inner.get( n );
                    if( (element instanceof Number) &&
                        Float.compare( ((Number) element).floatValue(), itemValue ) == 0 )
                    {
                        return n + 1;
                    }
                }
            }

            return 0;
        }

        // Handle case-insensitive string comparison
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
     * Checks if the list contains the specified item.
     * <p>
     * This method is equivalent to: {@code index(item) > 0}
     *
     * @param item The element to search for in the list.
     * @return {@code true} if the list contains the item, {@code false} otherwise.
     * @see #index(java.lang.Object)
     */
    public boolean has( Object item )
    {
        return index( item ) > 0;
    }

    /**
     * Returns the element at the specified 1-based index position in the list.
     * <p>
     * Negative index counts backward from the end of the list:
     * <ul>
     *   <li>{@code -1}: Last item</li>
     *   <li>{@code -2}: Second to last item</li>
     *   <li>And so on...</li>
     * </ul>
     *
     * @param index The 1-based ordinal position of the item to retrieve.
     * @return The element at the specified position.
     * @throws MingleException If the index is out of bounds.
     */
    public Object get( Object index )
    {
        return inner.get( toIndex( index ) );
    }

    /**
     * Replaces the element at the specified 1-based index position with a new value.
     * <p>
     * Negative index counts backward from the end of the list:
     * <ul>
     *   <li>{@code -1}: Last item</li>
     *   <li>{@code -2}: Second to last item</li>
     *   <li>And so on...</li>
     * </ul>
     * <p>
     * This method fires a property change notification.
     *
     * @param index The 1-based ordinal position of the element to replace.
     * @param item The new value to set (any valid Une data type).
     * @return {@code this} for method chaining.
     * @throws MingleException If the index is out of bounds.
     */
    public list set( Object index, Object item )
    {
        Object old = inner.set( toIndex( index ), item );

        firePropertyChanged( old, item );

        return this;
    }

    /**
     * Negates (inverts) the value at the specified 1-based index position.
     * <p>
     * Negative index counts backward from the end of the list:
     * <ul>
     *   <li>{@code -1}: Last item</li>
     *   <li>{@code -2}: Second to last item</li>
     *   <li>And so on...</li>
     * </ul>
     * <p>
     * Supported value types for negation:
     * <ul>
     *   <li><b>Boolean:</b> {@code true} becomes {@code false}, {@code false} becomes {@code true}</li>
     *   <li><b>Number 0:</b> Becomes {@code 1}</li>
     *   <li><b>Number 1:</b> Becomes {@code 0}</li>
     *   <li><b>Other types:</b> Throws {@code MingleException}</li>
     * </ul>
     *
     * @param index The 1-based ordinal position of the element to negate.
     * @return {@code this} for method chaining.
     * @throws MingleException If the value at index is not boolean, 0, or 1.
     */
    public list negate( Object index )
    {
        Object value = get( index );

        if( value instanceof Boolean )
            return set( index, ! ((Boolean) value) );

        if( value instanceof Number )
        {
            Integer n = UtilType.toInteger( value );

                 if( n == 0 )  return set( index, 1f );
            else if( n == 1 )  return set( index, 0f );
        }

        throw new MingleException( "Value at "+ index +" is neither boolean nor 0 or 1" );
    }

    /**
     * Returns the last element in the list.
     * <p>
     * This method is equivalent to: {@code get(-1)}
     * <p>
     * Note: This method uses 0-based indexing internally, matching Java's
     * {@code List.get()} behavior.
     *
     * @return The last element in the list.
     * @throws MingleException If the list is empty.
     * @see #get(java.lang.Object)
     */
    public Object last()
    {
        checkNotEmpty( inner );

        return inner.get( inner.size() -1 );
    }

    /**
     * Returns the last element in the list, or a default value if the list is empty.
     * <p>
     * This method is equivalent to: {@code get(-1)} but provides a
     * default value instead of throwing an exception when empty.
     *
     * @param def The default value to return if the list is empty.
     * @return The last element in the list, or {@code def} if empty.
     * @see #get(java.lang.Object)
     */
    public Object last( Object def )
    {
        if( inner.isEmpty() )
            return def;

        return inner.get( inner.size() -1 );
    }

    /**
     * Adds a new element to the end (tail) of the list.
     * <p>
     * If the list has a maximum size constraint and is at capacity, the oldest
     * element is automatically removed (FIFO behavior) before adding the new element.
     * <p>
     * String values are automatically converted to appropriate numeric types
     * (e.g., "12.5" becomes {@code 12.5f}) for consistency.
     * <p>
     * This method fires a property change notification.
     *
     * @param item The element to add (any valid Une data type).
     * @return {@code this} for method chaining.
     */
    public list add( Object item )
    {
        if( item instanceof String )
            item = UtilType.toUneBasics( item.toString() );      // v.g.: "12.5" --> 12.5f

        synchronized( inner )
        {
            // Remove oldest element if list is at max capacity
            if( inner.size() >= maxLen )
            {
                Object old = inner.get( 0 );
                inner.remove( 0 );
                firePropertyChanged( old, inner.isEmpty() ? "" : inner.get( 0 ) );
            }

            inner.add( item );
            firePropertyChanged( "", item );
        }

        return this;
    }

    /**
     * Inserts an element at the specified 1-based index position, shifting existing
     * elements to the right.
     * <p>
     * Negative index counts backward from the end of the list:
     * <ul>
     *   <li>{@code -1}: Insert before last item</li>
     *   <li>{@code -2}: Insert before second to last item</li>
     *   <li>And so on...</li>
     * </ul>
     * <p>
     * If the parameter is an instance of this class ({@code list}), the list itself
     * will be appended as a single element (not flattened).
     * <p>
     * If the list has a maximum size constraint and is at capacity, the oldest
     * element is automatically removed before adding the new element.
     *
     * @param item The element to insert (any valid Une data type).
     * @param index The 1-based ordinal position where to insert the element.
     * @return {@code this} for method chaining.
     */
    public list add( Object item, Object index )
    {
        if( item instanceof String )
            item = UtilType.toUne( item.toString() );     // v.g.: "12.5" --> 12.5f

        Object old = null;
        int    ndx = toIndex( index, true );

        if( ndx < inner.size() )
            old = inner.get( ndx );

        inner.add( ndx, item );

        firePropertyChanged( old, item );

        return this;
    }

    /**
     * Appends all elements from the specified list to the end (tail) of this list.
     * <p>
     * Elements are added in their original order from the source list.
     * If the source list is this same list instance, elements are duplicated.
     *
     * @param lst The list containing elements to add (must be an instance of {@code list}).
     * @return {@code this} for method chaining.
     * @throws MingleException If the parameter is not a {@code list} instance.
     */
    public list addAll( Object lst )
    {
        checkOfClass( lst, list.class );

        List l = ((list) lst).asList();

        synchronized( inner )
        {
            for( Object item : l )
                add( item );
        }

        return this;
    }

    /**
     * Inserts all elements from the specified list at the specified 1-based index position.
     * <p>
     * Existing elements from the insertion point onward are shifted to the right.
     * Negative index counts backward from the end of the list:
     * <ul>
     *   <li>{@code -1}: Insert before last item</li>
     *   <li>{@code -2}: Insert before second to last item</li>
     *   <li>And so on...</li>
     * </ul>
     *
     * @param lst The list containing elements to insert (must be an instance of {@code list}).
     * @param index The 1-based ordinal position where to insert the elements.
     * @return {@code this} for method chaining.
     * @throws MingleException If the parameter is not a {@code list} instance.
     */
    public list addAll( Object lst, Object index )
    {
        checkOfClass( lst, list.class );

        List l = ((list) lst).asList();
        int  ndx;

        synchronized( inner )
        {
            ndx = toIndex( index, true );

            for( int n = 0; n < l.size(); n++ )
                add( l.get( n ), ndx + n + 1 );    // +1 because add(item, index) is 1-based Une index
        }

        return this;
    }

    /**
     * Deletes an element from the list by value or index position.
     * <p>
     * This method supports multiple deletion modes:
     * <ul>
     *   <li><b>By number:</b> If the value is a number, deletes the element at
     *       that 1-based ordinal position. Negative index counts backward from the end.</li>
     *   <li><b>By string (numeric):</b> If the string represents a number,
     *       it's converted to an integer and used as a 1-based index position.</li>
     *   <li><b>By string (non-numeric):</b> Searches for a case-insensitive match
     *       and deletes the first occurrence found.</li>
     *   <li><b>By other object:</b> Searches for an exact match using
     *       {@code equals()} and deletes the first occurrence found.</li>
     * </ul>
     * <p>
     * Elements after the deleted one are shifted left to fill the gap.
     *
     * @param value The element to delete, or the 1-based ordinal position of the element to delete.
     * @return {@code this} for method chaining.
     * @throws MingleException If the element to delete is not found.
     */
    public list del( Object value )
    {
        if( (value instanceof Number) ||
           ((value instanceof String) && Language.isNumber( value.toString() )) )
        {
            int n = toIndex( value );
            Object old;

            synchronized( inner )
            {
                old = inner.remove( n );
            }

            firePropertyChanged( old, "" );

            return this;
        }

        synchronized( inner )
        {
            if( ! inner.remove( value ) )
                throw new MingleException( value +" does not exist in list" );
        }

        firePropertyChanged( value, "" );

        return this;
    }

    /**
     * Creates a deep clone of this list.
     * <p>
     * The returned list is a completely independent copy with all elements cloned:
     * <ul>
     *   <li><b>Primitive/Immutable types:</b> Boolean, Number, String, {@code date}, {@code time}
     *       are copied by reference (they are immutable).</li>
     *   <li><b>Complex types:</b> Other {@code ExtraTypeCollection} instances
     *       are recursively cloned.</li>
     *   <li><b>Capacity:</b> The new list inherits the same maximum length constraint.</li>
     *   <li><b>Listeners:</b> Property change listeners are NOT copied to the cloned list.</li>
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
             cloned.maxLen = this.maxLen;

        synchronized( inner )
        {
            for( Object item : inner )
                cloned.add( cloneValue( item ) );
        }

        return cloned;
    }

    /**
     * Rotates all elements one position to the right, moving the last element to the front.
     * <p>
     * This method is equivalent to: {@code rotate(1)}
     * <p>
     * For a list {@code [1, 2, 3, 4, 5]}, the result is {@code [5, 1, 2, 3, 4]}.
     *
     * @return {@code this} for method chaining.
     */
    public list rotate()
    {
        rotate( 1 );

        return this;
    }

    /**
     * Rotates elements in the list by the specified number of positions.
     * <p>
     * Rotation direction depends on the sign of the places parameter:
     * <ul>
     *   <li><b>No parameter:</b> Rotates all elements 1 position to the right.</li>
     *   <li><b>Positive:</b> Rotates all elements N positions to the right.</li>
     *   <li><b>Negative:</b> Rotates all elements N positions to the left.</li>
     * </ul>
     * <p>
     * Examples:
     * <ul>
     *   <li>{@code [1, 2, 3, 4, 5]} with {@code rotate()} → {@code [5, 1, 2, 3, 4]}</li>
     *   <li>{@code [1, 2, 3, 4, 5]} with {@code rotate(2)} → {@code [4, 5, 1, 2, 3]}</li>
     *   <li>{@code [1, 2, 3, 4, 5]} with {@code rotate(-1)} → {@code [2, 3, 4, 5, 1]}</li>
     * </ul>
     *
     * @param places The number of positions to rotate (optional, default is 1).
     * @return {@code this} for method chaining.
     */
    public list rotate( Object places )
    {
        int n = UtilType.toInteger( places );

        if( (n != 0) && (inner.size() > 1) )
            Collections.rotate( inner, n );

        return this;
    }

    /**
     * Removes duplicate elements from the list while preserving order.
     * <p>
     * Only the first occurrence of each element is retained. Subsequent duplicates
     * are removed. The relative order of unique elements is maintained.
     * <p>
     * This method fires property change notifications for each removed duplicate.
     *
     * @return {@code this} for method chaining.
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
     * Sorts the list into ascending order according to the natural ordering of its elements.
     * <p>
     * Elements must implement {@code Comparable} or be of standard Une data types
     * (Boolean, Number, String, date, time, list, pair, etc.).
     * <p>
     * Sorting is stable (equal elements retain their relative order).
     *
     * @return {@code this} for method chaining.
     */
    public list sort()
    {
        Collections.sort( inner );

        return this;
    }

    /**
     * Reverses the order of elements in the list in place.
     * <p>
     * The first element becomes the last, and vice versa.
     *
     * @return {@code this} for method chaining.
     */
    public list reverse()
    {
        Collections.reverse( inner );

        return this;
    }

    /**
     * Splits a string by comma and adds all parts as new items to the list.
     * <p>
     * This is a convenience method equivalent to: {@code split(string, ",")}
     *
     * @param string The string to split by comma.
     * @return {@code this} for method chaining.
     */
    public list split( Object string )
    {
        return split( string, "," );
    }

    /**
     * Splits a string into elements using the specified separator and adds all new
     * items to the end of the list.
     * <p>
     * The separator is used as a Java regular expression, unless it's a single
     * character, in which case it will be properly escaped.
     * <p>
     * Empty strings resulting from the split are filtered out and not added to the list.
     *
     * @param string The string to split.
     * @param separator The separator string or regular expression.
     * @return {@code this} for method chaining.
     */
    public list split( Object string, Object separator )
    {
        if( UtilStr.isEmpty( string ) )
            return this;

        if( UtilStr.isEmpty( separator ) )
        {
            add( string );
            return this;
        }

        String ite = string.toString();
        String sep = Language.escape( separator.toString() );

        for( String s : ite.split( sep ) )
            add( s );

        return this;
    }

    //------------------------------------------------------------------------//
    // OVERRIDEN

    /**
     * Maps (transforms) each element of the list using the specified expression.
     * <p>
     * The expression is evaluated for each element with {@code x} bound to the
     * current element value. The result of each evaluation becomes a new element
     * in the returned list.
     * <p>
     * Example:
     * <pre>
     * // Multiply each number by 2
     * list: [1, 2, 3].map( "x * 2" )  // Returns: [2, 4, 6]
     * // Get year from each date
     * list: [date("2020-01-01"), date("2021-06-15")].map( "x:year()" )  // Returns: [2020, 2021]
     * </pre>
     *
     * @param expr The expression to evaluate for each element.
     * @return A new list containing the transformed elements.
     * @throws MingleException If the expression returns no value for any element.
     */
    @Override
    public list map( Object expr )
    {
        list     l2Ret = new list();
        IXprEval naxe  = new NAXE().build( expr.toString() );

        synchronized( inner )
        {
            for( int n = 0; n < inner.size(); n++ )
            {
                Object result = naxe.eval( "x", inner.get( n ) );

                if( result == null )
                    throw new MingleException( expr +": returns no value" );

                l2Ret.add( result );
            }
        }

        return l2Ret;
    }

    /**
     * Filters the list, keeping only elements that satisfy the specified expression.
     * <p>
     * The expression is evaluated for each element with {@code x} bound to the
     * current element value. Only elements where the expression evaluates to {@code true}
     * are included in the returned list.
     * <p>
     * Example:
     * <pre>
     * // Keep only even numbers
     * list: [1, 2, 3, 4, 5].filter( "x % 2 == 0" )  // Returns: [2, 4]
     * // Keep only positive numbers
     * list: [-1, 0, 1, -2, 2].filter( "x > 0" )  // Returns: [1, 2]
     * </pre>
     *
     * @param expr The boolean expression to evaluate for each element.
     * @return A new list containing only elements that satisfy the expression.
     * @throws MingleException If the expression does not evaluate to a boolean value.
     */
    @Override
    public list filter( Object expr )
    {
        checkNotEmpty( inner );

        list     l2Ret = new list();
        String   sExpr = expr.toString();
        IXprEval naxe  = new NAXE().build( sExpr );

        synchronized( inner )
        {
            for( int n = 0; n < inner.size(); n++ )
            {
                Object result = naxe.eval( "x", inner.get( n ) );

                checkIsBoolean( sExpr, result );

                if( (boolean) result )    // if true...
                    l2Ret.add( inner.get( n ) );
            }
        }

        return l2Ret;
    }

    /**
     * Reduces the list to a single value by accumulating results across all elements.
     * <p>
     * The expression is evaluated repeatedly, with {@code x} and {@code y} bound to
     * successive pairs of elements:
     * <ul>
     *   <li>First iteration: {@code x} = element[0], {@code y} = element[1]</li>
     *   <li>Second iteration: {@code x} = previous result, {@code y} = element[2]</li>
     *   <li>And so on...</li>
     * </ul>
     * <p>
     * For a single-element list, that element is returned without evaluation.
     *
     * @param expr The expression to reduce elements with.
     * @return The accumulated result after processing all elements.
     * @throws MingleException If the list has only one element (no reduction needed).
     */
    @Override
    public Object reduce( Object expr )
    {
        checkNotEmpty( inner );

        if( inner.size() == 1 )
            return inner.get( 0 );

        IXprEval naxe = new NAXE().build( expr.toString() );
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
     * Removes from this list all elements that do not exist in the specified list.
     * <p>
     * After this operation, this list will contain only elements that were
     * present in both lists (intersection). The relative order of remaining
     * elements is preserved.
     * <p>
     * String comparison is case-insensitive.
     *
     * @param oList The list to compare against (must be an instance of {@code list}).
     * @return {@code this} for method chaining.
     * @throws MingleException If the parameter is not a {@code list} instance.
     */
    @Override
    public list intersect( Object oList )
    {
        checkOfClass( oList, list.class );

        list other = (list) oList;
        List otherData = other.asList();

        synchronized( inner )
        {
            ListIterator itera = inner.listIterator();

            while( itera.hasNext() )
            {
                Object item = itera.next();

                if( item instanceof String )
                {
                    boolean bFound = false;

                    for( Object o : otherData )
                    {
                        if( (o instanceof String) && (o.toString().equalsIgnoreCase( item.toString() ) ) )
                        {
                            bFound = true;
                            break;
                        }
                    }

                    if( ! bFound )
                    {
                        itera.remove();
                        firePropertyChanged( item, "" );
                    }
                }
                else if( ! otherData.contains( item ) )
                {
                    itera.remove();
                    firePropertyChanged( item, "" );
                }
            }
        }

        return this;
    }

    /**
     * Adds all elements from the specified list to this list.
     * <p>
     * Duplicates are allowed (the list can contain the same element multiple times).
     * The maximum size constraint (if set) is respected during addition.
     *
     * @param oList The list containing elements to add (must be an instance of {@code list}).
     * @return {@code this} for method chaining.
     * @throws MingleException If the parameter is not a {@code list} instance.
     */
    @Override
    public list union( Object oList )
    {
        return addAll( oList );
    }

    /**
     * Compares this list to another list based on size and element equality.
     * <p>
     * Comparison rules:
     * <ul>
     *   <li><b>Greater:</b> Returns {@code 1} when this list has more elements.</li>
     *   <li><b>Equal:</b> Returns {@code 0} when lists have the same number of elements
     *       and all elements match (order-independent, case-insensitive for strings).</li>
     *   <li><b>Less:</b> Returns {@code -1} when this list has fewer elements or
     *       at least one element in this list is not present in the other list.</li>
     * </ul>
     *
     * @param o The list to compare with (must be an instance of {@code list}).
     * @return {@code 1} if greater, {@code 0} if equal, {@code -1} if less.
     * @throws MingleException If the parameter is not a {@code list} instance.
     */
    @Override
    public int compareTo( Object o )
    {
        if( this == o )
            return 0;

        checkOfClass( o, list.class );

        list other     = (list) o;
        List otherData = other.asList();

        synchronized( inner )
        {
            if( inner.size() < otherData.size() ) return -1;
            if( inner.size() > otherData.size() ) return  1;

            // Both lists have same length

            for( int n = 0; n < inner.size(); n++ )
            {
                Object o1 = inner.get( n );

                if( o1 instanceof String )
                {
                    boolean has = false;

                    for( Object o2 : otherData )     // Search if same string (ignoring case) is in the received list
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
                else if( ! otherData.contains( o1 ) )
                {
                    return -1;
                }
            }
        }

        return 0;
    }

    /**
     * Returns a JSON string representation of this instance's data.
     * The result can be used for interoperativity.
     * @return A JSON string representation.
     */
    @Override
    public String toJson()
    {
        if( isEmpty() )
            return "[]";

        JsonArray ja = Json.array();

        synchronized( inner )
        {
            for( Object item : inner )
            {
                if( item instanceof ExtraTypeCollection )
                    ja.add( Json.parse( ((ExtraTypeCollection) item).toJson() ) );
                else
                    ja.add( UtilType.toJson( item ) );
            }
        }

        return ja.toString();
    }

    @Override
    public String toString()
    {
        return UtilColls.toString( inner );
    }

    /**
     * Returns a hash code value for this list.
     * <p>
     * This method is supported for the benefit of hash tables such as those provided by
     * {@link java.util.HashMap}.
     *
     * @return A hash code value for this object.
     */
    @Override
    public int hashCode()
    {
        int hash = 7;
            hash = 53 * hash + Objects.hashCode( this.inner );

        return hash;
    }

    /**
     * Checks if this list is equal to another object.
     * <p>
     * Two lists are considered equal if:
     * <ul>
     *   <li>Both are instances of {@code list}</li>
     *   <li>They have the same number of elements</li>
     *   <li>All corresponding elements are equal (order-independent, case-insensitive for strings)</li>
     * </ul>
     *
     * @param obj The object to compare with (may be {@code null}).
     * @return {@code true} if the object represents the same list, {@code false} otherwise.
     */
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
     * Peeks at the object at the top of this stack without removing it.
     * <p>
     * This method treats the list as a stack, where the top is the
     * last added element (equivalent to {@code last()} without exception on empty list).
     *
     * @return The object at the top of the stack, or the default value if empty.
     */
    public Object peek()
    {
        return last();
    }

    /**
     * Removes and returns the object at the top of this stack.
     * <p>
     * This method treats the list as a stack, where the top is the
     * last added element. The element is removed from the list.
     *
     * @return The object that was at the top of the stack.
     * @throws MingleException If the list is empty.
     */
    public Object pop()
    {
        Object item = last();

        del( -1 );

        return item;
    }

    /**
     * Pushes an item onto the top of this stack.
     * <p>
     * This method treats the list as a stack, adding the element to the end.
     * Equivalent to: {@code add(item)}.
     *
     * @param item The item to push onto the stack.
     * @return {@code this} for method chaining.
     */
    public list push( Object item )
    {
        return add( item );
    }

    //------------------------------------------------------------------------//
    // TO BE USED FROM SCRIPTS

    /**
     * Serializes this list to a JSON object.
     * <p>
     * The JSON structure is:
     * <pre>
     * {
     *   "class": "com.peyrona.mingle.lang.xpreval.functions.list",
     *   "data": [1, 2, 3, "four", true]
     * }
     * </pre>
     *
     * @return A JSON object containing the class name and a JSON array of all elements.
     * @see UtilType#toJson(java.lang.Object)
     */
    @Override
    public JsonObject serialize()
    {
        JsonArray ja = Json.array();

        synchronized( inner )
        {
            for( Object item : inner )
                ja.add( UtilType.toJson( item ) );
        }

        return Json.object()
                   .add( "class", getClass().getCanonicalName() )
                   .add( "data" , ja );
    }

    /**
     * Deserializes a JSON object to populate this list instance.
     * <p>
     * Expects a JSON object in the format produced by {@link #serialize()}.
     * The list is cleared and populated with elements from the JSON array.
     *
     * @param o The JSON object containing a "data" array field.
     * @return {@code this} for method chaining.
     * @throws MingleException If the JSON structure is invalid.
     */
    @Override
    public list deserialize( JsonObject json )
    {
        JsonValue jv = json.get( "data" );
        JsonArray ja = jv.isArray() ? jv.asArray() : null;     // At this point it is never null

        empty();

        for( JsonValue val : ja.values() )
            add( UtilJson.toUneType( val ) );

        return this;
    }

    /**
     * Returns an iterator over the elements in this list.
     * <p>
     * The returned iterator provides sequential access to all elements.
     * Note that the iterator is not thread-safe for concurrent modifications.
     *
     * @return An iterator for this list.
     */
    @Override
    public Iterator iterator()
    {
        return inner.iterator();
    }

    /**
     * Returns a standard Java {@link java.util.ArrayList} containing a shallow copy of all elements.
     * <p>
     * The returned list is independent of the internal list but elements
     * are shared (shallow copy). Modifications to the returned list do not
     * affect this {@code list} instance, and vice versa.
     *
     * @return A new {@code ArrayList} with all elements from this list.
     */
    public List<Object> asList()
    {
        return new ArrayList( inner );
    }

    /**
     * Returns a typed Java {@link java.util.ArrayList} containing only elements of the
     * specified class.
     * <p>
     * Elements that are {@code null} or not assignable to the specified class
     * are filtered out.
     *
     * @param <T> The type of elements to include.
     * @param clazz The class to filter elements by (must not be {@code null}).
     * @return A new typed {@code ArrayList} containing only matching elements.
     * @param <T> The type parameter.
     */
    public <T> List<T> asListOf( Class<T> clazz )
    {
        List<T> l = new ArrayList<>( inner.size() );

        synchronized( inner )
        {
            for( Object item : inner )
                if( item != null && clazz.isAssignableFrom( item.getClass() ) )
                    l.add( (T) item );
        }

        return l;
    }

    //------------------------------------------------------------------------//
    // PRIVATE
    /**
     * Converts a 1-based Une index to a 0-based Java index.
     * <p>
     * This method handles the conversion from Une's 1-based indexing to Java's
     * 0-based indexing. Does not allow appending (index must be within bounds).
     *
     * @param index The 1-based Une index to convert.
     * @return The 0-based Java index.
     * @throws MingleException If the index is out of bounds.
     */
    private int toIndex( Object index )
    {
        return toIndex( index, false );
    }

    /**
     * Converts a 1-based Une index to a 0-based Java index.
     * <p>
     * This method handles the conversion from Une's 1-based indexing to Java's
     * 0-based indexing.
     *
     * @param index The 1-based Une index to convert.
     * @param allowAppend If {@code true}, allows index equal to size (for append position).
     *                      If {@code false}, index must be strictly within bounds.
     * @return The 0-based Java index.
     * @throws MingleException If the index is out of bounds.
     */
    private int toIndex( Object index, boolean allowAppend )
    {
        int n = UtilType.toInteger( index );

        int size = inner.size();

        if( n < 0 )  n = size + n;    // + beacuse is a negative number
        else         n--;

        int limit = allowAppend ? size : size - 1;

        if( n < 0 || n > limit )
            throw new MingleException( index + ": index out of bounds" );

        return n;
    }
}