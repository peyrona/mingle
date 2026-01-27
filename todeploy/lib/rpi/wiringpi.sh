#!/bin/bash

set -euo pipefail

# ---------------------------------------------------------------------------------------------
# Raspberry Pi GPIO Setup Script
#
# This script configures GPIO access for Raspberry Pi systems by:
# - Detecting Raspberry Pi hardware
# - Creating gpio group if needed
# - Installing WiringPi from LOCAL files if missing (supports Bookworm and Trixie only)
# - Adding users to gpio group
# - Testing GPIO access
# ---------------------------------------------------------------------------------------------

# ---------------------------------------------------------------------------------------------
# Utility Functions
# ---------------------------------------------------------------------------------------------

isRoot()
{
    [ "${EUID:-$(id -u)}" -eq 0 ]
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

is_valid_OS_version()
{
    local version_id=""

    # Try to get VERSION_ID from standard locations
    if [ -f /etc/os-release ]; then
        version_id=$(grep -Po '^VERSION_ID="\K[^"]+' /etc/os-release 2>/dev/null)
    elif [ -f /etc/debian_version ]; then
        # Fallback for minimal systems without /etc/os-release
        version_id=$(head -1 /etc/debian_version | cut -d. -f1 | tr -d '[:alpha:]\n')
    fi

    # Validate version format and check against required versions
    case "$version_id" in
        12|13)
            return 0  # True - valid version
            ;;
        *)
            # Handle special cases like "bookworm" codenames in testing repos
            if [ -f /etc/os-release ]; then
                local codename
                codename=$(grep -Po '^VERSION_CODENAME="\K[^"]+' /etc/os-release 2>/dev/null)
                case "$codename" in
                    bookworm|trixie) return 0 ;;  # True for codenames
                esac
            fi
            return 1  # False - invalid version
            ;;
    esac
}

wiringpi_lib_exists()
{
    # Check for WiringPi library (including versioned files like libwiringPi.so.2.61)
    # Include architecture-specific library paths for ARM systems
    local lib_dirs=(
        "/usr/lib"
        "/usr/local/lib"
        "/lib"
        "/usr/lib/arm-linux-gnueabihf"
        "/usr/lib/aarch64-linux-gnu"
        "/lib/arm-linux-gnueabihf"
        "/lib/aarch64-linux-gnu"
    )

    for dir in "${lib_dirs[@]}"; do
        # Check for any libwiringPi.so* file
        if ls "$dir"/libwiringPi.so* &> /dev/null; then
            return 0              # WiringPi library found
        fi
    done

    # Also check if 'gpio' command exists (indicates WiringPi tools are installed)
    if command -v gpio &> /dev/null;
    then
        return 0                  # WiringPi library found
    fi

    # Check common paths for gpio command (may not be in PATH after fresh install)
    if [ -x "/usr/bin/gpio" ] || [ -x "/usr/local/bin/gpio" ]; then
        return 0                  # WiringPi library found
    fi

    return 1    # WiringPi library not found
}

check_libgpiod()
{
    command -v gpiodetect &> /dev/null && command -v gpioinfo &> /dev/null
}

fix_wiringpi_library()
{
    log "INFO" "Checking WiringPi library setup..."

    # Include architecture-specific library paths for ARM systems
    local lib_dirs=(
        "/usr/lib"
        "/usr/local/lib"
        "/lib"
        "/usr/lib/arm-linux-gnueabihf"
        "/usr/lib/aarch64-linux-gnu"
        "/lib/arm-linux-gnueabihf"
        "/lib/aarch64-linux-gnu"
    )
    local changes_made=0

    # Enable nullglob to handle cases where no files match the pattern
    shopt -s nullglob

    for dir in "${lib_dirs[@]}"; do
        # Find versioned library files (e.g., libwiringPi.so.2.61)
        for lib in "$dir"/libwiringPi.so.*; do
            if [ -f "$lib" ]; then
                # Fix permissions if needed
                if [ ! -r "$lib" ]; then
                    log "INFO" "Fixing permissions on $lib..."
                    chmod 644 "$lib" && changes_made=1
                fi

                # Create unversioned symlink if it doesn't exist
                local base_symlink="$dir/libwiringPi.so"

                if [ ! -e "$base_symlink" ]; then
                    log "INFO" "Creating symlink: $base_symlink -> $(basename "$lib")"
                    ln -s "$(basename "$lib")" "$base_symlink" && changes_made=1
                fi
            fi
        done

        # Also check/fix the unversioned library if it exists as a file
        if [ -f "$dir/libwiringPi.so" ] && [ ! -r "$dir/libwiringPi.so" ]; then
            log "INFO" "Fixing permissions on $dir/libwiringPi.so..."
            chmod 644 "$dir/libwiringPi.so" && changes_made=1
        fi
    done

    # Disable nullglob
    shopt -u nullglob

    if [ $changes_made -eq 1 ]; then
        log "INFO" "WiringPi library setup completed."
        # Update library cache
        if command -v ldconfig &> /dev/null; then
            ldconfig 2>/dev/null || true
        fi
    else
        log "INFO" "WiringPi library is properly configured."
    fi
}

