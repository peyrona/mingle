
package com.peyrona.mingle.candi.unec.transpiler;

import com.peyrona.mingle.candi.unec.parser.ParseBase;
import com.peyrona.mingle.candi.unec.parser.ParseDevice;
import com.peyrona.mingle.candi.unec.parser.ParseDriver;
import com.peyrona.mingle.candi.unec.parser.ParseInclude;
import com.peyrona.mingle.candi.unec.parser.ParseRule;
import com.peyrona.mingle.candi.unec.parser.ParseScript;
import com.peyrona.mingle.candi.unec.parser.ParseUse;
import com.peyrona.mingle.lang.interfaces.ICandi;
import com.peyrona.mingle.lang.interfaces.IXprEval;
import com.peyrona.mingle.lang.japi.UtilColls;
import com.peyrona.mingle.lang.japi.UtilStr;
import com.peyrona.mingle.lang.japi.UtilType;
import com.peyrona.mingle.lang.lexer.CodeError;
import com.peyrona.mingle.lang.lexer.Language;
import com.peyrona.mingle.lang.lexer.Lexeme;
import com.peyrona.mingle.lang.lexer.Lexer;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * A Transpilation Unit.
 *
 * @author Francisco José Morero Peyrona
 *
 * Official web site at: <a href="https://github.com/peyrona/mingle">https://github.com/peyrona/mingle</a>
 */
public final class TransUnit
{
    public  final SourceUnit          sourceUnit;
    private final boolean             bZeroIncs;
    private final List<List<Lexeme>>  lstLexByCmd = new ArrayList<>();    // Lexemes grouped by command
    private final List<ParseBase>     lstCommands = new ArrayList<>();    // Each command has its own Lexeme[] and CodeError[]
    private final List<ICandi.IError> lstLexerErr = new ArrayList<>();    // Errors associated with Lexer
    private final List<ICandi.IError> lstOtherErr = new ArrayList<>();    // Errors added from outside of this class (basically by Checker.java)
    private final IXprEval            xprEval;

    //------------------------------------------------------------------------//
    // CONSTRUCTOR IS PACKAGE SCOPE

    TransUnit( SourceUnit su, IXprEval xprEval )
    {
        Objects.requireNonNull( su );

        this.sourceUnit = su;
        this.xprEval    = xprEval;

        if( sourceUnit.error != null )
        {
            bZeroIncs = false;
        }
        else
        {
            Lexer lexer = new Lexer( sourceUnit.code );

            lstLexerErr.addAll( lexer.getErrors() );

            lstLexByCmd.addAll( UnecTools.splitByCommand( lexer.getLexemes() ) );

            // In 1st phase only INCLUDE and USE commands from received Source-Unit are created

            for( List<Lexeme> lexemes : lstLexByCmd )
            {
                ParseBase cmd     = null;
                String    keyword = lexemes.get(0).text();

                     if( ParseInclude.is( keyword ) )  cmd = new ParseInclude( lexemes );
                else if( ParseUse.is(     keyword ) )  cmd = new ParseUse(     lexemes );

                if( cmd != null )
                    lstCommands.add( cmd );
            }

            List<ParseInclude> lstCmdInc = getCommands( ParseInclude.class );

            boolean bAuto = bZeroIncs = lstCmdInc.isEmpty();

            if( ! bAuto )
            {
                // It can be that the source code has this: 'INCLUDE "*"' (there is at least one Include but this include says that auto include has to used)

                for( ParseInclude pi : lstCmdInc )
                    if( (bAuto = pi.autoInclude()) == true )
                        break;
            }

            if( bAuto )
                lstCommands.add( new ParseInclude( new Lexer( "INCLUDE \"file://{*home.inc*}standard-replaces.une\"" ).getLexemes() ) );
        }
    }

    //------------------------------------------------------------------------//
    // PUBLIC INTERFACE

