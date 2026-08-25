#!/usr/bin/env bash
#
# Local Maven Central release helper for walt.id public libraries.
#
# Usage:
#   scripts/release-maven-central-local.sh 1.0.0
#
# This uploads a deployment and leaves it for MANUAL review and publishing at
# https://central.sonatype.com/publishing/deployments — it never releases automatically.
#
# Requires (never stored in this repository):
#   ~/.gradle/gradle.properties
#     mavenCentralUsername=<CENTRAL_TOKEN_USERNAME>
#     mavenCentralPassword=<CENTRAL_TOKEN_PASSWORD>
#     signing.gnupg.keyName=A865BF5305F3CBE1F3C95211090BAF922C11B918

set -euo pipefail

readonly GPG_FINGERPRINT="A865BF5305F3CBE1F3C95211090BAF922C11B918"
readonly DEPLOYMENTS_URL="https://central.sonatype.com/publishing/deployments"
readonly REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

die() { printf '\nERROR: %s\n' "$*" >&2; exit 1; }
step() { printf '\n=== %s ===\n' "$*"; }

# --- 1/2. Version argument -------------------------------------------------
VERSION="${1:-}"
[ -n "$VERSION" ] || die "No version given. Usage: $0 <version>   (e.g. $0 1.0.0)"

# --- 3. Reject snapshots ---------------------------------------------------
case "$VERSION" in
  *-SNAPSHOT) die "Refusing to release a -SNAPSHOT version to Maven Central: $VERSION" ;;
esac

cd "$REPO_ROOT"

# --- 4. Clean working tree -------------------------------------------------
step "Git state"
[ -z "$(git status --porcelain)" ] || {
  git status --short
  die "Working tree is not clean. Commit or stash before releasing."
}

# --- 5. Show commit / branch / tag ----------------------------------------
git --no-pager log -1 --oneline
printf 'branch: %s\n' "$(git rev-parse --abbrev-ref HEAD)"
printf 'commit: %s\n' "$(git rev-parse HEAD)"
printf 'tags:   %s\n' "$(git tag --points-at HEAD | tr '\n' ' ')"

# --- 6. GPG key present ----------------------------------------------------
step "GPG signing key"
gpg --list-secret-keys "$GPG_FINGERPRINT" >/dev/null 2>&1 \
  || die "Secret GPG key $GPG_FINGERPRINT not found in the local keyring."
printf 'found secret key %s\n' "$GPG_FINGERPRINT"

# --- 7. Credentials configured --------------------------------------------
step "Maven Central credentials"
GRADLE_PROPS="${GRADLE_USER_HOME:-$HOME/.gradle}/gradle.properties"
for prop in mavenCentralUsername mavenCentralPassword signing.gnupg.keyName; do
  if [ -n "${!prop:-}" ]; then
    printf '%s: from environment\n' "$prop"
  elif [ -f "$GRADLE_PROPS" ] && grep -q "^${prop}=" "$GRADLE_PROPS"; then
    printf '%s: set in %s\n' "$prop" "$GRADLE_PROPS"
  else
    die "$prop is not configured (checked environment and $GRADLE_PROPS)."
  fi
done

GRADLE=(./gradlew "-PwaltidVersion=$VERSION" --no-configuration-cache)

# --- 8. Build and test the public libraries --------------------------------
step "Building and testing the Maven Central library set"
"${GRADLE[@]}" build

# --- 9/10. Local validation + the module list about to be uploaded ---------
step "Publishing to Maven Local for inspection"
"${GRADLE[@]}" publishToMavenLocal

step "Modules that will be uploaded to Maven Central"
MODULES="$("${GRADLE[@]}" -q centralPublishTargets 2>/dev/null || true)"
if [ -z "$MODULES" ]; then
  MODULES="$("${GRADLE[@]}" publishToMavenCentral --dry-run 2>/dev/null \
    | grep -oE '^:[a-z0-9:_-]+:publishToMavenCentral' | sed 's/:publishToMavenCentral$//' | sort -u)"
fi
[ -n "$MODULES" ] || die "Could not determine the module set. Aborting rather than uploading blind."
printf '%s\n' "$MODULES"
printf '\ncount: %s modules\n' "$(printf '%s\n' "$MODULES" | grep -c .)"

# --- 11. Explicit confirmation --------------------------------------------
step "Confirm"
cat <<EOF
About to UPLOAD version $VERSION to Maven Central.

The deployment will NOT be released automatically. You must review and publish it manually at:
  $DEPLOYMENTS_URL

Maven Central coordinates are immutable once published.
EOF
printf '\nType exactly "yes" to upload: '
read -r CONFIRM
[ "$CONFIRM" = "yes" ] || die "Aborted (got \"$CONFIRM\")."

# --- 12/13. Upload ---------------------------------------------------------
step "Uploading to Maven Central"
"${GRADLE[@]}" publishToMavenCentral

# --- 14. Where to review ---------------------------------------------------
step "Upload complete"
cat <<EOF
Deployment uploaded for version $VERSION.

Review, wait for validation, and publish MANUALLY at:
  $DEPLOYMENTS_URL

This script does not create or push a Git tag, and does not release the deployment.
EOF
