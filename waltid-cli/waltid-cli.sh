#!/usr/bin/env sh

# Launcher script which builds the waltid-cli from source **once** and executes the command supplied
# If changes are made to the source then waltid-cli has to manually be rebuild (e.g. through waltid-cli-development.sh)

if [ -f 'build/install/waltid-cli/bin/waltid-cli' ]; then
  build/install/waltid-cli/bin/waltid-cli "$@"
else
  echo "waltid-cli not yet build."

  echo "Running build..."
  ../gradlew installDist

  echo "Trying to run CLI command after build..."
  build/install/waltid-cli/bin/waltid-cli "$@"
fi
