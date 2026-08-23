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
/** \file message.cpp
 * Message factories and clock (see message.hpp).
 */
#include "stick/message.hpp"

#include "naxe/datetime.hpp"

namespace stick
{
    int64_t nowEpochMillis()
    {
        return naxe::nowEpochSeconds() * 1000;
    }

    Message makeDeviceChanged( std::string_view device, const naxe::Value & newValue, bool isOwn )
    {
        return Message{ MsgType::DeviceChanged, nowEpochMillis(), device, isOwn, true, newValue };
    }

    Message makeChangeActuator( std::string_view device, const naxe::Value & newValue, bool isOwn )
    {
        return Message{ MsgType::ChangeActuator, nowEpochMillis(), device, isOwn, true, newValue };
    }

    Message makeReadDevice( std::string_view device, bool isOwn )
    {
        return Message{ MsgType::ReadDevice, nowEpochMillis(), device, isOwn, false, naxe::Value() };
    }

    Message makeExecute( std::string_view scriptOrRuleName, bool force, bool isOwn )
    {
        return Message{ MsgType::Execute, nowEpochMillis(), scriptOrRuleName, isOwn, true, naxe::Value::makeBool( force ) };
    }

    Message makeTemporalCheck()
    {
        return Message{ MsgType::TemporalCheck, nowEpochMillis(), std::string_view(), true, false, naxe::Value() };
    }

    Message makeControlIn( std::string_view line )
    {
        return Message{ MsgType::ControlIn, nowEpochMillis(), line, false, false, naxe::Value() };
    }
} // namespace stick
