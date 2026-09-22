#!/usr/bin/env python3
"""Prove the real simulator runner rejects empty selections and failing Kotlin tests."""
import argparse
from pathlib import Path
import uuid
import xml.etree.ElementTree as ET

from ios_simulator_test import passed_one_test, run_test

parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument("--device", required=True)
parser.add_argument("--output", type=Path, required=True)
args = parser.parse_args()
args.output.mkdir(parents=True, exist_ok=True)
suite = ET.Element("testsuite", name="recovery.runner", tests="2", failures="0")
failures = 0
for name, arguments in (
    ("rejects-empty-selection", ["--ktest_filter=no.such.recovery.Test"]),
    ("rejects-kotlin-failure", [
        "--ktest_filter=id.walt.wallet2.mobile.test.KeychainRecoveryWorkflowTest.runPhase",
        "--recoveryRun=" + str(uuid.uuid4()), "--recoveryPhase=invalid-runner-control",
    ]),
):
    case = ET.SubElement(suite, "testcase", classname="recovery.runner", name=name)
    rejected = False
    try:
        output = run_test(args.device, arguments)
    except RuntimeError as error:
        output, rejected = str(error), True
    (args.output / (name + ".log")).write_text(output)
    stopped = "RECOVERY_HOST_STOPPED=" in output
    expected = (
        not rejected and not passed_one_test(output) and "0 tests" in output
        if name == "rejects-empty-selection" else
        rejected and "[  FAILED  ]" in output and "Unknown recovery phase" in output
        and "exited without reporting completion" in output
    )
    if not (stopped and expected):
        failures += 1
        ET.SubElement(case, "failure", message="Runner did not reject the intended case with verified host exit; see " + name + ".log")
suite.set("failures", str(failures))
ET.ElementTree(suite).write(args.output / "results.xml", encoding="utf-8", xml_declaration=True)
if failures:
    raise SystemExit("Recovery runner negative controls failed")
print("Recovery runner rejected empty selection and Kotlin failure; both hosts exited")
