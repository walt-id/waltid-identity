#!/usr/bin/env sh

gradle --quiet installDist && build/install/waltid-cli/bin/waltid-cli "$@"
