@echo off
REM PJDBC CLI wrapper script for Windows
REM
REM Usage: pjdbc <command> [options]
REM
REM Set PJDBC_JAR environment variable to override JAR location.

setlocal enabledelayedexpansion

REM Find script directory
set "SCRIPT_DIR=%~dp0"
set "PROJECT_DIR=%SCRIPT_DIR%.."

REM Find PJDBC JAR
set "PJDBC_JAR_FOUND="

REM 1. Check PJDBC_JAR environment variable
if defined PJDBC_JAR (
    if exist "%PJDBC_JAR%" (
        set "PJDBC_JAR_FOUND=%PJDBC_JAR%"
        goto :found_jar
    )
)

REM 2. Check target directory (development)
for %%f in ("%PROJECT_DIR%\target\PJDBC-*.jar") do (
    set "jarname=%%~nxf"
    echo !jarname! | findstr /i "sources javadoc" >nul
    if errorlevel 1 (
        set "PJDBC_JAR_FOUND=%%f"
        goto :found_jar
    )
)

REM 3. Check lib directory
if exist "%PROJECT_DIR%\lib\pjdbc.jar" (
    set "PJDBC_JAR_FOUND=%PROJECT_DIR%\lib\pjdbc.jar"
    goto :found_jar
)

REM 4. Check user home
if exist "%USERPROFILE%\.pjdbc\pjdbc.jar" (
    set "PJDBC_JAR_FOUND=%USERPROFILE%\.pjdbc\pjdbc.jar"
    goto :found_jar
)

REM JAR not found
echo Error: Could not find PJDBC JAR file. >&2
echo. >&2
echo Solutions: >&2
echo   1. Run 'mvn package' to build the JAR >&2
echo   2. Set PJDBC_JAR environment variable to the JAR path >&2
echo   3. Copy pjdbc.jar to %%USERPROFILE%%\.pjdbc\ >&2
exit /b 1

:found_jar

REM Build classpath
set "CLASSPATH=%PJDBC_JAR_FOUND%"

REM Add additional JARs from lib/
if exist "%PROJECT_DIR%\lib" (
    for %%f in ("%PROJECT_DIR%\lib\*.jar") do (
        if not "%%f"=="%PJDBC_JAR_FOUND%" (
            set "CLASSPATH=!CLASSPATH!;%%f"
        )
    )
)

REM Add user-specified classpath
if defined PJDBC_CLASSPATH (
    set "CLASSPATH=!CLASSPATH!;%PJDBC_CLASSPATH%"
)

REM Run the CLI
java %PJDBC_JAVA_OPTS% -cp "%CLASSPATH%" org.pjdbc.cli.PjdbcCli %*
