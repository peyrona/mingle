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

# Global variables
SCRIPT_DIR=""
JAVA_CMD=""
CLASSPATH=""
SYSD_MODE="start"

# ---------------------------------------------------------------------------------------------
# Utility Functions
# ---------------------------------------------------------------------------------------------

log()
{
    local level=$1
    shift
    echo -e "[$(date +'%Y-%m-%d %H:%M:%S')] [$level] $*"
    echo ""
}

die()
{
    log "ERROR" "$*"
    exit 1
}

check_superuser()
{
    if [ "$EUID" -ne 0 ]; then
        die "$1"
    fi
}

init_environment()
{
    # Get script directory
    SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" &> /dev/null && pwd)" || \
        die "Failed to determine script directory"
    cd "${SCRIPT_DIR}" || die "Failed to change to script directory"

    # The use of * for wildcards in CLASSPATH is supported by the java command
    # but not when using the CLASSPATH environment variable directly.
    # CLASSPATH="lib/*:lib/glue/*:lib/gum/*:lib/updater/*"

    # Include all JARs in current dir and in 'lib' dir and subdirs, excluding 'controllers' dir
    CLASSPATH=$(find . lib -name "*.jar" -not -path "*/controllers/*" | tr '\n' ':')
    export CLASSPATH
}

find_java()
{
    # JDK or JRE existing in same folder as Mingle, has precedence --------------

    local jdk_path

    # Look for JDK in current directory
    while IFS= read -r -d '' jdk_path; do
        if [[ -x "$jdk_path/bin/java" ]]; then
            export JAVA_HOME="$jdk_path"
            export PATH="$jdk_path/bin:$PATH"
            JAVA_CMD="$jdk_path/bin/java"
            return 0
        fi
    done < <(find . -maxdepth 1 -type d -name "*jdk*" -print0)

    # Look for JRE in current directory
    while IFS= read -r -d '' jdk_path; do
        if [[ -x "$jdk_path/bin/java" ]]; then
            export JAVA_HOME="$jdk_path"
            export PATH="$jdk_path/bin:$PATH"
            JAVA_CMD="$jdk_path/bin/java"
            return 0
        fi
    done < <(find . -maxdepth 1 -type d -name "*jre*" -print0)

    # Now look for a 'normal' installation --------------------------------------

    # Check JAVA_HOME
    if [ -n "${JAVA_HOME:-}" ] && [ -x "$JAVA_HOME/bin/java" ]; then
        JAVA_CMD="$JAVA_HOME/bin/java"
        return 0
    fi

    # Check if java is in PATH
    if command -v java >/dev/null 2>&1; then
        JAVA_CMD="java"
        return 0
    fi

    die "Java not found: it is not installed and was not found in current folder"
}

select_file()
{
    local prompt=$1
    local extens=$2
    local selected_file=""

    if command -v zenity >/dev/null 2>&1; then
        selected_file=$(zenity --file-selection --title="$prompt" --file-filter="$extens" 2>/dev/null) || true
    elif command -v dialog >/dev/null 2>&1; then
        selected_file=$(dialog --stdout --title "$prompt   [Arrows] [Space] [Tab]" --fselect ./ 22 78) || true
    else
        read -r -p "$prompt - Enter a '$extens' file: " selected_file
    fi

    echo "$selected_file"
}

add_extension()
{
    local filepath=$1
    local extension=$2
    local basename
    local current_ext

    basename=$(basename "$filepath")
    current_ext="${basename##*.}"

    if [ "$basename" = "$current_ext" ] || [ "$current_ext" != "$extension" ]; then
        echo "${filepath}.${extension}"
    else
        echo "$filepath"
    fi
}

