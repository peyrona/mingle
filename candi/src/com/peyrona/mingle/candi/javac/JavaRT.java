
package com.peyrona.mingle.candi.javac;

import com.peyrona.mingle.candi.Prepared;
import com.peyrona.mingle.lang.interfaces.ICandi;
import com.peyrona.mingle.lang.interfaces.IController;
import com.peyrona.mingle.lang.interfaces.commands.ICommand;
import com.peyrona.mingle.lang.interfaces.commands.IDevice;
import com.peyrona.mingle.lang.interfaces.commands.IDriver;
import com.peyrona.mingle.lang.interfaces.commands.IRule;
import com.peyrona.mingle.lang.interfaces.commands.IScript;
import com.peyrona.mingle.lang.interfaces.exen.IRuntime;
import com.peyrona.mingle.lang.japi.Pair;
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
 * Official web site at: <a href="https://mingle.peyrona.com">https://mingle.peyrona.com</a>
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

        if( ! code.startsWith( "public void " ) )
            code = "public void "+ call +"( IRuntime rt ) throws Exception\n{\n"+ code +"\n}";

        if( ! code.contains( "public class " ) )    // Has to be here
            code = "public class "+ call +"\n{\n"+ code +"\n}";

        code = createImports() + code;

        try
        {
            prep.setCode( imc.compile( call, code ) );

            for( Pair<String,Integer> err : imc.getErrors() )
            {
                int lincol[] = offset2LineCol( code, err.getValue() );
                prep.addError( new CodeError( err.getKey(), lincol[0], lincol[1] ) );
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
            prepared.addExtra(sEXTRA_KEY_URI_LOADED, lstLoaded.toArray( String[]::new ) );

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

        sb.append( "import " ).append( IController.class.getCanonicalName() ).append( ';' ).append( '\n' )
          .append( "import " ).append( IRuntime.class.getCanonicalName()    ).append( ';' ).append( '\n' )
          .append( "import " ).append( ICommand.class.getCanonicalName()    ).append( ';' ).append( '\n' )
          .append( "import " ).append( IDevice.class.getCanonicalName()     ).append( ';' ).append( '\n' )
          .append( "import " ).append( IDriver.class.getCanonicalName()     ).append( ';' ).append( '\n' )
          .append( "import " ).append( IScript.class.getCanonicalName()     ).append( ';' ).append( '\n' )
          .append( "import " ).append( IRule.class.getCanonicalName()       ).append( ';' ).append( '\n' )
          .append( '\n' );

        return sb.toString();
    }

    private static int[] offset2LineCol( String code, int offset )
    {
        int line = 0;
        int lastNewlineIndex = -1; // Tracks the index of the last newline

        for( int n = 0; n < offset; n++ )
        {
            if( code.charAt( n ) == '\n' )
            {
                line++;
                lastNewlineIndex = n;
            }
        }

        int column = offset - lastNewlineIndex - 1;    // Column is the distance from the last newline to the offset

        return new int[] {line, column};
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