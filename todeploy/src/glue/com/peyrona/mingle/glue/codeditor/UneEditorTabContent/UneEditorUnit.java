
package com.peyrona.mingle.glue.codeditor.UneEditorTabContent;

import com.eclipsesource.json.JsonValue;
import com.peyrona.mingle.glue.ConsolePanel;
import com.peyrona.mingle.glue.JTools;
import com.peyrona.mingle.glue.Util;
import com.peyrona.mingle.lang.MingleException;
import com.peyrona.mingle.lang.interfaces.IConfig;
import com.peyrona.mingle.lang.interfaces.ILogger;
import com.peyrona.mingle.lang.japi.Config;
import com.peyrona.mingle.lang.japi.UtilANSI;
import com.peyrona.mingle.lang.japi.UtilIO;
import com.peyrona.mingle.lang.japi.UtilJson;
import com.peyrona.mingle.lang.japi.UtilStr;
import com.peyrona.mingle.lang.japi.UtilSys;
import com.peyrona.mingle.tape.TranspilerTask;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.net.URISyntaxException;
import java.util.function.BiConsumer;
import javax.swing.JSplitPane;
import javax.swing.SwingUtilities;

/**
 * A Split Panel with an editor at top and a text area (to show transpiler and run output) at bottom.
 */
public final class UneEditorUnit extends JSplitPane
{
    private static int nSocketPort = 2048;

    private File    fCode     = null;
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
        setTopComponent( new UneEditorPane( sCode ) );
        setBottomComponent( new ConsolePanel() );

        getEditor().setText( sCode );
        getEditor().grabFocus();

        addComponentListener( new ComponentAdapter()
        {
            @Override
            public void componentShown( ComponentEvent ce )     // Invoked when this component becames visible
            {
                if( is1stTime && (ce.getComponent() == UneEditorUnit.this) )
                {
                    UtilSys.execute( getClass().getName(),
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
        setDividerLocation( .65d );
        stop();
        System.setProperty( "grid", (isForGrid  ? "true" : "false") );
        getConsole().clear();
        getConsole().appendln( "Mingle Standard Platform Traspiler\n" );

        try
        {
            return TranspilerTask.execute( UtilSys.getConfig(), null, new Printer( getConsole() ), fCode.toURI() );
        }
        catch( IOException | URISyntaxException ioe )
        {
            JTools.error( ioe );
        }
        catch( Exception exc )
        {
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

        File fModel = new File( UtilIO.getPath( fCode ), UtilIO.getName( fCode ) +".model" );

        try
        {
            setDividerLocation( .65d );
            getConsole().clear();

            procExEn = Util.runStick( fModel, createConfigFile( bUseFakedCtrl, level ) );

            Util.catchOutput( procExEn, (str) -> getConsole().append( str ) );

            if( procExEn == null )
            {
                JTools.error( "Unable to start local internal ExEn.\nIt could be there is another instance of ExEn already\nrunning with same communcations configuration.", this );
                return false;
            }
        }
        catch( IOException ioe )
        {
            JTools.error(new MingleException( "Unable to start local internal ExEn.", ioe ), this );
        }

        return true;
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
                                    "     \"init\"   : { \"port\": "+ (nSocketPort++) +", \"ssl\": false, \"allow\": \"intranet\" },"+
                                    "     \"builder\": \"com.peyrona.mingle.network.plain.PlainSocketServer\","+
                                    "     \"uris\"   : [\"file://{*home.lib*}network.jar\"]"+
                                    "  }"+
                                    "]").asArray();
        }
        catch( Exception ioe )
        {
            return null;    // This can not happen
        }
    }

    //------------------------------------------------------------------------//
    // INNER CLASS
    //------------------------------------------------------------------------//
    private static final class Printer extends PrintWriter
    {
        private final ConsolePanel console;

        Printer( ConsolePanel cp )
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