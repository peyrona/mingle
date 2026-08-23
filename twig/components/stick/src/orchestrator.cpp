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
/** \file orchestrator.cpp
 * Orchestrator implementation (see orchestrator.hpp).
 */
#include "stick/orchestrator.hpp"

#include <cstring>

#include "naxe/pair.hpp"
#include "stick/logger.hpp"

namespace stick
{
    namespace
    {
        /** Copies `s`'s bytes into `arena` and repoints `s` at the copy (no-op for an
         *  empty view). @return False only if the arena has no room for the copy. */
        bool stabilizeString( std::string_view & s, naxe::Arena & arena )
        {
            if( s.empty() )
            {
                return true;
            }

            char * dst = static_cast<char *>( arena.alloc( s.size() ) );
            if( dst == nullptr )
            {
                return false;
            }

            std::memcpy( dst, s.data(), s.size() );
            s = std::string_view( dst, s.size() );
            return true;
        }

        /** Deep-copies every std::string_view field of whichever variant `cmd.type`
         * selects into `arena`, so the command no longer depends on the lifetime of
         * whatever buffer those views originally pointed into (model.hpp's zero-copy
         * contract only guarantees they stay valid as long as the ORIGINAL JSON buffer
         * is resident, which callers such as the control channel do not promise).
         * Device.config/init and Rule action values are untouched -- jsonToUneValue()
         * already arena-copies those.
         *
         * @return False only if the arena ran out of room for one of the copies (`cmd`
         *         is left partially stabilized; the caller must not add it).
         */
        bool stabilizeCommandStrings( cil::Command & cmd, naxe::Arena & arena )
        {
            switch( cmd.type )
            {
                case cil::CommandType::Device:
                    return stabilizeString( cmd.device.name, arena )
                        && stabilizeString( cmd.device.driver, arena );

                case cil::CommandType::Driver:
                    return stabilizeString( cmd.driver.name, arena )
                        && stabilizeString( cmd.driver.script, arena );

                case cil::CommandType::Rule:
                    if( ! stabilizeString( cmd.rule.name, arena )
                        || ! stabilizeString( cmd.rule.when, arena )
                        || ! stabilizeString( cmd.rule.ifExpr, arena ) )
                    {
                        return false;
                    }
                    for( uint32_t i = 0; i < cmd.rule.actionCount; ++i )
                    {
                        if( ! stabilizeString( cmd.rule.actions[ i ].target, arena ) )
                        {
                            return false;
                        }
                    }
                    return true;

                case cil::CommandType::Script:
                    if( ! stabilizeString( cmd.script.name, arena )
                        || ! stabilizeString( cmd.script.language, arena )
                        || ! stabilizeString( cmd.script.call, arena ) )
                    {
                        return false;
                    }
                    for( uint32_t i = 0; i < cmd.script.fromCount; ++i )
                    {
                        if( ! stabilizeString( cmd.script.from[ i ], arena ) )
                        {
                            return false;
                        }
                    }
                    return true;

                default:
                    return true;
            }
        }
        /** Extracts the name a command is addressable by, if any.
         *
         * Matches Device/Driver/Script (always named) and Rule (optionally anonymous,
         * per cil::Rule::hasName).
         *
         * @param cmd The command to inspect.
         * @param hasName Receives whether the command has a usable name.
         * @param name Receives the name (empty if !hasName).
         */
        void commandNameFor( const cil::Command & cmd, bool & hasName, std::string_view & name )
        {
            switch( cmd.type )
            {
                case cil::CommandType::Device:
                    name = cmd.device.name;
                    hasName = ! name.empty();
                    break;

                case cil::CommandType::Driver:
                    name = cmd.driver.name;
                    hasName = ! name.empty();
                    break;

                case cil::CommandType::Script:
                    name = cmd.script.name;
                    hasName = ! name.empty();
                    break;

                case cil::CommandType::Rule:
                    hasName = cmd.rule.hasName;
                    name = cmd.rule.hasName ? cmd.rule.name : std::string_view();
                    break;

                default:
                    hasName = false;
                    name = std::string_view();
                    break;
            }
        }
    } // namespace

