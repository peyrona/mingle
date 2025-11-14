#!/bin/bash

# Function to find Java executable
find_java_executable()
{
    # Check if Java is in PATH
    if command -v java >/dev/null 2>&1; then
        echo "java"
        return 0
    fi

    # Check for JDK subdirectories in current directory
    for dir in */; do
        if [[ "$dir" == *"jdk"* ]]; then
            java_path="${dir}bin/java"
            if [[ -x "$java_path" ]]; then
                echo "$java_path"
                return 0
            fi
        fi
    done

    return 1
}

# Find Java executable
java_executable=$(find_java_executable)

if [[ $? -ne 0 ]]; then
    echo "Java was not found, I can download it and make it ready to be used by Mingle."
    read -p "Do you want to do it now? [Y/n] " response

    if [[ -z "$response" || "$response" =~ ^[Yy]$ ]]; then
        echo "Running Java setup script..."
        ./etc/get-java-mac.sh
        if [[ $? -eq 0 ]]; then
            echo "Java setup completed. Please run this script again."
        else
            echo "Java setup failed: check ./etc/get-java-mac.sh"
        fi
    else
        echo "Java setup cancelled."
    fi

    echo "Press any key to exit..."
    read -n 1 -s
    exit 1
fi

# Generate timestamp for log file (macOS date format)
timestamp=$(date +"%Y-%m-%d_%H-%M-%S")

# Run the Java command
nohup "$java_executable" -cp "glue.jar:tape.jar:lib/*:lib/glue/*" \
           -disableassertions                     \
           -javaagent:lib/lang.jar                \
           com.peyrona.mingle.glue.Main > "glue_$timestamp.out" 2>&1 &

echo "Process started..."
