#!/bin/bash

set -euo pipefail

# ---------------------------------------------------------------------------------------------
# Improved Mingle applications launcher for Linux systems
# ---------------------------------------------------------------------------------------------

# Terminal colors
readonly NORM="\033[0m"
readonly BLUE="\033[1;34m"
readonly YELLOW="\033[1;33m"
readonly RED="\033[1;31m"
readonly GREY="\033[0;90m"

# Global variables
SCRIPT_DIR=""
JAVA_CMD=""
CLASSPATH=""

# ---------------------------------------------------------------------------------------------
# Utility Functions
# ---------------------------------------------------------------------------------------------

log()
{
    local level=$1
    shift
    echo -e "[$(date +'%Y-%m-%d %H:%M:%S')] [$level] $*" >&2
}

die()
{
    log "ERROR" "$*"
    exit 1
}

check_superuser()
{
    if [[ $EUID -ne 0 ]]; then
        die "$1"
    fi
}

press_any_key()
{
    read -r -n 1 -s -p "Press any key to continue..."
    echo
}

get_ip_address()
{
    hostname -I | awk '{print $1}'
}

get_service_file_path()
{
    local service_name=$1
    echo "/etc/systemd/system/mingle_${service_name}.service"
}

get_log_file_path()
{
    local component=$1
    local log_type=${2:-out}
    echo "${SCRIPT_DIR}/log/${component}.${log_type}.txt"
}

init_environment()
{
    # Get script directory
    SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)" || \
        die "Failed to determine script directory"

    cd "${SCRIPT_DIR}" || die "Failed to change to script directory"

    # Creates the log dir if it does not exist
    if [ ! -d "${SCRIPT_DIR}/log" ]; then
        mkdir "${SCRIPT_DIR}/log"

        if [[ $EUID == 0 ]]; then             # If current user is root,
            chmod a+rw "${SCRIPT_DIR}/log"    # change permissions to read and write for all OS users
        fi
    fi

    # Include all JARs in current dir and in 'lib' dir and subdirs, excluding 'controllers' dir
    CLASSPATH=$(find . lib -name "*.jar" -not -path "*/controllers/*" 2>/dev/null | tr '\n' ':' || true)

    if [[ -z "$CLASSPATH" ]]; then
        log "WARN" "No JAR files found in current directory or lib/"
    fi

    export CLASSPATH
}

find_java()
{
    local jdk_path

    # Look for JDK/JRE in current directory
    for pattern in "*jdk*" "*jre*"; do
        while IFS= read -r -d '' jdk_path; do
            if [[ -x "$jdk_path/bin/java" ]]; then
                export JAVA_HOME="$jdk_path"
                export PATH="$jdk_path/bin:$PATH"
                JAVA_CMD="$jdk_path/bin/java"
                return 0
            fi
        done < <(find . -maxdepth 1 -type d -name "$pattern" -print0 2>/dev/null || true)
    done

    # Check JAVA_HOME
    if [[ -n "${JAVA_HOME:-}" && -x "$JAVA_HOME/bin/java" ]]; then
        JAVA_CMD="$JAVA_HOME/bin/java"
        return 0
    fi

    # Check if java is in PATH
    if command -v java >/dev/null 2>&1; then
        JAVA_CMD="java"
        return 0
    fi

    # Java not found - offer to download
    echo "Java was not found. I can download it and make it ready to be used by Mingle."
    read -r -p "Do you want to do it now? [Y/n] " response

    if [[ -z "$response" || "$response" =~ ^[Yy]$ ]]; then
        echo "Running Java setup script..."
        if ./etc/get-java-linux.sh; then
            echo "Java setup completed. Please run this script again."
        else
            echo "Java setup failed: check ./etc/get-java-linux.sh"
        fi
    else
        echo "Java setup cancelled."
    fi
    echo "Press any key to exit..."
    read -r -s -n 1
    exit 1
}

