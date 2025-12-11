
package com.peyrona.mingle.glue.codeditor.UneEditorTabContent;

import com.peyrona.mingle.glue.JTools;
import com.peyrona.mingle.lang.japi.UtilColls;
import com.peyrona.mingle.lang.japi.UtilStr;
import com.peyrona.mingle.lang.japi.UtilUnit;
import java.awt.Color;
import java.awt.Font;
import java.awt.event.ActionEvent;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.util.Arrays;
import java.util.function.BiConsumer;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.swing.AbstractAction;
import javax.swing.Action;
import javax.swing.KeyStroke;
import javax.swing.SwingUtilities;
import javax.swing.event.CaretEvent;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.text.BadLocationException;
import org.fife.ui.rsyntaxtextarea.RSyntaxDocument;
import org.fife.ui.rsyntaxtextarea.RSyntaxTextArea;
import org.fife.ui.rsyntaxtextarea.SyntaxConstants;
import org.fife.ui.rsyntaxtextarea.SyntaxScheme;
import org.fife.ui.rsyntaxtextarea.Token;
import org.fife.ui.rtextarea.RTextScrollPane;
import org.fife.ui.rtextarea.SearchContext;
import org.fife.ui.rtextarea.SearchEngine;

/**
 * This class is a special JScrollPane (RTextScrollPane) containing a RSyntaxTextArea and the
 * methods to interact with the RSyntaxTextArea.<br>
 * <br>
 * RSyntaxTextArea:
 * <ul>
 *     <li>Project home: https://github.com/bobbylight/RSyntaxTextArea</li>>
 *     <li>New language: https://github.com/bobbylight/rsyntaxtextarea/wiki</li>
 * </ul>
 *
 * @author Francisco José Morero Peyrona
 *
 * Official web site at: <a href="https://github.com/peyrona/mingle">https://github.com/peyrona/mingle</a>
 */
public final class UneEditorPane extends RTextScrollPane
{
    private final RSyntaxTextArea rsta;
    private final DumbSense       dumbSense;
    private       boolean         isChanged = false;
    private       String          sOriginalText;

    //------------------------------------------------------------------------//
    // PACKAGE SCROPE CONSTRUCTORS  (use UneEditorUnit::newEditor(...))

