#!/bin/bash

# Mingle Java API Documentation Generator
# This script generates Javadoc HTML documentation for all Mingle projects
#
# This script does not compress the folder: this is done at realease.sh

set -e  # Exit on any error

# Configuration
MAIN_TITLE="Mingle Platform API Documentation"
OUTPUT_DIR="javadocs"
LOGO_PATH="docs/mingle-logo-256x256.png"
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$SCRIPT_DIR"

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# Function to print colored output
print_status()
{
    echo -e "${BLUE}[INFO]${NC} $1"
}

print_success()
{
    echo -e "${GREEN}[SUCCESS]${NC} $1"
}

print_warning()
{
    echo -e "${YELLOW}[WARNING]${NC} $1"
}

print_error()
{
    echo -e "${RED}[ERROR]${NC} $1"
}

# Function to check if a directory contains Java source files
has_java_sources()
{
    local dir="$1"
    [ -d "$dir/src" ] && find "$dir/src" -name "*.java" -type f 2>/dev/null | grep -q .
}

# Function to get all projects with Java sources
get_java_projects()
{
    local projects=()
    for dir in */; do
        if [ -d "$dir" ] && has_java_sources "$dir"; then
            projects+=("${dir%/}")
        fi
    done
    echo "${projects[@]}"
}

# Function to get projects in dependency order (hardcoded based on analysis)
get_projects_in_dependency_order()
{
        local ordered_projects=(
            "lang"
            "network"
            "controllers"
            "cil"
            "candi"
            "tape"
            "stick"
            "gum"
            "updater"
            "glue"
            "menu"
        )

    # Filter to only include projects that actually exist
    local existing_projects=($(get_java_projects))
    local final_order=()

    for project in "${ordered_projects[@]}"; do
        local found=false
        for existing_project in "${existing_projects[@]}"; do
            if [[ "$project" == "$existing_project" ]]; then
                found=true
                break
            fi
        done
        if [[ "$found" == true ]]; then
            final_order+=("$project")
        fi
    done

    echo "${final_order[@]}"
}

# Function to map package names to JAR files
map_package_to_jar()
{
    local package="$1"
    local jar_files=""

    case "$package" in
        "org.apache.poi"|"org.apache.poi.ss"|"org.apache.poi.xssf")
            jar_files=$(find todeploy/lib -name "poi*.jar" -type f 2>/dev/null | tr '\n' ':')
            jar_files="${jar_files}$(find todeploy/lib/controllers/poi-bin-*/lib -name "poi*.jar" -type f 2>/dev/null | tr '\n' ':')"
            ;;
        "com.sun.jna")
            jar_files=$(find todeploy/lib -name "jna*.jar" -type f 2>/dev/null | tr '\n' ':')
            ;;
        "com.eclipsesource.json")
            jar_files=$(find todeploy/lib -name "*json*.jar" -type f 2>/dev/null | tr '\n' ':')
            ;;
        "com.ghgande.j2mod")
            jar_files=$(find todeploy/lib -name "*j2mod*.jar" -type f 2>/dev/null | tr '\n' ':')
            ;;
        "javax.speech")
            jar_files=$(find todeploy/lib/controllers/freetts-*/ -name "*.jar" -type f 2>/dev/null | tr '\n' ':')
            ;;
        "org.xnio")
            jar_files=$(find todeploy/lib/gum/ -name "xnio*.jar" -type f 2>/dev/null | tr '\n' ':')
            ;;
        "org.wildfly"|"io.undertow")
            jar_files=$(find todeploy/lib/gum/ -name "*.jar" -type f 2>/dev/null | tr '\n' ':')
            ;;
    esac

    # Remove trailing colon
    echo "${jar_files%:}"
}

# Function to extract import statements from Java files
extract_imports()
{
    local project="$1"
    local temp_file=$(mktemp)

    # Extract all import statements from Java files
    find "$project/src" -name "*.java" -type f -exec grep -h "^import " {} \; 2>/dev/null | \
    sed 's/^import //' | sed 's/;//' | sed 's/^static //' | \
    sort | uniq > "$temp_file"

    echo "$temp_file"
}

