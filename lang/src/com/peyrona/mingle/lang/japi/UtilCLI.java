
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
 * Official web site at: <a href="https://mingle.peyrona.com">https://mingle.peyrona.com</a>
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

    public boolean isEmpty()
    {
        return (as.length == 0);
    }

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

    public boolean isValue( String sValueKey, String sToCompare )
    {
        String sValue = getValue( sValueKey );

        if( sToCompare == null )
        {
            return (sValue == null);
        }

        return sToCompare.equalsIgnoreCase( sValue );
    }

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