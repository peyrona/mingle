/*
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.peyrona.mingle.lang.japi;

import com.peyrona.mingle.lang.MingleException;
import com.peyrona.mingle.lang.interfaces.ICandi;
import com.peyrona.mingle.lang.interfaces.ILogger;
import com.peyrona.mingle.lang.xpreval.functions.date;
import com.peyrona.mingle.lang.xpreval.functions.list;
import com.peyrona.mingle.lang.xpreval.functions.pair;
import com.peyrona.mingle.lang.xpreval.functions.time;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 *
 * @author Francisco José Morero Peyrona
 * @see <a href="https://github.com/peyrona/mingle">https://github.com/peyrona/mingle</a>
 */
public final class UtilLib
{
    // LIBRARY command registry: maps library name (lower-case) → loaded class
    // Phase 1 (transpile-time): name registered with Void.class sentinel so the tokenizer accepts LibraryName:fn()
    // Phase 2 (runtime): Void.class replaced by the real class so invokeLibrary() can dispatch via reflection (Java path)
    private static final ConcurrentHashMap<String,Class<?>>           libraryRegistry    = new ConcurrentHashMap<>();

    // Script-based library registry: maps library name (lower-case) → ILanguage runtime
    // Used by the GraalVM path (Python, JavaScript, Ruby …): invokeLibrary() delegates to
    // ILanguage.invokeFunction() instead of Java reflection when an entry exists here.
    private static final ConcurrentHashMap<String,ICandi.ILanguage>   scriptLangRegistry = new ConcurrentHashMap<>();

    private static final Class<?>[] EMPTY_CLASS_ARRAY  = new Class<?>[0];
    private static final Class<?>[] ONE_CLASS_ARRAY    = { Object.class };
    private static final Class<?>[] TWO_CLASS_ARRAY    = { Object.class, Object.class };
    private static final Class<?>[] THREE_CLASS_ARRAY  = { Object.class, Object.class, Object.class };

    //------------------------------------------------------------------------//
    // LIBRARY REGISTRY

    /**
     * Registers a library name at transpile time so the expression tokenizer accepts
     * {@code LibraryName:functionName()} syntax without a class yet being loaded.
     *
     * @param sName Library name as declared in the LIBRARY Une command (case-insensitive).
     */
    public static void registerLibraryName( String sName )
    {
        if( UtilStr.isNotEmpty( sName ) )
        {
            libraryRegistry.putIfAbsent( sName.toLowerCase(), Void.class );
        }
    }

    /**
     * Registers a library name together with its loaded class at runtime.
     * Called by LibraryManager after loading the library JAR/module.
     *
     * @param sName  Library name as declared in the LIBRARY Une command (case-insensitive).
     * @param clazz  The loaded class whose public methods will be dispatched by {@link #invokeLibrary}.
     */
    public static void registerLibrary( String sName, Class<?> clazz )
    {
        if( UtilStr.isNotEmpty( sName ) && clazz != null )
        {
            libraryRegistry.put( sName.toLowerCase(), clazz );
        }
    }

    /**
     * Registers a script-based library (Python, JavaScript, Ruby, C via JNA …) so that
     * {@link #invokeLibrary} can delegate function calls to the language runtime.
     * <p>
     * Called by {@link com.peyrona.mingle.cil.libraries.Library} at startup after the
     * module has been loaded via {@link ICandi.ILanguage#bind}. The entry is written to
     * {@link #scriptLangRegistry} for actual dispatch, and a {@code Void.class} sentinel is
     * also added to {@link #libraryRegistry} (if not already present) so that
     * {@link #isLibraryNamespace} returns {@code true} at runtime — even when stick.jar
     * runs as a separate JVM from the transpiler and {@link #registerLibraryName} was never
     * called in this process.
     *
     * @param sName   Library name as declared in the LIBRARY Une command (case-insensitive).
     * @param langMgr The {@link ICandi.ILanguage} runtime that has the module bound to it.
     */
    public static void registerScriptLibrary( String sName, ICandi.ILanguage langMgr )
    {
        if( UtilStr.isNotEmpty( sName ) && langMgr != null )
        {
            String key = sName.toLowerCase();
            libraryRegistry.putIfAbsent( key, Void.class ); // ensure isLibraryNamespace() returns true at runtime
            scriptLangRegistry.put( key, langMgr );
        }
    }

