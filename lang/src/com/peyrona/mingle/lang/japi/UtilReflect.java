
package com.peyrona.mingle.lang.japi;

import com.peyrona.mingle.lang.MingleException;
import java.io.IOException;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.MalformedURLException;
import java.net.URISyntaxException;
import java.util.Arrays;

/**
 *
 * @author Francisco José Morero Peyrona
 *
 * Official web site at: <a href="https://github.com/peyrona/mingle">https://github.com/peyrona/mingle</a>
 *
 * Official web site at: <a href="https://github.com/peyrona/mingle">https://github.com/peyrona/mingle</a>
 */
public final class UtilReflect
{
    public static boolean areAll( Class<?> clazz, Object... instances )
    {
        if( UtilColls.isEmpty( instances ) )
            return false;

        for( Object obj : instances )
            if( ! clazz.isAssignableFrom( obj.getClass() ) )
                return false;

        return true;
    }

    public static boolean areAllSame( Object... instances )
    {
        if( UtilColls.isEmpty( instances ) )
            return false;

        Class<?> clazz = instances[0].getClass();

        for( int n = 1; n < instances.length; n++ )
            if( ! clazz.isAssignableFrom( instances[n].getClass() ) )
                return false;

        return true;
    }

    /**
     * <p>
     * This method instead of throwing an exception, returns silently null because throwing
     * exceptions is CPU expensive.
     *
     * @param clazz
     * @param sField
     * @return
     */
    public static Field getField( Class<?> clazz, String sField )
    {
        sField = sField.trim().toLowerCase();

        for( Field field : clazz.getDeclaredFields() )
        {
            if( field.getName().equalsIgnoreCase( sField ) )
                return field;
        }

        if( clazz.getSuperclass() != null )
            return getField( clazz.getSuperclass(), sField );

        return null;
    }

    /**
     * Searches (ignoring case) for a match among public methods by their names and parameters types.<br>
     * The scope for the search is the class and its superclasses.
     * <p>
     * This method instead of throwing an exception, returns silently null because throwing
     * exceptions is CPU expensive.
     *
     * @param clazz Class to search into.
     * @param sMethod Method name (case is ignored).
     * @param aoParamTypes The parameters the method receives (if any). Can be null or empty.
     * @return The method or null if passed method with passed arguments does not exists.
     * @throws IllegalArgumentException if passed method name is empty.
     */
    public static Method getMethod( Class<?> clazz, String sMethod, Class<?>... aoParamTypes )
    {
        int nParams = (aoParamTypes == null) ? 0 : aoParamTypes.length;

        sMethod = sMethod.trim();

        for( Method method : clazz.getDeclaredMethods() )
        {
            if( (method.isVarArgs() || (nParams == method.getParameterCount()) )        // This 1st to speed up
                &&
                method.getName().equalsIgnoreCase( sMethod ) )
            {
                if( method.isVarArgs() || nParams == 0 )    // This is not needed -> (method.getParameterCount() == 0)
                    return method;                          // because above is checked that both have same number of parameters

                if( Arrays.equals( aoParamTypes, method.getParameterTypes() ) )
                    return method;
            }
        }

        // Code arrives here when received class does not have the method: let check its superclass

        if( clazz.getSuperclass() != null )
        {
            return getMethod( clazz.getSuperclass(), sMethod, aoParamTypes );
        }

        return null;
    }

    /**
     * Dynamically creates an new instance.
     *
     * @param <T> Type to be returned.
     * @param type Type class to be returned.
     * @param args Real arguments to pass to the constructor.
     * @return A new instance for passed URI.
     * @throws ClassNotFoundException
     * @throws InstantiationException
     * @throws java.lang.NoSuchMethodException
     * @throws java.lang.IllegalAccessException
     * @throws java.net.MalformedURLException
     * @throws java.net.URISyntaxException
     * @throws java.lang.reflect.InvocationTargetException
     */
    public static <T> T newInstance( Class<T> type, Object... args )
                        throws ClassNotFoundException, InstantiationException, NoSuchMethodException, IllegalAccessException,
                               MalformedURLException, URISyntaxException, IOException, IllegalArgumentException, InvocationTargetException
    {
        Class[] ac = null;

        if( args != null )
        {
            ac = new Class[ args.length ];

            for( int n = 0; n < args.length; n++ )
                ac[n] = args[n].getClass();
        }

        return newInstance( type, type.getName(), null, (Class[]) ac, (Object[]) args );
    }

