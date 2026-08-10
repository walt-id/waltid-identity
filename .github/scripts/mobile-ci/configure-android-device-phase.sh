#!/usr/bin/env bash
set -euo pipefail

phase="${1:?Android device test phase is required}"
emulator_api_level="34"
emulator_profile=""

case "$phase" in
  wallet-mobile)
    script="./waltid-identity/.github/scripts/mobile-ci/run-android-wallet-mobile-tests.sh"
    emulator_options="-no-snapshot-save -no-window -gpu swiftshader_indirect -noaudio -no-boot-anim"
    report_paths=$'waltid-identity/waltid-libraries/protocols/waltid-openid4vc-wallet-mobile/build/outputs/androidTest-results/**/*.xml\nwaltid-identity/waltid-libraries/crypto/waltid-crypto2-signum/build/outputs/androidTest-results/**/*.xml'
    artifact_paths=$'waltid-identity/waltid-libraries/protocols/waltid-openid4vc-wallet-mobile/build/reports/androidTests/**\nwaltid-identity/waltid-libraries/protocols/waltid-openid4vc-wallet-mobile/build/outputs/androidTest-results/**\nwaltid-identity/waltid-libraries/crypto/waltid-crypto2-signum/build/reports/androidTests/**\nwaltid-identity/waltid-libraries/crypto/waltid-crypto2-signum/build/outputs/androidTest-results/**'
    emulator_target="default"
    ;;
  compose-demo)
    script="./waltid-identity/.github/scripts/mobile-ci/run-android-compose-demo-tests.sh"
    emulator_options="-no-snapshot-save -no-window -gpu swiftshader_indirect -noaudio -no-boot-anim"
    report_paths="waltid-identity/waltid-applications/waltid-wallet-demo-compose/androidApp/build/outputs/androidTest-results/**/*.xml"
    artifact_paths=$'waltid-identity/waltid-applications/waltid-wallet-demo-compose/androidApp/build/reports/androidTests/**\nwaltid-identity/waltid-applications/waltid-wallet-demo-compose/androidApp/build/outputs/androidTest-results/**'
    emulator_target="default"
    ;;
  dc-api-compose)
    # Dedicated Play Store lane; these tests require Google Play services. Every Digital Credentials
    # test class must be named here: other phases only skip them, and a skip reads as green, so a
    # class left out of this list runs nowhere without failing anything.
    dc_api_test_classes="id.walt.walletdemo.compose.android.DigitalCredentialSharingE2ETest"
    script="ANDROID_TEST_CLASS=$dc_api_test_classes ./waltid-identity/.github/scripts/mobile-ci/run-android-compose-demo-tests.sh"
    emulator_options="-no-window -gpu auto -noaudio -no-boot-anim -camera-back none -memory 4096 -feature GLDirectMem,HasSharedSlotsHostMemoryAllocator"
    report_paths="waltid-identity/waltid-applications/waltid-wallet-demo-compose/androidApp/build/outputs/androidTest-results/**/*.xml"
    artifact_paths=$'waltid-identity/waltid-applications/waltid-wallet-demo-compose/androidApp/build/reports/androidTests/**\nwaltid-identity/waltid-applications/waltid-wallet-demo-compose/androidApp/build/outputs/androidTest-results/**'
    emulator_api_level="37.0"
    emulator_profile="pixel_7"
    emulator_target="playstore_ps16k"
    ;;
  enterprise-mobile)
    script="./waltid-identity/.github/scripts/mobile-ci/run-enterprise-android-mobile-tests.sh"
    emulator_options="-no-snapshot-save -no-window -gpu swiftshader_indirect -noaudio -no-boot-anim -memory 1536"
    report_paths="waltid-identity/waltid-libraries/protocols/waltid-openid4vc-wallet-mobile/build/outputs/androidTest-results/**/*.xml"
    artifact_paths=$'waltid-identity/waltid-libraries/protocols/waltid-openid4vc-wallet-mobile/build/reports/androidTests/**\nwaltid-identity/waltid-libraries/protocols/waltid-openid4vc-wallet-mobile/build/outputs/androidTest-results/**\nwaltid-identity-enterprise/waltid-enterprise-integration-tests/build/**'
    emulator_target="default"
    ;;
  *)
    echo "Unknown Android device test phase: $phase" >&2
    exit 1
    ;;
esac

{
  echo "script=$script"
  echo "emulator_api_level=$emulator_api_level"
  echo "emulator_profile=$emulator_profile"
  echo "emulator_options=$emulator_options"
  echo "emulator_target=$emulator_target"
  echo "report_paths<<ANDROID_TEST_REPORT_PATHS"
  printf '%s\n' "$report_paths"
  echo "ANDROID_TEST_REPORT_PATHS"
  echo "artifact_paths<<ANDROID_TEST_ARTIFACT_PATHS"
  printf '%s\n' "$artifact_paths"
  echo "ANDROID_TEST_ARTIFACT_PATHS"
} >> "$GITHUB_OUTPUT"
