# cil: model structs + builders (Tier 1, hardware-agnostic). Depends on jsonview and
# naxe. test/cil_tests.cpp is gated by CONFIG_TWIG_ONDEVICE_SELFTEST (see
# main/Kconfig.projbuild and naxe/CMakeLists.txt's own note on why).
set(cil_srcs "src/builder.cpp")

if(CONFIG_TWIG_ONDEVICE_SELFTEST)
    list(APPEND cil_srcs "test/cil_tests.cpp")
endif()

idf_component_register(
    SRCS ${cil_srcs}
    INCLUDE_DIRS "include"
    REQUIRES jsonview naxe
)
