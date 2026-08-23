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
/** \file checker_main.cpp
 *
 * Host-side CLI entry point for the compatibility checker.
 *
 * Usage:
 *   checker <model-file>                 — print findings; exit 0 if clean, 1 otherwise.
 *   checker --emit-selection <model-file> — print the Kconfig driver selection (for
 *                                           tools/build_model.sh) and exit 0; on any
 *                                           finding, print the report and exit 1.
 *
 * Host-only: links naxe + jsonview + cil + the header-only driver registry.
 */
#include <cstdio>
#include <fstream>
#include <string>
#include <string_view>
#include <vector>

#include "cil/builder.hpp"
#include "compat_checker/checker.hpp"
#include "drivers/registry.hpp"
#include "jsonview/json_view.hpp"
#include "naxe/arena.hpp"
#include "naxe/util.hpp"

namespace
{
    constexpr size_t kModelBufferMax = 1 << 20;  // 1 MiB — generous for any real .model
    constexpr size_t kArenaBufferSize = 1 << 20; // arena for parsed ASTs/unescaped text
    constexpr unsigned kMaxTokens = 2048;
    constexpr size_t kMaxCommands = 256;

    /** Reads a whole file into `out`; returns false on failure. */
    bool readFile( const char * path, std::string & out )
    {
        std::ifstream in( path, std::ios::binary );
        if( ! in ) return false;
        out.assign( std::istreambuf_iterator<char>( in ), std::istreambuf_iterator<char>() );
        return in.good() || in.eof();
    }

    /** Human-readable label for a finding kind. */
    const char * kindLabel( compat_checker::FindingKind kind )
    {
        switch( kind )
        {
            case compat_checker::FindingKind::UnsupportedFunction:   return "unsupported function";
            case compat_checker::FindingKind::UnsupportedMethod:     return "unsupported method";
            case compat_checker::FindingKind::UnsupportedOperator:   return "unsupported operator";
            case compat_checker::FindingKind::EdgeDetectionOperator: return "edge-detection operator";
            case compat_checker::FindingKind::NonUneScript:          return "non-une script";
            case compat_checker::FindingKind::FileBasedScript:       return "file-based script";
            case compat_checker::FindingKind::UnsupportedAction:     return "unsupported action";
            case compat_checker::FindingKind::UnknownDriver:         return "unknown driver";
            case compat_checker::FindingKind::ParseError:            return "parse error";
        }
        return "?";
    }

    /** Prints every finding, one per line, to stderr — stdout in `--emit-selection` mode
     * is reserved for the selection-matrix payload (see build_model.sh, which redirects
     * stdout to a file), so diagnostics must never land there or they get swallowed. */
    void printReport( const compat_checker::Report & report )
    {
        for( uint32_t i = 0; i < report.count(); ++i )
        {
            const compat_checker::Finding & f = report.at( i );
            std::fprintf( stderr, "[%s] command %d: '%.*s'", kindLabel( f.kind ), f.commandIndex,
                         static_cast<int>( f.name.size() ), f.name.data() );
            if( ! f.detail.empty() )
            {
                std::fprintf( stderr, " (%.*s)", static_cast<int>( f.detail.size() ), f.detail.data() );
            }
            std::fprintf( stderr, "\n" );
        }
    }

    /** Builds the full driver-selection matrix: one `CONFIG_DRIVER_*=y|n` line per
     * registry entry — `y` for every referenced-and-available driver, `n` otherwise. The
     * drivers' Kconfig symbols default to `y`, so the selective build must emit explicit
     * `=n` lines to keep unreferenced drivers out of the image. */
    std::string buildSelection( const cil::Command * commands, uint32_t count )
    {
        bool referenced[ drivers::kDriverRegistryCount ] = {};

        auto recordDriver = [ & ]( std::string_view name )
        {
            const drivers::DriverDescriptor * d = drivers::findDriver( name );
            if( d == nullptr ) return;
            for( size_t i = 0; i < drivers::kDriverRegistryCount; ++i )
            {
                if( &drivers::kDriverRegistry[ i ] == d )
                {
                    referenced[ i ] = true;
                    return;
                }
            }
        };

        for( uint32_t i = 0; i < count; ++i )
        {
            const cil::Command & cmd = commands[ i ];
            if( cmd.type == cil::CommandType::Device )
            {
                recordDriver( cmd.device.driver );
            }
            else if( cmd.type == cil::CommandType::Driver )
            {
                recordDriver( cmd.driver.name );
            }
        }

        std::string out;
        for( size_t i = 0; i < drivers::kDriverRegistryCount; ++i )
        {
            out += drivers::kDriverRegistry[ i ].kconfig;
            out += referenced[ i ] ? "=y\n" : "=n\n";
        }
        return out;
    }
} // namespace

int main( int argc, char ** argv )
{
    bool emitSelection = false;
    const char * modelPath = nullptr;

    for( int i = 1; i < argc; ++i )
    {
        const std::string_view arg( argv[ i ] );
        if( arg == "--emit-selection" )
        {
            emitSelection = true;
        }
        else if( modelPath == nullptr )
        {
            modelPath = argv[ i ];
        }
        else
        {
            std::fprintf( stderr, "usage: checker [--emit-selection] <model-file>\n" );
            return 2;
        }
    }

    if( modelPath == nullptr )
    {
        std::fprintf( stderr, "usage: checker [--emit-selection] <model-file>\n" );
        return 2;
    }

    std::string model;
    if( ! readFile( modelPath, model ) )
    {
        std::fprintf( stderr, "checker: cannot read model file: %s\n", modelPath );
        return 2;
    }
    if( model.size() > kModelBufferMax )
    {
        std::fprintf( stderr, "checker: model file too large (%zu bytes)\n", model.size() );
        return 2;
    }

    std::vector<jsmntok_t> tokens( kMaxTokens );
    jsonview::JsonView view( model.data(), model.size(), tokens.data(), kMaxTokens );
    if( ! view.ok() )
    {
        std::fprintf( stderr, "checker: model is not valid JSON\n" );
        return 2;
    }

    std::vector<uint8_t> arenaBuf( kArenaBufferSize );
    naxe::Arena arena( arenaBuf.data(), arenaBuf.size() );

    std::vector<cil::Command> commands( kMaxCommands );
    const int built = cil::buildModel( view, arena, commands.data(), static_cast<int>( kMaxCommands ) );

    if( built <= 0 )
    {
        std::fprintf( stderr, "checker: no commands could be built from the model\n" );
        return 2;
    }

    compat_checker::Report report;
    compat_checker::checkModel( commands.data(), static_cast<uint32_t>( built ), report, arena );

    if( ! report.empty() )
    {
        printReport( report );
        std::fprintf( stderr, "checker: %u incompatibilit%s found\n", report.count(),
                     report.count() == 1 ? "y" : "ies" );
        return 1;
    }

    if( emitSelection )
    {
        const std::string selection = buildSelection( commands.data(), static_cast<uint32_t>( built ) );
        std::fputs( selection.c_str(), stdout );
    }
    else
    {
        std::printf( "checker: model is compatible\n" );
    }

    return 0;
}
