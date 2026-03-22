#!/bin/bash

set -euo pipefail

# ---------------------------------------------------------------------------------------------
# Mingle start script for Linux systems
#
# This script:
# 1. Checks if Mingle is needed to be installed
# 2. Checks if is running on a RPi, if so, invokes another script to install WiringPi lib.
# 3. Checks for Java, downloads it if not present
# 4. Runs the 'menu' application JAR
# ---------------------------------------------------------------------------------------------

# Global variables
SCRIPT_DIR=""
JAVA_CMD=""

# ---------------------------------------------------------------------------------------------
# Utility Functions
# ---------------------------------------------------------------------------------------------

isRoot()
{
    [ "$EUID" -eq 0 ] || [ "$(id -u)" -eq 0 ]
}

log()
{
    local level=$1
    shift
    echo "[$(date +'%Y-%m-%d %H:%M:%S')] [$level] $*" >&2
}

die()
{
    log "ERROR" "$*"
    exit 1
}

# ---------------------------------------------------------------------------------------------
# Bootstrap: Download and Install
# Called when the script is piped from a one-liner (app files not yet present).
# Existing files are never replaced.
# ---------------------------------------------------------------------------------------------

bootstrap_app()
{
    # 1. Dependency Check: unzip is vital for the bootstrap
    if ! command -v unzip >/dev/null 2>&1; then
        die "'unzip' is required but not found. Please install it (e.g.: sudo apt-get update && sudo apt-get install unzip)"
    fi

    # 2. Determine installation directory
    local install_dir
    if [[ "$(basename "$(pwd)")" == "mingle" ]]; then
        install_dir="$(pwd)"
        log "INFO" "Already inside a 'mingle' directory. Installing here..."
    else
        install_dir="$(pwd)/mingle"
        log "INFO" "Bootstrapping Mingle into '$install_dir'..."
    fi

    # 3. Create directory with proper permissions
    mkdir -p "$install_dir" || die "Failed to create directory '$install_dir'. Try running with sudo if permissions are restricted."

    # 4. Fetch latest release info
    log "INFO" "Fetching latest release info from GitHub..."
    local api_response zip_url
    api_response=$(curl -fsSL "https://api.github.com/repos/peyrona/mingle/releases/latest") || \
        die "Failed to fetch release info from GitHub."

    zip_url=$(echo "$api_response" | grep '"browser_download_url"' | grep '\.zip"' | head -1 | cut -d'"' -f4 || true)
    [[ -n "$zip_url" ]] || die "Could not determine the latest release download URL."

    # 5. Download and Unpack
    local zip_file="$install_dir/_download.zip"
    log "INFO" "Downloading $zip_url..."
    curl -L --progress-bar -o "$zip_file" "$zip_url" || { rm -f "$zip_file"; die "Download failed."; }

    log "INFO" "Extracting files..."
    unzip -o "$zip_file" -d "$install_dir" || die "Extraction failed."
    rm "$zip_file"

    # 6. Handoff to the local script
    log "INFO" "Bootstrap complete. Starting Mingle..."
    cd "$install_dir" || die "Failed to change to '$install_dir'"

    # Ensure the local script is executable
    chmod +x run-lin.sh
    exec bash ./run-lin.sh "$@"
}

init_environment()
{
    # Get script directory and change into it, so all paths are relative
    SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)" || \
        die "Failed to determine script directory"

    cd "${SCRIPT_DIR}" || die "Failed to change to script directory"

    if [ ! -d "log" ]; then
        mkdir log
    fi

    if isRoot; then
        chmod -R 777 log      # Proper directory permissions: rwxr-xr-x
    fi

    # Ensure DISPLAY is set for GUI applications (needed when launched from .desktop files)
    if [ -z "${DISPLAY:-}" ]; then
        export DISPLAY=":0"
        log "WARN" "DISPLAY was not set. Defaulting to :0"
    fi
}

# ---------------------------------------------------------------------------------------------
# Java Detection and Installation
# ---------------------------------------------------------------------------------------------

find_java()
{
    local jdk_path

    # Look for JDK/JRE in the current directory (e.g., one downloaded by this script)
    for pattern in "*jdk*" "*jre*"; do
        while IFS= read -r -d '' jdk_path; do
            if [[ -x "$jdk_path/bin/java" ]]; then
                log "INFO" "Found local Java in: $jdk_path"
                JAVA_CMD="$jdk_path/bin/java"
                return 0
            fi
        done < <(find . -maxdepth 1 -type d -name "$pattern" -print0 2>/dev/null || true)
    done

    # Check if JAVA_HOME is set and valid
    if [[ -n "${JAVA_HOME:-}" && -x "$JAVA_HOME/bin/java" ]]; then
        log "INFO" "Found Java via JAVA_HOME: $JAVA_HOME"
        JAVA_CMD="$JAVA_HOME/bin/java"
        return 0
    fi

    # Check if 'java' is in the system's PATH
    if command -v java >/dev/null 2>&1; then
        log "INFO" "Found 'java' in system PATH"
        JAVA_CMD="java"
        return 0
    fi

    log "WARN" "No suitable Java installation found."
    return 1
}

