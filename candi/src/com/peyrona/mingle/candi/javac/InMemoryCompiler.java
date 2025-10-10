
package com.peyrona.mingle.candi.javac;

import com.peyrona.mingle.candi.IntermediateCodeWriter;
import com.peyrona.mingle.lang.japi.Pair;
import com.peyrona.mingle.lang.japi.UtilIO;
import com.peyrona.mingle.lang.japi.UtilStr;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import javax.tools.Diagnostic;
import javax.tools.DiagnosticCollector;
import javax.tools.FileObject;
import javax.tools.ForwardingJavaFileManager;
import javax.tools.JavaCompiler;
import javax.tools.JavaCompiler.CompilationTask;
import javax.tools.JavaFileManager.Location;
import javax.tools.JavaFileObject;
import javax.tools.JavaFileObject.Kind;
import javax.tools.SimpleJavaFileObject;
import javax.tools.StandardLocation;
import javax.tools.ToolProvider;

/**
 * A Java source code compiler that recieves source code in a String and that performs
 * compilation on the fly and using only memory (without touching disk).
 * <p>
 * Note: This class to compile requires "tools.jar" file from JDK lib to be added.
 * <p>
 * Info needed to understand how this class works can be found here:
 *      * hhttp://www.javabeat.net/articles/73-the-java-60-compiler-api-1.html
 *      * http://blogs.helion-prime.com/category/general-programming/java
 *      * http://today.java.net/article/2008/04/09/source-code-analysis-using-java-6-apis
 *      * http://www.ibm.com/developerworks/java/library/j-jcomp/index.html
 *
  * @author Francisco José Morero Peyrona
 *
 * Official web site at: <a href="https://github.com/peyrona/mingle">https://github.com/peyrona/mingle</a>
 */
final class InMemoryCompiler
{
    private final DiagnosticCollector<JavaFileObject> diagnostics   = new DiagnosticCollector<>();
    private       boolean                             isCompiled    = false;

    //------------------------------------------------------------------------//

    // NEXT: quitar este método y ver cómo manejo lo del sClassFileVersion
    byte[] compile( String className, String classCode ) throws URISyntaxException, IOException
    {
        return compile( className, classCode, "8" );
    }

    /**
     * Compiles passed source code and returns the result of compilation.
     *
     * @param className The name of the class
     * @param classCode The class source code
     * @return Compiled code
     */
    byte[] compile( String className, String classCode, String sClassFileVersion ) throws URISyntaxException, IOException
    {
        if( UtilStr.endsWith( className, ".java" ) )
        {
            className = UtilStr.removeLast( className, ".java".length() );
        }

        if( IntermediateCodeWriter.isRequired() )
        {
            try( IntermediateCodeWriter writer = IntermediateCodeWriter.get() )
            {
                writer.startSection( "Java source code to be compiled" )
                      .writeln( "Class Name = "+ className )
                      .writeln()
                      .writeln( "Class File Version = "+ sClassFileVersion )
                      .writeln()
                      .writeln( "Source:")
                      .writeln()
                      .writeln( classCode )
                      .endSection();
            }
        }

        List<String> options = new ArrayList<>();     // Compilation options

        try
        {
            options.add( "-classpath" );
            options.add( UtilIO.expandPath( "{*home*}lib/lang.jar" ).get( 0 ).toString() );
            options.add( "--release" );
            options.add( sClassFileVersion );
        }
        catch( IOException ex )
        {
            // Nothing to do: it is too complex to report this error and it can not happen
        }

        JavaMemFileManager fileManager = new JavaMemFileManager();
        JavaFileObject     javaStrObj  = new JavaStringObject( className, classCode );
        JavaCompiler       compiler    = ToolProvider.getSystemJavaCompiler();
        CompilationTask    compileTask = compiler.getTask( null, fileManager, diagnostics, options, null, Arrays.asList( javaStrObj ) );
        byte[]             abByteCode  = null;

        try
        {
            if( compileTask.call() )
            {
                abByteCode = fileManager.getClassBytes( className );
                isCompiled = true;
            }

            fileManager.close();
        }
        catch( IOException ioe )
        {
            // Nothing to do
        }

        return abByteCode;
    }

    List<Pair<String,Integer>> getErrors()
    {
        List<Pair<String,Integer>> list = new ArrayList<>();

        if( (! isCompiled) && diagnostics.getDiagnostics().isEmpty() )
        {
            list.add( new Pair( "Source code not compiled", 0 ) );
        }
        else
        {
            for( Diagnostic<? extends JavaFileObject> d : diagnostics.getDiagnostics() )
                    list.add( new Pair( d.getMessage( Locale.ENGLISH ), (int) d.getPosition() ) );
        }

        return list;
    }

    //------------------------------------------------------------------------//
    // Inner Class
    // Makes a String behaving like a SimpleJavaFileObject.
    //------------------------------------------------------------------------//
    private final class JavaStringObject extends SimpleJavaFileObject
    {
        private final String contents;

        JavaStringObject( String className, String contents ) throws URISyntaxException
        {
            super( URI.create( "string:///"+ className + Kind.SOURCE.extension ), Kind.SOURCE );
            this.contents = contents;
        }

        @Override
        public CharSequence getCharContent( boolean ignoreEncodingErrors ) throws IOException
        {
            return contents;
        }
    }

    //------------------------------------------------------------------------//
    // Inner Class
    // Makes a SimpleJavaFileObject able to store compiled java code.
    //------------------------------------------------------------------------//
    private final class ClassMemFileObject extends SimpleJavaFileObject
    {
        private final ByteArrayOutputStream os = new ByteArrayOutputStream();

        ClassMemFileObject( String className )
        {
            super( URI.create( "mem:///"+ className + Kind.CLASS.extension ), Kind.CLASS );
        }

        private byte[] getBytes()
        {
            return os.toByteArray();
        }

        @Override
        public OutputStream openOutputStream()
        {
            return os;
        }
    }

    //------------------------------------------------------------------------//
    // Inner Class
    // Standard FileManager reads classes from disk, but as we perform the
    // compilation in memory, we'll need our own FileManager.
    //------------------------------------------------------------------------//
    @SuppressWarnings("rawtypes")
    private final class JavaMemFileManager extends ForwardingJavaFileManager
    {
        private final HashMap<String, ClassMemFileObject> classes = new HashMap<>();

        JavaMemFileManager()
        {
            super( ToolProvider.getSystemJavaCompiler().getStandardFileManager( null, null, null ) );
        }

        @Override
        public JavaFileObject getJavaFileForOutput( Location location, String className,
                                                    Kind kind, FileObject sibling ) throws IOException
        {
            JavaFileObject ret;

            if( StandardLocation.CLASS_OUTPUT == location && JavaFileObject.Kind.CLASS == kind )
            {
                ClassMemFileObject clase = new ClassMemFileObject( className );
                classes.put( className, clase );
                ret = clase;
            }
            else
            {
                ret = super.getJavaFileForOutput( location, className, kind, sibling );
            }

            return ret;
        }

        private byte[] getClassBytes( String className )
        {
            byte[] ret = null;

            if( classes.containsKey( className ) )
            {
                ret = classes.get(className).getBytes();
            }

            return ret;
        }
    }
}