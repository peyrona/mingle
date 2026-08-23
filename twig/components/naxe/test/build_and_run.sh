#!/usr/bin/env bash
# Tier 1 host test build for components/naxe (see the project plan's Build & test
# strategy). Plain g++, no cmake, no FreeRTOS/ESP-IDF — the fast, no-flashing iteration
# loop. Flags mirror the on-target build: C++20, -fno-exceptions, -fno-rtti.
set -euo pipefail

cd "$(dirname "${BASH_SOURCE[0]}")"

INCLUDE_DIR=../include
ETL_INCLUDE=../../etl/include
FLAGS=(-O2 -Wall -Wextra -std=c++20 -fno-exceptions -fno-rtti -I"$INCLUDE_DIR" -I"$ETL_INCLUDE")

mkdir -p build

for src in ../src/value.cpp ../src/list.cpp ../src/pair.cpp ../src/util.cpp ../src/memory.cpp \
           ../src/ast.cpp ../src/operators.cpp ../src/functions.cpp ../src/datetime.cpp \
           ../src/parser.cpp ../src/cron.cpp; do
    g++ "${FLAGS[@]}" -c "$src" -o "build/$(basename "${src%.cpp}").o"
done

g++ "${FLAGS[@]}" -c value_model_tests.cpp -o build/value_model_tests.o
g++ "${FLAGS[@]}" -c eval_tests.cpp -o build/eval_tests.o
g++ "${FLAGS[@]}" -c functions_tests.cpp -o build/functions_tests.o
g++ "${FLAGS[@]}" -c parser_tests.cpp -o build/parser_tests.o
g++ "${FLAGS[@]}" -c cron_tests.cpp -o build/cron_tests.o
g++ "${FLAGS[@]}" -c host_main.cpp -o build/host_main.o

g++ build/value.o build/list.o build/pair.o build/util.o build/memory.o \
    build/ast.o build/operators.o build/functions.o build/datetime.o build/parser.o \
    build/cron.o \
    build/value_model_tests.o build/eval_tests.o build/functions_tests.o build/parser_tests.o \
    build/cron_tests.o \
    build/host_main.o -o build/test_value

./build/test_value
