
package com.peyrona.mingle.glue.codeditor.UneEditorTabContent;

import com.eclipsesource.json.Json;
import com.eclipsesource.json.JsonArray;
import com.eclipsesource.json.JsonObject;
import com.eclipsesource.json.JsonValue;
import com.peyrona.mingle.glue.ConsolePanel;
import com.peyrona.mingle.glue.JTools;
import com.peyrona.mingle.glue.Util;
import com.peyrona.mingle.lang.interfaces.ILogger;
import com.peyrona.mingle.lang.japi.UtilANSI;
import com.peyrona.mingle.lang.japi.UtilIO;
import com.peyrona.mingle.lang.japi.UtilJson;
import com.peyrona.mingle.lang.japi.UtilStr;
import com.peyrona.mingle.lang.japi.UtilSys;
import com.peyrona.mingle.tape.TranspilerTask;
import java.awt.event.ActionEvent;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.regex.Pattern;
import javax.swing.Action;
import javax.swing.JSplitPane;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;
import org.fife.ui.rsyntaxtextarea.RSyntaxTextAreaEditorKit;

/**
 * A Split Panel with an editor at top and a PanelConsole (to show transpiler and run output) at bottom.
 */
public final class UneEditorUnit extends JSplitPane
{
    private static final AtomicInteger nSocketPort  = new AtomicInteger( 20000 );   // High enough port number to no conflict with System ports
    private static final Pattern       SCRIPT_KW    = Pattern.compile( "^\\s*(SCRIPT|LIBRARY)\\b", Pattern.CASE_INSENSITIVE | Pattern.MULTILINE );

    private File    fCode            = null;
    private long    fileLastModified = 0L;
    private boolean isFolded         = false;
    private Process procExEn         = null;
    private boolean is1stTime        = true;

    //------------------------------------------------------------------------//

    public static UneEditorPane newEditor( String sCode )
    {
        return new UneEditorPane( sCode );
    }

    //------------------------------------------------------------------------//
    // CONSTRUCTORS

    public UneEditorUnit( File file  ) throws IOException
    {
        this( (file == null) ? "" : UtilIO.getAsText( file ));

        setFile( file );
    }

    public UneEditorUnit( String sCode  )
    {
        setOrientation( JSplitPane.VERTICAL_SPLIT );
        setContinuousLayout( true );
        setOneTouchExpandable( true );
        setTopComponent( new UneEditorPane( sCode ) );    // UneEditorPane constructor already calls setText(sCode)
        setBottomComponent( new ConsolePanel() );

        getEditor().grabFocus();

        addComponentListener( new ComponentAdapter()
        {
            @Override
            public void componentShown( ComponentEvent ce )     // Invoked when this component becames visible
            {
                if( is1stTime && (ce.getComponent() == UneEditorUnit.this) )
                {
                    UtilSys.executor( true )
                           .delay( 250 )   // Needed
                           .execute( () -> SwingUtilities.invokeLater( () -> UneEditorUnit.this.setDividerLocation( 1d ) ) );

                    is1stTime = false;
                }
            }
        } );
    }

    //------------------------------------------------------------------------//
    // PUBLIC SCOPE

    public UneEditorPane getEditor()
    {
        return (UneEditorPane) getTopComponent();
    }

    public ConsolePanel getConsole()
    {
        return (ConsolePanel) getBottomComponent();
    }

    public File getFile()
    {
        return fCode;
    }

    public UneEditorUnit setFile( File file )
    {
        this.fCode            = file;
        this.fileLastModified = (file != null) ? file.lastModified() : 0L;

        getEditor().setStyle( ((file == null) ? "une" : UtilIO.getExtension( file )) );

        return this;
    }

    public String getText()
    {
        return getEditor().getText();
    }

    public boolean isEmpty()
    {
        return UtilStr.isEmpty( getEditor().getText() );
    }

    public UneEditorUnit onSelected()
    {
        getEditor().grabFocus();     // This is done inside SwingUtilities.invokeLater(...)

        return this;
    }

    public boolean isChanged()
    {
        return getEditor().isChanged();
    }

    public UneEditorUnit toggleFolding()
    {
        if( isFolded )
        {
            Action a = new RSyntaxTextAreaEditorKit.ExpandAllFoldsAction();
            a.actionPerformed(new ActionEvent(getEditor().getTextArea(), ActionEvent.ACTION_PERFORMED, "expandAllFolds"));
        }
        else
        {
            Action a = new RSyntaxTextAreaEditorKit.CollapseAllFoldsAction();
            a.actionPerformed(new ActionEvent(getEditor().getTextArea(), ActionEvent.ACTION_PERFORMED, "collapseAllFolds"));
        }

        isFolded = ! isFolded;

        return this;
    }

    public boolean canUndo()
    {
        return getEditor().canUndo();
    }

    public boolean canRedo()
    {
        return getEditor().canRedo();
    }

    public UneEditorUnit undo()
    {
        getEditor().undo();

        return this;
    }