    Orchestrator::Orchestrator( DeviceManager & devices, RuleManager & rules, ScriptManager & scripts,
                                DriverManager & drivers, EventBus & bus, naxe::Arena & modelArena,
                                uint8_t * cycleArenaBuf, size_t cycleArenaSize,
                                ControlChannel * controlChannel )
        : devices_( &devices )
        , rules_( &rules )
        , scripts_( &scripts )
        , drivers_( &drivers )
        , bus_( &bus )
        , modelArena_( &modelArena )
        , cycleArena_( cycleArenaBuf, cycleArenaSize )
        , environment_( nullptr )
        , controlChannel_( controlChannel )
    {
    }

    void Orchestrator::attach()
    {
        bus_->addListener( &Orchestrator::busListenerTrampoline, this, EventBus::kAllTypes );
    }

    void Orchestrator::boot( const cil::Command * commands, uint32_t count )
    {
        for( uint32_t i = 0; i < count; ++i )
        {
            addOneCommand( commands[ i ] );
        }
    }

    bool Orchestrator::addOneCommand( const cil::Command & cmd )
    {
        // Copy into modelArena_ FIRST: Device/Rule/Script store a pointer to the
        // cil::Device/Rule/Script they were built from (see device.hpp/rule.hpp/
        // script.hpp), which must therefore outlive the manager entry -- the struct copy
        // below (*stable = cmd) gives it a stable ADDRESS, and stabilizeCommandStrings()
        // then gives its std::string_view fields a stable, arena-owned BACKING BUFFER too,
        // independent of whatever buffer `cmd`'s own views originally pointed into (the
        // model.hpp zero-copy contract only promises those stay valid as long as the
        // ORIGINAL JSON text is resident -- true for boot()'s own caller-supplied
        // `commands` array today only because it happens to be sliced from a permanent
        // literal, and not something a future caller, e.g. a transient control-channel
        // buffer, need honor).
        cil::Command * stable = modelArena_->alloc<cil::Command>();

        if( stable == nullptr )
        {
            logger().log( LogLevel::Severe, "failed to add command: model arena exhausted" );
            return false;
        }

        *stable = cmd;

        if( ! stabilizeCommandStrings( *stable, *modelArena_ ) )
        {
            logger().log( LogLevel::Severe, "failed to add command: model arena exhausted (string copy)" );
            return false;
        }

        switch( stable->type )
        {
            case cil::CommandType::Driver:
                return drivers_->add( stable->driver );
            case cil::CommandType::Device:
                return devices_->add( stable->device );
            case cil::CommandType::Rule:
                return rules_->add( stable->rule, *modelArena_ );
            case cil::CommandType::Script:
                return scripts_->add( stable->script, *modelArena_ );
            default:
                return false;
        }
    }

    bool Orchestrator::removeOneCommand( const cil::Command & cmd )
    {
        bool hasName;
        std::string_view name;
        commandNameFor( cmd, hasName, name );

        if( ! hasName )
        {
            return false;
        }

        switch( cmd.type )
        {
            case cil::CommandType::Device: return devices_->remove( name );
            case cil::CommandType::Rule:   return rules_->remove( name );
            case cil::CommandType::Script: return scripts_->remove( name );
            case cil::CommandType::Driver: return drivers_->remove( name );
            default:                       return false;
        }
    }

    void Orchestrator::sendControlReply( std::string_view body )
    {
        if( controlChannel_ != nullptr && ! body.empty() )
        {
            controlChannel_->sendLine( body );
        }
    }

    void Orchestrator::handleMessage( const Message & msg )
    {
        switch( msg.type )
        {
            case MsgType::DeviceChanged:  handleDeviceChanged( msg ); break;
            case MsgType::ChangeActuator: handleChangeActuator( msg ); break;
            case MsgType::Execute:        handleExecute( msg ); break;
            case MsgType::ReadDevice:     handleReadDevice( msg ); break;
            case MsgType::TemporalCheck:  handleTemporalCheck( msg ); break;
            case MsgType::ControlIn:      handleControlIn( msg ); break;
        }
    }

