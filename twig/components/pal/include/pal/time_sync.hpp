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
/** \file time_sync.hpp
 *
 * Time-sync (SNTP) over QEMU's emulated Ethernet, backed by ESP-IDF's built-in
 * `esp_netif_sntp` client. Brings up the OpenCores Ethernet MAC (`CONFIG_ETH_USE_OPENETH`),
 * waits for a DHCP lease from QEMU's SLIRP user-mode network, then queries a public NTP
 * server over the host's internet and marks the device clock valid once synced (via
 * `naxe::setTimeValid`).
 *
 * This is the ESP-IDF backend of the PAL's `time_sync` surface; a real board would use a
 * different MAC/PHY but the same `esp_netif_sntp` client.
 */
#pragma once

#include <cstdint>

namespace pal
{
    /** Brings up Ethernet and initializes SNTP.
     *
     * @return true if an IP address was acquired (DHCP); false otherwise.
     */
    bool timeSyncInit();

    /** Waits up to `timeoutMs` for the first successful SNTP sync.
     *
     * On success, marks the device clock valid (naxe::setTimeValid(true)).
     *
     * @return true if the clock was synchronized within the timeout.
     */
    bool timeSyncWait( uint32_t timeoutMs );
} // namespace pal
