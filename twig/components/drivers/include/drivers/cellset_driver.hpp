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
/** \file cellset_driver.hpp
 *
 * The "spreadsheet" driver: every Device routed to CellSetDriver is a cell that holds
 * either a plain value or a `=formula` referencing other devices (ported from Java's
 * `com.peyrona.mingle.controllers.CellSet`).
 *
 * Design departures from the Java original (each grounded in what this port's runtime
 * already provides, not guessed):
 *
 *  - **One shared instance, not one per device.** Java gives every Device using
 *    CellDriver its own `CellSet` instance, with `map`/`instances`/the Dispatcher held as
 *    static class-level state shared across them. This port's `DriverManager` already
 *    collapses "one IController per Device" into one `DriverOps` shared by every device
 *    routed to it (see driver_manager.hpp) — `CellSetDriver` simply IS that shared state,
 *    with one `Cell` array slot per device instead of one object per device.
 *
 *  - **No incremental per-formula evaluator state.** Java's `IXprEval.eval(name, value)`
 *    is a *stateful* evaluator: it accumulates one variable at a time and only produces a
 *    result once every variable the formula references has been supplied at least once
 *    (returning null, "not ready yet", until then). `naxe::eval()` has no such mode — it
 *    is a stateless, full-`Environment` evaluator (see ast.hpp), the same one `Rule` uses
 *    for WHEN/IF. `CellSetDriver` reproduces the *effect* of Java's accumulation with its
 *    own persistent `env_` Pair, fed one entry per `DeviceChanged` message observed
 *    (every device, not just cells) and never reset — so by the time a formula's last
 *    unknown variable arrives, every other variable it needs is already in `env_`. The
 *    one behavioural difference: a variable that has *never* changed evaluates against
 *    naxe's documented empty-string fallback (ast.hpp) rather than suppressing the
 *    formula's result — the same fallback every WHEN/IF clause in this port already
 *    lives with, not a new one invented here.
 *
 *  - **One long-lived Arena for both AST compilation and evaluation.** `Rule` also
 *    compiles its ASTs into a long-lived arena, but evaluates against a *per-cycle* arena
 *    that Orchestrator resets every dispatch (rule results are consumed and gone before
 *    the next reset). `CellSetDriver`'s formula results are NOT transient: they are
 *    cached in `Cell::value` and compared against on every future recalculation
 *    (`valueEquals`), so any arena backing a String result must outlive that comparison
 *    indefinitely — this port has no such "long-lived but reclaimable" arena tier, so
 *    `CellSetDriver` is given one dedicated Arena (sized at construction, like every
 *    other fixed-capacity resource in this port) that is never reset. In practice this
 *    only matters for String-valued formulas; the numeric/bool formulas in every real
 *    CellSetDriver example in the corpus cost a handful of bytes each.
 *
 *  - **No ExtraTypeCollection live-mutation listener.** Java's CellSet wires a
 *    PropertyChangeListener onto `list`/`pair` config values so a direct in-place mutation
 *    (`list:add(5)`) still fires `sendChanged`. This port's `naxe::List`/`naxe::Pair` have
 *    no such notification hook (mutations are only ever visible by re-reading or
 *    re-writing the device) — a real gap against the Java feature set, not a rounding
 *    error, but orthogonal to the cell/formula mechanism this file ports.
 *
 * Gated by CONFIG_DRIVER_CELLSET (see Kconfig) — FreeRTOS-dependent only via EventBus
 * (registerOps()/attach()); compile()/onDeviceChanged() are pure logic.
 */
#pragma once

#include <cstdint>
#include <string_view>

#include "cil/model.hpp"
#include "naxe/arena.hpp"
#include "naxe/ast.hpp"
#include "naxe/pair.hpp"
#include "naxe/parser.hpp"
#include "naxe/value.hpp"
#include "stick/driver_manager.hpp"
#include "stick/event_bus.hpp"
#include "stick/message.hpp"

namespace drivers
{
    /** The shared spreadsheet driver backing every Device routed to CellSetDriver. */
    class CellSetDriver
    {
    public:
        /** Maximum number of cell devices one firmware image can host. */
        static constexpr uint32_t kMaxCells = 32;
        /** Maximum distinct device names one cell's formula may reference. */
        static constexpr uint32_t kMaxReferencedVars = 8;

        /** Constructs an empty driver.
         *
         * @param bus The EventBus to listen on and post to (must outlive this driver).
         * @param arena Dedicated, never-reset Arena for formula ASTs and evaluation
         *              results (see the class doc comment's Arena note). Must outlive
         *              this driver and must not be shared with a per-cycle arena that
         *              gets reset out from under a cached Cell::value.
         * @param groupResolver Optional group-membership resolver for ANY/ALL/NONE
         *                      quantifiers inside a cell formula (matches Java's CellSet
         *                      passing `getRuntime()::getGroupMemberNames` to its
         *                      evaluator, and Rule's own groupResolver parameter).
         *                      Stored and reused for every add()/write() compile, since
         *                      DriverOps::write's fixed signature has no room to pass
         *                      one per call.
         * @param groupResolverCtx Opaque context for groupResolver.
         */
        CellSetDriver( stick::EventBus & bus, naxe::Arena & arena,
                       naxe::GroupResolverFn groupResolver = nullptr, void * groupResolverCtx = nullptr );