select_file()
{
    local prompt=$1
    local extens=$2
    local selected_file=""

    if command -v zenity >/dev/null 2>&1; then
        selected_file=$(zenity --file-selection --title="$prompt" --file-filter="$extens" 2>/dev/null || true)
    elif command -v dialog >/dev/null 2>&1; then
        selected_file=$(dialog --stdout --title "$prompt   [Arrows] [Space] [Tab]" --fselect ./ 22 78 || true)
    else
        read -r -p "$prompt - Enter a '$extens' file: " selected_file
    fi

    echo "$selected_file"
}

add_extension()
{
    local filepath=$1
    local extension=$2
    local basename current_ext

    basename=$(basename "$filepath")
    current_ext="${basename##*.}"

    if [[ "$basename" == "$current_ext" || "$current_ext" != "$extension" ]]; then
        echo "${filepath}.${extension}"
    else
        echo "$filepath"
    fi
}

# ---------------------------------------------------------------------------------------------
# Systemd Service Functions
# ---------------------------------------------------------------------------------------------

create_service_file()
{
    local service_name=$1
    local exec_start=$2
    local service_file
    service_file=$(get_service_file_path "$service_name")

    # Calculate JAVA_HOME properly
    local java_home_dir=""
    if [[ "$JAVA_CMD" == */bin/java ]]; then
        java_home_dir=$(dirname "$(dirname "$JAVA_CMD")")
        # Verify it's actually a java home
        if [[ ! -d "$java_home_dir/bin" || ! -d "$java_home_dir/lib" ]]; then
            java_home_dir=""
        fi
    fi

    # Create service file if it doesn't exist
    if [[ ! -f "$service_file" ]]; then
        local log_file
        log_file=$(get_log_file_path "$service_name" "service")

        cat > "$service_file" << EOF
[Unit]
Description=Mingle ${service_name^}
After=network.target
StartLimitBurst=5
StartLimitIntervalSec=300

[Service]
Type=simple
WorkingDirectory=${SCRIPT_DIR}
Environment="CLASSPATH=${CLASSPATH}"
ExecStart=${exec_start}
Restart=on-failure
RestartSec=50
StandardOutput=append:${log_file}
StandardError=append:${log_file}

[Install]
WantedBy=multi-user.target
EOF

        # Notes (about [Unit] and [Service] config):
        #       * StartLimitBurst      : This sets the maximum number of restarts that are allowed within a specific time interval.
        #       * StartLimitIntervalSec: This defines the time interval (in seconds) for the restart limit.
        #       * RestartSec           : Seconds to wait between 2 conscutives re-starts
        #
        # So: there will be 5 tries to start the service during a max time of 300 secs (5 mins) trying every 50 secs

        # Add JAVA_HOME if detected
        if [[ -n "$java_home_dir" ]]; then
            sed -i "/^Environment=\"CLASSPATH/a Environment=\"JAVA_HOME=${java_home_dir}\"" "$service_file"
        fi

        systemctl daemon-reload || die "Failed to reload systemd daemon"
        systemctl enable "mingle_${service_name}.service" || die "Failed to enable service"

        log "INFO" "systemd service created: $service_file"
        press_any_key
        return 0
    else
        log "INFO" "Service already exists: $service_file"
        press_any_key
        return 1
    fi
}

manage_service()
{
    echo "Wait ..."

    local service_name=$1
    local action=${2:-start}

    case "$action" in
        start)
            systemctl "start" "mingle_${service_name}.service" || die "Failed to start service 'mingle_${service_name}'"
            ;;
        stop|restart)
            systemctl "$action" "mingle_${service_name}.service" || die "Failed to $action service 'mingle_${service_name}'"
            systemctl status "mingle_${service_name}.service" --no-pager || true
            press_any_key
            ;;
        status)
            systemctl status "mingle_${service_name}.service" --no-pager || true
            press_any_key
            ;;
        *)
            die "Invalid systemd action: $action (use: start, stop, restart, status)"
            ;;
    esac
}

