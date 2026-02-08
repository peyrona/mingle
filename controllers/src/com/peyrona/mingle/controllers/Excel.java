
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

    private static final String sKEY_FILE_NAME      = "file";           // The file to be read
    private static final int    nFLUSH_TIMEOUT      = 180 * 1000;       // Time limit (in milliseconds) for flushing
    private static final String sKEY_WRITE_INTERVAL = "writeinterval";  // Configured number of writes before flushing

    //------------------------------------------------------------------------//

    @Override
    public void set( String deviceName, Map<String,Object> deviceConf, IController.Listener listener )
    {
        setDeviceName( deviceName );
        setListener( listener );     // Must be at begining: in case an error happens, Listener is needed
        setDeviceConfig( deviceConf );   // Store raw config first, validated values will be stored at the end

        String    sFileName = (String) get( sKEY_FILE_NAME );    // Mandatory
        List<URI> lstURL    = new ArrayList<>();

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

        set( sKEY_FILE_NAME, lstURL.get(0) );

        // Read flush interval from configuration
        Object oWriteInterval = get( sKEY_WRITE_INTERVAL );
        int    nWriteInterval = (oWriteInterval != null) ? ((Number) oWriteInterval).intValue() : 5;

        // Store validated configuration (overwrites raw values with validated ones)
        set( sKEY_WRITE_INTERVAL, UtilUnit.setBetween( 1, nWriteInterval, Integer.MAX_VALUE ) );

        setValid( true );
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

        UtilSys.executor( true )
               .execute( () ->
                            {
                                synchronized( Excel.this )     // Ensure thread-safe access to workbook and counters
                                {
                                    Map<String,String> map = UtilColls.toMap( deviceValue.toString() );
                                    writeRow( book, sheet, map );

                                    int  interval = (int) get( sKEY_WRITE_INTERVAL );
                                    long elapsed  = System.currentTimeMillis() - nLastFlush;

                                    // Flush if write counter reaches the interval
                                    if( (++nWrites >= interval) || (elapsed >= nFLUSH_TIMEOUT) )
                                    {
                                        flush();
                                        nWrites    = 0;
                                        nLastFlush = System.currentTimeMillis();
                                    }

                                    sendChanged( deviceValue );
                                }
                            } );
    }

    @Override
    public boolean start( IRuntime rt )
    {
        if( isInvalid() || (! super.start( rt )) )
            return false;

        if( ! isDiskWritable( true ) )
        {
            stop();
            return false;
        }

        if( ! isFaked() )   // isFaked() is initialized by super:start(...)
        {
            synchronized( this )
            {
                file  = UtilIO.addExtension( new File( get( sKEY_FILE_NAME ).toString() ), ".xlsx" );
                book  = new XSSFWorkbook();
                sheet = book.createSheet();
            }

            String sSheetName = (String) get( "sheetname" );   // Optional

            if( sSheetName != null )
                book.setSheetName( book.getSheetIndex( sheet ), sSheetName );

            String sHeads = (String) get( "heads" );   // Optional

            if( sHeads != null )
                writeHead( book, sheet, UtilColls.toMap( sHeads ) );
        }

        nLastFlush = System.currentTimeMillis();

        return isValid();
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