# Function to build smart classpath based on detected dependencies
build_smart_classpath()
{
    local project="$1"
    local classpath=""
    local added_jars=()

    # Always include ALL Mingle JARs from todeploy/lib and todeploy/ (Option 1: Comprehensive)
        local mingle_jars=$(find todeploy/lib todeploy -maxdepth 1 -name "*.jar" -type f 2>/dev/null || true)
    for jar in $mingle_jars; do
        if [ -f "$jar" ]; then
            if [ -n "$classpath" ]; then
                classpath="$classpath:$jar"
            else
                classpath="$jar"
            fi
            added_jars+=("$jar")
        fi
    done

    # Extract imports from source files
    local imports_file=$(extract_imports "$project")

    # Process each import and map to JAR files
    while IFS= read -r import_line; do
        if [ -n "$import_line" ]; then
            # Extract package name (first two or three parts)
            local package=$(echo "$import_line" | cut -d'.' -f1-3)

            # Map package to JAR files
            local jar_files=$(map_package_to_jar "$package")

            # Add each JAR to classpath if not already added
            if [ -n "$jar_files" ]; then
                IFS=':' read -ra JAR_ARRAY <<< "$jar_files"
                for jar in "${JAR_ARRAY[@]}"; do
                    if [ -n "$jar" ] && [ -f "$jar" ]; then
                        # Check if JAR is already in classpath
                        local already_added=false
                        for added_jar in "${added_jars[@]}"; do
                            if [[ "$jar" == "$added_jar" ]]; then
                                already_added=true
                                break
                            fi
                        done
                        if [[ "$already_added" == false ]]; then
                            if [ -n "$classpath" ]; then
                                classpath="$classpath:$jar"
                            else
                                classpath="$jar"
                            fi
                            added_jars+=("$jar")
                        fi
                    fi
                done
            fi
        fi
    done < "$imports_file"

    # Clean up temp file
    rm -f "$imports_file"

    # Add project-specific JARs that weren't already included
    if [ -d "$project/lib" ]; then
        local project_jars=$(find "$project/lib" -name "*.jar" -type f 2>/dev/null || true)
        for jar in $project_jars; do
            local already_added=false
            for added_jar in "${added_jars[@]}"; do
                if [[ "$jar" == "$added_jar" ]]; then
                    already_added=true
                    break
                fi
            done
            if [[ "$already_added" == false ]]; then
                if [ -n "$classpath" ]; then
                    classpath="$classpath:$jar"
                else
                    classpath="$jar"
                fi
                added_jars+=("$jar")
            fi
        done
    fi

    echo "$classpath"
}

