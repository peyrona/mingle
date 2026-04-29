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
package com.peyrona.mingle.candi.graalvm;

import com.peyrona.mingle.candi.Prepared;
import com.peyrona.mingle.lang.MingleException;
import com.peyrona.mingle.lang.interfaces.ICandi;
import com.peyrona.mingle.lang.interfaces.IController;
import com.peyrona.mingle.lang.interfaces.exen.IRuntime;
import com.peyrona.mingle.lang.japi.UtilIO;
import com.peyrona.mingle.lang.japi.UtilStr;
import com.peyrona.mingle.lang.lexer.CodeError;
import com.peyrona.mingle.lang.xpreval.functions.date;
import com.peyrona.mingle.lang.xpreval.functions.list;
import com.peyrona.mingle.lang.xpreval.functions.pair;
import com.peyrona.mingle.lang.xpreval.functions.time;
import java.io.IOException;
import java.net.URI;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.Engine;
import org.graalvm.polyglot.HostAccess;
import org.graalvm.polyglot.PolyglotException;
import org.graalvm.polyglot.Source;
import org.graalvm.polyglot.Value;
import org.graalvm.polyglot.io.IOAccess;

/**
 * Abstract base for all GraalVM Polyglot language runtimes in Mingle.
 * <p>
 * Replaces {@code NashornRT} (JavaScript, broken on Java 15+) and {@code JythonRT}
 * (Python 2.7, unmaintained) with a single unified implementation backed by the
 * GraalVM Polyglot API. Any GraalVM-supported language (JavaScript, Python, Ruby, R …)
 * can be hosted by creating a thin concrete subclass that calls {@code super(langId)}
 * with the GraalVM language identifier (e.g. {@code "js"}, {@code "python"}, {@code "ruby"}).
 * <p>
 * <b>Deployment model:</b> GraalVM JARs are not bundled inside Mingle itself — they are
 * plain Maven-Central JARs placed in the runtime {@code lib/} directory alongside the other
 * Mingle JARs. No GraalVM JDK installation is required. On a standard JVM the language
 * runtimes operate in interpreter-only mode (adequate for infrequent IoT scripting). On a
 * GraalVM JDK the JIT compiler kicks in automatically, providing additional throughput.
 * <p>
 * <b>Required JARs (from Maven Central, GraalVM 23.0.x for Java 11 compatibility):</b>
 * <ul>
 *   <li>{@code org.graalvm.sdk:graal-sdk} — core Polyglot API (always required)</li>
 *   <li>{@code com.oracle.truffle:truffle-api} — Truffle framework (always required)</li>
 *   <li>{@code org.graalvm.js:js} — JavaScript support, ~35 MB (bundle with distribution)</li>
 *   <li>{@code org.graalvm.tools:graalpython} — Python 3 support, ~180 MB (optional download)</li>
 * </ul>
 * <p>
 * <b>Lifecycle (mirrors SCRIPT / LIBRARY usage):</b>
 * <ol>
 *   <li>{@link #prepare(String, String)} — syntax-checks the source at transpile-time.</li>
 *   <li>{@link #bind(String, ICandi.IPrepared)} — loads the module into a persistent
 *       {@link Context}; stores source for later fresh-context SCRIPT execution.</li>
 *   <li>{@link #execute(String, IRuntime)} — executes the whole script in a <em>fresh</em>
 *       {@link Context} on each invocation (stateless, safe for concurrent rule firings).</li>
 *   <li>{@link #invokeFunction(String, String, Object...)} — calls a named function inside
 *       the <em>persistent</em> {@link Context} created during {@code bind()}. Used by the
 *       LIBRARY command so the module's global state is preserved across calls.</li>
 * </ol>
 * <p>
 * <b>Thread safety:</b> Each persistent {@link Context} is protected by a per-entry lock.
 * Fresh contexts created in {@link #execute} are local to the calling thread and need no
 * synchronisation.
 *
 * @author Francisco José Morero Peyrona
 *
 * Official web site at: <a href="https://github.com/peyrona/mingle">https://github.com/peyrona/mingle</a>
 */
public abstract class GraalVmRT implements ICandi.ILanguage
{
    /**
     * GraalVM Polyglot language identifier for this runtime instance (e.g. {@code "js"},
     * {@code "python"}, {@code "ruby"}).
     */
    protected final String langId;

