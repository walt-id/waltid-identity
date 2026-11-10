#!/usr/bin/env python3
"""Sanitize issuer proof reports and merge them into the existing CI artifact format."""

import argparse
from collections import Counter
import json
from pathlib import Path


PROOFS = ("jwt", "attestation")
VARIANTS_PER_PROOF = 20  # The Basic VCI + HAIP selection in gradle.yml.
SUITE_PIN = {"revision": "db1080a", "tag": "release-v5.2.3", "version": "5.2.4"}


def read_json(path, default):
    return json.loads(path.read_text()) if path.is_file() else default


def write_json(path, value):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(value, indent=2) + "\n")


def result_errors(results, proof):
    errors = []
    ids = [row.get("variantId") for row in results]
    if len(results) != VARIANTS_PER_PROOF or len(set(ids)) != VARIANTS_PER_PROOF:
        errors.append(f"{proof}: expected {VARIANTS_PER_PROOF} distinct issuer variants.")
    if any(row.get("proofType") != proof or
           not str(row.get("variantId", "")).endswith(f"-proof-{proof}") for row in results):
        errors.append(f"{proof}: report contains an unexpected proof mode or variant ID.")
    if any(row.get("status") != "PASSED" or not row.get("modules") or
           any(module.get("accepted") is not True for module in row["modules"]) for row in results):
        errors.append(f"{proof}: issuer report contains non-passing variants or unaccepted/missing modules.")
    if any(row.get("batchCoverage") not in ("PASSED", "NOT_OFFERED_BY_PINNED_SUITE") for row in results):
        errors.append(f"{proof}: issuer report has missing or unsuccessful batch classifications.")
    return errors


def prepare(reports, output, proof, setup_outcome, test_outcome, deferred_reports=None, deferred_test_outcome=None):
    raw_results = read_json(reports / "results.json", [])
    matrix = read_json(reports / "matrix.json", [])
    matrix_by_id = {row["variantId"]: row for row in matrix}
    # Match the previous jq allowlist. Never transfer raw errors, credentials or suite logs.
    results = [dict(
        variantId=row.get("variantId"), status=row.get("status"), planId=row.get("planId"),
        proofType=row.get("variant", {}).get("credential_proof_type"),
        batchCoverage=matrix_by_id.get(row.get("variantId"), {}).get("batchCoverage") or "UNKNOWN",
        modules=[{key: module.get(key) for key in
                  ("testModule", "testId", "status", "result", "accepted", "failureSummary")}
                 for module in row.get("modules", [])],
    ) for row in raw_results]
    errors = result_errors(results, proof)
    if len(matrix) != VARIANTS_PER_PROOF or set(matrix_by_id) != {row["variantId"] for row in results}:
        errors.append(f"{proof}: selected matrix and executed results do not match.")
    if setup_outcome != "success" or test_outcome != "success":
        errors.append(f"{proof}: issuer setup and live test steps must both succeed.")
    if deferred_reports is not None:
        errors.extend(prepare_deferred(deferred_reports, output / "deferred", proof, setup_outcome, deferred_test_outcome))
    write_json(output / "results.json", results)
    write_json(output / "shard.json", dict(
        proofType=proof, setupOutcome=setup_outcome, testOutcome=test_outcome,
        deferredTestOutcome=deferred_test_outcome, reportErrors=errors,
    ))
    return errors


def grouped(values, name):
    return [{name: value, "count": count} for value, count in
            sorted(Counter(values).items(), key=lambda item: str(item[0]))]


