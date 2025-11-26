#!/bin/bash

set -euo pipefail

# ---------------------------------------------------------------------------------------------
# Raspberry Pi GPIO Setup Script
#
# This script configures GPIO access for Raspberry Pi systems by:
# - Detecting Raspberry Pi hardware
# - Creating gpio group if needed
# - Installing WiringPi if missing
# - Adding users to gpio group
# - Testing GPIO access
# ---------------------------------------------------------------------------------------------

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
# Raspberry Pi Detection Functions
# ---------------------------------------------------------------------------------------------

is_raspberry_pi()
{
    grep -q "Raspberry Pi" /proc/cpuinfo 2>/dev/null || \
    grep -q "BCM2835" /proc/cpuinfo 2>/dev/null
}

wiringpi_lib_exists()
{
    local paths=(
        "/usr/lib/libwiringPi.so"
        "/usr/local/lib/libwiringPi.so"
        "/lib/libwiringPi.so"
    )

    for path in "${paths[@]}"; do
        if [ -f "$path" ]; then
            return 0              # WiringPi library found
        fi
    done

    # Also check if 'gpio' command exists (indicates WiringPi tools are installed)
    if command -v gpio &> /dev/null; then
        return 0                  # WiringPi library found
    fi

    return 1    # WiringPi library not found
}

check_libgpiod()
{
    command -v gpiodetect &> /dev/null && command -v gpioinfo &> /dev/null
}

# ---------------------------------------------------------------------------------------------
# GPIO Group Management Functions
# ---------------------------------------------------------------------------------------------

create_gpio_group()
{
    log "INFO" "Creating gpio group with GID 997..."

    groupadd -r -g 997 gpio 2>/dev/null || {
        log "WARNING" "Failed to create gpio group. It may already exist."
        return 1
    }

    log "INFO" "GPIO group created successfully."
    return 0
}

get_actual_user()
{
    # Determine the actual user running the script (handles sudo cases)
    if [ -n "${SUDO_USER:-}" ]; then
        echo "$SUDO_USER"
    else
        echo "$USER"
    fi
}

add_user_to_gpio()
{
    local actual_user
    actual_user=$(get_actual_user)

    log "INFO" "Adding user '$actual_user' to gpio group..."

    if isRoot; then
        adduser "$actual_user" gpio && {
            log "INFO" "Successfully added $actual_user to gpio group."
            return 0
        } || {
            log "ERROR" "Failed to add $actual_user to gpio group."
            return 1
        }
    else
        # Try with sudo if not root
        if command -v sudo &> /dev/null; then
            sudo adduser "$actual_user" gpio && {
                log "INFO" "Successfully added $actual_user to gpio group."
                return 0
            } || {
                log "ERROR" "Failed to add $actual_user to gpio group with sudo."
                return 1
            }
        else
            log "ERROR" "Root privileges required to add user to gpio group."
            return 1
        fi
    fi
}

# ---------------------------------------------------------------------------------------------
# WiringPi Installation Functions
# ---------------------------------------------------------------------------------------------

download_wiringpi()
{
    log "INFO" "Attempting to download and install WiringPi from GitHub..."

    # --- Detect Architecture ---
    local ARCH
    ARCH=$(uname -m)
    local WIRINGPI_FILE
    local WIRINGPI_URL

    if [ "$ARCH" = "aarch64" ] || [ "$ARCH" = "arm64" ]; then
        log "INFO" "Detected ARM64 architecture."
        WIRINGPI_FILE="wiringpi_3.16_arm64.deb"
        WIRINGPI_URL="https://github.com/WiringPi/WiringPi/releases/download/3.16/wiringpi_3.16_arm64.deb"
    elif [ "$ARCH" = "armv7l" ] || [ "$ARCH" = "armv6l" ]; then
        log "INFO" "Detected ARM32 architecture."
        WIRINGPI_FILE="wiringpi_3.16_armhf.deb"
        WIRINGPI_URL="https://github.com/WiringPi/WiringPi/releases/download/3.16/wiringpi_3.16_armhf.deb"
    else
        die "Unsupported architecture for WiringPi download: $ARCH"
    fi

    # --- 1. Download WiringPi ---
    log "INFO" "Downloading from $WIRINGPI_URL..."

    if ! curl -L --progress-bar -o "$WIRINGPI_FILE" "$WIRINGPI_URL"; then
        rm -f "$WIRINGPI_FILE" # Clean up partial file
        die "curl command failed to download from $WIRINGPI_URL"
    fi

    if [ ! -s "$WIRINGPI_FILE" ]; then
        rm -f "$WIRINGPI_FILE" # Clean up empty file
        die "Download failed or resulted in an empty file."
    fi

    log "INFO" "Download complete."

    # --- 2. Install WiringPi ---
    log "INFO" "Installing $WIRINGPI_FILE..."
    if isRoot; then
        if ! dpkg -i "$WIRINGPI_FILE"; then
            rm -f "$WIRINGPI_FILE" # Clean up package file
            die "Failed to install WiringPi package."
        fi
    else
        if ! sudo dpkg -i "$WIRINGPI_FILE"; then
            rm -f "$WIRINGPI_FILE" # Clean up package file
            die "Failed to install WiringPi package with sudo."
        fi
    fi

    # --- 3. Delete Package ---
    log "INFO" "Cleaning up $WIRINGPI_FILE..."
    rm "$WIRINGPI_FILE"

    log "INFO" "✅ WiringPi 3.16 is ready"
}

