
package com.peyrona.mingle.lang.xpreval.functions;

import com.eclipsesource.json.Json;
import com.eclipsesource.json.JsonObject;
import com.peyrona.mingle.lang.MingleException;
import com.peyrona.mingle.lang.interfaces.IXprEval;
import com.peyrona.mingle.lang.japi.UtilColls;
import com.peyrona.mingle.lang.japi.UtilJson;
import com.peyrona.mingle.lang.japi.UtilType;
import com.peyrona.mingle.lang.xpreval.NAXE;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * A simple dictionary: key/value pairs; where key must be a String or Number
 * (internally always stored as a lowercase String).
 * <p>
 * This collection is case-insensitive for String keys and achieves O(1) lookup
 * performance by normalizing all keys to lowercase. Numeric keys are canonicalized
 * such that '1' and '1.0' refer to the same entry.
 * <p>
 * Values can be any valid Une type.
 *
 * @author Francisco José Morero Peyrona
 *
 * Official web site at: <a href="https://github.com/peyrona/mingle">https://github.com/peyrona/mingle</a>
 */
public final class pair
             extends ExtraTypeCollection<pair>
{
    private final Map inner = new ConcurrentHashMap();

    //------------------------------------------------------------------------//

    // NOTE: As Une is case insentivive, entries are stored in lowercase to ensure O(1) performance.

    /**
     * A Pair set is technically named a "dictionary". It can be considered as a special type of list: in a
     * dictionary every item of the list is a pair of values (instead of one single value).<br>
     * Dictionaries look like this:<br>
     * <pre>
     *      name = John Doe, age = 27, married = true
     * </pre>
     *
     * To create a new 'pair', a set of even parameters is needed: first one is used as key, second as this key's value,
     * third is a new key and fourth this key's value and so on...<br>
     * Although not mandatory, it is recommended that all keys will be strings. Values can be any Une valid value.<br>
     * Another way is to pass a well-formed JSON object as a string.<br>
     * <br>
     * A 'pair' can be created in the following ways:
     * <ul>
     *     <li>pair() --> Creates an empty pairs build</li>
     *     <li>pair( "name", "John Doe", "age", 27, "married", true ) --> Creates the previously mentioned pairs build.</li>
     *     <li>pair( "{ \"age\": 27, \"name\": \"Frank\" }" ) --> Plain JSON object</li>
     *     <li>pair( string ) --> Using only one string that represents a valid JSON object: can contain any other JSON value.
     *         This string can be returned from <code>::toString()</code> or from <code>::serialize():toString()</code>) </li>
     * </ul>
     *
     * @param items
     */
    public pair( Object... items )
    {
        if( items == null || items.length == 0 )
            return;

        // Single string argument: try JSON parsing

        if( items.length == 1 && (items[0] instanceof String) )
        {
            String s = ((String) items[0]).trim();

            if( s.charAt( 0 ) == '{' )
            {
                try
                {
                    JsonObject jo = Json.parse( s ).asObject();

                    // Try serialize() format first (has "class" and "data" keys)
                    if( jo.get( "class" ) != null && jo.get( "data" ) != null )
                    {
                        deserialize( jo );
                        return;
                    }

                    // Plain JSON object - convert to pair
                    pair p = (pair) UtilJson.toUneType( jo );
                    putAll( p );
                    return;
                }
                catch( Exception exc )
                {
                    throw new MingleException( exc );
                }
            }
        }

        // Key-value pairs

        if( items.length % 2 != 0 )
            throw new MingleException( "Not equal number of keys and values" );

        for( int n = 0; n < items.length - 1; n += 2 )
            put( items[n], items[n+1] );
    }

    //------------------------------------------------------------------------//

    /**
     * Returns the size (number of pairs) of this dictionary.
     *
     * @return The size (number of items) of this dictionary.
     */
    public int size()
    {
        return inner.size();
    }

    /**
     * Returns the size (number of pairs) of this dictionary.
     *
     * @return The size (number of items) of this dictionary.
     */
    public int len()
    {
        return inner.size();
    }

    /**
     * Returns true if the dictionary has no elements.<br>
     * <br>
     * This method is equivalent to: size() == 0
     *
     * @return true if the dictionary has no elements.
     * @see #size()
     */
    public boolean isEmpty()
    {
        return inner.isEmpty();
    }

    /**
     * Deletes all item pairs.
     *
     * @return The pairs build itself.
     */
    public pair empty()
    {
        Object[] values;

        synchronized( inner )
        {
            values = inner.values().toArray();
            inner.clear();
        }

        for( Object val : values )
            firePropertyChanged( val, "" );

        return this;
    }

    /**
     * Returns the 'value' associated with passed 'key' or "" if key was not found.
     *
     * @param key Must be a String or Number (Numbers are converted to Strings internally).
     * @return The value associated with passed 'key' or "" if key was not found.
     */
    public Object get( Object key )
    {
        return get( key, "" );
    }

    /**
     * Returns the 'value' associated with passed 'key' or def value if the 'key' does not exist.
     *
     * @param key Must be a String or Number (Numbers are converted to Strings internally).
     * @param def The value to be returned in case the dictionary does not have the key.
     * @return The value associated with passed 'key' or the default value.
     */
    public Object get( Object key, Object def )
    {
        return inner.getOrDefault( keyAsString( key ), def );
    }

    /**
     * Adds a new pair to the current build of pairs or replaces current value for
     * a new one if key currently existed.
     *
     * @param key Pair key. Must be a String or Number (internally stored as lowercase String).
     * @param value Pair value. Any valid Une data.
     * @return Itself.
     */
    public pair put( Object key, Object value )
    {
        String sKey = keyAsString( key );
        Object old  = inner.put( sKey, value );

        firePropertyChanged( old, value );

        return this;
    }

    /**
     * Add all items in passed dictionary to this dictionary.
     *
     * @param oPair Pair instance to be added.
     * @return Itself.
     */
    public pair putAll( Object oPair )
    {
        checkOfClass( oPair, pair.class );

        synchronized( inner )
        {
            for( Map.Entry entry : ((pair) oPair).getEntrySet() )
                put( entry.getKey(), entry.getValue() );
        }

        return this;
    }

    /**
     * Returns a list with all keys of this dictionary (all are strings).
     *
     * @return A list with all keys of this dictionary (all are strings).
     */
    public list keys()
    {
        return new list( inner.keySet().toArray() );
    }

    /**
     * Returns a list with all values of this dictionary.
     *
     * @return A list with all values of this dictionary.
     */
    public list values()
    {
        return new list( inner.values().toArray() );
    }

    /**
     * Returns true if this pair contains a mapping for the specified key.
     * More formally, returns true if and only if this pair contains a mapping
     * for a key k such that Objects.equals(key, k). (There can be at most one
     * such mapping.)
     *
     * @param key key whose presence in this map is to be tested
     * @return true if this pair contains a mapping for the specified key
     */
    public boolean hasKey( Object key )
    {
        try
        {
            return inner.containsKey( keyAsString( key ) );
        }
        catch( Exception exc )
        {
            return false;
        }
    }

    /**
     * Returns true if this pair maps one or more keys to the specified value.
     * More formally, returns true if and only if this pair contains at least
     * one mapping to a value v such that Objects.equals(value, v).
     *
     * @param value value whose presence in this map is to be tested
     * @return true if this pair maps one or more keys to the specified value
     */
    public boolean hasValue( Object value )
    {
        return inner.containsValue( value );
    }

    /**
     * Negates (invert) the boolean value associated with the specified key.
     * <ul>
     *   <li>If value is Boolean: true becomes false, false becomes true.</li>
     *   <li>If value is Number 0: becomes 1.</li>
     *   <li>If value is Number 1: becomes 0.</li>
     *   <li>Otherwise: throws MingleException.</li>
     * </ul>
     *
     * @param key The key whose value is to be inverted. Must be a String or Number.
     * @return The pair itself.
     * @throws MingleException If value for key is not boolean, 0, or 1.
     */
    public pair negate( Object key )
    {
        Object value = get( key, null );

        if( value == null )
            throw new MingleException( "Key '"+ key +"' does not exist in pair" );

        if( value instanceof Boolean )
            return put( key, ! ((Boolean) value) );

        if( value instanceof Number )
        {
            Integer n = UtilType.toInteger( value );

                 if( n == 0 )  return put( key, 1f );
            else if( n == 1 )  return put( key, 0f );
        }

        throw new MingleException( "Value for key '"+ key +"' is neither boolean nor 0 or 1" );
    }

    /**
     * Deletes the 'pair' which key is passed 'key'.
     *
     * @param key The key of the pair to be deleted. Must be a String or Number.
     * @return The pairs build itself.
     */
    public pair del( Object key )
    {
        Object old = inner.remove( keyAsString( key ) );

        if( old != null )
            firePropertyChanged( old, "" );

        return this;
    }

    /**
     * Deep clones this pair instance.
     *
     * @return A deep clone of this pair.
     */
    @Override
    public pair clone()
    {
        pair cloned = new pair();

        synchronized( inner )
        {
            for( Object key : inner.keySet() )
            {
                Object val = inner.get( key );

                cloned.put( key, cloneValue( val ) );
            }
        }

        return cloned;
    }

 // public pair sort() --> does not make sense becasue Map does not keep its order

    public pair split( Object items )
    {
        return split( items, ",", "=" );
    }

    public pair split( Object items, Object entrySep, Object pairSep )
    {
        List pairs = new list().split( items, entrySep ).asList();

        synchronized( inner )
        {
            for( Object pair : pairs )
            {
                Object key, val;
                list   l = new list().split( pair, pairSep );    // new list() is needed because list.split(...) put passed items to the existing ones

                if( l.size() == 2 )    // true almost allways
                {
                    key = l.get(1);
                    val = l.get(2);
                }
                else
                {
                    key = l.isEmpty() ? "" : l.get(1);
                    val = l.size() < 2 ? "" : l.get(2);
                }

                put( key, val );
            }
        }

        return this;
    }

    //------------------------------------------------------------------------//
    // OVERRIDEN

    @Override
    public pair map( Object expr )
    {
        pair     p2Ret = new pair();
        IXprEval naxe  = new NAXE().build( expr.toString() );

        synchronized( inner )
        {
            for( Map.Entry entry : getEntrySet() )
            {
                naxe.set( "x", entry.getKey()   );
                naxe.set( "y", entry.getValue() );

                Object result = naxe.eval();

                if( result == null )
                    throw new MingleException( expr +": returns no value" );

                p2Ret.put( entry.getKey(), result );
            }
        }

        return p2Ret;
    }

    @Override
    public pair filter( Object expr )
    {
        checkNotEmpty( inner );

        pair     p2Ret = new pair();
        String   sExpr = expr.toString();
        IXprEval naxe  = new NAXE().build( sExpr );

        synchronized( inner )
        {
            for( Map.Entry entry : getEntrySet() )
            {
                naxe.set( "x", entry.getKey()   );
                naxe.set( "y", entry.getValue() );

                Object result = naxe.eval();

                checkIsBoolean( sExpr, result );

                if( (boolean) result )
                    p2Ret.put( entry.getKey(), entry.getValue() );
            }
        }

        return p2Ret;
    }

    @Override
    public Object reduce( Object expr )
    {
        checkNotEmpty( inner );

        return values().reduce( expr );
    }

    @Override
    public pair intersect( Object oPair )
    {
        checkOfClass( oPair, pair.class );

        pair other     = (pair) oPair;
        list otherKeys = other.keys();

        synchronized( inner )
        {
            for( Object key : keys().asList() )
            {
                if( ! otherKeys.has( key ) )
                    del( key );
            }
        }

        return this;
    }

    @Override
    public pair union( Object oPair )
    {
        return putAll( oPair );
    }

    /**
     * Compares this pair to another pair.
     * <p>
     * Returns:
     * <ul>
     *    <li> 1 : when this pair is bigger (has more items) than passed pair.</li>
     *    <li> 0 : when this pair is the same as passed pair.</li>
     *    <li>-1 : when this pair is lower (has less items) than passed pair.</li>
     *    <li>-1 : when this pair does not contain a key that exist in passed pair.</li>
     *    <li> 1 : when this pair value is not the same as the value for the passed pair key.</li>
     *    <li>-1 : when passed pair is null.</li>
     * </ul>
     * @param o Date to compare (argument must be of type pair).
     * @return 1, 0 or -1
     */
    @Override
    public int compareTo( Object o )
    {
        if( this == o )
            return 0;

        checkOfClass( o, pair.class );

        Map other = ((pair) o).inner;

        if( size() < other.size() ) return -1;
        if( size() > other.size() ) return  1;

        // Both maps have same length

        synchronized( inner )
        {
            for( Map.Entry entry1 : getEntrySet() )
            {
                Object key = entry1.getKey();     // Has to exist in both maps

                if( ! other.containsKey( key ) )
                    return -1;

                Object val1 = entry1.getValue();
                Object val2 = other.get( key );

                if( val1 instanceof String && val2 instanceof String )
                {
                    if( ! ((String) val1).equalsIgnoreCase( (String) val2 ) )
                        return 1;
                }
                else if( ! val1.equals( val2 ) )
                {
                    return 1;
                }
            }
        }

        return 0;
    }

    @Override
    public String toString()
    {
        return UtilColls.toString( inner );
    }

    @Override
    public int hashCode()
    {
        int hash = 7;
            hash = 79 * hash + Objects.hashCode( this.inner );
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

        final pair other = (pair) obj;

        return (compareTo( other ) == 0);
    }

    //------------------------------------------------------------------------//
    // TO BE USED FROM SCRIPTS

    @Override
    public Object serialize()
    {
        JsonObject jo = Json.object();

        synchronized( inner )
        {
            for( Map.Entry entry : getEntrySet() )
                jo.add( entry.getKey().toString(), UtilType.toJson( entry.getValue() ) );
        }

        return Json.object()
                   .add( "class", getClass().getCanonicalName() )
                   .add( "data" , jo );
    }

    @Override
    public pair deserialize( Object o )
    {
        UtilJson   json = parse( o );
        JsonObject jo   = json.getObject( "data", null );

        empty();

        for( String key : jo.names() )
            put( key, UtilJson.toUneType( jo.get( key ) ) );

        return this;
    }

    @Override
    public Iterator iterator()
    {
        return inner.entrySet().iterator();
    }

    //------------------------------------------------------------------------//

    private String keyAsString( Object key )
    {
        if( key instanceof String )
        {
            return ((String) key).toLowerCase();
        }
        else if( key instanceof Number )
        {
            float f = ((Number) key).floatValue();

            return (f == (long) f) ? String.valueOf( (long) f )
                                   : String.valueOf( f );
        }

        throw new MingleException( (key == null ? "null" : key.getClass().getSimpleName()) +" is not a valid key type (must be String or Number)" );
    }

    private Set<Map.Entry> getEntrySet()
    {
        return inner.entrySet();
    }
}