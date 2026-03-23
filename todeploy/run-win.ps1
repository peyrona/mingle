#Requires -Version 5.1
<#
.SYNOPSIS
    Mingle start script for Windows systems (Windows 10 and above)

.DESCRIPTION
    This script:
      1. Checks if Mingle is needed to be installed (Bootstrap)
      2. Checks for Java, downloads it if not present
      3. Runs the 'menu' application JAR
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
    param([Parameter(Mandatory = $true)][string]$Message)
    Write-Log -Level "ERROR" -Message $Message
    exit 1
}

function Initialize-Environment {
    $script:ScriptDir = $PSScriptRoot
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
# Bootstarp function: to make Mingle fresh installation
# ---------------------------------------------------------------------------------------------

function Bootstrap-App {
    param([string[]]$Arguments)

    # 1. Determine installation directory
    $currentDir = Get-Location
    $installDir = Join-Path -Path $currentDir.Path -ChildPath "mingle"

    New-Item -Path $installDir -ItemType Directory -Force | Out-Null
    Set-Location -Path $installDir

    # 2. Fetch latest release info
    Write-Log -Level "INFO" -Message "Fetching latest release info from GitHub..."
    try {
        $apiUrl = "https://api.github.com/repos/peyrona/mingle/releases/latest"
        $release = Invoke-RestMethod -Uri $apiUrl -UseBasicParsing
        $zipUrl = $release.assets | Where-Object { $_.browser_download_url -like "*.zip" } | Select-Object -ExpandProperty browser_download_url -First 1
    } catch {
        Remove-Item -Path $installDir -Recurse -Force -ErrorAction SilentlyContinue
        Exit-WithError -Message "Failed to fetch release info from GitHub."
    }

    if (-not $zipUrl) { Exit-WithError -Message "Could not find a zip release." }

    # 3. Download and Unpack
    $zipFile = Join-Path -Path $installDir -ChildPath "_download.zip"
    Write-Log -Level "INFO" -Message "Downloading $zipUrl..."
    try {
        Invoke-WebRequest -Uri $zipUrl -OutFile $zipFile -UseBasicParsing
        Write-Log -Level "INFO" -Message "Extracting files..."
        Expand-Archive -Path $zipFile -DestinationPath $installDir -Force
        Remove-Item -Path $zipFile -Force
    } catch {
        Remove-Item -Path $installDir -Recurse -Force -ErrorAction SilentlyContinue
        Exit-WithError -Message "Bootstrap failed during download/extraction. Cleaned up $installDir."
    }

    # 4. Handoff to the local script
    Write-Log -Level "INFO" -Message "Bootstrap complete. Starting Mingle..."
    & powershell.exe -File .\run-win.ps1 $Arguments
}

# ---------------------------------------------------------------------------------------------
# Java Detection and Installation
# ---------------------------------------------------------------------------------------------

function Find-Java {
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

    if ($env:JAVA_HOME -and (Test-Path -Path "$env:JAVA_HOME\bin\java.exe" -PathType Leaf)) {
        Write-Log -Level "INFO" -Message "Found Java via JAVA_HOME: $env:JAVA_HOME"
        $script:JavaCmd = Join-Path -Path $env:JAVA_HOME -ChildPath "bin\java.exe"
        return $true
    }

    $javaInPath = Get-Command -Name "java" -ErrorAction SilentlyContinue
    if ($javaInPath) {
        Write-Log -Level "INFO" -Message "Found 'java' in system PATH"
        $script:JavaCmd = $javaInPath.Source
        return $true
    }

    return $false
}

function Install-Java {
    Write-Log -Level "INFO" -Message "Attempting to download and install Adoptium JDK 17..."

    $arch = $env:PROCESSOR_ARCHITECTURE
    $jdkArch = switch ($arch) {
        "AMD64" { "x64" }
        "x86"   { "x86" }
        "ARM64" { "aarch64" }
        default { Exit-WithError -Message "Unsupported architecture: $arch" }
    }
    $targetDir = "jdk.17.win.$jdkArch"

    $jdkUrl = "https://api.adoptium.net/v3/binary/latest/17/ga/windows/$jdkArch/jdk/hotspot/normal/eclipse"
    $downloadFile = "jdk-17-win.zip"

    try {
        [Net.ServicePointManager]::SecurityProtocol = [Net.SecurityProtocolType]::Tls12
        $progressPreference = 'SilentlyContinue'
        Invoke-WebRequest -Uri $jdkUrl -OutFile $downloadFile -UseBasicParsing
    }
    catch {
        Exit-WithError -Message "Failed to download JDK: $_"
    }

    New-Item -Path $targetDir -ItemType Directory -Force | Out-Null

    $tempExtract = "jdk-temp-extract"
    Expand-Archive -Path $downloadFile -DestinationPath $tempExtract -Force
    $extractedFolder = Get-ChildItem -Path $tempExtract -Directory | Select-Object -First 1
    Get-ChildItem -Path $extractedFolder.FullName | Move-Item -Destination $targetDir -Force

    Remove-Item -Path $tempExtract -Recurse -Force
    Remove-Item -Path $downloadFile -Force
}

# ---------------------------------------------------------------------------------------------
# Main Program
# ---------------------------------------------------------------------------------------------

function Main {
    param([string[]]$Arguments)

    # Bootstrap detection
    if ([string]::IsNullOrEmpty($MyInvocation.ScriptName)) {
        Bootstrap-App -Arguments $Arguments
        Write-Log -Level "INFO" -Message "Mingle was successfully installed inside 'mingle' folder"
        exit 0
    }

    if ($env:OS -ne "Windows_NT") {
        Exit-WithError -Message "This script is intended for Windows systems."
    }

    Initialize-Environment

    # Java setup
    if (-not (Find-Java)) {
        Install-Java
        if (-not (Find-Java)) {
            Exit-WithError -Message "Java setup failed."
        }
    }

    $menuJar = Join-Path $script:ScriptDir "lib\menu.jar"
    if (-not (Test-Path -Path $menuJar)) {
        Exit-WithError -Message "The application file 'menu.jar' was not found."
    }

    Write-Log -Level "INFO" -Message "Using Java: $script:JavaCmd"

    # Launch application
    $javaArgs = @("-jar", "`"$menuJar`"") + $Arguments
    Start-Process -FilePath $script:JavaCmd -ArgumentList $javaArgs
}

Main -Arguments $PassthroughArgs

# >>>>>>>>>>>>>>>>>>>>>> EOF <<<<<<<<<<<<<<<<<<<<<<<<<<