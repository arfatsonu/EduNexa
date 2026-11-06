#!/usr/bin/env bash
# EduNexa wrapper launcher. For complex JVM options use gradle.properties.
set -euo pipefail
APP_HOME="$(cd -- "$(dirname -- "$0")" && pwd -P)"
if [[ -n "${JAVA_HOME:-}" ]]; then
  JAVA_COMMAND="$JAVA_HOME/bin/java"
else
  JAVA_COMMAND="$(command -v java || true)"
fi
if [[ -z "$JAVA_COMMAND" || ! -x "$JAVA_COMMAND" ]]; then
  echo 'Java is required. Install a JDK and set JAVA_HOME.' >&2
  exit 1
fi
read -r -a JAVA_OPTIONS <<< "${JAVA_OPTS:-}"
read -r -a GRADLE_OPTIONS <<< "${GRADLE_OPTS:-}"
exec "$JAVA_COMMAND" "${JAVA_OPTIONS[@]}" "${GRADLE_OPTIONS[@]}" \
  -Dorg.gradle.appname=gradlew \
  -classpath "$APP_HOME/gradle/wrapper/gradle-wrapper.jar" \
  org.gradle.wrapper.GradleWrapperMain "$@"
