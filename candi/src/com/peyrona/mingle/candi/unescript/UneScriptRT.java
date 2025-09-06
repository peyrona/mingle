
package com.peyrona.mingle.candi.unescript;

import com.peyrona.mingle.candi.Prepared;
import com.peyrona.mingle.candi.unec.parser.ParseRuleThen;
import com.peyrona.mingle.candi.unec.parser.ParseRuleThen.Action;
import com.peyrona.mingle.candi.unec.transpiler.UnecTools;
import com.peyrona.mingle.lang.MingleException;
import com.peyrona.mingle.lang.interfaces.ICandi;
import com.peyrona.mingle.lang.interfaces.ICandi.IError;
import com.peyrona.mingle.lang.interfaces.IController;
import com.peyrona.mingle.lang.interfaces.ILogger;
import com.peyrona.mingle.lang.interfaces.IXprEval;
import com.peyrona.mingle.lang.interfaces.commands.ICommand;
import com.peyrona.mingle.lang.interfaces.commands.IDevice;
import com.peyrona.mingle.lang.interfaces.exen.IRuntime;
import com.peyrona.mingle.lang.japi.UtilColls;
import com.peyrona.mingle.lang.japi.UtilIO;
import com.peyrona.mingle.lang.japi.UtilSys;
import com.peyrona.mingle.lang.lexer.Lexeme;
import com.peyrona.mingle.lang.lexer.Lexer;
import com.peyrona.mingle.lang.messages.MsgChangeActuator;
import com.peyrona.mingle.lang.messages.MsgTrigger;
import java.io.IOException;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 *
 * @author Francisco José Morero Peyrona
 *
 * Official web site at: <a href="https://mingle.peyrona.com">https://mingle.peyrona.com</a>
 */
public class UneScriptRT implements ICandi.ILanguage
{
    private static final Map<String,List<AnAction>> map = new ConcurrentHashMap<>();

    //------------------------------------------------------------------------//

    @Override
    public ICandi.IPrepared prepare( String source, String call )
    {
        assert call == null || call.isEmpty();    // Une does not have 'function' concept

        Prepared prep  = new Prepared( source );
        Lexer    lexer = new Lexer( source );

        if( lexer.getErrors().isEmpty() )    // We can proceed with ParseRuleAction
        {
            List<List<Lexeme>> splitted = UnecTools.splitByDelimiter( lexer.getLexemes() );
            IXprEval           xprEval  = UtilSys.getConfig().newXprEval();    // Used just to check that it does not returns null

            if( xprEval == null )
            {
                return prep.addError( "Error reading config file" );
            }
            else
            {
                for( List<Lexeme> lst : splitted )
                {
                    new ParseRuleThen( lst, UtilSys.getConfig().newXprEval() )
                            .getErrors()
                            .forEach( (IError error) -> prep.addError( error ) );
                }
            }
        }
        else
        {
            for( IError err : lexer.getErrors() )
                prep.addError( err );
        }

        return prep;
    }

    @Override
    public ICandi.IPrepared prepare( List<URI> lstURIs, String call )
    {
        assert call == null || call.isEmpty();    // Une does not have 'function' concept

        if( UtilColls.isEmpty( lstURIs ) || lstURIs.size() > 1 )
            return new Prepared().addError( "One and only one URI is accepted" );

        try
        {
            return prepare( UtilIO.getAsText( lstURIs.get( 0 ) ), null );
        }
        catch( IOException ioe )
        {
            return new Prepared().addError( "Error reading "+ lstURIs.get( 0 ) +", "+ ioe.getMessage() );
        }
    }

    @Override
    public void bind( String sInvokerUID, ICandi.IPrepared prepared )
    {
        ParseRuleThen  parser = new ParseRuleThen( new Lexer( prepared.getCode() ).getLexemes(), UtilSys.getConfig().newXprEval() );    // newXprEval() will always return a non null because this method is invoked inside ExEn and ExEns check that this operation succedeed
        List<AnAction> list   = new ArrayList<>();

        for( Action act : parser.getActions() )
            list.add( new AnAction( act ) );

        map.put( sInvokerUID, list );
    }

    @Override
    public void execute( String sInvokerUID, IRuntime rt )     // This method is invoked only if no errors were reported by ::prepare(...)
    {
        try
        {
            map.get( sInvokerUID )
               .forEach( action ->
                         action.execute( rt ) );
        }
        catch( Exception e )
        {
            UtilSys.getLogger().log( ILogger.Level.SEVERE, e );
        }
    }

    @Override
    public IController newController( String sInvokerUID ) throws Exception
    {
        throw new UnsupportedOperationException();     // This does not make sense: can not cretae Contollers using Une
    }

    //------------------------------------------------------------------------//
    // INNER CLASS
    //------------------------------------------------------------------------//
    private static final class AnAction
    {
        private final Action   action;
        private       IXprEval xprEval = null;

        AnAction( Action action )
        {
            this.action = action;
        }

        void execute( IRuntime runtime )
        {
            Object value = null;

            switch( action.getType() )
            {
                case AssignDevice:
                    value = getDevice( runtime, action.getValueToSet().toString() ).value();
                    break;

                case AssignBasicData:
                    value = action.getValueToSet();
                    break;

                case AssignExpression:
                    if( xprEval == null )
                        xprEval = runtime.newXprEval().build( action.getValueToSet().toString(), null, runtime.newGroupWiseFn() );

                    value = eval( runtime );
                    break;

                case Expression:
                    if( xprEval == null )
                        xprEval = runtime.newXprEval().build( action.getTargetName(), null, runtime.newGroupWiseFn() );

                    eval( runtime );     // Do not assign return value (if any) to value
                    break;

                case RuleOrScript:
                    runtime.bus().post( new MsgTrigger( action.getTargetName(), false ), action.getDelay() );
                    break;
            }

            if( value != null )
                runtime.bus().post( new MsgChangeActuator( action.getTargetName(), value ), action.getDelay() );
        }

        private Object eval( IRuntime runtime )
        {
            if( ! xprEval.getErrors().isEmpty() )
                throw new MingleException( '"'+ xprEval.toString() +"\" has errors: "+ xprEval.toString() );

            Map<String,Object> mapVars = xprEval.getVars();

            if( mapVars.isEmpty() )
                return xprEval.eval();

            // Expression has variables: they can be not initialized or be obsolete.
            // These vars are not automatically updated. So, lets find out their values.

            for( String varname : mapVars.keySet() )
            {
                IDevice device = getDevice( runtime, varname );

                if( device == null )
                    throw new MingleException( varname +": device does not exist" );

                Object value = device.value();

                if( value == null )    // This device has not a value yet (it could be (v.g.) that there were an error while retrieving its value form the physical world)
                    return null;

                xprEval.set( varname, value );
            }

            Object oRet = xprEval.eval();

            if( oRet == null )
                throw new MingleException( "Error evaluating expression: "+ xprEval.toString() );

            return oRet;
        }

        private IDevice getDevice( IRuntime runtime, String name )
        {
            ICommand cmd = runtime.get( name );

            if( cmd instanceof IDevice )
                return (IDevice) cmd;

            throw new MingleException( name +": device does not exist" );
        }
    }
}