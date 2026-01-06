
package com.peyrona.mingle.lang.japi;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;

public final class UtilColls
{
    public static final char cRECORD_SEP = '\u001E';    // Record Separator (RS) character – \u001E.

    //------------------------------------------------------------------------//
    private UtilColls() {}  // Avoid this class instances creation
    //------------------------------------------------------------------------//

    /**
     * Checks if collection is not empty (also handles null).
     *
     * @param c the collection to check
     * @return {@code true} if collection is not null and not empty, {@code false} otherwise
     */
    public static boolean isNotEmpty( Collection c )
    {
        return ((c != null) && (! c.isEmpty()));
    }

    /**
     * Checks if collection is empty (also handles null).
     *
     * @param c the collection to check
     * @return {@code true} if collection is null or empty, {@code false} otherwise
     */
    public static boolean isEmpty( Collection c )
    {
        return ((c == null) || c.isEmpty());
    }

    /**
     * Checks if map is not empty (also handles null).
     *
     * @param c the map to check
     * @return {@code true} if map is not null and not empty, {@code false} otherwise
     */
    public static boolean isNotEmpty( Map c )
    {
        return ((c != null) && (! c.isEmpty()));
    }

    /**
     * Checks if map is empty (also handles null).
     *
     * @param c the map to check
     * @return {@code true} if map is null or empty, {@code false} otherwise
     */
    public static boolean isEmpty( Map c )
    {
        return ((c == null) || c.isEmpty());
    }

    /**
     * Checks if array is empty (also handles null).
     *
     * @param array the array to check
     * @return {@code true} if array is null or has length 0, {@code false} otherwise
     */
    public static boolean isEmpty( Object[] array )
    {
        return (array == null) || (array.length == 0);
    }

    /**
     * Checks if array is not empty (also handles null).
     *
     * @param array the array to check
     * @return {@code true} if array is not null and has at least one element, {@code false} otherwise
     */
    public static boolean isNotEmpty( Object[] array )
    {
        return ! isEmpty( array );
    }

    //------------------------------------------------------------------------//
    // CONVERTING BACK AND FORTH

    /**
     * Converts array to string using comma separator.
     *
     * @param array the array to convert
     * @return String representation of array with comma separator
     */
    public static String toString( Object[] array )
    {
        return toString( array, ',' );
    }

    /**
     * Converts array to string using specified separator.
     * <p>
     * String items are wrapped in double quotes, null items as "\"\"",
     * and numeric items without quotes.
     *
     * @param array     the array to convert
     * @param separator the character to use as separator
     * @return String representation of array with specified separator
     */
    public static String toString( Object[] array, char separator )
    {
        if( array == null )
            return null;

        // -----------------------------------------------------------------
        //    String str = UtilType.javaToJson( array ).toString();
        //
        //    if( str.length() <= 2 )    // "[]" JSON Array
        //        return "";
        //
        //    return str.substring( 1, str.length() - 1 );
        // -----------------------------------------------------------------

        if( array.length == 0 )
            return "";

        if( array.length == 1 )
            return ((array[0] == null) ? "" : array[0].toString());

        StringBuilder sb = new StringBuilder( array.length * 16 );

        for( Object obj : array )
        {
                 if( obj == null )            sb.append( "\"\"" );
            else if( obj instanceof String )  sb.append( '"' ).append( obj.toString() ).append( '"' );
            else                              sb.append( obj.toString() );

            sb.append( separator );
        }

        sb.deleteCharAt( sb.length() - 1 );   // Deletes last separator

        return sb.toString();
    }

    /**
     * Converts string to array using comma separator.
     *
     * @param str the string to split
     * @return Array of objects parsed from string
     * @see #toArray(java.lang.String, char)
     */
    public static Object[] toArray( String str )
    {
        return toArray( str, ',' );
    }

    /**
     * Opposite functionality to ::toString( Object[] ).<p>
     * If o is null an empty array of type String will be returned.
     *
     * @param str To split.
     * @param separator
     * @return
     * @see #toString(java.lang.Object[])
     */
    public static Object[] toArray( String str, char separator )
    {
        if( UtilStr.isEmpty( str ) )
            return new String[0];

        // Following RegEx is used to match whitespace only if that whitespace is outside a pair of double quotes.
        // It allows for spaces at the beginning of the string but excludes spaces within double-quoted sections.

        String[] as = str.split( separator +"\\s*(?=(?:[^\"]*\"[^\"]*\")*[^\"]*$)" );
        Object[] ao = new Object[ as.length ];

        for( int n = 0; n < as.length; n++ )     // e.g.: "item_1, item_2,  item_3"
            ao[n] = UtilType.toUne( as[n] );

        return ao;
    }

