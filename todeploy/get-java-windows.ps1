<#
.SYNOPSIS
    Downloads and unpacks the latest Adoptium JDK 11 for Windows x64.

.DESCRIPTION
    This script performs the following actions:
    1. Downloads the latest JDK 11 (Windows x64) from Adoptium to a .zip file.
    2. Creates a directory named 'jdk.11.windows' in the script's location.
    3. Unpacks the .zip file into a temporary folder.
    4. Moves the contents from the archive's top-level folder into 'jdk.11.windows'.
    5. Deletes the downloaded .zip file and the temporary folder.
    6. Provides clear status messages and error handling.

.EXAMPLE
    .\install_jdk11_windows.ps1
    Executes the script.
#>

# --- Configuration ---

# Stop the script immediately if any command fails
$ErrorActionPreference = "Stop"

# Treat unset variables as an error
Set-StrictMode -Version Latest

# --- Variables ---

# Get the directory where the script is located
# $PSScriptRoot is an automatic variable representing the script's directory
$ScriptDir = $PSScriptRoot

# API URL for JDK 11, Windows, x64
$JdkUrl = "https://api.adoptium.net/v3/binary/latest/11/ga/windows/x64/jdk/hotspot/normal/eclipse"

# Define file and directory paths relative to the script location
$DownloadFile = Join-Path $ScriptDir "jdk-11-windows.zip"
$TargetDir = Join-Path $ScriptDir "jdk.11.windows"
# Create a temporary directory for extraction
$TmpDir = Join-Path $ScriptDir "jdk.11.windows_tmp"


# --- 1. Download JDK ---
Write-Host "Downloading Adoptium JDK 11 for Windows, wait..."
try {
    # -UseBasicParsing is faster and avoids issues in some environments
    Invoke-WebRequest -Uri $JdkUrl -OutFile $DownloadFile -UseBasicParsing
} catch {
    Write-Error "Error during download: $_"
    # Exit with a non-zero status code
    exit 1
}

# Check if download was successful (file exists and is not empty)
if (!(Test-Path $DownloadFile) -or (Get-Item $DownloadFile).Length -lt 1000) { # Check if file is tiny
    Write-Error "Error: Download failed or resulted in an empty/invalid file."
    Remove-Item -Path $DownloadFile -Force -ErrorAction SilentlyContinue
    exit 1
}

Write-Host "Download complete."


# --- 2. Create Directories ---
Write-Host "Creating directories..."
# -Force acts like 'mkdir -p', creating all needed dirs and not erroring if it exists.
# | Out-Null suppresses the output of the directory object.
New-Item -ItemType Directory -Path $TargetDir -Force | Out-Null
New-Item -ItemType Directory -Path $TmpDir -Force | Out-Null


# --- 3. Unpack JDK ---
Write-Host "Unpacking $DownloadFile..."
try {
    # Unzip to the temporary directory, overwriting if needed
    Expand-Archive -Path $DownloadFile -DestinationPath $TmpDir -Force
} catch {
    Write-Error "Error: Failed to unpack archive. Is the file corrupted? $_"
    exit 1
}

# The archive contains a single top-level folder (e.g., 'jdk-11.0.20+8').
# We need to find it and move its *contents* to our target directory.

# Find the single top-level directory inside the temp folder
$InnerDir = Get-ChildItem -Path $TmpDir | Select-Object -First 1
if ($null -eq $InnerDir -or -not $InnerDir.PSIsContainer) {
    Write-Error "Error: Archive seems to be empty or in an unexpected format."
    exit 1
}

Write-Host "Moving files to final destination..."
# Move all items from *inside* the inner directory to the target directory
Get-ChildItem -Path $InnerDir.FullName | Move-Item -Destination $TargetDir

Write-Host "Extraction complete."


# --- 4. Delete Archive and Temp Dir ---
Write-Host "Cleaning up $DownloadFile and temporary files..."
Remove-Item -Path $DownloadFile -Force
Remove-Item -Path $TmpDir -Recurse -Force


# --- Success ---
Write-Host ""
Write-Host "✅ Success! JDK 11 is ready in $TargetDir"
Write-Host "You can check the version with:"
# Use the call operator '&' to execute the command at the specified path
Write-Host "   & '$TargetDir\bin\java.exe' -version"