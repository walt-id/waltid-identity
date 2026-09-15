#!/usr/bin/env python3
"""Run resumable recovery phases on an explicitly selected Android device or iOS simulator.

State and logs contain public identity metadata only. Reinstall is a separate, explicit command.
The APK must be this repository's isolated mobile instrumentation app, never a demo/personal app.
"""
import argparse
import base64
import hashlib
import json
import os
import plistlib
from pathlib import Path
import re
import subprocess
import sys
import uuid
import xml.etree.ElementTree as ET

from ios_simulator_test import PACKAGE as IOS_PACKAGE, run_test

PACKAGE = "id.walt.wallet2.mobile.test"
TEST = "id.walt.wallet2.mobile.test.BlockStoreRecoveryWorkflowTest#runPhase"
IOS_TEST = "id.walt.wallet2.mobile.test.KeychainRecoveryWorkflowTest.runPhase"


def execute(command, timeout=180):
    result = subprocess.run([str(item) for item in command], text=True, stdout=subprocess.PIPE,
                            stderr=subprocess.STDOUT, timeout=timeout)
    if result.returncode:
        raise RuntimeError(result.stdout)
    return result.stdout


def digest(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()


class Qualification:
    def __init__(self, args):
        self.args = args
        if args.device in ("booted", "all"):
            raise ValueError("Select an exact device serial or simulator UDID")
        self.directory = args.output.resolve()
        self.directory.mkdir(parents=True, exist_ok=True)
        self.state_file = self.directory / "checkpoint.json"
        if args.platform == "android":
            sdk_root = os.environ.get("ANDROID_HOME") or os.environ.get("ANDROID_SDK_ROOT")
            if not sdk_root:
                raise ValueError("Set ANDROID_HOME to an Android SDK with platform-tools and build-tools installed")
            sdk = Path(sdk_root)
            self.adb = sdk / "platform-tools/adb"
            build_tools = max((p for p in (sdk / "build-tools").iterdir() if (p / "aapt").is_file()),
                              key=lambda p: tuple(int(n) for n in re.findall(r"\d+", p.name)))
            manifest = execute([build_tools / "aapt", "dump", "badging", args.apk])
            if not re.search(r"^package: name='" + re.escape(PACKAGE) + "'", manifest, re.MULTILINE):
                raise ValueError("Expected the isolated mobile instrumentation APK")
            artifact = digest(args.apk)
        else:
            info = plistlib.loads((args.app / "Info.plist").read_bytes())
            if info["CFBundleIdentifier"] != IOS_PACKAGE:
                raise ValueError("Expected the isolated recovery simulator app")
            files = [(str(p.relative_to(args.app)), digest(p)) for p in sorted(args.app.rglob('*')) if p.is_file()]
            artifact = hashlib.sha256(json.dumps(files).encode()).hexdigest()
        if self.state_file.exists():
            self.state = json.loads(self.state_file.read_text())
            if self.state["device"] != args.device or self.state["artifact"] != artifact or self.state["platform"] != args.platform:
                raise ValueError("Resume requires the original device and exact application artifact")
            if str(uuid.UUID(self.state["runId"])) != self.state["runId"]:
                raise ValueError("Invalid run identifier")
            if self.state["storage"] != args.storage or self.state["restoreStorage"] != (args.restore_storage or args.storage):
                raise ValueError("Resume requires the original source and destination storage configuration")
            if "expected" in self.state:
                base64.b64decode(self.state["expected"], validate=True)
        else:
            if args.command not in ("prepare", "local-loss"):
                raise ValueError("Prepare a checkpoint first")
            self.state = {"runId": str(uuid.uuid4()), "device": args.device, "platform": args.platform, "artifact": artifact,
                          "storage": args.storage, "restoreStorage": args.restore_storage or args.storage, "events": [], "phase": "new"}
            self.save()

    def storage_for(self, phase):
        restored = any(e["phase"] == "restore" and e["outcome"] == "passed" for e in self.state["events"])
        return self.state["restoreStorage"] if phase == "restore" or (phase == "verify" and restored) else self.state["storage"]

    def ios_phase(self, phase):
        arguments = ["--ktest_filter=" + IOS_TEST, "--recoveryPhase=" + phase,
                     "--recoveryRun=" + self.state["runId"], "--recoveryStorage=" + self.storage_for(phase)]
        if "expected" in self.state:
            arguments.append("--recoveryExpected=" + self.state["expected"])
        return run_test(self.args.device, arguments)

    def shell(self, *args):
        return execute([self.adb, "-s", self.args.device, "shell", *args])

    def save(self):
        temporary = self.state_file.with_suffix(".tmp")
        temporary.write_text(json.dumps(self.state, indent=2) + "\n")
        temporary.replace(self.state_file)
        events = self.state["events"]
        suite_name = "recovery." + self.state["storage"] + ".to." + self.state["restoreStorage"]
        suite = ET.Element("testsuite", name=suite_name,
                           tests=str(len(events)), failures=str(sum(e["outcome"] != "passed" for e in events)))
        for index, event in enumerate(events):
            case = ET.SubElement(suite, "testcase", classname=suite_name,
                                 name=f"{index:02d}-{event['phase']}")
            if event["outcome"] != "passed":
                ET.SubElement(case, "failure", message=event.get("reason", "See " + event["log"]))
        ET.ElementTree(suite).write(self.directory / "results.xml", encoding="utf-8", xml_declaration=True)

    def phase(self, phase):
        allowed = {"prepare": {"new"}, "verify": {"prepare", "restore", "verify"},
                   "lose-local": {"prepare", "verify"}, "restore": {"lose-local", "reinstall"}}
        if phase != "cleanup" and self.state["phase"] not in allowed[phase]:
            raise ValueError(f"Cannot run {phase} after {self.state['phase']}")
        command = ["am", "instrument", "-w", "-r", "-e", "class", TEST,
                   "-e", "recoveryPhase", phase, "-e", "recoveryRun", self.state["runId"],
                   "-e", "recoveryStorage", self.storage_for(phase)]
        if "expected" in self.state:
            command += ["-e", "recoveryExpected", self.state["expected"]]
        command += [PACKAGE + "/androidx.test.runner.AndroidJUnitRunner"]
        log = f"{len(self.state['events']):02d}-{phase}.log"
        try:
            output = self.ios_phase(phase) if self.state.get("platform") == "ios-simulator" else self.shell(*command)
        except (RuntimeError, OSError, subprocess.TimeoutExpired) as error:
            (self.directory / log).write_text(str(error))
            self.state["events"].append({"phase": phase, "outcome": "failed", "log": log})
            self.save()
            raise
        (self.directory / log).write_text(output)
        passed = (bool(re.search(r"^OK \(1 test\)$", output, re.MULTILINE)) or "[  PASSED  ] 1 tests." in output) \
            and not any(marker in output for marker in ("FAILURES!!!", "INSTRUMENTATION_FAILED", "[  FAILED  ]")) \
            and not re.search(r"^INSTRUMENTATION_STATUS_CODE: -[1-4]$", output, re.MULTILINE)
        self.state["events"].append({"phase": phase, "outcome": "passed" if passed else "failed", "log": log})
        if passed and phase == "prepare":
            checkpoint = re.search(r"(?:recoveryCheckpoint|RECOVERY_CHECKPOINT)=([A-Za-z0-9+/=]+)", output)
            if checkpoint is None:
                passed = False
                self.state["events"][-1]["outcome"] = "failed"
                self.state["events"][-1]["reason"] = "Missing public checkpoint"
            else:
                try:
                    public = json.loads(base64.b64decode(checkpoint[1], validate=True))
                    if "d" in json.loads(public["publicJwk"]):
                        raise ValueError("Private material is forbidden in checkpoints")
                    self.state["expected"] = checkpoint[1]
                except (ValueError, KeyError, TypeError):
                    passed = False
                    self.state["events"][-1].update(outcome="failed", reason="Invalid public checkpoint")
        if passed:
            self.state["phase"] = phase
        self.save()
        if not passed:
            raise RuntimeError(f"{phase} failed; see {self.directory / log}")
        print(f"{phase}: passed", flush=True)

    def install(self):
        if self.args.platform == "android":
            execute([self.adb, "-s", self.args.device, "install", "-r", "-t", self.args.apk])
        else:
            execute(["xcrun", "simctl", "install", self.args.device, self.args.app.resolve()])

    def reinstall(self):
        if self.state.get("platform") == "ios-simulator":
            raise ValueError("iOS reinstall is not a Keychain-loss test; use lose-local")
        if self.state["phase"] == "reinstall":
            self.install()
            return
        if self.state["phase"] not in ("prepare", "verify"):
            raise ValueError("Reinstall requires a prepared and verified test wallet")
        result = execute([self.adb, "-s", self.args.device, "uninstall", PACKAGE])
        if "Success" not in result:
            raise RuntimeError("Test app uninstall did not succeed")
        self.state["phase"] = "reinstall"
        self.save()
        self.install()


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("command", choices=["prepare", "verify", "lose-local", "restore", "cleanup", "reinstall", "local-loss"])
    parser.add_argument("--platform", choices=["android", "ios-simulator"], default="android")
    parser.add_argument("--device", required=True, help="Exact adb serial or simulator UDID")
    parser.add_argument("--apk", type=Path)
    parser.add_argument("--app", type=Path)
    parser.add_argument("--output", type=Path, required=True, help="Dedicated run directory, preserved across invocations")
    parser.add_argument("--storage", choices=["EncryptedDatabase", "NativeStorage", "Hardware"], default="EncryptedDatabase")
    parser.add_argument("--restore-storage", choices=["EncryptedDatabase", "NativeStorage", "Hardware"],
                        help="Destination storage; defaults to --storage")
    args = parser.parse_args()
    if (args.platform == "android" and not args.apk) or (args.platform == "ios-simulator" and not args.app):
        parser.error("Provide --apk for Android or --app for the iOS simulator")
    run = Qualification(args)
    if args.command in ("prepare", "local-loss"):
        run.install()
    if args.command == "local-loss":
        try:
            for phase in ("prepare", "verify", "lose-local", "restore", "verify"):
                run.phase(phase)
        except Exception:
            try:
                run.phase("cleanup")
            except Exception as cleanup_error:
                print(f"Cleanup also failed: {cleanup_error}", file=sys.stderr)
            raise
        else:
            run.phase("cleanup")
    elif args.command == "reinstall":
        run.reinstall()
    else:
        run.phase(args.command)
    print(f"Evidence: {run.directory}")


if __name__ == "__main__":
    try:
        main()
    except (RuntimeError, ValueError, OSError, subprocess.TimeoutExpired) as error:
        print(str(error), file=sys.stderr)
        sys.exit(1)
