
package com.peyrona.mingle.candi.python;

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
import org.python.core.PyCode;
import org.python.core.PyException;
import org.python.core.PyObject;
import org.python.core.PyTraceback;
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
        Prepared prepared = new Prepared( source );    // Can not prepare because PyCode is not convertible into a String

        if( UtilStr.isEmpty( call ) )  call = null;
        else                           prepared.addError( "CALL clause is not yet supported: insert the call inside your Python code" );

        // Compiled just to check if it has errors

        try
        {
            engine.compile( source );
        }
        catch( PyException pe )
        {
            prepared.addError( new CodeError( extractMessage( pe ),
                                              extractLineNumber( pe ),
                                              extractColumnNumber( pe ) ) );
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

        try
        {
            engine.exec( code );
        }
        catch( PyException pe )
        {
            int    line = extractInnermostLine( pe );
            String msg  = "Python runtime error"
                        + (line > 0 ? " at line " + line : "")
                        + ": " + extractMessage( pe );

            throw new MingleException( msg, pe );
        }
    }

    @Override
    public IController newController( String sInvokerUID ) throws Exception
    {
        // NEXT: hacerlo
        throw new UnsupportedOperationException( "Not supported yet." );
    }

    //------------------------------------------------------------------------//
    // PRIVATE SCOPE

    /**
     * Extracts the human-readable error message from a {@link PyException}.
     * <p>
     * For SyntaxError and its subclasses the relevant text is held in the
     * {@code msg} attribute of the exception value; for all other exceptions
     * {@link PyException#getMessage()} is used as a fallback.
     */
    private static String extractMessage( PyException pe )
    {
        if( pe.value != null )
        {
            try
            {
                PyObject msg = pe.value.__findattr__( "msg" );

                if( msg != null )
                    return pe.type.toString() + ": " + msg.toString();
            }
            catch( Exception ignored ) { }
        }

        String s = pe.getMessage();

        return UtilStr.isMeaningless( s ) ? pe.toString() : s;
    }

    /**
     * Extracts the 1-based line number from a {@link PyException}.
     * <p>
     * For compile-time errors (SyntaxError) the line number is stored in the
     * {@code lineno} attribute of the exception value.  For runtime errors it
     * is taken from the outermost {@link PyTraceback} frame.
     *
     * @return the line number, or {@code 0} when not available.
     */
    private static int extractLineNumber( PyException pe )
    {
        if( pe.value != null )
        {
            try
            {
                PyObject attr = pe.value.__findattr__( "lineno" );

                if( attr != null )
                    return attr.asInt();
            }
            catch( Exception ignored ) { }
        }

        return (pe.traceback instanceof PyTraceback) ? ((PyTraceback) pe.traceback).tb_lineno : 0;
    }

    /**
     * Extracts the 1-based column offset from a {@link PyException}.
     * <p>
     * Only SyntaxError (and subclasses) carry an {@code offset} attribute;
     * all other exception types return {@code 0}.
     *
     * @return the column offset, or {@code 0} when not available.
     */
    private static int extractColumnNumber( PyException pe )
    {
        if( pe.value != null )
        {
            try
            {
                PyObject attr = pe.value.__findattr__( "offset" );

                if( attr != null )
                    return attr.asInt();
            }
            catch( Exception ignored ) { }
        }

        return 0;
    }

    /**
     * Returns the line number from the innermost (deepest) {@link PyTraceback}
     * frame, which corresponds to the actual line in user code that caused the
     * runtime exception.
     *
     * @return the line number, or {@code 0} when no traceback is available.
     */
    private static int extractInnermostLine( PyException pe )
    {
        if( !(pe.traceback instanceof PyTraceback) )
            return 0;

        PyTraceback tb = (PyTraceback) pe.traceback;

        while( tb.tb_next instanceof PyTraceback )
            tb = (PyTraceback) tb.tb_next;

        return tb.tb_lineno;
    }
}