#!/usr/bin/env bash

# EXAMPLE
# bash waltid-unified-build/waltid-identity/waltid-services/waltid-etsi-plugtest-cli/run_plugtest.sh \
#  --data-dir eea-data/data \
#  --output-dir eea-data
# [--vendor-zip /path/to/all_files_YYYYMMDD.zip]

# run_plugtest.sh — Full ETSI plugtest cycle:
#   1. Clean old generated credentials and reports
#   2. Issue fresh credentials (stable key/cert from <data-dir>/)
#   3. Self-verify our credentials
#   4. Verify all other vendors' credentials from the vendor zip
#   5. Build the upload zip (credentials + self-verify reports + other-vendor reports)
#
# Usage:
#   ./run_plugtest.sh --data-dir <path> [--output-dir <path>] [--vendor-zip <path>]
#
# Required:
#   --data-dir    Directory containing key.jwk, cert.pem, and test-cases.json.
#
# Optional:
#   --output-dir  Directory for generated credentials, reports, and upload zip.
#                 Defaults to <data-dir>.
#   --vendor-zip  Path to the vendor zip downloaded from the ETSI portal.
#                 If omitted, the newest *.zip in <output-dir>/company_files_to_verify/ is used.

set -euo pipefail

MODULE_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
CLI="$MODULE_DIR/build/install/waltid-etsi-plugtest-cli/bin/waltid-etsi-plugtest-cli"
TEST_CASES="$MODULE_DIR/testcases"

# ── Argument parsing ──────────────────────────────────────────────────────────
DATA_DIR=""
OUTPUT_DIR=""
VENDOR_ZIP=""
SCHEMA_DIR=""
METADATA_DIR=""

while [[ $# -gt 0 ]]; do
    case "$1" in
        --data-dir)   DATA_DIR="$(cd "$2" && pwd)";   shift 2 ;;
        --output-dir) OUTPUT_DIR="$(cd "$2" && pwd)"; shift 2 ;;
        --vendor-zip) VENDOR_ZIP="$2";                shift 2 ;;
        --schema-dir)   SCHEMA_DIR="$(cd "$2" && pwd)";   shift 2 ;;
        --metadata-dir) METADATA_DIR="$(cd "$2" && pwd)"; shift 2 ;;
        *) echo "ERROR: Unknown argument: $1" >&2; exit 1 ;;
    esac
done

if [[ -z "$DATA_DIR" ]]; then
    echo "ERROR: --data-dir is required." >&2
    echo "Usage: $0 --data-dir <path> [--output-dir <path>] [--vendor-zip <path>]" >&2
    exit 1
fi

# Output dir defaults to data dir if not specified
OUTPUT_DIR="${OUTPUT_DIR:-$DATA_DIR}"

# ── Derived paths ─────────────────────────────────────────────────────────────
ISSUER_KEY="$DATA_DIR/key.jwk"
ISSUER_CERT="$DATA_DIR/cert.pem"
CREDS_DIR="$OUTPUT_DIR/waltid-generated"
REPORTS_DIR="$OUTPUT_DIR/verification-reports"
UPLOAD_ZIP="$OUTPUT_DIR/waltid-upload.zip"

# Stable issuer identity — key/cert must match what is hosted at the x5u URL
ISSUER_URL="https://raw.githubusercontent.com/walt-id/etsi-plugtest-static-files/refs/heads/main"

# ── Schema validation (optional) ──────────────────────────────────────────────
# Resolve the JSON Schema + Type Metadata dirs for the schema verification policy
# (vct -> Type Metadata schema_url -> local schema file). Auto-detect the hosted
# etsi-plugtest-static-files checkout next to the repo if not given explicitly.
if [[ -z "$SCHEMA_DIR" || -z "$METADATA_DIR" ]]; then
    for cand in \
        "$OUTPUT_DIR/../etsi-plugtest-static-files" \
        "$MODULE_DIR/../../../../../etsi-plugtest-static-files"; do
        if [[ -d "$cand/schemas" && -d "$cand/type-metadata" ]]; then
            SCHEMA_DIR="${SCHEMA_DIR:-$(cd "$cand/schemas" && pwd)}"
            METADATA_DIR="${METADATA_DIR:-$(cd "$cand/type-metadata" && pwd)}"
            break
        fi
    done
fi

SCHEMA_ARGS=()
if [[ -n "$SCHEMA_DIR" && -n "$METADATA_DIR" ]]; then
    SCHEMA_ARGS=(--schema-dir "$SCHEMA_DIR" --metadata-dir "$METADATA_DIR")
    echo "Schema validation enabled:"
    echo "  schema-dir   : $SCHEMA_DIR"
    echo "  metadata-dir : $METADATA_DIR"
else
    echo "Schema validation disabled (no --schema-dir/--metadata-dir and none auto-detected)."
fi

# ── Resolve vendor zip ────────────────────────────────────────────────────────
if [[ -z "$VENDOR_ZIP" ]]; then
    VENDOR_ZIP=$(ls -1t "$OUTPUT_DIR/company_files_to_verify/"*.zip 2>/dev/null | head -1 || true)
    if [[ -z "$VENDOR_ZIP" ]]; then
        echo "ERROR: No vendor zip found in $OUTPUT_DIR/company_files_to_verify/ and --vendor-zip not given." >&2
        exit 1
    fi
fi

# ── Sanity checks ─────────────────────────────────────────────────────────────
for f in "$CLI" "$ISSUER_KEY" "$ISSUER_CERT" "$VENDOR_ZIP"; do
    [[ -f "$f" ]] || { echo "ERROR: Required file not found: $f" >&2; exit 1; }
done
# TEST_CASES can be a file (test-cases.json) or a directory (testcases/)
[[ -f "$TEST_CASES" || -d "$TEST_CASES" ]] || { echo "ERROR: Test cases not found: $TEST_CASES" >&2; exit 1; }

