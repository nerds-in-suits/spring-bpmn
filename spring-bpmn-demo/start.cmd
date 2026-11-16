@echo off
rem Starts the spring-bpmn demo application (engine, JPA on H2, REST API and UI).
rem Builds the executable jar with Maven first if it is missing or if --build is the first argument.
rem All other arguments are passed to the application, e.g. start.cmd --server.port=9090
setlocal

set "MODULE_DIR=%~dp0"
set "MODULE_DIR=%MODULE_DIR:~0,-1%"
set "ROOT_DIR=%MODULE_DIR%\.."
set "JAR=%MODULE_DIR%\target\spring-bpmn-demo.jar"

rem %* keeps "name=value" together; cmd would split %1 %2 ... at the "=".
set "ARGS=%*"
if "%~1"=="--build" (
    if exist "%JAR%" del "%JAR%"
    set "ARGS=%ARGS:*--build=%"
)

if not exist "%JAR%" (
    echo Building %JAR% ...
    pushd "%ROOT_DIR%"
    call mvn -B -q -pl conventions,spring-bpmn-demo -am install -DskipTests
    if errorlevel 1 (
        popd
        exit /b 1
    )
    popd
)

echo Starting the demo; the UI will be at http://localhost:8080/bpmn-ui/ (unless --server.port is given).
cd /d "%MODULE_DIR%"
java -jar "%JAR%" %ARGS%
