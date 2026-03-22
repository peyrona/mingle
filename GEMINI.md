# GEMINI.md

This file provides guidance to Gemini CLI when working with code in this repository.

## Project Overview

**Mingle** is a platform for creating IoT and automation applications using **Une** - a human-friendly declarative language that doesn't require programming knowledge. Users familiar with spreadsheets can create complex IoT applications through declarative rules-based programming.

## Role Overview
You are a Senior Software Engineer with a focus on Clean Code principles. You are meticulous, pragmatic, critical and strict persona, obsessed with best practices.

## Build Commands

### Building Individual Modules

Each module uses NetBeans Ant for building:

```bash
# Build a specific module
cd <module-name>  # e.g., lang, candi, cil, stick, glue, etc.
ant clean jar

# The compiled JAR will be in dist/<module-name>.jar
```

### Building All Modules for Release

```bash
# From project root - builds all JARs and copies to todeploy/
./release.sh
```

This script:
- Prompts for version number (format: n.n.n)
- Builds all modules: lang, network, candi, cil, controllers, updater, tape, stick, gum, glue, menu
- Copies JARs to appropriate locations in `todeploy/`
- Optionally generates Javadocs
- Optionally converts ODT documentation to PDF
- Generates catalog.json for auto-updater
- Commits to GitHub
- Creates release ZIP file

### Generate API Documentation

```bash
./generate_javadocs.sh <version>
# Output: javadocs/ directory with HTML API docs for all modules
```

### Running the Application

```bash
# Linux
cd todeploy
./run-lin.sh

# macOS
./run-mac.sh

# Windows
./run-win.ps1
```

These scripts:
- Detect/download Java 11+ if needed
- Launch menu.jar (application launcher)
- From menu, you can start Glue (IDE), Gum (web monitor), or Stick (runtime)

### Running Tests

```bash
# Run tests for a specific module
cd <module-name>
ant test

# Example: Test the expression evaluator
cd lang
ant test
# Tests are in lang/test/com/peyrona/mingle/lang/xpreval/EvalTest*.java
```

### Transpiling Une Code

```bash
# Compile Une source to JSON model
java -jar tape.jar -source <file.une> -config config.json

# This transpiles Une source code to a .model file (JSON format)
```

### Running Une Programs

```bash
# Execute a transpiled Une program
java -jar stick.jar -model <file.model> -config config.json

# Or use Glue IDE for interactive development/debugging
```

## High-Level Architecture

Mingle consists of 11 modules organized in a layered architecture:

### Core Architecture Flow

```
Une Source (.une files)
    ↓ Transpilation (TAPE + CANDI)
JSON Model (.model files)
    ↓ Execution (STICK)
Running IoT Application
```

### Module Organization

**Language Core (Foundation)**
- **LANG**: Core interfaces, expression evaluator, lexer, messaging framework, utilities
  - Defines: ICommand, ICandi, IController, IRuntime, IEventBus, IXprEval
  - Expression evaluator (XprEval) with RPN-based arithmetic/logic
  - Lexer for tokenizing Une source code
  - No dependencies on other Mingle modules

**Compilation Layer**
- **CANDI**: Compiler/transpiler for Une language
  - Transpiles Une source → JSON model
  - Parser (ParseDevice, ParseDriver, ParseScript, ParseRule)
  - Semantic checker (validates references, types)
  - Supports embedded languages (Java, Python, JavaScript)
  - Uses: LANG

- **CIL**: Command serialization/deserialization library
  - Builds/unbuilds ICommand instances from JSON
  - Default implementations of Device, Driver, Rule, Script
  - Uses: LANG

**Execution Layer**
- **STICK**: Runtime execution engine (ExEn)
  - Loads JSON models and executes them
  - EventBus for message-driven communication
  - Managers: DeviceManager, RuleManager, ScriptManager, DriverManager, NetworkManager, GridManager
  - Main entry point: `stick/src/com/peyrona/mingle/stick/Main.java`
  - Uses: LANG, CIL, CONTROLLERS, NETWORK

- **CONTROLLERS**: Device driver implementations
  - IController interface implementations for various hardware/services
  - Examples: MQTT, Modbus, HTTP, Telegram, Excel, Raspberry Pi GPIO, sensors, actuators
  - Uses: LANG

