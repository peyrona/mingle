
package com.peyrona.mingle.glue.codeditor.UneEditorTabContent;

import com.eclipsesource.json.JsonValue;
import com.peyrona.mingle.glue.ConsolePanel;
import com.peyrona.mingle.glue.JTools;
import com.peyrona.mingle.glue.Util;
import com.peyrona.mingle.lang.interfaces.IConfig;
import com.peyrona.mingle.lang.interfaces.ILogger;
import com.peyrona.mingle.lang.japi.Config;
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
import java.net.URISyntaxException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiConsumer;
import javax.swing.Action;
import javax.swing.JSplitPane;
import javax.swing.SwingUtilities;
import org.fife.ui.rsyntaxtextarea.RSyntaxTextAreaEditorKit;

/**
 * A Split Panel with an editor at top and a PanelConsole (to show transpiler and run output) at bottom.
 */
public final class UneEditorUnit extends JSplitPane
{
    private static final AtomicInteger nSocketPort = new AtomicInteger( 20000 );   // High enough port number to no conflict with System ports

    private File    fCode     = null;
    private boolean isFolded  = false;
    private Process procExEn  = null;
    private boolean is1stTime = true;

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
                    UtilSys.execute( null,
                                     250,   // Needed
                                     () -> SwingUtilities.invokeLater( () -> UneEditorUnit.this.setDividerLocation( 1d ) ) );

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
        this.fCode = file;

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

    public boolean transpile( boolean isForGrid )
    {
        setDividerLocation( 0.65d );
        stop();
        System.setProperty( "grid", (isForGrid  ? "true" : "false") );
        getConsole().clear();
        getConsole().appendln( "Mingle Standard Platform Traspiler\n" );

        if( isNeededToSave() )
            save();

        try
        {
            return TranspilerTask.execute( UtilSys.getConfig(), null, new Consoler( getConsole() ), fCode.toURI() );
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

        return false;
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

        return this;
    }

    //------------------------------------------------------------------------//

    private File createConfigFile( boolean bUseFakedCtrl, ILogger.Level level ) throws IOException
    {
        File file = UtilIO.newFileWriter()
                          .setTemporal( "json" )
                          .replace( UtilSys.getConfig().toStrJSON() );    // Saves current Config as it is

        IConfig config = new Config().load( file.toString() )
                                     .set( "common" , "log_level"    , level )
                                     .set( "exen"   , "faked_drivers", bUseFakedCtrl )
                                     .set( "network", "servers"      , getNet() );

        UtilIO.newFileWriter()
              .setFile( file )
              .replace( config.toStrJSON() );

        return file;
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
            console.append( s );
        }

        @Override
        public void println( String s )
        {
            console.appendln( s );
        }
    }
}