    public List<ParseBase> getCommands()
    {
        return Collections.unmodifiableList( lstCommands );
    }

    public boolean hasErrors()
    {
        if( sourceUnit.error != null )    // If there is an error in sourceUnit there is no source code to be processed
            return true;

        if( ! lstLexerErr.isEmpty() )
            return true;

        if( ! lstOtherErr.isEmpty() )
            return true;

        for( ParseBase pb : lstCommands )
            if( ! pb.getErrors().isEmpty() )
                return true;

        return false;
    }

    public int errorCount()
    {
        if( sourceUnit.error != null )    // If there is an error in sourceUnit there is no source code to be processed
            return 1;

        int nErr = lstLexerErr.size() +
                   lstOtherErr.size();

        for( ParseBase pb : lstCommands )
            nErr += pb.getErrors().size();

        return nErr;
    }

    public List<ICandi.IError> getErrors()
    {
        List<ICandi.IError> lstErr = new ArrayList<>();

        if( sourceUnit.error != null )    // If there is an error in sourceUnit there is no source code to be processed
        {
            lstErr.add( new CodeError( sourceUnit.error, -1, -1 ) );
            return lstErr;
        }

        lstErr.addAll( lstLexerErr );

        for( ParseBase pb : lstCommands )
            lstErr.addAll( pb.getErrors() );

        lstErr.addAll( lstOtherErr );           // By placing these errs after Parse errs, they appear in a sensefull order

        return lstErr;
    }

    @Override
    public int hashCode()
    {
        int hash = 3;
            hash = 73 * hash + Objects.hashCode( this.sourceUnit );
        return hash;
    }

    @Override
    public boolean equals( Object obj )
    {
        if( this == obj )
        {
            return true;
        }

        if( (obj == null) || (getClass() != obj.getClass()) )
        {
            return false;
        }

        final TransUnit other = (TransUnit) obj;

        return Objects.equals( this.sourceUnit, other.sourceUnit );
    }

    @Override
    public String toString()
    {
        return UtilStr.toString( this );
    }

    //------------------------------------------------------------------------//
    // PACKAGE SCOPE

    boolean autoInclude()
    {
        if( bZeroIncs )     // There is no INCLUDE command in this source code file
            return true;

        // It can be that the source code has this: 'INCLUDE "*"' (there is at least one Include but this include says that auto include has to used)

        for( ParseInclude pi : getCommands( ParseInclude.class ) )
            if( pi.autoInclude() )
                return true;

        return false;
    }

    <T extends ParseBase> List<T> getCommands( Class<T> clazz )
    {
        List<T> list = new ArrayList<>();

        for( ParseBase cmd : lstCommands )
        {
            if( cmd.getClass().isAssignableFrom( clazz ) )
            {
                list.add( (T) cmd );
            }
        }

        return list;
    }

    TransUnit addError( ICandi.IError error )
    {
        assert error != null;

        lstOtherErr.add( error );

        return this;
    }

    TransUnit addErrors( Collection<ICandi.IError> lstErrors )
    {
        for( ICandi.IError error : lstErrors )
            addError( error );

        return this;
    }

    boolean hasCommand( ParseBase cmd )
    {
        return UtilColls.find( lstCommands, (ParseBase pb) -> pb.equals( cmd ) ) != null;
    }

    /**
     * Adds passed command to the internal list.
     *
     * @param cmd Command to be added.
     * @return false if the command was not added (because it already existed).
     */
    boolean addCommand( ParseBase cmd )
    {
        if( hasCommand( cmd ) )
            return false;

        lstCommands.add( cmd );

        return true;
    }

    String toCode()
    {
        StringBuilder sb = new StringBuilder( lstCommands.size() * 1024 );

        for( ParseBase pb : lstCommands )
            sb.append( pb.toCode() ).append( '\n' ).append( '\n' );

        return UtilStr.removeLast( sb, 2 ).toString();
    }