- **NETWORK**: Inter-ExEn communication
  - Socket, WebSocket support
  - Client/server for distributed ExEn instances
  - Uses: LANG

**Tools Layer**
- **TAPE**: CLI transpiler wrapper
  - Command-line interface to CANDI
  - Transpiles .une → .model
  - Uses: CANDI, LANG

- **GLUE**: Integrated Development Environment
  - GUI for editing, transpiling, debugging Une programs
  - Syntax highlighting, error reporting
  - ExEn monitor with real-time device/rule inspection
  - Uses: TAPE, STICK, GUM, LANG

- **GUM**: Web-based monitoring server
  - HTTP/WebSocket server for remote monitoring
  - REST-like services for ExEn control
  - Uses: STICK (via NETWORK), LANG

- **MENU**: Application launcher
  - Cross-platform startup menu
  - User-friendly selector for Glue/Gum/Stick
  - Platform-specific launchers

**Utilities**
- **UPDATER**: Auto-update system using GitHub API and hash verification

### Key Architectural Patterns

**Interface-Based Plugin System**
- Core interfaces in LANG define contracts
- Implementations loaded dynamically via reflection
- Configured in `config.json`

**Message-Driven Architecture**
- EventBus dispatches all inter-component messages
- Message types: MsgDeviceChanged, MsgChangeActuator, MsgExecute, MsgReadDevice
- Rules trigger on device changes → actions executed
- Supports delayed/periodic messages

**Manager Pattern**
- Stick uses specialized managers for each command type
- DeviceManager: CRUD and state management
- RuleManager: Condition evaluation and triggering
- ScriptManager: Embedded code execution
- DriverManager: Driver lifecycle and scheduling
- NetworkManager: Inter-ExEn communication
- GridManager: Distributed execution

**Configuration-Driven**
- Single `config.json` controls all module behavior
- Supports Une-style comments in JSON
- Precedence: CLI args > Environment vars > config.json

### Une Language Execution Flow

1. **Driver reads device** → Posts MsgReadDevice/MsgDeviceChanged
2. **EventBus dispatches** → DeviceManager updates state
3. **DeviceManager** → Posts MsgDeviceChanged if value changed
4. **RuleManager receives** → Evaluates WHEN clauses
5. **Rule triggers** → Posts MsgExecute for THEN actions
6. **Action execution**:
   - Change Actuator: Posts MsgChangeActuator → Driver writes to device
   - Execute Script: ScriptManager runs embedded code (Java/Python/JS)

### Critical Implementation Files

**Compilation Pipeline**
- `candi/src/com/peyrona/mingle/candi/unec/transpiler/Transpiler.java` - Main transpilation orchestrator
- `candi/src/com/peyrona/mingle/candi/unec/parser/Parse*.java` - Command parsers
- `lang/src/com/peyrona/mingle/lang/lexer/Lexer.java` - Tokenizer
- `lang/src/com/peyrona/mingle/lang/lexer/Language.java` - Une syntax rules

**Runtime Execution**
- `stick/src/com/peyrona/mingle/stick/Stick.java` - Main runtime engine
- `stick/src/com/peyrona/mingle/stick/EventBus.java` - Message dispatcher
- `stick/src/com/peyrona/mingle/stick/*Manager.java` - Component managers
- `lang/src/com/peyrona/mingle/lang/xpreval/NAXE.java` - Expression evaluator

**Command Framework**
- `lang/src/com/peyrona/mingle/lang/interfaces/commands/ICommand.java` - Base interface
- `cil/src/com/peyrona/mingle/cil/*.java` - Default implementations
- `controllers/src/com/peyrona/mingle/controllers/` - Device drivers

**Configuration**
- `lang/src/com/peyrona/mingle/lang/Config.java` - Configuration loader
- `todeploy/config.json` - Main configuration file

### Module Dependencies

```
GLUE, GUM, MENU, TAPE → use multiple modules below
    ↓
STICK (Runtime) → uses LANG, CIL, CONTROLLERS, NETWORK
CANDI (Compiler) → uses LANG
CONTROLLERS, NETWORK, CIL → use LANG
    ↓
LANG (Core) → no Mingle dependencies
```

### Une Language Structure

Une has 6 command types:

```une
DEVICE <name>             # Sensor/Actuator
  DRIVER <driver-name>    # What updates it
    CONFIG ...            # Configuration

DRIVER <name>             # Driver definition
  CONFIG ...

RULE                      # Trigger-action
  WHEN <condition>        # Condition (boolean expression)
  THEN <action>           # Action (change device or execute script)

SCRIPT <name>             # Embedded code
  LANGUAGE <lang>         # Java, Python, JavaScript, Une
  CALL <function>
  { ... code ... }

INCLUDE "<file>"          # File inclusion

USE <symbol> AS <new>     # Syntax customization
```

### Expression Evaluator (XprEval)

- Uses RPN (Reverse Polish Notation) for parsing/lexing and AST (Abstract Symbolic Tree) for evaluation
- NAXE (Not Another eXpression Evaluator) implementation
- Supports: arithmetic, boolean logic, string operations, date/time, lists, dictionaries (pairs)
- Functions: `floor()`, `ceil()`, `min()`, `max()`, `sin()`, `cos()`, `upper()`, `lower()`, etc.
- Used in: WHEN clauses, THEN actions, CONFIG values

### Adding New Features

**New Device Type**
1. Implement IController in controllers/ module
2. Register in Une DRIVER command
3. ControllerManager instantiates via reflection from config.json
4. Fire MsgDeviceChanged when state changes

**New Language Support**
1. Implement ICandi.ILanguage interface
2. Register in config.json under `exen.languages`
3. Implement `prepare()` and `execute()` methods
4. Reference in SCRIPT LANGUAGE clause

**New Function/Operator**
1. Add to `lang/src/com/peyrona/mingle/lang/xpreval/functions/` or `operators/`
2. Or use USE command in Une to alias existing functions

## Important Notes

- **Java Version**: Requires Java 11 or above
- **Thread Safety**: Concurrent collections are extensively used; device read/write must be async
- **Message Handling**: Never reuse message instances (race conditions)
- **Reflection**: Heavily used for plugin loading (drivers, languages, functions)
- **Configuration**: Everything customizable via config.json
- **Standard Library**: Include files in `todeploy/include/` (e.g., `{*home.inc*}`, `{*mqtt.inc*}`)
- **Examples**: Progressive examples in `todeploy/examples/` (01.clock.une through 80+)
- **Documentation**: PDFs in `docs/`: `Une_language.pdf`, `Mingle_Standard_Platform.pdf`, `Une_reference_sheet.pdf`

## Testing Strategy

- Unit tests: Individual components (Lexer, Parser, XprEval)
- Integration tests: Compilation pipeline
- Runtime tests: Example Une programs
- Test framework: JUnit 5
- Main test suites:
  - `lang/test/com/peyrona/mingle/lang/xpreval/EvalTest*.java` - Expression evaluator
  - `lang/test/com/peyrona/mingle/lang/japi/CronTest.java` - Cron utilities
  - Other modules have corresponding test/ directories

## Key Conventions

- Package naming: `com.peyrona.mingle.{module}.{submodule}`
- Interfaces: Prefix with `I` (IRuntime, ICommand, IController)
- Classes: PascalCase
- All files: Apache 2.0 license header
- Module structure: src/, test/, lib/, dist/, build/, nbproject/

## Technology Stack

* **Backend:** Java 11
* **Frontend:** HTML5, CSS3, and ES6+ (JavaScript)
* **IDE:** NetBeans (version 18 or newer) is required to manage all projects.
* **Version Control:** The code repository is hosted on GitHub.

## Coding Standards & Contribution Rules

When contributing new code, you must adhere to the following standards:

1. **Coding Style:** All new code **must** match the formatting and coding style of the pre-existing files.
2. **Documentation:** All new classes, methods, and functions must be thoroughly documented. And keep documentation up to date with code changes.
3. **Language:** All code, documentation, and comments **must** be written in English.
4. **Clarity over Speed:** Prioritize code clarity, readability, and maintainability over premature optimization or raw execution speed.
5. **Browser Compatibility:** Use HTML5, CSS3 and JavaScript ES6. Must be compatible with the recent/modern versions of major web browsers (e.g., Chrome, Firefox, Safari, and Edge).
6. **Compiling and Git:** Never compile, never build and never commit to GitHub unless it is clearly request. If you need at certain point to compile to validate code, ask for permission first.