is_service_exists()
{
    local service_name=$1
    local service_file
    service_file=$(get_service_file_path "$service_name")
    [[ -f "$service_file" ]]
}

is_service_active()
{
    local service_name=$1
    systemctl is-active --quiet "mingle_${service_name}.service" 2>/dev/null
}

systemd_create_service()
{
    local service_name=$1
    check_superuser "Service creation requires root privileges"

    read -p "Create service for '$service_name'? [y/N]: " confirm
    if [[ ! "$confirm" =~ ^[Yy]$ ]]; then
        log "INFO" "Service creation cancelled by user"
        return
    fi

    if [[ "$service_name" == "gum" ]]; then
        local exec_start="${SCRIPT_DIR}/menu.sh gum service"
        create_service_file "gum" "$exec_start"
    elif [[ "$service_name" == "stick" ]]; then
        local model=""
        local exec_start=""

        # Prompt for model file (optional)
        model=$(select_file "Select model for Stick service (optional)" "*.model")

        if [[ -n "$model" ]]; then
            if [[ "$model" =~ [\'\"] ]]; then
                log "ERROR" "Model filename cannot contain quotes."
                press_any_key
                return
            fi

            model=$(add_extension "$model" "model")
            # Verify model file exists
            if [[ ! -f "$model" ]]; then
                log "ERROR" "Model file not found: $model"
                press_any_key
                return
            fi
            # Create service with model
            exec_start="${SCRIPT_DIR}/menu.sh stick service \"$model\""
        else
            # Create service without model
            exec_start="${SCRIPT_DIR}/menu.sh stick service"
        fi

        create_service_file "stick" "$exec_start"
    fi
}

systemd_delete_service()
{
    local service_name=$1
    local service_file
    service_file=$(get_service_file_path "$service_name")

    check_superuser "Service deletion requires root privileges"

    if [[ ! -f "$service_file" ]]; then
        log "INFO" "Service file does not exist: $service_file"
        press_any_key
        return
    fi

    read -p "Delete service '$service_name'? [y/N]: " confirm
    if [[ ! "$confirm" =~ ^[Yy]$ ]]; then
        log "INFO" "Service deletion cancelled by user"
        return
    fi

    log "INFO" "Stopping and disabling service: mingle_${service_name}.service"
    systemctl stop "mingle_${service_name}.service" >/dev/null 2>&1 || true
    systemctl disable "mingle_${service_name}.service" >/dev/null 2>&1 || true

    log "INFO" "Deleting service file: $service_file"
    rm -f "$service_file" || die "Failed to delete service file"

    log "INFO" "Reloading systemd daemon"
    systemctl daemon-reload || die "Failed to reload systemd daemon"

    log "INFO" "Service mingle_${service_name} deleted successfully."
    press_any_key
}

run_systemd_manager()
{
    check_superuser "Service management requires root privileges"
    local CURRENT_SERVICE="stick"  # Start with stick (most used)

    while true; do
        show_service_menu "$CURRENT_SERVICE"
        read -r -s -n 1 key

        if [[ "$key" == "0" ]]; then    # '0' key toggles between Stick and Gum
            if [[ "$CURRENT_SERVICE" == "gum" ]]; then
                CURRENT_SERVICE="stick"
            else
                CURRENT_SERVICE="gum"
            fi
        else
            local opt=${key,,}
            local service_exists=false
            local service_active=false

            if is_service_exists "$CURRENT_SERVICE"; then
                service_exists=true
            fi

            if $service_exists && is_service_active "$CURRENT_SERVICE"; then
                service_active=true
            fi

            case $opt in
                c)
                    if ! $service_exists; then
                        systemd_create_service "$CURRENT_SERVICE"
                    fi
                    ;;
                d)
                    if $service_exists; then
                        systemd_delete_service "$CURRENT_SERVICE"
                    fi
                    ;;
                s)
                    if $service_exists && ! $service_active; then
                        manage_service "$CURRENT_SERVICE" "start"
                    fi
                    ;;
                t)
                    if $service_exists && $service_active; then
                        manage_service "$CURRENT_SERVICE" "stop"
                    fi
                    ;;
                r)
                    if $service_exists && $service_active; then
                        manage_service "$CURRENT_SERVICE" "restart"
                    fi
                    ;;
                u)
                    if $service_exists; then
                        manage_service "$CURRENT_SERVICE" "status"
                    fi
                    ;;
                x)
                    break
                    ;; # Exit this manager, back to main menu
                *)
                    log "ERROR" "Invalid option: $opt"
                    press_any_key
                    ;;
            esac
        fi
    done
}

