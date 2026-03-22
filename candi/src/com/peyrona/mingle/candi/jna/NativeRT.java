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
package com.peyrona.mingle.candi.jna;

import com.peyrona.mingle.candi.Prepared;
import com.peyrona.mingle.lang.MingleException;
import com.peyrona.mingle.lang.interfaces.ICandi;
import com.peyrona.mingle.lang.interfaces.IController;
import com.peyrona.mingle.lang.interfaces.exen.IRuntime;
import java.io.File;
import java.net.URI;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import com.sun.jna.Function;
import com.sun.jna.NativeLibrary;

/**
 * {@link ICandi.ILanguage} implementation that loads native shared libraries
 * ({@code .so} / {@code .dll} / {@code .dylib}) and dispatches function calls
 * via <a href="https://github.com/java-native-access/jna">JNA</a> (Java Native Access).
 * <p>
 * This runtime enables the LIBRARY command to expose functions from C, Rust, or any
 * other language that produces a standard shared library, without requiring JNI wrappers
 * or any changes to the native code:
 * <pre>
 * LIBRARY MathLib
 *    LANGUAGE C
 *    FROM     "file:///usr/lib/libm.so.6"
 *    CONFIG
 *       returns.floor = double
 *
 * WHEN temperature {@literal >} 50
 * THEN setpoint = MathLib:floor( temperature )
 * </pre>
 * <p>
 * <b>Return-type resolution</b> — JNA needs to know the native return type at call time.
 * Declare it in the LIBRARY CONFIG section using {@code returns.<functionName> = <type>}
 * where {@code <type>} is one of: {@code double} (default), {@code float}, {@code int},
 * {@code long}, {@code string}, {@code void}.  Unspecified functions default to
 * {@code double}, which covers the vast majority of numeric IoT use cases.
 * <p>
 * <b>Argument types</b> — Une numbers are always {@code Double}; JNA maps them to C
 * {@code double} parameters automatically.  {@code String} arguments map to {@code const
 * char*}.  {@code Boolean} is converted to C convention ({@code 0}/{@code 1} int).
 * <p>
 * <b>Required JAR</b>: {@code jna-5.14.0.jar} from Maven Central; add a {@code download}
 * entry in {@code config.json} so Mingle fetches it automatically on first run.
 * <p>
 * <b>Security</b>: native code is gated by the {@code exen.allow_native_code} flag in
 * {@code config.json} (checked by {@code Library.start()} before this runtime is invoked).
 * <p>
 * <b>Thread safety</b>: {@link #bind} and {@link #configure} use a {@link ConcurrentHashMap}
 * keyed by {@code sInvokerUID}, so multiple LIBRARY commands with {@code LANGUAGE C} share
 * the same {@code NativeRT} instance safely.
 *
 * @author Francisco José Morero Peyrona
 *
 * Official web site at: <a href="https://github.com/peyrona/mingle">https://github.com/peyrona/mingle</a>
 */
public final class NativeRT implements ICandi.ILanguage
{
    private static final String PREFIX_RETURNS = "returns.";

    /** Maps bindId → loaded {@link NativeLibrary}. */
    private final ConcurrentHashMap<String,NativeLibrary>       libraries   = new ConcurrentHashMap<>();

    /** Maps bindId → (lowerCaseFunctionName → JNA return-type Class). */
    private final ConcurrentHashMap<String,Map<String,Class<?>>> returnTypes = new ConcurrentHashMap<>();

    //------------------------------------------------------------------------//

    /**
     * Not supported for native libraries — a FROM clause pointing to the shared library
     * file is required.
     *
     * @return A {@link ICandi.IPrepared} carrying a descriptive error.
     */
    @Override
    public ICandi.IPrepared prepare( String sSource, String call )
    {
        return new Prepared().addError( "Native libraries require a FROM clause pointing to a .so / .dll / .dylib file; "+
                                        "inline source code is not supported." );
    }

