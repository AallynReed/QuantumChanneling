@echo off
setlocal

REM Track whether build.bat was double-clicked vs run from a shell so we can pause at the end
REM when there's no parent terminal to keep the output visible. CMDCMDLINE pattern matches
REM "cmd.exe /c ..." which is how Explorer launches double-clicks.
set "PAUSE_AT_END=0"
echo %CMDCMDLINE% | findstr /I /C:"/c"" >NUL && set "PAUSE_AT_END=1"

REM Pin to Java 17 (Forge 1.20.1 requires it).
if not defined JAVA_HOME (
    set "JAVA_HOME=C:\Program Files\Eclipse Adoptium\jdk-17.0.19.10-hotspot"
)

if not exist "%JAVA_HOME%\bin\java.exe" (
    echo.
    echo [build.bat] JAVA_HOME does not point at a JDK: "%JAVA_HOME%"
    echo [build.bat] Edit build.bat or set JAVA_HOME to a JDK 17 install before running.
    echo.
    set "BUILD_RC=1"
    goto :done
)

echo [build.bat] Using JDK at "%JAVA_HOME%"
echo.

REM No args -> build and deploy the jar into deploy_dir (gradle.properties).
REM Otherwise forward whatever was passed (e.g. build.bat runClient, build.bat clean build).
if "%~1"=="" (
    call "%~dp0gradlew.bat" build deployJar
) else (
    call "%~dp0gradlew.bat" %*
)
set "BUILD_RC=%ERRORLEVEL%"

:done
echo.
if "%BUILD_RC%"=="0" (
    echo [build.bat] SUCCESS
) else (
    echo [build.bat] FAILED with exit code %BUILD_RC%
    echo [build.bat] Scroll up to read the error - usually the last red block from gradle.
)
echo.

if "%PAUSE_AT_END%"=="1" pause
exit /b %BUILD_RC%
