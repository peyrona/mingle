
package com.peyrona.mingle.lang.interfaces;

import com.peyrona.mingle.lang.MingleException;
import com.peyrona.mingle.lang.interfaces.exen.IRuntime;
import java.net.URI;
import java.util.List;

/**
 * Interface for all Compilers and all Interpreters except Une transpiler.
 * <p>
 * Note: UneC is different (it does not implements ICandi) because the Une transpiler has to
 * manage one or (normally) more source files (all others languages managed by Mingle use only
 * one chunk of source code) and this makes things much more complex.
 *
 * @author Francisco José Morero Peyrona
 *
 * Official web site at: <a href="https://mingle.peyrona.com">https://mingle.peyrona.com</a>
 */
public interface ICandi
{
    public interface IError
    {
        /**
         * Returns the error message.
         * @return the error message.
         */
        String message();

        /**
         * Returns the error line (1 based).
         * @return the error line (1 based).
         */
        int    line();

        /**
         * Returns the error column (1 based).
         * @return the error column (1 based).
         */
        int    column();
    }

    /**
     * This interface contains foreign (not Une) code ready to be used by the Mingle ExEn: it is
     * compiled code for languages like Java or C or the source code itself for JS and others.
     */
    public interface IPrepared
    {
        IError[] getErrors();
        String   getCallName();    // Can be null for languages like JS or Python but can not be null for languages like Java
        String   getCode();        // Code ready to be used (compiled for languages like Java or C, the source code itself for JS and others)
        Object   getExtra( String key );
    }

    public interface ILanguage
    {
        /**
         * Provides the opportunity to compile or in any other way pre-process the source code.
         * <p>
         * Implementations (like Java) will compile source code into byte-codes, others (like
         * JavaScript) will simply return the received source code.
         *
         * @param sSource Source code to be compiled.
         * @param call Entry point.
         * @return An instance of IPrepared.
         */
        IPrepared prepare( String sSource, String call );

        /**
         * If target code is already compiled, it is directly executed, otherwise this method
         * provides the opportunity to compile or in any other way pre-process the source code
         * previously to execute it.<br>
         * Implementations (like Java) will compile source code into byte-codes, others (like
         * JavaScript) will simply return the received source code.
         *
         * @param lstURIs Compiled code (normally JAR files).
         * @param call Entry point.
         * @param sCallName Entry point (function) name: the SCRIPT's CALL clause.
         * @return An instance of IPrepared.
         */
        IPrepared prepare( List<URI> lstURIs, String call );

        /**
         * Binds sInvokerUID to ICandi.IPrepared.
         *
         * @param sInvokerUID An Unique ID, e.g. the SCRIPT name.
         * @param prepared What was returned by ::prepare(...).
         */
        public void bind( String sInvokerUID, ICandi.IPrepared prepared );

        /**
         * Executes a piece of code (previously prepared).Normally this method is invoked by SCRIPT:execute(...) method.<p>
         * Information can be sent back to the Mingle ExEn by sending a Message via bus: e.g.:
         * <code>rt:bus().post( ... )</code>
         *
         * @param sInvokerUID An Unique ID, e.g. the SCRIPT name.
         * @param rt An instance of current IRuntime.
         * @throws java.lang.Exception If something goes wrong.
         */
        void execute( String sInvokerUID, IRuntime rt ) throws Exception;

        /**
         * Creates a new instance of IConntroller.
         *
         * @param sInvokerUID An Unique ID, e.g. the SCRIPT name.
         * @return An instance of IController.
         * @throws Exception
         */
        IController newController( String sInvokerUID ) throws Exception;
    }

    public interface IBuilder
    {
        /**
         * Returns an ILanguage instance for passed language or null if there is no way to create an ILanguage
         * instance.<br>
         * <br>
         * It tries to load the language definition from the MSP "config.json" file. If file is not found in
         * default folder or the file does not have the appropriate entry ("languages") or the entry is not
         * valid JSON, null will be returned.
         *
         *
         * @param sLanguageName Language name to be created.
         * @return Returns an ILanguage instance for passed language.
         * @throws MingleException If there was an error instantiating the builder.
         */
        ICandi.ILanguage build( String sLanguageName );

        /**
         * Creates a new ILanguage using passed language definition JSON String (as it appears in "config.json" MSP file).<br>
         * <br>
         * If passed parameter is not valid JSON a MingleException is thrown.
         *
         * @param sJSON_LangDef sJSON_LangDef JSON String with the language building definition.
         * @return The appropriate ILanguage.
         * @throws MingleException If there was an error instantiating the builder.
         */
        ICandi.ILanguage buildUsing( String sJSON_LangDef );
    }
}