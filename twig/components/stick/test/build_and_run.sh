#!/usr/bin/env bash
# Tier 1 host test build for components/stick (depends on cil + naxe + jsonview). Plain
# g++, no cmake, no FreeRTOS/ESP-IDF. Flags mirror the on-target build: C++20,
# -fno-exceptions, -fno-rtti.
set -euo pipefail

cd "$(dirname "${BASH_SOURCE[0]}")"

FLAGS=(-O2 -Wall -Wextra -std=c++20 -fno-exceptions -fno-rtti
       -I../include -I../../cil/include -I../../naxe/include -I../../jsonview/include
       -I../../jsonview/third_party/jsmn -I../../etl/include)

mkdir -p build

# naxe (full — the evaluator/parser are used by Rule/Script compilation).
for src in ../../naxe/src/value.cpp ../../naxe/src/list.cpp ../../naxe/src/pair.cpp \
           ../../naxe/src/util.cpp ../../naxe/src/memory.cpp ../../naxe/src/ast.cpp \
           ../../naxe/src/operators.cpp ../../naxe/src/functions.cpp ../../naxe/src/datetime.cpp \
           ../../naxe/src/parser.cpp; do
    g++ "${FLAGS[@]}" -c "$src" -o "build/naxe_$(basename "${src%.cpp}").o"
done

g++ "${FLAGS[@]}" -c ../../jsonview/src/json_view.cpp -o build/json_view.o
g++ "${FLAGS[@]}" -c ../../jsonview/src/jsmn_impl.cpp -o build/jsmn_impl.o
g++ "${FLAGS[@]}" -c ../../cil/src/builder.cpp -o build/builder.o

for src in ../src/message.cpp ../src/device.cpp ../src/device_manager.cpp \
           ../src/driver_manager.cpp ../src/action.cpp ../src/rule.cpp \
           ../src/rule_manager.cpp ../src/script.cpp ../src/script_manager.cpp \
           ../src/temporal.cpp ../src/logger.cpp; do
    g++ "${FLAGS[@]}" -c "$src" -o "build/$(basename "${src%.cpp}").o"
done

g++ "${FLAGS[@]}" -c stick_tests.cpp -o build/stick_tests.o
g++ "${FLAGS[@]}" -c temporal_tests.cpp -o build/temporal_tests.o
g++ "${FLAGS[@]}" -c host_main.cpp -o build/host_main.o

g++ build/naxe_*.o build/json_view.o build/jsmn_impl.o build/builder.o \
    build/message.o build/device.o build/device_manager.o build/driver_manager.o \
    build/action.o build/rule.o build/rule_manager.o build/script.o build/script_manager.o \
    build/temporal.o build/logger.o \
    build/stick_tests.o build/temporal_tests.o build/host_main.o -o build/test_stick

./build/test_stick