install_wiringpi()
{
    log "INFO" "Attempting to install WiringPi..."

    apt-get update -qq

    if apt-get install -y wiringpi 2>/dev/null; then
        log "INFO" "WiringPi installed successfully via apt."
        return 0
    elif apt-get install -y raspberrypi-sys-mods 2>/dev/null; then
        log "INFO" "WiringPi installed successfully via raspberrypi-sys-mods."
        return 0
    fi

    # If package manager fails, download from GitHub
    log "WARNING" "Package manager installation failed. Downloading from GitHub..."
    download_wiringpi
}

# ---------------------------------------------------------------------------------------------
# GPIO Testing Functions
# ---------------------------------------------------------------------------------------------

check_gpio()
{
    local actual_user
    actual_user=$(get_actual_user)

    echo ""
    echo "Verifying GPIO access for user '$actual_user'..."

    # Check if user is in gpio group
    if groups "$actual_user" | grep -qw "gpio"; then
        echo "User '$actual_user' is in gpio group."
    else
        log "WARNING" "User '$actual_user' is not in gpio group."
        return 1
    fi

    # Test WiringPi access
    if command -v gpio &> /dev/null; then
        echo "Testing with 'gpio readall' (this may take a moment)..."
        if gpio readall &> /dev/null; then
            echo "GPIO access verified successfully!"
            return 0
        else
            log "WARNING" "GPIO access test failed."
            log "INFO" "You may need to logout and login again or reboot."
            return 1
        fi
    elif check_libgpiod; then
        echo "Testing with 'gpiodetect'..."
        if gpiodetect &> /dev/null; then
            echo "GPIO access verified successfully (libgpiod)!"
            return 0
        else
            log "WARNING" "GPIO access test failed (libgpiod)."
            return 1
        fi
    else
        log "WARNING" "No GPIO testing tools available."
        return 1
    fi
}

# ---------------------------------------------------------------------------------------------
# Main GPIO Setup Function
# ---------------------------------------------------------------------------------------------

init_gpio()
{
    # Although this check is done in ::main(), it has to be done here to because
    # another script could invoke this script via 'init_gpio' (bypassing 'main')
    if ! is_raspberry_pi; then
        log "INFO" "This is not a Raspberry Pi. GPIO setup skipped."
        return 0
    fi

    log "INFO" "Configuring GPIO access..."

    # Check if any GPIO library is available
    if ! wiringpi_lib_exists && ! check_libgpiod; then
        log "WARNING" "No GPIO library found. Attempting to install WiringPi..."

        if ! install_wiringpi; then
            log "ERROR" "Failed to install GPIO library. GPIO access may not work."
            log "INFO" "Please install WiringPi manually:"
            log "INFO" "  sudo apt-get update"
            log "INFO" "  sudo apt-get install wiringpi"
            return 1
        fi
    fi

    # Ensure gpio group exists
    if ! getent group gpio >/dev/null 2>&1; then
        log "WARNING" "GPIO group does not exist. Creating it..."
        if ! create_gpio_group; then
            log "ERROR" "Failed to create GPIO group."
            return 1
        fi
    fi

    # Get actual user and check group membership
    local actual_user
    actual_user=$(get_actual_user)

    if ! groups "$actual_user" | grep -qw "gpio"; then
        log "WARNING" "User '$actual_user' is not in gpio group. Adding..."
        if ! add_user_to_gpio; then
            log "ERROR" "Failed to add user to GPIO group."
            return 1
        fi

        echo ""
        echo "IMPORTANT: Group membership changes require:"
        echo "   - Logout and login again, OR"
        echo "   - Reboot the system"
        echo ""
        echo "After applying changes, run this script again to verify GPIO access."
        return 0
    else
        log "INFO" "User '$actual_user' is already in gpio group."
    fi

    # Test GPIO access
    check_gpio
}

# ---------------------------------------------------------------------------------------------
# Main Program
# ---------------------------------------------------------------------------------------------

main()
{
    clear

    if ! is_raspberry_pi; then
        echo "This is not a Raspberry Pi. GPIO setup skipped."
        return 0
    fi

    echo "===================================="
    echo "   Raspberry Pi GPIO Setup Script   "
    echo "===================================="

    echo "This script configures GPIO access for Raspberry Pi systems."
    echo "It is needed to be executed only once."
    echo ""
    echo "Note: Mingle was tested with WiringPi version 2.61-1."

    if ! isRoot; then
        echo "This script needs root privileges. Can not continue."
        return 1
    fi

    echo ""
    echo "Press any key to continue or [Ctrl-C] to abort script..."
    read -r -s -n 1

    # Initialize GPIO setup
    init_gpio
}

main "$@"

# >>>>>>>>>>>>>>>> EOF <<<<<<<<<<<<<<<<<<<