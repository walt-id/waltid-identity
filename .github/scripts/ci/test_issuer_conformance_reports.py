import copy
import json
import os
from pathlib import Path
import shutil
import subprocess
import tempfile
import unittest

import issuer_conformance_reports as reports


class IssuerConformanceReportsTest(unittest.TestCase):
    def setUp(self):
        self.directory = tempfile.TemporaryDirectory()
        self.addCleanup(self.directory.cleanup)
        self.root = Path(self.directory.name)
        self.shards = self.root / "shards"
        self.output = self.root / "merged"

    def make_shard(self, proof, *, setup="success", test="success", mutate=None):
        raw, matrix = [], []
        for index in range(20):
            variant_id = f"variant-{index}-proof-{proof}"
            raw.append(dict(
                variantId=variant_id, variant={"credential_proof_type": proof}, status="PASSED",
                planId=f"plan-{index}", error="PRIVATE ERROR", credentials="PRIVATE CREDENTIAL",
                modules=[dict(testModule="oid4vci-1_0-issuer-happy-flow", testId=f"test-{index}",
                              status="FINISHED", result="PASSED", accepted=True,
                              error="PRIVATE MODULE ERROR", log="PRIVATE LOG")],
            ))
            coverage = "PASSED" if index < 16 else "NOT_OFFERED_BY_PINNED_SUITE"
            matrix.append(dict(variantId=variant_id, batchCoverage=coverage))
            if index < 16:
                raw[-1]["modules"].append(dict(
                    testModule="oid4vci-1_0-issuer-batch-issuance", testId=f"batch-{index}",
                    status="FINISHED", result="PASSED", accepted=True,
                ))
        if mutate:
            mutate(raw, matrix)
        source = self.root / "raw" / proof
        reports.write_json(source / "results.json", raw)
        reports.write_json(source / "matrix.json", matrix)
        destination = self.shards / f"issuer-conformance-proof-{proof}"
        errors = reports.prepare(source, destination, proof, setup, test)
        (destination / "identity-revision.txt").write_text("identity-commit\n")
        reports.write_json(destination / "suite-version.json", reports.SUITE_PIN)
        return destination, errors

    def make_both(self):
        for proof in reports.PROOFS:
            _, errors = self.make_shard(proof)
            self.assertEqual(errors, [])

    def test_full_results_keep_artifact_schema_and_coverage(self):
        self.make_both()
        self.assertEqual(reports.merge(self.shards, self.output), [])
        results = reports.read_json(self.output / "results.json", [])
        self.assertEqual(len(results), 40)
        self.assertEqual(set(results[0]), {"variantId", "status", "planId", "proofType", "batchCoverage", "modules"})
        self.assertEqual(set(results[0]["modules"][0]),
                         {"testModule", "testId", "status", "result", "accepted", "failureSummary"})
        for proof in reports.PROOFS:
            self.assertEqual(reports.read_json(self.output / proof / "results.json", []),
                             [row for row in results if row["proofType"] == proof])
        self.assertEqual(reports.read_json(self.output / "coverage.json", {}), dict(
            variants=[{"status": "PASSED", "count": 40}],
            proof_modes=[{"proofType": "attestation", "count": 20}, {"proofType": "jwt", "count": 20}],
            batch_coverage=[{"status": "NOT_OFFERED_BY_PINNED_SUITE", "count": 8}, {"status": "PASSED", "count": 32}],
            batch_modules=[{"result": "PASSED", "count": 32}],
        ))

    def test_raw_credentials_errors_and_logs_never_reach_transfer_reports(self):
        self.make_both()
        reports.merge(self.shards, self.output)
        for directory in (self.shards, self.output):
            for path in directory.rglob("*.json"):
                self.assertNotIn("PRIVATE", path.read_text())

    def test_missing_shard_fails_but_preserves_other_proof_results(self):
        self.make_shard("jwt")
        errors = reports.merge(self.shards, self.output)
        self.assertTrue(any("missing proof job" in error for error in errors))
        self.assertEqual(len(reports.read_json(self.output / "results.json", [])), 20)
        self.assertEqual(reports.read_json(self.output / "attestation/results.json", None), [])

    def test_failed_setup_or_test_cannot_pass_with_stale_green_results(self):
        for setup, test in (("failure", "skipped"), ("success", "failure"), ("success", "cancelled")):
            with self.subTest(setup=setup, test=test):
                self.make_shard("jwt")
                _, errors = self.make_shard("attestation", setup=setup, test=test)
                self.assertTrue(errors)
                self.assertTrue(reports.merge(self.shards, self.output))

    def test_empty_duplicate_wrong_mode_and_unaccepted_results_fail(self):
        mutations = {
            "empty": lambda rows, matrix: rows.clear(),
            "duplicate": lambda rows, matrix: rows.__setitem__(1, copy.deepcopy(rows[0])),
            "wrong mode": lambda rows, matrix: rows[0]["variant"].update(credential_proof_type="attestation"),
            "failed variant": lambda rows, matrix: rows[0].update(status="FAILED"),
            "unaccepted module": lambda rows, matrix: rows[0]["modules"][0].update(accepted=False),
            "missing modules": lambda rows, matrix: rows[0].update(modules=[]),
            "missing matrix": lambda rows, matrix: matrix.clear(),
            "unknown batch": lambda rows, matrix: matrix[0].update(batchCoverage="UNKNOWN"),
        }
        for label, mutation in mutations.items():
            with self.subTest(label=label):
                _, errors = self.make_shard("jwt", mutate=mutation)
                self.assertTrue(errors)

    def test_missing_matrix_variant_is_not_hidden_by_valid_results(self):
        _, errors = self.make_shard("jwt", mutate=lambda rows, matrix: matrix[0].update(variantId="different"))
        self.assertTrue(any("matrix" in error for error in errors))

    def test_different_protocol_selections_fail_even_with_twenty_results_each(self):
        self.make_both()
        path = self.shards / "issuer-conformance-proof-attestation/results.json"
        rows = reports.read_json(path, [])
        rows[0]["variantId"] = "unexpected-variant-proof-attestation"
        reports.write_json(path, rows)
        self.assertIn("Issuer proof jobs did not execute the same protocol variants.",
                      reports.merge(self.shards, self.output))

    def test_revision_mismatch_fails(self):
        self.make_both()
        (self.shards / "issuer-conformance-proof-attestation/identity-revision.txt").write_text("other-commit")
        self.assertIn("Issuer proof jobs tested different Identity revisions.", reports.merge(self.shards, self.output))

    def test_missing_or_wrong_suite_revision_fails(self):
        self.make_both()
        path = self.shards / "issuer-conformance-proof-attestation/suite-version.json"
        for version in ({}, {**reports.SUITE_PIN, "revision": "different"}):
            reports.write_json(path, version)
            self.assertTrue(any("suite revision" in error for error in reports.merge(self.shards, self.output)))

    def test_setup_failure_without_raw_reports_still_transfers_status(self):
        output = self.shards / "issuer-conformance-proof-jwt"
        self.assertTrue(reports.prepare(self.root / "absent", output, "jwt", "failure", "skipped"))
        self.assertEqual(reports.read_json(output / "shard.json", {})["setupOutcome"], "failure")
        self.assertEqual(reports.read_json(output / "results.json", None), [])

    @unittest.skipUnless(shutil.which("jq"), "issuer summary rendering requires jq")
    def test_summary_keeps_tables_and_plan_links_and_discloses_incomplete_coverage(self):
        self.make_both()
        script = Path(__file__).with_name("publish-issuer-conformance-summary.sh")
        summary = self.root / "summary.md"
        for missing in (False, True):
            with self.subTest(missing=missing):
                if missing:
                    shutil.rmtree(self.shards / "issuer-conformance-proof-attestation")
                reports.merge(self.shards, self.output)
                summary.write_text("")
                subprocess.run(["bash", str(script)], check=True, capture_output=True, text=True,
                               env={**os.environ, "ISSUER_DIAGNOSTICS_DIR": str(self.output),
                                    "GITHUB_STEP_SUMMARY": str(summary)})
                text = summary.read_text()
                self.assertIn("# OpenID4VCI Issuer Conformance Summary", text)
                self.assertIn("| Variant | Status | Plan | Batch coverage |", text)
                self.assertIn("https://conformance.waltid.cloud/plan-detail.html?plan=plan-0", text)
                self.assertIn("issuer-conformance-basic-haip-batch", text)
                self.assertEqual("Issuer result validation failed" in text, missing)


if __name__ == "__main__":
    unittest.main()