def merge(shards, output, require_deferred=False):
    results, statuses, errors = [], [], []
    revisions, versions, variant_ids = {}, {}, {}
    for proof in PROOFS:
        directory = shards / f"issuer-conformance-proof-{proof}"
        status = read_json(directory / "shard.json", None)
        if status is None:
            errors.append(f"{proof}: missing proof job report.")
            status = dict(proofType=proof, setupOutcome="missing", testOutcome="missing", reportErrors=[])
        if status.get("proofType") != proof or status.get("setupOutcome") != "success" or status.get("testOutcome") != "success":
            errors.append(f"{proof}: issuer setup and live test steps must both succeed.")
        errors.extend(status.get("reportErrors", []))
        statuses.append(status)
        proof_results = read_json(directory / "results.json", [])
        errors.extend(result_errors(proof_results, proof))
        variant_ids[proof] = {row["variantId"].removesuffix(f"-proof-{proof}") for row in proof_results}
        results.extend(proof_results)
        write_json(output / proof / "results.json", proof_results)
        revision = directory / "identity-revision.txt"
        if revision.is_file() and revision.read_text().strip():
            revisions[proof] = revision.read_text().strip()
        else:
            errors.append(f"{proof}: missing Identity revision.")
        version = read_json(directory / "suite-version.json", {})
        if any(version.get(key) != value for key, value in SUITE_PIN.items()):
            errors.append(f"{proof}: missing or unexpected hosted suite revision.")
        if version:
            versions[proof] = version

    if len(set(revisions.values())) > 1:
        errors.append("Issuer proof jobs tested different Identity revisions.")
    if variant_ids["jwt"] != variant_ids["attestation"]:
        errors.append("Issuer proof jobs did not execute the same protocol variants.")
    if revisions:
        (output / "identity-revision.txt").write_text(next(iter(revisions.values())) + "\n")
    if versions:
        write_json(output / "suite-version.json", next(iter(versions.values())))
    write_json(output / "results.json", results)
    write_json(output / "shards.json", statuses)
    write_json(output / "coverage.json", dict(
        variants=grouped((row.get("status") for row in results), "status"),
        proof_modes=grouped((row.get("proofType") for row in results), "proofType"),
        batch_coverage=grouped((row.get("batchCoverage") for row in results), "status"),
        batch_modules=grouped((module.get("result") for row in results for module in row.get("modules", [])
                               if module.get("testModule") == "oid4vci-1_0-issuer-batch-issuance"), "result"),
    ))
    if require_deferred:
        errors.extend(merge_deferred(shards, output, statuses))
    errors = list(dict.fromkeys(errors))
    write_json(output / "validation-errors.json", errors)
    return errors


DEFERRED_HAPPY = "oid4vci-1_0-issuer-happy-flow-skip-notification"
DEFERRED_BATCH = "oid4vci-1_0-issuer-batch-issuance"
DEFERRED_VARIANT_KEYS = ("fapi_profile", "credential_format", "vci_grant_type",
                         "vci_authorization_code_flow_variant", "client_auth_type", "sender_constrain",
                         "authorization_request_type", "fapi_request_method", "vci_credential_encryption",
                         "credential_proof_type", "issuance_mode")


def expected_deferred_variants(proof):
    return {(profile, fmt, grant, "issuer_initiated", "client_attestation", "dpop", "simple", "unsigned",
             encryption, proof, "deferred")
            for profile in ("vci", "vci_haip") for fmt in ("sd_jwt_vc", "mdoc")
            for grant in (("authorization_code", "pre_authorization_code") if profile == "vci" else ("authorization_code",))
            for encryption in ("plain", "encrypted")}


def deferred_errors(rows, proof):
    errors = []
    expected = expected_deferred_variants(proof)
    actual = {tuple(row.get("variant", {}).get(key) for key in DEFERRED_VARIANT_KEYS) for row in rows}
    if len(rows) != 12 or len({row.get("variantId") for row in rows}) != 12 or actual != expected:
        errors.append(f"{proof}: expected exactly the 12 deferred issuer variants.")
    for row in rows:
        variant = row.get("variant", {})
        modules = row.get("modules", [])
        names = [module.get("testModule") for module in modules]
        required = {DEFERRED_HAPPY, DEFERRED_BATCH}
        expected_batch = "PASSED"
        if (row.get("status") != "PASSED" or not row.get("planId") or
                not str(row.get("variantId", "")).endswith(f"-deferred-proof-{proof}") or
                set(names) != required or len(names) != len(required) or row.get("batchCoverage") != expected_batch):
            errors.append(f"{proof}: deferred variants must pass every expected VCI module.")
        for module in modules:
            evidence = module.get("deferredEvidence") or {}
            offers = evidence.get("offers", 0)
            counts = [evidence.get(key, 0) for key in ("initial202", "approved204", "collected200")]
            if (module.get("status") != "FINISHED" or module.get("result") != "PASSED" or
                    module.get("accepted") is not True or not module.get("testId") or
                    type(offers) is not int or offers <= 0 or evidence.get("completeOffers") != offers or
                    evidence.get("failures") != 0 or evidence.get("proofVerified") is not True or any(type(value) is not int or value < offers for value in counts)):
                errors.append(f"{proof}: deferred module is missing a pass or complete approval/collection/proof evidence.")
    return list(dict.fromkeys(errors))


