
package com.peyrona.mingle.lang.japi;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Command Line Interface parser.<br>
 * <p>
 * Options has de form of: -port:54321
 * <p>
 * All searches are case insensitive.
 *
 * @author Francisco José Morero Peyrona
 *
 * Official web site at: <a href="https://github.com/peyrona/mingle">https://github.com/peyrona/mingle</a>
 */
public final class UtilCLI
{
    private final String[] as;

    //------------------------------------------------------------------------//

    public UtilCLI( String[] as )
    {
        if( as == null )
        {
            this.as = new String[0];
        }
        else
        {
            this.as = new String[ as.length ];
            System.arraycopy( as, 0, this.as, 0, as.length );
        }
    }

    //------------------------------------------------------------------------//

    /**
     * Checks if command line arguments array is empty.
     *
     * @return {@code true} if no arguments were provided, {@code false} otherwise.
     */
    public boolean isEmpty()
    {
        return (as.length == 0);
    }

    /**
     * Returns a copy of the raw command line arguments array.
     *
     * @return A defensive copy of the arguments array.
     */
    public String[] getRaw()
    {
        return Arrays.copyOf( as, as.length );
    }

    /**
     *
     * <p>
     * The search is case insensitive.
     *
     * @param sOpt
     * @return
     */
    public boolean hasOption( String sOpt )
    {
        return (getOption( sOpt ) != null);
    }

    /**
     * Options has de form of: -SSL (no value after the option).
     * <p>
     * The search is case insensitive.
     *
     * @param sOpt
     * @return
     */
    public String getOption( String sOpt )
    {
        return getOption( sOpt, null );
    }

    /**
     * Options has de form of: -SSL (no value after the option).
     * <p>
     * The search is case insensitive.
     *
     * @param sOpt
     * @param sDefault
     * @return
     */
    public String getOption( String sOpt, String sDefault )
    {
        if( sOpt.charAt( 0 ) != '-' )
            sOpt = '-' + sOpt;

        for( String s : as )
        {
            if( UtilStr.startsWith( s, sOpt ) )
                return s;
        }

        return sDefault;
    }

    /**
     * Options has de form of: -port:54321
     * <p>
     * The search is case insensitive.
     *
     * @param sOpt
     * @return
     */
    public String getValue( String sOpt )
    {
        return getValue( sOpt, null );
    }

    /**
     * Options has de form of: -port:54321
     * <p>
     * The search is case insensitive.
     *
     * @param sOpt      The option name to search for.
     * @param oDefault The default value to return if option is not found.
     * @return The value converted to type T, or oDefault if option not found.
     */
    public <T> T getValue( String sOpt, T oDefault )
    {
        String sRet = getOption( sOpt );

        if( sRet != null )
        {
            String[] asPair = sRet.split( "=" );

            sRet = ((asPair.length == 2) ? asPair[1] : null);
        }

        if( sRet == null )
            return oDefault;

        if( oDefault == null            )  return (T) sRet;
        if( oDefault instanceof String  )  return (T) sRet;
        if( oDefault instanceof Boolean )  return (T) UtilType.toBoolean( sRet );
        if( oDefault instanceof Integer )  return (T) UtilType.toInteger( sRet );
        if( oDefault instanceof Float   )  return (T) UtilType.toFloat( sRet );

        return null;
    }

    /**
     * Returns multiple values from an option with comma-separated values.
     * Options have the form: -option:value1,value2,value3
     * <p>
     * The search is case insensitive.
     *
     * @param sOpt The option name to search for.
     * @return Array of values, or null if option not found or no values.
     */
    public String[] getMultiValue( String sOpt )
    {
        String       sOption = getOption( sOpt );
        List<String> lstRet  =  new ArrayList<>();

        if( sOption != null )
        {
            String[] asPair = sOption.split( "=" );

            if( asPair.length == 2 )
            {
                for( String s : asPair[1].split( "," ) )
                {
                    lstRet.add( s.trim() );
                }
            }
        }

        return (lstRet.isEmpty() ? null : lstRet.toArray( String[]::new ));
    }

    /**
     * Checks if an option's value matches the specified string (case-insensitive).
     *
     * @param sValueKey The option name to get the value from.
     * @param sToCompare The string to compare against. If null, checks if value is also null.
     * @return {@code true} if the value matches (both null or equal ignoring case), {@code false} otherwise.
     */
    public boolean isValue( String sValueKey, String sToCompare )
    {
        String sValue = getValue( sValueKey );

        if( sToCompare == null )
        {
            return (sValue == null);
        }

        return sToCompare.equalsIgnoreCase( sValue );
    }

    /**
     * Returns all arguments that are not options (i.e., do not start with '-').
     *
     * @return Array of non-option arguments.
     */
    public String[] getNoOptions()
    {
        List<String> lst2Ret = new ArrayList<>();

        for( String s : as )
        {
            if( UtilStr.isNotEmpty( s ) && (s.charAt( 0 ) != '-') )
                lst2Ret.add( s );
        }

        return lst2Ret.toArray( String[]::new );
    }
}