    /**
     * Shared GraalVM {@link Engine} for this language. All {@link Context} instances created
     * by this runtime share the same engine, enabling GraalVM to cache compiled code and
     * share internal data structures across contexts.
     * <p>
     * {@code null} when the required language JARs are not on the classpath.
     */
    private final Engine engine;

    /**
     * Per-invoker storage: maps {@code sInvokerUID} (script or library name) to a
     * {@link BindingEntry} that holds the source code, call-function name, and the
     * persistent GraalVM {@link Context}.
     */
    private final ConcurrentHashMap<String,BindingEntry> bindings = new ConcurrentHashMap<>();

    /**
     * Cache for language-availability checks keyed by {@code langId}. Populated lazily on
     * first call to {@link #isAvailable(String)} and then reused for the JVM lifetime.
     */
    private static final ConcurrentHashMap<String,Boolean> availabilityCache = new ConcurrentHashMap<>();

    //------------------------------------------------------------------------//

    /**
     * Constructs the runtime for the given GraalVM language identifier.
     * <p>
     * Attempts to build a shared {@link Engine} immediately. If the required language JAR
     * is absent the engine is set to {@code null} and the first call to {@link #prepare}
     * will return a descriptive error.
     *
     * @param langId GraalVM language identifier (e.g. {@code "js"}, {@code "python"}).
     */
    protected GraalVmRT( String langId )
    {
        this.langId = langId;
        this.engine = buildEngine( langId );
    }

    //------------------------------------------------------------------------//
    // ICandi.ILanguage INTERFACE

    /**
     * Validates the given source code at transpile-time without retaining any compiled form.
     * <p>
     * A temporary {@link Context} is created, the source is parsed for syntax errors without
     * being executed, and then the context is closed immediately. Only syntax errors are
     * surfaced here; runtime errors are reported later in {@link #execute}.
     * <p>
     * If the required GraalVM language JAR is not present on the classpath, a single error
     * entry is added to the returned {@link Prepared} with an actionable message.
     *
     * @param source The script source code (inline or read from file).
     * @param call   Name of the function to invoke after evaluating the source (SCRIPT CALL
     *               clause), or {@code null} / blank for fire-and-forget scripts.
     * @return A {@link Prepared} instance whose {@link Prepared#getErrors()} is empty on
     *         success, or contains one or more {@link CodeError} entries on failure.
     */
    @Override
    public ICandi.IPrepared prepare( String source, String call )
    {
        Prepared prepared = new Prepared( source );

        if( engine == null )
        {
            prepared.addError( "GraalVM language '"+ langId +"' is not available."
                             + " Add the required JAR(s) to lib/ and the classpath."
                             + " See GraalVmRT Javadoc for the list of required JARs." );
            return prepared.setCallName( call );
        }

        if( UtilStr.isEmpty( call ) )  call = null;

        try( Context ctx = newTempContext() )
        {
            ctx.parse( Source.create( langId, source ) );
        }
        catch( PolyglotException pe )
        {
            prepared.addError( extractError( pe ) );
            prepared.setCode( (String) null );    // Frees RAM when there are errors
        }

        return prepared.setCallName( call );
    }

    /**
     * Reads the script source from a local or remote URI and delegates to
     * {@link #prepare(String, String)}.
     * <p>
     * Only the first URI in {@code lstURIs} is used. For libraries, a single module
     * file is the expected and natural case.
     *
     * @param lstURIs Non-empty list of file or HTTP URIs; only index 0 is read.
     * @param call    Entry-function name for SCRIPT CALL clause; {@code null} for libraries.
     * @return See {@link #prepare(String, String)}.
     */
    @Override
    public ICandi.IPrepared prepare( List<URI> lstURIs, String call )
    {
        if( lstURIs == null || lstURIs.isEmpty() )
        {
            Prepared p = new Prepared();
            p.addError( "FROM clause is empty for language '"+ langId +"'" );
            return p;
        }

        try
        {
            String source = UtilIO.getAsText( lstURIs.get(0) );
            return prepare( source, call );
        }
        catch( IOException ex )
        {
            Prepared p = new Prepared();
            p.addError( "Cannot read source file '"+ lstURIs.get(0) +"': "+ ex.getMessage() );
            return p;
        }
    }