    void Orchestrator::handleDeviceChanged( const Message & msg )
    {
        Device * device = devices_->named( msg.name );
        if( device == nullptr )
        {
            logger().log( LogLevel::Info, "DeviceChanged for unknown device '%.*s' ignored",
                          static_cast<int>( msg.name.size() ), msg.name.data() );
            return;
        }

        if( device->setValue( msg.value ) )
        {
            // Publish outward ONLY for changes THIS ExEn produced itself (msg.isOwn) --
            // a change that arrived FROM the control channel (handleControlIn()'s
            // Changed/Readed case, isOwn=false) must NOT be echoed straight back out.
            // Matches Java's ExEnComm.java class comment: "An ExEn sends only messages
            // produced by itself, therefore an ExEn can not resend messages received
            // from another ExEn or tool."
            if( msg.isOwn )
            {
                char buf[ 256 ];
                const size_t n = buildChanged( buf, sizeof( buf ), msg.name, msg.value, nowEpochMillis() );
                sendControlReply( std::string_view( buf, n ) );
            }

            evaluateRulesForDevice( msg.name );
        }
    }

    void Orchestrator::handleControlIn( const Message & msg )
    {
        // Real control messages are comparable in size to one model-file command --
        // 128 tokens is the same generous headroom exen_comm.cpp's own
        // buildOneCommandFromCopy() uses for a single Add/Remove command's own JSON.
        // Stack-local, not modelArena_: jsmn tokens are pure parse-time bookkeeping,
        // never referenced after parseExenComm() returns.
        jsmntok_t tokens[ 128 ];
        ExInMessage exIn;

        const bool parsedOk = parseExenComm( msg.name, tokens, 128, *modelArena_, exIn );

        // The line buffer (msg.name, ControlChannel's own stable buffer) is not touched
        // again past this point -- ack NOW, not after the switch below, so the control
        // channel can start reading the NEXT line while this one is still being
        // processed.
        if( controlChannel_ != nullptr )
        {
            controlChannel_->ackLineProcessed();
        }

        if( ! parsedOk )
        {
            char buf[ 256 ];
            const size_t n = buildError( buf, sizeof( buf ), "malformed or unrecognized request" );
            sendControlReply( std::string_view( buf, n ) );
            return;
        }

        switch( exIn.request )
        {
            // No CIL "unbuild" (Command -> JSON) serializer exists anywhere in this
            // port -- a real Listed reply would need one. Acknowledged with an Error
            // rather than silently dropped.
            case ExRequest::List:
            {
                char buf[ 128 ];
                const size_t n = buildError( buf, sizeof( buf ), "List is not supported on this device" );
                sendControlReply( std::string_view( buf, n ) );
                break;
            }

            // A real "gently finish" (Stick.java: rt.exit(0)) has no equivalent yet on
            // this target -- would need esp_restart() or similar. Logged, not silently
            // ignored.
            case ExRequest::Exit:
                logger().log( LogLevel::Warning,
                              "Exit requested over the control channel -- not implemented on this target" );
                break;

            case ExRequest::Add:
                for( uint32_t i = 0; i < exIn.commandCount; ++i )
                {
                    const cil::Command & cmd = exIn.commands[ i ];
                    const bool ok = addOneCommand( cmd );

                    bool hasName;
                    std::string_view name;
                    commandNameFor( cmd, hasName, name );

                    char buf[ 256 ];
                    const size_t n = ok
                        ? buildAdded( buf, sizeof( buf ), cmd.type, hasName, name )
                        : buildError( buf, sizeof( buf ), "failed to add command (see device log for detail)" );

                    sendControlReply( std::string_view( buf, n ) );
                }
                break;

            case ExRequest::Remove:
                for( uint32_t i = 0; i < exIn.commandCount; ++i )
                {
                    const cil::Command & cmd = exIn.commands[ i ];
                    const bool ok = removeOneCommand( cmd );

                    bool hasName;
                    std::string_view name;
                    commandNameFor( cmd, hasName, name );

                    char buf[ 256 ];
                    const size_t n = ok
                        ? buildRemoved( buf, sizeof( buf ), cmd.type, hasName, name )
                        : buildError( buf, sizeof( buf ), "failed to remove command (not found, or anonymous)" );

                    sendControlReply( std::string_view( buf, n ) );
                }
                break;

            // Change/Read/Execute/Changed/Readed all reuse the EXISTING
            // handleChangeActuator()/handleReadDevice()/handleExecute()/
            // handleDeviceChanged() logic directly (not bus_->post()) -- this method
            // already runs ON the bus consumer task, so re-enqueueing would just be a
            // wasted round-trip through the same queue. Each mirrors
            // GridManager.java's ServerListener._postRequest_()'s own "only act if a
            // local command with that name exists" tolerance.
            case ExRequest::Change:
            {
                if( devices_->named( exIn.name ) != nullptr && exIn.hasValue )
                {
                    const Message m = makeChangeActuator( exIn.name, exIn.value, false );
                    handleChangeActuator( m );
                }
                break;
            }

            // Matches GridManager.java's ServerListener's own `case Read:` exactly: a
            // legitimate TWO-reply sequence for one request, not a bug -- reply
            // immediately with whatever cached value exists (so a stable device isn't
            // silently unresponsive), THEN trigger a fresh driver read, which may
            // produce a second, updated Readed via the normal handleReadDevice() path.
            case ExRequest::Read:
            {
                Device * dev = devices_->named( exIn.name );

                if( dev != nullptr )
                {
                    if( dev->hasValue() )
                    {
                        char buf[ 256 ];
                        const size_t n = buildReaded( buf, sizeof( buf ), exIn.name, dev->value(), nowEpochMillis() );
                        sendControlReply( std::string_view( buf, n ) );
                    }

                    const Message m = makeReadDevice( exIn.name, false );
                    handleReadDevice( m );
                }
                break;
            }

            case ExRequest::Execute:
                if( rules_->named( exIn.name ) != nullptr || scripts_->named( exIn.name ) != nullptr )
                {
                    const Message m = makeExecute( exIn.name, false, false );
                    handleExecute( m );
                }
                break;

            // This port merged Java's separate MsgDeviceReaded into DeviceChanged --
            // both wire verbs collapse to the same handleDeviceChanged() call here for
            // the same reason. In practice neither has a real sender in this port's own
            // topology (no other ExEn/grid node exists to send them) -- handled anyway,
            // matching Java's own tolerance for a request that happens to reference a
            // locally-hosted device.
            case ExRequest::Changed:
            case ExRequest::Readed:
            {
                if( devices_->named( exIn.name ) != nullptr && exIn.hasValue )
                {
                    const Message m = makeDeviceChanged( exIn.name, exIn.value, false );
                    handleDeviceChanged( m );
                }
                break;
            }

            case ExRequest::Error:
                logger().log( LogLevel::Warning, "control channel peer reported an error: %.*s",
                              static_cast<int>( exIn.name.size() ), exIn.name.data() );
                break;

            default:
                break; // Listed/Added/Removed/Unknown -- never meaningfully arrive inbound
        }
    }