    /**
     * During 1st phase only following can be built: INCLUDE command (because it is needed to
     * find all USE commands) and USE command (because the USE command can be used to change
     * e.g. "SENSOR" into "DEVICE").<br>
     * <br>
     * During this second phase, all other commands are built (INCLUDE and USE are not longer
     * needed but can not be discarded because they will be used to show intermediate code and
     * errors).
     */
    TransUnit doCommands( List<ParseUse> lstUse )    // 2nd phase
    {
        if( sourceUnit.error != null )
            return this;

        // First it is needed to make all replacements: USE ... AS ...

        if( UtilColls.isNotEmpty( lstUse ) )
        {
            for( List<Lexeme> lexemes : lstLexByCmd )
            {
                String key = lexemes.get(0).text();    // keyword

                if( ! ParseInclude.is( key ) &&        // Is not INCLUDE and
                    ! ParseUse.is(     key ) )         // is not USE command
                {
                    for( ParseUse replacer : lstUse )
                        replacer.doUses( lexemes );
                }
            }
        }
        
        ParseUse.clean();    // To free RAM

        // In this second phase all other commands (except INCLUDE and USE) are processed

        for( List<Lexeme> lstCmdLexemes : lstLexByCmd )
        {
            ParseBase cmd = null;
            String    key = lstCmdLexemes.get(0).text();    // cmd keyword

                 if( ParseScript.is(  key ) )  cmd = new ParseScript( lstCmdLexemes );
            else if( ParseDriver.is(  key ) )  cmd = new ParseDriver( lstCmdLexemes );
            else if( ParseDevice.is(  key ) )  cmd = new ParseDevice( doUnits( lstCmdLexemes ), xprEval );    // Only DEVICE and RULE can have
            else if( ParseRule.is(    key ) )  cmd = new ParseRule(   doUnits( lstCmdLexemes ), xprEval );    // temperature and time units
            else if( ParseInclude.is( key ) )  cmd = null;                                                    // Processed at constructor
            else if( ParseUse.is(     key ) )  cmd = null;                                                    // Processed at constructor
            else
            {
                lstLexerErr.add( new CodeError( "Unrecognized command: \""+ key +'"', lstCmdLexemes.get(0) ) );
            }

            if( cmd != null )
                lstCommands.add( cmd );
        }

        lstLexByCmd.clear();   // Not needed any more

        return this;
    }

    //------------------------------------------------------------------------//
    // PRIVATE SCOPE

    /**
     * Convert numbers into their appropriate value considering Une units suffixes:
     * <ul>
     *    <li>Time:  u | t | s | m | h | d  (converts to millis)</li>
     *    <li>Temperature: C | F | K  (converts to Celsius)</li>
     * </ul>
     *
     * Note: suffixes can appear anywhere in the source code (not only in expressions),
     *       this is why the conversions are done here instead of doing it at RULEs.
     */
    private List<Lexeme> doUnits( List<Lexeme> lstLexemes )
    {
        int nLastToken = lstLexemes.size() - 1;

        // Traverses the array from last element to the first one

        for( int n = nLastToken; n > 0; n-- )    // It is not n >= 0 because previous is needed
        {
            Lexeme current  = lstLexemes.get( n );
            Lexeme previous = lstLexemes.get( n-1 );

            if( previous.isNumber() && current.isUnitSuffix() )
            {
                char  suffix = current.text().charAt( 0 );
                float amount = UtilType.toFloat( previous.text() );

                // There only time and temperature units, threfore, this is not needed -> if( Language.isTemperatureSuffix( suffix ) )

                if( Language.isTimeSuffix( suffix ) )  previous.updateUsign( Language.toMillis(  amount, suffix ) );   // This returns an int
                else                                   previous.updateUsign( Language.toCelsius( amount, suffix ) );   // This returns a float

                lstLexemes.remove( n-- );    // Suffix is not needed anymore: remove it (and decrease n)
            }
        }

        return lstLexemes;
    }
}