    UneEditorPane( String code )
    {
        super( new RSyntaxTextArea() );

        rsta = (RSyntaxTextArea) getViewport().getView();
        rsta.setSyntaxEditingStyle( SyntaxConstants.SYNTAX_STYLE_NONE );
        rsta.setLineWrap( false );
        rsta.setTabSize( 4 );
        rsta.setTabsEmulated( true );
        rsta.setCodeFoldingEnabled( true );
        rsta.setClearWhitespaceLinesEnabled( true );
        rsta.setAntiAliasingEnabled( true );
        rsta.setFont( rsta.getFont().deriveFont( Font.PLAIN, 11 ) );
        rsta.setHyperlinksEnabled( true );
     // NEXT: no funciona
     // Mirar aquí: https://github.com/bobbylight/RSyntaxTextArea/wiki
     //             https://www.youtube.com/watch?v=U9nQsA7Bvww
        rsta.setBracketMatchingEnabled( true );
      //textArea.setAnimateBracketMatching( true );
        rsta.setMatchedBracketBGColor( Color.darkGray );
        rsta.setMatchedBracketBorderColor( Color.yellow.darker() );
        rsta.setPaintMatchedBracketPair( true );
        rsta.setInsertPairedCharacters( true );
      //rsta.setShowMatchedBracketPopup( true );
      //rsta.setForeground( Color.red );
        rsta.setBackground( Color.darkGray.darker().darker() );
        rsta.setCaretColor( Color.gray.brighter() );
        rsta.setSelectionColor( new Color( 48, 86, 93 ) );
        rsta.setCurrentLineHighlightColor( Color.darkGray.darker() );
        rsta.setSelectedTextColor( Color.yellow );
        rsta.setUseSelectedTextColor( true );
        rsta.setMarkAllHighlightColor( new Color( 75,56,101 ) );
        rsta.setMarkAllOnOccurrenceSearches( true );
     // rsta.setMarkOccurrences( true );             No sé pa qué sirve
     // rsta.setMarkOccurrencesColor( Color.red );   No sé pa qué sirve
        //----------------------------------------------------

        SyntaxScheme scheme = rsta.getSyntaxScheme();
                     scheme.getStyle( Token.COMMENT_EOL                 ).foreground = Color.gray;
                     scheme.getStyle( Token.FUNCTION                    ).foreground = Color.pink;
                     scheme.getStyle( Token.IDENTIFIER                  ).foreground = new Color( 162, 208, 244 );
                     scheme.getStyle( Token.LITERAL_NUMBER_DECIMAL_INT  ).foreground = new Color( 199,  80,  97 );
                     scheme.getStyle( Token.LITERAL_NUMBER_FLOAT        ).foreground = new Color( 199,  80,  97 );
                     scheme.getStyle( Token.LITERAL_BOOLEAN             ).foreground = new Color( 193, 122,  27 );
                     scheme.getStyle( Token.LITERAL_STRING_DOUBLE_QUOTE ).foreground = new Color(  93, 152,  96 );
                     scheme.getStyle( Token.OPERATOR                    ).foreground = new Color( 193, 122,  27 );
                     scheme.getStyle( Token.PREPROCESSOR                ).foreground = new Color( 244, 198, 162 );
                     scheme.getStyle( Token.RESERVED_WORD               ).foreground = new Color(  16, 123, 208 );
                     scheme.getStyle( Token.RESERVED_WORD_2             ).foreground = new Color( 124,  92, 171 );

        ((RSyntaxDocument) rsta.getDocument()).setSyntaxStyle( new UneTokenMaker() );

        getGutter().setLineNumberFont( rsta.getFont() );
        getGutter().setBackground( rsta.getBackground().brighter());
        getGutter().setForeground( rsta.getForeground() );

        //------------------------------------------------------------------------//

         rsta.getDocument()
             .addDocumentListener( new DocumentListener()
                                    {
                                        @Override
                                        public void removeUpdate(  DocumentEvent de ) { isChanged = true; }

                                        @Override
                                        public void insertUpdate(  DocumentEvent de ) { isChanged = true; }

                                        @Override
                                        public void changedUpdate( DocumentEvent de ) { isChanged = true; }
                                    });

        //------------------------------------------------------------------------//
        // Key bindings

        Action actCloneLine = new AbstractAction()
                {
                    @Override
                    public void actionPerformed( ActionEvent ae )
                    {
                        try
                        {
                            int    caret = rsta.getCaretPosition();
                            int    line  = rsta.getLineOfOffset( caret );
                            String[] lines = getText().split( "\n" );
                            String text  = (line >= 0 && line < lines.length) ? lines[line] : "";
                            int    where = rsta.getLineEndOffset( line );

                            rsta.insert( text +'\n', where );
                        }
                        catch( BadLocationException ble )
                        {
                            JTools.alert( "Can not duplicate line: move cursor to a different position", UneEditorPane.this );
                        }
                    }
                };

        KeyStroke ksCloneLineUp = KeyStroke.getKeyStroke( KeyEvent.VK_UP  , KeyEvent.CTRL_DOWN_MASK | KeyEvent.SHIFT_DOWN_MASK );
        KeyStroke ksCloneLineDn = KeyStroke.getKeyStroke( KeyEvent.VK_DOWN, KeyEvent.CTRL_DOWN_MASK | KeyEvent.SHIFT_DOWN_MASK );

        rsta.getInputMap().put( ksCloneLineUp, "ActionCloneLine_Up" );
        rsta.getInputMap().put( ksCloneLineDn, "ActionCloneLine_Dn" );
        rsta.getActionMap().put( "ActionCloneLine_Up", actCloneLine );
        rsta.getActionMap().put( "ActionCloneLine_Dn", actCloneLine );

        //------------------------------------------------------------------------//
        // To show DumbSense (vs IntelliSense) pop up menu

        dumbSense = new DumbSense( rsta );

        rsta.addKeyListener( new KeyAdapter()
                            {
                                @Override
                                public void keyPressed( KeyEvent kpe )
                                {
                                    if( kpe.isControlDown() && kpe.getKeyCode() == KeyEvent.VK_SPACE )
                                        dumbSense.pop();
                                }
                            } );
        //------------------------------------------------------------------------//

        setText( code );
    }

