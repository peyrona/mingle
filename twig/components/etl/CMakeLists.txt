# Header-only component: the vendored ETL (Embedded Template Library).
#
# Exposes `include/` (both `etl/` and the project-wide `etl_profile.h`) to any
# component that REQUIRES etl. No sources to compile, no FreeRTOS/ESP-IDF dependency.
idf_component_register(
    INCLUDE_DIRS "include"
)
