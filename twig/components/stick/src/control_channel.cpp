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
/** \file control_channel.cpp
 * Outbound control-channel TCP client (see control_channel.hpp).
 */
#include "stick/control_channel.hpp"

#include "stick/message.hpp"

namespace stick
{
    ControlChannel::ControlChannel( EventBus & bus, const char * host, uint16_t port,
                                     UBaseType_t taskPriority, uint32_t taskStackWords )
        : bus_( &bus )
        , host_( host )
        , port_( port )
        , taskPriority_( taskPriority )
        , taskStackWords_( taskStackWords )
        , handle_{ -1 }
        , task_( nullptr )
        , lineLen_( 0 )
        , lineOverflow_( false )
        , backoffMs_( kInitialBackoffMs )
        , lastSendMs_( 0 )
    {
        lineAckSem_ = xSemaphoreCreateBinary();
        configASSERT( lineAckSem_ != nullptr );

        handleMutex_ = xSemaphoreCreateMutex();
        configASSERT( handleMutex_ != nullptr );
    }

    ControlChannel::~ControlChannel()
    {
        if( task_ != nullptr )
        {
            vTaskDelete( task_ );
        }

        if( pal::net_valid( handle_ ) )
        {
            pal::net_close( handle_ );
        }

        vSemaphoreDelete( lineAckSem_ );
        vSemaphoreDelete( handleMutex_ );
    }

    void ControlChannel::start()
    {
        const BaseType_t ok = xTaskCreate( &ControlChannel::taskTrampoline, "CtrlChan",
                                            taskStackWords_, this, taskPriority_, &task_ );
        configASSERT( ok == pdPASS );
    }

    void ControlChannel::ackLineProcessed()
    {
        xSemaphoreGive( lineAckSem_ );
    }

    bool ControlChannel::sendLine( std::string_view body )
    {
        return sendWithNewline( body );
    }

    bool ControlChannel::isConnected() const
    {
        xSemaphoreTake( handleMutex_, portMAX_DELAY );
        const bool valid = pal::net_valid( handle_ );
        xSemaphoreGive( handleMutex_ );
        return valid;
    }

    void ControlChannel::taskTrampoline( void * self )
    {
        static_cast<ControlChannel *>( self )->taskLoop();
    }

    void ControlChannel::taskLoop()
    {
        char chunk[ 256 ];

        for( ;; )
        {
            // Snapshot handle_ under the mutex -- sendWithNewline() can close/reassign it
            // from another task (see class header note) -- then use the LOCAL copy for the
            // rest of this iteration, including the blocking recv() below, which must NOT
            // itself hold the mutex.
            xSemaphoreTake( handleMutex_, portMAX_DELAY );
            const pal::NetHandle h = handle_;
            xSemaphoreGive( handleMutex_ );

            if( ! pal::net_valid( h ) )
            {
                tryConnect();
                continue;
            }

            const int32_t n = pal::net_recv( h, chunk, sizeof( chunk ), kRecvTimeoutMs );

            if( n == pal::kNetClosed || n == pal::kNetError )
            {
                xSemaphoreTake( handleMutex_, portMAX_DELAY );
                pal::net_close( handle_ );
                handle_ = pal::NetHandle{ -1 };
                xSemaphoreGive( handleMutex_ );
                continue;
            }

            if( n == pal::kNetTimeout )
            {
                maybeSendHeartbeat();
                continue;
            }

            feedBytes( std::string_view( chunk, static_cast<size_t>( n ) ) );
            maybeSendHeartbeat();
        }
    }

    bool ControlChannel::tryConnect()
    {
        const pal::NetHandle newHandle = pal::net_connect( host_, port_, kConnectTimeoutMs );

        xSemaphoreTake( handleMutex_, portMAX_DELAY );
        handle_ = newHandle;
        xSemaphoreGive( handleMutex_ );

        if( pal::net_valid( handle_ ) )
        {
            backoffMs_ = kInitialBackoffMs;
            lineLen_ = 0;
            lineOverflow_ = false;
            lastSendMs_ = nowEpochMillis();
            return true;
        }

        vTaskDelay( pdMS_TO_TICKS( backoffMs_ ) );
        backoffMs_ = ( backoffMs_ > kMaxBackoffMs / 2 ) ? kMaxBackoffMs : backoffMs_ * 2;
        return false;
    }

    void ControlChannel::feedBytes( std::string_view data )
    {
        for( size_t i = 0; i < data.size(); ++i )
        {
            const char c = data[ i ];

            if( c == '\r' )
            {
                continue;   // tolerate CRLF -- matches BufferedReader.readLine()'s own handling of either line ending
            }

            if( c != '\n' )
            {
                if( lineLen_ < kMaxLineLen )
                {
                    lineBuf_[ lineLen_++ ] = c;
                }
                else
                {
                    lineOverflow_ = true;   // keep consuming silently until the terminating '\n'
                }

                continue;
            }

            if( lineOverflow_ )
            {
                logger().log( LogLevel::Warning, "control channel: line exceeded %u bytes, dropped",
                              static_cast<unsigned>( kMaxLineLen ) );
                lineLen_ = 0;
                lineOverflow_ = false;
                continue;
            }

            if( lineLen_ == 0 )
            {
                continue;   // blank line -- heartbeat, silently consumed (see header note)
            }

            bus_->post( makeControlIn( std::string_view( lineBuf_, lineLen_ ) ) );
            xSemaphoreTake( lineAckSem_, portMAX_DELAY );   // wait for the listener to finish reading lineBuf_
            lineLen_ = 0;
        }
    }

    void ControlChannel::maybeSendHeartbeat()
    {
        xSemaphoreTake( handleMutex_, portMAX_DELAY );
        const pal::NetHandle h = handle_;
        xSemaphoreGive( handleMutex_ );

        if( ! pal::net_valid( h ) )
        {
            return;
        }

        if( nowEpochMillis() - lastSendMs_ < static_cast<int64_t>( kHeartbeatIntervalMs ) )
        {
            return;
        }

        sendWithNewline( std::string_view() );   // just the trailing '\n' -- a blank-line heartbeat, see header note
    }

    bool ControlChannel::sendWithNewline( std::string_view body )
    {
        xSemaphoreTake( handleMutex_, portMAX_DELAY );

        const pal::NetHandle h = handle_;
        bool ok = false;

        if( pal::net_valid( h ) )
        {
            ok = pal::net_send( h, body.data(), body.size() ) && pal::net_send( h, "\n", 1 );

            if( ok )
            {
                lastSendMs_ = nowEpochMillis();
            }
            else
            {
                pal::net_close( handle_ );
                handle_ = pal::NetHandle{ -1 };
            }
        }

        xSemaphoreGive( handleMutex_ );
        return ok;
    }
} // namespace stick
