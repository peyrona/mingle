#Requires -Version 5.1
<#
.SYNOPSIS
    Mingle start script for Windows systems (Windows 10 and above)

.DESCRIPTION
    This script:
      1. Checks if Mingle is needed to be installed
      2. Checks if is running on a RPi, if so, invokes another script to install WiringPi lib.
      3. Checks for Java, downloads it if not present
      4. Runs the 'menu' application JAR

.NOTES
    Run with: powershell -ExecutionPolicy Bypass -File run-win.ps1
#>

[CmdletBinding()]
param(
    [Parameter(ValueFromRemainingArguments = $true)]
    [string[]]$PassthroughArgs
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

# ---------------------------------------------------------------------------------------------
# Global variables
# ---------------------------------------------------------------------------------------------

$script:ScriptDir = ""
$script:JavaCmd = ""

# ---------------------------------------------------------------------------------------------
# Utility Functions
# ---------------------------------------------------------------------------------------------

function Test-IsAdmin {
    $currentPrincipal = New-Object Security.Principal.WindowsPrincipal([Security.Principal.WindowsIdentity]::GetCurrent())
    return $currentPrincipal.IsInRole([Security.Principal.WindowsBuiltInRole]::Administrator)
}

function Write-Log {
    param(
        [Parameter(Mandatory = $true)]
        [ValidateSet("INFO", "WARN", "ERROR")]
        [string]$Level,

        [Parameter(Mandatory = $true)]
        [string]$Message
    )

    $timestamp = Get-Date -Format "yyyy-MM-dd HH:mm:ss"
    Write-Host "[$timestamp] [$Level] $Message" -ForegroundColor $(
        switch ($Level) {
            "ERROR" { "Red" }
            "WARN"  { "Yellow" }
            default { "White" }
        }
    )
}

function Exit-WithError {
    param(
        [Parameter(Mandatory = $true)]
        [string]$Message
    )

    Write-Log -Level "ERROR" -Message $Message
    exit 1
}

function Initialize-Environment {
    # Get script directory
    $script:ScriptDir = Split-Path -Parent $MyInvocation.ScriptName
    if ([string]::IsNullOrEmpty($script:ScriptDir)) {
        $script:ScriptDir = $PSScriptRoot
    }
    if ([string]::IsNullOrEmpty($script:ScriptDir)) {
        $script:ScriptDir = (Get-Location).Path
    }

    Set-Location -Path $script:ScriptDir -ErrorAction Stop

    # Create log directory if it doesn't exist
    $logDir = Join-Path -Path $script:ScriptDir -ChildPath "log"
    if (-not (Test-Path -Path $logDir -PathType Container)) {
        New-Item -Path $logDir -ItemType Directory -Force | Out-Null
    }
}

# ---------------------------------------------------------------------------------------------
# Java Detection and Installation
# ---------------------------------------------------------------------------------------------

function Find-Java {
    # Look for JDK/JRE in the current directory (e.g., one downloaded by this script)
    $jdkPatterns = @("*jdk*", "*jre*")

    foreach ($pattern in $jdkPatterns) {
        $jdkDirs = Get-ChildItem -Path "." -Directory -Filter $pattern -ErrorAction SilentlyContinue
        foreach ($jdkDir in $jdkDirs) {
            $javaPath = Join-Path -Path $jdkDir.FullName -ChildPath "bin\java.exe"
            if (Test-Path -Path $javaPath -PathType Leaf) {
                Write-Log -Level "INFO" -Message "Found local Java in: $($jdkDir.FullName)"
                $script:JavaCmd = $javaPath
                return $true
            }
        }
    }

    # Check if JAVA_HOME is set and valid
    if ($env:JAVA_HOME -and (Test-Path -Path "$env:JAVA_HOME\bin\java.exe" -PathType Leaf)) {
        Write-Log -Level "INFO" -Message "Found Java via JAVA_HOME: $env:JAVA_HOME"
        $script:JavaCmd = Join-Path -Path $env:JAVA_HOME -ChildPath "bin\java.exe"
        return $true
    }

    # Check if 'java' is in the system's PATH
    $javaInPath = Get-Command -Name "java" -ErrorAction SilentlyContinue
    if ($javaInPath) {
        Write-Log -Level "INFO" -Message "Found 'java' in system PATH"
        $script:JavaCmd = $javaInPath.Source
        return $true
    }

    Write-Log -Level "WARN" -Message "No suitable Java installation found."
    return $false
}

function Install-Java {
    Write-Log -Level "INFO" -Message "Attempting to download and install Adoptium JDK 17..."

    # --- Detect Architecture ---
    $arch = $env:PROCESSOR_ARCHITECTURE
    $jdkArch = ""
    $targetDir = ""

    switch ($arch) {
        "AMD64" {
            Write-Log -Level "INFO" -Message "Detected AMD64 (x64) architecture."
            $jdkArch = "x64"
            $targetDir = "jdk.17.win.x64"
        }
        "x86" {
            Write-Log -Level "INFO" -Message "Detected x86 (32-bit) architecture."
            $jdkArch = "x86"
            $targetDir = "jdk.17.win.x86"
        }
        "ARM64" {
            Write-Log -Level "INFO" -Message "Detected ARM64 architecture."
            $jdkArch = "aarch64"
            $targetDir = "jdk.17.win.aarch64"
        }
        default {
            Exit-WithError -Message "Unsupported architecture for automatic download: $arch"
        }
    }

    # --- Variables ---
    $jdkUrl = "https://api.adoptium.net/v3/binary/latest/17/ga/windows/$jdkArch/jdk/hotspot/normal/eclipse"
    $downloadFile = "jdk-17-win.zip"

    # --- 1. Download JDK ---
    Write-Log -Level "INFO" -Message "Downloading from $jdkUrl..."

    try {
        # Use TLS 1.2 for secure connection
        [Net.ServicePointManager]::SecurityProtocol = [Net.SecurityProtocolType]::Tls12

        $progressPreference = 'SilentlyContinue'  # Speeds up Invoke-WebRequest
        Invoke-WebRequest -Uri $jdkUrl -OutFile $downloadFile -UseBasicParsing
    }
    catch {
        if (Test-Path -Path $downloadFile) {
            Remove-Item -Path $downloadFile -Force
        }
        Exit-WithError -Message "Failed to download from $jdkUrl. Error: $_"
    }

    if (-not (Test-Path -Path $downloadFile) -or (Get-Item -Path $downloadFile).Length -eq 0) {
        if (Test-Path -Path $downloadFile) {
            Remove-Item -Path $downloadFile -Force
        }
        Exit-WithError -Message "Download failed or resulted in an empty file."
    }

    Write-Log -Level "INFO" -Message "Download complete."

    # --- 2. Create Directory ---
    Write-Log -Level "INFO" -Message "Creating directory .\$targetDir..."
    New-Item -Path $targetDir -ItemType Directory -Force | Out-Null

    # --- 3. Unpack JDK ---
    Write-Log -Level "INFO" -Message "Unpacking $downloadFile into .\$targetDir..."

    try {
        # Extract to a temp location first, then move contents (to handle --strip-components=1 equivalent)
        $tempExtract = "jdk-temp-extract"
        if (Test-Path -Path $tempExtract) {
            Remove-Item -Path $tempExtract -Recurse -Force
        }

        Expand-Archive -Path $downloadFile -DestinationPath $tempExtract -Force

        # Get the single subdirectory (the JDK root folder)
        $extractedFolder = Get-ChildItem -Path $tempExtract -Directory | Select-Object -First 1

        if ($extractedFolder) {
            # Move all contents from the extracted folder to target directory
            Get-ChildItem -Path $extractedFolder.FullName | Move-Item -Destination $targetDir -Force
        }

        # Clean up temp extraction folder
        Remove-Item -Path $tempExtract -Recurse -Force
    }
    catch {
        Exit-WithError -Message "Failed to unpack the JDK archive. Error: $_"
    }

    Write-Log -Level "INFO" -Message "Extraction complete."

    # --- 4. Delete Archive ---
    Write-Log -Level "INFO" -Message "Cleaning up $downloadFile..."
    Remove-Item -Path $downloadFile -Force

    Write-Log -Level "INFO" -Message "JDK 17 is ready in .\$targetDir"
}

# ---------------------------------------------------------------------------------------------
# Bootstrap: Download and Install
# Called when the script is piped from a one-liner (app files not yet present).
# Existing files are never replaced.
# ---------------------------------------------------------------------------------------------

function Expand-ZipNoOverwrite {
    <#
    .SYNOPSIS
        Extracts a ZIP archive, skipping files that already exist at the destination.
    #>
    param(
        [Parameter(Mandatory = $true)]
        [string]$ZipPath,

        [Parameter(Mandatory = $true)]
        [string]$DestPath
    )

    Add-Type -AssemblyName System.IO.Compression.FileSystem
    $zip = [System.IO.Compression.ZipFile]::OpenRead($ZipPath)

    try {
        foreach ($entry in $zip.Entries) {
            # Directory entries have names ending with '/'
            if ($entry.FullName.EndsWith('/') -or $entry.FullName.EndsWith('\')) {
                $dirPath = Join-Path $DestPath $entry.FullName
                New-Item -ItemType Directory -Path $dirPath -Force | Out-Null
                continue
            }

            $destFile = Join-Path $DestPath $entry.FullName

            # Skip if file already exists (no-overwrite behavior)
            if (Test-Path -Path $destFile -PathType Leaf) {
                continue
            }

            # Ensure parent directory exists
            $destDir = Split-Path $destFile -Parent
            if (-not (Test-Path -Path $destDir)) {
                New-Item -ItemType Directory -Path $destDir -Force | Out-Null
            }

            [System.IO.Compression.ZipFileExtensions]::ExtractToFile($entry, $destFile)
        }
    }
    finally {
        $zip.Dispose()
    }
}

function Bootstrap-App {
    param(
        [string[]]$Arguments
    )

    $currentDir = (Get-Location).Path

    # If lib\menu.jar already exists here, no download needed
    $existingJar = Join-Path $currentDir "lib\menu.jar"
    if (Test-Path -Path $existingJar -PathType Leaf) {
        Write-Log -Level "INFO" -Message "Mingle already present in current directory."
        $scriptPath = Join-Path $currentDir "run-win.ps1"
        & powershell -ExecutionPolicy Bypass -File $scriptPath @Arguments
        exit $LASTEXITCODE
    }

    $currentDirName = Split-Path $currentDir -Leaf

    if ($currentDirName -eq "mingle") {
        $installDir = $currentDir
        Write-Log -Level "INFO" -Message "Already inside a 'mingle' directory. Installing here: '$installDir'..."
    }
    else {
        $installDir = Join-Path $currentDir "mingle"
        Write-Log -Level "INFO" -Message "Bootstrapping Mingle into '$installDir'..."
    }

    # Create install directory if needed
    if (-not (Test-Path -Path $installDir -PathType Container)) {
        New-Item -Path $installDir -ItemType Directory -Force | Out-Null
    }

    # Fetch latest release ZIP URL from GitHub API
    Write-Log -Level "INFO" -Message "Fetching latest release info from GitHub..."

    [Net.ServicePointManager]::SecurityProtocol = [Net.SecurityProtocolType]::Tls12

    try {
        $releaseInfo = Invoke-RestMethod -Uri "https://api.github.com/repos/peyrona/mingle/releases/latest" -UseBasicParsing
        $zipAsset    = $releaseInfo.assets | Where-Object { $_.name -like "*.zip" } | Select-Object -First 1
        $zipUrl      = $zipAsset.browser_download_url
    }
    catch {
        Exit-WithError -Message "Failed to fetch release info from GitHub. Error: $_"
    }

    if ([string]::IsNullOrEmpty($zipUrl)) {
        Exit-WithError -Message "Could not determine the latest release download URL."
    }

    # Download the ZIP
    $zipFile = Join-Path $installDir "_download.zip"
    Write-Log -Level "INFO" -Message "Downloading $zipUrl..."

    try {
        $ProgressPreference = 'Continue'
        Invoke-WebRequest -Uri $zipUrl -OutFile $zipFile -UseBasicParsing
    }
    catch {
        if (Test-Path $zipFile) { Remove-Item $zipFile -Force }
        Exit-WithError -Message "Download failed. Error: $_"
    }

    # Extract without overwriting existing files
    Write-Log -Level "INFO" -Message "Extracting (existing files will not be replaced)..."
    Expand-ZipNoOverwrite -ZipPath $zipFile -DestPath $installDir
    Remove-Item $zipFile -Force

    Write-Log -Level "INFO" -Message "Bootstrap complete. Starting Mingle..."

    $scriptPath = Join-Path $installDir "run-win.ps1"
    & powershell -ExecutionPolicy Bypass -File $scriptPath @Arguments
    exit $LASTEXITCODE
}

# ---------------------------------------------------------------------------------------------
# Main Program
# ---------------------------------------------------------------------------------------------

function Main {
    param(
        [string[]]$Arguments
    )

    # Bootstrap: if running from a pipe (one-liner install), download and install first.
    # $PSScriptRoot is empty when the script is executed via iex/piped input.
    $menuJar = if ($PSScriptRoot) { Join-Path $PSScriptRoot "lib\menu.jar" } else { "lib\menu.jar" }
    if (-not (Test-Path -Path $menuJar -PathType Leaf)) {
        Bootstrap-App -Arguments $Arguments
        return
    }

    Clear-Host

    # Check if running on Windows
    if ($env:OS -ne "Windows_NT") {
        Exit-WithError -Message "This script is intended for Windows systems."
    }

    if (Test-IsAdmin) {
        Write-Log -Level "INFO" -Message "This script is launched by an Admin"
    }
    else {
        Write-Log -Level "INFO" -Message "This script is NOT launched by an Admin"
    }

    Write-Log -Level "INFO" -Message "This script runs 'menu.jar' passing all received parameters"

    Initialize-Environment

    # If Java is not found, download it.
    if (-not (Find-Java)) {
        Install-Java

        # After download, try to find it again to set JavaCmd.
        if (-not (Find-Java)) {
            Exit-WithError -Message "Java check failed. Could not find an usable Java installation even after download."
        }
    }

    Write-Log -Level "INFO" -Message "Using Java: $script:JavaCmd"

    $menuJar = Join-Path -Path $script:ScriptDir -ChildPath "lib/menu.jar"
    if (-not (Test-Path -Path $menuJar -PathType Leaf)) {
        Exit-WithError -Message "The application file 'menu.jar' was not found."
    }

    if ($null -eq $Arguments -or $Arguments.Count -eq 0) {
        Write-Host "Press any key to continue..."
        $null = $Host.UI.RawUI.ReadKey("NoEcho,IncludeKeyDown")
    }

    # All arguments passed to this script will be forwarded to the application.
    # Use Start-Process to launch independently, so GUI apps survive console closure
    $javaArgs = @("-jar", $menuJar) + $Arguments
    Start-Process -FilePath $script:JavaCmd -ArgumentList $javaArgs
}

# Entry point
Main -Arguments $PassthroughArgs

# >>>>>>>>>>>>>>>> EOF <<<<<<<<<<<<<<<<<<<
