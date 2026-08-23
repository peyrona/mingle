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
/** \file cil_tests.cpp
 *
 * Host/on-device tests for the JSON read path (jsonview over jsmn) and the CIL builders.
 * Covers JsonView field/array lookup and a full `buildModel` over a representative
 * transpiled `.model`, including the Int/Float number split and nested config pairs.
 */
#include "cil/cil_tests.hpp"

#include <cstdio>
#include <cstring>
#include <string_view>

#include "cil/builder.hpp"
#include "cil/model.hpp"
#include "jsonview/json_view.hpp"
#include "naxe/arena.hpp"
#include "naxe/list.hpp"
#include "naxe/pair.hpp"
#include "naxe/value.hpp"

namespace
{
    int failures = 0;

    void check( bool condition, const char * message )
    {
        if( ! condition )
        {
            ++failures;
            std::printf( "FAIL: %s\n", message );
        }
    }

    constexpr size_t kArenaSize = 16384;
    alignas( 8 ) uint8_t arenaBuffer[ kArenaSize ];

    constexpr unsigned kMaxTokens = 256;
    jsmntok_t tokens[ kMaxTokens ];

    void testJsonView()
    {
        const char json[] =
            "{\"name\":\"sensor\",\"value\":42,\"ratio\":1.5,\"on\":true,\"off\":null,"
            "\"list\":[1,2,3],\"nested\":{\"a\":{\"b\":\"deep\"},\"c\":0}}";

        jsonview::JsonView view( json, sizeof( json ) - 1, tokens, kMaxTokens );
        check( view.ok(), "JsonView parses" );
        check( view.type( view.root() ) == jsonview::Type::Object, "root is Object" );

        // findField + getString.
        const int nameIdx = view.findField( view.root(), "name" );
        std::string_view name;
        check( nameIdx >= 0 && view.getString( nameIdx, name ) && name == "sensor", "name field" );

        // Integer vs fractional number.
        const int valueIdx = view.findField( view.root(), "value" );
        int64_t ivalue;
        check( view.getInt64( valueIdx, ivalue ) && ivalue == 42, "int64 value" );
        const int ratioIdx = view.findField( view.root(), "ratio" );
        float fratio;
        check( view.getFloat( ratioIdx, fratio ) && fratio == 1.5f, "float ratio" );

        // bool / null.
        bool b = false;
        check( view.getBool( view.findField( view.root(), "on" ), b ) && b, "bool true" );
        check( view.isNull( view.findField( view.root(), "off" ) ), "null" );

        // array.
        const int listIdx = view.findField( view.root(), "list" );
        check( view.arrayCount( listIdx ) == 3, "array count 3" );
        int64_t first;
        check( view.getInt64( view.arrayElement( listIdx, 0 ), first ) && first == 1, "array[0] == 1" );

        // skipSubtree: find "c" after the nested {"b":"deep"} object.
        const int nestedIdx = view.findField( view.root(), "nested" );
        check( view.findField( nestedIdx, "c" ) >= 0, "findField skips nested object to reach later sibling" );

        // getRawSpan covers a whole subtree.
        std::string_view span;
        check( view.getRawSpan( listIdx, span ) && span == "[1,2,3]", "raw span of array" );
    }

