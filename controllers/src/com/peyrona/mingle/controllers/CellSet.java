
package com.peyrona.mingle.controllers;

import com.peyrona.mingle.lang.interfaces.ICandi;
import com.peyrona.mingle.lang.interfaces.IController;
import com.peyrona.mingle.lang.interfaces.ILogger;
import com.peyrona.mingle.lang.interfaces.IXprEval;
import com.peyrona.mingle.lang.interfaces.commands.IDevice;
import com.peyrona.mingle.lang.interfaces.exen.IEventBus;
import com.peyrona.mingle.lang.interfaces.exen.IRuntime;
import com.peyrona.mingle.lang.japi.Dispatcher;
import com.peyrona.mingle.lang.japi.UtilColls;
import com.peyrona.mingle.lang.japi.UtilStr;
import com.peyrona.mingle.lang.messages.MsgDeviceChanged;
import com.peyrona.mingle.lang.xpreval.functions.ExtraTypeCollection;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/**
 * This Controller can be imagined as a simple spreadsheet: it is an arbitrary
 * set of cells and every cell can contain a value or a formula.<br>
 * Cells have a name to refer to them (a cell is the equivalent to a variable
 * in traditional languages).
 * <p>
 * All Devices referring to a CellDriver, refer to the same Driver instance:
 * the name of the device is the cell's name.
 * </p>
 * This Controller ignores FakeController flag.
 *
 * @author Francisco José Morero Peyrona
 *
 * Official web site at: <a href="https://github.com/peyrona/mingle">https://github.com/peyrona/mingle</a>
 */
