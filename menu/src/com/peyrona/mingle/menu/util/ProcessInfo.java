package com.peyrona.mingle.menu.util;

/**
 * Represents detailed information about a system process.
 * <p>
 * This class is designed to be an immutable data carrier for process attributes
 * such as PID, name, command line, CPU usage, memory usage, and start time.
 */
public class ProcessInfo {
    private final int pid;
    private final String name;
    private final String commandLine;
    private final double cpuUsage;
    private final long memoryUsageKB;
    private final long startTimeMillis;

    /**
     * Constructs a new ProcessInfo object.
     *
     * @param pid             The process identifier (PID).
     * @param name            The name of the process executable.
     * @param commandLine     The full command line used to start the process.
     * @param cpuUsage        The CPU usage of the process, typically as a percentage.
     * @param memoryUsageKB   The memory usage of the process in kilobytes (KB).
     * @param startTimeMillis The process start time in milliseconds since the epoch.
     */
    public ProcessInfo(int pid, String name, String commandLine, double cpuUsage, long memoryUsageKB, long startTimeMillis) {
        this.pid = pid;
        this.name = name;
        this.commandLine = commandLine;
        this.cpuUsage = cpuUsage;
        this.memoryUsageKB = memoryUsageKB;
        this.startTimeMillis = startTimeMillis;
    }

    /**
     * @return The process identifier (PID).
     */
    public int getPid() { return pid; }

    /**
     * @return The name of the process executable.
     */
    public String getName() { return name; }

    /**
     * @return The full command line used to start the process.
     */
    public String getCommandLine() { return commandLine; }

    /**
     * @return The CPU usage of the process.
     */
    public double getCpuUsage() { return cpuUsage; }

    /**
     * @return The memory usage of the process in kilobytes (KB).
     */
    public long getMemoryUsageKB() { return memoryUsageKB; }

    /**
     * @return The process start time in milliseconds since the epoch.
     */
    public long getStartTimeMillis() { return startTimeMillis; }

    @Override
    public String toString() {
        return "ProcessInfo{" +
               "pid=" + pid +
               ", name='" + name + "'" +
               ", commandLine='" + commandLine + "'" +
               ", cpuUsage=" + cpuUsage +
               ", memoryUsageKB=" + memoryUsageKB +
               ", startTimeMillis=" + startTimeMillis +
               '}';
    }
}
