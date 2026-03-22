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
package com.peyrona.mingle.candi.graalvm;

/**
 * GraalVM-based Python 3 language runtime for Mingle.
 * <p>
 * Replaces the legacy {@code JythonRT}, which was limited to the unmaintained
 * Python 2.7 interpreter. This implementation uses GraalPy (formerly GraalPython),
 * a full Python 3 implementation on the GraalVM Truffle framework.
 * <p>
 * <b>Required JARs</b> (Maven Central, GraalVM 23.0.x) — <em>optional download</em>:
 * <ul>
 *   <li>{@code org.graalvm.sdk:graal-sdk}</li>
 *   <li>{@code com.oracle.truffle:truffle-api}</li>
 *   <li>{@code org.graalvm.tools:graalpython} (~180 MB)</li>
 * </ul>
 * Due to the large distribution size, the Python JARs are <b>not</b> bundled with
 * the standard Mingle distribution. Download them separately and place them on the
 * application classpath (e.g. {@code todeploy/lib/}) before using Python scripts or
 * libraries. If the JARs are absent, {@code prepare()} will return a descriptive error.
 * <p>
 * All behaviour is inherited from {@link GraalVmRT}; this class exists only to
 * satisfy {@link com.peyrona.mingle.candi.LangBuilder}'s reflection-based
 * instantiation, which requires a no-arg constructor and a concrete class.
 *
 * @author Francisco José Morero Peyrona
 *
 * Official web site at: <a href="https://github.com/peyrona/mingle">https://github.com/peyrona/mingle</a>
 */
public final class GraalPyRT extends GraalVmRT
{
    /** No-arg constructor required by {@link com.peyrona.mingle.candi.LangBuilder}. */
    public GraalPyRT()
    {
        super( "python" );
    }
}
