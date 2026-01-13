
package com.peyrona.mingle.stick;

import com.peyrona.mingle.lang.interfaces.commands.ICommand;
import com.peyrona.mingle.lang.interfaces.commands.IDriver;
import com.peyrona.mingle.lang.interfaces.commands.IRule;
import com.peyrona.mingle.lang.interfaces.commands.IScript;
import com.peyrona.mingle.lang.interfaces.exen.IRuntime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Manages SCRIPT instances in the runtime environment.
 *
 * @author Francisco José Morero Peyrona
 *
 * Official web site at: <a href="https://github.com/peyrona/mingle">https://github.com/peyrona/mingle</a>
 */
final class   ScriptManager
      extends BaseManager<IScript>
{
    //------------------------------------------------------------------------//
    // PACKAGE SCOPE

    ScriptManager( IRuntime runtime )
    {
        super( runtime );
    }

    //------------------------------------------------------------------------//

    /**
     * This method is invoked by Stick::remove(...) and can not be auto-invoked because so far
     * I can not find a way to solve this: what if the script is going to be used by another
     * native SCRIPT (from Java, Une, etc...)
     */
    synchronized void clean()
    {
        IDriver[]     aDrivers = getAllDrivers();
        IRule[]       aRules   = getAllRules();
        List<IScript> lstUsed  = new ArrayList<>();

        // Add to lstUsed all the Scripts that are used by DRIVERs

        forEach( (IScript scpt) ->
                {
                    if( (! isStarted() && scpt.isOnStart()) || scpt.isOnStop() )    // Add all that are ONSTART (if not started yet) or ONSTOP
                    {
                        lstUsed.add( scpt );
                    }
                    else
                    {
                        for( IDriver drv : aDrivers )    // Add all scripts that are used by DRIVERs
                        {
                            if( scpt.name().equals( drv.getScriptName() ) )
                            {
                                lstUsed.add( scpt );
                                break;
                            }
                        }

                        for( IRule rule : aRules )       // Add all scripts that are used by RULEs
                        {
                            for( IRule.IAction action : rule.getActions() )
                            {
                                if( scpt.name().equals( action.getTarget() ) )
                                {
                                    lstUsed.add( scpt );
                                    break;
                                }
                            }
                        }
                    }
                } );

        // The rest of the scripts are not used and therefore can be deleted

        forEach( (IScript scpt) ->
                {
                    if( ! lstUsed.contains( scpt ) )
                        runtime.remove( scpt );
                } );
    }

    //------------------------------------------------------------------------//

    private IDriver[] getAllDrivers()
    {
        ICommand[] aCommands = runtime.all( "drivers" );

        return Arrays.copyOf( aCommands, aCommands.length, IDriver[].class );
    }

    private IRule[] getAllRules()
    {
        ICommand[] aCommands = runtime.all( "rules" );

        return Arrays.copyOf( aCommands, aCommands.length, IRule[].class );
    }
}