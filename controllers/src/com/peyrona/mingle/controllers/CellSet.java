
package com.peyrona.mingle.controllers;

import com.peyrona.mingle.lang.interfaces.ICandi;
import com.peyrona.mingle.lang.interfaces.IController;
import com.peyrona.mingle.lang.interfaces.ILogger;
import com.peyrona.mingle.lang.interfaces.IXprEval;
import com.peyrona.mingle.lang.interfaces.exen.IEventBus;
import com.peyrona.mingle.lang.interfaces.exen.IRuntime;
import com.peyrona.mingle.lang.japi.Dispatcher;
import com.peyrona.mingle.lang.japi.UtilColls;
import com.peyrona.mingle.lang.japi.UtilStr;
import com.peyrona.mingle.lang.messages.MsgDeviceChanged;
import com.peyrona.mingle.lang.xpreval.functions.ExtraTypeCollection;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

/**
 * This Controller can be imagined as a simple spreadsheet: it is an arbitrary
setDeviceConfig of cells and every cell can contain a value or a formula.<br>
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
    private static final String                    KEY_VALUE = "value";
    private static final Object                       LOCK   = new Object();
    private static final Map<String,CellValue>        map    = new ConcurrentHashMap<>();
    private static final AtomicInteger                nCount = new AtomicInteger( 0 );    // To track live instances (before clean-up)
    private static       Dispatcher<MsgDeviceChanged> dis    = null;                      // Needed to recalculate new values for all affected cells every time a device changes its value
    private static       IEventBus.Listener           ebl    = null;
    private              PropertyChangeListener       pcl    = null;

    //------------------------------------------------------------------------//
    // This controller is always valid

    @Override
    public void set( String deviceName, Map<String,Object> deviceConf, IController.Listener listener )
    {
        setDeviceName( deviceName );                       // Must be 1st
        setListener( listener );                           // Must be at begining: in case an error happens, Listener is needed
        set( KEY_VALUE, deviceConf.get( KEY_VALUE ) );     // Initial value. It is guarranted to exist because it is REQUIRED and therefore the Transpiler checks it

        // Can not validate the value now because could be a formula and for thisd case, the Runtime is needed.

        setValid( true );
    }

    @Override
    public boolean start( IRuntime rt )
    {
        if( ! super.start( rt ) )
            return false;

        CellValue cv = new CellValue( get( KEY_VALUE ) );    // Previously saved (at ::setDeviceConfig(...)) for this CellSet instance

             if( cv.hasErrors() )        sendIsInvalid( "Formula has errors: unusable device" );
        else if( hasCircularRef( cv ) )  sendIsInvalid( "Formula has circular references: unusable device" );
        else                             map.put( getDeviceName(), cv );

        if( isInvalid() )
            return false;

        nCount.incrementAndGet();

        synchronized( LOCK )    // Class-level lock guards static fields dis and ebl
        {
            // This is needed because 'list' and 'pair' classes can be modified directly (list:add( 5 )).
            if( get( KEY_VALUE ) instanceof ExtraTypeCollection )
            {
                pcl = (PropertyChangeEvent pce) -> sendChanged( CellSet.this.get( KEY_VALUE ) );    // Bug 3 fix: sendChanged (not sendReaded) so dependent formula cells are notified

                ((ExtraTypeCollection) get( KEY_VALUE )).addPropertyChangeListener( pcl );
            }

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
                                    32, 4096 )
                            .start();

            // This listener receives messages of type 'MsgDeviceChanged'.
            // Everytime a device changes its value, all cells which have an expression (not a constant)
            // have to be re-visited and those which have this device in their formula (expression) have
            // to be evaluated.

            ebl = (IEventBus.Listener<MsgDeviceChanged>) (MsgDeviceChanged msg) -> dis.add( msg );

            rt.bus().add( ebl, MsgDeviceChanged.class );
        }

        return isValid();
    }

    @Override
    public void stop()
    {
        if( ! isStarted() )
            return;

        if( nCount.decrementAndGet() == 0 )   // Only tear down shared resources when the very last instance stops.
        {
            synchronized( LOCK )
            {
                if( ebl != null )
                {
                    getRuntime().bus().remove( ebl );
                    ebl = null;
                }

                if( dis != null )
                {
                    dis.stop();
                    dis = null;
                }
            }
        }

        if( getDeviceName() != null )      // Only remove this instance's cell, not all cells
            map.remove( getDeviceName() );

        if( pcl != null )
        {
            ((ExtraTypeCollection) get( KEY_VALUE )).removePropertyChangeListener( pcl );
            pcl = null;
        }

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

        if( newVal == null )
            return;

        CellValue cv = map.get( getDeviceName() );

        if( cv == null )
            return;

        // Always notify; the change must be reported
        sendChanged( cv.write( newVal ) );
    }

    //------------------------------------------------------------------------//
    // PRIVATE SCOPE

    /**
     * Check if there is a circular reference using a full DFS over the cell graph.
     * <p>
     * The previous 2-hop check (A→B→A) missed chains of three or more cells
     * (e.g. A→B→C→A). This DFS walks all reachable formula cells and reports
     * a cycle as soon as a path leads back to the cell being registered.
     *
     * @param cv the CellValue of the cell currently being registered
     * @return true if a cycle is detected (also marks the controller invalid via sendIsInvalid)
     */
    private boolean hasCircularRef( CellValue cv )
    {
        if( ! cv.isFormula() )
            return false;

        Set<String> visited = new HashSet<>();

        if( dfsHasCycle( getDeviceName(), cv, visited ) )
        {
            String formulaStr = (cv.xpreval != null) ? cv.xpreval.toString() : "null";
            sendIsInvalid( "Circular reference in: "+ formulaStr );
            return true;
        }

        return false;
    }

    /**
     * DFS helper: returns true if any path reachable from {@code cv} leads back
     * to {@code originName}.
     *
     * @param originName the cell being registered (the cycle root we are looking for)
     * @param cv         the CellValue whose formula vars are explored next
     * @param visited    set of cell names already fully explored (prevents re-visiting)
     * @return true when a path back to originName is found
     */
    private boolean dfsHasCycle( String originName, CellValue cv, Set<String> visited )
    {
        IXprEval xpr = cv.xpreval;    // single atomic read; safe after isFormula() establishes happens-before

        if( xpr == null )
            return false;

        for( String var : xpr.getVars().keySet() )
        {
            if( var.equals( originName ) )
                return true;

            if( ! visited.add( var ) )
                continue;

            CellValue referenced = map.get( var );

            if( referenced != null && referenced.isFormula() && dfsHasCycle( originName, referenced, visited ) )
                return true;
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
            update( val );
        }

        //------------------------------------------------------------------------//

        @Override
        public String toString()
        {
            return UtilStr.toString( this );
        }

        //------------------------------------------------------------------------//

        // isFormula(), read(), write() and onDeviceChanged() are synchronized on the
        // CellValue instance.  Without this, the Dispatcher consumer thread (which calls
        // onDeviceChanged) races with the controller thread (read/write) and the
        // PropertyChangeListener thread, causing torn reads/writes of 'value' and 'xpreval'.

        synchronized boolean isFormula()
        {
            return xpreval != null;
        }

        boolean hasErrors()
        {
            return bErrors;    // only called during single-threaded init (start()) or from within synchronized onDeviceChanged()
        }

        synchronized Object read()
        {
            return value;    // If has errors, 'value' already contains the errors
        }

        /**
         * This cell value is changed (by RULE THEN action).
         *
         * @param value
         * @return
         */
        synchronized Object write( Object val )
        {
            update( val );  // This allows to change Cell's current formula for a new formula at runtime.
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
        synchronized Object onDeviceChanged( String devName, Object devValue )
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

        private void update( Object val )   // Invoked from CellSet::write(...) (which is sync)
        {
            if( val.getClass().equals( String.class ) )
            {
                String str = val.toString().trim();

                if( (str.length() > 0) && (str.charAt( 0 ) == '=') )
                {
                    str = new StringBuilder( str ).deleteCharAt(0).toString();   // substr to jump '=' (strange but this is the fastest way)

                    if( xpreval != null && str.equals( xpreval.toString() ) )    // toString() returns the original xpr
                        return;

                    xpreval = getRuntime().newXprEval().build( str, null, getRuntime()::getGroupMemberNames );

                    List<ICandi.IError> errors = xpreval.getErrors();

                    bErrors = UtilColls.isNotEmpty( errors );

                    if( bErrors )
                    {
                        xpreval = null;
                        value   = "Error: Unable to create expression evaluator:\n"+ errors.toString();
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