    /**
     * Returns true if the given name is a registered library namespace.
     * Used by the expression tokenizer and AST evaluator to distinguish library calls
     * from device variable references.
     *
     * @param sName Name to test.
     * @return true if the name was registered via {@link #registerLibraryName} or {@link #registerLibrary}.
     */
    public static boolean isLibraryNamespace( String sName )
    {
        return UtilStr.isNotEmpty( sName ) && libraryRegistry.containsKey( sName.toLowerCase() );
    }

    /**
     * Removes a library from the registry. Called when a Library command is stopped.
     *
     * @param sName Library name to remove (case-insensitive).
     */
    public static void unregisterLibrary( String sName )
    {
        if( UtilStr.isNotEmpty( sName ) )
        {
            String key = sName.toLowerCase();
            libraryRegistry.remove( key );
            scriptLangRegistry.remove( key );
            libraryInstances.entrySet().removeIf( e -> e.getKey().getSimpleName().equalsIgnoreCase( sName ) );
        }
    }

    /**
     * Invokes a function from a registered library.
     * <p>
     * Before invocation, Une extended types in {@code aoArgs} are marshalled to their Java native
     * equivalents ({@code list}→{@code java.util.List}, {@code pair}→{@code java.util.Map},
     * {@code date}→{@code java.time.LocalDate}, {@code time}→{@code java.time.LocalTime}).
     * The return value is marshalled back from Java native types to Une types using the same mapping.
     *
     * @param sLib   Library name (case-insensitive).
     * @param sFn    Function name to invoke.
     * @param aoArgs Arguments to pass (may be null or empty).
     * @return The function result, marshalled to the appropriate Une type.
     * @throws MingleException if the library is not fully loaded yet, or if the function is not found,
     *                         or if invocation fails.
     */
    public static Object invokeLibrary( String sLib, String sFn, Object... aoArgs )
    {
        Class<?> clazz = libraryRegistry.get( sLib.toLowerCase() );

        if( clazz == null )
            throw new MingleException( "Library \"" + sLib + "\" is not registered." + " Declare a LIBRARY command before using it in rules." );

        // Script-based library path (Python, JavaScript, Ruby … via GraalVM)
        if( clazz == Void.class )
        {
            ICandi.ILanguage langMgr = scriptLangRegistry.get( sLib.toLowerCase() );

            if( langMgr == null )
                throw new MingleException( "Library \"" + sLib + "\" is not loaded yet." + " Ensure the LIBRARY command started successfully." );

            Object[] marshalledArgs = marshallArgsIn( aoArgs );

            try
            {
                return marshallResultOut( langMgr.invokeFunction( sLib, sFn, marshalledArgs ) );
            }
            catch( Exception ex )
            {
                throw new MingleException( "Library \"" + sLib + "\" function \"" + sFn + "\": " + ex.getMessage(), ex );
            }
        }

        Object[]   marshalledArgs = marshallArgsIn( aoArgs );
        int        nArgs          = (marshalledArgs == null) ? 0 : marshalledArgs.length;
        Class<?>[] aParamType;

        switch( nArgs )
        {
            case 0:  aParamType = EMPTY_CLASS_ARRAY;  break;
            case 1:  aParamType = ONE_CLASS_ARRAY;    break;
            case 2:  aParamType = TWO_CLASS_ARRAY;    break;
            case 3:  aParamType = THREE_CLASS_ARRAY;  break;
            default:
                aParamType = new Class<?>[nArgs];
                Arrays.fill( aParamType, Object.class );
                break;
        }

        java.lang.reflect.Method method = UtilReflect.getMethod( clazz, sFn, aParamType );

        if( method == null )
            throw new MingleException( '"' + sFn + "\" does not exist in library \"" + sLib + "\". Usage: " + toInvocation( clazz, sFn, marshalledArgs ) );

        method.setAccessible( true );

        try
        {
            Object target = java.lang.reflect.Modifier.isStatic( method.getModifiers() )
                          ? null
                          : getOrCreateInstance( clazz );

            Object result = (marshalledArgs == null || marshalledArgs.length == 0)
                          ? method.invoke( target )
                          : method.invoke( target, marshalledArgs );

            return marshallResultOut( result );
        }
        catch( java.lang.reflect.InvocationTargetException exc )
        {
            Throwable       cause = (exc.getCause() != null) ? exc.getCause() : exc;
            MingleException me    = new MingleException( "Error executing library function " + toInvocation( clazz, sFn, marshalledArgs ), cause );
            UtilSys.getLogger().log( ILogger.Level.SEVERE, me );
            throw me;
        }
        catch( Exception exc )
        {
            MingleException me = new MingleException( "Error executing library function " + toInvocation( clazz, sFn, marshalledArgs ), exc );
            UtilSys.getLogger().log( ILogger.Level.SEVERE, me );
            throw me;
        }
    }

