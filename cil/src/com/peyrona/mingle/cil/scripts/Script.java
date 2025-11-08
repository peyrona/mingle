
package com.peyrona.mingle.cil.scripts;

import com.peyrona.mingle.cil.Command;
import com.peyrona.mingle.lang.MingleException;
import com.peyrona.mingle.lang.interfaces.ICandi;
import com.peyrona.mingle.lang.interfaces.IController;
import com.peyrona.mingle.lang.interfaces.ILogger;
import com.peyrona.mingle.lang.interfaces.commands.IScript;
import com.peyrona.mingle.lang.interfaces.exen.IRuntime;
import com.peyrona.mingle.lang.japi.UtilColls;
import com.peyrona.mingle.lang.japi.UtilIO;
import com.peyrona.mingle.lang.japi.UtilStr;
import com.peyrona.mingle.lang.japi.UtilSys;
import com.peyrona.mingle.lang.messages.MsgTrigger;
import java.io.IOException;
import java.net.URISyntaxException;
import java.util.Arrays;

/**
 * Represents an script.
 *
 * Note: Stick (which is a higher level entity and has a broader vision) is responsible for invoking ::execute()
 * <p>
 * This has to be a public class to dynamically add instances to ExEn.
 *
 * @author Francisco José Morero Peyrona
 *
 * Official web site at: <a href="https://github.com/peyrona/mingle">https://github.com/peyrona/mingle</a>
 */
