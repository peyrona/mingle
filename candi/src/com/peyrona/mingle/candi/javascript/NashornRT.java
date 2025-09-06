
package com.peyrona.mingle.candi.javascript;

import com.peyrona.mingle.candi.Prepared;
import com.peyrona.mingle.lang.MingleException;
import com.peyrona.mingle.lang.interfaces.ICandi;
import com.peyrona.mingle.lang.interfaces.IController;
import com.peyrona.mingle.lang.interfaces.exen.IRuntime;
import com.peyrona.mingle.lang.japi.Pair;
import com.peyrona.mingle.lang.japi.UtilStr;
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
 * Official web site at: <a href="https://mingle.peyrona.com">https://mingle.peyrona.com</a>
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

        if( UtilStr.isEmpty( call ) )  call = null;
        else                           prepared.addError( "CALL clause is not yet suported: insert the call inside your JavaScript code" );  //call = call.trim();

        // Compiled just to check if it has errors (inside ::bind(...) it is needed to compile again)

        try
        {
            ((Compilable) engine).compile( prepared.getCode() );
        }
        catch( ScriptException se )
        {
            prepared.addError( "Error compiling. Cause:\n"+ UtilStr.toString( se ) );
        }

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
            throw new MingleException( "Error executing. Cause: "+ exc.getMessage(), exc );
        }
    }
}