
package com.peyrona.mingle.candi.javac;

import com.peyrona.mingle.lang.MingleException;
import com.peyrona.mingle.lang.interfaces.exen.IRuntime;
import com.peyrona.mingle.lang.japi.UtilReflect;
import java.lang.reflect.Method;

/**
 * This class evaluates code fragments.
 *
 * @author Francisco José Morero Peyrona
 *
 * Official web site at: <a href="https://github.com/peyrona/mingle">https://github.com/peyrona/mingle</a>
 */
final class InMemoryExecutor
{
    static void execute( byte[] compiled, String className, String methodName, IRuntime rt )
    {
        try
        {
            ByteArrayClassLoader bacl  = new ByteArrayClassLoader( compiled );
            Class<?>             clazz = bacl.findClass( className );

            if( methodName == null )
                methodName = className;

            Object instance = clazz.getDeclaredConstructor().newInstance();
            Method method   = UtilReflect.getMethod( clazz, methodName, (Class<?>[]) null );   // 1st try to find the method with no parameters

            if( method != null )
            {
                method.invoke( instance, new Object[] {} );
            }
            else                                                                               // If not found...
            {
                method = UtilReflect.getMethod( clazz, methodName, IRuntime.class );           // Try to find it receiving IRuntime parameter

                if( method == null )
                    throw new MingleException( "Error: method not found '"+ methodName +'\'' );

                method.invoke( instance, rt );
            }
        }
        catch( Throwable thr )     // Throwable needed
        {
            String msg = "Error executing Java compiled code";

            if( (className != null) && (methodName != null) )
                msg += ". Method "+ className +':'+ methodName +". Cause: ";
            else
                msg += ": ";

            throw new MingleException( msg+ thr.getMessage(), thr );
        }
    }

    //------------------------------------------------------------------------//
    // Inner Class
    // ClassLoader to load a class form a byte[]
    //------------------------------------------------------------------------//
    private static final class ByteArrayClassLoader extends ClassLoader
    {
        byte[] bytes;

        public ByteArrayClassLoader( byte[] bytes )
        {
            this.bytes = bytes;
        }

        @Override
        public Class<?> findClass( String className )
        {
            return defineClass( className, bytes, 0, bytes.length );
        }
    }
}