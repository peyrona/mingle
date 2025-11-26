
package com.peyrona.mingle.menu.util;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 *
 * @author francisco
 */
public final class UtilJVM
{
    private static String sClassPath = null;

    //------------------------------------------------------------------------//

    /**
     * Receives the result of invoking ::buildJavaCmd(...) and returns the String that represents it.
     *
     * @param command The result of invoking ::buildJavaCmd(...)
     * @return the String that represents received command.
     */
    public static String javaCmdToString( List<String> command )
    {
        return command.stream()
                      .map( arg -> quoteArgument( arg ) )
                      .collect( Collectors.joining( " " ) );
    }

    /**
     * Builds a Java command string for launching a Mingle component with additional files.
     *
     * @param jarName
     * @param lstOptions
     * @param args
     * @return The full Java command as a string.
     */
    public static List<String> buildJavaCmd( String jarName, List<String> lstOptions, String... args )
    {
        if( lstOptions == null )
            lstOptions = new ArrayList<>();

        List<String> lstArgs = (args == null) ? new ArrayList<>()
                                              : Arrays.asList( args );

        List<String> command = new ArrayList<>();
                     command.add( UtilJVM.findJavaCmd() );
                     command.add( "-javaagent:lib/lang.jar" );
                     command.add( "-cp" );
                     command.add( getClasspath() );
                     command.addAll( lstOptions );
                     command.add( "com.peyrona.mingle." + jarName.replace( ".jar", "" ) + ".Main" );
                     command.addAll( lstArgs );

        return command;
    }

    //------------------------------------------------------------------------//
    // PRIVATE SCOPE

    private static String getClasspath()
    {
        if( sClassPath == null )
        {
            sClassPath = String.join( File.pathSeparator,
                                      findJarsRecursive(UtilSys.getWorkingDir(), "controllers", new HashSet<>() ) );
        }

        return sClassPath;
    }

    private static String quoteArgument( String arg )
    {
        if( UtilSys.isEmpty( arg ) )
            return "\"\"";

        if( arg.contains( " " ) || arg.contains( "\"" ) || arg.contains( "'" ) || arg.contains( "\\" ) )
        {
            // Escape backslashes and quotes, then wrap in double quotes
            return "\"" + arg.replace( "\\", "\\\\" ).replace( "\"", "\\\"" ) + "\"";
        }

        return arg;
    }

    private static Set<String> findJarsRecursive( File currentDirectory, String excludeFolderName, Set<String> lstClassPath )
    {
        File[] filesAndDirs = currentDirectory.listFiles();

        if( filesAndDirs == null )
            return lstClassPath;

        for( File fileOrDir : filesAndDirs )
        {
            if( fileOrDir.isDirectory() )
            {
                if( ! fileOrDir.getName().equals( excludeFolderName ) )
                    findJarsRecursive( fileOrDir, excludeFolderName, lstClassPath );

            }
            else if( fileOrDir.isFile() && fileOrDir.getName().toLowerCase().endsWith( ".jar" ) )
            {
                lstClassPath.add( fileOrDir.getAbsolutePath() );
            }
        }

        return lstClassPath;
    }

    private static String findJavaCmd()
    {
        // 1. Look for System property "java.home"
        String sJavaHome = System.getProperty( "java.home" );

        if( sJavaHome != null )
        {
            String sJavaExe = path2Exec( new File( sJavaHome ) );

            if( sJavaExe != null )
                return sJavaExe;
        }

        // 2. Look for JDK/JRE in current directory
        File[] afDirs = UtilSys.getWorkingDir().listFiles( File::isDirectory );

        if( afDirs != null )
        {
            for( File fDir : afDirs )
            {
                String dirName = fDir.getName().toLowerCase();

                if( dirName.contains( "jdk" ) || dirName.contains( "jre" ) )
                {
                    String sJavaExe = path2Exec( new File( fDir, "bin" ) );

                    if( sJavaExe != null )
                        return sJavaExe;
                }
            }
        }

        // 3. Check JAVA_HOME environment variable (equivalent to checking ${JAVA_HOME:-})
        String javaHomeEnv = System.getenv( "JAVA_HOME" );

        if( javaHomeEnv != null )
        {
            String sJavaExe = path2Exec( new File( javaHomeEnv ) );

            if( sJavaExe != null )
                return sJavaExe;
        }

        // 4. Check if java is in PATH (equivalent to command -v java)
        Process process = null;

        try
        {
            String[] args = UtilSys.sOS.contains("wind") ? new String[]{"where","java"}
                                                         : new String[]{"which","java"};

            process = new ProcessBuilder( args ).start();

            try( BufferedReader bf = new BufferedReader( new InputStreamReader( process.getInputStream(), StandardCharsets.UTF_8 ) ) )
            {
                String sPath = bf.readLine();

                if( sPath != null )
                {
                    String sJavaExe = path2Exec( new File( sPath ) );

                    if( sJavaExe != null )
                        return sJavaExe;
                }
            }
        }
        catch( IOException e )
        {
            // Ignore PATH check errors
        }
        finally
        {
            if( process != null )
                process.destroyForcibly();
        }

        // 5. Fallback: try to rediscover Java from current process if all else fails
        try
        {
            String currentJavaPath = ProcessHandle.current().info().command().orElse( null );

            if( UtilSys.isNotEmpty( currentJavaPath ) )
            {
                File currentJavaFile = new File( currentJavaPath );

                if( currentJavaFile.exists() && currentJavaFile.canExecute() )
                {
                    // Validate this is actually a Java executable
                    Process testProcess = new ProcessBuilder( currentJavaPath, "-version" ).start();
                    testProcess.waitFor( 2, java.util.concurrent.TimeUnit.SECONDS );

                    if( testProcess.exitValue() == 0 )
                    {
                        String sJavaExe = path2Exec( currentJavaFile );

                        if( sJavaExe != null )
                            return sJavaExe;
                    }
                }
            }
        }
        catch( Exception e )
        {
            // Ignore fallback errors
        }

        // 6. Java not found - offer to download (equivalent to script's download prompt)
        System.out.println( "Unexpected situation: Java was not found. Can not continue." );    // Unexpected because this is a Java app an it is running
        System.exit( 1 );
        return null;    // This line won't be reached due to exit() calls above
    }

    private static String path2Exec( File fPath )
    {
        File   fExe;
        String sExEName = UtilSys.sOS.contains( "wind" ) ? "java.exe" : "java";

        // Double check: just in case

             if( fPath.isDirectory() )  fExe = new File( fPath.getAbsolutePath() );                   // Most probable
        else if( fPath.isFile() )       fExe = new File( fPath.getParentFile().getAbsolutePath() );   // Is not ".../bin/", but e.g. ".../bin/java.exe"
        else                            return null;

        fExe = new File( fExe, "bin" + File.separator + sExEName );

        if( fExe.exists() && fExe.canExecute() )
        {
            String sPath = fExe.getParentFile().getAbsolutePath();
            String sLibs = System.getProperty( "java.library.path", "" );
                   sLibs = sPath + (sLibs.isEmpty() ? "" : (File.pathSeparator + sLibs));

            // Set JAVA_HOME and PATH (equivalent to export commands)
            System.setProperty( "java.home"        , sPath );
            System.setProperty( "java.library.path", sLibs );

            return fExe.getAbsolutePath();
        }

        return null;
    }
}