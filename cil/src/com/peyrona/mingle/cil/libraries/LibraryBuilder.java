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
package com.peyrona.mingle.cil.libraries;

import com.eclipsesource.json.JsonObject;
import com.eclipsesource.json.JsonValue;
import com.peyrona.mingle.lang.interfaces.commands.ICmdKeys;
import com.peyrona.mingle.lang.interfaces.commands.ILibrary;
import com.peyrona.mingle.lang.japi.CommandSerializer;
import com.peyrona.mingle.lang.japi.UtilJson;
import com.peyrona.mingle.lang.japi.UtilType;
import java.util.HashMap;
import java.util.Map;

/**
 * Builds (deserializes) and unbuilds (serializes) {@link ILibrary} instances.
 * <p>
 * Pattern mirrors {@link com.peyrona.mingle.cil.scripts.ScriptBuilder}: one static
 * {@code build} method for JSON → {@link Library}, one static {@code unbuild} method
 * for {@link ILibrary} → JSON.
 *
 * @author Francisco José Morero Peyrona
 *
 * Official web site at: <a href="https://github.com/peyrona/mingle">https://github.com/peyrona/mingle</a>
 */
public final class LibraryBuilder
{
    /**
     * Deserializes a LIBRARY command from its JSON representation.
     *
     * @param jo JSON object produced by {@link CommandSerializer#Library}.
     * @return A new {@link Library} instance ready to be started.
     */
    public static ILibrary build( JsonObject jo )
    {
        UtilJson json    = new UtilJson( jo );
        String[] aFrom   = UtilType.convertArray( UtilJson.toArray( json.getArray( ICmdKeys.LIBRARY_FROM ) ), String.class );
        Map<String,Object> config = new HashMap<>();

        JsonValue jvConfig = jo.get( ICmdKeys.LIBRARY_CONFIG );

        if( jvConfig != null && ! jvConfig.isNull() )
            jvConfig.asObject()
                    .forEach( member -> config.put( member.getName(), UtilJson.toUneType( member.getValue() ) ) );

        return new Library( json.getString( ICmdKeys.CMD_NAME         ),
                            json.getString( ICmdKeys.LIBRARY_LANGUAGE ),
                            aFrom,
                            config );
    }

    /**
     * Serializes a {@link ILibrary} back to its JSON representation.
     *
     * @param lib The library to serialize.
     * @return JSON string representation of the LIBRARY command.
     */
    public static String unbuild( ILibrary lib )
    {
        return CommandSerializer.Library( lib.name(),
                                          lib.getLanguage(),
                                          lib.getFrom(),
                                          lib.getConfig() );
    }

    //------------------------------------------------------------------------//
    // PRIVATE SCOPE

    private LibraryBuilder()
    {
        // Avoids creating instances of this class
    }
}
