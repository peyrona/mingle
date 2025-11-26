
package com.peyrona.mingle.controllers;

import com.peyrona.mingle.lang.interfaces.IController;
import com.peyrona.mingle.lang.interfaces.ILogger;
import com.peyrona.mingle.lang.interfaces.exen.IRuntime;
import com.peyrona.mingle.lang.japi.UtilStr;
import java.io.IOException;
import java.util.Iterator;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import javax.speech.AudioException;
import javax.speech.Central;
import javax.speech.EngineException;
import javax.speech.EngineStateError;
import javax.speech.synthesis.Synthesizer;
import javax.speech.synthesis.SynthesizerModeDesc;

/**
 * This Controller send device's value to Sun FreeTTS.<br>
 * <br>
 * See note at ControllerBase API doc.
 *
 * @author Francisco José Morero Peyrona
 *
 * Official web site at: <a href="https://github.com/peyrona/mingle">https://github.com/peyrona/mingle</a>
 */
public final class   SunFreeTTS
             extends ControllerBase
{
    private static final String KEY_LOCALE = "locale";

                         // DevName,DevVal
    private static final Map<String,String> mapPending = new ConcurrentHashMap<>();
    private static       Synthesizer        synthesizer;

    //------------------------------------------------------------------------//

    @Override
    public void set( String deviceName, Map<String,Object> mapConfig, IController.Listener listener )    // CARE: this map can be inmutable
    {
        setDeviceName( deviceName );
        setListener( listener );     // Must be at begining: in case an error happens, Listener is needed

        Locale locale = new Locale( (String) mapConfig.getOrDefault( KEY_LOCALE, Locale.US.toString() ) );

        // NEXT: allow other languages and locales and make sy not static final (one instance per DEVICE is needed)
        locale = Locale.US;

        set( KEY_LOCALE, locale );
        // --------------------------------------------------------------------------------------------------------

        setValid( true );
    }

    @Override
    public void read()
    {
        // Nothing to read: this is an output-only Controller: value has to be readed from the twin-model
    }

    @Override
    public void write( Object text )
    {
        if( isFaked() || isInvalid() || UtilStr.isMeaningless( text.toString() ) )
            return;

        boolean bNoPending = mapPending.isEmpty();

        mapPending.put( getDeviceName(), String.valueOf( text ) );

        if( bNoPending )     // Otherwise there is a Thread already working (processing the map)
        {
            new Thread( () ->
                        {
                            while( ! mapPending.isEmpty() )    // A new request could arrive meanwhile all entries in Map were being processed
                            {
                                for( Iterator<Map.Entry<String,String>> itera = mapPending.entrySet().iterator(); itera.hasNext(); )
                                {
                                    Map.Entry<String,String> entry = itera.next();

                                    say( entry.getKey(), entry.getValue() );
                                    sendChanged( entry.getKey(), entry.getValue() );
                                    itera.remove();
                                }
                            }

                        } ).start();
        }
    }

    @Override
    public void start( IRuntime rt )
    {
        if( isInvalid() )
            return;

        super.start( rt );

        if( synthesizer == null )
        {
            try
            {
                System.setProperty( "freetts.voices", "com.sun.speech.freetts.en.us" + ".cmu_us_kal.KevinVoiceDirectory" );
                Central.registerEngineCentral( "com.sun.speech.freetts" + ".jsapi.FreeTTSEngineCentral" );

                synthesizer = Central.createSynthesizer( new SynthesizerModeDesc( (Locale) get( KEY_LOCALE ) ) );
                synthesizer.allocate();
                synthesizer.resume();

                setValid( true );
            }
            catch( IllegalArgumentException | AudioException | EngineException exc )
            {
                sendIsInvalid( exc );
            }
        }
    }

    @Override
    public void stop()
    {
        try
        {
            synthesizer.deallocate();
            synthesizer = null;
        }
        catch( EngineException | EngineStateError exc )
        {
            sendGenericError( ILogger.Level.WARNING, "Can not deallocate synthesizer" );
        }
    }

    //------------------------------------------------------------------------//

    private void say( String devName, String text )
    {
        try
        {
            synthesizer.resume();
            synthesizer.speakPlainText( text, null );
            synthesizer.waitEngineState( Synthesizer.QUEUE_EMPTY );

            sendChanged( devName, text );
        }
        catch( InterruptedException | AudioException exc )
        {
            sendWriteError( text, exc );
        }
        catch( EngineStateError thr )    // Has to be in its oww catch because this is a Throwable, not an Exception
        {
            sendWriteError( text, new IOException( thr ) );
        }
    }
}