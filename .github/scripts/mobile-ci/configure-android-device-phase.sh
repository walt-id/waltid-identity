#!/usr/bin/env bash
set -euo pipefail

phase="${1:?Android device test phase is required}"

case "$phase" in
  crypto2-signum)
    script="./waltid-identity/.github/scripts/mobile-ci/run-android-crypto2-signum-tests.sh"
    emulator_options="-no-snapshot-save -no-window -gpu swiftshader_indirect -noaudio -no-boot-anim"
    report_paths="waltid-identity/waltid-libraries/crypto/waltid-crypto2-signum/build/outputs/androidTest-results/**/*.xml"
    artifact_paths=$'waltid-identity/waltid-libraries/crypto/waltid-crypto2-signum/build/reports/androidTests/**\nwaltid-identity/waltid-libraries/crypto/waltid-crypto2-signum/build/outputs/androidTest-results/**'
    ;;
  wallet-mobile)
    script="./waltid-identity/.github/scripts/mobile-ci/run-android-wallet-mobile-tests.sh"
    emulator_options="-no-snapshot-save -no-window -gpu swiftshader_indirect -noaudio -no-boot-anim"
    report_paths="waltid-identity/waltid-libraries/protocols/waltid-openid4vc-wallet-mobile/build/outputs/androidTest-results/**/*.xml"
    artifact_paths=$'waltid-identity/waltid-libraries/protocols/waltid-openid4vc-wallet-mobile/build/reports/androidTests/**\nwaltid-identity/waltid-libraries/protocols/waltid-openid4vc-wallet-mobile/build/outputs/androidTest-results/**'
    ;;
  compose-demo)
    script="./waltid-identity/.github/scripts/mobile-ci/run-android-compose-demo-tests.sh"
    emulator_options="-no-snapshot-save -no-window -gpu swiftshader_indirect -noaudio -no-boot-anim"
    report_paths="waltid-identity/waltid-applications/waltid-wallet-demo-compose/androidApp/build/outputs/androidTest-results/**/*.xml"
    artifact_paths=$'waltid-identity/waltid-applications/waltid-wallet-demo-compose/androidApp/build/reports/androidTests/**\nwaltid-identity/waltid-applications/waltid-wallet-demo-compose/androidApp/build/outputs/androidTest-results/**'
    ;;
  enterprise-mobile)
    script="./waltid-identity/.github/scripts/mobile-ci/run-enterprise-android-mobile-tests.sh"
    emulator_options="-no-snapshot-save -no-window -gpu swiftshader_indirect -noaudio -no-boot-anim -memory 1536"
    report_paths="waltid-identity/waltid-libraries/protocols/waltid-openid4vc-wallet-mobile/build/outputs/androidTest-results/**/*.xml"
    artifact_paths=$'waltid-identity/waltid-libraries/protocols/waltid-openid4vc-wallet-mobile/build/reports/androidTests/**\nwaltid-identity/waltid-libraries/protocols/waltid-openid4vc-wallet-mobile/build/outputs/androidTest-results/**\nwaltid-identity-enterprise/waltid-enterprise-integration-tests/build/**'
    ;;
  *)
    echo "Unknown Android device test phase: $phase" >&2
    exit 1
    ;;
esac

{
  echo "script=$script"
  echo "emulator_options=$emulator_options"
  echo "report_paths=$report_paths"
  echo "artifact_paths<<ANDROID_TEST_ARTIFACT_PATHS"
  printf '%s\n' "$artifact_paths"
  echo "ANDROID_TEST_ARTIFACT_PATHS"
} >> "$GITHUB_OUTPUT"
