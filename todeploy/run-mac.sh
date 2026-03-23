#!/bin/bash

set -euo pipefail

# ---------------------------------------------------------------------------------------------
# Mingle start script for macOS systems (version 13 Ventura and above)
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
# ---------------------------------------------------------------------------------------------

bootstrap_app()
{
    # 1. Dependency Check: unzip is vital for the bootstrap
    if ! command -v unzip >/dev/null 2>&1; then
        die "'unzip' is required but not found. Please install it (e.g.: brew install unzip)"
    fi

    # 2. Determine installation directory
    local install_dir
    install_dir="$(pwd)/mingle"
    log "INFO" "Bootstrapping Mingle into '$install_dir'..."

    # 3. Create directory
    mkdir -p "$install_dir" || die "Failed to create directory '$install_dir'."

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
    chmod +x run-mac.sh
    exec bash ./run-mac.sh "$@"
}

init_environment()
{
    # Get script directory and change into it
    SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)" || \
        die "Failed to determine script directory"

    cd "${SCRIPT_DIR}" || die "Failed to change to script directory"

    # Note: DISPLAY export as it is typically not used in macOS Aqua/Quartz
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
            if [[ -x "$jdk_path/Contents/Home/bin/java" ]]; then
                log "INFO" "Found local Java in: $jdk_path"
                JAVA_CMD="$jdk_path/Contents/Home/bin/java"
                return 0
            elif [[ -x "$jdk_path/bin/java" ]]; then
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
        log "INFO" "Detected Intel (x64) architecture."
        JDK_ARCH="x64"
        TARGET_DIR="jdk.17.mac.x64"
    elif [ "$ARCH" = "arm64" ]; then
        log "INFO" "Detected Apple Silicon (arm64) architecture."
        JDK_ARCH="aarch64"
        TARGET_DIR="jdk.17.mac.aarch64"
    else
        die "Unsupported architecture for automatic download: $ARCH"
    fi

    # --- Variables ---
    local JDK_URL="https://api.adoptium.net/v3/binary/latest/17/ga/mac/$JDK_ARCH/jdk/hotspot/normal/eclipse"
    local DOWNLOAD_FILE="jdk-17-mac.tar.gz"

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

    log "INFO" "JDK 17 is ready in ./$TARGET_DIR"
}

# ---------------------------------------------------------------------------------------------
# Main Program
# ---------------------------------------------------------------------------------------------

main()
{
    [[ "$(uname)" == "Darwin" ]] || die "This script is intended for macOS systems."

    # Robust pipe detection: If BASH_SOURCE is not a file on disk, we are in a pipe
    local src="${BASH_SOURCE[0]:-}"
    if [[ ! -f "$src" ]]; then
        bootstrap_app "$@"
        echo "Mingle was successfully installed inside 'mingle' folder"
        exit 0
    fi

    # Normal execution starts here
    init_environment

    # Java setup and application launch
    if ! find_java; then
        download_java
        find_java || die "Java check failed even after download."
    fi

    log "INFO" "Using Java: $JAVA_CMD"
    [[ -f "lib/menu.jar" ]] || die "The application file 'menu.jar' was not found."

    # Keep macOS-specific user prompt if no args are passed
    if [ $# -eq 0 ]; then
        echo "Press any key to continue..."
        read -r -s -n 1
    fi

    # Using 'exec' instead of 'setsid' for better macOS compatibility
    exec "$JAVA_CMD" -jar lib/menu.jar "$@"
}

main "$@"

# >>>>>>>>>>>>>>>> EOF <<<<<<<<<<<<<<<<<<<
