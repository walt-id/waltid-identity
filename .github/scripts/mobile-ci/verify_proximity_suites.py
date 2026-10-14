#!/usr/bin/env python3
"""Require the proximity test targets and critical platform adapter suites."""

import argparse
import json
from pathlib import Path

from verify_test_results import EvidenceError, verify


REPORT_TASKS = {"jvm": "jvmTest", "android-host": "testAndroidHostTest",
                "ios-simulator": "iosSimulatorArm64Test", "js-node": "jsNodeTest"}


def platform_suites(platform):
    # Require execution boundaries, not a second inventory of test methods.
    suites = {"waltid-libraries/credentials/waltid-mdoc-proximity": [
        "id.walt.mdoc.proximity.IndependentSessionVectorTest"]}
    mobile = "waltid-libraries/credentials/waltid-mdoc-proximity-mobile"
    compose = "waltid-applications/waltid-wallet-demo-compose"
    if platform == "android-host":
        suites[mobile] = [f"id.walt.mdoc.proximity.mobile.{name}" for name in (
            "AndroidCentralGattCallbackTest", "AndroidPeripheralGattCallbackTest",
            "AndroidMdocHostApduServiceTest", "AndroidNfcSessionRegistryTest", "AndroidWifiAwareCallbackTest")]
        suites["waltid-libraries/protocols/waltid-openid4vc-wallet-mobile"] = [
            "id.walt.wallet2.mobile.IndependentNfcReaderTest"]
        suites[f"{compose}/sharedUI"] = [f"id.walt.walletdemo.compose.ui.{name}" for name in (
            "PreparedProximitySharingAndroidTest", "WalletDemoProximityAndroidTest")]
    elif platform == "ios-simulator":
        suites[mobile] = [f"id.walt.mdoc.proximity.mobile.{name}" for name in (
            "IosCentralGattCallbackTest", "IosPeripheralGattCallbackTest")]
        suites[f"{compose}/sharedUI"] = ["id.walt.walletdemo.compose.ui.WalletDemoProximityIosTest"]
        suites[f"{compose}/sharedLogic"] = [f"id.walt.walletdemo.compose.logic.{name}" for name in (
            "CredentialDisplayNormalizerTest", "DemoSharingSettingsStoreTest", "StoredCredentialMetadataParserTest",
            "WalletDemoControllerSkipPinTest", "WalletDemoControllerTest", "WalletDemoKeySetupTest",
            "WalletDemoMetadataModelsTest", "walletapi2.WalletApi2AuthorizationCallbackTest",
            "walletapi2.WalletApi2MappingsTest", "walletapi2.WalletApiProfileTest", "walletapi2.WalletDeepLinkSchemeWebTest")]
    return suites


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--platform", required=True,
                        choices=REPORT_TASKS)
    parser.add_argument("--root", type=Path, default=Path(__file__).resolve().parents[3])
    args = parser.parse_args()
    results, errors = [], []
    for module, classes in platform_suites(args.platform).items():
        pattern = f"{module}/build/test-results/{REPORT_TASKS[args.platform]}/*.xml"
        try:
            counts = verify(list(args.root.glob(pattern)), required_classes=classes,
                            forbidden_prefixes=("id.walt.proximity.physical.", "ProximityPhysicalDeviceTests"))
            results.append({"module": module, "platform": args.platform, **counts})
        except EvidenceError as error:
            errors.append(f"{module}: {error}")
    if errors or not results:
        parser.exit(1, "Test evidence rejected:\n" + "\n".join(errors or ["No suite assigned to platform"]) + "\n")
    print(json.dumps(results, indent=2))


if __name__ == "__main__":
    main()
