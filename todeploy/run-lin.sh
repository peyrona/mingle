#!/bin/bash

set -euo pipefail

# ---------------------------------------------------------------------------------------------
# Mingle start script for Linux systems
#
# This script checks for Java, downloads it if not present, and then runs the
# main application JAR.
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
    log "INFO" "Attempting to download and install Adoptium JDK 11..."

    # --- Detect Architecture ---
    local ARCH
    ARCH=$(uname -m)
    local JDK_ARCH
    local TARGET_DIR

    if [ "$ARCH" = "x86_64" ]; then
        log "INFO" "Detected AMD64 (x64) architecture."
        JDK_ARCH="x64"
        TARGET_DIR="jdk.11.linux.x64"
    elif [ "$ARCH" = "i686" ] || [ "$ARCH" = "i386" ]; then
        log "INFO" "Detected AMD32 (x86) architecture."
        JDK_ARCH="x86"
        TARGET_DIR="jdk.11.linux.x86"
    elif [ "$ARCH" = "aarch64" ] || [ "$ARCH" = "arm64" ]; then
        log "INFO" "Detected ARM64 architecture."
        JDK_ARCH="aarch64"
        TARGET_DIR="jdk.11.linux.aarch64"
    elif [ "$ARCH" = "armv7l" ] || [ "$ARCH" = "armv6l" ]; then
        log "INFO" "Detected ARM32 architecture."
        JDK_ARCH="arm"
        TARGET_DIR="jdk.11.linux.arm"
    else
        die "Unsupported architecture for automatic download: $ARCH"
    fi

    # --- Variables ---
    local JDK_URL="https://api.adoptium.net/v3/binary/latest/11/ga/linux/$JDK_ARCH/jdk/hotspot/normal/eclipse"
    local DOWNLOAD_FILE="jdk-11-linux.tar.gz"

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

    log "INFO" "✅ JDK 11 is ready in ./$TARGET_DIR"
}

# ---------------------------------------------------------------------------------------------
# Main Program
# ---------------------------------------------------------------------------------------------

main()
{
    clear

    [[ "$(uname)" == "Linux" ]] || die "This script is intended for Linux systems."

    if isRoot; then
        log "INFO" "This script is launched by an Admin"
    else
        log "INFO" "This script is NOT launched by an Admin"
    fi

    init_environment

    if [ $# -eq 0 ]; then    # No parameters provided
        log "INFO" "This script runs 'menu.jar' passing all received parameters"
    fi

    # If Java is not found, download it.
    if ! find_java; then
        download_java
        # After download, try to find it again to set JAVA_CMD.
        if ! find_java; then
            die "Java check failed. Could not find an usable Java installation even after download."
        fi
    fi

    log "INFO" "Using Java: $JAVA_CMD"

    if [ ! -f "menu.jar" ]; then
        die "The application file 'menu.jar' was not found in the current directory."
    fi

    if [ $# -eq 0 ]; then    # No parameters provided
        echo "Press any key to continue..."
        read -r -s -n 1
    fi

    # All arguments passed to this script will be forwarded to the application.
    "$JAVA_CMD" -jar menu.jar "$@"
}

main "$@"

# >>>>>>>>>>>>>>>> EOF <<<<<<<<<<<<<<<<<<<