
package com.peyrona.mingle.lang.japi;

import java.util.Collection;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Thread-safe map where each key maps to a list of values.
 * <p>
 * This class wraps a {@code ConcurrentHashMap<K, List<V>>} and provides convenience
 * methods for managing one-to-many relationships. All operations are thread-safe.
 *
 * @param <K> the type of keys maintained by this map
 * @param <V> the type of values in the lists
 */
public class OneToMany<K,V>
{
    private final ConcurrentHashMap<K, List<V>> map;    // Internal ConcurrentHashMap to store key-list mappings

    //------------------------------------------------------------------------//

    /**
     * Constructs a new OneToMany collection with default initial capacity.
     */
    public OneToMany()
    {
        this.map = new ConcurrentHashMap<>();
    }

    /**
     * Constructs a new OneToMany collection with specified initial capacity.
     *
     * @param initialCapacity the initial capacity of the underlying map
     */
    public OneToMany(int initialCapacity)
    {
        this.map = new ConcurrentHashMap<>( initialCapacity );
    }

    //------------------------------------------------------------------------//

    /**
     * Adds a value to the list associated with the given key. Creates a new list if the key doesn't exist.
     *
     * @param key   the key to associate the value with
     * @param value the value to put to the list
     */
    public void put(K key, V value)
    {
        map.compute( key, (k, existingList) ->
                            {
                                if( existingList == null )
                                {
                                    // Use CopyOnWriteArrayList for thread safety during iteration
                                    List<V> newList = new CopyOnWriteArrayList<>();
                                            newList.add( value );
                                    return newList;
                                }

                                existingList.add( value );

                                return existingList;
                            } );
    }

    /**
     * Retrieves the list of values for a given key. Returns an empty list if the key is not found.
     *
     * @param key the key to retrieve values for
     * @return an unmodifiable list of values
     */
    public List<V> get(K key)
    {
        List<V> list = map.get( key );

        return list == null ? Collections.emptyList() : Collections.unmodifiableList( list );
    }

    /**
     * Returns an enumeration of the keys in this map.
     *
     * @return An enumeration of the keys in this map.
     */
    public Enumeration<K> keys()
    {
        return map.keys();
    }

    /**
     * Returns a Collection view of the values contained in this map. The collection is backed
     * by the map, so changes to the map are reflected in the collection, and vice-versa.
     * The collection supports element removal, which removes the corresponding mapping from
     * this map, via the Iterator.remove, Collection.remove, removeAll, retainAll, and clear
     * operations. It does not support the add or addAll operations.
     *
     * @return A Collection view of the values contained in this map.
     */
    public Collection<List<V>> values()
    {
        return map.values();
    }

    /**
     * Removes a specific value from the list associated with a key.
     * <p>
     * If the list becomes empty after removal, the key is removed from the map.
     *
     * @param key   the key of the list to modify
     * @param value the value to remove
     * @return true if the value was removed, false otherwise
     */
    public boolean remove(K key, V value)
    {
        // Use computeIfPresent to ensure atomicity and cleanup empty lists
        // We use a mutable boolean array to extract the result 'removed' from the lambda
        boolean[] removed = { false };

        map.computeIfPresent( key, (k, list) ->
        {
            if( list.remove( value ) )
            {
                removed[0] = true;
                return list.isEmpty() ? null : list; // Remove key if list is empty
            }
            return list;
        } );

        return removed[0];
    }

    /**
     * Removes all values associated with a given key.
     *
     * @param key the key to remove
     * @return the list of values that were associated with the key, or null if no such key existed
     */
    public List<V> removeAll(K key)
    {
        return map.remove( key );
    }

    /**
     * Checks if the collection contains the specified key.
     *
     * @param key the key to check
     * @return true if the key exists, false otherwise
     */
    public boolean containsKey(K key)
    {
        return map.containsKey( key );
    }

    /**
     * Returns the number of keys in the collection.
     *
     * @return the number of keys
     */
    public int size()
    {
        return map.size();
    }

    /**
     * Checks if the collection is empty.
     *
     * @return true if the collection has no keys, false otherwise
     */
    public boolean isEmpty()
    {
        return map.isEmpty();
    }

    /**
     * Clears all entries from the collection.
     */
    public void clear()
    {
        map.clear();
    }

    /**
     * Finds the first key that contains the specified value in its associated list.
     * <p>
     * <b>Note on Consistency:</b> This method iterates over the map and its values.
     * Since the map and the lists are concurrent collections, the result reflects
     * the state of the collection at the time of iteration. It is possible to miss
     * a value if it is moved or added to a list that has already been visited
     * during the iteration. This provides <i>weak consistency</i>.
     *
     * @param value the value to search for
     * @return the first key whose list contains the value, or null if not found
     */
    public K getKeyForValue( V value )
    {
        for( Map.Entry<K,List<V>> entry : map.entrySet() )
        {
            if( entry.getValue().contains( value ) )
                return entry.getKey();
        }

        return null;
    }

    /**
     * Returns a Set view of the mappings contained in this map.
     * <p>
     * The set is backed by the map, so changes to the map are reflected in the set,
     * and vice-versa. The set supports element removal, which removes the corresponding
     * mapping from the map, via Iterator.remove, Set.remove, removeAll, retainAll, and
     * clear operations. It does not support the add or addAll operations.
     *
     * @return a set view of the mappings contained in this map
     */
    public Set<Entry<K,List<V>>> entrySet()
    {
        return map.entrySet();
    }
}