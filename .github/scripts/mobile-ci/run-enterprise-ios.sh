#!/usr/bin/env bash
set -euo pipefail

# The Enterprise task requires the coordinated checkout above Identity.
cd "$(dirname "${BASH_SOURCE[0]}")/../../../.."

# A 7 GiB macOS runner also hosts Xcode and a simulator. Compile the JVM fixture
# in one bounded process, and let that process exit before mobile execution.
# In-process Kotlin compilation prevents compiler daemons retaining their heaps.
gradle_options=(
  --no-daemon
  --no-configuration-cache
  --max-workers=1
  -Pkotlin.compiler.execution.strategy=in-process
  '-Dorg.gradle.jvmargs=-Xmx3g -Dfile.encoding=UTF-8'
)

./gradlew :waltid-enterprise-integration-tests:classes "${gradle_options[@]}" "$@"

# The existing task still owns both demos, fresh simulators, fixture lifetimes
# and normal test assertions. Its classes dependency now reuses the built output.
exec ./gradlew :waltid-enterprise-integration-tests:enterpriseIosMobileIntegrationTest \
  "${gradle_options[@]}" "$@"
