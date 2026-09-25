#!/usr/bin/env python3
"""Explicit local, selected-device holder/reader test controller. Never invoked by CI."""

import argparse
import hashlib
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
import ipaddress
import json
import os
from pathlib import Path
import plistlib
import re
import secrets
import shlex
import signal
import subprocess
import sys
import threading
import time
import uuid

ROOT = Path(__file__).resolve().parents[2]
sys.path.insert(0, str(ROOT / ".github/scripts/mobile-ci"))
from proximity_physical_preflight import Device, FIXTURE, PEER_REVISION, PreconditionError, preflight, reject_ci
from verify_test_results import verify

SDK = ROOT / "waltid-libraries/protocols/waltid-openid4vc-wallet-mobile"
NATIVE = ROOT / "waltid-libraries/protocols/waltid-wallet-sdk-ios/PhysicalTests"
PACKAGE = "id.walt.proximity.physical"
SDK_TASK = ":waltid-libraries:protocols:waltid-openid4vc-wallet-mobile:"


def command(args, *, timeout=30, data=None, check=True, environment=None):
    result = subprocess.run(list(map(str, args)), cwd=ROOT, input=data, text=True, capture_output=True,
                            timeout=timeout, env=environment)
    if check and result.returncode:
        raise PreconditionError(f"{Path(str(args[0])).name} failed; inspect the retained private diagnostics")
    return result


def adb(serial, *args, **options):
    return command(["adb", "-s", serial, *args], **options)


def android_device(serial):
    if not re.fullmatch(r"[A-Za-z0-9_.:-]+", serial):
        raise PreconditionError("Invalid explicit Android serial")
    available = adb(serial, "get-state", check=False).stdout.strip() == "device"
    if not available:
        return Device(serial, "android", False, False, 0, frozenset())
    qemu = adb(serial, "shell", "getprop", "ro.kernel.qemu").stdout.strip()
    level = int(adb(serial, "shell", "getprop", "ro.build.version.sdk").stdout.strip())
    features = adb(serial, "shell", "pm", "list", "features").stdout.splitlines()
    capabilities = {label for feature, label in [("bluetooth_le", "ble"), ("nfc", "nfc-reader"), ("nfc.hce", "nfc-host")]
                    if f"feature:android.hardware.{feature}" in features}
    return Device(serial, "android", qemu != "1" and not serial.startswith("emulator-"), available, level, frozenset(capabilities))


def ios_device(identifier):
    result = json.loads(command(["xcodebuildmcp", "device", "list", "--output", "json"]).stdout)
    if result.get("schema") != "xcodebuildmcp.output.device-list" or result.get("didError"):
        raise PreconditionError("Unable to obtain authoritative iOS device discovery")
    matches = [item for item in result["data"]["devices"] if item["deviceId"] == identifier]
    if len(matches) != 1 or matches[0]["platform"] != "iOS":
        raise PreconditionError("Select exactly one available physical iPhone")
    item = matches[0]
    # Runtime CoreBluetooth and CardSession checks remain mandatory in the test itself.
    return Device(identifier, "ios", True, item["isAvailable"], int(item["osVersion"].split(".")[0]),
                  frozenset({"ble", "nfc-host"}))


def clean_source():
    if command(["git", "status", "--porcelain"]).stdout.strip():
        raise PreconditionError("Commit or isolate changes before a physical run so its exact source is reproducible")
    return command(["git", "rev-parse", "HEAD"]).stdout.strip()


def build(args, evidence, *, environment=None):
    result = command(args, timeout=1800, check=False, environment=environment)
    evidence.write_text(result.stdout + result.stderr)
    if result.returncode:
        raise PreconditionError(f"Build/setup failed: {evidence.name}")
    if str(args[0]) == "xcodebuildmcp" and json.loads(result.stdout).get("didError"):
        raise PreconditionError(f"Xcode build/setup failed: {evidence.name}")