    void testBuildModel()
    {
        // A representative model: device (with config + null init), rule (with an action
        // whose value is JSON-escaped Une source text), driver, script.
        const char * model =
            R"({"commands":[)"
            R"({"cmd":"device","name":"clock","driver":"clockdriver","config":{"interval":3000},"init":null},)"
            R"({"cmd":"rule","name":null,"when":"clock","then":[{"after":-1,"target":"console","value":"\"'clock' value is: \" + clock"}],"if":null},)"
            R"({"cmd":"driver","name":"clockdriver","script":"clockcontroller"},)"
            R"({"cmd":"script","name":"clockcontroller","language":"java","onstart":false,"onstop":false,"prestart":false,"prestop":false,"inline":false,"from":["file://x.jar"],"call":"com.x.Clock"})"
            R"(]})";

        jsonview::JsonView view( model, std::strlen( model ), tokens, kMaxTokens );
        check( view.ok(), "model parses" );

        naxe::Arena arena( arenaBuffer, kArenaSize );
        cil::Command commands[ 8 ];

        const int built = cil::buildModel( view, arena, commands, 8 );
        check( built == 4, "buildModel builds 4 commands" );

        // Command 0: device.
        check( commands[ 0 ].type == cil::CommandType::Device, "cmd0 is Device" );
        check( commands[ 0 ].device.name == "clock", "device name" );
        check( commands[ 0 ].device.driver == "clockdriver", "device driver" );

        const naxe::Value & config = commands[ 0 ].device.config;
        check( config.isPair(), "device config is a Pair" );
        const naxe::Value * interval = config.pairValue()->get( "interval" );
        check( interval != nullptr && interval->isInt() && interval->intValue() == 3000,
               "config.interval is Int 3000" );

        check( commands[ 0 ].device.init.isPair() && commands[ 0 ].device.init.pairValue()->empty(),
               "device init (null) is an empty Pair" );

        // Command 1: rule.
        check( commands[ 1 ].type == cil::CommandType::Rule, "cmd1 is Rule" );
        check( ! commands[ 1 ].rule.hasName, "rule has no name" );
        check( commands[ 1 ].rule.when == "clock", "rule when == clock" );
        check( ! commands[ 1 ].rule.hasIf, "rule has no if" );
        check( commands[ 1 ].rule.actionCount == 1, "rule has 1 action" );
        check( commands[ 1 ].rule.actions[ 0 ].after == -1, "action.after == -1" );
        check( commands[ 1 ].rule.actions[ 0 ].target == "console", "action.target == console" );
        check( commands[ 1 ].rule.actions[ 0 ].hasValue, "action has value" );
        check( commands[ 1 ].rule.actions[ 0 ].value.isString(), "action value is a String" );

        // Command 2: driver.
        check( commands[ 2 ].type == cil::CommandType::Driver, "cmd2 is Driver" );
        check( commands[ 2 ].driver.name == "clockdriver", "driver name" );
        check( commands[ 2 ].driver.script == "clockcontroller", "driver script" );

        // Command 3: script.
        check( commands[ 3 ].type == cil::CommandType::Script, "cmd3 is Script" );
        check( commands[ 3 ].script.name == "clockcontroller", "script name" );
        check( commands[ 3 ].script.language == "java", "script language" );
        check( commands[ 3 ].script.call == "com.x.Clock", "script call" );
        check( commands[ 3 ].script.fromCount == 1 && commands[ 3 ].script.from[ 0 ] == "file://x.jar",
               "script from[0]" );
        check( ! commands[ 3 ].script.inlineCode, "script inline false" );
    }

    void testJsonToUneValue()
    {
        const char json[] = R"({"n":5,"f":2.5,"s":"hi","b":true,"z":null,"arr":[1,"two"],"obj":{"k":9}})";
        jsonview::JsonView view( json, std::strlen( json ), tokens, kMaxTokens );
        naxe::Arena arena( arenaBuffer, kArenaSize );

        // Integral -> Int, fractional -> Float, string -> String, bool -> Bool, null -> "".
        naxe::Value n = cil::jsonToUneValue( view, view.findField( view.root(), "n" ), arena );
        check( n.isInt() && n.intValue() == 5, "jsonToUneValue: integral -> Int" );
        naxe::Value f = cil::jsonToUneValue( view, view.findField( view.root(), "f" ), arena );
        check( f.isFloat() && f.floatValue() == 2.5f, "jsonToUneValue: fractional -> Float" );
        naxe::Value s = cil::jsonToUneValue( view, view.findField( view.root(), "s" ), arena );
        check( s.isString() && s.stringValue() == "hi", "jsonToUneValue: string" );
        naxe::Value b = cil::jsonToUneValue( view, view.findField( view.root(), "b" ), arena );
        check( b.isBool() && b.boolValue(), "jsonToUneValue: bool" );
        naxe::Value z = cil::jsonToUneValue( view, view.findField( view.root(), "z" ), arena );
        check( z.isString() && z.stringValue().empty(), "jsonToUneValue: null -> empty string" );

        // Array -> List, Object -> Pair.
        naxe::Value arr = cil::jsonToUneValue( view, view.findField( view.root(), "arr" ), arena );
        check( arr.isList() && arr.listValue()->size() == 2, "jsonToUneValue: array -> List" );
        naxe::Value obj = cil::jsonToUneValue( view, view.findField( view.root(), "obj" ), arena );
        check( obj.isPair() && obj.pairValue()->get( "k" )->intValue() == 9, "jsonToUneValue: object -> Pair" );
    }
} // namespace

namespace cil
{
    int runCilTests()
    {
        failures = 0;

        testJsonView();
        testBuildModel();
        testJsonToUneValue();

        if( failures == 0 )
        {
            std::printf( "[cil] json+cili tests: PASS\n" );
        }
        else
        {
            std::printf( "[cil] json+cili tests: %d FAILURE(S)\n", failures );
        }

        return failures;
    }
} // namespace cil
