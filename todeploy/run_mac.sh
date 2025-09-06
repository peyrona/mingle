#!/bin/bash

# Generate timestamp for log file (macOS date format)
timestamp=$(date +"%Y-%m-%d_%H-%M-%S")

# Run the Java command
nohup java -cp "glue.jar:tape.jar:lib/*:lib/glue/*" \
           -disableassertions                     \
           -javaagent:lib/lang.jar                \
           com.peyrona.mingle.glue.Main > "glue_$timestamp.out" 2>&1 &

echo "Process started..."

