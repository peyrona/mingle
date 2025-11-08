
package com.peyrona.mingle.controllers;

import com.peyrona.mingle.lang.MingleException;
import com.peyrona.mingle.lang.interfaces.IController;
import com.peyrona.mingle.lang.interfaces.ILogger;
import com.peyrona.mingle.lang.interfaces.exen.IRuntime;
import com.peyrona.mingle.lang.japi.UtilColls;
import com.peyrona.mingle.lang.japi.UtilIO;
import com.peyrona.mingle.lang.japi.UtilSys;
import com.peyrona.mingle.lang.japi.UtilType;
import com.peyrona.mingle.lang.japi.UtilUnit;
import com.peyrona.mingle.lang.xpreval.functions.date;
import com.peyrona.mingle.lang.xpreval.functions.time;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
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
    private File         file       = null;
    private XSSFWorkbook book       = null;
    private XSSFSheet    sheet      = null;
    private int          nWrites    = 0;     // Counter for pending writes
    private long         nLastFlush = 0;     // Timestamp of the last flush operation

    private static final int    nFLUSH_TIMEOUT     = 180 * 1000;       // Time limit (in milliseconds) for flushing
    private static final String KEY_WRITE_INTERVAL = "writeinterval";  // Configured number of writes before flushing

    //------------------------------------------------------------------------//

    @Override
    public void set( String deviceName, Map<String,Object> mapConfig, IController.Listener listener )
    {
        setName( deviceName );
        setListener( listener );     // Must be at begining: in case an error happens, Listener is needed

        String    sFileName  = (String) mapConfig.get( "file" );        // Mandatory
        String    sSheetName = (String) mapConfig.get( "sheetname" );   // Optional
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
            return;
        }

        if( isFaked() )
        {
            setValid( true );
            return;
        }

        synchronized( this )
        {
            file  = UtilIO.addExtension( new File( lstURL.get(0) ), ".xlsx" );
            book  = new XSSFWorkbook();
            sheet = book.createSheet();
        }

        if( sSheetName != null )
            book.setSheetName( book.getSheetIndex( sheet ), sSheetName );

        String sHeads = (String) mapConfig.get( "heads" );   // Optional

        if( sHeads != null )
            writeHead( book, sheet, UtilColls.toMap( sHeads ) );

        // Read flush interval from configuration
        Integer nWriteInterval = (Integer) mapConfig.get( KEY_WRITE_INTERVAL );

        if( nWriteInterval == null )
            nWriteInterval = 5;

        set( KEY_WRITE_INTERVAL, UtilUnit.setBetween( 1, nWriteInterval, Integer.MAX_VALUE ) );

        setValid( true );
        set( mapConfig );     // Can be done because mapConfig values are not modified
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
        if( isFaked() || isInvalid() || (deviceValue == null) || (book == null) || (sheet == null) )
            return;

        UtilSys.execute( getClass().getName(),
                         () ->
                            {
                                synchronized( Excel.this )     // Ensure thread-safe access to workbook and counters
                                {
                                    Map<String,String> map = UtilColls.toMap( deviceValue.toString() );
                                    writeRow( book, sheet, map );

                                    int  interval = (int) get( KEY_WRITE_INTERVAL );
                                    long elapsed  = System.currentTimeMillis() - nLastFlush;

                                    // Flush if write counter reaches the interval
                                    if( (++nWrites >= interval) || (elapsed >= nFLUSH_TIMEOUT) )
                                    {
                                        flush();
                                        nWrites    = 0;
                                        nLastFlush = System.currentTimeMillis();
                                    }

                                    sendReaded( deviceValue );
                                }
                            } );
    }

    @Override
    public void start( IRuntime rt )
    {
        if( isInvalid() )
            return;

        super.start( rt );

        if( ! useDisk( true ) )
            stop();

        nLastFlush = System.currentTimeMillis();
    }

    @Override
    public void stop()
    {
        flush(); // Flush any remaining pending writes
        close();

        nWrites = 0;
        file    = null;
        book    = null;
        sheet   = null;

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
            // Write the current workbook to a byte array, close it, and then write to file.
            // This pattern avoids InvalidOperationException when writing multiple times.
            ByteArrayOutputStream baos = new ByteArrayOutputStream();

            book.write( baos );
            book.close();

            try( FileOutputStream fos = new FileOutputStream( file ) )
            {
                baos.writeTo( fos );
            }

            // Re-open the workbook from the byte array to continue modifications
            book = new XSSFWorkbook( new ByteArrayInputStream( baos.toByteArray() ) );

            if( book.getNumberOfSheets() > 0 )
                sheet = book.getSheetAt( 0 );

            nLastFlush = System.currentTimeMillis(); // Update the last flush time
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