    /**
     * Loads the module into a persistent {@link Context} and stores the source code for
     * later fresh-context SCRIPT execution.
     * <p>
     * This method is called once per named invoker (script or library name) at startup.
     * The persistent context created here is used exclusively by {@link #invokeFunction};
     * {@link #execute} always creates its own disposable context.
     *
     * @param sInvokerUID The unique identifier for this binding (script or library name).
     * @param prepared    The {@link Prepared} object returned by {@link #prepare}.
     * @throws MingleException if the source cannot be evaluated into the persistent context.
     */
    @Override
    public void bind( String sInvokerUID, ICandi.IPrepared prepared )
    {
        String source   = prepared.getCode();
        String callName = prepared.getCallName();

        // Create the persistent Context (shared engine for optimisation) and eval the module into it
        Context persistentCtx = newPersistentContext();

        try
        {
            persistentCtx.eval( Source.create( langId, source ) );
        }
        catch( PolyglotException pe )
        {
            persistentCtx.close();
            throw new MingleException( "GraalVM bind failed for '"+ sInvokerUID +"': "
                                     + pe.getMessage(), pe );
        }

        bindings.put( sInvokerUID, new BindingEntry( source, callName, persistentCtx ) );
    }

    /**
     * Executes the script in a fresh, disposable {@link Context}.
     * <p>
     * A new context is created for every invocation so executions are completely isolated —
     * global state from one firing does not leak into subsequent firings. The {@code IRuntime}
     * instance is injected as the {@code _exen_rt_} global variable, mirroring the behaviour
     * of the legacy {@code NashornRT} and {@code JythonRT} runtimes.
     * <p>
     * If a CALL function name was specified in the SCRIPT command, it is invoked after the
     * source has been evaluated (giving the function access to any top-level initialisations).
     *
     * @param sInvokerUID The name under which the script was {@link #bind bound}.
     * @param rt          The runtime instance to inject as {@code _exen_rt_}.
     * @throws MingleException on any evaluation or invocation error.
     */
    @Override
    public void execute( String sInvokerUID, IRuntime rt )
    {
        BindingEntry entry = bindings.get( sInvokerUID );

        if( entry == null )
            throw new MingleException( "No binding found for '"+ sInvokerUID +"': call bind() first." );

        try( Context ctx = newTempContext() )
        {
            ctx.getBindings( langId ).putMember( "_exen_rt_", rt );

            ctx.eval( Source.create( langId, entry.source ) );

            if( UtilStr.isNotEmpty( entry.callName ) )
            {
                Value fn = ctx.getBindings( langId ).getMember( entry.callName );

                if( fn != null && fn.canExecute() )
                    fn.execute();
                else
                    throw new MingleException( "CALL function '"+ entry.callName
                                             + "' not found in '"+ sInvokerUID +"'." );
            }
        }
        catch( PolyglotException pe )
        {
            throw new MingleException( buildRuntimeMessage( pe ), pe );
        }
    }

    /**
     * Invokes a named function from a module that was previously loaded via {@link #bind}.
     * <p>
     * Used exclusively by the LIBRARY command: unlike {@link #execute}, this method uses the
     * <em>persistent</em> {@link Context} created during {@code bind()} so that the module's
     * global state (e.g. module-level variables initialised in {@code init()}) is preserved
     * across calls.
     * <p>
     * Access to the shared context is serialised through a per-entry lock to ensure thread
     * safety when multiple rules fire concurrently.
     * <p>
     * Arguments are expected to be Java-native types (primitives, {@link String},
     * {@link java.util.List}, {@link java.util.Map}, {@link LocalDate}, {@link LocalTime})
     * — callers are expected to marshall Une types before calling this method (which
     * {@link com.peyrona.mingle.lang.xpreval.functions.StdXprFns#invokeLibrary} does).
     * The return value is converted from a GraalVM {@link Value} to a Java-native or Une
     * extended type before being returned.
     *
     * @param sInvokerUID The library name (used to look up the persistent context).
     * @param funcName    The function name to invoke.
     * @param args        Arguments, already marshalled to Java-native types.
     * @return The function result as a Java-native type or Une extended type
     *         ({@link list}, {@link pair}, {@link date}, {@link time}), or {@code null}.
     * @throws Exception on any invocation error.
     */
    @Override
    public Object invokeFunction( String sInvokerUID, String funcName, Object... args ) throws Exception
    {
        BindingEntry entry = bindings.get( sInvokerUID );

        if( entry == null )
            throw new MingleException( "Library '"+ sInvokerUID +"' is not loaded."
                                     + " Ensure LIBRARY command appears before any rules." );

        synchronized( entry.lock )
        {
            Value fn = entry.context.getBindings( langId ).getMember( funcName );

            if( fn == null || !fn.canExecute() )
                throw new MingleException( "Function '"+ funcName
                                         + "' not found in library '"+ sInvokerUID +"'." );

            Value result = fn.execute( args );

            return valueToUne( result );
        }
    }