    /**
     * Converts array of objects to array of strings.
     *
     * @param ao the object array to convert
     * @return String array representation, or null if ao is null
     */
    public static String[] toArrayOfStr( Object[] ao )
    {
        if( ao == null )
            return null;

        String[] as = new String[ ao.length ];

        for( int n = 0; n < ao.length; n++ )
            as[n] = (ao[n] == null) ? "null" : ao[n].toString();

        return as;
    }

    /**
     * Converts map to string using comma and equals separators.
     *
     * @param map the map to convert
     * @return String representation with format "key1=value1,key2=value2,..."
     * @see #toString(java.util.Map, char, char)
     */
    public static String toString( Map map )
    {
        return toString( map, ',', '=' );
    }

    /**
     * From a Map returns a String with following format:<br>
     * [key1][pairSep][value1][entrySep][key2][pairsep][value2][entrySep]...[keyN][pairsep][valueN]
     * <p>
     * Null keys or values are converted to "null".
     *
     * @param map Map to convert into its String representation.
     * @param cEntrySep Map entry separator
     * @param cPairSep {Key,Value} pair separator
     * @return A String with above described format.
     */
    public static String toString( Map map, char cEntrySep, char cPairSep )
    {
        if( (map == null) || map.isEmpty() )
            return "";

        StringBuilder  sb  = new StringBuilder( 1024 );
        Set<Map.Entry> set = map.entrySet();

        for( Map.Entry entry : set )
        {
            String  sEntry = (entry.getKey()   == null) ? "nokey" : entry.getKey().toString();    // Better to be "nokey"
            Object  sValue = (entry.getValue() == null) ? ""      : entry.getValue();             // Better to be ""

            sb.append( '"' ).append( sEntry ).append( '"' )
              .append( cPairSep );

            if( sValue instanceof String )  sb.append( '"' ).append( sValue ).append( '"' );
            else                            sb.append( sValue );

            sb.append( cEntrySep );
        }

        sb.deleteCharAt( sb.length() - 1 );   // Deletes last sEntrySep

        return sb.toString();
    }

    /**
     * Converts list to string using comma separator.
     *
     * @param list the list to convert
     * @return String representation of list
     * @see #toString(java.lang.Object[])
     */
    public static String toString( List list )
    {
        return toString( list.toArray() );
    }

    /**
     * Converts list to string using specified separator.
     *
     * @param list the list to convert
     * @param sep  the character to use as separator
     * @return String representation of list
     */
    public static String toString( List list, char sep )
    {
        return toString( list.toArray(), sep );
    }

    /**
     * Converts string to list using comma separator.
     *
     * @param str the string to split
     * @return List of objects parsed from string
     */
    public static List toList( String str )
    {
        return toList( toArray( str ) );
    }

    /**
     * Converts string to list using specified separator.
     *
     * @param str the string to split
     * @param sep the character to use as separator
     * @return List of objects parsed from string
     */
    public static List toList( String str, char sep )
    {
        return toList( toArray( str, sep ) );
    }

    /**
     * Creates a new modifiable List from passed items (this method is just a short
     * (but useful) version of: new ArrayList( Arrays.asList( items ) ).
     *
     * @param items
     * @return A new modifiable List from passed items.
     */
    public static List toList( Object... items )
    {
        // Do not use: Arrays.asList( items ) directly because it returns an unmodifiable List.
        // It is needed to do as follows:

        return new ArrayList( Arrays.asList( items ) );
    }

    /**
     * This makes following call:
     * <pre>
     *    return string2Map( s, ",", "=" );
     * </pre>
     *
     * @param s
     * @return
     * @see #toMap(java.lang.String, char, char)
     */
    public static Map<String,String> toMap( String s )
    {
        return UtilColls.toMap( s, ',', '=' );
    }

