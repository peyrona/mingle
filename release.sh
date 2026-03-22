#!/bin/bash
# ==============================================================================
# This script takes all needed actions to create a new MSP release.
#
# USAGE:
#   ./release.sh
#
#
# Architecture Diagram
# Repository Root (/home/francisco/proyectos/mingle/)
# ├── catalog.json                    ← Git-tracked (source of truth)
# ├── release.sh                      ← Generates catalog.json
# └── todeploy/
#     ├── etc/
#     │   └── catalog.json          ← Copied by release.sh (runtime use)
#     ├── lib/
#     └── ... (other deployment files)
# Updater Behavior:
# 1. Downloads catalog.json from GitHub (repository root)
# 2. Compares with local todeploy/etc/catalog.json
# 3. Updates files based on catalog entries
# Git Behavior:
# - Tracks: catalog.json (at root)
# - Ignores: todeploy/etc/catalog.json (deployment copy)
# ==============================================================================

set -euo pipefail
clear
echo "📦 Creating a new release ..."

# --- Version Handling ---
# Regex pattern for exactly three segments separated by dots, where each segment is one or more digits.
# Example: 1.0.5 or 12.34.567
VERSION_PATTERN='^[0-9]+\.[0-9]+\.[0-9]+$'

# 1. Check command-line argument first
if [ $# -gt 0 ]; then
    VERSION="$1"
    # Check if the argument is valid before proceeding
    if [[ "$VERSION" =~ $VERSION_PATTERN ]]; then
        echo "Using version from argument: $VERSION"
        exit 0 # Exit the script if version is valid
    else
        echo "Error: Command-line version '$VERSION' is not in the required 'n.n.n' format."
        # Fall through to the interactive prompt loop
    fi
fi

# 2. Loop to prompt user until a valid version is provided
while true; do
    read -p "Please enter the version (format n.n.n): " version_input
    VERSION="${version_input}"

    # Check the format using the regex pattern
    if [[ "$VERSION" =~ $VERSION_PATTERN ]]; then
        echo "Version '$VERSION' accepted."
        break # Exit the loop since the format is correct
    else
        echo "Invalid format. Version must be 'n.n.n' (e.g., 1.2.3)."
    fi
done

#-------------------------------------------------------------------------------
# Exit the whole script if FILE is >= 5 minutes old.
# Prefers creation time (birth) when available; falls back to modification time.
check()
{
    local path="$1"

    if [[ -z "$path" ]]; then
      echo "ERROR: missing file path" >&2
      exit 1
    fi

    if [[ ! -e "$path" ]]; then
      echo "ERROR: file not found: $path" >&2
      exit 1
    fi

    # Get birth time (creation) in seconds since Epoch if known; 0 if unknown.
    # Then get modification time in seconds since Epoch.
    local birth mtime
    birth=$(stat -c %W -- "$path" 2>/dev/null || echo 0)
    mtime=$(stat -c %Y -- "$path")

    # Prefer birth if available; otherwise use modification time.
    local ts="$mtime"
    if [[ "$birth" != "0" && "$birth" -gt 0 ]]; then
      ts="$birth"
    fi

    # Current time in seconds since Epoch.
    local now
    now=$(date +%s)

    # Age in seconds; 5 minutes = 300 seconds.
    local age=$(( now - ts ))
    if (( age >= 5*60 )); then
      echo "INFO: $path is at least 5 minutes old; exiting."
      exit 0
    fi
}

#-------------------------------------------------------------------------------

echo "Compiling all JARs and copying to 'todeploy/'"
echo "Wait..."

PRJ_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

# All JARs go into todeploy/lib/
declare -a PROJECTS=(
    "lang:lib"
    "network:lib"
    "controllers:lib"
    "candi:lib"
    "cil:lib"
    "tape:lib"
    "stick:lib"
    "gum:lib"
    "glue:lib"
    "menu:lib"
    "updater:lib"
)

mkdir -p "$PRJ_ROOT/todeploy/lib"
cp "$PRJ_ROOT/lang/lib/minimal-json-0.9.5.jar" "$PRJ_ROOT/todeploy/lib/"

for entry in "${PROJECTS[@]}"; do
    IFS=':' read -r prj dest_subdir <<< "$entry"
    dest_dir="$PRJ_ROOT/todeploy/${dest_subdir:+$dest_subdir/}"

    echo "Building $prj..."

    (cd "$PRJ_ROOT/$prj" && ant clean jar > /dev/null) || {
        echo "⛔️ Ant build failed for '$prj'. Exiting script."
        exit 1
    }

    jar_path="$PRJ_ROOT/$prj/dist/${prj}.jar"
    check "$jar_path"

    cp "$jar_path" "$dest_dir"
done

echo "✅ All JARs built and copied successfully"


cd "$PRJ_ROOT"

# ==============================================================================
# Une examples validation: ensures that provided examples are valid Une code
# ==============================================================================

echo ""
read -p "Do you want to transpile all Une examples? (y/N): " generate_javadocs

if [[ "$generate_javadocs" =~ ^[Yy]$ ]]; then
    echo "🔍 Transpiling all Une examples..."

    rm $PRJ_ROOT/todeploy/examples/*.model

    java -cp "$PRJ_ROOT/todeploy/lib/*" \
         com.peyrona.mingle.tape.Main "$PRJ_ROOT/todeploy/examples/*.une" \
         -config="$PRJ_ROOT/todeploy/etc/config.json" ||
               {
                  echo "⛔️ Une compilation failed for one or more examples. Exiting script."
                  exit 1
               }

    java -cp "$PRJ_ROOT/todeploy/lib/*" \
         com.peyrona.mingle.tape.Main "$PRJ_ROOT/todeploy/examples/grid/*.une" \
         -grid=true \
         -config="$PRJ_ROOT/todeploy/etc/config.json" ||
               {
                  echo "⛔️ Une compilation failed for one or more examples. Exiting script."
                  exit 1
               }

    echo "✅ All examples transpiled successfully."
fi

# ==============================================================================
# Genarting Java API HTML docs
# ==============================================================================

echo ""
read -p "Do you want to generate Javadocs? (y/N): " generate_javadocs

if [[ "$generate_javadocs" =~ ^[Yy]$ ]]; then
    echo "📄 Generating Javadocs ..."
    ./generate_javadocs.sh "${VERSION:0:3}" || { echo "⛔️ Javadoc generation failed. Exiting script."; exit 1; }
    zip -r javadocs.zip javadocs/* > /dev/null 2>&1
    mv javadocs.zip todeploy/docs/
    echo "📄 Javadocs created, zipped and 'javadocs.zip' moved to 'todeploy/docs/'"
    read -rs -n1 -p "Press any key to continue..."
else
    echo "⏭️  Skipping Javadoc generation"
fi

# ==============================================================================
# Converting ODTs into PDFs
# ==============================================================================

# The directory containing the files to be deployed.
DEPLOY_DIR=$PRJ_ROOT/todeploy

echo ""
read -p "Do you want to generate PDFs? (y/N): " generate_pdfs

if [[ "$generate_pdfs" =~ ^[Yy]$ ]]; then
    echo "Creating PDFs..."

    soffice --headless --convert-to pdf:writer_pdf_Export --outdir "$DEPLOY_DIR/docs" "$PRJ_ROOT/docs/Une_language.odt" ||
    {
      echo "⛔️ Failed to convert Une_language.odt to PDF"
      exit 1
    }

    soffice --headless --convert-to pdf:writer_pdf_Export --outdir "$DEPLOY_DIR/docs" "$PRJ_ROOT/docs/Mingle_Standard_Platform.odt" ||
    {
      echo "⛔️ Failed to convert Mingle_Standard_Platform.odt to PDF"
      exit 1
    }

    soffice --headless --convert-to pdf:writer_pdf_Export --outdir "$DEPLOY_DIR/docs" "$PRJ_ROOT/docs/Une_reference_sheet.odt" ||
    {
      echo "⛔️ Failed to convert Une_reference_sheet.odt to PDF"
      exit 1
    }
else
    echo "⏭️  Skipping PDF generation"
fi

# ==============================================================================
# Script to generate a JSON file catalog for an updater.
#
# This file is used by the updater to check for and download updates.
# The catalog is generated at repository root (catalog.json) for Git versioning
# and automatically copied to todeploy/etc/catalog.json for runtime deployment.
# It recursively scans the todeploy directory, calculates the SHA-256 hash for each
# file, and generates a `catalog.json` file. It can be configured to
# ignore specific subdirectories.
# ==============================================================================

# --- Configuration ---

# The output file for the catalog (at repository root for Git versioning).
CATALOG_FILE=$PRJ_ROOT/catalog.json

# Directories you want to ignore to this list.
# Paths should be relative to the DEPLOY_DIR above.
IGNORE_DIRS=(
  "log"
  "tmp"
)

# --- Safety Checks ---
# Exit if the source directory does not exist.
if [ ! -d "$DEPLOY_DIR" ]; then
  echo "Error: Source directory '$DEPLOY_DIR' not found."
  exit 1
fi

# --- Main Logic ---
echo "📦 Generating catalog for version $VERSION..."
if [ ${#IGNORE_DIRS[@]} -gt 0 ]; then
  echo "🙈 Ignoring directories: ${IGNORE_DIRS[*]}"
fi

# Dynamically build the arguments for the find command to ignore directories.
find_ignore_args=()

for dir in "${IGNORE_DIRS[@]}"; do
  # Add a "-not -path" argument for each directory to ignore.
  # The "/*" ensures we ignore all content *inside* that directory.
  find_ignore_args+=(-not -path "$DEPLOY_DIR/$dir/*")
done

# 1. Write the JSON header to the output file.
RELEASE_DATE=$(date -u +'%Y-%m-%d')
printf '{\n' > "$CATALOG_FILE"
printf '  "version": "%s",\n' "$VERSION" >> "$CATALOG_FILE"
printf '  "release_date": "%s",\n' "$RELEASE_DATE" >> "$CATALOG_FILE"
printf '  "files": [\n' >> "$CATALOG_FILE"

# 2. Process the files, including the ignore arguments.
first_file=true
find "$DEPLOY_DIR" "${find_ignore_args[@]}" -type f -not -path '*/.*' | sort | while IFS= read -r file_path; do
  # Calculate the SHA-256 hash of the file.
  file_hash=$(sha256sum "$file_path" | awk '{print $1}')

  # Get the relative path by removing the source directory prefix.
  relative_path="${file_path#$DEPLOY_DIR}"
  relative_path="${relative_path#/}"

  # Add comma separator for all but first file
  if [ "$first_file" = true ]; then
    first_file=false
    printf '    { "path": "%s", "hash": "%s" }' "$relative_path" "$file_hash" >> "$CATALOG_FILE"
  else
    printf ',\n    { "path": "%s", "hash": "%s" }' "$relative_path" "$file_hash" >> "$CATALOG_FILE"
  fi
done

# 3. Write the closing brackets for the JSON file.
printf '\n  ]\n}\n' >> "$CATALOG_FILE"

echo "✅ Catalog '$CATALOG_FILE' generated successfully."

# Copy catalog.json to todeploy/etc/ for local application use.
# The catalog at repository root is used for Git versioning, while the copy
# in todeploy/etc/ is used by the running application.
mkdir -p "$DEPLOY_DIR/etc"
cp "$CATALOG_FILE" "$DEPLOY_DIR/etc/catalog.json"
echo "✅ Catalog copied to todeploy/etc/ for deployment."

# ==============================================================================
# Update and commit to GitHub
# ==============================================================================
# Check if a version was passed as a command-line argument.

if [ $# -ge 2 ]; then
    COMMENT="$2"
else
    read -p "Please enter the comment for the commit (default 'Updates for version $VERSION'): " comment_input
    COMMENT="${comment_input:-Updates for version $VERSION}"
fi

echo "📦 Committing changes to GitHub ..."

git config user.name "Peyrona"
git config user.email "peyrona@users.noreply.github.com"

git add .
git commit -m "$COMMENT"
git push

echo "✅ Changes pushed successfully."

# ==============================================================================
# Generate ZIP to be used as new release.
# Creating the ZIP Has to be the last action.
# ==============================================================================

echo "Generating the ZIP..."

cd $PRJ_ROOT/todeploy

# Cleanup already done above
# Hidden files are not included by default in zips

today=$(date -u +'%Y-%m-%d')
fileName="mingle.$VERSION-$today.zip"

zip -r $fileName docs/* etc/* examples/* include/* lib/*       \
                 glue.jar gum.jar menu.jar stick.jar tape.jar  \
                 config.json readme.md                         \
                 rpi.sh run-lin.sh run-mac.sh run-win.ps1      \
                 --exclude "examples/*.model"                  \
                 --exclude "etc/glue-settings.json"            \
                 --exclude '*/.*'                              \
                 --exclude '*/.*/*' > /dev/null 2>&1

cd $PRJ_ROOT
mv $PRJ_ROOT/todeploy/*.zip .

echo "✅ ZIP file created at: todeploy/$fileName"

# ==============================================================================

echo "✅ Mission accomplished."

# >>>>>>>>>>>>>>>>>>> EOF <<<<<<<<<<<<<<<<<<<<<<<