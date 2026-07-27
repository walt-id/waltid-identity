#!/usr/bin/env bash
set -euo pipefail

phase="${1:?Android device test phase is required}"
emulator_api_level="34"

case "$phase" in
  wallet-mobile)
    script="./waltid-identity/.github/scripts/mobile-ci/run-android-wallet-mobile-tests.sh"
    emulator_options="-no-snapshot-save -no-window -gpu swiftshader_indirect -noaudio -no-boot-anim"
    report_paths="waltid-identity/waltid-libraries/protocols/waltid-openid4vc-wallet-mobile/build/outputs/androidTest-results/**/*.xml"
    artifact_paths=$'waltid-identity/waltid-libraries/protocols/waltid-openid4vc-wallet-mobile/build/reports/androidTests/**\nwaltid-identity/waltid-libraries/protocols/waltid-openid4vc-wallet-mobile/build/outputs/androidTest-results/**'
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
    script="play_services_version=\$(adb shell dumpsys package com.google.android.gms | sed -n 's/.*versionCode=\\([0-9]*\\).*/\\1/p' | head -n 1); if [[ -z \"\$play_services_version\" || \"\$play_services_version\" -lt 243100000 ]]; then echo \"::error::Digital Credentials requires Google Play services version 243100000 or newer; found \${play_services_version:-missing}\"; exit 1; fi; ANDROID_TEST_CLASS=id.walt.walletdemo.compose.android.DigitalCredentialSharingE2ETest ./waltid-identity/.github/scripts/mobile-ci/run-android-compose-demo-tests.sh"
    emulator_options="-no-snapshot-save -no-window -gpu swiftshader_indirect -noaudio -no-boot-anim"
    report_paths="waltid-identity/waltid-applications/waltid-wallet-demo-compose/androidApp/build/outputs/androidTest-results/**/*.xml"
    artifact_paths=$'waltid-identity/waltid-applications/waltid-wallet-demo-compose/androidApp/build/reports/androidTests/**\nwaltid-identity/waltid-applications/waltid-wallet-demo-compose/androidApp/build/outputs/androidTest-results/**\nwaltid-identity/waltid-applications/waltid-wallet-demo-compose/androidApp/build/outputs/connected_android_test_additional_output/**'
    emulator_api_level="37.0"
    emulator_target="google_apis_playstore"
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
  echo "emulator_options=$emulator_options"
  echo "emulator_target=$emulator_target"
  echo "report_paths=$report_paths"
  echo "artifact_paths<<ANDROID_TEST_ARTIFACT_PATHS"
  printf '%s\n' "$artifact_paths"
  echo "ANDROID_TEST_ARTIFACT_PATHS"
} >> "$GITHUB_OUTPUT"
