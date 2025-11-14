# Function to find Java executable
function Find-JavaExecutable
{
    # Check if Java is in PATH
    try
    {
        $javaVersion = & java -version 2>&1

        if ($LASTEXITCODE -eq 0)
        {
            return "java"
        }
    }
    catch
    {
        # Java not found in PATH
    }

    # Check for JDK subdirectories in current directory
    $jdkDirs = Get-ChildItem -Directory | Where-Object { $_.Name -like "*jdk*" }

    if ($jdkDirs)
    {
        # Use the first JDK directory found
        $jdkPath = Join-Path $jdkDirs[0].FullName "bin\java.exe"

        if (Test-Path $jdkPath)
        {
            return $jdkPath
        }
    }

    return $null
}

# Find Java executable
$javaExecutable = Find-JavaExecutable

if (-not $javaExecutable)
{
    Write-Host "Java was not found, I can download it and make it ready to be used by Mingle."
    $response = Read-Host "Do you want to do it now? [Y/n]"

    if ($response -eq "" -or $response -eq "Y" -or $response -eq "y")
    {
        Write-Host "Running Java setup script..."
        & "./etc/get-java-windows.ps1"
        if ($LASTEXITCODE -eq 0)
        {
            Write-Host "Java setup completed. Please run this script again."
        }
        else
        {
            Write-Host "Java setup failed, check: ./etc/get-java-windows.ps1"
        }
    }
    else
    {
        Write-Host "Java setup cancelled."
    }

    Write-Host "Press any key to exit..."
    $null = $Host.UI.RawUI.ReadKey("NoEcho,IncludeKeyDown")
    exit 1
}

# Generate timestamp for log file
$timestamp = Get-Date -Format "yyyy-MM-dd_HH-mm-ss"

# Define the command
$javaCommand = "$javaExecutable -cp glue.jar;tape.jar;lib\*;lib\glue\* -disableassertions -javaagent:lib\lang.jar com.peyrona.mingle.glue.Main"

# Run the Java command in the background and redirect output
try
{
    Start-Process -NoNewWindow -FilePath "cmd.exe" -ArgumentList "/c $javaCommand > glue_$timestamp.out 2>&1"
    Write-Host "Process started..."
}
catch
{
    Write-Host "Error starting process: $_"
    Write-Host "Press any key to exit..."
    $null = $Host.UI.RawUI.ReadKey("NoEcho,IncludeKeyDown")
    exit 1
}