public final class      Script
             extends    Command
             implements IScript
{
    // The package scope members are accessed by ScriptBuilder
    private final String     langName;          // Language name
    private final boolean    isOnStart;         // AutoExecute at start
    private final boolean    isOnStop;          // AutoExecute at stop
    private final boolean    isInline;          // This inf. is not part of the language, it is provided by the transpiler (True when Une source code FROM clause contents (SCRIPT command) is in between brackets ({...}))
    private final String     call;              // Function or method to call (invoke) inside FROM (can be null for some languages (not for Java))
    private final String[]   asFrom;            // URI(s) or Code
    private ICandi.ILanguage langMgr  = null;   // Language manager
    private ICandi.IPrepared prepared = null;

    //------------------------------------------------------------------------//
    // PACKAGE SCOPE CONSTRUCTOR

    Script( String name, String language, boolean onStart, boolean onStop, boolean inline, String[] from, String call )
    {
        super( name );

        this.langName  = language;    // Needed because ::unbuild() uses it
        this.isOnStart = onStart;
        this.isOnStop  = onStop;
        this.isInline  = inline;
        this.asFrom    = from;        // Needed because ::unbuild() uses it
        this.call      = call;        // Case must be preserved. But can be null
    }

    //------------------------------------------------------------------------//

    @Override
    public boolean isOnStart()
    {
        return isOnStart;
    }

    @Override
    public boolean isOnStop()
    {
        return isOnStop;
    }

    @Override
    public String getLanguage()
    {
        return langName;
    }

    @Override
    public boolean isInline()
    {
        return isInline;
    }

    @Override
    public String getCall()
    {
        return call;
    }

    @Override
    public String[] getFrom()
    {
        return asFrom;

// TODO: terminar esto para que se pueda acceder desde Glue al código fuente cuando el SCRIPT es INLINE
//        if( langMgr == null )
//            return asFrom;
//
//        String sCode = prepared.getCode();
//               sCode = (UtilStr.isEmpty( sCode ) ? "<compiled code>" : sCode);
//
//        return new String[] { sCode };
    }


    @Override
    public void start( IRuntime runtime )
    {
        if( isStarted() )
            return;

        super.start( runtime );

        prepare();

        // SCRIPTs must be executed by posting a message into the bus because (among other reasons) this is the way it
        // will not be executed until ExEn is fully functional (functional == everything is in memory and initilized).
        // Stick (which is a higher level entity and has a broader vision) is responsible for invoking ::execute()

        if( isOnStart && canExecute() )
            getRuntime().bus().post( new MsgTrigger( name(), false ) );
    }

    @Override
    public void stop()
    {
        if( ! isStarted() )
            return;

        if( isOnStop && canExecute() )    // Because bus could be stopped, can't do following --> getRuntime().bus().post( new MsgTrigger( getName() ) );
        {
            try
            {
                langMgr.execute( name(), getRuntime() );
            }
            catch( Exception exc )
            {
                // Nothing to do
            }
        }

        super.stop();    // Can not do anything else because vars (langMgr, asFrom, etc) are needed if someone re-starts it

        langMgr  = null; // After stopping
        prepared = null;
    }

    @Override
    public void execute()
    {
        if( ! canExecute() )
            return;

        UtilSys.execute( getClass().getSimpleName() + name(),
                            () ->
                            {
                                try
                                {
                                    langMgr.execute( name(), getRuntime() );
                                }
                                catch( Exception exc )
                                {
                                    String sCause = UtilStr.toStringBrief( exc );
                                    getRuntime().log( ILogger.Level.SEVERE, new MingleException( "Error executing \""+ name() + "\": "+ sCause, exc ) );
                                }

                                // Can not do following becasue an ONSTART type script can also be invoked by rules later during execution
                                // if( isOnStart && ! isOnStop )
                                //     stop();
                            }
                       );
    }

    @Override
    public IController newController()
    {
        if( canExecute() )
        {
            try
            {
                return langMgr.newController( name() );     // Can not call stop() because there can be more than one instance of the same controller
            }
            catch( Exception exc )
            {
                String sCause = UtilStr.toStringBrief( exc );
                getRuntime().log( ILogger.Level.SEVERE, new MingleException( "Error in '"+ name() +"' when creating Controller."+ sCause, exc ) );
            }
        }

        return null;
    }

    @Override
    public String toString()
    {
        return UtilStr.toString( this );
    }

    //------------------------------------------------------------------------//

    private boolean canExecute()
    {
        assert isStarted() : "Not started";

        if( prepared == null )
            getRuntime().log( ILogger.Level.SEVERE, new MingleException( "Script '"+ name() +"' (using '"+ langName +"') can not be executed because it has errors." ) );

        return (prepared != null);
    }

    private synchronized void prepare()
    {
        if( prepared != null )
            return;

        langMgr = getRuntime().newLanguageBuilder().build( langName );

        if( langMgr == null )
        {
            prepared = null;
            getRuntime().log( ILogger.Level.SEVERE, '"'+ langName +"\": language not available" );
        }

        if( isInline )    // ::asFrom has only one item (the source code) and the code is already compiled and checked that it is error free
        {
             prepared = new ICandi.IPrepared()
                            {
                                @Override
                                public ICandi.IError[] getErrors()    { return new ICandi.IError[0]; }

                                @Override
                                public String getCode()               { return Script.this.asFrom[0]; }

                                @Override
                                public String getCallName()           { return Script.this.call; }

                                @Override
                                public Object getExtra( String key )  { return null; }
                            };
        }
        else
        {
            try
            {
                prepared = langMgr.prepare( UtilIO.expandPath( asFrom ), call );
            }
            catch( IOException | URISyntaxException ex )
            {
                prepared = null;
                getRuntime().log( ILogger.Level.SEVERE, new MingleException( "Malformed URI: "+ Arrays.toString( asFrom ), ex ) );
            }
        }

        if( UtilColls.isNotEmpty( prepared.getErrors() ) )
        {
            final StringBuilder sb = new StringBuilder( prepared.getErrors().length * 256 );

            for( ICandi.IError error : prepared.getErrors() )
            {
                sb.append( "Error: " ).append( error.message() ).append( '\n' )
                  .append( "At lin:" ).append( error.line() ).append( ", col:" ).append( error.column() ).append( '\n' );
            }

            getRuntime().log( ILogger.Level.SEVERE, new MingleException( "Source code compilation errors:\n"+ sb.toString() ) );
            prepared = null;
        }

        if( prepared == null )
            langMgr = null;     // Not needed any more
        else
            langMgr.bind( name(), prepared );
    }
}
