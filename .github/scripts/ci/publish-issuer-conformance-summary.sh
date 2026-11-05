#!/usr/bin/env bash
set -euo pipefail

# Retain the issuer summary tables and artifact links used by the combined job.
ISSUER_REQUESTED=true
ISSUER_SETUP_OUTCOME=$(jq -r 'map("\(.proofType)=\(.setupOutcome)") | join(", ")' "$ISSUER_DIAGNOSTICS_DIR/shards.json")
CONFORMANCE_TEST_OUTCOME=$(jq -r 'map("\(.proofType)=\(.testOutcome)") | join(", ")' "$ISSUER_DIAGNOSTICS_DIR/shards.json")

{
  echo '# OpenID4VCI Issuer Conformance Summary'
  echo
  if [ "$ISSUER_REQUESTED" != "true" ]; then
    echo '_Issuer conformance was not requested._'
  else
    echo '- Strict issuer results: `enabled`'
    echo '- Required executed batch and key-attestation coverage: `enabled` for each proof mode'
    echo '- Proof modes: `jwt` and `attestation` (separate credential configurations)'
    echo "- Issuer runtime setup: \`$ISSUER_SETUP_OUTCOME\`; proof jobs: \`$CONFORMANCE_TEST_OUTCOME\`."
    echo
    if jq -e 'length > 0' "$ISSUER_DIAGNOSTICS_DIR/validation-errors.json" >/dev/null; then
      echo '**Issuer result validation failed:**'
      echo
      jq -r '.[] | "- \(. | @html)"' "$ISSUER_DIAGNOSTICS_DIR/validation-errors.json"
      echo
    fi
    echo '> CI coverage gap: `oid4vci-1_0-issuer-happy-flow-additional-requests` is excluded because its TLS probes reach the Cloudflare Quick Tunnel edge. This run does not establish TLS conformance. Local runs retain this module.'
    echo
    if [ -s "$ISSUER_DIAGNOSTICS_DIR/results.json" ]; then
      issuer_total="$(jq 'length' "$ISSUER_DIAGNOSTICS_DIR/results.json")"
      echo "- Total: $issuer_total"
      echo "- Passed: $(jq '[.[] | select(.status == "PASSED")] | length' "$ISSUER_DIAGNOSTICS_DIR/results.json")"
      echo "- Failed: $(jq '[.[] | select(.status != "PASSED")] | length' "$ISSUER_DIAGNOSTICS_DIR/results.json")"
      jq -r '.batch_coverage[] | "- Batch coverage \(.status): \(.count)"' "$ISSUER_DIAGNOSTICS_DIR/coverage.json"
      echo
      echo '| Proof mode | Variant runs |'
      echo '|------------|--------------|'
      jq -r '.proof_modes[] | "| \(.proofType | @html) | \(.count) |"' \
        "$ISSUER_DIAGNOSTICS_DIR/coverage.json"
      echo
      echo '`NOT_OFFERED_BY_PINNED_SUITE` is a coverage gap, not a batch pass. `UNKNOWN` means the runner did not write a batch classification.'
      echo
      echo '<details>'
      echo '<summary>OpenID4VCI Issuer results</summary>'
      echo
      echo '| Variant | Status | Plan | Batch coverage |'
      echo '|---------|--------|------|----------------|'
      if [ "$issuer_total" -gt 0 ]; then
        jq -r '.[] | "| \(.variantId | @html) | \(.status | @html) | \(if .planId then "[\(.planId | @html)](https://conformance.waltid.cloud/plan-detail.html?plan=\(.planId | @uri))" else "—" end) | \(.batchCoverage | @html) |"' \
          "$ISSUER_DIAGNOSTICS_DIR/results.json"
      fi
      echo
      echo '</details>'
      echo
      if [ "$issuer_total" -eq 0 ]; then
        echo '_No issuer variant results were produced. This does not establish issuer or batch coverage._'
        echo
      fi
      echo 'See the `issuer-conformance-basic-haip-batch` artifact for sanitized plan/test IDs and https://conformance.waltid.cloud/logs.html for suite logs.'
    else
      echo '- Total: 0'
      echo '- Passed: 0'
      echo '- Failed: 0'
      echo
      echo '<details>'
      echo '<summary>OpenID4VCI Issuer results</summary>'
      echo
      echo '| Variant | Status | Plan | Batch coverage |'
      echo '|---------|--------|------|----------------|'
      echo
      echo '</details>'
      echo
      echo '_No issuer conformance results were produced. See the setup/test outcomes above and their logs; this is not a passing result._'
    fi
  fi
  echo
  if [ -f "$ISSUER_DIAGNOSTICS_DIR/deferred/results.json" ]; then
    echo '## Deferred issuance'
    echo
    echo 'VCI single-request happy-flow (without notification) and batch modules only. Both proof modes require initial 202, approval 204, deferred collection 200, and suite proof evidence.'
    echo
    deferred_total="$(jq 'length' "$ISSUER_DIAGNOSTICS_DIR/deferred/results.json")"
    echo "- Total: $deferred_total"
    echo "- Passed: $(jq '[.[] | select(.status == "PASSED")] | length' "$ISSUER_DIAGNOSTICS_DIR/deferred/results.json")"
    echo "- Failed: $(jq '[.[] | select(.status != "PASSED")] | length' "$ISSUER_DIAGNOSTICS_DIR/deferred/results.json")"
    echo
    echo '| Proof mode | Variants | Passed | Failed |'
    echo '|------------|----------|--------|--------|'
    jq -r '. as $results | ["jwt", "attestation"][] as $proof |
      [$results[] | select(.proofType == $proof)] |
      "| \($proof) | \(length) | \([.[] | select(.status == "PASSED")] | length) | \([.[] | select(.status != "PASSED")] | length) |"' \
      "$ISSUER_DIAGNOSTICS_DIR/deferred/results.json"
    echo
    echo 'Expected: 12 deferred variants per proof mode. Deferred uses the generic VCI plan for both Basic/HAIP profiles, including encrypted HAIP batch. Repeated issuance, pending polling and lifecycle recovery are not covered here.'
    echo
    echo '<details>'
    echo '<summary>OpenID4VCI Issuer deferred results</summary>'
    echo
    echo '| Variant | Status | Plan | Batch coverage |'
    echo '|---------|--------|------|----------------|'
    jq -r '
      def cell: (. // "" | tostring | gsub("[\r\n]+"; " ") | @html | gsub("\\|"; "&#124;"));
      .[] | "| \(.variantId | cell) | \(.status | cell) | \(if .planId != null and .planId != "" and .planId != "N/A" then "[\(.planId | cell)](https://conformance.waltid.cloud/plan-detail.html?plan=\(.planId | @uri))" else "—" end) | \(.batchCoverage | cell) |"' \
      "$ISSUER_DIAGNOSTICS_DIR/deferred/results.json"
    echo
    echo '| Test | Status | Suite | Log | Error |'
    echo '|------|--------|-------|-----|-------|'
    jq -r '
      def cell: (. // "" | tostring | gsub("[\r\n]+"; " ") | @html | gsub("\\|"; "&#124;"));
      .[] as $variant |
      if ($variant.modules | length) == 0 then
        [($variant.variantId | cell), ($variant.status // "UNKNOWN" | ascii_downcase | cell), "", "",
         "No module results were produced; see proof-job setup/test logs."]
      else
        $variant.modules[] |
        [(($variant.variantId + "/" + .testModule) | cell),
         (if .accepted == true then "passed" else "failed" end), ((.result // .status) | cell),
         (if .testId != null and .testId != "" and .testId != "N/A" then
            "[log](https://conformance.waltid.cloud/log-detail.html?log=\(.testId | @uri))"
          else "" end),
         ((.failureSummary // (if .accepted == true then "" else
           "Module was not accepted; see suite log and proof evidence." end)) | cell)]
      end | "| " + join(" | ") + " |"' \
      "$ISSUER_DIAGNOSTICS_DIR/deferred/results.json"
    echo
    echo '</details>'
    if [ "$deferred_total" -eq 0 ]; then
      echo
      echo '_No deferred issuer results were produced. See the proof-job outcomes and validation errors above; this does not establish deferred coverage._'
    fi
  fi
  echo
} >> "$GITHUB_STEP_SUMMARY"