show_service_menu()
{
    local current_service=$1

    clear
    echo -e "=============================================="
    echo -e "          ${BLUE}::: Service Manager :::${NORM}"
    echo -e "=============================================="

    if [[ "$current_service" == "stick" ]]; then
        echo -e "  Target: [${RED}Stick${NORM}]   [Gum]  (use ${BLUE}0${NORM} to switch)"
    else
        echo -e "  Target: [Stick]   [${RED}Gum${NORM}]  (use ${BLUE}0${NORM} to switch)"
    fi

    echo -e "----------------------------------------------"

    local service_exists=false
    local service_active=false

    if is_service_exists "$current_service"; then
        service_exists=true
    fi

    if $service_exists && is_service_active "$current_service"; then
        service_active=true
    fi

    if $service_exists; then
        echo -e "  ${GREY}Create service${NORM}  |  ${YELLOW}D${NORM}elete service"
    else
        echo -e "  ${YELLOW}C${NORM}reate service  |  ${GREY}Delete service${NORM}"
    fi

    echo -e "----------------------------------------------"

    if ! $service_exists; then
        echo -e "  ${GREY}Start  |  Stop  |  Restart  |  Status${NORM}"
    else
        if $service_active; then
            echo -e "  ${GREY}Start${NORM}  |  S${YELLOW}t${NORM}op  |  ${YELLOW}R${NORM}estart  |  Stat${YELLOW}u${NORM}s"
        else
            echo -e "  ${YELLOW}S${NORM}tart  |  ${GREY}Stop${NORM}  |  ${GREY}Restart${NORM}  |  Stat${YELLOW}u${NORM}s"
        fi
    fi

    echo -e "----------------------------------------------"
    echo -e "${YELLOW}X${NORM} Exit to main menu"
    echo -e "=============================================="
}

# ---------------------------------------------------------------------------------------------
# Help and Info Functions
# ---------------------------------------------------------------------------------------------