        /** Destroys the accumulated-environment Pair (production firmware never
         *  destructs its one driver — see ClockDriver's own note). */
        ~CellSetDriver();

        CellSetDriver( const CellSetDriver & ) = delete;
        CellSetDriver & operator=( const CellSetDriver & ) = delete;

        /** Registers this driver as an EventBus listener for DeviceChanged (needed to
         *  recalculate formula cells when a device they reference changes — a plain
         *  DriverOps read/write pair, unlike ClockDriver/ConsoleDriver, is not enough).
         *  Call once, before bus.start(). */
        void attach();

        /** Compiles and registers one CellSetDriver-routed Device from the model.
         *
         * Reads `parsed.config`'s required "value" key (a plain value, or a string
         * starting with '=' for a formula — see compile()); rejects (returning false,
         * logged) a formula that fails to parse or that would introduce a circular
         * reference to an already-registered cell (DFS over referenced variable names,
         * matching Java's `hasCircularRef`/`dfsHasCycle`). A rejected device is simply
         * never usable via read()/write() below — this driver has no way to retract the
         * Device entry DeviceManager already holds for it.
         *
         * @param parsed The Device to register (must outlive this driver's use of it —
         *               only its "value" config entry's bytes are read, not retained).
         * @return true if registered.
         */
        bool add( const cil::Device & parsed );

        /** @return DriverOps for DriverManager::registerOps(kCellSetDriverName, ...). */
        stick::DriverOps ops();

    private:
        /** One cell: either a literal value or a compiled formula. */
        struct Cell
        {
            std::string_view name;
            bool hasValue = false;
            naxe::Value value;
            bool isFormula = false;
            naxe::ASTNode * ast = nullptr;
            std::string_view referencedVars[ kMaxReferencedVars ];
            uint32_t referencedVarCount = 0;
        };

        Cell * find( std::string_view name );
        const Cell * find( std::string_view name ) const;
        bool referencesVar( const Cell & cell, std::string_view name ) const;

        /** DFS over `cell`'s referenced variables, looking for a path back to
         *  `originName` through other registered formula cells (matches Java's
         *  `CellSet::dfsHasCycle`). `visited`/`visitedCount` is the caller's scratch
         *  array (capacity kMaxCells), shared across the whole DFS to avoid re-walking
         *  an already-explored variable. */
        bool dfsHasCycle( std::string_view originName, const Cell & cell,
                          std::string_view * visited, uint32_t & visitedCount );

        /** Compiles `raw` into `cell` (formula detection + AST compile + referenced-var
         *  collection for a '='-prefixed string, or a plain stored value otherwise).
         *
         * A formula cell is NOT eagerly evaluated (matches Java: `CellValue::update()`
         * only ever builds the expression, it never evaluates it — the cell has no
         * value until `onDeviceChanged()` below sees one of its referenced devices
         * change). A formula that fails to parse instead stores a visible
         * "Error: ..." string as the cell's value (matches Java surfacing the parse
         * errors as the device's own value rather than dropping the write silently).
         *
         * @return false only when `raw` is a formula string that failed to parse.
         */
        bool compile( Cell & cell, const naxe::Value & raw );

        /** Handles one observed DeviceChanged: accumulates `value` into the persistent
         *  environment, then recalculates every formula cell that references `name`,
         *  posting a new DeviceChanged for each one whose result actually changed
         *  (`valueEquals` guards against a feedback spiral — matches Java's
         *  `Objects.equals` check before `sendChanged`). A cell's own recalculation may
         *  itself trigger this same method again on a later dispatch, letting a chain of
         *  dependent cells settle over several cycles, exactly as in Java. */
        void onDeviceChanged( std::string_view name, const naxe::Value & value );

        /** Re-broadcasts a cell's cached value (a no-op if it has none yet — matches
         *  Java's `read()` only calling `sendReaded` when the value is non-null). */
        void read( std::string_view deviceName );

        /** Recompiles a cell's value/formula in place and broadcasts the result, if any.
         *  Unlike add(), does NOT re-run circular-reference detection (matches Java's
         *  `CellValue::write()`, which calls `update()` directly with no DFS guard). */
        void write( std::string_view deviceName, const naxe::Value & newValue );

        static void readTrampoline( void * ctx, std::string_view deviceName );
        static void writeTrampoline( void * ctx, std::string_view deviceName, const naxe::Value & value );
        static void busListenerTrampoline( void * ctx, const stick::Message & msg );

        stick::EventBus * bus_;
        naxe::Arena * arena_;
        naxe::GroupResolverFn groupResolver_;
        void * groupResolverCtx_;
        naxe::Pair * env_; // accumulated device name -> last known value (see class doc comment)
        Cell cells_[ kMaxCells ];
        uint32_t count_;
    };
} // namespace drivers
