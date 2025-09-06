
# Generate timestamp for log file
$timestamp = Get-Date -Format "yyyy-MM-dd_HH-mm-ss"

# Define the command
$javaCommand = "java -cp glue.jar;tape.jar;lib\*;lib\glue\* -disableassertions -javaagent:lib\lang.jar com.peyrona.mingle.glue.Main"

# Run the Java command in the background and redirect output
Start-Process -NoNewWindow -FilePath "cmd.exe" -ArgumentList "/c $javaCommand > glue_$timestamp.out 2>&1"

Write-Host "Process started..."

