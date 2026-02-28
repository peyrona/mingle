
package com.peyrona.mingle.candi.javac;

import com.peyrona.mingle.candi.Prepared;
import com.peyrona.mingle.lang.MingleException;
import com.peyrona.mingle.lang.interfaces.ICandi;
import com.peyrona.mingle.lang.interfaces.IController;
import com.peyrona.mingle.lang.interfaces.exen.IRuntime;
import com.peyrona.mingle.lang.japi.UtilReflect;
import com.peyrona.mingle.lang.japi.UtilStr;
import com.peyrona.mingle.lang.japi.UtilSys;
import com.peyrona.mingle.lang.lexer.CodeError;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Java runtime compiler and executor.<br>
 * <br>
 * It turns out that JarOutputStream has three undocumented quirks:
 *    a) Directory names must end with a '/' slash.
 *    b) Paths must use '/' slashes, not '\'
 *    c) Entries may not begin with a '/' slash.
 *
 * Taken from here: https://stackoverflow.com/questions/1281229/how-to-use-jaroutputstream-to-create-a-jar-file
 *
 * @author Francisco José Morero Peyrona
 *
 * Official web site at: <a href="https://github.com/peyrona/mingle">https://github.com/peyrona/mingle</a>
 */
public final class JavaRT implements ICandi.ILanguage
{
    private static final String sEXTRA_KEY_URI_LOADED = "Loaded URIs";

    private static final Map<String,MapValue> map = new ConcurrentHashMap<>();

    //------------------------------------------------------------------------//

    @Override
    public ICandi.IPrepared prepare( String code, String call )
    {
        Prepared         prep = new Prepared( code );
        InMemoryCompiler imc  = new InMemoryCompiler();

        if( UtilStr.isMeaningless( call ) )
            call = "_"+ UUID.randomUUID().toString().replace( '-', '_' );

        prep.setCallName( call );

        if( code.startsWith( "import " ) )
            prep.addError( "'import' not allowed: use full qualified names" );

        if( code.contains( "public class " ) )
            prep.addError( "Do not declare a class, only methods are allowed. For elaborated Java code, create a JAR" );

        // Track whether each preamble wrapper is applied so we can subtract their line count
        // from the compiler's reported lines to get user-code-relative positions.
        // We check for the exact signature rather than startsWith() so that user code that
        // begins with a Javadoc comment (/**) is handled correctly.
        boolean methodWrapped = ! code.contains( "public void " + call + "(" );

        if( methodWrapped )
            code = "public void "+ call +"( IRuntime rt ) throws Exception\n{\n"+ code +"\n}";

        if( ! code.contains( "public class " ) )    // Has to be here
            code = "public class "+ call +"\n{\n"+ code +"\n}";

        final String imports = createImports();
        code = imports + code;

        // Lines added before the user code by createImports() + the class/method wrappers.
        // createImports() produces exactly one '\n' per statement, so count them dynamically
        // to stay in sync if the method is ever modified.
        int importLines = 0;
        for( int n = 0; n < imports.length(); n++ )
            if( imports.charAt( n ) == '\n' ) importLines++;

        // Lines added before the user code:
        //   imports  → importLines  (7 import statements + 1 blank line = 8)
        //   class {  → always 2 lines  ("public class NAME\n{\n")
        //   method { → 2 lines only when the method wrapper was added
        final int preambleLines = importLines + 2 + (methodWrapped ? 2 : 0);

        try
        {
            prep.setCode( imc.compile( call, code ) );

            for( CodeError err : imc.getErrors() )
            {
                // Shift compiler line (1-based, relative to generated source) to be
                // 1-based relative to the user's embedded code block.
                int userLine = (err.line() > 0) ? Math.max( 1, err.line() - preambleLines ) : 0;
                prep.addError( new CodeError( err.message(), userLine, err.column() ) );
            }

            if( (prep.getCode() == null) && (prep.getErrors().length == 0) )
                prep.addError( "Compilation task failed: reason unknown" );
        }
        catch( URISyntaxException | IOException exc )
        {
            prep.addError( exc.getMessage() );
        }

        if( prep.getErrors().length > 0 )
            prep.setCode( (String) null );     // Saves RAM

        return prep;
    }

