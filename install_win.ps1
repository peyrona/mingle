@echo off

:: Step 1: Create a folder named "mingle" in the user's home directory
set "MINGLE_DIR=%USERPROFILE%\mingle"
echo "Last Mingle Version along with JDK 11 will be downloaded to %MINGLE_DIR"
echo "Later you can move this folder to any other place if you want."
Write-Host "Press any key to continue..."
$x = $host.UI.RawUI.ReadKey("NoEcho,IncludeKeyDown")
mkdir "%MINGLE_DIR%"

:: Step 2: Create a subfolder named "java-11" inside "mingle"
set "JAVA_DIR=%MINGLE_DIR%\java-11"
mkdir "%JAVA_DIR%"

:: Step 3: Download Java JDK version 11 from Adoptium into the "java-11" folder
echo Downloading JDK 11...
set "JDK_URL=https://github.com/adoptium/temurin11-binaries/releases/latest/download/OpenJDK11U-jdk_x64_windows_hotspot.zip"
powershell -Command "Invoke-WebRequest -Uri '%JDK_URL%' -OutFile '%JAVA_DIR%\jdk11.zip'"

:: Check if the JDK was downloaded successfully
if not exist "%JAVA_DIR%\jdk11.zip" (
    echo ERROR: Failed to download JDK 11.
    exit /b 1
)

:: Step 4: Extract the downloaded JDK into the "java-11" folder
echo Extracting JDK 11...
powershell -Command "Expand-Archive -Path '%JAVA_DIR%\jdk11.zip' -DestinationPath '%JAVA_DIR%'"

:: Check if extraction was successful
for /d %%i in ("%JAVA_DIR%\jdk-11*") do set "JDK_FOLDER=%%i"
if not exist "%JDK_FOLDER%" (
    echo ERROR: Failed to extract JDK 11.
    exit /b 1
)

:: Step 5: Download "update.jar" into the "mingle" folder
echo Downloading update.jar...
set "UPDATE_JAR_URL=https://mingle.peyrona.com/update.jar"
powershell -Command "Invoke-WebRequest -Uri '%UPDATE_JAR_URL%' -OutFile '%MINGLE_DIR%\update.jar'"

:: Check if the update.jar was downloaded successfully
if not exist "%MINGLE_DIR%\update.jar" (
    echo ERROR: Failed to download update.jar.
    exit /b 1
)

:: Step 6: Check if java.exe exists, then execute "update.jar"
if exist "%JDK_FOLDER%\bin\java.exe" (
    echo Running update.jar using JDK 11...
    "%JDK_FOLDER%\bin\java.exe" -jar "%MINGLE_DIR%\update.jar"
) else (
    echo ERROR: java.exe not found in the downloaded JDK.
    exit /b 1
)

:: Pause to show the output
pause