class Android:
    def __init__(self, serial, run_id, role):
        self.serial, self.run_id, self.role = serial, run_id, role
        self.folder = f"files/proximity-physical/{run_id}"

    def shell(self, *args, **options):
        return adb(self.serial, "shell", shlex.join(["run-as", PACKAGE, *args]), **options)

    def event(self, name):
        result = self.shell("cat", f"{self.folder}/{name}.json", check=False, timeout=3)
        return json.loads(result.stdout) if result.returncode == 0 else None

    def send(self, name, value):
        self.shell("mkdir", "-p", self.folder)
        self.shell("sh", "-c", f"cat > {self.folder}/{name}.tmp", data=json.dumps(value))
        self.shell("mv", f"{self.folder}/{name}.tmp", f"{self.folder}/{name}.json")

    def launch(self, args, log):
        cls = "AndroidHolderPhysicalTest" if self.role == "holder" else "AndroidReaderPhysicalTest"
        options = dict(class_=f"id.walt.proximity.physical.{cls}", physicalOptIn=args.opt_in,
                       selectedDeviceId=self.serial, controllerIsLocal="true", runId=self.run_id,
                       configuration=args.configuration, fixture=FIXTURE, peerRevision=PEER_REVISION,
                       annotation="id.walt.mobile.test.PhysicalDeviceTest")
        invocation = ["am", "instrument", "-w", "-r"]
        for key, value in options.items():
            invocation += ["-e", key.removesuffix("_"), value]
        invocation += [PACKAGE + "/androidx.test.runner.AndroidJUnitRunner"]
        return subprocess.Popen(["adb", "-s", self.serial, "shell", shlex.join(invocation)],
                                stdout=log, stderr=subprocess.STDOUT, start_new_session=True)

    def cleanup(self):
        adb(self.serial, "shell", "am", "force-stop", PACKAGE)
        self.shell("rm", "-rf", self.folder)


class IOS:
    def __init__(self, run_id, host_address):
        self.run_id = run_id
        self.token = secrets.token_hex(32)
        self.events, self.commands = {}, {}
        self.lock = threading.Lock()
        owner = self

        class Handler(BaseHTTPRequestHandler):
            def setup(self):
                super().setup()
                self.connection.settimeout(3)

            def log_message(self, *_):
                pass

            def handle_request(self, posting):
                if self.headers.get("Authorization") != "Bearer " + owner.token:
                    self.send_error(403); return
                parts = self.path.strip("/").split("/")
                if len(parts) != 3 or parts[0] != run_id or not re.fullmatch(r"[a-z0-9-]+", parts[2]):
                    self.send_error(404); return
                if posting and parts[1] == "events":
                    try:
                        size = int(self.headers.get("Content-Length", "0"))
                        if not 0 < size <= 65_536:
                            self.send_error(413); return
                        value = json.loads(self.rfile.read(size))
                        if (not isinstance(value, dict) or value.get("event") != parts[2]
                                or value.get("role") != "holder"
                                or not all(isinstance(v, str) for v in value.values())):
                            self.send_error(400); return
                    except (ValueError, OSError):
                        self.send_error(400); return
                    with owner.lock:
                        if parts[2] in owner.events:
                            self.send_error(409); return
                        owner.events[parts[2]] = value
                    response = {}
                elif not posting and parts[1] == "commands":
                    with owner.lock:
                        response = owner.commands.get(parts[2])
                    if response is None:
                        self.send_error(404); return
                else:
                    self.send_error(404); return
                body = json.dumps(response).encode()
                self.send_response(200)
                self.send_header("Content-Type", "application/json")
                self.send_header("Content-Length", str(len(body)))
                self.end_headers(); self.wfile.write(body)

            def do_GET(self): self.handle_request(False)
            def do_POST(self): self.handle_request(True)

        self.server = ThreadingHTTPServer((host_address, 0), Handler)
        self.server.timeout = 1
        self.thread = threading.Thread(target=self.server.serve_forever, daemon=True)
        self.thread.start()
        self.url = f"http://{host_address}:{self.server.server_port}"

    def event(self, name):
        with self.lock: return self.events.get(name)

    def send(self, name, value):
        with self.lock: self.commands[name] = value

    def close(self):
        self.server.shutdown(); self.server.server_close(); self.thread.join(timeout=3)


