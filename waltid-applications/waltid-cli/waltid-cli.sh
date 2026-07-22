#!/usr/bin/env sh

# Launcher script which builds the waltid-cli from source once and executes the supplied command.
# If sources change, rebuild through waltid-cli-development.sh.

SCRIPT_DIR=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
PROJECT_DIR="$SCRIPT_DIR/../.."
LAUNCHER="$SCRIPT_DIR/build/install/waltid-jvm/bin/waltid"

if [ ! -f "$LAUNCHER" ]; then
  echo "waltid-cli is not built yet."
  echo "Running build..."
  "$PROJECT_DIR/gradlew" -p "$PROJECT_DIR" :waltid-applications:waltid-cli:installJvmDist || exit $?
fi

exec "$LAUNCHER" "$@"
