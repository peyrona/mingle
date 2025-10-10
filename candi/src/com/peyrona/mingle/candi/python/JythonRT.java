
package com.peyrona.mingle.candi.python;

import com.peyrona.mingle.candi.Prepared;
import com.peyrona.mingle.lang.interfaces.ICandi;
import com.peyrona.mingle.lang.interfaces.IController;
import com.peyrona.mingle.lang.interfaces.exen.IRuntime;
import com.peyrona.mingle.lang.japi.Pair;
import com.peyrona.mingle.lang.japi.UtilStr;
import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.python.core.PyCode;
import org.python.core.PyException;
import org.python.util.PythonInterpreter;

/**
 * Provides Python code execution using Jython.
 *
 * @author Francisco José Morero Peyrona
 *
 * Official web site at: <a href="https://github.com/peyrona/mingle">https://github.com/peyrona/mingle</a>
 */
public class JythonRT implements ICandi.ILanguage
{
// NEXT: seguramente habrá una manera de transformar los PyCode en byte[] y poder hacer lo mismo q hago con Java

    private static final Map<String,Pair<String,PyCode>> map    = new ConcurrentHashMap();
    private static final PythonInterpreter               engine = new PythonInterpreter();

    //------------------------------------------------------------------------//

    @Override
    public ICandi.IPrepared prepare( String source, String call )
    {
        Prepared prepared = new Prepared( source )         // Can not prepare because CompiledScript is not convertible into a String
                                .setCallName( call );

        if( UtilStr.isEmpty( call ) )  call = null;
        else                           prepared.addError( "CALL clause is not yet suported: insert the call inside your Python code" );

        // Compiled just to check if it has errors

        try
        {
            engine.compile( source );
        }
        catch( PyException pe )
        {
            prepared.addError( "Error compiling. Cause:\n"+ UtilStr.toString( pe ) );
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
    public void bind( String sInvokerUID, ICandi.IPrepared prepared )
    {
        try( PythonInterpreter python = new PythonInterpreter() )
        {
            map.put( sInvokerUID, new Pair( prepared.getCallName(), python.compile( prepared.getCode() ) ) );
        }
    }

    @Override
    public void execute( String sInvokerUID, IRuntime rt )
    {
     // String call = map.get( sInvokerUID ).getKey();       // NEXT: usarlo
        PyCode code = map.get( sInvokerUID ).getValue();

        engine.set( "_exen_rt_", rt );
        engine.exec( code );
    }

    @Override
    public IController newController( String sInvokerUID ) throws Exception
    {
        // NEXT: hacerlo
        throw new UnsupportedOperationException( "Not supported yet." );
    }
}