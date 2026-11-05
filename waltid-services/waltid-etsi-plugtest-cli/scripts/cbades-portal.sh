#!/usr/bin/env bash
# ETSI CB-AdES Plugtests portal helper (https://signature-plugtests.etsi.org).
# Credentials are read from the environment (never commit them):
#   ETSI_USER, ETSI_PASSWORD   portal login
#
# Usage:
#   cbades-portal.sh download <out.zip>           download the "download all" package
#   cbades-portal.sh upload <file.zip>            upload a flat zip (signatures, reports or augmented signatures)
#   cbades-portal.sh check <file.cbor> <out-dir>  run the online conformance checker, write HTML + text reports
#   cbades-portal.sh registered [participant]     list signatures registered in "CB-AdES Uploads"
set -euo pipefail

BASE=https://signature-plugtests.etsi.org
: "${ETSI_USER:?set ETSI_USER}" "${ETSI_PASSWORD:?set ETSI_PASSWORD}"

TOKEN=$(curl -sS --fail --data-urlencode "username=$ETSI_USER" --data-urlencode "password=$ETSI_PASSWORD" "$BASE/api/auth/login" |
    python3 -c 'import sys, json; print(json.load(sys.stdin)["access_token"])')

api() {
    local method=$1 path=$2
    shift 2
    curl -sS --fail -X "$method" -H "Authorization: Bearer $TOKEN" "$@" "$BASE$path"
}

case "${1:-}" in
download)
    api GET /api/sig/download/all -o "${2:?output zip}"
    ;;
upload)
    # The upload page sends sig=cb; other values (e.g. "cbades") are rejected only after the
    # files were already extracted, which leaves them in the folder but unregistered.
    api POST /api/sig/uploadfile -F "file=@${2:?zip file};type=application/zip" -F reflow=false -F highContrast=false -F sig=cb
    echo
    ;;
check)
    file=${2:?cbor file}
    out=${3:?output directory}
    mkdir -p "$out"
    api POST /api/checker/uploadfile -F "file=@$file" -F selectedSpec=ts11915201v010101 -F sig=cbades >"$out/response.json"
    python3 - "$out" <<'PY'
import base64, html, json, os, re, sys
out = sys.argv[1]
data = json.load(open(f"{out}/response.json"))
for name, value in data["result"].items():
    path = os.path.join(out, name.replace("/", "__"))
    content = base64.b64decode(value)
    open(path, "wb").write(content)
    if name.endswith(".html"):
        text = re.sub(r"<(script|style)[^>]*>.*?</\1>", "", content.decode("utf-8", "replace"), flags=re.S)
        text = re.sub(r"\n\s*\n+", "\n", html.unescape(re.sub(r"<[^>]+>", "\n", text)))
        open(path[:-5] + ".txt", "w").write(text)
issues = open(next(os.path.join(out, f) for f in os.listdir(out) if f.endswith("errorsandwarnings.txt"))).read()
for m in re.finditer(r"\d+\.(Failure|Error|Warning|Exception)\s+Tool\s+Location-\{CodeTest\}:\s*(\S+)", issues):
    print(m.group(1), m.group(2))
PY
    ;;
registered)
    api GET /api/sig/signatures_list | python3 -c '
import sys, json
participant = sys.argv[1] if len(sys.argv) > 1 else None
for s in json.load(sys.stdin):
    if participant is None or s["signer"] == participant:
        print(s["signer"], s["signature"])' "${2:-}"
    ;;
*)
    sed -n '2,12p' "$0"
    exit 1
    ;;
esac
