
package com.peyrona.mingle.controllers;

import com.peyrona.mingle.lang.MingleException;
import com.peyrona.mingle.lang.interfaces.IController;
import com.peyrona.mingle.lang.interfaces.ILogger;
import com.peyrona.mingle.lang.interfaces.exen.IRuntime;
import com.peyrona.mingle.lang.japi.UtilColls;
import com.peyrona.mingle.lang.japi.UtilIO;
import com.peyrona.mingle.lang.japi.UtilSys;
import com.peyrona.mingle.lang.japi.UtilType;
import com.peyrona.mingle.lang.xpreval.functions.date;
import com.peyrona.mingle.lang.xpreval.functions.time;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.xssf.usermodel.XSSFCell;
import org.apache.poi.xssf.usermodel.XSSFCellStyle;
import org.apache.poi.xssf.usermodel.XSSFFont;
import org.apache.poi.xssf.usermodel.XSSFRow;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

/**
 * This Controller writes values to a file using MS Excel format.
 * <p>
 * See note at ControllerBase.
 *
 * @author Francisco José Morero Peyrona
 *
 * Official web site at: <a href="https://github.com/peyrona/mingle">https://github.com/peyrona/mingle</a>
 */
public final class   Excel
             extends ControllerBase
{
    private File         file  = null;
    private XSSFWorkbook book  = null;
    private XSSFSheet    sheet = null;

    //------------------------------------------------------------------------//

    @Override
    public void set( String deviceName, Map<String,Object> mapConfig, IController.Listener listener )
    {
        setName( deviceName );
        setListener( listener );     // Must be at begining: in case an error happens, Listener is needed

        String    sSheetName = (String) mapConfig.get( "sheetname" );   // Optional
        String    sFileName  = (String) mapConfig.get( "file" );
        List<URI> lstURL     = new ArrayList<>();

        try
        {
            lstURL = UtilIO.expandPath( sFileName );
        }
        catch( IOException | URISyntaxException ioe )
        {
            // Nothing to do
        }

        if( lstURL.size() != 1 )
        {
            sendIsInvalid( "Invalid file name: "+ sFileName );
        }
        else
        {
            synchronized( this )
            {
                file = UtilIO.addExtension( new File( lstURL.get(0) ), ".xlxs" );

                book  = new XSSFWorkbook();
                sheet = book.createSheet();
            }

            if( sSheetName != null )
                book.setSheetName( book.getSheetIndex( sheet ), sSheetName );

            String sHeads = (String) mapConfig.get( "heads" );   // Optional

            if( sHeads != null )
                writeHead( book, sheet, UtilColls.toMap( sHeads ) );

            setValid( true );
        }
    }

    @Override
    public void read()
    {
        // Nothing to do.
        // DO NOT DO THIS --> sendIsNotReadable();
    }

    @Override
    public void write( Object deviceValue )    // deviceValue is a string like this: "<col_name> = <col_value>, ..."
    {
        if( isFaked || isInvalid() || (deviceValue == null) || (book == null) || (sheet == null) )
            return;

        UtilSys.execute( getClass().getName(),
                         () ->
                            {
                                Map<String,String> map = UtilColls.toMap( deviceValue.toString() );
                                writeRow( book, sheet, map );
                                flush();
                                sendReaded( deviceValue );
                            } );
    }

    @Override
    public void start( IRuntime rt )
    {
        super.start( rt );

        String use_disk = "use_disk";

        if( ! rt.getFromConfig( "exen", use_disk, true ) )
        {
            sendIsInvalid( use_disk +" flag is off: can not use File System" );
            stop();
        }
    }

    @Override
    public void stop()
    {
        flush();
        close();

        file  = null;
        book  = null;
        sheet = null;

        super.stop();
    }

    //------------------------------------------------------------------------//

    /**
     *
     * @param book
     * @param sheet
     * @param nCol Column ordinal position: 1 based
     * @param sCaption Column caption
     */
    private void writeHead( XSSFWorkbook book, XSSFSheet sheet, Map<String,String> mapColValues )
    {
        XSSFRow header = sheet.getRow( 0 );

        if( header == null )
            header = sheet.createRow( 0 );

        XSSFFont font = ((XSSFWorkbook) book).createFont();
                 font.setFontName( "Arial" );
                 font.setBold( true );

        XSSFCellStyle headerStyle = book.createCellStyle();
                      headerStyle.setFillForegroundColor( IndexedColors.AQUA.getIndex() );
                      headerStyle.setFillPattern( FillPatternType.SOLID_FOREGROUND );
                      headerStyle.setFont( font );

        for( Map.Entry<String,String> entry : mapColValues.entrySet() )
        {
            int      nCol       = UtilType.toInteger( entry.getKey() );
            XSSFCell headerCell = header.getCell( nCol - 1 );

            if( headerCell == null )
                headerCell = header.createCell( nCol - 1 );

            headerCell.setCellValue( entry.getValue() );
            headerCell.setCellStyle( headerStyle );
        }
    }

    private void writeRow( XSSFWorkbook book, XSSFSheet sheet, Map<String,String> mapColValues )
    {
        int nLastRow = sheet.getLastRowNum();

        XSSFCellStyle style = book.createCellStyle();
                      style.setWrapText( true );

        XSSFRow  row = sheet.createRow( nLastRow + 1 );
        XSSFCell cell;

        for( Map.Entry<String,String> entry : mapColValues.entrySet() )
        {
            int    nCol = UtilType.toInteger( entry.getKey() );
            Object oVal = UtilType.toUne( entry.getValue() );

            cell = row.createCell( nCol - 1 );
            cell.setCellStyle( style );

                 if( oVal instanceof Boolean ) cell.setCellValue( (Boolean) oVal );
            else if( oVal instanceof String  ) cell.setCellValue( (String)  oVal );
            else if( oVal instanceof Float   ) cell.setCellValue( ((Float)  oVal).doubleValue() );
            else if( oVal instanceof date    ) cell.setCellValue( ((date)   oVal).asLocalDate() );
            else if( oVal instanceof time    ) cell.setCellValue( ((time)   oVal).toString() );
            else
                sendWriteError(oVal, new MingleException( oVal.getClass().getSimpleName() +" invalid Excel value" ) );
        }
    }

    private void flush()
    {
        if( isInvalid() || (book == null) || (file == null) )
            return;

        try
        {
            book.write( new FileOutputStream( file ) );
        }
        catch( IOException ioe )
        {
            sendWriteError( "Flushing the book: "+ file, ioe );
        }
    }

    private void close()
    {
        if( isValid() && (book != null) )
        {
            try
            {
                book.close();
            }
            catch( IOException ioe )
            {
                sendGenericError( ILogger.Level.SEVERE, "Closing file: "+ ioe.getMessage() );
            }
        }
    }
}