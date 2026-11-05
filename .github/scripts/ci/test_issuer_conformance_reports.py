import copy
import json
import os
from pathlib import Path
import shutil
import subprocess
import tempfile
import unittest
from urllib.parse import quote

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

    def make_deferred(self, proof, mutate=None, outcome="success"):
        destination, _ = self.make_shard(proof)
        raw, matrix = [], []
        for index, values in enumerate(sorted(reports.expected_deferred_variants(proof))):
            variant = dict(zip(reports.DEFERRED_VARIANT_KEYS, values))
            identifier = f"variant-{index}-deferred-proof-{proof}"
            names = [reports.DEFERRED_HAPPY, reports.DEFERRED_BATCH]
            raw.append(dict(
                variantId=identifier, variant=variant, status="PASSED", planId=f"deferred-plan-{index}",
                credentials="PRIVATE", modules=[dict(
                    testModule=name, testId=f"deferred-test-{index}-{name}", status="FINISHED", result="PASSED", accepted=True,
                    deferredEvidence=dict(offers=1, initial202=1, approved204=1, collected200=1, completeOffers=1, failures=0, proofVerified=True),
                ) for name in names],
            ))
            matrix.append(dict(variantId=identifier, batchCoverage="PASSED"))
        if mutate:
            mutate(raw, matrix)
        source = self.root / "deferred-raw" / proof
        reports.write_json(source / "results.json", raw)
        reports.write_json(source / "matrix.json", matrix)
        errors = reports.prepare_deferred(source, destination / "deferred", proof, "success", outcome)
        status = reports.read_json(destination / "shard.json", {})
        status.update(deferredTestOutcome=outcome, reportErrors=errors)
        reports.write_json(destination / "shard.json", status)
        return errors

    def render_summary(self):
        script = Path(__file__).with_name("publish-issuer-conformance-summary.sh")
        summary = self.root / "summary.md"
        summary.write_text("")
        subprocess.run(["bash", str(script)], check=True, capture_output=True, text=True,
                       env={**os.environ, "ISSUER_DIAGNOSTICS_DIR": str(self.output),
                            "GITHUB_STEP_SUMMARY": str(summary)})
        return summary.read_text()

    def test_deferred_runs_require_both_proofs_and_preserve_immediate_results(self):
        for proof in reports.PROOFS:
            self.assertEqual([], self.make_deferred(proof))
        self.assertEqual([], reports.merge(self.shards, self.output, require_deferred=True))
        self.assertEqual(40, len(reports.read_json(self.output / "results.json", [])))
        self.assertEqual(24, len(reports.read_json(self.output / "deferred/results.json", [])))
        for path in self.output.rglob("*.json"):
            self.assertNotIn("PRIVATE", path.read_text())
        shutil.rmtree(self.shards / "issuer-conformance-proof-attestation/deferred")
        self.assertTrue(reports.merge(self.shards, self.output, require_deferred=True))
        self.assertEqual(12, len(reports.read_json(self.output / "deferred/jwt/results.json", [])))

    def test_deferred_skips_missing_evidence_wrong_matrix_and_missing_batch_fail(self):
        mutations = {
            "skipped": lambda rows, matrix: rows[0]["modules"][0].update(result="SKIPPED"),
            "missing approval": lambda rows, matrix: rows[0]["modules"][0]["deferredEvidence"].update(approved204=0),
            "incomplete offer": lambda rows, matrix: rows[0]["modules"][0]["deferredEvidence"].update(completeOffers=0),
            "missing proof evidence": lambda rows, matrix: rows[0]["modules"][0]["deferredEvidence"].update(proofVerified=False),
            "proxy failure": lambda rows, matrix: rows[0]["modules"][0]["deferredEvidence"].update(failures=1),
            "missing matrix": lambda rows, matrix: matrix.clear(),
            "duplicate variant": lambda rows, matrix: rows.__setitem__(1, copy.deepcopy(rows[0])),
            "wallet initiated": lambda rows, matrix: rows[0]["variant"].update(vci_authorization_code_flow_variant="wallet_initiated"),
            "wrong proof": lambda rows, matrix: rows[0]["variant"].update(credential_proof_type="attestation"),
            "missing batch": lambda rows, matrix: next(row for row in rows if len(row["modules"]) == 2)["modules"].pop(),
            "fapi module": lambda rows, matrix: rows[0]["modules"].append(dict(testModule="fapi2-security-profile-final-test")),
        }
        for label, mutate in mutations.items():
            with self.subTest(label=label):
                self.assertTrue(self.make_deferred("jwt", mutate))
        self.assertTrue(self.make_deferred("jwt", outcome="failure"))

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
        for missing in (False, True):
            with self.subTest(missing=missing):
                if missing:
                    shutil.rmtree(self.shards / "issuer-conformance-proof-attestation")
                reports.merge(self.shards, self.output)
                text = self.render_summary()
                self.assertIn("# OpenID4VCI Issuer Conformance Summary", text)
                self.assertIn("| Variant | Status | Plan | Batch coverage |", text)
                self.assertIn("https://conformance.waltid.cloud/plan-detail.html?plan=plan-0", text)
                self.assertIn("issuer-conformance-basic-haip-batch", text)
                self.assertEqual("Issuer result validation failed" in text, missing)

    @unittest.skipUnless(shutil.which("jq"), "issuer summary rendering requires jq")
    def test_deferred_summary_lists_both_proofs_plans_and_module_logs(self):
        for proof in reports.PROOFS:
            self.make_deferred(proof)
        self.assertEqual([], reports.merge(self.shards, self.output, require_deferred=True))
        text = self.render_summary().split("## Deferred issuance", 1)[1]
        self.assertIn("- Total: 24\n- Passed: 24\n- Failed: 0", text)
        self.assertIn("| jwt | 12 | 12 | 0 |", text)
        self.assertIn("| attestation | 12 | 12 | 0 |", text)
        self.assertIn("| Variant | Status | Plan | Batch coverage |", text)
        self.assertIn("| Test | Status | Suite | Log | Error |", text)
        self.assertEqual(24, text.count("https://conformance.waltid.cloud/plan-detail.html?plan="))
        self.assertEqual(48, text.count("https://conformance.waltid.cloud/log-detail.html?log="))
        for proof in reports.PROOFS:
            for module in (reports.DEFERRED_HAPPY, reports.DEFERRED_BATCH):
                self.assertIn(f"variant-0-deferred-proof-{proof}/{module} | passed | PASSED | [log]", text)
        self.assertNotIn("PRIVATE", text)

    @unittest.skipUnless(shutil.which("jq"), "issuer summary rendering requires jq")
    def test_deferred_summary_keeps_failure_link_and_escapes_failure_text(self):
        test_id = "failed test/0?proof=jwt"

        def fail(rows, matrix):
            rows[0]["status"] = "FAILED"
            rows[0]["modules"][0].update(
                testId=test_id, accepted=False, result="FAILED",
                failureSummary="HTTP 502 | <condition>\nupstream failed",
                error="PRIVATE RESPONSE", log="PRIVATE LOG",
            )

        self.make_deferred("jwt", fail)
        self.make_deferred("attestation")
        self.assertTrue(reports.merge(self.shards, self.output, require_deferred=True))
        summary = self.render_summary()
        self.assertIn("Issuer result validation failed", summary)
        text = summary.split("## Deferred issuance", 1)[1]
        self.assertIn("- Total: 24\n- Passed: 23\n- Failed: 1", text)
        self.assertIn("| jwt | 12 | 11 | 1 |", text)
        url = f"https://conformance.waltid.cloud/log-detail.html?log={quote(test_id, safe='')}"
        row = next(line for line in text.splitlines() if url in line)
        self.assertIn("| failed | FAILED | [log]", row)
        self.assertIn("HTTP 502", row)
        self.assertIn("upstream failed", row)
        self.assertEqual(7, len(row.split("|")))
        self.assertNotIn("<condition>", text)
        self.assertNotIn("PRIVATE", text)

    @unittest.skipUnless(shutil.which("jq"), "issuer summary rendering requires jq")
    def test_deferred_summary_shows_blocked_variant_without_fabricated_links(self):
        def block(rows, matrix):
            rows[0].update(status="BLOCKED", planId=None, modules=[])
            rows[1]["modules"][0].update(testId="N/A", accepted=False, result="ERROR")

        self.make_deferred("jwt", block)
        self.assertTrue(reports.merge(self.shards, self.output, require_deferred=True))
        text = self.render_summary().split("## Deferred issuance", 1)[1]
        self.assertIn("| attestation | 0 | 0 | 0 |", text)
        self.assertIn("| variant-0-deferred-proof-jwt | BLOCKED | — |", text)
        self.assertIn("No module results were produced; see proof-job setup/test logs.", text)
        self.assertNotIn("?plan=null", text)
        self.assertNotIn("?log=N/A", text)

    @unittest.skipUnless(shutil.which("jq"), "issuer summary rendering requires jq")
    def test_deferred_summary_discloses_empty_results(self):
        self.assertTrue(reports.merge(self.shards, self.output, require_deferred=True))
        text = self.render_summary().split("## Deferred issuance", 1)[1]
        self.assertIn("- Total: 0\n- Passed: 0\n- Failed: 0", text)
        for proof in reports.PROOFS:
            self.assertIn(f"| {proof} | 0 | 0 | 0 |", text)
        self.assertIn("| Test | Status | Suite | Log | Error |", text)
        self.assertIn("No deferred issuer results were produced", text)
        self.assertNotIn("[log]", text)


if __name__ == "__main__":
    unittest.main()
