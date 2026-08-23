# naxe: value model + evaluator + parser (Tier 1, hardware-agnostic — no FreeRTOS/ESP-IDF
# calls in src/). Depends on the header-only etl component for the ParseError string.
# Registers the test-suite bodies here so main.cpp can run them on-device under QEMU
# (gated by CONFIG_TWIG_ONDEVICE_SELFTEST, see main/Kconfig.projbuild -- each test file's
# own static buffers cost real BSS, so a deployment image can opt out); the host build
# compiles them separately via test/build_and_run.sh regardless of this option.
set(naxe_srcs
    "src/value.cpp" "src/list.cpp" "src/pair.cpp" "src/util.cpp" "src/memory.cpp"
    "src/ast.cpp" "src/operators.cpp" "src/functions.cpp" "src/datetime.cpp"
    "src/parser.cpp" "src/cron.cpp"
)

if(CONFIG_TWIG_ONDEVICE_SELFTEST)
    list(APPEND naxe_srcs
        "test/value_model_tests.cpp" "test/eval_tests.cpp" "test/functions_tests.cpp"
        "test/parser_tests.cpp" "test/cron_tests.cpp"
    )
endif()

idf_component_register(
    SRCS ${naxe_srcs}
    INCLUDE_DIRS "include"
    REQUIRES etl
)