# Function to generate error file for failed projects
generate_error_file()
{
    local project="$1"
    local version="$2"
    local project_output_dir="$OUTPUT_DIR/$project"
    local error_file="$project_output_dir/javadoc_errors.txt"

    print_status "Generating error file for project: $project"

    # Create project-specific output directory
    mkdir -p "$project_output_dir"

    # Try to generate javadoc and capture errors
    local source_files_list=$(find "$project/src" -name "*.java" -type f 2>/dev/null)

    if [ -z "$source_files_list" ]; then
        echo "No Java source files found in $project" > "$error_file"
        return 1
    fi

    # Build smart classpath based on detected dependencies
    print_status "Building smart classpath for $project..."
    local classpath=$(build_smart_classpath "$project")

    if [ -n "$classpath" ]; then
        print_status "Classpath includes: $(echo "$classpath" | tr ':' '\n' | wc -l) JAR files"
    else
        print_warning "No classpath dependencies detected for $project"
    fi

    # Generate javadoc and capture all output
    local javadoc_cmd="javadoc"
    local javadoc_args=(
        -d "$project_output_dir"
        -sourcepath "$project/src"
        -doctitle "Mingle $project API v$version"
        -header "Mingle $project v$version"
        -charset "UTF-8"
        -docencoding "UTF-8"
        -encoding "UTF-8"
        -author
        -use
        -windowtitle "Mingle $project API v$version"
        -Xdoclint:none
    )

    # Add classpath if available
    if [ -n "$classpath" ]; then
        javadoc_args+=("-classpath" "$classpath")
    fi

    # Add source files
    javadoc_args+=($source_files_list)

    # Create error file with detailed information
    cat > "$error_file" << EOF
Javadoc Generation Error Report for Project: $project
Version: $version
Generated on: $(date)
===============================================

PROJECT INFORMATION:
- Project Name: $project
- Version: $version
- Source Directory: $project/src
- Output Directory: $project_output_dir
- Classpath: $classpath

SOURCE FILES FOUND:
$source_files_list

JAVADOC COMMAND EXECUTED:
$javadoc_cmd ${javadoc_args[*]}

ERROR OUTPUT:
EOF

    # Execute javadoc and capture errors
    if ! $javadoc_cmd "${javadoc_args[@]}" >> "$error_file" 2>&1; then
        echo "" >> "$error_file"
        echo "ADDITIONAL DIAGNOSTIC INFORMATION:" >> "$error_file"
        echo "===================================" >> "$error_file"
        echo "Java version:" >> "$error_file"
        java -version 2>&1 >> "$error_file"
        echo "" >> "$error_file"
        echo "Javadoc version:" >> "$error_file"
        javadoc -version 2>&1 >> "$error_file"
        echo "" >> "$error_file"
        echo "Available JAR files in project/lib:" >> "$error_file"
        if [ -d "$project/lib" ]; then
            find "$project/lib" -name "*.jar" -type f >> "$error_file" 2>/dev/null || echo "No JAR files found" >> "$error_file"
        else
            echo "No lib directory found" >> "$error_file"
        fi
    fi

    # Create a simple HTML error page
    cat > "$project_output_dir/index.html" << EOF
<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>Mingle $project API Documentation v$version - Error</title>
    <style>
        body {
            font-family: 'Segoe UI', Tahoma, Geneva, Verdana, sans-serif;
            margin: 0;
            padding: 20px;
            background: linear-gradient(135deg, #ff6b6b 0%, #ee5a24 100%);
            min-height: 100vh;
            display: flex;
            align-items: center;
            justify-content: center;
        }
        .error-container {
            background: rgba(255, 255, 255, 0.95);
            border-radius: 15px;
            padding: 40px;
            box-shadow: 0 8px 32px rgba(0, 0, 0, 0.1);
            max-width: 800px;
            text-align: center;
        }
        .error-icon {
            font-size: 4em;
            color: #ee5a24;
            margin-bottom: 20px;
        }
        h1 {
            color: #333;
            font-size: 2em;
            margin-bottom: 20px;
        }
        .error-message {
            color: #666;
            line-height: 1.6;
            margin-bottom: 30px;
        }
        .error-details {
            background: #f8f9fa;
            border-left: 4px solid #ee5a24;
            padding: 20px;
            margin: 20px 0;
            text-align: left;
            border-radius: 5px;
        }
        .error-link {
            display: inline-block;
            background: #ee5a24;
            color: white;
            text-decoration: none;
            padding: 12px 24px;
            border-radius: 25px;
            font-weight: 500;
            transition: all 0.3s ease;
        }
        .error-link:hover {
            background: #c44569;
            transform: scale(1.05);
        }
    </style>
</head>
<body>
    <div class="error-container">
        <div class="error-icon">⚠️</div>
        <h1>Javadoc Generation Failed</h1>
        <div class="error-message">
            <strong>Project:</strong> $project<br>
            <strong>Version:</strong> $version<br>
            The Javadoc generation for this project encountered compilation errors.
            This is typically due to missing dependencies or compilation issues in the source code.
        </div>
        <div class="error-details">
            <strong>Error Details:</strong><br>
            The complete error log has been saved to <code>javadoc_errors.txt</code> in this directory.
            You can view the detailed compilation errors and diagnostic information there.
        </div>
        <a href="javadoc_errors.txt" class="error-link">View Error Details</a>
    </div>
</body>
</html>
EOF

    print_success "Error file generated for $project: $error_file"
    return 0
}

# Function to generate Javadoc for a single project
generate_project_javadoc()
{
    local project="$1"
    local version="$2"
    local project_output_dir="$OUTPUT_DIR/$project"

    print_status "Generating Javadoc for project: $project"

    # Create project-specific output directory
    mkdir -p "$project_output_dir"

    # Find all Java source files
    local source_files_list=$(find "$project/src" -name "*.java" -type f 2>/dev/null)

    if [ -z "$source_files_list" ]; then
        print_warning "No Java source files found in $project"
        return 1
    fi

    # Build smart classpath based on detected dependencies
    print_status "Building smart classpath for $project..."
    local classpath=$(build_smart_classpath "$project")

    if [ -n "$classpath" ]; then
        print_status "Classpath includes: $(echo "$classpath" | tr ':' '\n' | wc -l) JAR files"
    else
        print_warning "No classpath dependencies detected for $project"
    fi

    # Generate Javadoc
    local javadoc_cmd="javadoc"
    local javadoc_args=(
        -d "$project_output_dir"
        -sourcepath "$project/src"
        -doctitle "Mingle $project API Documentation v$version"
        -header "Mingle $project v$version"
        -footer "Mingle Platform v$version"
        -bottom "The 'Mingle Platform v$version' API docs are released under the 'Creative Commons' license."
        -charset "UTF-8"
        -docencoding "UTF-8"
        -encoding "UTF-8"
        -author
        -use
        -windowtitle "Mingle $project API v$version"
        -Xdoclint:none
        -quiet
    )

    # Add classpath if available
    if [ -n "$classpath" ]; then
        javadoc_args+=("-classpath" "$classpath")
    fi

    # Add source files
    javadoc_args+=($source_files_list)

    # Execute javadoc command with timeout
    print_status "Running javadoc for $project (this may take a moment)..."
    if timeout 300 $javadoc_cmd "${javadoc_args[@]}" > /dev/null 2>&1; then
        # Check if index.html was actually created
        if [ -f "$project_output_dir/index.html" ]; then
            print_success "Javadoc generated for $project"
            return 0
        else
            print_error "Javadoc failed to create files for $project"
            return 1
        fi
    else
        local exit_code=$?
        if [ $exit_code -eq 124 ]; then
            print_error "Javadoc generation for $project timed out after 5 minutes"
            return 1
        else
            # Try to generate with minimal options to avoid dependency issues
            print_warning "Retrying $project with minimal options"
            local minimal_args=(
                -d "$project_output_dir"
                -sourcepath "$project/src"
                -doctitle "Mingle $project API Documentation v$version"
                -header "Mingle $project v$version"
                -charset "UTF-8"
                -docencoding "UTF-8"
                -encoding "UTF-8"
                -author
                -use
                -windowtitle "Mingle $project API v$version"
                -Xdoclint:none
                -quiet
            )

            # Add source files to minimal args
            minimal_args+=($source_files_list)

            if timeout 300 $javadoc_cmd "${minimal_args[@]}" > /dev/null 2>&1; then
                if [ -f "$project_output_dir/index.html" ]; then
                    print_success "Javadoc generated for $project (minimal mode)"
                    return 0
                else
                    print_error "Javadoc failed to create files for $project even in minimal mode"
                    return 1
                fi
            else
                print_error "Javadoc generation for $project failed completely"
                # Generate error file with compilation errors
                generate_error_file "$project" "$version"
                return 1
            fi
        fi
    fi
}

# Function to create main index page
create_main_index()
{
    local version="$1"
    shift
    local projects=("$@")

    print_status "Creating main index page"

    cat > "$OUTPUT_DIR/index.html" << EOF
<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>Mingle Platform API Documentation v$version</title>
    <style>
        body {
            font-family: 'Segoe UI', Tahoma, Geneva, Verdana, sans-serif;
            margin: 0;
            padding: 0;
            background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
            min-height: 100vh;
        }
        .container {
            max-width: 1200px;
            margin: 0 auto;
            padding: 20px;
        }
        .banner-card {
            background: rgba(255, 255, 255, 0.1);
            border-radius: 15px;
            padding: 25px;
            box-shadow: 0 8px 32px rgba(0, 0, 0, 0.1);
            border: 1px solid rgba(255, 255, 255, 0.2);
            backdrop-filter: blur(10px);
            text-align: center;
        }
        .banner-logo {
            max-width: 80px;
            height: auto;
            margin-bottom: 15px;
            border-radius: 10px;
            background: transparent;
        }
        .banner-title {
            color: white;
            font-size: 1.8em;
            margin: 0;
            text-shadow: 2px 2px 4px rgba(0, 0, 0, 0.3);
            margin-bottom: 5px;
        }
        .banner-subtitle {
            color: rgba(255, 255, 255, 0.9);
            font-size: 1em;
            margin: 0;
        }
        .projects-grid {
            display: grid;
            grid-template-columns: repeat(auto-fit, minmax(300px, 1fr));
            gap: 25px;
            margin-top: 20px;
        }
        .project-card {
            background: rgba(255, 255, 255, 0.95);
            border-radius: 15px;
            padding: 25px;
            box-shadow: 0 8px 32px rgba(0, 0, 0, 0.1);
            transition: transform 0.3s ease, box-shadow 0.3s ease;
            border: 1px solid rgba(255, 255, 255, 0.2);
        }
        .project-card:hover {
            transform: translateY(-5px);
            box-shadow: 0 12px 40px rgba(0, 0, 0, 0.2);
        }
        .project-name {
            font-size: 1.5em;
            font-weight: bold;
            color: #333;
            margin-bottom: 10px;
            text-transform: capitalize;
        }
        .project-description {
            color: #666;
            margin-bottom: 15px;
            line-height: 1.6;
        }
        .project-link {
            display: inline-block;
            background: linear-gradient(45deg, #667eea, #764ba2);
            color: white;
            text-decoration: none;
            padding: 10px 20px;
            border-radius: 25px;
            font-weight: 500;
            transition: all 0.3s ease;
        }
        .project-link:hover {
            background: linear-gradient(45deg, #764ba2, #667eea);
            transform: scale(1.05);
        }
        .footer {
            text-align: center;
            padding: 30px 0;
            color: rgba(255, 255, 255, 0.8);
            margin-top: 50px;
        }
        .generation-info {
            background: rgba(255, 255, 255, 0.1);
            padding: 15px;
            border-radius: 10px;
            margin-top: 20px;
            backdrop-filter: blur(10px);
        }
    </style>
</head>
<body>
    <div class="container">
        <div class="projects-grid">
            <div class="banner-card">
                <img src="mingle-logo-256x256.png" alt="Mingle Logo" class="banner-logo">
                <h1 class="banner-title">Mingle Platform</h1>
                <p class="banner-subtitle">API Documentation v$version</p>
            </div>
EOF

    # Add project cards (sorted alphabetically for display)
    local sorted_projects=($(printf '%s\n' "${projects[@]}" | sort))
    for project in "${sorted_projects[@]}"; do
        local description=""
        local has_error=""

        # Check if project has error file
        if [ -f "$OUTPUT_DIR/$project/javadoc_errors.txt" ]; then
            has_error="true"
        fi

        case "$project" in
            "lang")
                description="Core language module providing lexer, parse, expression evaluation, and fundamental utilities."
                ;;
            "candi")
                description="Compiler and transpiler for the UNE language, and provider for other langugaes integration."
                ;;
            "gum")
                description="Graphical User Management module providing web-based dashboard and monitoring capabilities."
                ;;
            "controllers")
                description="Device controllers and communication interfaces for hardware integration and protocol handling."
                ;;
            "network")
                description="Networking components providing socket communication, WebSocket support, and client-server infrastructure."
                ;;
            "updater")
                description="Automatic update management system for keeping Mingle components current with latest releases."
                ;;
            "cil")
                description="Common Interface Library providing shared interfaces and abstractions across Mingle commands."
                ;;
            "stick")
                description="Core runtime engine providing device management, rule execution, and orchestration for the Mingle platform."
                ;;
            "tape")
                description="Transpiler tasks for Mingle operations."
                ;;
            "menu")
                description="Cross-platform launcher and management system for Mingle components with both GUI and console interfaces."
                ;;
            *)
                description="Mingle platform module contributing to the overall ecosystem functionality."
                ;;
        esac

        if [ "$has_error" = "true" ]; then
            cat >> "$OUTPUT_DIR/index.html" << EOF
            <div class="project-card" style="border-left: 4px solid #ee5a24;">
                <div class="project-name">$project ⚠️</div>
                <div class="project-description">$description</div>
                <div style="color: #ee5a24; font-weight: 500; margin-bottom: 10px;">⚠️ Javadoc generation failed</div>
                <a href="$project/index.html" class="project-link" style="background: linear-gradient(45deg, #ee5a24, #ff6b6b);">View Error Details</a>
            </div>