    //------------------------------------------------------------------------//

    @Override
    public void setEnabled( boolean isEnabled )
    {
        super.setEnabled( isEnabled );
        rsta.setEditable( isEnabled );
    }

    @Override
    public void grabFocus()
    {
        SwingUtilities.invokeLater( () -> rsta.grabFocus() );
    }

    //------------------------------------------------------------------------//

    public boolean canUndo()
    {
        return rsta.canUndo();
    }

    public boolean canRedo()
    {
        return rsta.canRedo();
    }

    public UneEditorPane undo()
    {
        rsta.undoLastAction();
        return this;
    }

    public UneEditorPane redo()
    {
        rsta.redoLastAction();
        return this;
    }

    /**
     * Copy into clipboard as RTF the selected text or all if nothing is selected.
     * @return
     */
    public UneEditorPane copyAsRTF()
    {
        if( getSelectedText().isEmpty() )
        {
            int pos = rsta.getCaretPosition();

            rsta.selectAll();
            rsta.copyAsStyledText();
            rsta.select( 0,0 );
            rsta.setCaretPosition( pos );
        }
        else
        {
            rsta.copyAsStyledText();
        }

        return this;
    }

    /**
     * Returns the text in the editor after removing empty lines at begining and at
     * end of the document and after trimming extra spaces at the end of the lines.
     *
     * @return The text in the editor after removing unnecessary chars.
     */
    public String getText()
    {
        StringBuilder sb      = new StringBuilder( 1024 * 16 );
        String[]      asLines = toLines();

        if( UtilColls.isEmpty( asLines ) )
            return "";

        // Removes empty lines at the begining of the script

        while( (asLines.length > 0) &&  asLines[0].isBlank() )
            asLines = Arrays.copyOfRange( asLines, 1, asLines.length );

        // Removes empty lines at the end of the script

        while( (asLines.length > 0) && asLines[ asLines.length - 1 ].isBlank() )
            asLines = Arrays.copyOf( asLines, asLines.length - 1 );

        // Composes the string from the array

        for( String sLine : asLines )
        {
            sb.append( UtilStr.rtrim( sLine ) )    // Removes extra spaces at the end of the line
              .append( UtilStr.sEoL );             // System dependent cEoL
        }                                          // Note that textArea.setClearWhitespaceLinesEnabled( true ) only works on lines contaning only white spaces

        return UtilStr.removeLast( sb, UtilStr.sEoL.length() )
                      .toString();
    }

    public UneEditorPane setText( String code )
    {
        int nPos = getCaretOffset();
            nPos = (nPos < 0) ? 0 : nPos;

        code = UtilStr.isEmpty( code ) ? "" : code;

        rsta.setText( code );        // This places caret at end of file
        sOriginalText = getText();
        rsta.setText( sOriginalText );

        isChanged = false;

        if( ! code.isEmpty() )
            setCaretOffset( nPos );

        return this;
    }

    public UneEditorPane insert( String s )
    {
        rsta.insert( s, getCaretOffset() );
        return this;
    }

    public UneEditorPane setRows( int n )
    {
        rsta.setRows( n );
        return this;
    }

    public UneEditorPane setColumns( int n )
    {
        rsta.setColumns( n );
        return this;
    }

    public UneEditorPane hideLineNumbers()
    {
        setLineNumbersEnabled( false );
        return this;
    }

    public UneEditorPane selectAll()
    {
        rsta.selectAll();
        return this;
    }

    public UneEditorPane saved()
    {
        sOriginalText = getText();
        isChanged = false;

        return this;
    }

