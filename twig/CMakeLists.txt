cmake_minimum_required(VERSION 3.16)
include($ENV{IDF_PATH}/tools/cmake/project.cmake)
project(twig_oop)

# Pin the C++ standard to C++20. ESP-IDF v5.3 defaults chip targets to gnu++2b
# (C++23); the project plan targets C++20 so host (g++ 11) and target (g++ 13)
# stay on one standard. GCC honours the last -std= flag, and this APPEND lands
# after the toolchain default, so gnu++20 wins.
idf_build_set_property(CXX_COMPILE_OPTIONS "-std=gnu++20" APPEND)
