#!/usr/bin/env bash
set -euo pipefail

phase="${1:?Android device test phase is required}"
emulator_api_level="34"
emulator_profile=""
emulator_avd_name=""
emulator_test_options=""

case "$phase" in
  wallet-mobile)
    script="./waltid-identity/.github/scripts/mobile-ci/run-android-wallet-mobile-tests.sh"
    emulator_options="-no-snapshot-save -no-window -gpu swiftshader_indirect -noaudio -no-boot-anim"
    report_paths=$'waltid-identity/waltid-libraries/protocols/waltid-openid4vc-wallet-mobile/build/outputs/androidTest-results/**/*.xml\nwaltid-identity/waltid-libraries/crypto/waltid-crypto2-signum/build/outputs/androidTest-results/**/*.xml'
    artifact_paths=$'waltid-identity/waltid-libraries/protocols/waltid-openid4vc-wallet-mobile/build/reports/androidTests/**\nwaltid-identity/waltid-libraries/protocols/waltid-openid4vc-wallet-mobile/build/outputs/androidTest-results/**\nwaltid-identity/waltid-libraries/crypto/waltid-crypto2-signum/build/reports/androidTests/**\nwaltid-identity/waltid-libraries/crypto/waltid-crypto2-signum/build/outputs/androidTest-results/**'
    emulator_target="default"
    ;;
  compose-demo)
    # The default image has no Google Play services. Keep GMS-only classes out of
    # instrumentation discovery; their class-level assumption would otherwise
    # collapse the reported test count and make Gradle fail the phase.
    compose_demo_excluded_classes="id.walt.walletdemo.compose.android.DigitalCredentialSharingE2ETest,id.walt.walletdemo.compose.android.DigitalCredentialIssuanceE2ETest"
    script="ANDROID_TEST_NOT_CLASS=$compose_demo_excluded_classes ./waltid-identity/.github/scripts/mobile-ci/run-android-compose-demo-tests.sh"
    emulator_options="-no-snapshot-save -no-window -gpu swiftshader_indirect -noaudio -no-boot-anim"
    report_paths="waltid-identity/waltid-applications/waltid-wallet-demo-compose/androidApp/build/outputs/androidTest-results/**/*.xml"
    artifact_paths=$'waltid-identity/waltid-applications/waltid-wallet-demo-compose/androidApp/build/reports/androidTests/**\nwaltid-identity/waltid-applications/waltid-wallet-demo-compose/androidApp/build/outputs/androidTest-results/**'
    emulator_target="default"
    ;;
  dc-api-compose)
    # Dedicated Play Store lane for the GMS-gated Digital Credentials E2Es.
    dc_api_test_classes="id.walt.walletdemo.compose.android.DigitalCredentialSharingE2ETest,id.walt.walletdemo.compose.android.DigitalCredentialIssuanceE2ETest"
    script="ANDROID_TEST_CLASS=$dc_api_test_classes ./waltid-identity/.github/scripts/mobile-ci/run-android-dc-api-compose-tests.sh"
    # The cached artifact is the configured userdata disk, not a Quick Boot state. Always cold-boot
    # it so the first process/ADB/GMS state is recreated for every job and never restored from a
    # potentially poisoned host snapshot.
    emulator_options="-no-snapshot -no-snapshot-save -no-window -gpu auto -noaudio -no-boot-anim -camera-back none -memory 4096 -feature GLDirectMem,HasSharedSlotsHostMemoryAllocator"
    emulator_test_options="$emulator_options"
    emulator_avd_name="dc-api-api37-pixel7-playstore"
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

if [[ -z "$emulator_test_options" ]]; then
  emulator_test_options="$emulator_options"
fi

{
  echo "script=$script"
  echo "emulator_api_level=$emulator_api_level"
  echo "emulator_profile=$emulator_profile"
  echo "emulator_avd_name=$emulator_avd_name"
  echo "emulator_options=$emulator_options"
  echo "emulator_test_options=$emulator_test_options"
  echo "emulator_target=$emulator_target"
  echo "report_paths<<ANDROID_TEST_REPORT_PATHS"
  printf '%s\n' "$report_paths"
  echo "ANDROID_TEST_REPORT_PATHS"
  echo "artifact_paths<<ANDROID_TEST_ARTIFACT_PATHS"
  printf '%s\n' "$artifact_paths"
  echo "ANDROID_TEST_ARTIFACT_PATHS"
} >> "$GITHUB_OUTPUT"
