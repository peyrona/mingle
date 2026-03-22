
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
 * This Controller can be imagined as a simple spreadsheet: every cell can
 * contain a value or a formula.<br>
 * Cells have a name to refer to them (a cell is the equivalent to a variable
 * in traditional languages).
 * <p>
 * Each Device using a CellDriver gets its own CellSet instance. The device
 * name serves as the cell name. Shared infrastructure (Dispatcher, EventBus
 * listener) is managed as static class-level state, initialized on first
 * instance start and torn down on last instance stop.
 * <p>
 * Two separate registries are maintained as static state:
 * {@code map} (device name → CellValue) is used for formula-variable lookups
 * and circular-reference detection; {@code instances} (device name → CellSet)
 * is used for lifecycle management and Dispatcher fan-out.
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
    private static final String                       KEY_VALUE = "value";
    private static final Object                       LOCK      = new Object();
    private static final Map<String,CellValue>        values    = new ConcurrentHashMap<>();   // Maps device name → CellValue (for formula lookups and DFS)
    private static final Map<String,CellSet>          instances = new ConcurrentHashMap<>();   // Maps device name → owning CellSet instance (for lifecycle management)
    private static final AtomicInteger                nCount    = new AtomicInteger( 0 );      // To track live instances (for shared-resource teardown)
    private static       Dispatcher<MsgDeviceChanged> dis       = null;                        // Shared: recalculates formula cells on any device change
    private static       IEventBus.Listener           ebl       = null;                        // Shared: feeds MsgDeviceChanged events into the Dispatcher
    private volatile     CellValue                    cell      = null;                        // Per-instance cell data (value or formula)
    private              PropertyChangeListener       pcl       = null;                        // Per-instance: listens for direct mutations on ExtraTypeCollection values

    //------------------------------------------------------------------------//
    // This controller is always valid

    @Override
    public void set( String deviceName, Map<String,Object> deviceConf, IController.Listener listener )
    {
        setDeviceName( deviceName );                       // Must be 1st
        setListener( listener );                           // Must be at beginning: in case an error happens, Listener is needed
        set( KEY_VALUE, deviceConf.get( KEY_VALUE ) );     // Initial value. It is guaranteed to exist because it is REQUIRED and therefore the Transpiler checks it

        // Cannot validate the value now because it could be a formula, and for that case the Runtime is needed.

        setValid( true );
    }

    @Override
    public boolean start( IRuntime rt )
    {
        if( ! super.start( rt ) )
            return false;

        CellValue cv = new CellValue( get( KEY_VALUE ) );    // Previously saved (at ::set(...)) for this CellSet instance

        if( cv.hasErrors() )
        {
            sendIsInvalid( "Formula has errors: unusable device'"+ getDeviceName() +'\'' );
        }
        else if( hasCircularRef( cv ) )
        {
            String formulaStr = (cv.xpreval != null) ? cv.xpreval.toString() : "null";
            sendIsInvalid( "Circular reference in '"+ formulaStr +"': unusable device '"+ getDeviceName() +'\'' );
        }
        else
        {
            cell = cv;
        }

        if( isInvalid() )
            return false;

        try
        {
            synchronized( LOCK )    // Class-level lock guards static fields dis and ebl
            {
                // This is needed because 'list' and 'pair' classes can be modified directly (list:add( 5 )).
                if( get( KEY_VALUE ) instanceof ExtraTypeCollection )
                {
                    pcl = (PropertyChangeEvent pce) -> sendChanged( CellSet.this.get( KEY_VALUE ) );    // sendChanged (not sendReaded) so dependent formula cells are notified

                    ((ExtraTypeCollection) get( KEY_VALUE )).addPropertyChangeListener( pcl );
                }

                // Dispatcher and EventBus listener are shared across all CellSet instances.
                if( dis == null )
                {
                    Consumer<MsgDeviceChanged> consumer = (msg) ->
                        {
                            for( CellSet cs : instances.values() )
                            {
                                CellValue c = cs.cell;    // Single volatile read; prevents TOCTOU with concurrent stop()

                                if( c != null && c.isFormula() )
                                {
                                    Object oldVal = c.read();
                                    Object newVal = c.onDeviceChanged( msg.name, msg.value );

                                    if( ! Objects.equals( newVal, oldVal ) )    // Needed to avoid a feedback spiral
                                        cs.sendChanged( newVal );
                                }
                            }
                        };

                    dis = new Dispatcher<>( consumer,
                                            (exc) -> sendGenericError( ILogger.Level.SEVERE, exc.getMessage() ),
                                            32, 4096 )
                                        .start();

                    // This listener receives messages of type 'MsgDeviceChanged'.
                    // Every time a device changes its value, all cells which have an expression (not a constant) have to
                    // be re-visited and those which have this device in their formula (expression) have to be evaluated.

                    ebl = (IEventBus.Listener<MsgDeviceChanged>) (MsgDeviceChanged msg) -> dis.add( msg );

                    rt.bus().add( ebl, MsgDeviceChanged.class );
                }
            }
        }
        catch( RuntimeException ex )
        {
            // Synchronized block failed (e.g. bus.add() throws). cell has been set but map.put and
            // nCount.incrementAndGet have not run yet, so there is nothing to roll back for those.
            cell = null;
            pcl  = null;
            setValid( false );
            return false;
        }

        // Commit: register in both shared maps and increment the live-instance counter.
        // All three happen after the synchronized block to avoid any rollback on failure.
        values.put( getDeviceName(), cell );
        instances.put( getDeviceName(), this );
        nCount.incrementAndGet();

        return true;
    }

    @Override
    public void stop()
    {
        if( ! isStarted() )
            return;

        // instances.remove() returns non-null if and only if this instance was fully registered
        // (i.e. instances.put and nCount.incrementAndGet both ran in start()). This replaces
        // the former bCounted flag with an equivalent but simpler idiom.
        values.remove( getDeviceName() );
        boolean wasRegistered = (instances.remove( getDeviceName() ) != null);

        if( wasRegistered && nCount.decrementAndGet() == 0 )    // Only tear down shared resources when the very last instance stops.
        {
            synchronized( LOCK )
            {
                // Re-check after acquiring lock: a concurrent start() may have
                // incremented nCount between our decrement and this lock acquisition.
                if( nCount.get() == 0 )
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
        }

        if( pcl != null )
        {
            ((ExtraTypeCollection) get( KEY_VALUE )).removePropertyChangeListener( pcl );
            pcl = null;
        }

        cell = null;
        super.stop();
    }

    @Override
    public void read()
    {
        CellValue c = cell;    // Single volatile read; prevents TOCTOU with concurrent stop()

        if( isInvalid() || c == null )
            return;

        Object value = c.read();

        if( value != null )
            sendReaded( value );
    }

    @Override
    public synchronized void write( Object newVal )
    {
        if( isInvalid() || newVal == null || cell == null )
            return;

        // Manage PCL lifecycle when the value transitions to or from an ExtraTypeCollection.
        // Without this, a collection assigned at runtime via write() has no listener and its
        // mutations never trigger sendChanged; and replacing a collection with a non-collection
        // would leave a dangling listener on the old object.
        Object oldStored = get( KEY_VALUE );

        if( pcl != null && oldStored instanceof ExtraTypeCollection )
            ((ExtraTypeCollection) oldStored).removePropertyChangeListener( pcl );

        if( newVal instanceof ExtraTypeCollection )
        {
            if( pcl == null )
                pcl = (pce) -> sendChanged( CellSet.this.get( KEY_VALUE ) );

            ((ExtraTypeCollection) newVal).addPropertyChangeListener( pcl );
            set( KEY_VALUE, newVal );    // Keep mapConfig in sync so PCL callback and stop() use the current collection.
        }
        else
        {
            pcl = null;
            set( KEY_VALUE, newVal );    // Keep mapConfig in sync for non-collection types too.
        }

        // Always notify; the change must be reported
        sendChanged( cell.write( newVal ) );
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
     * @return true if a cycle is detected
     */
    private boolean hasCircularRef( CellValue cv )
    {
        if( ! cv.isFormula() )
            return false;

        Set<String> visited = new HashSet<>();

        return dfsHasCycle( getDeviceName(), cv, visited );
    }

    /**
     * DFS helper: returns true if any path reachable from {@code cv} leads back
     * to {@code originName}.
     * <p>
     * Uses {@code map} (device name → CellValue) for neighbour lookups, which
     * avoids cross-instance field access and the visibility concerns that would
     * otherwise require extra volatile reads.
     * </p>
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

            CellValue refCv = values.get( var );    // map now stores CellValue directly; no cross-instance field access

            if( refCv != null && refCv.isFormula() && dfsHasCycle( originName, refCv, visited ) )
                return true;
        }

        return false;
    }

    //------------------------------------------------------------------------//
    // INNER CLASS
    // CARE: formulas can reference devices that are not yet initialized
    //------------------------------------------------------------------------//
    private final class CellValue
    {
        private volatile Object   value   = null;
        private volatile IXprEval xpreval = null;
        private          boolean  bErrors = false;    // A boolean instead of "(xpreval != null) && (! xpreval.getErrors().isEmpty());" to save CPU

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

        boolean isFormula()
        {
            return xpreval != null;
        }

        boolean hasErrors()
        {
            return bErrors;    // only called during single-threaded init (start()) or from within synchronized onDeviceChanged()
        }

        Object read()
        {
            return value;     // If has errors, 'value' already contains the errors
        }

        /**
         * This cell value is changed (by RULE THEN action).
         *
         * @param val the new value or formula
         * @return the resulting stored value
         */
        synchronized Object write( Object val )
        {
            update( val );  // This allows to change Cell's current formula for a new formula at runtime.
                            // I do not think anyone is going to use this possibility, but it can be done.
            return value;
        }

        /**
         * A device that is not a Cell has changed its value: this affects those
         * Cells which have a formula that references this device.
         *
         * @param devName  the name of the device that changed
         * @param devValue the new value of that device
         * @return the resulting cell value
         */
        synchronized Object onDeviceChanged( String devName, Object devValue )
        {
            if( isFormula() && ! hasErrors() && xpreval != null )    // If has errors, 'value' already contains the errors
            {
                Object v = xpreval.eval( devName, devValue );

                if( v != null )    // null when not all vars have a value -> the formula is not ready yet
                    value = v;
            }

            return value;
        }

        //------------------------------------------------------------------------//

        private void update( Object val )   // Invoked from CellSet::write(...) (which is sync)
        {
            if( val instanceof String )
            {
                String str = val.toString().trim();

                if( (str.length() > 0) && (str.charAt( 0 ) == '=') )
                {
                    str = str.substring( 1 );    // Delete '='

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