    public boolean isChanged()
    {
        return isChanged;
    }

    public boolean isDeepChanged()    // Much more expensive in CPU terms
    {
        return ! getText().equals( sOriginalText );
    }

    public String getSelectedText()
    {
        return (rsta.getSelectedText() == null) ? "" : rsta.getSelectedText();
    }

    // To add extra functionality
    public UneEditorPane addOnTextChanged( final Runnable listener )
    {
        rsta.getDocument()
            .addDocumentListener( new DocumentListener()
                                    {
                                        @Override
                                        public void removeUpdate(  DocumentEvent de ) { listener.run(); }

                                        @Override
                                        public void insertUpdate(  DocumentEvent de ) { listener.run(); }

                                        @Override
                                        public void changedUpdate( DocumentEvent de ) { listener.run(); }
                                    } );

        return this;
    }

    public UneEditorPane addOnCaretChanged( final BiConsumer<Integer,Integer> consumer )
    {
        rsta.addCaretListener( (CaretEvent ce) -> consumer.accept( ce.getDot(), ce.getMark() ) );
        return this;
    }

    public int getCaretOffset()
    {
        return rsta.getCaretPosition();
    }

    public UneEditorPane setCaretOffset( int pos )
    {
        rsta.setCaretPosition( UtilUnit.setBetween( 0, pos, rsta.getText().length() - 1 ) );

        return this;
    }

    /**
     * Zero based (must be like this).
     * @return
     */
    public int getCaretLine()
    {
        return rsta.getCaretLineNumber();
    }

    /**
     * Zero based (must be like this).
     * @return
     */
    public int getCaretColumn()
    {
        try
        {
            return (rsta.getCaretPosition() - rsta.getLineStartOffset( getCaretLine() ));
        }
        catch( BadLocationException ex )
        {
            return -1;
        }
    }

    public UneEditorPane setEditable( boolean isEditable )
    {
        rsta.setEditable( isEditable );
        return this;
    }

    public boolean search( String sWhat, Boolean forward )
    {
        boolean found;

        sWhat = (UtilStr.isEmpty( sWhat ) ? null : sWhat);     // null means to un-highlight found text

        SearchContext context = newSearchContext( sWhat, forward );

        if( forward == null ) found = SearchEngine.markAll( rsta, context ).getMarkedCount() > 0;
        else                  found = SearchEngine.find(    rsta, context ).wasFound();

        if( ! found )
        {
            int pos = rsta.getCaretPosition();

            rsta.select( 0,0 );                // There could be one found string highlighted
            rsta.setCaretPosition( pos );
        }

        return found;
    }

    public UneEditorPane replaceSearch( String sSearch, String sReplace, boolean bAll )
    {
        SearchContext context = newSearchContext( sSearch, true );
                      context.setReplaceWith( sReplace );

        if( bAll )  SearchEngine.replaceAll( rsta, context );
        else        SearchEngine.replace(    rsta, context );

        return this;
    }

    public UneEditorPane replaceSelection( String sWhat )
    {
        rsta.replaceSelection( sWhat );
        return this;
    }