show_help()
{
    cat << 'EOF'
================================================================================
                          MINGLE APPLICATION LAUNCHER
================================================================================

This script is the main entry point for running and managing Mingle applications
on Linux. It offers two modes of operation: an interactive menu for ease of
use, and a direct command-line interface for scripting and automation.

--------------------------------------------------------------------------------
USAGE
--------------------------------------------------------------------------------

  ./menu.sh [COMMAND] [ARGUMENTS]

  - Run without arguments to enter the interactive menu:
    ./menu.sh

  - Run with a command for direct execution:
    ./menu.sh <app_name> [arguments]
    sudo ./menu.sh service <service_name> [arguments]

--------------------------------------------------------------------------------
COMMANDS
--------------------------------------------------------------------------------

=== Launching Applications ===

  glue
    Launches Glue, the Mission Control IDE.
    Example: ./menu.sh glue

  gum
    Launches Gum, the dashboard and file server.
    Example: ./menu.sh gum

  stick [mode] [model_file]
    Launches Stick, the execution engine.
    'mode' can be: default, lowram, debug, profile, resident. Defaults to 'default'.
    [model_file] is optional. If omitted, Stick will start without a specific
    model file, relying on its internal configuration (e.g., 'config.json').
    Examples:
      ./menu.sh stick                            # Default mode, no model file specified
      ./menu.sh stick my_model.model             # Default mode with 'my_model.model'
      ./menu.sh stick debug my_model.model       # Debug mode with 'my_model.model'
      ./menu.sh stick lowram                     # Low memory mode, no model file specified

  tape [une_file] [args...]
    Launches Tape, the UNE language transpiler.
    If run with no arguments, a file selection dialog will appear.
    Example: ./menu.sh tape my_script.une

=== Managing Services (systemd) ===

Requires root privileges (sudo). These commands create a systemd service on the
first run and then manage it.

  service gum [action]
    Manages the Gum service.
    'action' can be: start, stop, restart, status. Defaults to 'start'.
    Example (start): sudo ./menu.sh service gum
    Example (stop):  sudo ./menu.sh service gum stop

  service stick [model_file] [action]
    Manages the Stick service.
    'action' can be: start, stop, restart, status. Defaults to 'start'.

    - To create/run with a model:
      sudo ./menu.sh service stick my_model.model

    - To create/run without a model:
      sudo ./menu.sh service stick

    - To stop or restart the service:
      sudo ./menu.sh service stick stop
      sudo ./menu.sh service stick restart

    IMPORTANT: The service's model file is set only when the service is first
    created. To change the model for an existing service, you must delete and
    recreate it via the 'Service Manager' (option 'E'). Alternatively, create the
    service without a model file and specify the model in 'config.json', which
    Stick loads by default.

--------------------------------------------------------------------------------
INTERACTIVE MENU
--------------------------------------------------------------------------------

Run './menu.sh' to access the main menu for guided operations:

  G - Launch Glue (Mission Control IDE)
  U - Launch Gum (Dashboards/File Server)
  S - Launch Stick (default mode)
  T - Launch Stick (low memory mode)
  I - Launch Stick (debug mode, port 8800)
  C - Launch Stick (profiling mode, VisualVM)
  K - Launch Stick (resident mode, nohup)
  A - Launch Tape (transpiler)
  L - List and kill running Mingle JVM processes
  O - Show system and Java information
  E - Open the Service Manager (requires root)
  H - Show this help screen
  X - Exit

The Service Manager provides a menu to create, delete, start, stop, and
check the status of 'gum' and 'stick' services.

--------------------------------------------------------------------------------
FILES & LOCATIONS
--------------------------------------------------------------------------------

- Script Directory: All paths are relative to the script's location.
- JARs: Located in './' and './lib/'.
- Logs: Stored in './log/'. Application output is in '*.out.txt', and
        service logs are in '*.service.txt'.
- Service Files: Created at '/etc/systemd/system/mingle_*.service'.

EOF
    press_any_key
}

show_system_info()
{
    echo "======================================================================"
    echo "JAVA"
    echo "======================================================================"
    [[ -n "${JAVA_HOME:-}" ]] && echo "JAVA_HOME=$JAVA_HOME"
    "$JAVA_CMD" -version 2>&1
    echo ""
    echo "CLASSPATH=$CLASSPATH"
    echo ""

    echo "======================================================================"
    echo "SYSTEMD SERVICES"
    echo "======================================================================"
    systemctl status mingle_stick.service --no-pager 2>/dev/null || echo "Stick service not configured"
    echo ""
    systemctl status mingle_gum.service --no-pager 2>/dev/null || echo "Gum service not configured"
    echo ""

    echo "======================================================================"
    echo "SYSTEM"
    echo "======================================================================"
    if command -v lscpu >/dev/null 2>&1; then
        lscpu | grep -E "Architecture|Model name|CPU\(s\):"
    else
        uname -m
    fi
    echo ""

    if command -v free >/dev/null 2>&1; then
        free -h
    fi
    echo ""

    press_any_key
}

