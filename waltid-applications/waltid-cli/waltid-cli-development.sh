#!/usr/bin/env sh

# Launcher script which rebuilds the waltid-cli from source and executes the command supplied

../../gradlew --quiet installJvmDist && build/install/waltid-jvm/bin/waltid "$@"
