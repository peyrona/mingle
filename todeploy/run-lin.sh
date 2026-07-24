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
JAVA_DIST=""    # Java distribution to download: 'temurin' (HotSpot) or 'semeru' (OpenJ9)
JAVA_VER=""     # Java major version to download: '17' or '21'

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
        die "'unzip' is required but not found. Please install it (e.g.: sudo apt-get update && sudo apt-get install unzip)"
    fi

    # 2. Determine installation directory
    local install_dir
    install_dir="$(pwd)/mingle"
    log "INFO" "Bootstrapping Mingle into '$install_dir'..."

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

prompt_java_choice()
{
    # Distribution and version to download. The defaults preserve the previous
    # behaviour (Eclipse Temurin JDK 17) for non-interactive launches.
    JAVA_DIST="temurin"
    JAVA_VER="17"

    # Only ask when a real terminal is available. Reading from /dev/tty (instead
    # of stdin) lets the prompt work even when this script is piped from the
    # 'curl ... | bash' installer. With no controlling terminal (e.g. launched
    # from a .desktop file via setsid) we silently keep the default.
    [ -r /dev/tty ] || return 0

    {
        echo ""
        echo "No Java runtime was found. Choose which one to download and install:"
        echo ""
        echo "  1) JDK 17       - Eclipse Temurin (HotSpot): proven Long-Term Support release; the most"
        echo "                    tested option, and it matches Mingle's build target."
        echo "  2) JDK 21       - Eclipse Temurin (HotSpot): newest Long-Term Support release; latest"
        echo "                    language features and improved performance/garbage collection."
        echo "  3) IBM J9 (17)  - IBM Semeru (OpenJ9): uses less memory and starts faster than HotSpot;"
        echo "                    ideal for Raspberry Pi and other low-resource devices."
        echo "  4) IBM J9 (21)  - IBM Semeru (OpenJ9): the same OpenJ9 efficiency on the newest LTS."
        echo ""
    } >&2

    local choice=""
    read -r -p "Enter choice [1-4] (default 1): " choice < /dev/tty || choice=""

    case "$choice" in
        2) JAVA_DIST="temurin"; JAVA_VER="21" ;;
        3) JAVA_DIST="semeru";  JAVA_VER="17" ;;
        4) JAVA_DIST="semeru";  JAVA_VER="21" ;;
        *) JAVA_DIST="temurin"; JAVA_VER="17" ;;
    esac
}

download_java()
{
    prompt_java_choice

    if [ "$JAVA_DIST" = "semeru" ]; then
        download_semeru "$JAVA_VER"
    else
        download_temurin "$JAVA_VER"
    fi
}

download_temurin()
{
    local ver="$1"
    log "INFO" "Attempting to download and install Eclipse Temurin (HotSpot) JDK $ver..."

    local ARCH
    ARCH=$(uname -m)
    local JDK_URL=""
    local TARGET_DIR=""
    local DOWNLOAD_FILE="jdk-${ver}-linux.tar.gz"

    if [ "$ARCH" = "x86_64" ]; then
        log "INFO" "Detected AMD64 (x64) architecture."
        JDK_URL="https://api.adoptium.net/v3/binary/latest/${ver}/ga/linux/x64/jdk/hotspot/normal/eclipse"
        TARGET_DIR="jdk.${ver}.linux.x64"
    elif [[ "$ARCH" =~ i.86 ]]; then
        die "Linux x86 not supported: after installing it manually, try again this script."
    elif [ "$ARCH" = "aarch64" ] || [ "$ARCH" = "arm64" ]; then
        log "INFO" "Detected ARM64 architecture."
        JDK_URL="https://api.adoptium.net/v3/binary/latest/${ver}/ga/linux/aarch64/jdk/hotspot/normal/eclipse"
        TARGET_DIR="jdk.${ver}.linux.aarch64"
    elif [[ "$ARCH" =~ armv.l ]]; then
        log "INFO" "Detected ARM32 architecture."
        JDK_URL="https://api.adoptium.net/v3/binary/latest/${ver}/ga/linux/arm/jdk/hotspot/normal/eclipse"
        TARGET_DIR="jdk.${ver}.linux.arm32"
    else
        die "Unsupported architecture for automatic download: $ARCH"
    fi

    log "INFO" "Downloading from $JDK_URL..."
    if ! curl -L --progress-bar -o "$DOWNLOAD_FILE" "$JDK_URL"; then
        rm -f "$DOWNLOAD_FILE"
        die "Download failed."
    fi

    mkdir -p "$TARGET_DIR"
    log "INFO" "Unpacking into ./$TARGET_DIR..."
    if ! tar xzf "$DOWNLOAD_FILE" -C "$TARGET_DIR" --strip-components=1; then
        die "Failed to unpack the JDK archive."
    fi
    rm "$DOWNLOAD_FILE"
}

