/*
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
// pal::net -- the platform-abstraction-layer networking interface, per PLAN.md's
// "Portability: platform abstraction layer" section and IMPLEMENTATION.md's Phase 8.
//
// CLIENT ONLY, by design -- no listen()/accept() anywhere in this interface. PLAN.md's
// NAT reasoning is explicit: this device always dials OUT to a known host:port, never
// listens, so there is no server-side surface for the PAL to abstract at all. Do not add
// one "for completeness" -- a listening socket on this device is a design regression, not
// a missing feature.
//
// This header is the boundary IMPLEMENTATION.md's Phase 8 notes call for: core components
// (stick/) depend only on this, never on lwIP/BSD-socket headers directly, so a future
// non-ESP-IDF FreeRTOS vendor needs only a new pal_<vendor>/ implementing these four
// functions, not any change to control-channel logic.
//
// Implementation note (components/pal/src/net_posix.cpp): ESP-IDF's lwIP exposes a real
// POSIX/BSD sockets API (<sys/socket.h>, <netdb.h>, ...) under both the real xtensa target
// and QEMU's openeth-backed netif -- see IMPLEMENTATION.md's Phase 8 QEMU-networking note.
// That means, unlike event_bus.hpp (which wraps FreeRTOS primitives that need a vendored
// POSIX simulator to run on host), this component's SINGLE implementation file compiles
// and runs correctly on both the host test build (real Linux sockets) and the real
// xtensa-esp32s3-elf-g++ / QEMU build (real lwIP sockets) with no #ifdef split and no
// second backend -- same production code, two targets, matching this port's established
// testing philosophy without needing a mock.
#pragma once

#include <cstddef>
#include <cstdint>

namespace pal
{
    /** Opaque connected-socket handle.
     *
     * fd < 0 means invalid/not-connected -- callers use net_valid(), never inspect fd
     * directly (its meaning is backend-specific).
     */
    struct NetHandle
    {
        int fd = -1;
    };

    /** Whether a NetHandle refers to an open, connected socket.
     *
     * @param h The handle to test.
     * @return true if the handle is valid, false otherwise.
     */
    inline bool net_valid( NetHandle h )
    {
        return h.fd >= 0;
    }

    /** Dials host:port over TCP, blocking up to timeoutMs for the connect().
     *
     * Returns an invalid handle (net_valid() == false) on any failure (DNS/connect
     * refused/timeout) -- the caller (control_channel.cpp's reconnect-with-backoff loop)
     * is responsible for retrying, this function does not retry internally (unlike Java's
     * SocketClient.connect(), which retries 3x with a 1s delay itself -- that retry policy
     * is a control-channel-level concern here, not baked into the PAL, so it composes with
     * this port's own backoff schedule instead of stacking two different ones).
     *
     * Sets TCP_NODELAY and SO_KEEPALIVE on the resulting socket, matching
     * SocketClient.java's connect() shape exactly (see IMPLEMENTATION.md's Phase 8
     * research note) -- these are real protocol-shape decisions carried over from Java,
     * not arbitrary.
     *
     * @param host The remote host name or IP address.
     * @param port The remote TCP port.
     * @param timeoutMs Max time to block waiting for the connection.
     * @return A valid handle on success, an invalid handle on failure.
     */
    NetHandle net_connect( const char * host, uint16_t port, uint32_t timeoutMs );

    /** Sends every byte of buf, looping internally over short writes.
     *
     * Returns false on any error or on the peer having closed the connection -- callers
     * should treat false as "this handle is dead, close it and reconnect", not as a
     * partial-send condition to retry piecemeal.
     *
     * @param h The connected handle.
     * @param buf The bytes to send.
     * @param len Number of bytes in buf.
     * @return true if all bytes were sent, false on error/closed peer.
     */
    bool net_send( NetHandle h, const void * buf, size_t len );

    /** Reads up to cap bytes into buf, waiting up to timeoutMs for at least one byte.
     *
     * Design departure from Java, and why: SocketClient.java's reader thread deliberately
     * never sets a read timeout ("a client can be days without requesting inf. from
     * server") and just blocks forever on readLine(). This PAL's net_recv always takes an
     * explicit timeout instead, because this port's receive loop (control_channel.cpp,
     * Phase 8 item 3/4) needs to wake up periodically even with no inbound traffic, to
     * send heartbeats and to notice a dead connection without waiting for the OS's TCP
     * keepalive probes to time out (which can take minutes by default). A blocking-forever
     * read would defeat both.
     *
     * @param h The connected handle.
     * @param buf Destination buffer.
     * @param cap Capacity of buf in bytes.
     * @param timeoutMs Max time to block for incoming data.
     * @return > 0 = bytes read; 0 = peer closed the connection cleanly (matches
     *         BufferedReader.readLine() returning null on EOF); -1 = socket error (treat
     *         like a clean close: the handle is dead); -2 = timeout expired with no data
     *         (NOT an error -- the caller's loop should just check its heartbeat/backoff
     *         timers and call again).
     */
    int32_t net_recv( NetHandle h, void * buf, size_t cap, uint32_t timeoutMs );

    /** Sentinel for a clean peer close returned by net_recv(). */
    constexpr int32_t kNetClosed = 0;
    /** Sentinel for a socket error returned by net_recv(). */
    constexpr int32_t kNetError = -1;
    /** Sentinel for a read timeout returned by net_recv(). */
    constexpr int32_t kNetTimeout = -2;

    /** Closes the socket. Safe to call on an already-invalid handle (no-op).
     *
     * Works for BOTH TCP handles (net_connect()) and UDP handles (net_udp_open()) --
     * closing a socket fd is the same syscall either way, so this one function serves
     * both rather than adding a redundant net_udp_close().
     *
     * @param h The handle to close.
     */
    void net_close( NetHandle h );

    // -- UDP, added Phase 9 for the hand-rolled SNTP client (time_sync.hpp) -----------
    //
    // Deliberately minimal: one unconnected datagram socket, explicit destination per
    // send (send-to, not connect-then-send), explicit timeout per recv -- exactly what
    // an RFC 4330 request/response exchange needs and nothing more. NOT a general
    // "UDP client" abstraction; if a second UDP-based need ever shows up, revisit
    // whether this shape still fits rather than assuming it does.

    /** Opens an unconnected UDP (datagram) socket, bound to an OS-assigned local port.
     *
     * @return A valid handle on success, an invalid handle on failure. Close with
     *         net_close() like any other handle.
     */
    NetHandle net_udp_open();

    /** Sends one UDP datagram to host:port.
     *
     * Unlike net_send() (TCP, loops over short writes), a UDP send is always
     * all-or-nothing at the syscall level -- no short-write case exists to loop over.
     *
     * @param h A handle from net_udp_open().
     * @param host Destination host name or IP address.
     * @param port Destination UDP port.
     * @param buf The datagram payload.
     * @param len Number of bytes in buf.
     * @return true if the datagram was handed to the OS successfully.
     */
    bool net_udp_send_to( NetHandle h, const char * host, uint16_t port, const void * buf, size_t len );

    /** Receives up to cap bytes of the next datagram, waiting up to timeoutMs.
     *
     * Same return-value convention as net_recv() (>0 bytes, kNetClosed/kNetError/
     * kNetTimeout) except kNetClosed cannot occur for UDP (no peer to close a
     * connection) -- included only for a uniform return type, callers should not expect
     * to see it from this function.
     *
     * @param h A handle from net_udp_open().
     * @param buf Destination buffer.
     * @param cap Capacity of buf in bytes.
     * @param timeoutMs Max time to block for an incoming datagram.
     * @return > 0 = bytes read; kNetError = socket error; kNetTimeout = no datagram
     *         arrived in time.
     */
    int32_t net_udp_recv( NetHandle h, void * buf, size_t cap, uint32_t timeoutMs );
}
