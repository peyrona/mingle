# pal: platform abstraction layer (ESP-IDF backend). Time sync over QEMU's emulated
# Ethernet + SNTP. net_posix.cpp is the TCP/UDP client PAL for stick's control channel:
# it uses only standard BSD-sockets calls, which both the host test build (g++ against
# the host's own libc sockets) and the real ESP-IDF/QEMU build (lwIP's BSD-sockets
# compat layer, via REQUIRES lwip) satisfy with the same single source file -- see
# net_posix.cpp's header comment. FreeRTOS/ESP-IDF-dependent.
idf_component_register(
    SRCS "src/time_sync.cpp" "src/net_posix.cpp"
    INCLUDE_DIRS "include"
    REQUIRES naxe esp_netif esp_eth lwip
)
