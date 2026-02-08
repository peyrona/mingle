
package com.peyrona.mingle.menu.util;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

/**
 * A simple shell command execution class for the Menu module.
 * <p>
 * Provides a builder-based API for executing shell commands synchronously.
 * <p>
 * <b>Usage Example:</b>
 * <pre>{@code
 * ProcessResult result = new Execute.Builder("systemctl", "status", "glue")
 *     .build()
 *     .executeAndWait();
 *
 * if (result.succeeded()) {
 *     System.out.println(result.output);
 * }
 * }</pre>
 *
 * @author Francisco José Morero Peyrona
 */
public final class Execute
{
    private final String[] command;

    //------------------------------------------------------------------------//

    private Execute( String[] command )
    {
        this.command = command;
    }

    //------------------------------------------------------------------------//

    /**
     * Executes the command synchronously and waits for completion.
     *
     * @return a ProcessResult containing exit code and output
     * @throws IOException if an I/O error occurs
     * @throws InterruptedException if the thread is interrupted while waiting
     */
    public ProcessResult executeAndWait() throws IOException, InterruptedException
    {
        ProcessBuilder pb = new ProcessBuilder( command );
        pb.redirectErrorStream( true );

        Process process = pb.start();

        StringBuilder output = new StringBuilder();

        try( BufferedReader reader = new BufferedReader(
                new InputStreamReader( process.getInputStream(), StandardCharsets.UTF_8 ) ) )
        {
            String line;

            while( (line = reader.readLine()) != null )
            {
                output.append( line ).append( "\n" );
            }
        }

        int exitCode = process.waitFor();

        return new ProcessResult( exitCode, output.toString().trim() );
    }

    //------------------------------------------------------------------------//
    // INNER CLASSES
    //------------------------------------------------------------------------//

    /**
     * Builder for creating Execute instances.
     */
    public static final class Builder
    {
        private final String[] command;

        /**
         * Creates a new Builder with the specified command and arguments.
         *
         * @param command the command and its arguments
         */
        public Builder( String... command )
        {
            this.command = command;
        }

        /**
         * Builds the Execute instance.
         *
         * @return a new Execute instance
         */
        public Execute build()
        {
            return new Execute( command );
        }
    }

    /**
     * Represents the result of a synchronous shell command execution.
     */
    public static final class ProcessResult
    {
        /** The exit code returned by the process. */
        public final int exitCode;

        /** The combined stdout and stderr output from the process. */
        public final String output;

        /**
         * Constructs a new ProcessResult.
         *
         * @param exitCode the exit code returned by the process
         * @param output   the output captured from the process
         */
        public ProcessResult( int exitCode, String output )
        {
            this.exitCode = exitCode;
            this.output   = output;
        }

        /**
         * Checks if the process completed successfully (exit code 0).
         *
         * @return true if exit code is 0, false otherwise
         */
        public boolean succeeded()
        {
            return (exitCode == 0);
        }

        /**
         * Returns the output from the process.
         *
         * @return the output string
         */
        public String getOutput()
        {
            return output;
        }

        @Override
        public String toString()
        {
            return "ProcessResult{exitCode=" + exitCode +
                   ", output=" + (output.length() > 100 ? output.substring( 0, 100 ) + "..." : output) +
                   '}';
        }
    }
}