    /**
     * Returns a Map of Strings based on format described in map2String method.
     *
     * @param s String to convert into a Map.
     * @param cEntrySep Map entry separator. In  "day=22 , month=2" it is the ','
     * @param cPairSep {Key,Value} pair separator. In "day=22" it is the '='
     * @return The resulting Map.
     */
    public static Map<String,String> toMap( String s, char cEntrySep, char cPairSep )
    {
        if( UtilStr.isEmpty( s ) )
            return new HashMap<>();

        if( cEntrySep == cPairSep )
            throw new IllegalArgumentException( "Both separators are the same." );

        Map<String,String> map    = new HashMap<>();
        Object[]           asPair = toArray( s, cEntrySep );

        for( Object pair : asPair )    // i.e.: "key1=val1 , , key2 =val2 ,"
        {
            String sPair = pair.toString();

            if( ! sPair.trim().isEmpty() )
            {
                if( sPair.indexOf( cPairSep ) > -1 )
                {
                    int    nIndex = sPair.indexOf( cPairSep );
                    String sKey   = sPair.substring( 0, nIndex  ).trim().replaceAll("^\"|\"$", "");     // i.e.: "key1=val1, key2 =val2, key3 = val3"
                    String sValue = ((sPair.length() < nIndex + 1) ? "" : sPair.substring( nIndex + 1 )).trim().replaceAll("^\"|\"$", "");

                    map.put( ("null".equals( sKey   ) ? "" : sKey  ),
                             ("null".equals( sValue ) ? "" : sValue) );
                }
                else     // i.e.: "key"   (no value, no '=')
                {
                    map.put( sPair, "" );
                }
            }
        }

        return map;
    }

    //------------------------------------------------------------------------//

    /**
     * Returns the item at index n from List list or null if n is out of range.<br>
     * Positive numbers get items counting from the head of the list and negative from tail.<br>
     * If list is null or 'ndx' is out of range, then null is returned.
     *
     * @param <T>
     * @param list
     * @param ndx
     * @return the item at index n from List list or null if n is out of range.
     */
    public static <T> T getAt( List<T> list, int ndx )
    {
        return getAt( list, ndx, null );
    }

    /**
     * Returns the item at index 'ndx' from List list or 'def' if n is out of range.<br>
     * Positive numbers get items counting from the head of the list and negative from tail.<br>
     * If list is null or 'ndx' is out of range, then 'def' is returned.
     *
     * @param <T>
     * @param list
     * @param ndx
     * @param def
     * @return the item at index n from List list or 'def' if n is out of range.
     */
    public static <T> T getAt( List<T> list, int ndx, T def )
    {
        if( (list == null) || list.isEmpty() )
            return def;

        int size = list.size();

        if( ndx >= 0 )   // Counting from head
        {
            return (ndx < size) ? list.get( ndx ) : def;
        }
        else             // Counting from tail
        {
            ndx = size + ndx;

            return (ndx >= 0) ? list.get( ndx ): def;
        }
    }

    /**
     * Returns true if the array contains the item: when item is a String, the equality is done ignoring the case.
     *
     * @param array
     * @param item
     * @return true if the array contains the item.
     */
    public static boolean contains( Object[] array, Object item )
    {
        if( isEmpty( array ) )
            return false;

        if( item instanceof String )
        {
            for( Object o : array )
            {
                if( (o == null) && (item == null) )
                    return true;

                if( (o != null) && (o instanceof String) && (o.toString().equalsIgnoreCase( item.toString() )) )
                    return true;
            }
        }
        else
        {
            for( Object o : array )
            {
                if( (o == null) && (item == null) )
                    return true;

                if( (o != null) && (o.equals( item )) )
                    return true;
            }
        }

        return false;
    }

    /**
     * Checks if all items in collection satisfy the predicate.
     *
     * @param list      the collection to check
     * @param condition the predicate to test each item
     * @return {@code true} if all items satisfy the condition, {@code false} otherwise
     */
    public static boolean areAll( Collection list, Predicate condition )
    {
        for( Object item : list )
        {
            if( ! condition.test( item ) )
                return false;
        }

        return true;
    }

    /**
     * Counts items in collection that satisfy the predicate.
     *
     * @param list      the collection to count items in
     * @param condition the predicate to test each item
     * @return Number of items that satisfy the condition
     */
    public static int count( Collection list, Predicate condition )
    {
        int n = 0;

        for( Object item : list )
        {
            if( condition.test( item ) )
                n++;
        }

        return n;
    }

    /**
     * Removes the last item from the list.
     *
     * @param <T>  the type of elements in the list
     * @param list the list to modify
     * @return The modified list (same instance)
     */
    public static <T> List<T> removeTail( List<T> list )
    {
        if( ! list.isEmpty() )
            list.remove( list.size() - 1 );

        return list;
    }