# ---------------------------------------------------------------------------------------------
# Application Functions
# ---------------------------------------------------------------------------------------------

run_glue()
{
    local log_file
    log_file=$(get_log_file_path "glue")

    nohup "$JAVA_CMD" \
            -javaagent:lib/lang.jar \
            com.peyrona.mingle.glue.Main "$@" \
            > "$log_file" 2>&1 &

    log "INFO" "Glue started (PID: $!)"
}

run_gum()
{
    local mode=${1:-normal}

    if [[ "$mode" == "service" ]]; then
        shift # consume 'service'
        # Run in foreground for systemd
        exec "$JAVA_CMD" \
                -javaagent:lib/lang.jar \
                com.peyrona.mingle.gum.Main "$@"
    else
        # Run in background
        local log_file
        log_file=$(get_log_file_path "gum")

        nohup "$JAVA_CMD" \
                -javaagent:lib/lang.jar \
                com.peyrona.mingle.gum.Main "$@" \
                > "$log_file" 2>&1 &

        local ip_addr
        ip_addr=$(get_ip_address)
        echo "Open a web browser: http://${ip_addr}:8080/gum"
        echo "(Remember: it is recommended to have one or more ExEns running)"
        press_any_key
    fi
}

run_stick()
{
    # CLI mode parsing
    local mode="default"
    local file=""
    local extra_args=()

    if [[ -n "${1:-}" && "${1}" =~ ^(default|lowram|debug|profile|resident|service)$ ]]; then
        mode="$1"
        shift
    fi

    if [[ -n "${1:-}" && ! "$1" =~ ^- ]]; then
        file="$1"
        shift
    fi
    extra_args=("$@")

    # If no file and no extra args were provided, prompt for a file.
    # This handles interactive calls like `run_stick debug` from the menu.
    if [[ -z "$file" && ${#extra_args[@]} -eq 0 && "$mode" != "service" ]]; then
         local selected_file
         selected_file=$(select_file "Stick [$mode mode]" "*.model")
         clear
         if [[ -n "$selected_file" ]]; then
            file="$selected_file"
         fi
    fi

    local final_args=()
    if [[ -n "$file" ]]; then
        final_args+=("$(add_extension "$file" "model")")
    fi
    final_args+=("${extra_args[@]}")

    case "$mode" in
        default)
            "$JAVA_CMD" \
                -javaagent:lib/lang.jar \
                com.peyrona.mingle.stick.Main "${final_args[@]}"
            ;;
        lowram)
            "$JAVA_CMD" \
                -javaagent:lib/lang.jar \
                com.peyrona.mingle.stick.Main \
                -XX:+UseStringDeduplication \
                -XX:+UseCompressedOops \
                "${final_args[@]}"
            ;;
        debug)
            "$JAVA_CMD" \
                -javaagent:lib/lang.jar \
                com.peyrona.mingle.stick.Main \
                -agentlib:jdwp=transport=dt_socket,server=y,suspend=y,address=0.0.0.0:8800 \
                "${final_args[@]}"
            ;;
        profile)
            local ip_addr=$(get_ip_address)
            echo "Remote JMX: IP=$ip_addr, port=1099, ssl=false, authenticate=false"
            press_any_key

            "$JAVA_CMD" \
                -javaagent:lib/lang.jar \
                com.peyrona.mingle.stick.Main \
                -server \
                -Dcom.sun.management.jmxremote \
                -Dcom.sun.management.jmxremote.port=1099 \
                -Dcom.sun.management.jmxremote.ssl=false \
                -Dcom.sun.management.jmxremote.authenticate=false \
                -Dcom.sun.management.jmxremote.local.only=false \
                -Djava.rmi.server.hostname="${ip_addr}" \
                -XX:+HeapDumpOnOutOfMemoryError \
                "${final_args[@]}"
            ;;
        resident)
            local log_file=$(get_log_file_path "stick")

            nohup "$JAVA_CMD" \
                    -javaagent:lib/lang.jar \
                    com.peyrona.mingle.stick.Main "${final_args[@]}" \
                    > "$log_file" 2>&1 &
            log "INFO" "Stick started resident (PID: $!)"
            ;;
        service)
            # Invokes stick with the model file if passed or with no args if not passed
            exec "$JAVA_CMD" \
                    -javaagent:lib/lang.jar \
                    com.peyrona.mingle.stick.Main "${final_args[@]}"
            ;;
    esac

    [[ "$mode" != "resident" && "$mode" != "service" ]] && press_any_key
}

