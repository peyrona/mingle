
package com.peyrona.mingle.lang.xpreval.functions;

import com.eclipsesource.json.Json;
import com.eclipsesource.json.JsonObject;
import com.eclipsesource.json.JsonValue;
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
 * A case-insensitive dictionary implementation for the Une language that stores key-value pairs.
 * <p>
 * This collection maps String or Number keys to any valid Une value. Keys are case-insensitive
 * and normalized to lowercase for consistent lookup, achieving O(1) retrieval performance.
 * Numeric keys are canonicalized such that '1' and '1.0' refer to the same entry.
 * <p>
 * <b>Thread Safety:</b> All mutating operations are thread-safe using a ConcurrentHashMap.
 * Property change listeners can be registered to receive notifications of modifications.
 * <p>
 * <b>Key Normalization:</b>
 * <ul>
 *   <li>String keys are converted to lowercase</li>
 *   <li>Number keys are converted to String (1, 1.0, 1.00 all become "1")</li>
 * </ul>
 *
 * @author Francisco José Morero Peyrona
 * @see com.peyrona.mingle.lang.xpreval.functions.list
 * @see <a href="https://github.com/peyrona/mingle">https://github.com/peyrona/mingle</a>
 */
public final class pair
             extends ExtraTypeCollection<pair>
{
    private final Map inner = new ConcurrentHashMap();

    //------------------------------------------------------------------------//

    // NOTE: As Une is case insentivive, entries are stored in lowercase to ensure O(1) performance.

    /**
     * Constructs a new pair instance with optional initial data.
     * <p>
     * Accepts multiple argument formats:
     * <ul>
     *   <li><b>Empty:</b> {@code new pair()} creates an empty dictionary.</li>
     *   <li><b>Key-value pairs:</b> {@code new pair("name", "John", "age", 27)} adds alternating key-value arguments.
     *       Keys must be String or Number; values can be any valid Une type.</li>
     *   <li><b>Plain JSON object:</b> {@code new pair("{\"age\":27,\"name\":\"Frank\"}")} parses a JSON object string.
     *   <li><b>Serialized JSON:</b> {@code new list("{\"class\":\"...\",\"data\":{...}}")} deserializes from
     *       {@link #serialize()} output.</li>
     * </ul>
     * <p>
     * <b>Examples:</b>
     * <pre>{@code
     * // Empty dictionary
     * pair p1 = new pair();
     *
     * // Key-value initialization
     * pair p2 = new pair("name", "John Doe", "age", 27, "married", true);
     *
     * // From plain JSON object
     * pair p3 = new pair("{\"age\":27,\"name\":\"Frank\"}");
     *
     * // From serialized format
     * pair p4 = new pair("{\"class\":\"com.peyrona.mingle.lang.xpreval.functions.pair\",\"data\":{\"name\":\"John\"}}");
     * }</pre>
     *
     * @param items Optional arguments:
     *            <ul>
     *              <li>(empty) - Empty pair</li>
     *              <li>(key, value, key, value, ...) - Alternating key-value pairs</li>
     *              <li>(String) - JSON object string to parse</li>
     *            </ul>
     * @throws MingleException If key-value count is not even, JSON parsing fails, or keys are not String/Number
     */
    public pair( Object... items )
    {
        if( items == null || items.length == 0 )
            return;

        // Single string argument: try JSON parsing

        if( items.length == 1 && (items[0] instanceof String) )
        {
            String s = ((String) items[0]).trim();

            if( ! s.isEmpty() && s.charAt( 0 ) == '{' )
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
     * Returns the number of key-value pairs in this dictionary.
     * <p>
     * Equivalent to {@link #len()}.
     *
     * @return The number of entries in this dictionary
     */
    public int size()
    {
        return inner.size();
    }

    /**
     * Returns the number of key-value pairs in this dictionary.
     * <p>
     * Alias for {@link #size()} provided for consistency with other collection types.
     *
     * @return The number of entries in this dictionary
     */
    public int len()
    {
        return inner.size();
    }

    /**
     * Tests whether this dictionary contains no entries.
     *
     * @return {@code true} if this dictionary contains no key-value mappings; {@code false} otherwise
     */
    public boolean isEmpty()
    {
        return inner.isEmpty();
    }

    /**
     * Removes all key-value mappings from this dictionary.
     * <p>
     * Fires property change events for each removed value.
     *
     * @return This dictionary instance (for method chaining)
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
     * Retrieves the value associated with the specified key.
     * <p>
     * Returns an empty string if the key does not exist.
     * <p>
     * Keys are case-insensitive. Both String and Number types are accepted.
     *
     * @param key The key whose associated value is to be returned. Must be String or Number.
     * @return The value associated with the key, or an empty string if the key is not present
     */
    public Object get( Object key )
    {
        return get( key, "" );
    }

    /**
     * Retrieves the value associated with the specified key, returning a default value if not found.
     * <p>
     * Keys are case-insensitive. Both String and Number types are accepted.
     *
     * @param key The key whose associated value is to be returned. Must be String or Number.
     * @param def The default value to return if the key is not present
     * @return The value associated with the key, or {@code def} if the key is not present
     */
    public Object get( Object key, Object def )
    {
        return inner.getOrDefault( keyAsString( key ), def );
    }

    /**
     * Associates the specified value with the specified key.
     * <p>
     * If the dictionary previously contained a mapping for the key, the old value is replaced.
     * Fires a property change event when a value is added or modified.
     * <p>
     * Keys are case-insensitive and normalized to lowercase internally.
     *
     * @param key The key with which the specified value is to be associated. Must be String or Number.
     * @param value The value to be associated with the specified key. Any valid Une type.
     * @return This dictionary instance (for method chaining)
     */
    public pair put( Object key, Object value )
    {
        String sKey = keyAsString( key );
        Object old  = inner.put( sKey, value );

        firePropertyChanged( old, value );

        return this;
    }

    /**
     * Copies all mappings from the specified dictionary into this dictionary.
     * <p>
     * Existing mappings are overwritten for any keys present in both dictionaries.
     *
     * @param oPair The dictionary whose mappings are to be copied. Must be a {@code pair} instance.
     * @return This dictionary instance (for method chaining)
     * @throws MingleException If {@code oPair} is not an instance of {@code pair}
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
     * Returns a list containing all keys in this dictionary.
     * <p>
     * All keys are returned as lowercase strings (their normalized form).
     *
     * @return A new {@link list} containing all keys in this dictionary
     */
    public list keys()
    {
        return new list( inner.keySet().toArray() );
    }

    /**
     * Returns a list containing all values in this dictionary.
     *
     * @return A new {@link list} containing all values in this dictionary
     */
    public list values()
    {
        return new list( inner.values().toArray() );
    }

    /**
     * Tests whether this dictionary contains a mapping for the specified key.
     * <p>
     * Key lookup is case-insensitive.
     *
     * @param key The key whose presence in this dictionary is to be tested. Must be String or Number.
     * @return {@code true} if this dictionary contains a mapping for the specified key; {@code false} otherwise
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
     * Tests whether this dictionary maps one or more keys to the specified value.
     *
     * @param value The value whose presence in this dictionary is to be tested
     * @return {@code true} if this dictionary maps one or more keys to the specified value; {@code false} otherwise
     */
    public boolean hasValue( Object value )
    {
        return inner.containsValue( value );
    }

    /**
     * Inverts the boolean or binary numeric value associated with the specified key.
     * <p>
     * Supported value types:
     * <ul>
     *   <li>{@code Boolean}: {@code true} becomes {@code false}, {@code false} becomes {@code true}</li>
     *   <li>{@code Number 0}: Becomes {@code 1}</li>
     *   <li>{@code Number 1}: Becomes {@code 0}</li>
     * </ul>
     *
     * @param key The key whose value is to be inverted. Must be String or Number.
     * @return This dictionary instance (for method chaining)
     * @throws MingleException If the key does not exist, or if its value is not a boolean or numeric 0/1
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
     * Removes the mapping for the specified key from this dictionary if present.
     * <p>
     * Fires a property change event when a value is removed.
     *
     * @param key The key whose mapping is to be removed. Must be String or Number.
     * @return This dictionary instance (for method chaining)
     */
    public pair del( Object key )
    {
        Object old = inner.remove( keyAsString( key ) );

        if( old != null )
            firePropertyChanged( old, "" );

        return this;
    }

    /**
     * Creates a deep copy of this dictionary.
     * <p>
     * All nested values that are {@code list} or {@code pair} instances are also cloned recursively.
     * Immutable values (Boolean, Number, String) are shared.
     *
     * @return A deep clone of this dictionary
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

    /**
     * Parses a delimited string into key-value pairs and adds them to this dictionary.
     * <p>
     * Uses comma as the entry separator and equals sign as the key-value separator.
     * Existing entries are overwritten if keys match.
     * <p>
     * <b>Example:</b>
     * <pre>{@code
     * pair p = new pair();
     * p.split("name=John,age=27,married=true");
     * // Result: {name=John, age=27, married=true}
     * }</pre>
     *
     * @param items The string to parse, containing entries separated by commas and key-value pairs separated by equals
     * @return This dictionary instance (for method chaining)
     */
    public pair split( Object items )
    {
        return split( items, ",", "=" );
    }

    /**
     * Parses a delimited string into key-value pairs and adds them to this dictionary.
     * <p>
     * Each entry is separated by {@code entrySep}, and within each entry, keys and values
     * are separated by {@code pairSep}. If an entry does not contain the key-value separator,
     * the entry becomes a key with an empty string as its value.
     * <p>
     * <b>Example:</b>
     * <pre>{@code
     * pair p = new pair();
     * p.split("name:John|age:27|married:true", "|", ":");
     * // Result: {name=John, age=27, married=true}
     * }</pre>
     *
     * @param items The string to parse, containing key-value entries
     * @param entrySep The separator between key-value entries (e.g., ",", "|")
     * @param pairSep The separator between keys and values (e.g., "=", ":")
     * @return This dictionary instance (for method chaining)
     */
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

    /**
     * Applies an expression to each key-value pair and returns a new dictionary with the results.
     * <p>
     * Within the expression, {@code x} represents the key and {@code y} represents the value.
     * The expression is evaluated for each mapping, and the result becomes the new value for that key.
     * <p>
     * <b>Example:</b>
     * <pre>{@code
     * pair p = new pair("a", 1, "b", 2, "c", 3);
     * pair result = p.map("y * 2");
     * // Result: {a=2, b=4, c=6}
     * }</pre>
     *
     * @param expr An Une expression that uses {@code x} (key) and/or {@code y} (value) variables
     * @return A new dictionary with transformed values
     * @throws MingleException If the expression returns null
     */
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

    /**
     * Filters this dictionary based on a boolean expression applied to each key-value pair.
     * <p>
     * Within the expression, {@code x} represents the key and {@code y} represents the value.
     * Only entries where the expression evaluates to {@code true} are included in the result.
     * <p>
     * <b>Example:</b>
     * <pre>{@code
     * pair p = new pair("a", 1, "b", 2, "c", 3, "d", 4);
     * pair result = p.filter("y > 2");
     * // Result: {c=3, d=4}
     * }</pre>
     *
     * @param expr An Une expression that evaluates to a boolean, using {@code x} (key) and/or {@code y} (value)
     * @return A new dictionary containing only the entries that satisfy the expression
     * @throws MingleException If this dictionary is empty or the expression does not return a boolean
     */
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

                if( (boolean) result )    // if( true )
                    p2Ret.put( entry.getKey(), entry.getValue() );
            }
        }

        return p2Ret;
    }

    /**
     * Reduces all values in this dictionary to a single value using the specified expression.
     * <p>
     * Equivalent to calling {@code values().reduce(expr)}.
     * <p>
     * <b>Example:</b>
     * <pre>{@code
     * pair p = new pair("a", 1, "b", 2, "c", 3);
     * Object sum = p.reduce("x + y");  // Result: 6
     * }</pre>
     *
     * @param expr An Une expression for the reduction, using {@code x} (accumulator) and {@code y} (current value)
     * @return The result of applying the reduction expression to all values
     * @throws MingleException If this dictionary is empty
     */
    @Override
    public Object reduce( Object expr )
    {
        checkNotEmpty( inner );

        return values().reduce( expr );
    }

    /**
     * Modifies this dictionary to retain only entries whose keys exist in the specified dictionary.
     * <p>
     * Entries with keys not present in the other dictionary are removed.
     * <p>
     * <b>Example:</b>
     * <pre>{@code
     * pair p1 = new pair("a", 1, "b", 2, "c", 3);
     * pair p2 = new pair("b", 20, "c", 30);
     * p1.intersect(p2);
     * // p1 now contains: {b=2, c=3}
     * }</pre>
     *
     * @param oPair The dictionary whose keys define the intersection. Must be a {@code pair} instance.
     * @return This dictionary instance (for method chaining)
     * @throws MingleException If {@code oPair} is not an instance of {@code pair}
     */
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

    /**
     * Adds all mappings from the specified dictionary to this dictionary.
     * <p>
     * This is an alias for {@link #putAll(Object)}. Existing mappings are overwritten
     * if keys overlap.
     * <p>
     * <b>Example:</b>
     * <pre>{@code
     * pair p1 = new pair("a", 1, "b", 2);
     * pair p2 = new pair("c", 3, "a", 10);
     * p1.union(p2);
     * // p1 now contains: {a=10, b=2, c=3}
     * }</pre>
     *
     * @param oPair The dictionary whose mappings are to be added. Must be a {@code pair} instance.
     * @return This dictionary instance (for method chaining)
     */
    @Override
    public pair union( Object oPair )
    {
        return putAll( oPair );
    }

    /**
     * Compares this dictionary to another for ordering.
     * <p>
     * Returns:
     * <ul>
     *   <li>{@code 1} if this dictionary has more entries than {@code o}</li>
     *   <li>{@code -1} if this dictionary has fewer entries than {@code o}</li>
     *   <li>{@code 0} if both dictionaries have the same keys and values</li>
     *   <li>{@code -1} if a key in this dictionary is not present in {@code o}</li>
     *   <li>{@code 1} if a value differs between the dictionaries</li>
     *   <li>{@code -1} if {@code o} is {@code null}</li>
     * </ul>
     * <p>
     * String value comparisons are case-insensitive.
     *
     * @param o The dictionary to compare to. Must be a {@code pair} instance.
     * @return {@code -1}, {@code 0}, or {@code 1} based on the comparison rules
     * @throws MingleException If {@code o} is not an instance of {@code pair}
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

    /**
     * Returns a JSON string representation of this instance's data.
     * The result can be used for interoperativity.
     * @return A JSON string representation.
     */
    @Override
    public String toJson()
    {
        if( isEmpty() )
            return "{}";

        JsonObject jo = Json.object();

        synchronized( inner )
        {
            for( Map.Entry entry : getEntrySet() )
            {
                Object val = entry.getValue();

                if( val instanceof ExtraTypeCollection )
                    jo.add( entry.getKey().toString(), Json.parse( ((ExtraTypeCollection) val).toJson() ) );
                else
                    jo.add( entry.getKey().toString(), UtilType.toJson( val ) );
            }
        }

        return jo.toString();
    }

    /**
     * Returns a string representation of this dictionary.
     * <p>
     * Format: {@code {key1=value1, key2=value2, ...}}
     *
     * @return A string representation of this dictionary
     */
    @Override
    public String toString()
    {
        return UtilColls.toString( inner );
    }

    /**
     * Returns the hash code value for this dictionary.
     *
     * @return The hash code value
     */
    @Override
    public int hashCode()
    {
        int hash = 7;
            hash = 79 * hash + Objects.hashCode( this.inner );
        return hash;
    }

    /**
     * Compares this dictionary to the specified object for equality.
     * <p>
     * Returns {@code true} if and only if the specified object is also a {@code pair}
     * and both dictionaries contain the same key-value mappings.
     * Comparison is case-insensitive for string values.
     *
     * @param obj The object to compare for equality
     * @return {@code true} if the specified object is equal to this dictionary
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

        final pair other = (pair) obj;

        return (compareTo( other ) == 0);
    }

    //------------------------------------------------------------------------//
    // TO BE USED FROM SCRIPTS

    /**
     * Serializes this dictionary to a JSON object.
     * <p>
     * The serialized format includes:
     * <ul>
     *   <li>{@code "class"}: The fully qualified class name</li>
     *   <li>{@code "data"}: A JSON object containing all key-value mappings</li>
     * </ul>
     * <p>
     * This can be passed to {@link #deserialize(JsonObject)} to reconstruct the dictionary.
     *
     * @return A JSON object representing this dictionary
     * @see UtilType#toJson(java.lang.Object)
     */
    @Override
    public JsonObject serialize()
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

    /**
     * Deserializes a JSON object into this dictionary.
     * <p>
     * Clears any existing entries and populates from the JSON data.
     * The JSON object should have a {@code "data"} field containing a JSON object
     * with key-value mappings.
     *
     * @param json A JSON object containing serialized pair data, typically from {@link #serialize()}
     * @return This dictionary instance (for method chaining)
     */
    @Override
    public pair deserialize( JsonObject json )
    {
        JsonValue jv = json.get( "data" );

        if( jv == null || ! jv.isObject() )
            throw new MingleException( "Invalid serialized pair: missing or non-object 'data' field" );

        JsonObject jo = jv.asObject();

        empty();

        for( String key : jo.names() )
            put( key, UtilJson.toUneType( jo.get( key ) ) );

        return this;
    }

    /**
     * Returns an iterator over the entries in this dictionary.
     * <p>
     * The iterator returns {@link java.util.Map.Entry} objects.
     *
     * @return An iterator over the key-value mappings in this dictionary
     */
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