    /**
     * Validates the shared library file referenced by the first URI in {@code lstURIs}.
     * <p>
     * Only the first URI is used; only {@code file://} URIs are accepted.  The method
     * does <em>not</em> load the library — that happens in {@link #bind}.
     *
     * @param lstURIs URIs from the LIBRARY FROM clause; only the first one is used.
     * @param call    Ignored (native libraries have no entry-point concept).
     * @return A {@link ICandi.IPrepared} whose {@link ICandi.IPrepared#getCode()} holds
     *         the absolute file-system path, or a {@link ICandi.IPrepared} with errors.
     */
    @Override
    public ICandi.IPrepared prepare( List<URI> lstURIs, String call )
    {
        if( lstURIs == null || lstURIs.isEmpty() )
            return new Prepared().addError( "FROM clause is required for native libraries — provide a file:// URI to the shared library." );

        URI uri = lstURIs.get( 0 );

        if( !"file".equalsIgnoreCase( uri.getScheme() ) )
            return new Prepared().addError( "Native libraries require a file:// URI in the FROM clause; got scheme: '" + uri.getScheme() + "'" );

        File libFile = new File( uri );

        if( !libFile.exists() )
            return new Prepared().addError( "Native library file not found: " + libFile.getAbsolutePath() );

        return new Prepared( libFile.getAbsolutePath() );
    }

    /**
     * Loads the native shared library whose path is stored in {@code prepared.getCode()}.
     *
     * @param sInvokerUID An Unique ID, e.g. the LIBRARY name.
     * @param prepared    The result of {@link #prepare(List, String)} whose code is the file path.
     * @throws MingleException If JNA cannot load the library.
     */
    @Override
    public void bind( String sInvokerUID, ICandi.IPrepared prepared )
    {
        String nativePath = prepared.getCode();

        try
        {
            libraries.put( sInvokerUID, NativeLibrary.getInstance( nativePath ) );
        }
        catch( UnsatisfiedLinkError ex )
        {
            throw new MingleException( "Cannot load native library '"+ nativePath +"': "+ ex.getMessage(), ex );
        }
    }

    /**
     * Processes the LIBRARY CONFIG entries to build a per-library return-type map.
     * <p>
     * Recognised keys: {@code returns.<functionName>} with values {@code double} (default),
     * {@code float}, {@code int} / {@code integer}, {@code long}, {@code string},
     * {@code void}.  All other CONFIG keys are silently ignored.
     *
     * @param sBindId An Unique ID identifying the prepared module (e.g. the LIBRARY name).
     * @param config  The CONFIG map from the LIBRARY command.
     */
    @Override
    public void configure( String sBindId, Map<String,Object> config )
    {
        if( config == null || config.isEmpty() )
            return;

        Map<String,Class<?>> types = new HashMap<>();

        for( Map.Entry<String,Object> entry : config.entrySet() )
        {
            String key = entry.getKey();

            if( !key.startsWith( PREFIX_RETURNS ) )
                continue;

            String   fnName = key.substring( PREFIX_RETURNS.length() ).toLowerCase();
            Class<?> type   = parseReturnType( String.valueOf( entry.getValue() ) );

            types.put( fnName, type );
        }

        if( !types.isEmpty() )
            returnTypes.put( sBindId, types );
    }