run_tape()
{
    # If no arguments are passed at all, enter interactive mode.
    if [[ $# -eq 0 ]]; then
        local file
        file=$(select_file "Tape (*.une)" "*.une")
        clear
        if [[ -z "$file" ]]; then return; fi # User cancelled

        file=$(add_extension "$file" "une")
        "$JAVA_CMD" \
            -javaagent:lib/lang.jar \
            com.peyrona.mingle.tape.Main "$file"
        press_any_key
        return
    fi

    # Otherwise, we are in CLI mode.
    local file=""
    local extra_args=()
    # If first arg is not an option, it's a file.
    if [[ -n "${1:-}" && ! "$1" =~ ^- ]]; then
        file="$1"
        shift
    fi
    extra_args=("$@")

    local args_to_java=()
    if [[ -n "$file" ]]; then
        args_to_java+=("$(add_extension "$file" "une")")
    fi
    args_to_java+=("${extra_args[@]}")

    "$JAVA_CMD" \
        -javaagent:lib/lang.jar \
        com.peyrona.mingle.tape.Main "${args_to_java[@]}"

    press_any_key
}

list_processes()
{
    local java_pids
    java_pids=$(pgrep -f 'java.*mingle' || true)

    if [[ -z "$java_pids" ]]; then
        echo "No Mingle JVM instances currently running"
        press_any_key
        return
    fi

    echo "Running Mingle processes:"
    echo ""

    local cmdline jar

    for pid in $java_pids; do
        cmdline=$(tr '\0' ' ' < "/proc/$pid/cmdline" 2>/dev/null || echo "N/A")
        jar=$(echo "$cmdline" | grep -oE 'mingle\.[a-zA-Z0-9_]+' | head -1 || echo "unknown")
        echo "PID: $pid, Component: $jar"
    done
    echo ""

    read -r -p "Enter PID to kill (empty to return): " pid

    if [[ -n "$pid" ]]; then
        echo -e "Kill process forcefully? ${YELLOW}Y${NORM} = SIGKILL, ${YELLOW}N${NORM} = SIGTERM (graceful)"
        read -r -s -n 1 level

        case $level in
            [Yy]) level=9 ;;    # SIGKILL
            *)    level=15 ;;   # SIGTERM
        esac

        if kill -"$level" "$pid" 2>/dev/null; then
            echo ""
            echo "Process $pid killed with signal $level"
        else
            echo ""
            echo "Failed to kill process $pid (may require sudo)"
        fi

        echo ""
        press_any_key
    fi
}

# ---------------------------------------------------------------------------------------------
# Main Menu
# ---------------------------------------------------------------------------------------------