    /**
     * Removes the last item from the list if it satisfies the predicate.
     *
     * @param <T>       the type of elements in the list
     * @param list      the list to modify
     * @param condition the predicate to test against the last item
     * @return The modified list (same instance)
     */
    public static <T> List<T> removeTailIf( List<T> list, Predicate<T> condition )
    {
        if( (! list.isEmpty()) && (condition.test( getAt( list, -1 ) )) )
            list.remove( list.size() - 1 );

        return list;
    }

    /**
     * Returns the first occurrence in the list that matches the predicate or null if no match.
     *
     * @param <T>
     * @param list Where to search.
     * @param condition How to find a match.
     * @return The first occurrence in the list that matches the predicate or null if no match.
     */
    public static <T> T find( Collection<T> list, Predicate<T> condition )
    {
        for( T item : list )
        {
            if( condition.test( item ) )
                return item;
        }

        return null;
    }

    /**
     * Returns the index of the first item in list that matches the predicate.
     *
     * @param <T>       the type of elements in the list
     * @param list      the list to search
     * @param condition the predicate to test each item
     * @return The index of the first matching item, or -1 if no match found
     */
    public static <T> int findIndex( List<T> list, Predicate<T> condition )
    {
        for( int n = 0; n < list.size(); n++ )
        {
            if( condition.test( list.get( n ) ) )
                return n;
        }

        return -1;
    }

    /**
     * Finds all duplicate items in the collection.
     *
     * @param <T>  the type of elements in the collection
     * @param coll the collection to search for duplicates
     * @return List of items that appear more than once (each duplicate listed once)
     * @throws IllegalArgumentException if coll is a Map
     */
    public static <T> List findDuplicates( Collection<T> coll )
    {
        if( (coll == null) || (coll.size() < 2) )
            return new ArrayList<>();

        if( coll instanceof Map )
            throw new IllegalArgumentException( "No he probado que funcione con Maps");

        List<T> lstTmp = new ArrayList<>();
        List<T> lstDup = new ArrayList<>();

        for( T item : coll )
        {
            if( (lstTmp.indexOf( item ) >  -1) &&    // It is already in lstTmp
                (lstDup.indexOf( item ) == -1) )     // but not still in lstDup (avoids duplicates in asDup)
            {
                lstDup.add( item );
            }

            lstTmp.add( item );
        }

        return lstDup;
    }

    /**
     * Sorts the array in ascending order.
     * <p>
     * Array elements must implement Comparable interface.
     *
     * @param <T>   the type of elements in the array
     * @param array the array to sort (modified in place)
     * @return The sorted array (same instance)
     */
    public static <T> T[] sort( T[] array )
    {
        if( isNotEmpty( array ) )
            Arrays.sort( array );

        return array;
    }

    /**
     * Returns the items that exist in arr2 that do not exist in array1: items that were added.
     *
     * @param <T>
     * @param arr1
     * @param arr2
     * @return The items that exist in arr2 that do not exist in array1: items that were added.
     */
    public static <T> T[] added( T[] arr1, T[] arr2 )
    {
        List<T> result  = new ArrayList<>();
        Set<T>  setArr1 = new HashSet<>( Arrays.asList( arr1 ) );

        for( T item : arr2 )
        {
            if( ! setArr1.contains( item ) )
                result.add( item );
        }

        return result.toArray( Arrays.copyOf( arr1, 0 ) );
    }

    /**
     * Returns the items that are in array1 but are not in array2: items that were removed.
     *
     * @param <T>
     * @param arr1
     * @param arr2
     * @return The items that are in array1 but are not in array2: items that were removed.
     */
    public static <T> T[] removed( T[] arr1, T[] arr2 )
    {
        List<T> result  = new ArrayList<>();
        Set<T>  setArr2 = new HashSet<>( Arrays.asList( arr2 ) );

        for( T item : arr1 )
        {
            if( ! setArr2.contains( item ) )
                result.add( item );
        }

        return result.toArray( Arrays.copyOf( arr1, 0 ) );
    }

    /**
     * Returns a new array containing only the items that exist in both arrays: intersection.
     *
     * @param <T>
     * @param arr1
     * @param arr2
     * @return A new array containing only the items that exist in both arrays: intersection.
     */
    public static <T> T[] intersection( T[] arr1, T[] arr2 )
    {
        List<T> result = new ArrayList<>();
        Set<T>  set    = new HashSet<>( Arrays.asList( arr1 ) );

        for( T item : arr2 )
        {
            if( set.contains( item ) )
                result.add( item );
        }

        return result.toArray( Arrays.copyOf( arr1, 0 ) );
    }

    private static class item
    {
        public item()
        {
        }
    }
}