/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package com.peyrona.mingle.lang.japi;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

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
                                    List<V> newList = new ArrayList<>();
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
     *
     * @param key   the key of the list to modify
     * @param value the value to remove
     * @return true if the value was removed, false otherwise
     */
    public boolean remove(K key, V value)
    {
        List<V> list = map.get( key );                    // Can not use ::get(key) because it returns an unmodifiable list

        return (list != null) && list.remove( value );    // true  --> the value was removed but the key remains
    }                                                     // false --> Key not found in the map

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

    public K getKeyForValue( V value )
    {
        for( Map.Entry<K,List<V>> entry : map.entrySet() )
        {
            if( entry.getValue().contains( value ) )
                return entry.getKey();
        }

        return null;
    }

    public Set<Entry<K,List<V>>> entrySet()
    {
        return map.entrySet();
    }
}