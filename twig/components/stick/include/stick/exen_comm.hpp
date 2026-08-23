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
/** \file exen_comm.hpp
 *
 * Parses/builds the control-channel wire protocol, ported from
 * lang/.../japi/ExEnComm.java + the request-handling shapes read directly out of
 * stick/.../GridManager.java's ServerListener (the one real piece of Java that already
 * implements every request end-to-end).
 *
 * Framing note: this module handles ONE already-newline-stripped line's JSON body --
 * `{ "<RequestName>": <payload-or-null> }`. The newline-delimited line reassembly itself
 * (accumulating bytes off pal::net_recv() until '\n') is control_channel.cpp's job, not
 * this one's -- matches jsonview::JsonView, which likewise only ever sees a buffer
 * someone else already delimited.
 *
 * LIFETIME WARNING, the one genuinely subtle thing in this file -- read before touching
 * parseExenComm()'s Add/Remove handling: cil::buildCommand() (cil/builder.hpp) writes
 * ZERO-COPY std::string_views into whatever buffer backed the JsonView it's given. For
 * the boot-time model file this is safe: that buffer is read once and kept resident for
 * the process's whole life. For an INBOUND control-channel line, it is NOT safe --
 * control_channel.cpp reuses one line buffer for every message, so a cil::Command built
 * directly against that buffer would have its name/driver/when/etc. spans silently
 * corrupt as soon as the NEXT line arrives. parseExenComm() avoids this by copying an
 * Add/Remove payload's raw JSON bytes (JsonView::getRawSpan()) into `arena` FIRST, then
 * building commands against that arena-resident copy -- so the resulting spans live as
 * long as `arena` does, matching every other long-lived CIL command's contract. Every
 * other request's payload data (Change/Changed/Readed's value, Read/Execute/Error's bare
 * string) goes through naxe::jsonUnescape()/cil::jsonToUneValue(), which already
 * arena-copy internally, so no special handling is needed there.
 *
 * Tier 1 (hardware-agnostic): no FreeRTOS/ESP-IDF dependency.
 */
#pragma once

#include <cstddef>
#include <cstdint>
#include <string_view>

#include "cil/model.hpp"
#include "jsonview/json_view.hpp"
#include "naxe/arena.hpp"
#include "naxe/value.hpp"

namespace stick
{
    /** The control-channel wire protocol's request verbs.
     *
     * Matches lang/.../japi/ExEnComm.java's Request enum exactly, plus Unknown (never
     * appears on the wire -- an internal sentinel for "parse failed" / "not a request
     * this port's ExEn ever needs to construct or handle").
     */
    enum class ExRequest : uint8_t
    {
        List, Listed, Add, Added, Remove, Removed,
        Changed, Change, Read, Readed, Execute, Exit, Error,
        Unknown
    };

    /** One parsed control-channel request, with the payload fields that request needs. */
    struct ExInMessage
    {
        // A control-channel Add/Remove batch has no real-usage data to size this from --
        // generous for "push a handful of new devices/rules at once", revisit once real
        // control-channel traffic exists.
        static constexpr uint32_t kMaxBatchCommands = 8;

        ExRequest request = ExRequest::Unknown;

        // Change/Changed/Readed: the payload's "name" field (device name).
        // Read/Execute: the bare payload string (rule/script/device name to act on).
        // Error: the bare payload string (the error message text).
        // Unused (left empty) for List/Exit/Add/Remove/Unknown.
        std::string_view name;

        // Change/Changed/Readed only -- the payload's "value"/"when" fields.
        // hasValue is false if "value" was absent or JSON null (matches
        // ExEnComm.getValue()'s own null-tolerance). whenMs is 0 if "when" was absent --
        // caller should stamp its own clock in that case, matching real Java's
        // ExEnComm.getWhen() falling back to System.currentTimeMillis().
        bool hasValue = false;
        naxe::Value value;
        int64_t whenMs = 0;

        // Add/Remove only: commands successfully built from the payload (a single CIL
        // command object, or a JSON array of them -- both real shapes, see
        // ExEnComm.java's array-vs-single constructors). commandCount can be LESS than
        // what the payload actually contained: a per-command build failure is skipped,
        // not fatal to the rest of the batch, and a payload with more than
        // kMaxBatchCommands entries is silently capped. LIFETIME: these commands'
        // std::string_views point into `arena` (the parameter passed to
        // parseExenComm()), NOT into the original `line` buffer -- see this header's top
        // comment.
        cil::Command commands[ kMaxBatchCommands ];
        uint32_t commandCount = 0;
    };

