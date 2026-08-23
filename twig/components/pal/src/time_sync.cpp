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
/** \file time_sync.cpp
 * SNTP time sync over QEMU's emulated Ethernet (see time_sync.hpp).
 */
#include "pal/time_sync.hpp"

#include <cstdio>

#include "esp_err.h"
#include "esp_eth.h"
#include "esp_eth_mac_openeth.h"
#include "esp_event.h"
#include "esp_netif.h"
#include "esp_netif_sntp.h"
#include "freertos/FreeRTOS.h"
#include "freertos/task.h"

#include "naxe/datetime.hpp"

namespace pal
{
    namespace
    {
        /** Called by esp_netif_sntp once the first sync completes. */
        void onSync( struct timeval * tv )
        {
            (void) tv;
            naxe::setTimeValid( true );
            std::printf( "[time-sync] synchronized\n" );
        }
    } // namespace

    bool timeSyncInit()
    {
#if !CONFIG_ETH_USE_OPENETH
        std::printf( "[time-sync] CONFIG_ETH_USE_OPENETH is off — no networking on this target\n" );
        return false;
#else
        std::printf( "[time-sync] bringing up Ethernet (QEMU open_eth)...\n" );

        ESP_ERROR_CHECK( esp_netif_init() );
        ESP_ERROR_CHECK( esp_event_loop_create_default() );

        esp_netif_config_t netifCfg = ESP_NETIF_DEFAULT_ETH();
        esp_netif_t * ethNetif = esp_netif_new( &netifCfg );

        eth_mac_config_t macConfig = ETH_MAC_DEFAULT_CONFIG();
        eth_phy_config_t phyConfig = ETH_PHY_DEFAULT_CONFIG();
        phyConfig.autonego_timeout_ms = 100; // open_eth has no real PHY to negotiate with

        esp_eth_mac_t * mac = esp_eth_mac_new_openeth( &macConfig );
        esp_eth_phy_t * phy = esp_eth_phy_new_dp83848( &phyConfig ); // stand-in PHY

        esp_eth_config_t ethConfig = ETH_DEFAULT_CONFIG( mac, phy );
        esp_eth_handle_t ethHandle = nullptr;
        ESP_ERROR_CHECK( esp_eth_driver_install( &ethConfig, &ethHandle ) );

        esp_eth_netif_glue_handle_t glue = esp_eth_new_netif_glue( ethHandle );
        ESP_ERROR_CHECK( esp_netif_attach( ethNetif, glue ) );
        ESP_ERROR_CHECK( esp_eth_start( ethHandle ) );

        std::printf( "[time-sync] waiting for DHCP lease...\n" );

        esp_netif_ip_info_t ipInfo{};
        bool gotIp = false;

        for( int attempt = 0; attempt < 100; ++attempt ) // up to ~20 s
        {
            if( esp_netif_get_ip_info( ethNetif, &ipInfo ) == ESP_OK && ipInfo.ip.addr != 0 )
            {
                gotIp = true;
                break;
            }
            vTaskDelay( pdMS_TO_TICKS( 200 ) );
        }

        if( ! gotIp )
        {
            std::printf( "[time-sync] FAIL: never got a DHCP lease\n" );
            return false;
        }

        std::printf( "[time-sync] got IP " IPSTR "\n", IP2STR( &ipInfo.ip ) );

        // Public NTP pool, resolved via SLIRP's DNS (10.0.2.3); the host forwards the
        // outbound UDP/123 request onto the internet.
        esp_sntp_config_t sntpConfig = ESP_NETIF_SNTP_DEFAULT_CONFIG( "pool.ntp.org" );
        sntpConfig.sync_cb = &onSync;

        if( esp_netif_sntp_init( &sntpConfig ) != ESP_OK )
        {
            std::printf( "[time-sync] FAIL: esp_netif_sntp_init\n" );
            return false;
        }

        return true;
#endif
    }

    bool timeSyncWait( uint32_t timeoutMs )
    {
        const TickType_t ticks = pdMS_TO_TICKS( timeoutMs );
        const esp_err_t err = esp_netif_sntp_sync_wait( ticks );

        if( err == ESP_OK )
        {
            naxe::setTimeValid( true );
            return true;
        }

        std::printf( "[time-sync] not synced within %u ms (err %d)\n",
                     static_cast<unsigned>( timeoutMs ), static_cast<int>( err ) );
        return false;
    }
} // namespace pal
