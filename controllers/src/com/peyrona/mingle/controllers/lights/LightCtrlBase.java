
package com.peyrona.mingle.controllers.lights;

import com.peyrona.mingle.controllers.ControllerBase;
import com.peyrona.mingle.lang.japi.UtilUnit;

/**
 *
 * @author francisco
 */
public abstract class LightCtrlBase extends ControllerBase
{
    private int   nRampTime = 700;
    private short nCandela  = 0;    // As percent

    //------------------------------------------------------------------------//

    public abstract boolean isDimmable();
    public abstract boolean isRGB();

    //------------------------------------------------------------------------//

    public int getRampTime()
    {
        return nRampTime;
    }

    public LightCtrlBase setRampTime( int millis )
    {
        nRampTime = (millis < 0 ? 0 : millis);

        return this;
    }

    public int getCandela()
    {
        return nCandela;
    }

    public LightCtrlBase setCandela( int percent )
    {
        nCandela = (short) UtilUnit.setBetween( 0, percent, 100 );

        return this;
    }
}