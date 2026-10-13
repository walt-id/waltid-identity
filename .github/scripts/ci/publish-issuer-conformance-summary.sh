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
} >> "$GITHUB_STEP_SUMMARY"
