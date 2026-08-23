#!/usr/bin/env bash
# Builds the compatibility-checker CLI if it is missing or stale.
#
# The checker derives the deferred-feature surface live from naxe's dispatch tables, so a
# change to any naxe/checker/registry source must trigger a rebuild — a timestamp check
# against those sources, not just "binary exists".
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT_DIR="$(cd "$SCRIPT_DIR/../.." && pwd)"

CHECKER="$SCRIPT_DIR/build/checker"

if [ -x "$CHECKER" ] && ! find \
        "$SCRIPT_DIR/include" "$SCRIPT_DIR/src" \
        "$ROOT_DIR/components/naxe/include" "$ROOT_DIR/components/naxe/src" \
        "$ROOT_DIR/components/drivers/include" \
        -type f \( -name '*.cpp' -o -name '*.hpp' \) -newer "$CHECKER" | grep -q .; then
    exit 0
fi

echo "building compatibility checker..." >&2
"$SCRIPT_DIR/build_cli.sh"