# ---------------------------------------------------------------------------------------------
# GPIO Group Management Functions
# ---------------------------------------------------------------------------------------------

create_gpio_group()
{
    log "INFO" "Creating gpio group with GID 997..."

    if groupadd -r -g 997 gpio 2>/dev/null; then
        log "INFO" "GPIO group created successfully."
        return 0
    else
        # Check if group already exists (not an error)
        if getent group gpio >/dev/null 2>&1; then
            log "INFO" "GPIO group already exists."
            return 0
        else
            log "ERROR" "Failed to create gpio group."
            return 1
        fi
    fi
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

add_specific_user_to_gpio()
{
    local username=$1

    if id "$username" &>/dev/null; then
        log "INFO" "Adding user '$username' to gpio group..."
        if usermod -aG gpio "$username"; then
             log "INFO" "Successfully added $username to gpio group."
        else
             log "ERROR" "Failed to add $username to gpio group."
        fi
    else
        log "WARNING" "User '$username' does not exist. Skipping."
    fi
}

add_user_to_gpio()
{
    local actual_user
    actual_user=$(get_actual_user)
    add_specific_user_to_gpio "$actual_user"
}

# ---------------------------------------------------------------------------------------------
# WiringPi Installation Functions
# ---------------------------------------------------------------------------------------------

install_wiringpi()
{
    log "INFO" "Installing WiringPi from local files..."

    # Detect location of script to find local lib directory
    local SCRIPT_DIR
    SCRIPT_DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" &> /dev/null && pwd )"

    # --- Detect Architecture ---
    local ARCH
    ARCH="$(dpkg --print-architecture 2>/dev/null || uname -m)"
    local WIRINGPI_FILE=""

    # Map architecture to filename
    # Based on file listing: wiringpi_3.16_arm64.deb, wiringpi_3.16_armhf.deb
    if [[ "$ARCH" == "arm64" || "$ARCH" == "aarch64" ]]; then
        WIRINGPI_FILE="wiringpi_3.16_arm64.deb"
    elif [[ "$ARCH" == "armhf" || "$ARCH" == "armv7l" || "$ARCH" == "armv6l" ]]; then
        WIRINGPI_FILE="wiringpi_3.16_armhf.deb"
    else
        log "ERROR" "Unsupported architecture: $ARCH"
        return 1
    fi

    local FULL_PATH="$SCRIPT_DIR/$WIRINGPI_FILE"

    if [ ! -f "$FULL_PATH" ]; then
        log "ERROR" "Local WiringPi package not found: $FULL_PATH"
        return 1
    fi

    # --- Install WiringPi ---
    log "INFO" "Installing $WIRINGPI_FILE..."

    if ! dpkg -i "$FULL_PATH"; then
        log "ERROR" "Failed to install WiringPi package."
        return 1
    fi

    log "INFO" "✅ WiringPi installed successfully."
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

    # Test WiringPi access - check common paths if command lookup fails
    local gpio_cmd=""
    if command -v gpio &> /dev/null;
    then
        gpio_cmd="gpio"
    elif [ -x "/usr/bin/gpio" ]; then
        gpio_cmd="/usr/bin/gpio"
    elif [ -x "/usr/local/bin/gpio" ]; then
        gpio_cmd="/usr/local/bin/gpio"
    fi

    if [ -n "$gpio_cmd" ]; then
        echo "Testing with '$gpio_cmd readall'..."

        # Capture output to display meaningful errors
        local OUTPUT
        if OUTPUT=$($gpio_cmd readall 2>&1); then
            echo "GPIO access verified successfully!"
            return 0
        else
            log "WARNING" "GPIO access test failed. Error details:"
            echo "---------------------------------------------------"
            echo "$OUTPUT"
            echo "---------------------------------------------------"

            if echo "$OUTPUT" | grep -q "GLIBC"; then
                log "ERROR" "GLIBC Version Mismatch Detected!"
                echo "The installed WiringPi package requires a newer OS version (newer GLIBC)."
                echo "Solution: Use an older .deb package (e.g. 2.52) or upgrade your OS."
            else
                log "INFO" "You may need to logout and login again or reboot."
            fi
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
    elif wiringpi_lib_exists; then
        # Library exists but no command-line tools to test with
        echo "WiringPi library is installed (gpio command not available for testing)."
        echo "GPIO should work for applications using the library directly."
        return 0
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

    # WiringPi is required for Java GPIO code (uses JNA to call native C functions).
    # libgpiod alone is not sufficient - we need WiringPi library installed.
    if ! wiringpi_lib_exists; then
        log "WARNING" "WiringPi library not found. Attempting to install WiringPi..."

        if ! install_wiringpi; then
            log "ERROR" "Failed to install WiringPi library. GPIO access may not work."
            # Do NOT return 1 here if we want to continue with group setup,
            # but usually it's better to continue as much as possible.
        fi
    fi

    # Fix library symlinks and permissions (fixes "file does not exist" and "Permission denied" errors)
    if wiringpi_lib_exists; then
        fix_wiringpi_library
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
        add_specific_user_to_gpio "$actual_user"
    else
        log "INFO" "User '$actual_user' is already in gpio group."
    fi

    # PROMPT for additional users
    if [ -t 0 ]; then
        echo ""
        echo "---------------------------------------------------------"
        echo "Would you like to add other users to the 'gpio' group?"
        
        # Show current members of the gpio group
        CURRENT_MEMBERS=$(getent group gpio | cut -d: -f4)
        if [ -n "$CURRENT_MEMBERS" ]; then
            echo "Current members of 'gpio' group: $CURRENT_MEMBERS"
        else
            echo "The 'gpio' group currently has no members."
        fi

        echo "Enter usernames separated by commas (or press Enter to skip):"
        read -r EXTRA_USERS

        if [ -n "$EXTRA_USERS" ]; then
            IFS=',' read -ra ADDR <<< "$EXTRA_USERS"
            for user in "${ADDR[@]}"; do
                # Trim whitespace
                user=$(echo "$user" | xargs)
                if [ -n "$user" ]; then
                    add_specific_user_to_gpio "$user"
                fi
            done
        fi
    else
        log "INFO" "Non-interactive mode detected. Skipping additional user prompt."
    fi

    echo ""
    echo "IMPORTANT: Group membership changes require:"
    echo "   - Logout and login again, OR"
    echo "   - Reboot the system"
    echo ""
    echo "After applying changes, run this script again to verify GPIO access."

    # Test GPIO access
    check_gpio
}

# ---------------------------------------------------------------------------------------------
# Main Program
# ---------------------------------------------------------------------------------------------

main()
{
    if ! is_raspberry_pi; then
        return 0
    fi

    # WiringPi is required for Java GPIO code (uses JNA to call native C functions).
    # libgpiod alone is not sufficient - we need WiringPi library installed.
    if wiringpi_lib_exists; then
        return 0
    fi

    if ! is_valid_OS_version; then
       die "Can not install 'wiringpi' lib. Invalid Debian version. Valid are: 12 (Bookworm) or 13 (Trixie)"
    fi

    clear
    echo "===================================="
    echo "   Raspberry Pi GPIO Setup Script   "
    echo "===================================="

    echo "This script configures GPIO access for Raspberry Pi systems."
    echo "It is needed to be executed only once."
    echo ""
    echo "Note: This script installs WiringPi version 3.16 from LOCAL files."

    if ! isRoot; then
        echo "This script needs root privileges. Can not continue."
        return 1
    fi

    echo ""

    if [ -t 0 ]; then
        echo "Press any key to continue or [Ctrl-C] to abort script..."
        read -r -s -n 1
    else
        echo "Non-interactive mode detected. Continuing automatically..."
    fi

    # Initialize GPIO setup
    init_gpio
}

main "$@"

# >>>>>>>>>>>>>>>> EOF <<<<<<<<<<<<<<<<<<<