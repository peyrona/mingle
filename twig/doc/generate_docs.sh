#!/usr/bin/env bash
#
# Generates the Twig API documentation (HTML) with Doxygen + the
# doxygen-awesome-css theme (light/dark toggle).
#
# Output goes to twig/doc/api-docs/ (created if missing, wiped and
# regenerated on every run, since it is a build artifact).

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
DOXYGEN_DIR="$SCRIPT_DIR/doxigen"
OUTPUT_DIR="$SCRIPT_DIR/api-docs"

command -v doxygen >/dev/null 2>&1 || { echo "error: doxygen is not installed or not on PATH" >&2; exit 1; }

mkdir -p "$OUTPUT_DIR"

cd "$DOXYGEN_DIR"
doxygen Doxyfile

echo "Documentation generated in: $OUTPUT_DIR/html/index.html"
