#!/usr/bin/env sh

# Launcher script which rebuilds the waltid-cli from source and executes the command supplied

SCRIPT_DIR=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
PROJECT_DIR="$SCRIPT_DIR/../.."
LAUNCHER="$SCRIPT_DIR/build/install/waltid-jvm/bin/waltid"

"$PROJECT_DIR/gradlew" -p "$PROJECT_DIR" --quiet :waltid-applications:waltid-cli:installJvmDist && exec "$LAUNCHER" "$@"