download_semeru()
{
    local ver="$1"
    log "INFO" "Attempting to download and install IBM Semeru (OpenJ9) JDK $ver..."

    local ARCH SARCH
    ARCH=$(uname -m)

    case "$ARCH" in
        x86_64)        SARCH="x64" ;;
        aarch64|arm64) SARCH="aarch64" ;;
        *)
            log "WARN" "IBM Semeru (OpenJ9) ships 64-bit builds only; none available for '$ARCH'. Falling back to Eclipse Temurin JDK 17."
            download_temurin 17
            return 0
            ;;
    esac

    # IBM Semeru has no 'latest binary' redirect API, so resolve the asset URL
    # from the GitHub release. Anchoring on 'open-jdk_<arch>_linux_' and the
    # trailing '.tar.gz"' skips the JRE, debug/test images and sidecar files
    # (.json/.sha256.txt/.sig).
    log "INFO" "Resolving latest IBM Semeru (OpenJ9) $ver for linux/$SARCH..."
    local api_response JDK_URL
    api_response=$(curl -fsSL "https://api.github.com/repos/ibmruntimes/semeru${ver}-binaries/releases/latest") \
        || die "Failed to fetch IBM Semeru release info from GitHub."

    JDK_URL=$(echo "$api_response" \
                | grep '"browser_download_url"' \
                | grep "ibm-semeru-open-jdk_${SARCH}_linux_" \
                | grep '\.tar\.gz"' \
                | head -1 | cut -d'"' -f4 || true)

    if [ -z "$JDK_URL" ]; then
        log "WARN" "No IBM Semeru $ver build found for linux/$SARCH. Falling back to Eclipse Temurin JDK 17."
        download_temurin 17
        return 0
    fi

    local TARGET_DIR="jdk.${ver}.linux.${SARCH}.openj9"
    local DOWNLOAD_FILE="jdk-semeru-${ver}-linux.tar.gz"

    log "INFO" "Downloading from $JDK_URL..."
    if ! curl -L --progress-bar -o "$DOWNLOAD_FILE" "$JDK_URL"; then
        rm -f "$DOWNLOAD_FILE"
        die "Download failed."
    fi

    mkdir -p "$TARGET_DIR"
    log "INFO" "Unpacking into ./$TARGET_DIR..."
    if ! tar xzf "$DOWNLOAD_FILE" -C "$TARGET_DIR" --strip-components=1; then
        die "Failed to unpack the JDK archive."
    fi
    rm "$DOWNLOAD_FILE"
}

# ---------------------------------------------------------------------------------------------
# Main Program
# ---------------------------------------------------------------------------------------------

main()
{
    [[ "$(uname)" == "Linux" ]] || die "This script is intended for Linux systems."

    # Robust pipe detection: If BASH_SOURCE is not a file on disk, we are in a pipe
    local src="${BASH_SOURCE[0]:-}"
    if [[ ! -f "$src" ]]; then
        bootstrap_app "$@"
        echo "Mingle was successfully installed inside 'mingle' folder"
        exit 0
    fi

    # Normal execution starts here
    init_environment

    # Check for Raspberry Pi GPIO setup
    if grep -q "Raspberry Pi" /sys/firmware/devicetree/base/model 2>/dev/null; then
        log "INFO" "Raspberry Pi hardware detected."
        if [ -f "lib/rpi/wiringpi.sh" ]; then
            bash "lib/rpi/wiringpi.sh"
        else
            log "WARN" "The application file 'lib/rpi/wiringpi.sh' was not found. Skipping WiringPi setup."
        fi
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