create_service_file()
{
    local service_name=$1
    local exec_start=$2
    local exec_stop=$3
    local service_file="/etc/systemd/system/mingle_${service_name}.service"
    local _JAVA_HOME_=$(dirname "$(dirname "$JAVA_CMD")")

    if [[ ! -f "$service_file" ]]; then
        cat > "$service_file" << EOF
[Unit]
Description=Mingle_${service_name^}
After=network.target syslog.target

[Service]
WorkingDirectory=${SCRIPT_DIR}
Environment="JAVA_HOME=${_JAVA_HOME_}"
Environment="PATH=${PATH}"
Environment="CLASSPATH=${CLASSPATH}"
ExecStart=${exec_start}
ExecStop=${exec_stop}
Type=simple
Restart=on-abnormal
RestartSec=5

[Install]
WantedBy=multi-user.target
EOF

        systemctl daemon-reload
        systemctl enable mingle_${service_name}.service

        log "INFO" "systemd file for ${service_name^} created at: $service_file"
        echo "   * To check the status of the service: 'sudo systemctl status mingle_${service_name}.service'"
        echo "   * To start automatically at boot: 'sudo systemctl enable mingle_${service_name}.service'"
        echo "   * To disable automatic start: 'sudo systemctl disable mingle_${service_name}.service'"
        echo " "
        echo "Restart=on-abnormal -> Restart when the process is terminated by a signal, an operation times out, or the watchdog is triggered"
        echo " "
        read -n 1 -p "Press any key to continue..."
    fi

    systemctl start mingle_${service_name}.service
    systemctl status mingle_${service_name}.service
    read -n 1 -p "Press any key to continue..."
}


show_help()
{
    cat << EOF
===============================================================================
                        Mingle Menu Usage Guide
===============================================================================

This script is an improved launcher for Mingle applications on Linux systems.
It provides an interactive menu to run various Mingle components (e.g., Glue,
Gum, Stick, Tape) and manage them via systemd. It also supports unattended
execution with command-line arguments.


Usage
---------------------------------------------------------------------------
./menu.sh [OPTION] [ARGUMENTS]

Unattended Usage
----------------
The script supports unattended execution by passing the desired option and
any additional parameters directly from the command line.

Example to execute Stick in default mode with a model file:
    ./menu.sh stick [path/]my_house[.model]

Notes:
    * The path to model and '.model' extension are optional.
    * For Stick, the model file can also be specified in the 'config.json' file,
      eliminating the need to pass it as an argument.

Systemd Integration
-------------------
Gum and Stick can be managed as systemd services. The script allows you to
create service files and control the services directly.

* Create Service Files:
      + For Gum  :  ./menu.sh 1
      + For Stick:  ./menu.sh 2

* Start/Stop Services:
      + To start or stop Gum:
           ./menu.sh 1 start  # Start Gum service
           ./menu.sh 1 stop   # Stop Gum service
      + To start or stop Stick:
           ./menu.sh 2 start  # Start Stick service
           ./menu.sh 2 stop   # Stop Stick service

Notes:
    * If only one parameter is passed (e.g., ./menu.sh 1), the script assumes
      start as the default action.
    * Ensure you run the script as root (sudo) when creating or managing systemd
      services.
EOF
    echo ""
    read -n 1 -p "Press any key to continue..."
}

# ---------------------------------------------------------------------------------------------
# Application Functions
# ---------------------------------------------------------------------------------------------

run_glue()
{
    nohup "$JAVA_CMD" \
      # -Dsun.java2d.xrender=false \  To be used when having errors under Linux
        -javaagent:lib/lang.jar \
        com.peyrona.mingle.glue.Main \   # can not use '-jar glue.jar' because it overrides the classpath setting
        > "$SCRIPT_DIR"/glue.out.txt 2>&1 &
}

