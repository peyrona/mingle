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
package com.peyrona.mingle.cil.libraries;

import com.peyrona.mingle.cil.Command;
import com.peyrona.mingle.lang.interfaces.ICandi;
import com.peyrona.mingle.lang.interfaces.ILogger;
import com.peyrona.mingle.lang.interfaces.commands.ILibrary;
import com.peyrona.mingle.lang.interfaces.exen.IRuntime;
import com.peyrona.mingle.lang.japi.UtilIO;
import com.peyrona.mingle.lang.japi.UtilStr;
import com.peyrona.mingle.lang.xpreval.functions.StdXprFns;
import java.io.IOException;
import java.lang.reflect.Method;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;
import java.util.Map;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

/**
 * Runtime implementation of the Une LIBRARY command.
 * <p>
 * A LIBRARY command exposes an external function collection (Java JAR, Python module,
 * JavaScript file, or any other GraalVM-supported language) so that its public functions
 * are callable directly in WHEN/THEN/CONFIG expressions using the colon operator:
 * {@code LibraryName:functionName(args)}.
 * <p>
 * <b>Java libraries</b>: the class is located by matching the library name (case-insensitive)
 * against the simple names of all classes in the JAR — no CLASS clause is required.
 * If a {@code public static void init(java.util.Map)} method is found on the loaded class,
 * it is called once with the CONFIG values declared in the LIBRARY command.
 * <p>
 * <b>Script-based libraries</b> (Python, JavaScript, Ruby, …): the FROM clause points to a
 * source file. The file is loaded into a persistent GraalVM Polyglot context via the
 * {@link ICandi.ILanguage} runtime configured in {@code config.json}. All top-level
 * functions defined in the module become callable immediately after loading. GraalVM JARs
 * must be on the application classpath; see {@link com.peyrona.mingle.candi.graalvm.GraalVmRT}
 * for the list of required JARs.
 *
 * @author Francisco José Morero Peyrona
 *
 * Official web site at: <a href="https://github.com/peyrona/mingle">https://github.com/peyrona/mingle</a>
 */