    //------------------------------------------------------------------------//
    // LIBRARY HELPERS (private)
    //------------------------------------------------------------------------//

    /**
     * Returns a cached instance of the given library class, creating one via the no-arg
     * constructor on first access. Used for instance (non-static) library methods.
     *
     * @param clazz Library class.
     * @return A cached instance for instance methods.
     */
    private static final ConcurrentHashMap<Class<?>,Object> libraryInstances = new ConcurrentHashMap<>();

    //------------------------------------------------------------------------//

    /**
     * Marshalls Une extended types in the argument array to their Java native equivalents so that
     * library methods written without LANG dependencies can receive standard Java types.
     * Primitives (Number, String, Boolean) pass through unchanged.
     *
     * @param aoArgs Original argument array (may be null).
     * @return A new array with marshalled values, or null if aoArgs was null or empty.
     */
    private static Object[] marshallArgsIn( Object[] aoArgs )
    {
        if( aoArgs == null || aoArgs.length == 0 )
            return null;

        Object[] result = new Object[ aoArgs.length ];

        for( int n = 0; n < aoArgs.length; n++ )
            result[n] = marshallIn( aoArgs[n] );

        return result;
    }

    private static Object marshallIn( Object o )
    {
        if( o instanceof list )
            return ((list) o).asList();

        if( o instanceof pair )
        {
            pair               p = (pair) o;
            Map<Object,Object> m = new HashMap<>();

            for( Object k : p.keys().asList() )
                m.put( k, p.get( k ) );

            return m;
        }

        if( o instanceof date ) return ((date) o).asLocalDate();
        if( o instanceof time ) return ((time) o).asLocalTime();

        return o;    // Number, String, Boolean, null → pass through
    }

    /**
     * Marshalls a library function return value from Java native types back to Une extended types.
     *
     * @param result Raw return value from the library method (may be null).
     * @return The appropriate Une type, or the original value if no mapping applies.
     */
    private static Object marshallResultOut( Object result )
    {
        if( result == null )
            return null;

        if( result instanceof java.util.List )
        {
            return new list( ((java.util.List<?>) result).toArray() );
        }
        if( result instanceof java.util.Map )
        {
            pair p = new pair();

            for( Map.Entry<?,?> e : ((java.util.Map<?,?>) result).entrySet() )
                p.put( e.getKey(), e.getValue() );

            return p;
        }
        if( result instanceof java.time.LocalDate ) return new date( result.toString() );
        if( result instanceof java.time.LocalTime ) return new time( result.toString() );
        return result;
    }

    private static Object getOrCreateInstance( Class<?> clazz )
    {
        return libraryInstances.computeIfAbsent( clazz, c ->
        {
            try
            {
                return c.getDeclaredConstructor().newInstance();
            }
            catch( Exception e )
            {
                throw new MingleException( "Library class \"" + c.getSimpleName() +
                                           "\" has no accessible no-arg constructor for instance methods.", e );
            }
        } );
    }

    private static String toInvocation( Class<?> clazz, String sFn, Object[] aoArgs )
    {
        if( UtilColls.isEmpty( aoArgs ) )
            return sFn + "()";

        if( clazz != null )
            sFn = clazz.getSimpleName() + ':' + sFn;

        StringBuilder sb = new StringBuilder( sFn.length() + 2 + (aoArgs.length * 12) );
        sb.append( sFn ).append( '(' );

        for( Object obj : aoArgs )
        {
            if( obj instanceof Number )
            {
                float fv = ((Number) obj).floatValue();
                int   iv = (int) fv;

                if( Math.abs( fv - iv ) <= 0.00001f )
                    sb.append( iv );
                else
                    sb.append( fv );
            }
            else if( obj instanceof String )
            {
                sb.append( '"' ).append( obj ).append( '"' );
            }
            else
            {
                sb.append( obj );
            }

            sb.append( ',' );
        }

        return UtilStr.removeLast( sb, 1 ).append( ')' ).toString();
    }

    //------------------------------------------------------------------------//
    // PRIVATE CONSTRUCTOR

    private UtilLib()
    {
        // To avoid instaces of this class
    }
}