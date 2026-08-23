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
// See pal/net.hpp's header comment: this ONE file is the implementation for both the host
// test build (real Linux sockets) and the real ESP-IDF/QEMU build (real lwIP sockets) --
// both expose the same POSIX/BSD sockets API, so no vendor split is needed here (unlike
// event_bus.hpp, which genuinely needs two different FreeRTOS backends).
#include "pal/net.hpp"

#include <cerrno>
#include <cstdio>
#include <cstring>

#include <fcntl.h>
#include <netdb.h>
#include <netinet/in.h>
#include <netinet/tcp.h>
#include <sys/socket.h>
#include <sys/types.h>
#include <unistd.h>

#include <chrono>

#ifndef MSG_NOSIGNAL
// Not every BSD-sockets implementation defines this flag (it's a Linux-ism); falling back
// to 0 just means "no special flag" -- SIGPIPE-on-write-to-closed-socket is a Linux/host
// concern this port's caller (control_channel.cpp) doesn't need to worry about on-device
// (lwIP sockets report a closed peer via a normal -1/ECONNRESET return, not a signal).
#define MSG_NOSIGNAL 0
#endif

namespace pal
{
    namespace
    {
        using Clock = std::chrono::steady_clock;

        /** Sets or clears O_NONBLOCK on a socket descriptor.
         *
         * @param fd The socket descriptor.
         * @param nonBlocking true to set non-blocking mode, false to clear it.
         * @return true on success, false if fcntl() fails.
         */
        bool setNonBlocking( int fd, bool nonBlocking )
        {
            int flags = fcntl( fd, F_GETFL, 0 );

            if( flags < 0 )
                return false;

            flags = nonBlocking ? (flags | O_NONBLOCK) : (flags & ~O_NONBLOCK);

            return fcntl( fd, F_SETFL, flags ) == 0;
        }

        /** Milliseconds remaining until deadline, clamped to >= 0.
         *
         * 0 means "already at or past the deadline," never negative (a negative timeval
         * is undefined behavior for select()/setsockopt()).
         *
         * @param deadline The absolute time to measure against.
         * @return Remaining milliseconds, or 0 if already past.
         */
        uint32_t msUntil( Clock::time_point deadline )
        {
            auto now = Clock::now();

            if( now >= deadline )
                return 0;

            return static_cast<uint32_t>(
                std::chrono::duration_cast<std::chrono::milliseconds>( deadline - now ).count() );
        }

        /** Converts a millisecond duration into a POSIX timeval.
         *
         * @param ms The duration in milliseconds.
         * @return The equivalent timeval.
         */
        timeval toTimeval( uint32_t ms )
        {
            timeval tv;
            tv.tv_sec = ms / 1000;
            tv.tv_usec = static_cast<long>( (ms % 1000) * 1000 );
            return tv;
        }
    }

    /** See pal/net.hpp for the contract; this is the definition. */
    NetHandle net_connect( const char * host, uint16_t port, uint32_t timeoutMs )
    {
        char portStr[ 6 ];
        std::snprintf( portStr, sizeof( portStr ), "%u", static_cast<unsigned>( port ) );

        addrinfo hints{};
        hints.ai_family = AF_UNSPEC;
        hints.ai_socktype = SOCK_STREAM;

        addrinfo * res = nullptr;

        if( getaddrinfo( host, portStr, &hints, &res ) != 0 || res == nullptr )
            return NetHandle{ -1 };

        NetHandle result{ -1 };

        for( addrinfo * ai = res; ai != nullptr; ai = ai->ai_next )
        {
            int fd = socket( ai->ai_family, ai->ai_socktype, ai->ai_protocol );

            if( fd < 0 )
                continue;

            if( ! setNonBlocking( fd, true ) )
            {
                ::close( fd );
                continue;
            }

            int rc = connect( fd, ai->ai_addr, ai->ai_addrlen );

            if( rc == 0 )
            {
                // Connected immediately (common on loopback) -- nothing to wait for.
            }
            else if( errno == EINPROGRESS )
            {
                // Deadline-based retry loop, not a single select() call: a
                // FreeRTOS-POSIX-simulator build's signal-driven tick (confirmed via a
                // real test failure, not guessed -- see control_channel_tests.cpp's
                // history) can interrupt select() with EINTR well before timeoutMs
                // actually elapses. Recomputing the remaining time on each retry keeps
                // the overall timeout honest instead of either giving up early or
                // silently restarting the full timeoutMs window on every signal.
                Clock::time_point deadline = Clock::now() + std::chrono::milliseconds( timeoutMs );
                int selRc;

                for( ;; )
                {
                    uint32_t remain = msUntil( deadline );

                    if( remain == 0 )
                    {
                        selRc = 0;   // timed out
                        break;
                    }

                    fd_set writeSet;
                    FD_ZERO( &writeSet );
                    FD_SET( fd, &writeSet );

                    timeval tv = toTimeval( remain );
                    selRc = select( fd + 1, nullptr, &writeSet, nullptr, &tv );

                    if( selRc >= 0 || errno != EINTR )
                        break;
                }

                if( selRc <= 0 )   // timeout (0) or select() itself failed (<0)
                {
                    ::close( fd );
                    continue;
                }

                int soErr = 0;
                socklen_t soErrLen = sizeof( soErr );

                if( getsockopt( fd, SOL_SOCKET, SO_ERROR, &soErr, &soErrLen ) != 0 || soErr != 0 )
                {
                    ::close( fd );
                    continue;
                }
            }
            else
            {
                ::close( fd );
                continue;
            }

            // Connected. Back to blocking mode -- net_send() loops on short writes itself,
            // and net_recv() uses SO_RCVTIMEO rather than a nonblocking poll loop.
            setNonBlocking( fd, false );

            int one = 1;
            setsockopt( fd, IPPROTO_TCP, TCP_NODELAY, &one, sizeof( one ) );
            setsockopt( fd, SOL_SOCKET, SO_KEEPALIVE, &one, sizeof( one ) );

            result.fd = fd;
            break;
        }

        freeaddrinfo( res );

        return result;
    }