    /** Parses one newline-stripped ExEnComm line.
     *
     * `tokens`/`maxTokens` back the JSON parse of `line` itself (caller-supplied,
     * stack- or arena-allocated, sized for one line). `arena` is where Add/Remove
     * command spans and Change/Changed/Readed's jsonToUneValue()'d value end up living
     * (see this header's top comment) -- must outlive `out`'s use.
     *
     * Returns false (out.request left at ExRequest::Unknown) on any parse failure:
     * malformed JSON, more than one top-level key, an unrecognized request name, or a
     * required payload field missing/wrong-shaped for that request. Caller should reply
     * with buildError(), matching GridManager.java's ServerListener's own
     * malformed-input tolerance -- never crash, never silently drop.
     *
     * @param line The newline-stripped line bytes.
     * @param tokens Caller-supplied JSON token array backing the parse.
     * @param maxTokens Capacity of tokens.
     * @param arena Long-lived arena for the parsed spans and values.
     * @param out The parsed request, filled on success.
     * @return true if the line parsed into a recognized request.
     */
    bool parseExenComm( std::string_view line, jsmntok_t * tokens, unsigned maxTokens,
                         naxe::Arena & arena, ExInMessage & out );

    /** ---- Outbound builders ----
     *
     * Hand-formatted fixed-shape text, no writer library -- matches stick/action.hpp's
     * "outbound messages are hand-formatted text" convention. Each writes ONLY the JSON
     * object body (no trailing '\n' -- the control channel appends that once, right
     * before pal::net_send(), rather than every builder duplicating it) into `buf`
     * (capacity `cap`) and returns the number of bytes written, or 0 if `buf` was too
     * small OR `value`'s type isn't supported for outbound serialization (see below) --
     * callers must treat 0 as "did not send anything", never send a truncated/partial
     * line (that would corrupt the newline-delimited framing for every message after it).
     *
     * Only List/Add/Remove/Change/Read/Execute/Exit are ever RECEIVED by this device
     * (this ExEn is always the control-channel's client, dialing a host that plays the
     * traditional GridManager-server role) -- so only Changed/Readed/Added/Removed/Error
     * are ever SENT.
     *
     * Added/Removed are a DELIBERATE SIMPLIFICATION, not a byte-for-byte match of real
     * Java: `GridManager.java`'s `ServerListener._add_()`/`_remove_()` reply with the
     * FULL command JSON. This port has no such "unbuild" serializer (JSON is read-only by
     * design), so `buildAdded()`/`buildRemoved()` instead send a smaller informational
     * ack -- `{"Added":{"name":<name-or-null>,"type":"device|driver|rule|script"}}` --
     * enough for a host to know WHAT got added/removed without echoing its full
     * definition back.
     *
     * Building a `Listed` reply is NOT implemented at all -- same root cause (no CIL
     * "unbuild"), deliberately deferred, not silently dropped.
     *
     * Value serialization support: Int (plain integer) / Float (formatted "%.6g",
     * matching float32's own ~7-significant-digit precision ceiling) / Bool
     * ("true"/"false") / String (JSON-escaped, quoted). DateTime/List/Pair are NOT
     * supported (returns 0) -- DateTime because this port's single unified epoch-seconds
     * representation can't tell whether the original Une value was a `date` or a `time`
     * (real Java serializes them as two different ExtraType JSON shapes), so guessing
     * which one to emit would risk silently corrupting what the host-side deserializer
     * expects; List/Pair for the same "no generic serializer, on purpose" reason as
     * everywhere else in this port.
     *
     * @param buf Destination buffer for the JSON object body.
     * @param cap Capacity of buf.
     * @param name The device name.
     * @param value The value to serialize.
     * @param whenMs The event's epoch-millisecond timestamp.
     * @return The number of bytes written, or 0 if buf was too small or the value type
     *         isn't supported.
     */
    size_t buildChanged( char * buf, size_t cap, std::string_view name,
                          const naxe::Value & value, int64_t whenMs );
    /** Builds a `{"Readed":{...}}` reply; same contract as buildChanged(). */
    size_t buildReaded( char * buf, size_t cap, std::string_view name,
                         const naxe::Value & value, int64_t whenMs );

    /** Builds an `{"Added":{...}}` informational ack; same framing contract as
     * buildChanged().
     *
     * hasName=false emits "name":null (an anonymous Rule -- the one command type that
     * can legitimately lack one). name is ignored when hasName is false.
     *
     * @param buf Destination buffer for the JSON object body.
     * @param cap Capacity of buf.
     * @param type The command type that was added.
     * @param hasName Whether the added command carries a name.
     * @param name The command name (ignored unless hasName).
     * @return The number of bytes written, or 0 on failure.
     */
    size_t buildAdded( char * buf, size_t cap, cil::CommandType type, bool hasName,
                        std::string_view name );
    /** Builds a `{"Removed":{...}}` informational ack; same contract as buildAdded(). */
    size_t buildRemoved( char * buf, size_t cap, cil::CommandType type, bool hasName,
                          std::string_view name );

    /** Builds a `{"Error":<message>}` reply; same framing contract as buildChanged().
     *
     * @param buf Destination buffer for the JSON object body.
     * @param cap Capacity of buf.
     * @param message The error message.
     * @return The number of bytes written, or 0 on failure.
     */
    size_t buildError( char * buf, size_t cap, std::string_view message );
} // namespace stick
