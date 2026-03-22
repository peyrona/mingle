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
package com.peyrona.mingle.stick;

import com.peyrona.mingle.lang.interfaces.commands.ILibrary;
import com.peyrona.mingle.lang.interfaces.exen.IRuntime;

/**
 * Manages LIBRARY instances in the runtime environment.
 * <p>
 * Libraries are started before any other command type so that their functions
 * are available to PRE ONSTART scripts and all subsequent rule evaluations.
 * Each library's {@link ILibrary#start} call loads the external code, locates
 * the matching class (for Java), calls {@code init(Map)} if present, and
 * registers the library's public methods in the expression evaluator.
 *
 * @author Francisco José Morero Peyrona
 *
 * Official web site at: <a href="https://github.com/peyrona/mingle">https://github.com/peyrona/mingle</a>
 */
final class   LibraryManager
      extends BaseManager<ILibrary>
{
    //------------------------------------------------------------------------//
    // PACKAGE SCOPE

    LibraryManager( IRuntime runtime )
    {
        super( runtime );
    }
}