public final class      Library
             extends    Command
             implements ILibrary
{
    // Package-scope fields accessed by LibraryBuilder
    final String              langName;
    final String[]            asFrom;
    final Map<String,Object>  config;

    /**
     * Language runtime used for non-Java libraries (Python, JavaScript, …).
     * {@code null} for Java libraries (which use URLClassLoader + reflection instead).
     * Set during {@link #start} and cleared on {@link #stop}.
     */
    private ICandi.ILanguage  langMgr;

    //------------------------------------------------------------------------//
    // PACKAGE SCOPE CONSTRUCTOR

    Library( String name, String language, String[] from, Map<String,Object> config )
    {
        super( name );

        this.langName = language;
        this.asFrom   = from;
        this.config   = (config != null) ? Collections.unmodifiableMap( config ) : Collections.emptyMap();
    }

    //------------------------------------------------------------------------//

    @Override
    public String getLanguage()
    {
        return langName;
    }

    @Override
    public String[] getFrom()
    {
        return asFrom;
    }

    @Override
    public Map<String,Object> getConfig()
    {
        return config;
    }

    @Override
    public void start( IRuntime runtime )
    {
        if( isStarted() )
            return;

        super.start( runtime );

        if( ! runtime.getFromConfig( "exen", "allow_native_code", true ) )
        {
            runtime.log( ILogger.Level.WARNING,
                         "Library '"+ name() +"' (using '"+ langName +"') is not allowed: 'allow_native_code' flag is 'false'" );
            return;
        }

        if( "java".equalsIgnoreCase( langName ) )  loadJavaLibrary(   runtime );
        else                                       loadScriptLibrary( runtime );
    }

    @Override
    public void stop()
    {
        StdXprFns.unregisterLibrary( name() );

        langMgr = null;

        super.stop();
    }

    @Override
    public String toString()
    {
        return UtilStr.toString( this );
    }

    //------------------------------------------------------------------------//
    // PRIVATE INTERFACE

    /**
     * Loads the Java JAR(s) from the FROM clause, locates the matching class by
     * simple-name convention, calls {@code init(Map)} if present, and registers
     * the class in the expression evaluator.
     *
     * @param runtime The runtime for path expansion and logging.
     */
    private void loadJavaLibrary( IRuntime runtime )
    {
        List<URI> lstURIs;

        try
        {
            lstURIs = UtilIO.expandPath( asFrom );
        }
        catch( IOException | URISyntaxException ex )
        {
            runtime.exit( 0, 1, "Library '"+ name() +"': malformed URI(s): "+ java.util.Arrays.toString( asFrom ) +"  Cause: "+ ex.getMessage() );
            return;
        }

        java.net.URL[] aURLs = lstURIs.stream()
                                      .map( uri -> { try { return uri.toURL(); }
                                                     catch( java.net.MalformedURLException e ) { throw new RuntimeException( e ); } } )
                                      .toArray( java.net.URL[]::new );

        try( URLClassLoader loader = new URLClassLoader( aURLs, getClass().getClassLoader() ) )
        {
            List<String> matches = new ArrayList<>();

            // Scan all JARs for classes whose simple name matches the library name
            for( URI uri : lstURIs )
            {
                String path = uri.getPath();

                if( path == null || ! path.endsWith( ".jar" ) )
                    continue;

                try( JarFile jar = new JarFile( path ) )
                {
                    Enumeration<JarEntry> entries = jar.entries();

                    while( entries.hasMoreElements() )
                    {
                        JarEntry entry = entries.nextElement();
                        String   eName = entry.getName();

                        if( ! eName.endsWith( ".class" ) || eName.contains( "$" ) )
                            continue;    // Skip non-class and inner class entries

                        // Convert JAR path to fully-qualified class name
                        String fqName    = eName.replace( '/', '.' ).replace( ".class", "" );
                        int    lastDot   = fqName.lastIndexOf( '.' );
                        String simpleName = (lastDot >= 0) ? fqName.substring( lastDot + 1 ) : fqName;

                        if( simpleName.equalsIgnoreCase( name() ) )
                            matches.add( fqName );
                    }
                }
                catch( IOException ex )
                {
                    runtime.exit( 0, 1, "Library '"+ name() +"': cannot read JAR '"+ path +"': "+ ex.getMessage() );
                    return;
                }
            }

            if( matches.isEmpty() )
            {
                runtime.exit( 0, 1, "Library '"+ name() +"': no class with simple name '"+ name() +"' found in "+ java.util.Arrays.toString( asFrom ) );
                return;
            }

            if( matches.size() > 1 )
            {
                runtime.exit( 0, 1, "Library '"+ name() +"': ambiguous — multiple classes match name '"+ name() +"': "+ matches );
                return;
            }

            Class<?> clazz = loader.loadClass( matches.get(0) );

            callInitIfPresent( clazz, runtime );

            StdXprFns.registerLibrary( name(), clazz );
        }
        catch( IOException ex )
        {
            runtime.exit( 0, 1, "Library '"+ name() +"': I/O error loading JAR(s): "+ ex.getMessage() );
        }
        catch( ClassNotFoundException ex )
        {
            runtime.exit( 0, 1, "Library '"+ name() +"': class not found after scan: "+ ex.getMessage() );
        }
    }

    /**
     * Loads a script-based library (Python, JavaScript, Ruby, …) using the GraalVM Polyglot
     * runtime registered for the language in {@code config.json}.
     * <p>
     * The FROM clause must point to a single source file ({@code .py}, {@code .js}, etc.).
     * The file is validated via {@link ICandi.ILanguage#prepare(List, String)} and then
     * loaded into a persistent GraalVM {@link org.graalvm.polyglot.Context} via
     * {@link ICandi.ILanguage#bind}. After loading, all top-level functions in the module
     * are callable via {@link com.peyrona.mingle.lang.xpreval.functions.StdXprFns#invokeLibrary}.
     * <p>
     * If the required GraalVM language JARs are absent, {@code prepare()} returns an error
     * and this method exits after logging the problem — no {@link NullPointerException}
     * or cryptic exception is propagated.
     *
     * @param runtime The runtime for language-builder access and error reporting.
     */
    private void loadScriptLibrary( IRuntime runtime )
    {
        langMgr = runtime.newLanguageBuilder().build( langName );

        if( langMgr == null )
        {
            runtime.exit( 0, 1, "Library '"+ name() +"': no language runtime registered for '"
                               + langName +"'. Check the 'languages' entry in config.json." );
            return;
        }

        List<URI> lstURIs;

        try
        {
            lstURIs = UtilIO.expandPath( asFrom );
        }
        catch( Exception ex )
        {
            runtime.exit( 0, 1, "Library '"+ name() +"': malformed URI(s): "
                               + java.util.Arrays.toString( asFrom )
                               + "  Cause: "+ ex.getMessage() );
            return;
        }

        ICandi.IPrepared prepared = langMgr.prepare( lstURIs, null );

        if( prepared.getErrors().length > 0 )
        {
            ICandi.IError err = prepared.getErrors()[0];
            runtime.exit( 0, 1, "Library '"+ name() +"': "+ err.message() );
            return;
        }

        langMgr.bind( name(), prepared );
        langMgr.configure( name(), config );

        StdXprFns.registerScriptLibrary( name(), langMgr );

        runtime.log( ILogger.Level.INFO,
                     "Library '"+ name() +"' ("+ langName +") loaded successfully." );
    }

    /**
     * Calls {@code public static void init(java.util.Map)} on the library class if it exists.
     *
     * @param clazz   The loaded library class.
     * @param runtime The runtime for logging.
     */
    private void callInitIfPresent( Class<?> clazz, IRuntime runtime )
    {
        try
        {
            Method initMethod = clazz.getMethod( "init", Map.class );

            initMethod.invoke( null, config );
        }
        catch( NoSuchMethodException ex )
        {
            // init(Map) is optional — nothing to do
        }
        catch( Exception ex )
        {
            runtime.log( ILogger.Level.WARNING,
                         "Library '"+ name() +"': error calling init(Map): "+ ex.getMessage() );
        }
    }
}
