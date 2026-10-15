#!/usr/bin/env python3
"""Require portable test targets and platform adapters; reject unexpected skips."""

import argparse
import json
from pathlib import Path

from verify_test_results import EvidenceError, verify


REPORT_TASKS = {"jvm": "jvmTest", "android-host": "testAndroidHostTest",
                "ios-simulator": "iosSimulatorArm64Test", "js-node": "jsNodeTest"}
X509_MODULE = "waltid-libraries/crypto/waltid-x509"


def platform_suites(platform):
    # Require execution boundaries, not a second inventory of test methods.
    suites = {"waltid-libraries/credentials/waltid-mdoc-proximity": [], X509_MODULE: []}
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
        suites[f"{compose}/sharedLogic"] = []
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
        allowed_skips = []
        if module == X509_MODULE:
            # Ed25519 support remains a separate follow-up; all other cases must execute.
            allowed_skips.append("id.walt.certificate.x509.X509CertificateSigningTest#shouldSignCertificateWithEdKey")
            if args.platform in {"jvm", "js-node"}:
                allowed_skips.append("id.walt.certificate.x509.SignumPublicKeyInfoUtilTest#shouldRetrieveKeyInfoOfEd25519Key")
        try:
            counts = verify(list(args.root.glob(pattern)), required_classes=classes,
                            allowed_skips=allowed_skips,
                            forbidden_prefixes=("id.walt.proximity.physical.", "ProximityPhysicalDeviceTests"))
            results.append({"module": module, "platform": args.platform, **counts})
        except EvidenceError as error:
            errors.append(f"{module}: {error}")
    if errors or not results:
        parser.exit(1, "Test evidence rejected:\n" + "\n".join(errors or ["No suite assigned to platform"]) + "\n")
    print(json.dumps(results, indent=2))


if __name__ == "__main__":
    main()
