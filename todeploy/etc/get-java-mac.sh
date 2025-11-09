#!/bin/bash

# --- Script Configuration ---

# This script downloads and unpacks the latest Adoptium JDK 11
# for macOS. It auto-detects for Apple Silicon (aarch64)
# or Intel (x64) and downloads the correct binary.
#
# It will create a directory (e.g., 'jdk.11.macos.aarch64')
# in the current directory and place the JDK contents inside it.

# Exit immediately if a command exits with a non-zero status.
set -e
# Treat unset variables as an error.
set -u

cd .. # Move to Mingle home folder

# --- Detect Architecture ---
ARCH=$(uname -m)
if [ "$ARCH" = "arm64" ]; then
    echo "Detected Apple Silicon (aarch64) architecture."
    JDK_ARCH="aarch64"
    TARGET_DIR="jdk.11.macos.aarch64"
elif [ "$ARCH" = "x86_64" ]; then
    echo "Detected Intel (x64) architecture."
    JDK_ARCH="x64"
    TARGET_DIR="jdk.11.macos.x64"
else
    echo "Error: Unsupported architecture: $ARCH"
    exit 1
fi

# --- Variables ---
JDK_URL="https://api.adoptium.net/v3/binary/latest/11/ga/mac/$JDK_ARCH/jdk/hotspot/normal/eclipse"
DOWNLOAD_FILE="jdk-11-macos.tar.gz"


# --- 1. Download JDK ---
echo "Downloading Adoptium JDK 11 for macOS $JDK_ARCH..."
# -L: Follow redirects
# --progress-bar: Show progress bar
# -S: Show errors if they occur
# -o: Output file
curl -L --progress-bar -o "$DOWNLOAD_FILE" "$JDK_URL"

# Check if download was successful (file is not empty)
if [ ! -s "$DOWNLOAD_FILE" ]; then
    echo "Error: Download failed or resulted in an empty file."
    rm -f "$DOWNLOAD_FILE" # Clean up empty file
    exit 1
fi

echo "Download complete."


# --- 2. Create Directory ---
echo "Creating directory ./$TARGET_DIR..."
# -p: Create parent directories if needed, and don't error if it already exists.
mkdir -p "$TARGET_DIR"


# --- 3. Unpack JDK ---
echo "Unpacking $DOWNLOAD_FILE into ./$TARGET_DIR..."
# x: extract
# z: gzipped file
# f: from file
# -C: Change to directory before extracting
# --strip-components=1: Removes the top-level directory from the archive
tar xzf "$DOWNLOAD_FILE" -C "$TARGET_DIR" --strip-components=1

echo "Extraction complete."


# --- 4. Delete Archive ---
echo "Cleaning up $DOWNLOAD_FILE..."
rm "$DOWNLOAD_FILE"


# --- Success ---
echo ""
echo "✅ Success! JDK 11 is ready in ./$TARGET_DIR"
echo "You can check the version with:"
echo "   ./$TARGET_DIR/Contents/Home/bin/java -version"