    /**
     * Not supported: GraalVM language runtimes do not create {@link IController} instances.
     *
     * @throws UnsupportedOperationException always.
     */
    @Override
    public IController newController( String sInvokerUID ) throws Exception
    {
        throw new UnsupportedOperationException( "newController() is not supported by GraalVmRT." );
    }

    //------------------------------------------------------------------------//
    // PACKAGE-SCOPE UTILITIES

    /**
     * Returns {@code true} if the GraalVM language identified by {@code langId} is available
     * on the current classpath.
     * <p>
     * The result is cached after the first check — language availability does not change
     * during a JVM run. The check works by attempting to build a temporary {@link Context};
     * if the language JARs are absent, a {@code ClassNotFoundException} or
     * {@code PolyglotException} is caught and {@code false} is returned.
     *
     * @param langId GraalVM language identifier to test.
     * @return {@code true} if the language is installed and usable, {@code false} otherwise.
     */
    static boolean isAvailable( String langId )
    {
        return availabilityCache.computeIfAbsent( langId, id ->
        {
            try( Context ctx = Context.newBuilder( id )
                                      .option( "engine.WarnInterpreterOnly", "false" )
                                      .build() )
            {
                return true;
            }
            catch( Exception ex )
            {
                return false;
            }
        });
    }

    //------------------------------------------------------------------------//
    // PRIVATE INTERFACE

    /**
     * Attempts to build a shared {@link Engine} for the given language.
     *
     * @param langId GraalVM language identifier.
     * @return The built engine, or {@code null} if the language JARs are not available.
     */
    private static Engine buildEngine( String langId )
    {
        try
        {
            return Engine.newBuilder( langId )
                         .option( "engine.WarnInterpreterOnly", "false" )
                         .build();
        }
        catch( Exception ex )
        {
            return null;    // Language not installed; errors will surface in prepare()
        }
    }

    /**
     * Creates a temporary (disposable) {@link Context} linked to the shared engine.
     * Used by {@link #prepare} for syntax checking and by {@link #execute} for script runs.
     * <p>
     * Host access is enabled so scripts can call Java APIs (e.g. {@code java.time.*}) and
     * invoke methods on injected host objects such as {@code _exen_rt_}.
     * I/O and native access are disabled for security: scripts may not read the filesystem
     * or call native libraries directly.
     *
     * @return A new, ready-to-use {@link Context}; caller must close it when done.
     */
    private Context newTempContext()
    {
        Context.Builder builder = Context.newBuilder( langId )
                                         .allowHostAccess( HostAccess.ALL )
                                         .allowHostClassLookup( className -> true )
                                         .allowIO( IOAccess.NONE )
                                         .allowNativeAccess( false );

        if( engine != null )
            builder.engine( engine );   // Share engine for code caching when available

        return builder.build();
    }

    /**
     * Creates the persistent {@link Context} used for LIBRARY invocations.
     * Identical security settings to {@link #newTempContext} but the caller is responsible
     * for the lifecycle (no auto-close — it lives until {@code stop()} is called).
     *
     * @return A new {@link Context}; caller must close it when the library is stopped.
     */
    private Context newPersistentContext()
    {
        Context.Builder builder = Context.newBuilder( langId )
                                         .allowHostAccess( HostAccess.ALL )
                                         .allowHostClassLookup( className -> true )
                                         .allowIO( IOAccess.NONE )
                                         .allowNativeAccess( false );

        if( engine != null )
            builder.engine( engine );

        return builder.build();
    }

