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

package com.peyrona.mingle.lang;

import com.peyrona.mingle.lang.japi.UtilColls;

/**
 *
 * @author Francisco José Morero Peyrona
 *
 * Official web site at: <a href="https://github.com/peyrona/mingle">https://github.com/peyrona/mingle</a>
 */
public class MingleException extends RuntimeException
{
    public static final short SHOULD_NOT_HAPPEN = 0;
    public static final short INVALID_ARGUMENTS = 1;
    public static final short INVALID_STATE     = 2;

    private static final String[] asMSG = new String[] { "This should not happen", "Invalid arguments" };

    //------------------------------------------------------------------------//

    public MingleException()
    {
        super( "" );
    }

    public MingleException( short msg )
    {
        super( asMSG[msg] );
    }

    public MingleException( short msg, Object... args )
    {
        super( asMSG[msg] +": "+ UtilColls.toString( args ) );
    }

    public MingleException( String message )
    {
        super( message );
    }

    public MingleException( Throwable cause )
    {
        super( cause );
    }

    public MingleException( String message, Throwable cause )
    {
        super( message, cause );
    }
}