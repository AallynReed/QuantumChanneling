@echo off
setlocal

REM Pin to Java 17 (Forge 1.20.1 requires it).
if not defined JAVA_HOME (
    set "JAVA_HOME=C:\Program Files\Eclipse Adoptium\jdk-17.0.19.10-hotspot"
)

if not exist "%JAVA_HOME%\bin\java.exe" (
    echo [build.bat] JAVA_HOME does not point at a JDK: "%JAVA_HOME%"
    echo [build.bat] Edit build.bat or set JAVA_HOME to a JDK 17 install before running.
    exit /b 1
)

echo [build.bat] Using JDK at "%JAVA_HOME%"
call "%~dp0gradlew.bat" build %*
exit /b %ERRORLEVEL%
