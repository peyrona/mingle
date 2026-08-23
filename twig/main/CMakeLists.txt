idf_component_register(
    SRCS "main.cpp"
    INCLUDE_DIRS "."
    REQUIRES etl naxe jsonview cil stick drivers pal esp_hw_support
)
