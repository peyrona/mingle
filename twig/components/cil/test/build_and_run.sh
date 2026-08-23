#!/usr/bin/env bash
# Tier 1 host test build for components/cil (which depends on jsonview + naxe). Plain
# g++, no cmake, no FreeRTOS/ESP-IDF. Flags mirror the on-target build: C++20,
# -fno-exceptions, -fno-rtti.
set -euo pipefail

cd "$(dirname "${BASH_SOURCE[0]}")"

FLAGS=(-O2 -Wall -Wextra -std=c++20 -fno-exceptions -fno-rtti
       -I../include -I../../naxe/include -I../../jsonview/include -I../../jsonview/third_party/jsmn
       -I../../etl/include)

mkdir -p build

# naxe subset cil depends on (value model only — not the evaluator/parser).
for src in ../../naxe/src/value.cpp ../../naxe/src/list.cpp ../../naxe/src/pair.cpp \
           ../../naxe/src/util.cpp ../../naxe/src/memory.cpp; do
    g++ "${FLAGS[@]}" -c "$src" -o "build/naxe_$(basename "${src%.cpp}").o"
done

g++ "${FLAGS[@]}" -c ../../jsonview/src/json_view.cpp -o build/json_view.o
g++ "${FLAGS[@]}" -c ../../jsonview/src/jsmn_impl.cpp -o build/jsmn_impl.o
g++ "${FLAGS[@]}" -c ../src/builder.cpp -o build/builder.o
g++ "${FLAGS[@]}" -c cil_tests.cpp -o build/cil_tests.o
g++ "${FLAGS[@]}" -c host_main.cpp -o build/host_main.o

g++ build/naxe_value.o build/naxe_list.o build/naxe_pair.o build/naxe_util.o build/naxe_memory.o \
    build/json_view.o build/jsmn_impl.o build/builder.o \
    build/cil_tests.o build/host_main.o -o build/test_cil

./build/test_cil
