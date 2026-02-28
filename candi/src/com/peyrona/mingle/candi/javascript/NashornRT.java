
package com.peyrona.mingle.candi.javascript;

import com.peyrona.mingle.candi.Prepared;
import com.peyrona.mingle.lang.MingleException;
import com.peyrona.mingle.lang.interfaces.ICandi;
import com.peyrona.mingle.lang.interfaces.IController;
import com.peyrona.mingle.lang.interfaces.exen.IRuntime;
import com.peyrona.mingle.lang.japi.Pair;
import com.peyrona.mingle.lang.japi.UtilStr;
import com.peyrona.mingle.lang.lexer.CodeError;
import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import javax.script.Bindings;
import javax.script.Compilable;
import javax.script.CompiledScript;
import javax.script.ScriptEngine;
import javax.script.ScriptEngineManager;
import javax.script.ScriptException;

/**
 * Provides JavaScript code execution using Nashorn.
 *
 * @author Francisco José Morero Peyrona
 *
 * Official web site at: <a href="https://github.com/peyrona/mingle">https://github.com/peyrona/mingle</a>
 */
public class NashornRT implements ICandi.ILanguage
{
    private static final Map<String,Pair<String,CompiledScript>> map    = new ConcurrentHashMap();
    private static final ScriptEngine                            engine = new ScriptEngineManager().getEngineByName( "nashorn" );

    //------------------------------------------------------------------------//

    @Override
    public ICandi.IPrepared prepare( String source, String call )
    {
        source = source.trim();

        Prepared prepared = new Prepared( source );         // Can not prepare because CompiledScript is not convertible into a String or String Base64 or whatever

        if( engine == null )
        {
            prepared.addError( "Nashorn JavaScript engine is not available: requires Java 11 or earlier" );
            return prepared.setCallName( call );
        }

        if( UtilStr.isEmpty( call ) )  call = null;
        else                           prepared.addError( "CALL clause is not yet supported: insert the call inside your JavaScript code" );

        // Compiled just to check if it has errors (inside ::bind(...) it is needed to compile again)

        try
        {
            ((Compilable) engine).compile( prepared.getCode() );
        }
        catch( ScriptException se )
        {
            int line = se.getLineNumber();
            int col  = se.getColumnNumber();

            prepared.addError( new CodeError( se.getMessage(),
                                              line < 0 ? 0 : line,
                                              col  < 0 ? 0 : col ) );
        }

        if( prepared.getErrors().length > 0 )
            prepared.setCode( (String) null );    // Saves RAM

        return prepared.setCallName( call );
    }

    @Override
    public ICandi.IPrepared prepare( List<URI> lstURIs, String call )
    {
        // NEXT: hacerlo
        throw new UnsupportedOperationException( "Not supported yet." );
    }

    @Override
    public IController newController( String sInvokerUID ) throws Exception
    {
        // TODO: hacerlo
        throw new UnsupportedOperationException( "Not supported yet." );
    }

    @Override
    public void bind( String sInvokerUID, ICandi.IPrepared prepared )
    {
        try
        {
            String         call = prepared.getCallName();
            CompiledScript code = ((Compilable) engine).compile( prepared.getCode() );

            map.put( sInvokerUID, new Pair( call, code ) );
        }
        catch( ScriptException se )
        {
           throw new MingleException( se );    // This should not happen because ::prepare(...) reports all errors
        }
    }

    @Override
    public void execute( String sInvokerUID, IRuntime rt )     // This method is invoked only if no errors were reported by ::prepare(...)
    {
        try
        {
            // String      call = map.get( sInvokerUID ).getKey();       // NEXT: usarlo
            CompiledScript code = map.get( sInvokerUID ).getValue();

            Bindings bindings = engine.createBindings();
                     bindings.put( "_exen_rt_", rt );

            code.eval( bindings );
        }
        catch( ScriptException exc )
        {
            int    line = exc.getLineNumber();
            int    col  = exc.getColumnNumber();
            String msg  = "JavaScript runtime error"
                        + (line > 0 ? " at line " + line : "")
                        + (col  > 0 ? ", column " + col  : "")
                        + ": " + exc.getMessage();

            throw new MingleException( msg, exc );
        }
    }
}