    /**
     * Dynamically creates an new instance.
     *
     * @param <T> Type to be returned.
     * @param type Type class to be returned.
     * @param asURIs One or more URIs to be dynamically loaded.
     * @param sFullClassName Full class name.
     * @return A new instance for passed URI.
     * @throws ClassNotFoundException
     * @throws InstantiationException
     * @throws java.lang.NoSuchMethodException
     * @throws java.lang.IllegalAccessException
     * @throws java.net.MalformedURLException
     * @throws java.net.URISyntaxException
     * @throws java.lang.reflect.InvocationTargetException
     */
    public static <T> T newInstance( Class<T> type, String sFullClassName, String... asURIs )
                        throws ClassNotFoundException, InstantiationException, NoSuchMethodException, IllegalAccessException,
                               MalformedURLException, URISyntaxException, IOException, IllegalArgumentException, InvocationTargetException
    {
        return newInstance( type, sFullClassName, asURIs, (Class[]) null, (Object[]) null );
    }

    /**
     * Dynamically creates an new instance.
     * <p>
     * Only URIs (JARs) that are not already loaded has to be included here.
     *
     * @param <T> Type to be returned.
     * @param type Type class to be returned.
     * @param asURIs One or more URIs to be dynamically loaded.
     * @param sFullClassName Full class name.
     * @param formalArgs Constructor formal parameters or null to use the zero argument constructor.
     * @param realArgs Constructor real parameters or null to use the zero argument constructor.
     * @return A new instance for passed URI.
     * @throws ClassNotFoundException
     * @throws InstantiationException
     * @throws java.lang.NoSuchMethodException
     * @throws java.lang.IllegalAccessException
     * @throws java.net.MalformedURLException
     * @throws java.net.URISyntaxException
     * @throws java.lang.reflect.InvocationTargetException
     */
    public static <T> T newInstance( Class<T> type, String sFullClassName, String[] asURIs, Class[] formalArgs, Object... realArgs )
                        throws ClassNotFoundException, InstantiationException, NoSuchMethodException, IllegalAccessException,
                               MalformedURLException, URISyntaxException, IOException, IllegalArgumentException, InvocationTargetException
    {
        assert type           != null;
        assert sFullClassName != null;

        if( asURIs != null )
        {
            for( String sURI : asURIs )
                UtilSys.addToClassPath( sURI );
        }

        Class       clazz    = Class.forName( sFullClassName );
        Constructor cntor    = getConstructor( clazz, formalArgs );
        Object      instance = (realArgs == null) ? cntor.newInstance() : cntor.newInstance( realArgs );

        if( ! type.isAssignableFrom( instance.getClass() ) )
        {
            throw new InstantiationException( "Can not cast '"+ sFullClassName +"' to '"+ clazz.getName() +'\'' );
        }

        return type.cast( instance );
    }

    /**
     * Use reflection to change value of any instance field.
     * @param classInstance An Object instance.
     * @param fieldName The name of a field in the class instantiated by classInstancee
     * @param newValue The value you want the field to be set to.
     * @throws java.lang.IllegalAccessException
     */
    public static void setFiledValue( final Object classInstance, final String fieldName, final Object newValue )
                  throws IllegalAccessException
    {
        final Field field = getField( classInstance.getClass(), fieldName );

        if( field != null )
        {
            field.setAccessible( true );
            field.set(classInstance, newValue);
        }
        else
        {
            throw new IllegalAccessException( classInstance.getClass().getSimpleName() +':'+ fieldName +" not found" );
        }
    }

    public static Object invoke( Object obj, String sMethod, Object... param )
    {
        Method method = getMethod( obj.getClass(), sMethod, toItsClass( param, false ) );

        if( method == null )
        {
            throw new IllegalArgumentException( "Method '"+ sMethod +"' not found.");
        }

        return invoke( obj, method, param );
    }

    /**
     *
     *
     * @param obj
     * @param method
     * @param param
     * @return
     */
    public static Object invoke( Object obj, Method method, Object... param )
    {
        assert obj    != null;
        assert method != null;

        method.setAccessible( true );

        // When this method is invoked passing an array as its 3rd parameter,
        // the array has to be wrapped into another array. e.g.: passing
        // Object[] -> [ true, 12, "string" ], will be received as one array
        // with only one item, being this item the previous 3 items array:
        // Object[] [ [ true, 12, "string" ] ]
        // Following IF cheks if this is the case.
        if( (param != null)                              &&
            (param.length != method.getParameterCount()) &&
            (param.length == 1)                          &&
            (param[0].getClass().isArray())              &&
            (((Object[]) param[0]).length == method.getParameterCount()) )
        {
            param = (Object[]) param[0];
        }

        // e.g.: params == [12, true, "str" ], but received method signature is Object[]
        if( (param != null)                   &&
            (param.length > 1)                &&
            (method.getParameterCount() == 1) &&
            (method.getParameterTypes()[0].getClass().isArray()) )
        {
            param = (Object[]) param[0];
        }

        try
        {
            return method.invoke( obj, param );
        }
        catch( IllegalAccessException | IllegalArgumentException | InvocationTargetException exc )
        {
            throw new MingleException( "Error invoking method '"+ method.getName() +'\'' );
        }
    }

