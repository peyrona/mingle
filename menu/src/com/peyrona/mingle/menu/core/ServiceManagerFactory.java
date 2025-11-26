package com.peyrona.mingle.menu.core;

import com.peyrona.mingle.menu.linux.LinuxServiceManager;
import com.peyrona.mingle.menu.mac.MacServiceManager;
import com.peyrona.mingle.menu.util.UtilSys;
import com.peyrona.mingle.menu.win.WinServiceManager;
import java.util.List;

/**
 * Factory class to create platform-specific service managers.
 */
public final class ServiceManagerFactory
{
    /**
     * Creates and returns the appropriate service manager for the current platform.
     * @return IServiceManager instance for the current platform
     */
    static IServiceManager createServiceManager()
    {
             if( UtilSys.sOS.contains( "linux"  ) )  return new LinuxServiceManager();
        else if( UtilSys.sOS.contains( "mac"    ) )  return new MacServiceManager();
        else if( UtilSys.sOS.contains( "darwin" ) )  return new MacServiceManager();
        else if( UtilSys.sOS.contains( "win"    ) )  return new WinServiceManager();
        else                                         return new VoidServiceManager();
    }

    //------------------------------------------------------------------------//
    // PRIVATE SCOPE

    private ServiceManagerFactory() {}  // Prevent instantiation

    //------------------------------------------------------------------------//
    // INNER CLASS
    //------------------------------------------------------------------------//

    private static final class VoidServiceManager implements IServiceManager
    {
        @Override public boolean isAvailable()                                                      { return false; }
        @Override public boolean exists( String componentName )                                     { return false; }
        @Override public boolean create( String jarName, List<String> lstOptions, String... args )  { return false; }
        @Override public boolean start( String c )                                                  { return false; }
        @Override public boolean stop( String c )                                                   { return false; }
        @Override public boolean restart( String c )                                                { return false; }
        @Override public String  getStatus( String c )                                              { return "Unsupported OS"; }
        @Override public boolean isRunning( String c )                                              { return false; }
        @Override public boolean showLog( String c )                                                { return false; }
        @Override public boolean delete( String service )                                           { return false; }
    }
}