    naxe::Environment Orchestrator::buildEnvironment()
    {
        // reset() runs the destructor deferred by the PREVIOUS call's defer() below,
        // reclaiming that Pair before this cycle allocates its own.
        cycleArena_.reset();
        environment_ = naxe::Pair::create( devices_->count() );

        if( environment_ != nullptr && ! cycleArena_.defer( environment_ ) )
        {
            naxe::Pair::destroy( environment_ );
            environment_ = nullptr;
        }

        if( environment_ != nullptr )
        {
            for( uint32_t i = 0; i < devices_->count(); ++i )
            {
                Device & d = devices_->at( i );
                if( d.hasValue() )
                {
                    environment_->set( d.name(), d.value() );
                }
            }
        }
        return naxe::Environment( environment_ );
    }

    void Orchestrator::evaluateRulesForDevice( std::string_view deviceName )
    {
        const naxe::Environment env = buildEnvironment();

        for( uint32_t i = 0; i < rules_->count(); ++i )
        {
            Rule & rule = rules_->at( i );
            if( rule.referencesDevice( deviceName ) )
            {
                rule.evaluate( env, cycleArena_, &Orchestrator::actionResultTrampoline, this,
                               nowEpochMillis(), &Orchestrator::scheduleWakeTrampoline, this );
            }
        }
    }

    void Orchestrator::evaluateAllRulesAndFireActions()
    {
        const naxe::Environment env = buildEnvironment();

        for( uint32_t i = 0; i < rules_->count(); ++i )
        {
            rules_->at( i ).evaluate( env, cycleArena_, &Orchestrator::actionResultTrampoline, this,
                                      nowEpochMillis(), &Orchestrator::scheduleWakeTrampoline, this );
        }
    }

