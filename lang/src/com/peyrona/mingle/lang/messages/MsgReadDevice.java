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

package com.peyrona.mingle.lang.messages;

/**
 * Requests to the IDriver (which request to IController) to read current device's value.<br>
 * <br>
 * This is used just when a device is created to find out its value.
 *
 * @author Francisco José Morero Peyrona
 *
 * Official web site at: <a href="https://github.com/peyrona/mingle">https://github.com/peyrona/mingle</a>
 */
public class MsgReadDevice extends Message
{
    public MsgReadDevice( String device )
    {
        this( device, true );
    }

    public MsgReadDevice( String device, boolean isOwn )
    {
        super( device, null, isOwn );
    }
}