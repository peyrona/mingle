#!/usr/bin/env bash
# Runs a transpiled Une `.model` file on the C++ stick runtime (ESP32-S3, under QEMU).
#
# This is the quick dev loop: compatibility-check -> select drivers -> embed the model ->
# build -> boot under QEMU. It delegates to tools/build_model.sh --qemu; use that script
# directly for a build without the QEMU boot, or tools/check_model.sh for the check alone.
#
# Usage: ./run_model.sh <path/to/model.model> [--seconds N] [--skip-check]
#
# Example:
#   tools/run_model.sh /home/francisco/proyectos/mingle/todeploy/examples/01.clock.model
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

MODEL="${1:?Usage: $0 <model-file.model> [--seconds N] [--skip-check]}"
shift

exec "$SCRIPT_DIR/build_model.sh" "$MODEL" --qemu "$@"
