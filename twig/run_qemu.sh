#!/usr/bin/env bash
# Builds and runs this project under Espressif's QEMU (esp32s3), printing the serial
# console for a fixed duration then tearing QEMU down. Exists because `idf.py qemu
# monitor` requires an interactive TTY for idf_monitor.py, which isn't available in
# non-interactive shells — this talks to QEMU's serial socket directly instead.
#
# Usage: ./run_qemu.sh [seconds to capture, default 8]
set -euo pipefail

cd "$(dirname "${BASH_SOURCE[0]}")"

DURATION="${1:-8}"
QEMU_VERSION=esp_develop_9.0.0_20240606
QEMU_BIN_DIR="$HOME/.espressif/tools/qemu-xtensa/$QEMU_VERSION/qemu/bin"

if [ ! -x "$QEMU_BIN_DIR/qemu-system-xtensa" ]; then
    echo "qemu-system-xtensa not found at $QEMU_BIN_DIR" >&2
    echo "Install it with: python3 \$IDF_PATH/tools/idf_tools.py install qemu-xtensa" >&2
    exit 1
fi

# shellcheck disable=SC1090
source "$HOME/esp/esp-idf/export.sh" > /dev/null
export PATH="$QEMU_BIN_DIR:$PATH"

# MINGLE_DRIVER_DEFAULTS: an optional extra sdkconfig.defaults fragment (the per-model
# driver selection written by tools/build_model.sh). When set, it is merged after the
# project's own sdkconfig.defaults so its driver toggles win.
EXTRA_DEFAULTS="${MINGLE_DRIVER_DEFAULTS:-}"
if [ -n "$EXTRA_DEFAULTS" ]; then
    idf.py -DSDKCONFIG_DEFAULTS="sdkconfig.defaults;$EXTRA_DEFAULTS" build
else
    idf.py build
fi

# flash_image.bin / qemu_efuse.bin are normally generated as a side effect of `idf.py
# qemu`'s own action; regenerate them the same way it does, using the project's actual
# configured flash size rather than a stale/guessed value.
cd build

FLASH_SIZE=$(grep '^CONFIG_ESPTOOLPY_FLASHSIZE=' ../sdkconfig | cut -d'"' -f2)
python3 -m esptool --chip=esp32s3 merge_bin --output=flash_image.bin \
    --fill-flash-size="$FLASH_SIZE" @flash_args

python3 - <<'PYEOF'
import os
import sys
sys.path.insert(0, os.path.join(os.environ['IDF_PATH'], 'tools'))
from idf_py_actions import qemu_ext
with open("qemu_efuse.bin", "wb") as f:
    f.write(qemu_ext.QEMU_TARGETS['esp32s3'].default_efuse)
PYEOF

# Phase 1 has no networking, so no `-nic` flag yet (Phase 8 re-adds
# `-nic user,model=open_eth` together with CONFIG_ETH_USE_OPENETH=y).
# Phase 10: `-nic user,model=open_eth` emulates the OpenCores Ethernet MAC, which
# ESP-IDF's esp_eth has a driver for (CONFIG_ETH_USE_OPENETH=y) — enables real lwIP
# networking (SNTP) under QEMU.
"$QEMU_BIN_DIR/qemu-system-xtensa" -M esp32s3 \
    -drive file=flash_image.bin,if=mtd,format=raw \
    -drive file=qemu_efuse.bin,if=none,format=raw,id=efuse \
    -global driver=nvram.esp32s3.efuse,property=drive,value=efuse \
    -global driver=timer.esp32s3.timg,property=wdt_disable,value=true \
    -nic user,model=open_eth -nographic -serial tcp::5555,server \
    > qemu_stdout.log 2>&1 &
QEMU_PID=$!

sleep 2
timeout "$DURATION" nc localhost 5555 || true

kill "$QEMU_PID" 2>/dev/null || true
wait "$QEMU_PID" 2>/dev/null || true
