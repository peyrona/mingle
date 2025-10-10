#!/bin/bash

ARCH=$(uname -m)
if [ "$ARCH" = "aarch64" ]; then
    readonly TARGET="Raspberry Pi"
    readonly EXEC_FILE="'run_lin.sh'"
    readonly JDK_URL="https://github.com/adoptium/temurin11-binaries/releases/download/jdk-11.0.24%2B8/OpenJDK11U-jdk_aarch64_linux_hotspot_11.0.24_8.tar.gz"
else
    readonly TARGET="Linux"
    readonly MINGLE_DIR="$HOME/mingle"
    readonly EXEC_FILE="'run_lin.sh'"
    readonly JDK_URL="https://github.com/adoptium/temurin11-binaries/releases/download/jdk-11.0.24%2B8/OpenJDK11U-jdk_x64_linux_hotspot_11.0.24_8.tar.gz"
fi

readonly MINGLE_DIR="$HOME/mingle"

#---------------------------------------------------------------------------------------------------
# FUNCTIONS

# Function to check if a command exists
command_exists()
{
    command -v "$1" >/dev/null 2>&1
}

# Function to extract major version number from Java version string
get_java_major_version()
{
    local version=$1
    # Extract the first number from version string
    echo "$version" | awk -F[\"\.] '{print $1}'
}

# Function to search for a pattern in directory names (case insensitive)
# Parameters:
#   $1: pattern to search for
# Returns:
#   0 if pattern is found in any directory name, 1 otherwise
find_dir_pattern() {
    local pattern="$1"

    # Convert pattern to lowercase
    pattern=$(echo "$pattern" | tr '[:upper:]' '[:lower:]')

    # Use find to search for directories and check if any match the pattern
    while IFS= read -r dir; do
        # Convert directory name to lowercase and check for pattern
        if [[ $(echo "$dir" | tr '[:upper:]' '[:lower:]') == *"$pattern"* ]]; then
            return 0
        fi
    done < <(find "$MINGLE_DIR" -type d)

    return 1
}

#---------------------------------------------------------------------------------------------------

clear

# Welcome
echo "----------------------------------------------------"
echo "   Mingle installation script for $TARGET"
echo "----------------------------------------------------"
echo ""
echo "Last Mingle version along with JDK 11 will be downloaded to folder: $MINGLE_DIR"
echo "(after installation, you can move this folder to any other place if you want)"
echo ""
read -n 1 -p "Press any key to continue (or Ctrl-C to abort)..."
echo ""

# Create a folder named "mingle" in the user's home directory
mkdir -p "$MINGLE_DIR" || { echo "ERROR: Failed to create directory $MINGLE_DIR"; echo "Can not continue"; exit 1; }

# Is Java JDK 11 or higher already installed in the OS?
JDK_OK=1;   # 1 indicates error/requirement not met

if command_exists java && command_exists javac; then
    java_version_string=$(java -version 2>&1 | head -n 1 | cut -d'"' -f2)
    java_major_version=$(get_java_major_version "$java_version_string")
    # Check if version could be determined and is >= 11
    if [ -n "$java_major_version" ] && [ "$java_major_version" -ge 11 ]; then
        JDK_OK=0  # 0 typically means success in bash
        echo "JDK 11 or above is installed in OS"
    fi
fi

# Java JDK 11 or higher is not installed in the OS but it could be already downloaded into 'MINGLE_DIR'
if find_dir_pattern "jdk"; then
    JDK_OK=0  # 0 typically means success in bash
    echo "'*jdk*' folder found inside folder: $MINGLE_DIR"
else
    echo "JDK 11 or above not found, I attempting to download it."
    read -n 1 -p "Press any key to continue..."
fi

# Download Java JDK version 11 from Adoptium into the "java-11" folder if needed
if [ "$JDK_OK" -ne 0 ] ; then
    echo ""
    echo ""
    echo "Downloading JDK 11, wait..."
    curl -L -o "$MINGLE_DIR/jdk11.tar.gz" "$JDK_URL"

    # Check if the JDK archive was downloaded successfully
    if [ ! -f "$MINGLE_DIR/jdk11.tar.gz" ]; then
        echo "ERROR: JDK download failed."
        echo "You have to download it manually (the folder must contains 'jdk' in its name to be recognized propoerly)."
        echo "Once it is downloaded and extracted, re-run this script again"
        read -n 1 -p "Press any key to continue..."
        exit 1
    else
        # Extract the downloaded JDK into the "java-11" folder
        echo ""
        echo ""
        echo "Extracting JDK ..."
        tar -xzf "jdk11.tar.gz" -C "$MINGLE_DIR"

        # Check if extraction was successful
        JDK_FOLDER=$(find "$MINGLE_DIR" -type d -name "*jdk*" | head -n 1)

        if [ ! -d "$JDK_FOLDER" ]; then
            echo "ERROR: JDK extraction failed."
            echo "You have to extract it manually (the folder must contains 'jdk' in its name to be recognized propoerly)."
            echo "Once it is downloaded and extracted, re-run this script again"
            read -n 1 -p "Press any key to continue..."
            exit 1
        fi
    fi


    # The tar.gz is not needed any longer
    rm "$MINGLE_DIR/jdk11.tar.gz"
fi

# Download MSP into the "mingle" folder
echo ""
echo ""
echo "Downloading Mingle..."
MSP="https://github.com/peyrona/mingle/msp"
# Use wget to download all files and folders recursively
wget --recursive --no-parent --no-clobber --convert-links --adjust-extension --directory-prefix="$MINGLE_DIR" "$MSP"

echo ""
echo ""
echo "To run Mingle IDE, execute $EXEC_FILE (be sure this is an executable file: the 'x' flag is ON)"