    @Override
    public ICandi.IPrepared prepare( List<URI> lstURIs, String call )
    {
        Prepared     prepared  = new Prepared();
        List<String> lstLoaded = new ArrayList<>();    // The asURIs ones that were JARs

        for( URI uri : lstURIs )
        {
            String sURI = uri.toString();

            if( UtilStr.endsWith( sURI, ".jar" ) )
            {
                try
                {
                    UtilSys.addToClassPath( sURI );
                    lstLoaded.add( sURI );
                }
                catch( IOException | URISyntaxException ex )
                {
                    prepared.addError( "loading URI: "+ sURI );
                }
            }
// NEXT: se podría hacer q además de aceptar .JAR se aceptasen .class e incluso .java (compilar on-the-fly)
//           else if( UtilStr.endsWith( sURI, "class" ) )
//           {
//                    if( (josJAR == null) && ((josJAR = newTmpJAR()) == null) )
//                    {
//                        prepared.addError( sURI +": can not create container JAR", -1 );
//                    }
//                    else
//                    {
//                        josJAR.add( download( sURI ) );
//                    }
//            }
//            else if( UtilStr.endsWith( sURI, "java" ) )
//            {
//                    if( (josJAR == null) && ((josJAR = newTmpJAR()) == null) )
//                    {
//                        prepared.addError( sURI +": can not create container JAR", -1 );
//                    }
//                    else
//                    {
//                        String sClassName = UtilIO.getName( fURI );    // In Java, the name of the class is the name of the file
//
//                        josJAR.add( compile( download( sURI ), sClassName ) );
//                    }
//
//             }
            else
            {
                prepared.addError( sURI +": file type unknown for language Java (only JARs are allowed at this moment)" );
            }
         }

        if( ! lstLoaded.isEmpty() )
            prepared.addExtra( sEXTRA_KEY_URI_LOADED, lstLoaded.toArray( String[]::new ) );

        return prepared.setCallName( call );
    }

    @Override
    public void bind( String sInvokerUID, ICandi.IPrepared prepared )
    {
        byte[]   abCode = UtilStr.isEmpty( prepared.getCode() ) ? null : Base64.getDecoder().decode( prepared.getCode() );
        String[] asURIs = (String[]) prepared.getExtra( sEXTRA_KEY_URI_LOADED );
        String   sCall  = prepared.getCallName();

        map.put( sInvokerUID, new MapValue( sCall, abCode, asURIs ) );
    }

    @Override
    public void execute( String sInvokerUID, IRuntime rt )     // This method is invoked only if no errors were reported by ::prepare(...)
    {
        String call = map.get( sInvokerUID ).call;
        byte[] code = map.get( sInvokerUID ).code;

        InMemoryExecutor.execute( code, call, call, rt );
    }

    @Override
    public IController newController( String sInvokerUID ) throws Exception     // This method is invoked only if no errors were reported by ::prepare(...)
    {                                                                           // Can not delete from map because there can be more than one instance of same driver
        String   call = map.get( sInvokerUID ).call;
        String[] uris = map.get( sInvokerUID ).uris;

        return UtilReflect.newInstance( IController.class, call, uris );
    }

    //------------------------------------------------------------------------//
    // PRIVATE SCOPE

    private static String createImports()
    {
        StringBuilder sb = new StringBuilder( 2 * 1024 );
        String        ss = MingleException.class.getPackageName();

        sb.append( "import " ).append( ss ).append( ".interfaces.*;\n" )
          .append( "import " ).append( ss ).append( ".interfaces.commands.*;\n" )
          .append( "import " ).append( ss ).append( ".interfaces.exen.*;\n" )
          .append( "import " ).append( ss ).append( ".interfaces.network.*;\n" )
          .append( "import " ).append( ss ).append( ".japi.*;\n" )
          .append( "import " ).append( ss ).append( ".messages.*;\n" )
          .append( "import " ).append( ss ).append( ".xpreval.functions.*;\n" )
          .append( '\n' );

        return sb.toString();
    }

    //------------------------------------------------------------------------//
    // INNER CLASS
    // Just an struct to be used as the ::map value.
    //------------------------------------------------------------------------//
    private static final class MapValue
    {
        private final String   call;
        private final byte[]   code;
        private final String[] uris;

        MapValue( String call, byte[] code,  String[] uris )
        {
            this.call = call;
            this.code = code;
            this.uris = uris;
        }
    }
}