def prepare_deferred(reports, output, proof, setup_outcome, test_outcome):
    raw = read_json(reports / "results.json", [])
    matrix = read_json(reports / "matrix.json", [])
    by_id = {row.get("variantId"): row for row in matrix}
    expected_values = {key: {variant[index] for variant in expected_deferred_variants(proof)}
                       for index, key in enumerate(DEFERRED_VARIANT_KEYS)}
    rows = [dict(
        variantId=row.get("variantId"), status=row.get("status"), planId=row.get("planId"), proofType=proof,
        variant={key: value if value in expected_values[key] else None
                 for key in DEFERRED_VARIANT_KEYS for value in [row.get("variant", {}).get(key)]},
        batchCoverage=by_id.get(row.get("variantId"), {}).get("batchCoverage", "UNKNOWN"),
        modules=[dict(
            **{key: module.get(key) for key in ("testModule", "testId", "status", "result", "accepted", "failureSummary")},
            deferredEvidence={key: (module.get("deferredEvidence") or {}).get(key)
                              for key in ("offers", "initial202", "approved204", "collected200", "completeOffers", "failures", "proofVerified")},
        ) for module in row.get("modules", [])],
    ) for row in raw]
    errors = deferred_errors(rows, proof)
    if len(matrix) != 12 or set(by_id) != {row["variantId"] for row in rows}:
        errors.append(f"{proof}: deferred matrix and executed results do not match.")
    if setup_outcome != "success" or test_outcome != "success":
        errors.append(f"{proof}: deferred setup and live test steps must both succeed.")
    write_json(output / "results.json", rows)
    return errors


def merge_deferred(shards, output, statuses):
    errors, rows = [], []
    for proof in PROOFS:
        proof_rows = read_json(shards / f"issuer-conformance-proof-{proof}" / "deferred/results.json", [])
        errors.extend(deferred_errors(proof_rows, proof))
        status = next((row for row in statuses if row.get("proofType") == proof), {})
        if status.get("deferredTestOutcome") != "success":
            errors.append(f"{proof}: deferred live test step must succeed.")
        write_json(output / "deferred" / proof / "results.json", proof_rows)
        rows.extend(proof_rows)
    write_json(output / "deferred/results.json", rows)
    write_json(output / "deferred/coverage.json", dict(
        variants=grouped((row.get("status") for row in rows), "status"),
        proof_modes=grouped((row.get("proofType") for row in rows), "proofType"),
        batch_coverage=grouped((row.get("batchCoverage") for row in rows), "status"),
    ))
    return errors


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    commands = parser.add_subparsers(dest="command", required=True)
    prepare_parser = commands.add_parser("prepare")
    prepare_parser.add_argument("--reports", type=Path, required=True)
    prepare_parser.add_argument("--proof", choices=PROOFS, required=True)
    prepare_parser.add_argument("--setup-outcome", required=True)
    prepare_parser.add_argument("--test-outcome", required=True)
    prepare_parser.add_argument("--deferred-reports", type=Path)
    prepare_parser.add_argument("--deferred-test-outcome")
    merge_parser = commands.add_parser("merge")
    merge_parser.add_argument("--require-deferred", action="store_true")
    merge_parser.add_argument("--shards", type=Path, required=True)
    for command in (prepare_parser, merge_parser):
        command.add_argument("--output", type=Path, required=True)
    args = parser.parse_args()
    if args.command == "prepare":
        errors = prepare(args.reports, args.output, args.proof, args.setup_outcome, args.test_outcome,
                         args.deferred_reports, args.deferred_test_outcome)
    else:
        errors = merge(args.shards, args.output, args.require_deferred)
    for error in errors:
        print(f"::error::{error}")
    return bool(errors)


if __name__ == "__main__":
    raise SystemExit(main())