public final class   CellSet
             extends ControllerBase
{
    private static final String KEY_VALUE = "value";
    private static final Map<String,CellValue>        map = new ConcurrentHashMap<>();
    private static       Dispatcher<MsgDeviceChanged> dis = null;    // Needed to recalculate new values for all affected cells every time a device changes its value
    private static       IEventBus.Listener           ebl = null;

    //------------------------------------------------------------------------//
    // This controller is always valid

    @Override
    public void set( String deviceName, Map<String,Object> deviceConf, IController.Listener listener )
    {
        setDeviceName( deviceName );                       // Must be 1st
        setListener( listener );                           // Must be at begining: in case an error happens, Listener is needed
        set( deviceConf );
        set( KEY_VALUE, deviceConf.get( KEY_VALUE ) );     // Initial value. It is guarranted to exist because it is REQUIRED and therefore the Transpiler checks it

        // Can not validate the value now because caould be a formula and for thisd case, the Runtime is needed.

        // 'list' and 'pair' classes can be modified (list:add( 5 )) without this driver knowing it.
        // That is why the following is needed.
        if( get( KEY_VALUE ) instanceof ExtraTypeCollection )
        {
            PropertyChangeListener pcl = (PropertyChangeEvent pce) ->
                                        {
                                            sendReaded( CellSet.this.get( KEY_VALUE ) );
                                        };

            ((ExtraTypeCollection) get( KEY_VALUE )).addPropertyChangeListener( pcl );
        }

        setValid( true );
    }

    @Override
    public void start( IRuntime rt )
    {
        super.start( rt );

        CellValue cv = new CellValue( get( KEY_VALUE ) );    // Previously saved (at ::set(...)) for this CellSet instance

             if( cv.hasErrors() )        sendIsInvalid( "Formula has errors: unusable device" );
        else if( hasCircularRef( cv ) )  sendIsInvalid( "Formula has circular references: unusable device" );
        else                             map.put( getDeviceName(), cv );

        if( isInvalid() )
            return;

        synchronized( CellSet.class )
        {
            if( dis != null )
                return;

            Consumer<MsgDeviceChanged> consumer = (msg) ->
                {
                    for( Map.Entry<String,CellValue> entry : map.entrySet() )
                    {
                        if( entry.getValue().isFormula() )
                        {
                            Object oldVal = entry.getValue().read();
                            Object newVal = entry.getValue().onDeviceChanged( msg.name, msg.value );

                            if( ! Objects.equals( newVal, oldVal ) )    // Needed to avoid a feedback spiral
                                sendChanged( entry.getKey(), newVal );
                        }
                    }
                };

            dis = new Dispatcher<>( consumer,
                                    (exc) -> sendGenericError( ILogger.Level.SEVERE, exc.getMessage() ),
                                    getClass().getName() )
                            .start();

            // This listener receives messages of type 'MsgDeviceChanged'.
            // Everytime a device changes its value, all cells which have an expression (not a constant)
            // have to be re-visited and those which have this device in their formula (expression) have
            // to be evaluated.

            ebl = (IEventBus.Listener<MsgDeviceChanged>) (MsgDeviceChanged msg) -> dis.add( msg );

            rt.bus().add( ebl, MsgDeviceChanged.class );
        }
    }

    @Override
    public void stop()
    {
        getRuntime().bus().remove( ebl );

        ebl = null;

        if( dis != null )
            dis.stop();

        dis = null;

        // Only remove this instance's cell, not all cells
        String deviceName = getDeviceName();

        if( deviceName != null )
            map.remove( deviceName );

        super.stop();
    }

    @Override
    public synchronized void read()
    {
        if( isInvalid() )
            return;

        Object    value;
        CellValue cellValue = map.get( getDeviceName() );

        if( cellValue == null )
            return;

        value = cellValue.read();

        if( value != null )
            sendReaded( value );
    }

    @Override
    public synchronized void write( Object newVal )
    {
        if( isInvalid() )
            return;

        CellValue cv = map.get( getDeviceName() );
        Object    va = ((IDevice) getRuntime().get( getDeviceName() )).value();

        if( cv != null && ! Objects.equals( cv.read(), newVal ) )
        {
            Object value = cv.write( newVal );

            if( value != null )
                sendChanged( value );
        }
    }

    //------------------------------------------------------------------------//
    // PRIVATE SCOPE

    /**
     * Check if there is a circular reference
     *
     * @param cellVal
     * @return
     */
    private boolean hasCircularRef( CellValue cv )
    {
        if( ! cv.isFormula() )
            return false;

        if( cv.xpreval == null )
            return false;

        for( String var1 : cv.xpreval.getVars().keySet() )
        {
            if( map.containsKey( var1 ) )       // true means that passed var (which appears in the cellName's formula) is another cell
            {
                CellValue referencedCell = map.get( var1 );

                if( referencedCell != null && referencedCell.isFormula() )    // and this referenced cell contains also a formula
                {
                    if( referencedCell.xpreval != null )
                    {
                        for( String var2 : referencedCell.xpreval.getVars().keySet() )     // So we have to find if the vars contained in this xpr references the other cell
                        {
                            if( var2.equals( getDeviceName() ) )
                            {
                                String formulaStr = (cv.xpreval != null) ? cv.xpreval.toString() : "null";
                                sendIsInvalid( "Circular reference in: "+ formulaStr +" on variable: "+ var2 );
                                return true;              // 'sendIsInvalid(...)' sets this Controller instance to 'inval
                            }
                        }
                    }
                }
            }
        }

        return false;
    }

    //------------------------------------------------------------------------//
    // INNER CLASS
    // CARE: formulas can referenciate devices that are not yet initialized
    //------------------------------------------------------------------------//
    final class CellValue
    {
        private Object   value   = null;
        private IXprEval xpreval = null;
        private boolean  bErrors = false;    // A boolean instead of "(xpreval != null) && (! xpreval.getErrors().isEmpty());" to save CPU

        //------------------------------------------------------------------------//

        CellValue( Object val )
        {
            init( val );
        }

        //------------------------------------------------------------------------//

        @Override
        public String toString()
        {
            return UtilStr.toString( this );
        }

        //------------------------------------------------------------------------//

        boolean isFormula()
        {
            return xpreval != null;
        }

        boolean hasErrors()
        {
            return bErrors;
        }

        Object read()
        {
            return value;    // If has errors, 'value' already contains the errors
        }

        /**
         * This cell value is changed (by RULE THEN action).
         *
         * @param value
         * @return
         */
        Object write( Object val )
        {
            init( val );    // This allows to change Cell's current formula for a new formula at runtime.
                            // I do not think anyone is going to use this possibility, but it can be done.

            return value;
        }

        /**
         * A device that is not a Cell has changed its value: this affects those
         * Cells which have formula that include this device that have changed.
         *
         * @param devName
         * @param devValue
         * @return
         */
        Object onDeviceChanged( String devName, Object devValue )
        {
            if( isFormula() )
            {
                if( ! hasErrors() && xpreval != null )    // If has errors, 'value' already contains the errors
                {
                    Object v = xpreval.eval( devName, devValue );

                    if( v != null )    // null when not all vars have a value -> the formula is not ready yet
                        value = v;
                }
            }
            else
            {
                value = devValue;
            }

            return value;
        }

        //------------------------------------------------------------------------//

        private void init( Object val )
        {
            if( val instanceof String )
            {
                String str = val.toString().trim();

                if( (str.length() > 0) && (str.charAt( 0 ) == '=') )
                {
                    str = str.substring( 1 )          // substr to avoid '='
                             .replace( '\'', '"' );   // e.g.: "myDevice +'%'"

                    xpreval = getRuntime().newXprEval().build( str, null, getRuntime().newGroupWiseFn() );

                    if( xpreval != null )
                    {
                        List<ICandi.IError> errors = xpreval.getErrors();
                        bErrors = UtilColls.isNotEmpty( errors );

                        value   = bErrors ? "Error(s): "+ errors.toString()
                                          : null;    // Will be inited when 'xpreval' is evaluated for the 1st time
                    }
                    else
                    {
                        bErrors = true;
                        value   = "Error: Unable to create expression evaluator";
                    }
                }
                else
                {
                    value = str;
                }
            }
            else
            {
                xpreval = null;
                bErrors = false;
                value   = val;
            }
        }
    }
}