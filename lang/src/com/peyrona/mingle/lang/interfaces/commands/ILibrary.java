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
package com.peyrona.mingle.lang.interfaces.commands;

import java.util.Map;

/**
 * Represents a LIBRARY command: an external function collection declared in Une source code
 * whose public methods become callable in WHEN/THEN/CONFIG expressions using the colon operator:
 * {@code LibraryName:functionName(args)}.
 * <p>
 * For Java libraries the class to expose is identified by name-matching convention: the runtime
 * scans the JAR for a class whose simple name matches the library name (case-insensitive).
 * For Python and JavaScript libraries the FROM file is the module; its top-level functions
 * are exposed directly.
 * <p>
 * CONFIG values are delivered to the library via convention: if the library class exposes a
 * {@code public static void init(java.util.Map)} method, it is called once after loading.
 *
 * @author Francisco José Morero Peyrona
 *
 * Official web site at: <a href="https://github.com/peyrona/mingle">https://github.com/peyrona/mingle</a>
 */
public interface ILibrary extends ICommand
{
    /**
     * Returns the programming language of this library (e.g. "java", "python", "javascript").
     *
     * @return The programming language name, always lower-case.
     */
    String getLanguage();

    /**
     * Returns the FROM clause contents: an array of file URIs pointing to the library source or JAR.
     *
     * @return An array of URI strings; never null but may be empty if missing.
     */
    String[] getFrom();

    /**
     * Returns the CONFIG values declared in the LIBRARY command, or an empty map if none.
     * These values are passed to the library's {@code init(Map)} method after loading.
     *
     * @return An unmodifiable map of configuration key-value pairs; never null.
     */
    Map<String,Object> getConfig();
}
