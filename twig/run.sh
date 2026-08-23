#!/usr/bin/env bash
# Single entry point: run the Tier-1 host test suites, then execute a transpiled `.model`
# on-device under QEMU (compatibility-check -> select drivers -> embed -> build -> boot).
#
# Joins the two previously separate flows:
#   - the host unit-test loops (components/*/test/build_and_run.sh,
#     tools/compat_checker/test/build_and_run.sh)
#   - the model execution (tools/run_model.sh -> build_model.sh --qemu)
#
# Usage: ./run.sh <model-file.model> [--seconds N] [--skip-check]
#   --seconds N    QEMU serial-capture window in seconds (default 40).
#   --skip-check   skip the compatibility gate and driver selection.
#
# If any host test suite fails, the script aborts before touching the model.
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

MODEL="${1:?Usage: $0 <model-file.model> [--seconds N] [--skip-check]}"
shift

echo "=== Tier 1 host tests ==="
"$ROOT_DIR/components/naxe/test/build_and_run.sh"
"$ROOT_DIR/components/cil/test/build_and_run.sh"
"$ROOT_DIR/components/stick/test/build_and_run.sh"
"$ROOT_DIR/tools/compat_checker/test/build_and_run.sh"

echo ""
echo "=== Model execution (QEMU) ==="
exec "$ROOT_DIR/tools/run_model.sh" "$MODEL" "$@"
