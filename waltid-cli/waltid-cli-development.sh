#!/usr/bin/env sh

# Launcher script which rebuilds the waltid-cli from source and executes the command supplied

gradle --quiet installDist && build/install/waltid-cli/bin/waltid-cli "$@"
