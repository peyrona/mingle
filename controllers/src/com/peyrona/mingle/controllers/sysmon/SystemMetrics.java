
package com.peyrona.mingle.controllers.sysmon;

import com.sun.management.OperatingSystemMXBean;
import java.io.File;
import java.lang.management.ManagementFactory;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * This class provides basic information about usage in: RAM, Disks and CPU.
 * <p>
 * Note: The number of Root FSs and their total space is read only once: when
 * the number od them or capacity changes, the application that calls here must
 * be rebooted.
 *
 * @author Francisco José Morero Peyrona
 *
 * Official web site at: <a href="https://github.com/peyrona/mingle">https://github.com/peyrona/mingle</a>
 */
public final class SystemMetrics
{
    private static final    OperatingSystemMXBean OSMXBean = (com.sun.management.OperatingSystemMXBean) ManagementFactory.getOperatingSystemMXBean();
    private static          Map<String,Long> mapRootsTotal = null;
    private static          Map<String,Long> mapRootsFree  = null;
    private static volatile int              nCPUs         = -1;

    //----------------------------------------------------------------------------//
    // DISK

    /**
     * Returns the amount of machine used disk in bytes.
     *
     * @return the amount of used disk in bytes.
     * @see #getFreeSpace()
     */
    public static Map<String,Long> getUsedSpace()
    {
        getRootsTotal();
        getRootsFree();

        for( Map.Entry<String,Long> entry : mapRootsFree.entrySet() )
        {
            long nTotal = mapRootsTotal.get( entry.getKey() );             // gets the value of passed key
            long nFree  = (new File( entry.getKey() )).getFreeSpace();     // Less accurate but much faster than :getUsableSpace()

            entry.setValue( nTotal - nFree );
        }

        return Collections.unmodifiableMap( mapRootsFree );
    }

    /**
     * Returns the total amount of machine disk capacity  in bytes.
     *
     * @return the total amount of machine disk capacity  in bytes.
     */
    public static Map<String,Long> getTotalSpace()
    {
        return getRootsTotal();
    }

    /**
     * Returns the amount of machine free disk in bytes.
     *
     * @return the amount of free disk in bytes.
     * @see #getUsedSpace()
     */
    public static Map<String,Long> getFreeSpace()
    {
        for( Map.Entry<String,Long> entry : getRootsFree().entrySet() )
        {
            entry.setValue( (new File( entry.getKey() )).getFreeSpace() );   // Less accurate but much faster than :getUsableSpace()
        }

        return Collections.unmodifiableMap( mapRootsFree );
    }

    //----------------------------------------------------------------------------//
    // RAM

    /**
     * Returns free JVM memory.
     *
     * @return free JVM memory.
     */
    public static long getJvmFreeMemory()
    {
        return Runtime.getRuntime().freeMemory();
    }

    public static long getJvmTotalMemory()
    {
        return Runtime.getRuntime().totalMemory();
    }

// FOLLOWING 2 METHODS ARE NOT AVAILABLE BECAUSE THEY RETURN WRONG VALUES AND THERE IS NOTHING THAT CAN BE DONE
//
//    /**
//     * Returns the amount of physical machine used memory as percentage over total.
//     * <p>
//     * CARE: ::getFreeMemory() return the amount of free memory in bytes.
//     *
//     * @return the amount of used memory as percentage over total.
//     * @see #getFreeMemory()
//     */
//    public static byte getUsedMemory()
//    {
//        return (byte) (((getTotalJvmMemory() - getFreeMemory()) / (float) getTotalJvmMemory()) * 100);
//    }
//
//    /**
//     * Returns the amount of physical machine free memory in bytes.
//     * <p>
//     * CARE: ::getUsedMemory() return the amount of used memory in percentage over total.
//     *
//     * @return the amount of used memory as percentage.
//     * @see #getUsedMemory()
//     */
//    public static long getFreeMemory()
//    {
//        return OSMXBean.getFreePhysicalMemorySize();
//    }

    //----------------------------------------------------------------------------//
    // CPUs

    // IMPORTANT NOTE:
    // For applications that implement the Oracle JDK OperatingSystemMXBean Interface in containers,
    // the OperatingSystemMXBean.getSystemCpuLoad() method may return an incorrect value.
    // When the CPU load reaches 100%, the OperatingSystemMXBean.getSystemCpuLoad() method returns a
    // value of 1 to indicate the CPU load is 100%.  However, the OperatingSystemMXBean.getSystemCpuLoad()
    // continues to return a value of 1 throughout the rest of the runtime, even though the actual
    // CPU load has decreased.
    //
    // To reproduce the issue, use the test case from JDK-8265836 with:
    //     + Oracle Java SE 8 Update 321 b33 or older release; or
    //     + Oracle Java SE 11.0.13 or older release

    public static int getProcessors()
    {
        if( nCPUs == -1 )
        {
            nCPUs = OSMXBean.getAvailableProcessors();
        }

        return nCPUs;
    }

    /**
     * Returns the "recent CPU usage" for the whole system between 0.0 and 1.0.
     * @return The "recent CPU usage" for the whole system between 0.0 and 1.0.
     */
    public static float getCpuLoad()
    {
        return round( (float) OSMXBean.getSystemCpuLoad() );
    }

    /**
     * Returns System Load Average between 0.0 and 1.0.
     *
     * @return System Load Average between 0.0 and 1.0.
     */
    public static float getSystemLoadAverage()
    {
        return round( (float) OSMXBean.getSystemLoadAverage() );
    }

    /**
     * Returns the "recent CPU usage" for the Java Virtual Machine process.
     * @return The "recent CPU usage" for the Java Virtual Machine process.
     */
    public static float getJvmCpuLoad()
    {
        return round( (float) OSMXBean.getProcessCpuLoad() );
    }

    /**
     * Returns the CPU time used by the process on which the Java virtual machine is running in nanoseconds.
     * @return The CPU time used by the process on which the Java virtual machine is running in nanoseconds.
     */
    public static long getJvmCpuTime()
    {
        return OSMXBean.getProcessCpuTime();
    }

    //----------------------------------------------------------------------------//

    private SystemMetrics()
    {
        // Avoid the creation of instances of this class
    }

    private static Map<String,Long> getRootsTotal()
    {
        if( mapRootsTotal == null )
        {
            synchronized( SystemMetrics.class )
            {
                Map<String,Long> map  = new HashMap<>();

                for( File file : File.listRoots() )                               // Get a list of all filesystem roots on this system )
                {
                    map.put( file.getAbsolutePath(), file.getTotalSpace() );      // Puts the root file and its total size
                }

                mapRootsTotal = Collections.unmodifiableMap( map );
            }
        }

        return mapRootsTotal;
    }

    private static Map<String,Long> getRootsFree()
    {
        if( mapRootsFree == null )
        {
            synchronized( SystemMetrics.class )
            {
                Map<String,Long> map  = new HashMap<>();

                for( String fRoot : mapRootsTotal.keySet() )
                {
                    map.put( fRoot, 0l );     // Puts just the file, because the free space will be calculated in every call
                }

                mapRootsFree = map;
            }
        }

        return mapRootsFree;
    }

    private static float round( float amount )
    {
        return (float) (Math.round( amount * 100f) / 100f);
    }
}