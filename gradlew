#!/bin/sh

# Minimal Gradle wrapper launcher. The wrapper JAR downloads and starts the
# pinned Gradle distribution declared in gradle/wrapper/gradle-wrapper.properties.
set -eu

APP_HOME=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
CLASSPATH="$APP_HOME/gradle/wrapper/gradle-wrapper.jar"

if [ -n "${JAVA_HOME:-}" ]; then
    JAVA="$JAVA_HOME/bin/java"
else
    JAVA="java"
fi

exec "$JAVA" ${JAVA_OPTS:-} ${GRADLE_OPTS:-} \
    -classpath "$CLASSPATH" \
    org.gradle.wrapper.GradleWrapperMain "$@"