    /**
     * Returns the class that invoked this method 'nSkips' stack above.
     *
     * @param nSkips The amount of jumps in the stack.
     * @return The class that invoked this method 'nSkips' stack above.
     */
    public static Class<?> getCallerClass( int nSkips )
    {
        return StackWalker.getInstance( StackWalker.Option.RETAIN_CLASS_REFERENCE )
                .walk( frames -> frames
                .skip( nSkips )         // How many methos to skip including this one
                .findFirst() )
                .map( frame -> frame.getDeclaringClass() )
                .orElse( null );
    }

    /**
 * Returns the name of the method that invoked this method 'nSkips' stack above.
 *
 * @param nSkips The amount of jumps in the stack.
 * @return The name of the method that invoked this method 'nSkips' stack above.
 */
    public static String getCallerMethodName( int nSkips )
    {
        return StackWalker.getInstance()
                .walk( frames -> frames
                .skip( nSkips )         // How many methods to skip including this one
                .findFirst() )
                .map( StackWalker.StackFrame::getMethodName )
                .orElse( null );
    }

    //------------------------------------------------------------------------//
    // PRIVATE SCOPE

    private UtilReflect() {}  // Avoid creation of instances of this class

    /**
     * Returns the array of formal parameters (method declaration) base on received parameters.
     *
     * @param aoParam Current received parameters.
     * @param bUseVarArgs When true, if all arguments are of same type, lets say String,
     *                    returned signature will be: method( String[] ) (AKA as ( String... ) )
     *                    instead of this (in the case there are lets say 3 Strings): method( String, String, String )
     * @return The array of formal parameters (method declaration) base on received parameters.
     */
    private static Class[] toItsClass( Object[] aoParam, boolean bUseVarArgs )
    {
        if( (aoParam != null) && (aoParam.length > 0) )
        {
            if( bUseVarArgs )
            {
                Class clazz = aoParam[0].getClass();
                int   n     = 1;

                for( ; n < aoParam.length; n++ )
                {
                    if( clazz != aoParam[n].getClass() ) break;
                }

                     if( n < aoParam.length     ) clazz = Object[].class;      // Not all the args are of the same type: only Object[] can be used
                else if( clazz == String.class  ) clazz = String[].class;
                else if( clazz == Float.class   ) clazz = Float[].class;
                else if( clazz == Integer.class ) clazz = Integer[].class;
                else if( clazz == Boolean.class ) clazz = Boolean[].class;

                return new Class[] { clazz };
            }
            else
            {
                Class[] aClass = new Class[aoParam.length];

                for( int n = 0; n < aoParam.length; n++ )
                    aClass[n] = ((aoParam[n] == null) ? null : aoParam[n].getClass());

                return aClass;
            }
        }

        return null;
    }

    private static Constructor getConstructor( Class clazz, Class[] formalArgs ) throws NoSuchMethodException
    {
        try
        {
            return (formalArgs == null) ? clazz.getConstructor()
                                        : clazz.getConstructor( formalArgs );
        }
        catch( NoSuchMethodException nsme )
        {
            Constructor cons = (formalArgs == null) ? clazz.getDeclaredConstructor()
                                                    : clazz.getDeclaredConstructor( formalArgs );
            cons.setAccessible( true );

            return cons;
        }
    }

////    /**
////     * Searchs for a method in a flexible way.
////     *
////     * @param obj
////     * @param sMethod
////     * @param aoTypes
////     * @return
////     */
////    private static Method _getMethodFlex_( Object obj, String sMethod, Class[] aoTypes )
////    {
////        assert obj != null;
////
////        int   nParams = (aoTypes == null) ? 0 : aoTypes.length;
////        Class clazz   = obj.getClass();
////
////        while( clazz != null )       // 1st search in object's class: if not found, search in object's superclass until root class
////        {
////            // 1st try with methods with same number of args and same signature (args type)
////            for( Method method : getMethodsNamed( sMethod, clazz ) )
////            {
////                if( nParams == method.getParameterCount() )
////                {
////                    if( nParams == 0 )
////                        return method;
////
////                    Class<?>[] aoMethodTypes = method.getParameterTypes();
////
////                    if( Arrays.equals( aoTypes, aoMethodTypes ) )
////                        return method;
////                }
////            }
////
////            // If nothing was found, try with same number of args but this time it is enough with assignable args
////            for( Method method : getMethodsNamed( sMethod, clazz ) )
////            {
////                if( nParams == method.getParameterCount() )
////                {
////                    Class<?>[] aoMethodTypes = method.getParameterTypes();
////
////                    int n = 0;
////
////                    for( ; n < aoMethodTypes.length; n++ )
////                    {
////                        if( ! aoMethodTypes[n].isAssignableFrom( aoTypes[n] ) )
////                            break;
////                    }
////
////                    if( n == aoMethodTypes.length )     // All params are assignable
////                        return method;
////                }
////            }
////
////            clazz = clazz.getSuperclass();
////        }
////
////        return null;
////    }
}