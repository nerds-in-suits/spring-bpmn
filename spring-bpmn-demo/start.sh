#!/bin/sh
# Starts the spring-bpmn demo application (engine, JPA on H2, REST API and UI).
# Builds the executable jar with Maven first if it is missing or if --build is the first argument.
# All other arguments are passed to the application, e.g. ./start.sh --server.port=9090
set -e

MODULE_DIR=$(cd "$(dirname "$0")" && pwd)
ROOT_DIR=$(dirname "$MODULE_DIR")
JAR="$MODULE_DIR/target/spring-bpmn-demo.jar"

if [ "$1" = "--build" ]; then
    shift
    rm -f "$JAR"
fi

if [ ! -f "$JAR" ]; then
    echo "Building $JAR ..."
    (cd "$ROOT_DIR" && mvn -B -q -pl conventions,spring-bpmn-demo -am install -DskipTests)
fi

echo "Starting the demo; the UI will be at http://localhost:8080/bpmn-ui/ (unless --server.port is given)."
cd "$MODULE_DIR"
exec java -jar "$JAR" "$@"
