
package com.peyrona.mingle.candi.unec.transpiler;

import com.peyrona.mingle.candi.unec.parser.ParseBase;
import com.peyrona.mingle.candi.unec.parser.ParseDevice;
import com.peyrona.mingle.candi.unec.parser.ParseDriver;
import com.peyrona.mingle.candi.unec.parser.ParseRule;
import com.peyrona.mingle.candi.unec.parser.ParseScript;
import com.peyrona.mingle.lang.interfaces.ICandi;
import com.peyrona.mingle.lang.japi.UtilStr;
import com.peyrona.mingle.lang.japi.UtilSys;
import java.io.PrintWriter;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.UUID;

/**
 *
 * <p>
 * Errors are sent to System.err and transpiled code is sent to System.out.
 *
 * @author Francisco José Morero Peyrona
 *
 * Official web site at: <a href="https://mingle.peyrona.com">https://mingle.peyrona.com</a>
 */
final class Writer
{
    /**
     *
     *
     * @param pwCode Output stream to write transpiled code
     * @param pwInfo Output stream to write information and errors
     * @param tus
     */
    void write( PrintWriter pwCode, PrintWriter pwInfo, List<TransUnit> tus )
    {
        pwCode.println( "{\"transpiler\":\"MSP Transpiler ver."+ UtilSys.getVersion( Transpiler.class ) +"\",\n"+
                         "\"code-version\":\"1.0\",\n"+
                         "\"uid\":\""+ UUID.randomUUID().toString() +"\",\n"+
                         "\"generated\":\""+ LocalDateTime.now().format( DateTimeFormatter.ISO_LOCAL_DATE_TIME ) +"\",\n"+
                         "\"commands\":[" );

        for( int n = 0; n < tus.size(); n++ )
        {
            TransUnit unit = tus.get( n );

            String sURI  = ((unit.sourceUnit.uri == null) ? "From source code" : unit.sourceUnit.uri);
            String sLine = UtilStr.rightPad( "", '-', sURI.length() );

            pwInfo.println( sLine );
            pwInfo.println( sURI  );
            pwInfo.println( sLine );
            pwInfo.println( unit.getCommands().size() + " commands transpiled" );

            if( unit.hasErrors() )
            {
                if( unit.sourceUnit.error != null )              // If there is an error accessing the URI (file) ...
                {
                    pwInfo.println( unit.sourceUnit.error );
                }
                else if( ! unit.getErrors().isEmpty() )          // ... there can not be compilation errors
                {
                    String[] asSource = unit.sourceUnit.code.split( "\\R" );

                    for( ICandi.IError error : unit.getErrors() )
                        pwInfo.println( showError( error, asSource ) );

                    pwInfo.println();
                }
            }
            else
            {
                pwInfo.println( "0 errors found\n" );

                if( areThereCmds2PrintIn( unit ) )
                {
                    makeTUs( unit.getCommands(), pwCode );

                    if( (n < (tus.size()-1)) && areThereCmds2PrintAfter( tus, n ) )
                        pwCode.println( ',' );
                }
            }
        }

        pwCode.println( "\n]\n}" );
        pwCode.flush();
    }

    //------------------------------------------------------------------------//
    // PRIVATE SCOPE

    private void makeTUs( List<ParseBase> lstUnits, PrintWriter ps )
    {
        for( int n = 0; n < lstUnits.size(); n++ )
        {
            String sOut = lstUnits.get( n ).serialize();

            if( sOut != null )
            {
                ps.print( sOut );

                if( _areThereCmds2PrintAfter_( lstUnits, n ) )
                    ps.println( ',' );
            }
        }
    }

    private boolean areThereCmds2PrintIn( TransUnit tu )
    {
        return _areThereCmds2PrintAfter_( tu.getCommands(), -1 );
    }

    private boolean areThereCmds2PrintAfter( List<TransUnit> list, int ndx )
    {
        for( int n = ndx + 1; n < list.size(); n++ )
        {
            if( areThereCmds2PrintIn( list.get( n ) ) )
                return true;
        }

        return false;
    }

    private String showError( ICandi.IError error, String[] aSourceLines )
    {
        if( error.line() < 1 )
            return "Error [lin:?,col:?]: "+  error.message();

        if( error.line() > aSourceLines.length )
            return error.message() +"[lin:<Invalid>,col<Invalid]";

        String sLine = aSourceLines[ error.line() -1 ];    // -1 because line is 1 based

        if( (! sLine.isEmpty()) && sLine.charAt( 0 ) != '\n' )      // If not already starts with \n
            sLine = '\n'+ sLine;

        if( ! UtilStr.isLastChar( sLine, '\n' ) )          // If not already ends with \n
            sLine += '\n';

        String sCol = (error.column() < 1) ? "?" : Integer.toString( error.column() );
        String sErr = "Error [lin:"+ error.line() +",col:"+ sCol +"]: "+ error.message() + sLine;

        if( error.column() > 0 )
            sErr += UtilStr.leftPad( "^", ' ', error.column() );

        return sErr;
    }

    private boolean _areThereCmds2PrintAfter_( List<ParseBase> list, int ndx )
    {
        for( int n = ndx + 1; n < list.size(); n++ )
        {
            ParseBase cmd = list.get( n );

            if( cmd instanceof ParseDevice ||
                cmd instanceof ParseDriver ||
                cmd instanceof ParseScript ||
                cmd instanceof ParseRule )
            {
                return true;
            }
        }

        return false;
    }
}