    public UneEditorUnit redo()
    {
        getEditor().redo();

        return this;
    }

    public UneEditorUnit addOnCaretChanged( final BiConsumer<Integer,Integer> consumer )
    {
        getEditor().addOnCaretChanged( consumer );

        return this;
    }

    public UneEditorUnit addOnTextChanged( final Runnable listener )
    {
        getEditor().addOnTextChanged( listener );

        return this;
    }

    /**
     * 1 based
     *
     * @return
     */
    public int getCaretLine()
    {
        return getEditor().getCaretLine() + 1;
    }

    /**
     * 1 based
     *
     * @return
     */
    public int getCaretColumn()
    {
        return getEditor().getCaretColumn() + 1;
    }

    public int getCaretOffset()
    {
        return getEditor().getCaretOffset();
    }

    public int setCaretOffset( int offset )
    {
        getEditor().setCaretOffset( offset );

        return getEditor().getCaretOffset();
    }

    public String getSelectedText()
    {
        return getEditor().getSelectedText();
    }

    public boolean searchText( String text )
    {
        return getEditor().search( text, null );
    }

    public boolean findPreviousSearch( String str )
    {
        return getEditor().search( str, false );
    }

    public boolean findNextSearch( String str )
    {
        return getEditor().search( str, true );
    }

    public UneEditorUnit replaceSearch( String search, String replace, boolean all )
    {
        getEditor().replaceSearch( search, replace, all );

        return this;
    }

    public UneEditorUnit replaceSelection( String str )
    {
        getEditor().replaceSelection( str );

        return this;
    }

    public UneEditorUnit insertText( String str )
    {
        getEditor().insert( str );

        return this;
    }

    public UneEditorUnit toggleRem()
    {
        getEditor().toggleRem();

        return this;
    }

    public UneEditorUnit copyAsRTF()
    {
        getEditor().copyAsRTF();

        return this;
    }

    /**
     * Transpiles the Une source in this editor.
     * <p>
     * If the source contains at least one {@code SCRIPT} command an indeterminate progress
     * bar is shown (via {@link JTools#showWaitFrame}) while {@link TranspilerTask} runs on a
     * background thread, keeping the UI responsive. For sources without any {@code SCRIPT}
     * command the transpilation is so fast (&lt;½ s) that the progress indicator would only
     * flash, so it is skipped and the call runs synchronously.
     * <p>
     * The {@code onDone} callback — if non-null — is always invoked on the EDT once
     * transpilation finishes, receiving {@code true} on success or {@code false} on error.
     *
     * @param isForGrid whether to transpile in grid mode.
     * @param onDone    optional EDT callback receiving the success flag; may be {@code null}.
     */
    public void transpile( boolean isForGrid, Consumer<Boolean> onDone )
    {
        if( isForGrid && ! UtilSys.getConfig().isModule( "grid" ) )
        {
            JTools.alert( "Transpiling for a Grid.\n"+
                          "But the config file used by Glue does not include Grid configuration,\n" +
                          "Make sure you will run this Une script using a proper configuration file\n"+
                          "Configuration file currently used:"+ UtilSys.getConfig().getURI(),
                          this );
        }

        setDividerLocation( 0.65d );
        stop();
        System.setProperty( "grid", (isForGrid ? "true" : "false") );
        getConsole().clear();
        getConsole().appendln( "Mingle Standard Platform Traspiler\n" );

        if( isNeededToSave() )
            save();

        if( fCode == null )
        {
            if( onDone != null ) onDone.accept( false );
            return;
        }

        final URI      uri      = fCode.toURI();
        final Consoler consoler = new Consoler( getConsole() );

        if( SCRIPT_KW.matcher( getText() ).find() )
        {
            JTools.showWaitFrame( "Transpiling..." );

            new SwingWorker<Boolean,Void>()
            {
                @Override
                protected Boolean doInBackground() throws Exception
                {
                    return TranspilerTask.execute( UtilSys.getConfig(), null, consoler, uri );
                }

                @Override
                protected void done()
                {
                    JTools.hideWaitFrame();

                    boolean result = false;

                    try
                    {
                        result = get();
                    }
                    catch( ExecutionException ex )
                    {
                        Throwable cause = ex.getCause();

                        if( cause instanceof IOException || cause instanceof URISyntaxException )
                            JTools.error( (Exception) cause );
                        else
                        {
                            cause.printStackTrace( System.err );
                            getConsole().appendln( "Internal error: transpilation aborted", UtilANSI.nRED );
                        }
                    }
                    catch( InterruptedException ex )
                    {
                        Thread.currentThread().interrupt();
                    }

                    if( onDone != null )
                        onDone.accept( result );
                }
            }.execute();
        }
        else
        {
            boolean result = false;

            try
            {
                result = TranspilerTask.execute( UtilSys.getConfig(), null, consoler, uri );
            }
            catch( IOException | URISyntaxException ioe )
            {
                JTools.error( ioe );
            }
            catch( Exception exc )
            {
                exc.printStackTrace( System.err );
                getConsole().appendln( "Internal error: transpilation aborted", UtilANSI.nRED );
            }

            if( onDone != null )
                onDone.accept( result );
        }
    }