show_main_menu()
{
    clear
    echo -e "=============================================="
    echo -e "            ${BLUE}::: Mingle Menu :::${NORM}"
    echo -e "=============================================="
    echo -e " ${YELLOW}G${NORM}lue      : Mission Control (IDE)"
    echo -e " G${YELLOW}u${NORM}m       : Dashboards and file-server"
    echo -e " ${YELLOW}S${NORM}tick     : ExEn (default)"
    echo -e " S${YELLOW}t${NORM}ick     :     Low memory mode"
    echo -e " St${YELLOW}i${NORM}ck     :     Debug mode (JPDA port 8800)"
    echo -e " Sti${YELLOW}c${NORM}k     :     Profiling mode (VisualVM)"
    echo -e " Stic${YELLOW}k${NORM}     :     Resident mode (nohup)"
    echo -e " T${YELLOW}a${NORM}pe      : Transpiler"
    echo -e " ${YELLOW}L${NORM}ist/Kill : Manage JVM processes"
    echo -e " Inf${YELLOW}o${NORM}      : System information"
    echo -e " S${YELLOW}e${NORM}rvices  : Service Manager (root required)"
    echo -e "----------------------------------------------"
    echo -e " ${YELLOW}H${NORM}elp"
    echo -e " E${YELLOW}x${NORM}it"
    echo -e "=============================================="
}

# ---------------------------------------------------------------------------------------------
# Main Program
# ---------------------------------------------------------------------------------------------

main()
{
    [[ "$(uname)" == "Linux" ]] || die "This script only runs on Linux"

    init_environment
    find_java

    # Handle command-line arguments
    if [[ $# -gt 0 ]]; then
        local cmd=$1
        shift

        case "${cmd,,}" in
            glue) run_glue "$@" ;;
            gum) run_gum "$@" ;;
            stick) run_stick "$@" ;;
            tape) run_tape "$@" ;;
            service)
                check_superuser "Service management requires root privileges"
                local service_type=${1:-}
                [[ -z "$service_type" ]] && die "Service type 'gum' or 'stick' required."
                shift || true

                local model_file=""
                local action=""

                # Handle stick's optional model file
                if [[ "$service_type" == "stick" ]]; then
                    # If the next arg is NOT an action, it's a model file
                    if [[ -n "$1" && ! "$1" =~ ^(start|stop|restart|status)$ ]]; then
                        model_file="$1"
                        if [[ "$model_file" =~ [\'\"] ]]; then
                            die "Model filename cannot contain quotes."
                        fi
                        shift || true
                    fi
                fi

                # The next arg (if it exists) must be an action
                if [[ -n "$1" ]]; then
                    if [[ "$1" =~ ^(start|stop|restart|status)$ ]]; then
                        action="$1"
                        shift || true
                    else
                        die "Invalid action: '$1'. Use 'start', 'stop', 'restart', or 'status'."
                    fi
                fi

                # Any remaining args are invalid
                if [[ -n "$1" ]]; then
                    die "Too many arguments for service command."
                fi

                # Default action if not specified
                [[ -z "$action" ]] && action="start"

                case "$service_type" in
                    gum)
                        if [[ -n "$model_file" ]]; then
                            die "The 'gum' service does not take a model file."
                        fi
                        create_service_file "gum" "${SCRIPT_DIR}/menu.sh gum service"
                        manage_service "gum" "$action"
                        ;;
                    stick)
                        create_service_file "stick" "${SCRIPT_DIR}/menu.sh stick service \"$model_file\""
                        manage_service "stick" "$action"
                        ;;
                    *)
                        die "Invalid service type: '$service_type' (use: gum, stick)"
                        ;;
                esac
                ;;
            help|--help|-h) show_help ;;
            *) die "Unknown command: $cmd" ;;
        esac
        exit 0
    fi

    # Interactive menu mode
    while true; do
        show_main_menu
        read -r -s -n 1 -p "Select option: " opt
        clear

        case "${opt,,}" in
            h) show_help ;;
            x) exit 0 ;;
            g) run_glue ;;
            u) run_gum ;;
            s) run_stick "default" ;;
            t) run_stick "lowram" ;;
            i) run_stick "debug" ;;
            c) run_stick "profile" ;;
            k) run_stick "resident" ;;
            a) run_tape ;;
            l) list_processes ;;
            o) show_system_info ;;
            e) run_systemd_manager ;;
            *)
                log "ERROR" "Invalid option: $opt"
                press_any_key
                ;;
        esac
    done
}

main "$@"