run_gum()
{
    # can not use '-jar gum.jar' because it overrides the classpath setting

    if [ $# -eq 0 ]; then                   # No arguments provided: normmal gum (TSR)
        nohup "$JAVA_CMD" \
            -javaagent:lib/lang.jar \
            com.peyrona.mingle.gum.Main \
            > "$SCRIPT_DIR"/gum.out.txt 2>&1 &

        if [ "$#" -eq 0 ]; then
            local ip_addr
            ip_addr=$(hostname -I | awk '{print $1}')
            echo "Open a web browser and go to: http://${ip_addr}:8080/gum"
            echo "(Remember: it is recommended to have one or more ExEns running)"
            read -n 1 -p "Press any key to continue..."
        fi
    else                                    # Any argument: run as a service
      exec "$JAVA_CMD" \
               -javaagent:lib/lang.jar \
               com.peyrona.mingle.gum.Main \
               > "$SCRIPT_DIR"/gum.out.txt 2>&1
    fi
}

run_stick()
{
    local mode=$1
    local file=$2

    if [ -z "$file" ] && [ "$mode" != "service" ]; then     # When mode is "service" there is no need to ask for a file
        file=$(select_file "Stick [$mode mode]" "*.model")
        clear
    fi

    if [ -n "$file" ]; then
        file=$(add_extension "$file" "model")
    fi

    case "$mode" in
        "default")
            "$JAVA_CMD" \
                -javaagent:lib/lang.jar \
                com.peyrona.mingle.stick.Main "$file"      # can not use '-jar stick.jar' because it overrides the classpath setting
            ;;
        "lowram")
            "$JAVA_CMD" \
                -javaagent:lib/lang.jar \
                -XX:+UseStringDeduplication
                -XX:+UseCompressedOops \
                com.peyrona.mingle.stick.Main "$file"
            ;;
        "debug")
            local ip_addr=$(hostname -I | awk '{print $1}')
            "$JAVA_CMD" \
                -javaagent:lib/lang.jar \
                -agentlib:jdwp=transport=dt_socket,server=y,suspend=y,address="${ip_addr}:8800" \
                com.peyrona.mingle.stick.Main "$file"
            ;;
        "profile")
            IP=$(hostname -I | awk '{print $1}')
            echo "Remote JMX will be at IP=$IP, port=1099, ssl=false, authenticate=false"
            read -n 1 -p "Press any key to start profiling..."
            "$JAVA_CMD" \
                -server \
                -javaagent:lib/lang.jar \
                -Dcom.sun.management.jmxremote \
                -Dcom.sun.management.jmxremote.port=1099 \
                -Dcom.sun.management.jmxremote.ssl=false \
                -Dcom.sun.management.jmxremote.authenticate=false \
                -Dcom.sun.management.jmxremote.local.only=false \
                -Djava.rmi.server.hostname="${IP}" \
                -XX:+HeapDumpOnOutOfMemoryError \
                -jar stick.jar "$file"
            ;;
        "resident")
            nohup "$JAVA_CMD" \
                -javaagent:lib/lang.jar \
                com.peyrona.mingle.stick.Main "$file" \
                > "$SCRIPT_DIR"/stick.out.txt 2>&1 &
            ;;
        "service")
            exec "$JAVA_CMD" \
                    -javaagent:lib/lang.jar \
                    com.peyrona.mingle.stick.Main \
                    > "$SCRIPT_DIR"/stick.out.txt 2>&1
            ;;
    esac

    read -n 1 -p "Press any key to continue..."
}

run_tape()
{
    local file=$1

    if [ -z "$file" ]; then
       file=$(select_file "Tape (*.une)" "*.une")
       clear
    fi

    if [ -n "$file" ]; then
        file=$(add_extension "$file" "une")
        "$JAVA_CMD" \
            -javaagent:lib/lang.jar \
            com.peyrona.mingle.tape "$file"    # can not use '-jar tape.jar' because it overrides the classpath setting
        read -n 1 -p "Press any key to continue..."
    fi
}

list_processes()
{
    java_pids=$(ps aux | grep -E '[j]ava.*jar' | awk '{print $2}' || true)

    if [[ -z "$java_pids" ]]; then
        echo "It looks like there are no instances of the JVM currently running"
        read -n 1 -p "Press key to continue..."
    else
        for pid in $java_pids; do
            cmdline=$(tr -d '\0' < /proc/$pid/cmdline)
            jar=$(echo "$cmdline" | grep -oP '[^ ]*\.jar')
            echo "PID: $pid, JAR: $jar"
            echo ""
        done
        echo ""
        read -p "Enter the PID to be killed (empty to return to menu): " pid
        if [[ -n "$pid" ]]; then                    # -n == not empty
            echo -e "Enter ${YELLOW}0${NORM} for SIGTERM (15) or ${YELLOW}1${NORM} for SIGKILL (9):"
            read -n 1 -p "" level
            case $level in
                1) level=9 ;;
                *) level=15 ;;
            esac

            if kill -$level $pid; then
                echo ""
            else
                echo "Failed to kill process with PID $pid."
                read -n 1 -p "Press to continue..."
            fi
        fi
    fi
}

show_system_info()
{
    echo "----------------------------------------------------------------------"
    echo "JAVA"
    echo "----------"
    if [ -n "${JAVA_HOME:-}" ]; then
        echo "JAVA_HOME=$JAVA_HOME"
    fi
    "$JAVA_CMD" -version
    echo ""
    echo "CLASSPATH=$CLASSPATH"
    echo ""
    echo "PATH=$PATH"
    echo ""
    echo "----------------------------------------------------------------------"
    echo "CPU"
    echo "----------"
    if command -v lscpu >/dev/null 2>&1; then
        lscpu | grep -E "Architecture|Vendor"
    else
        uname -m
    fi
    echo ""
    echo "----------------------------------------------------------------------"
    echo "RAM"
    echo "----------"
    if command -v free >/dev/null 2>&1; then
        free -m
    else
        vm_stat
    fi
    echo ""
    echo ""
    read -n 1 -p "Press any key to continue..."
}

