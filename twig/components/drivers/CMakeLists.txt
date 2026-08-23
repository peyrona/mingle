# drivers: concrete device drivers (clock sensor, console output). FreeRTOS-dependent
# (the clock driver owns a software timer).
#
# Each driver is gated by its CONFIG_DRIVER_* Kconfig symbol (see Kconfig) so the
# selective build (tools/build_model.sh) can compile in exactly the drivers a model
# references, per the plan's "fixed compile-time table, no reflection" decision.
set(driver_srcs "src/console_driver.cpp")

if(CONFIG_DRIVER_CLOCK)
    list(APPEND driver_srcs "src/clock_driver.cpp")
endif()

if(CONFIG_DRIVER_CELLSET)
    list(APPEND driver_srcs "src/cellset_driver.cpp")
endif()

if(CONFIG_DRIVER_CRON)
    list(APPEND driver_srcs "src/kronos_driver.cpp")
endif()

if(CONFIG_DRIVER_SYSMON)
    list(APPEND driver_srcs "src/sysmon_driver.cpp")
endif()

idf_component_register(
    SRCS ${driver_srcs}
    INCLUDE_DIRS "include"
    REQUIRES stick naxe
)
