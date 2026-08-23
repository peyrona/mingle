#!/usr/bin/env bash
# Host build for the compatibility-checker CLI (tools/compat_checker/src/checker_main.cpp).
# Plain g++, no cmake, no FreeRTOS/ESP-IDF. Flags mirror the on-target build: C++20,
# -fno-exceptions, -fno-rtti. Produces build/checker, used by tools/check_model.sh and
# tools/build_model.sh.
set -euo pipefail

cd "$(dirname "${BASH_SOURCE[0]}")"

ROOT=../../components
INCLUDES=(-Iinclude -I"$ROOT/naxe/include" -I"$ROOT/jsonview/include"
          -I"$ROOT/jsonview/third_party/jsmn" -I"$ROOT/cil/include"
          -I"$ROOT/drivers/include" -I"$ROOT/etl/include")

FLAGS=(-O2 -Wall -Wextra -std=c++20 -fno-exceptions -fno-rtti "${INCLUDES[@]}")

mkdir -p build

# naxe (full — the evaluator/parser/function table are what the checker walks).
for src in "$ROOT/naxe/src"/value.cpp "$ROOT/naxe/src"/list.cpp "$ROOT/naxe/src"/pair.cpp \
           "$ROOT/naxe/src"/util.cpp "$ROOT/naxe/src"/memory.cpp "$ROOT/naxe/src"/ast.cpp \
           "$ROOT/naxe/src"/operators.cpp "$ROOT/naxe/src"/functions.cpp "$ROOT/naxe/src"/datetime.cpp \
           "$ROOT/naxe/src"/parser.cpp; do
    g++ "${FLAGS[@]}" -c "$src" -o "build/naxe_$(basename "${src%.cpp}").o"
done

g++ "${FLAGS[@]}" -c "$ROOT/jsonview/src/json_view.cpp" -o build/json_view.o
g++ "${FLAGS[@]}" -c "$ROOT/jsonview/src/jsmn_impl.cpp" -o build/jsmn_impl.o
g++ "${FLAGS[@]}" -c "$ROOT/cil/src/builder.cpp" -o build/builder.o
g++ "${FLAGS[@]}" -c src/checker.cpp -o build/checker.o
g++ "${FLAGS[@]}" -c src/checker_main.cpp -o build/checker_main.o

g++ build/naxe_*.o build/json_view.o build/jsmn_impl.o build/builder.o \
    build/checker.o build/checker_main.o -o build/checker
