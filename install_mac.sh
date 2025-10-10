#!/bin/bash

# Step 1: Create a folder named "mingle" in the user's home directory
MINGLE_DIR="$HOME/mingle"
echo "Last Mingle Version along with JDK 11 will be downloaded to $MINGLE_DIR"
echo "Later you can move this folder to any other place if you want."
read -n 1 -p "Press key to continue..."
mkdir -p "$MINGLE_DIR" || { echo "ERROR: Failed to create directory $MINGLE_DIR"; exit 1; }

# Step 2: Create a subfolder named "java-11" inside "mingle"
JAVA_DIR="$MINGLE_DIR/java-11"
mkdir -p "$JAVA_DIR" || { echo "ERROR: Failed to create directory $JAVA_DIR"; exit 1; }

# Step 3: Detect CPU architecture and set appropriate JDK URL
ARCH=$(uname -m)
if [ "$ARCH" = "arm64" ]; then
    JDK_URL="https://github.com/adoptium/temurin11-binaries/releases/latest/download/OpenJDK11U-jdk_aarch64_mac_hotspot.tar.gz"
else
    JDK_URL="https://github.com/adoptium/temurin11-binaries/releases/latest/download/OpenJDK11U-jdk_x64_mac_hotspot.tar.gz"
fi

# Step 4: Download Java JDK version 11 from Adoptium into the "java-11" folder
echo "Downloading JDK 11 for macOS ($ARCH)..."
curl -L -o "$JAVA_DIR/jdk11.tar.gz" "$JDK_URL" || { echo "ERROR: Failed to download JDK 11."; exit 1; }

# Check if the JDK archive was downloaded successfully
if [ ! -f "$JAVA_DIR/jdk11.tar.gz" ]; then
    echo "ERROR: JDK download failed."
    exit 1
fi

# Step 5: Extract the downloaded JDK into the "java-11" folder
echo "Extracting JDK 11..."
tar -xzf "$JAVA_DIR/jdk11.tar.gz" -C "$JAVA_DIR" || { echo "ERROR: Failed to extract JDK."; exit 1; }

# Check if extraction was successful
JDK_FOLDER=$(find "$JAVA_DIR" -type d -name "jdk-11*" | head -n 1)
if [ ! -d "$JDK_FOLDER" ]; then
    echo "ERROR: JDK extraction failed."
    exit 1
fi

# Step 6: Download "update.jar" into the "mingle" folder
# Check if wget is installed, if not, provide instructions to install it
if ! command -v wget &> /dev/null; then
    echo "wget could not be found. Please install wget using Homebrew:"
    echo "Run: brew install wget"
    exit 1
fi

echo "Downloading Mingle..."
MSP="https://github.com/peyrona/mingle/msp"
# Use wget to download all files and folders recursively
wget --recursive --no-parent --no-clobber --convert-links --adjust-extension --directory-prefix="$MINGLE_DIR" "$MSP"

echo "You can run the IDE at any time by executing 'run_lin.sh'"
