# jsonview: read-only JSON view over vendored jsmn (Tier 1, hardware-agnostic — no
# FreeRTOS/ESP-IDF calls).
idf_component_register(
    SRCS "src/json_view.cpp" "src/jsmn_impl.cpp"
    INCLUDE_DIRS "include" "third_party/jsmn"
)