    public UneEditorPane toggleRem()
    {
        String text;
        int    nLineStart = -1;
        int    nLineEnd   = -1;

        try
        {
            if( rsta.getSelectedText() == null )     // Nothing is selected --> REM only current line
            {
                nLineStart = nLineEnd = rsta.getLineOfOffset( rsta.getCaretPosition() );
            }
            else
            {
                nLineStart = rsta.getLineOfOffset( rsta.getSelectionStart() );
                nLineEnd   = rsta.getLineOfOffset( rsta.getSelectionEnd()   );
            }
        }
        catch( BadLocationException ex )
        {
            Logger.getLogger( UneEditorPane.class.getName() ).log( Level.SEVERE, null, ex );
        }

        if( nLineStart > -1 && nLineEnd > -1 )
        {
            try
            {
                int nOffsetStart = rsta.getLineStartOffset( nLineStart );
                int nOffsetEnd   = rsta.getLineEndOffset(   nLineEnd   );

                text = rsta.getText( nOffsetStart, nOffsetEnd - nOffsetStart - 1 );    // -1 is needed

                if( UtilStr.isNotEmpty( text ) )
                {
                    StringBuilder sb    = new StringBuilder( 1024 * 8 );
                    String[]      asLoC = text.split( "\\R" );    // Java 8 provides an “\R” pattern that matches any Unicode line-break sequence
                                                                  // and covers all the newline characters for different operating systems.

                    for( int n = 0; n < asLoC.length; n++ )       // Clarity over efficiency
                    {
                        if( asLoC[n].trim().startsWith( "#" ) )   // replace( '\t', ' ' ) is not needed because ::rsta.setTabsEmulated( true )
                        {
                            String search = (asLoC[n].startsWith( "# " ) ? "# " : "#");   // User could do it manually and add only "#" or could delete the space " " after "#"
                            asLoC[n] = asLoC[n].replaceFirst( search, "" );
                        }
                        else if( ! asLoC[n].isEmpty() )
                        {
                            asLoC[n] = "# " + asLoC[n];
                        }

                        sb.append( asLoC[n] )
                          .append( UtilStr.sEoL );
                    }

                    UtilStr.removeLast( sb, 1 );

                    int n = getCaretOffset();

                    rsta.replaceRange( sb.toString(), nOffsetStart, nOffsetEnd - 1 );    // -1 is needed

                    setCaretOffset( n );
                }
            }
            catch( BadLocationException ble )
            {
                JTools.alert( ble.getMessage(), this );
            }
        }

        return this;
    }

    void setStyle( String sFileExt )
    {
        boolean bChanged = isChanged;

        switch( sFileExt )
        {
            case "json" : rsta.setSyntaxEditingStyle( SyntaxConstants.SYNTAX_STYLE_JSON       ); break;
            case "java" : rsta.setSyntaxEditingStyle( SyntaxConstants.SYNTAX_STYLE_JAVA       ); break;
            case "js"   : rsta.setSyntaxEditingStyle( SyntaxConstants.SYNTAX_STYLE_JAVASCRIPT ); break;
            case "py"   : rsta.setSyntaxEditingStyle( SyntaxConstants.SYNTAX_STYLE_PYTHON     ); break;
            case "html" : rsta.setSyntaxEditingStyle( SyntaxConstants.SYNTAX_STYLE_HTML       ); break;
            case "sql"  : rsta.setSyntaxEditingStyle( SyntaxConstants.SYNTAX_STYLE_SQL        ); break;
            case "model":
            case "une"  : break;
            default     : throw new AssertionError("Unknown file extension: "+ sFileExt );
        }

        if( ! bChanged )
            isChanged = false;    // It is strange, but after changing the style, RSyntaxTextArea says the content is changed.

     // rsta.getSyntaxScheme().getStyle( Token.COMMENT_EOL ).foreground = Color.gray;
     // rsta.getSyntaxScheme().getStyle( Token.COMMENT_EOL ).commentDelim = "#";      // NEXT: NO FUNCIONA
    }

    //------------------------------------------------------------------------//
    // PRIVATE SCOPE

    private SearchContext newSearchContext( String sWhat, Boolean forward )
    {
        SearchContext sc = new SearchContext();
                      sc.setSearchFor( sWhat );
                   // sc.setSearchSelectionOnly( UtilStr.isNotEmpty( getSelectedText() ) );
                      sc.setMarkAll( true );
                      sc.setMatchCase( false );
                      sc.setRegularExpression( false );
                      sc.setSearchWrap( true );
                      sc.setWholeWord( false );

        if( forward != null )
            sc.setSearchForward( forward );

        return sc;
    }

    private String[] toLines()
    {
        return rsta.getText()
                // .replace( "\t", "    " )  // This is not needed because above I did: rsta.setTabsEmulated( true );
                   .split( "\\R" );          // Java 8 provides an “\R” pattern that matches any Unicode line-break sequence
                                             // and covers all the newline characters for different operating systems.
    }
}