# stick: the runtime. The managers/rules/scripts are Tier 1 (hardware-agnostic);
# EventBus + Orchestrator are FreeRTOS-dependent (event_bus.hpp includes freertos/*).
# freertos is a common ESP-IDF component available without an explicit dependency edge,
# but declared here for clarity.
#
# test/*.cpp is gated by CONFIG_TWIG_ONDEVICE_SELFTEST (see main/Kconfig.projbuild and
# naxe/CMakeLists.txt's own note on why): stick_tests/temporal_tests/runtime_tests each
# own a full RuleManager/DeviceManager-sized static footprint of their own.
set(stick_srcs
    "src/message.cpp" "src/device.cpp" "src/device_manager.cpp"
    "src/driver_manager.cpp" "src/action.cpp" "src/rule.cpp"
    "src/rule_manager.cpp" "src/script.cpp" "src/script_manager.cpp"
    "src/temporal.cpp" "src/event_bus.cpp" "src/orchestrator.cpp"
    "src/logger.cpp" "src/exen_comm.cpp" "src/control_channel.cpp"
)

if(CONFIG_TWIG_ONDEVICE_SELFTEST)
    list(APPEND stick_srcs
        "test/stick_tests.cpp" "test/temporal_tests.cpp" "test/runtime_tests.cpp"
    )
endif()

idf_component_register(
    SRCS ${stick_srcs}
    INCLUDE_DIRS "include"
    REQUIRES cil naxe freertos pal
)