def wait_event(endpoint, name, workers, deadline=60):
    end = time.monotonic() + deadline
    while time.monotonic() < end:
        value = endpoint.event(name)
        if value is not None:
            return value
        if any(worker.poll() is not None and worker.returncode != 0 for worker in workers):
            raise PreconditionError(f"Test process failed before {name}")
        time.sleep(0.1)
    raise PreconditionError(f"Physical phase timed out: {name}")


def validate_event(value, name, configuration):
    if (not isinstance(value, dict) or value.get("event") != name
            or value.get("role") != name.split("-", 1)[0]
            or not str(value.get("elapsedNanos", "")).isdigit()):
        raise PreconditionError(f"Invalid physical phase evidence: {name}")
    expected = {}
    if name.endswith("-started"):
        expected = {"configuration": configuration}
    elif "-review-" in name:
        expected = {"engagement": "Nfc" if configuration.startswith("nfc") else "Qr",
                    "transport": "Nfc" if configuration == "nfc-direct-disconnect" else "BluetoothLowEnergy"}
    elif "-connected-" in name:
        expected = {"bearer": "nfc" if configuration == "nfc-direct-disconnect"
                    else "l2cap" if configuration.startswith("ble-l2cap") else "gatt"}
        if expected["bearer"] == "l2cap" and not 1 <= int(value.get("psm", 0)) <= 65535:
            raise PreconditionError("Missing actual physical L2CAP PSM")
    elif "-verified-" in name:
        expected = dict(fieldCount="2", issuerAuthenticated="true", deviceAuthenticated="true", issuerTrusted="true")
        if configuration == "nfc-direct-disconnect" and int(value.get("getResponseCount", 0)) < 1:
            raise PreconditionError("Missing physical NFC fragmentation evidence")
    elif "-completed-" in name:
        expected = dict(approvedFieldCount="2")
    elif name == "reader-disconnected-2":
        expected = dict(receivedData="false")
    elif name == "holder-rejected-2":
        expected = dict(disclosed="false", approvalRejected="true")
    elif name.endswith("-passed"):
        expected = dict(rounds="3")
        if name.startswith("reader"): expected["peerRevision"] = PEER_REVISION
    if any(value.get(key) != field for key, field in expected.items()):
        raise PreconditionError(f"Physical phase oracle mismatch: {name}")


def cleanup_run(workers, endpoints, ios, holder_id, logs, report):
    errors = []
    def attempt(action):
        try: action()
        except (OSError, subprocess.SubprocessError, PreconditionError, ValueError):
            errors.append("Owned resource cleanup failed; inspect private diagnostics")
    def stop_worker(worker):
        if worker.poll() is None:
            os.killpg(worker.pid, signal.SIGTERM)
            try: worker.wait(timeout=5)
            except subprocess.TimeoutExpired:
                os.killpg(worker.pid, signal.SIGKILL)
                worker.wait(timeout=5)
    for worker in workers: attempt(lambda: stop_worker(worker))
    for endpoint in endpoints: attempt(endpoint.cleanup)
    if ios:
        event = ios.event("holder-started")
        if event and str(event.get("processId", "")).isdigit():
            def stop_ios():
                result = command(["xcodebuildmcp", "device", "stop", "--device-id", holder_id,
                                  "--process-id", str(event["processId"]), "--output", "json"])
                if json.loads(result.stdout).get("didError"):
                    raise PreconditionError("Unable to stop the selected iOS test host")
            attempt(stop_ios)
        elif workers:
            errors.append("iOS process identity unavailable; inspect the selected disposable host")
        attempt(ios.close)
    for log in logs: attempt(log.close)
    if errors:
        report.update(status="failed", cleanupRequired=True, cleanupErrors=errors)


