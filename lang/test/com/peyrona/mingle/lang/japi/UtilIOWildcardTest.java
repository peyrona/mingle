package com.peyrona.mingle.lang.japi;

import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.net.URI;
import org.junit.Test;
import static org.junit.Assert.*;

/**
 * Unit tests for wildcard handling in UtilIO.expandPath() method.
 * 
 * @author Francisco José Morero Peyrona
 */
public class UtilIOWildcardTest
{
    private static final String TEST_DIR = "test_wildcard_temp";
    
    @Test
    public void testSingleWildcard_NonRecursive() throws IOException, URISyntaxException
    {
        // Setup test directory structure
        File fTestDir = setupTestDirectory();
        
        try
        {
            // Test "*.txt" should match only files in current directory
            List<URI> lstURIs = UtilIO.expandPath( fTestDir.getAbsolutePath() + File.separator + "*.txt" );
            
            // Should find file1.txt and file2.txt, but NOT subfile.txt
            assertEquals( "Should find exactly 2 .txt files in current directory", 2, lstURIs.size() );
            
            boolean bFoundFile1 = false;
            boolean bFoundFile2 = false;
            
            for( URI uri : lstURIs )
            {
                String sFileName = new File( uri ).getName();
                if( sFileName.equals( "file1.txt" ) )
                    bFoundFile1 = true;
                else if( sFileName.equals( "file2.txt" ) )
                    bFoundFile2 = true;
            }
            
            assertTrue( "Should find file1.txt", bFoundFile1 );
            assertTrue( "Should find file2.txt", bFoundFile2 );
        }
        finally
        {
            cleanupTestDirectory( fTestDir );
        }
    }
    
    @Test
    public void testDoubleWildcard_Recursive() throws IOException, URISyntaxException
    {
        // Setup test directory structure
        File fTestDir = setupTestDirectory();
        
        try
        {
            // Test "**/*.txt" should match files in current directory AND subdirectories
            List<URI> lstURIs = UtilIO.expandPath( fTestDir.getAbsolutePath() + File.separator + "**" + File.separator + "*.txt" );
            
            // Should find file1.txt, file2.txt, and subfile.txt
            assertEquals( "Should find exactly 3 .txt files recursively", 3, lstURIs.size() );
            
            boolean bFoundFile1 = false;
            boolean bFoundFile2 = false;
            boolean bFoundSubfile = false;
            
            for( URI uri : lstURIs )
            {
                String sFileName = new File( uri ).getName();
                if( sFileName.equals( "file1.txt" ) )
                    bFoundFile1 = true;
                else if( sFileName.equals( "file2.txt" ) )
                    bFoundFile2 = true;
                else if( sFileName.equals( "subfile.txt" ) )
                    bFoundSubfile = true;
            }
            
            assertTrue( "Should find file1.txt", bFoundFile1 );
            assertTrue( "Should find file2.txt", bFoundFile2 );
            assertTrue( "Should find subfile.txt in subdirectory", bFoundSubfile );
        }
        finally
        {
            cleanupTestDirectory( fTestDir );
        }
    }
    
    @Test
    public void testAllFiles_NonRecursive() throws IOException, URISyntaxException
    {
        // Setup test directory structure
        File fTestDir = setupTestDirectory();
        
        try
        {
            // Test "*" should match all files in current directory only
            List<URI> lstURIs = UtilIO.expandPath( fTestDir.getAbsolutePath() + File.separator + "*" );
            
            // Should find file1.txt, file2.txt, and file3.java, but NOT files in subdirectory
            assertEquals( "Should find exactly 3 files in current directory", 3, lstURIs.size() );
        }
        finally
        {
            cleanupTestDirectory( fTestDir );
        }
    }
    
    @Test
    public void testAllFiles_Recursive() throws IOException, URISyntaxException
    {
        // Setup test directory structure
        File fTestDir = setupTestDirectory();
        
        try
        {
            // Test "**/*" should match all files in current directory AND subdirectories
            List<URI> lstURIs = UtilIO.expandPath( fTestDir.getAbsolutePath() + File.separator + "**" + File.separator + "*" );
            
            // Should find all files including those in subdirectory
            assertEquals( "Should find exactly 5 files recursively", 5, lstURIs.size() );
        }
        finally
        {
            cleanupTestDirectory( fTestDir );
        }
    }
    
    @Test
    public void testComplexPattern_NonRecursive() throws IOException, URISyntaxException
    {
        // Setup test directory structure
        File fTestDir = setupTestDirectory();
        
        try
        {
            // Test "file*.txt" should match only files starting with "file" in current directory
            List<URI> lstURIs = UtilIO.expandPath( fTestDir.getAbsolutePath() + File.separator + "file*.txt" );
            
            // Should find file1.txt and file2.txt, but NOT file3.java or subfile.txt
            assertEquals( "Should find exactly 2 files matching pattern", 2, lstURIs.size() );
        }
        finally
        {
            cleanupTestDirectory( fTestDir );
        }
    }
    
    @Test
    public void testNoGlobPattern() throws IOException, URISyntaxException
    {
        // Setup test directory structure
        File fTestDir = setupTestDirectory();
        
        try
        {
            // Test specific file without wildcards
            String sFilePath = fTestDir.getAbsolutePath() + File.separator + "file1.txt";
            List<URI> lstURIs = UtilIO.expandPath( sFilePath );
            
            // Should find exactly 1 file
            assertEquals( "Should find exactly 1 specific file", 1, lstURIs.size() );
            assertEquals( "Should find the correct file", "file1.txt", new File( lstURIs.get( 0 ) ).getName() );
        }
        finally
        {
            cleanupTestDirectory( fTestDir );
        }
    }
    
    //------------------------------------------------------------------------//
    // PRIVATE HELPER METHODS
    //------------------------------------------------------------------------//
    
    /**
     * Creates a test directory structure for wildcard testing.
     * 
     * @return Test directory File object
     * @throws IOException If directory creation fails
     */
    private File setupTestDirectory() throws IOException
    {
        File fTestDir = new File( TEST_DIR );
        
        // Clean up any existing test directory
        if( fTestDir.exists() )
            deleteDirectory( fTestDir );
        
        // Create test directory structure
        fTestDir.mkdirs();
        
        // Create files in root directory
        new File( fTestDir, "file1.txt" ).createNewFile();
        new File( fTestDir, "file2.txt" ).createNewFile();
        new File( fTestDir, "file3.java" ).createNewFile();
        
        // Create subdirectory with files
        File fSubDir = new File( fTestDir, "subdir" );
        fSubDir.mkdirs();
        new File( fSubDir, "subfile.txt" ).createNewFile();
        new File( fSubDir, "subfile.java" ).createNewFile();
        
        return fTestDir;
    }
    
    /**
     * Cleans up test directory after test completion.
     * 
     * @param fDirectory Directory to delete
     */
    private void cleanupTestDirectory( File fDirectory )
    {
        if( fDirectory != null && fDirectory.exists() )
            deleteDirectory( fDirectory );
    }
    
    /**
     * Recursively deletes a directory and all its contents.
     * 
     * @param fDirectory Directory to delete
     */
    private void deleteDirectory( File fDirectory )
    {
        if( fDirectory == null || ! fDirectory.exists() )
            return;
        
        if( fDirectory.isDirectory() )
        {
            File[] aFiles = fDirectory.listFiles();
            if( aFiles != null )
            {
                for( File fFile : aFiles )
                {
                    if( fFile.isDirectory() )
                        deleteDirectory( fFile );
                    else
                        fFile.delete();
                }
            }
        }
        
        fDirectory.delete();
    }
}