/*
 * Copyright Francisco Morero Peyrona.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.peyrona.mingle.controllers.rpi;

import com.peyrona.mingle.lang.japi.UtilStr;
import java.util.Random;

/**
 *
 * @author Francisco José Morero Peyrona
 *
 * Official web site at: <a href="https://mingle.peyrona.com">https://mingle.peyrona.com</a>
 */
final class PinFaked implements IPin
{
    private final boolean bDigital;
    private final boolean bInput;
    private       Object  value = null;

    //------------------------------------------------------------------------//

    PinFaked( boolean isDigital, boolean isInput )
    {
        this.bDigital = isDigital;
        this.bInput   = isInput;
    }

    //------------------------------------------------------------------------//

    @Override
    public boolean isInput()
    {
        return bInput;
    }

    @Override
    public boolean isDigital()
    {
        return bDigital;
    }

    @Override
    public void cleanup()
    {
        // Nothing to do
    }

    @Override
    public Object read()
    {
        if( (! bInput) && (value != null) )
            return value;

        int min = 0;
        int max = (bDigital ? 1 : 255);
        int val = new Random().nextInt( max - min + 1 ) + min;

        return bDigital ? (val == 1) : val;
    }

    @Override
    public void write( boolean value )
    {
        if( ! bInput)
            this.value = value;
    }

    @Override
    public void write( int value )
    {
        if( ! bInput)
            this.value = value;
    }

    @Override
    public int hashCode()
    {
        int hash = 5;
        hash = 37 * hash + (this.bDigital ? 1 : 0);
        hash = 37 * hash + (this.bInput ? 1 : 0);
        return hash;
    }

    @Override
    public boolean equals(Object obj)
    {
        if( this == obj )
            return true;

        if( obj == null )
            return false;

        if( getClass() != obj.getClass() )
            return false;

        final PinFaked other = (PinFaked) obj;

        if( this.bDigital != other.bDigital )
            return false;

        return this.bInput == other.bInput;
    }

    @Override
    public String toString()
    {
        return UtilStr.toString( this );
    }
}