    /**
     * Extracts a {@link CodeError} from a {@link PolyglotException}.
     * <p>
     * GraalVM {@code PolyglotException} carries precise source-location information via
     * {@link PolyglotException#getSourceLocation()}, yielding 1-based line and column
     * numbers without the fragile string parsing required by Nashorn / Jython.
     *
     * @param pe The exception to extract information from.
     * @return A {@link CodeError} with message, line, and column populated.
     */
    private static CodeError extractError( PolyglotException pe )
    {
        int    line = 0;
        int    col  = 0;
        String msg  = pe.getMessage();

        if( pe.getSourceLocation() != null )
        {
            line = pe.getSourceLocation().getStartLine();
            col  = pe.getSourceLocation().getStartColumn();
        }

        return new CodeError( msg, line, col );
    }

    /**
     * Builds a human-readable runtime-error message from a {@link PolyglotException},
     * including source location when available.
     *
     * @param pe The exception thrown during script execution.
     * @return A descriptive error string.
     */
    private String buildRuntimeMessage( PolyglotException pe )
    {
        StringBuilder sb = new StringBuilder( langId ).append( " runtime error" );

        if( pe.getSourceLocation() != null )
        {
            int line = pe.getSourceLocation().getStartLine();
            int col  = pe.getSourceLocation().getStartColumn();

            if( line > 0 ) sb.append( " at line " ).append( line );
            if( col  > 0 ) sb.append( ", column " ).append( col );
        }

        sb.append( ": " ).append( pe.getMessage() );

        return sb.toString();
    }

    /**
     * Converts a GraalVM {@link Value} to a Java-native or Une extended type.
     * <p>
     * Mapping:
     * <ul>
     *   <li>null / Polyglot null  → Java {@code null}</li>
     *   <li>String               → {@link String}</li>
     *   <li>Boolean              → {@link Boolean}</li>
     *   <li>Integer number       → {@link Long}</li>
     *   <li>Floating number      → {@link Double}</li>
     *   <li>Array (JS array, Python list) → Une {@link list}</li>
     *   <li>Object with members (JS object, Python dict) → Une {@link pair}</li>
     *   <li>Host object          → underlying Java object</li>
     *   <li>Anything else        → {@link String} via {@code toString()}</li>
     * </ul>
     *
     * @param v The GraalVM {@link Value} to convert; may be {@code null}.
     * @return The converted Java / Une value.
     */
    private Object valueToUne( Value v )
    {
        if( v == null || v.isNull() )    return null;
        if( v.isString()             )   return v.asString();
        if( v.isBoolean()            )   return v.asBoolean();

        if( v.isNumber() )
        {
            if( v.fitsInLong()   )  return v.asLong();
            if( v.fitsInDouble() )  return v.asDouble();
        }

        if( v.hasArrayElements() )
        {
            long   size    = v.getArraySize();
            Object[] items = new Object[ (int) size ];

            for( int n = 0; n < size; n++ )
                items[n] = valueToUne( v.getArrayElement(n) );

            return new list( items );
        }

        if( v.hasMembers() )
        {
            pair       p    = new pair();
            Set<String> keys = v.getMemberKeys();

            for( String key : keys )
                p.put( key, valueToUne( v.getMember(key) ) );

            return p;
        }

        if( v.isHostObject() )   return v.asHostObject();

        return v.toString();
    }

    //------------------------------------------------------------------------//
    // INNER CLASSES

    /**
     * Immutable container for all data associated with a single bound invoker (script or
     * library). Held in {@link #bindings} keyed by {@code sInvokerUID}.
     */
    private static final class BindingEntry
    {
        /** Source code retained for fresh-context SCRIPT execution. */
        final String  source;

        /** Entry-function name from the SCRIPT CALL clause; {@code null} for libraries. */
        final String  callName;

        /**
         * Persistent {@link Context} holding the loaded module state. Used exclusively
         * by {@link #invokeFunction} (LIBRARY path). Must be closed when the library stops.
         */
        final Context context;

        /**
         * Per-entry lock used to serialise concurrent {@link #invokeFunction} calls.
         * GraalVM {@link Context} is not thread-safe.
         */
        final Object  lock = new Object();

        BindingEntry( String source, String callName, Context context )
        {
            this.source   = source;
            this.callName = callName;
            this.context  = context;
        }
    }
}
