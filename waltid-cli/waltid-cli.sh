#!/usr/bin/env sh

if [ -f 'build/install/waltid-cli/bin/waltid-cli' ]; then
  build/install/waltid-cli/bin/waltid-cli "$@"
else
  echo "waltid-cli not yet build."

  echo "Running build..."
  ../gradlew installDist

  echo "Trying to run CLI command after build..."
  build/install/waltid-cli/bin/waltid-cli "$@"
fi
