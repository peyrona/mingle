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
 * GraalVM-based JavaScript language runtime for Mingle.
 * <p>
 * Replaces the legacy {@code NashornRT}, which relied on the Nashorn engine removed
 * from the JDK in Java 15. This implementation uses GraalJS, Nashorn's officially
 * supported successor, via the GraalVM Polyglot API.
 * <p>
 * <b>Required JARs</b> (Maven Central, GraalVM 23.0.x):
 * <ul>
 *   <li>{@code org.graalvm.sdk:graal-sdk}</li>
 *   <li>{@code com.oracle.truffle:truffle-api}</li>
 *   <li>{@code org.graalvm.js:js} (~35 MB)</li>
 * </ul>
 * All three must be placed on the application classpath (e.g. {@code todeploy/lib/})
 * <em>before</em> launching Stick or Tape. No GraalVM JDK is required.
 * <p>
 * All behaviour is inherited from {@link GraalVmRT}; this class exists only to
 * satisfy {@link com.peyrona.mingle.candi.LangBuilder}'s reflection-based
 * instantiation, which requires a no-arg constructor and a concrete class.
 *
 * @author Francisco José Morero Peyrona
 *
 * Official web site at: <a href="https://github.com/peyrona/mingle">https://github.com/peyrona/mingle</a>
 */
public final class GraalJsRT extends GraalVmRT
{
    /** No-arg constructor required by {@link com.peyrona.mingle.candi.LangBuilder}. */
    public GraalJsRT()
    {
        super( "js" );
    }
}