    public boolean isRunning()
    {
        return (procExEn != null);
    }

    public boolean run( boolean bUseFakedCtrl, ILogger.Level level )
    {
        if( isRunning() )
            return true;

        try
        {
            File fModel = new File( UtilIO.getPath( fCode ), UtilIO.getName( fCode ) +".model" );

            setDividerLocation( 0.65d );
            getConsole().clear();

            procExEn = Util.runStick( fModel, createConfigFile( bUseFakedCtrl, level ) );

            if( procExEn == null )
                JTools.error( "Unable to start local internal ExEn.\nIt could be there is another instance of ExEn already\nrunning with same communcations configuration.", this );
            else
                Util.catchOutput( procExEn, (str) -> getConsole().append( str ) );
        }
        catch( Exception ioe )
        {
            JTools.error( ioe );
        }

        return procExEn != null;
    }

    public UneEditorUnit stop()
    {
        if( isRunning() )
        {
            Util.killProcess( procExEn );
            procExEn = null;
        }

        return this;
    }

    public boolean isNeededToSave()
    {
        return getEditor().isDeepChanged();
    }

    public UneEditorUnit save()
    {
        String code = getEditor().getText();   // To save CPU

        if( (fCode == null) || getEditor().isDeepChanged() )     // Needed to be checked again
        {
            fCode = JTools.fileSaver( JTools.FileType.Une, fCode, code );

            getEditor().setStyle( UtilIO.getExtension( fCode ) );
        }

        getEditor().saved();

        if( fCode != null )
            fileLastModified = fCode.lastModified();

        return this;
    }

    /**
     * Returns {@code true} if the file on disk has a different {@code lastModified}
     * timestamp than when it was last loaded or saved by this editor unit.
     *
     * @return {@code true} if an external change is detected.
     */
    public boolean hasExternalChanges()
    {
        return (fCode != null) && fCode.exists() && (fCode.lastModified() != fileLastModified);
    }

    /**
     * Reloads the file content from disk, replacing whatever is in the editor.
     * Resets the change flag and the stored {@code lastModified} timestamp.
     *
     * @throws IOException if the file cannot be read.
     */
    public void reloadFromDisk() throws IOException
    {
        String content = UtilIO.getAsText( fCode );

        getEditor().setText( content );   // setText() already resets isChanged and sOriginalText
        fileLastModified = fCode.lastModified();
    }

    /**
     * Updates the stored {@code lastModified} timestamp to the current on-disk value
     * without reloading the content. Prevents re-prompting for the same external modification.
     */
    public void snapshotLastModified()
    {
        if( fCode != null )
            fileLastModified = fCode.lastModified();
    }

    //------------------------------------------------------------------------//

    private File createConfigFile( boolean bUseFakedCtrl, ILogger.Level level ) throws IOException
    {
        JsonArray jaConfig = Json.parse( UtilSys.getConfig().toStrJSON() ).asArray();

        for( int n = 0; n < jaConfig.size(); n++ )
        {
            JsonObject jo = jaConfig.get( n ).asObject();
            String module = jo.getString( "module", null );

            if( module == null )
                continue;

            switch( module )
            {
                case "grid":
                    jaConfig.remove( n-- );
                    break;
                case "common":
                    jo.add( "log_level", level.toString().toLowerCase() );
                    break;
                case "exen":
                    jo.add( "faked_drivers", bUseFakedCtrl );
                    break;
                case "network":
                    JsonValue net = getNet();
                    if( net != null )
                        jo.add( "servers", net );
                    break;
            }
        }

        return UtilIO.newFileWriter()
                     .setTemporal( "json" )
                     .replace( jaConfig.toString() );
    }

    private JsonValue getNet()
    {
        try
        {
            return  UtilJson.parse( "["+
                                    "  {"+
                                    "     \"name\"   : \"Plain Socket\","+
                                    "     \"init\"   : { \"port\": "+ nSocketPort.incrementAndGet() +", \"ssl\": false, \"allow\": \"intranet\" },"+
                                    "     \"builder\": \"com.peyrona.mingle.network.socket.PlainSocketServer\","+
                                    "     \"uris\"   : [\"file://{*home.lib*}network.jar\"]"+
                                    "  }"+
                                    ']').asArray();
        }
        catch( Exception ioe )
        {
            return null;    // This can not happen
        }
    }

    //------------------------------------------------------------------------//
    // INNER CLASS
    //------------------------------------------------------------------------//
    private static final class Consoler extends PrintWriter
    {
        private final ConsolePanel console;

        Consoler( ConsolePanel cp )
        {
            super( new ByteArrayOutputStream() );

            console = cp;
        }

        @Override
        public void print( String s )
        {
            SwingUtilities.invokeLater( () -> console.append( s ) );
        }

        @Override
        public void println( String s )
        {
            SwingUtilities.invokeLater( () -> console.appendln( s ) );
        }
    }
}