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
 * GraalVM-based R language runtime for Mingle.
 * <p>
 * Provides R scripting support via FastR, the GraalVM Truffle-based R implementation.
 * R scripts can be used in SCRIPT and LIBRARY Une commands, which is particularly
 * useful for statistical processing of sensor data and IoT analytics.
 * <p>
 * <b>Required JARs</b> (Maven Central, GraalVM 23.0.x) — <em>downloaded on demand</em>:
 * <ul>
 *   <li>{@code org.graalvm.sdk:graal-sdk} (already present for JavaScript)</li>
 *   <li>{@code com.oracle.truffle:truffle-api} (already present for JavaScript)</li>
 *   <li>{@code org.graalvm.r:r} (~80 MB)</li>
 * </ul>
 * The {@code r} JAR is not bundled with the standard Mingle distribution due to its
 * size. It is downloaded automatically to {@code todeploy/lib/} the first time a Une
 * programme using R is loaded, via the {@code "download"} entry in the language
 * configuration. The download URL can be overridden in {@code config.json}.
 * <p>
 * <b>Note:</b> FastR availability varies across GraalVM releases. Verify that the
 * JAR exists at the configured {@code "remote"} URL before use. If unavailable,
 * update the URL in {@code config.json} to point to the appropriate distribution.
 * <p>
 * All behaviour is inherited from {@link GraalVmRT}; this class exists only to
 * satisfy {@link com.peyrona.mingle.candi.LangBuilder}'s reflection-based
 * instantiation, which requires a no-arg constructor and a concrete class.
 *
 * @author Francisco José Morero Peyrona
 *
 * Official web site at: <a href="https://github.com/peyrona/mingle">https://github.com/peyrona/mingle</a>
 */
public final class GraalRRT extends GraalVmRT
{
    /** No-arg constructor required by {@link com.peyrona.mingle.candi.LangBuilder}. */
    public GraalRRT()
    {
        super( "R" );
    }
}
