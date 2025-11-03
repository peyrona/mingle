#!/bin/bash

# --- Script Configuration ---

# This script downloads and unpacks the latest Adoptium JDK 11.
# It will create a directory named 'jdk.11.linux' in the current
# directory and place the JDK contents inside it.

# Exit immediately if a command exits with a non-zero status.
set -e
# Treat unset variables as an error.
set -u

# --- Variables ---
# API URL to get the latest GA (General Availability) release for JDK 11, Linux, x64
JDK_URL="https://api.adoptium.net/v3/binary/latest/11/ga/linux/x64/jdk/hotspot/normal/eclipse"
DOWNLOAD_FILE="jdk-11-linux.tar.gz"
TARGET_DIR="jdk.11.linux"


# --- 1. Download JDK ---
echo "Downloading Adoptium JDK 11, wait..."
# -L: Follow redirects
# -s: Silent mode
# -S: Show errors if they occur
# -o: Output file
curl -LsSo "$DOWNLOAD_FILE" "$JDK_URL"

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
echo "   ./$TARGET_DIR/bin/java -version"