# ---------------------------------------------------------------------------------------------
# Main Menu
# ---------------------------------------------------------------------------------------------

show_menu()
{
    clear
    echo -e "=============================================="
    echo -e "            ${BLUE}::: Mingle Menu :::${NORM}"
    echo -e "=============================================="
    echo -e "${YELLOW}G${NORM}lue      : Mission Control (IDE)"
    echo -e "G${YELLOW}u${NORM}m       : Gum: dashboards and file-server"
    echo -e "${YELLOW}S${NORM}tick     : Stick default (ExEn)"
    echo -e "S${YELLOW}t${NORM}ick     :       for low memory"
    echo -e "St${YELLOW}i${NORM}ck     :       for debug (port: 8800)"
    echo -e "Sti${YELLOW}c${NORM}k     :       for profiling (VisualVM)"
    echo -e "Stic${YELLOW}k${NORM}     :       and stay resident (nohup)"
    echo -e "Ta${YELLOW}p${NORM}e      : Transpiler"
    echo -e "${YELLOW}L${NORM}ist/Kill : List and kill JVMs"
    echo -e "Inf${YELLOW}o${NORM}      : System information"
    echo -e "----------------------------------------------"

    if [ "$SYSD_MODE" = "stop" ]; then
        echo -e "  [Start] or [${RED}Stop${NORM}] via 'systemd' (change: ${YELLOW}0${NORM})"
    else
        echo -e "  [${RED}Start${NORM}] or [Stop] via 'systemd' (change: ${YELLOW}0${NORM})"
    fi

    echo -e "${YELLOW}1${NORM}. Gum"
    echo -e "${YELLOW}2${NORM}. Stick"
    echo -e "----------------------------------------------"
    echo -e "${YELLOW}H${NORM}elp"
    echo -e "E${YELLOW}x${NORM}it"
    echo -e "----------------------------------------------"
}

# ---------------------------------------------------------------------------------------------
# Main Program
# ---------------------------------------------------------------------------------------------

main()
{
    if [[ "$(uname)" != "Linux" ]]; then
        echo "This script runs only on Linux"
        exit 0
    fi

    init_environment
    find_java

    while true; do
        local action

        if [ "$#" -gt 0 ]; then
            action=$1
        else
            clear
            show_menu
            read -n 1 -p "Select option: " opt
            clear
            action=$opt
        fi

        action=${action,,}

        case $action in
            h) show_help ;;
            x) exit 0 ;;
            g) run_glue ;;
            u) run_gum "$@" ;;
            s) run_stick "default" "${2:-}" ;;
            t) run_stick "lowram" "${2:-}" ;;
            i) run_stick "debug" "${2:-}" ;;
            c) run_stick "profile" "${2:-}" ;;
            k) run_stick "resident" "${2:-}" ;;
            p) run_tape "${2:-}" ;;
            l) list_processes ;;
            o) show_system_info ;;
            8) run_gum "service" ;;
            9) run_stick "service" "" ;;
            0) SYSD_MODE=$([[ "$SYSD_MODE" = "stop" ]] && echo "start" || echo "stop") ;;
            1) check_superuser "This option must be run as root"
               create_service_file "gum" "${SCRIPT_DIR}/menu.sh 8" "pkill -f gum.jar"
               [ -n "${2:-}" ] && SYSD_MODE=$2
               [ -z "$SYSD_MODE" ] && SYSD_MODE="start"
               systemctl "$SYSD_MODE" mingle_gum.service
               ;;
            2) check_superuser "This option must be run as root"
               create_service_file "stick" "${SCRIPT_DIR}/menu.sh 9" "pkill -f stick.jar"
               [ -n "${2:-}" ] && SYSD_MODE=$2
               [ -z "$SYSD_MODE" ] && SYSD_MODE="start"
               systemctl "$SYSD_MODE" mingle_stick.service
               ;;
            *) if [ "$#" -eq 0 ]; then
                    log "ERROR" "Invalid option: $action"
               fi
               ;;
        esac

        if [ "$#" -gt 0 ]; then
            exit 0  # Exit if script was called with arguments (unattended mode)
        fi
    done
}

main "$@"