def signed_ios_host(args, app):
    info = plistlib.loads((app / "Info.plist").read_bytes())
    signed = plistlib.loads(command(["codesign", "-d", "--entitlements", ":-", app]).stdout.encode())
    if info.get("CFBundleIdentifier") != args.ios_bundle or signed.get("com.apple.developer.team-identifier") != args.ios_team:
        raise PreconditionError("Built host signing identity does not match the selected disposable host")
    prefixes = set(signed.get("com.apple.developer.nfc.hce.iso7816.select-identifier-prefixes", []))
    return dict(bundle_id=args.ios_bundle, team_id=args.ios_team,
                card_session_entitled=signed.get("com.apple.developer.nfc.hce") is True
                and {"D2760000850101", "A0000002480400", "A0000002480401"} <= prefixes)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--opt-in", required=True)
    parser.add_argument("--holder-platform", choices=["android", "ios"], required=True)
    parser.add_argument("--holder-id", required=True)
    parser.add_argument("--reader-id", required=True)
    parser.add_argument("--configuration", required=True)
    parser.add_argument("--ios-bundle")
    parser.add_argument("--ios-team")
    parser.add_argument("--ios-entitlements", type=Path)
    parser.add_argument("--controller-address", help="This Mac's explicit private LAN IPv4 address for an iOS holder")
    parser.add_argument("--output", type=Path, default=ROOT / "build/proximity-physical")
    args = parser.parse_args()
    reject_ci(os.environ)
    if args.opt_in != "physical-local":
        raise PreconditionError("Explicit physical-local opt-in is required")
    source = clean_source()
    reader_device = android_device(args.reader_id)
    holder_device = android_device(args.holder_id) if args.holder_platform == "android" else ios_device(args.holder_id)
    if args.holder_platform == "ios":
        if not args.ios_bundle or not args.ios_bundle.endswith(".proximityphysical") or not args.ios_team:
            raise PreconditionError("Select a separate signed host ending in .proximityphysical and its team")
        address = ipaddress.IPv4Address(args.controller_address or "0.0.0.0")
        if not address.is_private or address.is_loopback or address.is_unspecified:
            raise PreconditionError("Select this Mac's private LAN address for the authenticated local controller")
        if args.configuration == "nfc-direct-disconnect":
            raise PreconditionError("iOS direct NFC needs a separate prepared-sharing procedure; this suite does not claim it")
    else:
        preflight(environment=os.environ, opt_in=args.opt_in, holder_id=args.holder_id, reader_id=args.reader_id,
                  devices=[holder_device, reader_device], configuration=args.configuration, fixture=FIXTURE, peer_revision=PEER_REVISION)
    run_id = str(uuid.uuid4())
    output = args.output.resolve() / run_id
    private = output / "private"
    private.mkdir(parents=True, mode=0o700)
    events, workers, logs, endpoints = [], [], [], []
    ios = None
    report = dict(executionClass="physical-local", runId=run_id, source=source,
                  peerRevision=PEER_REVISION, fixture=FIXTURE, configuration=args.configuration,
                  holderPlatform=args.holder_platform, holderOS=holder_device.os_major, readerAndroidAPI=reader_device.os_major,
                  status="failed", events=events, timingBasis="Controller observation time; device monotonic clocks are not compared")
    started = time.monotonic()
    try:
        print("Building selected test artifacts. Hardware exchanges have not started.", flush=True)
        build([ROOT / "gradlew", SDK_TASK + "assembleAndroidDeviceTest", "-PenableProximityPhysicalTests=true",
               "-PenableAndroidBuild=true", "-PenableIosBuild=false", "--console=plain"], private / "android-build.log")
        apk_root = SDK / "build/outputs/apk/androidTest"
        metadata = json.loads((apk_root / "output-metadata.json").read_text())
        if metadata["applicationId"] != PACKAGE or len(metadata["elements"]) != 1:
            raise PreconditionError("Expected the isolated physical APK, not a default instrumented artifact")
        apk = apk_root / metadata["elements"][0]["outputFile"]
        report["androidApkSha256"] = hashlib.sha256(apk.read_bytes()).hexdigest()
        native_args, native_env = None, None
        if args.holder_platform == "ios":
            build([ROOT / "gradlew", SDK_TASK + "assembleWalletCorePhysicalFixturesReleaseXCFramework",
                   "-PenableWalletSdkPhysicalFixtures=true", "-PenableAndroidBuild=false", "-PenableIosBuild=true", "--console=plain"], private / "ios-framework.log")
            derived = output / "derived"
            native_env = dict(os.environ, WALLET_SDK_PHYSICAL_FIXTURES="1")
            settings = [f"PHYSICAL_HOST_BUNDLE_ID={args.ios_bundle}", f"PHYSICAL_TEAM_ID={args.ios_team}"]
            if args.ios_entitlements: settings.append(f"PHYSICAL_HOST_ENTITLEMENTS={args.ios_entitlements.resolve()}")
            base = dict(projectPath=str(NATIVE / "PhysicalTests.xcodeproj"), scheme="ProximityPhysicalDeviceTests",
                        configuration="Debug", derivedDataPath=str(derived))
            build(["xcodebuildmcp", "device", "build", "--json", json.dumps(base | {"extraArgs": ["build-for-testing", *settings]}), "--output", "json"],
                  private / "ios-build.json", environment=native_env)
            signed = signed_ios_host(args, derived / "Build/Products/Debug-iphoneos/PhysicalHost.app")
            preflight(environment=os.environ, opt_in=args.opt_in, holder_id=args.holder_id, reader_id=args.reader_id,
                      devices=[holder_device, reader_device], configuration=args.configuration, fixture=FIXTURE,
                      peer_revision=PEER_REVISION, signed_host=signed)
            report["signedHost"] = signed
            ios = IOS(run_id, args.controller_address)
            values = dict(PROXIMITY_OPT_IN=args.opt_in, PROXIMITY_DEVICE_ID=args.holder_id, PROXIMITY_READER_ID=args.reader_id,
                          PROXIMITY_FIXTURE=FIXTURE, PROXIMITY_PEER_REVISION=PEER_REVISION, PROXIMITY_HOST_BUNDLE_ID=args.ios_bundle,
                          PROXIMITY_TEAM_ID=args.ios_team, PROXIMITY_RUN_ID=run_id, PROXIMITY_CONFIGURATION=args.configuration,
                          PROXIMITY_CONTROL_URL=ios.url, PROXIMITY_CONTROL_TOKEN=ios.token)
            native_args = base | dict(deviceId=args.holder_id, extraArgs=[*settings,
                *[f"{key}={value}" for key, value in values.items()], "-resultBundlePath", str(private / "physical.xcresult")])
        if clean_source() != source:
            raise PreconditionError("Source changed while building the physical artifacts")
        reader = Android(args.reader_id, run_id, "reader")
        holder = Android(args.holder_id, run_id, "holder") if args.holder_platform == "android" else ios
        endpoints = [reader] + ([holder] if args.holder_platform == "android" else [])
        for endpoint in endpoints:
            adb(endpoint.serial, "install", "-r", "-g", apk, timeout=120)
        print("Starting disposable test hosts. Accept their OS prompts and position phones for NFC when requested.", flush=True)
        for role, endpoint in [("holder", holder), ("reader", reader)]:
            log = (private / f"{role}-test.log").open("w")
            logs.append(log)
            worker = endpoint.launch(args, log) if isinstance(endpoint, Android) else subprocess.Popen(
                ["xcodebuildmcp", "device", "test", "--json", json.dumps(native_args), "--output", "json"],
                stdout=log, stderr=subprocess.STDOUT, env=native_env, start_new_session=True)
            workers.append(worker)
        def observe(endpoint, name, deadline=60):
            value = wait_event(endpoint, name, workers, deadline)
            validate_event(value, name, args.configuration)
            allowed = {"role", "event", "elapsedNanos", "configuration", "bearer", "psm", "engagement", "transport",
                       "fieldCount", "approvedFieldCount", "issuerAuthenticated", "deviceAuthenticated", "issuerTrusted",
                       "getResponseCount", "receivedData", "disclosed", "approvalRejected", "rounds", "peerRevision"}
            safe = {key: field for key, field in value.items() if key in allowed}
            if "root" in value: safe["issuerAnchorSha256"] = hashlib.sha256(bytes.fromhex(value["root"])).hexdigest()
            safe["controllerElapsedMs"] = round((time.monotonic() - started) * 1000)
            events.append(safe)
            (output / "result.json").write_text(json.dumps(report, indent=2) + "\n")
            return value
        observe(holder, "holder-started", 120)
        observe(reader, "reader-started")
        for round_number in range(1, 4):
            ready = observe(holder, f"holder-ready-{round_number}")
            reader.send(f"peer-input-{round_number}", ready)
            if args.configuration.startswith("nfc"):
                print(f"Round {round_number}: hold the phones together; the reader controls NFC field loss.", flush=True)
            observe(reader, f"reader-connected-{round_number}")
            if args.configuration == "nfc-ble-continuation": observe(reader, f"reader-nfc-field-disabled-{round_number}")
            observe(holder, f"holder-review-{round_number}")
            if round_number == 2:
                reader.send("disconnect-2", {})
                observe(reader, "reader-disconnected-2", 15)
                observe(holder, "holder-rejected-2", 15)
            else:
                holder.send(f"approve-{round_number}", {})
                observe(reader, f"reader-verified-{round_number}", 45)
                observe(holder, f"holder-completed-{round_number}", 30)
        observe(holder, "holder-passed")
        observe(reader, "reader-passed")
        for worker in workers:
            if worker.wait(timeout=30) != 0: raise PreconditionError("A physical test process failed")
        for log in logs: log.flush()
        for role in (["holder", "reader"] if args.holder_platform == "android" else ["reader"]):
            text = (private / f"{role}-test.log").read_text()
            if not re.search(r"OK \(1 test\)", text) or "FAILURES!!!" in text:
                raise PreconditionError(f"Missing passing nonzero {role} instrumentation verdict")
        if ios:
            xml = private / "physical.xml"
            parsed = command(["xcresultparser", "-o", "junit", private / "physical.xcresult"], timeout=60)
            xml.write_text(parsed.stdout)
            verify([xml], [{"class": "ProximityPhysicalDeviceTests", "name": "testSuccessDisconnectAndFreshRecovery"}])
        if clean_source() != source: raise PreconditionError("Source changed during the physical run")
        report.update(status="passed", executedTests=2, skippedTests=0)
    except BaseException as error:
        report["failureType"] = type(error).__name__
        raise
    finally:
        cleanup_run(workers, endpoints, ios, args.holder_id, logs, report)
        report["durationSeconds"] = round(time.monotonic() - started, 3)
        (output / "result.json").write_text(json.dumps(report, indent=2) + "\n")
        print(f"Physical run {report['status']}: {output / 'result.json'}", flush=True)
    if report.get("cleanupRequired"):
        raise PreconditionError("Physical cleanup was incomplete; the run is failed")


if __name__ == "__main__":
    try: main()
    except (PreconditionError, OSError, subprocess.SubprocessError, ValueError) as error:
        sys.exit(f"Physical run failed: {error}")