download_java()
{
    log "INFO" "Attempting to download and install Adoptium JDK 17..."

    # --- Detect Architecture ---
    local ARCH
    ARCH=$(uname -m)
    local JDK_ARCH
    local TARGET_DIR

    if [ "$ARCH" = "x86_64" ]; then
        log "INFO" "Detected AMD64 (x64) architecture."
        JDK_ARCH="x64"
        TARGET_DIR="jdk.17.linux.x64"
    elif [ "$ARCH" = "i686" ] || [ "$ARCH" = "i386" ]; then
        log "INFO" "Detected AMD32 (x86) architecture."
        JDK_ARCH="x86"
        TARGET_DIR="jdk.17.linux.x86"
    elif [ "$ARCH" = "aarch64" ] || [ "$ARCH" = "arm64" ]; then
        log "INFO" "Detected ARM64 architecture."
        JDK_ARCH="aarch64"
        TARGET_DIR="jdk.17.linux.aarch64"
    elif [ "$ARCH" = "armv7l" ] || [ "$ARCH" = "armv6l" ]; then
        log "INFO" "Detected ARM32 architecture."
        JDK_ARCH="arm"
        TARGET_DIR="jdk.17.linux.arm"
    else
        die "Unsupported architecture for automatic download: $ARCH"
    fi

    # --- Variables ---
    local JDK_URL="https://api.adoptium.net/v3/binary/latest/17/ga/linux/$JDK_ARCH/jdk/hotspot/normal/eclipse"
    local DOWNLOAD_FILE="jdk-17-linux.tar.gz"

    # --- 1. Download JDK ---
    log "INFO" "Downloading from $JDK_URL..."
    if ! curl -L --progress-bar -o "$DOWNLOAD_FILE" "$JDK_URL"; then
        rm -f "$DOWNLOAD_FILE" # Clean up partial file
        die "curl command failed to download from $JDK_URL"
    fi

    if [ ! -s "$DOWNLOAD_FILE" ]; then
        rm -f "$DOWNLOAD_FILE" # Clean up empty file
        die "Download failed or resulted in an empty file."
    fi
    log "INFO" "Download complete."

    # --- 2. Create Directory ---
    log "INFO" "Creating directory ./$TARGET_DIR..."
    mkdir -p "$TARGET_DIR"

    # --- 3. Unpack JDK ---
    log "INFO" "Unpacking $DOWNLOAD_FILE into ./$TARGET_DIR..."
    if ! tar xzf "$DOWNLOAD_FILE" -C "$TARGET_DIR" --strip-components=1; then
        die "Failed to unpack the JDK archive."
    fi
    log "INFO" "Extraction complete."

    # --- 4. Delete Archive ---
    log "INFO" "Cleaning up $DOWNLOAD_FILE..."
    rm "$DOWNLOAD_FILE"

    log "INFO" "✅ JDK 17 is ready in ./$TARGET_DIR"
}

# ---------------------------------------------------------------------------------------------
# Main Program
# ---------------------------------------------------------------------------------------------

main()
{
    # Robust pipe detection: If BASH_SOURCE is not a file on disk, we are in a pipe
    local src="${BASH_SOURCE[0]:-}"
    if [[ ! -f "$src" ]]; then
        bootstrap_app "$@"
        return # bootstrap_app uses 'exec', but return is a safety fallback
    fi

    # Normal execution starts here
    clear
    [[ "$(uname)" == "Linux" ]] || die "This script is intended for Linux systems."

    init_environment

    # Check for Raspberry Pi GPIO setup
    if [ -f "lib/rpi/wiringpi.sh" ]; then
        bash "lib/rpi/wiringpi.sh"
    else
        die "The application file 'lib/rpi/wiringpi.sh' was not found. Try running the installation one-liner again."
    fi

    # Java setup and application launch
    if ! find_java; then
        download_java
        find_java || die "Java check failed even after download."
    fi

    log "INFO" "Using Java: $JAVA_CMD"
    [[ -f "lib/menu.jar" ]] || die "The application file 'menu.jar' was not found."

    setsid "$JAVA_CMD" -jar lib/menu.jar "$@"
}

main "$@"

# >>>>>>>>>>>>>>>> EOF <<<<<<<<<<<<<<<<<<<