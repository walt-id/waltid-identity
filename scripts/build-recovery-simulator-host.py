#!/usr/bin/env python3
"""Build an isolated, entitled simulator application for the Kotlin recovery qualification phases."""
import argparse
import os
from pathlib import Path
import plistlib
import shutil
import subprocess

parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument("--output", type=Path, required=True)
args = parser.parse_args()
root = Path(__file__).resolve().parents[1]
output = args.output.resolve()
output.mkdir(parents=True, exist_ok=True)
app = output / "RecoveryTests.app"
app.mkdir(exist_ok=True)
bundle = "id.walt.wallet.recovery-tests"
entitlements = output / "simulator.entitlements"
entitlements.write_bytes(plistlib.dumps({"application-identifier": bundle, "keychain-access-groups": [bundle]}))
(app / "Info.plist").write_bytes(plistlib.dumps({
    "CFBundleIdentifier": bundle, "CFBundleExecutable": "RecoveryTests", "CFBundleName": "RecoveryTests",
    "CFBundlePackageType": "APPL", "CFBundleVersion": "1", "CFBundleShortVersionString": "1.0",
    "MinimumOSVersion": "16.0", "UIDeviceFamily": [1, 2],
}))
init = output / "ios-host.init.gradle"
init.write_text("""gradle.projectsEvaluated {
    def module = gradle.rootProject.findProject(':waltid-libraries:protocols:waltid-openid4vc-wallet-mobile')
    if (module == null) return
    // Run the adapter's existing contract in the same entitled host, without duplicating its tests.
    module.extensions.getByName('kotlin').sourceSets.getByName('iosTest').kotlin.srcDir(
        gradle.rootProject.file('waltid-libraries/protocols/waltid-openid4vc-wallet-recovery-keychain/src/iosTest/kotlin'))
    def target = module.extensions.getByName('kotlin').targets.getByName('iosSimulatorArm64')
    target.binaries.getTest('DEBUG').linkerOpts('-sectcreate', '__TEXT', '__entitlements',
        System.getenv('RECOVERY_TEST_ENTITLEMENTS'), '-L' + System.getenv('RECOVERY_TEST_SWIFT_LIBS'))
}
""")
developer = os.environ.get("DEVELOPER_DIR") or subprocess.check_output(["xcode-select", "-p"], text=True).strip()
env = dict(os.environ, RECOVERY_TEST_ENTITLEMENTS=str(entitlements),
           RECOVERY_TEST_SWIFT_LIBS=developer + "/Toolchains/XcodeDefault.xctoolchain/usr/lib/swift/iphonesimulator")
log = output / "build.log"
with log.open("w") as stream:
    result = subprocess.run([str(root / "gradlew"),
        ":waltid-libraries:protocols:waltid-openid4vc-wallet-mobile:linkDebugTestIosSimulatorArm64",
        "-PenableIosBuild=true", "-PenableAndroidBuild=false", "-PenableIosRecoveryTests=true",
        "-I", str(init), "--console=plain"], cwd=root, env=env, stdout=stream, stderr=subprocess.STDOUT)
if result.returncode:
    raise SystemExit(f"Simulator host build failed; see {log}")
modules = root / "waltid-libraries/protocols"
shutil.copy2(modules / "waltid-openid4vc-wallet-mobile/build/bin/iosSimulatorArm64/debugTest/test.kexe", app / "RecoveryTests")
framework = app / "Frameworks/SQLCipher.framework"
shutil.copytree(modules / "waltid-openid4vc-wallet-persistence-mobile/build/kotlin/swiftImportDd/dd_iphonesimulator/Build/Products/Debug-iphonesimulator/PackageFrameworks/SQLCipher.framework",
                framework, dirs_exist_ok=True)
for target in (framework, app):
    subprocess.run(["codesign", "--force", "--sign", "-", str(target)], check=True)
print(f"Simulator test app: {app}")
print(f"Build log: {log}")