    /**
     * Invokes the named function in the native shared library identified by {@code sInvokerUID}.
     *
     * @param sInvokerUID An Unique ID identifying the prepared module (e.g. the LIBRARY name).
     * @param funcName    The exported C/Rust function name.
     * @param args        Arguments to pass; {@code Double} maps to C {@code double},
     *                    {@code String} maps to {@code const char*},
     *                    {@code Boolean} is converted to {@code 0}/{@code 1}.
     * @return The function result as a {@code Double} (for numeric types), {@code String},
     *         or {@code null} (for {@code void} functions).
     * @throws MingleException If the library or function is not found, or invocation fails.
     */
    @Override
    public Object invokeFunction( String sInvokerUID, String funcName, Object... args ) throws Exception
    {
        String uid = sInvokerUID.toLowerCase();   // normalise: bind/configure store keys as lowercase

        NativeLibrary lib = libraries.get( uid );

        if( lib == null )
            throw new MingleException( "Native library '"+ sInvokerUID +"' is not loaded. "
                                     + "Ensure the LIBRARY command appears before any rules." );

        Function fn;

        try
        {
            fn = lib.getFunction( funcName );
        }
        catch( UnsatisfiedLinkError ex )
        {
            throw new MingleException( "Function '"+ funcName +"' not found in native library '"+ sInvokerUID +"'." );
        }

        Class<?> returnType = resolveReturnType( uid, funcName );
        Object[] nativeArgs = marshallArgs( args );

        Object result = fn.invoke( returnType, nativeArgs );

        return marshallResult( result );
    }

    /**
     * Not supported — native shared libraries cannot be used as SCRIPT commands.
     *
     * @throws UnsupportedOperationException Always.
     */
    @Override
    public void execute( String sInvokerUID, IRuntime rt ) throws Exception
    {
        throw new UnsupportedOperationException( "Native shared libraries cannot be used as SCRIPT commands." );
    }

    /**
     * Not supported — native shared libraries cannot be used as DRIVER commands.
     *
     * @throws UnsupportedOperationException Always.
     */
    @Override
    public IController newController( String sInvokerUID ) throws Exception
    {
        throw new UnsupportedOperationException( "Native shared libraries cannot be used as DRIVER commands." );
    }

    //------------------------------------------------------------------------//
    // PRIVATE INTERFACE

    /**
     * Returns the JNA return-type for {@code funcName} in library {@code bindId}.
     * Checks the map populated by {@link #configure}; defaults to {@code double.class}.
     */
    private Class<?> resolveReturnType( String bindId, String funcName )
    {
        Map<String,Class<?>> types = returnTypes.get( bindId );

        if( types == null )
            return double.class;

        Class<?> type = types.get( funcName.toLowerCase() );

        return (type != null) ? type : double.class;
    }

    /**
     * Parses a {@code returns.*} CONFIG value to a JNA-compatible return-type class.
     * <p>
     * Recognised values (case-insensitive):
     * <ul>
     *   <li>{@code double} — C {@code double} (default when value is unrecognised)</li>
     *   <li>{@code float} — C {@code float}</li>
     *   <li>{@code int} / {@code integer} — C {@code int}</li>
     *   <li>{@code long} — C {@code long}</li>
     *   <li>{@code string} — C {@code const char*}</li>
     *   <li>{@code void} — function returns nothing</li>
     * </ul>
     */
    private static Class<?> parseReturnType( String value )
    {
        if( value == null )
            return double.class;

        switch( value.trim().toLowerCase() )
        {
            case "float":                   return float.class;
            case "int":   case "integer":   return int.class;
            case "long":                    return long.class;
            case "string":                  return String.class;
            case "void":                    return void.class;
            case "double": default:         return double.class;
        }
    }

    /**
     * Converts Une / Java arguments to types suitable for JNA native calls.
     * <p>
     * {@code Boolean} values are mapped to {@code 0}/{@code 1} integers following
     * C convention.  All other types are passed through unchanged.
     */
    private static Object[] marshallArgs( Object[] args )
    {
        if( args == null || args.length == 0 )
            return args;

        Object[] out = new Object[ args.length ];

        for( int n = 0; n < args.length; n++ )
        {
            Object a = args[ n ];
            out[ n ] = (a instanceof Boolean) ? ((Boolean) a ? 1 : 0) : a;
        }

        return out;
    }

    /**
     * Widens all JNA numeric results to {@code Double} — Une's internal numeric type.
     * {@code String} and {@code null} are passed through unchanged.
     */
    private static Object marshallResult( Object result )
    {
        if( result instanceof Number && !(result instanceof Double) )
            return ((Number) result).doubleValue();

        return result;
    }
}