EOF
        else
            cat >> "$OUTPUT_DIR/index.html" << EOF
            <div class="project-card">
                <div class="project-name">$project</div>
                <div class="project-description">$description</div>
                <a href="$project/index.html" class="project-link">View API Documentation</a>
            </div>
EOF
        fi
    done

    cat >> "$OUTPUT_DIR/index.html" << EOF
        </div>

        <div class="footer">
            Mingle Platform v$version API documentation is released under Creative Commons license.<br>
            Documentation Generated: $(date)<br>
            Generated with Mingle Javadoc Generator
        </div>
    </div>
</body>
</html>
EOF

    print_success "Main index page created"
}

# Function to copy logo to output directory
copy_logo()
{
    if [ -f "$LOGO_PATH" ]; then
        mkdir -p "$OUTPUT_DIR"
        cp "$LOGO_PATH" "$OUTPUT_DIR/"
        print_success "Logo copied to javadocs folder"
    else
        print_warning "Logo not found at $LOGO_PATH"
    fi
}


# Main execution
main()
{
    local version="$1"

    if [ -z "$version" ]; then
        print_error "Version parameter is required"
        print_error "Usage: $0 <version>"
        exit 1
    fi

    print_status "Starting Mingle Javadoc generation for version: $version"
    print_status "Output directory: $OUTPUT_DIR"

    # Clean previous output
    if [ -d "$OUTPUT_DIR" ]; then
        print_status "Cleaning previous output"
        rm -rf "$OUTPUT_DIR"
    fi
    mkdir -p "$OUTPUT_DIR"

    # Get all Java projects in dependency order
    local projects=($(get_projects_in_dependency_order))

    if [ ${#projects[@]} -eq 0 ]; then
        print_error "No projects with Java source files found"
        exit 1
    fi

    print_status "Processing ${#projects[@]} projects in dependency order:"
    for project in "${projects[@]}"; do
        print_status "  $project"
    done

    # Generate Javadoc for each project
    local successful_projects=()
    local failed_projects=()

    for project in "${projects[@]}"; do
        if generate_project_javadoc "$project" "$version"; then
            successful_projects+=("$project")
        else
            failed_projects+=("$project")
        fi
    done

    # Copy logo
    copy_logo

    # Create main index (include all projects)
    create_main_index "$version" "${projects[@]}"

    # Summary
    print_status "=== Javadoc Generation Summary ==="
    print_success "Successfully generated: ${#successful_projects[@]} projects"
    if [ ${#successful_projects[@]} -gt 0 ]; then
        print_success "Projects: ${successful_projects[*]}"
    fi

    if [ ${#failed_projects[@]} -gt 0 ]; then
        print_error "Failed to generate: ${#failed_projects[@]} projects"
        print_error "Projects: ${failed_projects[*]}"
    fi

    print_status "Documentation available at: $OUTPUT_DIR/index.html"

    # Exit with error code if any projects failed
    if [ ${#failed_projects[@]} -gt 0 ]; then
        exit 1
    fi
}

# Check if javadoc is available
if ! command -v javadoc &> /dev/null; then
    print_error "javadoc command not found. Please ensure JDK is installed and in PATH."
    exit 1
fi

# Run main function
main "$@"