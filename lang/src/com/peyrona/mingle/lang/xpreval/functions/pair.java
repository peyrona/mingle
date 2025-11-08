
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
 * A simple dictionary: key/value pairs; where key must be a basic Une data type: String, Number or Boolean.
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

    // NOTE: As Une is case insentivive, when adding and updating entries, it is need to check that they do not exist yet ignoring case.

    /**
     * A Pair set is technically named a “dictionary”. It can be considered as a special type of list: in a
     * dictionary every item of the list is a pair of values (instead of one single value).<br>
     * Dictionaries look like this:<br>
     * <pre>
     *      name = John Doe, age = 27, married = true
     * </pre>
     *
     * To create a new 'pair', a set of even parameters is needed: first one is used as key, second as this key's value,
     * third is a new key and fourth this key's value and so on...<br>
     * Although not mandatory, it is recommended that all keys will be strings. Values can be any Une valid value.
     * <br>
     * A 'pair' can be created in the following ways:
     * <ul>
     *     <li>pair( "name", "John Doe", "age", 27, "married", true ) --> Creates the previously mentioned pairs build.</li>
     *     <li>pair() --> Creates an empty pairs build</li>
     * </ul>
     *
     * @param items
     */
    public pair( Object... items )
    {
        if( UtilColls.isNotEmpty( items ) )
        {
            if( items.length % 2 != 0 )
                throw new MingleException( "Not equal number of keys and values" );

            for( int n = 0; n < items.length - 1; n += 2 )
                put( items[n], items[n+1] );
        }
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
        Set<Map.Entry> entries = getEntrySet();

        inner.clear();

        synchronized( inner )
        {
            for( Map.Entry entry : entries )
                firePropertyChanged( entry.getValue(), "" );
        }

        return this;
    }

    /**
     * Returns the 'value' associated with passed 'key' or "" if key was not found.
     *
     * @param key Must be a basic Une data type: String, Number or Boolean.
     * @return The value associated with passed 'key' or "" if key was not found.
     */
    public Object get( Object key )
    {
        return get( key, "" );
    }

    /**
     * Returns the 'value' associated with passed 'key' or def value key does not exist.
     *
     * @param key Must be a basic Une data type: String, Number or Boolean.
     * @param def The value to be returned in case the dictionary does not have the key.
     * @return The value associated with passed 'key'.
     */
    public Object get( Object key, Object def )
    {
        checkIsBasicUneType( key );

        Object value = inner.get( key );

        if( value != null )
            return value;

        if( key instanceof String )
        {
            Map.Entry entry = getEntryFor( (String) key );

            if( entry != null )
                return entry.getValue();
        }
        else if( key instanceof Integer )
        {
            value = inner.get( ((Integer) key).floatValue() );

            if( value != null )
                return value;
        }

        return def;
    }

    /**
     * Adds a new pair to the current build of pairs or replaces current value for
     * a new one if key currently existed.
     *
     * @param key Pair key. Must be a basic Une data type: String, Number or Boolean.
     * @param value Pair value. Any valid Une data.
     * @return Itself.
     */
    public pair put( Object key, Object value )
    {
        checkIsBasicUneType( key );

        Object old;

        if( key instanceof String )
        {
            Map.Entry entry = getEntryFor( (String) key );

            old = (entry != null) ? entry.setValue( value )
                                  : inner.put( key, value );
        }
        else
        {
            old = inner.put( key, value );     // If existed, the value is updated, otherwise a new entry is created
        }

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
        return inner.containsKey( key );
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
     * Deletes the 'pair' which key is passed 'key'.
     *
     * @param key The key of the pair to be deleted. Any basic Une data.
     * @return The pairs build itself.
     */
    public pair del( Object key )
    {
        checkIsBasicUneType( key );

        Object old;

        synchronized( inner )
        {
            old = inner.remove( key );

            if( (old == null) && (key instanceof String) )
            {
                Map.Entry entry = getEntryFor( (String) key );   // As getEntryFor(...) performs a no sensitive search,

                if( entry != null )
                {
                    key = entry.getKey();                        // the Entry returned has the key as it appears inside the Map (with its proper case)

                    old = inner.remove( key );
                }
            }
         // else --> the dictionary does not contains the key --> nothing to do
        }

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

                cloned.put( cloneValue( key ), cloneValue( val ) );
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
    public pair fromJSON( Object o )
    {
        checkOfClass( o, String.class );

        Object p = UtilJson.toUneType( o.toString() );

        checkOfClass( p, pair.class );

        return (pair) p;
    }

    /**
     * Returns a JSON formatted String.
     *
     * @return A JSON formatted String.
     */
    @Override
    public String toString()
    {
        return UtilColls.toString( inner, ',', '=' );
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

        return build( jo );
    }

    @Override
    public pair deserialize( Object o )
    {
        UtilJson   json = parse( o );
        JsonObject jo   = json.getObject( "data", null );     // At this point it is never null

        inner.clear();

        synchronized( inner )
        {
            for( String key : jo.names() )
                inner.put( UtilType.toUneBasics( key ), UtilType.toUne( jo.get( key ) ) );
        }

        return this;
    }

    @Override
    public Iterator iterator()
    {
        return inner.entrySet().iterator();
    }

    //------------------------------------------------------------------------//

    private void checkIsBasicUneType( Object key )
    {
        if( ! StdXprFns.isBasicType( key ) )
            throw new IllegalArgumentException( (key == null ? "null" : key.getClass().getSimpleName()) +" is not a basic Une data type" );
    }

    private Set<Map.Entry> getEntrySet()
    {
        return inner.entrySet();
    }

    private Map.Entry getEntryFor( String key )
    {
        synchronized( inner )
        {
            for( Map.Entry entry : getEntrySet() )
            {
                if( (entry.getKey() instanceof String) && entry.getKey().toString().equalsIgnoreCase( key ) )
                    return entry;
            }
        }

        return null;
    }
}
