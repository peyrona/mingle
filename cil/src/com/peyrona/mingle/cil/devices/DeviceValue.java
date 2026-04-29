
package com.peyrona.mingle.cil.devices;

import com.peyrona.mingle.lang.MingleException;
import com.peyrona.mingle.lang.japi.Chronometer;
import com.peyrona.mingle.lang.japi.UtilStr;
import com.peyrona.mingle.lang.japi.UtilType;

/**
 *
 * @author Francisco José Morero Peyrona
 *
 * Official web site at: <a href="https://github.com/peyrona/mingle">https://github.com/peyrona/mingle</a>
 * @param <T>
 */
class DeviceValue
{
    private final    Chronometer notified = new Chronometer();   // When was the last time the value was modified.
    private volatile Object      value    = null;                // This is its value until first set happens ('null' is not a valid Une value).
    private final    Float       delta;                          // null means ignore delta (do not use 0.0f, use null).
    private volatile boolean     bErrSent = false;                // volatile is enough here: a concurrent race may fire the exception 2-3 times instead of 1; after that the 'true' publishes.

    //------------------------------------------------------------------------//

    DeviceValue( Float delta )
    {
        this.delta = (delta < 0.0001f) ? null : delta;
    }

    //------------------------------------------------------------------------//
    // PUBLIC SCOPE

    Object get()
    {
        return value;
    }

    /**
     * Updates internal get.
     *
     * @param newValue
     * @return this
     */
    boolean set( Object newValue )
    {
        notified.reset();               // Value can be updated or not, but as physical device is working, the chronometer has to be reseted

        if( isEnough( newValue ) )      // Most part of times delta will be null (and be null means that any value is significant (enough)).
        {                               // (I tried different approaches, and to accept any value as a changed when delta == null is the best)
            value = newValue;
            return true;
        }

        return false;
    }

    Float delta()
    {
        return delta;
    }

    boolean isElapsed( long milliseconds )
    {
        boolean is = notified.isElapsed( milliseconds );

        if( is )                 // Although the device can have a long period of 'downtime' the Stick can asks frequently about this value, therefore,
            notified.reset();    // the chronometer has to be reset everytime the device have been unaccesible for time enougth to be considered downtimed.
                                 // Pq de otro modo, la acción asociada al hecho de que el device este downtimed, se ejecutará no con la frecuencia del downtime-interval, sino con la frecuencia con la que Stick pregunta.
        return is;
    }

    @Override
    public String toString()
    {
        return UtilStr.toString( this );
    }

    //------------------------------------------------------------------------//
    // PRIVATE SCOPE

    boolean isEnough( Object newValue )
    {
        if( newValue == null )     // Although null is not a valid Une value, a wrong implemented piece could send null values: so better to check.
            return false;

        if( delta == null )
            return true;

        if( value == null )
            return true;

        if( (newValue instanceof Number) && (value instanceof Number) )
        {
            float fOld = UtilType.toFloat( value );
            float fNew = UtilType.toFloat( newValue );

            return (Math.abs( fNew - fOld ) >= delta);
        }

        if( ! bErrSent )
        {
            bErrSent = true;
            String valueTypeName = (value == null) ? "null" : value.getClass().getSimpleName();
            throw new MingleException( "Can not apply Delta to a '"+ valueTypeName +"'\nDelta is ignored." );
        }

        return true;
    }
}