    /** See pal/net.hpp for the contract; this is the definition. */
    bool net_send( NetHandle h, const void * buf, size_t len )
    {
        if( ! net_valid( h ) )
            return false;

        const uint8_t * p = static_cast<const uint8_t *>( buf );
        size_t remaining = len;

        while( remaining > 0 )
        {
            ssize_t n = send( h.fd, p, remaining, MSG_NOSIGNAL );

            if( n < 0 && errno == EINTR )
                continue;   // interrupted before any bytes went out -- just retry this send()

            if( n <= 0 )    // 0 is not a valid TCP send() return for remaining > 0; treat like an error
                return false;

            p += n;
            remaining -= static_cast<size_t>( n );
        }

        return true;
    }

    /** See pal/net.hpp for the contract; this is the definition. */
    int32_t net_recv( NetHandle h, void * buf, size_t cap, uint32_t timeoutMs )
    {
        if( ! net_valid( h ) )
            return kNetError;

        // Deadline-based retry loop -- see net_connect()'s identical rationale on EINTR
        // above (the same signal-driven tick interrupts recv() too, confirmed by the
        // same real test failure). Each retry re-sets SO_RCVTIMEO to the REMAINING
        // budget, not the original timeoutMs, so the overall wait this function honors
        // is still bounded close to what the caller asked for.
        Clock::time_point deadline = Clock::now() + std::chrono::milliseconds( timeoutMs );

        for( ;; )
        {
            uint32_t remain = msUntil( deadline );

            if( remain == 0 )
                return kNetTimeout;

            timeval tv = toTimeval( remain );

            if( setsockopt( h.fd, SOL_SOCKET, SO_RCVTIMEO, &tv, sizeof( tv ) ) != 0 )
                return kNetError;

            ssize_t n = recv( h.fd, buf, cap, 0 );

            if( n > 0 )
                return static_cast<int32_t>( n );

            if( n == 0 )
                return kNetClosed;

            if( errno == EINTR )
                continue;

            if( errno == EAGAIN || errno == EWOULDBLOCK )
                return kNetTimeout;

            return kNetError;
        }
    }

    /** See pal/net.hpp for the contract; this is the definition. */
    void net_close( NetHandle h )
    {
        if( net_valid( h ) )
            ::close( h.fd );
    }

    /** See pal/net.hpp for the contract; this is the definition. */
    NetHandle net_udp_open()
    {
        int fd = socket( AF_INET, SOCK_DGRAM, 0 );

        if( fd < 0 )
            return NetHandle{ -1 };

        return NetHandle{ fd };
    }

    /** See pal/net.hpp for the contract; this is the definition. */
    bool net_udp_send_to( NetHandle h, const char * host, uint16_t port, const void * buf, size_t len )
    {
        if( ! net_valid( h ) )
            return false;

        char portStr[ 6 ];
        std::snprintf( portStr, sizeof( portStr ), "%u", static_cast<unsigned>( port ) );

        addrinfo hints{};
        hints.ai_family = AF_UNSPEC;
        hints.ai_socktype = SOCK_DGRAM;

        addrinfo * res = nullptr;

        if( getaddrinfo( host, portStr, &hints, &res ) != 0 || res == nullptr )
            return false;

        bool ok = false;

        for( addrinfo * ai = res; ai != nullptr; ai = ai->ai_next )
        {
            ssize_t n = sendto( h.fd, buf, len, MSG_NOSIGNAL, ai->ai_addr, ai->ai_addrlen );

            if( n == static_cast<ssize_t>( len ) )
            {
                ok = true;
                break;
            }
        }

        freeaddrinfo( res );

        return ok;
    }

    /** See pal/net.hpp for the contract; this is the definition. */
    int32_t net_udp_recv( NetHandle h, void * buf, size_t cap, uint32_t timeoutMs )
    {
        if( ! net_valid( h ) )
            return kNetError;

        // Same deadline-based EINTR retry as net_recv() -- see its comment above for why
        // (the FreeRTOS-POSIX simulator's signal-driven tick interrupts blocking calls
        // in completely ordinary operation).
        Clock::time_point deadline = Clock::now() + std::chrono::milliseconds( timeoutMs );

        for( ;; )
        {
            uint32_t remain = msUntil( deadline );

            if( remain == 0 )
                return kNetTimeout;

            timeval tv = toTimeval( remain );

            if( setsockopt( h.fd, SOL_SOCKET, SO_RCVTIMEO, &tv, sizeof( tv ) ) != 0 )
                return kNetError;

            ssize_t n = recvfrom( h.fd, buf, cap, 0, nullptr, nullptr );

            if( n > 0 )
                return static_cast<int32_t>( n );

            if( n == 0 )
                continue;   // a zero-length UDP datagram is legal but useless here -- keep waiting

            if( errno == EINTR )
                continue;

            if( errno == EAGAIN || errno == EWOULDBLOCK )
                return kNetTimeout;

            return kNetError;
        }
    }
}
