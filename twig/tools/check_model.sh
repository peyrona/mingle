#!/usr/bin/env bash
# Checks whether a transpiled `.model` is compatible with twig-oop's feature and driver
# surface, by running the host-side compatibility checker (tools/compat_checker).
#
# Usage: ./check_model.sh <model-file.model>
#
# Exit status: 0 = compatible; 1 = incompatibilities (listed on stdout); 2 = usage/IO error.
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

MODEL="${1:?Usage: $0 <model-file.model>}"

if [ ! -f "$MODEL" ]; then
    echo "error: model file not found: $MODEL" >&2
    exit 2
fi

CHECKER="$SCRIPT_DIR/compat_checker/build/checker"
"$SCRIPT_DIR/compat_checker/ensure_built.sh"

exec "$CHECKER" "$MODEL"
