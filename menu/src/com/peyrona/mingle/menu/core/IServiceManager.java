package com.peyrona.mingle.menu.core;

import java.util.List;

/**
 * Interface for platform-specific service management.
 * Each platform (Linux, macOS, Windows) will implement this interface
 * to provide service management functionality.
 */
public interface IServiceManager
{
    /**
     * Checks if the service manager is available on the current platform.
     * @return true if the service manager is available, false otherwise
     */
    boolean isAvailable();

    /**
     * Checks if a service exists for the specified component.
     *
     * @param service Either "stick" or "gum".
     * @return true if service exists, false otherwise
     */
    boolean exists( String service );

    /**
     * Creates a service for the specified component if it does not exist.
     *
     * @param service Either "stick" or "gum".
     * @param lstOptions JVM options
     * @param args Additional arguments for the service
     * @return true if service was created successfully, false otherwise
     */
    boolean create( String service, List<String> lstOptions, String... args );

    /**
     * Starts a service for the specified component.
     * @param service Either "stick" or "gum".
     * @return true if service was started successfully, false otherwise
     */
    boolean start( String service );

    /**
     * Stops a service for the specified component.
     * @param service Either "stick" or "gum".
     * @return true if service was stopped successfully, false otherwise
     */
    boolean stop( String service );

    /**
     * Restarts a service for the specified component.
     * @param service Either "stick" or "gum".
     * @return true if service was restarted successfully, false otherwise
     */
    boolean restart( String service );

    /**
     * Gets the status of a service for the specified component.
     * @param service The name of the component
     * @return The service status output
     */
    String getStatus( String service );

    /**
     * Checks if a service is currently running for the specified component.
     * @param service The name of the component
     * @return true if service is running, false otherwise
     */
    boolean isRunning( String service );

    /**
     * Outputs to console (System.out) the last log entries.
     * @param service Either "stick" or "gum".
     * @return true if ran successfully.
     */
    boolean showLog( String service );

    /**
     * Deletes the service file for the specified component.
     * @param service Either "stick" or "gum".
     * @return true if service was deleted successfully, false otherwise
     */
    boolean delete( String service );
}