package com.peyrona.mingle.menu.core;

import com.peyrona.mingle.menu.linux.LinuxProcessManager;
import com.peyrona.mingle.menu.mac.MacProcessManager;
import com.peyrona.mingle.menu.util.UtilSys;
import com.peyrona.mingle.menu.win.WinProcessManager;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Factory class to create platform-specific process managers.
 */
public final class ProcessManagerFactory
{
    /**
     * Creates and returns the appropriate process manager for the current platform.
     * @return IProcessManager instance for the current platform, or null if none available
     */
    static IProcessManager createProcessManager()
    {
            if( UtilSys.sOS.contains( "linux"   ) )  return new LinuxProcessManager();
       else if( UtilSys.sOS.contains( "mac"     ) )  return new MacProcessManager();
       else if( UtilSys.sOS.contains( "darwin"  ) )  return new MacProcessManager();
       else if( UtilSys.sOS.contains( "windows" ) )  return new WinProcessManager();
       else                                          return new VoidProcessManager();
    }

    //------------------------------------------------------------------------//
    // INNER CLASS
    //------------------------------------------------------------------------//

    private static final class VoidProcessManager implements IProcessManager
    {
        @Override
        public Process execJar( String jarName, List<String> lstOptions, String... args ) throws IOException
        {
            return null;
        }

        @Override
        public boolean kill( long pid, boolean forceful )
        {
            return false;
        }

        @Override
        public boolean isAvailable()
        {
            return false;
        }

        @Override
        public List<ProcessInfo> list() throws IOException, InterruptedException
        {
            return new ArrayList<>();
        }
    }
}