#!/bin/bash
# ==============================================================================
# This script takes all needed actions to create a new MSP release.
#
# USAGE:
#   ./release.sh [version]
#
# PARAMETERS:
#   [version]: (Optional) The version string to include in the JSON.
#              If not provided, the script will prompt for it.
# ==============================================================================

set -euo pipefail
clear
echo "📦 Creating a release ZIP file ..."

# --- Version Handling ---
# Check if a version was passed as a command-line argument.
if [ "${1:-}" = "" ]; then
  # If not, prompt the user to enter it.
  read -p "Please enter the version (default: 1.2.1): " version_input
  VERSION="${version_input:-1.2.1}"
else
  # If an argument was provided, use it.
  VERSION="$1"
fi

#-------------------------------------------------------------------------------
# Exit the whole script if FILE is >= 15 minutes old.
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

echo "Compiling all JARs and copying to 'todeploy/' ..."

PRJ_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

cd $PRJ_ROOT/lang
ant clean jar > /dev/null || { echo "⛔️ Ant build failed. Exiting script."; exit 1; }
check "$PRJ_ROOT/lang/dist/lang.jar"
cp "$PRJ_ROOT/lang/dist/lang.jar" "$PRJ_ROOT/todeploy/lib/"


cd $PRJ_ROOT/network
ant clean jar > /dev/null || { echo "⛔️ Ant build failed. Exiting script."; exit 1; }
check "$PRJ_ROOT/network/dist/network.jar"
cp "$PRJ_ROOT/network/dist/network.jar" "$PRJ_ROOT/todeploy/lib/"


cd $PRJ_ROOT/candi
ant clean jar > /dev/null || { echo "⛔️ Ant build failed. Exiting script."; exit 1; }
check "$PRJ_ROOT/candi/dist/candi.jar"
cp "$PRJ_ROOT/candi/dist/candi.jar" "$PRJ_ROOT/todeploy/lib/"


cd $PRJ_ROOT/cil
ant clean jar > /dev/null || { echo "⛔️ Ant build failed. Exiting script."; exit 1; }
check "$PRJ_ROOT/cil/dist/cil.jar"
cp "$PRJ_ROOT/cil/dist/cil.jar" "$PRJ_ROOT/todeploy/lib/"


cd $PRJ_ROOT/controllers
ant clean jar > /dev/null || { echo "⛔️ Ant build failed. Exiting script."; exit 1; }
check "$PRJ_ROOT/controllers/dist/controllers.jar"
cp "$PRJ_ROOT/controllers/dist/controllers.jar" "$PRJ_ROOT/todeploy/lib/"


cd $PRJ_ROOT/updater
ant clean jar > /dev/null || { echo "⛔️ Ant build failed. Exiting script."; exit 1; }
check "$PRJ_ROOT/updater/dist/updater.jar"
cp "$PRJ_ROOT/updater/dist/updater.jar" "$PRJ_ROOT/todeploy/lib/"


cd $PRJ_ROOT/tape
ant clean jar > /dev/null || { echo "⛔️ Ant build failed. Exiting script."; exit 1; }
check "$PRJ_ROOT/tape/dist/tape.jar"
cp "$PRJ_ROOT/tape/dist/tape.jar" "$PRJ_ROOT/todeploy/"


cd $PRJ_ROOT/stick
ant clean jar > /dev/null || { echo "⛔️ Ant build failed. Exiting script."; exit 1; }
check "$PRJ_ROOT/stick/dist/stick.jar"
cp "$PRJ_ROOT/stick/dist/stick.jar" "$PRJ_ROOT/todeploy/"


cd $PRJ_ROOT/gum
ant clean jar > /dev/null || { echo "⛔️ Ant build failed. Exiting script."; exit 1; }
check "$PRJ_ROOT/gum/dist/gum.jar"
cp "$PRJ_ROOT/gum/dist/gum.jar" "$PRJ_ROOT/todeploy/"


cd $PRJ_ROOT/glue
ant clean jar > /dev/null || { echo "⛔️ Ant build failed. Exiting script."; exit 1; }
check "$PRJ_ROOT/glue/dist/glue.jar"
cp "$PRJ_ROOT/glue/dist/glue.jar" "$PRJ_ROOT/todeploy/"

cd "$PRJ_ROOT"

# ==============================================================================
# Converting ODTs into PDFs
# ==============================================================================

# The directory containing the files to be deployed.
DEPLOY_DIR=$PRJ_ROOT/todeploy

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

# ==============================================================================
# Script to generate a JSON file catalog for an updater.
#
# It recursively scans a directory, calculates the SHA-256 hash for each
# file, and generates a `catalog.json` file. It can be configured to
# ignore specific subdirectories.
# ==============================================================================

# --- Configuration ---

# The output file for the catalog.
CATALOG_FILE=$DEPLOY_DIR/catalog.json

# Directories you want to ignore to this list.
# Paths should be relative to the DEPLOY_DIR above.
IGNORE_DIRS=(
  "etc"
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

# ==============================================================================
# Update and commit to GitHub
# ==============================================================================
# Check if a version was passed as a command-line argument.

read -p "Please enter the comment for the commit (default 'Updates for version $VERSION'): " comment_input

COMMENT="${comment_input:-Updates for version $VERSION}"

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

today=$(date -u +'%Y-%m-%d')
fileName="mingle.$VERSION-$today.zip"

zip -r $fileName docs/* examples/* include/* lib/* \
                 config.json glue.jar gum.jar stick.jar tape.jar \
                 catalog.json menu.sh run_mac.sh run_win.ps1 \
                 --exclude '*/.history/*'

cd $PRJ_ROOT
mv $PRJ_ROOT/todeploy/*.zip .

echo "✅ ZIP file created at: todeploy/$fileName"

# >>>>>>>>>>>>>>>>>>> EOF <<<<<<<<<<<<<<<<<<<<<<<