echo "Data dir   : $DATA_DIR"
echo "Output dir : $OUTPUT_DIR"
echo "Vendor zip : $VENDOR_ZIP"
echo "CLI        : $CLI"

TIMESTAMP=$(date +%Y%m%d_%H%M%S)
VENDOR_REPORTS_DIR="$REPORTS_DIR/all_files_${TIMESTAMP}"
SELF_REPORTS_DIR="$REPORTS_DIR/self_${TIMESTAMP}"
mkdir -p "$CREDS_DIR" "$REPORTS_DIR"

# ── Step 1: Clean ─────────────────────────────────────────────────────────────
echo ""
echo "==> Step 1: Cleaning old credentials..."
find "$CREDS_DIR" -maxdepth 1 -type f -delete
echo "    Cleaned $CREDS_DIR"

# ── Step 2: Issue credentials ─────────────────────────────────────────────────
echo ""
echo "==> Step 2: Generating credentials..."
"$CLI" generate \
    --test-cases  "$TEST_CASES" \
    --output      "$CREDS_DIR" \
    --issuer-key  "$ISSUER_KEY" \
    --issuer-cert "$ISSUER_CERT" \
    --issuer-url  "$ISSUER_URL" \
    "${SCHEMA_ARGS[@]}"

CRED_COUNT=$(ls "$CREDS_DIR" | wc -l)
echo "    Generated $CRED_COUNT credentials"

# ── Step 3: Self-verification ─────────────────────────────────────────────────
echo ""
echo "==> Step 3: Self-verifying our credentials..."

SELF_ZIP=$(mktemp /tmp/walt-self-XXXXXX.zip)
python3 - <<PYEOF
import zipfile, glob, os
with zipfile.ZipFile("$SELF_ZIP", 'w', zipfile.ZIP_DEFLATED) as zf:
    for f in sorted(glob.glob(os.path.join("$CREDS_DIR", '*'))):
        zf.write(f, 'WALT/' + os.path.basename(f))
PYEOF

mkdir -p "$SELF_REPORTS_DIR"
"$CLI" validate \
    --zip        "$SELF_ZIP" \
    --test-cases "$TEST_CASES" \
    --output     "$SELF_REPORTS_DIR" \
    "${SCHEMA_ARGS[@]}"
rm -f "$SELF_ZIP"

SELF_VALID=$(grep -rl '<VALID>'   "$SELF_REPORTS_DIR" 2>/dev/null | wc -l || true)
SELF_INVALID=$(grep -rl '<INVALID>' "$SELF_REPORTS_DIR" 2>/dev/null | wc -l || true)
echo "    Self-verify: $SELF_VALID VALID, $SELF_INVALID INVALID"
if [[ "$SELF_INVALID" -gt 0 ]]; then
    echo "    WARNING: $SELF_INVALID of our own credentials failed self-verification!" >&2
    grep -rl '<INVALID>' "$SELF_REPORTS_DIR" | while read -r f; do
        echo "      INVALID: $(basename "$f")" >&2
    done
fi

# ── Step 4: Verify vendor credentials ─────────────────────────────────────────
echo ""
echo "==> Step 4: Verifying vendor credentials from $(basename "$VENDOR_ZIP")..."
mkdir -p "$VENDOR_REPORTS_DIR"
"$CLI" validate \
    --zip        "$VENDOR_ZIP" \
    --test-cases "$TEST_CASES" \
    --output     "$VENDOR_REPORTS_DIR" \
    "${SCHEMA_ARGS[@]}"

TOTAL=$(ls "$VENDOR_REPORTS_DIR" | wc -l)
VALID=$(grep -rl '<VALID>'         "$VENDOR_REPORTS_DIR" 2>/dev/null | wc -l || true)
INVALID=$(grep -rl '<INVALID>'     "$VENDOR_REPORTS_DIR" 2>/dev/null | wc -l || true)
INDET=$(grep -rl '<INDETERMINATE>' "$VENDOR_REPORTS_DIR" 2>/dev/null | wc -l || true)
echo "    $TOTAL reports: $VALID VALID, $INVALID INVALID, $INDET INDETERMINATE"

# ── Step 5: Build upload zip ──────────────────────────────────────────────────
echo ""
echo "==> Step 5: Building upload zip..."
python3 - <<PYEOF
import os, zipfile, glob

creds_dir      = "$CREDS_DIR"
vendor_reports = "$VENDOR_REPORTS_DIR"
self_reports   = "$SELF_REPORTS_DIR"
out_zip        = "$UPLOAD_ZIP"

# Self-verify reports take precedence over any WALT reports from the vendor run
self_names = {os.path.basename(f) for f in glob.glob(os.path.join(self_reports, '*.xml'))}

with zipfile.ZipFile(out_zip, 'w', zipfile.ZIP_DEFLATED) as zf:
    cc = sum(1 for f in sorted(glob.glob(os.path.join(creds_dir, '*')))
             if not zf.write(f, os.path.basename(f)))
    rc = sum(1 for f in sorted(glob.glob(os.path.join(vendor_reports, '*.xml')))
             if os.path.basename(f) not in self_names
             and not zf.write(f, os.path.basename(f)))
    sc = sum(1 for f in sorted(glob.glob(os.path.join(self_reports, '*.xml')))
             if not zf.write(f, os.path.basename(f)))

size_mb = os.path.getsize(out_zip) / 1_000_000
print(f"    {cc} credentials + {rc} vendor reports + {sc} self-verify reports = {cc+rc+sc} files, {size_mb:.1f} MB")
print(f"    Output: {out_zip}")
PYEOF

echo ""
echo "Done. Upload: $UPLOAD_ZIP"