    void Orchestrator::handleTemporalCheck( const Message & msg )
    {
        (void) msg;
        evaluateAllRulesAndFireActions();
    }

    void Orchestrator::onActionResult( const ActionResult & result )
    {
        // Actions are always dispatched via the bus, never called in-place: this method
        // runs from inside evaluateRulesForDevice()/evaluateAllRulesAndFireActions()'s rule
        // loop, and both writeActuator() and handleExecute() reach buildEnvironment(), which
        // resets the shared per-cycle arena. Calling them synchronously here would invalidate
        // the Environment out from under any rule still pending later in that same loop (and,
        // for a rule-executes-rule chain, recurse with no bound). Routing through
        // bus_->postDelayed() confines each arena reset to one message per consumer-tick.
        switch( result.kind )
        {
            case ActionKind::ChangeActuator:
                bus_->postDelayed( makeChangeActuator( result.target, result.value ),
                                    static_cast<uint32_t>( result.delayMs ) );
                break;

            case ActionKind::Execute:
                // force=false: a rule named as an Execute target is re-triggered (its WHEN/IF
                // re-evaluated), not fired unconditionally -- matches twig's original.
                bus_->postDelayed( makeExecute( result.target, false ),
                                    static_cast<uint32_t>( result.delayMs ) );
                break;

            case ActionKind::Unsupported:
                logger().log( LogLevel::Warning, "unsupported action target: %.*s",
                              static_cast<int>( result.target.size() ), result.target.data() );
                break;
        }
    }

    void Orchestrator::writeActuator( std::string_view deviceName, const naxe::Value & value )
    {
        const Device * device = devices_->named( deviceName );
        if( device == nullptr )
        {
            logger().log( LogLevel::Info, "ChangeActuator: unknown device '%.*s' ignored",
                          static_cast<int>( deviceName.size() ), deviceName.data() );
            return;
        }

        const DriverEntry * driver = drivers_->named( device->driverName() );
        if( driver == nullptr || ! driver->hasOps )
        {
            logger().log( LogLevel::Info, "ChangeActuator: no driver ops for device '%.*s'",
                          static_cast<int>( deviceName.size() ), deviceName.data() );
            return;
        }

        driver->ops.write( driver->ops.ctx, deviceName, value );
    }

    void Orchestrator::handleChangeActuator( const Message & msg )
    {
        writeActuator( msg.name, msg.value );
    }

    void Orchestrator::handleExecute( const Message & msg )
    {
        const bool force = msg.value.isBool() && msg.value.boolValue();

        if( Rule * rule = rules_->named( msg.name ) )
        {
            const naxe::Environment env = buildEnvironment();
            rule->trigger( force, env, cycleArena_, &Orchestrator::actionResultTrampoline, this,
                           nowEpochMillis(), &Orchestrator::scheduleWakeTrampoline, this );
            return;
        }

        if( Script * script = scripts_->named( msg.name ) )
        {
            const naxe::Environment env = buildEnvironment();
            script->execute( env, cycleArena_, &Orchestrator::actionResultTrampoline, this );
            return;
        }

        logger().log( LogLevel::Info, "Execute: no rule/script named '%.*s'",
                      static_cast<int>( msg.name.size() ), msg.name.data() );
    }

    void Orchestrator::handleReadDevice( const Message & msg )
    {
        const Device * device = devices_->named( msg.name );
        if( device == nullptr )
        {
            return;
        }

        const DriverEntry * driver = drivers_->named( device->driverName() );
        if( driver != nullptr && driver->hasOps )
        {
            driver->ops.read( driver->ops.ctx, msg.name );
        }
    }

    void Orchestrator::busListenerTrampoline( void * ctx, const Message & msg )
    {
        static_cast<Orchestrator *>( ctx )->handleMessage( msg );
    }

    void Orchestrator::actionResultTrampoline( void * ctx, const ActionResult & result )
    {
        static_cast<Orchestrator *>( ctx )->onActionResult( result );
    }

    void Orchestrator::scheduleWakeTrampoline( void * ctx, int64_t delayMs )
    {
        Orchestrator * self = static_cast<Orchestrator *>( ctx );
        const uint32_t clamped = ( delayMs < 0 ) ? 0 : static_cast<uint32_t>( delayMs );
        self->bus_->postDelayed( makeTemporalCheck(), clamped );
    }
} // namespace stick
