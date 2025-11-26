#!/usr/bin/env pwsh

#Requires -Version 5.1

# ---------------------------------------------------------------------------------------------
# Mingle start script for Windows systems
#
# This script checks for Java, downloads it if not present, and then runs the
# main application JAR.
# ---------------------------------------------------------------------------------------------

# Global variables
$ScriptDir = ""
$JavaCmd = ""

# ---------------------------------------------------------------------------------------------
# Utility Functions
# ---------------------------------------------------------------------------------------------

function Log {
    param(
        [string]$Level,
        [string]$Message
    )
    $timestamp = Get-Date -Format "yyyy-MM-dd HH:mm:ss"
    Write-Host "[$timestamp] [$Level] $Message" -ForegroundColor $(
        switch ($Level) {
            "ERROR" { "Red" }
            "WARN" { "Yellow" }
            "INFO" { "White" }
            default { "White" }
        }
    )
}

function Die {
    param([string]$Message)
    Log "ERROR" $Message
    exit 1
}

function Init-Environment {
    try
    {
        $ScriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
        Set-Location $ScriptDir
    }
    catch
    {
        Die "Failed to determine script directory"
    }

    if (!(Test-Path "log" -PathType Container))
    {
        New-Item -ItemType Directory -Path "log" -Force
        # Windows handles directory permissions differently - no direct chmod equivalent needed
    }
}

# ---------------------------------------------------------------------------------------------
# Java Detection and Installation
# ---------------------------------------------------------------------------------------------

function Find-Java
{

    # Look for JDK/JRE in the current directory
    $jdkDirs = Get-ChildItem -Directory -Path "." | Where-Object { $_.Name -like "*jdk*" -or $_.Name -like "*jre*" }

    foreach ($jdkDir in $jdkDirs)
    {
        $javaPath = Join-Path $jdkDir.FullName "bin\java.exe"
        if (Test-Path $javaPath)
        {
            Log "INFO" "Found local Java in: $($jdkDir.FullName)"
            $script:JavaCmd = $javaPath
            return $true
        }
    }

    # Check if JAVA_HOME is set and valid
    if ($env:JAVA_HOME)
    {
        $javaPath = Join-Path $env:JAVA_HOME "bin\java.exe"
        if (Test-Path $javaPath)
        {
            Log "INFO" "Found Java via JAVA_HOME: $env:JAVA_HOME"
            $script:JavaCmd = $javaPath
            return $true
        }
    }

    # Check if 'java' is in the system's PATH
    try {
        $javaCmd = Get-Command java -ErrorAction Stop
        if ($javaCmd) {
            Log "INFO" "Found 'java' in system PATH"
            $script:JavaCmd = "java"
            return $true
        }
    }
    catch {
        # Java not found in PATH
    }

    Log "WARN" "No suitable Java installation found."
    return $false
}

function Download-Java {
    Log "INFO" "Attempting to download and install Adoptium JDK 11..."

    # --- Detect Architecture ---
    $arch = $env:PROCESSOR_ARCHITECTURE.ToLower()
    $jdkArch = ""
    $targetDir = ""

    if ($arch -eq "amd64") {
        Log "INFO" "Detected AMD64 (x64) architecture."
        $jdkArch = "x64"
        $targetDir = "jdk.11.windows.x64"
    }
    elseif ($arch -eq "x86") {
        Log "INFO" "Detected x86 architecture."
        $jdkArch = "x86"
        $targetDir = "jdk.11.windows.x86"
    }
    elseif ($arch -eq "arm64") {
        Log "INFO" "Detected ARM64 architecture."
        $jdkArch = "aarch64"
        $targetDir = "jdk.11.windows.aarch64"
    }
    else {
        Die "Unsupported architecture for automatic download: $arch"
    }

    # --- Variables ---
    $jdkUrl = "https://api.adoptium.net/v3/binary/latest/11/ga/windows/$jdkArch/jdk/hotspot/normal/eclipse"
    $downloadFile = "jdk-11-windows.zip"

    # --- 1. Download JDK ---
    Log "INFO" "Downloading from $jdkUrl..."
    try {
        Invoke-WebRequest -Uri $jdkUrl -OutFile $downloadFile -UseBasicParsing
    }
    catch {
        if (Test-Path $downloadFile) {
            Remove-Item $downloadFile -Force
        }
        Die "Failed to download from $jdkUrl"
    }

    if (-not (Test-Path $downloadFile) -or (Get-Item $downloadFile).Length -eq 0) {
        if (Test-Path $downloadFile) {
            Remove-Item $downloadFile -Force
        }
        Die "Download failed or resulted in an empty file."
    }
    Log "INFO" "Download complete."

    # --- 2. Create Directory ---
    Log "INFO" "Creating directory .\$targetDir..."
    New-Item -ItemType Directory -Path $targetDir -Force | Out-Null

    # --- 3. Unpack JDK ---
    Log "INFO" "Unpacking $downloadFile into .\$targetDir..."
    try {
        Expand-Archive -Path $downloadFile -DestinationPath $targetDir -Force
        # Move contents up one level (similar to --strip-components=1)
        $extractedDir = Get-ChildItem -Path $targetDir -Directory | Select-Object -First 1
        if ($extractedDir) {
            Get-ChildItem -Path $extractedDir.FullName | Move-Item -Destination $targetDir -Force
            Remove-Item $extractedDir.FullName -Recurse -Force
        }
    }
    catch {
        Die "Failed to unpack the JDK archive."
    }
    Log "INFO" "Extraction complete."

    # --- 4. Delete Archive ---
    Log "INFO" "Cleaning up $downloadFile..."
    Remove-Item $downloadFile -Force

    Log "INFO" "✅ JDK 11 is ready in .\$targetDir"
}

# ---------------------------------------------------------------------------------------------
# Main Program
# ---------------------------------------------------------------------------------------------

function Main
{
    cls

    if ($PSVersionTable.Platform -and $PSVersionTable.Platform -ne "Win32NT" -and $env:OS -ne "Windows_NT")
    {
        Die "This script is intended for Windows systems."
    }

    Init-Environment

    if ($args.Count -eq 0)    # No parameters provided
    {
        Log "INFO" "This script runs 'menu.jar' passing all received parameters"

        $currentUser = [Security.Principal.WindowsIdentity]::GetCurrent()
        $principal = New-Object Security.Principal.WindowsPrincipal($currentUser)

        if ($principal.IsInRole([Security.Principal.WindowsBuiltInRole]::Administrator))
        {
            Log "INFO" "This script is launched by an Admin"
        }
        else
        {
            Log "INFO" "This script is NOT launched by an Admin"
        }

        Log "INFO" "This script runs 'menu.jar' passing all received parameters"
    }

    # If Java is not found, download it.
    if (-not (Find-Java))
    {
        Download-Java
        # After download, try to find it again to set JavaCmd.
        if (-not (Find-Java))
        {
            Die "Java check failed. Could not find an usable Java installation even after download."
        }
    }

    Log "INFO" "Using Java: $JavaCmd"

    if (-not (Test-Path "menu.jar"))
    {
        Die "The application file 'menu.jar' was not found in the current directory."
    }

    if ($args.Count -eq 0)    # No parameters provided
    {
        Write-Host "Press any key to continue..."
        $null = $Host.UI.RawUI.ReadKey("NoEcho,IncludeKeyDown")
    }

    # All arguments passed to this script will be forwarded to the application.
    & $JavaCmd -jar menu.jar $args
}

Main $args

# >>>>>>>>>>>>>>>> EOF <<<<<<<<<<<<<<<<<<<